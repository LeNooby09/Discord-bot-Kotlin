package utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility class for server address operations.
 * Provides methods for normalizing, validating, and checking server addresses.
 */
object ServerUtils {

  // Cache validation results to avoid repeated checks
  private val validationCache = ConcurrentHashMap<String, Boolean>()

  // Cache normalized addresses
  private val normalizedAddressCache = ConcurrentHashMap<String, String>()

  // Cache domain type results
  private val webDomainCache = ConcurrentHashMap<String, Boolean>()

  // Cache blacklist check results
  private val blacklistCache = ConcurrentHashMap<String, Boolean>()

  // Precompile regular expressions
  private val ipPattern = Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$")

  // Common TLDs for quick checking
  private val commonTlds = setOf(".com", ".org", ".net", ".io", ".co", ".edu", ".gov")

  /**
   * Checks if a domain is likely a web domain (vs. a plain IP or service)
   */
  fun isLikelyWebDomain(server: String): Boolean {
    return webDomainCache.getOrPut(server) {
      val normalized = server.lowercase()
      normalized.contains("://") || // Has protocol
        normalized.startsWith("www.") || // Starts with www
        commonTlds.any { normalized.endsWith(it) } || // Has common TLD
        normalized.contains(".") && !normalized.matches(ipPattern) // Has domain extension but not IP
    }
  }

  /**
   * Normalizes a server address by extracting just the hostname/domain part
   * This is particularly important for ping checks which need just the host
   */
  fun normalizeServerAddress(server: String): String {
    return normalizedAddressCache.getOrPut(server) {
      try {
        // Handle URLs with protocol
        if (server.contains("://")) {
          return@getOrPut URL(server).host
        }

        // Handle URLs without protocol but with www or common TLDs
        if (isLikelyWebDomain(server)) {
          // Try to parse as URL with default protocol
          try {
            return@getOrPut URL("https://$server").host
          } catch (e: Exception) {
            // If that fails, extract domain manually
            val domainParts = server.split("/", limit = 2)
            return@getOrPut domainParts[0] // Take just the domain part, ignore any path
          }
        }

        // For non-web domains or IPs, return as is
        server
      } catch (e: Exception) {
        // If all parsing fails, return original input
        server
      }
    }
  }

  /**
   * Checks if an IP address is blacklisted
   * Currently blacklists all IPs starting with "100."
   */
  fun isIpBlacklisted(server: String): Boolean {
    return blacklistCache.getOrPut(server) {
      // Normalize the server address to get just the hostname/IP
      val normalizedServer = normalizeServerAddress(server)

      // Check if the normalized server address is an IP address starting with "100."
      if (normalizedServer.matches(ipPattern) && normalizedServer.startsWith("100.")) {
        return@getOrPut true
      }

      // For non-IP addresses, we don't need to resolve them for blacklist checking
      // This is a significant optimization to avoid unnecessary DNS lookups
      false
    }
  }

  /**
   * Validates if a server address is valid (can be resolved or is a valid URL)
   *
   * @param server The server address to validate
   * @return true if the server address is valid, false otherwise
   */
  suspend fun isValidServerAddress(server: String): Boolean {
    return validationCache.getOrPut(server) {
      try {
        // First check if it's a valid URL format
        if (server.contains("://")) {
          try {
            URL(server).toURI()
            return@getOrPut true
          } catch (e: Exception) {
            return@getOrPut false
          }
        }

        // If it looks like a web domain but doesn't have a protocol, try with https://
        if (isLikelyWebDomain(server) && !server.contains("://")) {
          try {
            URL("https://$server").toURI()
            return@getOrPut true
          } catch (e: Exception) {
            // Continue to hostname resolution if URL parsing fails
          }
        }

        // Try to resolve the hostname or IP
        val isResolvable = withContext(Dispatchers.IO) {
          try {
            InetAddress.getByName(server)
            true
          } catch (e: UnknownHostException) {
            false
          }
        }

        isResolvable
      } catch (e: Exception) {
        false
      }
    }
  }
}
