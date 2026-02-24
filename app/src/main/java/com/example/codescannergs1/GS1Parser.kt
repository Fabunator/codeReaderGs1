package com.example.codescannergs1

import android.util.Log

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
        var remainingData = if (data.startsWith("]C1")) data.substring(3) else data

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
}

private data class AI(val length: Int, val type: AIType)
private enum class AIType { NUMERIC, ALPHANUMERIC, DATE }
private data class ExtractionResult(val value: String, val remainingData: String)
