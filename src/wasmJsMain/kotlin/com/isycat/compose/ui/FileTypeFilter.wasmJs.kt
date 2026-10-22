package com.isycat.compose.ui

actual data class FileTypeFilter actual constructor(actual val name: String, actual val extensions: List<String>) {
    actual constructor(name: String, vararg extensions: String) : this(name, extensions.toList())

    actual fun accepts(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('/').substringAfterLast('.', "")
        return extensions.any { extension.equals(it, ignoreCase = true) }
    }

    /** The browser's file picker's `accept` list: `.png,.jpg`. */
    val accept: String get() = extensions.joinToString(",") { ".$it" }
}
