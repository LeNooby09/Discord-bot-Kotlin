package commands.admin.functions

import database.DatabaseManager
import dev.kord.core.event.message.MessageCreateEvent
import org.slf4j.LoggerFactory

/**
 * Handler for the 'remove' subcommand of the admin command.
 * Removes a user from admin status.
 */
object RemoveCommandHandler {
	private val logger = LoggerFactory.getLogger(RemoveCommandHandler::class.java)
	private val dbManager = DatabaseManager.getInstance()

	/**
	 * Handles the remove command to remove a user from admin status.
	 * This subcommand requires a security code that is printed to the console when the command is run.
	 * Security codes expire after 5 minutes.
	 *
	 * @param event The message event
	 * @param args The command arguments
	 * @return true if the command was successful, false otherwise
	 */
	suspend fun handleRemoveCommand(event: MessageCreateEvent, args: List<String>): Boolean {
		val userId = event.message.author?.id?.value?.toString() ?: return false

		if (args.isEmpty()) {
			event.message.channel.createMessage("Please specify a user ID to remove: admin remove <user_id> [code]")
			return false
		}

		val targetUserId = args[0]

		// Check if the target user is an admin
		if (!dbManager.isAdmin(targetUserId)) {
			event.message.channel.createMessage("User with ID $targetUserId is not an admin.")
			return false
		}

		// Check if trying to remove self
		if (targetUserId == userId) {
			event.message.channel.createMessage("You cannot remove yourself from admin status.")
			return false
		}

		// If a code is provided, validate it and remove the admin
		if (args.size >= 2) {
			val code = args[1]
			val codeType = dbManager.validateAndUseCode(code, userId)

			if (codeType == null || codeType != "admin_remove") {
				event.message.channel.createMessage("Invalid, expired, or already used security code. Note that codes expire after 5 minutes.")
				return false
			}

			// Remove the admin
			if (dbManager.removeAdminUser(targetUserId)) {
				event.message.channel.createMessage("User with ID $targetUserId has been removed from admin status.")
				logger.info("Admin $userId removed admin $targetUserId")
				return true
			} else {
				event.message.channel.createMessage("Failed to remove user from admin status. Please try again later.")
				return false
			}
		} else {
			// Generate a security code and print it to the console
			val securityCode = dbManager.createVerificationCode("admin_remove", userId)

			if (securityCode != null) {
				println("\n=============================================================")
				println("SECURITY CODE FOR ADMIN REMOVAL: $securityCode")
				println("This code is required to remove user $targetUserId from admin status.")
				println("This code will expire after 5 minutes.")
				println("=============================================================\n")

				event.message.channel.createMessage(
					"A security code has been printed to the console. " +
						"To confirm removal, run: admin remove $targetUserId <security_code>\n" +
						"Note: The security code will expire after 5 minutes."
				)
				return true
			} else {
				event.message.channel.createMessage("Failed to generate a security code. Please try again later.")
				return false
			}
		}
	}
}
