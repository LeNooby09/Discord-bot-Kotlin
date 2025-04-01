package commands

import database.DatabaseManager
import dev.kord.core.Kord
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import utils.logger
import java.io.File
import java.net.URL
import java.util.jar.JarFile

/**
 * Registry for all bot commands.
 * Handles command registration, indexing, and execution.
 */
class CommandRegistry {
	private val logger = logger()
	private val commands = mutableMapOf<String, Command>()

	/**
	 * Returns a read-only view of the commands map.
	 * This allows other components to access the list of commands without modifying it.
	 */
	val allCommands: Map<String, Command>
		get() = commands.toMap()

	/**
	 * Registers a command with the registry.
	 *
	 * @param command The command to register
	 */
	fun register(command: Command) {
		logger.info("Registering command: ${command.name}")
		commands[command.name] = command
	}

	/**
	 * Automatically discovers and registers all commands in the commands package.
	 * This method uses reflection to find all classes that implement the Command interface.
	 */
	fun registerAllCommandsInPackage() {
		logger.info("Discovering command classes in package")
		val commandClasses = findCommandClasses()
		logger.info("Found ${commandClasses.size} command classes")

		for (commandClass in commandClasses) {
			try {
				logger.debug("Instantiating command class: ${commandClass.name}")
				val constructor = commandClass.getDeclaredConstructor()
				constructor.isAccessible = true
				val command = constructor.newInstance() as Command
				register(command)
				logger.info("Successfully registered command: ${command.name}")
			} catch (e: Exception) {
				logger.error("Failed to register command class: ${commandClass.name}", e)
			}
		}
	}

	/**
	 * Finds all classes in the commands package that implement the Command interface.
	 *
	 * @return A list of Class objects representing command classes
	 */
	private fun findCommandClasses(): List<Class<*>> {
		val commandClasses = mutableListOf<Class<*>>()
		val packageName = "commands"
		val classLoader = Thread.currentThread().contextClassLoader

		try {
			logger.debug("Scanning for command classes in package: $packageName")
			// Get all resources for the package
			val resources = classLoader.getResources(packageName.replace('.', '/'))

			while (resources.hasMoreElements()) {
				val resource = resources.nextElement()
				logger.debug("Examining resource: ${resource.path}")

				// Handle directory-based classpath entries
				if (resource.protocol == "file") {
					logger.debug("Processing file-based resource")
					val directory = File(resource.toURI())
					if (directory.exists()) {
						val files = directory.listFiles()
						if (files != null) {
							logger.debug("Found ${files.size} files in directory")
							for (file in files) {
								if (file.isFile && file.name.endsWith(".class")) {
									val className = packageName + "." + file.name.substring(0, file.name.length - 6)
									logger.debug("Checking class: $className")
									try {
										val clazz = Class.forName(className)
										if (isCommandClass(clazz)) {
											logger.debug("Found command class: $className")
											commandClasses.add(clazz)
										}
									} catch (e: Exception) {
										logger.error("Error loading class: $className", e)
									}
								}
							}
						}
					}
				}
				// Handle JAR-based classpath entries
				else if (resource.protocol == "jar") {
					logger.debug("Processing JAR-based resource")
					val jarPath = resource.path.substring(5, resource.path.indexOf("!"))
					logger.debug("JAR path: $jarPath")
					val jar = JarFile(URL(jarPath).file)
					val entries = jar.entries()

					while (entries.hasMoreElements()) {
						val entry = entries.nextElement()
						val entryName = entry.name

						if (entryName.startsWith(packageName.replace('.', '/')) &&
							entryName.endsWith(".class") &&
							!entryName.contains('$')
						) {
							val className = entryName.substring(0, entryName.length - 6).replace('/', '.')
							logger.debug("Checking JAR class: $className")
							try {
								val clazz = Class.forName(className)
								if (isCommandClass(clazz)) {
									logger.debug("Found command class in JAR: $className")
									commandClasses.add(clazz)
								}
							} catch (e: Exception) {
								logger.error("Error loading class from JAR: $className", e)
							}
						}
					}

					jar.close()
				}
			}
		} catch (e: Exception) {
			logger.error("Error scanning for command classes", e)
		}

		logger.info("Found ${commandClasses.size} command classes in total")
		return commandClasses
	}

	/**
	 * Checks if a class implements the Command interface.
	 *
	 * @param clazz The class to check
	 * @return true if the class implements Command, false otherwise
	 */
	private fun isCommandClass(clazz: Class<*>): Boolean {
		// Skip abstract classes and interfaces
		if (clazz.isInterface || java.lang.reflect.Modifier.isAbstract(clazz.modifiers)) {
			return false
		}

		// Skip HelpCommand as it requires a CommandRegistry in its constructor
		// and will be registered manually
		if (clazz.name == "commands.HelpCommand") {
			logger.debug("Skipping HelpCommand for automatic registration")
			return false
		}

		// Check if the class implements the Command interface
		return Command::class.java.isAssignableFrom(clazz)
	}

	/**
	 * Registers all commands with the Kord instance.
	 *
	 * @param kord The Kord instance to register handlers on
	 * @param mention The bot's mention string used in commands
	 */
	fun registerAllCommands(kord: Kord, mention: String) {
		logger.info("Setting up message event handler for commands")

		kord.on<MessageCreateEvent> {
			// Skip messages from bots
			if (message.author?.isBot != false) return@on

			val content = message.content
			val authorName = message.author?.username ?: "Unknown"
			val channelId = message.channelId.toString()

			// Check if this is a DM channel
			val isDM = message.getChannel().type == dev.kord.common.entity.ChannelType.DM

			// Variables to store command information
			var commandText = ""
			var commandName = ""

			// Process messages differently based on whether they're from a DM or not
			if (isDM) {
				// In DMs, process the message directly as a command without requiring the mention
				logger.info("Received DM command message from $authorName in channel $channelId: $content")

				// Extract the command name from the message
				commandText = content.trim()
				commandName = commandText.split(" ")[0]
				logger.debug("Parsed DM command name: $commandName")
			} else {
				// In servers, check for both the mention and the custom prefix
				val serverId = try {
					message.getGuild().id.toString()
				} catch (e: Exception) {
					logger.error("Failed to get guild ID", e)
					return@on
				}
				val dbManager = DatabaseManager.getInstance()
				val serverPrefix = dbManager.getServerPrefix(serverId)

				// Check if the message starts with either the mention or the custom prefix
				if (content.startsWith(mention)) {
					logger.info("Received server command message (mention) from $authorName in channel $channelId: $content")

					// Extract the command name from the message
					commandText = content.removePrefix(mention).trim()
					commandName = commandText.split(" ")[0]
					logger.debug("Parsed server command name (mention): $commandName")
				} else if (content.startsWith(serverPrefix)) {
					logger.info("Received server command message (prefix: $serverPrefix) from $authorName in channel $channelId: $content")

					// Extract the command name from the message
					commandText = content.removePrefix(serverPrefix).trim()
					commandName = commandText.split(" ")[0]
					logger.debug("Parsed server command name (prefix): $commandName")
				} else {
					// Skip messages that don't start with either the mention or the custom prefix
					return@on
				}
			}

			// If command name is empty, default to help command
			val effectiveCommandName = if (commandName.isEmpty()) "help" else commandName

			// Execute the command if it exists
			val command = commands[effectiveCommandName]
			if (command != null) {
				logger.info("Executing command: ${command.name}")
				try {
					val success = command.execute(this)
					if (success) {
						logger.info("Command ${command.name} executed successfully")
					} else {
						logger.warn("Command ${command.name} execution returned false")
					}
				} catch (e: Exception) {
					logger.error("Error executing command ${command.name}", e)
					message.channel.createMessage("An error occurred while executing the command.")
				}
			} else {
				// Handle invalid command
				logger.warn("Unknown command received: $effectiveCommandName")
				message.channel.createMessage("Unknown command: $effectiveCommandName")
			}
		}

		logger.info("Command event handler registered with ${commands.size} commands")
	}
}
