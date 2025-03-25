package commands

import dev.kord.core.Kord
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on

/**
 * Registry for all bot commands.
 * Handles command registration, indexing, and execution.
 */
class CommandRegistry {
  private val commands = mutableMapOf<String, Command>()

  /**
   * Registers a command with the registry.
   *
   * @param command The command to register
   */
  fun register(command: Command) {
    commands[command.name] = command
  }

  /**
   * Registers all commands with the Kord instance.
   *
   * @param kord The Kord instance to register handlers on
   * @param mention The bot's mention string used in commands
   */
  fun registerAllCommands(kord: Kord, mention: String) {
    kord.on<MessageCreateEvent> {
      if (message.author?.isBot != false) return@on

      val content = message.content
      if (!content.startsWith(mention)) return@on

      // Extract the command name from the message
      val commandText = content.removePrefix(mention).trim()
      val commandName = commandText.split(" ")[0]

      // Execute the command if it exists
      val command = commands[commandName]
      if (command != null) {
        command.execute(this)
      } else {
        // Handle invalid command
        message.channel.createMessage("Unknown command: $commandName")
      }
    }
  }
}
