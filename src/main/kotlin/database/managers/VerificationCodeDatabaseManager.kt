package database.managers

import org.slf4j.LoggerFactory
import utils.Logger
import java.sql.Connection
import java.sql.SQLException
import kotlin.random.Random

/**
 * Manages verification code data in the database.
 */
class VerificationCodeDatabaseManager private constructor() {
	private val logger = LoggerFactory.getLogger(VerificationCodeDatabaseManager::class.java)
	private var connection: Connection? = null

	/**
	 * Initializes the database manager with a database connection.
	 * @param conn The database connection
	 */
	fun initialize(conn: Connection?) {
		connection = conn
		logger.info("VerificationCodeDatabaseManager initialized")
	}

	/**
	 * Creates the necessary tables in the database if they don't exist.
	 */
	fun createTables() {
		logger.debug("Creating verification_codes table if it doesn't exist")
		val statement = connection?.createStatement()

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
		logger.debug("verification_codes table created successfully")
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
				Logger.Database.logNullConnection(logger, "create verification code")
				return null
			}
		} catch (e: SQLException) {
			Logger.Database.logError(logger, "creating verification code", e, connection)
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
			logger.debug("Validating verification code: $code")
			connection?.let { conn ->
				// First check if the code exists, is not used, and is not expired
				val checkStatement = conn.prepareStatement(
					"""
                    SELECT type FROM verification_codes 
                    WHERE code = ? AND used = 0 
                    AND created_at > ? 
                    """
				)
				checkStatement.setString(1, code)
				// Codes expire after 5 minutes (300000 milliseconds)
				checkStatement.setLong(2, System.currentTimeMillis() - 300000)

				val resultSet = checkStatement.executeQuery()

				if (resultSet.next()) {
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

					logger.info("Verification code $code validated and used by user $userId")
					return codeType
				} else {
					resultSet.close()
					checkStatement.close()
					logger.warn("Invalid, expired, or already used verification code: $code")
					return null
				}
			} ?: run {
				Logger.Database.logNullConnection(logger, "validate verification code")
				return null
			}
		} catch (e: SQLException) {
			Logger.Database.logError(logger, "validating verification code", e, connection)
			return null
		}
	}

	/**
	 * Generates a random verification code.
	 * @return A random 8-character alphanumeric code
	 */
	private fun generateRandomCode(): String {
		val charPool: List<Char> = ('A'..'Z') + ('0'..'9')
		return (1..8)
			.map { Random.nextInt(0, charPool.size) }
			.map(charPool::get)
			.joinToString("")
	}

	companion object {
		private var instance: VerificationCodeDatabaseManager? = null

		/**
		 * Gets the singleton instance of the VerificationCodeDatabaseManager.
		 * @return The VerificationCodeDatabaseManager instance
		 */
		@Synchronized
		fun getInstance(): VerificationCodeDatabaseManager {
			if (instance == null) {
				instance = VerificationCodeDatabaseManager()
			}
			return instance!!
		}
	}
}
