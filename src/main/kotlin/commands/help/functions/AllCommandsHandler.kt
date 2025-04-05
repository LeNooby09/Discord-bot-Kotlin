package commands.help.functions

import commands.CommandRegistry
import commands.EasterEggCommand
import dev.kord.core.event.message.MessageCreateEvent
import org.slf4j.LoggerFactory

/**
 * Handler for showing a list of all available commands.
 */
object AllCommandsHandler {
	private val logger = LoggerFactory.getLogger(AllCommandsHandler::class.java)

	/**
	 * Shows a list of all available commands.
	 *
	 * @param event The message event
	 * @param commandRegistry The command registry containing all commands
	 * @return true if the command was successful, false otherwise
	 */
	suspend fun showAllCommands(event: MessageCreateEvent, commandRegistry: CommandRegistry): Boolean {
		logger.info("User ${event.message.author?.id?.value?.toString() ?: "unknown"} requested list of all commands")

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
