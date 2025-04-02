package commands

import database.DatabaseManager
import dev.kord.core.event.message.MessageCreateEvent

/**
 * Easter egg command that responds with a special message when a specific prefix is set.
 * This command only works if the server's prefix is set to a specific value.
 */
class MinecraftMovieCommand : Command {
	override val name = "movie"
	override val description = "A mysterious command"

	// The special prefix that activates this easter egg
	private val easterEggPrefix = "minecraft"

	override suspend fun execute(event: MessageCreateEvent): Boolean {
		// Check if this is a DM channel
		val isDM = event.message.getChannel().type == dev.kord.common.entity.ChannelType.DM

		if (isDM) {
			// Easter egg doesn't work in DMs
			event.message.channel.createMessage("This command doesn't do anything special.")
			return true
		}

		// Get the server ID
		val serverId = try {
			event.message.getGuild().id.toString()
		} catch (e: Exception) {
			logger.error("Failed to get guild ID in LexCommand", e)
			event.message.channel.createMessage("Something went wrong.")
			return false
		}

		// Get the server's prefix
		val dbManager = DatabaseManager.getInstance()
		val serverPrefix = dbManager.getServerPrefix(serverId)

		// Check if the server's prefix matches the easter egg prefix
		if (serverPrefix == easterEggPrefix) {
			// Easter egg activated!
			event.message.channel.createMessage(quotes())
			return true
		} else {
			// Easter egg not activated
			event.message.channel.createMessage("This command doesn't do anything special.")
			return true
		}
	}
}

fun quotes(): String {
	val quotes = mutableListOf(
		"I.. am STEVE",
		"Chicken jockey!",
		"FLINT AND STEEL!",
		"The Nether",
		"**RELEASE**",
		"This is a crafting table",
		"Welcome to the Overworld",
		"As a child I yearned for the mines"
	)
	return quotes.random()
}
