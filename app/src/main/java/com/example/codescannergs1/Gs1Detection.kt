package com.example.codescannergs1

/**
 * Einstufung eines gelesenen Codes als GS1-Elementstring.
 *
 * Entscheidend ist allein FNC1 an erster Symbolposition. Nach ISO/IEC 15424 steht
 * das in der AIM-Symbologiekennung: `]C1` statt `]C0` bei Code 128, `]d2` statt
 * `]d1` bei Data Matrix, `]Q3` statt `]Q1` bei QR. Der Inhalt allein reicht nicht –
 * ein normaler Code 128 mit dem Inhalt `0104012345678901` ist byteweise identisch
 * mit dem entsprechenden GS1-128, und auch ein Gruppentrennzeichen im Inhalt ist
 * kein Beleg: Code 128, Data Matrix und QR koennen ASCII 29 regulaer codieren.
 *
 * zxing-cpp liefert die Kennung mit. ML Kit nicht – Codes, die nur ML Kit findet,
 * koennen daher hoechstens als [Gs1Level.PROBABLE] eingestuft werden.
 */
enum class Gs1Level {
    /** Keine GS1-Daten. */
    NONE,

    /** Keine Kennung vorhanden, der Inhalt geht aber vollstaendig als AI-Kette auf. */
    PROBABLE,

    /** Die AIM-Kennung weist FNC1 an erster Position aus. */
    CONFIRMED
}

data class Gs1Classification(
    val level: Gs1Level,
    /** AIM-Symbologiekennung, etwa "]C1"; null, wenn der Decoder keine liefert. */
    val symbologyId: String?,
    /**
     * Teil des Inhalts, der sich nicht mehr als AI zuordnen liess.
     * null, wenn der gesamte Elementstring aufging.
     */
    val unparsedRest: String?,
    /**
     * Symbologiekennung, die faelschlich *im Symbol* codiert ist.
     *
     * Die Kennung stellt der Lesegeraet voran, sie gehoert nicht in die Nutzdaten.
     * Steht sie trotzdem drin, liefert jedes normgerechte Lesegeraet sie doppelt und
     * GS1-Parser scheitern daran – ein Etikettenfehler, der gemeldet werden muss.
     */
    val embeddedSymbologyId: String?
) {
    val isGs1: Boolean get() = level != Gs1Level.NONE
}

object Gs1Detector {

    /**
     * AIM-Modifier, die FNC1 an *erster* Position bedeuten (ISO/IEC 15424).
     * `]C2`, `]d3` und `]Q5` stehen fuer FNC1 an zweiter Position – das ist die
     * AIM-Branchenkennzeichnung, kein GS1, und faellt hier bewusst heraus.
     */
    private val FNC1_FIRST = setOf(
        "]C1",          // Code 128 -> GS1-128
        "]d2", "]d5",   // Data Matrix -> GS1 DataMatrix (5 = zusaetzlich ECI)
        "]Q3", "]Q4"    // QR Code -> GS1 QR (4 = zusaetzlich ECI)
    )

    /** Symbologien, die ueberhaupt einen GS1-Elementstring tragen koennen. */
    private val ELEMENT_STRING_CAPABLE_PREFIXES = setOf("]C", "]d", "]Q", "]e", "]L", "]z")

    /**
     * Einstufung allein aus der Kennung.
     * @return [Gs1Level.CONFIRMED] oder [Gs1Level.NONE], oder null wenn die Kennung
     *         fehlt oder unbekannt ist und deshalb der Inhalt entscheiden muss.
     */
    fun levelFromSymbologyId(symbologyId: String?): Gs1Level? {
        val id = symbologyId?.takeIf { it.length >= 3 } ?: return null
        if (id in FNC1_FIRST) return Gs1Level.CONFIRMED
        // Die DataBar-Familie ist definitionsgemaess GS1, dort gibt es keine Variante ohne.
        if (id.startsWith("]e")) return Gs1Level.CONFIRMED
        // Bekannte Symbologie, aber ohne FNC1 an erster Stelle
        if (id.take(2) in ELEMENT_STRING_CAPABLE_PREFIXES) return Gs1Level.NONE
        // EAN/UPC (]E), ITF (]I), Code 39 (]A) und andere tragen keinen Elementstring
        return Gs1Level.NONE
    }

    /**
     * Stuft einen gelesenen Code ein.
     *
     * @param symbologyId AIM-Kennung des Decoders, oder null
     * @param canCarryElementString true fuer Symbologien, in denen ein GS1-Elementstring
     *        ueberhaupt vorkommen kann (Code 128, Data Matrix, QR, PDF417, Aztec,
     *        DataBar). Fuer EAN/UPC, ITF, Code 39/93 und Codabar false – dort waere
     *        eine Inhaltspruefung nur eine Fehlerquelle.
     * @param elementString Inhalt mit FNC1 als 0x1D, ohne AIM-Kennung
     */
    fun classify(
        symbologyId: String?,
        canCarryElementString: Boolean,
        elementString: String
    ): Gs1Classification {
        val fromId = levelFromSymbologyId(symbologyId)
        val embedded = embeddedSymbologyId(elementString)

        if (fromId == Gs1Level.CONFIRMED) {
            val validation = GS1Parser.validate(elementString)
            return Gs1Classification(
                Gs1Level.CONFIRMED, symbologyId, validation.unparsedRest, embedded
            )
        }
        if (fromId == Gs1Level.NONE || !canCarryElementString) {
            return Gs1Classification(Gs1Level.NONE, symbologyId, null, null)
        }

        // Keine Kennung: nur der Inhalt kann noch etwas aussagen.
        val validation = GS1Parser.validate(elementString)
        val level = if (validation.complete && validation.aiCount > 0 && validation.plausible) {
            Gs1Level.PROBABLE
        } else {
            Gs1Level.NONE
        }
        return Gs1Classification(level, symbologyId, null, if (level == Gs1Level.NONE) null else embedded)
    }

    /**
     * Findet eine faelschlich mit codierte Symbologiekennung am Anfang der Nutzdaten.
     *
     * Ein GS1-Elementstring beginnt immer mit einer numerischen AI; "]" gehoert nicht
     * zum GS1-Zeichensatz 82 und kann dort auch sonst nicht stehen. Ein fuehrendes
     * "]" plus zwei Zeichen ist daher eindeutig eine mit codierte Kennung.
     */
    private fun embeddedSymbologyId(elementString: String): String? =
        if (elementString.length >= 3 && elementString[0] == ']') {
            elementString.substring(0, 3)
        } else {
            null
        }
}
