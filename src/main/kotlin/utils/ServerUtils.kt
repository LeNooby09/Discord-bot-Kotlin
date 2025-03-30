package utils

import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException

/**
 * Utility class for server address operations.
 * Provides methods for normalizing, validating, and checking server addresses.
 */
object ServerUtils {
  private val logger = logger()

  /**
   * Checks if a domain is likely a web domain (vs. a plain IP or service)
   */
  fun isLikelyWebDomain(server: String): Boolean {
    val normalized = server.lowercase()
    return normalized.contains("://") || // Has protocol
      normalized.startsWith("www.") || // Starts with www
      normalized.endsWith(".com") || normalized.endsWith(".org") ||
      normalized.endsWith(".net") || normalized.endsWith(".io") ||
      normalized.endsWith(".co") || normalized.contains(".") // Has domain extension
  }

  /**
   * Normalizes a server address by extracting just the hostname/domain part
   * This is particularly important for ping checks which need just the host
   */
  fun normalizeServerAddress(server: String): String {
    return try {
      // Handle URLs with protocol
      if (server.contains("://")) {
        val url = URL(server)
        return url.host
      }

      // Handle URLs without protocol but with www or common TLDs
      if (isLikelyWebDomain(server)) {
        // Try to parse as URL with default protocol
        try {
          val url = URL("https://$server")
          return url.host
        } catch (e: Exception) {
          // If that fails, try to extract domain manually
          val domainParts = server.split("/", limit = 2)
          return domainParts[0] // Take just the domain part, ignore any path
        }
      }

      // For non-web domains or IPs, return as is
      server
    } catch (e: Exception) {
      // If all parsing fails, return original input
      logger.warn("Error normalizing server address: ${e.message}", e)
      server
    }
  }

  /**
   * Checks if an IP address is blacklisted
   * Currently blacklists all IPs starting with "100."
   */
  fun isIpBlacklisted(server: String): Boolean {
    // Normalize the server address to get just the hostname/IP
    val normalizedServer = try {
      // For URLs with protocol, extract the host
      if (server.contains("://")) {
        val url = URL(server)
        url.host
      } else {
        server
      }
    } catch (e: Exception) {
      server
    }

    // Check if the normalized server address is an IP address starting with "100."
    // IP address pattern: digits.digits.digits.digits
    val ipPattern = Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$")

    if (normalizedServer.matches(ipPattern)) {
      // If it's an IP address, check if it starts with "100."
      return normalizedServer.startsWith("100.")
    }

    // Try to resolve the hostname to an IP address
    try {
      val inetAddress = InetAddress.getByName(normalizedServer)
      val ipAddress = inetAddress.hostAddress

      // Check if the resolved IP address starts with "100."
      return ipAddress.startsWith("100.")
    } catch (e: Exception) {
      // If resolution fails, it's not a blacklisted IP
      return false
    }
  }

  /**
   * Validates if a server address is valid (can be resolved or is a valid URL)
   *
   * @param server The server address to validate
   * @return true if the server address is valid, false otherwise
   */
  suspend fun isValidServerAddress(server: String): Boolean {
    return try {
      // Try to resolve the hostname or IP
      try {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
          InetAddress.getByName(server)
        }
        true
      } catch (e: UnknownHostException) {
        // If it's not a valid hostname/IP, check if it's a URL
        try {
          URL(if (!server.startsWith("http")) "https://$server" else server).toURI()
          true
        } catch (e: Exception) {
          false
        }
      }
    } catch (e: Exception) {
      logger.warn("Error validating server address: ${e.message}", e)
      false
    }
  }
}
