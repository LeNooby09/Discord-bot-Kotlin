package commands.admin

import database.DatabaseManager
import dev.kord.common.entity.Snowflake
import dev.kord.core.event.message.MessageCreateEvent

/**
 * Command for admin user management functionality.
 * Allows admins to add, remove, and list admin users.
 */
class AdminUserManagementCommand : commands.Command {
	override val name = "adminuser"
	override val description = "Admin user management commands"

	private val dbManager = DatabaseManager.getInstance()

	override suspend fun execute(event: MessageCreateEvent): Boolean {
		logger.info("Executing admin user management command")

		val messageText = extractMessageText(event)
		val args = messageText.split(" ")

		if (args.isEmpty() || args[0].isEmpty()) {
			event.message.channel.createMessage("Please specify a subcommand: list, add, or remove")
			return false
		}

		// Check if the user is an admin for all commands
		val userId = event.message.author?.id?.value?.toString() ?: return false
		if (!dbManager.isAdmin(userId)) {
			event.message.channel.createMessage("You need to be an admin to use this command.")
			return false
		}

		val subcommand = args[0].lowercase()

		return when (subcommand) {
			"list" -> handleListCommand(event)
			"add" -> handleAddCommand(event, args)
			"remove" -> handleRemoveCommand(event, args)
			else -> {
				event.message.channel.createMessage("Unknown subcommand: ${args[0]}. Available subcommands: list, add, remove")
				false
			}
		}
	}

	/**
	 * Handles the list subcommand, which lists all admin users.
	 */
	private suspend fun handleListCommand(event: MessageCreateEvent): Boolean {
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

	/**
	 * Handles the add subcommand, which adds a user as an admin by their user ID.
	 * This subcommand requires a security code that is printed to the console when the command is run.
	 * Security codes expire after 5 minutes.
	 */
	private suspend fun handleAddCommand(event: MessageCreateEvent, args: List<String>): Boolean {
		val userId = event.message.author?.id?.value?.toString() ?: return false

		if (args.size < 2) {
			event.message.channel.createMessage("Please specify a user ID to add as admin: adminuser add <user_id> [code]")
			return false
		}

		val targetUserId = args[1]

		// Check if the target user is already an admin
		if (dbManager.isAdmin(targetUserId)) {
			event.message.channel.createMessage("User with ID $targetUserId is already an admin.")
			return false
		}

		// If a code is provided, validate it and add the admin
		if (args.size >= 3) {
			val code = args[2]
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
						"To confirm addition, run: adminuser add $targetUserId <security_code>\n" +
						"Note: The security code will expire after 5 minutes."
				)
				return true
			} else {
				event.message.channel.createMessage("Failed to generate a security code. Please try again later.")
				return false
			}
		}
	}

	/**
	 * Handles the remove subcommand, which removes a user from admin status.
	 * This subcommand requires a security code that is printed to the console when the command is run.
	 * Security codes expire after 5 minutes.
	 */
	private suspend fun handleRemoveCommand(event: MessageCreateEvent, args: List<String>): Boolean {
		val userId = event.message.author?.id?.value?.toString() ?: return false

		if (args.size < 2) {
			event.message.channel.createMessage("Please specify a user ID to remove: adminuser remove <user_id> [code]")
			return false
		}

		val targetUserId = args[1]

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
		if (args.size >= 3) {
			val code = args[2]
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
						"To confirm removal, run: adminuser remove $targetUserId <security_code>\n" +
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
