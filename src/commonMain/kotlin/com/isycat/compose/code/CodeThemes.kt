package com.isycat.compose.code

import androidx.compose.ui.graphics.Color
import com.isycat.compose.resources.Res

/**
 * The code themes to choose among, as moonshine's IDE offers them: its defaults (One Dark Pro Night Flat first, then One
 * Dark Pro, moonshine Dark and Darcula), every scheme a person's JetBrains IDEs keep and their VS Code as its
 * settings make it and with the themes it has installed (on the desktop), and themes imported from files. A theme is
 * made when it is chosen, from bundled files or the person's own.
 */
object CodeThemes {
    /** A theme to choose: what names it where a choice is kept, what it is called, where it comes from, and how it is made. */
    class Choice(val id: String, val label: String, val detail: String, val load: suspend () -> CodeTheme)

    /** The default theme's choice id: One Dark Pro Night Flat. */
    const val DEFAULT_ID = "moonshine:one-dark-pro-night-flat"

    /** The themes bundled, the default first. */
    val builtIn: List<Choice> = listOf(
        Choice(DEFAULT_ID, "One Dark Pro Night Flat", "default") {
            VsCodeThemes.theme(VsCodeThemes.oneDarkPro(nightFlat = true), name = "One Dark Pro Night Flat", source = "One Dark Pro Night Flat (bundled)")
        },
        Choice("moonshine:one-dark-pro", "One Dark Pro", "bundled") {
            VsCodeThemes.theme(VsCodeThemes.oneDarkPro(), source = "One Dark Pro (bundled)")
        },
        Choice("moonshine:dark", "moonshine Dark", "moonshine") { MOONSHINE_DARK },
        Choice("jetbrains:Darcula", "Darcula", "JetBrains") { JetBrainsSchemes.darcula() },
    )

    /** Every theme to choose among: the bundled ones, then those this machine's IDEs have, then [imported]. */
    suspend fun choices(imported: List<Choice> = emptyList()): List<Choice> = builtIn + installedChoices() + imported

    /** A theme imported earlier from the file [fileName], as [text] holds it, to choose among the others. */
    fun imported(fileName: String, text: String): Choice =
        Choice("imported:$fileName", fileName.substringBeforeLast('.'), "imported") { load(fileName, text) }

    /**
     * The theme [text] holds, from the file [fileName], whichever kind it is: a JetBrains scheme (`.icls`), a VS Code
     * theme (JSON with `tokenColors` or `colors`), or VS Code settings (a settings.json, or a fragment of one). Throws
     * where it is none of them.
     */
    suspend fun load(fileName: String, text: String): CodeTheme {
        val name = fileName.substringBeforeLast('.')
        return when {
            fileName.endsWith(".icls", ignoreCase = true) || text.trimStart('﻿').trimStart().startsWith("<") -> JetBrainsSchemes.load(text, fileName)
            looksLikeVsCodeTheme(text) -> VsCodeThemes.theme(VsCodeTheme.read(text, fileName), name = name, source = "VS Code theme $fileName")
            else -> VsCodeThemes.fromSettings(text, name, "VS Code settings $fileName", ::installedVsCodeTheme)
        }
    }

    private fun looksLikeVsCodeTheme(text: String): Boolean =
        Regex(""""(tokenColors|semanticTokenColors)"\s*:""").containsMatchIn(text) ||
            (Regex(""""colors"\s*:""").containsMatchIn(text) && !text.contains("\"editor."))

    /**
     * moonshine's own dark theme: the editor's panels and accent around code coloured as moonshine's shader editor first
     * coloured code, keywords lilac, types teal, numbers apricot, strings green, comments grey and slanted.
     */
    val MOONSHINE_DARK: CodeTheme = run {
        val keyword = TokenStyle(Color(0xFFC59BFF))
        val type = TokenStyle(Color(0xFF6FD0C5))
        val comment = TokenStyle(Color(0xFF7D8494), italic = true)
        CodeTheme(
            name = "moonshine Dark",
            dark = true,
            palette = EditorPalette(
                background = Color(0xFF121317),
                foreground = Color(0xFFE4E6EC),
                caret = Color(0xFF8AB4FF),
                caretRow = Color(0x732A2C35),
                selection = Color(0xFF2B3B5E),
                lineNumber = Color(0xFF5A5E6A),
                lineNumberCaret = Color(0xFFA3A7B3),
                matchedBracket = Color(0xFF2E3A52),
                error = Color(0xFFEF5B5B),
                warning = Color(0xFFF2B53D),
                widget = Color(0xFF1B1D23),
                widgetSelection = Color(0xFF2B3B5E),
            ),
            styles = mapOf(
                "TEXT" to TokenStyle(Color(0xFFE4E6EC)),
                "DEFAULT_KEYWORD" to keyword,
                "DEFAULT_NUMBER" to TokenStyle(Color(0xFFF2B07A)),
                "DEFAULT_STRING" to TokenStyle(Color(0xFFA8D88A)),
                "DEFAULT_VALID_STRING_ESCAPE" to TokenStyle(Color(0xFFE88AC0)),
                "DEFAULT_LINE_COMMENT" to comment,
                "DEFAULT_BLOCK_COMMENT" to comment,
                "DEFAULT_DOC_COMMENT" to comment,
                "DEFAULT_METADATA" to TokenStyle(Color(0xFFE8C46A)),
                "DEFAULT_CLASS_NAME" to type,
                "DEFAULT_CLASS_REFERENCE" to type,
                "DEFAULT_INTERFACE_NAME" to type,
                "DEFAULT_FUNCTION_DECLARATION" to TokenStyle(Color(0xFF8AB4FF)),
                "DEFAULT_FUNCTION_CALL" to TokenStyle(Color(0xFFB9CCF2)),
                "DEFAULT_INSTANCE_FIELD" to TokenStyle(Color(0xFFE8C46A)),
                "DEFAULT_STATIC_FIELD" to TokenStyle(Color(0xFFE8C46A), italic = true),
                "DEFAULT_CONSTANT" to TokenStyle(Color(0xFFF2B07A), italic = true),
                "DEFAULT_PARAMETER" to TokenStyle(Color(0xFFD4C8F0)),
                "DEFAULT_LABEL" to TokenStyle(Color(0xFFE88AC0)),
                "BAD_CHARACTER" to TokenStyle(effect = TextEffect.WAVE, effectColor = Color(0xFFEF5B5B)),
            ),
            source = "moonshine",
        )
    }
}

/** A file bundled with the code themes, as text. */
internal suspend fun bundledText(name: String): String = Res.readBytes("files/code-themes/$name").decodeToString()

/** The themes this machine's IDEs have, where it can look: none in a browser. */
internal expect suspend fun installedChoices(): List<CodeThemes.Choice>

/** A theme the person's VS Code has installed, by its name or label, where it can look; null in a browser. */
internal expect suspend fun installedVsCodeTheme(name: String): VsCodeTheme?
