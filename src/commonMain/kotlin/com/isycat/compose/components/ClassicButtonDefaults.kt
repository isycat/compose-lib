package com.isycat.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Buttons and chips as Material3 drew them before 1.9, which re-tokenised them: outlined buttons and chips took the dimmer
 * outlineVariant border, outlined buttons an onSurfaceVariant label, every disabled label onSurfaceVariant instead of
 * onSurface, and a filled button's disabled container 10% instead of 12%. The apps were designed on the old look, so they
 * pass these to keep it. Each takes the same parameters as Material's own, with the old defaults.
 */
object ClassicButtonDefaults {
    private const val DISABLED_CONTENT = 0.38f
    private const val DISABLED_CONTAINER = 0.12f
    private val OutlineWidth = 1.dp

    @Composable
    fun buttonColors(
        containerColor: Color = MaterialTheme.colorScheme.primary,
        contentColor: Color = MaterialTheme.colorScheme.onPrimary,
        disabledContainerColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_CONTAINER),
        disabledContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_CONTENT),
    ): ButtonColors = ButtonDefaults.buttonColors(
        containerColor = containerColor,
        contentColor = contentColor,
        disabledContainerColor = disabledContainerColor,
        disabledContentColor = disabledContentColor,
    )

    @Composable
    fun textButtonColors(
        containerColor: Color = Color.Transparent,
        contentColor: Color = MaterialTheme.colorScheme.primary,
        disabledContainerColor: Color = Color.Transparent,
        disabledContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_CONTENT),
    ): ButtonColors = ButtonDefaults.textButtonColors(
        containerColor = containerColor,
        contentColor = contentColor,
        disabledContainerColor = disabledContainerColor,
        disabledContentColor = disabledContentColor,
    )

    @Composable
    fun outlinedButtonColors(
        containerColor: Color = Color.Transparent,
        contentColor: Color = MaterialTheme.colorScheme.primary,
        disabledContainerColor: Color = Color.Transparent,
        disabledContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_CONTENT),
    ): ButtonColors = ButtonDefaults.outlinedButtonColors(
        containerColor = containerColor,
        contentColor = contentColor,
        disabledContainerColor = disabledContainerColor,
        disabledContentColor = disabledContentColor,
    )

    @Composable
    fun outlinedButtonBorder(enabled: Boolean = true): BorderStroke {
        val outline = MaterialTheme.colorScheme.outline
        return BorderStroke(OutlineWidth, if (enabled) outline else outline.copy(alpha = DISABLED_CONTAINER))
    }

    @Composable
    fun filterChipBorder(enabled: Boolean, selected: Boolean): BorderStroke =
        FilterChipDefaults.filterChipBorder(enabled = enabled, selected = selected, borderColor = MaterialTheme.colorScheme.outline)

    @Composable
    fun assistChipBorder(enabled: Boolean): BorderStroke =
        AssistChipDefaults.assistChipBorder(enabled = enabled, borderColor = MaterialTheme.colorScheme.outline)
}
