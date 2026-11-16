package com.isycat.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * An app's offer to open its files on a double-click ([FileTypesOffer]), as a bar across the window under its title
 * bar, as moonshine and dittoCAD show it: the question, "Don't ask again", Not now and the button that takes them. It
 * takes its height only while the offer stands; a failure to take the files says why in its place.
 */
@Composable
fun FileTypesBar(offer: FileTypesOffer, modifier: Modifier = Modifier) {
    if (!offer.asking) return
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val hand = Modifier.pointerHoverIcon(PointerIcon.Hand)
    Column(modifier.fillMaxWidth().testTag(FILE_TYPES_BAR)) {
        Row(
            Modifier.fillMaxWidth().height(FILE_TYPES_BAR_HEIGHT).background(colors.surfaceContainer).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val failure = offer.failure
            Icon(
                if (failure == null) Icons.Outlined.FolderOpen else Icons.Outlined.WarningAmber,
                null,
                Modifier.size(18.dp),
                tint = if (failure == null) colors.primary else colors.error,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                failure ?: offer.question,
                style = MaterialTheme.typography.bodyMedium,
                color = if (failure == null) colors.onSurface else colors.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).testTag(FILE_TYPES_QUESTION),
            )
            Spacer(Modifier.width(12.dp))
            if (failure == null) {
                // The box and its words tick together, as a checkbox's label does.
                Row(
                    hand.clickable(enabled = !offer.working) { offer.dontAskAgain = !offer.dontAskAgain },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(offer.dontAskAgain, { if (!offer.working) offer.dontAskAgain = it }, hand.testTag(FILE_TYPES_DONT_ASK))
                    Text("Don't ask again", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Spacer(Modifier.width(14.dp))
                OutlinedButton(onClick = offer::decline, enabled = !offer.working, modifier = hand.testTag(FILE_TYPES_NOT_NOW)) {
                    Text("Not now")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { scope.launch { offer.accept() } }, enabled = !offer.working, modifier = hand.testTag(FILE_TYPES_YES)) {
                    Text(offer.answer)
                }
            } else {
                OutlinedButton(onClick = offer::decline, modifier = hand) { Text("Close") }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))
    }
}

/** Marks the bar that offers to open the app's files. */
const val FILE_TYPES_BAR = "file-types-bar"

/** Marks the bar's question. */
const val FILE_TYPES_QUESTION = "file-types-question"

/** Marks the bar's "Don't ask again" box. */
const val FILE_TYPES_DONT_ASK = "file-types-dont-ask"

/** Marks the bar's Not now. */
const val FILE_TYPES_NOT_NOW = "file-types-not-now"

/** Marks the bar's yes. */
const val FILE_TYPES_YES = "file-types-yes"

/** The bar's height, its line under it aside. */
val FILE_TYPES_BAR_HEIGHT = 44.dp
