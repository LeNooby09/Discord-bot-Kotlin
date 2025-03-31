package commands

import database.DatabaseManager
import dev.kord.core.event.message.MessageCreateEvent

/**
 * Command that allows server administrators to manage the custom prefix for their server.
 * Supports setting a new prefix, viewing the current prefix, and resetting to the default.
 */
class PrefixCommand : Command {
	override val name = "prefix"
	override val description = "Manage the custom prefix for this server (admin only)"

	override suspend fun execute(event: MessageCreateEvent): Boolean {
		val messageText = extractMessageText(event)
		val args = messageText.split(" ").filter { it.isNotEmpty() }
		val dbManager = DatabaseManager.getInstance()

		// Check if this is a DM channel
		val isDM = event.message.getChannel().type == dev.kord.common.entity.ChannelType.DM
		if (isDM) {
			event.message.channel.createMessage("This command can only be used in a server.")
			return false
		}

		// Get the server ID
		val serverId = try {
			event.message.getGuild().id.toString()
		} catch (e: Exception) {
			logger.error("Failed to get guild ID in PrefixCommand", e)
			event.message.channel.createMessage("Failed to get server information. Please try again later.")
			return false
		}
		val userId = event.message.author?.id?.toString() ?: ""

		// Check if the user is an admin
		if (!dbManager.isAdmin(userId)) {
			event.message.channel.createMessage("You must be a bot admin to use this command.")
			return false
		}

		// Handle subcommands
		if (args.isEmpty()) {
			// Display current prefix
			val currentPrefix = dbManager.getServerPrefix(serverId)
			event.message.channel.createMessage("Current prefix for this server: `$currentPrefix`")
			return true
		}

		when (args[0].lowercase()) {
			"set" -> {
				if (args.size < 2) {
					event.message.channel.createMessage("Please specify a prefix to set. Usage: `prefix set <new_prefix>`")
					return false
				}

				val newPrefix = args[1]
				if (newPrefix.length > 10) {
					event.message.channel.createMessage("Prefix must be 10 characters or less.")
					return false
				}

				if (dbManager.setServerPrefix(serverId, newPrefix)) {
					event.message.channel.createMessage("Server prefix set to: `$newPrefix`")
					return true
				} else {
					event.message.channel.createMessage("Failed to set server prefix.")
					return false
				}
			}

			"reset" -> {
				if (dbManager.removeServerPrefix(serverId)) {
					event.message.channel.createMessage("Server prefix reset to default: `!`")
					return true
				} else {
					event.message.channel.createMessage("Failed to reset server prefix.")
					return false
				}
			}

			"help" -> {
				val helpMessage = """
                    **Prefix Command Help**

                    Manage the custom prefix for this server (admin only).

                    **Subcommands:**
                    > `prefix` - Display the current prefix
                    > `prefix set <new_prefix>` - Set a new prefix
                    > `prefix reset` - Reset to the default prefix (!)
                    > `prefix help` - Show this help message

                    Note: The bot will always respond to its mention regardless of the custom prefix.
                """.trimIndent()

				event.message.channel.createMessage(helpMessage)
				return true
			}

			else -> {
				event.message.channel.createMessage("Unknown subcommand. Use `prefix help` for usage information.")
				return false
			}
		}
	}
}
