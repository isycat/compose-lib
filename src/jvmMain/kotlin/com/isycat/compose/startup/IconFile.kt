package com.isycat.compose.startup

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

/**
 * A Windows icon file (`.ico`) holding [images], one per size, as an exe's icon needs: each stored as a 32-bit bitmap
 * with its alpha, the form every resource compiler reads (some cannot read the PNG form).
 */
object IconFile {
    /** Writes [images] (square, at most 256 pixels across, each a different size) to [file]. */
    fun write(images: List<BufferedImage>, file: File) {
        require(images.isNotEmpty() && images.all { it.width == it.height && it.width in 1..MAX_SIZE }) {
            "An icon holds square images of 1 to $MAX_SIZE pixels"
        }
        val bitmaps = images.map(::bitmap)
        val out = ByteArrayOutputStream()
        DataOutputStream(out).run {
            littleShort(0)
            littleShort(ICON_TYPE)
            littleShort(images.size)
            var offset = HEADER + ENTRY * images.size
            images.zip(bitmaps).forEach { (image, bitmap) ->
                // A size of 256 is written as 0, as the format has one byte for it.
                writeByte(image.width % MAX_SIZE)
                writeByte(image.height % MAX_SIZE)
                writeByte(0)
                writeByte(0)
                littleShort(1)
                littleShort(BITS)
                littleInt(bitmap.size)
                littleInt(offset)
                offset += bitmap.size
            }
            bitmaps.forEach(::write)
        }
        file.parentFile?.mkdirs()
        file.writeBytes(out.toByteArray())
    }

    /** [image] as an icon's bitmap: its header (twice its height, for the mask), its pixels bottom row first, then a clear mask. */
    private fun bitmap(image: BufferedImage): ByteArray {
        val size = image.width
        val maskRow = ((size + MASK_ALIGN - 1) / MASK_ALIGN) * (MASK_ALIGN / Byte.SIZE_BITS)
        val out = ByteArrayOutputStream()
        DataOutputStream(out).run {
            littleInt(BITMAP_HEADER)
            littleInt(size)
            littleInt(size * 2)
            littleShort(1)
            littleShort(BITS)
            littleInt(0)
            littleInt(size * size * Int.SIZE_BYTES + maskRow * size)
            repeat(UNUSED_HEADER_INTS) { littleInt(0) }
            for (y in size - 1 downTo 0) {
                for (x in 0 until size) littleInt(image.getRGB(x, y))
            }
            write(ByteArray(maskRow * size))
        }
        return out.toByteArray()
    }

    private fun DataOutputStream.littleShort(value: Int) {
        writeByte(value and BYTE)
        writeByte((value shr Byte.SIZE_BITS) and BYTE)
    }

    private fun DataOutputStream.littleInt(value: Int) {
        littleShort(value and SHORT)
        littleShort((value ushr Short.SIZE_BITS) and SHORT)
    }

    private const val MAX_SIZE = 256
    private const val ICON_TYPE = 1
    private const val HEADER = 6
    private const val ENTRY = 16
    private const val BITS = 32
    private const val BITMAP_HEADER = 40
    private const val UNUSED_HEADER_INTS = 4
    private const val MASK_ALIGN = 32
    private const val BYTE = 0xFF
    private const val SHORT = 0xFFFF
}
