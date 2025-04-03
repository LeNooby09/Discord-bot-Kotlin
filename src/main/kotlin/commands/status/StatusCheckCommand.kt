package commands.status

import dev.kord.core.event.message.MessageCreateEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import utils.NetworkUtils
import utils.ServerUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * Command for status check functionality.
 * Allows users to check server status, add/remove servers, and list monitored servers.
 */
class StatusCheckCommand : commands.Command {
	override val name = "statuscheck"
	override val description = "Status check commands for monitoring servers"

	// Store channel ID for notifications
	private var notificationChannel: dev.kord.core.behavior.channel.MessageChannelBehavior? = null

	// Cache for server status to reduce network calls
	private val serverStatusCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()

	// Cache TTL in milliseconds (5 minutes)
	private val CACHE_TTL = 5 * 60 * 1000L

	// Dispatcher for network operations
	private val networkDispatcher = Dispatchers.IO.limitedParallelism(10)

	// Reference to the data manager
	private val dataManager = StatusDataCommand.getInstance()

	override suspend fun execute(event: MessageCreateEvent): Boolean {
		val messageText = extractMessageText(event)

		val username = event.message.author?.username ?: "Unknown"
		val userId = event.message.author?.id?.toString() ?: "unknown"

		// Parse the subcommand and arguments
		val parts = messageText.split(" ")
		val subcommand = if (parts.isNotEmpty()) parts[0].lowercase() else "help"

		// Single consolidated log message with all relevant information
		logger.info("Executing status check command: $subcommand ${parts.drop(1)} for user: $username (ID: $userId)")

		// Set the notification channel to the current channel if not already set
		if (notificationChannel == null) {
			notificationChannel = event.message.channel
		}

		when (subcommand) {
			"check" -> handleCheckCommand(event, parts.drop(1))
			"add" -> handleAddCommand(event, parts.drop(1))
			"delete", "remove" -> handleDeleteCommand(event, parts.drop(1))
			"list" -> handleListCommand(event)
			else -> handleHelpCommand(event)
		}

		return true
	}

	private suspend fun handleCheckCommand(event: MessageCreateEvent, args: List<String>) = coroutineScope {
		if (args.isEmpty()) {
			event.message.channel.createMessage("Please specify a server to check. Usage: statuscheck check <server>")
			return@coroutineScope
		}

		val server = args[0]
		logger.info("Checking status of server: $server")

		// Check if the server is in the IP blacklist
		if (ServerUtils.isIpBlacklisted(server)) {
			logger.warn("Attempted to check blacklisted IP: $server")
			event.message.channel.createMessage("This IP address is blacklisted and cannot be checked.")
			return@coroutineScope
		}

		// Send initial message indicating check is in progress
		event.message.channel.createMessage("Checking server $server status...")

		// Store the channel for later use
		val channel = event.message.channel

		// Use async to perform the check without blocking the command response
		// This provides structured concurrency (will be properly cancelled if needed)
		launch(networkDispatcher) {
			try {
				// Perform the server check
				val isOnline = checkServerStatus(server)

				// Create the status message
				val statusMessage = if (isOnline) {
					"Server $server is online"
				} else {
					"Server $server is offline"
				}

				// Log the result
				logger.info("Server $server status check result: ${if (isOnline) "online" else "offline"}")

				// Send the result as a new message
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
			event.message.channel.createMessage("Please specify a server to add. Usage: statuscheck add <server>")
			return@coroutineScope
		}

		val server = args[0]
		val userId = event.message.author?.id?.toString() ?: "unknown"

		// Check if the server is in the IP blacklist
		if (ServerUtils.isIpBlacklisted(server)) {
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
				val userServersMap = dataManager.getServerMapForUser(userId)
				userServersMap[server] = status

				// Save the updated data
				dataManager.saveData()

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
			event.message.channel.createMessage("Please specify a server to delete. Usage: statuscheck delete <server>")
			return@coroutineScope
		}

		val server = args[0]
		val userId = event.message.author?.id?.toString() ?: "unknown"
		val userServersMap = dataManager.getServerMapForUser(userId)

		if (userServersMap.remove(server) != null) {
			// Launch a coroutine to save data asynchronously
			launch {
				dataManager.saveData()
			}
			event.message.channel.createMessage("Removed server $server from your monitoring list")
		} else {
			event.message.channel.createMessage("Server $server is not in your monitoring list")
		}
	}

	private suspend fun handleListCommand(event: MessageCreateEvent) = coroutineScope {
		val userId = event.message.author?.id?.toString() ?: "unknown"
		val userServersMap = dataManager.getServerMapForUser(userId)

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
            **Status Check Command Help**
            Monitor server status with the following subcommands:
            
            `statuscheck check <server>` - Check if a server is online
            `statuscheck add <server>` - Add a server to your personal monitoring list
            `statuscheck delete <server>` - Remove a server from your personal monitoring list
            `statuscheck list` - List all servers in your personal monitoring list and their status
            
            Your server list is stored in a database and persists between bot restarts.
            The bot will automatically notify you when a server's status changes.
        """.trimIndent()

		event.message.channel.createMessage(helpText)
	}

	/**
	 * Checks the status of a server with caching to reduce network calls
	 *
	 * @param server The server address to check
	 * @return true if the server is online, false otherwise
	 */
	suspend fun checkServerStatus(server: String): Boolean {
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

	companion object {
		private var instance: StatusCheckCommand? = null

		/**
		 * Gets the singleton instance of the StatusCheckCommand.
		 * @return The StatusCheckCommand instance
		 */
		@Synchronized
		fun getInstance(): StatusCheckCommand {
			if (instance == null) {
				instance = StatusCheckCommand()
			}
			return instance!!
		}
	}
}
