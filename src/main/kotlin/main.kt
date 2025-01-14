import dev.kord.core.Kord
import java.io.File
import java.io.IOException

private val token: String = try {
  File("./.token").readText().trim()
} catch (e: IOException) {
  throw RuntimeException("Failed to read token file", e)
}

suspend fun main() {
  val kord = Kord(token)

  kord.login()
}