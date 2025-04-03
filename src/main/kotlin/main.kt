import org.slf4j.LoggerFactory
import utils.initializers.*

// Get logger for the main class
private val logger = LoggerFactory.getLogger("MainKt")

suspend fun main() {
	logger.info("Starting Discord bot")

	// Initialize logs directory
	LogsInitializer.createLogsDirectory()

	// Read bot token
	val token = BotInitializer.readBotToken()

	// Initialize database
	val dbManager = DatabaseInitializer.initializeDatabase()

	// Generate one-time admin verification code if no admins exist
	DatabaseInitializer.generateOneTimeAdminCode(dbManager)

	// Initialize Kord and set up commands
	val (kord, mention) = BotInitializer.initializeKord(token)

	// Initialize commands
	CommandInitializer.initializeCommands(kord, mention)

	// Login to Discord
	BotInitializer.loginToDiscord(kord)

	// Initialize status manager
	StatusInitializer.initializeStatus(kord)
}
