package com.isycat.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

data class BreadcrumbItem<T>(
    val text: String,
    val data: T,
    val icon: (@Composable () -> Unit)? = null
)

@Composable
fun <T> Breadcrumbs(
    items: List<BreadcrumbItem<T>>,
    rootText: String,
    onRootClick: () -> Unit,
    onItemClick: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val breadcrumbsStyle = MaterialTheme.typography.bodyLarge.copy(letterSpacing = 0.15.em)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .background(color = MaterialTheme.colorScheme.background)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = rootText.uppercase(),
                style = breadcrumbsStyle,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .height(32.dp)
                    .clickable { onRootClick() }
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            )

            items.forEach { item ->
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = ">",
                    style = breadcrumbsStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(32.dp)
                        .clickable { onItemClick(item.data) }
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = item.text.uppercase(),
                        style = breadcrumbsStyle,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (item.icon != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        item.icon.invoke()
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.height(1.7.dp))
    }
}
