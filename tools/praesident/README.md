# Verlauf des Präsidenten

Rechnet den Verlauf des Präsidenten aus allen gemeldeten Geschäften vorab und ergänzt `app/src/main/assets/copytrading/regierung.json` um die Geschäfte der Jahresmeldung. Ergebnis ist `app/src/main/assets/copytrading/praesident.json`; die App führt den Verlauf ab dem letzten gemeldeten Tag mit dem Bestand an diesem Tag fort.

Reihenfolge (Python 3 mit PyMuPDF):

1. `jahresmeldung.py <278e.pdf> <jahresmeldung.json>` liest Teil 6 (Bestände) und Teil 7 (Geschäfte) der Jahresmeldung (OGE Form 278e) und meldet Lücken in der fortlaufenden Nummer je Depot.
2. `kuerzel.py <jahresmeldung.json> <regierung.json> <yahoo-ordner> namen-hand.json <namen.json>` ordnet Namen ohne Netz zu: über die Einzelmeldungen mit Kürzel und die Eckdaten von Yahoo, Abkürzungen aus `abkuerzungen.json`. Anleihen, Vorzugsaktien und Einlagen erkennt es am Namen.
3. `suchen.py <namen.json> <zwischenspeicher.json> <suche.json>` sucht die übrigen Namen bei Yahoo Finance, auch mit ausgeschriebenen und umgestellten Varianten. Angenommen wird nur ein Treffer, der in beide Richtungen zum Namen passt; der Rest landet auf der Prüfliste.
4. `zuordnung.py <namen.json> <suche.json> <zwischenspeicher.json> namen-hand.json <yahoo-ordner> <zuordnung.json>` führt alles zusammen; die von Hand geprüften Namen in `namen-hand.json` haben Vorrang.
5. `kurse.py <zuordnung.json> <regierung.json> <kursordner> <vorhandene kursordner>...` holt fehlende Tagesschlusskurse.
6. `verlauf.py <regierung.json> <jahresmeldung.json> <zuordnung.json> <ziel-regierung.json> <ziel-praesident.json> <kursordner>...` rechnet den Verlauf wie die App. Kürzel, unter denen Yahoo nach einer Umbenennung keine Kurse mehr führt, stehen in `umbenannt.json`.

Kurse und Suchergebnisse von Yahoo bleiben außerhalb des Repos; in `praesident.json` stehen nur der berechnete Index, die Anteile am Bestand und die gemeldeten Beträge.
