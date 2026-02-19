# Compose Lib

Reusable Compose Desktop components and utilities for building rich desktop applications.

## Features

- Material 3 components for Compose Desktop
- WebView integration for embedded browsers
- Text-to-Speech (TTS) utilities
- Native system integration helpers
- Coroutine utilities for Compose
- Custom UI components and extensions

## Installation

### JitPack

Add JitPack repository to your `build.gradle.kts`:

```kotlin
repositories {
    maven("https://jitpack.io")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation("com.github.isycat:compose-lib:v1.0.1")
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
    <repository>
        <id>compose-dev</id>
        <url>https://maven.pkg.jetbrains.space/public/p/compose/dev</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.isycat</groupId>
        <artifactId>compose-lib</artifactId>
        <version>v1.0.1</version>
    </dependency>
</dependencies>
```

## Usage

```kotlin
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.isycat.compose.*

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        // Use Material 3 components
        MaterialTheme {
            // Your UI components
        }
    }
}
```

## Components

This library includes various reusable components for Compose Desktop:
- WebView integration
- Native system dialogs
- Clipboard utilities
- Window management helpers
- Material 3 theme utilities

## Building

```bash
./gradlew build
./gradlew test
```

## Requirements

- JDK 21+
- Kotlin 2.1.21+
- Gradle 8.5+
- Jetbrains Compose 1.8.1+

## Dependencies

- Jetbrains Compose Desktop
- Compose Material 3
- Compose Material Icons Extended
- Compose WebView Multiplatform
- Kotlinx Coroutines (Core & Swing)
- Kotlinx Serialization
- JNA (Java Native Access)

## Platform Support

- Windows
- macOS
- Linux

## License

MIT License - see LICENSE file for details

## Contributing

This module is part of the [DotaHALP](https://github.com/isycat/dota-halp) project but is maintained as a standalone library for reusability.

Contributions are welcome! Please open an issue or pull request.

## Related Projects

- [DotaHALP](https://github.com/isycat/dota-halp) - Dota 2 drafting assistant using this library
- [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform) - Official Jetbrains Compose Desktop

## Documentation

For detailed documentation on Compose Desktop, see:
- [Jetbrains Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Compose Material 3](https://developer.android.com/jetpack/compose/designsystems/material3)
