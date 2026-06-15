package com.isycat.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * Compact, fixed-height single-line search field (built on BasicTextField so it can be shorter than
 * Material's 56dp OutlinedTextField). Leading search icon, trailing clear button, stable layout.
 *
 * Pass [glowColor] to opt into an accent outline + outer glow (and a subtle inner gradient) that
 * lights up on focus — backwards compatible (null = the plain neutral field).
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search…",
    onClear: () -> Unit = { onValueChange("") },
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    height: Int = 40,
    glowColor: Color? = null
) {
    var focused by remember { mutableStateOf(false) }
    val textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = fieldModifier(modifier, focusRequester, height).onFocusChanged { focused = it.isFocused },
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner -> SearchDecoration(value.isEmpty(), placeholder, onClear, focused, glowColor, inner) }
    )
}

/** [TextFieldValue] variant (for cursor/selection control, e.g. select-all on focus). */
@Composable
fun SearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search…",
    onClear: () -> Unit = { onValueChange(TextFieldValue("")) },
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    height: Int = 40,
    glowColor: Color? = null
) {
    var focused by remember { mutableStateOf(false) }
    val textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = fieldModifier(modifier, focusRequester, height).onFocusChanged { focused = it.isFocused },
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner -> SearchDecoration(value.text.isEmpty(), placeholder, onClear, focused, glowColor, inner) }
    )
}

// --- helpers ---

private fun fieldModifier(modifier: Modifier, focusRequester: FocusRequester?, height: Int): Modifier =
    modifier
        .fillMaxWidth()
        .height(height.dp)
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)

@Composable
private fun SearchDecoration(
    empty: Boolean,
    placeholder: String,
    onClear: () -> Unit,
    focused: Boolean,
    glowColor: Color?,
    inner: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val accentFocused = glowColor != null && focused
    val borderColor = when {
        accentFocused -> glowColor!!
        glowColor != null -> glowColor.copy(alpha = 0.40f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    val borderWidth = if (accentFocused) 1.5.dp else 1.dp
    val background: Brush = if (accentFocused)
        Brush.verticalGradient(listOf(glowColor!!.copy(alpha = 0.07f), glowColor.copy(alpha = 0.02f)))
    else
        SolidColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    val iconTint = if (accentFocused) glowColor!! else MaterialTheme.colorScheme.onSurfaceVariant

    Box(Modifier.fillMaxSize()) {
        if (accentFocused) {
            Box(Modifier.matchParentSize().blur(10.dp, BlurredEdgeTreatment.Unbounded).background(glowColor!!.copy(alpha = 0.13f), shape))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(background, shape)
                .border(borderWidth, borderColor, shape)
                .padding(horizontal = 10.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (empty) Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                inner()
            }
            if (!empty) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "Clear search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp).clickable { onClear() }
                )
            }
        }
    }
}
