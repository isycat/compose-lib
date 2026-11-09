package com.isycat.compose.code

import kotlin.jvm.JvmField
import kotlin.math.sign

/**
 * Font style bits as TextMate themes give them: none set is -1 (inherit), else italic, bold, underline, strikethrough.
 */
object FontStyle {
    const val NOT_SET = -1
    const val NONE = 0
    const val ITALIC = 1
    const val BOLD = 2
    const val UNDERLINE = 4
    const val STRIKETHROUGH = 8
}

/** What a scope's style is: its font style bits and its colours, as ids into the theme's [TextMateTheme.colorMap]. */
class StyleAttributes(@JvmField val fontStyle: Int, @JvmField val foregroundId: Int, @JvmField val backgroundId: Int)

/** A list of scopes, the innermost last, as a theme matches against it. */
class ScopeStack(@JvmField val parent: ScopeStack?, @JvmField val scopeName: String) {
    fun push(scope: String): ScopeStack = ScopeStack(this, scope)

    fun segments(): List<String> {
        val out = ArrayList<String>()
        var at: ScopeStack? = this
        while (at != null) {
            out += at.scopeName
            at = at.parent
        }
        return out.asReversed()
    }

    override fun toString(): String = segments().joinToString(" ")
}

/** One rule of a theme, as read: the scope it styles, the scopes it must be inside (innermost first), and its style. */
class ParsedThemeRule(
    val scope: String,
    val parentScopes: List<String>?,
    val index: Int,
    val fontStyle: Int,
    val foreground: String?,
    val background: String?,
)

/** A rule in the theme's trie, as vscode-textmate keeps it. */
private class TrieRule(var scopeDepth: Int, val parentScopes: List<String>, var fontStyle: Int, var foreground: Int, var background: Int) {
    fun clone() = TrieRule(scopeDepth, parentScopes, fontStyle, foreground, background)

    fun acceptOverwrite(depth: Int, fontStyle: Int, foreground: Int, background: Int) {
        if (scopeDepth <= depth) scopeDepth = depth
        if (fontStyle != FontStyle.NOT_SET) this.fontStyle = fontStyle
        if (foreground != 0) this.foreground = foreground
        if (background != 0) this.background = background
    }
}

private class TrieElement(private val mainRule: TrieRule, private val rulesWithParentScopes: ArrayList<TrieRule> = ArrayList(), private val children: HashMap<String, TrieElement> = HashMap()) {
    fun match(scope: String): List<TrieRule> {
        if (scope.isNotEmpty()) {
            val dot = scope.indexOf('.')
            val head = if (dot < 0) scope else scope.substring(0, dot)
            val tail = if (dot < 0) "" else scope.substring(dot + 1)
            children[head]?.let { return it.match(tail) }
        }
        val rules = ArrayList<TrieRule>(rulesWithParentScopes.size + 1)
        rules.addAll(rulesWithParentScopes)
        rules += mainRule
        rules.sortWith(::bySpecificity)
        return rules
    }

    fun insert(depth: Int, scope: String, parentScopes: List<String>?, fontStyle: Int, foreground: Int, background: Int) {
        if (scope.isEmpty()) {
            insertHere(depth, parentScopes, fontStyle, foreground, background)
            return
        }
        val dot = scope.indexOf('.')
        val head = if (dot < 0) scope else scope.substring(0, dot)
        val tail = if (dot < 0) "" else scope.substring(dot + 1)
        val child = children.getOrPut(head) { TrieElement(mainRule.clone(), ArrayList(rulesWithParentScopes.map { it.clone() })) }
        child.insert(depth + 1, tail, parentScopes, fontStyle, foreground, background)
    }

    private fun insertHere(depth: Int, parentScopes: List<String>?, fontStyle: Int, foreground: Int, background: Int) {
        if (parentScopes == null) {
            mainRule.acceptOverwrite(depth, fontStyle, foreground, background)
            return
        }
        for (rule in rulesWithParentScopes) {
            if (compareLists(rule.parentScopes, parentScopes) == 0) {
                rule.acceptOverwrite(depth, fontStyle, foreground, background)
                return
            }
        }
        rulesWithParentScopes += TrieRule(
            depth, parentScopes,
            if (fontStyle == FontStyle.NOT_SET) mainRule.fontStyle else fontStyle,
            if (foreground == 0) mainRule.foreground else foreground,
            if (background == 0) mainRule.background else background,
        )
    }

    companion object {
        fun bySpecificity(a: TrieRule, b: TrieRule): Int {
            if (a.scopeDepth != b.scopeDepth) return b.scopeDepth - a.scopeDepth
            var i = 0
            var j = 0
            while (true) {
                if (a.parentScopes.getOrNull(i) == ">") i++
                if (b.parentScopes.getOrNull(j) == ">") j++
                if (i >= a.parentScopes.size || j >= b.parentScopes.size) break
                val difference = b.parentScopes[j].length - a.parentScopes[i].length
                if (difference != 0) return difference
                i++
                j++
            }
            return b.parentScopes.size - a.parentScopes.size
        }
    }
}

/**
 * A TextMate theme as VS Code's tokenizer holds it (vscode-textmate 9.3's `Theme`, ported rule for rule, as moonshine's
 * IDE has it): its rules in a trie by scope segments, the most specific matching rule winning, rules with parent scopes
 * before those without, and a colour map that gives each colour an id, as tokens carry them.
 */
class TextMateTheme private constructor(private val colors: ColorMap, val defaults: StyleAttributes, private val root: TrieElement) {
    private val cachedMatch = HashMap<String, List<TrieRule>>()

    /** The colours tokens name by id, id 0 standing for none. */
    val colorMap: List<String?> get() = colors.list()

    /** The style the innermost scope of [scopePath] takes, or null where no rule matches it. */
    fun match(scopePath: ScopeStack?): StyleAttributes? {
        if (scopePath == null) return defaults
        val rules = cachedMatch.getOrPut(scopePath.scopeName) { root.match(scopePath.scopeName) }
        val rule = rules.firstOrNull { matchesParents(scopePath.parent, it.parentScopes) } ?: return null
        return StyleAttributes(rule.fontStyle, rule.foreground, rule.background)
    }

    private fun matchesParents(path: ScopeStack?, parentScopes: List<String>): Boolean {
        if (parentScopes.isEmpty()) return true
        var scopePath = path
        var index = 0
        while (index < parentScopes.size) {
            var pattern = parentScopes[index]
            var mustMatch = false
            if (pattern == ">") {
                if (index == parentScopes.size - 1) return false
                pattern = parentScopes[++index]
                mustMatch = true
            }
            while (scopePath != null && !matchesScope(scopePath.scopeName, pattern)) {
                if (mustMatch) return false
                scopePath = scopePath.parent
            }
            if (scopePath == null) return false
            scopePath = scopePath.parent
            index++
        }
        return true
    }

    /** Colours by id, as a theme's tokens name them; frozen when a list is given. */
    class ColorMap(frozen: List<String>? = null) {
        private val idOf = HashMap<String, Int>()
        private val colorOf = ArrayList<String?>()
        private val isFrozen = frozen != null
        private var lastId = 0

        init {
            colorOf += null as String?
            frozen?.forEachIndexed { i, c ->
                idOf[c] = i
                while (colorOf.size <= i) colorOf += null as String?
                colorOf[i] = c
            }
        }

        fun id(color: String?): Int {
            if (color == null) return 0
            val upper = color.uppercase()
            idOf[upper]?.let { if (it != 0) return it }
            check(!isFrozen) { "Missing color in color map - $upper" }
            val id = ++lastId
            idOf[upper] = id
            while (colorOf.size <= id) colorOf += null as String?
            colorOf[id] = upper
            return id
        }

        fun list(): List<String?> = colorOf.toList()
    }

    companion object {
        /**
         * The theme [rules] make, each a scope selector (a string, comma separated, or a list) and its settings, as
         * a VS Code theme's `tokenColors` hold them.
         */
        fun create(rules: List<RawThemeRule>, colorMap: List<String>? = null): TextMateTheme = fromParsed(parse(rules), colorMap)

        fun parse(rules: List<RawThemeRule>): MutableList<ParsedThemeRule> {
            val out = ArrayList<ParsedThemeRule>()
            for ((index, rule) in rules.withIndex()) {
                val scopes: List<String> = when (val scope = rule.scope) {
                    is String -> scope.trimStart(',').trimEnd(',').split(',')
                    is List<*> -> scope.map { it.toString() }
                    else -> listOf("")
                }
                var fontStyle = FontStyle.NOT_SET
                rule.fontStyle?.let { style ->
                    fontStyle = FontStyle.NONE
                    for (part in style.split(' ')) when (part) {
                        "italic" -> fontStyle = fontStyle or FontStyle.ITALIC
                        "bold" -> fontStyle = fontStyle or FontStyle.BOLD
                        "underline" -> fontStyle = fontStyle or FontStyle.UNDERLINE
                        "strikethrough" -> fontStyle = fontStyle or FontStyle.STRIKETHROUGH
                    }
                }
                val foreground = rule.foreground?.takeIf(::isValidHexColor)
                val background = rule.background?.takeIf(::isValidHexColor)
                for (selector in scopes) {
                    val segments = selector.trim().split(' ')
                    val scope = segments.last()
                    val parents = if (segments.size > 1) segments.subList(0, segments.size - 1).reversed() else null
                    out += ParsedThemeRule(scope, parents, index, fontStyle, foreground, background)
                }
            }
            return out
        }

        fun fromParsed(rules: MutableList<ParsedThemeRule>, colorMap: List<String>?): TextMateTheme {
            rules.sortWith { a, b ->
                var r = a.scope.compareTo(b.scope).sign
                if (r == 0) r = compareLists(a.parentScopes, b.parentScopes)
                if (r == 0) r = a.index - b.index
                r
            }
            var fontStyle = FontStyle.NONE
            var foreground = "#000000"
            var background = "#ffffff"
            while (rules.isNotEmpty() && rules[0].scope.isEmpty()) {
                val rule = rules.removeAt(0)
                if (rule.fontStyle != FontStyle.NOT_SET) fontStyle = rule.fontStyle
                if (rule.foreground != null) foreground = rule.foreground
                if (rule.background != null) background = rule.background
            }
            val colors = ColorMap(colorMap)
            val defaults = StyleAttributes(fontStyle, colors.id(foreground), colors.id(background))
            val root = TrieElement(TrieRule(0, emptyList(), FontStyle.NOT_SET, 0, 0))
            for (rule in rules) root.insert(0, rule.scope, rule.parentScopes, rule.fontStyle, colors.id(rule.foreground), colors.id(rule.background))
            return TextMateTheme(colors, defaults, root)
        }

        private val HEX = listOf(Regex("^#[0-9a-fA-F]{6}$"), Regex("^#[0-9a-fA-F]{8}$"), Regex("^#[0-9a-fA-F]{3}$"), Regex("^#[0-9a-fA-F]{4}$"))

        fun isValidHexColor(color: String): Boolean = HEX.any { it.matches(color) }
    }
}

/** A theme rule as a theme file writes it: its scope (a string or a list of strings) and its settings. */
class RawThemeRule(val scope: Any?, val foreground: String?, val background: String?, val fontStyle: String?)

/** Whether [scope] is [pattern] or lies under it (`keyword.control` under `keyword`). */
internal fun matchesScope(scope: String, pattern: String): Boolean =
    pattern == scope || (scope.startsWith(pattern) && scope.length > pattern.length && scope[pattern.length] == '.')

/** vscode-textmate's `strArrCmp`: null first, then shorter first, then element by element. */
internal fun compareLists(a: List<String>?, b: List<String>?): Int {
    if (a == null && b == null) return 0
    if (a == null) return -1
    if (b == null) return 1
    if (a.size != b.size) return a.size - b.size
    for (i in a.indices) {
        val r = a[i].compareTo(b[i]).sign
        if (r != 0) return r
    }
    return 0
}
