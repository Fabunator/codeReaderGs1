# Referenzsymbole "GS1 DataBar Limited CC-A"

Diese PNG-Dateien sind mit [zint](https://github.com/zint/zint) (BSD-3-Clause) erzeugt
und dienen zum Prüfen der Composite-Erkennung – entweder vom Bildschirm abscannen oder
über den Galerie-Import der App laden.

Alle Symbole tragen im Linearanteil die GTIN `(01) 0 4012345678901`
(zint-Primary `0401234567890`, Prüfziffer wird ergänzt) und im CC-A-Anteil die unten
genannten Elementstrings. Erzeugt mit:

```
zint -b 133 --mode=1 --primary="0401234567890" -d "<Daten>" --scale=6 --quietzones -o <datei>.png
```

| Datei | CC-A-Inhalt | Zeilen |
|---|---|---|
| `dbar-ltd-cca-lot-only.png` | `(10) ABC123` | 4 |
| `dbar-ltd-cca-expiry-lot.png` | `(17) 261231 (10) LOT123` | 4 |
| `dbar-ltd-cca-expiry-lot-long.png` | `(17) 270630 (10) A1B2C3D4` | 4 |
| `dbar-ltd-cca-serial.png` | `(21) SN12345678` | 4 |
| `dbar-ltd-cca-lot-serial.png` | `(10) LOT1 (21) SER1` | 4 |
| `dbar-ltd-cca-8-rows.png` | `(10) LOT123456 (21) SERIAL987654 (17) 261231` | 8 |

Erwartetes Ergebnis in der App: Typ `GS1 DataBar Limited CC-A`, Rohwert
`]e0` + `01` + GTIN-14 + Elementstring des CC-A-Anteils, Gruppentrennzeichen als `<GS>`.

Beispiel für `dbar-ltd-cca-expiry-lot.png`:

```
]e0010401234567890117261231 10LOT123
```

(ohne Leerzeichen: `]e001040123456789011726123110LOT123` – AI 17 hat feste Länge,
deshalb steht vor `10` kein Trennzeichen; nach `LOT123` folgt nichts mehr.)

Die maschinell geprüften Testvektoren (60 Symbole als Modulraster) liegen in
`app/src/test/resources/cca_vectors.tsv` und werden von `CcaDecoderTest` ausgewertet.
