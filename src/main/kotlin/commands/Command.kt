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
}
