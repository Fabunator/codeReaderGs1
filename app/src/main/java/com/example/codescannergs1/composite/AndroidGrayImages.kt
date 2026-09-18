package com.example.codescannergs1.composite

import android.graphics.Bitmap
import java.nio.ByteBuffer

/**
 * Sicht auf die Y-Ebene eines Kamerabildes.
 *
 * Zuschnitt und Drehung werden genauso angewandt wie in zxing-cpp
 * (ImageView::rotated, Drehung im Uhrzeigersinn): erst zuschneiden, dann drehen.
 * Nur dann passen die von zxing-cpp gemeldeten Eckpunkte zu den hier
 * abgetasteten Pixeln.
 */
class YPlaneGrayImage(
    private val buffer: ByteBuffer,
    private val rowStride: Int,
    private val pixelStride: Int,
    private val cropLeft: Int,
    private val cropTop: Int,
    private val cropWidth: Int,
    private val cropHeight: Int,
    rotation: Int
) : GrayImage {

    private val rot = ((rotation % 360) + 360) % 360

    override val width: Int = if (rot == 90 || rot == 270) cropHeight else cropWidth
    override val height: Int = if (rot == 90 || rot == 270) cropWidth else cropHeight

    override fun luma(x: Int, y: Int): Int {
        val sx: Int
        val sy: Int
        when (rot) {
            90 -> { sx = y; sy = cropHeight - 1 - x }
            180 -> { sx = cropWidth - 1 - x; sy = cropHeight - 1 - y }
            270 -> { sx = cropWidth - 1 - y; sy = x }
            else -> { sx = x; sy = y }
        }
        if (sx < 0 || sy < 0 || sx >= cropWidth || sy >= cropHeight) return 255
        val index = (cropTop + sy) * rowStride + (cropLeft + sx) * pixelStride
        if (index < 0 || index >= buffer.limit()) return 255
        return buffer.get(index).toInt() and 0xFF
    }
}

/** Graustufensicht auf eine Bitmap (Galerie-Import). */
class BitmapGrayImage(bitmap: Bitmap, rotation: Int = 0) : GrayImage {

    private val rot = ((rotation % 360) + 360) % 360
    private val srcWidth = bitmap.width
    private val srcHeight = bitmap.height
    private val gray = ByteArray(srcWidth * srcHeight)

    init {
        val pixels = IntArray(srcWidth * srcHeight)
        bitmap.getPixels(pixels, 0, srcWidth, 0, 0, srcWidth, srcHeight)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            // gleiche Gewichtung wie in zxing-cpp
            gray[i] = ((r * 77 + g * 150 + b * 29) shr 8).toByte()
        }
    }

    override val width: Int = if (rot == 90 || rot == 270) srcHeight else srcWidth
    override val height: Int = if (rot == 90 || rot == 270) srcWidth else srcHeight

    override fun luma(x: Int, y: Int): Int {
        val sx: Int
        val sy: Int
        when (rot) {
            90 -> { sx = y; sy = srcHeight - 1 - x }
            180 -> { sx = srcWidth - 1 - x; sy = srcHeight - 1 - y }
            270 -> { sx = srcWidth - 1 - y; sy = x }
            else -> { sx = x; sy = y }
        }
        if (sx < 0 || sy < 0 || sx >= srcWidth || sy >= srcHeight) return 255
        return gray[sy * srcWidth + sx].toInt() and 0xFF
    }
}
