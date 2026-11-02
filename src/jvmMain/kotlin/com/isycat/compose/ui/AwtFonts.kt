package com.isycat.compose.ui

import com.isycat.compose.resources.Res
import java.awt.Font
import java.net.URI

/**
 * The fonts compose-lib bundles, as AWT fonts, for anything drawn with Java2D rather than Compose (a splash screen, an
 * image written out). It then has the same face as the components, whatever fonts the system has.
 */
object AwtFonts {
    /** Roboto, the title bar's font ([TitleBarDefaults.fontFamily]), at [size] points. */
    fun roboto(size: Float): Font = roboto.deriveFont(size)

    private val roboto: Font by lazy { load("font/roboto_regular.ttf") }

    private fun load(path: String): Font =
        URI(Res.getUri(path)).toURL().openStream().use { Font.createFont(Font.TRUETYPE_FONT, it) }
}
