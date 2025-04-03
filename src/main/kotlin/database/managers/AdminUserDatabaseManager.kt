package database.managers

import org.slf4j.LoggerFactory
import utils.Logger
import java.sql.Connection
import java.sql.SQLException

/**
 * Manages admin user data in the database.
 */
class AdminUserDatabaseManager private constructor() {
	private val logger = LoggerFactory.getLogger(AdminUserDatabaseManager::class.java)
	private var connection: Connection? = null

	/**
	 * Initializes the database manager with a database connection.
	 * @param conn The database connection
	 */
	fun initialize(conn: Connection?) {
		connection = conn
		logger.info("AdminUserDatabaseManager initialized")
	}

	/**
	 * Creates the necessary tables in the database if they don't exist.
	 */
	fun createTables() {
		logger.debug("Creating admin_users table if it doesn't exist")
		val statement = connection?.createStatement()

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

		statement?.close()
		logger.debug("admin_users table created successfully")
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
				Logger.Database.logNullConnection(logger, "add admin user")
				return false
			}
		} catch (e: SQLException) {
			Logger.Database.logError(logger, "adding admin user", e, connection)
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
				Logger.Database.logNullConnection(logger, "check admin status")
				return false
			}
		} catch (e: SQLException) {
			Logger.Database.logError(logger, "checking admin status", e, connection)
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
				Logger.Database.logNullConnection(logger, "get admin users")
			}
		} catch (e: SQLException) {
			Logger.Database.logError(logger, "getting admin users", e, connection)
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
				Logger.Database.logNullConnection(logger, "remove admin user")
				return false
			}
		} catch (e: SQLException) {
			Logger.Database.logError(logger, "removing admin user", e, connection)
			return false
		}
	}

	companion object {
		private var instance: AdminUserDatabaseManager? = null

		/**
		 * Gets the singleton instance of the AdminUserDatabaseManager.
		 * @return The AdminUserDatabaseManager instance
		 */
		@Synchronized
		fun getInstance(): AdminUserDatabaseManager {
			if (instance == null) {
				instance = AdminUserDatabaseManager()
			}
			return instance!!
		}
	}
}
