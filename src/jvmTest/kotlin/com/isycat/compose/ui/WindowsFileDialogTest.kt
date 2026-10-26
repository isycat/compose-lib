package com.isycat.compose.ui

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Drives the Explorer dialog's COM interface without showing it, to prove the vtable slots: each setter is read back
 * through its getter. Windows only.
 */
class WindowsFileDialogTest {
    @BeforeTest
    fun windowsOnly() {
        assumeTrue(System.getProperty("os.name").orEmpty().startsWith("Windows"), "Explorer dialogs are Windows only")
        WindowsFileDialog.enterApartment()
    }

    @Test
    fun optionsFileNameAndFolderRoundTrip() {
        val dialog = WindowsFileDialog.create(save = true)
        try {
            dialog.options = dialog.options or WindowsFileDialog.FOS_OVERWRITEPROMPT or WindowsFileDialog.FOS_FORCEFILESYSTEM
            assertTrue(dialog.options and WindowsFileDialog.FOS_FORCEFILESYSTEM != 0)

            dialog.fileName = "deck.cshproj"
            assertEquals("deck.cshproj", dialog.fileName)

            val folder = Files.createTempDirectory("dialog").toFile()
            dialog.setFolder(folder)
            assertEquals(folder.canonicalFile, assertNotNull(dialog.folder()).canonicalFile)

            dialog.setTitle("Save project as")
            dialog.setOkLabel("Save here")
            dialog.setFileTypes(listOf("cardSTUDIO project" to "*.cshproj"))
            dialog.setDefaultExtension("cshproj")
        } finally {
            dialog.release()
        }
    }

    @Test
    fun aShellItemKnowsItsPath() {
        val folder = Files.createTempDirectory("item").toFile()
        val item = assertNotNull(WindowsFileDialog.shellItem(folder))
        item.use { assertEquals(folder.canonicalFile, assertNotNull(it.path()).canonicalFile) }
        assertEquals(null, WindowsFileDialog.shellItem(File(folder, "missing")))
    }
}
