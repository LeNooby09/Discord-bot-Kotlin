package commands.admin.functions

import database.DatabaseManager
import dev.kord.core.event.message.MessageCreateEvent
import org.slf4j.LoggerFactory

/**
 * Handler for the 'flush' subcommand of the admin command.
 * Flushes the database (deletes all data except admin users).
 */
object FlushCommandHandler {
	private val logger = LoggerFactory.getLogger(FlushCommandHandler::class.java)
	private val dbManager = DatabaseManager.getInstance()

	/**
	 * Handles the flush command to flush the database.
	 * This subcommand requires a security code that is printed to the console when the command is run.
	 * Security codes expire after 5 minutes.
	 *
	 * @param event The message event
	 * @param args The command arguments
	 * @return true if the command was successful, false otherwise
	 */
	suspend fun handleFlushCommand(event: MessageCreateEvent, args: List<String>): Boolean {
		val userId = event.message.author?.id?.value?.toString() ?: return false

		// If a code is provided, validate it and flush the database
		if (args.isNotEmpty()) {
			val code = args[0]
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
					"A security code has been printed to the console. " +
						"To confirm database flush, run: admin flush <security_code>\n" +
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
