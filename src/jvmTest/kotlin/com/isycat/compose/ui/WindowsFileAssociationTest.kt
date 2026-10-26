package com.isycat.compose.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsFileAssociationTest {
    @Test
    fun entriesPointTheExtensionAtTheAppWithItsIcon() {
        val exe = File("C:\\Apps\\card-studio.exe")
        val entries = WindowsFileAssociation.entries(
            WindowsFileAssociation.Association(".cshproj", "Isycat.CardStudio.Project", "cardSTUDIO project", exe)
        ).associate { (key, name, data) -> "$key|$name" to data }

        val path = exe.absolutePath
        assertEquals("Isycat.CardStudio.Project", entries["Software\\Classes\\.cshproj|"])
        assertEquals("", entries["Software\\Classes\\.cshproj\\OpenWithProgids|Isycat.CardStudio.Project"])
        assertEquals("cardSTUDIO project", entries["Software\\Classes\\Isycat.CardStudio.Project|"])
        assertEquals("\"$path\",0", entries["Software\\Classes\\Isycat.CardStudio.Project\\DefaultIcon|"])
        assertEquals("\"$path\" \"%1\"", entries["Software\\Classes\\Isycat.CardStudio.Project\\shell\\open\\command|"])
    }
}
