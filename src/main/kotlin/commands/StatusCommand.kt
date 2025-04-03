package commands

import commands.status.StatusCheckCommand
import commands.status.StatusDataCommand
import commands.status.StatusMonitorCommand
import dev.kord.core.event.message.MessageCreateEvent

/**
 * Main command for status functionality.
 * Delegates to specialized command classes for different aspects of status management.
 */
class StatusCommand : Command {
	override val name = "status"
	override val description = "Monitors server status with subcommands: check, add, delete, list, help"

	// References to specialized command classes
	private val dataCommand = StatusDataCommand.getInstance()
	private val checkCommand = StatusCheckCommand.getInstance()
	private val monitorCommand = StatusMonitorCommand.getInstance()

	override suspend fun execute(event: MessageCreateEvent): Boolean {
		logger.info("Executing status command")

		val messageText = extractMessageText(event)
		val parts = messageText.split(" ")
		val subcommand = if (parts.isNotEmpty()) parts[0].lowercase() else "help"

		// Delegate to the appropriate specialized command based on the subcommand
		return when (subcommand) {
			// Check commands
			"check", "add", "delete", "remove", "list" -> {
				// These commands are handled by the check command
				checkCommand.execute(event)
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
				handleHelpCommand(event)
				true
			}
		}
	}
    
	/**
	 * Handles the help command
	 */
	private suspend fun handleHelpCommand(event: MessageCreateEvent): Boolean {
		val helpText = """
            **Status Command Help**
            Monitor server status with the following subcommands:
            
            **Check Commands:**
            `status check <server>` - Check if a server is online
            `status add <server>` - Add a server to your personal monitoring list
            `status delete <server>` - Remove a server from your personal monitoring list
            `status list` - List all servers in your personal monitoring list and their status
            
            **Monitor Commands:**
            `status monitor start` - Start the server status monitoring
            `status monitor stop` - Stop the server status monitoring
            `status monitor status` - Check if monitoring is running
            
            Your server list is stored in a database and persists between bot restarts.
            The bot will automatically notify you when a server's status changes.
        """.trimIndent()

		event.message.channel.createMessage(helpText)
		return true
	}
}
