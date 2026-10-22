package com.isycat.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class StatusItem(
    val label: String,
    val value: String,
    val valueColor: Color? = null,
    val icon: (@Composable () -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusDashboard(
    items: List<StatusItem>,
    modifier: Modifier = Modifier,
    columns: Int = 3
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.medium
    ) {
        FlowRow(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            maxItemsInEachRow = columns
        ) {
            items.forEach { item ->
                StatusBox(item)
            }
        }
    }
}

@Composable
private fun StatusBox(item: StatusItem) {
    Column {
        Text(
            text = item.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.offset(x = (-4).dp)) {
            Row(
                modifier = ClickableModifier(onClick = item.onClick)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.icon != null) {
                    item.icon.invoke()
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    modifier = Modifier.padding(bottom = 2.dp),
                    text = item.value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = item.valueColor ?: MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
