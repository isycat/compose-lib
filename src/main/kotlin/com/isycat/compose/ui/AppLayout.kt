package com.isycat.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

data class AppTabItem(
    val title: String? = null,
    val icon: (@Composable () -> Unit)? = null
)

/**
 * A button among the tabs that isn't a tab: it looks just like one, but clicking it runs [onClick] instead of selecting
 * anything. [anchored] is composed inside the button, so a menu it opens (a `DropdownMenu`) drops down from it.
 */
data class AppTabButton(
    val title: String? = null,
    val icon: (@Composable () -> Unit)? = null,
    val anchored: (@Composable () -> Unit)? = null,
    val onClick: () -> Unit
)

/** Where [AppLayout] puts its tabs. */
enum class TabPlacement {
    /** A tab row across the top, under the top bar. */
    Top,

    /** A narrow rail down the left side, like a side bar: icons over their titles, the content beside it. */
    Side
}

@Composable
fun AppLayout(
    tabs: List<AppTabItem>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    /** When set, the tab bar uses a glowing accent indicator and accent-glow selected tabs. */
    accentColor: Color? = null,
    /** Optional override for the tab bar background (defaults to the Material surface). */
    tabContainerColor: Color? = null,
    /** Where the tabs go: across the top (the default) or down a side rail. */
    tabPlacement: TabPlacement = TabPlacement.Top,
    /** Buttons that look like tabs but aren't tied to one, placed before the tabs. */
    buttons: List<AppTabButton> = emptyList(),
    content: @Composable (Int) -> Unit
) {
    if (tabPlacement == TabPlacement.Side) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = { topBar?.invoke() },
            bottomBar = { bottomBar?.invoke() }
        ) { innerPadding ->
            Row(Modifier.fillMaxSize().padding(innerPadding)) {
                TabRail(tabs, buttons, selectedTabIndex, onTabSelected, accentColor, tabContainerColor)
                VerticalDivider()
                Column(Modifier.fillMaxSize()) { content(selectedTabIndex) }
            }
        }
        return
    }
    // The buttons come first in the row, so the selected tab's position in it is shifted past them.
    val selectedPosition = selectedTabIndex + buttons.size
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            topBar?.invoke()
            if (accentColor != null) {
                TabRow(
                    selectedTabIndex = selectedPosition,
                    containerColor = tabContainerColor ?: MaterialTheme.colorScheme.surface,
                    indicator = { positions -> GlowIndicator(positions, selectedPosition, accentColor) }
                ) {
                    buttons.forEach { button ->
                        TabButton(button) { AccentTab(AppTabItem(button.title, button.icon), false, accentColor, button.onClick) }
                    }
                    tabs.forEachIndexed { index, item ->
                        AccentTab(item, index == selectedTabIndex, accentColor) { onTabSelected(index) }
                    }
                }
            } else {
                TabRow(selectedTabIndex = selectedPosition) {
                    buttons.forEach { button ->
                        TabButton(button) {
                            Tab(
                                selected = false,
                                onClick = button.onClick,
                                text = button.title?.let { { Text(it) } },
                                icon = button.icon
                            )
                        }
                    }
                    tabs.forEachIndexed { index, item ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { onTabSelected(index) },
                            text = item.title?.let { { Text(it) } },
                            icon = item.icon
                        )
                    }
                }
            }
        },
        bottomBar = { bottomBar?.invoke() }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            content(selectedTabIndex)
        }
    }
}

private val RailWidth = 72.dp
private val RailItemHeight = 64.dp

/**
 * The tabs as a vertical rail, after any [buttons]: each one an icon over its title, the selected one in [accentColor]
 * (glowing, with a glowing bar on the rail's inner edge) or the primary colour when there's no accent.
 */
@Composable
private fun TabRail(
    tabs: List<AppTabItem>,
    buttons: List<AppTabButton>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    accentColor: Color?,
    containerColor: Color?
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .width(RailWidth)
            .fillMaxHeight()
            .background(containerColor ?: MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp)
            .selectableGroup(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        buttons.forEach { button ->
            RailItem(
                AppTabItem(button.title, button.icon),
                Modifier.clickable(role = Role.Button, onClick = button.onClick),
                selected = false,
                accent = accent,
                glow = false
            ) { button.anchored?.invoke() }
        }
        tabs.forEachIndexed { index, item ->
            val selected = index == selectedTabIndex
            RailItem(
                item,
                Modifier.selectable(selected = selected, role = Role.Tab, onClick = { onTabSelected(index) }),
                selected = selected,
                accent = accent,
                glow = accentColor != null
            )
        }
    }
}

/** One entry of the rail, tab or button: [item]'s icon over its title, marked as [selected] with the [accent]. */
@Composable
private fun RailItem(
    item: AppTabItem,
    interaction: Modifier,
    selected: Boolean,
    accent: Color,
    glow: Boolean,
    anchored: @Composable () -> Unit = {}
) {
    val color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .fillMaxWidth()
            .height(RailItemHeight)
            .then(interaction),
        contentAlignment = Alignment.Center
    ) {
        if (selected) RailIndicator(accent, glow = glow, Modifier.align(Alignment.CenterEnd))
        CompositionLocalProvider(LocalContentColor provides color) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val glowing = selected && glow
                item.icon?.let { icon -> if (glowing) GlowWhenSelected(true) { icon() } else icon() }
                item.title?.let { title ->
                    val text = @Composable { Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                    if (glowing) GlowWhenSelected(true, text) else text()
                }
            }
        }
        anchored()
    }
}

/** A button in a top tab row: the tab-looking [content], with the button's anchored content (its menu) beside it. */
@Composable
private fun TabButton(button: AppTabButton, content: @Composable () -> Unit) {
    Box {
        content()
        button.anchored?.invoke()
    }
}

/** The rail's selection mark: a short vertical bar, with a blurred halo when [glow]. */
@Composable
private fun RailIndicator(color: Color, glow: Boolean, modifier: Modifier) {
    Box(modifier.padding(vertical = 14.dp).width(3.dp).fillMaxHeight()) {
        val shape = RoundedCornerShape(2.dp)
        if (glow) Box(Modifier.matchParentSize().blur(7.dp, BlurredEdgeTreatment.Unbounded).background(color, shape))
        Box(Modifier.matchParentSize().background(color, shape))
    }
}

/** A tab whose selected content is the accent color with a soft glow behind it. */
@Composable
private fun AccentTab(item: AppTabItem, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        selectedContentColor = accent,
        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        text = item.title?.let { title -> { GlowWhenSelected(selected) { Text(title) } } },
        icon = item.icon?.let { ic -> { GlowWhenSelected(selected) { ic() } } }
    )
}

/** Renders [content] with a soft glow (a blurred copy behind) when [selected]; content inherits
 *  the Tab's accent content color, so the blurred copy glows in the accent. */
@Composable
private fun GlowWhenSelected(selected: Boolean, content: @Composable () -> Unit) {
    if (selected) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.blur(8.dp, BlurredEdgeTreatment.Unbounded)) { content() }
            content()
        }
    } else {
        content()
    }
}

/** A glowing underline indicator: a blurred accent halo under a crisp accent bar. */
@Composable
private fun GlowIndicator(positions: List<TabPosition>, selectedIndex: Int, color: Color) {
    if (selectedIndex !in positions.indices) return
    Box(
        Modifier
            .tabIndicatorOffset(positions[selectedIndex])
            .padding(horizontal = 12.dp)
            .height(3.dp)
    ) {
        Box(Modifier.matchParentSize().blur(7.dp, BlurredEdgeTreatment.Unbounded).background(color, RoundedCornerShape(2.dp)))
        Box(Modifier.matchParentSize().background(color, RoundedCornerShape(2.dp)))
    }
}
