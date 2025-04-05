package commands.status.functions

import commands.status.StatusDataCommand
import dev.kord.core.event.message.MessageCreateEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Handler for the 'list' subcommand of the status command.
 * Lists all servers in the user's monitoring list and their status.
 */
object ListCommandHandler {
	private val logger = LoggerFactory.getLogger(ListCommandHandler::class.java)

	/**
	 * Handles the list command to list all servers in the monitoring list.
	 *
	 * @param event The message event
	 */
	suspend fun handleListCommand(event: MessageCreateEvent) = coroutineScope {
		val userId = event.message.author?.id?.toString() ?: "unknown"
		val dataManager = StatusDataCommand.getInstance()
		val userServersMap = dataManager.getServerMapForUser(userId)

		if (userServersMap.isEmpty()) {
			event.message.channel.createMessage("You don't have any servers in your monitoring list")
			return@coroutineScope
		}

		// For large server lists, build the string in a background coroutine
		val serverList = if (userServersMap.size > 20) {
			withContext(Dispatchers.Default) {
				userServersMap.entries.joinToString("\n") { (server, status) ->
					"$server: ${if (status) "online" else "offline"}"
				}
			}
		} else {
			// For small lists, just do it directly
			userServersMap.entries.joinToString("\n") { (server, status) ->
				"$server: ${if (status) "online" else "offline"}"
			}
		}

		event.message.channel.createMessage("Your monitored servers:\n$serverList")
	}
}
