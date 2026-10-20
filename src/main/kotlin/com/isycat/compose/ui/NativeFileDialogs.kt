package com.isycat.compose.ui

import com.sun.jna.Native
import com.sun.jna.Pointer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.Dialog
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Window
import java.io.File
import java.util.concurrent.Executors
import javax.swing.JFileChooser
import javax.swing.UIManager

/** An entry in a file dialog's type list: what it's called ("Images") and the extensions it shows, without dots. */
data class FileTypeFilter(val name: String, val extensions: List<String>) {
    constructor(name: String, vararg extensions: String) : this(name, extensions.toList())

    fun accepts(file: File): Boolean = extensions.any { file.extension.equals(it, ignoreCase = true) }

    internal val pattern: String get() = extensions.joinToString(";") { "*.$it" }
}

/**
 * Open, save and folder pickers that look like the platform's own. On Windows they are the Explorer dialogs every
 * Windows app uses (`IFileOpenDialog` / `IFileSaveDialog`); elsewhere AWT's native dialog, or for folders on Linux,
 * Swing's chooser in the system look. Each suspends while the dialog is open, and the dialog is modal to [owner].
 *
 * Call from the UI thread (the owner's native handle is read there). If the Explorer dialog can't be created, the
 * AWT/Swing one is shown instead.
 */
object NativeFileDialogs {
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")
    private val isMac = System.getProperty("os.name").orEmpty().startsWith("Mac")

    // The Explorer dialogs are COM objects: they live on one thread in a single-threaded apartment, kept for reuse.
    private val comThread: CoroutineDispatcher by lazy {
        Executors.newSingleThreadExecutor { Thread(it, "native-file-dialogs").apply { isDaemon = true } }
            .asCoroutineDispatcher()
    }

    /**
     * Asks for files to open, filtered by [filters] (the first is selected; "All files" is offered after them).
     * Returns the chosen files, or an empty list when cancelled.
     */
    suspend fun openFiles(
        owner: Window?,
        title: String,
        directory: File? = null,
        filters: List<FileTypeFilter> = emptyList(),
        multiple: Boolean = false
    ): List<File> {
        if (isWindows) {
            val options = WindowsFileDialog.FOS_FILEMUSTEXIST or WindowsFileDialog.FOS_PATHMUSTEXIST or
                (if (multiple) WindowsFileDialog.FOS_ALLOWMULTISELECT else 0)
            explorer(
                save = false, handleOf(owner), title, directory, options,
                setup = { setFileTypes(filters.map { it.name to it.pattern } + ("All files" to "*.*")) },
                read = { if (multiple) results() else listOfNotNull(result()) }
            )?.let { return it.value.orEmpty() }
        }
        return withContext(Dispatchers.Swing) {
            val dialog = awtDialog(owner, title, FileDialog.LOAD)
            dialog.isMultipleMode = multiple
            directory?.let { dialog.directory = it.absolutePath }
            if (filters.isNotEmpty()) dialog.setFilenameFilter { dir, name -> filters.any { f -> f.accepts(File(dir, name)) } }
            dialog.isVisible = true
            dialog.files.toList()
        }
    }

    /**
     * Asks where to save a file of type [filter], suggesting [fileName] in [directory]. The filter's first extension is
     * added when the name has none, and Windows asks before overwriting. Returns null when cancelled.
     */
    suspend fun saveFile(
        owner: Window?,
        title: String,
        directory: File? = null,
        fileName: String? = null,
        filter: FileTypeFilter? = null
    ): File? {
        val extension = filter?.extensions?.firstOrNull()
        if (isWindows) {
            val options = WindowsFileDialog.FOS_OVERWRITEPROMPT or WindowsFileDialog.FOS_PATHMUSTEXIST
            explorer(
                save = true, handleOf(owner), title, directory, options,
                setup = {
                    if (filter != null) setFileTypes(listOf(filter.name to filter.pattern))
                    extension?.let(::setDefaultExtension)
                    fileName?.let { this.fileName = it }
                },
                read = { result() }
            )?.let { return it.value }
        }
        return withContext(Dispatchers.Swing) {
            val dialog = awtDialog(owner, title, FileDialog.SAVE)
            directory?.let { dialog.directory = it.absolutePath }
            fileName?.let { dialog.file = it }
            dialog.isVisible = true
            val picked = dialog.files.firstOrNull() ?: return@withContext null
            if (extension == null || filter.accepts(picked)) picked else File(picked.parentFile, "${picked.name}.$extension")
        }
    }

    /** Asks for a folder, starting in [directory]. [okLabel] replaces "Select Folder" where the platform allows. */
    suspend fun pickFolder(owner: Window?, title: String, directory: File? = null, okLabel: String? = null): File? {
        if (isWindows) {
            val options = WindowsFileDialog.FOS_PICKFOLDERS or WindowsFileDialog.FOS_PATHMUSTEXIST
            explorer(
                save = false, handleOf(owner), title, directory, options,
                setup = { okLabel?.let(::setOkLabel) },
                read = { result() }
            )?.let { return it.value }
        }
        return withContext(Dispatchers.Swing) {
            if (isMac) {
                // macOS's own dialog picks folders when asked through this property.
                System.setProperty("apple.awt.fileDialogForDirectories", "true")
                try {
                    val dialog = awtDialog(owner, title, FileDialog.LOAD)
                    directory?.let { dialog.directory = it.absolutePath }
                    dialog.isVisible = true
                    dialog.files.firstOrNull()
                } finally {
                    System.setProperty("apple.awt.fileDialogForDirectories", "false")
                }
            } else {
                swingFolderChooser(owner, title, directory, okLabel)
            }
        }
    }

    /** What the user chose, which may be null (cancelled), as opposed to no answer at all (the dialog failed). */
    private class Chosen<T>(val value: T?)

    /**
     * Shows an Explorer dialog on the COM thread, set up by [setup] and read by [read] once the user has chosen.
     * Returns null when it couldn't be shown, so the caller falls back to AWT. A failure after the user chose is thrown:
     * showing a second dialog then would only confuse.
     */
    private suspend fun <T> explorer(
        save: Boolean,
        owner: Pointer?,
        title: String,
        directory: File?,
        options: Int,
        setup: WindowsFileDialog.Dialog.() -> Unit,
        read: WindowsFileDialog.Dialog.() -> T
    ): Chosen<T>? = withContext(comThread) {
        val dialog = runCatching {
            WindowsFileDialog.enterApartment()
            WindowsFileDialog.create(save)
        }.getOrNull() ?: return@withContext null
        try {
            val hr = runCatching {
                dialog.options = dialog.options or options or WindowsFileDialog.FOS_FORCEFILESYSTEM or
                    WindowsFileDialog.FOS_NOCHANGEDIR
                dialog.setTitle(title)
                directory?.takeIf { it.isDirectory }?.let(dialog::setFolder)
                dialog.setup()
                dialog.show(owner)
            }.getOrNull()
            when {
                hr == null || (hr < 0 && hr != WindowsFileDialog.CANCELLED) -> null
                hr == WindowsFileDialog.CANCELLED -> Chosen(null)
                else -> Chosen(dialog.read())
            }
        } finally {
            dialog.release()
        }
    }

    private fun handleOf(owner: Window?): Pointer? = owner?.let { runCatching { Native.getWindowPointer(it) }.getOrNull() }

    private fun awtDialog(owner: Window?, title: String, mode: Int): FileDialog = when (owner) {
        is Frame -> FileDialog(owner, title, mode)
        is Dialog -> FileDialog(owner, title, mode)
        else -> FileDialog(null as Frame?, title, mode)
    }

    private fun swingFolderChooser(owner: Window?, title: String, directory: File?, okLabel: String?): File? {
        val previous = UIManager.getLookAndFeel()
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        try {
            val chooser = JFileChooser(directory)
            chooser.dialogTitle = title
            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            val answer = chooser.showDialog(owner, okLabel ?: "Select folder")
            return if (answer == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
        } finally {
            runCatching { UIManager.setLookAndFeel(previous) }
        }
    }
}
