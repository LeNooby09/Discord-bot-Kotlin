package utils.initializers

import dev.kord.core.Kord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.slf4j.LoggerFactory
import utils.StatusManager

/**
 * Handles initialization of the bot's status management.
 */
object StatusInitializer {
	private val logger = LoggerFactory.getLogger(StatusInitializer::class.java)

	/**
	 * Initializes the status manager and starts periodic status updates.
	 * @param kord The Kord instance
	 */
	suspend fun initializeStatus(kord: Kord) {
		logger.info("Initializing status manager")

		// Get the status manager instance
		val statusManager = StatusManager.getInstance()

		// Set the initial default status
		statusManager.setInitialStatus(kord)

		// Start periodic status updates
		statusManager.startStatusUpdates(kord, CoroutineScope(Dispatchers.Default))

		logger.info("Status manager initialized")
	}
}
