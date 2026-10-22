package com.isycat.compose.ui

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.User32

object WindowsTitleBar {
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20

    @Suppress("FunctionName")
    private interface Dwmapi : Library {
        fun DwmSetWindowAttribute(
            hwnd: WinDef.HWND,
            attr: Int,
            attrVal: IntArray,
            attrSize: Int
        ): Int
    }

    private val dwmapi: Dwmapi by lazy {
        Native.load("dwmapi", Dwmapi::class.java)
    }

    fun setDarkMode(windowTitle: String, enabled: Boolean) {
        val osName = System.getProperty("os.name") ?: return
        if (!osName.startsWith("Windows")) return

        try {
            val hwnd = User32.INSTANCE.FindWindow(null, windowTitle)
            if (hwnd == null || hwnd.pointer == Pointer.NULL) return

            val value = if (enabled) 1 else 0
            dwmapi.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, intArrayOf(value), Int.SIZE_BYTES)
        } catch (_: Throwable) {
            // Best-effort only.
        }
    }
}
