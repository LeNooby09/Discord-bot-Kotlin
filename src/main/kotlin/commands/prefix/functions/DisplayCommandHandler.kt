package commands.prefix.functions

import dev.kord.core.event.message.MessageCreateEvent
import org.slf4j.LoggerFactory
import utils.PrefixManager

/**
 * Handler for displaying the current prefix.
 * This is the default action when no subcommand is specified.
 */
object DisplayCommandHandler {
	private val logger = LoggerFactory.getLogger(DisplayCommandHandler::class.java)
	private val prefixManager = PrefixManager.getInstance()

	/**
	 * Handles the display command to show the current prefix.
	 *
	 * @param event The message event
	 * @param serverId The server ID
	 * @return true if the command was successful, false otherwise
	 */
	suspend fun handleDisplayCommand(event: MessageCreateEvent, serverId: String): Boolean {
		val currentPrefix = prefixManager.getServerPrefix(serverId)
		event.message.channel.createMessage("Current prefix for this server: `$currentPrefix`")
		logger.info("User ${event.message.author?.id?.value?.toString() ?: "unknown"} requested current prefix for server $serverId: $currentPrefix")
		return true
	}
}
