package com.isycat.compose.code

import androidx.compose.ui.graphics.Color
import com.isycat.compose.resources.Res

/**
 * The code themes to choose among, as moonshine's IDE offers them: those it carries (GitHub Dark Default, One Dark Pro,
 * Dracula, Ayu Dark, moonshine Dark, IntelliJ IDEA's and VS Code's default dark themes, Leaf, Matcha, isycat and isycat
 * VS Code), the scheme the person's JetBrains IDE uses (on the desktop), and themes imported from files. A theme is made
 * when it is chosen, from bundled files or the person's own.
 */
object CodeThemes {
    /** A theme to choose: what names it where a choice is kept, what it is called, where it comes from, and how it is made. */
    class Choice(val id: String, val label: String, val detail: String, val load: suspend () -> CodeTheme)

    /** moonshine's default theme's choice id: GitHub Dark Default. */
    const val DEFAULT_ID = "moonshine:github-dark-default"

    /** isycat's choice id: a JetBrains scheme in its IDE's editor font. */
    const val ISYCAT_ID = "moonshine:isycat"

    private const val ONE_DARK_PRO_ID = "moonshine:one-dark-pro"
    private const val ISLANDS_DARK_ID = "jetbrains:Islands Dark"
    private const val VS_CODE_DEFAULT_ID = "vscode-default:${VsCodeThemes.DEFAULT_THEME}"
    private const val IMPORTED = "imported:"
    private const val ISYCAT = "isycat"

    /** The themes bundled, in moonshine's order. */
    val builtIn: List<Choice> = listOf(
        packaged(DEFAULT_ID, "GitHub Dark Default", "default, GitHub Theme", "GitHub-dark-default.json"),
        packaged(ONE_DARK_PRO_ID, "One Dark Pro", "One Dark Pro", "OneDark-Pro.json"),
        packaged("moonshine:dracula", "Dracula", "Dracula Theme", "Dracula.json"),
        packaged("moonshine:ayu-dark", "Ayu Dark", "Ayu", "Ayu-dark.json"),
        Choice("moonshine:dark", MOONSHINE_DARK_NAME, "moonshine") { MOONSHINE_DARK },
        Choice(ISLANDS_DARK_ID, "Islands Dark", "IntelliJ IDEA's default") { JetBrainsSchemes.islandsDark() },
        Choice(VS_CODE_DEFAULT_ID, VsCodeThemes.DEFAULT_THEME, "VS Code's default") {
            val theme = checkNotNull(VsCodeThemes.bundled(VsCodeThemes.DEFAULT_THEME)) { "VS Code's default theme is bundled" }
            VsCodeThemes.theme(theme, name = VsCodeThemes.DEFAULT_THEME, source = "VS Code's ${VsCodeThemes.DEFAULT_THEME} (bundled)")
        },
        settings("moonshine:green", "Leaf", "green.vscode-settings.jsonc"),
        settings("moonshine:matcha", "Matcha", "matcha.vscode-settings.jsonc"),
        Choice(ISYCAT_ID, ISYCAT, "") {
            JetBrainsSchemes.load(bundledText("isycat.icls"), "isycat.icls", CodeFont(listOf("Source Code Pro"), JetBrainsSchemes.DEFAULT_SIZE, JetBrainsSchemes.DEFAULT_LINE_SPACING))
        },
        settings("moonshine:isycat-vscode", "isycat VS Code", "isycat.vscode-settings.jsonc"),
    )

    /** A VS Code theme bundled from an extension, one self-contained file. */
    private fun packaged(id: String, label: String, detail: String, file: String) =
        Choice(id, label, detail) { VsCodeThemes.theme(VsCodeTheme.read(bundledText(file), file), name = label, source = "$label (bundled)") }

    /** VS Code settings bundled, over the theme they name or VS Code's default. */
    private fun settings(id: String, label: String, file: String) =
        Choice(id, label, "") { VsCodeThemes.fromSettings(bundledText(file), label, "$label (bundled)") }

    /**
     * The choice a choice kept as [id] stands for now, where the set it was made from had another: One Dark Pro for its
     * Night Flat variant, Islands Dark (built on it) for Darcula, isycat for the person's own copy of the isycat scheme,
     * and VS Code's default as bundled for the installed one; [default] where a theme of the old set's has no
     * counterpart. Every other id is kept, to be found among the themes as they are.
     */
    fun carriedOver(id: String, default: String): String = when {
        id == "moonshine:one-dark-pro-night-flat" -> ONE_DARK_PRO_ID
        id == "jetbrains:Darcula" -> ISLANDS_DARK_ID
        id == "vscode:${VsCodeThemes.DEFAULT_THEME}" -> VS_CODE_DEFAULT_ID
        id.startsWith("jetbrains:") && id.substringAfterLast('/').substringAfterLast('\\') == "$ISYCAT.icls" -> ISYCAT_ID
        id.startsWith("vscode:") || id.startsWith("vscode-settings:") -> default
        else -> id
    }

    /** Every theme to choose among: the bundled ones, then those this machine's IDEs have, then [imported]. */
    suspend fun choices(imported: List<Choice> = emptyList()): List<Choice> = builtIn + installedChoices() + imported

    /** A theme imported earlier from the file [fileName], as [text] holds it, to choose among the others. */
    fun imported(fileName: String, text: String): Choice =
        Choice("$IMPORTED$fileName", fileName.substringBeforeLast('.'), "imported") { load(fileName, text) }

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
    private const val MOONSHINE_DARK_NAME = "moonshine Dark"

    val MOONSHINE_DARK: CodeTheme = run {
        val keyword = TokenStyle(Color(0xFFC59BFF))
        val type = TokenStyle(Color(0xFF6FD0C5))
        val comment = TokenStyle(Color(0xFF7D8494), italic = true)
        CodeTheme(
            name = MOONSHINE_DARK_NAME,
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

/** The scheme the person's JetBrains IDE uses, where it can look and it is not one bundled: none in a browser. */
internal expect suspend fun installedChoices(): List<CodeThemes.Choice>

/** A theme the person's VS Code has installed, by its name or label, where it can look; null in a browser. */
internal expect suspend fun installedVsCodeTheme(name: String): VsCodeTheme?
