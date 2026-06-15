package com.isycat.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class AppTabItem(
    val title: String? = null,
    val icon: (@Composable () -> Unit)? = null
)

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
    content: @Composable (Int) -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            topBar?.invoke()
            if (accentColor != null) {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = tabContainerColor ?: MaterialTheme.colorScheme.surface,
                    indicator = { positions -> GlowIndicator(positions, selectedTabIndex, accentColor) }
                ) {
                    tabs.forEachIndexed { index, item ->
                        AccentTab(item, index == selectedTabIndex, accentColor) { onTabSelected(index) }
                    }
                }
            } else {
                TabRow(selectedTabIndex = selectedTabIndex) {
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
