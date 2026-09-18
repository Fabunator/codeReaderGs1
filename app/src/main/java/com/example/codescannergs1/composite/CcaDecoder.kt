package com.example.codescannergs1.composite

/**
 * Decoder fuer den 2D-Anteil (CC-A) eines GS1-Composite-Symbols vom Typ
 * "GS1 DataBar Limited CC-A".
 *
 * Der CC-A-Anteil ist formal ein MicroPDF417-artiges Symbol, verwendet aber eine
 * eigene, stark komprimierte Codierung (ISO/IEC 24723):
 *
 *  1. Modulraster  -> Symbolzeichen (17 Module je Codewort, PDF417-Tabelle)
 *  2. Codewoerter  -> Reed-Solomon-Korrektur ueber GF(929)
 *  3. Datencodewoerter -> Basis-928-Umkehrung in einen Bitstrom
 *  4. Bitstrom     -> GS1-Elementstring (Encodation Method + General Purpose Field)
 *
 * Bei einem DataBar Limited ist der CC-A immer 3 Spalten breit; damit legt die
 * Zeilenzahl (4..8) alle uebrigen Parameter eindeutig fest.
 *
 * Kein Android-Bezug – bewusst rein in Kotlin gehalten, damit die komplette
 * Decodierkette per JVM-Unit-Test gegen Referenzsymbole geprueft werden kann.
 */
object CcaDecoder {

    const val FNC1 = ''

    /** Modulbreite einer CC-A-Zeile bei 3 Datenspalten: 17 + 10 + 17 + 17 + 10 + 1. */
    const val ROW_MODULES = 72

    const val COLUMNS = 3

    /** Spaltenoffsets innerhalb einer Zeile. */
    private val DATA_OFFSETS = intArrayOf(0, 27, 44)
    private const val CENTRE_RAP_OFFSET = 17
    private const val RIGHT_RAP_OFFSET = 61
    private const val STOP_OFFSET = 71

    /**
     * Gueltige CC-A-Varianten bei 3 Spalten (ISO/IEC 24723 Tabelle 9 sowie
     * Tabellen 10/11 fuer die Row Address Patterns), indiziert ueber rows-4.
     */
    data class Variant(
        val rows: Int,
        val dataCodewords: Int,
        val eccCodewords: Int,
        val bitLength: Int,
        val centreRapStart: Int,
        val rightRapStart: Int,
        val clusterStart: Int
    ) {
        val totalCodewords: Int get() = rows * COLUMNS
    }

    val VARIANTS = arrayOf(
        Variant(4, 8, 4, 78, 43, 23, 1),
        Variant(5, 10, 5, 98, 33, 13, 0),
        Variant(6, 12, 6, 118, 37, 17, 1),
        Variant(7, 14, 7, 138, 47, 27, 2),
        Variant(8, 17, 7, 167, 1, 33, 2)
    )

    fun variantForRows(rows: Int): Variant? =
        if (rows in 4..8) VARIANTS[rows - 4] else null

    class CcaException(message: String) : Exception(message)

    /** Ergebnis einer erfolgreichen CC-A-Decodierung. */
    data class Result(
        /** GS1-Elementstring des 2D-Anteils, FNC1 als 0x1D. */
        val elementString: String,
        val rows: Int,
        /** Anzahl per Reed-Solomon korrigierter Codewoerter. */
        val correctedCodewords: Int
    )

    // ----------------------------------------------------------------------
    // Schritt 1: Modulraster -> Codewoerter
    // ----------------------------------------------------------------------

    /**
     * Liest die Codewoerter aus dem abgetasteten Modulraster.
     *
     * @param moduleRows eine Zeile je CC-A-Codewortzeile, jeweils [ROW_MODULES] Module,
     *                   true = dunkel.
     * @throws CcaException wenn RAP-, Stop- oder Clusterpruefung fehlschlaegt.
     */
    fun codewordsFromModules(moduleRows: List<BooleanArray>): Pair<IntArray, Variant> {
        val variant = variantForRows(moduleRows.size)
            ?: throw CcaException("CC-A am DataBar Limited hat 4..8 Zeilen, gefunden: ${moduleRows.size}")

        val codewords = IntArray(variant.totalCodewords)
        var out = 0
        for (row in moduleRows.indices) {
            val bits = moduleRows[row]
            if (bits.size != ROW_MODULES) {
                throw CcaException("Zeile $row hat ${bits.size} statt $ROW_MODULES Module")
            }

            val expectedCluster = (variant.clusterStart + row) % 3
            val expectedCentre = Pdf417Tables.rapCentre[(variant.centreRapStart - 1 + row) % 52]
            val expectedRight = Pdf417Tables.rapSide[(variant.rightRapStart - 1 + row) % 52]

            if (readInt(bits, CENTRE_RAP_OFFSET, 10) != expectedCentre) {
                throw CcaException("Zeile $row: Centre-RAP passt nicht")
            }
            if (readInt(bits, RIGHT_RAP_OFFSET, 10) != expectedRight) {
                throw CcaException("Zeile $row: Right-RAP passt nicht")
            }
            if (!bits[STOP_OFFSET]) {
                throw CcaException("Zeile $row: Stop-Modul fehlt")
            }

            for (offset in DATA_OFFSETS) {
                val pattern = readInt(bits, offset, 17)
                val hit = Pdf417Tables.patternToCodeword[pattern]
                    ?: throw CcaException("Zeile $row: unbekanntes Symbolzeichen")
                if ((hit shr 16) != expectedCluster) {
                    throw CcaException("Zeile $row: Cluster ${hit shr 16} statt $expectedCluster")
                }
                codewords[out++] = hit and 0xFFFF
            }
        }
        return codewords to variant
    }

    private fun readInt(bits: BooleanArray, offset: Int, length: Int): Int {
        var v = 0
        for (i in 0 until length) {
            v = (v shl 1) or if (bits[offset + i]) 1 else 0
        }
        return v
    }

    // ----------------------------------------------------------------------
    // Schritt 2: Reed-Solomon ueber GF(929), Generator 3
    // ----------------------------------------------------------------------

    private const val MOD = 929
    private val EXP = IntArray(MOD - 1)
    private val LOG = IntArray(MOD)

    init {
        var x = 1
        for (i in 0 until MOD - 1) {
            EXP[i] = x
            LOG[x] = i
            x = x * 3 % MOD
        }
    }

    private fun powMod(base: Int, exp: Int): Int {
        var r = 1
        var b = base % MOD
        var e = exp
        while (e > 0) {
            if (e and 1 == 1) r = r * b % MOD
            b = b * b % MOD
            e = e shr 1
        }
        return r
    }

    private fun inv(a: Int) = powMod(a, MOD - 2)

    /** Syndrome S_1..S_ecc (Horner-Auswertung an 3^j). */
    private fun syndromes(codewords: IntArray, ecc: Int): IntArray {
        val syn = IntArray(ecc)
        for (j in 1..ecc) {
            val a = EXP[j % (MOD - 1)]
            var acc = 0
            for (c in codewords) acc = (acc * a + c) % MOD
            syn[j - 1] = acc
        }
        return syn
    }

    /**
     * Korrigiert [codewords] (Daten + ECC, hoechstwertig zuerst) in place.
     *
     * CC-A hat maximal 7 ECC-Codewoerter (also t <= 3) und maximal 24 Codewoerter.
     * Bei diesen Groessen ist eine vollstaendige Suche ueber die Fehlerpositionen
     * guenstig (<= 2024 Kombinationen) und deutlich leichter zu verifizieren als
     * Berlekamp-Massey mit Chien-Suche und Forney-Algorithmus.
     *
     * @return Anzahl korrigierter Codewoerter oder -1, wenn nicht korrigierbar.
     */
    fun reedSolomonCorrect(codewords: IntArray, ecc: Int): Int {
        val n = codewords.size
        val syn = syndromes(codewords, ecc)
        if (syn.all { it == 0 }) return 0

        val t = ecc / 2
        val positions = IntArray(t)

        for (v in 1..t) {
            if (searchErrors(codewords, syn, ecc, n, v, 0, 0, positions)) {
                return v
            }
        }
        return -1
    }

    /** Rekursive Kombinationssuche ueber v Fehlerpositionen. */
    private fun searchErrors(
        codewords: IntArray,
        syn: IntArray,
        ecc: Int,
        n: Int,
        v: Int,
        depth: Int,
        start: Int,
        positions: IntArray
    ): Boolean {
        if (depth == v) {
            val xs = IntArray(v) { EXP[(n - 1 - positions[it]) % (MOD - 1)] }
            val magnitudes = solveVandermonde(xs, syn, v) ?: return false
            if (magnitudes.any { it == 0 }) return false
            // gegen alle Syndrome pruefen
            for (j in 1..ecc) {
                var acc = 0
                for (l in 0 until v) acc = (acc + magnitudes[l] * powMod(xs[l], j)) % MOD
                if (acc != syn[j - 1]) return false
            }
            for (l in 0 until v) {
                val p = positions[l]
                codewords[p] = ((codewords[p] - magnitudes[l]) % MOD + MOD) % MOD
            }
            return true
        }
        for (p in start until n) {
            positions[depth] = p
            if (searchErrors(codewords, syn, ecc, n, v, depth + 1, p + 1, positions)) return true
        }
        return false
    }

    /** Loest S_j = sum_l e_l * X_l^j (j = 1..v) per Gauss-Elimination mod 929. */
    private fun solveVandermonde(xs: IntArray, syn: IntArray, v: Int): IntArray? {
        val m = Array(v) { j -> IntArray(v + 1) }
        for (j in 0 until v) {
            for (l in 0 until v) m[j][l] = powMod(xs[l], j + 1)
            m[j][v] = syn[j]
        }
        for (col in 0 until v) {
            var piv = -1
            for (r in col until v) if (m[r][col] != 0) { piv = r; break }
            if (piv < 0) return null
            val tmp = m[col]; m[col] = m[piv]; m[piv] = tmp
            val iv = inv(m[col][col])
            for (c in col..v) m[col][c] = m[col][c] * iv % MOD
            for (r in 0 until v) {
                if (r != col && m[r][col] != 0) {
                    val f = m[r][col]
                    for (c in col..v) m[r][c] = ((m[r][c] - f * m[col][c]) % MOD + MOD) % MOD
                }
            }
        }
        return IntArray(v) { m[it][v] }
    }

    // ----------------------------------------------------------------------
    // Schritt 3: Basis-928-Codewoerter -> Bitstrom
    // ----------------------------------------------------------------------

    /**
     * Umkehrung der CC-A-Codewortbildung: Bloecke von bis zu 69 Bit werden als
     * Ganzzahl zur Basis 928 in bis zu 7 Codewoerter abgebildet (hoechstwertig zuerst).
     */
    private val BASE_928 = java.math.BigInteger.valueOf(928L)

    fun codewordsToBits(dataCodewords: IntArray, bitLength: Int): BooleanArray {
        val bits = BooleanArray(bitLength)
        var written = 0
        var index = 0
        var remaining = bitLength
        while (remaining > 0) {
            val bitCount = if (remaining < 69) remaining else 69
            val cwCount = bitCount / 10 + 1
            if (index + cwCount > dataCodewords.size) {
                throw CcaException("Codewortblock unvollstaendig")
            }
            // Bis zu 69 Bit je Block passen nicht in einen Long
            var value = java.math.BigInteger.ZERO
            for (k in 0 until cwCount) {
                value = value.multiply(BASE_928)
                    .add(java.math.BigInteger.valueOf(dataCodewords[index + k].toLong()))
            }
            if (value.bitLength() > bitCount) {
                throw CcaException("Basis-928-Block ueberschreitet $bitCount Bit")
            }
            for (i in 0 until bitCount) {
                bits[written + i] = value.testBit(bitCount - 1 - i)
            }
            written += bitCount
            index += cwCount
            remaining -= bitCount
        }
        return bits
    }

    // ----------------------------------------------------------------------
    // Schritt 4: Bitstrom -> GS1-Elementstring
    // ----------------------------------------------------------------------

    private const val ALNUM_PUNCS = "*,-./"
    private const val ISO_PUNCS = "!\"%&'()*+,-./:;<=>?_ "
    private const val TABLE3 = "BDHIJKLNPQRSTVWZ"

    private const val MODE_NUMERIC = 1
    private const val MODE_ALPHANUMERIC = 2
    private const val MODE_ISO646 = 3

    private class BitReader(val bits: BooleanArray) {
        var pos = 0
        fun left() = bits.size - pos
        fun peek(n: Int): Int {
            var v = 0
            for (i in 0 until n) v = (v shl 1) or if (bits[pos + i]) 1 else 0
            return v
        }
        fun read(n: Int): Int {
            if (left() < n) throw CcaException("Bitstrom zu kurz")
            val v = peek(n)
            pos += n
            return v
        }
        fun skip(n: Int) { pos += n }
    }

    /** Komplette Umsetzung des Bitstroms in den GS1-Elementstring. */
    fun decodeBits(bits: BooleanArray): String =
        decodeBitsInternal(BitReader(bits)).trimEnd(FNC1)

    private fun decodeBitsInternal(r: BitReader): String {
        if (r.read(1) == 0) {
            // Encodation Method "0": alles im General Purpose Field
            return decodeGeneralField(r, MODE_NUMERIC)
        }
        return if (r.read(1) == 0) decodeMethodDate(r) else decodeMethodAi90(r)
    }

    /** Encodation Method "10": Datum (AI 11/17) und Chargennummer (AI 10). */
    private fun decodeMethodDate(r: BitReader): String {
        if (r.left() >= 2 && r.peek(2) == 3) {
            // "11" = kein Datum, die Daten beginnen mit dem Wert von AI 10
            r.skip(2)
            return "10" + decodeGeneralField(r, MODE_NUMERIC).trimEnd(FNC1)
        }
        val dateValue = r.read(16)
        val isExpiry = r.read(1) == 1
        val prefix = (if (isExpiry) "17" else "11") + formatDate(dateValue)

        val general = decodeGeneralField(r, MODE_NUMERIC).trimEnd(FNC1)
        return when {
            general.startsWith(FNC1) -> prefix + general.substring(1)
            general.isNotEmpty() -> prefix + "10" + general
            else -> prefix
        }
    }

    /** yy * 384 + (mm - 1) * 32 + dd, Tag 0 bedeutet "kein Tag angegeben". */
    private fun formatDate(value: Int): String {
        val year = value / 384
        val rest = value % 384
        val month = rest / 32 + 1
        val day = rest % 32
        return "%02d%02d%02d".format(year, month, day)
    }

    /** Encodation Method "11": Elementstring beginnt mit AI 90. */
    private fun decodeMethodAi90(r: BitReader): String {
        val ai90Mode = if (r.read(1) == 0) {
            1 // alphanumerisch
        } else {
            if (r.read(1) == 1) 2 else 3 // Alpha bzw. numerisch
        }

        val croppedAi = if (r.read(1) == 0) "" else if (r.read(1) == 0) "21" else "8004"

        val first = r.read(5)
        val numericValue: Int
        val letter: Char
        if (first < 31) {
            numericValue = first
            letter = TABLE3[r.read(4)]
        } else {
            numericValue = r.read(10)
            letter = (65 + r.read(5)).toChar()
        }

        val head = StringBuilder("90")
        if (numericValue != 0) head.append(numericValue)
        head.append(letter)

        val general: String
        if (ai90Mode == 2) {
            // Alpha-Codierung nach ISO/IEC 24723 5.3.3
            while (r.left() >= 5) {
                val v = r.peek(5)
                if (v == 31) {
                    r.skip(5)
                    head.append(FNC1)
                    break
                }
                if (v <= 25) {
                    r.skip(5)
                    head.append((65 + v).toChar())
                } else {
                    if (r.left() < 6) break
                    head.append((r.read(6) - 4).toChar())
                }
            }
            general = decodeGeneralField(r, MODE_NUMERIC)
        } else {
            general = decodeGeneralField(
                r,
                if (ai90Mode == 1) MODE_ALPHANUMERIC else MODE_NUMERIC
            )
        }

        var result = (head.toString() + general).trimEnd(FNC1)
        if (croppedAi.isNotEmpty()) {
            val i = result.indexOf(FNC1)
            if (i >= 0) result = result.substring(0, i + 1) + croppedAi + result.substring(i + 1)
        }
        return result
    }

    /**
     * General Purpose Data Compaction (ISO/IEC 24723 5.4 bzw. ISO/IEC 24724 7.2.5.5)
     * mit den drei Modi Numeric, Alphanumeric und ISO/IEC 646.
     */
    private fun decodeGeneralField(r: BitReader, startMode: Int): String {
        val out = StringBuilder()
        var mode = startMode
        loop@ while (true) {
            when (mode) {
                MODE_NUMERIC -> {
                    if (r.left() < 4) break@loop
                    if (r.left() < 7) {
                        // Restbits: entweder Alphanumeric-Latch (Fuellmuster) oder letzte Ziffer
                        val v = r.read(4)
                        if (v == 0) { mode = MODE_ALPHANUMERIC; continue@loop }
                        out.append(('0' + (v - 1)))
                        break@loop
                    }
                    if (r.peek(4) == 0) {
                        r.skip(4)
                        mode = MODE_ALPHANUMERIC
                        continue@loop
                    }
                    val v = r.read(7) - 8
                    appendDigitPair(out, v / 11, v % 11)
                }

                MODE_ALPHANUMERIC -> {
                    if (r.left() < 3) break@loop
                    if (r.peek(3) == 0) { r.skip(3); mode = MODE_NUMERIC; continue@loop }
                    if (r.left() < 5) break@loop
                    when (val v5 = r.peek(5)) {
                        4 -> { r.skip(5); mode = MODE_ISO646 }
                        15 -> { r.skip(5); out.append(FNC1); mode = MODE_NUMERIC }
                        in 5..14 -> { r.skip(5); out.append(('0' + (v5 - 5))) }
                        else -> {
                            if (r.left() < 6) break@loop
                            when (val v6 = r.read(6)) {
                                in 32..57 -> out.append((v6 + 33).toChar())
                                in 58..62 -> out.append(ALNUM_PUNCS[v6 - 58])
                                else -> throw CcaException("Ungueltiges Alphanumeric-Zeichen $v6")
                            }
                        }
                    }
                }

                else -> { // MODE_ISO646
                    if (r.left() < 3) break@loop
                    if (r.peek(3) == 0) { r.skip(3); mode = MODE_NUMERIC; continue@loop }
                    if (r.left() < 5) break@loop
                    when (val v5 = r.peek(5)) {
                        4 -> { r.skip(5); mode = MODE_ALPHANUMERIC }
                        15 -> { r.skip(5); out.append(FNC1); mode = MODE_NUMERIC }
                        in 5..14 -> { r.skip(5); out.append(('0' + (v5 - 5))) }
                        else -> {
                            if (r.left() < 7) break@loop
                            val v7 = r.peek(7)
                            when {
                                v7 in 64..89 -> { r.skip(7); out.append((v7 + 1).toChar()) }
                                v7 in 90..115 -> { r.skip(7); out.append((v7 + 7).toChar()) }
                                else -> {
                                    if (r.left() < 8) break@loop
                                    val v8 = r.read(8)
                                    if (v8 in 232..252) out.append(ISO_PUNCS[v8 - 232])
                                    else throw CcaException("Ungueltiges ISO646-Zeichen $v8")
                                }
                            }
                        }
                    }
                }
            }
        }
        return out.toString()
    }

    private fun appendDigitPair(out: StringBuilder, d1: Int, d2: Int) {
        out.append(if (d1 == 10) FNC1 else ('0' + d1))
        out.append(if (d2 == 10) FNC1 else ('0' + d2))
    }

    // ----------------------------------------------------------------------
    // Gesamtkette
    // ----------------------------------------------------------------------

    /** Decodiert den CC-A-Anteil aus dem abgetasteten Modulraster. */
    fun decodeModuleRows(moduleRows: List<BooleanArray>): Result {
        val (codewords, variant) = codewordsFromModules(moduleRows)
        val corrected = reedSolomonCorrect(codewords, variant.eccCodewords)
        if (corrected < 0) throw CcaException("Reed-Solomon-Korrektur fehlgeschlagen")
        val bits = codewordsToBits(
            codewords.copyOfRange(0, variant.dataCodewords),
            variant.bitLength
        )
        return Result(decodeBits(bits), variant.rows, corrected)
    }
}
