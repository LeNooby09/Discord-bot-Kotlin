package database.managers

import org.slf4j.LoggerFactory
import utils.Logger
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages server status data in the database.
 */
class ServerStatusDatabaseManager private constructor() {
	private val logger = LoggerFactory.getLogger(ServerStatusDatabaseManager::class.java)
	private var connection: Connection? = null

	// Cache for server status data to reduce database reads
	private val serverStatusCache = ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>>()

	/**
	 * Initializes the database manager with a database connection.
	 * @param conn The database connection
	 */
	fun initialize(conn: Connection?) {
		connection = conn
		logger.info("ServerStatusDatabaseManager initialized")
	}

	/**
	 * Creates the necessary tables in the database if they don't exist.
	 */
	fun createTables() {
		logger.debug("Creating server_status table if it doesn't exist")
		val statement = connection?.createStatement()

		// Create the server_status table
		statement?.execute(
			"""
            CREATE TABLE IF NOT EXISTS server_status (
                user_id TEXT NOT NULL,
                server TEXT NOT NULL,
                status INTEGER NOT NULL,
                PRIMARY KEY (user_id, server)
            )
            """
		)

		statement?.close()
		logger.debug("server_status table created successfully")
	}

	/**
	 * Saves server status data to the database.
	 * @param userId The ID of the user
	 * @param serverMap A map of servers and their status
	 */
	fun saveServerStatus(userId: String, serverMap: Map<String, Boolean>) {
		try {
			logger.debug("Saving server status data for user $userId")
			connection?.let { conn ->
				// Use a transaction for better performance
				conn.autoCommit = false

				// First delete existing entries for this user
				val deleteStatement = conn.prepareStatement("DELETE FROM server_status WHERE user_id = ?")
				deleteStatement.setString(1, userId)
				deleteStatement.executeUpdate()
				deleteStatement.close()

				// Then insert new entries
				val insertStatement =
					conn.prepareStatement("INSERT INTO server_status (user_id, server, status) VALUES (?, ?, ?)")

				for ((server, status) in serverMap) {
					insertStatement.setString(1, userId)
					insertStatement.setString(2, server)
					insertStatement.setInt(3, if (status) 1 else 0)
					insertStatement.addBatch()
				}

				insertStatement.executeBatch()
				insertStatement.close()

				conn.commit()
				conn.autoCommit = true
				logger.debug("Server status data saved successfully for user $userId")

				// Update the cache
				val cachedMap = serverStatusCache.computeIfAbsent(userId) { ConcurrentHashMap() }
				cachedMap.clear()
				cachedMap.putAll(serverMap)
			} ?: run {
				Logger.Database.logNullConnection(logger, "save server status data")
			}
		} catch (e: SQLException) {
			Logger.Database.logError(logger, "saving server status data", e, connection)
		}
	}

	/**
	 * Loads server status data for all users from the database.
	 * @return A map of user IDs to maps of servers and their status
	 */
	fun loadAllServerStatus(): Map<String, ConcurrentHashMap<String, Boolean>> {
		val result = ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>>()

		try {
			logger.debug("Loading all server status data from database")
			connection?.let { conn ->
				val statement = conn.createStatement()
				val resultSet = statement.executeQuery("SELECT user_id, server, status FROM server_status")

				while (resultSet.next()) {
					val userId = resultSet.getString("user_id")
					val server = resultSet.getString("server")
					val status = resultSet.getInt("status") == 1

					val serverMap = result.computeIfAbsent(userId) { ConcurrentHashMap() }
					serverMap[server] = status
				}

				resultSet.close()
				statement.close()
				logger.debug("Loaded server status data for ${result.size} users")

				// Update the cache
				serverStatusCache.clear()
				serverStatusCache.putAll(result)
			} ?: run {
				Logger.Database.logNullConnection(logger, "load server status data")
			}
		} catch (e: SQLException) {
			Logger.Database.logError(logger, "loading server status data", e, connection)
		}

		return result
	}

	/**
	 * Loads server status data for a specific user from the database.
	 * @param userId The ID of the user
	 * @return A map of servers and their status
	 */
	fun loadServerStatus(userId: String): ConcurrentHashMap<String, Boolean> {
		// Check the cache first
		val cachedResult = serverStatusCache[userId]
		if (cachedResult != null) {
			logger.debug("Using cached server status data for user $userId")
			return cachedResult
		}

		val result = ConcurrentHashMap<String, Boolean>()

		try {
			logger.debug("Loading server status data for user $userId")
			connection?.let { conn ->
				val statement = conn.prepareStatement("SELECT server, status FROM server_status WHERE user_id = ?")
				statement.setString(1, userId)
				val resultSet = statement.executeQuery()

				while (resultSet.next()) {
					val server = resultSet.getString("server")
					val status = resultSet.getInt("status") == 1
					result[server] = status
				}

				resultSet.close()
				statement.close()
				logger.debug("Loaded ${result.size} servers for user $userId")

				// Update the cache
				serverStatusCache[userId] = result
			} ?: run {
				Logger.Database.logNullConnection(logger, "load server status data")
			}
		} catch (e: SQLException) {
			Logger.Database.logError(logger, "loading server status data for user $userId", e, connection)
		}

		return result
	}

	companion object {
		private var instance: ServerStatusDatabaseManager? = null

		/**
		 * Gets the singleton instance of the ServerStatusDatabaseManager.
		 * @return The ServerStatusDatabaseManager instance
		 */
		@Synchronized
		fun getInstance(): ServerStatusDatabaseManager {
			if (instance == null) {
				instance = ServerStatusDatabaseManager()
			}
			return instance!!
		}
	}
}
