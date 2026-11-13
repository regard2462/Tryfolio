#!/usr/bin/env python3
# Baut app/src/main/assets/indikatoren/epu.json: die Indizes der wirtschaftspolitischen Unsicherheit (Economic Policy
# Uncertainty, EPU) und der Nachrichten ueber Aktienmarkt-Schwankungen (Equity Market Volatility, EMV) als Monatsreihen.
#
# Quelle und Bedingungen (am 14.11.2026 auf https://www.policyuncertainty.com/ gelesen): "This work is licensed under a
# Creative Commons Attribution 4.0 International License". Die Tabellen tragen den Vermerk, die Daten duerften mit Nennung
# der Autoren, der Arbeit und der Seite frei genutzt werden. Zitate stehen je Reihe in der Datei (Feld "zitat").
#   - US_Policy_Uncertainty_Data.xlsx, Blatt "Main News Index": Baker, Bloom und Davis, USA, monatlich ab 1900
#   - Global_Policy_Uncertainty_Data.xlsx: Davis, Globaler Index (GEPU_current, gewichtet nach BIP zu laufenden Kursen) ab 1997
#   - EMV_Data.xlsx: Baker, Bloom, Davis und Kost, "Overall EMV Tracker" ab 1985
#
# Aufruf:
#   python tools/daten/epu.py holen <Ordner>                      laedt die drei Tabellen (Browserkennung, kein Konto)
#   python tools/daten/epu.py bauen <Ordner> --stand=JJJJ-MM-TT   liest sie und schreibt die Datei
# Danach: python tools/daten/manifest.py bauen   (nicht signieren)
#
# Pflege: Die USA-Reihe erscheint monatlich, der Globale Index mit mehreren Monaten Verzug. Ein Abruf im Quartal genuegt.
#
# Schnittstelle der Datei: us, gl, emv = {"a": "JJJJ-MM", "w": [Wert je Monat, null bei Luecke]}, Werte auf eine Stelle gerundet.

import datetime
import json
import os
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from historie import blaetter  # noqa: E402

KENNUNG = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
BASIS = "https://www.policyuncertainty.com/media/"
DATEIEN = ["US_Policy_Uncertainty_Data.xlsx", "Global_Policy_Uncertainty_Data.xlsx", "EMV_Data.xlsx"]


def repo():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def holen(ordner):
    os.makedirs(ordner, exist_ok=True)
    for name in DATEIEN:
        r = urllib.request.Request(BASIS + name, headers={"User-Agent": KENNUNG, "Accept": "*/*"})
        with urllib.request.urlopen(r, timeout=300) as a:
            b = a.read()
        with open(os.path.join(ordner, name), "wb") as f:
            f.write(b)
        print("%s: %d Bytes" % (name, len(b)))


def monatsreihe(tabelle, spalte, kopfname, von):
    # tabelle: {Zeilennummer: {Spalte: Wert}}; liest Jahr (Spalte 1), Monat (2) und die Wertspalte
    kopf = next(k for k in sorted(tabelle) if str(tabelle[k].get(1)).strip() == "Year")
    if str(tabelle[kopf].get(spalte)).strip() != kopfname:
        raise SystemExit("Spalte %d heisst nicht %s" % (spalte, kopfname))
    je = {}
    for k in sorted(tabelle):
        if k <= kopf:
            continue
        z = tabelle[k]
        try:
            j, m = int(float(z[1])), int(float(z[2]))
        except (KeyError, ValueError):
            continue
        v = z.get(spalte)
        if isinstance(v, float):
            je[(j, m)] = round(v, 1)
    erster = min(je)
    letzter = max(je)
    if erster != von:
        raise SystemExit("Reihe %s beginnt bei %s, erwartet %s" % (kopfname, erster, von))
    w = []
    j, m = erster
    while (j, m) <= letzter:
        w.append(je.get((j, m)))
        m += 1
        if m == 13:
            j, m = j + 1, 1
    return {"a": "%04d-%02d" % erster, "w": w}


def bauen(ordner, stand):
    us = blaetter(os.path.join(ordner, DATEIEN[0]))["Main News Index"]
    gl = blaetter(os.path.join(ordner, DATEIEN[1]))["Sheet1"]
    emv = blaetter(os.path.join(ordner, DATEIEN[2]))["EMV Index"]
    out = {
        "stand": stand, "schema": 1,
        "quelle": {
            "name": "Economic Policy Uncertainty (Baker, Bloom, Davis)", "url": "https://www.policyuncertainty.com/",
            "lizenz": "CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/)",
        },
        "zitat": {
            "us": "Baker, Scott R., Nicholas Bloom and Steven J. Davis, 2016. \"Measuring Economic Policy Uncertainty\", Quarterly Journal of Economics, 131(4), 1593-1636.",
            "gl": "Davis, Steven J., 2016. \"An Index of Global Economic Policy Uncertainty\", Macroeconomic Review, October.",
            "emv": "Baker, Scott R., Nicholas Bloom, Steven J. Davis and Kyle J. Kost, 2019. \"Policy News and Stock Market Volatility\", NBER Working Paper 25720.",
        },
        "us": monatsreihe(us, 3, "News_Based_Policy_Uncert_Index", (1900, 1)),
        "gl": monatsreihe(gl, 3, "GEPU_current", (1997, 1)),
        "emv": monatsreihe(emv, 3, "Overall EMV Tracker", (1985, 1)),
    }
    ziel = os.path.join(repo(), "app", "src", "main", "assets", "indikatoren", "epu.json")
    with open(ziel, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    print("%s: %d Bytes (US %d Monate, global %d, EMV %d)" % (ziel, os.path.getsize(ziel), len(out["us"]["w"]), len(out["gl"]["w"]), len(out["emv"]["w"])))


def main():
    if len(sys.argv) < 3 or sys.argv[1] not in ("holen", "bauen"):
        raise SystemExit("Aufruf: epu.py holen <Ordner> | epu.py bauen <Ordner> [--stand=JJJJ-MM-TT]")
    if sys.argv[1] == "holen":
        holen(sys.argv[2])
        return
    stand = next((a.split("=", 1)[1] for a in sys.argv[3:] if a.startswith("--stand=")), datetime.date.today().isoformat())
    bauen(sys.argv[2], stand)


if __name__ == "__main__":
    main()
