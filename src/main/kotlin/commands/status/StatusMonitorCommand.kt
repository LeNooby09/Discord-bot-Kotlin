package commands.status

import dev.kord.core.event.message.MessageCreateEvent
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.seconds

/**
 * Command for status monitoring functionality.
 * Handles background monitoring of servers and notifications on status changes.
 */
class StatusMonitorCommand : commands.Command {
	override val name = "statusmonitor"
	override val description = "Status monitoring commands"

	// Reference to the data manager and check command
	private val dataManager = StatusDataCommand.getInstance()
	private val checkCommand = StatusCheckCommand.getInstance()

	// Background job for monitoring servers
	private var monitorJob: Job? = null

	// Channel to send notifications to
	private var notificationChannel: dev.kord.core.behavior.channel.MessageChannelBehavior? = null

	init {
		// Start the background monitoring job
		startMonitoring()
	}

	override suspend fun execute(event: MessageCreateEvent): Boolean {
		logger.info("Executing status monitor command")

		val messageText = extractMessageText(event)
		val args = messageText.split(" ")

		if (args.isEmpty() || args[0].isEmpty()) {
			event.message.channel.createMessage("Please specify a subcommand: start, stop, or status")
			return false
		}

		// Set the notification channel to the current channel
		notificationChannel = event.message.channel

		val subcommand = args[0].lowercase()

		return when (subcommand) {
			"start" -> {
				if (monitorJob?.isActive == true) {
					event.message.channel.createMessage("Monitoring is already running")
				} else {
					startMonitoring()
					event.message.channel.createMessage("Server status monitoring started")
				}
				true
			}

			"stop" -> {
				stopMonitoring()
				event.message.channel.createMessage("Server status monitoring stopped")
				true
			}

			"status" -> {
				val status = if (monitorJob?.isActive == true) "running" else "stopped"
				event.message.channel.createMessage("Server status monitoring is currently $status")
				true
			}

			else -> {
				event.message.channel.createMessage("Unknown subcommand: ${args[0]}. Available subcommands: start, stop, status")
				false
			}
		}
	}

	/**
	 * Starts the server monitoring job
	 */
	fun startMonitoring() {
		logger.info("Starting server monitoring job")

		// Use a supervisor scope to prevent individual monitoring cycle failures from stopping the job
		val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

	/**
	 * Stops the server monitoring job
	 */
	fun stopMonitoring() {
		logger.info("Stopping server monitoring job")
		monitorJob?.cancel()
		logger.info("Server monitoring job stopped")
	}

	/**
	 * Checks the status of all servers for all users
	 */
	private suspend fun checkAllServers() = coroutineScope {
		// Get all user servers from the data manager
		val userServers = dataManager.getAllUserServers()

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
									val currentStatus = checkCommand.checkServerStatus(server)

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
			dataManager.saveData()
		}
	}

	/**
	 * Notifies a user about a server status change
	 */
	private suspend fun notifyStatusChange(server: String, isOnline: Boolean, userId: String) {
		// For now, we'll just use the global notification channel
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

	companion object {
		private var instance: StatusMonitorCommand? = null

		/**
		 * Gets the singleton instance of the StatusMonitorCommand.
		 * @return The StatusMonitorCommand instance
		 */
		@Synchronized
		fun getInstance(): StatusMonitorCommand {
			if (instance == null) {
				instance = StatusMonitorCommand()
			}
			return instance!!
		}
	}
}
