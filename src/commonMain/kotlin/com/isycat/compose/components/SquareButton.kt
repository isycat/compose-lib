package com.isycat.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The colours of a [SquareButton]: its fill under the pointer and while selected, and its icon's in each state. */
data class SquareButtonColors(
    val hover: Color,
    val selected: Color,
    val icon: Color,
    val iconHover: Color,
    val iconSelected: Color,
    val iconDisabled: Color,
)

/** What a [SquareButton] looks like where nothing says otherwise. */
object SquareButtonDefaults {
    /** The corners every square button and its highlight share. */
    val shape = RoundedCornerShape(4.dp)

    /** Colours from the theme: a faint fill under the pointer, the secondary container while selected. */
    @Composable
    fun colors(): SquareButtonColors {
        val scheme = MaterialTheme.colorScheme
        return SquareButtonColors(
            hover = scheme.onSurface.copy(alpha = HOVER),
            selected = scheme.secondaryContainer,
            icon = scheme.onSurfaceVariant,
            iconHover = scheme.onSurface,
            iconSelected = scheme.primary,
            iconDisabled = scheme.onSurface.copy(alpha = DISABLED),
        )
    }

    private const val HOVER = 0.08f
    private const val DISABLED = 0.38f
}

/**
 * A small button lit square under the pointer and while [selected], with no ripple: the highlight the title bar's
 * buttons have, for every icon button that wants it. [content] draws what it holds, in the tint it is handed for its
 * state; [description] names it for accessibility. A tooltip, where one is wanted, goes round it. A click leaves the
 * keyboard where it was, as a toolbar's buttons do, so the app's keys, Space among them, never click it again.
 */
@Composable
fun SquareButton(
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    width: Dp = 28.dp,
    height: Dp = width,
    colors: SquareButtonColors = SquareButtonDefaults.colors(),
    onClick: () -> Unit,
    content: @Composable (tint: Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background = when {
        selected -> colors.selected
        hovered && enabled -> colors.hover
        else -> Color.Transparent
    }
    val tint = when {
        !enabled -> colors.iconDisabled
        selected -> colors.iconSelected
        hovered -> colors.iconHover
        else -> colors.icon
    }
    Box(
        modifier
            .size(width, height)
            .clip(SquareButtonDefaults.shape)
            .background(background)
            .hoverable(interaction)
            .focusProperties { canFocus = false }
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content(tint)
    }
}
