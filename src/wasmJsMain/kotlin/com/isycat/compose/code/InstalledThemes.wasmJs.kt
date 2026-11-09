package com.isycat.compose.code

// A browser cannot look at the person's IDEs: their themes come in by import.

internal actual suspend fun installedChoices(): List<CodeThemes.Choice> = emptyList()

internal actual suspend fun installedVsCodeTheme(name: String): VsCodeTheme? = null
