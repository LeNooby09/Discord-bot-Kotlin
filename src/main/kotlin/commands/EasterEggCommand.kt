package commands

import database.DatabaseManager
import dev.kord.core.event.message.MessageCreateEvent

/**
 * Base class for easter egg commands that respond with special messages when specific prefixes are set.
 * These commands only work if the server's prefix is set to a specific value.
 */
abstract class EasterEggCommand : Command {
	/**
	 * The special prefix that activates this easter egg.
	 * Subclasses must override this property.
	 */
	protected abstract val easterEggPrefix: String

	/**
	 * The message to send when the easter egg is activated.
	 * Subclasses must override this method.
	 */
	protected abstract suspend fun getEasterEggMessage(): String

	override suspend fun execute(event: MessageCreateEvent): Boolean {

		// Get the server ID
		val serverId = try {
			event.message.getGuild().id.toString()
		} catch (e: Exception) {
			logger.error("Failed to get guild ID in ${this.javaClass.simpleName}", e)
			return false
		}

		// Get the server's prefix
		val dbManager = DatabaseManager.getInstance()
		val serverPrefix = dbManager.getServerPrefix(serverId)

		// Check if the server's prefix matches the easter egg prefix
		if (serverPrefix == easterEggPrefix) {
			// Easter egg activated!
			event.message.channel.createMessage(getEasterEggMessage())
			return true
		} else {
			return false
		}
	}
}
