package commands

import dev.kord.core.Kord
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import utils.MessageParser
import utils.logger
import java.io.File

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
	 * This optimized version uses a more direct approach to find command classes.
	 *
	 * @return A list of Class objects representing command classes
	 */
	private fun findCommandClasses(): List<Class<*>> {
		val commandClasses = mutableListOf<Class<*>>()
		val packageName = "commands"

		try {
			logger.debug("Scanning for command classes in package: $packageName")

			// Get all command classes directly by name
			// This is more efficient than scanning the classpath
			val knownCommandClasses = listOf(
				"AdminCommand",
				"EasterEggLexCommand",
				"MinecraftMovieCommand",
				"PingCommand",
				"PrefixCommand",
				"StatusCommand"
			)

			for (className in knownCommandClasses) {
				val fullClassName = "$packageName.$className"
				try {
					logger.debug("Loading known command class: $fullClassName")
					val clazz = Class.forName(fullClassName)
					if (isCommandClass(clazz)) {
						logger.debug("Found command class: $fullClassName")
						commandClasses.add(clazz)
					}
				} catch (e: Exception) {
					logger.error("Error loading known class: $fullClassName", e)
				}
			}

			// As a fallback, scan the classpath for any command classes we might have missed
			val classLoader = Thread.currentThread().contextClassLoader
			val resources = classLoader.getResources(packageName.replace('.', '/'))

			while (resources.hasMoreElements()) {
				val resource = resources.nextElement()
				logger.debug("Examining resource: ${resource.path}")

				// Handle directory-based classpath entries
				if (resource.protocol == "file") {
					logger.debug("Processing file-based resource")
					val directory = File(resource.toURI())
					if (directory.exists()) {
						val files = directory.listFiles { file ->
							file.isFile && file.name.endsWith(".class") && !file.name.contains('$')
						}

						if (files != null) {
							logger.debug("Found ${files.size} class files in directory")
							for (file in files) {
								val className = packageName + "." + file.name.substring(0, file.name.length - 6)

								// Skip classes we've already loaded
								if (commandClasses.any { it.name == className }) {
									logger.debug("Skipping already loaded class: $className")
									continue
								}

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
		val messageParser = MessageParser()

		kord.on<MessageCreateEvent> {
			// Skip messages from bots
			if (message.author?.isBot != false) return@on

			val authorName = message.author?.username ?: "Unknown"

			// Parse the message to extract the command name
			val parsedCommand = messageParser.parseMessage(this) ?: return@on
			val commandName = parsedCommand.commandName

			// Execute the command if it exists
			val command = commands[commandName]
			if (command != null) {
				logger.info("Executing command: ${command.name} from user: $authorName")
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
				logger.warn("Unknown command received: $commandName")
				message.channel.createMessage("Unknown command: $commandName")
			}
		}

		logger.info("Command event handler registered with ${commands.size} commands")
	}
}
