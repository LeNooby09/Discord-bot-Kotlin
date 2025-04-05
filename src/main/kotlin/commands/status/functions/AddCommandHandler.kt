package commands.status.functions

import commands.status.StatusDataCommand
import dev.kord.core.event.message.MessageCreateEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import utils.ServerUtils

/**
 * Handler for the 'add' subcommand of the status command.
 * Adds a server to the user's monitoring list.
 */
object AddCommandHandler {
	private val logger = LoggerFactory.getLogger(AddCommandHandler::class.java)

	/**
	 * Handles the add command to add a server to the monitoring list.
	 *
	 * @param event The message event
	 * @param args The command arguments
	 */
	suspend fun handleAddCommand(event: MessageCreateEvent, args: List<String>) = coroutineScope {
		if (args.isEmpty()) {
			event.message.channel.createMessage("Please specify a server to add. Usage: status add <server>")
			return@coroutineScope
		}

		val server = args[0]
		val userId = event.message.author?.id?.toString() ?: "unknown"

		// Check if the server is in the IP blacklist
		if (ServerUtils.isIpBlacklisted(server)) {
			event.message.channel.createMessage("This IP address is blacklisted and cannot be monitored.")
			return@coroutineScope
		}

		// Check if the server is valid using ServerUtils - this is already a suspend function
		val isValid = try {
			ServerUtils.isValidServerAddress(server)
		} catch (e: Exception) {
			event.message.channel.createMessage("Error validating server address: ${e.message}")
			return@coroutineScope
		}

		if (!isValid) {
			event.message.channel.createMessage("Invalid server address: $server")
			return@coroutineScope
		}

		// Send initial message indicating check is in progress
		val message = event.message.channel.createMessage("Adding server $server to monitoring list. Checking status...")

		// Store the channel for later use
		val channel = event.message.channel

		// Use launch with structured concurrency for better lifecycle management
		launch(Dispatchers.IO.limitedParallelism(10)) {
			try {
				// Add the server to the monitoring list
				val status = ServerStatusChecker.checkServerStatus(server)

				// Update user's server map
				val dataManager = StatusDataCommand.getInstance()
				val userServersMap = dataManager.getServerMapForUser(userId)
				userServersMap[server] = status

				// Save the updated data
				dataManager.saveData()

				// Send the result as a new message
				channel.createMessage("Added server $server to monitoring list. Current status: ${if (status) "online" else "offline"}")
			} catch (e: Exception) {
				// Handle any errors
				logger.error("Error adding server $server to monitoring list", e)
				channel.createMessage("Error adding server $server: ${e.message}")
			}
		}
	}
}
