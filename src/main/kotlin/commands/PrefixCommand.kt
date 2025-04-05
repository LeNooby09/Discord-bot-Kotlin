package commands

import commands.prefix.functions.DisplayCommandHandler
import commands.prefix.functions.HelpCommandHandler
import commands.prefix.functions.ResetCommandHandler
import commands.prefix.functions.SetCommandHandler
import database.DatabaseManager
import dev.kord.common.entity.Permission
import dev.kord.core.event.message.MessageCreateEvent
import org.slf4j.LoggerFactory

/**
 * Command that allows server administrators to manage the custom prefix for their server.
 * Supports setting a new prefix, viewing the current prefix, and resetting to the default.
 */
class PrefixCommand : Command {
	override val name = "prefix"
	override val description = "Manage the custom prefix for this server (server admin only)"
	override val logger = LoggerFactory.getLogger(PrefixCommand::class.java)
	private val dbManager = DatabaseManager.getInstance()

	override suspend fun execute(event: MessageCreateEvent): Boolean {
		val messageText = extractMessageText(event)
		val args = messageText.split(" ").filter { it.isNotEmpty() }

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

		// Check if the user is a server admin
		val member = try {
			event.message.getAuthorAsMember()
		} catch (e: Exception) {
			logger.error("Failed to get member in PrefixCommand", e)
			event.message.channel.createMessage("Failed to get user information. Please try again later.")
			return false
		}

		// Check if the member has administrator permission
		val hasAdminPermission = member.getPermissions().contains(Permission.Administrator)
		if (!hasAdminPermission) {
			event.message.channel.createMessage("You must be a server administrator to use this command.")
			return false
		}

		// Handle subcommands
		if (args.isEmpty()) {
			// Display current prefix
			return DisplayCommandHandler.handleDisplayCommand(event, serverId)
		}

		val subcommand = args[0].lowercase()
		val subArgs = if (args.size > 1) args.subList(1, args.size) else emptyList()

		return when (subcommand) {
			"set" -> SetCommandHandler.handleSetCommand(event, subArgs, serverId)
			"reset" -> ResetCommandHandler.handleResetCommand(event, serverId)
			"help" -> HelpCommandHandler.handleHelpCommand(event)
			else -> {
				event.message.channel.createMessage("Unknown subcommand. Use `prefix help` for usage information.")
				false
			}
		}
	}
}
