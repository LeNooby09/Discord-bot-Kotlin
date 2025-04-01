package commands

import database.BotCustomizationManager
import dev.kord.core.event.message.MessageCreateEvent
import org.slf4j.LoggerFactory
import utils.StatusManager

/**
 * Handles status-related functionality for admin commands.
 * This class is responsible for managing custom bot statuses and presence status.
 */
class AdminStatusHandler {
	private val logger = LoggerFactory.getLogger(AdminStatusHandler::class.java)

	/**
	 * Handles the status subcommand, which allows admins to manage custom bot statuses.
	 * Supports adding, listing, removing custom statuses, and setting the bot's presence status.
	 */
	suspend fun handleStatusCommand(event: MessageCreateEvent, args: List<String>): Boolean {
		if (args.size < 2) {
			return handleStatusHelpCommand(event)
		}

		val statusSubcommand = args[1].lowercase()

		return when (statusSubcommand) {
			"add" -> handleStatusAddCommand(event, args.drop(2))
			"list" -> handleStatusListCommand(event)
			"remove" -> handleStatusRemoveCommand(event, args.drop(2))
			"presence" -> handleStatusPresenceCommand(event, args.drop(2))
			"set" -> handleStatusSetCommand(event, args.drop(2))
			"setid" -> handleStatusSetIdCommand(event, args.drop(2))
			else -> handleStatusHelpCommand(event)
		}
	}

	/**
	 * Handles the status add subcommand, which adds a new custom status.
	 */
	private suspend fun handleStatusAddCommand(event: MessageCreateEvent, args: List<String>): Boolean {
		if (args.size < 2) {
			event.message.channel.createMessage("Please specify a status type and text. Usage: admin status add <type> <text>")
			return false
		}

		val statusType = args[0].lowercase()
		val statusText = args.drop(1).joinToString(" ")

		// Validate status type
		if (!listOf("playing", "watching", "listening", "competing").contains(statusType)) {
			event.message.channel.createMessage("Invalid status type. Valid types are: playing, watching, listening, competing")
			return false
		}

		// Add the custom status
		val botCustomizationManager = BotCustomizationManager.getInstance()
		val statusId = botCustomizationManager.addCustomStatus(statusText, statusType)

		if (statusId > 0) {
			event.message.channel.createMessage("Added custom status: $statusType $statusText (ID: $statusId)")
			return true
		} else {
			event.message.channel.createMessage("Failed to add custom status. Please try again later.")
			return false
		}
	}

	/**
	 * Handles the status list subcommand, which lists all custom statuses.
	 */
	private suspend fun handleStatusListCommand(event: MessageCreateEvent): Boolean {
		val botCustomizationManager = BotCustomizationManager.getInstance()
		val statuses = botCustomizationManager.getAllCustomStatuses()

		if (statuses.isEmpty()) {
			event.message.channel.createMessage("No custom statuses found.")
			return true
		}

		val message = buildString {
			append("**Custom Bot Statuses**\n\n")

			statuses.forEach { status ->
				val (id, text, type) = status
				append("ID: $id - $type $text\n")
			}

			append("\nUse `admin status remove <id>` to remove a status.")
		}

		event.message.channel.createMessage(message)
		return true
	}

	/**
	 * Handles the status remove subcommand, which removes a custom status.
	 */
	private suspend fun handleStatusRemoveCommand(event: MessageCreateEvent, args: List<String>): Boolean {
		if (args.isEmpty()) {
			event.message.channel.createMessage("Please specify a status ID to remove. Usage: admin status remove <id>")
			return false
		}

		val statusId = try {
			args[0].toLong()
		} catch (e: NumberFormatException) {
			event.message.channel.createMessage("Invalid status ID. Please provide a valid number.")
			return false
		}

		val botCustomizationManager = BotCustomizationManager.getInstance()
		if (botCustomizationManager.removeCustomStatus(statusId)) {
			event.message.channel.createMessage("Custom status with ID $statusId removed successfully.")
			return true
		} else {
			event.message.channel.createMessage("Failed to remove custom status with ID $statusId. It may not exist.")
			return false
		}
	}

	/**
	 * Handles the status presence subcommand, which sets the bot's presence status.
	 */
	private suspend fun handleStatusPresenceCommand(event: MessageCreateEvent, args: List<String>): Boolean {
		if (args.isEmpty()) {
			event.message.channel.createMessage("Please specify a presence status. Valid values: online, idle, dnd, invisible")
			return false
		}

		val statusType = args[0]

		// Use the StatusManager to set the presence status
		val statusManager = StatusManager.getInstance()
		val (success, message) = statusManager.setPresenceStatusFromString(event.kord, statusType)

		// Send the result message
		event.message.channel.createMessage(message)
		return success
	}

	/**
	 * Handles the status set subcommand, which directly sets a custom status.
	 */
	private suspend fun handleStatusSetCommand(event: MessageCreateEvent, args: List<String>): Boolean {
		if (args.size < 2) {
			event.message.channel.createMessage("Please specify a status type and text. Usage: admin status set <type> <text>")
			return false
		}

		val statusType = args[0]
		val statusText = args.drop(1).joinToString(" ")

		// Use the StatusManager to set the custom status
		val statusManager = StatusManager.getInstance()
		val (success, message) = statusManager.setCustomStatus(event.kord, statusText, statusType)

		// Send the result message
		event.message.channel.createMessage(message)
		return success
	}

	/**
	 * Handles the status setid subcommand, which sets a custom status by ID.
	 */
	private suspend fun handleStatusSetIdCommand(event: MessageCreateEvent, args: List<String>): Boolean {
		if (args.isEmpty()) {
			event.message.channel.createMessage("Please specify a status ID. Usage: admin status setid <id>")
			return false
		}

		val statusId = try {
			args[0].toLong()
		} catch (e: NumberFormatException) {
			event.message.channel.createMessage("Invalid status ID. Please provide a valid number.")
			return false
		}

		// Use the StatusManager to set the custom status by ID
		val statusManager = StatusManager.getInstance()
		val (success, message) = statusManager.setCustomStatusById(event.kord, statusId)

		// Send the result message
		event.message.channel.createMessage(message)
		return success
	}


	/**
	 * Handles the status help subcommand, which shows help for the status command.
	 */
	private suspend fun handleStatusHelpCommand(event: MessageCreateEvent): Boolean {
		val helpText = """
            **Bot Status Management Help**

            Manage custom bot statuses with the following subcommands:

            `admin status add <type> <text>` - Add a new custom status
            `admin status list` - List all custom statuses
            `admin status remove <id>` - Remove a custom status by ID
            `admin status presence <status>` - Set the bot's presence status
            `admin status set <type> <text>` - Directly set a custom status
            `admin status setid <id>` - Set a custom status by ID

            Valid status types for add/set: playing, watching, listening, competing
            Valid presence statuses: online, idle, dnd, invisible

            Examples:
            `admin status add playing Minecraft`
            `admin status add watching over the server`
            `admin status add listening to music`
            `admin status presence idle`
            `admin status set playing Among Us`
            `admin status setid 5`
        """.trimIndent()

		event.message.channel.createMessage(helpText)
		return true
	}

	companion object {
		private var instance: AdminStatusHandler? = null

		/**
		 * Gets the singleton instance of the AdminStatusHandler.
		 * @return The AdminStatusHandler instance
		 */
		fun getInstance(): AdminStatusHandler {
			if (instance == null) {
				instance = AdminStatusHandler()
			}
			return instance!!
		}
	}
}
