package commands

import database.DatabaseManager
import dev.kord.core.event.message.MessageCreateEvent
import org.slf4j.LoggerFactory

/**
 * Command for admin-related functionality.
 * Allows users to verify themselves as admins using a verification code,
 * and provides admin-only commands for managing the bot.
 */
class AdminCommand : Command {
	override val name = "admin"
	override val description = "Admin management commands (verify, generate, list, remove)"

	private val dbManager = DatabaseManager.getInstance()

	override suspend fun execute(event: MessageCreateEvent): Boolean {
		logger.info("Executing admin command")

		val messageText = extractMessageText(event)
		val args = messageText.split(" ")

		if (args.isEmpty() || args[0].isEmpty()) {
			event.message.channel.createMessage("Please specify a subcommand: verify, generate, list, or remove")
			return false
		}

		val subcommand = args[0].lowercase()

		// Allow the verify command without admin check
		if (subcommand == "verify") {
			return handleVerifyCommand(event, args)
		}

		// Check if the user is an admin for all other commands
		val userId = event.message.author?.id?.value?.toString() ?: return false
		if (!dbManager.isAdmin(userId)) {
			event.message.channel.createMessage("You need to be an admin to use this command.")
			return false
		}

		return when (subcommand) {
			"generate" -> handleGenerateCommand(event, args)
			"list" -> handleListCommand(event)
			"remove" -> handleRemoveCommand(event, args)
			else -> {
				event.message.channel.createMessage("Unknown subcommand: ${args[0]}. Available subcommands: verify, generate, list, remove")
				false
			}
		}
	}

	/**
	 * Handles the verify subcommand, which allows users to verify themselves as admins.
	 */
	private suspend fun handleVerifyCommand(event: MessageCreateEvent, args: List<String>): Boolean {
		if (args.size < 2) {
			event.message.channel.createMessage("Please provide a verification code: admin verify <code>")
			return false
		}

		val code = args[1]
		val userId = event.message.author?.id?.value?.toString() ?: return false
		val username = event.message.author?.username ?: "Unknown"

		// Validate the code
		val codeType = dbManager.validateAndUseCode(code, userId)

		if (codeType == null) {
			event.message.channel.createMessage("Invalid or already used verification code.")
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

	/**
	 * Handles the generate subcommand, which generates new verification codes.
	 * This subcommand is only available to admins.
	 */
	private suspend fun handleGenerateCommand(event: MessageCreateEvent, args: List<String>): Boolean {
		val userId = event.message.author?.id?.value?.toString() ?: return false

		// Generate a new verification code
		val code = dbManager.createVerificationCode("admin", userId)

		if (code != null) {
			event.message.channel.createMessage("Generated new admin verification code: $code")
			logger.info("Admin $userId generated a new verification code")
			return true
		} else {
			event.message.channel.createMessage("Failed to generate a verification code. Please try again later.")
			return false
		}
	}

	/**
	 * Handles the list subcommand, which lists all admin users.
	 * This subcommand is only available to admins.
	 */
	private suspend fun handleListCommand(event: MessageCreateEvent): Boolean {
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

	/**
	 * Handles the remove subcommand, which removes a user from admin status.
	 * This subcommand is only available to admins and requires a security code
	 * that is printed to the console when the command is run.
	 */
	private suspend fun handleRemoveCommand(event: MessageCreateEvent, args: List<String>): Boolean {
		val userId = event.message.author?.id?.value?.toString() ?: return false

		if (args.size < 2) {
			event.message.channel.createMessage("Please specify a user ID to remove: admin remove <user_id> [code]")
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
				event.message.channel.createMessage("Invalid or already used security code.")
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
				println("=============================================================\n")

				event.message.channel.createMessage(
					"A security code has been printed to the console. " +
						"To confirm removal, run: admin remove $targetUserId <security_code>"
				)
				return true
			} else {
				event.message.channel.createMessage("Failed to generate a security code. Please try again later.")
				return false
			}
		}
	}

	companion object {
		/**
		 * Generates a one-time admin verification code if no admins exist.
		 * This is called during bot startup to ensure there's a way to set up the first admin.
		 *
		 * @param dbManager The database manager instance
		 * @return true if a code was generated, false if admins already exist or generation failed
		 */
		fun generateOneTimeAdminCode(dbManager: DatabaseManager): Boolean {
			// Get a logger for this companion object
			val logger = LoggerFactory.getLogger("AdminCommand")

			// Check if any admins exist
			val admins = dbManager.getAllAdmins()
			if (admins.isEmpty()) {
				val adminCode = dbManager.createVerificationCode("admin")
				if (adminCode != null) {
					println("\n=============================================================")
					println("ADMIN VERIFICATION CODE: $adminCode")
					println("Use this code with the 'admin verify' command to become admin")
					println("This code can only be used once and will expire after restart")
					println("=============================================================\n")
					logger.info("Generated one-time admin verification code")
					return true
				} else {
					logger.error("Failed to generate admin verification code")
					return false
				}
			} else {
				logger.info("Admin users already exist, skipping verification code generation")
				return false
			}
		}
	}
}
