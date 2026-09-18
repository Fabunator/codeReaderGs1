package com.example.codescannergs1.composite

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

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
 * Das lineare Symbol liefert nur Lage und Leserichtung. Maßstab und linke Kante des
 * CC-A werden aus dem Symbol selbst bestimmt: eine CC-A-Zeile ist genau
 * [CcaDecoder.ROW_MODULES] = 72 Module breit, beginnt mit einem Balken (erstes Modul
 * des ersten Codeworts) und endet mit dem Stop-Balken. Der Abstand vom linken Rand
 * des ersten dunklen Laufs bis zum rechten Rand des letzten dunklen Laufs einer
 * Zeile ist damit exakt 72 X.
 *
 * Ablauf:
 *  1. Abtastlinien senkrecht zur Leserichtung oberhalb des Linearsymbols legen
 *  2. je Linie linke/rechte dunkle Kante und damit Breite und X bestimmen
 *  3. zusammenhängende Linienbänder mit konsistenter Breite/Kante als Kandidaten nehmen
 *  4. je Kandidat Zeilenzahl 4..8 durchprobieren, Raster abtasten und decodieren;
 *     RAP-, Cluster- und Reed-Solomon-Prüfung verwerfen falsche Kandidaten
 */
object CcaImageDecoder {

    /** Wie weit oberhalb des Linearsymbols gesucht wird, in geschätzten Modulbreiten. */
    private const val SEARCH_DEPTH_MODULES = 46f

    /** Abtastschritt senkrecht zur Leserichtung, in geschätzten Modulbreiten. */
    private const val SCAN_STEP_MODULES = 0.25f

    /** Modulbreite des DataBar Limited inkl. Guard-Mustern – nur zur Startschätzung. */
    private const val LINEAR_MODULES_ESTIMATE = 73f

    private val ZERO = floatArrayOf(0f)

    /** Übliche Zeilenhöhen eines CC-A in Modulbreiten (ISO/IEC 24723: mindestens 2X). */
    private val ROW_HEIGHTS_MODULES = floatArrayOf(2f, 3f, 4f, 2.5f, 5f)

    private val TOP_SHIFTS = floatArrayOf(0f, -0.5f, 0.5f, -1f, 1f)
    private val HEIGHT_SHIFTS = floatArrayOf(0f, -1f, 1f, -2f, 2f)

    data class Match(
        val result: CcaDecoder.Result,
        /** Modulbreite in Pixeln. */
        val moduleSize: Float,
        val rowHeightModules: Float
    )

    fun decode(image: GrayImage, linear: Quad): Match? {
        val leftMid = mid(linear.topLeft, linear.bottomLeft)
        val rightMid = mid(linear.topRight, linear.bottomRight)
        val lineLen = dist(leftMid, rightMid)
        if (lineLen < 40f) return null

        val ux = (rightMid.x - leftMid.x) / lineLen
        val uy = (rightMid.y - leftMid.y) / lineLen
        val moduleEstimate = lineLen / LINEAR_MODULES_ESTIMATE

        // halbe Höhe des gemeldeten Bereichs – Startpunkt jenseits des Linearsymbols
        val halfHeight = dist(linear.topLeft, leftMid)

        for (sign in intArrayOf(-1, 1)) {
            val nx = -uy * sign
            val ny = ux * sign
            val match = searchSide(
                image, leftMid, rightMid, ux, uy, nx, ny,
                moduleEstimate, halfHeight
            )
            if (match != null) return match
        }
        return null
    }

    private class ScanLine(
        val offset: Float,
        val left: Float,
        val width: Float
    )

    private fun searchSide(
        image: GrayImage,
        leftMid: Pt,
        rightMid: Pt,
        ux: Float,
        uy: Float,
        nx: Float,
        ny: Float,
        moduleEstimate: Float,
        halfHeight: Float
    ): Match? {
        val lineLen = dist(leftMid, rightMid)
        // Abtastbereich entlang der Leserichtung etwas über das Linearsymbol hinaus
        val fromS = -3f * moduleEstimate
        val toS = lineLen + 3f * moduleEstimate
        val samples = ((toS - fromS) / (moduleEstimate * 0.25f)).toInt().coerceIn(64, 4096)

        val step = moduleEstimate * SCAN_STEP_MODULES
        val start = halfHeight + moduleEstimate * 0.5f
        val end = halfHeight + moduleEstimate * SEARCH_DEPTH_MODULES

        val lines = ArrayList<ScanLine>()
        var t = start
        while (t <= end) {
            val line = measureLine(image, leftMid, ux, uy, nx, ny, t, fromS, toS, samples)
            lines.add(line ?: ScanLine(t, Float.NaN, Float.NaN))
            t += step
        }

        // zusammenhängende Bänder mit konsistenter Breite und linker Kante sammeln
        val bands = ArrayList<Band>()
        var i = 0
        while (i < lines.size) {
            if (lines[i].width.isNaN()) { i++; continue }
            var j = i + 1
            while (j < lines.size && !lines[j].width.isNaN() &&
                abs(lines[j].width - lines[i].width) < 0.08f * lines[i].width &&
                abs(lines[j].left - lines[i].left) < 1.5f * (lines[i].width / CcaDecoder.ROW_MODULES)
            ) j++

            val band = lines.subList(i, j)
            if (band.size >= 8) {
                bands.add(
                    Band(
                        left = median(band.map { it.left }),
                        moduleSize = median(band.map { it.width }) / CcaDecoder.ROW_MODULES,
                        top = band.first().offset,
                        bottom = band.last().offset + step
                    )
                )
            }
            i = if (j > i + 1) j else i + 1
        }

        // Drei Durchgänge, vom Wahrscheinlichsten zum Aufwendigsten – jeweils über
        // alle Bänder, damit der Normalfall im ersten Durchgang erledigt ist:
        //  0: Zeilenhöhe = Bandhöhe / Zeilenzahl, ohne Korrektur
        //  1: dasselbe mit kleinen Verschiebungen der Bandgrenzen
        //     (bei unscharfen Kanten liegt das gemessene Band leicht daneben)
        //  2: feste Zeilenhöhen 2X/3X/4X, verankert am nahen und am fernen Bandrand –
        //     greift, wenn das Band mehr als den CC-A umfasst, etwa wenn Trennmuster
        //     und Linearsymbol mit hineinlaufen
        for (pass in 0..2) {
            for (band in bands) {
                val match = tryBand(image, leftMid, ux, uy, nx, ny, band, step, pass)
                if (match != null) return match
            }
        }
        return null
    }

    private class Band(
        val left: Float,
        val moduleSize: Float,
        val top: Float,
        val bottom: Float
    ) {
        val height: Float get() = bottom - top
    }

    private fun tryBand(
        image: GrayImage,
        origin: Pt,
        ux: Float, uy: Float,
        nx: Float, ny: Float,
        band: Band,
        step: Float,
        pass: Int
    ): Match? {
        val topShifts = if (pass == 0) ZERO else TOP_SHIFTS
        for (rows in 4..8) {
            val heights = if (pass == 2) {
                ROW_HEIGHTS_MODULES.map { it * band.moduleSize }
            } else {
                val shifts = if (pass == 0) ZERO else HEIGHT_SHIFTS
                shifts.map { (band.height + it * step) / rows }
            }
            for (rowHeight in heights) {
                val rowHeightModules = rowHeight / band.moduleSize
                if (rowHeightModules < 1.4f || rowHeightModules > 5.5f) continue
                val anchors = if (pass == 2) {
                    floatArrayOf(band.top, band.bottom - rows * rowHeight)
                } else {
                    floatArrayOf(band.top)
                }
                for (anchor in anchors) {
                    for (dt in topShifts) {
                        val decoded = trySample(
                            image, origin, ux, uy, nx, ny,
                            band.left, band.moduleSize, anchor + dt * step, rowHeight, rows
                        )
                        if (decoded != null) return Match(decoded, band.moduleSize, rowHeightModules)
                    }
                }
            }
        }
        return null
    }

    /** Bestimmt linke Kante und Breite der dunklen Struktur auf einer Abtastlinie. */
    private fun measureLine(
        image: GrayImage,
        origin: Pt,
        ux: Float, uy: Float,
        nx: Float, ny: Float,
        t: Float,
        fromS: Float, toS: Float,
        samples: Int
    ): ScanLine? {
        val values = IntArray(samples)
        val ds = (toS - fromS) / (samples - 1)
        var min = 255
        var max = 0
        for (k in 0 until samples) {
            val s = fromS + k * ds
            val x = origin.x + ux * s + nx * t
            val y = origin.y + uy * s + ny * t
            val v = sample(image, x, y)
            values[k] = v
            if (v < min) min = v
            if (v > max) max = v
        }
        if (max - min < 24) return null
        val threshold = (min + max) / 2

        var firstDark = -1
        var lastDark = -1
        for (k in 0 until samples) {
            if (values[k] < threshold) {
                if (firstDark < 0) firstDark = k
                lastDark = k
            }
        }
        if (firstDark < 0 || lastDark <= firstDark) return null
        // Kanten auf halbem Weg zum jeweils benachbarten hellen Sample
        val leftEdge = fromS + (firstDark - 0.5f) * ds
        val rightEdge = fromS + (lastDark + 0.5f) * ds
        val width = rightEdge - leftEdge
        if (width < 20f) return null
        return ScanLine(t, leftEdge, width)
    }

    private fun trySample(
        image: GrayImage,
        origin: Pt,
        ux: Float, uy: Float,
        nx: Float, ny: Float,
        left: Float,
        moduleSize: Float,
        bandTop: Float,
        rowHeight: Float,
        rows: Int
    ): CcaDecoder.Result? {
        val moduleRows = ArrayList<BooleanArray>(rows)
        for (row in 0 until rows) {
            val t = bandTop + rowHeight * (row + 0.5f)
            val bits = BooleanArray(CcaDecoder.ROW_MODULES)
            // Schwelle je Zeile aus Minimum/Maximum der Modulmittelpunkte
            val lumas = IntArray(CcaDecoder.ROW_MODULES)
            var min = 255
            var max = 0
            for (m in 0 until CcaDecoder.ROW_MODULES) {
                val s = left + moduleSize * (m + 0.5f)
                var acc = 0
                var n = 0
                // drei Abtastpunkte über die Zeilenhöhe mitteln
                for (dy in intArrayOf(-1, 0, 1)) {
                    val tt = t + dy * rowHeight * 0.25f
                    val x = origin.x + ux * s + nx * tt
                    val y = origin.y + uy * s + ny * tt
                    acc += sample(image, x, y)
                    n++
                }
                val v = acc / n
                lumas[m] = v
                if (v < min) min = v
                if (v > max) max = v
            }
            if (max - min < 24) return null
            val threshold = (min + max) / 2
            for (m in 0 until CcaDecoder.ROW_MODULES) bits[m] = lumas[m] < threshold
            moduleRows.add(bits)
        }

        // Zeilenreihenfolge: der CC-A steht über dem Linearsymbol, die Suche läuft
        // vom Linearsymbol nach außen – also von der letzten zur ersten Codewortzeile.
        val reversed = moduleRows.reversed()
        return tryDecode(reversed) ?: tryDecode(moduleRows)
    }

    private fun tryDecode(rows: List<BooleanArray>): CcaDecoder.Result? =
        try {
            CcaDecoder.decodeModuleRows(rows)
        } catch (_: Exception) {
            null
        }

    /** Bilineare Abtastung. */
    private fun sample(image: GrayImage, x: Float, y: Float): Int {
        if (x < 0f || y < 0f || x > image.width - 1f || y > image.height - 1f) return 255
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = (x0 + 1).coerceAtMost(image.width - 1)
        val y1 = (y0 + 1).coerceAtMost(image.height - 1)
        val fx = x - x0
        val fy = y - y0
        val a = image.luma(x0, y0)
        val b = image.luma(x1, y0)
        val c = image.luma(x0, y1)
        val d = image.luma(x1, y1)
        val top = a + (b - a) * fx
        val bottom = c + (d - c) * fx
        return (top + (bottom - top) * fy).roundToInt().coerceIn(0, 255)
    }

    private fun mid(a: Pt, b: Pt) = Pt((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    private fun dist(a: Pt, b: Pt) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }
}
