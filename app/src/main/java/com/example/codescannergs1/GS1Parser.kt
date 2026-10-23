package com.example.codescannergs1

import android.util.Log
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Locale

object GS1Parser {

    /**
     * Alle GS1 Application Identifier, erzeugt aus [GS1_SYNTAX_DICTIONARY].
     *
     * AI-Bereiche wie `3100-3105` werden zu Einzeleintraegen aufgeloest, damit die
     * Suche ein einfacher Map-Zugriff bleibt. Die Menge ist praefixfrei (keine AI ist
     * Anfang einer anderen), deshalb passt beim Probieren von 4 bis 2 Stellen immer
     * hoechstens eine Laenge.
     */
    internal val aiDefinitions: Map<String, AI> by lazy {
        parseDictionary(GS1_SYNTAX_DICTIONARY, parseDescriptions(GS1_AI_DESCRIPTIONS))
    }

    /** Laengen, die eine AI haben kann – von lang nach kurz probiert. */
    private val AI_LENGTHS = 4 downTo 2

    /** Titel, die im Dictionary fehlen. */
    private val FALLBACK_TITLES = mapOf(
        "8110" to "COUPON CODE (NORTH AMERICA)",
        "8112" to "PAPERLESS COUPON (NORTH AMERICA)"
    )

    // FNC1 separator character
    internal const val FNC1 = '\u001d'

    /**
     * Zerlegt einen Elementstring in AI → Wert.
     *
     * @param formatForDisplay true: Datumswerte als Datum, AIs mit implizitem
     *        Dezimalkomma (310n–369n, 390n–395n) als Dezimalzahl, z. B. (3103)001250
     *        → "1,250". false: Rohwerte wie im Symbol.
     */
    fun parse(data: String, formatForDisplay: Boolean = true): Map<String, String> {
        val parsedData = mutableMapOf<String, String>()

        var remainingData = stripSymbologyPrefix(data)

        while (remainingData.isNotEmpty()) {
            // Nach AIs fester Laenge ist kein Trennzeichen vorgesehen; manche Erzeuger
            // setzen dort trotzdem eins. Ueberspringen statt daran zu scheitern.
            if (remainingData[0] == FNC1) {
                remainingData = remainingData.substring(1)
                continue
            }
            val match = matchAi(remainingData)
            if (match == null) {
                Log.w("GS1Parser", "No matching AI found for remaining data: $remainingData")
                parsedData["unknown"] = remainingData
                break
            }
            val (code, ai) = match
            val result = extractData(remainingData.substring(code.length), ai)
            parsedData[code] = if (formatForDisplay) formatValue(ai, result.value) else result.value
            remainingData = result.remainingData
        }
        return parsedData
    }

    /** Findet die AI am Anfang von [data]. */
    private fun matchAi(data: String): Pair<String, AI>? {
        for (aiLength in AI_LENGTHS) {
            if (data.length < aiLength) continue
            val code = data.substring(0, aiLength)
            aiDefinitions[code]?.let { return code to it }
        }
        return null
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
            val (code, ai) = matchAi(remainingData)
                ?: return Validation(false, aiCount, remainingData, plausible)
            val result = extractData(remainingData.substring(code.length), ai)
            // Wert leer oder bei fester Laenge abgeschnitten -> Kette geht nicht auf
            if (result.value.isEmpty() || (ai.length > 0 && result.value.length != ai.length)) {
                return Validation(false, aiCount, remainingData, plausible)
            }
            if (!checkPlausibility(code, result.value).first) plausible = false
            remainingData = result.remainingData
            aiCount++
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

    // ------------------------------------------------------------------
    // Anzeige
    // ------------------------------------------------------------------

    private fun formatValue(ai: AI, raw: String): String = when {
        ai.type == AIType.DATE -> formatDateForDisplay(raw)
        ai.decimals != null -> formatDecimalAiValue(ai, raw, Locale.getDefault())
        else -> raw
    }

    /**
     * Wert einer AI mit implizitem Dezimalkomma. Die Nachkommastellen stehen in der
     * letzten Ziffer der AI ([AI.decimals]) und gelten fuer die letzte Komponente –
     * bei (391n)/(393n) steht davor der dreistellige ISO-4217-Waehrungscode.
     */
    internal fun formatDecimalAiValue(ai: AI, raw: String, locale: Locale): String {
        val decimals = ai.decimals ?: return raw
        val prefixLength = ai.components.dropLast(1).sumOf { it.maxLength }
        if (raw.length <= prefixLength || !raw.all { it.isDigit() }) return raw
        val amount = insertDecimalPoint(raw.substring(prefixLength), decimals, locale)
        return if (prefixLength > 0) "$amount (${raw.substring(0, prefixLength)})" else amount
    }

    /** "001250", 3 → "1,250" (deutsch) bzw. "1.250" (englisch). */
    internal fun insertDecimalPoint(digits: String, decimals: Int, locale: Locale): String {
        if (decimals <= 0) return digits.trimStart('0').ifEmpty { "0" }
        val padded = digits.padStart(decimals + 1, '0')
        val intPart = padded.dropLast(decimals).trimStart('0').ifEmpty { "0" }
        val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        return intPart + separator + padded.takeLast(decimals)
    }

    private fun formatDateForDisplay(rawDate: String): String {
        if (rawDate.length != 6 || !rawDate.all { it.isDigit() }) return rawDate
        return try {
            // Tag "00" heisst laut GS1 "ohne Tagesangabe" – dann nur Monat und Jahr
            val withoutDay = rawDate.endsWith("00")
            val parser = SimpleDateFormat(if (withoutDay) "yyMM" else "yyMMdd", Locale.US).apply { isLenient = false }
            val parsed = parser.parse(if (withoutDay) rawDate.take(4) else rawDate) ?: return rawDate
            val formatter = SimpleDateFormat(if (withoutDay) "MMM yyyy" else "dd MMM yyyy", Locale.getDefault())
            formatter.format(parsed)
        } catch (_: Exception) {
            rawDate
        }
    }

    // ------------------------------------------------------------------
    // Plausibilitaet
    // ------------------------------------------------------------------

    /**
     * Prueft einen AI-Wert gegen die Spezifikation aus dem Syntax Dictionary:
     * Laenge, Zeichensatz je Komponente und die Linter, die hier umgesetzt sind
     * (siehe [checkLinter]). Nicht umgesetzte Linter – etwa Laendercodes oder IBAN –
     * werden uebergangen.
     *
     * Die Meldungstexte wertet `buildPlausibilityHint` in MainActivity aus; beim
     * Aendern dort mitziehen.
     */
    fun checkPlausibility(ai: String, value: String): Pair<Boolean, String> {
        val definition = aiDefinitions[ai] ?: return Pair(false, "Unbekannter AI")

        if (value.length > definition.maxLength || value.length < definition.minLength) {
            return Pair(false, "AI hat falsche Länge (max. ${definition.maxLength})")
        }
        if (value.isEmpty()) return Pair(false, "Wert ist leer")

        var pos = 0
        for (component in definition.components) {
            if (pos >= value.length) {
                if (component.optional) break
                return Pair(false, "AI hat falsche Länge (max. ${definition.maxLength})")
            }
            val end = minOf(value.length, pos + component.maxLength)
            if (end - pos < component.minLength) {
                return Pair(false, "AI hat falsche Länge (max. ${definition.maxLength})")
            }
            val part = value.substring(pos, end)
            pos = end

            checkCharset(component.charset, part)?.let { return Pair(false, it) }
            for (linter in component.linters) {
                checkLinter(linter, part)?.let { return Pair(false, it) }
            }
        }
        if (pos < value.length) return Pair(false, "AI hat falsche Länge (max. ${definition.maxLength})")

        return Pair(true, "OK")
    }

    private const val CSET82 = "!\"%&'()*+,-./0123456789:;<=>?ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz"
    private const val CSET39 = "#-/0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val CSET32 = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val BASE64URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_="

    private fun checkCharset(charset: Char, part: String): String? {
        val allowed = when (charset) {
            'N' -> return if (part.all { it in '0'..'9' }) null else "Nur Zahlen erlaubt"
            'X' -> CSET82
            'Y' -> CSET39
            'Z' -> BASE64URL
            else -> return null
        }
        val bad = part.firstOrNull { it !in allowed } ?: return null
        return "Unzulässiges Zeichen '$bad'"
    }

    /** Die umgesetzten Linter des Syntax Dictionary; null = bestanden oder nicht umgesetzt. */
    private fun checkLinter(linter: String, part: String): String? = when (linter) {
        "csum" -> if (checkMod10(part)) null else "Prüfziffer falsch"
        "csumalpha" -> if (checkAlphaCheckPair(part)) null else "Prüfzeichenpaar falsch"
        "yymmdd" -> if (isValidDate(part, allowDayZero = false)) null else "Ungültiges Datum"
        "yymmd0" -> if (isValidDate(part, allowDayZero = true)) null else "Ungültiges Datum"
        "yyyymmdd" -> if (isValidDate(part.drop(2), allowDayZero = false)) null else "Ungültiges Datum"
        "hhmi" -> if (part.length == 4 && part.take(2).toInt() < 24 && part.drop(2).toInt() < 60) null else "Ungültige Uhrzeit"
        "hh" -> if (part.toInt() < 24) null else "Ungültige Uhrzeit"
        "mi", "ss" -> if (part.toInt() < 60) null else "Ungültige Uhrzeit"
        "yesno" -> if (part == "0" || part == "1") null else "Nur 0 oder 1 erlaubt"
        "zero" -> if (part.all { it == '0' }) null else "Muss 0 sein"
        "nonzero" -> if (part.any { it != '0' }) null else "Darf nicht 0 sein"
        "nozeroprefix" -> if (part == "0" || !part.startsWith("0")) null else "Führende Null nicht erlaubt"
        "hasnondigit" -> if (part.any { !it.isDigit() }) null else "Muss ein Nicht-Ziffern-Zeichen enthalten"
        "hyphen" -> if (part == "-") null else "Nur '-' erlaubt"
        "winding" -> if (part in setOf("0", "1", "9")) null else "Wickelrichtung muss 0, 1 oder 9 sein"
        "pieceoftotal" -> {
            val piece = part.take(2).toInt()
            val total = part.drop(2).toInt()
            if (piece in 1..total) null else "Teil/Gesamt ungültig"
        }
        else -> null
    }

    private fun isValidDate(yymmdd: String, allowDayZero: Boolean): Boolean {
        if (yymmdd.length != 6 || !yymmdd.all { it.isDigit() }) return false
        val year = 2000 + yymmdd.substring(0, 2).toInt()
        val month = yymmdd.substring(2, 4).toInt()
        val day = yymmdd.substring(4, 6).toInt()
        if (month !in 1..12) return false
        if (day == 0) return allowDayZero
        val leap = year % 4 == 0
        val days = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        return day <= days[month - 1]
    }

    /** GS1-Pruefziffer (Modulo 10, Gewichte 3/1 von rechts) – GTIN, SSCC, GLN, GSRN … */
    private fun checkMod10(code: String): Boolean {
        if (code.length < 2 || !code.all { it.isDigit() }) return false
        val digits = code.map { it - '0' }
        val payload = digits.dropLast(1).reversed()
        var sum = 0
        for ((i, digit) in payload.withIndex()) {
            sum += if (i % 2 == 0) digit * 3 else digit
        }
        return digits.last() == (10 - (sum % 10)) % 10
    }

    /**
     * GS1-Pruefzeichenpaar fuer alphanumerische Schluessel (GMN, CPID …):
     * Zeichenwerte nach CSET 82, gewichtet mit Primzahlen von rechts (2, 3, 5 …),
     * Summe mod 1021, als zwei Zeichen aus CSET 32.
     */
    private val PRIMES = intArrayOf(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83)

    private fun checkAlphaCheckPair(value: String): Boolean {
        val n = value.length - 2
        if (n < 1 || n > PRIMES.size) return false
        var sum = 0
        for (i in 0 until n) {
            val v = CSET82.indexOf(value[i])
            if (v < 0) return false
            sum += v * PRIMES[n - 1 - i]
        }
        sum %= 1021
        return value[n] == CSET32[sum shr 5] && value[n + 1] == CSET32[sum and 31]
    }

    // ------------------------------------------------------------------
    // Dictionary einlesen
    // ------------------------------------------------------------------

    private val COMPONENT = Regex("""^(\[)?([NXYZ])(\.\.)?(\d+)(])?((?:,\w+)*)$""")

    /**
     * Liest die langen Beschreibungen (Zeilen "AI<TAB>Text" oder "AI-AI<TAB>Text")
     * und loest Bereiche zu Einzeleintraegen auf.
     */
    internal fun parseDescriptions(text: String): Map<String, String> {
        val result = HashMap<String, String>()
        for (rawLine in text.lineSequence()) {
            val tab = rawLine.indexOf('\t')
            if (tab < 0) continue
            val description = rawLine.substring(tab + 1).trim()
            if (description.isEmpty()) continue
            val range = rawLine.substring(0, tab).trim().split('-')
            val first = range[0]
            val last = range.getOrElse(1) { first }
            for (n in first.toInt()..last.toInt()) {
                result[n.toString().padStart(first.length, '0')] = description
            }
        }
        return result
    }

    /** Liest das GS1 Barcode Syntax Dictionary, siehe Kopf von Gs1AiDictionary.kt. */
    internal fun parseDictionary(text: String, descriptions: Map<String, String> = emptyMap()): Map<String, AI> {
        val result = LinkedHashMap<String, AI>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            // Der Titel folgt dem ersten "#" – er kann selbst "#" enthalten ("CERT # 1")
            val hash = line.indexOf('#')
            val title = if (hash >= 0) line.substring(hash + 1).trim().ifEmpty { null } else null
            val tokens = (if (hash >= 0) line.substring(0, hash) else line).trim().split(Regex("\\s+"))

            val flags = tokens.getOrNull(1)?.takeIf { t -> t.none { it.isLetterOrDigit() || it == '[' } } ?: ""
            val components = tokens.drop(1).mapNotNull { token ->
                val m = COMPONENT.matchEntire(token) ?: return@mapNotNull null
                val (open, charset, variable, len, _, linters) = m.destructured
                val max = len.toInt()
                AiComponent(
                    charset = charset[0],
                    minLength = if (variable.isEmpty()) max else 1,
                    maxLength = max,
                    optional = open.isNotEmpty(),
                    linters = linters.split(',').filter { it.isNotEmpty() }
                )
            }
            require(components.isNotEmpty()) { "Keine Spezifikation in: $line" }

            val range = tokens[0].split('-')
            val first = range[0]
            val last = range.getOrElse(1) { first }
            for (n in first.toInt()..last.toInt()) {
                val code = n.toString().padStart(first.length, '0')
                result[code] = buildAi(code, flags, components, title ?: FALLBACK_TITLES[code])
                    .copy(description = descriptions[code])
            }
        }
        return result
    }

    private fun buildAi(code: String, flags: String, components: List<AiComponent>, title: String?): AI {
        val fixed = components.none { it.optional || it.minLength != it.maxLength }
        val min = components.filter { !it.optional }.sumOf { it.minLength }
        val max = components.sumOf { it.maxLength }
        val type = when {
            components.size == 1 && components[0].maxLength == 6 &&
                components[0].linters.any { it == "yymmdd" || it == "yymmd0" } -> AIType.DATE
            components.all { it.charset == 'N' } -> AIType.NUMERIC
            else -> AIType.ALPHANUMERIC
        }
        return AI(
            length = if (fixed) max else -1,
            minLength = min,
            maxLength = max,
            type = type,
            name = title,
            components = components,
            fnc1Required = '*' !in flags,
            decimals = impliedDecimals(code)
        )
    }

    /**
     * Anzahl Nachkommastellen fuer AIs mit implizitem Dezimalkomma: bei 310n–369n
     * (Masse und Gewichte) und 390n–395n (Betraege, Preise, Rabatt) gibt die vierte
     * Ziffer an, wie viele der Stellen hinter dem Komma stehen.
     */
    private fun impliedDecimals(code: String): Int? {
        if (code.length != 4) return null
        val prefix2 = code.take(2).toInt()
        val prefix3 = code.take(3).toInt()
        return if (prefix2 in 31..36 || prefix3 in 390..395) code[3] - '0' else null
    }
}

/** Eine Komponente des Datenfelds, z. B. "N6,yymmdd" oder "[X..17]". */
internal data class AiComponent(
    val charset: Char,
    val minLength: Int,
    val maxLength: Int,
    val optional: Boolean,
    val linters: List<String>
)

/**
 * @param length feste Laenge des Datenfelds, oder -1 bei variabler Laenge
 * @param fnc1Required true, wenn nach dem Feld ein FNC1 folgen muss (sofern es nicht
 *        am Ende steht) – das gilt auch fuer manche AIs fester Laenge, etwa (7003)
 * @param decimals implizite Nachkommastellen, siehe GS1Parser.impliedDecimals
 * @param description ausfuehrliche Bezeichnung von GS1, z. B. "Net weight, kilograms
 *        (variable measure trade item)" – [name] ist nur das Kurzzeichen ("NET WEIGHT (kg)")
 */
internal data class AI(
    val length: Int,
    val minLength: Int,
    val maxLength: Int,
    val type: AIType,
    val name: String? = "NONE",
    val components: List<AiComponent> = emptyList(),
    val fnc1Required: Boolean = length < 0,
    val decimals: Int? = null,
    val description: String? = null
)
internal enum class AIType { NUMERIC, ALPHANUMERIC, DATE }
private data class ExtractionResult(val value: String, val remainingData: String)
