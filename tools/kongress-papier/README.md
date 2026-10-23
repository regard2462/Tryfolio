# Papiermeldungen (PTR) des Repräsentantenhauses auswerten

Eingescannte Periodic Transaction Reports (Formular mit Kreuzen, eigene Tabellen der Vermögensverwalter, handschriftliche Formulare) werden zu Geschäften im JSON-Format. Alle Schritte laufen lokal; einzig `laden.py` (PDFs des House Clerk) und `suchen.py` (Kürzelsuche bei Yahoo Finance) greifen aufs Netz zu.

Voraussetzungen: Python 3 mit PyMuPDF, NumPy, SciPy und Pillow; Windows 10/11 mit einer installierten OCR-Sprache (Windows.Media.Ocr, Aufruf über PowerShell). Python immer mit `-I` starten. Alle Aufrufe aus dem Arbeitsordner `papier`, Zwischenstände unter `cache`. `<zuordnung.json>` und `<namen-hand.json>` sind die vorhandenen Namens-Kürzel-Zuordnungen (hier als Kopie unter `cache/zuordnung-praesident.json` und `cache/namen-hand-praesident.json`).

## Reihenfolge

| Nr. | Skript | Aufruf | Zweck |
|---|---|---|---|
| 1 | `liste.py` | `python -I skript/liste.py cache/meldungen.json index/2025FD.zip index/2026FD.zip` | Papier-PTRs aus den Jahresverzeichnissen (FilingType P, DocID nicht elektronisch) |
| 2 | `laden.py` | `python -I skript/laden.py cache/meldungen.json pdf` | PDFs laden (nacheinander, mit Pause, Kennung im Skript); vorhandene bleiben |
| 3 | `uebersicht.py` | `python -I skript/uebersicht.py cache/meldungen.json pdf cache/uebersicht.json` | Seitenzahl und Seitenformat je Meldung |
| 4 | `lage.py` | `python -I skript/lage.py bilder cache/meldungen.json pdf cache/lage`, dann `python -I skript/ocr_ordner.py cache/lage`, dann `python -I skript/lage.py werten cache/meldungen.json cache/lage pdf cache/lage.json` | Drehung jeder Seite über die OCR in allen vier Lagen |
| 5 | `gitter_alle.py` | `python -I skript/gitter_alle.py cache/meldungen.json pdf cache/lage.json cache [id ...]` | Seite drehen und entzerren, Tabellengitter finden, Spalten deuten (Inhaber, Name, Art, Datum, Benachrichtigung, Beträge A–K), Spaltenstreifen für die OCR schneiden. Fehlt auf einer Seite die Inhaberspalte: `--nachtragen cache <id> ...`; weicht die Vorlage einzelner Seiten ab: `--angleichen cache <id> ...` |
| 6 | `varianten.py` | `python -I skript/varianten.py cache/meldungen.json cache` | weitere OCR-Fassungen der Inhaber- und Datumsstreifen |
| 7 | `zellen.py` | `python -I skript/zellen.py cache/meldungen.json cache` | Zeilen festlegen (Bänder ohne Zeilenlinie an den Datumszeilen teilen) und Zellenblätter für Inhaber, Datum, Benachrichtigung und Name bauen (`--felder=name` baut nur einzelne Felder neu) |
| 8 | `ocr_ordner.py` | `python -I skript/ocr_ordner.py cache/streifen` und `python -I skript/ocr_ordner.py cache/blatt` | Windows-OCR (`winocr.ps1`) über alle Streifen und Blätter, drei Prozesse, ohne Fenster |
| 9 | `lesen.py` | `python -I skript/lesen.py cache/meldungen.json cache [id ...]` | je Zeile Kreuze über die Pixel der Zellen (Schwelle je Seite aus dem Rauschen leerer Zellen), Daten über die Abstimmung der OCR-Fassungen, den Bildvergleich ganzer Zellen und den Ziffernvergleich (`ziffern.py`), Plausibilität; Ergebnis `cache/zeilen/<id>.json` |
| 10 | `inhaber.py` | `python -I skript/inhaber.py cache/meldungen.json cache` | Inhaber (SP, DC, JT) über den Bildvergleich mit sicher gelesenen Kürzeln derselben Meldung bzw. desselben Abgeordneten |
| 11 | `namen.py` | `python -I skript/namen.py cache/meldungen.json cache <zuordnung.json> <namen-hand.json> cache/namen-bereinigt.json skript/hand.json` | je Zeile die beste Lesart des Namens wählen, OCR-Fehler bereinigen (Wortschatz aus vorhandenen Zuordnungen und häufigen Lesungen), an geprüfte oder häufige Namen angleichen (gesperrt, wenn ein mehrfach gelesenes Wort durch ein deutlich anderes ersetzt würde, die Aktienklasse sich ändert oder ein „&“ verloren ginge). Schreibt daneben `-guete.json` (Anteil bekannter Wörter) und `-angeglichen.json` (Protokoll der Angleichungen) |
| 12 | `zusammen.py namen` | `python -I skript/zusammen.py namen cache/meldungen.json cache skript/hand.json cache/namen-bereinigt.json cache/namen-liste.json` | Liste der endgültigen Namen |
| 13 | `kuerzel_papier.py vorbereiten` | `python -I skript/kuerzel_papier.py vorbereiten cache/namen-liste.json <zuordnung.json> <namen-hand.json> cache/offen.json` | Art und Kürzel ohne Netz (im Namen, Optionen, Handliste `kuerzel-hand.json`, vorhandene Zuordnungen); offene Namen für die Suche |
| 14 | `suchliste.py` | `python -I skript/suchliste.py cache/offen.json cache/suche-cache.json cache/offen-suche.json` | nur Namen mit einem aussagekräftigen Wort gehen in die Suche |
| 15 | `suchen.py` | `python -I skript/suchen.py cache/offen-suche.json cache/suche-cache.json cache/suche.json --varianten-nur-sauber` | strenge Yahoo-Suche (Kopie aus den Werkzeugen zum Präsidenten), eigener Zwischenspeicher |
| 16 | `kuerzel_papier.py zusammen` | `python -I skript/kuerzel_papier.py zusammen cache/namen-liste.json <zuordnung.json> <namen-hand.json> cache/suche.json cache/kuerzel.json` | Kürzel und Typ je Name; danach Ergänzung über die ersten zwei Wörter sicher zugeordneter Aktiennamen und Abgleich der Aktienklasse (GOOGL/GOOG, NWSA/NWS, FOXA/FOX, BRK-A/BRK-B, BF-A/BF-B) |
| 17 | `zusammen.py bauen` | `python -I skript/zusammen.py bauen cache/meldungen.json cache skript/hand.json cache/namen-bereinigt.json cache/kuerzel.json <stand> papier.json cache/details.json` | Ergebnisdatei; offene Zeilen zählen als unlesbar |
| 17b | `anzeige.py` | `python -I skript/anzeige.py papier.json verzeichnis cache/suche-cache.json <zuordnung.json> papier-anzeige.json cache/anzeige-protokoll.json` | Anzeigenamen: Geschäfte mit Kürzel bekommen den Namen aus dem Verzeichnis der US-Börsen (`verzeichnis/nasdaqlisted.txt` und `otherlisted.txt` von nasdaqtrader.com) oder aus den Yahoo-Treffern; Aktien und Fonds ohne Kürzel werden OCR-bewusst mit Verzeichnis, Yahoo-Treffern und Zuordnungen abgeglichen (nur bei klarem Abstand zum nächsten Kandidaten); übrige Namen ohne Maklerzusatz und Rahmenreste, mit römischen Ziffern und „LINKED TO S&P 500“. Unlesbare Namen werden leer. `--probe <stichprobe.json> <stichprobe-wahr.json> cache/kuerzel.json verzeichnis cache/suche-cache.json <zuordnung.json>` vergleicht mit der Sichtprüfung (Stand 4.2: 53 von 60 Namen im Kern richtig, vorher 43) |
| 18 | `stichprobe.py`, `pruefbogen.py` | `python -I skript/stichprobe.py cache/meldungen.json cache skript/hand.json cache/namen-bereinigt.json 60 <startwert> skript/pruefung/stichprobe.json`, dann `python -I skript/pruefbogen.py skript/pruefung/stichprobe.json cache cache/pruef/probe` | Zufallsstichprobe der rein automatisch gelesenen Zeilen und Prüfbögen (Zeilenbilder mit Nummer); die am Bild gelesenen Werte kommen nach `skript/pruefung/stichprobe-wahr.json` |
| 19 | `stichprobe_werten.py` | `python -I skript/stichprobe_werten.py cache/meldungen.json cache skript/hand.json cache/namen-bereinigt.json skript/pruefung/stichprobe.json skript/pruefung/stichprobe-wahr.json skript/pruefung/stichprobe-ergebnis.json` | Vergleich je Feld (Inhaber, Name, Art, Datum, Benachrichtigung, Betragsspalte) |
| 20 | `kuerzel_probe.py` | `python -I skript/kuerzel_probe.py cache/meldungen.json cache skript/hand.json cache/namen-bereinigt.json cache/kuerzel.json 50 <startwert> skript/pruefung/kuerzel-probe.json`, dann `python -I skript/pruefbogen.py skript/pruefung/kuerzel-probe.json cache cache/pruef/kp name` | Zufallsstichprobe der Geschäfte mit Kürzel zur Prüfung am Bild |
| 21 | `bericht.py` | `python -I skript/bericht.py cache/meldungen.json papier.json skript/pruefung/stichprobe.json skript/pruefung/stichprobe-ergebnis.json bericht.md cache/details.json skript/pruefung/bericht-zusatz.md` | Bericht je Abgeordnetem, Fehlerquoten der Stichprobe, dazu die von Hand geschriebenen Abschnitte aus `bericht-zusatz.md` |

Hilfsmittel: `zeigen.py` (Gitter über der Seite einzeichnen), `pruefbogen.py … name` (nur die Namensspalte) und `pruefbogen.py … datum`, `korrektur_eintragen.py` (Ergebnisse einer Sichtprüfung in `hand.json` übernehmen), `tabelle.py` und `ziffern.py` (Bausteine).

## Handprüfung

`hand.json` enthält, was am Seitenbild geprüft wurde:

- `ersetzen`: alle Geschäfte einer Meldung von Hand (handschriftliche Formulare, Briefe, Sonderfälle), mit `grund`.
- `korrekturen`: einzelne Zeilen der automatischen Lesung, Schlüssel `Seite/Zeile` aus `cache/zeilen/<id>.json` (`ok`, `unlesbar`, `streichen`, `aufnehmen` oder berichtigte Felder `i`, `w`, `a`, `d`, `g`, `b`).

`korrektur_eintragen.py <prüfbogen.json> <angaben.txt> skript/hand.json` überträgt eine Liste wie `pruefung/optionen.txt` (Nummer des Prüfbogens und Angaben, etwa `12 d=03/14/25 b=B` oder `7 w="CALL/TTE FLEX EURO PM @ 59 EXP 02/14/2025"`; statt der Nummer geht auch `<DocID> <Seite>/<Zeile>`). Die Zeilennummern bleiben gültig, solange Gitter und `zellen.py` für die Meldung nicht neu laufen.

`kuerzel-hand.json` enthält von Hand geprüfte Namen mit Kürzel und Art (`k` null: bewusst ohne Kürzel). Sie gehen allen anderen Quellen vor und gelten auch für Schreibweisen mit gleichem Suchnamen.

## Neue Meldungen

Neue Jahresverzeichnisse nach `index/` legen und die Schritte 1 bis 17 wiederholen; vorhandene PDFs, Bilder, OCR-Ergebnisse und Suchtreffer werden wiederverwendet. Bei einer neuen Vorlage (andere Spaltenzahl) zeigt `gitter_alle.py` eine Meldung „Spaltenzahl … passt zu keiner Vorlage“; dann `spalten_deuten` erweitern. Handschriftliche Meldungen erkennt man an vielen offenen Zeilen in `cache/details.json`; sie gehören nach Sichtprüfung als `ersetzen` in `hand.json`.
