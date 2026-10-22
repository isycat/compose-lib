package com.isycat.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun CollapsibleSection(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    isInitialCollapsed: Boolean = false,
    onCollapsedChange: (Boolean) -> Unit = {},
    showDividers: Boolean = true,
    titlePadding: Modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 8.dp),
    accentColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    var isCollapsed by remember { mutableStateOf(isInitialCollapsed) }
    val interactionSource = remember { MutableInteractionSource() }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showDividers) HorizontalDivider()
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hoverable(interactionSource = interactionSource)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = accentColor),
                ) { 
                    isCollapsed = !isCollapsed 
                    onCollapsedChange(isCollapsed)
                }
                .then(titlePadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                title()
            }

            Icon(
                imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                contentDescription = if (isCollapsed) "Expand" else "Collapse",
            )
        }

        if (showDividers) HorizontalDivider()

        AnimatedVisibility(visible = !isCollapsed) {
            content()
        }
    }
}
