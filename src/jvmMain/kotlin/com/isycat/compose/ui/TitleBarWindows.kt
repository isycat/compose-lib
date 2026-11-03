package com.isycat.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.ConcurrentHashMap
import javax.swing.RootPaneContainer

/**
 * This window as a [TitleBarWindow], for a [TabbedTitleBar] that stands in for its title bar.
 *
 * On Windows the system's caption comes off and the bar takes its place. The window keeps its frame, so it still
 * resizes from its sides and bottom, snaps to the screen's edges and halves, and casts its shadow. The system treats the
 * bar's empty part as the caption, so it moves the window, maximises or restores it on a double-click and shows the
 * window menu on a right-click; the bar's top edge resizes it. Elsewhere, or with the `compose.nativeTitleBar`
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

/**
 * Gives [window], and the panes under its content, [color]. Compose's own pane is see-through, so until its first
 * frame, and as it resizes, what showed through was AWT's white: opaque in [color], it shows that instead.
 */
private fun fill(window: Window, color: java.awt.Color) {
    window.background = color
    (window as? RootPaneContainer)?.let { panes ->
        panes.rootPane.background = color
        panes.layeredPane.background = color
        panes.contentPane.background = color
        panes.contentPane.components.filterIsInstance<javax.swing.JLayeredPane>().forEach { pane ->
            pane.isOpaque = true
            pane.background = color
        }
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

    override val handlesCaption: Boolean get() = true

    override fun captionAt(area: Rect?) = CustomTitleBar.captionAt(window, area)

    override fun clickableAt(key: Any, area: Rect?) = CustomTitleBar.clickableAt(window, key, area)
}

/**
 * What a point in a window with the bar as its caption is to the system, as WM_NCHITTEST answers: a resize edge along the
 * top ([edge] deep, its corners [corner] wide) unless [maximized], the caption ([CustomTitleBar.HTCAPTION]) on the bar
 * ([caption]) away from its tabs and buttons ([clickable]), or null for the window's own content. All in the client
 * area's pixels, [width] wide.
 */
internal fun captionHit(
    x: Float,
    y: Float,
    width: Float,
    caption: Rect?,
    clickable: Collection<Rect>,
    maximized: Boolean,
    edge: Float,
    corner: Float,
): Int? {
    if (!maximized && y in 0f..<edge) {
        return when {
            x < corner -> CustomTitleBar.HTTOPLEFT
            x >= width - corner -> CustomTitleBar.HTTOPRIGHT
            else -> CustomTitleBar.HTTOP
        }
    }
    val point = Offset(x, y)
    if (caption == null || !caption.contains(point)) return null
    return if (clickable.any { it.contains(point) }) null else CustomTitleBar.HTCAPTION
}

private const val RESIZE_EDGE_DP = 4f
private const val RESIZE_CORNER_DP = 8f

/**
 * Takes a window's caption off on Windows, leaving its frame, so its content runs to its top, and has the system treat
 * the bar as the caption. Windows only: the window's procedure is wrapped, so the system measures the frame without the
 * caption, and its hit test answers the caption on the bar's empty part and resize edges along its top. The system then
 * moves, snaps, maximises and restores the window from there itself.
 */
internal object CustomTitleBar {
    const val HTCLIENT = 1
    const val HTCAPTION = 2
    const val HTTOP = 12
    const val HTTOPLEFT = 13
    const val HTTOPRIGHT = 14

    private const val GWLP_WNDPROC = -4
    private const val WM_NCCALCSIZE = 0x0083
    private const val WM_NCHITTEST = 0x0084
    private const val DEFAULT_DPI = 96f

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

        fun ScreenToClient(hwnd: WinDef.HWND, point: WinDef.POINT): Boolean

        fun GetDpiForWindow(hwnd: WinDef.HWND): Int
    }

    private val user32: User32? by lazy { runCatching { User32.INSTANCE }.getOrNull() }
    private val more: MoreUser32? by lazy {
        runCatching { Native.load("user32", MoreUser32::class.java, W32APIOptions.DEFAULT_OPTIONS) }.getOrNull()
    }

    /**
     * A window given a title bar of its own: its handle, the procedure it had and the one put first, and where the bar
     * and its tabs and buttons are, as the bar reports them from its thread for the window's procedure to read.
     */
    private class Installed(val hwnd: WinDef.HWND, val previous: Pointer) {
        lateinit var procedure: WinUser.WindowProc

        @Volatile var caption: Rect? = null
        val clickable = ConcurrentHashMap<Any, Rect>()
    }

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
        val wrapped = Installed(hwnd, previous)
        wrapped.procedure =
            object : WinUser.WindowProc {
                override fun callback(
                    hwnd: WinDef.HWND,
                    message: Int,
                    wParam: WinDef.WPARAM,
                    lParam: WinDef.LPARAM,
                ): WinDef.LRESULT = windowProc(hwnd, message, wParam, lParam, wrapped)
            }
        installed[window] = wrapped
        user32.SetWindowLongPtr(hwnd, GWLP_WNDPROC, CallbackReference.getFunctionPointer(wrapped.procedure))
        // The frame measured again, without the caption.
        user32.SetWindowPos(hwnd, null, 0, 0, 0, 0, SWP_FRAMECHANGED or SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE)
        return true
    }

    /** Gives [window] its system title bar back, as it closes. */
    fun remove(window: Window) {
        val wrapped = installed.remove(window) ?: return
        runCatching { user32?.SetWindowLongPtr(wrapped.hwnd, GWLP_WNDPROC, wrapped.previous) }
    }

    /** Where [window]'s bar is, in its client area's pixels; null when it's gone. */
    fun captionAt(window: Window, area: Rect?) {
        installed[window]?.caption = area
    }

    /** Where one of [window]'s bar's tabs or buttons, [key], is; null when it's gone. */
    fun clickableAt(window: Window, key: Any, area: Rect?) {
        val clickable = installed[window]?.clickable ?: return
        if (area == null) clickable.remove(key) else clickable[key] = area
    }

    private fun windowProc(
        hwnd: WinDef.HWND,
        message: Int,
        wParam: WinDef.WPARAM,
        lParam: WinDef.LPARAM,
        wrapped: Installed,
    ): WinDef.LRESULT {
        val user32 = user32!!
        val previous = wrapped.previous
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
            message == WM_NCHITTEST -> {
                // The frame's own sides and bottom stay the system's; within the client area the bar decides.
                val result = user32.CallWindowProc(previous, hwnd, message, wParam, lParam)
                if (result.toInt() != HTCLIENT) return result
                return WinDef.LRESULT((hit(hwnd, lParam, wrapped) ?: HTCLIENT).toLong())
            }
        }
        return user32.CallWindowProc(previous, hwnd, message, wParam, lParam)
    }

    /** What the point in a WM_NCHITTEST's [lParam] (screen pixels) is on [hwnd]'s bar, by [captionHit]. */
    private fun hit(hwnd: WinDef.HWND, lParam: WinDef.LPARAM, wrapped: Installed): Int? {
        val more = more ?: return null
        val user32 = user32 ?: return null
        val packed = lParam.toLong()
        // Signed: on a screen left of or above the main one, the coordinates are negative.
        val point = WinDef.POINT((packed and LOW_WORD).toInt().toShort().toInt(), ((packed shr WORD_BITS) and LOW_WORD).toInt().toShort().toInt())
        if (!more.ScreenToClient(hwnd, point)) return null
        val client = WinDef.RECT()
        user32.GetClientRect(hwnd, client)
        val scale = runCatching { more.GetDpiForWindow(hwnd) }.getOrDefault(0).takeIf { it > 0 }?.div(DEFAULT_DPI) ?: 1f
        return captionHit(
            point.x.toFloat(),
            point.y.toFloat(),
            (client.right - client.left).toFloat(),
            wrapped.caption,
            wrapped.clickable.values,
            more.IsZoomed(hwnd),
            RESIZE_EDGE_DP * scale,
            RESIZE_CORNER_DP * scale,
        )
    }
}
