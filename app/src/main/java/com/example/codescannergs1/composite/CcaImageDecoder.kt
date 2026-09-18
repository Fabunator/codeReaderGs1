package com.example.codescannergs1.composite

import kotlin.math.abs
import kotlin.math.hypot

/** Graustufenbild als schmale Schnittstelle, damit die Suche ohne Android testbar bleibt. */
interface GrayImage {
    val width: Int
    val height: Int
    /** Helligkeit 0..255 an ganzzahliger Position. */
    fun luma(x: Int, y: Int): Int
}

data class Pt(val x: Float, val y: Float)

/**
 * Lage des linearen Symbols im Bild (vier Eckpunkte, Reihenfolge wie bei zxing-cpp).
 * Der CC-A-Anteil liegt oberhalb – aus Sicht des Bildes kann das jede Richtung sein,
 * deshalb werden beide Normalenrichtungen probiert.
 */
data class Quad(val topLeft: Pt, val topRight: Pt, val bottomRight: Pt, val bottomLeft: Pt)

/**
 * Findet und decodiert den CC-A-Anteil oberhalb eines erkannten GS1 DataBar Limited.
 *
 * Das Raster wird aus dem Linearsymbol abgeleitet: der CC-A steht bündig über dessen
 * linker Kante und ist genau [CcaDecoder.ROW_MODULES] = 72 Module breit, während der
 * DataBar Limited 73 Module Tinte breit ist. Aus der von zxing-cpp gemeldeten Lage
 * ergeben sich damit Modulbreite und linke Kante direkt.
 *
 * Bewusst *nicht* über die dunklen Ränder des CC-A selbst: auf Negativ-Etiketten
 * (helles Symbol auf dunklem Grund) liegt das erste Modul einer Zeile – ein Balken –
 * farblich auf dem Hintergrund, seine Kante ist dort nicht messbar.
 *
 * Kandidaten werden über die Row Address Patterns geprüft. Das ist sehr trennscharf:
 *  - der Right-RAP muss in der Tabelle stehen, ebenso der Centre-RAP,
 *  - für jede gültige CC-A-Variante gilt centreIndex - rightIndex = 20 (mod 52),
 *  - das letzte Modul der Zeile ist der Stop-Balken,
 *  - alle drei Codewörter einer Zeile müssen aus demselben Cluster stammen.
 * Der Right-RAP-Index nummeriert zugleich die Zeile, daher ergeben sich Zeilenzahl
 * und Zeilenhöhe direkt aus den Fundstellen.
 */
object CcaImageDecoder {

    /** Modulbreite des DataBar Limited (Tintenbereich), Bezug für das Raster. */
    private const val LINEAR_MODULES = 73f

    /** Wie weit jenseits des Linearsymbols gesucht wird, in Modulbreiten. */
    private const val SEARCH_DEPTH_MODULES = 46f

    /** Abtastschritt senkrecht zur Leserichtung, in Modulbreiten. */
    private const val SCAN_STEP_MODULES = 0.4f

    /** Right-RAP-Startwert je Zeilenzahl 4..8 (ISO/IEC 24723 Tabellen 10/11). */
    private val RIGHT_RAP_START = intArrayOf(23, 13, 17, 27, 33)

    /** Maßstabskorrekturen, falls die gemeldete Lage leicht zu eng oder zu weit ist. */
    private val SCALES = floatArrayOf(1.00f, 0.98f, 1.02f)

    /** Verschiebungen der linken Kante in Modulbreiten. */
    private val LEFT_OFFSETS = floatArrayOf(0f, -0.25f, 0.25f, -0.5f, 0.5f, -1f, 1f)

    /** Erste Spalte des Vortests: ab hier liegen Right-RAP und Stop-Modul. */
    private const val PRETEST_FROM = 44

    private const val CENTRE_MINUS_RIGHT = 20

    data class Match(
        val result: CcaDecoder.Result,
        /** Modulbreite in Pixeln. */
        val moduleSize: Float,
        val rowHeightModules: Float,
        /** true, wenn die Balken heller als der Hintergrund sind (Negativdruck). */
        val inverted: Boolean
    )

    fun decode(image: GrayImage, linear: Quad): Match? {
        val leftMid = mid(linear.topLeft, linear.bottomLeft)
        val rightMid = mid(linear.topRight, linear.bottomRight)
        val lineLength = dist(leftMid, rightMid)
        if (lineLength < 40f) return null

        val ux = (rightMid.x - leftMid.x) / lineLength
        val uy = (rightMid.y - leftMid.y) / lineLength
        val moduleSize0 = lineLength / LINEAR_MODULES
        // halbe Höhe des gemeldeten Bereichs: Startpunkt jenseits des Linearsymbols
        val halfHeight = dist(linear.topLeft, leftMid)

        val ctx = Context(image, leftMid, ux, uy, moduleSize0, halfHeight)

        for (sign in SIGNS) {
            ctx.nx = -uy * sign
            ctx.ny = ux * sign
            // Liegt auf dieser Seite überhaupt etwas? Ein leerer Ruhebereich wird so
            // in Bruchteilen einer Millisekunde abgewiesen.
            if (!hasStructure(ctx)) continue
            for (inverted in BOOLEANS) {
                ctx.inverted = inverted
                for (scale in SCALES) {
                    for (leftOffset in LEFT_OFFSETS) {
                        val match = tryGrid(ctx, moduleSize0 * scale, leftOffset)
                        if (match != null) return match
                    }
                }
            }
        }
        return null
    }

    private val SIGNS = intArrayOf(-1, 1)
    private val BOOLEANS = booleanArrayOf(false, true)

    private class Context(
        val image: GrayImage,
        val origin: Pt,
        val ux: Float,
        val uy: Float,
        val moduleSize0: Float,
        val halfHeight: Float
    ) {
        var nx = 0f
        var ny = 0f
        var inverted = false
        val luma = FloatArray(CcaDecoder.ROW_MODULES)
        val bits = BooleanArray(CcaDecoder.ROW_MODULES)
    }

    private fun tryGrid(ctx: Context, moduleSize: Float, leftOffsetModules: Float): Match? {
        val step = ctx.moduleSize0 * SCAN_STEP_MODULES
        val start = ctx.halfHeight + step
        val end = ctx.halfHeight + ctx.moduleSize0 * SEARCH_DEPTH_MODULES
        val s0 = leftOffsetModules * moduleSize

        // Zeilenkandidaten je Right-RAP-Index (erster Fund gewinnt)
        var found = 0
        val rowBits = arrayOfNulls<BooleanArray>(52)
        val rowOffset = FloatArray(52)

        var t = start
        while (t <= end) {
            if (readRow(ctx, s0, moduleSize, t)) {
                val rightIndex = rapIndex(ctx.bits, 61, Pdf417Tables.rapSide)
                if (rightIndex >= 0 && rowBits[rightIndex] == null) {
                    rowBits[rightIndex] = ctx.bits.copyOf()
                    rowOffset[rightIndex] = t
                    found++
                }
            }
            t += step
        }
        if (found < 4) return null

        for (rows in 8 downTo 4) {
            val first = (RIGHT_RAP_START[rows - 4] - 1 + 52) % 52
            var complete = true
            for (k in 0 until rows) {
                if (rowBits[(first + k) % 52] == null) { complete = false; break }
            }
            if (!complete) continue

            val moduleRows = ArrayList<BooleanArray>(rows)
            for (k in 0 until rows) moduleRows.add(rowBits[(first + k) % 52]!!)
            val result = try {
                CcaDecoder.decodeModuleRows(moduleRows)
            } catch (_: Exception) {
                continue
            }
            val span = rowOffset[(first + rows - 1) % 52] - rowOffset[first % 52]
            val rowHeight = if (rows > 1) abs(span) / (rows - 1) else moduleSize * 2f
            return Match(result, moduleSize, rowHeight / moduleSize, ctx.inverted)
        }
        return null
    }

    /**
     * Tastet eine Zeile ab und prüft die billigen Strukturmerkmale.
     * Bei Erfolg stehen die 72 Module in [Context.bits].
     */
    private fun readRow(ctx: Context, s0: Float, moduleSize: Float, t: Float): Boolean {
        val baseX = ctx.origin.x + ctx.nx * t
        val baseY = ctx.origin.y + ctx.ny * t

        // Vortest auf dem rechten Teil der Zeile: Stop-Modul und Right-RAP.
        // Das verwirft die meisten Abtastlinien, bevor die ganze Zeile gelesen wird.
        var pmin = Float.MAX_VALUE
        var pmax = -Float.MAX_VALUE
        for (m in PRETEST_FROM until CcaDecoder.ROW_MODULES) {
            val s = s0 + moduleSize * (m + 0.5f)
            val v = sample(ctx.image, baseX + ctx.ux * s, baseY + ctx.uy * s)
            ctx.luma[m] = v
            if (v < pmin) pmin = v
            if (v > pmax) pmax = v
        }
        if (pmax - pmin < 24f) return false
        val pthr = (pmin + pmax) * 0.5f
        for (m in PRETEST_FROM until CcaDecoder.ROW_MODULES) {
            ctx.bits[m] = if (ctx.inverted) ctx.luma[m] > pthr else ctx.luma[m] < pthr
        }
        if (!ctx.bits[71]) return false
        if (rapIndex(ctx.bits, 61, Pdf417Tables.rapSide) < 0) return false

        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (m in 0 until CcaDecoder.ROW_MODULES) {
            if (m < PRETEST_FROM) {
                val s = s0 + moduleSize * (m + 0.5f)
                ctx.luma[m] = sample(ctx.image, baseX + ctx.ux * s, baseY + ctx.uy * s)
            }
            val v = ctx.luma[m]
            if (v < min) min = v
            if (v > max) max = v
        }
        if (max - min < 24f) return false
        val threshold = (min + max) * 0.5f
        for (m in 0 until CcaDecoder.ROW_MODULES) {
            ctx.bits[m] = if (ctx.inverted) ctx.luma[m] > threshold else ctx.luma[m] < threshold
        }

        if (!ctx.bits[71]) return false
        val right = rapIndex(ctx.bits, 61, Pdf417Tables.rapSide)
        if (right < 0) return false
        val centre = rapIndex(ctx.bits, 17, Pdf417Tables.rapCentre)
        if (centre < 0) return false
        if (((centre - right) % 52 + 52) % 52 != CENTRE_MINUS_RIGHT) return false

        // alle drei Codewörter müssen bekannt sein und aus demselben Cluster stammen
        var cluster = -1
        for (offset in intArrayOf(0, 27, 44)) {
            val hit = Pdf417Tables.patternToCodeword[readInt(ctx.bits, offset, 17)] ?: return false
            val c = hit shr 16
            if (cluster < 0) cluster = c else if (cluster != c) return false
        }
        return true
    }

    /**
     * Grobe Vorprüfung: hat der Bereich jenseits des Linearsymbols überhaupt
     * genügend Kontrast und Kantenwechsel, um ein Symbol zu enthalten?
     */
    private fun hasStructure(ctx: Context): Boolean {
        val m = ctx.moduleSize0
        var structured = 0
        var t = ctx.halfHeight + m
        val end = ctx.halfHeight + m * SEARCH_DEPTH_MODULES
        while (t <= end) {
            val baseX = ctx.origin.x + ctx.nx * t
            val baseY = ctx.origin.y + ctx.ny * t
            var min = Float.MAX_VALUE
            var max = -Float.MAX_VALUE
            for (k in 0 until CcaDecoder.ROW_MODULES) {
                val s = m * (k + 0.5f)
                val v = sample(ctx.image, baseX + ctx.ux * s, baseY + ctx.uy * s)
                ctx.luma[k] = v
                if (v < min) min = v
                if (v > max) max = v
            }
            if (max - min >= 24f) {
                val thr = (min + max) * 0.5f
                var changes = 0
                var prev = ctx.luma[0] < thr
                for (k in 1 until CcaDecoder.ROW_MODULES) {
                    val cur = ctx.luma[k] < thr
                    if (cur != prev) changes++
                    prev = cur
                }
                // eine CC-A-Zeile hat 36 Wechsel; grobzügige Untergrenze
                if (changes >= 18) structured++
                if (structured >= 3) return true
            }
            t += m * 2f
        }
        return false
    }

    private fun rapIndex(bits: BooleanArray, offset: Int, table: IntArray): Int {
        val v = readInt(bits, offset, 10)
        for (i in table.indices) if (table[i] == v) return i
        return -1
    }

    private fun readInt(bits: BooleanArray, offset: Int, length: Int): Int {
        var v = 0
        for (i in 0 until length) v = (v shl 1) or if (bits[offset + i]) 1 else 0
        return v
    }

    /** Bilineare Abtastung. */
    private fun sample(image: GrayImage, x: Float, y: Float): Float {
        if (x < 0f || y < 0f || x > image.width - 2f || y > image.height - 2f) return 255f
        val x0 = x.toInt()
        val y0 = y.toInt()
        val fx = x - x0
        val fy = y - y0
        val a = image.luma(x0, y0)
        val b = image.luma(x0 + 1, y0)
        val c = image.luma(x0, y0 + 1)
        val d = image.luma(x0 + 1, y0 + 1)
        val top = a + (b - a) * fx
        val bottom = c + (d - c) * fx
        return top + (bottom - top) * fy
    }

    private fun mid(a: Pt, b: Pt) = Pt((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    private fun dist(a: Pt, b: Pt) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
}
