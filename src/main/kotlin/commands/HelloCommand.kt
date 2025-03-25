package commands

import dev.kord.core.event.message.MessageCreateEvent

/**
 * Command that responds with "hello!" when a user types "hello".
 */
class HelloCommand : Command {
  override val name = "hello"

  override suspend fun execute(event: MessageCreateEvent): Boolean {
    event.message.channel.createMessage("hello!")
    return true
  }
}
