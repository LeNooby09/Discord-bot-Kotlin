package commands

import dev.kord.core.event.message.MessageCreateEvent
import kotlinx.coroutines.*
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Command that allows users to monitor server status.
 * Supports checking if servers are online, adding/removing servers, and notifications on status changes.
 */
class StatusCommand : Command {
  override val name = "status"
  override val description = "Monitors server status with subcommands: check, add, delete, list, help"

  // Store servers and their last known status
  private val servers = ConcurrentHashMap<String, Boolean>()

  // Background job for monitoring servers
  private var monitorJob: Job? = null

  // Channel to send notifications to
  private var notificationChannel: dev.kord.core.behavior.channel.MessageChannelBehavior? = null

  init {
    // Start the background monitoring job
    startMonitoring()
  }

  override suspend fun execute(event: MessageCreateEvent): Boolean {
    val content = event.message.content
    val mention = "<@1327594330130481272>"
    val messageText = content.removePrefix(mention).trim().removePrefix(name).trim()

    val username = event.message.author?.username ?: "Unknown"
    logger.info("Executing status command for user: $username")

    // Set the notification channel to the current channel if not already set
    if (notificationChannel == null) {
      logger.debug("Setting notification channel")
      notificationChannel = event.message.channel
    }

    // Parse the subcommand and arguments
    val parts = messageText.split(" ")
    val subcommand = if (parts.isNotEmpty()) parts[0].lowercase() else "help"
    logger.info("Status subcommand: $subcommand, args: ${parts.drop(1)}")

    when (subcommand) {
      "check" -> handleCheckCommand(event, parts.drop(1))
      "add" -> handleAddCommand(event, parts.drop(1))
      "delete", "remove" -> handleDeleteCommand(event, parts.drop(1))
      "list" -> handleListCommand(event)
      else -> handleHelpCommand(event)
    }

    logger.debug("Status command execution completed")
    return true
  }

  private suspend fun handleCheckCommand(event: MessageCreateEvent, args: List<String>) {
    logger.debug("Handling check command")

    if (args.isEmpty()) {
      logger.info("Check command called without server argument")
      event.message.channel.createMessage("Please specify a server to check. Usage: status check <server>")
      return
    }

    val server = args[0]
    logger.info("Checking status of server: $server")

    // Check if the server is in the IP blacklist
    if (isIpBlacklisted(server)) {
      logger.warn("Attempted to check blacklisted IP: $server")
      event.message.channel.createMessage("⛔ This IP address is blacklisted and cannot be checked.")
      return
    }

    // Send initial message indicating check is in progress
    logger.debug("Sending initial status message")
    event.message.channel.createMessage("⏳ Checking server $server status...")

    // Store the channel for later use
    val channel = event.message.channel

    // Launch a coroutine to perform the check in the background
    logger.debug("Launching background check for server: $server")
    CoroutineScope(Dispatchers.Default).launch {
      try {
        // Perform the server check
        logger.debug("Performing status check for server: $server")
        val isOnline = checkServerStatus(server)

        // Create the status message
        val statusMessage = if (isOnline) {
          logger.info("Server $server is online")
          "✅ Server $server is online"
        } else {
          logger.info("Server $server is offline")
          "❌ Server $server is offline"
        }

        // Send the result as a new message
        logger.debug("Sending status result message")
        withContext(Dispatchers.IO) {
          channel.createMessage(statusMessage)
        }
      } catch (e: Exception) {
        // Handle any errors
        logger.error("Error checking server status for $server", e)
        withContext(Dispatchers.IO) {
          channel.createMessage("❌ Error checking server $server: ${e.message}")
        }
      }
    }
  }

  private suspend fun handleAddCommand(event: MessageCreateEvent, args: List<String>) {
    if (args.isEmpty()) {
      event.message.channel.createMessage("Please specify a server to add. Usage: status add <server>")
      return
    }

    val server = args[0]

    // Check if the server is in the IP blacklist
    if (isIpBlacklisted(server)) {
      event.message.channel.createMessage("⛔ This IP address is blacklisted and cannot be monitored.")
      return
    }

    // Check if the server is valid
    try {
      // Try to resolve the hostname or IP
      val isValid = try {
        InetAddress.getByName(server)
        true
      } catch (e: UnknownHostException) {
        // If it's not a valid hostname/IP, check if it's a URL
        try {
          URL(if (!server.startsWith("http")) "https://$server" else server).toURI()
          true
        } catch (e: Exception) {
          false
        }
      }

      if (!isValid) {
        event.message.channel.createMessage("Invalid server address: $server")
        return
      }

      // Send initial message indicating check is in progress
      val message = event.message.channel.createMessage("⏳ Adding server $server to monitoring list. Checking status...")

      // Store the channel for later use
      val channel = event.message.channel

      // Launch a coroutine to perform the check in the background
      CoroutineScope(Dispatchers.Default).launch {
        try {
          // Add the server to the monitoring list
          val status = checkServerStatus(server)
          servers[server] = status

          // Send the result as a new message
          withContext(Dispatchers.IO) {
            channel.createMessage("Added server $server to monitoring list. Current status: ${if (status) "online ✅" else "offline ❌"}")
          }
        } catch (e: Exception) {
          // Handle any errors
          logger.error("Error adding server $server to monitoring list", e)
          withContext(Dispatchers.IO) {
            channel.createMessage("❌ Error adding server $server: ${e.message}")
          }
        }
      }
    } catch (e: Exception) {
      event.message.channel.createMessage("Error validating server address: ${e.message}")
    }
  }

  private suspend fun handleDeleteCommand(event: MessageCreateEvent, args: List<String>) {
    if (args.isEmpty()) {
      event.message.channel.createMessage("Please specify a server to delete. Usage: status delete <server>")
      return
    }

    val server = args[0]

    if (servers.remove(server) != null) {
      event.message.channel.createMessage("Removed server $server from monitoring list")
    } else {
      event.message.channel.createMessage("Server $server is not in the monitoring list")
    }
  }

  private suspend fun handleListCommand(event: MessageCreateEvent) {
    if (servers.isEmpty()) {
      event.message.channel.createMessage("No servers are currently being monitored")
      return
    }

    val serverList = servers.entries.joinToString("\n") { (server, status) ->
      "$server: ${if (status) "online ✅" else "offline ❌"}"
    }

    event.message.channel.createMessage("Monitored servers:\n$serverList")
  }

  private suspend fun handleHelpCommand(event: MessageCreateEvent) {
    val helpText = """
            **Status Command Help**
            Monitor server status with the following subcommands:

            `status check <server>` - Check if a server is online
            `status add <server>` - Add a server to the monitoring list
            `status delete <server>` - Remove a server from the monitoring list
            `status list` - List all monitored servers and their status

            The bot will automatically notify you when a server's status changes.
        """.trimIndent()

    event.message.channel.createMessage(helpText)
  }

  private fun startMonitoring() {
    logger.info("Starting server monitoring job")
    monitorJob = CoroutineScope(Dispatchers.Default).launch {
      while (isActive) {
        checkAllServers()
        logger.debug("Server monitoring cycle completed, waiting for next cycle")
        delay(30.seconds)
      }
    }
    logger.info("Server monitoring job started")
  }

  private suspend fun checkAllServers() {
    val serverCount = servers.size
    if (serverCount > 0) {
      logger.debug("Checking status of $serverCount servers")
    }

    val serversCopy = HashMap(servers)

    for ((server, previousStatus) in serversCopy) {
      logger.debug("Checking server: $server (previous status: ${if (previousStatus) "online" else "offline"})")
      val currentStatus = checkServerStatus(server)

      // If status changed, update and notify
      if (currentStatus != previousStatus) {
        logger.info("Server $server status changed from ${if (previousStatus) "online" else "offline"} to ${if (currentStatus) "online" else "offline"}")
        servers[server] = currentStatus
        notifyStatusChange(server, currentStatus)
      }
    }
  }

  private suspend fun notifyStatusChange(server: String, isOnline: Boolean) {
    val channel = notificationChannel
    if (channel == null) {
      logger.warn("Cannot notify status change: notification channel not set")
      return
    }

    val statusMessage = if (isOnline) {
      "🔔 Server $server is now online ✅"
    } else {
      "🔔 Server $server is now offline ❌"
    }

    logger.info("Sending status change notification for server $server: ${if (isOnline) "online" else "offline"}")
    try {
      channel.createMessage(statusMessage)
      logger.debug("Status change notification sent successfully")
    } catch (e: Exception) {
      logger.error("Failed to send status change notification for server $server", e)
    }
  }

  private fun checkServerStatus(server: String): Boolean {
    val normalizedServer = normalizeServerAddress(server)
    logger.debug("Checking status for server: $normalizedServer")

    // First try HTTP/HTTPS connection for web domains
    if (isLikelyWebDomain(server)) {
      logger.debug("Server $server appears to be a web domain, trying HTTP check")
      try {
        val result = checkWebDomainStatus(server)
        logger.debug("HTTP check for $server completed with result: ${if (result) "online" else "offline"}")
        return result
      } catch (e: Exception) {
        logger.warn("HTTP check failed for $server: ${e.message}, falling back to ping", e)
        // Fall back to ping if HTTP check fails
      }
    }

    // Try to ping the server as fallback or for non-web domains
    logger.debug("Trying ping check for server: $normalizedServer")
    return try {
      val address = InetAddress.getByName(normalizedServer)
      val result = address.isReachable(5000) // 5 second timeout
      logger.debug("Ping check for $normalizedServer completed with result: ${if (result) "reachable" else "unreachable"}")
      result
    } catch (e: IOException) {
      logger.debug("Ping check for $normalizedServer failed with IOException: ${e.message}")
      false
    } catch (e: UnknownHostException) {
      logger.debug("Ping check for $normalizedServer failed with UnknownHostException: ${e.message}")
      false
    } catch (e: Exception) {
      logger.warn("Ping check for $normalizedServer failed with unexpected exception", e)
      false
    }
  }

  /**
   * Checks if a domain is likely a web domain (vs. a plain IP or service)
   */
  private fun isLikelyWebDomain(server: String): Boolean {
    val normalized = server.lowercase()
    return normalized.contains("://") || // Has protocol
           normalized.startsWith("www.") || // Starts with www
           normalized.endsWith(".com") || normalized.endsWith(".org") || 
           normalized.endsWith(".net") || normalized.endsWith(".io") ||
           normalized.endsWith(".co") || normalized.contains(".") // Has domain extension
  }

  /**
   * Checks if a web domain is available by making an HTTP request
   */
  private fun checkWebDomainStatus(server: String): Boolean {
    logger.debug("Checking web domain status for: $server")
    try {
      // Ensure server has a protocol
      val urlStr = if (!server.contains("://")) {
        // Try HTTPS first, most modern sites use it
        logger.debug("No protocol specified, trying HTTPS for $server")
        "https://$server"
      } else {
        server
      }

      logger.debug("Making HTTP request to: $urlStr")
      val url = URL(urlStr)
      val connection = url.openConnection() as HttpURLConnection
      connection.connectTimeout = 5000 // 5 second timeout
      connection.readTimeout = 5000
      connection.requestMethod = "HEAD" // Don't need the body, just check if site responds
      connection.instanceFollowRedirects = true

      val responseCode = connection.responseCode
      connection.disconnect()
      logger.debug("Received response code $responseCode from $urlStr")

      // Consider 2xx and 3xx response codes as "online"
      val isOnline = responseCode in 200..399
      logger.debug("Web domain $server is ${if (isOnline) "online" else "offline"} (response code: $responseCode)")
      return isOnline
    } catch (e: Exception) {
      logger.debug("HTTPS request failed for $server: ${e.message}")

      // If HTTPS fails, try HTTP as fallback
      if (server.contains("https://")) {
        logger.debug("Trying HTTP fallback for $server")
        try {
          val httpUrl = URL(server.replace("https://", "http://"))
          logger.debug("Making HTTP request to: $httpUrl")
          val connection = httpUrl.openConnection() as HttpURLConnection
          connection.connectTimeout = 5000
          connection.readTimeout = 5000
          connection.requestMethod = "HEAD"
          connection.instanceFollowRedirects = true

          val responseCode = connection.responseCode
          connection.disconnect()
          logger.debug("Received response code $responseCode from HTTP fallback")

          val isOnline = responseCode in 200..399
          logger.debug("Web domain $server HTTP fallback is ${if (isOnline) "online" else "offline"} (response code: $responseCode)")
          return isOnline
        } catch (e: Exception) {
          logger.debug("HTTP fallback request also failed for $server: ${e.message}")
          return false
        }
      }
      logger.debug("Web domain check failed for $server")
      return false
    }
  }

  /**
   * Checks if an IP address is blacklisted
   * Currently blacklists all IPs starting with "100."
   */
  private fun isIpBlacklisted(server: String): Boolean {
    // Normalize the server address to get just the hostname/IP
    val normalizedServer = try {
      // For URLs with protocol, extract the host
      if (server.contains("://")) {
        val url = URL(server)
        url.host
      } else {
        server
      }
    } catch (e: Exception) {
      server
    }

    // Check if the normalized server address is an IP address starting with "100."
    // IP address pattern: digits.digits.digits.digits
    val ipPattern = Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$")

    if (normalizedServer.matches(ipPattern)) {
      // If it's an IP address, check if it starts with "100."
      return normalizedServer.startsWith("100.")
    }

    // Try to resolve the hostname to an IP address
    try {
      val inetAddress = InetAddress.getByName(normalizedServer)
      val ipAddress = inetAddress.hostAddress

      // Check if the resolved IP address starts with "100."
      return ipAddress.startsWith("100.")
    } catch (e: Exception) {
      // If resolution fails, it's not a blacklisted IP
      return false
    }
  }

  /**
   * Normalizes a server address by extracting just the hostname/domain part
   * This is particularly important for ping checks which need just the host
   */
  private fun normalizeServerAddress(server: String): String {
    return try {
      // Handle URLs with protocol
      if (server.contains("://")) {
        val url = URL(server)
        return url.host
      }

      // Handle URLs without protocol but with www or common TLDs
      if (isLikelyWebDomain(server)) {
        // Try to parse as URL with default protocol
        try {
          val url = URL("https://$server")
          return url.host
        } catch (e: Exception) {
          // If that fails, try to extract domain manually
          val domainParts = server.split("/", limit = 2)
          return domainParts[0] // Take just the domain part, ignore any path
        }
      }

      // For non-web domains or IPs, return as is
      server
    } catch (e: Exception) {
      // If all parsing fails, return original input
      logger.warn("Error normalizing server address: ${e.message}", e)
      server
    }
  }
}
