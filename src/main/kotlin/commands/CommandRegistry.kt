package commands

import dev.kord.core.Kord
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import java.io.File
import java.net.URL
import java.util.jar.JarFile

/**
 * Registry for all bot commands.
 * Handles command registration, indexing, and execution.
 */
class CommandRegistry {
  private val commands = mutableMapOf<String, Command>()

  /**
   * Registers a command with the registry.
   *
   * @param command The command to register
   */
  fun register(command: Command) {
    commands[command.name] = command
  }

  /**
   * Automatically discovers and registers all commands in the commands package.
   * This method uses reflection to find all classes that implement the Command interface.
   */
  fun registerAllCommandsInPackage() {
    val commandClasses = findCommandClasses()
    for (commandClass in commandClasses) {
      try {
        val constructor = commandClass.getDeclaredConstructor()
        constructor.isAccessible = true
        val command = constructor.newInstance() as Command
        register(command)
        println("Registered command: ${command.name}")
      } catch (e: Exception) {
        println("Failed to register command class: ${commandClass.name}")
        e.printStackTrace()
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
      // Get all resources for the package
      val resources = classLoader.getResources(packageName.replace('.', '/'))

      while (resources.hasMoreElements()) {
        val resource = resources.nextElement()

        // Handle directory-based classpath entries
        if (resource.protocol == "file") {
          val directory = File(resource.toURI())
          if (directory.exists()) {
            val files = directory.listFiles()
            if (files != null) {
              for (file in files) {
                if (file.isFile && file.name.endsWith(".class")) {
                  val className = packageName + "." + file.name.substring(0, file.name.length - 6)
                  try {
                    val clazz = Class.forName(className)
                    if (isCommandClass(clazz)) {
                      commandClasses.add(clazz)
                    }
                  } catch (e: Exception) {
                    println("Error loading class: $className")
                    e.printStackTrace()
                  }
                }
              }
            }
          }
        }
        // Handle JAR-based classpath entries
        else if (resource.protocol == "jar") {
          val jarPath = resource.path.substring(5, resource.path.indexOf("!"))
          val jar = JarFile(URL(jarPath).file)
          val entries = jar.entries()

          while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val entryName = entry.name

            if (entryName.startsWith(packageName.replace('.', '/')) && 
                entryName.endsWith(".class") && 
                !entryName.contains('$')) {
              val className = entryName.substring(0, entryName.length - 6).replace('/', '.')
              try {
                val clazz = Class.forName(className)
                if (isCommandClass(clazz)) {
                  commandClasses.add(clazz)
                }
              } catch (e: Exception) {
                println("Error loading class: $className")
                e.printStackTrace()
              }
            }
          }

          jar.close()
        }
      }
    } catch (e: Exception) {
      println("Error scanning for command classes")
      e.printStackTrace()
    }

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
    kord.on<MessageCreateEvent> {
      if (message.author?.isBot != false) return@on

      val content = message.content
      if (!content.startsWith(mention)) return@on

      // Extract the command name from the message
      val commandText = content.removePrefix(mention).trim()
      val commandName = commandText.split(" ")[0]

      // Execute the command if it exists
      val command = commands[commandName]
      if (command != null) {
        command.execute(this)
      } else {
        // Handle invalid command
        message.channel.createMessage("Unknown command: $commandName")
      }
    }
  }
}
