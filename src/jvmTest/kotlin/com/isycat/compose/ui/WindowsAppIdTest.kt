package com.isycat.compose.ui

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import javax.swing.JFrame
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sets the IDs and reads them back from Windows. The window is made but never shown. Windows only. */
class WindowsAppIdTest {
    @Suppress("FunctionName")
    private interface Shell32 : StdCallLibrary {
        fun GetCurrentProcessExplicitAppUserModelID(appId: PointerByReference): WinNT.HRESULT

        fun SHGetPropertyStoreForWindow(hwnd: WinDef.HWND, riid: Guid.REFIID, store: PointerByReference): WinNT.HRESULT

        companion object {
            val INSTANCE: Shell32 = Native.load("shell32", Shell32::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }

    @BeforeTest
    fun windowsOnly() {
        assumeTrue(System.getProperty("os.name").orEmpty().startsWith("Windows"), "AppUserModelIDs are Windows only")
    }

    @Test
    fun processIdIsSet() {
        assertTrue(WindowsAppId.setForProcess("Isycat.ComposeLibTest"))
        val out = PointerByReference()
        assertEquals(0, Shell32.INSTANCE.GetCurrentProcessExplicitAppUserModelID(out).toInt())
        assertEquals("Isycat.ComposeLibTest", out.value.getWideString(0))
        Ole32.INSTANCE.CoTaskMemFree(out.value)
    }

    @Test
    fun windowIdAndRelaunchCommandAreSet() {
        val frame = JFrame()
        try {
            frame.pack() // a native window, not shown
            val exe = File("C:\\Apps\\Test App\\test.exe")
            WindowsAppId.setForWindow(frame, "Isycat.ComposeLibTest", exe, "Test App")
            val hwnd = WinDef.HWND(Native.getWindowPointer(frame))
            val ref = PointerByReference()
            val iid = Guid.REFIID(Guid.IID("{886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99}"))
            assertEquals(0, Shell32.INSTANCE.SHGetPropertyStoreForWindow(hwnd, iid, ref).toInt())
            val store = ref.value
            try {
                assertEquals("Isycat.ComposeLibTest", read(store, 5))
                assertEquals("\"${exe.absolutePath}\"", read(store, 2))
                assertEquals("${exe.absolutePath},0", read(store, 3))
                assertEquals("Test App", read(store, 4))
            } finally {
                call(store, 2)
            }
        } finally {
            frame.dispose()
        }
    }

    /** The System.AppUserModel string property [pid], through IPropertyStore::GetValue. */
    private fun read(store: Pointer, pid: Int): String? {
        val key = Memory(20).apply {
            write(0, Guid.GUID.fromString("{9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3}").toByteArray(), 0, 16)
            setInt(16, pid)
        }
        val variant = Memory(8L + 2 * Native.POINTER_SIZE).apply { clear() }
        // The window's store returns S_FALSE (1) for a value it holds, so check for success, not S_OK.
        assertTrue(call(store, 5, key, variant) >= 0)
        return if (variant.getShort(0).toInt() == 31) variant.getPointer(8).getWideString(0) else null
    }

    private fun call(obj: Pointer, slot: Int, vararg args: Any): Int {
        val method = obj.getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE)
        return Function.getFunction(method, Function.ALT_CONVENTION).invokeInt(arrayOf(obj, *args))
    }
}
