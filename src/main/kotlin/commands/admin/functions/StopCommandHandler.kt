package commands.admin.functions

import database.DatabaseManager
import dev.kord.core.event.message.MessageCreateEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import utils.StatusManager
import kotlin.system.exitProcess

/**
 * Handler for the 'stop' subcommand of the admin command.
 * Allows admins to remotely stop the bot server.
 */
object StopCommandHandler {
	private val logger = LoggerFactory.getLogger(StopCommandHandler::class.java)
	private val dbManager = DatabaseManager.getInstance()

	/**
	 * Handles the stop command to remotely stop the bot server.
	 * This is a sensitive operation that requires a security code for confirmation.
	 * The security code expires after 5 minutes.
	 *
	 * @param event The message event
	 * @param args The command arguments
	 * @return true if the command was successful, false otherwise
	 */
	suspend fun handleStopCommand(event: MessageCreateEvent, args: List<String>): Boolean {
		val userId = event.message.author?.id?.value?.toString() ?: return false

		// If a code is provided, validate it and stop the server
		if (args.isNotEmpty()) {
			val code = args[0]
			val codeType = dbManager.validateAndUseCode(code, userId)

			if (codeType == null || codeType != "admin_stop") {
				event.message.channel.createMessage("Invalid, expired, or already used security code. Note that codes expire after 5 minutes.")
				return false
			}

			// Send confirmation message before stopping
			event.message.channel.createMessage("**Server shutdown initiated by admin.** The bot will shut down in 5 seconds.")
			logger.info("Admin $userId initiated server shutdown")

			// Launch a coroutine to handle the shutdown after a delay
			event.kord.launch {
				// Wait 5 seconds to allow the message to be sent
				delay(5000)

				// Stop status updates
				StatusManager.getInstance().stopStatusUpdates()

				// Close the database connection
				dbManager.close()

				// Shutdown the Kord client
				event.kord.shutdown()

				// Log the shutdown
				logger.info("Bot server shutting down by admin command")

				// Exit the application
				exitProcess(0)
			}

			return true
		} else {
			// Generate a security code and print it to the console
			val securityCode = dbManager.createVerificationCode("admin_stop", userId)

			if (securityCode != null) {
				println("\n=============================================================")
				println("SECURITY CODE FOR BOT SERVER SHUTDOWN: $securityCode")
				println("This code is required to stop the bot server.")
				println("This code will expire after 5 minutes.")
				println("=============================================================\n")

				event.message.channel.createMessage(
					"**WARNING**: This will shut down the bot server completely.\n" +
						"A security code has been printed to the console. " +
						"To confirm server shutdown, run: admin stop <security_code>\n" +
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
