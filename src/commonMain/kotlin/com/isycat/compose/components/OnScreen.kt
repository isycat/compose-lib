package com.isycat.compose.components

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the content is on screen. A host that keeps content composed while it's hidden (a tab behind another) provides
 * false there, and what floats over the window from that content (hover tooltips, dialogs) isn't shown until it's back.
 */
val LocalOnScreen = compositionLocalOf { true }
