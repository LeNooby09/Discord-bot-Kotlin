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
