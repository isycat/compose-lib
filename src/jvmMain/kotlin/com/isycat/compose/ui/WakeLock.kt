package com.isycat.compose.ui

import com.sun.jna.Library
import com.sun.jna.Native

/**
 * Keeps the machine and display awake while held, via Win32 `SetThreadExecutionState`. Intended to
 * be acquired while a long-running passive window (e.g. PokéHALP! TV showing a capture feed) is open,
 * since a video feed with no keyboard/mouse input doesn't reset Windows' idle-sleep timer.
 *
 * No-op off Windows, and best-effort: any failure (library missing, etc.) is swallowed. The request
 * is `ES_CONTINUOUS`, so it stays in effect until [release] rather than needing a heartbeat. It is
 * also cleared automatically if the process dies.
 */
object WakeLock {
    // Win32 EXECUTION_STATE flags (winbase.h). ES_CONTINUOUS doesn't fit in a signed Int literal.
    private val ES_CONTINUOUS = 0x80000000.toInt()
    private const val ES_SYSTEM_REQUIRED = 0x00000001
    private const val ES_DISPLAY_REQUIRED = 0x00000002

    @Suppress("FunctionName")
    private interface Kernel32 : Library {
        fun SetThreadExecutionState(esFlags: Int): Int
    }

    private val kernel32: Kernel32? by lazy {
        if (System.getProperty("os.name")?.startsWith("Windows") != true) null
        else runCatching { Native.load("kernel32", Kernel32::class.java) }.getOrNull()
    }

    /** Prevent system + display sleep until [release]. Safe to call repeatedly. */
    fun acquire() {
        runCatching { kernel32?.SetThreadExecutionState(ES_CONTINUOUS or ES_SYSTEM_REQUIRED or ES_DISPLAY_REQUIRED) }
    }

    /** Allow the machine to sleep again (clears the continuous requirement). */
    fun release() {
        runCatching { kernel32?.SetThreadExecutionState(ES_CONTINUOUS) }
    }
}
