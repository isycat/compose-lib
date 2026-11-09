package com.isycat.compose.code

import androidx.compose.ui.graphics.Color

/**
 * A VS Code colour theme as VS Code builds it (`ColorThemeData`): the theme file and those it includes, their `colors`
 * merged, their `tokenColors` joined in order, then a person's settings on top, `editor.tokenColorCustomizations` after
 * the theme's own rules so they win, and `workbench.colorCustomizations` over its colours.
 */
class VsCodeTheme(
    val name: String,
    /** "dark", "light", "hcDark" or "hcLight". */
    val type: String,
    val colors: Map<String, String>,
    val tokenColors: List<Map<String, Any?>>,
    /** What settings call it, in `workbench.colorTheme` and `"[Theme]"` sections: its id, or its label. */
    val settingsId: String = name,
) {
    val dark: Boolean get() = type == "dark" || type == "hcDark" || type == "vs-dark" || type == "hc-black"

    /** A colour the theme gives, or VS Code's default for it ([DEFAULTS]); null where neither says. */
    fun color(key: String): String? = colors[key] ?: DEFAULTS[key]?.let { if (dark) it.first else it.second }

    /**
     * The theme with [settings] on top, as VS Code applies a person's settings.json: the general customisations, then
     * those for this theme alone; in each, the groups (`comments`, `strings`…) before `textMateRules`, so a rule wins
     * where both name a scope. A colour customised to `default` takes VS Code's default rather than the theme's.
     */
    fun customized(settings: VsCodeSettings): VsCodeTheme {
        val custom = ArrayList<Map<String, Any?>>()
        for (section in settings.tokenColorCustomizations(settingsId)) {
            for ((group, scopes) in TOKEN_GROUPS) {
                val style: Map<String, Any?> = when (val value = section[group]) {
                    is String -> mapOf("foreground" to value)
                    is Map<*, *> -> value.entries.associate { it.key.toString() to it.value }
                    else -> continue
                }
                if (!VsCodeSettings.truthy(section[group])) continue
                for (scope in scopes) custom += mapOf("scope" to scope, "settings" to style)
            }
            for (rule in (section["textMateRules"] as? List<*>).orEmpty()) {
                val r = (rule as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: continue
                if (VsCodeSettings.truthy(r["scope"]) && VsCodeSettings.truthy(r["settings"])) custom += r
            }
        }
        val colours = LinkedHashMap(colors)
        for ((key, value) in settings.colorCustomizations(settingsId)) if (value == DEFAULT_COLOUR) colours.remove(key) else colours[key] = value
        return VsCodeTheme(name, type, colours, tokenColors + custom, settingsId)
    }

    /** The rules VS Code's tokenizer styles tokens by: the editor's colours first, then every rule with a scope. */
    fun rules(): List<RawThemeRule> {
        val out = ArrayList<RawThemeRule>()
        out += RawThemeRule(null, color("editor.foreground"), color("editor.background"), null)
        for (rule in tokenColors) {
            val scope = rule["scope"] ?: continue
            val settings = rule["settings"] as? Map<*, *> ?: continue
            out += RawThemeRule(scope, settings["foreground"] as? String, settings["background"] as? String, settings["fontStyle"] as? String)
        }
        return out
    }

    fun textMateTheme(): TextMateTheme = TextMateTheme.create(rules())

    companion object {
        /** The editor colours VS Code registers with defaults (dark, light), for those a theme leaves out. */
        val DEFAULTS: Map<String, Pair<String, String>> = mapOf(
            "editor.background" to ("#1E1E1E" to "#FFFFFF"),
            "editor.foreground" to ("#BBBBBB" to "#333333"),
            "editor.selectionBackground" to ("#264F78" to "#ADD6FF"),
            "editor.lineHighlightBorder" to ("#282828" to "#EEEEEE"),
            "editorLineNumber.foreground" to ("#858585" to "#237893"),
            "editorCursor.foreground" to ("#AEAFAD" to "#000000"),
            "editorBracketMatch.background" to ("#0064001A" to "#0064001A"),
            "editorError.foreground" to ("#F14C4C" to "#E51400"),
            "editorWarning.foreground" to ("#CCA700" to "#BF8803"),
            "editorSuggestWidget.background" to ("#252526" to "#F3F3F3"),
            "editorSuggestWidget.selectedBackground" to ("#04395E" to "#0060C0"),
            "editorSuggestWidget.foreground" to ("#D4D4D4" to "#000000"),
        )

        /** What a colour is customised to, to have VS Code's default rather than the theme's. */
        const val DEFAULT_COLOUR = "default"

        /** The groups `editor.tokenColorCustomizations` may name, and the scopes each stands for, as VS Code has them. */
        val TOKEN_GROUPS: Map<String, List<String>> = linkedMapOf(
            "comments" to listOf("comment", "punctuation.definition.comment"),
            "strings" to listOf("string", "meta.embedded.assembly"),
            "keywords" to listOf("keyword - keyword.operator", "keyword.control", "storage", "storage.type"),
            "numbers" to listOf("constant.numeric"),
            "types" to listOf("entity.name.type", "entity.name.class", "support.type", "support.class"),
            "functions" to listOf("entity.name.function", "support.function"),
            "variables" to listOf("variable", "entity.name.variable"),
        )

        /**
         * The theme [text] holds, named [fileName] where it says no name, with the themes it includes: [include] gives
         * the text of a file named relative to it, or null where there is none.
         */
        fun read(text: String, fileName: String, include: (String) -> String? = { null }): VsCodeTheme {
            val raw = readJson(text) as? Map<*, *> ?: throw IllegalArgumentException("$fileName is not a VS Code theme")
            var colors = LinkedHashMap<String, String>()
            var tokenColors = ArrayList<Map<String, Any?>>()
            (raw["include"] as? String)?.let { path ->
                val name = path.substringAfterLast('/')
                include(name)?.let { inner -> read(inner, name, include) }?.let { base ->
                    colors = LinkedHashMap(base.colors)
                    tokenColors = ArrayList(base.tokenColors)
                }
            }
            (raw["colors"] as? Map<*, *>)?.forEach { (key, value) -> if (value is String) colors[key.toString()] = value }
            when (val tokens = raw["tokenColors"]) {
                is List<*> -> tokenColors.addAll(tokens.filterIsInstance<Map<*, *>>().map { rule -> rule.entries.associate { it.key.toString() to it.value } })
                // A theme's rules in a TextMate theme of their own, as `*.tmTheme`, are not read: a plist is not JSON.
                else -> Unit
            }
            return VsCodeTheme(raw["name"] as? String ?: fileName.substringBeforeLast('.'), raw["type"] as? String ?: "dark", colors, tokenColors)
        }
    }
}

/**
 * A VS Code settings.json, or a fragment of one (its properties with no braces round them, as people paste them):
 * what of it bears on how code looks.
 */
class VsCodeSettings(val values: Map<String, Any?>) {
    /** [key]'s settings as VS Code applies them under [theme]: the general ones, then those for that theme alone, if any. */
    private fun sections(key: String, theme: String): List<Map<String, Any?>> {
        val all = (values[key] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: return emptyList()
        return listOfNotNull(all, themeSpecific(all, theme))
    }

    fun tokenColorCustomizations(theme: String) = sections("editor.tokenColorCustomizations", theme)

    /** The colours customised under [theme], those for it alone over the general ones. */
    fun colorCustomizations(theme: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (section in sections("workbench.colorCustomizations", theme)) for ((key, value) in section) if (value is String && !isThemeScope(key)) out[key] = value
        return out
    }

    /** The colour theme it chooses, or null for VS Code's default. */
    val colorTheme: String? get() = values["workbench.colorTheme"] as? String

    val fontFamily: String? get() = values["editor.fontFamily"] as? String
    val fontSize: Double? get() = (values["editor.fontSize"] as? Number)?.toDouble()
    val lineHeight: Double? get() = (values["editor.lineHeight"] as? Number)?.toDouble()
    val fontLigatures: Boolean? get() = when (val v = values["editor.fontLigatures"]) {
        is Boolean -> v
        is String -> true
        else -> null
    }

    companion object {
        private val THEME_SCOPE = Regex("""\[(.+?)\]""")

        /** Whether [key] names themes, as `"[Dark 2026]"` or `"[Dark*][*Light]"` do. */
        fun isThemeScope(key: String): Boolean = key.length >= 2 && key.first() == '[' && key.last() == ']'

        /** Whether [pattern], one name of a theme section, names [theme]: exactly, or with `*` at either end or both. */
        fun themeScopeMatches(pattern: String, theme: String): Boolean {
            val starts = pattern.first() == '*'
            val ends = pattern.last() == '*'
            return pattern == theme ||
                (starts && ends && theme.contains(pattern.substring(1, maxOf(1, pattern.length - 1)))) ||
                (ends && theme.startsWith(pattern.dropLast(1))) ||
                (starts && theme.endsWith(pattern.drop(1)))
        }

        /**
         * Every section of [all] for [theme] gathered as VS Code gathers them (`getThemeSpecificColors`): lists joined,
         * other values the later's where it has one; null where no section is for it.
         */
        fun themeSpecific(all: Map<String, Any?>, theme: String): Map<String, Any?>? {
            var out: LinkedHashMap<String, Any?>? = null
            for ((key, value) in all) {
                if (!isThemeScope(key) || value !is Map<*, *>) continue
                for (match in THEME_SCOPE.findAll(key)) {
                    if (!themeScopeMatches(match.groupValues[1], theme)) continue
                    val into = out ?: LinkedHashMap<String, Any?>().also { out = it }
                    for ((name, setting) in value) {
                        val before = into[name.toString()]
                        if (before is List<*> && setting is List<*>) into[name.toString()] = before + setting else if (truthy(setting)) into[name.toString()] = setting
                    }
                }
            }
            return out
        }

        /** Whether [value] counts as given, as JavaScript tests it: not null, false, nought or an empty string. */
        fun truthy(value: Any?): Boolean = when (value) {
            null, false, "" -> false
            is Number -> value.toDouble() != 0.0 && !value.toDouble().isNaN()
            else -> true
        }

        /** Settings from [text]: a whole settings.json or a fragment of its properties. */
        fun parse(text: String): VsCodeSettings {
            val body = text.trim().trimStart('﻿')
            val values = readJson(if (body.startsWith("{")) body else "{\n$body\n}") as? Map<*, *> ?: emptyMap<String, Any?>()
            return VsCodeSettings(values.entries.associate { it.key.toString() to it.value })
        }
    }
}

/**
 * VS Code colour themes as code themes: code coloured by the theme's rules, through the scopes VS Code's grammars give
 * each kind of text, the editor's colours from the theme's `colors`, and the font from a person's settings.
 */
object VsCodeThemes {
    /** The theme VS Code uses when settings name none: "Dark 2026" since VS Code 1.10x. */
    const val DEFAULT_THEME = "Dark 2026"

    /** [theme] as a code theme, its font [font] or the editor's own. */
    fun theme(theme: VsCodeTheme, font: CodeFont? = null, name: String = theme.name, source: String = "VS Code theme ${theme.name}"): CodeTheme {
        fun colour(key: String): Color? = colorOf(theme.color(key))
        val background = colour("editor.background") ?: Color(0xFF1E1E1E)
        val foreground = colour("editor.foreground") ?: Color(0xFFBBBBBB)
        val selection = colour("editor.selectionBackground") ?: Color(0xFF264F78)
        val palette = EditorPalette(
            background = background,
            foreground = foreground,
            caret = colour("editorCursor.foreground") ?: foreground,
            caretRow = colour("editor.lineHighlightBackground") ?: Color.Transparent,
            selection = selection,
            lineNumber = colour("editorLineNumber.foreground") ?: Color(0xFF858585),
            lineNumberCaret = colour("editorLineNumber.activeForeground") ?: if (theme.dark) Color(0xFFC6C6C6) else Color(0xFF0B216F),
            gutter = colour("editorGutter.background") ?: background,
            matchedBracket = colour("editorBracketMatch.background") ?: Color(0x1A006400),
            error = colour("editorError.foreground") ?: Color(0xFFF14C4C),
            warning = colour("editorWarning.foreground") ?: Color(0xFFCCA700),
            widget = colour("editorSuggestWidget.background") ?: background,
            widgetSelection = colour("editorSuggestWidget.selectedBackground") ?: selection,
            widgetForeground = colour("editorSuggestWidget.foreground") ?: foreground,
        )
        return CodeTheme(name, theme.dark, palette, emptyMap(), font = font, source = source, textMate = theme.textMateTheme())
    }

    /**
     * The theme [settingsText] (a settings.json, or a fragment of its properties) makes: the theme it names among
     * [named] or VS Code's own, else VS Code's default, its customisations over it, its font as the settings give it.
     */
    suspend fun fromSettings(settingsText: String, name: String, source: String, named: suspend (String) -> VsCodeTheme? = { null }): CodeTheme {
        val settings = VsCodeSettings.parse(settingsText)
        val base = settings.colorTheme ?: DEFAULT_THEME
        val theme = (named(base) ?: bundled(base) ?: checkNotNull(bundled(DEFAULT_THEME)) { "VS Code's default theme is bundled" }).customized(settings)
        return theme(theme, fontOf(settings), name, source)
    }

    /** VS Code's font for [settings]: its family list, size and line height, VS Code's defaults where it says none. */
    fun fontOf(settings: VsCodeSettings): CodeFont {
        val families = (settings.fontFamily ?: DEFAULT_FONT_FAMILY).split(',').map { it.trim().trim('\'', '"') }.filter { it.isNotEmpty() && it != "monospace" }
        val size = settings.fontSize?.toFloat() ?: DEFAULT_FONT_SIZE
        // As VS Code works it out: none for its golden ratio, a ratio below 8, else pixels; rounded.
        val given = settings.lineHeight?.toFloat() ?: 0f
        val height = when {
            given == 0f -> GOLDEN_LINE_HEIGHT_RATIO * size
            given < MAX_RATIO -> given * size
            else -> given
        }
        return CodeFont(families, size, kotlin.math.round(height), lineHeightPixels = true, ligatures = settings.fontLigatures ?: false)
    }

    private const val DEFAULT_FONT_FAMILY = "Consolas, 'Courier New', monospace"
    private const val DEFAULT_FONT_SIZE = 14f
    private const val GOLDEN_LINE_HEIGHT_RATIO = 1.35f
    private const val MAX_RATIO = 8f

    // VS Code's default themes as bundled, which include each other by name.
    private val DEFAULT_FILES = listOf(
        "2026-dark.json", "2026-light.json", "dark_modern.json", "dark_plus.json", "dark_vs.json",
        "light_modern.json", "light_plus.json", "light_vs.json", "hc_black.json", "hc_light.json",
    )

    /** One of VS Code's default themes as bundled, by its name or label ("Dark 2026", "Dark Modern"). */
    suspend fun bundled(name: String): VsCodeTheme? {
        val manifest = readJson(bundledText("package.json")) as? Map<*, *> ?: return null
        val nls = readJson(bundledText("package.nls.json")) as? Map<*, *>
        val entry = themeEntries(manifest, nls).firstOrNull { it.matches(name) } ?: return null
        val files = DEFAULT_FILES.associateWith { bundledText(it) }
        val file = entry.path.substringAfterLast('/')
        val text = files[file] ?: return null
        return VsCodeTheme.read(text, file) { files[it] }.asContributed(entry)
    }

    /**
     * One Dark Pro (Binaryify, MIT), bundled: the most installed open-source dark theme. [nightFlat] gives its Night
     * Flat variant (background #16191d).
     */
    suspend fun oneDarkPro(nightFlat: Boolean = false): VsCodeTheme {
        val file = if (nightFlat) "OneDark-Pro-night-flat.json" else "OneDark-Pro.json"
        return VsCodeTheme.read(bundledText(file), file)
    }

    /** A theme an extension's manifest brings: its id, label, VS Code's type for it, and its file. */
    class ThemeEntry(val id: String?, val label: String, val uiTheme: String, val path: String) {
        fun matches(name: String) = name.equals(label, ignoreCase = true) || name.equals(id, ignoreCase = true)
    }

    /** The themes an extension's [manifest] brings, their `%key%` labels read from its [nls] strings. */
    fun themeEntries(manifest: Map<*, *>, nls: Map<*, *>?): List<ThemeEntry> {
        val themes = ((manifest["contributes"] as? Map<*, *>)?.get("themes") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        return themes.mapNotNull { theme ->
            val path = theme["path"] as? String ?: return@mapNotNull null
            val raw = theme["label"] as? String ?: return@mapNotNull null
            val label = if (raw.startsWith('%') && raw.endsWith('%')) nls?.get(raw.trim('%')) as? String ?: raw else raw
            ThemeEntry(theme["id"] as? String, label, theme["uiTheme"] as? String ?: "vs-dark", path)
        }
    }

    /** The theme as the extension that brings it says it: VS Code's type for its `uiTheme`, and what settings call it. */
    fun VsCodeTheme.asContributed(entry: ThemeEntry): VsCodeTheme {
        val type = when (entry.uiTheme) {
            "vs" -> "light"
            "hc-black" -> "hcDark"
            "hc-light" -> "hcLight"
            else -> "dark"
        }
        return VsCodeTheme(name, type, colors, tokenColors, settingsId = entry.id ?: entry.label)
    }
}
