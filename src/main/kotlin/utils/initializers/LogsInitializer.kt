package utils.initializers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Handles initialization of the logs directory.
 */
object LogsInitializer {
	private val logger = LoggerFactory.getLogger(LogsInitializer::class.java)

	/**
	 * Creates the logs directory if it doesn't exist.
	 */
	suspend fun createLogsDirectory() {
		try {
			val logsPath = Paths.get("logs")
			if (!Files.exists(logsPath)) {
				withContext(Dispatchers.IO) {
					Files.createDirectories(logsPath)
				}
				logger.info("Created logs directory")
			}
		} catch (e: IOException) {
			logger.warn("Failed to create logs directory: ${e.message}")
		}
	}
}
