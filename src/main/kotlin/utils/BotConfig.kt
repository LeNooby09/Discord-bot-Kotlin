package utils

/**
 * Configuration for the bot.
 * This is used to store global configuration values that are used across the bot.
 */
object BotConfig {
	/**
	 * The bot's mention string used in commands.
	 * This is set in main.kt when the bot starts.
	 */
	var mention: String = "<@1327594330130481272>" // Default value, will be overridden in main.kt
}
