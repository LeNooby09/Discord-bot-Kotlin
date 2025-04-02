package commands

import dev.kord.core.event.message.MessageCreateEvent
import org.slf4j.Logger
import utils.BotConfig
import utils.PrefixManager
import utils.logger

/**
 * Interface for all bot commands.
 * Each command should implement this interface and be placed in a separate file.
 */
interface Command {
	/**
	 * The name of the command that users will type after the mention.
	 */
	val name: String

	/**
	 * A brief description of what the command does.
	 * This will be displayed in the help command.
	 */
	val description: String
		get() = "No description provided"

	/**
	 * Logger for this command.
	 * Each command implementation will have its own logger instance.
	 */
	val logger: Logger
		get() = logger()

	/**
	 * Executes the command logic.
	 *
	 * @param event The message event that triggered this command
	 * @return true if the command was executed successfully, false otherwise
	 */
	suspend fun execute(event: MessageCreateEvent): Boolean

	/**
	 * Extracts the message text after the bot mention/prefix and command name.
	 * This is a utility method to avoid duplicating this logic in each command.
	 * Handles DM messages (no prefix required), mention-prefixed server messages,
	 * and custom prefix-prefixed server messages.
	 *
	 * @param event The message event
	 * @param commandName The name of the command to remove from the message
	 * @return The message text without the prefix and command name
	 */
	suspend fun extractMessageText(event: MessageCreateEvent, commandName: String = ""): String {
		val content = event.message.content
		val nameToRemove = commandName.ifEmpty { name }

		// Check if this is a DM channel
		val isDM = event.message.getChannel().type == dev.kord.common.entity.ChannelType.DM

		if (isDM) {
			// In DMs, just remove the command name
			return content.trim().removePrefix(nameToRemove).trim()
		} else {
			// In servers, determine which prefix was used (mention or custom)
			val mention = BotConfig.mention
			val serverId = try {
				event.message.getGuild().id.toString()
			} catch (e: Exception) {
				// If we can't get the guild ID, just use the default prefix
				logger.error("Failed to get guild ID in extractMessageText", e)
				return content.trim().removePrefix(nameToRemove).trim()
			}
			val prefixManager = PrefixManager.getInstance()
			val serverPrefix: String = prefixManager.getServerPrefix(serverId)

			// Check which prefix was used and remove it
			return when {
				content.startsWith(mention) -> {
					content.removePrefix(mention).trim().removePrefix(nameToRemove).trim()
				}

				content.startsWith(serverPrefix) -> {
					content.removePrefix(serverPrefix).trim().removePrefix(nameToRemove).trim()
				}

				else -> {
					// This shouldn't happen as the CommandRegistry already checks for prefixes
					// But just in case, return the content without the command name
					content.trim().removePrefix(nameToRemove).trim()
				}
			}
		}
	}
}
