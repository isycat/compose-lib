package com.isycat.compose.startup

import java.awt.SplashScreen
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * An app starting up, as cardSTUDIO starts. Each [step] is shown on the splash screen while there is one (the JVM shows
 * [splash]'s image the moment it starts, given `-splash:`, and closes it by itself when the first window opens), and
 * noted, with how long after the process started it began, in a log that [finish] writes, so a slow start can be traced
 * to its step.
 */
class Startup(private val splash: SplashArt, private val log: () -> File?) {
    private val began: Instant = ProcessHandle.current().info().startInstant().orElseGet(Instant::now)
    private val lines = mutableListOf<String>()
    private val screen: SplashScreen? = runCatching { SplashScreen.getSplashScreen() }.getOrNull()

    init {
        record(if (screen != null) "Started, splash showing" else "Started, without a splash")
    }

    /** Now doing [text], [progress] of the way (0 to 1) when that is known. */
    fun step(text: String, progress: Float? = null) {
        record(text)
        val shown = screen ?: return
        runCatching {
            if (!shown.isVisible) return
            val g = shown.createGraphics()
            try {
                splash.paintStatus(g, text, progress)
            } finally {
                g.dispose()
            }
            shown.update()
        }
    }

    /** Notes [text] in the log, not on the splash. */
    fun record(text: String) {
        val ms = Duration.between(began, Instant.now()).toMillis()
        synchronized(lines) { lines += "%7d ms  %s".format(ms, text) }
    }

    /** Notes [text] as the last step and writes the log, replacing the last start's. */
    fun finish(text: String) {
        record(text)
        val all = synchronized(lines) { lines.joinToString("\n", postfix = "\n") }
        runCatching { log()?.writeText(all) }
    }

    companion object {
        /** What the splash says before the app's code has run. */
        const val FIRST_STEP = "Starting…"
    }
}
