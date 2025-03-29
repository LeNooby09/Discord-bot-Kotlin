package commands

import dev.kord.core.event.message.MessageCreateEvent

/**
 * Command that responds with "pong!" when a user types "ping".
 */
class PingCommand : Command {
  override val name = "ping"

  override suspend fun execute(event: MessageCreateEvent): Boolean {
    val username = event.message.author?.username ?: "Unknown"
    logger.info("Executing ping command for user: $username")

    logger.debug("Sending 'pong!' response")
    event.message.channel.createMessage("pong!")

    logger.info("Ping command executed successfully")
    return true
  }
}
