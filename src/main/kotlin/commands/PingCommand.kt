package commands

import dev.kord.core.event.message.MessageCreateEvent

/**
 * Command that responds with "pong!" when a user types "ping".
 */
class PingCommand : Command {
  override val name = "ping"

  override suspend fun execute(event: MessageCreateEvent): Boolean {
    event.message.channel.createMessage("pong!")
    return true
  }
}
