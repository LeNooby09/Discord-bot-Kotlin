package commands

import dev.kord.core.event.message.MessageCreateEvent
import java.time.Duration
import java.time.Instant

/**
 * Command that responds with "pong!" when a user types "ping".
 * Includes detailed information about execution time and Discord API response.
 */
class PingCommand : Command {
	override val name = "ping"
	override val description = "Responds with detailed ping information including bot uptime and response times"

	companion object {
		private val startTime = Instant.now()
	}

	override suspend fun execute(event: MessageCreateEvent): Boolean {
		val username = event.message.author?.username ?: "Unknown"
		logger.info("Executing ping command for user: $username")

		// Measure preparation time (before API call)
		val preparationStart = System.currentTimeMillis()

		// Calculate uptime
		val uptime = Duration.between(startTime, Instant.now())
		val uptimeFormatted = formatUptime(uptime)

		// Get gateway ping
		val gatewayPing = event.kord.gateway.averagePing?.toString() ?: "N/A"

		// Calculate preparation time
		val preparationTime = System.currentTimeMillis() - preparationStart

		// Measure API call time
		val apiCallStart = System.currentTimeMillis()
		logger.debug("Sending enhanced ping response")

		// Send the message and measure the round-trip time
		event.message.channel.createMessage(
			"""
      |**Pong!** 🏓
      |
      |**Command Information:**
      |> User: $username
      |> Channel: ${event.message.channel.id}
      |> Message ID: ${event.message.id}
      |
      |**Bot Information:**
      |> Uptime: $uptimeFormatted
      |> Gateway Ping: $gatewayPing
      |
      |**Response Times:**
      |> Preparation Time: ${preparationTime}ms
      |> API Call Time: Measuring...
      """.trimMargin()
		)

		// Calculate API call time
		val apiCallTime = System.currentTimeMillis() - apiCallStart

		// Calculate total execution time
		val totalExecutionTime = preparationTime + apiCallTime

		// Send a second message with the complete timing information
		event.message.channel.createMessage(
			"""
      |**Response Times (Complete):**
      |> Preparation Time: ${preparationTime}ms
      |> API Call Time: ${apiCallTime}ms
      |> Total Execution Time: ${totalExecutionTime}ms
      """.trimMargin()
		)

		logger.info("Ping command executed successfully in ${totalExecutionTime}ms (prep: ${preparationTime}ms, api: ${apiCallTime}ms)")
		return true
	}

	private fun formatUptime(uptime: Duration): String {
		val days = uptime.toDays()
		val hours = uptime.toHours() % 24
		val minutes = uptime.toMinutes() % 60
		val seconds = uptime.seconds % 60

		return buildString {
			if (days > 0) append("${days}d ")
			if (hours > 0) append("${hours}h ")
			if (minutes > 0) append("${minutes}m ")
			append("${seconds}s")
		}
	}
}
