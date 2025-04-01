# Discord Bot in Kotlin

A feature-rich Discord bot built with Kotlin and the Kord library. This bot provides various utility commands and server
status monitoring capabilities.

## Features

- **Command System**: Easily extensible command system with automatic command discovery
- **Server Status Monitoring**: Monitor the status of servers and receive notifications when they change
- **Database Persistence**: SQLite database for storing user preferences and server status
- **Detailed Logging**: Comprehensive logging for debugging and monitoring

## Available Commands

- **help**: Lists all available commands or provides details about a specific command
	- Usage: `@bot help` or `@bot help <command>`

- **ping**: Responds with detailed ping information including bot uptime and response times
	- Usage: `@bot ping`

- **echo**: Echoes back the text you provide after the command
	- Usage: `@bot echo <message>`

- **status**: Monitor the status of servers and receive notifications when they change
	- Subcommands:
		- `check <server>`: Check the status of a specific server
		- `add <server>`: Add a server to your monitoring list
		- `delete <server>`: Remove a server from your monitoring list
		- `list`: List all servers you're monitoring
		- `help`: Show help for the status command
	- Usage: `@bot status <subcommand> [arguments]`

## Setup

### Prerequisites

- GraalVM 24.0.0 or higher (or any JDK 17 or higher for non-native builds)
- Maven

### Installation

1. Clone the repository:
   ```
   git clone https://github.com/yourusername/Discord-bot-Kotlin.git
   cd Discord-bot-Kotlin
   ```

2. Create a `.token` file in the project root with your Discord bot token:
   ```
   echo "YOUR_BOT_TOKEN" > .token
   ```

3. Build and run the project with Maven:

   #### Standard JVM Build
   ```
   mvn clean package
   java -jar target/Discord-bot-Kotlin-1.0-SNAPSHOT.jar
   ```

   #### GraalVM Native Image Build
   ```
   # Make sure you're using GraalVM
   $GRAALVM_HOME/bin/java -version  # Should show GraalVM version

   # Build native image
   mvn clean package -Pnative

   # Run the native executable
   ./target/Discord-bot-Kotlin
   ```

## Configuration

The bot reads its token from a `.token` file in the project root. This file should contain only the bot token, with no
additional text or whitespace.

The bot uses a SQLite database stored in `bot_data.db` in the project root. This file will be created automatically when
the bot is first run.

### Logging

The bot uses Logback for logging. Logs are written to both the console and log files:

- Log files are stored in the `logs` directory
- The current log file is named `discord-bot.log`
- Log files are rotated daily and when they reach 10MB in size
- Compressed archives of old logs are kept for 30 days, with a total cap of 1GB
- Log levels can be configured in `src/main/resources/logback.xml`

## Dependencies

- [Kord](https://github.com/kordlib/kord): Kotlin Discord API wrapper
- [SQLite JDBC](https://github.com/xerial/sqlite-jdbc): JDBC driver for SQLite
- [SLF4J](http://www.slf4j.org/): Simple Logging Facade for Java
- [Logback](https://logback.qos.ch/): Logging implementation for SLF4J
- [GraalVM](https://www.graalvm.org/): High-performance runtime with native image capabilities

## GraalVM Native Image

This project supports building a native executable using GraalVM's native-image tool. Native images offer several
advantages:

- **Faster Startup**: Native executables start almost instantly compared to JVM applications
- **Lower Memory Usage**: Native images have a smaller memory footprint
- **Reduced Disk Space**: The executable contains only the code paths that are actually used
- **Simplified Deployment**: No need to install a JVM on the target system

### Native Image Configuration

The project includes the necessary configuration files for GraalVM native-image in
`src/main/resources/META-INF/native-image/`:

- `reflect-config.json`: Configuration for classes that use reflection
- `resource-config.json`: Configuration for resources that need to be included in the native image
- `jni-config.json`: Configuration for JNI (Java Native Interface) calls
- `proxy-config.json`: Configuration for dynamic proxies

### Native Image Limitations

When using the native image, be aware of these limitations:

- Dynamic class loading and reflection require explicit configuration
- Some JVM features like JMX are not available
- Debugging can be more challenging
- Build times are longer than standard JVM builds

## Development

### Project Structure

- `src/main/kotlin/commands/`: Contains all bot commands
- `src/main/kotlin/database/`: Database management
- `src/main/kotlin/utils/`: Utility classes
- `src/main/kotlin/main.kt`: Entry point of the application

### Adding New Commands

1. Create a new Kotlin class in the `commands` package that implements the `Command` interface
2. Implement the required methods:
	- `name`: The name of the command
	- `description`: A brief description of what the command does
	- `execute`: The logic to execute when the command is invoked
3. The command will be automatically discovered and registered when the bot starts

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
