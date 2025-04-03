package commands.admin

import database.DatabaseManager
import dev.kord.core.event.message.MessageCreateEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import utils.StatusManager
import kotlin.system.exitProcess

/**
 * Command for admin shutdown functionality.
 * Allows admins to remotely stop the bot server.
 */
class AdminShutdownCommand : commands.Command {
	override val name = "adminshutdown"
	override val description = "Admin shutdown command to stop the bot server"

	private val dbManager = DatabaseManager.getInstance()

	override suspend fun execute(event: MessageCreateEvent): Boolean {
		logger.info("Executing admin shutdown command")

		val messageText = extractMessageText(event)
		val args = messageText.split(" ")

		// Check if the user is an admin
		val userId = event.message.author?.id?.value?.toString() ?: return false
		if (!dbManager.isAdmin(userId)) {
			event.message.channel.createMessage("You need to be an admin to use this command.")
			return false
		}

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
						"To confirm shutdown, run: adminshutdown <security_code>\n" +
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
