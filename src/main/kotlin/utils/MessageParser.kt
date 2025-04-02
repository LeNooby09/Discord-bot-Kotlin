package utils

import dev.kord.core.event.message.MessageCreateEvent

/**
 * Utility class for parsing command messages.
 * Extracts command name and arguments from messages based on the prefix used.
 */
class MessageParser {
	/**
	 * Parses a message and extracts the command name and arguments.
	 *
	 * @param event The message event to parse
	 * @return A ParsedCommand object containing the command name and the original event,
	 *         or null if the message doesn't start with a valid prefix
	 */
	suspend fun parseMessage(event: MessageCreateEvent): ParsedCommand? {
		val content = event.message.content
		val authorName = event.message.author?.username ?: "Unknown"
		val channelId = event.message.channelId.toString()
		val logger = logger()

		// Check if this is a DM channel
		val isDM = event.message.getChannel().type == dev.kord.common.entity.ChannelType.DM

		// Variables to store command information
		var commandText = ""
		var commandName = ""

		// Process messages differently based on whether they're from a DM or not
		if (isDM) {
			// In DMs, process the message directly as a command without requiring the mention
			logger.info("Received DM command message from $authorName in channel $channelId: $content")

			// Extract the command name from the message
			commandText = content.trim()
			commandName = commandText.split(" ")[0]
			logger.debug("Parsed DM command name: $commandName")
		} else {
			// In servers, check for both the mention and the custom prefix
			val serverId = try {
				event.message.getGuild().id.toString()
			} catch (e: Exception) {
				logger.error("Failed to get guild ID", e)
				return null
			}
			val prefixManager = PrefixManager.getInstance()
			val serverPrefix: String = prefixManager.getServerPrefix(serverId)

			// Check if the message starts with either the mention or the custom prefix
			if (content.startsWith(BotConfig.mention)) {
				logger.info("Received server command message (mention) from $authorName in channel $channelId: $content")

				// Extract the command name from the message
				commandText = content.removePrefix(BotConfig.mention).trim()
				commandName = commandText.split(" ")[0]
				logger.debug("Parsed server command name (mention): $commandName")
			} else if (content.startsWith(serverPrefix)) {
				logger.info("Received server command message (prefix: $serverPrefix) from $authorName in channel $channelId: $content")

				// Extract the command name from the message
				commandText = content.removePrefix(serverPrefix).trim()
				commandName = commandText.split(" ")[0]
				logger.debug("Parsed server command name (prefix): $commandName")
			} else {
				// Skip messages that don't start with either the mention or the custom prefix
				return null
			}
		}

		// If command name is empty, default to help command
		val effectiveCommandName = commandName.ifEmpty { "help" }

		return ParsedCommand(effectiveCommandName, event)
	}
}

/**
 * Data class representing a parsed command.
 *
 * @property commandName The name of the command
 * @property event The original message event
 */
data class ParsedCommand(
	val commandName: String,
	val event: MessageCreateEvent
)
