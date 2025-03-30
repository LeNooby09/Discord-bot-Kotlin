package commands

import dev.kord.core.event.message.MessageCreateEvent
import org.slf4j.Logger
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
	 * Extracts the message text after the bot mention and command name.
	 * This is a utility method to avoid duplicating this logic in each command.
	 *
	 * @param event The message event
	 * @param commandName The name of the command to remove from the message
	 * @return The message text without the mention and command name
	 */
	fun extractMessageText(event: MessageCreateEvent, commandName: String = ""): String {
		val content = event.message.content
		val mention = "<@1327594330130481272>"
		val nameToRemove = commandName.ifEmpty { name }
		return content.removePrefix(mention).trim().removePrefix(nameToRemove).trim()
	}
}
