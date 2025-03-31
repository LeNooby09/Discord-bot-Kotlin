package database

import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Manages database operations for the bot.
 * Provides generic methods for reading and writing data to the database.
 */
class DatabaseManager private constructor() {
	private val logger = LoggerFactory.getLogger(DatabaseManager::class.java)
	private var connection: Connection? = null
	private val dbFile = "bot_data.db"

	// Cache for server status data to reduce database reads
	private val serverStatusCache = ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>>()

	// Prepared statement cache to avoid recreating statements
	private val preparedStatements = ConcurrentHashMap<String, PreparedStatement>()

	// Lock for thread-safe database operations
	private val dbLock = ReentrantReadWriteLock()

	// Batch size for database operations
	private val BATCH_SIZE = 100

	/**
	 * Initializes the database connection and creates tables if they don't exist.
	 */
	fun initialize() {
		try {
			logger.info("Initializing database connection to $dbFile")
			// Load the SQLite JDBC driver
			Class.forName("org.sqlite.JDBC")

			// Create a connection to the database
			connection = DriverManager.getConnection("jdbc:sqlite:$dbFile")
			logger.info("Database connection established")

			// Create tables if they don't exist
			createTables()

			// Initialize the ServerCustomizationManager
			val serverCustomizationManager = ServerCustomizationManager.getInstance()
			serverCustomizationManager.initialize(connection)
			serverCustomizationManager.createTables()
			logger.info("ServerCustomizationManager initialized")
		} catch (e: Exception) {
			logger.error("Failed to initialize database", e)
			throw RuntimeException("Failed to initialize database", e)
		}
	}

	/**
	 * Creates the necessary tables in the database if they don't exist.
	 */
	private fun createTables() {
		logger.debug("Creating tables if they don't exist")
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

		// Create the admin_users table
		statement?.execute(
			"""
            CREATE TABLE IF NOT EXISTS admin_users (
                user_id TEXT PRIMARY KEY,
                username TEXT NOT NULL,
                added_at INTEGER NOT NULL
            )
        """
		)

		// Create the verification_codes table
		statement?.execute(
			"""
            CREATE TABLE IF NOT EXISTS verification_codes (
                code TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                used INTEGER DEFAULT 0,
                used_by TEXT DEFAULT NULL,
                created_by TEXT DEFAULT NULL
            )
        """
		)

		statement?.close()
		logger.debug("Tables created successfully")
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
			} ?: run {
				utils.Logger.Database.logNullConnection(logger, "save server status data")
			}
		} catch (e: SQLException) {
			utils.Logger.Database.logError(logger, "saving server status data", e, connection)
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
			} ?: run {
				utils.Logger.Database.logNullConnection(logger, "load server status data")
			}
		} catch (e: SQLException) {
			utils.Logger.Database.logError(logger, "loading server status data", e, connection)
		}

		return result
	}

	/**
	 * Loads server status data for a specific user from the database.
	 * @param userId The ID of the user
	 * @return A map of servers and their status
	 */
	fun loadServerStatus(userId: String): ConcurrentHashMap<String, Boolean> {
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
			} ?: run {
				utils.Logger.Database.logNullConnection(logger, "load server status data")
			}
		} catch (e: SQLException) {
			utils.Logger.Database.logError(logger, "loading server status data for user $userId", e, connection)
		}

		return result
	}

	/**
	 * Closes the database connection.
	 */
	fun close() {
		try {
			logger.info("Closing database connection")
			connection?.close()
			logger.info("Database connection closed")
		} catch (e: SQLException) {
			utils.Logger.Database.logError(logger, "closing database connection", e, null)
		}
	}

	/**
	 * Adds a user as an admin.
	 * @param userId The Discord user ID
	 * @param username The Discord username
	 * @return True if the user was added successfully, false otherwise
	 */
	fun addAdminUser(userId: String, username: String): Boolean {
		try {
			logger.debug("Adding admin user $username ($userId)")
			connection?.let { conn ->
				val statement = conn.prepareStatement(
					"INSERT OR REPLACE INTO admin_users (user_id, username, added_at) VALUES (?, ?, ?)"
				)
				statement.setString(1, userId)
				statement.setString(2, username)
				statement.setLong(3, System.currentTimeMillis())

				val result = statement.executeUpdate() > 0
				statement.close()

				logger.info("Admin user $username ($userId) added successfully")
				return result
			} ?: run {
				utils.Logger.Database.logNullConnection(logger, "add admin user")
				return false
			}
		} catch (e: SQLException) {
			utils.Logger.Database.logError(logger, "adding admin user", e, connection)
			return false
		}
	}

	/**
	 * Checks if a user is an admin.
	 * @param userId The Discord user ID
	 * @return True if the user is an admin, false otherwise
	 */
	fun isAdmin(userId: String): Boolean {
		try {
			logger.debug("Checking if user $userId is an admin")
			connection?.let { conn ->
				val statement = conn.prepareStatement("SELECT 1 FROM admin_users WHERE user_id = ?")
				statement.setString(1, userId)
				val resultSet = statement.executeQuery()

				val isAdmin = resultSet.next()
				resultSet.close()
				statement.close()

				return isAdmin
			} ?: run {
				utils.Logger.Database.logNullConnection(logger, "check admin status")
				return false
			}
		} catch (e: SQLException) {
			utils.Logger.Database.logError(logger, "checking admin status", e, connection)
			return false
		}
	}

	/**
	 * Gets all admin users.
	 * @return A list of pairs containing user IDs and usernames
	 */
	fun getAllAdmins(): List<Pair<String, String>> {
		val admins = mutableListOf<Pair<String, String>>()

		try {
			logger.debug("Getting all admin users")
			connection?.let { conn ->
				val statement = conn.createStatement()
				val resultSet = statement.executeQuery("SELECT user_id, username FROM admin_users")

				while (resultSet.next()) {
					val userId = resultSet.getString("user_id")
					val username = resultSet.getString("username")
					admins.add(Pair(userId, username))
				}

				resultSet.close()
				statement.close()
				logger.debug("Found ${admins.size} admin users")
			} ?: run {
				utils.Logger.Database.logNullConnection(logger, "get admin users")
			}
		} catch (e: SQLException) {
			utils.Logger.Database.logError(logger, "getting admin users", e, connection)
		}

		return admins
	}

	/**
	 * Removes a user from admin status.
	 * @param userId The Discord user ID to remove from admins
	 * @return True if the user was removed successfully, false otherwise
	 */
	fun removeAdminUser(userId: String): Boolean {
		try {
			logger.debug("Removing admin user with ID $userId")
			connection?.let { conn ->
				val statement = conn.prepareStatement("DELETE FROM admin_users WHERE user_id = ?")
				statement.setString(1, userId)

				val result = statement.executeUpdate() > 0
				statement.close()

				if (result) {
					logger.info("Admin user with ID $userId removed successfully")
				} else {
					logger.warn("No admin user found with ID $userId to remove")
				}

				return result
			} ?: run {
				utils.Logger.Database.logNullConnection(logger, "remove admin user")
				return false
			}
		} catch (e: SQLException) {
			utils.Logger.Database.logError(logger, "removing admin user", e, connection)
			return false
		}
	}

	/**
	 * Creates a verification code that expires after 5 minutes.
	 * @param type The type of code (e.g., "admin", "user")
	 * @param createdBy The user ID of the admin who created the code (null for system-generated codes)
	 * @return The generated code, or null if an error occurred
	 */
	fun createVerificationCode(type: String, createdBy: String? = null): String? {
		try {
			logger.debug("Creating verification code of type $type")
			val code = generateRandomCode()

			connection?.let { conn ->
				val statement = conn.prepareStatement(
					"INSERT INTO verification_codes (code, type, created_at, created_by) VALUES (?, ?, ?, ?)"
				)
				statement.setString(1, code)
				statement.setString(2, type)
				statement.setLong(3, System.currentTimeMillis())
				if (createdBy != null) {
					statement.setString(4, createdBy)
				} else {
					statement.setNull(4, java.sql.Types.VARCHAR)
				}

				val result = statement.executeUpdate() > 0
				statement.close()

				if (result) {
					logger.info("Verification code created: $code (type: $type)")
					return code
				} else {
					logger.error("Failed to create verification code")
					return null
				}
			} ?: run {
				utils.Logger.Database.logNullConnection(logger, "create verification code")
				return null
			}
		} catch (e: SQLException) {
			utils.Logger.Database.logError(logger, "creating verification code", e, connection)
			return null
		}
	}

	/**
	 * Validates and uses a verification code.
	 * @param code The code to validate
	 * @param userId The user ID who is using the code
	 * @return The type of the code if valid, null otherwise
	 */
	fun validateAndUseCode(code: String, userId: String): String? {
		try {
			logger.debug("Validating verification code: $code for user $userId")
			connection?.let { conn ->
				conn.autoCommit = false

				// Check if the code exists, is unused, and is not older than 5 minutes
				val currentTime = System.currentTimeMillis()
				val fiveMinutesAgo = currentTime - (5 * 60 * 1000) // 5 minutes in milliseconds

				val checkStatement = conn.prepareStatement(
					"SELECT type, created_at FROM verification_codes WHERE code = ? AND used = 0"
				)
				checkStatement.setString(1, code)
				val resultSet = checkStatement.executeQuery()

				if (!resultSet.next()) {
					// Code doesn't exist or is already used
					resultSet.close()
					checkStatement.close()
					conn.rollback()
					conn.autoCommit = true
					logger.debug("Invalid or already used verification code: $code")
					return null
				}

				val codeType = resultSet.getString("type")
				val createdAt = resultSet.getLong("created_at")

				// Check if the code has expired (older than 5 minutes)
				if (createdAt < fiveMinutesAgo) {
					resultSet.close()
					checkStatement.close()
					conn.rollback()
					conn.autoCommit = true
					logger.debug("Expired verification code: $code (created at $createdAt, now $currentTime)")
					return null
				}

				resultSet.close()
				checkStatement.close()

				// Mark the code as used
				val updateStatement = conn.prepareStatement(
					"UPDATE verification_codes SET used = 1, used_by = ? WHERE code = ?"
				)
				updateStatement.setString(1, userId)
				updateStatement.setString(2, code)
				updateStatement.executeUpdate()
				updateStatement.close()

				conn.commit()
				conn.autoCommit = true

				logger.info("Verification code $code successfully used by $userId (type: $codeType)")
				return codeType
			} ?: run {
				utils.Logger.Database.logNullConnection(logger, "validate verification code")
				return null
			}
		} catch (e: SQLException) {
			utils.Logger.Database.logError(logger, "validating verification code", e, connection)
			return null
		}
	}

	/**
	 * Generates a random verification code.
	 * @return A random 8-character alphanumeric code
	 */
	private fun generateRandomCode(): String {
		val charPool = ('A'..'Z') + ('0'..'9')
		return (1..8)
			.map { charPool.random() }
			.joinToString("")
	}

	/**
	 * Flushes the database, deleting all data except admin users.
	 * @return True if the database was flushed successfully, false otherwise
	 */
	fun flushDatabase(): Boolean {
		try {
			logger.info("Flushing database (preserving admin users)")
			connection?.let { conn ->
				// Use a transaction for better consistency
				conn.autoCommit = false

				// Delete all server status data
				val deleteServerStatusStatement = conn.createStatement()
				deleteServerStatusStatement.executeUpdate("DELETE FROM server_status")
				deleteServerStatusStatement.close()
				logger.debug("Server status data deleted")

				// Delete all verification codes
				val deleteVerificationCodesStatement = conn.createStatement()
				deleteVerificationCodesStatement.executeUpdate("DELETE FROM verification_codes")
				deleteVerificationCodesStatement.close()
				logger.debug("Verification codes deleted")

				// Flush server customization data
				val serverCustomizationManager = ServerCustomizationManager.getInstance()
				serverCustomizationManager.flushData()
				logger.debug("Server customization data flushed")

				// Commit the transaction
				conn.commit()
				conn.autoCommit = true

				// Clear the server status cache
				serverStatusCache.clear()

				logger.info("Database flushed successfully (admin users preserved)")
				return true
			} ?: run {
				utils.Logger.Database.logNullConnection(logger, "flush database")
				return false
			}
		} catch (e: SQLException) {
			utils.Logger.Database.logError(logger, "flushing database", e, connection)
			return false
		}
	}

	/**
	 * Gets the prefix for a specific server.
	 * Delegates to ServerCustomizationManager.
	 * @param serverId The ID of the server
	 * @return The prefix for the server, or "!" if none is set
	 */
	fun getServerPrefix(serverId: String): String {
		return ServerCustomizationManager.getInstance().getServerPrefix(serverId)
	}

	/**
	 * Sets the prefix for a specific server.
	 * Delegates to ServerCustomizationManager.
	 * @param serverId The ID of the server
	 * @param prefix The prefix to set
	 * @return True if the prefix was set successfully, false otherwise
	 */
	fun setServerPrefix(serverId: String, prefix: String): Boolean {
		return ServerCustomizationManager.getInstance().setServerPrefix(serverId, prefix)
	}

	/**
	 * Removes the prefix for a specific server, resetting it to the default.
	 * Delegates to ServerCustomizationManager.
	 * @param serverId The ID of the server
	 * @return True if the prefix was removed successfully, false otherwise
	 */
	fun removeServerPrefix(serverId: String): Boolean {
		return ServerCustomizationManager.getInstance().removeServerPrefix(serverId)
	}

	companion object {
		private var instance: DatabaseManager? = null

		/**
		 * Gets the singleton instance of the DatabaseManager.
		 * @return The DatabaseManager instance
		 */
		fun getInstance(): DatabaseManager {
			if (instance == null) {
				instance = DatabaseManager()
			}
			return instance!!
		}
	}
}
