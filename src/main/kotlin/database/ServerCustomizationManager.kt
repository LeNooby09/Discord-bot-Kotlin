package database

import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages server customization operations for the bot.
 * Handles server-specific settings like custom prefixes.
 */
class ServerCustomizationManager private constructor() {
	private val logger = LoggerFactory.getLogger(ServerCustomizationManager::class.java)
	private var connection: Connection? = null

	// Cache for server prefixes to reduce database reads
	private val prefixCache = ConcurrentHashMap<String, String>()

	/**
	 * Initializes the ServerCustomizationManager with a database connection.
	 * @param dbConnection The database connection to use
	 */
	fun initialize(dbConnection: Connection?) {
		connection = dbConnection
		logger.info("ServerCustomizationManager initialized")
	}

	/**
	 * Creates the necessary tables for server customization if they don't exist.
	 */
	fun createTables() {
		logger.debug("Creating server customization tables if they don't exist")
		val statement = connection?.createStatement()

		// Create the server_prefixes table
		statement?.execute(
			"""
            CREATE TABLE IF NOT EXISTS server_prefixes (
                server_id TEXT PRIMARY KEY,
                prefix TEXT NOT NULL
            )
            """
		)

		statement?.close()
		logger.debug("Server customization tables created successfully")
	}

	/**
	 * Gets the prefix for a specific server.
	 * @param serverId The ID of the server
	 * @return The prefix for the server, or "!" if none is set
	 */
	fun getServerPrefix(serverId: String): String {
		// Check cache first
		val cachedPrefix = prefixCache[serverId]
		if (cachedPrefix != null) {
			logger.debug("Cache hit for server prefix $serverId: $cachedPrefix")
			return cachedPrefix
		}

		try {
			logger.debug("Cache miss for server prefix $serverId, querying database")
			connection?.let { conn ->
				val statement = conn.prepareStatement("SELECT prefix FROM server_prefixes WHERE server_id = ?")
				statement.setString(1, serverId)
				val resultSet = statement.executeQuery()

				if (resultSet.next()) {
					val prefix = resultSet.getString("prefix")
					resultSet.close()
					statement.close()
					logger.debug("Found prefix for server $serverId: $prefix")

					// Cache the result
					prefixCache[serverId] = prefix
					return prefix
				}

				resultSet.close()
				statement.close()
				logger.debug("No prefix found for server $serverId, using default")

				// Cache the default prefix
				prefixCache[serverId] = "!"
				return "!" // Default prefix
			} ?: run {
				utils.Logger.Database.logNullConnection(logger, "get server prefix")
				return "!" // Default prefix
			}
		} catch (e: SQLException) {
			utils.Logger.Database.logError(logger, "getting server prefix", e, connection)
			return "!" // Default prefix
		}
	}

	/**
	 * Sets the prefix for a specific server.
	 * @param serverId The ID of the server
	 * @param prefix The prefix to set
	 * @return True if the prefix was set successfully, false otherwise
	 */
	fun setServerPrefix(serverId: String, prefix: String): Boolean {
		try {
			logger.debug("Setting prefix for server $serverId to $prefix")
			connection?.let { conn ->
				// Use a transaction for better consistency
				conn.autoCommit = false

				// Delete existing prefix for this server
				val deleteStatement = conn.prepareStatement("DELETE FROM server_prefixes WHERE server_id = ?")
				deleteStatement.setString(1, serverId)
				deleteStatement.executeUpdate()
				deleteStatement.close()

				// Insert new prefix
				val insertStatement = conn.prepareStatement("INSERT INTO server_prefixes (server_id, prefix) VALUES (?, ?)")
				insertStatement.setString(1, serverId)
				insertStatement.setString(2, prefix)
				insertStatement.executeUpdate()
				insertStatement.close()

				conn.commit()
				conn.autoCommit = true

				// Update the cache
				prefixCache[serverId] = prefix

				logger.info("Prefix for server $serverId set to $prefix")
				return true
			} ?: run {
				utils.Logger.Database.logNullConnection(logger, "set server prefix")
				return false
			}
		} catch (e: SQLException) {
			utils.Logger.Database.logError(logger, "setting server prefix", e, connection)
			return false
		}
	}

	/**
	 * Removes the prefix for a specific server, resetting it to the default.
	 * @param serverId The ID of the server
	 * @return True if the prefix was removed successfully, false otherwise
	 */
	fun removeServerPrefix(serverId: String): Boolean {
		try {
			logger.debug("Removing prefix for server $serverId")
			connection?.let { conn ->
				val statement = conn.prepareStatement("DELETE FROM server_prefixes WHERE server_id = ?")
				statement.setString(1, serverId)
				val result = statement.executeUpdate() > 0
				statement.close()

				// Update the cache with the default prefix
				prefixCache[serverId] = "!"

				if (result) {
					logger.info("Prefix for server $serverId removed successfully")
				} else {
					logger.debug("No prefix found to remove for server $serverId")
				}

				return true
			} ?: run {
				utils.Logger.Database.logNullConnection(logger, "remove server prefix")
				return false
			}
		} catch (e: SQLException) {
			utils.Logger.Database.logError(logger, "removing server prefix", e, connection)
			return false
		}
	}

	/**
	 * Flushes all server customization data.
	 * @return True if the data was flushed successfully, false otherwise
	 */
	fun flushData(): Boolean {
		try {
			logger.info("Flushing server customization data")
			connection?.let { conn ->
				val statement = conn.createStatement()
				statement.executeUpdate("DELETE FROM server_prefixes")
				logger.debug("Server prefixes deleted")

				// Clear the prefix cache
				prefixCache.clear()
				logger.debug("Server prefix cache cleared")

				statement.close()
				return true
			} ?: run {
				utils.Logger.Database.logNullConnection(logger, "flush server customization data")
				return false
			}
		} catch (e: SQLException) {
			utils.Logger.Database.logError(logger, "flushing server customization data", e, connection)
			return false
		}
	}


	companion object {
		private var instance: ServerCustomizationManager? = null

		/**
		 * Gets the singleton instance of the ServerCustomizationManager.
		 * @return The ServerCustomizationManager instance
		 */
		fun getInstance(): ServerCustomizationManager {
			if (instance == null) {
				instance = ServerCustomizationManager()
			}
			return instance!!
		}
	}
}
