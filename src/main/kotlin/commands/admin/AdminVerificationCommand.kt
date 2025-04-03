package commands.admin

import database.DatabaseManager
import dev.kord.core.event.message.MessageCreateEvent
import org.slf4j.LoggerFactory

/**
 * Command for admin verification functionality.
 * Allows users to verify themselves as admins using a verification code,
 * and allows admins to generate new verification codes.
 */
class AdminVerificationCommand : commands.Command {
	override val name = "adminverify"
	override val description = "Admin verification commands"

	// Use the logger from the Command interface
	private val dbManager = DatabaseManager.getInstance()

	override suspend fun execute(event: MessageCreateEvent): Boolean {
		logger.info("Executing admin verification command")

		val messageText = extractMessageText(event)
		val args = messageText.split(" ")

		if (args.isEmpty() || args[0].isEmpty()) {
			event.message.channel.createMessage("Please specify a subcommand: verify or generate")
			return false
		}

		val subcommand = args[0].lowercase()

		return when (subcommand) {
			"verify" -> handleVerifyCommand(event, args)
			"generate" -> handleGenerateCommand(event, args)
			else -> {
				event.message.channel.createMessage("Unknown subcommand: ${args[0]}. Available subcommands: verify, generate")
				false
			}
		}
	}

	/**
	 * Handles the verify subcommand, which allows users to verify themselves as admins.
	 */
	private suspend fun handleVerifyCommand(event: MessageCreateEvent, args: List<String>): Boolean {
		if (args.size < 2) {
			event.message.channel.createMessage("Please provide a verification code: adminverify verify <code>")
			return false
		}

		val code = args[1]
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

	/**
	 * Handles the generate subcommand, which generates new verification codes.
	 * This subcommand is only available to admins.
	 */
	private suspend fun handleGenerateCommand(event: MessageCreateEvent, args: List<String>): Boolean {
		val userId = event.message.author?.id?.value?.toString() ?: return false

		// Check if the user is an admin
		if (!dbManager.isAdmin(userId)) {
			event.message.channel.createMessage("You need to be an admin to use this command.")
			return false
		}

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

	companion object {
		/**
		 * Generates a one-time admin verification code if no admins exist.
		 * This is called during bot initialization.
		 * @param dbManager The database manager instance
		 */
		fun generateOneTimeAdminCode(dbManager: DatabaseManager) {
			val logger = LoggerFactory.getLogger(AdminVerificationCommand::class.java)

			// Check if there are any admins
			val admins = dbManager.getAllAdmins()

			if (admins.isEmpty()) {
				// Generate a one-time admin verification code
				val code = dbManager.createVerificationCode("admin")

				if (code != null) {
					println("\n=============================================================")
					println("FIRST-TIME ADMIN VERIFICATION CODE: $code")
					println("Use this code to verify yourself as the first admin.")
					println("Command: adminverify verify $code")
					println("This code will expire after 5 minutes.")
					println("=============================================================\n")

					logger.info("Generated first-time admin verification code")
				} else {
					logger.error("Failed to generate first-time admin verification code")
				}
			}
		}
	}
}
