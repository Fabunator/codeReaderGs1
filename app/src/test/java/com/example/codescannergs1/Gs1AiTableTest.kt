package com.example.codescannergs1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Prueft die aus dem GS1 Barcode Syntax Dictionary erzeugte AI-Tabelle,
 * insbesondere die vierstelligen AIs und das implizite Dezimalkomma.
 */
class Gs1AiTableTest {

    private val GS = '\u001d'
    private val GTIN = "04012345678901"
    private val GLN = "4012345000009"

    // ------------------------------------------------------------------
    // Tabelle
    // ------------------------------------------------------------------

    @Test
    fun dictionaryIsCompleteAndPrefixFree() {
        val ais = GS1Parser.aiDefinitions
        assertTrue("erwartet mehrere hundert AIs, gefunden ${ais.size}", ais.size > 500)
        for (code in listOf("00", "01", "10", "21", "3103", "3922", "7003", "8004", "8018", "8200", "90", "91", "99")) {
            assertNotNull("AI $code fehlt", ais[code])
        }
        // Voraussetzung dafuer, dass parse() einfach 4..2 Stellen probieren darf
        for (a in ais.keys) for (b in ais.keys) {
            if (a != b) assertFalse("$a ist Praefix von $b", b.startsWith(a))
        }
        assertTrue(ais.keys.all { it.length in 2..4 })
    }

    @Test
    fun rangesAreExpandedWithTitle() {
        for (n in 0..5) {
            val ai = GS1Parser.aiDefinitions["310$n"]!!
            assertEquals("NET WEIGHT (kg)", ai.name)
            assertEquals(6, ai.length)
            assertEquals(n, ai.decimals)
            assertFalse(ai.fnc1Required)
        }
        assertNull("3106 gibt es nicht", GS1Parser.aiDefinitions["3106"])
        assertEquals("CERT # 1", GS1Parser.aiDefinitions["7230"]!!.name) // "#" im Titel
        assertNotNull(GS1Parser.aiDefinitions["8110"]!!.name)               // Titel fehlt im Dictionary
    }

    @Test
    fun lengthsAndFnc1FollowTheSpecification() {
        val ais = GS1Parser.aiDefinitions
        // feste Laenge, trotzdem FNC1 danach
        assertEquals(10, ais["7003"]!!.length)
        assertTrue(ais["7003"]!!.fnc1Required)
        // optionale Komponente -> variable Laenge
        assertEquals(-1, ais["7007"]!!.length)
        assertEquals(6, ais["7007"]!!.minLength)
        assertEquals(12, ais["7007"]!!.maxLength)
        // GLN: frueher faelschlich mit Laenge 1 eingetragen
        assertEquals(13, ais["410"]!!.length)
        assertEquals(AIType.DATE, ais["17"]!!.type)
        assertEquals(AIType.ALPHANUMERIC, ais["253"]!!.type)
    }

    @Test
    fun everyAiHasALongDescription() {
        val missing = GS1Parser.aiDefinitions.filterValues { it.description.isNullOrBlank() }.keys
        assertTrue("ohne Beschreibung: $missing", missing.isEmpty())
        val ais = GS1Parser.aiDefinitions
        assertEquals("Net weight, kilograms (variable measure trade item)", ais["3103"]!!.description)
        assertEquals("Expiration date and time (YYMMDDhhmm)", ais["7003"]!!.description)
        assertEquals("Company internal information", ais["97"]!!.description)
        assertEquals("National Healthcare Reimbursement Number (NHRN) - Germany PZN", ais["710"]!!.description)
    }

    @Test
    fun descriptionRangesAreExpanded() {
        val d = GS1Parser.parseDescriptions("3100-3102\tNet weight\n90\tInfo\nkaputt\n7003\t \n")
        assertEquals(mapOf("3100" to "Net weight", "3101" to "Net weight", "3102" to "Net weight", "90" to "Info"), d)
    }

    // ------------------------------------------------------------------
    // Zerlegen
    // ------------------------------------------------------------------

    @Test
    fun fourDigitAisAreParsed() {
        val data = "01${GTIN}310300125070032612311430${GS}10LOT42"
        val raw = GS1Parser.parse(data, formatForDisplay = false)
        assertEquals(GTIN, raw["01"])
        assertEquals("001250", raw["3103"])
        assertEquals("2612311430", raw["7003"])
        assertEquals("LOT42", raw["10"])
        assertNull(raw["unknown"])

        val v = GS1Parser.validate(data)
        assertTrue(v.complete)
        assertEquals(4, v.aiCount)
        assertTrue(v.plausible)
    }

    @Test
    fun weightIsDisplayedWithImpliedDecimals() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1,250", GS1Parser.parse("3103001250")["3103"])
            Locale.setDefault(Locale.US)
            assertEquals("1.250", GS1Parser.parse("3103001250")["3103"])
            assertEquals("1250", GS1Parser.parse("3100001250")["3100"])
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun decimalPointIsInsertedCorrectly() {
        val de = Locale.GERMANY
        assertEquals("0,05", GS1Parser.insertDecimalPoint("000005", 2, de))
        assertEquals("12,345", GS1Parser.insertDecimalPoint("012345", 3, de))
        assertEquals("0,12345", GS1Parser.insertDecimalPoint("012345", 5, de))
        assertEquals("0", GS1Parser.insertDecimalPoint("000000", 0, de))
    }

    @Test
    fun variableLengthPriceWithCurrency() {
        // (3932) Preis mit ISO-4217-Waehrung, zwei Nachkommastellen
        val data = "01${GTIN}39329781999${GS}10ABC"
        val raw = GS1Parser.parse(data, formatForDisplay = false)
        assertEquals("9781999", raw["3932"])
        assertEquals("ABC", raw["10"])
        assertEquals("19,99 (978)", GS1Parser.formatDecimalAiValue(GS1Parser.aiDefinitions["3932"]!!, "9781999", Locale.GERMANY))
        assertTrue(GS1Parser.validate(data).complete)
    }

    @Test
    fun glnIsNoLongerCutToOneDigit() {
        val raw = GS1Parser.parse("410${GLN}", formatForDisplay = false)
        assertEquals(GLN, raw["410"])
        assertTrue(GS1Parser.validate("410${GLN}").complete)
    }

    @Test
    fun unknownFourDigitPrefixIsStillReported() {
        // 3106 ist nicht vergeben
        val v = GS1Parser.validate("01${GTIN}3106001250")
        assertFalse(v.complete)
        assertEquals("3106001250", v.unparsedRest)
    }

    // ------------------------------------------------------------------
    // Plausibilitaet
    // ------------------------------------------------------------------

    @Test
    fun checkDigitsAreVerifiedForAllKeys() {
        assertTrue(GS1Parser.checkPlausibility("414", GLN).first)
        assertFalse(GS1Parser.checkPlausibility("414", "4012345000008").first)
        assertTrue(GS1Parser.checkPlausibility("8018", "401234500000000012").first)
        assertFalse(GS1Parser.checkPlausibility("8018", "401234500000000013").first)
        // Pruefziffer steckt in der zweiten Komponente: N1,zero N13,csum [X..16]
        assertTrue(GS1Parser.checkPlausibility("8003", "0${GLN}ABC").first)
        assertFalse(GS1Parser.checkPlausibility("8003", "1${GLN}ABC").first)
    }

    @Test
    fun alphanumericCheckCharacterPair() {
        // Beispiel aus der GS1-Dokumentation zur GMN
        assertTrue(GS1Parser.checkPlausibility("8013", "1987654Ad4X4bL5ttr2310c2K").first)
        assertFalse(GS1Parser.checkPlausibility("8013", "1987654Ad4X4bL5ttr2310c2L").first)
    }

    @Test
    fun datesAndTimes() {
        assertTrue("Tag 00 ist bei (17) erlaubt", GS1Parser.checkPlausibility("17", "261200").first)
        assertFalse(GS1Parser.checkPlausibility("17", "261300").first)
        assertFalse("bei (7006) kein Tag 00", GS1Parser.checkPlausibility("7006", "261200").first)
        assertTrue(GS1Parser.checkPlausibility("7003", "2402292359").first)
        assertFalse(GS1Parser.checkPlausibility("7003", "2402292460").first)
        assertFalse(GS1Parser.checkPlausibility("7003", "2302291200").first) // kein Schaltjahr
        assertTrue(GS1Parser.checkPlausibility("7007", "260101").first)
        assertTrue(GS1Parser.checkPlausibility("7007", "260101260131").first)
        assertFalse(GS1Parser.checkPlausibility("7007", "2601012601").first)
    }

    @Test
    fun charsetAndOptionalComponents() {
        assertTrue(GS1Parser.checkPlausibility("10", "AB-12/3").first)
        assertFalse("Leerzeichen gehoert nicht zu CSET 82", GS1Parser.checkPlausibility("10", "AB 12").first)
        assertTrue(GS1Parser.checkPlausibility("4331", "000250").first)
        assertTrue("optionales Minus", GS1Parser.checkPlausibility("4331", "000250-").first)
        assertFalse(GS1Parser.checkPlausibility("4331", "000250+").first)
        assertFalse(GS1Parser.checkPlausibility("3103", "00125A").first)
    }
}
