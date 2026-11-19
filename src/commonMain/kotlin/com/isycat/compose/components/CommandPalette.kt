package com.isycat.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * One thing a [CommandPalette] finds, and what choosing it does. [detail] says where it lives (a command's menu, an
 * item's folder), [kind] what it is ("Command"), [verb] what Enter does to it ("runs", "opens", "selects"). [keywords]
 * are other names it is found by, as other apps call it ("push pull" for a face move): a query typed whole within one
 * finds it, ranked below items whose own title matches as well.
 */
class PaletteItem(
    val title: String,
    val detail: String = "",
    val kind: String = "Command",
    val icon: ImageVector? = null,
    val shortcut: String? = null,
    val enabled: Boolean = true,
    val verb: String = "runs",
    val keywords: List<String> = emptyList(),
    val run: () -> Unit,
)

/**
 * How well [query] matches [item]: by its title as [matchScore] says, or, where a keyword holds the query whole, by that
 * keyword, a little below a title matching as well; null when neither does.
 */
fun itemScore(query: String, item: PaletteItem): Int? {
    val q = query.trim().lowercase()
    val byKeyword = item.keywords.filter { q in it.lowercase() }.mapNotNull { matchScore(q, it) }.maxOrNull()?.minus(KEYWORD)
    return listOfNotNull(matchScore(query, item.title), byKeyword).maxOrNull()
}

/**
 * How well [query] matches [text], higher better, or null when it does not. Every letter of the query must appear in
 * the text in order; one unbroken run scores best, at the start of the text or of a word better still, and shorter
 * texts rank above longer ones.
 */
fun matchScore(query: String, text: String): Int? {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return 0
    val t = text.lowercase()
    fun wordStart(i: Int) = i == 0 || !t[i - 1].isLetterOrDigit()
    val run = t.indexOf(q)
    if (run >= 0) return RUN + (if (run == 0) START else if (wordStart(run)) WORD else 0) - run - t.length
    var score = 0
    var from = 0
    var previous = -2
    for (c in q) {
        if (c.isWhitespace()) continue
        val at = t.indexOf(c, from)
        if (at < 0) return null
        score += when {
            at == previous + 1 -> CONSECUTIVE
            wordStart(at) -> WORD_LETTER
            else -> 1
        }
        previous = at
        from = at + 1
    }
    return score - t.length
}

/**
 * Where in [text] the letters of [query] were found, as [matchScore] finds them, so a result can show why it matched:
 * the unbroken run when there is one, otherwise each letter where it first follows the one before.
 */
fun matchPositions(query: String, text: String): List<Int> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    val t = text.lowercase()
    val run = t.indexOf(q)
    if (run >= 0) return (run until run + q.length).filter { !t[it].isWhitespace() }
    val found = ArrayList<Int>()
    var from = 0
    for (c in q) {
        if (c.isWhitespace()) continue
        val at = t.indexOf(c, from)
        if (at < 0) return emptyList()
        found += at
        from = at + 1
    }
    return found
}

/** The [items] matching [query], best first, in their own order when they match equally well; all of them for no query. */
fun paletteSearch(items: List<PaletteItem>, query: String): List<PaletteItem> {
    if (query.isBlank()) return items
    return items.withIndex()
        .mapNotNull { (index, item) -> itemScore(query, item)?.let { Triple(item, it, index) } }
        .sortedWith(compareByDescending<Triple<PaletteItem, Int, Int>> { it.second }.thenBy { it.third })
        .map { it.first }
}

/**
 * A [CommandPalette]'s query and the result chosen among what it finds: Up and Down move the choice, Enter runs it. With
 * nothing typed, the [quick] items stand first, numbered.
 */
class CommandPaletteState(private val items: List<PaletteItem>, initialQuery: String = "", val quick: List<PaletteItem> = emptyList()) {
    var query by mutableStateOf(initialQuery)
        private set

    var chosen by mutableIntStateOf(0)
        private set

    val results: List<PaletteItem> by derivedStateOf { if (query.isBlank()) quick + items else paletteSearch(items, query) }

    /** The number the result at [index] is run by, 1 to 9 then 0, while nothing is typed and it is a quick one; else null. */
    fun numberOf(index: Int): Int? = if (query.isBlank() && index < minOf(quick.size, QUICK_KEYS)) (index + 1) % QUICK_KEYS else null

    /** The quick item number [digit] runs, 1 to 9 then 0, while nothing is typed; null where there is none. */
    fun quickAt(digit: Int): PaletteItem? = if (query.isBlank()) quick.getOrNull((digit + QUICK_KEYS - 1) % QUICK_KEYS) else null

    val choice: PaletteItem? get() = results.getOrNull(chosen)

    /** Searches for [text] instead, choosing its best match. */
    fun type(text: String) {
        query = text
        chosen = 0
    }

    /** Chooses the result at [index], as pointing at it does. */
    fun choose(index: Int) {
        if (index in results.indices) chosen = index
    }

    /** Moves the choice [steps] results down, or up when negative, round from one end to the other. */
    fun move(steps: Int) {
        if (results.isNotEmpty()) chosen = ((chosen + steps) % results.size + results.size) % results.size
    }
}

/** What tells the palette's field apart, for tests to find it. */
const val COMMAND_PALETTE_FIELD = "command-palette-field"

/**
 * A command palette over the top of the window, as VS Code's and moonshine's quick search: a field that has the focus
 * the moment it opens, what it finds as the person types (the matched letters in the accent, each row's icon, title,
 * where it lives, its keys and what it is), and what the keys do. Up and Down choose, Enter runs the choice, Escape or
 * a click anywhere else closes it. [hint] is said under the results while nothing is typed.
 */
@Composable
fun CommandPalette(
    items: List<PaletteItem>,
    onDismiss: () -> Unit,
    placeholder: String = "Search commands",
    hint: String? = null,
    width: Dp = 600.dp,
    top: Dp = 80.dp,
    mostRows: Int = 14,
    quick: List<PaletteItem> = emptyList(),
) {
    val state = remember(items, quick) { CommandPaletteState(items, quick = quick) }
    val dismiss by rememberUpdatedState(onDismiss)
    val results = state.results
    val list = rememberLazyListState()
    val focus = remember { FocusRequester() }
    fun run(item: PaletteItem) {
        if (!item.enabled) return
        dismiss()
        item.run()
    }
    LaunchedEffect(state.chosen, results) {
        // Keep the chosen row in view as the keys move through the list, in a frame of its own.
        inFrameOfItsOwn()
        val visible = list.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return@LaunchedEffect
        val chosen = state.chosen
        if (chosen < visible.first().index) list.scrollToItem(chosen)
        else if (chosen > visible.last().index) list.scrollToItem(maxOf(0, chosen - visible.size + 2))
    }
    val offset = with(LocalDensity.current) { IntOffset(0, top.roundToPx()) }
    Popup(
        alignment = Alignment.TopCenter,
        offset = offset,
        onDismissRequest = { dismiss() },
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        val colors = MaterialTheme.colorScheme
        Surface(
            modifier = Modifier.width(width).onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Escape -> true.also { dismiss() }
                    Key.DirectionDown -> true.also { state.move(1) }
                    Key.DirectionUp -> true.also { state.move(-1) }
                    Key.Enter, Key.NumPadEnter -> true.also { state.choice?.let(::run) }
                    // While nothing is typed, a number runs the quick item it numbers, as JetBrains' Actions do.
                    else -> digitOf(event.key)?.let(state::quickAt)?.let { true.also { _ -> run(it) } } ?: false
                }
            }.outerShadow(12.dp, MaterialTheme.shapes.medium),
            shape = MaterialTheme.shapes.medium,
            color = colors.surfaceContainerHigh,
            border = BorderStroke(1.dp, colors.outlineVariant),
        ) {
            Column {
                SearchField(
                    state.query,
                    state::type,
                    Modifier.padding(FIELD_MARGIN).testTag(COMMAND_PALETTE_FIELD),
                    placeholder = placeholder,
                    focusRequester = focus,
                    glowColor = colors.primary,
                )
                LaunchedEffect(Unit) { focus.requestFocus() }
                HorizontalDivider(color = colors.outlineVariant)
                Box(Modifier.fillMaxWidth().heightIn(max = ROW * mostRows + LIST_PADDING * 2)) {
                    if (results.isEmpty()) {
                        Text(
                            "Nothing matches “${state.query.trim()}”",
                            Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().padding(vertical = LIST_PADDING), state = list) {
                            itemsIndexed(results) { index, item ->
                                // The quick items are set off from the rest by a line under the last of them.
                                val last = state.query.isBlank() && index == state.quick.lastIndex && index < results.lastIndex
                                Column {
                                    PaletteRow(item, state.query, index == state.chosen, state.numberOf(index), onPoint = { state.choose(index) }) {
                                        run(item)
                                    }
                                    if (last) HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 2.dp), color = colors.outlineVariant)
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = colors.outlineVariant)
                Text(
                    keysHint(state.choice?.verb ?: "runs", hint.takeIf { state.query.isBlank() }),
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    inlineContent = arrows(colors.onSurfaceVariant),
                )
            }
        }
    }
}

/**
 * What the keys do, said under the results, after [hint] when there is one: the arrows are drawn as icons, since the
 * bundled Roboto has no arrow glyphs and a browser has no other font to borrow them from.
 */
internal fun keysHint(verb: String, hint: String?): AnnotatedString = buildAnnotatedString {
    if (hint != null) append("$hint · ")
    appendInlineContent(UP_ARROW, "Up")
    append(" ")
    appendInlineContent(DOWN_ARROW, "Down")
    append(" choose · Enter $verb · Esc closes")
}

private fun arrows(tint: Color): Map<String, InlineTextContent> = mapOf(
    UP_ARROW to Icons.Filled.ArrowUpward,
    DOWN_ARROW to Icons.Filled.ArrowDownward,
).mapValues { (_, icon) ->
    InlineTextContent(Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)) { Icon(icon, null, tint = tint) }
}

private const val UP_ARROW = "up"
private const val DOWN_ARROW = "down"

@Composable
private fun PaletteRow(item: PaletteItem, query: String, chosen: Boolean, number: Int?, onPoint: () -> Unit, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) onPoint() }
    Row(
        Modifier.fillMaxWidth().height(ROW)
            .background(if (chosen) colors.secondaryContainer else Color.Transparent)
            .hoverable(hover)
            .clickable(hover, indication = null, enabled = item.enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A quick item's number, underlined, as the key that runs it.
        Box(Modifier.width(20.dp)) {
            if (number != null) {
                Text(
                    "$number",
                    Modifier.testTag("$QUICK_NUMBER:$number"),
                    style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Box(Modifier.width(28.dp)) {
            if (item.icon != null) {
                val tint = when {
                    !item.enabled -> colors.onSurface.copy(alpha = DISABLED)
                    chosen -> colors.primary
                    else -> colors.onSurfaceVariant
                }
                Icon(item.icon, null, Modifier.width(18.dp), tint = tint)
            }
        }
        // The letters the search matched stand out, so it is plain why each result is there.
        val matched = matchPositions(query, item.title)
        val title = buildAnnotatedString {
            append(item.title)
            for (at in matched) addStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.SemiBold), at, at + 1)
        }
        val text = if (item.enabled) colors.onSurface else colors.onSurface.copy(alpha = DISABLED)
        Text(title, style = MaterialTheme.typography.bodyMedium, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(10.dp))
        val quiet = colors.onSurfaceVariant
        Text(item.detail, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = quiet, maxLines = 1)
        if (item.shortcut != null) {
            Spacer(Modifier.width(10.dp))
            Text(item.shortcut, style = MaterialTheme.typography.bodySmall, color = quiet)
        }
        Spacer(Modifier.width(16.dp))
        Text(item.kind, Modifier.width(76.dp), style = MaterialTheme.typography.bodySmall, color = quiet, maxLines = 1)
    }
}

/** The digit [key] is, on the main keys or the number pad; null where it is none. */
private fun digitOf(key: Key): Int? = DIGITS.indexOfFirst { key in it }.takeIf { it >= 0 }

private val DIGITS = listOf(
    setOf(Key.Zero, Key.NumPad0),
    setOf(Key.One, Key.NumPad1),
    setOf(Key.Two, Key.NumPad2),
    setOf(Key.Three, Key.NumPad3),
    setOf(Key.Four, Key.NumPad4),
    setOf(Key.Five, Key.NumPad5),
    setOf(Key.Six, Key.NumPad6),
    setOf(Key.Seven, Key.NumPad7),
    setOf(Key.Eight, Key.NumPad8),
    setOf(Key.Nine, Key.NumPad9),
)

/** Marks a quick item's number in the palette: this, a colon and the number. */
const val QUICK_NUMBER = "palette-quick-number"

// How many quick items have keys: 1 to 9, then 0.
private const val QUICK_KEYS = 10

private val ROW = 36.dp
private val LIST_PADDING = 4.dp
private val FIELD_MARGIN = 10.dp
private const val DISABLED = 0.38f
private const val RUN = 1000
private const val START = 400
private const val WORD = 200

// How far a match by a keyword ranks below the same match by a title.
private const val KEYWORD = 100
private const val CONSECUTIVE = 8
private const val WORD_LETTER = 6
