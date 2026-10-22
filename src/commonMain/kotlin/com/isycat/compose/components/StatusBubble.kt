package com.isycat.compose.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What [StatusBubbleHost] shows: call [show] whenever something worth a glance happens, such as a save. */
class StatusBubbleState {
    internal class Bubble(val text: String, val icon: ImageVector?, val id: Int)

    internal var current by mutableStateOf<Bubble?>(null)
    private var count = 0

    /** Pops up [text], with [icon] before it if given. A new message replaces one still showing. */
    fun show(text: String, icon: ImageVector? = null) {
        current = Bubble(text, icon, ++count)
    }
}

@Composable
fun rememberStatusBubbleState(): StatusBubbleState = remember { StatusBubbleState() }

/**
 * A brief confirmation bubble, like "Saved": each message given to [state] pops in with a small pulse, stays for
 * [holdMillis], then fades away. Put it where it should appear, usually centred over the window's content. It never
 * takes clicks, so whatever is under it keeps working while it fades.
 */
@Composable
fun StatusBubbleHost(
    state: StatusBubbleState,
    modifier: Modifier = Modifier,
    holdMillis: Long = 900,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    iconColor: Color = MaterialTheme.colorScheme.primary,
) {
    val bubble = state.current ?: return
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(START_SCALE) }
    LaunchedEffect(bubble.id) {
        alpha.snapTo(0f)
        scale.snapTo(START_SCALE)
        coroutineScope {
            launch { alpha.animateTo(1f, tween(FADE_IN_MS)) }
            launch {
                // The pulse: a quick swell past full size that springs back.
                scale.animateTo(PULSE_SCALE, tween(FADE_IN_MS))
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
        }
        delay(holdMillis)
        coroutineScope {
            launch { alpha.animateTo(0f, tween(FADE_OUT_MS)) }
            launch { scale.animateTo(END_SCALE, tween(FADE_OUT_MS)) }
        }
        if (state.current === bubble) state.current = null
    }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .graphicsLayer {
                this.alpha = alpha.value
                scaleX = scale.value
                scaleY = scale.value
            }.background(containerColor, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bubble.icon?.let { Icon(it, null, Modifier.size(22.dp), tint = iconColor) }
        Text(bubble.text, style = MaterialTheme.typography.titleMedium, color = contentColor)
    }
}

private const val START_SCALE = 0.85f
private const val PULSE_SCALE = 1.06f
private const val END_SCALE = 0.96f
private const val FADE_IN_MS = 140
private const val FADE_OUT_MS = 450
