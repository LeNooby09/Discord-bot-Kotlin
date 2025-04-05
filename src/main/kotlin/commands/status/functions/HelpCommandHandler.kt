package commands.status.functions

import dev.kord.core.event.message.MessageCreateEvent
import org.slf4j.LoggerFactory

/**
 * Handler for the 'help' subcommand of the status command.
 * Displays help information for the status command.
 */
object HelpCommandHandler {
	private val logger = LoggerFactory.getLogger(HelpCommandHandler::class.java)

	/**
	 * Handles the help command to display help information.
	 *
	 * @param event The message event
	 */
	suspend fun handleHelpCommand(event: MessageCreateEvent) {
		val helpText = """
            **Status Command Help**
            Monitor server status with the following subcommands:
            
            **Check Commands:**
            `status check <server>` - Check if a server is online
            `status add <server>` - Add a server to your personal monitoring list
            `status delete <server>` - Remove a server from your personal monitoring list
            `status list` - List all servers in your personal monitoring list and their status
            
            **Monitor Commands:**
            `status monitor start` - Start the server status monitoring
            `status monitor stop` - Stop the server status monitoring
            `status monitor status` - Check if monitoring is running
            
            Your server list is stored in a database and persists between bot restarts.
            The bot will automatically notify you when a server's status changes.
        """.trimIndent()

		event.message.channel.createMessage(helpText)
	}
}
