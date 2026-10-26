package com.isycat.compose.ui

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.win32.StdCallLibrary
import java.io.File

/**
 * Registers a file type with Windows for the current user (no admin rights needed), so its files show the app's icon,
 * say what they are, and open in the app on a double-click. Only what's missing or wrong is written, so [ensure] is
 * cheap enough to run on every start; it reads and writes the registry, so call it off the UI thread.
 *
 * A choice the user made in "Open with" stays theirs: Windows keeps that separately, and it isn't touched.
 */
object WindowsFileAssociation {
    /**
     * Files ending `.[extension]` are a [description] (e.g. "cardSTUDIO project"), opened by [executable] with the file
     * as its only argument and shown with the icon at [iconIndex] in it. [progId] names the type in the registry, as
     * `Vendor.App.Type`.
     */
    data class Association(
        val extension: String,
        val progId: String,
        val description: String,
        val executable: File,
        val iconIndex: Int = 0
    )

    private const val CLASSES = "Software\\Classes"
    private const val SHCNE_ASSOCCHANGED = 0x08000000
    private const val SHCNF_IDLIST = 0

    @Suppress("FunctionName")
    private interface Shell32Notify : StdCallLibrary {
        fun SHChangeNotify(eventId: Int, flags: Int, item1: Pointer?, item2: Pointer?)

        companion object {
            val INSTANCE: Shell32Notify = Native.load("shell32", Shell32Notify::class.java)
        }
    }

    /** The registry values [association] needs, as (key under HKCU, value name, data); "" is a key's default value. */
    fun entries(association: Association): List<Triple<String, String, String>> {
        val exe = association.executable.absolutePath
        val ext = ".${association.extension.removePrefix(".")}"
        val type = "$CLASSES\\${association.progId}"
        return listOf(
            Triple("$CLASSES\\$ext", "", association.progId),
            Triple("$CLASSES\\$ext\\OpenWithProgids", association.progId, ""),
            Triple(type, "", association.description),
            Triple("$type\\DefaultIcon", "", "\"$exe\",${association.iconIndex}"),
            Triple("$type\\shell\\open\\command", "", "\"$exe\" \"%1\"")
        )
    }

    /**
     * Makes the registry match [association], then tells Explorer so icons refresh. Returns true when anything had to
     * change. Does nothing (and returns false) off Windows.
     */
    fun ensure(association: Association): Boolean {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return false
        val root = WinReg.HKEY_CURRENT_USER
        val stale = entries(association).filter { (key, name, data) ->
            !Advapi32Util.registryKeyExists(root, key) ||
                !Advapi32Util.registryValueExists(root, key, name) ||
                runCatching { Advapi32Util.registryGetStringValue(root, key, name) }.getOrNull() != data
        }
        if (stale.isEmpty()) return false
        stale.forEach { (key, name, data) ->
            if (!Advapi32Util.registryKeyExists(root, key)) Advapi32Util.registryCreateKey(root, key)
            Advapi32Util.registrySetStringValue(root, key, name, data)
        }
        Shell32Notify.INSTANCE.SHChangeNotify(SHCNE_ASSOCCHANGED, SHCNF_IDLIST, null, null)
        return true
    }
}
