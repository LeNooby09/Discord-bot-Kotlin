package commands

import commands.admin.functions.*
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
	override val description = "Admin management commands"

	private val dbManager = DatabaseManager.getInstance()
	override val logger = LoggerFactory.getLogger(AdminCommand::class.java)

	override suspend fun execute(event: MessageCreateEvent): Boolean {
		logger.info("Executing admin command")

		val messageText = extractMessageText(event)
		val args = messageText.split(" ")

		if (args.isEmpty() || args[0].isEmpty()) {
			event.message.channel.createMessage("Please specify a subcommand: verify, generate, list, remove, add, status, flush, or stop")
			return false
		}

		val subcommand = args[0].lowercase()
		val subArgs = if (args.size > 1) args.subList(1, args.size) else emptyList()

		// Allow the verify command without admin check
		if (subcommand == "verify") {
			return VerifyCommandHandler.handleVerifyCommand(event, subArgs)
		}

		// Check if the user is an admin for all other commands
		val userId = event.message.author?.id?.value?.toString() ?: return false
		if (!dbManager.isAdmin(userId)) {
			event.message.channel.createMessage("You need to be an admin to use this command.")
			return false
		}

		return when (subcommand) {
			"generate" -> GenerateCommandHandler.handleGenerateCommand(event, subArgs)
			"list" -> ListCommandHandler.handleListCommand(event)
			"remove" -> RemoveCommandHandler.handleRemoveCommand(event, subArgs)
			"flush" -> FlushCommandHandler.handleFlushCommand(event, subArgs)
			"add" -> AddCommandHandler.handleAddCommand(event, subArgs)
			"status" -> StatusCommandHandler.handleStatusCommand(event, subArgs)
			"stop" -> StopCommandHandler.handleStopCommand(event, subArgs)
			else -> {
				event.message.channel.createMessage("Unknown subcommand: ${args[0]}. Available subcommands: verify, generate, list, remove, flush, add, status, stop")
				false
			}
		}
	}

	companion object {
		/**
		 * Generates a one-time admin verification code if no admins exist.
		 * This is called during bot startup to ensure there's a way to set up the first admin.
		 * The verification code expires after 5 minutes.
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
					println("This code can only be used once and will expire after 5 minutes")
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
