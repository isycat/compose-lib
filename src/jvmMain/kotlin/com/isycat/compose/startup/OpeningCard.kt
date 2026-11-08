package com.isycat.compose.startup

import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.swing.JComponent
import javax.swing.JWindow
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * A card in the middle of the screen from the moment an app starts until its window has drawn, as moonshine's opening
 * card: the icon, the name, the step it is on and a bar of how far it has come, drawn by Swing alone before Compose is
 * made. Nothing is written beside the app to show it.
 *
 * The bar is measured, not guessed: each of [steps] fills the share of the bar that it took of the last start, read from
 * [timings] and written back there when the card closes. A first start shares the bar evenly between the steps. Within
 * a step, [step] can say how far through it is; else the bar moves on as the step's time passes, up to most of the
 * step's share by the time it took last.
 */
class OpeningCard private constructor(
    private val window: JWindow,
    private val face: Face,
    private val steps: List<String>,
    private val timings: Path?,
    /** How long each step took in the last start, in milliseconds, or null on a first start. */
    private val expected: LongArray?,
) {
    private val shares: DoubleArray = shares(steps.size, expected)
    private val took = LongArray(steps.size)
    private var current = 0
    private var currentBegan = System.nanoTime()
    private var shownAt = 0f
    private var shownStep = steps.first()
    private var closed = false

    /** Moves the bar on by time, 30 times a second, on the interface thread. */
    private val ticks = Timer(TICK_MS) {
        val at = timed()
        if (at - face.progress >= 1f / SplashArt.WIDTH) {
            face.progress = at
            face.repaint()
        }
    }

    /**
     * Now on [name], one of the card's steps, [within] (0 to 1) of the way through it, from any thread. The bar never
     * goes back, and is redrawn only once it moves a pixel or the step changes.
     */
    @Synchronized
    fun step(name: String, within: Double = 0.0) {
        if (closed) return
        val index = steps.indexOf(name).also { require(it >= 0) { "$name is not one of the card's steps" } }
        val now = System.nanoTime()
        while (current < index) {
            took[current] += now - currentBegan
            currentBegan = now
            current++
        }
        val at = (shares.take(index).sum() + shares[index] * within.coerceIn(0.0, 1.0)).toFloat()
        if (name == shownStep && at - shownAt < 1f / SplashArt.WIDTH) return
        shownStep = name
        shownAt = maxOf(shownAt, at)
        val progress = shownAt
        SwingUtilities.invokeLater {
            face.status = name
            face.progress = maxOf(face.progress, progress)
            face.repaint()
        }
    }

    /** Shows [icon] beside the name, once it is made. */
    fun icon(icon: Image) = SwingUtilities.invokeLater {
        face.icon = icon
        face.repaint()
    }

    /** Puts the card away and keeps how long each step took for the next start's bar; once, however often it is called. */
    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        took[current] += System.nanoTime() - currentBegan
        SwingUtilities.invokeLater {
            ticks.stop()
            window.dispose()
        }
        val file = timings ?: return
        val text = steps.indices.joinToString("\n", postfix = "\n") { "${steps[it]}\t${took[it] / NANOS_PER_MS}" }
        // Off the interface thread; only the next start's bar needs it, and a start that cannot keep it still opens.
        Thread({
            runCatching {
                Files.createDirectories(file.parent)
                val written = Files.writeString(file.resolveSibling("${file.fileName}.new"), text)
                Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }
        }, "opening card timings").start()
    }

    /** Where the bar stands by the time spent in the step it is on, against the time that step took last. */
    @Synchronized
    private fun timed(): Float {
        val last = expected?.get(current)?.takeIf { it > 0 } ?: return shownAt
        val within = minOf(TIMED_MOST, (System.nanoTime() - currentBegan).toDouble() / NANOS_PER_MS / last)
        return maxOf(shownAt, (shares.take(current).sum() + shares[current] * within).toFloat())
    }

    companion object {
        /**
         * The card drawn by [art], going through [steps] in order, its bar measured by [timings] (see [OpeningCard]);
         * shown now, on the first step, in the middle of the main screen.
         */
        fun show(art: SplashArt, steps: List<String>, timings: Path?): OpeningCard {
            require(steps.isNotEmpty()) { "An opening card needs a step" }
            val expected = expected(steps, timings)
            var card: OpeningCard? = null
            SwingUtilities.invokeAndWait {
                val window = JWindow()
                val face = Face(art, steps.first())
                // Clear outside the round corners, which the face draws smooth.
                window.background = Color(0, 0, 0, 0)
                window.focusableWindowState = false
                window.contentPane = face
                window.size = Dimension(art.width, art.height)
                window.setLocationRelativeTo(null)
                window.isVisible = true
                card = OpeningCard(window, face, steps, timings, expected).also { it.ticks.start() }
            }
            return checkNotNull(card)
        }

        /** How long each of [steps] took in the last start, in milliseconds, from [timings]; null where it names not every step. */
        fun expected(steps: List<String>, timings: Path?): LongArray? {
            val kept = runCatching {
                timings?.takeIf(Files::isRegularFile)?.let(Files::readAllLines)?.mapNotNull { line ->
                    line.split('\t').takeIf { it.size == 2 }?.let { (step, ms) -> ms.toLongOrNull()?.takeIf { it >= 0 }?.let { step to it } }
                }?.toMap()
            }.getOrNull().orEmpty()
            val measured = steps.map { kept[it] ?: return null }
            return measured.takeIf { it.sum() > 0 }?.toLongArray()
        }

        /** Each of [count] steps' share of the bar: its share of the time [expected] gives, or an even share. */
        fun shares(count: Int, expected: LongArray?): DoubleArray {
            val total = expected?.sum()?.takeIf { it > 0 }?.toDouble() ?: return DoubleArray(count) { 1.0 / count }
            return DoubleArray(count) { expected[it] / total }
        }

        /** How far through a step the bar goes by time alone, so it never stands full while the step goes on. */
        private const val TIMED_MOST = 0.9
        private const val TICK_MS = 33
        private const val NANOS_PER_MS = 1_000_000L
    }

    /** The card's face, drawn by its [art]. */
    private class Face(private val art: SplashArt, var status: String) : JComponent() {
        var icon: Image? = null
        var progress = 0f

        init {
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) = art.paint(g as Graphics2D, icon, status, progress)
    }
}
