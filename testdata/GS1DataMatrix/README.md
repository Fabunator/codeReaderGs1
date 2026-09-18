# Testaufnahmen GS1 DataMatrix

Reale Aufnahmen, mit denen die Erkennung schwieriger Data-Matrix-Codes geprüft wird.
Alle drei waren mit der ursprünglichen Einstellung (LocalAverage, ohne Rauschunter-
drückung) nicht oder nur teilweise lesbar.

| Datei | Inhalt | Besonderheit |
|---|---|---|
| `1789766537747.jpg` | `(01)04150040073932 (21)7430E45D4D9E (17)270930 (10)240942` | Punktdruck auf Karton: die Module berühren sich nicht |
| `1789766537760.jpg` | drei Codes, GTIN `00342388024460`, SN `986348977009` / `986629393122` / `987246955842`, `(17)230615 (10)7271401` | Bildschirmaufnahme, starke Helligkeitsunterschiede |
| `1789766537775.jpg` | `(01)08433042021573 (17)260228 (10)V999` | Symbol enthält die AIM-Kennung `]d2` zusätzlich als Nutzdaten |

## Was daran schwierig ist

**`…747` – Punktdruck.** Der Code ist als Raster einzelner, nicht zusammenhängender
Punkte gedruckt. Ein Decoder erwartet geschlossene Module und findet das Suchmuster
nicht. Nötig ist beides zusammen: die morphologische Schließung (`tryDenoise`), die die
Punkte zu Modulen verbindet, **und** ein fester Schwellwert (`FIXED_THRESHOLD`). Mit
der lokal gemittelten Binarisierung bleibt der Code in jeder Auflösung unlesbar.

**`…760` – Bildschirmaufnahme.** Hier ist es umgekehrt: helle Kartons vor dunklem
Hintergrund, ein fester Schwellwert scheitert vollständig, nur `LOCAL_AVERAGE`
funktioniert. Zwei der Codes werden in voller Auflösung gelesen, der dritte erst in der
halbierten Kopie. Der vierte Code am unteren Bildrand ist angeschnitten und
grundsätzlich nicht lesbar.

**`…775` – doppelte Kennung.** Der Erzeuger hat die Symbologiekennung `]d2` mit in die
Nutzdaten geschrieben. Der Decoder stellt seine eigene Kennung davor, sie steht also
zweimal da. `GS1Parser.stripSymbologyPrefix()` entfernt deshalb in einer Schleife.

## Folgerung für die App

Kein einziges Profil liest alle Fälle – die beiden ersten Aufnahmen brauchen
gegensätzliche Binarisierung. `CompositeScanner` probiert daher drei Profile:

1. `LOCAL_AVERAGE`, ohne Rauschunterdrückung (Standard, schnellstes)
2. `LOCAL_AVERAGE` mit Rauschunterdrückung
3. `FIXED_THRESHOLD` mit Rauschunterdrückung

Im Kamerabetrieb läuft je Bild nur ein Profil: solange nichts gefunden wird reihum das
nächste, bei einem Treffer bleibt es bei dem, das gerade funktioniert. Innerhalb von
drei Bildern ist damit alles einmal durchprobiert, ohne dass die Bildrate einbricht.
Beim Galerie-Import werden alle Profile durchprobiert, zusätzlich auf einer halbierten
Kopie des Bildes.
