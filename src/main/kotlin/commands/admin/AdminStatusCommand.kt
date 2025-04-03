package commands.admin

import commands.AdminStatusHandler
import database.DatabaseManager
import dev.kord.core.event.message.MessageCreateEvent

/**
 * Command for admin status management functionality.
 * Allows admins to manage custom bot statuses.
 * Delegates to the AdminStatusHandler class for status-related functionality.
 */
class AdminStatusCommand : commands.Command {
	override val name = "adminstatus"
	override val description = "Admin status management commands"

	private val dbManager = DatabaseManager.getInstance()

	override suspend fun execute(event: MessageCreateEvent): Boolean {
		logger.info("Executing admin status command")

		val messageText = extractMessageText(event)
		val args = messageText.split(" ")

		// Check if the user is an admin
		val userId = event.message.author?.id?.value?.toString() ?: return false
		if (!dbManager.isAdmin(userId)) {
			event.message.channel.createMessage("You need to be an admin to use this command.")
			return false
		}

		// Delegate to the AdminStatusHandler class
		return AdminStatusHandler.getInstance().handleStatusCommand(event, args)
	}
}
