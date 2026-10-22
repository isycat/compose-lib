package com.isycat.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun ClickableModifier(baseModifier: Modifier = Modifier.Companion, onClick: (() -> Unit)? = null) = baseModifier
    .clip(RoundedCornerShape(8.dp))
    .then(
        if (onClick != null)
            Modifier.Companion.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true)
            ) { onClick() }
        else Modifier.Companion
    )
