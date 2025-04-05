package commands.admin.functions

import database.DatabaseManager
import dev.kord.core.event.message.MessageCreateEvent
import org.slf4j.LoggerFactory

/**
 * Handler for the 'verify' subcommand of the admin command.
 * Allows users to verify themselves as admins using a verification code.
 */
object VerifyCommandHandler {
	private val logger = LoggerFactory.getLogger(VerifyCommandHandler::class.java)
	private val dbManager = DatabaseManager.getInstance()

	/**
	 * Handles the verify command to verify a user as an admin.
	 *
	 * @param event The message event
	 * @param args The command arguments
	 * @return true if the command was successful, false otherwise
	 */
	suspend fun handleVerifyCommand(event: MessageCreateEvent, args: List<String>): Boolean {
		if (args.isEmpty()) {
			event.message.channel.createMessage("Please provide a verification code: admin verify <code>")
			return false
		}

		val code = args[0]
		val userId = event.message.author?.id?.value?.toString() ?: return false
		val username = event.message.author?.username ?: "Unknown"

		// Validate the code
		val codeType = dbManager.validateAndUseCode(code, userId)

		if (codeType == null) {
			event.message.channel.createMessage("Invalid, expired, or already used verification code. Note that codes expire after 5 minutes.")
			return false
		}

		if (codeType == "admin") {
			// Add the user as an admin
			if (dbManager.addAdminUser(userId, username)) {
				event.message.channel.createMessage("You have been verified as an admin! You now have access to admin commands.")
				logger.info("User $username ($userId) verified as admin")
				return true
			} else {
				event.message.channel.createMessage("Failed to add you as an admin. Please try again later.")
				return false
			}
		} else {
			event.message.channel.createMessage("This code cannot be used for admin verification.")
			return false
		}
	}
}
