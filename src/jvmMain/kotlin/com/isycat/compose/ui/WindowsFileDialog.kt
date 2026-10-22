package com.isycat.compose.ui

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.io.File

/**
 * The Windows Explorer file dialogs (`IFileOpenDialog` / `IFileSaveDialog`), called through their COM vtables.
 * Every call must happen on one thread that has entered a single-threaded apartment ([enterApartment]); the dialog runs
 * its own message loop there while it's open.
 */
internal object WindowsFileDialog {
    private val CLSID_FILE_OPEN_DIALOG = Guid.GUID("{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}")
    private val CLSID_FILE_SAVE_DIALOG = Guid.GUID("{C0B4E2F3-BA21-4773-8DBA-335EC946EB8B}")
    private val IID_FILE_OPEN_DIALOG = Guid.GUID("{D57C7288-D4AD-4768-BE02-9D969532D960}")
    private val IID_FILE_SAVE_DIALOG = Guid.GUID("{84BCCD23-5FDE-4CDB-AEA4-AF64B83D78AB}")
    private val IID_SHELL_ITEM = Guid.GUID("{43826D1E-E718-42EE-BC55-A1E261C37BFE}")

    private const val CLSCTX_INPROC_SERVER = 0x1
    private const val COINIT_APARTMENTTHREADED = 0x2
    private const val COINIT_DISABLE_OLE1DDE = 0x4
    private const val RPC_E_CHANGED_MODE = 0x80010106.toInt()
    private const val SIGDN_FILESYSPATH = 0x80058000.toInt()

    /** What `Show` returns when the user closes the dialog without choosing. */
    const val CANCELLED = 0x800704C7.toInt()

    const val FOS_OVERWRITEPROMPT = 0x2
    const val FOS_NOCHANGEDIR = 0x8
    const val FOS_PICKFOLDERS = 0x20
    const val FOS_FORCEFILESYSTEM = 0x40
    const val FOS_ALLOWMULTISELECT = 0x200
    const val FOS_PATHMUSTEXIST = 0x800
    const val FOS_FILEMUSTEXIST = 0x1000

    @Suppress("FunctionName")
    private interface Shell32Items : StdCallLibrary {
        fun SHCreateItemFromParsingName(path: WString, bindContext: Pointer?, riid: Guid.GUID, item: PointerByReference): Int

        companion object {
            val INSTANCE: Shell32Items = Native.load("shell32", Shell32Items::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }

    /** Joins this thread to a single-threaded apartment, as the dialogs need. Safe to call more than once. */
    fun enterApartment() {
        val hr = Ole32.INSTANCE.CoInitializeEx(null, COINIT_APARTMENTTHREADED or COINIT_DISABLE_OLE1DDE).toInt()
        check(hr >= 0 || hr == RPC_E_CHANGED_MODE) { "CoInitializeEx failed: ${hex(hr)}" }
    }

    fun create(save: Boolean): Dialog {
        val ref = PointerByReference()
        val hr = Ole32.INSTANCE.CoCreateInstance(
            if (save) CLSID_FILE_SAVE_DIALOG else CLSID_FILE_OPEN_DIALOG,
            null,
            CLSCTX_INPROC_SERVER,
            if (save) IID_FILE_SAVE_DIALOG else IID_FILE_OPEN_DIALOG,
            ref
        ).toInt()
        check(hr >= 0) { "Couldn't create the file dialog: ${hex(hr)}" }
        return Dialog(ComObject(ref.value))
    }

    /** The shell's item for [file], or null when it doesn't exist. */
    fun shellItem(file: File): ComObject? {
        val ref = PointerByReference()
        val hr = Shell32Items.INSTANCE.SHCreateItemFromParsingName(WString(file.absolutePath), null, IID_SHELL_ITEM, ref)
        return if (hr >= 0) ComObject(ref.value) else null
    }

    /** `IFileDialog`, with `IFileOpenDialog.GetResults` for multi-select. Slots are vtable indices. */
    class Dialog(private val com: ComObject) {
        // Strings and the filter table must outlive the calls that receive them.
        private val keepAlive = mutableListOf<Memory>()

        fun show(owner: Pointer?): Int = com.call(3, owner)

        /** Sets the type list from (label, `*.png;*.jpg`) pairs and selects the first. */
        fun setFileTypes(types: List<Pair<String, String>>) {
            if (types.isEmpty()) return
            val table = Memory(Native.POINTER_SIZE.toLong() * 2 * types.size)
            types.flatMap { listOf(it.first, it.second) }.forEachIndexed { i, text ->
                table.setPointer(Native.POINTER_SIZE.toLong() * i, wide(text))
            }
            keepAlive += table
            com.check(4, "SetFileTypes", types.size, table)
            com.check(5, "SetFileTypeIndex", 1)
        }

        var options: Int
            get() = IntByReference().also { com.check(10, "GetOptions", it) }.value
            set(value) = com.check(9, "SetOptions", value)

        fun setFolder(folder: File) {
            val item = shellItem(folder) ?: return
            try {
                com.check(12, "SetFolder", item.pointer)
            } finally {
                item.release()
            }
        }

        /** The folder the dialog is showing, as a path. */
        fun folder(): File? {
            val ref = PointerByReference()
            if (com.call(13, ref) < 0 || ref.value == null) return null
            return ComObject(ref.value).use { it.path() }
        }

        var fileName: String
            get() {
                val ref = PointerByReference()
                com.check(16, "GetFileName", ref)
                return takeString(ref.value)
            }
            set(value) = com.check(15, "SetFileName", wide(value))

        fun setTitle(title: String) = com.check(17, "SetTitle", wide(title))

        fun setOkLabel(label: String) = com.check(18, "SetOkButtonLabel", wide(label))

        /** The chosen file or folder after [show] returned success. */
        fun result(): File? {
            val ref = PointerByReference()
            com.check(20, "GetResult", ref)
            return ComObject(ref.value).use { it.path() }
        }

        fun setDefaultExtension(extension: String) = com.check(22, "SetDefaultExtension", wide(extension))

        /** Every chosen file after [show] returned success; open dialogs only. */
        fun results(): List<File> {
            val ref = PointerByReference()
            com.check(27, "GetResults", ref)
            return ComObject(ref.value).use { array ->
                val count = IntByReference().also { array.check(7, "GetCount", it) }.value
                (0 until count).mapNotNull { i ->
                    val item = PointerByReference()
                    array.check(8, "GetItemAt", i, item)
                    ComObject(item.value).use { it.path() }
                }
            }
        }

        fun release() = com.release()

        private fun wide(text: String) = Memory((text.length + 1L) * Native.WCHAR_SIZE).also {
            it.setWideString(0, text)
            keepAlive += it
        }
    }

    /** A COM interface pointer: slot 2 of every vtable is `Release`. */
    class ComObject(val pointer: Pointer) {
        fun call(slot: Int, vararg args: Any?): Int {
            val function = pointer.getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE)
            return Function.getFunction(function, Function.ALT_CONVENTION).invokeInt(arrayOf<Any?>(pointer, *args))
        }

        fun check(slot: Int, name: String, vararg args: Any?) {
            val hr = call(slot, *args)
            check(hr >= 0) { "$name failed: ${hex(hr)}" }
        }

        /** `IShellItem.GetDisplayName(SIGDN_FILESYSPATH)`: null for items that aren't files, like "This PC". */
        fun path(): File? {
            val ref = PointerByReference()
            if (call(5, SIGDN_FILESYSPATH, ref) < 0) return null
            return File(takeString(ref.value))
        }

        fun release() {
            call(2)
        }

        inline fun <T> use(block: (ComObject) -> T): T = try {
            block(this)
        } finally {
            release()
        }
    }

    /** Reads a string the shell allocated for us, and frees it. */
    private fun takeString(pointer: Pointer?): String {
        if (pointer == null) return ""
        return try {
            pointer.getWideString(0)
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(pointer)
        }
    }

    private fun hex(hr: Int) = "0x%08X".format(hr)
}
