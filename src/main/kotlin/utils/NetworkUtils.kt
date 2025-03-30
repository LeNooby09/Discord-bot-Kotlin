package utils

import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException

/**
 * Utility class for network operations.
 * Provides methods for checking server status and making HTTP requests.
 */
object NetworkUtils {
  private val logger = logger()

  /**
   * Checks the status of a server.
   * First tries HTTP/HTTPS connection for web domains, then falls back to ping.
   *
   * @param server The server address to check
   * @return true if the server is online, false otherwise
   */
  fun checkServerStatus(server: String): Boolean {
    val normalizedServer = ServerUtils.normalizeServerAddress(server)
    logger.debug("Checking status for server: $normalizedServer")

    // First try HTTP/HTTPS connection for web domains
    if (ServerUtils.isLikelyWebDomain(server)) {
      logger.debug("Server $server appears to be a web domain, trying HTTP check")
      try {
        val result = checkWebDomainStatus(server)
        logger.debug("HTTP check for $server completed with result: ${if (result) "online" else "offline"}")
        return result
      } catch (e: Exception) {
        logger.warn("HTTP check failed for $server: ${e.message}, falling back to ping", e)
        // Fall back to ping if HTTP check fails
      }
    }

    // Try to ping the server as fallback or for non-web domains
    logger.debug("Trying ping check for server: $normalizedServer")
    return try {
      val address = InetAddress.getByName(normalizedServer)
      val result = address.isReachable(5000) // 5 second timeout
      logger.debug("Ping check for $normalizedServer completed with result: ${if (result) "reachable" else "unreachable"}")
      result
    } catch (e: IOException) {
      logger.debug("Ping check for $normalizedServer failed with IOException: ${e.message}")
      false
    } catch (e: UnknownHostException) {
      logger.debug("Ping check for $normalizedServer failed with UnknownHostException: ${e.message}")
      false
    } catch (e: Exception) {
      logger.warn("Ping check for $normalizedServer failed with unexpected exception", e)
      false
    }
  }

  /**
   * Checks if a web domain is available by making an HTTP request.
   *
   * @param server The web domain to check
   * @return true if the domain is online, false otherwise
   */
  private fun checkWebDomainStatus(server: String): Boolean {
    logger.debug("Checking web domain status for: $server")

    // Try HTTPS first
    val isHttps = tryHttpRequest(server, true)
    if (isHttps) {
      return true
    }

    // If HTTPS fails, try HTTP as fallback
    return tryHttpRequest(server, false)
  }

  /**
   * Makes an HTTP request to a server.
   *
   * @param server The server address
   * @param useHttps Whether to use HTTPS (true) or HTTP (false)
   * @return true if the request was successful, false otherwise
   */
  private fun tryHttpRequest(server: String, useHttps: Boolean): Boolean {
    try {
      // Ensure server has a protocol
      val protocol = if (useHttps) "https://" else "http://"
      val urlStr = if (!server.contains("://")) {
        logger.debug("No protocol specified, trying ${if (useHttps) "HTTPS" else "HTTP"} for $server")
        "$protocol$server"
      } else {
        if (useHttps && server.startsWith("http://")) {
          server.replace("http://", "https://")
        } else if (!useHttps && server.startsWith("https://")) {
          server.replace("https://", "http://")
        } else {
          server
        }
      }

      logger.debug("Making ${if (useHttps) "HTTPS" else "HTTP"} request to: $urlStr")
      val url = URL(urlStr)
      val connection = url.openConnection() as HttpURLConnection
      connection.connectTimeout = 5000 // 5 second timeout
      connection.readTimeout = 5000
      connection.requestMethod = "HEAD" // Don't need the body, just check if site responds
      connection.instanceFollowRedirects = true

      val responseCode = connection.responseCode
      connection.disconnect()
      logger.debug("Received response code $responseCode from $urlStr")

      // Consider 2xx and 3xx response codes as "online"
      val isOnline = responseCode in 200..399
      logger.debug("Web domain $server is ${if (isOnline) "online" else "offline"} (response code: $responseCode)")
      return isOnline
    } catch (e: Exception) {
      logger.debug("${if (useHttps) "HTTPS" else "HTTP"} request failed for $server: ${e.message}")
      return false
    }
  }
}
