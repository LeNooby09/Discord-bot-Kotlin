package utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
