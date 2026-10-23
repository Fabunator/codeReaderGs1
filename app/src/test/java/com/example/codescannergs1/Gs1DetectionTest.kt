package com.example.codescannergs1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueft die GS1-Einstufung.
 *
 * Kern der Sache: GS1 unterscheidet sich von einem gewoehnlichen Code allein durch
 * FNC1 an erster Symbolposition. Das steht in der AIM-Symbologiekennung und nirgends
 * sonst – insbesondere ist ein Gruppentrennzeichen im Inhalt kein Beleg, weil
 * Code 128, Data Matrix und QR ASCII 29 regulaer codieren koennen.
 */
class Gs1DetectionTest {

    private val GS = '\u001d'

    // ------------------------------------------------------------------
    // AIM-Symbologiekennungen
    // ------------------------------------------------------------------

    @Test
    fun symbologyIdDecidesForKnownModifiers() {
        val confirmed = listOf("]C1", "]d2", "]d5", "]Q3", "]Q4", "]e0")
        for (id in confirmed) {
            assertEquals("$id muss GS1 bestaetigen", Gs1Level.CONFIRMED, Gs1Detector.levelFromSymbologyId(id))
        }
        // ]C2, ]d3 und ]Q5 sind FNC1 an *zweiter* Position: AIM-Branchenkennung, kein GS1
        val notGs1 = listOf("]C0", "]C2", "]d1", "]d0", "]d3", "]Q1", "]Q2", "]Q5", "]E0", "]I1", "]A0")
        for (id in notGs1) {
            assertEquals("$id darf kein GS1 melden", Gs1Level.NONE, Gs1Detector.levelFromSymbologyId(id))
        }
        assertNull("ohne Kennung muss der Inhalt entscheiden", Gs1Detector.levelFromSymbologyId(null))
    }

    // ------------------------------------------------------------------
    // Der gemeldete Fehlerfall
    // ------------------------------------------------------------------

    @Test
    fun plainCode128WithGroupSeparatorInContentIsNotGs1() {
        // Gewoehnlicher Code 128, dessen Nutzdaten ein echtes ASCII 29 enthalten.
        // Die alte Regel "enthaelt <GS> also GS1" lag hier falsch.
        val result = Gs1Detector.classify(
            symbologyId = "]C0",
            canCarryElementString = true,
            elementString = "AB${GS}CD"
        )
        assertEquals(Gs1Level.NONE, result.level)
        assertTrue(!result.isGs1)
    }

    @Test
    fun plainCode128WithGs1LookingContentIsNotConfirmed() {
        // Byteweise identisch mit dem Inhalt eines GS1-128 – ohne FNC1 aber kein GS1.
        val result = Gs1Detector.classify(
            symbologyId = "]C0",
            canCarryElementString = true,
            elementString = "0104012345678901"
        )
        assertEquals(Gs1Level.NONE, result.level)
    }

    @Test
    fun gs1128IsConfirmed() {
        val result = Gs1Detector.classify(
            symbologyId = "]C1",
            canCarryElementString = true,
            elementString = "010401234567890110LOT1"
        )
        assertEquals(Gs1Level.CONFIRMED, result.level)
        assertNull("Inhalt geht vollstaendig auf", result.unparsedRest)
    }

    // ------------------------------------------------------------------
    // Ohne Kennung: nur der Inhalt
    // ------------------------------------------------------------------

    @Test
    fun withoutSymbologyIdValidElementStringIsProbable() {
        val result = Gs1Detector.classify(
            symbologyId = null,
            canCarryElementString = true,
            elementString = "010401234567890117261231"
        )
        assertEquals(Gs1Level.PROBABLE, result.level)
        assertTrue(result.isGs1)
    }

    @Test
    fun withoutSymbologyIdArbitraryTextIsNotGs1() {
        for (text in listOf("HELLO WORLD", "AB${GS}CD", "https://example.org", "")) {
            val result = Gs1Detector.classify(null, true, text)
            assertEquals("\"$text\" darf kein GS1 sein", Gs1Level.NONE, result.level)
        }
    }

    @Test
    fun formatsWithoutElementStringsAreNeverGs1() {
        // Ein EAN-13-Inhalt liesse sich zufaellig als AI-Kette lesen (401 + Rest).
        // Fuer EAN/UPC, ITF und Code 39 wird der Inhalt deshalb gar nicht erst geprueft.
        val result = Gs1Detector.classify(
            symbologyId = null,
            canCarryElementString = false,
            elementString = "4012345678901"
        )
        assertEquals(Gs1Level.NONE, result.level)
    }

    @Test
    fun invalidCheckDigitPreventsProbable() {
        // GTIN mit falscher Pruefziffer: als "wahrscheinlich" zu schwach.
        val result = Gs1Detector.classify(null, true, "0104012345678900")
        assertEquals(Gs1Level.NONE, result.level)
    }

    // ------------------------------------------------------------------
    // Hinweis bei bestaetigtem GS1, dessen Inhalt nicht aufgeht
    // ------------------------------------------------------------------

    @Test
    fun confirmedButUnparsableContentReportsRest() {
        // (3106) ist nicht vergeben
        val result = Gs1Detector.classify("]C1", true, "01040123456789013106001234")
        assertEquals(Gs1Level.CONFIRMED, result.level)
        assertEquals("3106001234", result.unparsedRest)
    }

    @Test
    fun fourDigitAiIsNoLongerReportedAsRest() {
        // Bis zur vollstaendigen AI-Tabelle wurde (3103) hier als nicht auswertbar gemeldet
        val result = Gs1Detector.classify("]C1", true, "01040123456789013103001234")
        assertEquals(Gs1Level.CONFIRMED, result.level)
        assertNull(result.unparsedRest)
    }

    // ------------------------------------------------------------------
    // Strukturpruefung
    // ------------------------------------------------------------------

    @Test
    fun validateAcceptsCompleteElementStrings() {
        val v = GS1Parser.validate("010401234567890117261231${GS}10LOT1")
        assertTrue("sollte vollstaendig aufgehen", v.complete)
        assertEquals(3, v.aiCount)
        assertNull(v.unparsedRest)
        assertTrue(v.plausible)
    }

    @Test
    fun validateReportsTheUnparsedRemainder() {
        val v = GS1Parser.validate("0104012345678901XYZ")
        assertTrue(!v.complete)
        assertEquals(1, v.aiCount)
        assertEquals("XYZ", v.unparsedRest)
    }

    @Test
    fun validateRejectsTruncatedFixedLengthAi() {
        // AI 01 braucht 14 Stellen
        val v = GS1Parser.validate("0104012345")
        assertTrue("abgeschnittener Wert darf nicht als vollstaendig gelten", !v.complete)
    }

    @Test
    fun validateStripsSymbologyIdAndLeadingFnc1() {
        assertTrue(GS1Parser.validate("]C1010401234567890110LOT1").complete)
        assertTrue(GS1Parser.validate("${GS}010401234567890110LOT1").complete)
    }

    @Test
    fun validateStripsRepeatedSymbologyIds() {
        // Auf manchen Etiketten steckt die AIM-Kennung zusaetzlich als Nutzdaten im
        // Symbol. Der Decoder stellt dann seine eigene Kennung davor und sie erscheint
        // doppelt – beobachtet an einem realen Pharma-Etikett.
        val doubled = "]d2]d201084330420215731726022810V999"
        val v = GS1Parser.validate(doubled)
        assertTrue("doppelte Kennung muss entfernt werden", v.complete)
        assertEquals(3, v.aiCount)

        val parsed = GS1Parser.parse(doubled, formatForDisplay = false)
        assertEquals("08433042021573", parsed["01"])
        assertEquals("260228", parsed["17"])
        assertEquals("V999", parsed["10"])

        val classification = Gs1Detector.classify("]d2", true, doubled)
        assertEquals(Gs1Level.CONFIRMED, classification.level)
        assertNull("Inhalt geht trotz doppelter Kennung auf", classification.unparsedRest)
        assertEquals("der Etikettenfehler muss gemeldet werden", "]d2", classification.embeddedSymbologyId)
    }

    // ------------------------------------------------------------------
    // Mitcodierte Symbologiekennung als Etikettenfehler
    // ------------------------------------------------------------------

    @Test
    fun cleanCodeReportsNoEmbeddedSymbologyId() {
        val clean = Gs1Detector.classify("]d2", true, "010401234567890110LOT1")
        assertNull(clean.embeddedSymbologyId)
    }

    @Test
    fun embeddedSymbologyIdIsOnlyReportedForGs1Codes() {
        // Beliebiger Text, der zufaellig mit "]" beginnt, ist kein Etikettenfehler
        val plain = Gs1Detector.classify("]C0", true, "]xy etwas Text")
        assertEquals(Gs1Level.NONE, plain.level)
        assertNull(plain.embeddedSymbologyId)
    }

    @Test
    fun embeddedIdDoesNotDowngradeTheLevel() {
        // Das Symbol ist trotz des Fehlers ein GS1-Symbol: FNC1 steht an erster Stelle.
        val result = Gs1Detector.classify("]C1", true, "]C1010401234567890110LOT1")
        assertEquals(Gs1Level.CONFIRMED, result.level)
        assertEquals("]C1", result.embeddedSymbologyId)
        assertNull("Inhalt geht nach dem Entfernen auf", result.unparsedRest)
    }

    // ------------------------------------------------------------------
    // Kennung, die der Decoder selbst voranstellt
    // ------------------------------------------------------------------

    @Test
    fun readerSuppliedPrefixIsSeparatedFromTheContent() {
        // ML Kit gibt bei Code 128 keine eigene Kennung heraus, sondern stellt sie den
        // Nutzdaten voran. Real gemessen an einem SSCC-Etikett.
        val (id, content) = Gs1Detector.splitSymbologyId("]C100742515207000000580", 'C')
        assertEquals("]C1", id)
        assertEquals("00742515207000000580", content)
    }

    @Test
    fun splittingOnlyAcceptsTheMatchingSymbology() {
        // falscher Buchstabe zur Symbologie
        assertNull(Gs1Detector.splitSymbologyId("]Q30104012345678901", 'C').first)
        // kein bekannter Buchstabe fuer dieses Format
        assertNull(Gs1Detector.splitSymbologyId("]C10104012345678901", null).first)
        // drittes Zeichen ist keine Ziffer
        assertNull(Gs1Detector.splitSymbologyId("]Cx0104012345678901", 'C').first)
        // gewoehnlicher Text, der zufaellig mit "]" beginnt, bleibt unveraendert
        val (id, rest) = Gs1Detector.splitSymbologyId("]xy etwas Text", 'C')
        assertNull(id)
        assertEquals("]xy etwas Text", rest)
    }

    @Test
    fun gs1128IsNotReportedAsFaultyLabel() {
        // Der gemeldete Fehler: an jedem GS1-128 erschien der Hinweis, "]C1" sei mit
        // im Symbol codiert. Das war die Kennung des Decoders, nicht der Inhalt.
        val (id, content) = Gs1Detector.splitSymbologyId("]C100742515207000000580", 'C')
        val result = Gs1Detector.classify(id, true, content)
        assertEquals(Gs1Level.CONFIRMED, result.level)
        assertNull("kein Etikettenfehler", result.embeddedSymbologyId)
    }

    @Test
    fun contentStartingWithAnIdentifierProvesNothingWithoutAReportedId() {
        // Ohne eigene Angabe des Decoders laesst sich nicht entscheiden, ob die Kennung
        // vom Leser stammt oder im Symbol steht. Dann wird nichts gemeldet.
        val result = Gs1Detector.classify(null, true, "]C100742515207000000580")
        assertNull(result.embeddedSymbologyId)
    }

    @Test
    fun doubledIdentifierIsStillFoundAfterSplitting() {
        // Steht die Kennung wirklich im Symbol, bleibt sie nach dem Abtrennen der
        // Leser-Kennung uebrig - und wird gemeldet.
        val raw = "]d2]d201084330420215731726022810V999"
        val (id, content) = Gs1Detector.splitSymbologyId(raw, 'd')
        assertEquals("]d2", id)
        val result = Gs1Detector.classify(id, true, content)
        assertEquals(Gs1Level.CONFIRMED, result.level)
        assertEquals("]d2", result.embeddedSymbologyId)
        assertNull("Inhalt geht auf", result.unparsedRest)
    }

    @Test
    fun ssccElementStringParsesCompletely() {
        val v = GS1Parser.validate("00742515207000000580")
        assertTrue("SSCC muss vollstaendig aufgehen", v.complete)
        assertEquals(1, v.aiCount)
        assertNull(v.unparsedRest)
    }
}
