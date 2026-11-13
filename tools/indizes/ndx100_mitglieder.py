#!/usr/bin/env python3
"""Baut assets/indizes/ndx100_mitglieder.json: Mitglieder des Nasdaq-100 seit 2007 je Kuerzel mit Aufnahme- und
Austrittstag, im selben Aufbau wie sp500_mitglieder.json.

Quelle: https://github.com/jmccarrell/n100tickers (MIT License, Copyright (c) 2021 - 2025 Jeff McCarrell)
  src/nasdaq_100_ticker_history/n100-ticker-changes-<Jahr>.yaml
      tickers_on_Jan_1   Mitglieder am 1. Januar (Kuerzel wie am jeweiligen Tag)
      changes            je Wirksamkeitstag difference (scheidet aus) und union (kommt hinzu)
  LICENSE                wird geprueft und der Hinweis in die Datei uebernommen
Die Tage folgen laut Quelle den Mitteilungen der Nasdaq zu Indexaenderungen. Der Januar 2007 ist aus der ersten
Aenderung am 1. Februar 2007 zurueckgerechnet.

Ausgabe (Tage seit 1970-01-01):
  stand       juengster Aenderungstag in der Liste
  a           [Kuerzel, ab] oder [Kuerzel, ab, bis]; im Index ab <= Tag < bis
  seit        leer (die Quelle nennt keinen Aufnahmetag vor 2007)
Aufruf: python -I ndx100_mitglieder.py <ziel.json> [<Ordner mit den Dateien der Quelle>]
Ohne Ordner laedt das Werkzeug die Dateien. Antwortet die Quelle mit 403 oder 429, bricht es ohne Wiederholung ab.
"""
import datetime
import json
import os
import sys
import time
import urllib.error
import urllib.request

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
BASIS = "https://raw.githubusercontent.com/jmccarrell/n100tickers/main/"
DATEN = "src/nasdaq_100_ticker_history/"
QUELLE = "https://github.com/jmccarrell/n100tickers"
URHEBER = "Copyright (c) 2021 - 2025 Jeff McCarrell"
EPOCHE = datetime.date(1970, 1, 1)
ERSTES_JAHR = 2007


def holen(name, ordner):
    if ordner:
        pfad = os.path.join(ordner, os.path.basename(name))
        if not os.path.isfile(pfad):
            return None
        with open(pfad, encoding="utf-8") as f:
            return f.read()
    req = urllib.request.Request(BASIS + name, headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            b = r.read()
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return None
        sys.exit(f"{name}: Antwort {e.code}, Abbruch ohne Wiederholung")
    time.sleep(0.7)
    return b.decode("utf-8")


def tag(s):
    return (datetime.date.fromisoformat(s) - EPOCHE).days


def lesen(text):
    """Liest eine Jahresdatei: (Mitglieder am 1. Januar, {Tag: (weg, hinzu)})."""
    anfang = []
    aenderungen = {}
    teil = None
    datum = None
    art = None
    for roh in text.splitlines():
        zeile = roh.split("#", 1)[0].rstrip()
        if not zeile.strip() or zeile.strip() == "---":
            continue
        kern = zeile.strip()
        if not zeile.startswith(" "):
            schluessel = kern.split(":", 1)[0]
            teil = schluessel if schluessel in ("tickers_on_Jan_1", "changes") else None
            continue
        if kern.startswith("- "):
            k = kern[2:].strip().strip("'").strip('"')
            if teil == "tickers_on_Jan_1":
                anfang.append(k)
            elif teil == "changes" and datum is not None and art in (0, 1):
                aenderungen[datum][art].append(k)
            else:
                sys.exit(f"Zeile unlesbar: {roh}")
            continue
        if teil != "changes" or not kern.endswith(":"):
            sys.exit(f"Zeile unlesbar: {roh}")
        name = kern[:-1].strip().strip("'").strip('"')
        if name == "difference":
            art = 0
        elif name == "union":
            art = 1
        else:
            datum = tag(name)
            art = None
            if datum in aenderungen:
                sys.exit(f"Tag doppelt: {name}")
            aenderungen[datum] = ([], [])
    return anfang, aenderungen


def main():
    ziel = sys.argv[1]
    ordner = sys.argv[2] if len(sys.argv) > 2 else None
    lizenz = holen("LICENSE", ordner)
    if not lizenz or not lizenz.startswith("MIT License") or "Jeff McCarrell" not in lizenz:
        sys.exit("Lizenz hat sich geaendert, bitte pruefen")
    erlaubnis = lizenz[lizenz.index("Permission"):].strip()
    absaetze = [" ".join(a.split()) for a in erlaubnis.split(chr(10) + chr(10))]

    offen = {}
    abschnitte = []
    stand = None
    jahr = ERSTES_JAHR
    while True:
        text = holen(f"{DATEN}n100-ticker-changes-{jahr}.yaml", ordner)
        if text is None:
            break
        anfang, aenderungen = lesen(text)
        neujahr = tag(f"{jahr}-01-01")
        if jahr == ERSTES_JAHR:
            offen = {k: neujahr for k in anfang}
        elif set(anfang) != set(offen):
            sys.exit(f"{jahr}: Mitglieder am 1. Januar passen nicht zum Vorjahr: {sorted(set(anfang) ^ set(offen))}")
        if len(anfang) != len(set(anfang)):
            sys.exit(f"{jahr}: Kuerzel doppelt")
        for t in sorted(aenderungen):
            if not neujahr <= t < tag(f"{jahr + 1}-01-01"):
                sys.exit(f"{jahr}: Tag ausserhalb des Jahres")
            weg, hinzu = aenderungen[t]
            for k in weg:
                if k not in offen:
                    sys.exit(f"{jahr}: {k} scheidet aus, ist aber kein Mitglied")
                ab = offen.pop(k)
                if t <= ab:
                    sys.exit(f"{jahr}: {k} ohne Dauer")
                abschnitte.append([k, ab, t])
            for k in hinzu:
                if k in offen:
                    sys.exit(f"{jahr}: {k} ist schon Mitglied")
                offen[k] = t
            stand = t
        jahr += 1
    if jahr - ERSTES_JAHR < 19 or not 95 <= len(offen) <= 110 or stand is None:
        sys.exit(f"Liste unvollstaendig: {jahr - ERSTES_JAHR} Jahre, {len(offen)} heutige Mitglieder")
    abschnitte += [[k, ab] for k, ab in offen.items()]
    abschnitte.sort(key=lambda a: (a[0], a[1]))

    aus = {
        "stand": (EPOCHE + datetime.timedelta(days=stand)).isoformat(),
        "quelle": QUELLE,
        "lizenz": ["MIT License", URHEBER] + absaetze,
        "tage": "seit 1970-01-01",
        "a": abschnitte,
        "seit": {},
    }
    with open(ziel, "w", encoding="utf-8", newline="") as f:
        json.dump(aus, f, ensure_ascii=False, separators=(",", ":"))
        print("", file=f)
    print(f"{len(abschnitte)} Abschnitte, {len(offen)} heutige Mitglieder, Stand {aus['stand']}")


if __name__ == "__main__":
    main()
