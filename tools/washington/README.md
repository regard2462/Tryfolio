# Behördendaten vorab auswerten

Diese Skripte erzeugen die mitgelieferten Dateien für den Abschnitt „Politik und Behörden“ auf der Seite einer US-Aktie.
Sie brauchen Python 3 und Netz, aber keine Schlüssel. Alle Abrufe tragen nur die Kennung der App.

| Skript | Ergebnis | Quelle |
|---|---|---|
| `lobbying.py <katalog.json> <zwischenordner> <ziel.json>` | `app/src/main/assets/behoerden/lobbying.json` | LD-2-Quartalsdateien des Clerk of the House (disclosurespreview.house.gov/data/LD) |
| `entlassungen.py <katalog.json> <ziel.json>` | `app/src/main/assets/behoerden/entlassungen.json` | WARN Feed (CC BY 4.0) |

Die Schnittstelle von LDA.gov sperrt Abrufe von außerhalb der USA; die Dateien des Repräsentantenhauses enthalten dieselben
Meldungen. Die Zuordnung zu Firmen des Katalogs steht in `namen.py`: Namen werden ohne Rechtsform verglichen, abweichende
Namen (Alphabet und Google, Meta und Facebook) stehen in `ANDERE_NAMEN`.

Die Transaktions- und Jahresmeldungen des Repräsentantenhauses (`copytrading/haus.json`, `copytrading/vermoegen.json`)
baut der Test `HausSammlung` im Modul desktop mit demselben PDF-Leser wie die App:

```sh
TROETE_HAUS=<arbeitsordner> ./gradlew :desktop:test --tests '*HausSammlung'
```

Danach `haus.json` und `vermoegen.json` aus dem Arbeitsordner nach `app/src/main/assets/copytrading` kopieren.
