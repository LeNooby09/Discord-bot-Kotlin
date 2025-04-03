package utils.initializers

import commands.admin.AdminVerificationCommand
import database.DatabaseManager
import org.slf4j.LoggerFactory

/**
 * Handles initialization of the database and related components.
 */
object DatabaseInitializer {
	private val logger = LoggerFactory.getLogger(DatabaseInitializer::class.java)

	/**
	 * Initializes the database and related components.
	 * @return The initialized database manager instance
	 */
	fun initializeDatabase(): DatabaseManager {
		logger.info("Initializing database")

		// Get the database manager instance
		val dbManager = DatabaseManager.getInstance()

		// Initialize the database
		dbManager.initialize()
		logger.info("Database initialized")

		// Add shutdown hook to close database connection
		Runtime.getRuntime().addShutdownHook(Thread {
			logger.info("Shutting down, closing database connection")
			dbManager.close()
		})

		return dbManager
	}

	/**
	 * Generates a one-time admin verification code if no admins exist.
	 * @param dbManager The database manager instance
	 */
	fun generateOneTimeAdminCode(dbManager: DatabaseManager) {
		// Generate one-time admin verification code if no admins exist
		AdminVerificationCommand.generateOneTimeAdminCode(dbManager)
	}
}
