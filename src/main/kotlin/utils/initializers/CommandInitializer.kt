package utils.initializers

import commands.CommandRegistry
import commands.HelpCommand
import dev.kord.core.Kord
import org.slf4j.LoggerFactory

/**
 * Handles initialization and registration of bot commands.
 */
object CommandInitializer {
	private val logger = LoggerFactory.getLogger(CommandInitializer::class.java)

	/**
	 * Initializes and registers all commands.
	 * @param kord The Kord instance
	 * @param mention The bot's mention ID
	 * @return The initialized command registry
	 */
	fun initializeCommands(kord: Kord, mention: String): CommandRegistry {
		logger.info("Initializing command registry")

		// Create command registry and register all commands
		val commandRegistry = CommandRegistry()
		commandRegistry.registerAllCommandsInPackage()

		// Register the help command manually since it needs a reference to the command registry
		val helpCommand = HelpCommand(commandRegistry)
		commandRegistry.register(helpCommand)

		// Register all commands with Kord
		commandRegistry.registerAllCommands(kord, mention)
		logger.info("All commands registered successfully")

		return commandRegistry
	}
}
