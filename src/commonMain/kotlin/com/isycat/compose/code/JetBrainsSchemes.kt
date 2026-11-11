package com.isycat.compose.code

import androidx.compose.ui.graphics.Color

/**
 * JetBrains colour schemes (`.icls`, as IntelliJ IDEA and Rider keep them and export them), read as those IDEs read
 * them (`AbstractColorsScheme`): each kind of text's attributes (colour, background, bold and italic, and a line drawn
 * along or round it in its own colour), and the editor's colours. A scheme built on Darcula or the light Default finds
 * what it leaves out there; both are bundled as IntelliJ IDEA 2025.3 ships them (intellij-community, Apache 2.0), with
 * what the Kotlin, Compose, YAML and Markdown plugins add, and Islands Dark, its default dark scheme.
 */
object JetBrainsSchemes {
    /** A scheme as its file holds it, before any parent is looked to. */
    class Scheme(
        val name: String,
        val parentName: String?,
        /** Each kind of text's attributes; null for one marked to inherit (`baseAttributes`). */
        val attributes: Map<String, TokenStyle?>,
        val colors: Map<String, Color?>,
        val options: Map<String, String>,
    )

    private var darcula: CodeTheme? = null
    private var default: CodeTheme? = null
    private var islandsDark: CodeTheme? = null

    /** Darcula, as IntelliJ IDEA 2025.3 ships it, with the Kotlin, Compose, YAML and Markdown plugins' additions. */
    suspend fun darcula(): CodeTheme = darcula ?: bundled(
        "Darcula",
        dark = true,
        additions = listOf("Darcula_Kotlin.xml", "IntelliJComposeColorSchemeDarcula.xml", "YAMLDarcula.xml", "MarkdownDarcula.xml", "MarkdownDefault.xml"),
    ).also { darcula = it }

    /** The light Default scheme, as IntelliJ IDEA 2025.3 ships it, with the Kotlin, Compose and Markdown plugins' additions. */
    suspend fun default(): CodeTheme = default ?: bundled(
        "Default",
        dark = false,
        additions = listOf("Default_Kotlin.xml", "IntelliJComposeColorSchemeDefault.xml", "MarkdownDefault.xml"),
    ).also { default = it }

    /** Islands Dark, IntelliJ IDEA 2025.3's default dark scheme (its default Islands Dark theme's), on Darcula. */
    suspend fun islandsDark(): CodeTheme = islandsDark ?: load(bundledText("IslandsDark.xml"), "Islands Dark").also { islandsDark = it }

    private suspend fun bundled(name: String, dark: Boolean, additions: List<String>): CodeTheme {
        val scheme = parse(bundledText("$name.xml"))
        val attributes = LinkedHashMap(scheme.attributes)
        for (addition in additions) for ((key, style) in parseAttributes(readXml(bundledText(addition)))) attributes.getOrPut(key) { style }
        return theme(Scheme(scheme.name, null, attributes, scheme.colors, scheme.options), null, dark, font = null, source = "JetBrains, $name")
    }

    /**
     * The scheme [text] holds, from the file [fileName], as a theme built on the scheme it names (Darcula, or Default
     * for a light one), its editor font [font] where the scheme gives none of its own.
     */
    suspend fun load(text: String, fileName: String, font: CodeFont? = null): CodeTheme {
        val scheme = parse(text)
        val parent = when (scheme.parentName) {
            null, "Darcula", "_@user_Darcula" -> darcula()
            "Default", "_@user_Default" -> default()
            else -> if (scheme.parentName.contains("Dark", ignoreCase = true) || scheme.parentName.contains("Darcula", ignoreCase = true)) darcula() else default()
        }
        return theme(scheme, parent, parent.dark, font ?: fontOf(scheme), "JetBrains scheme ${scheme.name} ($fileName)")
    }

    fun parse(text: String): Scheme {
        val root = readXml(text)
        val scheme = if (root.name == "scheme") root else root.elements("scheme").firstOrNull() ?: throw IllegalArgumentException("no scheme in it")
        val colors = LinkedHashMap<String, Color?>()
        val options = LinkedHashMap<String, String>()
        var attributes: Map<String, TokenStyle?> = emptyMap()
        for (child in scheme.children) when (child.name) {
            "option" -> child.attribute("name").takeIf { it.isNotEmpty() }?.let { options[it] = child.attribute("value") }
            "colors" -> for (option in child.elements("option")) {
                colors[option.attribute("name")] = color(option.attribute("value").takeIf { option.has("value") })
            }
            "attributes" -> attributes = parseAttributes(child)
            "font" -> for (option in child.elements("option")) options["font." + option.attribute("name")] = option.attribute("value")
        }
        val parent = if (scheme.attribute("default_scheme") == "true") null else scheme.attribute("parent_scheme").ifEmpty { "Default" }
        return Scheme(scheme.attribute("name"), parent, attributes, colors, options)
    }

    /** `<option name="KEY"><value>…</value></option>` entries, or `baseAttributes` alone marking a key to inherit. */
    private fun parseAttributes(element: XmlElement): Map<String, TokenStyle?> {
        val out = LinkedHashMap<String, TokenStyle?>()
        for (option in element.elements("option")) {
            val name = option.attribute("name")
            val value = option.elements("value").firstOrNull()
            when {
                value != null -> out[name] = style(value)
                option.has("baseAttributes") -> out[name] = null
            }
        }
        return out
    }

    private fun style(value: XmlElement): TokenStyle {
        val fields = value.elements("option").associate { it.attribute("name") to it.attribute("value").takeIf { _ -> it.has("value") } }
        val fontType = fields["FONT_TYPE"]?.toIntOrNull()?.takeIf { it in 0..BOLD_ITALIC } ?: 0
        val effectColor = color(fields["EFFECT_COLOR"])
        val effect = when (fields["EFFECT_TYPE"]?.toIntOrNull() ?: 0) {
            0 -> TextEffect.BOX
            1 -> TextEffect.UNDERLINE
            2 -> TextEffect.WAVE
            3 -> TextEffect.STRIKEOUT
            4 -> TextEffect.BOLD_UNDERLINE
            5 -> TextEffect.BOLD_DOTTED
            else -> null
        }
        return TokenStyle(
            foreground = color(fields["FOREGROUND"]),
            background = color(fields["BACKGROUND"]),
            bold = fontType and 1 != 0,
            italic = fontType and 2 != 0,
            // An effect draws only in a colour of its own, as JetBrains IDEs draw it.
            effect = effect.takeIf { effectColor != null },
            effectColor = effectColor,
        )
    }

    /** A colour as JetBrains writes it: six hex digits, opaque. */
    private fun color(value: String?): Color? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val rgb = text.toIntOrNull(HEX) ?: return null
        return Color(OPAQUE or rgb)
    }

    /** The scheme's own editor font, where it keeps one rather than following the IDE's Font settings. */
    private fun fontOf(scheme: Scheme): CodeFont? {
        val name = scheme.options["EDITOR_FONT_NAME"] ?: scheme.options["font.EDITOR_FONT_NAME"] ?: return null
        val size = (scheme.options["EDITOR_FONT_SIZE"] ?: scheme.options["font.EDITOR_FONT_SIZE"])?.toFloatOrNull() ?: DEFAULT_SIZE
        val spacing = scheme.options["LINE_SPACING"]?.toFloatOrNull() ?: DEFAULT_LINE_SPACING
        return CodeFont(listOf(name), size, spacing, ligatures = scheme.options["EDITOR_LIGATURES"] == "true")
    }

    private fun theme(scheme: Scheme, parent: CodeTheme?, dark: Boolean, font: CodeFont?, source: String): CodeTheme {
        val parentColors = parent?.colors.orEmpty()
        fun colour(key: String): Color? = if (key in scheme.colors) scheme.colors[key] else parentColors[key]
        fun attribute(key: String): TokenStyle? = (if (key in scheme.attributes) scheme.attributes[key] else null) ?: parent?.own(HighlightKey.of(key))?.style
        val text = attribute("TEXT")
        val background = text?.background ?: if (dark) Color(0xFF2B2B2B) else Color.White
        val foreground = text?.foreground ?: if (dark) Color(0xFFA9B7C6) else Color.Black
        val selection = colour("SELECTION_BACKGROUND") ?: Color(0xFF214283)
        val lookup = attribute("LOOKUP_COLOR")
        val palette = EditorPalette(
            background = background,
            foreground = foreground,
            caret = colour("CARET_COLOR") ?: foreground,
            caretRow = colour("CARET_ROW_COLOR") ?: background,
            selection = selection,
            lineNumber = colour("LINE_NUMBERS_COLOR") ?: foreground.copy(alpha = 0.5f),
            lineNumberCaret = colour("LINE_NUMBER_ON_CARET_ROW_COLOR") ?: foreground,
            // The new UI's gutter (EditorGutterColor): its own key, else the editor's background; GUTTER_BACKGROUND is the old UI's.
            gutter = colour("EDITOR_GUTTER_BACKGROUND") ?: background,
            matchedBracket = attribute("MATCHED_BRACE_ATTRIBUTES")?.background ?: Color.Transparent,
            error = attribute("ERRORS_ATTRIBUTES")?.effectColor ?: Color(0xFFBC3F3C),
            warning = attribute("WARNING_ATTRIBUTES")?.effectColor ?: Color(0xFFBE9117),
            widget = colour("LOOKUP_COLOR") ?: lookup?.background ?: background,
            widgetSelection = selection,
            widgetForeground = foreground,
        )
        val colors = LinkedHashMap(parentColors)
        colors.putAll(scheme.colors)
        return CodeTheme(scheme.name, dark, palette, scheme.attributes, parent, font, source, colors = colors)
    }

    /** IntelliJ IDEA's editor font size and line spacing when its Font settings say none. */
    const val DEFAULT_SIZE = 13f
    const val DEFAULT_LINE_SPACING = 1.2f

    private const val BOLD_ITALIC = 3
    private const val HEX = 16
    private const val OPAQUE = 0xFF000000.toInt()
}
