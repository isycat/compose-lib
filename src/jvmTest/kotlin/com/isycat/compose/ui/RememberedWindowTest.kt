package com.isycat.compose.ui

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import java.awt.Rectangle
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RememberedWindowTest {
    private val size = DpSize(1440.dp, 900.dp)
    private val screens = listOf(Rectangle(0, 0, 2560, 1440), Rectangle(2560, 200, 1920, 1080))

    @Test
    fun aPlaceIsKeptWholeAndReadBack() {
        val file = Files.createTempDirectory("window").resolve("place")
        try {
            assertNull(WindowPlace.read(file), "nothing kept yet")
            val place = WindowPlace(x = 2700f, y = 260f, width = 1200f, height = 800f, maximized = true)
            place.write(file)
            assertEquals(place, WindowPlace.read(file))
            Files.writeString(file, "x=1\ny=2\n")
            assertNull(WindowPlace.read(file), "a place missing its size is not used")
        } finally {
            file.parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun aWindowOpensWhereItWasLeftOnAnyScreenMaximisedIfItWas() {
        val second = WindowPlace.opening(WindowPlace(2700f, 260f, 1200f, 800f, maximized = false), screens, size)
        assertEquals(WindowPosition(2700.dp, 260.dp), second.position)
        assertEquals(DpSize(1200.dp, 800.dp), second.size)
        assertEquals(WindowPlacement.Floating, second.placement)
        val maximised = WindowPlace.opening(WindowPlace(100f, 100f, 1200f, 800f, maximized = true), screens, size)
        assertEquals(WindowPlacement.Maximized, maximised.placement)
        assertEquals(WindowPosition(100.dp, 100.dp), maximised.position, "where it goes back to when restored")
    }

    @Test
    fun itOpensCentredTheFirstTimeAndWhenItsScreenIsGone() {
        val first = WindowPlace.opening(null, screens, size)
        assertEquals(WindowPosition(Alignment.Center), first.position)
        assertEquals(size, first.size)
        val gone = WindowPlace.opening(WindowPlace(5000f, 300f, 1200f, 800f, maximized = false), screens, size)
        assertEquals(WindowPosition(Alignment.Center), gone.position, "its screen is unplugged")
        val topOff = WindowPlace.opening(WindowPlace(400f, -500f, 1200f, 800f, maximized = false), screens, size)
        assertEquals(WindowPosition(Alignment.Center), topOff.position, "its top edge, where it is grabbed, is off every screen")
    }
}
