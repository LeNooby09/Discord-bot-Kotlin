package commands.prefix.functions

import dev.kord.core.event.message.MessageCreateEvent
import org.slf4j.LoggerFactory
import utils.PrefixManager

/**
 * Handler for the 'reset' subcommand of the prefix command.
 * Resets the server prefix to the default.
 */
object ResetCommandHandler {
	private val logger = LoggerFactory.getLogger(ResetCommandHandler::class.java)
	private val prefixManager = PrefixManager.getInstance()

	/**
	 * Handles the reset command to reset the server prefix to the default.
	 *
	 * @param event The message event
	 * @param serverId The server ID
	 * @return true if the command was successful, false otherwise
	 */
	suspend fun handleResetCommand(event: MessageCreateEvent, serverId: String): Boolean {
		if (prefixManager.removeServerPrefix(serverId)) {
			event.message.channel.createMessage("Server prefix reset to default: `!`")
			logger.info("Server $serverId prefix reset to default by user ${event.message.author?.id?.value?.toString() ?: "unknown"}")
			return true
		} else {
			event.message.channel.createMessage("Failed to reset server prefix.")
			logger.error("Failed to reset server $serverId prefix")
			return false
		}
	}
}
