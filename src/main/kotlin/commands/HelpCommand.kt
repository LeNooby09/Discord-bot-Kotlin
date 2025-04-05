package commands

import commands.help.functions.AllCommandsHandler
import commands.help.functions.CommandHelpHandler
import dev.kord.core.event.message.MessageCreateEvent
import org.slf4j.LoggerFactory

/**
 * Command that lists all available commands and their descriptions.
 * Users can type "help" to see a list of all commands or "help <command>" to get details about a specific command.
 */
class HelpCommand(private val commandRegistry: CommandRegistry) : Command {
	override val name = "help"
	override val description = "Lists all available commands or provides details about a specific command"
	override val logger = LoggerFactory.getLogger(HelpCommand::class.java)

	override suspend fun execute(event: MessageCreateEvent): Boolean {
		logger.info("Executing help command")

		val messageText = extractMessageText(event)

		// Check if a specific command was requested
		if (messageText.isNotEmpty()) {
			val commandName = messageText.split(" ")[0]
			return CommandHelpHandler.showCommandHelp(commandName, event, commandRegistry)
		}

		// Otherwise, show help for all commands
		return AllCommandsHandler.showAllCommands(event, commandRegistry)
	}
}
