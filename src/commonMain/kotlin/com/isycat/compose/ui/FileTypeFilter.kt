package com.isycat.compose.ui

/** An entry in a file dialog's type list: what it's called ("Images") and the extensions it shows, without dots. */
expect class FileTypeFilter(name: String, extensions: List<String>) {
    constructor(name: String, vararg extensions: String)

    val name: String
    val extensions: List<String>

    /** Whether a file called [fileName] has one of the [extensions]. */
    fun accepts(fileName: String): Boolean
}
