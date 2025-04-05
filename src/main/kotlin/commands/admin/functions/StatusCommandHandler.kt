package commands.admin.functions

import commands.AdminStatusHandler
import dev.kord.core.event.message.MessageCreateEvent
import org.slf4j.LoggerFactory

/**
 * Handler for the 'status' subcommand of the admin command.
 * Allows admins to manage custom bot statuses.
 */
object StatusCommandHandler {
	private val logger = LoggerFactory.getLogger(StatusCommandHandler::class.java)

	/**
	 * Handles the status command to manage custom bot statuses.
	 * Delegates to the AdminStatusHandler class for status-related functionality.
	 *
	 * @param event The message event
	 * @param args The command arguments
	 * @return true if the command was successful, false otherwise
	 */
	suspend fun handleStatusCommand(event: MessageCreateEvent, args: List<String>): Boolean {
		// Delegate to the AdminStatusHandler class
		return AdminStatusHandler.getInstance().handleStatusCommand(event, args)
	}
}
