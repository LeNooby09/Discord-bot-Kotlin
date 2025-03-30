package commands

import database.DatabaseManager
import dev.kord.core.event.message.MessageCreateEvent
import kotlinx.coroutines.*
import utils.NetworkUtils
import utils.ServerUtils
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Command that allows users to monitor server status.
 * Supports checking if servers are online, adding/removing servers, and notifications on status changes.
 * Stores server data on a per-user basis and persists it to disk.
 */
class StatusCommand : Command {
  override val name = "status"
  override val description = "Monitors server status with subcommands: check, add, delete, list, help"

  // Store servers and their last known status per user
  // Map<UserId, Map<ServerAddress, IsOnline>>
  private val userServers = ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>>()

  // Store channel IDs for each user-server pair
  // Map<UserId, Map<ServerAddress, ChannelId>>
  private val userServerChannels = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()

  // Database manager for storing and retrieving data
  private val dbManager = DatabaseManager.getInstance()

  // Background job for monitoring servers
  private var monitorJob: Job? = null

  // Channel to send notifications to (for backward compatibility)
  private var notificationChannel: dev.kord.core.behavior.channel.MessageChannelBehavior? = null

  // Cache for server status to reduce network calls
  private val serverStatusCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()

  // Cache TTL in milliseconds (5 minutes)
  private val CACHE_TTL = 5 * 60 * 1000L

  // Dispatcher for network operations
  private val networkDispatcher = Dispatchers.IO.limitedParallelism(10)

  init {
    // Load saved data
    loadData()

    // Start the background monitoring job
    startMonitoring()
  }

  /**
   * Loads user server data from the database
   */
  private fun loadData() {
    try {
      logger.info("Loading user server data from database")

      // Load all server status data from the database
      val loadedData = dbManager.loadAllServerStatus()

      // Update the in-memory map with the loaded data
      userServers.clear()
      userServers.putAll(loadedData)

      logger.info("Loaded data for ${userServers.size} users from database")
    } catch (e: Exception) {
      logger.error("Error loading user server data from database", e)
    }
  }

  // Track the last save time to implement simple debouncing
  private var lastSaveTime = 0L
  private var pendingSaveJob: Job? = null

  /**
   * Saves user server data to the database with simple debouncing
   * This method is optimized to reduce database load by:
   * 1. Debouncing rapid save requests
   * 2. Using coroutines for asynchronous saving
   */
  private fun saveData() {
    // Cancel any pending save job
    pendingSaveJob?.cancel()

    // Launch in a new coroutine to avoid blocking
    pendingSaveJob = CoroutineScope(Dispatchers.Default).launch {
      // Simple debouncing - only save if it's been at least 500ms since the last save
      val currentTime = System.currentTimeMillis()
      val timeSinceLastSave = currentTime - lastSaveTime

      if (timeSinceLastSave < 500) {
        delay(500 - timeSinceLastSave)
      }

      try {
        logger.info("Saving user server data to database")
        lastSaveTime = System.currentTimeMillis()

        // Create a snapshot of the data to save to avoid concurrent modification
        val dataToSave = HashMap<String, Map<String, Boolean>>()
        userServers.forEach { (userId, serverMap) ->
          dataToSave[userId] = HashMap(serverMap)
        }

        // Save each user's server data to the database
        dataToSave.forEach { (userId, serverMap) ->
          try {
            dbManager.saveServerStatus(userId, serverMap)
          } catch (e: Exception) {
            logger.error("Error saving data for user $userId", e)
          }
        }

        logger.info("User server data saved successfully to database")
      } catch (e: Exception) {
        logger.error("Error saving user server data to database", e)
      }
    }
  }


  /**
   * Gets the server map for a user, loading it from the database if it doesn't exist in memory
   */
  private fun getServerMapForUser(userId: String): ConcurrentHashMap<String, Boolean> {
    return userServers.computeIfAbsent(userId) { 
      // If not in memory, try to load from database
      logger.debug("Loading server map for user $userId from database")
      dbManager.loadServerStatus(userId)
    }
  }

  override suspend fun execute(event: MessageCreateEvent): Boolean {
    val messageText = extractMessageText(event)

    val username = event.message.author?.username ?: "Unknown"
    val userId = event.message.author?.id?.toString() ?: "unknown"
    logger.info("Executing status command for user: $username (ID: $userId)")

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

  private suspend fun handleCheckCommand(event: MessageCreateEvent, args: List<String>) = coroutineScope {
    logger.debug("Handling check command")

    if (args.isEmpty()) {
      logger.info("Check command called without server argument")
      event.message.channel.createMessage("Please specify a server to check. Usage: status check <server>")
      return@coroutineScope
    }

    val server = args[0]
    logger.info("Checking status of server: $server")

    // Check if the server is in the IP blacklist
    if (isIpBlacklisted(server)) {
      logger.warn("Attempted to check blacklisted IP: $server")
      event.message.channel.createMessage("This IP address is blacklisted and cannot be checked.")
      return@coroutineScope
    }

    // Send initial message indicating check is in progress
    logger.debug("Sending initial status message")
    event.message.channel.createMessage("Checking server $server status...")

    // Store the channel for later use
    val channel = event.message.channel

    // Use async to perform the check without blocking the command response
    // This provides structured concurrency (will be properly cancelled if needed)
    launch(networkDispatcher) {
      try {
        // Perform the server check
        logger.debug("Performing status check for server: $server")
        val isOnline = checkServerStatus(server)

        // Create the status message
        val statusMessage = if (isOnline) {
          logger.info("Server $server is online")
          "Server $server is online"
        } else {
          logger.info("Server $server is offline")
          "Server $server is offline"
        }

        // Send the result as a new message
        logger.debug("Sending status result message")
        channel.createMessage(statusMessage)
      } catch (e: Exception) {
        // Handle any errors
        logger.error("Error checking server status for $server", e)
        channel.createMessage("Error checking server $server: ${e.message}")
      }
    }
  }

  private suspend fun handleAddCommand(event: MessageCreateEvent, args: List<String>) = coroutineScope {
    if (args.isEmpty()) {
      event.message.channel.createMessage("Please specify a server to add. Usage: status add <server>")
      return@coroutineScope
    }

    val server = args[0]
    val userId = event.message.author?.id?.toString() ?: "unknown"

    // Check if the server is in the IP blacklist
    if (isIpBlacklisted(server)) {
      event.message.channel.createMessage("This IP address is blacklisted and cannot be monitored.")
      return@coroutineScope
    }

    // Check if the server is valid using ServerUtils - this is already a suspend function
    val isValid = try {
      ServerUtils.isValidServerAddress(server)
    } catch (e: Exception) {
      event.message.channel.createMessage("Error validating server address: ${e.message}")
      return@coroutineScope
    }

    if (!isValid) {
      event.message.channel.createMessage("Invalid server address: $server")
      return@coroutineScope
    }

    // Send initial message indicating check is in progress
    val message = event.message.channel.createMessage("Adding server $server to monitoring list. Checking status...")

    // Store the channel for later use
    val channel = event.message.channel

    // Use launch with structured concurrency for better lifecycle management
    launch(networkDispatcher) {
      try {
        // Add the server to the monitoring list
        val status = checkServerStatus(server)

        // Update user's server map
        val userServersMap = getServerMapForUser(userId)
        userServersMap[server] = status

        // Store the channel ID for this user-server pair
        val channelId = channel.id.toString()
        userServerChannels.computeIfAbsent(userId) { ConcurrentHashMap() }[server] = channelId
        logger.debug("Stored channel ID $channelId for user $userId and server $server")

        // Save the updated data
        saveData()

        // Send the result as a new message
        channel.createMessage("Added server $server to monitoring list. Current status: ${if (status) "online" else "offline"}")
      } catch (e: Exception) {
        // Handle any errors
        logger.error("Error adding server $server to monitoring list", e)
        channel.createMessage("Error adding server $server: ${e.message}")
      }
    }
  }

  private suspend fun handleDeleteCommand(event: MessageCreateEvent, args: List<String>) = coroutineScope {
    if (args.isEmpty()) {
      event.message.channel.createMessage("Please specify a server to delete. Usage: status delete <server>")
      return@coroutineScope
    }

    val server = args[0]
    val userId = event.message.author?.id?.toString() ?: "unknown"
    val userServersMap = getServerMapForUser(userId)

    if (userServersMap.remove(server) != null) {
      // Launch a coroutine to save data asynchronously
      launch {
        saveData()
      }
      event.message.channel.createMessage("Removed server $server from your monitoring list")
    } else {
      event.message.channel.createMessage("Server $server is not in your monitoring list")
    }
  }

  private suspend fun handleListCommand(event: MessageCreateEvent) = coroutineScope {
    val userId = event.message.author?.id?.toString() ?: "unknown"
    val userServersMap = getServerMapForUser(userId)

    if (userServersMap.isEmpty()) {
      event.message.channel.createMessage("You don't have any servers in your monitoring list")
      return@coroutineScope
    }

    // For large server lists, build the string in a background coroutine
    val serverList = if (userServersMap.size > 20) {
      withContext(Dispatchers.Default) {
        userServersMap.entries.joinToString("\n") { (server, status) ->
          "$server: ${if (status) "online" else "offline"}"
        }
      }
    } else {
      // For small lists, just do it directly
      userServersMap.entries.joinToString("\n") { (server, status) ->
        "$server: ${if (status) "online" else "offline"}"
      }
    }

    event.message.channel.createMessage("Your monitored servers:\n$serverList")
  }

  private suspend fun handleHelpCommand(event: MessageCreateEvent) {
    val helpText = """
            **Status Command Help**
            Monitor server status with the following subcommands:

            `status check <server>` - Check if a server is online
            `status add <server>` - Add a server to your personal monitoring list
            `status delete <server>` - Remove a server from your personal monitoring list
            `status list` - List all servers in your personal monitoring list and their status

            Your server list is stored in a database and persists between bot restarts.
            The bot will automatically notify you when a server's status changes.
        """.trimIndent()

    event.message.channel.createMessage(helpText)
  }

  private fun startMonitoring() {
    logger.info("Starting server monitoring job")

    // Use a supervisor scope to prevent individual monitoring cycle failures from stopping the job
    val monitorScope = CoroutineScope(SupervisorJob() + networkDispatcher)

    monitorJob = monitorScope.launch {
      // Use exponential backoff for retries if monitoring fails
      var retryDelay = 5.seconds
      val maxDelay = 60.seconds

      while (isActive) {
        try {
          checkAllServers()
          logger.debug("Server monitoring cycle completed, waiting for next cycle")

          // Reset retry delay on success
          retryDelay = 5.seconds

          // Wait for the next cycle
          delay(30.seconds)
        } catch (e: Exception) {
          // If monitoring fails, log the error and retry with backoff
          logger.error("Error in server monitoring cycle", e)

          // Wait with exponential backoff
          logger.info("Retrying monitoring in $retryDelay")
          delay(retryDelay)

          // Increase retry delay for next failure, up to max
          retryDelay = (retryDelay * 2).coerceAtMost(maxDelay)
        }
      }
    }

    logger.info("Server monitoring job started")
  }

  private suspend fun checkAllServers() = coroutineScope {
    // Calculate total server count for logging
    val totalServerCount = userServers.values.sumOf { it.size }

    if (totalServerCount > 0) {
      logger.debug("Checking status of $totalServerCount servers across ${userServers.size} users")
    }

    // Track if we need to save data
    var needToSaveData = false

    // Process each user's servers in parallel
    val userChecks = userServers.map { (userId, serverMap) ->
      async {
        // Process each server for this user
        val statusChanges = mutableListOf<Triple<String, Boolean, Boolean>>() // server, oldStatus, newStatus

        // Check all servers for this user in parallel with a concurrency limit
        val serverChecks = serverMap.entries
          .chunked(10) // Process in chunks of 10 servers at a time to limit concurrency
          .flatMap { chunk ->
            chunk.map { (server, previousStatus) ->
              async {
                try {
                  // Check server status
                  val currentStatus = checkServerStatus(server)

                  // If status changed, record it
                  if (currentStatus != previousStatus) {
                    logger.info("Server $server status changed from ${if (previousStatus) "online" else "offline"} to ${if (currentStatus) "online" else "offline"}")

                    // Update the status in the original map
                    serverMap[server] = currentStatus

                    // Record the status change for notification
                    statusChanges.add(Triple(server, previousStatus, currentStatus))
                  }
                } catch (e: Exception) {
                  logger.error("Error checking server $server for user $userId", e)
                }
              }
            }
          }

        // Wait for all server checks to complete
        serverChecks.awaitAll()

        // If there were any status changes, we need to save data
        if (statusChanges.isNotEmpty()) {
          needToSaveData = true

          // Notify about all status changes
          statusChanges.forEach { (server, _, currentStatus) ->
            notifyStatusChange(server, currentStatus, userId)
          }
        }
      }
    }

    // Wait for all user checks to complete
    userChecks.awaitAll()

    // Save data once at the end if needed
    if (needToSaveData) {
      saveData()
    }
  }

  private suspend fun notifyStatusChange(server: String, isOnline: Boolean, userId: String) {
    // For now, we'll just use the global notification channel
    // In the future, we could enhance this to use the specific channel where the server was added
    val channel = notificationChannel

    if (channel == null) {
      logger.warn("Cannot notify status change: notification channel not set")
      return
    }

    val statusMessage = if (isOnline) {
      "<@$userId> Server $server is now online"
    } else {
      "<@$userId> Server $server is now offline"
    }

    logger.info("Sending status change notification for server $server: ${if (isOnline) "online" else "offline"} to user $userId")
    try {
      channel.createMessage(statusMessage)
      logger.debug("Status change notification sent successfully")
    } catch (e: Exception) {
      logger.error("Failed to send status change notification for server $server", e)
    }
  }

  /**
   * Checks the status of a server with caching to reduce network calls
   *
   * @param server The server address to check
   * @return true if the server is online, false otherwise
   */
  private suspend fun checkServerStatus(server: String): Boolean {
    val normalizedServer = ServerUtils.normalizeServerAddress(server)
    val currentTime = System.currentTimeMillis()

    // Check cache first
    val cachedStatus = serverStatusCache[normalizedServer]
    if (cachedStatus != null) {
      val (status, timestamp) = cachedStatus
      // If cache is still valid, return cached result
      if (currentTime - timestamp < CACHE_TTL) {
        logger.debug("Using cached status for server: $normalizedServer (${if (status) "online" else "offline"})")
        return status
      }
    }

    // Cache miss or expired, perform actual check
    logger.debug("Cache miss for server: $normalizedServer, performing network check")
    return withContext(networkDispatcher) {
      val status = NetworkUtils.checkServerStatus(normalizedServer)
      // Update cache with new result
      serverStatusCache[normalizedServer] = Pair(status, currentTime)
      status
    }
  }

  /**
   * Checks if a domain is likely a web domain (vs. a plain IP or service)
   * Delegates to ServerUtils
   */
  private fun isLikelyWebDomain(server: String): Boolean {
    return ServerUtils.isLikelyWebDomain(server)
  }

  // This method has been removed as it duplicates functionality in NetworkUtils.checkServerStatus
  // The checkServerStatus method above now delegates to NetworkUtils.checkServerStatus

  /**
   * Checks if an IP address is blacklisted
   * Currently blacklists all IPs starting with "100."
   */
  private fun isIpBlacklisted(server: String): Boolean {
    return ServerUtils.isIpBlacklisted(server)
  }

  /**
   * Normalizes a server address by extracting just the hostname/domain part
   * Delegates to ServerUtils
   */
  private fun normalizeServerAddress(server: String): String {
    return ServerUtils.normalizeServerAddress(server)
  }
}
