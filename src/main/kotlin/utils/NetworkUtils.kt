package utils

import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * Utility class for network operations.
 * Provides methods for checking server status and making HTTP requests.
 */
object NetworkUtils {
  private val logger = logger()

  // Cache for DNS resolutions to avoid repeated lookups
  private val dnsCache = ConcurrentHashMap<String, InetAddress>()

  // Connection pool for HTTP connections
  private val connectionPool = ConcurrentHashMap<String, HttpURLConnection>()

  // Shorter timeouts for faster checks
  private const val CONNECTION_TIMEOUT = 3000
  private const val READ_TIMEOUT = 3000
  private const val PING_TIMEOUT = 3000

  /**
   * Checks the status of a server.
   * Tries HTTP/HTTPS connection for web domains in parallel, then falls back to ping.
   *
   * @param server The server address to check
   * @return true if the server is online, false otherwise
   */
  suspend fun checkServerStatus(server: String): Boolean {
    val normalizedServer = ServerUtils.normalizeServerAddress(server)
    logger.debug("Checking status for server: $normalizedServer")

    // First try HTTP/HTTPS connection for web domains
    if (ServerUtils.isLikelyWebDomain(server)) {
      try {
        logger.debug("Server $normalizedServer appears to be a web domain, trying HTTP/HTTPS")
        val result = checkWebDomainStatus(server)
        logger.debug("Web domain check for $normalizedServer result: ${if (result) "online" else "offline"}")
        return result
      } catch (e: Exception) {
        logger.debug("HTTP/HTTPS check failed for $normalizedServer, falling back to ping", e)
        // Fall back to ping if HTTP check fails
      }
    }

    // Try to ping the server as fallback or for non-web domains
    logger.debug("Using ping to check $normalizedServer")
    val pingResult = pingServer(normalizedServer)
    logger.debug("Ping check for $normalizedServer result: ${if (pingResult) "online" else "offline"}")
    return pingResult
  }

  /**
   * Checks if a web domain is available by making HTTP and HTTPS requests in parallel.
   *
   * @param server The web domain to check
   * @return true if the domain is online, false otherwise
   */
  private suspend fun checkWebDomainStatus(server: String): Boolean = coroutineScope {
    // Try both HTTP and HTTPS in parallel
    val httpsDeferred = async {
      withTimeoutOrNull(CONNECTION_TIMEOUT.toLong()) {
        tryHttpRequest(server, true)
      } ?: false
    }

    val httpDeferred = async {
      withTimeoutOrNull(CONNECTION_TIMEOUT.toLong()) {
        tryHttpRequest(server, false)
      } ?: false
    }

    // Return true if either HTTP or HTTPS check succeeds
    httpsDeferred.await() || httpDeferred.await()
  }

  /**
   * Makes an HTTP request to a server.
   *
   * @param server The server address
   * @param useHttps Whether to use HTTPS (true) or HTTP (false)
   * @return true if the request was successful, false otherwise
   */
  private suspend fun tryHttpRequest(server: String, useHttps: Boolean): Boolean = withContext(Dispatchers.IO) {
    try {
      // Ensure server has a protocol
      val protocol = if (useHttps) "https://" else "http://"
      val urlStr = when {
        !server.contains("://") -> "$protocol$server"
        useHttps && server.startsWith("http://") -> server.replace("http://", "https://")
        !useHttps && server.startsWith("https://") -> server.replace("https://", "http://")
        else -> server
      }

      logger.debug("Attempting ${if (useHttps) "HTTPS" else "HTTP"} request to: $urlStr")

      // Try to get connection from pool or create new one
      val connection = connectionPool.getOrPut(urlStr) {
        logger.debug("Connection pool miss for $urlStr, creating new connection")
        (URL(urlStr).openConnection() as HttpURLConnection).apply {
          connectTimeout = CONNECTION_TIMEOUT
          readTimeout = READ_TIMEOUT
          requestMethod = "HEAD"
          instanceFollowRedirects = true
          useCaches = true
          defaultUseCaches = true
        }
      }

      logger.debug("Sending request to $urlStr with timeout ${CONNECTION_TIMEOUT}ms")
      val responseCode = connection.responseCode
      logger.debug("Received response code $responseCode from $urlStr")

      // Consider 2xx and 3xx response codes as "online"
      val isSuccess = responseCode in 200..399
      if (!isSuccess) {
        logger.debug("Request to $urlStr failed with response code $responseCode")
      }

      isSuccess
    } catch (e: Exception) {
      logger.debug("Exception during ${if (useHttps) "HTTPS" else "HTTP"} request to $server: ${e.message}")
      false
    }
  }

  /**
   * Pings a server to check if it's reachable.
   *
   * @param server The server address
   * @return true if the server is reachable, false otherwise
   */
  private suspend fun pingServer(server: String): Boolean = withContext(Dispatchers.IO) {
    try {
      logger.debug("Attempting to ping server: $server")

      // Use cached DNS resolution if available
      val address = dnsCache.getOrPut(server) {
        logger.debug("DNS cache miss for $server, resolving address")
        InetAddress.getByName(server)
      }

      logger.debug("Resolved $server to ${address.hostAddress}, pinging with timeout ${PING_TIMEOUT}ms")

      val result = withTimeoutOrNull(PING_TIMEOUT.milliseconds) {
        address.isReachable(PING_TIMEOUT)
      } ?: false

      if (!result) {
        logger.debug("Ping to $server (${address.hostAddress}) failed or timed out")
      }

      result
    } catch (e: Exception) {
      logger.debug("Exception while pinging $server: ${e.message}")
      false
    }
  }
}
