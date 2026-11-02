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
    implementation("com.github.isycat:compose-lib:v1.0.2")
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
        <version>v1.02</version>
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
- A tabbed title bar (`TabbedTitleBar`), as code editors draw theirs

### Tabbed title bar

`TabbedTitleBar` is a window's title bar with its documents as tabs: the app's icon and title, an optional menu behind
a hamburger, a tab per document (an unsaved one wears a dot, and a tab can have a close button), then the app's own
actions, an optional search box, undo and redo, and the window's buttons. The title can stay the app's or follow the
tab in front (`titleFollowsTab`). Its look is calm neutral greys with small, centred text and thin line icons; only the
tab in front takes the colour of the content below it (`TitleBarDefaults.colors()` and `typography()` change them).
It draws in its own bundled font (Roboto, Apache 2.0), not the system's, so it looks the same on every platform.
The window's buttons are compact by default, or spaced and drawn as Windows draws its own with
`windowButtons = TitleBarWindowButtons.Windows`.

On Windows it can stand in for the system's title bar: `rememberTitleBarWindow()` takes the window's caption off,
keeping its frame (so it still resizes, snaps and casts its shadow), and the bar moves, maximises and resizes it.
Elsewhere it returns null and the bar sits under the system's title bar, without the title or the window's buttons.

```kotlin
Window(onCloseRequest = ::exitApplication, state = windowState, title = "My app") {
    val chrome = rememberTitleBarWindow(windowState, onCloseRequest = ::exitApplication)
    Column {
        TabbedTitleBar(
            tabs = documents.map { TitleBarTab(it.name, dirty = it.unsaved, onClose = { close(it) }) },
            selectedTabIndex = front,
            onTabSelected = { front = it },
            title = "My app",
            window = chrome,
            menu = { expanded, dismiss -> DropdownMenu(expanded, dismiss) { /* items */ } },
            undoRedo = TitleBarUndoRedo(canUndo, canRedo, ::undo, ::redo),
        )
        // The window's content
    }
}
```

## Building

```bash
./gradlew build
./gradlew test
```

## Requirements

- JDK 21+
- Kotlin 2.4.21+
- Gradle 8.14.3+
- Jetbrains Compose 1.12.2+

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
