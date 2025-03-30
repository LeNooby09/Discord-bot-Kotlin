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
        logger.error("Database connection is null, cannot save server status data")
      }
    } catch (e: SQLException) {
      logger.error("Error saving server status data", e)
      connection?.rollback()
      connection?.autoCommit = true
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
        logger.error("Database connection is null, cannot load server status data")
      }
    } catch (e: SQLException) {
      logger.error("Error loading server status data", e)
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
        logger.error("Database connection is null, cannot load server status data")
      }
    } catch (e: SQLException) {
      logger.error("Error loading server status data for user $userId", e)
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
      logger.error("Error closing database connection", e)
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
        logger.error("Database connection is null, cannot add admin user")
        return false
      }
    } catch (e: SQLException) {
      logger.error("Error adding admin user", e)
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
        logger.error("Database connection is null, cannot check admin status")
        return false
      }
    } catch (e: SQLException) {
      logger.error("Error checking admin status", e)
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
        logger.error("Database connection is null, cannot get admin users")
      }
    } catch (e: SQLException) {
      logger.error("Error getting admin users", e)
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
        logger.error("Database connection is null, cannot remove admin user")
        return false
      }
    } catch (e: SQLException) {
      logger.error("Error removing admin user", e)
      return false
    }
  }

  /**
   * Creates a verification code.
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
        logger.error("Database connection is null, cannot create verification code")
        return null
      }
    } catch (e: SQLException) {
      logger.error("Error creating verification code", e)
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

        // Check if the code exists and is unused
        val checkStatement = conn.prepareStatement(
          "SELECT type FROM verification_codes WHERE code = ? AND used = 0"
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
        logger.error("Database connection is null, cannot validate verification code")
        return null
      }
    } catch (e: SQLException) {
      logger.error("Error validating verification code", e)
      connection?.rollback()
      connection?.autoCommit = true
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
