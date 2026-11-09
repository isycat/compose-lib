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
    fun theDefaultIsOneDarkProNightFlatColouringKotlinAsVsCodeDoes() = runBlocking {
        val theme = CodeThemes.builtIn.first().load()
        assertEquals(CodeThemes.DEFAULT_ID, CodeThemes.builtIn.first().id)
        assertEquals(color("#16191d"), theme.palette.background)
        assertEquals(color("#c678dd"), theme.style(KotlinKeys.CONTROL_KEYWORD).foreground)
        assertEquals(color("#d19a66"), theme.style(KotlinKeys.NUMBER).foreground)
        assertEquals(color("#98c379"), theme.style(KotlinKeys.STRING).foreground)
        assertEquals(color("#7f848e"), theme.style(KotlinKeys.LINE_COMMENT).foreground)
        assertEquals(color("#61afef"), theme.style(KotlinKeys.FUNCTION_CALL).foreground)
        assertEquals(color("#e5c07b"), theme.style(KotlinKeys.PACKAGE_PROPERTY).foreground)
        assertEquals(color("#abb2bf"), theme.style(KotlinKeys.IDENTIFIER).foreground, "a plain name in the editor's colour")
    }

    @Test
    fun everyBuiltInThemeLoads() = runBlocking {
        assertEquals(listOf("One Dark Pro Night Flat", "One Dark Pro", "moonshine Dark", "Darcula"), CodeThemes.builtIn.map { it.label })
        CodeThemes.builtIn.forEach { choice ->
            val theme = choice.load()
            assertTrue(theme.dark, choice.label)
            assertTrue(theme.style(KotlinKeys.KEYWORD).foreground != null, "${choice.label} colours keywords")
        }
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
