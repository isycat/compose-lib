package com.isycat.compose.ui

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class FontBytesTest {
    @Test
    fun `the bundled Roboto's bytes are there`() {
        val bytes = runBlocking { TitleBarDefaults.fontBytes() }
        assertTrue(bytes.isNotEmpty())
    }
}
