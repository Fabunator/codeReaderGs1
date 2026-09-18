package com.example.codescannergs1

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Locale

object GS1Parser {

    internal val aiDefinitions = mapOf(
        // Fixed length AIs
        "00" to AI(18, 18, 18, AIType.NUMERIC, "SSCC"),
        "01" to AI(14, 14, 14, AIType.NUMERIC, "GTIN"),
        "02" to AI(14, 14, 14, AIType.NUMERIC,"CONTENT"),
        "03" to AI(14, 14, 14, AIType.NUMERIC,"MTO"),
        "11" to AI(6, 6, 6, AIType.DATE, "PROD DATE"),
        "12" to AI(6, 6, 6, AIType.DATE, "DUE DATE"),
        "13" to AI(6, 6, 6, AIType.DATE, "PACK DATE"),
        "15" to AI(6, 6, 6, AIType.DATE, "BEST BEFORE"),
        "16" to AI(6, 6, 6, AIType.DATE, "SELL BY"),
        "17" to AI(6, 6, 6, AIType.DATE, "EXPIRY"),
        "20" to AI(2, 2, 2, AIType.NUMERIC, "VARIANT"),
        "402" to AI(1, 17, 17, AIType.NUMERIC, "GSIN"),
        "410" to AI(1, 13, 13, AIType.NUMERIC, "SHIP TO LOC"),
        "411" to AI(1, 13, 13, AIType.NUMERIC, "BILL TO"),
        "412" to AI(1, 13, 13, AIType.NUMERIC, "PURCHASE FROM"),
        "413" to AI(1, 13, 13, AIType.NUMERIC, "SHIP FOR LOC"),
        "414" to AI(1, 13, 13, AIType.NUMERIC, "LOC No."),
        "415" to AI(1, 13, 13, AIType.NUMERIC, "PAY TO"),
        "416" to AI(1, 13, 13, AIType.NUMERIC, "PROD/SERV LOC"),
        "417" to AI(1, 13, 13, AIType.NUMERIC, "PARTY"),
        "422" to AI(1, 3, 3, AIType.NUMERIC, "ORIGIN"),
        "424" to AI(1, 3, 3, AIType.NUMERIC, "COUNTRY - PROCESS"),
        "426" to AI(1, 3, 3, AIType.NUMERIC, "COUNTRY - FULL PROCESS"),

        // Variable length AIs
        "10" to AI(-1, 1, 20, AIType.ALPHANUMERIC, "LOT"),
        "21" to AI(-1, 1, 20, AIType.ALPHANUMERIC, "SERIAL"),
        "22" to AI(-1, 1, 20, AIType.ALPHANUMERIC, "CPV"),
        "235" to AI(-1, 1, 28, AIType.ALPHANUMERIC, "TPX"),
        "240" to AI(-1, 1, 30, AIType.ALPHANUMERIC, "ADDITIONAL ID"),
        "241" to AI(-1, 1, 30, AIType.ALPHANUMERIC, "CUST. PART No."),
        "242" to AI(-1, 1, 6, AIType.ALPHANUMERIC, "MTO VARIANT"),
        "243" to AI(-1, 1, 20, AIType.ALPHANUMERIC, "PCN"),
        "250" to AI(-1, 1, 30, AIType.ALPHANUMERIC, "SECONDARY SERIAL"),
        "251" to AI(-1, 1, 30, AIType.ALPHANUMERIC, "REF. TO SOURCE"),
        "253" to AI(-1, 13, 30, AIType.ALPHANUMERIC, "ADDITIONAL SERIAL"),
        "254" to AI(-1, 1, 20, AIType.ALPHANUMERIC, "GLN EXTENSION COMPONENT"),
        "255" to AI(-1, 13, 25, AIType.ALPHANUMERIC, "GCN"),
        "30" to AI(-1, 1, 8, AIType.ALPHANUMERIC, "VAR. COUNT"),
        "37" to AI(-1, 1, 8, AIType.NUMERIC, "COUNT"),
        "400" to AI(-1, 1, 30, AIType.ALPHANUMERIC, "ORDER NUMBER"),
        "401" to AI(-1, 1, 30, AIType.ALPHANUMERIC, "GINC"),
        "403" to AI(-1, 1, 30, AIType.ALPHANUMERIC, "ROUTE"),
        "420" to AI(-1, 1, 20, AIType.ALPHANUMERIC, "SHIP TO POST"),
        "421" to AI(-1, 3, 12, AIType.ALPHANUMERIC, "SHIP TO POST"),
        "423" to AI(-1, 3, 15, AIType.NUMERIC, "COUNTRY - INITIAL PROCESS"),
        "425" to AI(-1, 3, 15, AIType.NUMERIC, "COUNTRY - DISASSEMBLY"),
        "427" to AI(-1, 1, 3, AIType.ALPHANUMERIC, "ORIGIN SUBDIVISION"),
        "710" to AI(-1, 1, 20, AIType.ALPHANUMERIC,"NHRN PZN"),
        "711" to AI(-1, 1, 20, AIType.ALPHANUMERIC,"NHRN CIP"),
        "712" to AI(-1, 1, 20, AIType.ALPHANUMERIC,"NHRN CN"),
        "713" to AI(-1, 1, 20, AIType.ALPHANUMERIC,"NHRN DRN"),
        "714" to AI(-1, 1, 20, AIType.ALPHANUMERIC,"NHRN AIM"),
        "715" to AI(-1, 1, 20, AIType.ALPHANUMERIC,"NHRN NDC"),
        "716" to AI(-1, 1, 20, AIType.ALPHANUMERIC,"NHRN AIC"),
        "717" to AI(-1, 1, 20, AIType.ALPHANUMERIC,"NHRN SRN"),
        "90" to AI(-1, 1, 30, AIType.ALPHANUMERIC, "INTERNAL"),
        "91" to AI(-1, 1, 90, AIType.ALPHANUMERIC, "INTERNAL"),
        "92" to AI(-1, 1, 90, AIType.ALPHANUMERIC, "INTERNAL"),
        "93" to AI(-1, 1, 90, AIType.ALPHANUMERIC, "INTERNAL"),
        "94" to AI(-1, 1, 90, AIType.ALPHANUMERIC, "INTERNAL"),
        "95" to AI(-1, 1, 90, AIType.ALPHANUMERIC, "INTERNAL"),
        "96" to AI(-1, 1, 90, AIType.ALPHANUMERIC, "INTERNAL"),
        "97" to AI(-1, 1, 90, AIType.ALPHANUMERIC, "INTERNAL"),
        "98" to AI(-1, 1, 90, AIType.ALPHANUMERIC, "INTERNAL"),
        "99" to AI(-1, 1, 90, AIType.ALPHANUMERIC, "INTERNAL")
    )

    // FNC1 separator character
    internal const val FNC1 = '\u001d'

    fun parse(data: String, formatDatesForDisplay: Boolean = true): Map<String, String> {
        val parsedData = mutableMapOf<String, String>()
        
        var remainingData = stripSymbologyPrefix(data)

        while (remainingData.isNotEmpty()) {
            // Nach AIs fester Laenge ist kein Trennzeichen vorgesehen; manche Erzeuger
            // setzen dort trotzdem eins. Ueberspringen statt daran zu scheitern.
            if (remainingData[0] == FNC1) {
                remainingData = remainingData.substring(1)
                continue
            }
            var foundAi = false
            for (aiLength in 3 downTo 2) {
                if (remainingData.length >= aiLength) {
                    val potentialAi = remainingData.substring(0, aiLength)
                    if (aiDefinitions.containsKey(potentialAi)) {
                        val ai = aiDefinitions[potentialAi]!!
                        val dataField = remainingData.substring(aiLength)
                        val result = extractData(dataField, ai)

                        parsedData[potentialAi] = if (ai.type == AIType.DATE && formatDatesForDisplay) formatDateForDisplay(result.value) else result.value
                        remainingData = result.remainingData
                        foundAi = true
                        break
                    }
                }
            }

            if (!foundAi) {
                Log.w("GS1Parser", "No matching AI found for remaining data: $remainingData")
                if (remainingData.isNotEmpty()) {
                    parsedData["unknown"] = remainingData
                }
                break
            }
        }
        return parsedData
    }

    /**
     * Entfernt AIM-Kennungen (z.B. ]d2, ]C1) und fuehrende FNC1.
     *
     * Bewusst in einer Schleife: manche Etiketten tragen die Kennung zusaetzlich als
     * Nutzdaten im Symbol, sodass sie nach der Kennung des Decoders ein zweites Mal
     * auftaucht. Ein Elementstring beginnt nie mit "]" – die AI ist immer numerisch
     * und "]" gehoert nicht zum GS1-Zeichensatz 82 -, deshalb ist das gefahrlos.
     */
    internal fun stripSymbologyPrefix(data: String): String {
        var result = data
        while (true) {
            result = when {
                result.startsWith("]") && result.length >= 3 -> result.substring(3)
                result.startsWith(FNC1) -> result.substring(1)
                else -> return result
            }
        }
    }

    /** Ergebnis der Strukturpruefung eines Elementstrings. */
    data class Validation(
        /** true, wenn der gesamte Inhalt als Kette bekannter AIs aufgeht. */
        val complete: Boolean,
        /** Anzahl erkannter AIs. */
        val aiCount: Int,
        /** Rest, der sich nicht mehr zuordnen liess; null wenn alles aufging. */
        val unparsedRest: String?,
        /** true, wenn alle erkannten AIs die Plausibilitaetspruefung bestehen. */
        val plausible: Boolean
    )

    /**
     * Prueft, ob [data] vollstaendig als Kette bekannter Application Identifier aufgeht.
     *
     * Dient als zweites, inhaltliches Signal neben der AIM-Symbologiekennung: ohne
     * Kennung kann ein Code hoechstens "wahrscheinlich GS1" sein, und bei vorhandener
     * Kennung zeigt [Validation.unparsedRest], ab wo die Daten nicht mehr aufgehen.
     *
     * Achtung: [aiDefinitions] kennt nur zwei- und dreistellige AIs. Vierstellige wie
     * (3103) oder (7003) liefern deshalb einen Rest, obwohl der Code gueltig ist.
     */
    fun validate(data: String): Validation {
        var remainingData = stripSymbologyPrefix(data)
        var aiCount = 0
        var plausible = true

        while (remainingData.isNotEmpty()) {
            if (remainingData[0] == FNC1) {
                remainingData = remainingData.substring(1)
                continue
            }
            var foundAi = false
            for (aiLength in 3 downTo 2) {
                if (remainingData.length < aiLength) continue
                val potentialAi = remainingData.substring(0, aiLength)
                val ai = aiDefinitions[potentialAi] ?: continue
                val result = extractData(remainingData.substring(aiLength), ai)
                // Wert leer oder bei fester Laenge abgeschnitten -> Kette geht nicht auf
                if (result.value.isEmpty() || (ai.length > 0 && result.value.length != ai.length)) {
                    return Validation(false, aiCount, remainingData, plausible)
                }
                if (!checkPlausibility(potentialAi, result.value).first) plausible = false
                remainingData = result.remainingData
                aiCount++
                foundAi = true
                break
            }
            if (!foundAi) return Validation(false, aiCount, remainingData, plausible)
        }
        return Validation(true, aiCount, null, plausible)
    }

    private fun extractData(data: String, ai: AI): ExtractionResult {
        return if (ai.length > 0) { // Fixed length
            if (data.length >= ai.length) {
                val value = data.substring(0, ai.length)
                val remaining = data.substring(ai.length)
                ExtractionResult(value, remaining)
            } else {
                ExtractionResult(data, "")
            }
        } else { // Variable length
            val separatorIndex = data.indexOf(FNC1)
            if (separatorIndex != -1) {
                val value = data.substring(0, separatorIndex)
                val remaining = data.substring(separatorIndex + 1)
                ExtractionResult(value, remaining)
            } else {
                ExtractionResult(data, "")
            }
        }
    }
    private fun formatDateForDisplay(rawDate: String): String {
        if (rawDate.length != 6 || !rawDate.all { it.isDigit() }) return rawDate
        return try {
            val parser = SimpleDateFormat("yyMMdd", Locale.US).apply { isLenient = false }
            val parsed = parser.parse(rawDate) ?: return rawDate
            val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            formatter.format(parsed)
        } catch (_: Exception) {
            rawDate
        }
    }
    fun checkPlausibility(ai: String, value: String): Pair<Boolean, String> {
        val definition = aiDefinitions[ai] ?: return Pair(false, "Unbekannter AI")

        if (value.length > definition.maxLength || value.length < definition.minLength)
        {
            return Pair(false, "AI hat falsche Länge (max. ${definition.maxLength})")
        }

        when (definition.type) {
            AIType.NUMERIC -> if (!value.all { it.isDigit() }) return Pair(false, "Nur Zahlen erlaubt")
            AIType.ALPHANUMERIC -> if (value.isEmpty()) return Pair(false, "Wert ist leer")
            AIType.DATE -> {
                if (value.length != 6 || !value.all { it.isDigit() }) return Pair(false, "Datum muss JJMMTT sein")
                try {
                    val sdf = SimpleDateFormat("yyMMdd", Locale.US)
                    sdf.isLenient = false
                    sdf.parse(value)
                } catch (e: Exception) {
                    return Pair(false, "Ungültiges Datum")
                }
            }
        }

        // Prüfziffern-Checks
        if (ai == "01" || ai == "02") {
            if (!isValidGtin(value)) return Pair(false, "GTIN Prüfziffer falsch")
        } else if (ai == "00") {
            if (!isValidSscc(value)) return Pair(false, "SSCC Prüfziffer falsch")
        }

        return Pair(true, "OK")
    }

    private fun isValidGtin(gtin: String): Boolean {
        if (gtin.length != 14) return false
        return checkLuhn(gtin)
    }

    private fun isValidSscc(sscc: String): Boolean {
        if (sscc.length != 18) return false
        return checkLuhn(sscc)
    }

    private fun checkLuhn(code: String): Boolean {
        val digits = code.map { it.toString().toInt() }
        val checkDigit = digits.last()
        val payload = digits.dropLast(1).reversed()
        
        var sum = 0
        for ((i, digit) in payload.withIndex()) {
            sum += if (i % 2 == 0) digit * 3 else digit
        }
        
        val calculated = (10 - (sum % 10)) % 10
        return checkDigit == calculated
    }
}

internal data class AI(val length: Int, val minLength: Int, val maxLength: Int, val type: AIType, val name: String? = "NONE")
internal enum class AIType { NUMERIC, ALPHANUMERIC, DATE }
private data class ExtractionResult(val value: String, val remainingData: String)


