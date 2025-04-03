package commands

import dev.kord.core.event.message.MessageCreateEvent

/**
 * Command that lists all available commands and their descriptions.
 * Users can type "help" to see a list of all commands or "help <command>" to get details about a specific command.
 */
class HelpCommand(private val commandRegistry: CommandRegistry) : Command {
	override val name = "help"
	override val description = "Lists all available commands or provides details about a specific command"

	override suspend fun execute(event: MessageCreateEvent): Boolean {
		logger.info("Executing help command")

		val messageText = extractMessageText(event)

		// Check if a specific command was requested
		if (messageText.isNotEmpty()) {
			val commandName = messageText.split(" ")[0]
			return showCommandHelp(commandName, event)
		}

		// Otherwise, show help for all commands
		return showAllCommands(event)
	}

	/**
	 * Shows help for a specific command.
	 */
	private suspend fun showCommandHelp(commandName: String, event: MessageCreateEvent): Boolean {
		logger.debug("Showing help for command: $commandName")

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

	/**
	 * Shows a list of all available commands.
	 */
	private suspend fun showAllCommands(event: MessageCreateEvent): Boolean {
		logger.debug("Showing all commands")

		val commands = commandRegistry.allCommands

		val message = buildString {
			append("**Available Commands**\n\n")

			if (commands.isEmpty()) {
				append("No commands available.")
			} else {
				commands.values
					.filterNot { it is EasterEggCommand }  // Filter out easter egg commands
					.sortedBy { it.name }
					.forEach { command ->
						append("**${command.name}** - ${command.description}\n")
					}

				append("\nUse `help <command>` for more details about a specific command.")
			}
		}

		event.message.channel.createMessage(message)
		return true
	}
}
