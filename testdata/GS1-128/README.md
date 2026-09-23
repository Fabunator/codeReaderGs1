# Testaufnahmen GS1-128

Zwei Aufnahmen von SSCC-Versandetiketten (AI 00), je 2304 × 4096 Pixel.

| Datei | Inhalt |
|---|---|
| `MVIMG_20260923_085034.jpg` | `(00)742515207000000580`, zweites Etikett `(00)742515207000000573` |
| `MVIMG_20260923_085042.jpg` | `(00)742515207000000436` |

Beide werden von zxing-cpp mit jedem Profil gelesen; das zweite Etikett auf `…085034`
nur mit festem Schwellwert und Rauschunterdrückung.

## Wofür diese Bilder da sind

An ihnen ist ein Fehler in der Etikettenprüfung aufgefallen: die App meldete, die
Symbologiekennung `]C1` sei zusätzlich im Symbol codiert, obwohl die Etiketten in
Ordnung sind.

Die Ursache liegt darin, wie die beiden Decoder die Kennung herausgeben:

| Decoder | Kennung | Inhalt |
|---|---|---|
| zxing-cpp | `]C1` als eigenes Feld | `00742515207000000580` |
| ML Kit | – | `]C100742515207000000580` |

Die App hat den Inhalt von ML Kit ungeprüft übernommen. Ein führendes `]` galt als
Beweis für eine mit codierte Kennung – bei ML Kit stammt sie aber vom Leser.

Behoben in zwei Schritten:

1. `Gs1Detector.splitSymbologyId()` trennt eine vom Leser vorangestellte Kennung ab,
   bevor eingestuft wird. Damit ein Text, der zufällig mit `]` beginnt, nicht
   zerschnitten wird, muss der Buchstabe zur gelesenen Symbologie passen (`C` für
   Code 128, `d` für Data Matrix …) und das dritte Zeichen eine Ziffer sein.
2. Ein Etikettenfehler wird nur noch bei **nachweisbarer Doppelung** gemeldet: der
   Decoder hat die Kennung getrennt genannt *und* derselbe Text steht noch einmal am
   Anfang des Inhalts. Das ist bei `testdata/GS1DataMatrix/1789766537775.jpg`
   tatsächlich der Fall – dieser Hinweis bleibt erhalten.

Zusätzlich vergleicht der Kamerabetrieb gefundene Codes jetzt nach Inhalt statt nach
Rohwert und ersetzt einen vorhandenen Eintrag durch den aussagekräftigeren. Vorher
blieb der zuerst gefundene stehen – und das war je nach Bild der schlechtere.

Abgesichert durch `MlKitPathTest` und die neuen Fälle in `Gs1DetectionTest`.
