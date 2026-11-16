package com.isycat.compose.ui

import java.io.File

/**
 * [FileOpeners] on Windows, for the current user alone, through [WindowsFileAssociation]: each extension is described
 * by [association] for the running [exe]. A choice the user made in "Open with" stays theirs: Windows keeps it apart.
 */
class WindowsFileOpeners(
    private val exe: File,
    private val association: (extension: String, exe: File) -> WindowsFileAssociation.Association,
) : FileOpeners {
    override fun opensThem(extensions: List<String>): Boolean =
        extensions.all { WindowsFileAssociation.isAssociated(association(it, exe)) }

    override fun takeThem(extensions: List<String>) {
        extensions.forEach { WindowsFileAssociation.ensure(association(it, exe)) }
    }

    companion object {
        /**
         * The exe of the app running now, as its launcher names it (Launch4j's, or a jpackage image's); null when it was
         * started otherwise, as from Gradle, where no double-click could reach it.
         */
        fun runningExe(): File? = (System.getProperty("launch4j.exefile") ?: System.getProperty("jpackage.app-path"))
            ?.let(::File)?.takeIf { it.isFile }

        /** Openers for the running exe on Windows; null elsewhere, or where no exe runs. */
        fun here(association: (extension: String, exe: File) -> WindowsFileAssociation.Association): FileOpeners? =
            runningExe()?.takeIf { System.getProperty("os.name").orEmpty().startsWith("Windows") }
                ?.let { WindowsFileOpeners(it, association) }
    }
}
