package com.example.codescannergs1.composite

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.codescannergs1.Gs1Detector
import com.example.codescannergs1.ScannedCode
import zxingcpp.BarcodeReader

/**
 * Zweiter Decoder neben ML Kit.
 *
 * Zwei Aufgaben:
 *
 * 1. **Formate, die ML Kit nicht kennt** – die gesamte GS1-DataBar-Familie und
 *    MicroPDF417. Den CC-A-Anteil eines Composite-Symbols kann allerdings auch
 *    zxing-cpp nicht lesen; das uebernehmen [CcaImageDecoder] und [CcaDecoder].
 *
 * 2. **Verlaessliche GS1-Einstufung.** zxing-cpp liefert die AIM-Symbologiekennung
 *    nach ISO/IEC 15424 mit – `]C1` statt `]C0`, `]d2` statt `]d1`, `]Q3` statt `]Q1`.
 *    Nur daran laesst sich GS1 sicher erkennen, denn FNC1 an erster Symbolposition
 *    ist das einzige Unterscheidungsmerkmal und taucht im Inhalt nicht auf. ML Kit
 *    gibt die Kennung nicht heraus. Deshalb laeuft zxing-cpp ueber *alle* Formate,
 *    damit jeder gelesene Code eine belastbare Einstufung bekommt.
 */
object CompositeScanner {

    private const val TAG = "CompositeScanner"

    /** Anzeigeform des Gruppentrennzeichens, wie im Rest der App verwendet. */
    private const val GS_DISPLAY = "<GS>"

    /**
     * Leere Formatmenge bedeutet in zxing-cpp "alle Formate" (siehe ReadBarcode.cpp:
     * `formats.empty() || ...`). Gemessen an einem 1920x1080-Bild kostet das gegenueber
     * der reinen DataBar-Suche rund 11 ms je Bild. Wird das auf schwacher Hardware zu
     * teuer, ist [TRY_HARDER] der erste Hebel.
     */
    private val FORMATS = emptySet<BarcodeReader.Format>()

    /** Aufwendigere Suche: deutlich robuster, etwa Faktor 5 langsamer. */
    private const val TRY_HARDER = true

    /**
     * Zusaetzlich in verkleinerten Stufen suchen.
     *
     * **Muss gesetzt werden.** In zxing-cpp selbst ist das die Voreinstellung
     * (`ReaderOptions::tryDownscale` = true), im Android-Wrapper dagegen nicht:
     * `BarcodeReader.Options` setzt saemtliche `try*`-Schalter auf false und reicht
     * sie bei jedem Aufruf an die native Seite durch. Wer den Schalter nicht angibt,
     * schaltet das Verkleinern also aktiv ab.
     *
     * Folge: gelesen wird ausschliesslich in der Aufloesung, die hereingereicht wird.
     * Bei Kamerabildern faellt das nicht auf, die sind ohnehin klein. Ein Galeriefoto
     * mit 9 Megapixeln hat aber Module von 10 bis 20 Pixeln Kantenlaenge, und genau
     * dort scheitert die Suche - dieselben Symbole, die vom Bildschirm abgescannt
     * sofort gelesen werden, blieben beim Galerie-Import unlesbar.
     *
     * Die Bibliothek arbeitet die Stufen mit reiner Punktabtastung ab
     * (`ImageView::subsampled`), nicht mit gemitteltem Verkleinern. Gerade bei
     * Punktdruck ist das der entscheidende Unterschied: Mitteln verschmiert die
     * einzelnen Punkte, Punktabtastung nicht. Die Positionen der gefundenen Symbole
     * rechnet zxing-cpp auf die volle Aufloesung zurueck, die CC-A-Auswertung bleibt
     * dadurch unberuehrt.
     */
    private const val TRY_DOWNSCALE = true

    /**
     * Ein Decodier-Profil.
     *
     * Punktgedruckte Data-Matrix-Codes (Tintenstrahl, Nadelpraegung) bestehen aus
     * einzelnen Punkten, die sich nicht beruehren. Ein normaler Decoder erwartet
     * geschlossene Module und scheitert daran. Zwei Stellschrauben helfen, und zwar
     * nur zusammen:
     *
     *  - **denoise** wendet einen morphologischen Abschluss an und verbindet die
     *    Punkte zu Modulen (in zxing-cpp nur fuer Data Matrix, QR und Aztec wirksam).
     *  - der **Binarizer** entscheidet, ob das Muster ueberhaupt sauber schwarz/weiss
     *    wird. Auf gleichmaessig ausgeleuchteten Kartonagen liefert FIXED_THRESHOLD
     *    das bessere Ergebnis, bei starken Helligkeitsunterschieden – etwa dem Foto
     *    eines Bildschirms – ausschliesslich LOCAL_AVERAGE.
     *
     * Kein Profil liest alle Faelle, deshalb werden sie der Reihe nach probiert.
     */
    private data class Profile(
        val label: String,
        val binarizer: BarcodeReader.Binarizer,
        val denoise: Boolean
    )

    private val PROFILES = listOf(
        Profile("Standard", BarcodeReader.Binarizer.LOCAL_AVERAGE, false),
        Profile("Punktdruck", BarcodeReader.Binarizer.LOCAL_AVERAGE, true),
        Profile("Punktdruck, fester Schwellwert", BarcodeReader.Binarizer.FIXED_THRESHOLD, true),
        Profile("Punktdruck, Histogramm", BarcodeReader.Binarizer.GLOBAL_HISTOGRAM, true)
    )

    /**
     * Die Leseoptionen aller Profile.
     *
     * Bewusst getrennt vom Anlegen der Leser: ein `BarcodeReader` laedt im Konstruktor
     * die native Bibliothek, die es in einem JVM-Test nicht gibt. Die Optionen dagegen
     * sind eine gewoehnliche Datenklasse und lassen sich so im Test pruefen.
     */
    internal fun readerOptions(): List<BarcodeReader.Options> =
        PROFILES.map { profile ->
            BarcodeReader.Options(
                formats = FORMATS,
                textMode = BarcodeReader.TextMode.PLAIN,
                tryHarder = TRY_HARDER,
                tryRotate = TRY_HARDER,
                tryInvert = TRY_HARDER,
                tryDownscale = TRY_DOWNSCALE,
                tryDenoise = profile.denoise,
                binarizer = profile.binarizer,
                maxNumberOfSymbols = 8
            )
        }

    private val readers: List<BarcodeReader> by lazy { readerOptions().map { BarcodeReader(it) } }

    /**
     * Aktuelles Profil im Kamerabetrieb. Alle Profile bei jedem Bild zu probieren waere
     * zu teuer (je nach Bild 30-70 ms), deshalb laeuft je Bild nur eines: solange nichts
     * gefunden wird, reihum das naechste, bei einem Treffer bleibt es bei dem Profil,
     * das gerade funktioniert. Innerhalb von vier Bildern ist damit alles einmal
     * durchprobiert, ohne dass die Bildrate einbricht.
     *
     * Wird nur vom Analyse-Thread der Kamera geschrieben.
     */
    @Volatile
    private var cameraProfile = 0

    /** Kamerabild auswerten. Der ImageProxy bleibt unveraendert und wird nicht geschlossen. */
    fun scan(image: ImageProxy): List<ScannedCode> {
        val index = cameraProfile
        val results = try {
            readers[index].read(image)
        } catch (e: Exception) {
            Log.w(TAG, "zxing-cpp konnte das Kamerabild nicht auswerten", e)
            return emptyList()
        }
        if (results.isEmpty()) {
            cameraProfile = (index + 1) % PROFILES.size
            return emptyList()
        }

        val plane = image.planes[0]
        val crop = image.cropRect
        val gray: GrayImage by lazy {
            YPlaneGrayImage(
                buffer = plane.buffer,
                rowStride = plane.rowStride,
                pixelStride = 1,
                cropLeft = crop.left,
                cropTop = crop.top,
                cropWidth = crop.width(),
                cropHeight = crop.height(),
                rotation = image.imageInfo.rotationDegrees
            )
        }
        return results.mapNotNull { toScannedCode(it) { gray } }
    }

    /**
     * Galeriebild auswerten.
     *
     * Hier gibt es keinen Zeitdruck, deshalb werden alle Profile durchprobiert und die
     * Ergebnisse zusammengefasst – ein Bild kann mehrere Codes enthalten, die
     * unterschiedliche Profile brauchen.
     */
    fun scan(bitmap: Bitmap, rotationDegrees: Int = 0): List<ScannedCode> {
        val found = LinkedHashMap<String, ScannedCode>()
        collectInto(bitmap, rotationDegrees, found)

// Grosse Aufnahmen zusaetzlich halbiert lesen. Die Stufenleiter von zxing-cpp
        // geht in Dritteln (1/1, 1/3, 1/9) und laesst die Haelfte aus; ausserdem
        // verkleinert sie durch Punktabtastung, waehrend Bitmap.createScaledBitmap
        // mittelt. Beides sind andere Bilder - gemessen an einer Bildschirmaufnahme,
        // deren Symbole sich erst bei halber Groesse sauber aufloesten.
        val longSide = maxOf(bitmap.width, bitmap.height)
        if (longSide > 1600) {
            // Nur eine Zusatzchance, kein Muss: schlaegt sie fehl (auch wegen Speicher),
            // bleibt es bei den Ergebnissen aus der vollen Aufloesung.
            val half = try {
                Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, true)
            } catch (e: Throwable) {
                Log.w(TAG, "Verkleinerte Kopie konnte nicht erzeugt werden", e)
                null
            }
            if (half != null) {
                try {
                    collectInto(half, rotationDegrees, found)
                } finally {
                    half.recycle()
                }
            }
        }
        return found.values.toList()
    }

    /** Liest [bitmap] mit allen Profilen und sammelt neue Ergebnisse in [into]. */
    private fun collectInto(
        bitmap: Bitmap,
        rotationDegrees: Int,
        into: MutableMap<String, ScannedCode>
    ) {
        val gray: GrayImage by lazy { BitmapGrayImage(bitmap, rotationDegrees) }
        for (reader in readers) {
            val results = try {
                reader.read(bitmap, android.graphics.Rect(), rotationDegrees)
            } catch (e: Exception) {
                Log.w(TAG, "zxing-cpp konnte das Bild nicht auswerten", e)
                continue
            }
            for (result in results) {
                val code = toScannedCode(result) { gray } ?: continue
                if (!into.containsKey(code.rawValue)) into[code.rawValue] = code
            }
        }
    }

    private fun toScannedCode(
        result: BarcodeReader.Result,
        grayImage: () -> GrayImage
    ): ScannedCode? {
        if (result.error != null) return null
        val text = result.text ?: return null
        if (text.isEmpty()) return null

        var content = text
        var typeName = typeName(result.format)

        // Composite: den CC-A-Anteil ueber dem Linearsymbol dazunehmen
        if (result.format == BarcodeReader.Format.DATA_BAR_LTD) {
            val composite = decodeCcA(result, grayImage())
            if (composite != null) {
                content = text + composite.elementString
                typeName = "GS1 DataBar Limited CC-A"
            }
        }

        val symbologyId = result.symbologyIdentifier?.takeIf { it.isNotEmpty() }
        val classification = Gs1Detector.classify(
            symbologyId = symbologyId,
            canCarryElementString = canCarryElementString(result.format),
            elementString = content
        )

        return ScannedCode(
            rawValue = (symbologyId ?: "") + content.replace(CcaDecoder.FNC1.toString(), GS_DISPLAY),
            type = gs1TypeName(result.format, typeName, classification.level),
            isGs1 = classification.isGs1,
            gs1Level = classification.level.name,
            symbologyId = symbologyId,
            unparsedRest = classification.unparsedRest,
            embeddedSymbologyId = classification.embeddedSymbologyId
        )
    }

    /**
     * Bestaetigte GS1-Codes bekommen den gelaeufigen Namen: ein Code 128 mit FNC1 an
     * erster Stelle heisst GS1-128, nicht Code 128.
     */
    private fun gs1TypeName(
        format: BarcodeReader.Format,
        base: String,
        level: com.example.codescannergs1.Gs1Level
    ): String {
        if (level != com.example.codescannergs1.Gs1Level.CONFIRMED) return base
        return when (format) {
            BarcodeReader.Format.CODE_128 -> "GS1-128"
            BarcodeReader.Format.DATA_MATRIX -> "GS1 DataMatrix"
            BarcodeReader.Format.QR_CODE,
            BarcodeReader.Format.QR_CODE_MODEL_1,
            BarcodeReader.Format.QR_CODE_MODEL_2 -> "GS1 QR Code"
            else -> base
        }
    }

    private fun decodeCcA(result: BarcodeReader.Result, gray: GrayImage): CcaDecoder.Result? {
        val p = result.position
        val quad = Quad(
            Pt(p.topLeft.x.toFloat(), p.topLeft.y.toFloat()),
            Pt(p.topRight.x.toFloat(), p.topRight.y.toFloat()),
            Pt(p.bottomRight.x.toFloat(), p.bottomRight.y.toFloat()),
            Pt(p.bottomLeft.x.toFloat(), p.bottomLeft.y.toFloat())
        )
        return try {
            CcaImageDecoder.decode(gray, quad)?.result
        } catch (e: Exception) {
            Log.w(TAG, "CC-A-Decodierung fehlgeschlagen", e)
            null
        }
    }

    /**
     * Symbologien, in denen ein GS1-Elementstring ueberhaupt vorkommen kann.
     * Bei EAN/UPC, ITF, Code 39/93 und Codabar waere eine Inhaltspruefung nur eine
     * Fehlerquelle: ein 13-stelliger EAN-Inhalt laesst sich zufaellig als AI-Kette lesen.
     */
    private fun canCarryElementString(format: BarcodeReader.Format): Boolean = when (format) {
        BarcodeReader.Format.CODE_128,
        BarcodeReader.Format.DATA_MATRIX,
        BarcodeReader.Format.QR_CODE,
        BarcodeReader.Format.QR_CODE_MODEL_1,
        BarcodeReader.Format.QR_CODE_MODEL_2,
        BarcodeReader.Format.MICRO_QR_CODE,
        BarcodeReader.Format.RMQR_CODE,
        BarcodeReader.Format.PDF_417,
        BarcodeReader.Format.COMPACT_PDF_417,
        BarcodeReader.Format.MICRO_PDF_417,
        BarcodeReader.Format.AZTEC,
        BarcodeReader.Format.AZTEC_CODE,
        BarcodeReader.Format.DATA_BAR,
        BarcodeReader.Format.DATA_BAR_OMNI,
        BarcodeReader.Format.DATA_BAR_LTD,
        BarcodeReader.Format.DATA_BAR_EXP,
        BarcodeReader.Format.DATA_BAR_EXP_STK,
        BarcodeReader.Format.DATA_BAR_STK,
        BarcodeReader.Format.DATA_BAR_STK_OMNI -> true
        else -> false
    }

    private fun typeName(format: BarcodeReader.Format): String = when (format) {
        BarcodeReader.Format.DATA_BAR,
        BarcodeReader.Format.DATA_BAR_OMNI -> "GS1 DataBar Omnidirectional"
        BarcodeReader.Format.DATA_BAR_LTD -> "GS1 DataBar Limited"
        BarcodeReader.Format.DATA_BAR_EXP -> "GS1 DataBar Expanded"
        BarcodeReader.Format.DATA_BAR_EXP_STK -> "GS1 DataBar Expanded Stacked"
        BarcodeReader.Format.DATA_BAR_STK -> "GS1 DataBar Stacked"
        BarcodeReader.Format.DATA_BAR_STK_OMNI -> "GS1 DataBar Stacked Omnidirectional"
        BarcodeReader.Format.MICRO_PDF_417 -> "MicroPDF417"
        BarcodeReader.Format.PDF_417,
        BarcodeReader.Format.COMPACT_PDF_417 -> "PDF417"
        BarcodeReader.Format.CODE_128 -> "Code 128"
        BarcodeReader.Format.CODE_39,
        BarcodeReader.Format.CODE_39_STD,
        BarcodeReader.Format.CODE_39_EXT -> "Code 39"
        BarcodeReader.Format.CODE_93 -> "Code 93"
        BarcodeReader.Format.CODE_32 -> "Code 32"
        BarcodeReader.Format.PZN -> "PZN"
        BarcodeReader.Format.CODABAR -> "Codabar"
        BarcodeReader.Format.ITF -> "ITF"
        BarcodeReader.Format.ITF_14 -> "ITF-14"
        BarcodeReader.Format.EAN_13 -> "EAN-13"
        BarcodeReader.Format.EAN_8 -> "EAN-8"
        BarcodeReader.Format.UPC_A -> "UPC-A"
        BarcodeReader.Format.UPC_E -> "UPC-E"
        BarcodeReader.Format.ISBN -> "ISBN"
        BarcodeReader.Format.QR_CODE,
        BarcodeReader.Format.QR_CODE_MODEL_1,
        BarcodeReader.Format.QR_CODE_MODEL_2 -> "QR Code"
        BarcodeReader.Format.MICRO_QR_CODE -> "Micro QR Code"
        BarcodeReader.Format.RMQR_CODE -> "rMQR Code"
        BarcodeReader.Format.DATA_MATRIX -> "DataMatrix"
        BarcodeReader.Format.AZTEC,
        BarcodeReader.Format.AZTEC_CODE -> "Aztec"
        BarcodeReader.Format.MAXI_CODE -> "MaxiCode"
        BarcodeReader.Format.DX_FILM_EDGE -> "DX Film Edge"
        else -> format.name
    }
}
