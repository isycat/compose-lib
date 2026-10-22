package com.isycat.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

data class MultiSelectOption<T>(
    val data: T,
    val label: String,
    val icon: (@Composable () -> Unit)? = null,
    val isRecommended: Boolean = false,
    val isDisabled: Boolean = false
)

@Composable
fun <T> MultiSelect(
    options: List<MultiSelectOption<T>>,
    selectedOption: T?,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    recommendedColor: Color = Color(0xFFFFD54F),
    recommendedAlpha: Float = 0.16f,
    /** When set, the selected chip uses an accent gradient fill + outer glow (Champions-style). */
    accentGlow: Color? = null
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            val isSelected = selectedOption == option.data

            if (accentGlow != null) {
                GlowChip(option, isSelected, accentGlow) { onOptionSelected(option.data) }
            } else {
                val chipModifier = Modifier
                    .height(36.dp)
                    .graphicsLayer { this.alpha = if (option.isDisabled) 0.45f else 1f }

                AssistChip(
                    modifier = chipModifier,
                    enabled = true,
                    onClick = { onOptionSelected(option.data) },
                    label = { Text(option.label) },
                    leadingIcon = option.icon,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = when {
                            isSelected && option.isRecommended -> recommendedColor.copy(alpha = 0.4f)
                            isSelected -> MaterialTheme.colorScheme.secondaryContainer
                            option.isRecommended -> recommendedColor.copy(alpha = recommendedAlpha)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                        labelColor = when {
                            isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        },
                    ),
                    border = when {
                        isSelected && option.isRecommended -> BorderStroke(1.dp, recommendedColor.copy(alpha = 0.9f))
                        isSelected -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        option.isRecommended -> BorderStroke(1.dp, recommendedColor.copy(alpha = 0.7f))
                        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    }
                )
            }
        }
    }
}

/** A chip with an accent gradient fill + outer glow when selected; a subtle outline otherwise. */
@Composable
private fun <T> GlowChip(option: MultiSelectOption<T>, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    val background: Brush =
        if (selected) Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.28f)))
        else SolidColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
    val borderColor = if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val labelColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)

    Box(Modifier.alpha(if (option.isDisabled) 0.45f else 1f)) {
        if (selected) {
            Box(Modifier.matchParentSize().blur(12.dp, BlurredEdgeTreatment.Unbounded).background(accent.copy(alpha = 0.5f), shape))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(36.dp)
                .clip(shape)
                .background(background, shape)
                .border(1.dp, borderColor, shape)
                .clickable { onClick() }
                .padding(horizontal = 16.dp)
        ) {
            option.icon?.let {
                it()
                Spacer(Modifier.width(6.dp))
            }
            Text(option.label, color = labelColor, style = MaterialTheme.typography.labelLarge)
        }
    }
}
