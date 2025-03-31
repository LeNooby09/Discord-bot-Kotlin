package utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException

/**
 * Utility object for logging throughout the application.
 * Provides a consistent way to get SLF4J loggers.
 */
object Logger {
	/**
	 * Gets a logger for the specified class.
	 *
	 * @param clazz The class to get a logger for
	 * @return A SLF4J Logger instance
	 */
	fun getLogger(clazz: Class<*>): Logger {
		return LoggerFactory.getLogger(clazz)
	}

	/**
	 * Gets a logger for the specified name.
	 *
	 * @param name The name to get a logger for
	 * @return A SLF4J Logger instance
	 */
	fun getLogger(name: String): Logger {
		return LoggerFactory.getLogger(name)
	}

	/**
	 * Common logging patterns for database operations
	 */
	object Database {
		/**
		 * Logs a database connection error and handles rollback if needed
		 *
		 * @param logger The logger to use
		 * @param operation The operation that failed
		 * @param e The exception that occurred
		 * @param connection The database connection (can be null)
		 */
		fun logError(logger: Logger, operation: String, e: SQLException, connection: Connection?) {
			logger.error("Error $operation", e)
			try {
				connection?.rollback()
				connection?.autoCommit = true
			} catch (rollbackEx: SQLException) {
				logger.error("Error rolling back transaction", rollbackEx)
			}
		}

		/**
		 * Logs a null connection error
		 *
		 * @param logger The logger to use
		 * @param operation The operation that failed
		 */
		fun logNullConnection(logger: Logger, operation: String) {
			logger.error("Database connection is null, cannot $operation")
		}
	}

	/**
	 * Common logging patterns for network operations
	 */
	object Network {
		/**
		 * Logs the result of a server status check
		 *
		 * @param logger The logger to use
		 * @param server The server that was checked
		 * @param isOnline Whether the server is online
		 * @param method The method used to check (e.g., "HTTP", "HTTPS", "ping")
		 */
		fun logServerStatus(logger: Logger, server: String, isOnline: Boolean, method: String) {
			logger.debug("$method check for $server result: ${if (isOnline) "online" else "offline"}")
		}

		/**
		 * Logs an exception during a network operation
		 *
		 * @param logger The logger to use
		 * @param operation The operation that failed
		 * @param target The target of the operation
		 * @param e The exception that occurred
		 */
		fun logException(logger: Logger, operation: String, target: String, e: Exception) {
			logger.debug("Exception during $operation to $target: ${e.message}")
		}
	}
}

/**
 * Extension function to get a logger for a class.
 * Usage: `private val logger = logger()`
 *
 * @return A SLF4J Logger instance
 */
inline fun <reified T> T.logger(): Logger {
	return LoggerFactory.getLogger(T::class.java)
}
