package com.isycat.compose.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** A file saved as one of several types is named for the type chosen, unless the name the user typed already is one. */
class SaveFileTypesTest {
    private val part = FileTypeFilter("Parts", "part")
    private val packed = FileTypeFilter("Packages", "pack", "pk")
    private val types = listOf(part, packed)

    @Test
    fun aNameWithoutTheTypesExtensionTakesIt() {
        assertEquals(File("dir", "lid.pack"), NativeFileDialogs.ofType(File("dir", "lid"), packed, types))
        assertEquals(File("dir", "lid.v2.part"), NativeFileDialogs.ofType(File("dir", "lid.v2"), part, types))
    }

    @Test
    fun aNameTypedWithOneOfTheTypesExtensionsIsKept() {
        assertEquals(File("dir", "lid.pk"), NativeFileDialogs.ofType(File("dir", "lid.pk"), packed, types))
        assertEquals(File("dir", "lid.part"), NativeFileDialogs.ofType(File("dir", "lid.part"), packed, types))
    }

    @Test
    fun noTypeLeavesTheNameAsItIs() = assertEquals(File("dir", "lid"), NativeFileDialogs.ofType(File("dir", "lid"), null, emptyList()))
}
