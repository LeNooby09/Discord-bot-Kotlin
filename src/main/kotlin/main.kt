import dev.kord.core.Kord
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.PrivilegedIntent
import dev.kord.gateway.Intent
import java.io.File
import java.io.IOException

private val token: String = try {
  File("./.token").readText().trim()
} catch (e: IOException) {
  throw RuntimeException("Failed to read token file", e)
}

suspend fun main() {
  val kord = Kord(token)

  kord.on<MessageCreateEvent> {
    if (message.author?.isBot != false) return@on
    if (message.content != "<@1327594330130481272> ping") return@on
    message.channel.createMessage("pong!")
  }

  kord.login {
    @OptIn(PrivilegedIntent::class)
    intents += Intent.MessageContent
  }
}