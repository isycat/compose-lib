package com.isycat.compose.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.sun.jna.CallbackReference
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.Cursor
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.ConcurrentHashMap
import javax.swing.RootPaneContainer

/**
 * This window as a [TitleBarWindow], for a [TabbedTitleBar] that stands in for its title bar.
 *
 * On Windows the system's caption comes off and the bar takes its place. The window keeps its frame, so it still
 * resizes from its sides and bottom, snaps to the screen's edges and halves, and casts its shadow; the bar moves it,
 * maximises it on a double-click and resizes it from its top edge. Elsewhere, or with the `compose.nativeTitleBar`
 * system property set, the window keeps its own title bar and this is null, so the bar sits under it as a tab bar.
 *
 * [onCloseRequest] is what the bar's close button does, as the window's own would. [background], when given, fills the
 * window under its content, which at some display scales falls a pixel short of the window's edges and would show
 * AWT's white there: pass the bar's or the content's colour.
 */
@Composable
fun FrameWindowScope.rememberTitleBarWindow(
    state: WindowState,
    onCloseRequest: () -> Unit,
    background: Color? = null,
): TitleBarWindow? {
    val awtWindow = window
    val close by rememberUpdatedState(onCloseRequest)
    var installed by remember { mutableStateOf(false) }
    DisposableEffect(awtWindow) {
        fun install() {
            installed = CustomTitleBar.install(awtWindow)
        }
        val opened =
            object : WindowAdapter() {
                override fun windowOpened(event: WindowEvent) = install()
            }
        if (awtWindow.isDisplayable) install() else awtWindow.addWindowListener(opened)
        onDispose {
            awtWindow.removeWindowListener(opened)
            CustomTitleBar.remove(awtWindow)
            installed = false
        }
    }
    DisposableEffect(awtWindow, background) {
        if (background != null) fill(awtWindow, java.awt.Color(background.toArgb()))
        onDispose { }
    }
    if (!installed) return null
    return remember(awtWindow, state) { AwtTitleBarWindow(awtWindow, state) { close() } }
}

/** Gives [window], and the panes under its content, [color]. */
private fun fill(window: Window, color: java.awt.Color) {
    window.background = color
    (window as? RootPaneContainer)?.let { panes ->
        panes.rootPane.background = color
        panes.layeredPane.background = color
        panes.contentPane.background = color
    }
}

private class AwtTitleBarWindow(
    private val window: Window,
    private val state: WindowState,
    private val onClose: () -> Unit,
) : TitleBarWindow {
    override val isMaximized: Boolean get() = state.placement == WindowPlacement.Maximized

    override fun minimize() {
        state.isMinimized = true
    }

    override fun toggleMaximized() {
        state.placement = if (isMaximized) WindowPlacement.Floating else WindowPlacement.Maximized
    }

    override fun close() = onClose()

    override fun startMove() = CustomTitleBar.drag(window, CustomTitleBar.HTCAPTION)

    /** Its top edge resizes the window, as the caption's did, but for when it fills the screen. */
    @Composable
    override fun TopResizeEdge() {
        if (isMaximized) return
        Row(Modifier.fillMaxWidth().height(RESIZE_EDGE)) {
            Edge(CustomTitleBar.HTTOPLEFT, Cursor.NW_RESIZE_CURSOR, Modifier.width(RESIZE_CORNER).fillMaxHeight())
            Edge(CustomTitleBar.HTTOP, Cursor.N_RESIZE_CURSOR, Modifier.weight(1f).fillMaxHeight())
            Edge(CustomTitleBar.HTTOPRIGHT, Cursor.NE_RESIZE_CURSOR, Modifier.width(RESIZE_CORNER).fillMaxHeight())
        }
    }

    @Composable
    private fun Edge(hit: Int, cursor: Int, modifier: Modifier) {
        Box(
            modifier.pointerHoverIcon(PointerIcon(Cursor(cursor))).pointerInput(hit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    CustomTitleBar.drag(window, hit)
                }
            },
        )
    }
}

private val RESIZE_EDGE = 4.dp
private val RESIZE_CORNER = 8.dp

/**
 * Takes a window's caption off on Windows, leaving its frame, so its content runs to its top; then moves or resizes it
 * from there through the system's own move, as a press on the caption did. Windows only: the window's procedure is
 * wrapped, so the system measures the frame without the caption, and starts a move or resize when asked from the
 * window's own thread.
 */
internal object CustomTitleBar {
    const val HTCAPTION = 2
    const val HTTOP = 12
    const val HTTOPLEFT = 13
    const val HTTOPRIGHT = 14

    private const val GWLP_WNDPROC = -4
    private const val WM_NCCALCSIZE = 0x0083
    private const val WM_NCLBUTTONDOWN = 0x00A1
    private const val WM_APP = 0x8000

    /** Asks the window's own thread to start the system's move or resize from a hit-test code, in its wParam. */
    private const val DRAG = WM_APP + 0x31

    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_FRAMECHANGED = 0x0020
    private const val LOW_WORD = 0xFFFFL
    private const val WORD_BITS = 16

    /** What JNA's User32 leaves out. */
    @Suppress("FunctionName")
    private interface MoreUser32 : StdCallLibrary {
        fun IsZoomed(hwnd: WinDef.HWND): Boolean

        fun ReleaseCapture(): Boolean
    }

    private val user32: User32? by lazy { runCatching { User32.INSTANCE }.getOrNull() }
    private val more: MoreUser32? by lazy {
        runCatching { Native.load("user32", MoreUser32::class.java, W32APIOptions.DEFAULT_OPTIONS) }.getOrNull()
    }

    /** A window given a title bar of its own: its handle, the procedure it had, and the one put first. */
    private class Installed(val hwnd: WinDef.HWND, val previous: Pointer, val procedure: WinUser.WindowProc)

    private val installed = ConcurrentHashMap<Window, Installed>()

    /** Whether this system's caption can come off for the bar to stand in its place. */
    val supported: Boolean
        get() = System.getProperty("os.name").orEmpty().startsWith("Windows") && System.getProperty("compose.nativeTitleBar") == null

    /** Takes [window]'s caption off: true once it has, false where it can't, the window keeping its own title bar. */
    fun install(window: Window): Boolean {
        if (!supported || !window.isDisplayable) return false
        if (installed.containsKey(window)) return true
        return runCatching { wrap(window) }.getOrDefault(false)
    }

    private fun wrap(window: Window): Boolean {
        val user32 = user32 ?: return false
        if (more == null) return false
        val hwnd = Native.getWindowPointer(window)?.let(WinDef::HWND) ?: return false
        val previous = user32.GetWindowLongPtr(hwnd, GWLP_WNDPROC).toPointer() ?: return false
        val procedure =
            object : WinUser.WindowProc {
                override fun callback(
                    hwnd: WinDef.HWND,
                    message: Int,
                    wParam: WinDef.WPARAM,
                    lParam: WinDef.LPARAM,
                ): WinDef.LRESULT = windowProc(hwnd, message, wParam, lParam, previous)
            }
        installed[window] = Installed(hwnd, previous, procedure)
        user32.SetWindowLongPtr(hwnd, GWLP_WNDPROC, CallbackReference.getFunctionPointer(procedure))
        // The frame measured again, without the caption.
        user32.SetWindowPos(hwnd, null, 0, 0, 0, 0, SWP_FRAMECHANGED or SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE)
        return true
    }

    /** Gives [window] its system title bar back, as it closes. */
    fun remove(window: Window) {
        val wrapped = installed.remove(window) ?: return
        runCatching { user32?.SetWindowLongPtr(wrapped.hwnd, GWLP_WNDPROC, wrapped.previous) }
    }

    /** Starts the system's move of [window] from the pointer ([HTCAPTION]), or a resize from one of its top edges. */
    fun drag(window: Window, hit: Int) {
        val wrapped = installed[window] ?: return
        runCatching { user32?.PostMessage(wrapped.hwnd, DRAG, WinDef.WPARAM(hit.toLong()), WinDef.LPARAM(0)) }
    }

    private fun windowProc(
        hwnd: WinDef.HWND,
        message: Int,
        wParam: WinDef.WPARAM,
        lParam: WinDef.LPARAM,
        previous: Pointer,
    ): WinDef.LRESULT {
        val user32 = user32!!
        when {
            message == WM_NCCALCSIZE && wParam.toLong() != 0L -> {
                // The frame as the system measures it, but for the caption: the client area runs to the window's top,
                // and when maximised, which puts the frame past the screen's edges, from just inside it.
                val rect = Pointer(lParam.toLong())
                val left = rect.getInt(0)
                val top = rect.getInt(4)
                val result = user32.CallWindowProc(previous, hwnd, message, wParam, lParam)
                val frame = rect.getInt(0) - left
                rect.setInt(4, top + if (more?.IsZoomed(hwnd) == true) frame else 0)
                return result
            }
            message == DRAG -> {
                more?.ReleaseCapture()
                user32.DefWindowProc(hwnd, WM_NCLBUTTONDOWN, wParam, WinDef.LPARAM(cursor(user32)))
                return WinDef.LRESULT(0)
            }
        }
        return user32.CallWindowProc(previous, hwnd, message, wParam, lParam)
    }

    /** Where the pointer is on the screen, packed as a mouse message's lParam. */
    private fun cursor(user32: User32): Long {
        val point = WinDef.POINT()
        user32.GetCursorPos(point)
        return ((point.y.toLong() and LOW_WORD) shl WORD_BITS) or (point.x.toLong() and LOW_WORD)
    }
}
