package commands.status.functions

import commands.status.StatusDataCommand
import dev.kord.core.event.message.MessageCreateEvent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Handler for the 'delete' subcommand of the status command.
 * Removes a server from the user's monitoring list.
 */
object DeleteCommandHandler {
	private val logger = LoggerFactory.getLogger(DeleteCommandHandler::class.java)

	/**
	 * Handles the delete command to remove a server from the monitoring list.
	 *
	 * @param event The message event
	 * @param args The command arguments
	 */
	suspend fun handleDeleteCommand(event: MessageCreateEvent, args: List<String>) = coroutineScope {
		if (args.isEmpty()) {
			event.message.channel.createMessage("Please specify a server to delete. Usage: status delete <server>")
			return@coroutineScope
		}

		val server = args[0]
		val userId = event.message.author?.id?.toString() ?: "unknown"
		val dataManager = StatusDataCommand.getInstance()
		val userServersMap = dataManager.getServerMapForUser(userId)

		if (userServersMap.remove(server) != null) {
			// Launch a coroutine to save data asynchronously
			launch {
				dataManager.saveData()
			}
			event.message.channel.createMessage("Removed server $server from your monitoring list")
		} else {
			event.message.channel.createMessage("Server $server is not in your monitoring list")
		}
	}
}
