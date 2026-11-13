#!/usr/bin/env python3
# Baut app/src/main/assets/indikatoren/anomalien.json fuer das Werkzeug "Markt schlagen": je Signal aus der
# Anomalienforschung die mittlere Monatsrendite des Long-Short-Portfolios in der Stichprobe der Studie, danach bis zum
# Jahr der Veroeffentlichung und nach der Veroeffentlichung.
#
# Quelle: Chen und Zimmermann (2022), "Open Source Cross-Sectional Asset Pricing", Critical Finance Review 11(2),
# Datenstand 2026.10, https://www.openassetpricing.com, Lizenz CC BY 4.0. Gebraucht werden zwei Dateien des Datensatzes
# https://huggingface.co/datasets/OpenSourceAP/CrossSection :
#   SignalDoc.csv                          Signalbeschreibung (Jahr der Veroeffentlichung, Stichprobe der Studie)
#   Portfolios/Full Sets OP/PredictorLSretWide.parquet
#                                          Monatsrenditen der Long-Short-Portfolios in Prozent, eine Spalte je Signal
# Die Renditen liest dieses Skript als Seiten der Zeilenschnittstelle des Datensatzes (JSON, je 100 Monate):
#   https://datasets-server.huggingface.co/rows?dataset=OpenSourceAP%2FCrossSection&config=long_short_returns&split=train&offset=0&length=100
# abgelegt als rows-0.json, rows-100.json ... im selben Ordner wie SignalDoc.csv.
#
# Aufruf: python tools/daten/anomalien.py <Ordner> [Zieldatei]
#
# Abgrenzung nach Kalenderjahren (die Signalbeschreibung nennt nur Jahre): Stichprobe = erstes bis letztes Jahr der
# Stichprobe, dazwischen = danach bis einschliesslich Jahr der Veroeffentlichung, danach = ab dem Folgejahr.
# Aufgenommen werden Signale der Gruppe "Predictor" mit mindestens MIN_MONATE Monaten in der Stichprobe und danach.

import csv
import glob
import json
import os
import sys

MIN_MONATE = 60
STAND = "2026-10"


def renditen(ordner):
    # Monat (JJJJ, MM) -> {Signal: Rendite in Prozent}
    aus = {}
    seiten = glob.glob(os.path.join(ordner, "rows-*.json"))
    if not seiten:
        raise SystemExit("keine rows-*.json im Ordner")
    gesamt = None
    for pfad in seiten:
        with open(pfad, encoding="utf-8") as f:
            o = json.load(f)
        gesamt = o.get("num_rows_total", gesamt)
        for z in o["rows"]:
            r = z["row"]
            d = r["date"]
            aus[(int(d[0:4]), int(d[5:7]))] = {k: v for k, v in r.items() if k != "date" and v is not None}
    if gesamt is not None and len(aus) != gesamt:
        raise SystemExit(f"Monate unvollstaendig: {len(aus)} von {gesamt}")
    return aus


def mittel(werte):
    return round(sum(werte) / len(werte), 3) if werte else None


def bauen(ordner):
    monate = renditen(ordner)
    with open(os.path.join(ordner, "SignalDoc.csv"), encoding="utf-8", newline="") as f:
        doku = list(csv.DictReader(f))
    signale = []
    for z in doku:
        if z["Cat.Signal"] != "Predictor":
            continue
        k = z["Acronym"]
        try:
            jahr = int(float(z["Year"]))
            von = int(float(z["SampleStartYear"]))
            bis = int(float(z["SampleEndYear"]))
        except ValueError:
            continue
        studie, zwischen, danach = [], [], []
        for (j, _), r in monate.items():
            v = r.get(k)
            if v is None:
                continue
            if von <= j <= bis:
                studie.append(v)
            elif bis < j <= jahr:
                zwischen.append(v)
            elif j > max(jahr, bis):
                danach.append(v)
        if len(studie) < MIN_MONATE or len(danach) < MIN_MONATE:
            continue
        e = {
            "k": k, "name": z["LongDescription"].strip(), "autoren": z["Authors"].strip(), "jahr": jahr, "von": von, "bis": bis,
            "studie": mittel(studie), "danach": mittel(danach), "nStudie": len(studie), "nDanach": len(danach),
        }
        if zwischen:
            e["zwischen"] = mittel(zwischen)
            e["nZwischen"] = len(zwischen)
        signale.append(e)
    signale.sort(key=lambda e: e["k"].lower())
    letzter = max(monate)
    return {"stand": STAND, "bis": "%04d-%02d" % letzter, "minMonate": MIN_MONATE, "signale": signale}


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__ or "Aufruf: anomalien.py <Ordner> [Zieldatei]")
    repo = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
    ziel = sys.argv[2] if len(sys.argv) > 2 else os.path.join(repo, "app", "src", "main", "assets", "indikatoren", "anomalien.json")
    d = bauen(sys.argv[1])
    with open(ziel, "w", encoding="utf-8", newline="\n") as f:
        json.dump(d, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")
    s = d["signale"]
    print(len(s), "Signale bis", d["bis"], os.path.getsize(ziel), "Bytes")
    ms = sum(e["studie"] for e in s) / len(s)
    md = sum(e["danach"] for e in s) / len(s)
    print("Mittel Studie %.3f, danach %.3f, Rueckgang %.1f %%, schwaecher %d von %d" % (ms, md, (1 - md / ms) * 100, sum(1 for e in s if e["danach"] < e["studie"]), len(s)))


if __name__ == "__main__":
    main()
