package com.example.codescannergs1

import com.google.mlkit.vision.barcode.common.Barcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prueft den ML-Kit-Zweig mit nachgebauten Leseergebnissen. */
class MlKitPathTest {

    private val GS = "\u001d"

    // --- der gemeldete Fehler -----------------------------------------------

    @Test
    fun code128WithReaderPrefixIsNotFlagged() {
        val out = mlKitResultToScannedCode(Barcode.FORMAT_CODE_128, "]C100742515207000000580")
        assertNull("kein Etikettenfehler", out.embeddedSymbologyId)
        assertEquals("]C1", out.symbologyId)
        assertEquals(Gs1Level.CONFIRMED, out.gs1LevelOf())
        assertEquals("GS1-128", out.type)
        assertEquals("]C100742515207000000580", out.rawValue)
    }

    @Test
    fun code128WithLeadingFnc1IsNotFlaggedEither() {
        val out = mlKitResultToScannedCode(Barcode.FORMAT_CODE_128, GS + "00742515207000000580")
        assertNull(out.embeddedSymbologyId)
        assertEquals("]C1", out.symbologyId)
        assertEquals(Gs1Level.CONFIRMED, out.gs1LevelOf())
    }

    @Test
    fun plainCode128StaysPlain() {
        val out = mlKitResultToScannedCode(Barcode.FORMAT_CODE_128, "ABC-123")
        assertNull(out.symbologyId)
        assertNull(out.embeddedSymbologyId)
        assertEquals(Gs1Level.NONE, out.gs1LevelOf())
        assertEquals("Code 128", out.type)
    }

    @Test
    fun doubledIdentifierIsStillReported() {
        // Etikett, in dem die Kennung wirklich mit codiert ist: ML Kit stellt seine
        // eigene davor, sie steht also zweimal da.
        val out = mlKitResultToScannedCode(Barcode.FORMAT_DATA_MATRIX, "]d2]d201084330420215731726022810V999")
        assertEquals("]d2", out.embeddedSymbologyId)
        assertEquals(Gs1Level.CONFIRMED, out.gs1LevelOf())
    }

    @Test
    fun textStartingWithABracketIsLeftAlone() {
        val out = mlKitResultToScannedCode(Barcode.FORMAT_QR_CODE, "]xy Hinweis")
        assertNull(out.symbologyId)
        assertNull(out.embeddedSymbologyId)
        assertEquals("]xy Hinweis", out.rawValue)
    }

    // --- Zusammenfuehren ----------------------------------------------------

    @Test
    fun bothDecodersYieldOneEntryAndTheBetterOneWins() {
        // zxing-cpp meldet die Kennung getrennt, ML Kit stellt sie voran - derselbe Code.
        val fromZxing = ScannedCode(
            rawValue = "]C100742515207000000580",
            type = "GS1-128",
            isGs1 = true,
            gs1Level = Gs1Level.CONFIRMED.name,
            symbologyId = "]C1"
        )
        val fromMlKit = mlKitResultToScannedCode(Barcode.FORMAT_CODE_128, "]C100742515207000000580")

        val merged = mergeCodes(listOf(fromMlKit, fromZxing))
        assertEquals("nur ein Eintrag", 1, merged.size)
        assertEquals(Gs1Level.CONFIRMED, merged.single().gs1LevelOf())
        assertNull(merged.single().embeddedSymbologyId)
    }

    @Test
    fun doubledIdentifierEntriesFromBothDecodersMergeToOne() {
        // zxing-cpp: Kennung getrennt, Inhalt beginnt noch einmal damit
        val fromZxing = ScannedCode(
            rawValue = "]d2]d201084330420215731726022810V999",
            type = "GS1 DataMatrix",
            isGs1 = true,
            gs1Level = Gs1Level.CONFIRMED.name,
            symbologyId = "]d2",
            embeddedSymbologyId = "]d2"
        )
        val fromMlKit = mlKitResultToScannedCode(Barcode.FORMAT_DATA_MATRIX, "]d2]d201084330420215731726022810V999")

        val merged = mergeCodes(listOf(fromMlKit, fromZxing))
        assertEquals("nur ein Eintrag", 1, merged.size)
        assertEquals("Etikettenfehler bleibt gemeldet", "]d2", merged.single().embeddedSymbologyId)
    }

    @Test
    fun differentCodesAreKeptApart() {
        val a = mlKitResultToScannedCode(Barcode.FORMAT_CODE_128, "]C100742515207000000580")
        val b = mlKitResultToScannedCode(Barcode.FORMAT_CODE_128, "]C100742515207000000436")
        assertEquals(2, mergeCodes(listOf(a, b)).size)
    }

    @Test
    fun partialCompositeResultIsDropped() {
        // Liest ein Decoder nur den Linearanteil, ist dessen Inhalt ein Praefix des
        // vollstaendigen Eintrags - er darf nicht zusaetzlich erscheinen.
        val linearOnly = ScannedCode(
            rawValue = "]e00104012345678901",
            type = "GS1 DataBar Limited",
            isGs1 = true,
            gs1Level = Gs1Level.CONFIRMED.name,
            symbologyId = "]e0"
        )
        val complete = ScannedCode(
            rawValue = "]e0010401234567890110LOT1",
            type = "GS1 DataBar Limited CC-A",
            isGs1 = true,
            gs1Level = Gs1Level.CONFIRMED.name,
            symbologyId = "]e0"
        )
        val merged = mergeCodes(listOf(linearOnly, complete))
        assertEquals(1, merged.size)
        assertTrue(merged.single().type.endsWith("CC-A"))
    }
}
