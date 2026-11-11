package com.isycat.compose.code

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Code themes as moonshine's IDE has them: bundled, imported from JetBrains schemes, VS Code themes and settings. */
class CodeThemesTest {
    private fun color(hex: String) = checkNotNull(colorOf(hex))

    @Test
    fun theThemesAreMoonshinesWithTheirNamesAndSources() {
        assertEquals(
            listOf(
                "GitHub Dark Default", "One Dark Pro", "Dracula", "Ayu Dark", "moonshine Dark", "Islands Dark", "Dark 2026",
                "Leaf", "Matcha", "isycat", "isycat VS Code",
            ),
            CodeThemes.builtIn.map { it.label },
        )
        assertEquals("IntelliJ IDEA's default", CodeThemes.builtIn.single { it.label == "Islands Dark" }.detail)
        assertEquals(CodeThemes.DEFAULT_ID, CodeThemes.builtIn.first().id)
        assertEquals(CodeThemes.builtIn.map { it.id }.distinct(), CodeThemes.builtIn.map { it.id })
    }

    @Test
    fun everyBuiltInThemeLoadsDarkAndColoursKotlin() = runBlocking {
        CodeThemes.builtIn.forEach { choice ->
            val theme = choice.load()
            assertTrue(theme.dark, choice.label)
            assertTrue(theme.style(KotlinKeys.KEYWORD).foreground != null, "${choice.label} colours keywords")
            assertTrue(theme.style(KotlinKeys.STRING).foreground != theme.style(KotlinKeys.KEYWORD).foreground, "${choice.label} tells strings from keywords")
        }
    }

    @Test
    fun gitHubDarkDefaultColoursKotlinAsVsCodeDoes() = runBlocking {
        val theme = CodeThemes.builtIn.first().load()
        assertEquals(color("#0d1117"), theme.palette.background)
        assertEquals(color("#ff7b72"), theme.style(KotlinKeys.CONTROL_KEYWORD).foreground)
    }

    @Test
    fun isycatIsTheBundledSchemeOnDarculaInSourceCodePro() = runBlocking {
        val theme = CodeThemes.builtIn.single { it.id == CodeThemes.ISYCAT_ID }.load()
        assertEquals("isycat", theme.name)
        assertEquals(Color(0xFF131212), theme.palette.background)
        assertEquals(Color(0xFFBA7ACC), theme.style(KotlinKeys.KEYWORD).foreground, "Kotlin's keywords as Java's, as IntelliJ inherits them")
        assertEquals(Color(0xFF9A74B1), theme.style(DefaultKeys.KEYWORD).foreground)
        assertTrue(theme.style(DefaultKeys.KEYWORD).bold)
        assertEquals("Source Code Pro", theme.font?.families?.first())
    }

    @Test
    fun islandsDarkIsIntellijsDefaultDarkScheme() = runBlocking {
        val theme = JetBrainsSchemes.islandsDark()
        assertEquals("Islands Dark", theme.name)
        assertEquals(Color(0xFF191A1C), theme.palette.background)
        assertEquals(Color(0xFFBCBEC4), theme.palette.foreground)
    }

    @Test
    fun choicesKeptFromTheEarlierSetAreCarriedOver() {
        val default = CodeThemes.ISYCAT_ID
        assertEquals("moonshine:one-dark-pro", CodeThemes.carriedOver("moonshine:one-dark-pro-night-flat", default))
        assertEquals("jetbrains:Islands Dark", CodeThemes.carriedOver("jetbrains:Darcula", default))
        assertEquals("vscode-default:Dark 2026", CodeThemes.carriedOver("vscode:Dark 2026", default))
        assertEquals(CodeThemes.ISYCAT_ID, CodeThemes.carriedOver("""jetbrains:C:\Users\a\JetBrains\Rider2025.3\colors\isycat.icls""", "x"))
        assertEquals(CodeThemes.ISYCAT_ID, CodeThemes.carriedOver("jetbrains:/home/a/.config/JetBrains/IntelliJIdea2025.3/colors/isycat.icls", "x"))
        val mine = """jetbrains:C:\JetBrains\IntelliJIdea2025.3\colors\mine.icls"""
        assertEquals(mine, CodeThemes.carriedOver(mine, "x"))
        assertEquals(default, CodeThemes.carriedOver("vscode:Monokai", default))
        assertEquals(default, CodeThemes.carriedOver("""vscode-settings:C:\Users\a\settings.json""", default))
        assertEquals("moonshine:dark", CodeThemes.carriedOver("moonshine:dark", default))
        assertEquals("imported:mine.icls", CodeThemes.carriedOver("imported:mine.icls", default))
        CodeThemes.builtIn.forEach { assertEquals(it.id, CodeThemes.carriedOver(it.id, default)) }
    }

    @Test
    fun darculaColoursKotlinAsIntellijDoes() = runBlocking {
        val darcula = JetBrainsSchemes.darcula()
        assertEquals(Color(0xFF2B2B2B), darcula.palette.background)
        assertEquals(color("#cc7832"), darcula.style(KotlinKeys.KEYWORD).foreground)
        assertEquals(color("#6897bb"), darcula.style(KotlinKeys.NUMBER).foreground)
        assertEquals(color("#6a8759"), darcula.style(KotlinKeys.STRING).foreground)
        assertEquals(color("#808080"), darcula.style(KotlinKeys.LINE_COMMENT).foreground)
        val property = darcula.style(KotlinKeys.PACKAGE_PROPERTY)
        assertEquals(color("#9876aa"), property.foreground, "a top-level property as a static field")
        assertTrue(property.italic)
    }

    @Test
    fun anImportedJetBrainsSchemeIsBuiltOnDarcula() = runBlocking {
        val scheme = """
            <scheme name="mine" version="142" parent_scheme="Darcula">
              <colors><option name="CARET_COLOR" value="ff0000" /></colors>
              <attributes>
                <option name="KOTLIN_KEYWORD"><value><option name="FOREGROUND" value="00ff00" /><option name="FONT_TYPE" value="1" /></value></option>
              </attributes>
            </scheme>
        """.trimIndent()
        val theme = CodeThemes.load("mine.icls", scheme)
        assertEquals("mine", theme.name)
        assertEquals(Color(0xFFFF0000), theme.palette.caret)
        assertEquals(Color(0xFF00FF00), theme.style(KotlinKeys.KEYWORD).foreground)
        assertTrue(theme.style(KotlinKeys.KEYWORD).bold)
        assertEquals(color("#6897bb"), theme.style(KotlinKeys.NUMBER).foreground, "what it leaves out comes from Darcula")
    }

    @Test
    fun anImportedVsCodeThemeAndSettingsColourByScope() = runBlocking {
        val theme = """
            // A theme of one's own, with comments as VS Code allows.
            { "name": "Mine", "type": "dark", "colors": { "editor.background": "#101010" },
              "tokenColors": [ { "scope": ["keyword", "storage"], "settings": { "foreground": "#ff8800", "fontStyle": "italic" } }, ] }
        """.trimIndent()
        val mine = CodeThemes.load("mine-color-theme.json", theme)
        assertEquals(Color(0xFF101010), mine.palette.background)
        assertEquals(Color(0xFFFF8800), mine.style(KotlinKeys.DECLARING_KEYWORD).foreground)
        assertTrue(mine.style(KotlinKeys.DECLARING_KEYWORD).italic)
        val settings = """
            "workbench.colorTheme": "Dark Modern",
            "editor.tokenColorCustomizations": { "numbers": "#00ffff" },
            "workbench.colorCustomizations": { "editor.background": "#202020" },
            "editor.fontSize": 16,
        """.trimIndent()
        val customised = CodeThemes.load("settings.json", settings)
        assertEquals(Color(0xFF00FFFF), customised.style(KotlinKeys.NUMBER).foreground)
        assertEquals(Color(0xFF202020), customised.palette.background)
        assertEquals(16f, customised.font?.size)
        assertTrue(customised.style(KotlinKeys.STRING).foreground != null, "the rest from Dark Modern and the themes it includes")
    }

    @Test
    fun vsCodesDefaultThemesIncludeTheirBases() = runBlocking {
        val dark = checkNotNull(VsCodeThemes.bundled(VsCodeThemes.DEFAULT_THEME))
        assertTrue(dark.dark)
        // Dark 2026 includes Dark Modern, which includes Dark+, which includes Dark (Visual Studio).
        assertTrue(dark.tokenColors.size > 100, "${dark.tokenColors.size} rules")
    }

    @Test
    fun aSchemeWithADocumentTypeIsRefused() {
        assertFailsWith<IllegalArgumentException> { readXml("<!DOCTYPE x [<!ENTITY a \"b\">]><scheme name=\"&a;\"/>") }
        assertEquals("a & b", readXml("<scheme name=\"a &amp; b\"/>").attribute("name"))
    }
}
