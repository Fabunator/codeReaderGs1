package com.example.codescannergs1.composite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Prueft die Decodierkette fuer "GS1 DataBar Limited CC-A" gegen Referenzsymbole,
 * die mit zint erzeugt wurden (src/test/resources/cca_vectors.tsv).
 *
 * Geprueft werden drei Stufen:
 *  - Modulraster -> Elementstring (Codewoerter, Basis 928, Bitcodierung)
 *  - Reed-Solomon-Korrektur ueber GF(929)
 *  - Bildsuche: synthetisch gerendertes Symbol -> Elementstring
 */
class CcaDecoderTest {

    private data class Vector(
        val label: String,
        val gtin: String,
        val expected: String,
        val matrix: List<String>
    ) {
        /** Zeilen des CC-A-Anteils: alles ausser Trennmuster und Linearzeile. */
        val ccaRows: List<String> get() = matrix.subList(0, matrix.size - 2)
        val linearRow: String get() = matrix.last()
    }

    private fun loadVectors(): List<Vector> {
        val stream = javaClass.classLoader!!.getResourceAsStream("cca_vectors.tsv")
            ?: error("cca_vectors.tsv fehlt in src/test/resources")
        return stream.bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val p = line.split("\t")
                Vector(p[0], p[1], p[2].replace('^', CcaDecoder.FNC1), p[3].split(","))
            }
    }

    /** CC-A beginnt im Gesamtsymbol ein Modul rechts der linken Kante (top shift = 1). */
    private fun moduleRows(v: Vector): List<BooleanArray> = v.ccaRows.map { row ->
        BooleanArray(CcaDecoder.ROW_MODULES) { row[it + 1] == '1' }
    }

    @Test
    fun vectorsAreLoaded() {
        assertTrue("Referenzvektoren fehlen", loadVectors().size >= 20)
    }

    @Test
    fun decodesAllReferenceSymbols() {
        for (v in loadVectors()) {
            val result = CcaDecoder.decodeModuleRows(moduleRows(v))
            assertEquals(v.label, v.expected, result.elementString)
            assertEquals(v.label, v.ccaRows.size, result.rows)
            assertEquals(v.label, 0, result.correctedCodewords)
        }
    }

    @Test
    fun reedSolomonCorrectsUpToHalfTheEccCodewords() {
        val rnd = Random(4711)
        for (v in loadVectors()) {
            val (clean, variant) = CcaDecoder.codewordsFromModules(moduleRows(v))
            val correctable = variant.eccCodewords / 2
            for (errors in 1..correctable) {
                repeat(10) {
                    val cw = clean.copyOf()
                    val positions = HashSet<Int>()
                    while (positions.size < errors) positions.add(rnd.nextInt(cw.size))
                    for (p in positions) cw[p] = (cw[p] + 1 + rnd.nextInt(928)) % 929
                    val corrected = CcaDecoder.reedSolomonCorrect(cw, variant.eccCodewords)
                    assertEquals("${v.label}: $errors Fehler", errors, corrected)
                    assertTrue("${v.label}: Codewoerter nicht wiederhergestellt", cw.contentEquals(clean))
                }
            }
        }
    }

    @Test
    fun rejectsTamperedSymbol() {
        val v = loadVectors().first()
        val rows = moduleRows(v).map { it.copyOf() }
        // Centre-RAP verfaelschen -> muss abgewiesen werden, nicht falsch decodiert
        rows[0][18] = !rows[0][18]
        rows[0][19] = !rows[0][19]
        try {
            CcaDecoder.decodeModuleRows(rows)
            throw AssertionError("Verfaelschtes Symbol wurde akzeptiert")
        } catch (_: CcaDecoder.CcaException) {
            // erwartet
        }
    }

    // ------------------------------------------------------------------
    // Bildsuche auf synthetisch gerenderten Symbolen
    // ------------------------------------------------------------------

    private class ArrayGray(
        override val width: Int,
        override val height: Int,
        val pixels: ByteArray
    ) : GrayImage {
        override fun luma(x: Int, y: Int) = pixels[y * width + x].toInt() and 0xFF
    }

    private class Rotated90(private val src: GrayImage) : GrayImage {
        override val width get() = src.height
        override val height get() = src.width
        override fun luma(x: Int, y: Int) = src.luma(src.width - 1 - y, x)
    }

    private class Rendered(val image: GrayImage, val quad: Quad)

    /** Rendert das Gesamtsymbol; Zeilenhoehen: CC-A 2 Module, Trennmuster 1, Linear 20. */
    private fun render(v: Vector, module: Int, quiet: Int = 8): Rendered {
        val heights = IntArray(v.matrix.size) { 2 }
        heights[v.matrix.size - 2] = 1
        heights[v.matrix.size - 1] = 20
        val widthModules = v.matrix[0].length
        val w = (widthModules + 2 * quiet) * module
        val h = (heights.sum() + 2 * quiet) * module
        val pixels = ByteArray(w * h) { -1 }

        var yModule = quiet
        var linearTop = 0
        for (row in v.matrix.indices) {
            val top = yModule * module
            if (row == v.matrix.size - 1) linearTop = top
            for (py in top until top + heights[row] * module) {
                for (mx in 0 until widthModules) {
                    if (v.matrix[row][mx] == '1') {
                        val x0 = (quiet + mx) * module
                        for (px in x0 until x0 + module) pixels[py * w + px] = 0
                    }
                }
            }
            yModule += heights[row]
        }

        val first = v.linearRow.indexOf('1')
        val last = v.linearRow.lastIndexOf('1')
        val x0 = ((quiet + first) * module).toFloat()
        val x1 = ((quiet + last + 1) * module).toFloat()
        val linearHeight = heights.last() * module
        // wie zxing-cpp: nur das abgetastete Band, nicht die ganze Balkenhoehe
        val yTop = linearTop + linearHeight * 0.25f
        val yBottom = linearTop + linearHeight * 0.75f
        val quad = Quad(Pt(x0, yTop), Pt(x1, yTop), Pt(x1, yBottom), Pt(x0, yBottom))
        return Rendered(ArrayGray(w, h, pixels), quad)
    }

    @Test
    fun imageDecoderFindsAllReferenceSymbols() {
        for (module in intArrayOf(3, 5)) {
            for (v in loadVectors()) {
                val r = render(v, module)
                val match = CcaImageDecoder.decode(r.image, r.quad)
                    ?: throw AssertionError("${v.label} (Modul $module px): CC-A nicht gefunden")
                assertEquals("${v.label} (Modul $module px)", v.expected, match.result.elementString)
            }
        }
    }

    @Test
    fun imageDecoderWorksOnRotatedImage() {
        for (v in loadVectors().take(12)) {
            val r = render(v, 4)
            val rotated = Rotated90(r.image)
            // Rotated90 liefert neu(x, y) = alt(W - 1 - y, x); umgekehrt gilt
            // also fuer einen Punkt (x, y) des Originals: neu = (y, W - 1 - x).
            fun rot(p: Pt) = Pt(p.y, r.image.width - 1 - p.x)
            val quad = Quad(rot(r.quad.topLeft), rot(r.quad.topRight), rot(r.quad.bottomRight), rot(r.quad.bottomLeft))
            val match = CcaImageDecoder.decode(rotated, quad)
                ?: throw AssertionError("${v.label}: CC-A im gedrehten Bild nicht gefunden")
            assertEquals(v.label, v.expected, match.result.elementString)
        }
    }

    @Test
    fun imageDecoderReturnsNullWithoutCompositeComponent() {
        val v = loadVectors().first()
        // nur den Linearanteil rendern: darueber steht dann kein CC-A
        val linearOnly = Vector(v.label, v.gtin, v.expected, listOf(v.linearRow, v.linearRow, v.linearRow))
        val r = render(linearOnly, 5)
        assertEquals(null, CcaImageDecoder.decode(r.image, r.quad))
    }
}
