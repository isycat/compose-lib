package com.isycat.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.math.roundToInt

/** Where a window was left: its place and size when not maximised, in the units Compose sizes windows in, and whether it was maximised. */
data class WindowPlace(val x: Float, val y: Float, val width: Float, val height: Float, val maximized: Boolean) {
    /** Writes this place to [file], whole or not at all. */
    fun write(file: Path) {
        Files.createDirectories(file.parent)
        val text = "x=$x\ny=$y\nwidth=$width\nheight=$height\nmaximized=$maximized\n"
        val written = Files.writeString(file.resolveSibling("${file.fileName}.new"), text)
        Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        /** The place kept in [file], or null when there is none or it cannot be read. */
        fun read(file: Path): WindowPlace? = runCatching {
            val values = Files.readAllLines(file).mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
                .associate { (key, value) -> key.trim() to value.trim() }
            WindowPlace(
                values.getValue("x").toFloat(),
                values.getValue("y").toFloat(),
                values.getValue("width").toFloat(),
                values.getValue("height").toFloat(),
                values.getValue("maximized").toBooleanStrict(),
            ).takeIf { it.width > 0 && it.height > 0 }
        }.getOrNull()

        /**
         * How a window opens: at [place] when enough of its top edge, where it is grabbed, is on one of [screens] (in the
         * same units), maximised if it was; otherwise centred at [size], as the first time and when its screen is gone.
         */
        fun opening(place: WindowPlace?, screens: List<Rectangle>, size: DpSize): WindowState {
            val grab = place?.let { Rectangle(it.x.roundToInt(), it.y.roundToInt(), it.width.roundToInt(), GRAB_HEIGHT) }
            val onScreen = grab != null && screens.any { screen ->
                val shared = screen.intersection(grab)
                !shared.isEmpty && shared.width >= GRAB_WIDTH.coerceAtMost(grab.width) && shared.height >= GRAB_HEIGHT / 2
            }
            if (place == null || !onScreen) return WindowState(position = WindowPosition(Alignment.Center), size = size)
            return WindowState(
                placement = if (place.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
                position = WindowPosition(place.x.dp, place.y.dp),
                size = DpSize(place.width.dp, place.height.dp),
            )
        }

        /** How much of a window's top edge must be on a screen for it to open there: enough to grab and move it. */
        private const val GRAB_WIDTH = 120
        private const val GRAB_HEIGHT = 32
    }
}

/**
 * A window state that opens the window where it was left, kept in [file], or centred at [size] the first time and when
 * the screen it was on is gone; and keeps its place, size and maximised state there as they change, and as it closes.
 */
@Composable
fun rememberWindowStateIn(file: Path, size: DpSize): WindowState {
    val state = remember { WindowPlace.opening(WindowPlace.read(file), screens(), size) }
    val kept = remember { Kept(WindowPlace.read(file)) }
    LaunchedEffect(state) {
        snapshotFlow { Triple(state.placement, state.position, state.size) }.collectLatest { (placement, position, size) ->
            kept.update(placement, position, size)
            // Moving or resizing changes these many times a second; the place is written once it settles.
            delay(SETTLE_MS)
            withContext(Dispatchers.IO) { kept.write(file) }
        }
    }
    DisposableEffect(state) { onDispose { kept.write(file) } }
    return state
}

/** The place being kept: the window's last place and size when it was not maximised, and whether it is maximised now. */
private class Kept(private var place: WindowPlace?) {
    @Synchronized
    fun update(placement: WindowPlacement, position: WindowPosition, size: DpSize) {
        val maximized = placement == WindowPlacement.Maximized
        place = if (placement == WindowPlacement.Floating && position is WindowPosition.Absolute && size.isSpecified) {
            WindowPlace(position.x.value, position.y.value, size.width.value, size.height.value, maximized = false)
        } else {
            place?.copy(maximized = maximized)
        }
    }

    @Synchronized
    fun write(file: Path) {
        place?.let { runCatching { it.write(file) } }
    }
}

private fun screens(): List<Rectangle> = runCatching {
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }
}.getOrDefault(emptyList())

private const val SETTLE_MS = 400L
