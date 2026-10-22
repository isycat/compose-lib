package com.isycat.compose.ui

import java.io.File

actual data class FileTypeFilter actual constructor(actual val name: String, actual val extensions: List<String>) {
    actual constructor(name: String, vararg extensions: String) : this(name, extensions.toList())

    fun accepts(file: File): Boolean = extensions.any { file.extension.equals(it, ignoreCase = true) }

    actual fun accepts(fileName: String): Boolean = accepts(File(fileName))

    internal val pattern: String get() = extensions.joinToString(";") { "*.$it" }
}
