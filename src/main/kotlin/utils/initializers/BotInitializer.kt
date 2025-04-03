package utils.initializers

import dev.kord.core.Kord
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import org.slf4j.LoggerFactory
import utils.BotConfig
import java.io.File
import java.io.IOException

/**
 * Handles initialization of the Discord bot.
 */
object BotInitializer {
	private val logger = LoggerFactory.getLogger(BotInitializer::class.java)

	/**
	 * Reads the bot token from the .token file.
	 * @return The bot token
	 * @throws RuntimeException if the token file cannot be read
	 */
	fun readBotToken(): String {
		return try {
			logger.info("Reading bot token from .token file")
			File("./.token").readText().trim()
		} catch (e: IOException) {
			logger.error("Failed to read token file", e)
			throw RuntimeException("Failed to read token file", e)
		}
	}

	/**
	 * Initializes the Kord client with the provided token.
	 * @param token The bot token
	 * @return The initialized Kord instance and the bot's mention ID
	 */
	suspend fun initializeKord(token: String): Pair<Kord, String> {
		logger.info("Initializing Kord client")

		// Initialize Kord with the token
		val kord = Kord(token)

		// Get the bot's mention ID
		val mention = "<@1327594330130481272>" // This should ideally be dynamically determined
		BotConfig.mention = mention
		logger.info("Initializing bot with mention ID: $mention")

		return Pair(kord, mention)
	}

	/**
	 * Logs in to Discord with the necessary intents.
	 * @param kord The Kord instance
	 */
	suspend fun loginToDiscord(kord: Kord) {
		logger.info("Logging in to Discord")
		kord.login {
			@OptIn(PrivilegedIntent::class)
			intents += Intent.MessageContent
			logger.debug("Enabled MessageContent intent")
		}
		logger.info("Bot is now online and ready")
	}
}
