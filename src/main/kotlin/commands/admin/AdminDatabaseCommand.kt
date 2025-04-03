package commands.admin

import database.DatabaseManager
import dev.kord.core.event.message.MessageCreateEvent

/**
 * Command for admin database operations.
 * Allows admins to flush the database (delete all data except admin users).
 */
class AdminDatabaseCommand : commands.Command {
	override val name = "admindb"
	override val description = "Admin database management commands"

	private val dbManager = DatabaseManager.getInstance()

	override suspend fun execute(event: MessageCreateEvent): Boolean {
		logger.info("Executing admin database command")

		val messageText = extractMessageText(event)
		val args = messageText.split(" ")

		if (args.isEmpty() || args[0].isEmpty()) {
			event.message.channel.createMessage("Please specify a subcommand: flush")
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
			"flush" -> handleFlushCommand(event, args)
			else -> {
				event.message.channel.createMessage("Unknown subcommand: ${args[0]}. Available subcommands: flush")
				false
			}
		}
	}

	/**
	 * Handles the flush subcommand, which flushes the database (deletes all data except admin users).
	 * This subcommand requires a security code that is printed to the console when the command is run.
	 * Security codes expire after 5 minutes.
	 */
	private suspend fun handleFlushCommand(event: MessageCreateEvent, args: List<String>): Boolean {
		val userId = event.message.author?.id?.value?.toString() ?: return false

		// If a code is provided, validate it and flush the database
		if (args.size >= 2) {
			val code = args[1]
			val codeType = dbManager.validateAndUseCode(code, userId)

			if (codeType == null || codeType != "admin_flush") {
				event.message.channel.createMessage("Invalid, expired, or already used security code. Note that codes expire after 5 minutes.")
				return false
			}

			// Flush the database
			if (dbManager.flushDatabase()) {
				event.message.channel.createMessage("Database has been flushed successfully. All data except admin users has been deleted.")
				logger.info("Admin $userId flushed the database")
				return true
			} else {
				event.message.channel.createMessage("Failed to flush the database. Please try again later.")
				return false
			}
		} else {
			// Generate a security code and print it to the console
			val securityCode = dbManager.createVerificationCode("admin_flush", userId)

			if (securityCode != null) {
				println("\n=============================================================")
				println("SECURITY CODE FOR DATABASE FLUSH: $securityCode")
				println("This code is required to flush the database.")
				println("This code will expire after 5 minutes.")
				println("=============================================================\n")

				event.message.channel.createMessage(
					"**WARNING**: This will delete all data from the database except admin users.\n" +
						"A security code has been printed to the console. " +
						"To confirm database flush, run: admindb flush <security_code>\n" +
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
