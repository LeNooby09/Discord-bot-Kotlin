package database

import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException

/**
 * Manages bot customization operations.
 * Handles bot-specific settings like custom statuses.
 */
class BotCustomizationManager private constructor() {
	private val logger = LoggerFactory.getLogger(BotCustomizationManager::class.java)
	private var connection: Connection? = null

	/**
	 * Initializes the BotCustomizationManager with a database connection.
	 * @param dbConnection The database connection to use
	 */
	fun initialize(dbConnection: Connection?) {
		connection = dbConnection
		logger.info("BotCustomizationManager initialized")
	}

	/**
	 * Creates the necessary tables for bot customization if they don't exist.
	 */
	fun createTables() {
		logger.debug("Creating bot customization tables if they don't exist")
		val statement = connection?.createStatement()

		// Create the custom_statuses table
		statement?.execute(
			"""
            CREATE TABLE IF NOT EXISTS custom_statuses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                status_text TEXT NOT NULL,
                status_type TEXT NOT NULL
            )
            """
		)

		statement?.close()
		logger.debug("Bot customization tables created successfully")
	}

	/**
	 * Flushes all bot customization data.
	 * @return True if the data was flushed successfully, false otherwise
	 */
	fun flushData(): Boolean {
		try {
			logger.info("Flushing bot customization data")
			connection?.let { conn ->
				val statement = conn.createStatement()

				// Delete custom statuses
				statement.executeUpdate("DELETE FROM custom_statuses")
				logger.debug("Custom statuses deleted")

				statement.close()
				return true
			} ?: run {
				utils.Logger.Database.logNullConnection(logger, "flush bot customization data")
				return false
			}
		} catch (e: SQLException) {
			utils.Logger.Database.logError(logger, "flushing bot customization data", e, connection)
			return false
		}
	}

	/**
	 * Adds a custom status to the database.
	 * @param statusText The text of the custom status
	 * @param statusType The type of the status (e.g., "playing", "watching", "listening")
	 * @return The ID of the newly added status, or -1 if the operation failed
	 */
	fun addCustomStatus(statusText: String, statusType: String): Long {
		try {
			logger.debug("Adding custom status: $statusText (type: $statusType)")
			connection?.let { conn ->
				val statement = conn.prepareStatement(
					"INSERT INTO custom_statuses (status_text, status_type) VALUES (?, ?)",
					java.sql.Statement.RETURN_GENERATED_KEYS
				)
				statement.setString(1, statusText)
				statement.setString(2, statusType)
				statement.executeUpdate()

				// Get the generated ID
				val generatedKeys = statement.generatedKeys
				val statusId = if (generatedKeys.next()) {
					generatedKeys.getLong(1)
				} else {
					-1L
				}

				generatedKeys.close()
				statement.close()

				logger.info("Added custom status with ID $statusId: $statusText (type: $statusType)")
				return statusId
			} ?: run {
				utils.Logger.Database.logNullConnection(logger, "add custom status")
				return -1L
			}
		} catch (e: SQLException) {
			utils.Logger.Database.logError(logger, "adding custom status", e, connection)
			return -1L
		}
	}

	/**
	 * Gets all custom statuses from the database.
	 * @return A list of custom statuses as triples (id, text, type)
	 */
	fun getAllCustomStatuses(): List<Triple<Long, String, String>> {
		try {
			logger.debug("Getting all custom statuses")
			connection?.let { conn ->
				val statement = conn.prepareStatement("SELECT id, status_text, status_type FROM custom_statuses")
				val resultSet = statement.executeQuery()

				val statuses = mutableListOf<Triple<Long, String, String>>()
				while (resultSet.next()) {
					val id = resultSet.getLong("id")
					val text = resultSet.getString("status_text")
					val type = resultSet.getString("status_type")
					statuses.add(Triple(id, text, type))
				}

				resultSet.close()
				statement.close()

				logger.debug("Found ${statuses.size} custom statuses")
				return statuses
			} ?: run {
				utils.Logger.Database.logNullConnection(logger, "get all custom statuses")
				return emptyList()
			}
		} catch (e: SQLException) {
			utils.Logger.Database.logError(logger, "getting all custom statuses", e, connection)
			return emptyList()
		}
	}

	/**
	 * Removes a custom status from the database.
	 * @param statusId The ID of the status to remove
	 * @return True if the status was removed successfully, false otherwise
	 */
	fun removeCustomStatus(statusId: Long): Boolean {
		try {
			logger.debug("Removing custom status with ID $statusId")
			connection?.let { conn ->
				val statement = conn.prepareStatement("DELETE FROM custom_statuses WHERE id = ?")
				statement.setLong(1, statusId)
				val result = statement.executeUpdate() > 0
				statement.close()

				if (result) {
					logger.info("Custom status with ID $statusId removed successfully")
				} else {
					logger.debug("No custom status found with ID $statusId")
				}

				return result
			} ?: run {
				utils.Logger.Database.logNullConnection(logger, "remove custom status")
				return false
			}
		} catch (e: SQLException) {
			utils.Logger.Database.logError(logger, "removing custom status", e, connection)
			return false
		}
	}

	/**
	 * Gets a random custom status from the database.
	 * @return A pair of (text, type) for the random status, or null if no statuses exist
	 */
	fun getRandomCustomStatus(): Pair<String, String>? {
		try {
			logger.debug("Getting a random custom status")
			connection?.let { conn ->
				val statement =
					conn.prepareStatement("SELECT status_text, status_type FROM custom_statuses ORDER BY RANDOM() LIMIT 1")
				val resultSet = statement.executeQuery()

				if (resultSet.next()) {
					val text = resultSet.getString("status_text")
					val type = resultSet.getString("status_type")
					resultSet.close()
					statement.close()

					logger.debug("Found random custom status: $text (type: $type)")
					return Pair(text, type)
				}

				resultSet.close()
				statement.close()

				logger.debug("No custom statuses found")
				return null
			} ?: run {
				utils.Logger.Database.logNullConnection(logger, "get random custom status")
				return null
			}
		} catch (e: SQLException) {
			utils.Logger.Database.logError(logger, "getting random custom status", e, connection)
			return null
		}
	}

	/**
	 * Gets a custom status by ID from the database.
	 * @param statusId The ID of the status to get
	 * @return A pair of (text, type) for the status, or null if no status with the given ID exists
	 */
	fun getCustomStatusById(statusId: Long): Pair<String, String>? {
		try {
			logger.debug("Getting custom status with ID $statusId")
			connection?.let { conn ->
				val statement =
					conn.prepareStatement("SELECT status_text, status_type FROM custom_statuses WHERE id = ?")
				statement.setLong(1, statusId)
				val resultSet = statement.executeQuery()

				if (resultSet.next()) {
					val text = resultSet.getString("status_text")
					val type = resultSet.getString("status_type")
					resultSet.close()
					statement.close()

					logger.debug("Found custom status with ID $statusId: $text (type: $type)")
					return Pair(text, type)
				}

				resultSet.close()
				statement.close()

				logger.debug("No custom status found with ID $statusId")
				return null
			} ?: run {
				utils.Logger.Database.logNullConnection(logger, "get custom status by ID")
				return null
			}
		} catch (e: SQLException) {
			utils.Logger.Database.logError(logger, "getting custom status by ID", e, connection)
			return null
		}
	}

	companion object {
		private var instance: BotCustomizationManager? = null

		/**
		 * Gets the singleton instance of the BotCustomizationManager.
		 * @return The BotCustomizationManager instance
		 */
		fun getInstance(): BotCustomizationManager {
			if (instance == null) {
				instance = BotCustomizationManager()
			}
			return instance!!
		}
	}
}
