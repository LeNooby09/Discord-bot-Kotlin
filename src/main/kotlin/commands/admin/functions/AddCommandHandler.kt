package commands.admin.functions

import database.DatabaseManager
import dev.kord.common.entity.Snowflake
import dev.kord.core.event.message.MessageCreateEvent
import org.slf4j.LoggerFactory

/**
 * Handler for the 'add' subcommand of the admin command.
 * Adds a user as an admin by their user ID.
 */
object AddCommandHandler {
	private val logger = LoggerFactory.getLogger(AddCommandHandler::class.java)
	private val dbManager = DatabaseManager.getInstance()

	/**
	 * Handles the add command to add a user as an admin by their user ID.
	 * This subcommand requires a security code that is printed to the console when the command is run.
	 * Security codes expire after 5 minutes.
	 *
	 * @param event The message event
	 * @param args The command arguments
	 * @return true if the command was successful, false otherwise
	 */
	suspend fun handleAddCommand(event: MessageCreateEvent, args: List<String>): Boolean {
		val userId = event.message.author?.id?.value?.toString() ?: return false

		if (args.isEmpty()) {
			event.message.channel.createMessage("Please specify a user ID to add as admin: admin add <user_id> [code]")
			return false
		}

		val targetUserId = args[0]

		// Check if the target user is already an admin
		if (dbManager.isAdmin(targetUserId)) {
			event.message.channel.createMessage("User with ID $targetUserId is already an admin.")
			return false
		}

		// If a code is provided, validate it and add the admin
		if (args.size >= 2) {
			val code = args[1]
			val codeType = dbManager.validateAndUseCode(code, userId)

			if (codeType == null || codeType != "admin_add") {
				event.message.channel.createMessage("Invalid, expired, or already used security code. Note that codes expire after 5 minutes.")
				return false
			}

			// Try to look up the username for the target user ID
			val targetUser = try {
				event.kord.getUser(Snowflake(targetUserId))
			} catch (e: Exception) {
				logger.warn("Failed to convert user ID to Snowflake: $targetUserId", e)
				null
			}

			// Get the username if available, otherwise use a placeholder
			val targetUsername = targetUser?.username ?: "User ID: $targetUserId"

			// Add the admin with the resolved username
			if (dbManager.addAdminUser(targetUserId, targetUsername)) {
				event.message.channel.createMessage("User with ID $targetUserId (${targetUser?.username ?: "unknown username"}) has been added as an admin.")
				logger.info("Admin $userId added new admin $targetUserId (${targetUser?.username ?: "unknown username"})")
				return true
			} else {
				event.message.channel.createMessage("Failed to add user as admin. Please try again later.")
				return false
			}
		} else {
			// Generate a security code and print it to the console
			val securityCode = dbManager.createVerificationCode("admin_add", userId)

			if (securityCode != null) {
				println("\n=============================================================")
				println("SECURITY CODE FOR ADMIN ADDITION: $securityCode")
				println("This code is required to add user $targetUserId as an admin.")
				println("This code will expire after 5 minutes.")
				println("=============================================================\n")

				event.message.channel.createMessage(
					"A security code has been printed to the console. " +
						"To confirm addition, run: admin add $targetUserId <security_code>\n" +
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
