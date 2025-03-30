import commands.CommandRegistry
import database.DatabaseManager
import dev.kord.core.Kord
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

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
  logger.info("Starting Discord bot")

  // Initialize the database
  logger.info("Initializing database")
  val dbManager = DatabaseManager.getInstance()
  dbManager.initialize()

  // Add shutdown hook to close database connection
  Runtime.getRuntime().addShutdownHook(Thread {
    logger.info("Shutting down, closing database connection")
    dbManager.close()
  })

  // Generate one-time admin verification code if no admins exist
  val admins = dbManager.getAllAdmins()
  if (admins.isEmpty()) {
    val adminCode = dbManager.createVerificationCode("admin")
    if (adminCode != null) {
      println("\n===========================================================")
      println("ADMIN VERIFICATION CODE: $adminCode")
      println("Use this code with the 'admin verify' command to become admin")
      println("This code can only be used once and will expire after restart")
      println("===========================================================\n")
      logger.info("Generated one-time admin verification code")
    } else {
      logger.error("Failed to generate admin verification code")
    }
  } else {
    logger.info("Admin users already exist, skipping verification code generation")
  }

  logger.debug("Initializing Kord with token")
  val kord = Kord(token)
  val mention = "<@1327594330130481272>"
  logger.info("Bot mention ID: $mention")

  // Create and configure the command registry
  logger.info("Creating command registry")
  val commandRegistry = CommandRegistry()

  // Automatically discover and register all commands in the commands package
  logger.info("Discovering and registering commands")
  commandRegistry.registerAllCommandsInPackage()

  // Register the help command manually since it needs a reference to the command registry
  logger.info("Registering help command")
  val helpCommand = commands.HelpCommand(commandRegistry)
  commandRegistry.register(helpCommand)

  // Register all commands with Kord
  logger.info("Registering command handlers with Kord")
  commandRegistry.registerAllCommands(kord, mention)

  logger.info("Logging in to Discord")
  kord.login {
    @OptIn(PrivilegedIntent::class)
    intents += Intent.MessageContent
    logger.debug("Enabled MessageContent intent")
  }

  logger.info("Bot is now online and ready")
}
