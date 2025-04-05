package commands.admin.functions

import database.DatabaseManager
import dev.kord.core.event.message.MessageCreateEvent
import org.slf4j.LoggerFactory

/**
 * Handler for the 'generate' subcommand of the admin command.
 * Generates new verification codes.
 */
object GenerateCommandHandler {
	private val logger = LoggerFactory.getLogger(GenerateCommandHandler::class.java)
	private val dbManager = DatabaseManager.getInstance()

	/**
	 * Handles the generate command to generate a new verification code.
	 *
	 * @param event The message event
	 * @param args The command arguments
	 * @return true if the command was successful, false otherwise
	 */
	suspend fun handleGenerateCommand(event: MessageCreateEvent, args: List<String>): Boolean {
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
}
