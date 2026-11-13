#!/usr/bin/env python3
"""Baut assets/indizes/sp500_mitglieder.json: Mitglieder des S&P 500 seit 1996 je Kuerzel mit Aufnahme- und
Austrittstag, dazu fuer die heutigen Mitglieder der erste Aufnahmetag (auch vor 1996 und unter frueherem Kuerzel).

Quelle: https://github.com/fja05680/sp500 (MIT License, Copyright (c) 2019-2020 Farrell J. Aultman)
  sp500_ticker_start_end.csv  ticker,start_date,end_date (Kuerzel wie am jeweiligen Tag; end_date ist der erste Tag
                              ausserhalb des Index, leer bei heutigen Mitgliedern)
  sp500.csv                   heutige Mitglieder mit Spalte "Date added"
  LICENSE                     wird geprueft und der Hinweis in die Datei uebernommen

Ausgabe (Tage seit 1970-01-01):
  stand       juengster Aenderungstag in der Liste
  a           [Kuerzel, ab] oder [Kuerzel, ab, bis]; im Index ab <= Tag < bis
  seit        heutige Mitglieder: erster Aufnahmetag laut Liste der heutigen Mitglieder
Aufruf: python -I sp500_mitglieder.py <ziel.json>
Antwortet die Quelle mit 403 oder 429, bricht das Werkzeug ohne Wiederholung ab.
"""
import csv
import datetime
import io
import json
import sys
import time
import urllib.error
import urllib.request

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
BASIS = "https://raw.githubusercontent.com/fja05680/sp500/master/"
QUELLE = "https://github.com/fja05680/sp500"
URHEBER = "Copyright (c) 2019-2020 Farrell J. Aultman"
EPOCHE = datetime.date(1970, 1, 1)


def holen(name):
    req = urllib.request.Request(BASIS + name, headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            b = r.read()
    except urllib.error.HTTPError as e:
        sys.exit(f"{name}: Antwort {e.code}, Abbruch ohne Wiederholung")
    time.sleep(0.7)
    return b.decode("utf-8")


def tag(s):
    s = (s or "").strip()[:10]
    if not s:
        return None
    return (datetime.date.fromisoformat(s) - EPOCHE).days


def main():
    ziel = sys.argv[1]
    lizenz = holen("LICENSE")
    if not lizenz.startswith("MIT License") or "Farrell J. Aultman" not in lizenz:
        sys.exit("Lizenz hat sich geaendert, bitte pruefen")
    # Erlaubnis- und Haftungstext der MIT-Lizenz ab "Permission", als ein Absatz je Leerzeile
    erlaubnis = lizenz[lizenz.index("Permission"):].strip()
    absaetze = [" ".join(a.split()) for a in erlaubnis.split(chr(10) + chr(10))]

    abschnitte = []
    for r in csv.DictReader(io.StringIO(holen("sp500_ticker_start_end.csv"))):
        k = r["ticker"].strip()
        ab = tag(r["start_date"])
        bis = tag(r["end_date"])
        if not k or ab is None or (bis is not None and bis <= ab):
            sys.exit(f"Zeile unlesbar: {r}")
        abschnitte.append([k, ab] + ([bis] if bis is not None else []))
    abschnitte.sort(key=lambda a: (a[0], a[1]))
    # Je Kuerzel ueberschneiden sich die Abschnitte nicht
    for x, y in zip(abschnitte, abschnitte[1:]):
        if x[0] == y[0] and (len(x) < 3 or x[2] > y[1]):
            sys.exit(f"Abschnitte ueberschneiden sich: {x} {y}")
    heute = {a[0] for a in abschnitte if len(a) == 2}

    seit = {}
    for r in csv.DictReader(io.StringIO(holen("sp500.csv"))):
        k = r["Symbol"].strip()
        try:
            t = tag(r["Date added"])
        except ValueError:
            t = None
        if k in heute and t is not None:
            seit[k] = t
    fehlt = heute - set(seit)
    if len(heute) < 490 or len(fehlt) > 5:
        sys.exit(f"Liste unvollstaendig: {len(heute)} heutige Mitglieder, ohne Aufnahmetag {sorted(fehlt)}")

    stand = max(max(a[1:]) for a in abschnitte)
    aus = {
        "stand": (EPOCHE + datetime.timedelta(days=stand)).isoformat(),
        "quelle": QUELLE,
        "lizenz": ["MIT License", URHEBER] + absaetze,
        "tage": "seit 1970-01-01",
        "a": abschnitte,
        "seit": dict(sorted(seit.items())),
    }
    with open(ziel, "w", encoding="utf-8", newline="") as f:
        json.dump(aus, f, ensure_ascii=False, separators=(",", ":"))
        print("", file=f)
    print(f"{len(abschnitte)} Abschnitte, {len(heute)} heutige Mitglieder, Stand {aus['stand']}")


if __name__ == "__main__":
    main()
