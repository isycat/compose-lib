package com.isycat.compose.code

import androidx.compose.ui.graphics.Color

/** How a line drawn along or around text looks, as JetBrains schemes say it (`EFFECT_TYPE`). */
enum class TextEffect { BOX, UNDERLINE, WAVE, STRIKEOUT, BOLD_UNDERLINE, BOLD_DOTTED }

/**
 * How one kind of text looks: its colour and background, bold or italic, and a line along or around it. A colour left
 * null leaves the text its plain colour, or no background.
 */
data class TokenStyle(
    val foreground: Color? = null,
    val background: Color? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val effect: TextEffect? = null,
    val effectColor: Color? = null,
) {
    companion object {
        val PLAIN = TokenStyle()
    }
}

/**
 * A code editor's own colours around the text: its background and plain text, the caret and its line, selections,
 * line numbers, matched brackets and what is wrong.
 */
data class EditorPalette(
    val background: Color,
    val foreground: Color,
    val caret: Color,
    val caretRow: Color,
    val selection: Color,
    val lineNumber: Color,
    val lineNumberCaret: Color,
    val gutter: Color = background,
    val matchedBracket: Color,
    val error: Color,
    val warning: Color,
    /** Where suggestions are listed: their background, the chosen one's, and their text. */
    val widget: Color = background,
    val widgetSelection: Color = selection,
    val widgetForeground: Color = foreground,
)

/** The font code is drawn in: its families (the first available), its size in points and its line spacing. */
data class CodeFont(
    val families: List<String>,
    val size: Float,
    /** The line's height over the font's size, or, from VS Code, a height in pixels where [lineHeightPixels]. */
    val lineSpacing: Float,
    val lineHeightPixels: Boolean = false,
    val ligatures: Boolean = false,
)

/**
 * A kind of text a theme colours: named and falling back as JetBrains IDEs' are (its [name] is their attribute key,
 * [fallback] the key it takes its look from where a scheme gives it none), and seen as VS Code's TextMate grammars
 * scope it ([scopes], outermost first), so a VS Code theme colours it as VS Code colours that text.
 */
class HighlightKey private constructor(val name: String, val fallback: HighlightKey?, val scopes: List<String>) {
    override fun toString(): String = name

    companion object {
        private val known = HashMap<String, HighlightKey>()

        /** The key named [name]; made, with its [fallback] and [scopes], the first time it is asked for. */
        fun of(name: String, fallback: HighlightKey? = null, scopes: List<String> = emptyList()): HighlightKey =
            known.getOrPut(name) { HighlightKey(name, fallback, scopes) }
    }
}

/**
 * The kinds of text every language's fall back to, named and falling back as JetBrains IDEs' do
 * (`DefaultLanguageHighlighterColors`), so a JetBrains scheme colours code as it colours its own. [TEXT] is the
 * editor's plain text, its colour and background.
 */
object DefaultKeys {
    val TEXT = HighlightKey.of("TEXT")
    val IDENTIFIER = HighlightKey.of("DEFAULT_IDENTIFIER", TEXT)
    val NUMBER = HighlightKey.of("DEFAULT_NUMBER")
    val KEYWORD = HighlightKey.of("DEFAULT_KEYWORD")
    val STRING = HighlightKey.of("DEFAULT_STRING")
    val BLOCK_COMMENT = HighlightKey.of("DEFAULT_BLOCK_COMMENT")
    val LINE_COMMENT = HighlightKey.of("DEFAULT_LINE_COMMENT")
    val OPERATION_SIGN = HighlightKey.of("DEFAULT_OPERATION_SIGN")
    val BRACES = HighlightKey.of("DEFAULT_BRACES")
    val DOT = HighlightKey.of("DEFAULT_DOT")
    val SEMICOLON = HighlightKey.of("DEFAULT_SEMICOLON")
    val COMMA = HighlightKey.of("DEFAULT_COMMA")
    val PARENTHESES = HighlightKey.of("DEFAULT_PARENTHS")
    val BRACKETS = HighlightKey.of("DEFAULT_BRACKETS")
    val CONSTANT = HighlightKey.of("DEFAULT_CONSTANT", IDENTIFIER)
    val FUNCTION_DECLARATION = HighlightKey.of("DEFAULT_FUNCTION_DECLARATION", IDENTIFIER)
    val FUNCTION_CALL = HighlightKey.of("DEFAULT_FUNCTION_CALL", IDENTIFIER)
    val PARAMETER = HighlightKey.of("DEFAULT_PARAMETER", IDENTIFIER)
    val CLASS_REFERENCE = HighlightKey.of("DEFAULT_CLASS_REFERENCE", IDENTIFIER)
    val INSTANCE_FIELD = HighlightKey.of("DEFAULT_INSTANCE_FIELD", IDENTIFIER)
    val STATIC_FIELD = HighlightKey.of("DEFAULT_STATIC_FIELD", IDENTIFIER)
    val BAD_CHARACTER = HighlightKey.of("BAD_CHARACTER")
}

/**
 * Kotlin's kinds of text, as JetBrains' Kotlin plugin names them and falls them back (`KotlinHighlightingColors`), each
 * with the scopes VS Code's Kotlin grammar gives such text.
 */
object KotlinKeys {
    private fun kotlin(scope: String) = listOf("source.kotlin", "$scope.kotlin")

    private val JAVA_KEYWORD = HighlightKey.of("JAVA_KEYWORD", DefaultKeys.KEYWORD)
    val KEYWORD = HighlightKey.of("KOTLIN_KEYWORD", JAVA_KEYWORD, kotlin("keyword.other"))

    /** `if`, `for`, `return` and the like. */
    val CONTROL_KEYWORD = HighlightKey.of("KOTLIN_KEYWORD:control", KEYWORD, kotlin("keyword.control"))

    /** `val`, `var`, `fun`, `class`: what declares. */
    val DECLARING_KEYWORD = HighlightKey.of("KOTLIN_KEYWORD:declaring", KEYWORD, kotlin("storage.type"))

    /** `private`, `override` and the other modifiers. */
    val MODIFIER_KEYWORD = HighlightKey.of("KOTLIN_KEYWORD:modifier", KEYWORD, kotlin("storage.modifier"))

    /** `true`, `false` and `null`. */
    val LITERAL_KEYWORD = HighlightKey.of("KOTLIN_KEYWORD:literal", KEYWORD, kotlin("constant.language"))

    /** `this` and `super`. */
    val SELF_KEYWORD = HighlightKey.of("KOTLIN_KEYWORD:self", KEYWORD, kotlin("variable.language.this"))
    val NUMBER = HighlightKey.of("KOTLIN_NUMBER", DefaultKeys.NUMBER, kotlin("constant.numeric"))
    val STRING = HighlightKey.of("KOTLIN_STRING", DefaultKeys.STRING, kotlin("string.quoted.double"))
    val OPERATION_SIGN = HighlightKey.of("KOTLIN_OPERATION_SIGN", DefaultKeys.OPERATION_SIGN, kotlin("keyword.operator"))
    val PARENTHESIS = HighlightKey.of("KOTLIN_PARENTHESIS", DefaultKeys.PARENTHESES, kotlin("punctuation.section.group"))
    val BRACES = HighlightKey.of("KOTLIN_BRACES", DefaultKeys.BRACES, kotlin("punctuation.section.block"))
    val BRACKETS = HighlightKey.of("KOTLIN_BRACKETS", DefaultKeys.BRACKETS, kotlin("punctuation.section.brackets"))
    val COMMA = HighlightKey.of("KOTLIN_COMMA", DefaultKeys.COMMA, kotlin("punctuation.separator.delimiter"))
    val SEMICOLON = HighlightKey.of("KOTLIN_SEMICOLON", DefaultKeys.SEMICOLON, kotlin("punctuation.terminator"))
    val DOT = HighlightKey.of("KOTLIN_DOT", DefaultKeys.DOT, kotlin("punctuation.accessor"))
    val LINE_COMMENT = HighlightKey.of("KOTLIN_LINE_COMMENT", DefaultKeys.LINE_COMMENT, kotlin("comment.line.double-slash"))
    val BLOCK_COMMENT = HighlightKey.of("KOTLIN_BLOCK_COMMENT", DefaultKeys.BLOCK_COMMENT, kotlin("comment.block"))
    val FUNCTION_DECLARATION = HighlightKey.of("KOTLIN_FUNCTION_DECLARATION", DefaultKeys.FUNCTION_DECLARATION, kotlin("entity.name.function"))
    val FUNCTION_CALL = HighlightKey.of("KOTLIN_FUNCTION_CALL", DefaultKeys.FUNCTION_CALL, kotlin("entity.name.function.call"))
    val PARAMETER = HighlightKey.of("KOTLIN_PARAMETER", DefaultKeys.PARAMETER, kotlin("variable.parameter"))

    /** A property declared at the top of a file, as a script's own parameters are: `val size by param(...)`. */
    val PACKAGE_PROPERTY = HighlightKey.of("KOTLIN_PACKAGE_PROPERTY", DefaultKeys.STATIC_FIELD, kotlin("variable.other.constant"))
    val INSTANCE_PROPERTY = HighlightKey.of("KOTLIN_INSTANCE_PROPERTY", DefaultKeys.INSTANCE_FIELD, kotlin("variable.other.property"))

    /** A property called on a value from outside its class, as a unit is on a number: the `mm` of `20.mm`. */
    val EXTENSION_PROPERTY = HighlightKey.of("KOTLIN_EXTENSION_PROPERTY", INSTANCE_PROPERTY, kotlin("variable.other.property"))
    val IDENTIFIER = HighlightKey.of("KOTLIN_IDENTIFIER", DefaultKeys.IDENTIFIER, listOf("source.kotlin"))
}

/**
 * How code looks: the editor's [palette] and, for each kind of text ([HighlightKey]), its style. A JetBrains scheme's
 * styles are found as JetBrains IDEs find theirs (`EditorColorsSchemeImpl`): a theme with no [parent] gives a key its
 * own style, else the first along its fallbacks; one built on a [parent] looks first for a style given directly, its own
 * or its parent's, then along the key's fallbacks, then asks its parent. A style that is null in [styles] is a mark to
 * inherit, standing for nothing. A VS Code theme colours each key by its scopes through [textMate] instead, as VS Code
 * colours text the Kotlin grammar scopes so.
 */
class CodeTheme(
    val name: String,
    val dark: Boolean,
    val palette: EditorPalette,
    styles: Map<String, TokenStyle?>,
    val parent: CodeTheme? = null,
    val font: CodeFont? = null,
    /** Where it came from, as `JetBrains scheme isycat`, said where themes are chosen. */
    val source: String = "",
    /** For a VS Code theme, its rules, which colour text by scope in place of [styles]. */
    val textMate: TextMateTheme? = null,
    /** What an importer keeps of the theme beyond its styles, as a JetBrains scheme's editor colours. */
    internal val colors: Map<String, Color?> = emptyMap(),
) {
    private val defined: Map<String, TokenStyle?> = styles
    private val resolved = HashMap<String, TokenStyle>()

    /** How text of [key] looks. */
    fun style(key: HighlightKey): TokenStyle = resolved.getOrPut(key.name) {
        textMate?.let { styleByScopes(it, key) } ?: attributes(key)?.style ?: TokenStyle.PLAIN
    }

    private fun styleByScopes(theme: TextMateTheme, key: HighlightKey): TokenStyle {
        val stack = key.scopes.fold(null as ScopeStack?) { parent, scope -> ScopeStack(parent, scope) }
        val match = theme.match(stack) ?: theme.defaults
        val colors = theme.colorMap
        fun colour(id: Int) = colors.getOrNull(id)?.let(::colorOf)
        val style = if (match.fontStyle == FontStyle.NOT_SET) theme.defaults.fontStyle else match.fontStyle
        return TokenStyle(
            foreground = colour(match.foregroundId) ?: colour(theme.defaults.foregroundId),
            bold = style and FontStyle.BOLD != 0,
            italic = style and FontStyle.ITALIC != 0,
            effect = if (style and FontStyle.UNDERLINE != 0) TextEffect.UNDERLINE else if (style and FontStyle.STRIKETHROUGH != 0) TextEffect.STRIKEOUT else null,
        )
    }

    /** What this theme alone writes for [key]: a style, a mark to inherit, or nothing. */
    internal fun own(key: HighlightKey): Entry? = if (key.name in defined) Entry(defined[key.name]) else null

    /** A style a theme writes for a key, or, where [style] is null, its mark to inherit from the key's fallback. */
    internal class Entry(val style: TokenStyle?) {
        val inherits: Boolean get() = style == null
    }

    private fun attributes(key: HighlightKey): Entry? {
        val parent = parent ?: return baseAttributes(key)
        val direct = directly(key)
        if (direct != null && !direct.inherits) return direct
        key.fallback?.let { fallback -> fallbackAttributes(fallback)?.let { return it } }
        return parent.attributes(key)
    }

    /** A theme with no parent: its own style for [key], else the first along its fallbacks. */
    private fun baseAttributes(key: HighlightKey): Entry? {
        own(key)?.let { return it }
        val fallback = key.fallback ?: return null
        return fallbackAttributes(fallback)?.takeUnless { it.inherits }
    }

    /** The style written directly for [key], here or in the themes this builds on. */
    private fun directly(key: HighlightKey): Entry? = own(key) ?: parent?.directly(key)

    private fun fallbackAttributes(from: HighlightKey): Entry? {
        var key = from
        while (true) {
            val found = directly(key)
            val next = key.fallback
            if (found != null && (!found.inherits || next == null)) return found
            key = next ?: return null
        }
    }

    override fun toString(): String = name
}

/** A colour as a theme writes it, `#rgb`, `#rgba`, `#rrggbb` or `#rrggbbaa`; null for anything else. */
fun colorOf(hex: String?): Color? {
    if (hex == null || !hex.startsWith('#')) return null
    val digits = hex.substring(1)
    val full = when (digits.length) {
        3, 4 -> digits.map { "$it$it" }.joinToString("")
        6, 8 -> digits
        else -> return null
    }
    val value = full.toLongOrNull(16) ?: return null
    return if (full.length == 6) Color(0xFF000000L or value) else Color(((value and 0xFF) shl 24) or (value ushr 8))
}
