package commands

import dev.kord.core.event.message.MessageCreateEvent

/**
 * Command that echoes back the user's message.
 */
class EchoCommand : Command {
  override val name = "echo"
  override val description = "Echoes back the text you provide after the command"

  override suspend fun execute(event: MessageCreateEvent): Boolean {
    val content = event.message.content
    val mention = "<@1327594330130481272>"
    val messageText = content.removePrefix(mention).trim().removePrefix("echo").trim()

    if (messageText.isNotEmpty()) {
      event.message.channel.createMessage("Echo: $messageText")
    } else {
      event.message.channel.createMessage("Echo: You didn't say anything!")
    }

    return true
  }
}
