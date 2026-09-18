package com.example.codescannergs1.composite

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.codescannergs1.ScannedCode
import zxingcpp.BarcodeReader

/**
 * Zweiter Decoder neben ML Kit.
 *
 * ML Kit kennt weder die GS1-DataBar-Familie noch MicroPDF417. zxing-cpp liest beides.
 * Den CC-A-Anteil eines Composite-Symbols kann allerdings auch zxing-cpp nicht
 * decodieren – CC-A verwendet eigene Varianten- und ECC-Tabellen und eine eigene
 * Basis-928-Codierung. Dieser Teil wird daher von [CcaImageDecoder] und [CcaDecoder]
 * uebernommen, angesetzt an der von zxing-cpp gemeldeten Lage des Linearsymbols.
 */
object CompositeScanner {

    private const val TAG = "CompositeScanner"

    /** Anzeigeform des Gruppentrennzeichens, wie im Rest der App verwendet. */
    private const val GS_DISPLAY = "<GS>"

    private val FORMATS = setOf(
        BarcodeReader.Format.DATA_BAR,
        BarcodeReader.Format.DATA_BAR_LTD,
        BarcodeReader.Format.DATA_BAR_EXP,
        BarcodeReader.Format.DATA_BAR_EXP_STK,
        BarcodeReader.Format.DATA_BAR_STK,
        BarcodeReader.Format.DATA_BAR_STK_OMNI,
        BarcodeReader.Format.MICRO_PDF_417
    )

    private val reader: BarcodeReader by lazy {
        BarcodeReader(
            BarcodeReader.Options(
                formats = FORMATS,
                textMode = BarcodeReader.TextMode.PLAIN,
                tryHarder = true,
                tryRotate = true,
                tryInvert = true,
                maxNumberOfSymbols = 8
            )
        )
    }

    /** Kamerabild auswerten. Der ImageProxy bleibt unveraendert und wird nicht geschlossen. */
    fun scan(image: ImageProxy): List<ScannedCode> {
        val results = try {
            reader.read(image)
        } catch (e: Exception) {
            Log.w(TAG, "zxing-cpp konnte das Kamerabild nicht auswerten", e)
            return emptyList()
        }
        if (results.isEmpty()) return emptyList()

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

    /** Galeriebild auswerten. */
    fun scan(bitmap: Bitmap): List<ScannedCode> {
        val results = try {
            reader.read(bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "zxing-cpp konnte das Bild nicht auswerten", e)
            return emptyList()
        }
        if (results.isEmpty()) return emptyList()
        val gray: GrayImage by lazy { BitmapGrayImage(bitmap) }
        return results.mapNotNull { toScannedCode(it) { gray } }
    }

    private fun toScannedCode(
        result: BarcodeReader.Result,
        grayImage: () -> GrayImage
    ): ScannedCode? {
        if (result.error != null) return null
        val text = result.text ?: return null
        if (text.isEmpty()) return null

        if (result.format == BarcodeReader.Format.DATA_BAR_LTD) {
            val composite = decodeCcA(result, grayImage())
            if (composite != null) {
                val combined = text + composite.elementString
                return ScannedCode(
                    rawValue = "]e0" + combined.replace(CcaDecoder.FNC1.toString(), GS_DISPLAY),
                    type = "GS1 DataBar Limited CC-A",
                    isGs1 = true
                )
            }
        }

        return ScannedCode(
            rawValue = symbologyPrefix(result.format) +
                text.replace(CcaDecoder.FNC1.toString(), GS_DISPLAY),
            type = typeName(result.format),
            isGs1 = isGs1Format(result.format)
        )
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

    /** AIM-Symbologiekennung, damit der GS1Parser die Daten wie bisher erkennt. */
    private fun symbologyPrefix(format: BarcodeReader.Format): String = when (format) {
        BarcodeReader.Format.DATA_BAR,
        BarcodeReader.Format.DATA_BAR_OMNI,
        BarcodeReader.Format.DATA_BAR_LTD,
        BarcodeReader.Format.DATA_BAR_EXP,
        BarcodeReader.Format.DATA_BAR_EXP_STK,
        BarcodeReader.Format.DATA_BAR_STK,
        BarcodeReader.Format.DATA_BAR_STK_OMNI -> "]e0"
        else -> ""
    }

    private fun isGs1Format(format: BarcodeReader.Format): Boolean = when (format) {
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
        else -> format.name
    }
}
