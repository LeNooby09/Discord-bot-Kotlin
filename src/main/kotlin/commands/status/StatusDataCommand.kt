package commands.status

import database.DatabaseManager
import dev.kord.core.event.message.MessageCreateEvent
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Command for status data management functionality.
 * Handles loading and saving server status data.
 */
class StatusDataCommand : commands.Command {
	override val name = "statusdata"
	override val description = "Status data management commands"

	// Store servers and their last known status per user
	// Map<UserId, Map<ServerAddress, IsOnline>>
	private val userServers = ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>>()

	// Database manager for storing and retrieving data
	private val dbManager = DatabaseManager.getInstance()

	// Track the last save time to implement simple debouncing
	private var lastSaveTime = 0L
	private var pendingSaveJob: Job? = null

	init {
		// Load saved data
		loadData()
	}

	override suspend fun execute(event: MessageCreateEvent): Boolean {
		logger.info("Executing status data command")

		val messageText = extractMessageText(event)
		val args = messageText.split(" ")

		if (args.isEmpty() || args[0].isEmpty()) {
			event.message.channel.createMessage("Please specify a subcommand: load or save")
			return false
		}

		val subcommand = args[0].lowercase()

		return when (subcommand) {
			"load" -> {
				loadData()
				event.message.channel.createMessage("Status data loaded from database")
				true
			}

			"save" -> {
				saveData()
				event.message.channel.createMessage("Status data saved to database")
				true
			}

			else -> {
				event.message.channel.createMessage("Unknown subcommand: ${args[0]}. Available subcommands: load, save")
				false
			}
		}
	}

	/**
	 * Loads user server data from the database
	 */
	fun loadData() {
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

	/**
	 * Saves user server data to the database with simple debouncing
	 * This method is optimized to reduce database load by:
	 * 1. Debouncing rapid save requests
	 * 2. Using coroutines for asynchronous saving
	 */
	fun saveData() {
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
				lastSaveTime = System.currentTimeMillis()

				// Create a snapshot of the data to save to avoid concurrent modification
				val dataToSave = HashMap<String, Map<String, Boolean>>()
				userServers.forEach { (userId, serverMap) ->
					dataToSave[userId] = HashMap(serverMap)
				}

				// Log once before saving all data
				logger.info("Saving server data for ${dataToSave.size} users to database")

				// Track any errors
				val errors = mutableListOf<String>()

				// Save each user's server data to the database
				dataToSave.forEach { (userId, serverMap) ->
					try {
						dbManager.saveServerStatus(userId, serverMap)
					} catch (e: Exception) {
						errors.add(userId)
						logger.error("Error saving data for user $userId", e)
					}
				}

				// Log success or partial success
				if (errors.isEmpty()) {
					logger.info("Server data for all users saved successfully")
				} else {
					logger.warn("Server data saved with errors for ${errors.size} users")
				}
			} catch (e: Exception) {
				logger.error("Error in save operation", e)
			}
		}
	}

	/**
	 * Gets the server map for a user, loading it from the database if it doesn't exist in memory
	 */
	fun getServerMapForUser(userId: String): ConcurrentHashMap<String, Boolean> {
		return userServers.computeIfAbsent(userId) {
			// If not in memory, try to load from database
			logger.debug("Loading server map for user $userId from database")
			dbManager.loadServerStatus(userId)
		}
	}

	/**
	 * Gets all user servers
	 * @return A map of user IDs to maps of servers and their status
	 */
	fun getAllUserServers(): Map<String, ConcurrentHashMap<String, Boolean>> {
		return userServers
	}

	companion object {
		private var instance: StatusDataCommand? = null

		/**
		 * Gets the singleton instance of the StatusDataCommand.
		 * @return The StatusDataCommand instance
		 */
		@Synchronized
		fun getInstance(): StatusDataCommand {
			if (instance == null) {
				instance = StatusDataCommand()
			}
			return instance!!
		}
	}
}
