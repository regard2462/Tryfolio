#!/usr/bin/env python3
# Baut globus/leitzins.json (app/src/main/assets/): Leitzins der Zentralbanken je Land, Monatsreihe und letzte Zinsschritte.
#
# Quelle: Bank fuer Internationalen Zahlungsausgleich (BIS), Central bank policy rates (Datensatz WS_CBPOL),
#   https://data.bis.org/topics/CBPOL
# Nutzung laut https://data.bis.org/help/legal: frei, wenn die BIS als Quelle genannt wird.
#
# Gelesen werden zwei CSV-Dateien der BIS-Schnittstelle (Spalten FREQ,REF_AREA,TIME_PERIOD,OBS_VALUE):
#   Monatswerte  https://stats.bis.org/api/v2/data/dataflow/BIS/WS_CBPOL/1.0/M?detail=dataonly&format=csv
#   Tageswerte   https://stats.bis.org/api/v2/data/dataflow/BIS/WS_CBPOL/1.0/D?detail=dataonly&format=csv
# Die Monatsreihe steht je Land ab dem ersten Monat als Laufliste [Wert, Monate, Wert, Monate, ...] in Tausendstel
# Prozent (null: kein Wert). Die letzten Zinsschritte kommen aus den Tageswerten: Tag, Satz davor, Satz danach.
#
# Aufruf:
#   python tools/daten/leitzins.py holen <Ordner>            beide Dateien laden (cbpol_m.csv, cbpol_d.csv)
#   python tools/daten/leitzins.py bauen <Ordner> [Stand]    Datei aus dem Ordner bauen
# Danach: python tools/daten/manifest.py bauen --signieren
# Pflege: einmal im Jahr genuegt; neuere Werte holt die App je Land selbst nach.

import csv
import datetime
import json
import math
import os
import sys
import urllib.request

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
BASIS = "https://stats.bis.org/api/v2/data/dataflow/BIS/WS_CBPOL/1.0/"
SCHRITTE = 12


def repo():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def holen(ordner):
    os.makedirs(ordner, exist_ok=True)
    for f, name in (("M", "cbpol_m.csv"), ("D", "cbpol_d.csv")):
        req = urllib.request.Request(BASIS + f + "?detail=dataonly&format=csv", headers={"User-Agent": UA, "Accept": "text/csv, */*"})
        with urllib.request.urlopen(req, timeout=120) as r:
            b = r.read()
        with open(os.path.join(ordner, name), "wb") as aus:
            aus.write(b)
        print(name, len(b))


def reihen(pfad):
    """Land -> sortierte Liste (Zeitraum, Wert); NaN und Leeres fehlen"""
    aus = {}
    with open(pfad, encoding="utf-8") as f:
        for r in csv.DictReader(f):
            try:
                v = float(r["OBS_VALUE"])
            except (ValueError, TypeError, KeyError):
                continue
            if math.isnan(v) or math.isinf(v):
                continue
            aus.setdefault(r["REF_AREA"], {})[r["TIME_PERIOD"]] = v
    return {k: sorted(v.items()) for k, v in aus.items()}


def monat_nr(s):
    return int(s[:4]) * 12 + int(s[5:7]) - 1


def tausendstel(v):
    return int(round(v * 1000))


def bauen(ordner, stand):
    monate = reihen(os.path.join(ordner, "cbpol_m.csv"))
    tage = reihen(os.path.join(ordner, "cbpol_d.csv"))
    laender = {}
    bis = ""
    for land in sorted(monate):
        m = monate[land]
        if len(m) < 12:
            continue
        erster = monat_nr(m[0][0])
        letzter = monat_nr(m[-1][0])
        werte = [None] * (letzter - erster + 1)
        for zeit, v in m:
            werte[monat_nr(zeit) - erster] = tausendstel(v)
        lauf = []
        for v in werte:
            if lauf and lauf[-2] == v:
                lauf[-1] += 1
            else:
                lauf += [v, 1]
        t = tage.get(land, [])
        wechsel = [[t[i][0], tausendstel(t[i - 1][1]), tausendstel(t[i][1])] for i in range(1, len(t)) if tausendstel(t[i][1]) != tausendstel(t[i - 1][1])]
        eintrag = {"ab": m[0][0], "w": lauf, "s": wechsel[-SCHRITTE:]}
        if t:
            eintrag["t"] = t[-1][0]
        laender[land] = eintrag
        bis = max(bis, m[-1][0])
    aus = {
        "stand": stand,
        "bis": bis,
        "quelle": "Bank for International Settlements (BIS), Central bank policy rates",
        "adresse": "https://data.bis.org/topics/CBPOL",
        "hinweis": "w: Monatsreihe ab dem Monat ab als Laufliste Wert, Zahl der Monate, in Tausendstel Prozent; s: letzte Zinsschritte aus den Tageswerten (Tag, davor, danach); t: letzter Tag mit Wert",
        "l": laender,
    }
    text = json.dumps(aus, ensure_ascii=False, separators=(",", ":"))
    ziel = os.path.join(repo(), "app", "src", "main", "assets", "globus", "leitzins.json")
    with open(ziel, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print("Laender", len(laender), "bis", bis, "Bytes", len(text.encode("utf-8")))


if __name__ == "__main__":
    if len(sys.argv) < 3 or sys.argv[1] not in ("holen", "bauen"):
        raise SystemExit("Aufruf: leitzins.py holen <Ordner> | bauen <Ordner> [Stand]")
    if sys.argv[1] == "holen":
        holen(sys.argv[2])
    else:
        bauen(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else datetime.date.today().isoformat())
