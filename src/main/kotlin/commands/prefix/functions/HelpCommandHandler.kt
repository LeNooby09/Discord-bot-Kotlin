package commands.prefix.functions

import dev.kord.core.event.message.MessageCreateEvent
import org.slf4j.LoggerFactory

/**
 * Handler for the 'help' subcommand of the prefix command.
 * Displays help information for the prefix command.
 */
object HelpCommandHandler {
	private val logger = LoggerFactory.getLogger(HelpCommandHandler::class.java)

	/**
	 * Handles the help command to display help information.
	 *
	 * @param event The message event
	 * @return true if the command was successful, false otherwise
	 */
	suspend fun handleHelpCommand(event: MessageCreateEvent): Boolean {
		val helpMessage = """
            **Prefix Command Help**

            Manage the custom prefix for this server (server admin only).

            **Subcommands:**
            > `prefix` - Display the current prefix
            > `prefix set <new_prefix>` - Set a new prefix
            > `prefix reset` - Reset to the default prefix (!)
            > `prefix help` - Show this help message

            Note: The bot will always respond to its mention regardless of the custom prefix.
        """.trimIndent()

		event.message.channel.createMessage(helpMessage)
		logger.info("User ${event.message.author?.id?.value?.toString() ?: "unknown"} requested prefix command help")
		return true
	}
}
