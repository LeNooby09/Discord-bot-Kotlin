package commands.status

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import utils.NetworkUtils
import utils.ServerUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility class for status check functionality.
 * This class is no longer a Command and is only used internally by StatusCommand.
 * The functionality has been extracted to separate handler classes.
 */
class StatusCheckCommand {
	private val logger = LoggerFactory.getLogger(StatusCheckCommand::class.java)

	// Cache for server status to reduce network calls
	private val serverStatusCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()

	// Cache TTL in milliseconds (5 minutes)
	private val CACHE_TTL = 5 * 60 * 1000L

	// Dispatcher for network operations
	private val networkDispatcher = Dispatchers.IO.limitedParallelism(10)

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
