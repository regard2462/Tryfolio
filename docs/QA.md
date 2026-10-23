# Prüfung

Wie die App geprüft wird und was die Prüfungen abdecken. Die Ergebnisse des letzten Laufs liegen in [verification](verification).

## Automatische Prüfungen

| Prüfung | Inhalt |
|---|---|
| `./gradlew :app:testDebugUnitTest` | JUnit-Tests unter `app/src/test`: Handel und Bewertung, Wechselkurse und Dollaranzeige, Derivate, Steuern, Zinsen, Sparpläne, Handelskalender, Werkzeuge, Nachrichtenfeeds und wichtige Meldungen, Copytrading und Portfolioverläufe, Prediction Markets, Übungswetten und ihr Verlauf, Abrufkontingente, Nachrichten mit eigenem Schlüssel, Übersetzung, Sprache nach System. Sie nutzen feste lokale Daten und rufen keine Kursdienste auf. |
| `./gradlew :app:lintDebug` | Android Lint ohne Fehler. Die verbleibenden Warnungen betreffen neuere Bibliotheken, ein neueres Ziel-SDK und den Ordner `mipmap-anydpi-v26`; sie sind bewusst belassen. |
| `./gradlew :desktop:test` | Dieselben Tests auf dem Desktop, dazu das Programmsymbol und ein Rundgang durch die ganze Oberfläche ohne Fenster mit echten Kursen und Nachrichten (`DesktopOberflaecheTest`, Bilder unter `desktop/build/testbilder`). |
| `Tryfolio --selbsttest <Datei>` | Prüft ein fertiges Desktop-Paket ohne Fenster: Kurse, Schlagzeilen, Logos, Artikeltext, Oberfläche, Katalog, Einstellungen, Finanzwissen-Webserver. |
| `.github/workflows/desktop.yml` | Baut bei jedem Versions-Tag auf Windows, Linux und macOS (Apple Silicon), führt `:desktop:test` und den Selbsttest im fertigen Paket aus und hängt die Pakete an das Release. |

### Rundgang ohne Fenster

Einrichtung; Testdepot mit SAP und Apple; Depot mit Kursen und Logos; Antippen des Depotwerts schaltet auf US-Dollar und zurück, Guthaben in Dollar; Cash per Mausziehen und zurück; News mit Anbieter-Reitern, der Reiter des Guardian ohne Schlüssel und die Seite „Zugänge“ und Unterkategorien, Wischen per Maus mit mitwandernder Reitermarkierung, Zahl der wichtigen Meldungen im Protokoll; Artikel mit Text aus der Seite; Escape schließt erst das Blatt, dann die Seite; Copytrading mit den echten Meldungen des Repräsentantenhauses, Suche nach einer Abgeordneten, ihre Geschäfte und das Blatt zum Nachbilden, Portfolioverlauf der Abgeordneten aus ihren Geschäften, Investor mit 13F-Beständen, Portfolioverlauf, Vergleich mit dem S&P 500 und Käufen und Verkäufen je Quartal, Vergleichsseiten für Investoren und Strategien, Musterportfolio mit Kennzahlen aus zehn Jahren Kursen und Verlauf; Aktie mit Chart; Wikipedia-Blatt; Profil; Finanzwissen über die Kachel der Startseite; Englisch.

Ein zweiter Rundgang (`PrognoseOberflaecheTest`, Ablauf in `prognose.txt`) öffnet die Prediction Markets von der Startseite: Polymarket-Liste, ein Ereignis mit Verlauf und Ausgängen, eine Übungswette über das Wettblatt, „Meine Wetten“ mit Verlauf und die Kalshi-Liste mit Bildern.

Ein dritter Rundgang (`DepotVerlaufOberflaecheTest`, Ablauf in `depotverlauf.txt`) legt ein Testdepot mit SAP, Apple, NVIDIA-Aktien und einem Turbo Long auf NVIDIA an, der zum Teil wieder verkauft wird, öffnet unter Analytics die Werkzeuge (jedes mit Beschreibung), von dort den Depotverlauf und zeigt mit echten Kursen den Graphen mit Marken, beim Ziehen durch den Graphen die Veränderung des Tages mit den Werten dahinter, die größten Bewegungen, eine aufgeklappte Bewegung, Gewinn und Verlust je Wert und die Liste der Geschäfte.

### Depotverlauf

- `DepotzerlegungTest` rechnet das Ergebnis je Tag und Wert nach: Kauf und Verkauf verfälschen es nicht, Knock-outs mit Restwert, Bestand zu jedem Zeitpunkt, Wochen und Monate als Abschnitte, größte Anstiege und Rückgänge und ihre Ursachen, Prozent nur mit Bestand am Vortag. Er prüft auch, dass Kauf und Verkauf eines Hebelprodukts das Produkt mitbuchen, verkaufte Produkte für die Zeit im Depot wieder bewertbar sind und ältere Verkäufe ohne Produkt ihr Ergebnis am Verkaufstag tragen, auch in der Depotkurve: Ein Verkauf lässt sie nicht fallen, und ihr Ergebnis über den Zeitraum entspricht dem Gewinn des Hebels.

### Copytrading

- `KongressTest` liest echte Transaktionsmeldungen aus `app/src/test/resources/kongress` (verschlüsselte PDFs, Zeilen über Seitenumbrüche, Optionen, Ehepartner und gemeinsame Konten, lange Meldungen über mehrere Seiten, eine eingescannte Meldung ohne Text) und einen Auszug aus dem Jahresverzeichnis.
- `CopytradingTest` prüft die mitgelieferten 13F-Daten (Vollständigkeit und Summen je Quartal), die Einordnung der Veränderungen zum Vorquartal, das Erkennen neuer Meldungen bei der SEC, die Musterportfolios und ihre Kennzahlen sowie die Kürzel für Yahoo.
- `PortfolioverlaufTest` rechnet den zeitgewichteten Verlauf aus Käufen und Verkäufen nach (auch Verkäufe ohne Bestand und Werte ohne Kurse), die Umrechnung mit Tageskursen, Zeitraum, Kennzahlen, das Fortschreiben nach der letzten 13F-Meldung und das Lesen der Verlaufsdatei.
- Härtetest: `./gradlew :desktop:test --tests '*KongressMassenTest*' -Pkongress.ordner=<Ordner mit Meldungen>` liest alle Meldungen eines Ordners und schreibt die erkannten Geschäfte nach `desktop/build/kongress-massen.tsv`. Der Abgleich mit einem unabhängigen PDF-Leser (PyMuPDF) ergab für alle 369 elektronischen Meldungen des Jahres 2026 (Stand Oktober 2026) 3.537 von 3.537 Geschäften.

### Prediction Markets

- `PrognoseTest` liest gekürzte echte Antworten von Polymarket (Ereignisliste, Markt, Kursverlauf) und Kalshi (Suche, Ereignis, Markt, Kerzen) aus `app/src/test/resources/prognose`, erkennt aufgelöste Märkte und prüft die Übungswetten: Kauf, Zusammenlegen, Verkauf zum Geldkurs, Auszahlung bei Gewinn und Verlust, Mindesteinsatz, Guthaben.
- `WichtigTest` prüft die Punkte für wichtige Schlagzeilen an erfundenen Titeln: Ereignisse, Ausschlüsse, Abzüge, Bezug zum Depot, breite Berichterstattung, höchstens zwei je Geschichte und die Obergrenze von einem Fünftel.
- `Fassung30Test` prüft auch das Erkennen neuer Meldungen der Regierung in der Liste auf whitehouse.gov (an erfundenem HTML). Daneben prüft es die Kennzahlen (längster Rückgang, Sharpe und Sortino, Value at Risk, Beta, Alpha und Korrelation, Auswertung der Verkäufe) und das Einlesen der Daten von Senat und Regierung an erfundenen Beispielen: Namen mit Satzzeichen, Papiermeldungen, Arten der Werte, Kürzel „--“, Gewichte eines Bestands.
- `Fassung38Test` liest einen erfundenen vorberechneten Verlauf des Präsidenten (Format, Marken, Zahlen), führt ihn mit erfundenen Schlusskursen bis heute fort (Werte ohne Kurse fallen anteilig heraus, der Abruf deckt 95 Prozent des Bestands) und liest einen gekürzten Feed des Archivs „Trump's Truth“: eigene Beiträge, Zitate, weitergeteilte und gelöschte Beiträge, Bilder nur aus dem Speicher des Archivs, keine Adressen von Truth Social im Text.
- `Fassung39Test` liest Antworten von GitHub auf die Frage nach der neuesten Fassung (Entwürfe, Vorabfassungen, Fehlermeldungen und fremde Links zählen nicht), vergleicht Fassungen Stelle für Stelle (3.10 ist neuer als 3.9, 3.9.0 gleich 3.9), prüft den Abstand von einem Tag (ohne Netz eine Stunde), dass jede Fassung nur einmal gemeldet wird und dass die Fassung im Code zu den Bauangaben von Android und Desktop passt. Dazu der Kalender der Geschäfte einer Person: Tag des Geschäfts in UTC, Käufe vor Verkäufen, Tausch ohne Richtung, Summen je Monat, Spanne vom ersten bis zum letzten Monat mit Geschäften.
- `Fassung40Test` erkennt Optionen in allen drei Meldeformen (Senat mit Angaben im Namen, Repräsentantenhaus mit der Beschreibung, die der PDF-Leser ohne Leerzeichen liefert, Papiermeldungen mit „CALL/… @ … EXP …“), trennt strukturierte Produkte und kündbare Anleihen ab, prüft Black-Scholes gegen Lehrbuchwerte, rechnet einen Call im Portfolioverlauf mit Hebel und schließt ihn bei der Ausübung, liest die Beschreibung aus dem Meldungstext und die mitgelieferten Papiermeldungen und prüft die Leser für Nachrichten ohne lesbare Seite (Schnittstelle der EU-Kommission, PDF-Link einer Seite, Absätze aus PDF-Zeilen, HTML-Entitäten).
- `Fassung41Test` prüft die Auswertung je Person (gekaufte und verkaufte Werte nach 30, 90 und 365 Tagen gegen den Markt, Optionen zählen nicht, Häufigkeit, auffällige Monate, Branchen früher und zuletzt, Käufe vor großen Kursbewegungen gegen die Rate an zufälligen Tagen), die Regeln des Depot-Checks mit ihren Schwellen und die Chart-Indikatoren (gleitender Durchschnitt, RSI nach Wilder, Übertragung auf die Punkte des Charts).
- Verlauf des Präsidenten (`tools/praesident`): `jahresmeldung.py` liest Teil 6 und 7 der Jahresmeldung aus den Wortpositionen und meldet Lücken in der fortlaufenden Nummer je Depot (beim Stand 2026: keine). `kuerzel.py` und `suchen.py` ordnen die Namen Börsenkürzeln zu, nur bei passenden Wörtern und US-Börse; Stichproben der Zuordnungen und der als Anleihe erkannten Namen sind von Hand geprüft. `verlauf.py` rechnet wie die App und vergleicht den Aktienwert Ende 2025 mit den Beständen in Teil 6.
- `GleichwerteTest` prüft das Erkennen derselben Aktie an mehreren Plätzen an erfundenen und abgekürzten Namen (gleiche Namen mit anderem Kurs bleiben getrennt, Notierungen ohne Kurs kommen in keine Gruppe), das Zusammenwachsen gemerkter Gruppen und die Übernahme des jüngeren Kurses einer gleichen Notierung.
- `HandelndeTest` prüft „Wer handelt den Wert“: Richtung je Person nach der geschätzten Summe, nur passende Kürzel, keine Optionen und keine Geschäfte älter als zwölf Monate, die Einordnung der Investoren nach dem Quartalsvergleich und die Tendenz.
- `PersonenbilderTest` prüft die Zuordnung der Profilbilder: Wahlkreis vor Bundesstaat, Namen mit Akzenten, Rufnamen und eindeutige Nachnamen bei Senatoren.
- `ZusammenfuehrenTest` prüft das Zusammenführen zweier Depots: Guthaben, gleiche Positionen mit gewichtetem Einstand, Merkliste, Summe der Depotverläufe und Steuertöpfe.
- Der Workflow „Senat“ (`.github/workflows/senat.yml`) holt die Meldungen des Senats auf einem Rechner von GitHub in den USA (`tools/senat/holen.py`) und legt `senat.json` als Artefakt ab; vor einer neuen Fassung wird es nach `app/src/main/assets/copytrading/` übernommen.

### Währungen

- `AnzeigeWaehrungTest` prüft die Anzeige in Euro und US-Dollar, `HeimatwaehrungTest` die Heimatwährung auf der Wertpapierseite: welche Währungen dafür in Frage kommen (Pence als Pfund, ohne Wechselkurs keine), die Folge Euro, Dollar, Heimatwährung und die Beträge auf Deutsch und Englisch.

### Dienste mit eigenem Schlüssel

- `Fassung28Test` prüft die Abrufkontingente (gleitende Fenster, Mindestabstand, Speichern über einen Neustart, Limit des Anbieters), die Leser für Guardian, Finnhub und Alpha Vantage samt Fehlermeldungen, die Abstände der Quellen gegen die Tageslimits, das Verfallen gemerkter Guardian-Artikel, den DeepL-Übersetzer (gemerkte Texte kosten nichts, Limit des Anbieters), das Wörterbuch der Prediction Markets, die Sprache nach System und den Verlauf der Wetten. Die Testdaten bilden nur den Aufbau der Antworten nach. Mit echten Schlüsseln ist nicht geprüft.

## Prüfung auf dem Gerät

Auf dem Android-Emulator (Android 15, API 35) wird die neue Fassung über eine ältere installiert. Geprüft wird, dass Depot und Einstellungen erhalten bleiben und die Startseite Kurse zeigt, dazu die Bereiche, die neu sind oder sich geändert haben. Bisher waren das Umschalten auf US-Dollar, News mit den Anbieter-Reitern, Copytrading (Kongress, Person, Nachbilden, Investoren, Strategien, Portfolioverlauf), Prediction Markets, Wertpapierseite und Profil, zuletzt der Depotverlauf mit einem Depot, das älter als ein Jahr ist (Vergleichslinie, größte Bewegungen, Gewinn und Verlust je Wert, Geschäfte).

## Nicht geprüft

- Die Desktop-Fassung ist ohne sichtbares Fenster geprüft. Eine Sichtprüfung am Bildschirm (Fenster, Infobereich, Datei-Dialog, Browser) steht aus.
- Echte Android-Geräte außer dem Emulator.
- macOS außer dem Bau, den Desktop-Tests und dem Selbsttest auf einem Mac von GitHub; eine Sichtprüfung auf einem Mac steht aus.
