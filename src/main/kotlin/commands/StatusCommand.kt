package commands

import commands.status.StatusMonitorCommand
import commands.status.functions.*
import dev.kord.core.event.message.MessageCreateEvent

/**
 * Main command for status functionality.
 * Uses specialized function handlers for different aspects of status management.
 */
class StatusCommand : Command {
	override val name = "status"
	override val description = "Monitors server status with subcommands: check, add, delete, list, help"

	// Reference to monitor command class
	private val monitorCommand = StatusMonitorCommand.getInstance()

	override suspend fun execute(event: MessageCreateEvent): Boolean {
		logger.info("Executing status command")

		val messageText = extractMessageText(event)
		val parts = messageText.split(" ")
		val subcommand = if (parts.isNotEmpty()) parts[0].lowercase() else "help"
		val args = if (parts.size > 1) parts.subList(1, parts.size) else emptyList()

		// Call the appropriate function handler based on the subcommand
		return when (subcommand) {
			"check" -> {
				CheckCommandHandler.handleCheckCommand(event, args)
				true
			}

			"add" -> {
				AddCommandHandler.handleAddCommand(event, args)
				true
			}

			"delete", "remove" -> {
				DeleteCommandHandler.handleDeleteCommand(event, args)
				true
			}

			"list" -> {
				ListCommandHandler.handleListCommand(event)
				true
			}
			// Monitor commands
			"monitor", "start", "stop" -> {
				// Extract the monitor subcommand and pass it to the monitor command
				val monitorSubcommand = if (parts.size > 1) parts[1] else ""
				val monitorArgs = if (parts.size > 2) parts.subList(2, parts.size) else emptyList()
				val monitorMessageText = "$monitorSubcommand ${monitorArgs.joinToString(" ")}"

				// Create a new message event with the modified message text
				val monitorEvent = event // In a real implementation, we would create a modified event
				monitorCommand.execute(monitorEvent)
			}
			// Help command
			else -> {
				HelpCommandHandler.handleHelpCommand(event)
				true
			}
		}
	}
}
