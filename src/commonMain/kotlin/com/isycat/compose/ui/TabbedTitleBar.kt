package com.isycat.compose.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isycat.compose.components.HoverTooltip
import com.isycat.compose.resources.Res
import com.isycat.compose.resources.roboto_regular
import org.jetbrains.compose.resources.Font

/**
 * One document's tab in a [TabbedTitleBar]: its [title], an [icon] before it (drawn about 14dp, in the tab's icon
 * colour as `LocalContentColor`) and a [tooltip], such as the file's path. An unsaved one ([dirty]) wears a dot at its
 * end until the pointer is on it. [onClose] gives it a close button there, shown while the tab is in front or the
 * pointer is on it; none when null. [key] keeps each tab's hover with it as tabs come and go.
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

    /** Moves the window from the pointer, as a press on a system title bar does, snapping to the screen's edges. */
    fun startMove()

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
     * platform, the desktop's and the browser's alike.
     */
    @Composable
    fun fontFamily(): FontFamily = FontFamily(Font(Res.font.roboto_regular))

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
 * The icon, the title and the window's buttons only show when the bar stands in for the window's title bar, given
 * its [window]; without one (a window keeping the system's title bar, or a browser) the bar is a plain tab bar.
 * [titleFollowsTab] makes the title start with the tab in front's, as `TitleBarDefaults.title` spells it; off, it's
 * always [title].
 *
 * [menu] draws the menu itself, anchored to the hamburger, given whether it's open and how to close it, so a
 * `DropdownMenu(expanded, onDismissRequest) { … }` fits it as it is.
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
    windowButtons: TitleBarWindowButtons = TitleBarWindowButtons.Compact,
    menu: (@Composable (expanded: Boolean, onDismissRequest: () -> Unit) -> Unit)? = null,
    menuDescription: String = "Menu",
    search: TitleBarSearch? = null,
    undoRedo: TitleBarUndoRedo? = null,
    actions: @Composable RowScope.() -> Unit = {},
    colors: TitleBarColors = TitleBarDefaults.colors(),
    typography: TitleBarTypography = TitleBarDefaults.typography(),
    tooltip: @Composable (String) -> Unit = { TitleBarDefaults.Tooltip(it, colors, typography) },
) {
    val bar = Bar(colors, typography, tooltip)
    Box(
        modifier
            .fillMaxWidth()
            .height(TitleBarDefaults.Height)
            .background(colors.bar)
            .then(if (window != null) Modifier.movesWindow(window) else Modifier),
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 4.dp, end = if (window != null) 0.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (window != null) {
                Spacer(Modifier.width(6.dp))
                if (appIcon != null) {
                    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                        CompositionLocalProvider(LocalContentColor provides colors.accent) { appIcon() }
                    }
                    Spacer(Modifier.width(8.dp))
                }
                val shown = TitleBarDefaults.title(title, tabs.getOrNull(selectedTabIndex), titleFollowsTab)
                Label(shown, typography.small, colors.title, Modifier.widthIn(max = 320.dp).testTag(TitleBarTags.TITLE))
                Spacer(Modifier.width(6.dp))
            }
            if (menu != null) {
                bar.MenuButton(menu, menuDescription)
                Spacer(Modifier.width(6.dp))
            }
            Row(
                Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.Bottom,
            ) {
                tabs.forEachIndexed { index, tab ->
                    key(tab.key) { bar.DocumentTab(tab, index == selectedTabIndex) { onTabSelected(index) } }
                }
            }
            Spacer(Modifier.width(8.dp))
            actions()
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

/** The bar's parts, sharing its colours, text and tooltips. */
private class Bar(
    val colors: TitleBarColors,
    val typography: TitleBarTypography,
    val tooltip: @Composable (String) -> Unit,
) {
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
    fun DocumentTab(tab: TitleBarTab, selected: Boolean, onSelect: () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        Tip(tab.tooltip) {
            Row(
                Modifier
                    .height(TitleBarDefaults.Height - 6.dp)
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(if (selected) colors.selectedTab else if (hovered) colors.hover else Color.Transparent)
                    .hoverable(interaction)
                    .selectable(selected, interaction, indication = null, role = Role.Tab, onClick = onSelect)
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
                Label(tab.title, typography.tab, if (selected) colors.selectedTabContent else colors.tab)
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
