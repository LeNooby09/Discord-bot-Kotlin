package commands

import dev.kord.core.event.message.MessageCreateEvent

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
   * Executes the command logic.
   *
   * @param event The message event that triggered this command
   * @return true if the command was executed successfully, false otherwise
   */
  suspend fun execute(event: MessageCreateEvent): Boolean
}
