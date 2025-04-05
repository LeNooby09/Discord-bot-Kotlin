package commands.status.functions

import dev.kord.core.event.message.MessageCreateEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import utils.ServerUtils

/**
 * Handler for the 'check' subcommand of the status command.
 * Checks if a server is online and reports the status.
 */
object CheckCommandHandler {
	private val logger = LoggerFactory.getLogger(CheckCommandHandler::class.java)

	/**
	 * Handles the check command to check if a server is online.
	 *
	 * @param event The message event
	 * @param args The command arguments
	 */
	suspend fun handleCheckCommand(event: MessageCreateEvent, args: List<String>) = coroutineScope {
		if (args.isEmpty()) {
			event.message.channel.createMessage("Please specify a server to check. Usage: status check <server>")
			return@coroutineScope
		}

		val server = args[0]
		logger.info("Checking status of server: $server")

		// Check if the server is in the IP blacklist
		if (ServerUtils.isIpBlacklisted(server)) {
			logger.warn("Attempted to check blacklisted IP: $server")
			event.message.channel.createMessage("This IP address is blacklisted and cannot be checked.")
			return@coroutineScope
		}

		// Send initial message indicating check is in progress
		event.message.channel.createMessage("Checking server $server status...")

		// Store the channel for later use
		val channel = event.message.channel

		// Use async to perform the check without blocking the command response
		// This provides structured concurrency (will be properly cancelled if needed)
		launch(Dispatchers.IO.limitedParallelism(10)) {
			try {
				// Perform the server check
				val isOnline = ServerStatusChecker.checkServerStatus(server)

				// Create the status message
				val statusMessage = if (isOnline) {
					"Server $server is online"
				} else {
					"Server $server is offline"
				}

				// Log the result
				logger.info("Server $server status check result: ${if (isOnline) "online" else "offline"}")

				// Send the result as a new message
				channel.createMessage(statusMessage)
			} catch (e: Exception) {
				// Handle any errors
				logger.error("Error checking server status for $server", e)
				channel.createMessage("Error checking server $server: ${e.message}")
			}
		}
	}
}
