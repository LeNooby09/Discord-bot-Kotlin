package utils

import database.DatabaseManager

/**
 * Utility class for managing server-specific command prefixes.
 * Provides methods for getting and setting server prefixes.
 */
class PrefixManager {
	private val dbManager = DatabaseManager.getInstance()
	private val logger = logger()

	/**
	 * Gets the prefix for a specific server.
	 *
	 * @param serverId The ID of the server
	 * @return The server's prefix, or the default prefix if not set
	 */
	fun getServerPrefix(serverId: String): String {
		logger.debug("Getting prefix for server: $serverId")
		return dbManager.getServerPrefix(serverId)
	}

	/**
	 * Sets the prefix for a specific server.
	 *
	 * @param serverId The ID of the server
	 * @param prefix The new prefix to set
	 * @return true if the prefix was set successfully, false otherwise
	 */
	fun setServerPrefix(serverId: String, prefix: String): Boolean {
		logger.info("Setting prefix for server $serverId to: $prefix")
		return try {
			dbManager.setServerPrefix(serverId, prefix)
			true
		} catch (e: Exception) {
			logger.error("Failed to set prefix for server $serverId", e)
			false
		}
	}

	/**
	 * Removes the custom prefix for a specific server, resetting it to the default.
	 *
	 * @param serverId The ID of the server
	 * @return true if the prefix was removed successfully, false otherwise
	 */
	fun removeServerPrefix(serverId: String): Boolean {
		logger.info("Removing custom prefix for server $serverId")
		return try {
			dbManager.removeServerPrefix(serverId)
			true
		} catch (e: Exception) {
			logger.error("Failed to remove prefix for server $serverId", e)
			false
		}
	}

	companion object {
		private var instance: PrefixManager? = null

		/**
		 * Gets the singleton instance of the PrefixManager.
		 *
		 * @return The PrefixManager instance
		 */
		fun getInstance(): PrefixManager {
			if (instance == null) {
				instance = PrefixManager()
			}
			return instance!!
		}
	}
}
