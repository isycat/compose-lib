package com.isycat.compose.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.isycat.compose.components.HoverTooltip
import com.isycat.compose.resources.Res
import com.isycat.compose.resources.roboto_bold
import com.isycat.compose.resources.roboto_italic
import com.isycat.compose.resources.roboto_medium
import com.isycat.compose.resources.roboto_regular
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.getFontResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import kotlin.math.abs

/**
 * One document's tab in a [TabbedTitleBar]: its [title], an [icon] before it (drawn about 14dp, in the tab's icon
 * colour as `LocalContentColor`) and a [tooltip], such as the file's path. An unsaved one ([dirty]) wears a dot at its
 * end until the pointer is on it. [onClose] gives it a close button there, shown while the tab is in front or the
 * pointer is on it, and a middle-click closes it too; neither when null. [key] keeps each tab's hover with it as tabs
 * come and go.
 */
class TitleBarTab(
    val title: String,
    val icon: (@Composable () -> Unit)? = null,
    val tooltip: String? = null,
    val dirty: Boolean = false,
    val onClose: (() -> Unit)? = null,
    val key: Any = title,
)

/**
 * The search box of a [TabbedTitleBar], before its undo and redo: a button that opens the app's search, saying how
 * to reach it from anywhere ([shortcut], like "Ctrl+Space"). While that search is open over the window, [hidden]
 * keeps the box's room without drawing it, so nothing in the bar moves.
 */
class TitleBarSearch(
    val onClick: () -> Unit,
    val placeholder: String = "Search",
    val shortcut: String? = null,
    val tooltip: String? = null,
    val hidden: Boolean = false,
    val width: Dp = 250.dp,
)

/** The undo and redo buttons of a [TabbedTitleBar]; their tooltips can name the step and the key, "Undo (Ctrl+Z)". */
class TitleBarUndoRedo(
    val canUndo: Boolean,
    val canRedo: Boolean,
    val onUndo: () -> Unit,
    val onRedo: () -> Unit,
    val undoTooltip: String = "Undo",
    val redoTooltip: String = "Redo",
)

/**
 * The window a [TabbedTitleBar] is the title bar of. Given one, the bar shows the app's icon and title first and the
 * window's buttons last (minimise, maximise or restore, close); a press on its empty part moves the window and a
 * double-click maximises it or puts it back. On the desktop, `rememberTitleBarWindow()` in a window's scope gives one
 * where the system's own title bar can come off (Windows), and null elsewhere, where the bar sits under the system's.
 */
interface TitleBarWindow {
    /** Read as Compose state, so the maximise button follows the window. */
    val isMaximized: Boolean

    fun minimize()

    fun toggleMaximized()

    /** What the window's own close button would do: usually ask the app, which may ask to save first. */
    fun close()

    /**
     * Moves the window from the pointer, as a press on a system title bar does, snapping to the screen's edges. Only
     * called when the window doesn't [handlesCaption].
     */
    fun startMove() = Unit

    /**
     * True when the system moves the window from the bar, maximises it on a double-click and shows its menu, as for its
     * own caption. The bar then reports where it is ([captionAt]) and where its tabs and buttons are ([clickableAt]), and
     * doesn't move the window itself.
     */
    val handlesCaption: Boolean get() = false

    /** Where the bar is, in the window's pixels; null when it's gone. */
    fun captionAt(area: Rect?) = Unit

    /** Where one of the bar's tabs or buttons, [key], is in the window's pixels; null when it's gone. */
    fun clickableAt(key: Any, area: Rect?) = Unit

    /**
     * Drawn over the bar's top, where the window's own frame has no edge left to resize it from: a strip that resizes
     * the window upwards. None by default.
     */
    @Composable
    fun TopResizeEdge() {
    }
}

/** How a [TabbedTitleBar] draws the window's minimise, maximise and close buttons. */
enum class TitleBarWindowButtons(
    /** How wide each button is; all fill the bar's height. */
    val width: Dp,
) {
    /** Close together, with line glyphs as large as the bar's other icons. */
    Compact(34.dp),

    /** As Windows draws its own: as far apart, with its small hairline glyphs. */
    Windows(46.dp),
}

/** The colours of a [TabbedTitleBar]. */
@Immutable
data class TitleBarColors(
    /** The bar. */
    val bar: Color,
    /** The app's title. */
    val title: Color,
    /** The text of a tab not in front. */
    val tab: Color,
    /** The icon of a tab not in front: quieter than its text. */
    val tabIcon: Color,
    /** The tab in front: the colour of what lies under the bar, so the two read as one. */
    val selectedTab: Color,
    /** The text and icon of the tab in front. */
    val selectedTabContent: Color,
    /** Under a tab or button the pointer is on. */
    val hover: Color,
    /** Under a button that is on, as the hamburger while its menu is open. */
    val selected: Color,
    /** The mark on the tab in front, a button that is on, and the app's icon when it takes a tint. */
    val accent: Color,
    /** The buttons' glyphs: the menu, undo and redo, the window's buttons. */
    val icon: Color,
    /** A button's glyph under the pointer. */
    val iconHover: Color,
    /** A button's glyph while it can't be used. */
    val iconDisabled: Color,
    /** An unsaved tab's dot. */
    val dirty: Color,
    /** The search box, its border and its text. */
    val field: Color,
    val fieldHover: Color,
    val fieldBorder: Color,
    val fieldText: Color,
    /** Under the window's close button while the pointer is on it; its glyph turns white. */
    val close: Color,
    /** The default tooltip: its fill, border and text. */
    val tooltip: Color,
    val tooltipBorder: Color,
    val tooltipText: Color,
)

/** The text of a [TabbedTitleBar]: the tabs', and the smaller title's and search box's. */
@Immutable
data class TitleBarTypography(val tab: TextStyle, val small: TextStyle)

object TitleBarDefaults {
    /** How tall the bar is. */
    val Height: Dp = 40.dp

    /** How wide the bar must be to show the app's title; narrower, it shows only the icon. */
    val AppTitleMinBarWidth: Dp = 600.dp

    /** The red Windows gives a close button under the pointer. */
    val CloseRed: Color = Color(0xFFC42B1C)

    /**
     * Calm neutral greys, so the bar stays quiet whatever the app's colours: only the tab in front takes the colour of
     * what lies under the bar ([selectedTab], the theme's background), and the [accent] marks it.
     */
    @Composable
    fun colors(
        selectedTab: Color = MaterialTheme.colorScheme.background,
        accent: Color = MaterialTheme.colorScheme.primary,
        bar: Color = Color(0xFF1F2026),
        text: Color = Color(0xFFE4E6EC),
        textSecondary: Color = Color(0xFFA3A7B3),
        textMuted: Color = Color(0xFF8B8F9C),
        hover: Color = Color(0xFF2A2C35),
    ): TitleBarColors =
        TitleBarColors(
            bar = bar,
            title = textSecondary,
            tab = textSecondary,
            tabIcon = textMuted,
            selectedTab = selectedTab,
            selectedTabContent = text,
            hover = hover,
            selected = accent.copy(alpha = SELECTED_ALPHA),
            accent = accent,
            icon = textSecondary,
            iconHover = text,
            iconDisabled = Color(0xFF5A5E6A),
            dirty = textSecondary,
            field = Color(0xFF121317),
            fieldHover = Color(0xFF181A1F),
            fieldBorder = Color(0xFF2C2E37),
            fieldText = textMuted,
            close = CloseRed,
            tooltip = Color(0xFF22242B),
            tooltipBorder = Color(0xFF3A3D48),
            tooltipText = text,
        )

    /**
     * The bar's own font, Roboto, bundled with it rather than the system's, so the bar looks the same on every
     * platform, the desktop's and the browser's alike: regular, medium, bold and italic (Roboto 2.135, Apache 2.0).
     */
    @Composable
    fun fontFamily(): FontFamily =
        FontFamily(
            Font(Res.font.roboto_regular, FontWeight.Normal),
            Font(Res.font.roboto_medium, FontWeight.Medium),
            Font(Res.font.roboto_bold, FontWeight.Bold),
            Font(Res.font.roboto_italic, FontWeight.Normal, FontStyle.Italic),
        )

    /** The file of the bar's regular Roboto, for code that needs the font's bytes rather than a [FontFamily]. */
    suspend fun fontBytes(): ByteArray = getFontResourceBytes(getSystemResourceEnvironment(), Res.font.roboto_regular)

    /** The bar's text, small: 11sp for the tabs, the title and the search box alike. */
    @Composable
    fun typography(family: FontFamily = fontFamily()): TitleBarTypography {
        val small = TextStyle(fontFamily = family, fontSize = 11.sp, lineHeight = 14.sp)
        return TitleBarTypography(tab = small, small = small)
    }

    /** A small dark tooltip, as the bar's buttons and tabs show by default. */
    @Composable
    fun Tooltip(text: String, colors: TitleBarColors = colors(), typography: TitleBarTypography = typography()) {
        val shape = RoundedCornerShape(4.dp)
        Box(
            Modifier.shadow(6.dp, shape).background(colors.tooltip, shape).border(1.dp, colors.tooltipBorder, shape)
                .padding(horizontal = 8.dp, vertical = 5.dp),
        ) { Label(text, typography.small, colors.tooltipText) }
    }

    /**
     * The title the bar shows for [title]: just it, or, when it [followsTab], the tab in front's title with an unsaved
     * mark before it, "Tree.msplant • — moonshine plants". A window's own title can use it too, for the taskbar.
     */
    fun title(title: String, tab: TitleBarTab?, followsTab: Boolean): String =
        if (!followsTab || tab == null) title else "${tab.title}${if (tab.dirty) " •" else ""} — $title"
}

/**
 * A window's title bar with its documents as tabs, as code editors draw theirs: the app's [appIcon] and [title], an
 * optional [menu] behind a hamburger, the [tabs], then the app's own [actions], an optional [search] box and
 * [undoRedo] buttons, and the window's buttons, [windowButtons] compact or as Windows draws its own.
 *
 * The window's buttons only show when the bar stands in for the window's title bar, given its [window]. The icon and
 * the title show with them, or without a window when [showAppTitle] says so (a browser, which has no title bar of its
 * own in the page); without either (a window keeping the system's title bar) the bar is a plain tab bar.
 * [titleFollowsTab] makes the title start with the tab in front's, as `TitleBarDefaults.title` spells it; off, it's
 * always [title].
 *
 * [menu] draws the menu itself, anchored to the hamburger, given whether it's open and how to close it, so a
 * `DropdownMenu(expanded, onDismissRequest) { … }` fits it as it is.
 *
 * [onNewTab] puts a + after the tabs, as browsers have, named by [newTabDescription]. With [onMoveTab], a tab can be dragged
 * along the row: it follows the pointer, and each time it passes half of a neighbour, [onMoveTab] is called with its index
 * and the neighbour's, for the caller to swap them. A tab's title is at most
 * [tabTitleMaxWidth] wide, ending in an ellipsis past that; its tooltip can give the whole of it.
 *
 * When the tabs don't fit, the widest shrink first, their titles ending in an ellipsis, down to [tabMinWidth]; past that
 * the tabs scroll, keeping the one in front in view. The + stays visible after them. Below [appTitleMinBarWidth] the bar
 * leaves out the app's title (not its icon), to give the tabs its room.
 */
@Composable
fun TabbedTitleBar(
    tabs: List<TitleBarTab>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    titleFollowsTab: Boolean = false,
    appIcon: (@Composable () -> Unit)? = null,
    window: TitleBarWindow? = null,
    showAppTitle: Boolean = window != null,
    windowButtons: TitleBarWindowButtons = TitleBarWindowButtons.Compact,
    menu: (@Composable (expanded: Boolean, onDismissRequest: () -> Unit) -> Unit)? = null,
    menuDescription: String = "Menu",
    search: TitleBarSearch? = null,
    undoRedo: TitleBarUndoRedo? = null,
    onNewTab: (() -> Unit)? = null,
    onMoveTab: ((from: Int, to: Int) -> Unit)? = null,
    newTabDescription: String = "New tab",
    tabTitleMaxWidth: Dp = Dp.Infinity,
    tabMinWidth: Dp = MinTabWidth,
    appTitleMinBarWidth: Dp = TitleBarDefaults.AppTitleMinBarWidth,
    actions: @Composable RowScope.() -> Unit = {},
    colors: TitleBarColors = TitleBarDefaults.colors(),
    typography: TitleBarTypography = TitleBarDefaults.typography(),
    tooltip: @Composable (String) -> Unit = { TitleBarDefaults.Tooltip(it, colors, typography) },
) {
    val bar = Bar(colors, typography, tooltip, tabTitleMaxWidth, window)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(TitleBarDefaults.Height)
            .background(colors.bar)
            .then(
                when {
                    window == null -> Modifier
                    window.handlesCaption -> Modifier.reportsCaption(window)
                    else -> Modifier.movesWindow(window)
                },
            ),
    ) {
        val showsTitle = maxWidth >= appTitleMinBarWidth
        Row(
            Modifier.fillMaxSize().padding(start = 4.dp, end = if (window != null) 0.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showAppTitle) {
                Spacer(Modifier.width(6.dp))
                if (appIcon != null) {
                    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                        CompositionLocalProvider(LocalContentColor provides colors.accent) { appIcon() }
                    }
                    Spacer(Modifier.width(8.dp))
                }
                if (showsTitle) {
                    val shown = TitleBarDefaults.title(title, tabs.getOrNull(selectedTabIndex), titleFollowsTab)
                    Label(shown, typography.small, colors.title, Modifier.widthIn(max = 320.dp).testTag(TitleBarTags.TITLE))
                    Spacer(Modifier.width(6.dp))
                }
            }
            if (menu != null) {
                bar.MenuButton(menu, menuDescription)
                Spacer(Modifier.width(6.dp))
            }
            // The tabs, then the new tab button, which is measured first so it always has its room: right after the last
            // tab, or at the end once the tabs fill the row. Where the tabs don't fit at their own widths, the widest are
            // cut to fit, their titles ending in an ellipsis; below [tabMinWidth] they scroll, and the button doesn't.
            Row(Modifier.weight(1f).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                BoxWithConstraints(Modifier.weight(1f, fill = false).fillMaxHeight()) {
                    val room = constraints.maxWidth
                    val scroll = rememberScrollState()
                    // Where the tab in front is in the strip, so that when the tabs scroll it's kept in view.
                    var front by remember { mutableStateOf<IntRange?>(null) }
                    LaunchedEffect(front, room) {
                        val span = front ?: return@LaunchedEffect
                        when {
                            span.first < scroll.value -> scroll.scrollTo(span.first)
                            span.last > scroll.value + room -> scroll.scrollTo(span.last - room)
                        }
                    }
                    // A touch swipes the tabs along (a long press then drags one); a mouse wheel scrolls them too.
                    Box(Modifier.fillMaxHeight().wheelScrollsSideways(scroll).horizontalScroll(scroll)) {
                        val drag = rememberTabDrag(tabs, onMoveTab, onTabSelected)
                        TabStrip(room, tabMinWidth, selectedTabIndex, { front = it }, Modifier.fillMaxHeight().testTag(TitleBarTags.TABS)) {
                            tabs.forEachIndexed { index, tab ->
                                key(tab.key) {
                                    bar.DocumentTab(tab, index == selectedTabIndex, drag?.modifier(tab.key) ?: Modifier) {
                                        onTabSelected(index)
                                    }
                                }
                            }
                        }
                    }
                }
                if (onNewTab != null) {
                    Box(Modifier.fillMaxHeight().padding(start = 4.dp), contentAlignment = Alignment.Center) {
                        bar.Button(TitleBarIcons.Plus, newTabDescription, Modifier.testTag(TitleBarTags.NEW_TAB), onClick = onNewTab)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Row(bar.takesClicks(), verticalAlignment = Alignment.CenterVertically, content = actions)
            if (search != null) {
                bar.SearchBox(search)
                Spacer(Modifier.width(6.dp))
            }
            if (undoRedo != null) {
                bar.Button(TitleBarIcons.Undo, undoRedo.undoTooltip, enabled = undoRedo.canUndo, onClick = undoRedo.onUndo)
                bar.Button(TitleBarIcons.Redo, undoRedo.redoTooltip, enabled = undoRedo.canRedo, onClick = undoRedo.onRedo)
            }
            if (window != null) {
                Spacer(Modifier.width(8.dp))
                bar.WindowButtons(window, windowButtons)
            }
        }
        // Over everything else, so the window's top still resizes it.
        window?.TopResizeEdge()
    }
}

/** Test tags on a [TabbedTitleBar]'s parts. */
object TitleBarTags {
    const val TITLE = "title-bar-title"
    const val MENU = "title-bar-menu"
    const val SEARCH = "title-bar-search"
    const val NEW_TAB = "title-bar-new-tab"
    const val TABS = "title-bar-tabs"
}

/**
 * The tabs in a row, each at its own width while they fit in [room] pixels. When they don't, the widest are cut to one
 * width, so that they fit, but none below [minWidth] (one already narrower keeps its own). Each tab sits at the bottom.
 * [onFront] is told where the tab at [front] is placed, from its start to its end.
 */
@Composable
private fun TabStrip(
    room: Int,
    minWidth: Dp,
    front: Int,
    onFront: (IntRange?) -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val natural = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }
        val widths = tabWidths(natural, room, minWidth.roundToPx())
        val placeables =
            measurables.mapIndexed { index, measurable ->
                measurable.measure(Constraints(minWidth = widths[index], maxWidth = widths[index], maxHeight = constraints.maxHeight))
            }
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else placeables.maxOfOrNull { it.height } ?: 0
        layout(placeables.sumOf { it.width }, height) {
            var x = 0
            var span: IntRange? = null
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative(x, height - placeable.height)
                if (index == front) span = x..(x + placeable.width)
                x += placeable.width
            }
            onFront(span)
        }
    }
}

/**
 * The widths of tabs whose own widths are [natural], in [room]: their own while they fit; otherwise the widest are cut to
 * the one width that makes them fit, though not below [minWidth].
 */
internal fun tabWidths(natural: List<Int>, room: Int, minWidth: Int): List<Int> {
    if (natural.sum() <= room) return natural
    var left = room
    var count = natural.size
    var cap = 0
    for (width in natural.sorted()) {
        val share = left / count
        if (width > share) {
            cap = share
            break
        }
        left -= width
        count--
    }
    // Never wider than the room, so a tab scrolled into view is whole, close button and all.
    val limit = maxOf(cap, minOf(minWidth, room))
    return natural.map { minOf(it, limit) }
}

/**
 * One line of the bar's text, in [style]'s own line height and nothing more, so a row centring it centres the text:
 * Material's text styles add letter spacing and taller lines, which sat the bar's text off its middle.
 */
@Composable
private fun Label(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style.copy(color = color), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * A press on the bar where nothing else takes it moves the window through the system's own move, which snaps to the
 * screen's edges; a second press soon after maximises it or puts it back.
 */
private fun Modifier.movesWindow(window: TitleBarWindow): Modifier =
    pointerInput(window) {
        var last = 0L
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            if (!currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
            if (down.uptimeMillis - last < viewConfiguration.doubleTapTimeoutMillis) {
                last = 0L
                window.toggleMaximized()
            } else {
                last = down.uptimeMillis
                window.startMove()
            }
        }
    }

/**
 * Dragging tabs along the row: the tab being dragged ([dragged]) is drawn [offset] pixels from its place, above the
 * others. Each time it passes half of a neighbour, [onMoveTab] swaps them and the offset is taken back by that width.
 */
private class TabDrag(
    private val tabs: () -> List<TitleBarTab>,
    private val onMoveTab: () -> ((Int, Int) -> Unit)?,
    private val onSelect: () -> (Int) -> Unit,
) {
    var dragged by mutableStateOf<Any?>(null)
    var offset by mutableStateOf(0f)
    private val widths = mutableMapOf<Any, Int>()

    fun modifier(key: Any): Modifier =
        Modifier
            .onSizeChanged { widths[key] = it.width }
            .zIndex(if (dragged == key) 1f else 0f)
            .graphicsLayer { translationX = if (dragged == key) offset else 0f }
            .pointerInput(key) {
                // A mouse drags a tab at once. A touch swipe is left to the strip, which scrolls with it, so a touch
                // drags a tab only after a long press.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val dragging =
                        if (down.type == PointerType.Touch) {
                            awaitLongPressOrCancellation(down.id)?.also { start(key) }
                        } else {
                            awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                                change.consume()
                                start(key)
                                move(key, over)
                            }
                        } ?: return@awaitEachGesture
                    horizontalDrag(dragging.id) { change ->
                        // Read before consuming: a consumed change reports no movement.
                        move(key, change.positionChange().x)
                        change.consume()
                    }
                    stop()
                }
            }

    private fun start(key: Any) {
        dragged = key
        offset = 0f
        tabs().indexOfFirst { it.key == key }.takeIf { it >= 0 }?.let { onSelect()(it) }
    }

    private fun move(key: Any, by: Float) {
        offset += by
        step(key)
    }

    private fun stop() {
        dragged = null
        offset = 0f
    }

    // Swaps the dragged tab with the neighbour it has passed half of, if any.
    private fun step(key: Any) {
        val list = tabs()
        val at = list.indexOfFirst { it.key == key }
        if (at < 0) return
        val next = list.getOrNull(at + 1)?.let { widths[it.key] }
        val previous = list.getOrNull(at - 1)?.let { widths[it.key] }
        when {
            next != null && offset > next / 2f -> {
                onMoveTab()?.invoke(at, at + 1)
                offset -= next
            }
            previous != null && offset < -previous / 2f -> {
                onMoveTab()?.invoke(at, at - 1)
                offset += previous
            }
        }
    }
}

/** Dragging [tabs] along the row, when there's an [onMoveTab] to move them; null otherwise. */
@Composable
private fun rememberTabDrag(tabs: List<TitleBarTab>, onMoveTab: ((Int, Int) -> Unit)?, onSelect: (Int) -> Unit): TabDrag? {
    val currentTabs by rememberUpdatedState(tabs)
    val move by rememberUpdatedState(onMoveTab)
    val select by rememberUpdatedState(onSelect)
    val drag = remember { TabDrag({ currentTabs }, { move }, { select }) }
    return drag.takeIf { onMoveTab != null }
}

/**
 * A mouse wheel turned over a strip that scrolls sideways scrolls it, as a wheel does over a browser's tabs: a mouse
 * has no sideways wheel, and Compose only scrolls a row with one (or with Shift held). A sideways wheel or touchpad
 * swipe is left to the row's own scrolling.
 */
private fun Modifier.wheelScrollsSideways(scroll: ScrollState): Modifier =
    pointerInput(scroll) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull() ?: continue
                val delta = change.scrollDelta
                if (abs(delta.y) <= abs(delta.x) || scroll.maxValue == 0) continue
                scroll.dispatchRawDelta(delta.y * WHEEL_STEP.toPx())
                change.consume()
            }
        }
    }

// How far one notch of a mouse wheel scrolls the tabs.
private val WHEEL_STEP = 48.dp

/** Reports the bar's area to [window], which moves the window from it. */
private fun Modifier.reportsCaption(window: TitleBarWindow): Modifier =
    composed {
        DisposableEffect(window) { onDispose { window.captionAt(null) } }
        onGloballyPositioned { window.captionAt(it.boundsInWindow()) }
    }

/** Reports this part's area to [window] as one that takes its own clicks, so the window isn't moved from it. */
private fun Modifier.reportsClickable(window: TitleBarWindow): Modifier =
    composed {
        val key = remember { Any() }
        DisposableEffect(window) { onDispose { window.clickableAt(key, null) } }
        onGloballyPositioned { window.clickableAt(key, it.boundsInWindow()) }
    }

/** A middle-click closes the tab, as browsers' and editors' tabs do: on the button's release, while still over it. */
private fun Modifier.closesOnMiddleClick(onClose: () -> Unit): Modifier =
    composed {
        val close by rememberUpdatedState(onClose)
        pointerInput(Unit) {
            awaitEachGesture {
                val press = awaitPointerEvent()
                if (press.type != PointerEventType.Press || !press.buttons.isTertiaryPressed) return@awaitEachGesture
                press.changes.forEach { it.consume() }
                while (true) {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                    if (!event.buttons.isTertiaryPressed) {
                        if (event.changes.none { it.isOutOfBounds(size, extendedTouchPadding) }) close()
                        return@awaitEachGesture
                    }
                }
            }
        }
    }

/** The bar's parts, sharing its colours, text and tooltips. */
private class Bar(
    val colors: TitleBarColors,
    val typography: TitleBarTypography,
    val tooltip: @Composable (String) -> Unit,
    val tabTitleMaxWidth: Dp,
    val window: TitleBarWindow?,
) {
    /** Marks a part that takes its own clicks, for a window that moves from the rest of the bar. */
    fun takesClicks(): Modifier = window?.takeIf { it.handlesCaption }?.let { Modifier.reportsClickable(it) } ?: Modifier

    /** [content], with [text] shown while it's hovered, or just [content] when there's none. */
    @Composable
    fun Tip(text: String?, content: @Composable () -> Unit) {
        if (text == null) content() else HoverTooltip({ tooltip(text) }) { content() }
    }

    /** A small square button with [icon], named by [description] (its tooltip too); [selected] shows it as on. */
    @Composable
    fun Button(
        icon: ImageVector,
        description: String,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        selected: Boolean = false,
        size: Dp = 26.dp,
        iconSize: Dp = 16.dp,
        onClick: () -> Unit,
    ) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val background =
            when {
                selected -> colors.selected
                hovered && enabled -> colors.hover
                else -> Color.Transparent
            }
        val tint =
            when {
                !enabled -> colors.iconDisabled
                selected -> colors.accent
                hovered -> colors.iconHover
                else -> colors.icon
            }
        Tip(description) {
            Box(
                modifier
                    .then(takesClicks())
                    .size(size)
                    .clip(RoundedCornerShape(4.dp))
                    .background(background)
                    .hoverable(interaction)
                    .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                    .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                LineIcon(icon, tint, iconSize)
            }
        }
    }

    /** The hamburger, opening [menu] under it. */
    @Composable
    fun MenuButton(menu: @Composable (Boolean, () -> Unit) -> Unit, description: String) {
        var open by remember { mutableStateOf(false) }
        Box {
            Button(TitleBarIcons.Menu, description, Modifier.testTag(TitleBarTags.MENU), selected = open, size = 30.dp) { open = !open }
            menu(open) { open = false }
        }
    }

    /**
     * A document's tab: the one in front in the colour of what lies below the bar, joined to it, with a mark in the
     * accent; the others lit under the pointer. Its end holds the unsaved dot or the close button, keeping its room
     * either way, as every tab keeps the mark's, so tabs never shift as the pointer moves over them or one is picked.
     */
    @Composable
    fun DocumentTab(tab: TitleBarTab, selected: Boolean, modifier: Modifier = Modifier, onSelect: () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        var hovered by remember { mutableStateOf(false) }
        Tip(tab.tooltip) {
            Row(
                // Before [modifier], so outside a drag's offset: measured against the tab's place, where it settles.
                Modifier.tracksPointerOver { hovered = it }
                    .then(modifier)
                    .then(takesClicks())
                    .height(TitleBarDefaults.Height - 6.dp)
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(if (selected) colors.selectedTab else if (hovered) colors.hover else Color.Transparent)
                    .selectable(selected, interaction, indication = null, role = Role.Tab, onClick = onSelect)
                    // Inside the selectable, so it takes a middle-click before the tab would.
                    .then(tab.onClose?.let { Modifier.closesOnMiddleClick(it) } ?: Modifier)
                    .padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(2.dp).height(16.dp).background(if (selected) colors.accent else Color.Transparent))
                Spacer(Modifier.width(7.dp))
                tab.icon?.let { icon ->
                    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                        CompositionLocalProvider(LocalContentColor provides if (selected) colors.selectedTabContent else colors.tabIcon) { icon() }
                    }
                    Spacer(Modifier.width(7.dp))
                }
                // Weighted, so it's measured after the tab's fixed parts and gives way first when the tab is narrowed.
                Label(
                    tab.title,
                    typography.tab,
                    if (selected) colors.selectedTabContent else colors.tab,
                    Modifier.weight(1f, fill = false).widthIn(max = tabTitleMaxWidth),
                )
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    val close = tab.onClose
                    when {
                        tab.dirty && !hovered ->
                            // Raised a little, level with the middle of the title's capitals rather than its line's.
                            Box(
                                Modifier.offset(y = -DOT_RAISE).size(8.dp).clip(CircleShape).background(colors.dirty)
                                    .semantics { contentDescription = "Unsaved changes" },
                            )
                        close != null && (hovered || selected) -> Button(TitleBarIcons.Close, "Close ${tab.title}", size = 20.dp, onClick = close)
                    }
                }
            }
        }
    }

    /** The search box: a button that looks like a field. */
    @Composable
    fun SearchBox(search: TitleBarSearch) {
        if (search.hidden) {
            Spacer(Modifier.width(search.width).height(SEARCH_HEIGHT))
            return
        }
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val shape = RoundedCornerShape(4.dp)
        Tip(search.tooltip) {
            Row(
                Modifier
                    .then(takesClicks())
                    .width(search.width)
                    .height(SEARCH_HEIGHT)
                    .clip(shape)
                    .background(if (hovered) colors.fieldHover else colors.field)
                    .border(1.dp, colors.fieldBorder, shape)
                    .hoverable(interaction)
                    .clickable(interaction, indication = null, role = Role.Button, onClick = search.onClick)
                    .testTag(TitleBarTags.SEARCH)
                    .padding(horizontal = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LineIcon(TitleBarIcons.Search, colors.fieldText, 12.dp)
                Spacer(Modifier.width(6.dp))
                Label(search.placeholder, typography.small, colors.fieldText, Modifier.weight(1f))
                search.shortcut?.let { keys ->
                    Spacer(Modifier.width(8.dp))
                    Label(keys, typography.small, colors.fieldText)
                }
            }
        }
    }

    /** Minimise, maximise or restore, and close, which turns red under the pointer. */
    @Composable
    fun WindowButtons(window: TitleBarWindow, style: TitleBarWindowButtons) {
        val maximized = window.isMaximized
        WindowButton(Glyph.Minimize, "Minimise", style, colors.hover) { window.minimize() }
        WindowButton(if (maximized) Glyph.Restore else Glyph.Maximize, if (maximized) "Restore down" else "Maximise", style, colors.hover) {
            window.toggleMaximized()
        }
        WindowButton(Glyph.Close, "Close", style, colors.close, glyphOnHover = Color.White) { window.close() }
    }

    @Composable
    private fun WindowButton(
        glyph: Glyph,
        description: String,
        style: TitleBarWindowButtons,
        hoverColor: Color,
        glyphOnHover: Color = colors.icon,
        onClick: () -> Unit,
    ) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val tint = if (hovered) glyphOnHover else colors.icon
        Tip(description) {
            Box(
                Modifier
                    .then(takesClicks())
                    .width(style.width)
                    .fillMaxHeight()
                    .background(if (hovered) hoverColor else Color.Transparent)
                    .hoverable(interaction)
                    .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                when (style) {
                    TitleBarWindowButtons.Compact -> LineIcon(glyph.icon, tint, 20.dp)
                    TitleBarWindowButtons.Windows -> HairlineGlyph(glyph, tint)
                }
            }
        }
    }
}

/** [icon], one of [TitleBarIcons], drawn [size] square in [tint]. */
@Composable
private fun LineIcon(icon: ImageVector, tint: Color, size: Dp) {
    Image(rememberVectorPainter(icon), null, Modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** The bar's own icons: lines on a 24-unit grid with round ends, so they stay crisp at any size and take any tint. */
private object TitleBarIcons {
    private fun icon(vararg paths: String): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            for (path in paths) {
                addPath(
                    pathData = addPathNodes(path),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Menu = icon("M4 7h16", "M4 12h16", "M4 17h16")
    val Undo = icon("M9 14.5L4 9.5l5-5", "M4 9.5h11a5 5 0 0 1 0 10h-3")
    val Redo = icon("M15 14.5l5-5-5-5", "M20 9.5H9a5 5 0 0 0 0 10h3")
    val Search = icon("M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13", "M20.5 20.5l-5.4-5.4")
    val Close = icon("M6.5 6.5l11 11M17.5 6.5l-11 11")
    val Plus = icon("M12 5.5v13", "M5.5 12h13")
    val Minimize = icon("M6 12.5h12")
    val Maximize = icon("M6.5 6.5h11v11h-11z")
    val Restore = icon("M6.5 9.5h8v8h-8z", "M9.5 9.5V6.5h8v8h-3")
}

private enum class Glyph(val icon: ImageVector) {
    Minimize(TitleBarIcons.Minimize),
    Maximize(TitleBarIcons.Maximize),
    Restore(TitleBarIcons.Restore),
    Close(TitleBarIcons.Close),
}

/** A window button's glyph as Windows draws its own: 10dp, in hairlines. */
@Composable
private fun HairlineGlyph(glyph: Glyph, color: Color) {
    Canvas(Modifier.size(10.dp)) {
        val stroke = 1.dp.toPx()
        val half = stroke / 2
        val side = size.width
        when (glyph) {
            Glyph.Minimize -> drawLine(color, Offset(0f, side / 2), Offset(side, side / 2), stroke)
            Glyph.Maximize -> drawRect(color, Offset(half, half), Size(side - stroke, side - stroke), style = Stroke(stroke))
            Glyph.Restore -> {
                // A window in front, lower left, and the top and right edges of one behind it.
                val front = side * RESTORE_FRONT
                val back = side - front
                drawRect(color, Offset(half, back + half), Size(front - stroke, front - stroke), style = Stroke(stroke))
                drawLine(color, Offset(back, half), Offset(side - half, half), stroke)
                drawLine(color, Offset(side - half, half), Offset(side - half, front), stroke)
            }
            Glyph.Close -> {
                drawLine(color, Offset(0f, 0f), Offset(side, side), stroke)
                drawLine(color, Offset(side, 0f), Offset(0f, side), stroke)
            }
        }
    }
}

private val SEARCH_HEIGHT = 24.dp

/** How far the unsaved dot sits above the tab's middle, so it is level with the bar's font's capitals. */
private val DOT_RAISE = 0.67.dp
private const val RESTORE_FRONT = 0.8f
private const val SELECTED_ALPHA = 0.22f

/**
 * Tells [onChange] whether the pointer is over this, from where each pointer event this gets puts it. Unlike an enter and
 * an exit, that covers the release ending a drag that began here, which comes here wherever the pointer is by then: over
 * something else in the window, or outside the window's content (over a title bar's caption, say), which sends no exit.
 */
private fun Modifier.tracksPointerOver(onChange: (Boolean) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                onChange(event.type != PointerEventType.Exit && event.changes.none { it.isOutOfBounds(size, Size.Zero) })
            }
        }
    }

/** The narrowest a tab gets as tabs share the bar: its mark, icon and close button and a few letters of its title. */
val MinTabWidth = 96.dp
