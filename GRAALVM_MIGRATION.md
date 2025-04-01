# GraalVM 24 Migration Guide

This document outlines the changes made to port the Discord-bot-Kotlin project to GraalVM 24 and enable native image
compilation.

## Changes Made

### 1. Updated pom.xml

- Changed JVM target from Java 8 to Java 17 (required for GraalVM 24)
- Added GraalVM SDK and native-image dependencies
- Added GraalVM native-maven-plugin configuration
- Created a native profile for building native images with `-Pnative`

### 2. Added GraalVM Native Image Configuration

Created configuration files in `src/main/resources/META-INF/native-image/`:

- `reflect-config.json`: Configuration for classes that use reflection
	- Command classes
	- Database manager classes
	- SQLite JDBC driver
	- Logging classes

- `resource-config.json`: Configuration for resources
	- logback.xml
	- Properties files
	- Configuration files
	- Service provider interfaces

- `jni-config.json`: Configuration for JNI (Java Native Interface)
	- SQLite JDBC native methods

- `proxy-config.json`: Configuration for dynamic proxies
	- JDBC interfaces

### 3. Updated Documentation

- Updated README.md with GraalVM prerequisites
- Added instructions for building native images
- Added a section explaining GraalVM benefits and limitations

## Building with GraalVM

To build the project with GraalVM:

1. Install GraalVM 24.0.0 or higher
2. Set GRAALVM_HOME environment variable
3. Build with Maven:
   ```
   mvn clean package            # For standard JVM build
   mvn clean package -Pnative   # For native image build
   ```

## Running the Application

### JVM Mode

```
java -jar target/Discord-bot-Kotlin-1.0-SNAPSHOT.jar
```

### Native Image Mode

```
./target/Discord-bot-Kotlin
```

## Potential Issues

- Reflection: If new classes are added that use reflection, they must be added to reflect-config.json
- Resources: If new resource files are needed, ensure they match the patterns in resource-config.json
- JNI: If additional native libraries are used, they must be configured in jni-config.json
- Dynamic Proxies: If new interfaces are used with dynamic proxies, add them to proxy-config.json

## Performance Considerations

- Native images have faster startup time but may have slightly lower peak performance
- Memory usage is typically lower with native images
- Native image build times are significantly longer than standard JVM builds
