package com.isycat.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One answer of a [SlimDialog]: its [label], whether it is the [primary] one Enter gives, and what it does. */
data class DialogAnswer(val label: String, val primary: Boolean = false, val onClick: () -> Unit)

/**
 * A slim question over the dimmed window, as moonshine's are rather than Material's: a narrow title bar with [title] and
 * a close button, the [question] in bold, one line of [detail], and compact [answers] at the end, the primary filled.
 * Enter gives the primary answer; Escape, the close button or a click outside [onClose] it.
 */
@Composable
fun SlimDialog(
    title: String,
    question: String,
    detail: String?,
    answers: List<DialogAnswer>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    surface: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        Modifier.fillMaxSize()
            .background(colors.scrim.copy(alpha = SCRIM))
            .pointerInput(Unit) { detectTapGestures { onClose() } }
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                val down = event.type == KeyEventType.KeyDown
                when {
                    down && event.key == Key.Escape -> true.also { onClose() }
                    down && (event.key == Key.Enter || event.key == Key.NumPadEnter) ->
                        answers.firstOrNull { it.primary }?.also { it.onClick() } != null
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = surface,
            shadowElevation = 8.dp,
            modifier = modifier.widthIn(max = WIDTH).pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column {
                Row(Modifier.fillMaxWidth().height(TITLE_HEIGHT).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                    SquareButton("Close", width = 24.dp, onClick = onClose) { tint -> Icon(Icons.Filled.Close, null, Modifier.size(14.dp), tint = tint) }
                }
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
                    Text(question, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    detail?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) { answers.forEach { SlimButton(it) } }
                }
            }
        }
    }
}

/** A compact button: filled in the accent where it is the primary answer, else outlined. */
@Composable
private fun SlimButton(answer: DialogAnswer) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = SquareButtonDefaults.shape
    val fill = when {
        answer.primary -> if (hovered) colors.primary.copy(alpha = HOVERED_PRIMARY) else colors.primary
        hovered -> colors.onSurface.copy(alpha = HOVER)
        else -> Color.Transparent
    }
    Box(
        Modifier.height(BUTTON_HEIGHT)
            .clip(shape)
            .background(fill)
            .then(if (answer.primary) Modifier else Modifier.border(1.dp, colors.outlineVariant, shape))
            .hoverable(interaction)
            .clickable(interaction, indication = null, role = Role.Button, onClick = answer.onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(answer.label, style = MaterialTheme.typography.labelLarge, color = if (answer.primary) colors.onPrimary else colors.onSurface)
    }
}

private val WIDTH = 420.dp
private val TITLE_HEIGHT = 30.dp
private val BUTTON_HEIGHT = 30.dp
private const val SCRIM = 0.4f
private const val HOVER = 0.08f
private const val HOVERED_PRIMARY = 0.88f
