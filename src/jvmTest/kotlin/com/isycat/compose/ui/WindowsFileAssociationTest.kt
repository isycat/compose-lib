package com.isycat.compose.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsFileAssociationTest {
    @Test
    fun entriesPointTheExtensionAtTheAppWithItsIcon() {
        val exe = File("C:\\Apps\\card-render.exe")
        val entries = WindowsFileAssociation.entries(
            WindowsFileAssociation.Association(".chproj", "Isycat.CardRender.Project", "Card Render project", exe)
        ).associate { (key, name, data) -> "$key|$name" to data }

        val path = exe.absolutePath
        assertEquals("Isycat.CardRender.Project", entries["Software\\Classes\\.chproj|"])
        assertEquals("", entries["Software\\Classes\\.chproj\\OpenWithProgids|Isycat.CardRender.Project"])
        assertEquals("Card Render project", entries["Software\\Classes\\Isycat.CardRender.Project|"])
        assertEquals("\"$path\",0", entries["Software\\Classes\\Isycat.CardRender.Project\\DefaultIcon|"])
        assertEquals("\"$path\" \"%1\"", entries["Software\\Classes\\Isycat.CardRender.Project\\shell\\open\\command|"])
    }
}
