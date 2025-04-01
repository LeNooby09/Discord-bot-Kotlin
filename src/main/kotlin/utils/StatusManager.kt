package utils

import database.BotCustomizationManager
import dev.kord.common.entity.PresenceStatus
import dev.kord.core.Kord
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

/**
 * Manages the bot's status, including periodic updates with custom statuses.
 */
class StatusManager private constructor() {
	private val logger = LoggerFactory.getLogger(StatusManager::class.java)
	private var statusJob: Job? = null
	private var currentPresenceStatus: PresenceStatus = PresenceStatus.Online

	/**
	 * Sets the initial default status for the bot.
	 * @param kord The Kord instance
	 */
	suspend fun setInitialStatus(kord: Kord) {
		logger.info("Setting initial bot status")
		kord.editPresence {
			status = PresenceStatus.Online
			playing("Type !help for commands")
		}
		currentPresenceStatus = PresenceStatus.Online
	}

	/**
	 * Sets the bot's presence status.
	 * @param kord The Kord instance
	 * @param presenceStatus The presence status to set (online, idle, dnd, invisible)
	 * @return true if the status was set successfully, false otherwise
	 */
	private suspend fun setPresenceStatus(kord: Kord, presenceStatus: PresenceStatus): Boolean {
		try {
			logger.info("Setting bot presence status to $presenceStatus")
			kord.editPresence {
				status = presenceStatus
			}
			currentPresenceStatus = presenceStatus
			return true
		} catch (e: Exception) {
			logger.error("Error setting bot presence status", e)
			return false
		}
	}

	/**
	 * Converts a string status type to a PresenceStatus enum value and sets the bot's presence status.
	 * @param kord The Kord instance
	 * @param statusType The status type as a string (online, idle, dnd, invisible)
	 * @return A pair containing a boolean indicating success and a string message
	 */
	suspend fun setPresenceStatusFromString(kord: Kord, statusType: String): Pair<Boolean, String> {
		// Convert the status type to a PresenceStatus
		val presenceStatus = when (statusType.lowercase()) {
			"online" -> PresenceStatus.Online
			"idle" -> PresenceStatus.Idle
			"dnd" -> PresenceStatus.DoNotDisturb
			"invisible" -> PresenceStatus.Invisible
			else -> {
				return Pair(false, "Invalid presence status. Valid values: online, idle, dnd, invisible")
			}
		}

		// Set the bot's presence status
		val success = setPresenceStatus(kord, presenceStatus)

		return if (success) {
			Pair(true, "Bot presence status set to $statusType")
		} else {
			Pair(false, "Failed to set bot presence status. Please try again later.")
		}
	}

	/**
	 * Sets a custom status directly with the provided text and type.
	 * @param kord The Kord instance
	 * @param statusText The text of the custom status
	 * @param statusType The type of the status (playing, watching, listening, competing)
	 * @return A pair containing a boolean indicating success and a string message
	 */
	suspend fun setCustomStatus(kord: Kord, statusText: String, statusType: String): Pair<Boolean, String> {
		try {
			logger.info("Setting custom status: $statusType $statusText")

			// Validate status type
			val validType = when (statusType.lowercase()) {
				"playing", "watching", "listening", "competing" -> true
				else -> false
			}

			if (!validType) {
				return Pair(false, "Invalid status type. Valid types are: playing, watching, listening, competing")
			}

			// Update the bot's status based on the type
			when (statusType.lowercase()) {
				"playing" -> kord.editPresence {
					status = currentPresenceStatus
					playing(statusText)
				}

				"watching" -> kord.editPresence {
					status = currentPresenceStatus
					watching(statusText)
				}

				"listening" -> kord.editPresence {
					status = currentPresenceStatus
					listening(statusText)
				}

				"competing" -> kord.editPresence {
					status = currentPresenceStatus
					competing(statusText)
				}
			}

			return Pair(true, "Custom status set to: $statusType $statusText")
		} catch (e: Exception) {
			logger.error("Error setting custom status", e)
			return Pair(false, "Failed to set custom status. Please try again later.")
		}
	}

	/**
	 * Sets a custom status by ID from the database.
	 * @param kord The Kord instance
	 * @param statusId The ID of the status to set
	 * @return A pair containing a boolean indicating success and a string message
	 */
	suspend fun setCustomStatusById(kord: Kord, statusId: Long): Pair<Boolean, String> {
		try {
			logger.info("Setting custom status with ID $statusId")

			// Get the custom status from the database
			val botCustomizationManager = BotCustomizationManager.getInstance()
			val customStatus = botCustomizationManager.getCustomStatusById(statusId)
				?: return Pair(false, "No custom status found with ID $statusId")

			val (statusText, statusType) = customStatus

			// Set the custom status
			return setCustomStatus(kord, statusText, statusType)
		} catch (e: Exception) {
			logger.error("Error setting custom status by ID", e)
			return Pair(false, "Failed to set custom status. Please try again later.")
		}
	}

	/**
	 * Starts a coroutine to periodically update the bot's status.
	 * @param kord The Kord instance
	 * @param scope The coroutine scope to launch in
	 */
	fun startStatusUpdates(kord: Kord, scope: CoroutineScope) {
		logger.info("Starting periodic status updates")

		// Cancel any existing job
		statusJob?.cancel()

		// Launch a new coroutine to periodically update the status
		statusJob = scope.launch {
			while (isActive) {
				try {
					// Get a random custom status
					val botCustomizationManager = BotCustomizationManager.getInstance()
					val customStatus = botCustomizationManager.getRandomCustomStatus()

					if (customStatus != null) {
						val (statusText, statusType) = customStatus
						logger.debug("Setting custom status: $statusType $statusText")

						// Update the bot's status based on the type
						when (statusType.lowercase()) {
							"playing" -> kord.editPresence {
								status = currentPresenceStatus
								playing(statusText)
							}

							"watching" -> kord.editPresence {
								status = currentPresenceStatus
								watching(statusText)
							}

							"listening" -> kord.editPresence {
								status = currentPresenceStatus
								listening(statusText)
							}

							"competing" -> kord.editPresence {
								status = currentPresenceStatus
								competing(statusText)
							}

							else -> kord.editPresence {
								status = currentPresenceStatus
								playing(statusText)
							}
						}
					}

					// Wait before changing the status again (30 minutes)
					delay(30.minutes)
				} catch (e: Exception) {
					logger.error("Error updating bot status", e)
					delay(5.minutes) // Wait a bit before trying again
				}
			}
		}
	}

	/**
	 * Stops the periodic status updates.
	 */
	fun stopStatusUpdates() {
		logger.info("Stopping periodic status updates")
		statusJob?.cancel()
		statusJob = null
	}

	companion object {
		private var instance: StatusManager? = null

		/**
		 * Gets the singleton instance of the StatusManager.
		 * @return The StatusManager instance
		 */
		fun getInstance(): StatusManager {
			if (instance == null) {
				instance = StatusManager()
			}
			return instance!!
		}
	}
}
