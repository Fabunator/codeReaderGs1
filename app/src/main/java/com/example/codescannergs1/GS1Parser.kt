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
        var remainingData = if (data.startsWith("]")) data.substring(3) else data

        while (remainingData.isNotEmpty()) {
            var foundAi = false
            // Iterate through possible AI lengths (3, 2)
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
                Log.w("GS1Parser", "No matching AI found for: $remainingData")
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
            val value = data.substring(0, ai.length)
            val remaining = data.substring(ai.length)
            ExtractionResult(value, remaining)
        } else { // Variable length
            val separatorIndex = data.indexOf(FNC1)
            if (separatorIndex != -1) {
                val value = data.substring(0, separatorIndex)
                val remaining = data.substring(separatorIndex + 1)
                ExtractionResult(value, remaining)
            } else {
                // No separator, this is the last element
                ExtractionResult(data, "")
            }
        }
    }

    fun checkPlausibility(ai: String, value: String): Pair<Boolean, String> {
        val definition = aiDefinitions[ai]
        if (definition == null) {
            return Pair(false, "Unknown AI")
        }

        when (definition.type) {
            AIType.NUMERIC -> if (!value.all { it.isDigit() }) return Pair(false, "Value is not numeric")
            AIType.ALPHANUMERIC -> if (!value.all { it.isLetterOrDigit() }) return Pair(false, "Value is not alphanumeric")
            AIType.DATE -> {
                if (!value.all { it.isDigit() }) return Pair(false, "Date is not numeric")
                if (value.length != 6) return Pair(false, "Date must be 6 digits")
                try {
                    val date = SimpleDateFormat("yyMMdd", Locale.US).parse(value)
                    if (date == null) return Pair(false, "Invalid date format")
                } catch (e: Exception) {
                    return Pair(false, "Invalid date format")
                }
            }
        }

        if (ai == "01" || ai == "02") { // GTIN
            if (!isValidGtin(value)) return Pair(false, "Invalid GTIN check digit")
        } else if (ai == "00") { // SSCC
            if (!isValidSscc(value)) return Pair(false, "Invalid SSCC check digit")
        }

        return Pair(true, "OK")
    }

    private fun isValidGtin(gtin: String): Boolean {
        if (gtin.length != 14) return false
        val checkDigit = gtin.last().toString().toInt()
        val payload = gtin.substring(0, 13)
        var sum = 0
        for ((index, char) in payload.withIndex()) {
            val digit = char.toString().toInt()
            sum += if (index % 2 == 0) digit * 3 else digit
        }
        val calculatedCheckDigit = (10 - (sum % 10)) % 10
        return checkDigit == calculatedCheckDigit
    }

    private fun isValidSscc(sscc: String): Boolean {
        if (sscc.length != 18) return false
        val checkDigit = sscc.last().toString().toInt()
        val payload = sscc.substring(0, 17)
        var sum = 0
        for ((index, char) in payload.withIndex()) {
            val digit = char.toString().toInt()
            sum += if (index % 2 == 0) digit * 3 else digit
        }
        val calculatedCheckDigit = (10 - (sum % 10)) % 10
        return checkDigit == calculatedCheckDigit
    }
}

private data class AI(val length: Int, val type: AIType)
private enum class AIType { NUMERIC, ALPHANUMERIC, DATE }
private data class ExtractionResult(val value: String, val remainingData: String)
