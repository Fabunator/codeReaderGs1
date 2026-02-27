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

    fun parse(data: String): Map<String, String> {
        val parsedData = mutableMapOf<String, String>()
        
        // Entferne AIM Identifier (z.B. ]d2, ]C1) oder führendes FNC1
        var remainingData = when {
            data.startsWith("]") && data.length >= 3 -> data.substring(3)
            data.startsWith(FNC1) -> data.substring(1)
            else -> data
        }

        while (remainingData.isNotEmpty()) {
            var foundAi = false
            for (aiLength in 3 downTo 2) {
                if (remainingData.length >= aiLength) {
                    val potentialAi = remainingData.substring(0, aiLength)
                    if (aiDefinitions.containsKey(potentialAi)) {
                        val ai = aiDefinitions[potentialAi]!!
                        val dataField = remainingData.substring(aiLength)
                        val result = extractData(dataField, ai)

                        parsedData[potentialAi] = result.value
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
