package commands.prefix.functions

import dev.kord.core.event.message.MessageCreateEvent
import org.slf4j.LoggerFactory
import utils.PrefixManager

/**
 * Handler for the 'set' subcommand of the prefix command.
 * Sets a new prefix for the server.
 */
object SetCommandHandler {
	private val logger = LoggerFactory.getLogger(SetCommandHandler::class.java)
	private val prefixManager = PrefixManager.getInstance()

	/**
	 * Handles the set command to set a new prefix for the server.
	 *
	 * @param event The message event
	 * @param args The command arguments
	 * @param serverId The server ID
	 * @return true if the command was successful, false otherwise
	 */
	suspend fun handleSetCommand(event: MessageCreateEvent, args: List<String>, serverId: String): Boolean {
		if (args.isEmpty()) {
			event.message.channel.createMessage("Please specify a prefix to set. Usage: `prefix set <new_prefix>`")
			return false
		}

		val newPrefix = args[0]
		if (newPrefix.length > 10) {
			event.message.channel.createMessage("Prefix must be 10 characters or less.")
			return false
		}

		if (prefixManager.setServerPrefix(serverId, newPrefix)) {
			event.message.channel.createMessage("Server prefix set to: `$newPrefix`")
			logger.info("Server $serverId prefix set to: $newPrefix by user ${event.message.author?.id?.value?.toString() ?: "unknown"}")
			return true
		} else {
			event.message.channel.createMessage("Failed to set server prefix.")
			logger.error("Failed to set server $serverId prefix to: $newPrefix")
			return false
		}
	}
}
