package com.example.codescannergs1

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Locale

object GS1Parser {

    private val aiDefinitions = mapOf(
        // Fixed length AIs
        "00" to AI(18, AIType.NUMERIC),
        "01" to AI(14, AIType.NUMERIC),
        "02" to AI(14, AIType.NUMERIC),
        "11" to AI(6, AIType.DATE),
        "13" to AI(6, AIType.DATE),
        "15" to AI(6, AIType.DATE),
        "17" to AI(6, AIType.DATE),

        // Variable length AIs
        "10" to AI(-1, AIType.ALPHANUMERIC), // Batch/Lot, max 20
        "21" to AI(-1, AIType.ALPHANUMERIC) // Serial Number, max 20
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

private data class AI(val length: Int, val type: AIType)
private enum class AIType { NUMERIC, ALPHANUMERIC, DATE }
private data class ExtractionResult(val value: String, val remainingData: String)
