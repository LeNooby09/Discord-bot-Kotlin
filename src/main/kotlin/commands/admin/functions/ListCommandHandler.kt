package commands.admin.functions

import database.DatabaseManager
import dev.kord.core.event.message.MessageCreateEvent
import org.slf4j.LoggerFactory

/**
 * Handler for the 'list' subcommand of the admin command.
 * Lists all admin users.
 */
object ListCommandHandler {
	private val logger = LoggerFactory.getLogger(ListCommandHandler::class.java)
	private val dbManager = DatabaseManager.getInstance()

	/**
	 * Handles the list command to list all admin users.
	 *
	 * @param event The message event
	 * @return true if the command was successful, false otherwise
	 */
	suspend fun handleListCommand(event: MessageCreateEvent): Boolean {
		val userId = event.message.author?.id?.value?.toString() ?: return false

		// Get all admin users
		val admins = dbManager.getAllAdmins()

		val message = buildString {
			append("**Admin Users**\n\n")

			if (admins.isEmpty()) {
				append("No admin users found.")
			} else {
				admins.forEach { (id, username) ->
					append("- $username ($id)\n")
				}
			}
		}

		event.message.channel.createMessage(message)
		return true
	}
}
