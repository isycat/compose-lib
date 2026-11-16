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

    @Test
    fun staleListsOnlyWhatTheRegistryLacksOrHoldsOtherwise() {
        val association = WindowsFileAssociation.Association("ptcgcard", "Isycat.CardStudio.ptcgcard", "Pokemon card", File("C:\\Apps\\card-studio.exe"))
        val entries = WindowsFileAssociation.entries(association)
        val held = entries.associate { (key, name, data) -> (key to name) to data }.toMutableMap()
        assertEquals(emptyList(), WindowsFileAssociation.stale(association) { key, name -> held[key to name] })
        // Another app took the extension, and the type's open command is gone.
        held["Software\\Classes\\.ptcgcard" to ""] = "Other.App"
        held.remove("Software\\Classes\\Isycat.CardStudio.ptcgcard\\shell\\open\\command" to "")
        assertEquals(
            listOf("Software\\Classes\\.ptcgcard", "Software\\Classes\\Isycat.CardStudio.ptcgcard\\shell\\open\\command"),
            WindowsFileAssociation.stale(association) { key, name -> held[key to name] }.map { it.first },
        )
    }
}
