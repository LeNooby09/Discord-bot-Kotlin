package commands.help.functions

import commands.CommandRegistry
import commands.EasterEggCommand
import dev.kord.core.event.message.MessageCreateEvent
import org.slf4j.LoggerFactory

/**
 * Handler for showing help for a specific command.
 */
object CommandHelpHandler {
	private val logger = LoggerFactory.getLogger(CommandHelpHandler::class.java)

	/**
	 * Shows help for a specific command.
	 *
	 * @param commandName The name of the command to show help for
	 * @param event The message event
	 * @param commandRegistry The command registry containing all commands
	 * @return true if the command was found and help was shown, false otherwise
	 */
	suspend fun showCommandHelp(
		commandName: String,
		event: MessageCreateEvent,
		commandRegistry: CommandRegistry
	): Boolean {
		logger.info("User ${event.message.author?.id?.value?.toString() ?: "unknown"} requested help for command: $commandName")

		val command = commandRegistry.allCommands[commandName]
		if (command == null || command is EasterEggCommand) {
			event.message.channel.createMessage("Unknown command: $commandName")
			return false
		}

		val message = buildString {
			append("**Command: ${command.name}**\n\n")
			append("${command.description}\n")
		}

		event.message.channel.createMessage(message)
		return true
	}
}
