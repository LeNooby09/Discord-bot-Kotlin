import commands.AdminCommand
import commands.CommandRegistry
import database.DatabaseManager
import dev.kord.core.Kord
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

// Get logger for the main class
private val logger = LoggerFactory.getLogger("MainKt")

private val token: String = try {
	logger.info("Reading bot token from .token file")
	File("./.token").readText().trim()
} catch (e: IOException) {
	logger.error("Failed to read token file", e)
	throw RuntimeException("Failed to read token file", e)
}

suspend fun main() {
	// Create logs directory if it doesn't exist
	try {
		val logsPath = Paths.get("logs")
		if (!Files.exists(logsPath)) {
			withContext(Dispatchers.IO) {
				Files.createDirectories(logsPath)
			}
			println("Created logs directory")
		}
	} catch (e: IOException) {
		println("Warning: Failed to create logs directory: ${e.message}")
	}

	logger.info("Starting Discord bot")

	// Initialize the database
	val dbManager = DatabaseManager.getInstance()
	dbManager.initialize()
	logger.info("Database initialized")

	// Add shutdown hook to close database connection
	Runtime.getRuntime().addShutdownHook(Thread {
		logger.info("Shutting down, closing database connection")
		dbManager.close()
	})

	// Generate one-time admin verification code if no admins exist
	AdminCommand.generateOneTimeAdminCode(dbManager)

	// Initialize Kord and set up commands
	val kord = Kord(token)
	val mention = "<@1327594330130481272>"
	logger.info("Initializing bot with mention ID: $mention")

	// Create command registry and register all commands
	val commandRegistry = CommandRegistry()
	commandRegistry.registerAllCommandsInPackage()

	// Register the help command manually since it needs a reference to the command registry
	val helpCommand = commands.HelpCommand(commandRegistry)
	commandRegistry.register(helpCommand)

	// Register all commands with Kord
	commandRegistry.registerAllCommands(kord, mention)
	logger.info("All commands registered successfully")

	logger.info("Logging in to Discord")
	kord.login {
		@OptIn(PrivilegedIntent::class)
		intents += Intent.MessageContent
		logger.debug("Enabled MessageContent intent")
	}

	logger.info("Bot is now online and ready")
}
