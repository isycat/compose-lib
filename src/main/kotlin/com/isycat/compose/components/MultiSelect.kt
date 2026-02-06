package com.isycat.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    recommendedAlpha: Float = 0.16f
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            val isSelected = selectedOption == option.data

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
