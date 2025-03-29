import dev.kord.core.Kord
import dev.kord.gateway.PrivilegedIntent
import dev.kord.gateway.Intent
import java.io.File
import java.io.IOException
import commands.CommandRegistry
import org.slf4j.LoggerFactory

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
