#!/usr/bin/env python3
# Baut welt/gold.json (und die Kopie in app/src/main/assets/welt): Goldbestand der Zentralbanken je Land in Feinunzen.
#
# Quelle: Internationaler Waehrungsfonds, International Reserves and Foreign Currency Liquidity (IRFCL), Indikator
# IRFCLDT1_IRFCL56V_FTO (Gold in Feinunzen), Monatswerte, freier Abruf ohne Schluessel ueber die SDMX-Schnittstelle
# https://api.imf.org/external/sdmx/2.1/data/IMF.STA,IRFCL/.IRFCLDT1_IRFCL56V_FTO..M?startPeriod=JJJJ-MM
# (Schluessel LAND.INDIKATOR.SEKTOR.HAEUFIGKEIT). Datensatz: https://data.imf.org/en/datasets/IMF.STA:IRFCL
# Es melden rund 80 Laender, alle anderen fuehrt der IWF in diesem Datensatz nicht.
#
# Aufruf:
#   python tools/daten/goldreserven.py holen <Ordner>      holt die Monatswerte ab 25 Monaten vor heute nach <Ordner>/irfcl.xml
#   python tools/daten/goldreserven.py bauen <Ordner>      liest <Ordner>/irfcl.xml und schreibt welt/gold.json (beide Orte)
#   python tools/daten/goldreserven.py zeigen <Ordner>     Uebersicht, nichts schreiben
# Danach: python tools/daten/manifest.py bauen --signieren
#
# Regeln: Je Land die Reihe mit dem Sektor S1XS1311, sonst S1X, sonst S1311. Beobachtungen ohne OBS_VALUE (STATUS "-") fehlen.
# Zusammenfassungen und Nicht-Laender (G163, EZB, Weltbankgruppe, Kosovo ohne ISO-Code) fehlen, ebenso Werte von 0 und jedes
# Land mit mehr Unzen als die USA (offensichtlich falsche Meldung, etwa Angola mit 592,9 Mio. Unzen). Namen wie in
# welt/energie.json. Je Land: letzter Monat mit Wert (m), Unzen dort (u), Unzen zwoelf Monate davor (u12, falls vorhanden).

import datetime
import json
import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

QUELLE = "International Monetary Fund, International Reserves and Foreign Currency Liquidity (IRFCL)"
URL = "https://api.imf.org/external/sdmx/2.1/data/IMF.STA,IRFCL/.IRFCLDT1_IRFCL56V_FTO..M?startPeriod="
AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
MONATE_ZURUECK = 25
SEKTOREN = ["S1XS1311", "S1X", "S1311"]
NICHT_LAENDER = {"G163", "EZB", "ECB", "WBG", "KOS"}
DATEI = "irfcl.xml"
# Deutsche Namen, die energie.json nicht kennt
DE_NAMEN = {
    "KHM": "Kambodscha", "CYP": "Zypern", "MKD": "Nordmazedonien", "SVN": "Slowenien", "ISL": "Island", "LUX": "Luxemburg",
    "HKG": "Hongkong", "MLT": "Malta", "MDA": "Moldau", "MUS": "Mauritius", "PRY": "Paraguay", "URY": "Uruguay",
}
PERIODE = re.compile(r"^(\d{4})-M(\d{2})$")


def repo():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def start_periode(heute):
    m = heute.year * 12 + heute.month - 1 - MONATE_ZURUECK
    return "%04d-%02d" % (m // 12, m % 12 + 1)


def holen(ordner):
    os.makedirs(ordner, exist_ok=True)
    url = URL + start_periode(datetime.date.today())
    req = urllib.request.Request(url, headers={"User-Agent": AGENT, "Accept": "application/xml"})
    with urllib.request.urlopen(req, timeout=120) as r:
        daten = r.read()
    with open(os.path.join(ordner, DATEI), "wb") as f:
        f.write(daten)
    print(DATEI, len(daten), "Bytes")


def monat_index(p):
    m = PERIODE.match(p or "")
    return int(m.group(1)) * 12 + int(m.group(2)) - 1 if m else None


def monat_text(i):
    return "%04d-%02d" % (i // 12, i % 12 + 1)


def lesen(pfad):
    # {Land: {Monatsindex: Unzen}} nach der Sektorregel
    baum = ET.parse(pfad)
    je_land = {}
    for s in baum.iter():
        if not s.tag.endswith("Series"):
            continue
        c = s.attrib.get("COUNTRY", "")
        sektor = s.attrib.get("SECTOR", "")
        if sektor not in SEKTOREN:
            continue
        w = {}
        for o in s:
            if not o.tag.endswith("Obs"):
                continue
            i = monat_index(o.attrib.get("TIME_PERIOD"))
            try:
                x = float(o.attrib.get("OBS_VALUE"))
            except (TypeError, ValueError):
                continue
            if i is not None:
                w[i] = x
        if w:
            je_land.setdefault(c, {})[sektor] = w
    land = {}
    for c, d in je_land.items():
        for sektor in SEKTOREN:
            if sektor in d:
                land[c] = d[sektor]
                break
    return land


def namen():
    pfad = os.path.join(repo(), "daten", "welt", "energie.json")
    with open(pfad, encoding="utf-8") as f:
        e = json.load(f)
    n = {}
    for b in ("oel", "gas", "fackel"):
        for l in (e.get(b) or {}).get("laender", []):
            n.setdefault(l["c"], (l["en"], l.get("de") or l["en"]))
    return n


def bauen(ordner):
    roh = lesen(os.path.join(ordner, DATEI))
    nm = namen()
    verworfen = []
    kandidaten = {}
    for c, w in roh.items():
        if len(c) != 3 or not c.isalpha() or c in NICHT_LAENDER:
            verworfen.append((c, "kein Land"))
            continue
        i = max(w)
        if w[i] <= 0:
            verworfen.append((c, "Wert 0"))
            continue
        kandidaten[c] = (i, w)
    usa = kandidaten.get("USA")
    if not usa:
        raise SystemExit("USA fehlen, Aufbau der Antwort ist ein anderer (zeigen aufrufen)")
    grenze = usa[1][usa[0]]
    laender = []
    for c, (i, w) in kandidaten.items():
        if w[i] > grenze:
            verworfen.append((c, "mehr als die USA (%.0f)" % w[i]))
            continue
        en, de = nm.get(c, (c, c))
        if de == en and c in DE_NAMEN:
            de = DE_NAMEN[c]
        if c not in nm:
            print("Kein Name in energie.json:", c)
        l = {"c": c, "en": en, "de": de, "m": monat_text(i), "u": round(w[i], 3)}
        v = w.get(i - 12)
        if v is not None and v > 0:
            l["u12"] = round(v, 3)
        laender.append(l)
    laender.sort(key=lambda x: -x["u"])
    d = {"stand": datetime.date.today().isoformat(), "quelle": QUELLE, "laender": laender}
    return d, verworfen


def schreiben(d):
    text = json.dumps(d, ensure_ascii=False, separators=(",", ":")) + "\n"
    for teile in (("daten", "welt"), ("app", "src", "main", "assets", "welt")):
        ziel = os.path.join(repo(), *teile, "gold.json")
        with open(ziel, "w", encoding="utf-8", newline="") as f:
            f.write(text)
    print("welt/gold.json", len(text.encode("utf-8")), "Bytes")


def zeigen(d, verworfen):
    print("Stand", d["stand"], "Laender", len(d["laender"]))
    monate = {}
    for l in d["laender"]:
        monate[l["m"]] = monate.get(l["m"], 0) + 1
    print("Letzte Monate", sorted(monate.items()))
    for l in d["laender"][:8]:
        print("  ", l["c"], l["de"], l["m"], l["u"], round(l["u"] / 32150.7466, 1), "t", l.get("u12"))
    print("Verworfen", verworfen)


def main():
    if len(sys.argv) < 3 or sys.argv[1] not in ("holen", "bauen", "zeigen"):
        raise SystemExit("Aufruf: goldreserven.py holen|bauen|zeigen <Ordner>")
    ordner = sys.argv[2]
    if sys.argv[1] == "holen":
        holen(ordner)
        return
    d, verworfen = bauen(ordner)
    zeigen(d, verworfen)
    if sys.argv[1] == "bauen":
        schreiben(d)


if __name__ == "__main__":
    main()
