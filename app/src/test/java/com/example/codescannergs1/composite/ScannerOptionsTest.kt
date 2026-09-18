package com.example.codescannergs1.composite

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zxingcpp.BarcodeReader

/**
 * Sichert die Leseoptionen ab.
 *
 * Hintergrund ist ein realer Fehler: der Android-Wrapper von zxing-cpp setzt in
 * `BarcodeReader.Options` saemtliche `try*`-Schalter auf false, also auch
 * `tryDownscale` – anders als die C++-Bibliothek darunter, wo er voreingestellt an
 * ist. Die Optionen gehen bei jedem Aufruf vollstaendig an die native Seite, ein
 * nicht gesetzter Schalter schaltet die Funktion daher aktiv ab.
 *
 * Die Folge war nur beim Galerie-Import sichtbar, weil Kamerabilder klein sind und
 * ohne verkleinerte Stufen auskommen: Aufnahmen mit 9 Megapixeln blieben unlesbar,
 * waehrend dieselben Symbole vom Bildschirm abgescannt sofort gelesen wurden.
 *
 * Hier wird beides festgehalten – die Voreinstellung des Wrappers als Beleg fuer die
 * Falle, und dass die App sie fuer jedes Profil ueberschreibt.
 */
class ScannerOptionsTest {

    @Test
    fun wrapperDefaultsDisableDownscaling() {
        // Schlaegt dieser Test fehl, hat der Wrapper seine Voreinstellungen geaendert.
        // Dann darf der Kommentar in CompositeScanner angepasst werden - die Optionen
        // selbst sollten trotzdem ausdruecklich gesetzt bleiben.
        val default = BarcodeReader.Options()
        assertFalse("Wrapper-Voreinstellung", default.tryDownscale)
        assertFalse("Wrapper-Voreinstellung", default.tryHarder)
        assertFalse("Wrapper-Voreinstellung", default.tryRotate)
        assertFalse("Wrapper-Voreinstellung", default.tryInvert)
    }

    @Test
    fun everyProfileSearchesDownscaledSteps() {
        val options = CompositeScanner.readerOptions()
        assertTrue("es muss Profile geben", options.isNotEmpty())
        for (o in options) {
            assertTrue("tryDownscale muss an sein", o.tryDownscale)
            assertTrue("tryHarder muss an sein", o.tryHarder)
            assertTrue("tryRotate muss an sein", o.tryRotate)
            assertTrue("tryInvert muss an sein", o.tryInvert)
        }
    }

    @Test
    fun profilesCoverOppositeBinarizers() {
        val options = CompositeScanner.readerOptions()
        val binarizers = options.map { it.binarizer }.toSet()
        // Punktdruck auf Karton braucht einen festen Schwellwert, die Aufnahme eines
        // Bildschirms ausschliesslich die lokale Mittelung. Ein Profil allein reicht
        // fuer beides nicht.
        assertTrue(BarcodeReader.Binarizer.LOCAL_AVERAGE in binarizers)
        assertTrue(BarcodeReader.Binarizer.FIXED_THRESHOLD in binarizers)
        assertTrue("ohne Rauschunterdrueckung", options.any { !it.tryDenoise })
        assertTrue("mit Rauschunterdrueckung", options.any { it.tryDenoise })
    }

    @Test
    fun allFormatsAreSearched() {
        // Leere Formatmenge heisst in zxing-cpp "alle Formate". Nur so bekommt jeder
        // gelesene Code eine AIM-Symbologiekennung und damit eine belastbare
        // GS1-Einstufung.
        for (o in CompositeScanner.readerOptions()) {
            assertTrue("leere Menge = alle Formate", o.formats.isEmpty())
            assertTrue("mehrere Symbole je Bild", o.maxNumberOfSymbols > 1)
        }
    }
}
