package com.isycat.compose.ui

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.io.File

/**
 * The Windows taskbar groups windows, pins and shortcuts by their AppUserModelID (AUMID). A JVM app started by a
 * wrapper exe (Launch4j) runs in javaw.exe, so without an explicit ID its window doesn't group with the exe's pin.
 *
 * Give each app one ID, as `Company.Product`, and use it in [setForProcess], [setForWindow] and on every installer
 * shortcut. Every call does nothing off Windows; JNA is only touched on Windows.
 */
object WindowsAppId {
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    /**
     * Sets this process's AUMID. Call it first in main(), before any window exists: windows made earlier keep the old
     * one. Returns true when it was set.
     */
    fun setForProcess(id: String): Boolean = isWindows && runCatching { Win32.setForProcess(id) }.getOrDefault(false)

    /**
     * Sets [window]'s AUMID and what a pin made from it runs: [executable] with the icon at [iconIndex] in it, named
     * [displayName]. Without these a pin made from the window would start javaw.exe. A window not shown yet gets them
     * as it opens.
     */
    fun setForWindow(window: java.awt.Window, id: String, executable: File, displayName: String, iconIndex: Int = 0) {
        if (!isWindows) return
        if (!window.isDisplayable) {
            window.addWindowListener(
                object : java.awt.event.WindowAdapter() {
                    override fun windowOpened(event: java.awt.event.WindowEvent) {
                        window.removeWindowListener(this)
                        setForWindow(window, id, executable, displayName, iconIndex)
                    }
                }
            )
            return
        }
        runCatching { Win32.setForWindow(window, id, executable, displayName, iconIndex) }
    }

    /** The JNA calls, in their own class so it's only loaded on Windows. */
    private object Win32 {
        // The System.AppUserModel property keys' format ID; the property IDs are below.
        private const val APP_USER_MODEL_FMTID = "{9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3}"
        private const val PID_RELAUNCH_COMMAND = 2
        private const val PID_RELAUNCH_ICON_RESOURCE = 3
        private const val PID_RELAUNCH_DISPLAY_NAME_RESOURCE = 4
        private const val PID_ID = 5
        private const val IID_IPROPERTYSTORE = "{886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99}"
        private const val VT_LPWSTR: Short = 31

        // Vtable slots: IUnknown's Release, then IPropertyStore's SetValue and Commit.
        private const val RELEASE = 2
        private const val SET_VALUE = 6
        private const val COMMIT = 7

        // PROPVARIANT: the type tag, three reserved words, then the value; 16 bytes on 32-bit, 24 on 64-bit.
        private val propVariantSize = 8 + 2 * Native.POINTER_SIZE

        @Suppress("FunctionName")
        private interface Shell32 : StdCallLibrary {
            fun SetCurrentProcessExplicitAppUserModelID(appId: WString): WinNT.HRESULT

            fun SHGetPropertyStoreForWindow(hwnd: WinDef.HWND, riid: Guid.REFIID, store: PointerByReference): WinNT.HRESULT

            companion object {
                val INSTANCE: Shell32 = Native.load("shell32", Shell32::class.java, W32APIOptions.DEFAULT_OPTIONS)
            }
        }

        fun setForProcess(id: String): Boolean =
            Shell32.INSTANCE.SetCurrentProcessExplicitAppUserModelID(WString(id)).toInt() == WinError.S_OK.toInt()

        fun setForWindow(window: java.awt.Window, id: String, executable: File, displayName: String, iconIndex: Int) {
            val hwnd = WinDef.HWND(Native.getWindowPointer(window) ?: return)
            val init = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED).toInt()
            try {
                val ref = PointerByReference()
                val iid = Guid.REFIID(Guid.IID(IID_IPROPERTYSTORE))
                if (Shell32.INSTANCE.SHGetPropertyStoreForWindow(hwnd, iid, ref).toInt() != WinError.S_OK.toInt()) return
                val store = ref.value
                try {
                    // The relaunch details first: Windows only reads them for a window with its own ID.
                    set(store, PID_RELAUNCH_COMMAND, "\"${executable.absolutePath}\"")
                    set(store, PID_RELAUNCH_ICON_RESOURCE, "${executable.absolutePath},$iconIndex")
                    set(store, PID_RELAUNCH_DISPLAY_NAME_RESOURCE, displayName)
                    set(store, PID_ID, id)
                    call(store, COMMIT)
                } finally {
                    call(store, RELEASE)
                }
            } finally {
                // S_OK and S_FALSE each need a matching uninit; a thread already in another mode (RPC_E_CHANGED_MODE) doesn't.
                if (init == WinError.S_OK.toInt() || init == WinError.S_FALSE.toInt()) Ole32.INSTANCE.CoUninitialize()
            }
        }

        private fun set(store: Pointer, pid: Int, value: String) {
            val key = Memory(20).apply {
                write(0, Guid.GUID.fromString(APP_USER_MODEL_FMTID).toByteArray(), 0, 16)
                setInt(16, pid)
            }
            val text = Memory((value.length + 1L) * Native.WCHAR_SIZE).apply { setWideString(0, value) }
            val variant = Memory(propVariantSize.toLong()).apply {
                clear()
                setShort(0, VT_LPWSTR)
                setPointer(8, text)
            }
            call(store, SET_VALUE, key, variant)
        }

        /** Calls the COM method in [slot] of [obj]'s vtable. */
        private fun call(obj: Pointer, slot: Int, vararg args: Any): Int {
            val method = obj.getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE)
            return Function.getFunction(method, Function.ALT_CONVENTION).invokeInt(arrayOf(obj, *args))
        }
    }
}
