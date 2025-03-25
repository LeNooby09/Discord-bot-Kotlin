import dev.kord.core.Kord
import dev.kord.gateway.PrivilegedIntent
import dev.kord.gateway.Intent
import java.io.File
import java.io.IOException
import commands.CommandRegistry
import commands.PingCommand
import commands.HelloCommand

private val token: String = try {
  File("./.token").readText().trim()
} catch (e: IOException) {
  throw RuntimeException("Failed to read token file", e)
}

suspend fun main() {
  val kord = Kord(token)
  val mention = "<@1327594330130481272>"

  // Create and configure the command registry
  val commandRegistry = CommandRegistry()
  commandRegistry.register(PingCommand())
  commandRegistry.register(HelloCommand())

  // Register all commands with Kord
  commandRegistry.registerAllCommands(kord, mention)

  kord.login {
    @OptIn(PrivilegedIntent::class)
    intents += Intent.MessageContent
  }
}
