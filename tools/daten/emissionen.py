#!/usr/bin/env python3
# Direkte Treibhausgas-Emissionen je Mutterfirma aus dem Greenhouse Gas Reporting Program (GHGRP) der US-Umweltbehoerde
# EPA, ueber die Envirofacts-Schnittstelle (data.epa.gov/efservice, ohne Schluessel). Ergebnis ist die Datei
# app/src/main/assets/firmen/emissionen.json.
#
#   python tools/daten/emissionen.py [ziel.json]
#
# Je Anlage und Jahr steht in PUB_DIM_FACILITY die gemeldete oberste US-Muttergesellschaft als Text, oft mit
# Eigentumsanteilen ("A (60%); B (40%)"). Die Emissionen der Anlage (alle Gase in t CO2e, Summe aus
# PUB_FACTS_SECTOR_GHG_EMISSION) werden nach diesen Anteilen auf die Muetter verteilt. Eine Mutter ohne Anteilsangabe
# bekommt bei einem einzigen Eintrag 100 Prozent, bei mehreren gleiche Teile. Die Zuordnung zu Aktien geschieht in der
# App ueber den Namen, nur bei eindeutigem Treffer.
#
# Datei: {format, stand, jahr0, jahre, firmen: [{n: Name, y: [Kuerzel im Katalog], e: [t je Jahr ab jahr0],
#         z: [Zahl der Anlagen je Jahr], s: [[Anlage, Staat, Ort, t im letzten Jahr, Anteil in Prozent], ...]}]}
# y steht, wenn der Name zur Muttergesellschaft einer Aktie im Katalog passt, auch bei Toechtern und Zusaetzen ("Shell
# Petroleum Inc", "BP America Inc"); die Regeln stehen in emissionen_eu.py (zuordnen). Ohne y bleibt der Name, die App gleicht
# ihn dann ueber den Firmennamen ab.
#
#   python tools/daten/emissionen.py --zuordnung [datei.json]   ergaenzt y in einer vorhandenen Datei, ohne Abruf
# Die EPA stellt die Daten als Werk der US-Regierung frei bereit (https://www.epa.gov/ghgreporting).
import datetime
import json
import re
import sys
import time
import urllib.request

BASIS = "https://data.epa.gov/efservice"
KENNUNG = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"}
SEITE = 4000
JAHRE = 12
# Unter dieser Jahressumme (Tonnen CO2e, anteilig) fliegt eine Mutter aus der Datei
MINDEST = 25000


def holen(pfad):
    for versuch in range(4):
        try:
            req = urllib.request.Request(BASIS + pfad, headers=KENNUNG)
            return json.loads(urllib.request.urlopen(req, timeout=180).read().decode("utf-8", "replace"))
        except Exception as e:  # noqa: BLE001
            if versuch == 3:
                raise
            time.sleep(5 * (versuch + 1))


def zahl(tabelle, jahr):
    r = holen(f"/{tabelle}/year/=/{jahr}/COUNT/JSON")
    return int(r[0]["TOTALQUERYRESULTS"]) if r else 0


def zeilen(tabelle, jahr):
    n = zahl(tabelle, jahr)
    for a in range(0, n, SEITE):
        yield from holen(f"/{tabelle}/year/=/{jahr}/rows/{a}:{min(a + SEITE, n) - 1}/JSON")
        time.sleep(0.3)


ANTEIL = re.compile(r"\s*\((\d+(?:\.\d+)?)\s*%\)\s*$")


def muetter(text):
    # [(Name, Anteil 0..1)] aus "Name (60%); Name2 (40%)"
    teile = [t.strip() for t in (text or "").split(";") if t.strip()]
    aus = []
    for t in teile:
        m = ANTEIL.search(t)
        name = ANTEIL.sub("", t).strip().rstrip(",")
        if name:
            aus.append((name, float(m.group(1)) / 100 if m else None))
    if not aus:
        return []
    ohne = [i for i, (_, a) in enumerate(aus) if a is None]
    if ohne:
        rest = max(0.0, 1 - sum(a for _, a in aus if a is not None))
        je = (rest if len(ohne) < len(aus) else 1.0) / len(ohne)
        aus = [(n, je if a is None else a) for n, a in aus]
    return aus


FORMEN = {
    "INC", "INCORPORATED", "CORP", "CORPORATION", "CO", "COMPANY", "COMPANIES", "LLC", "LP", "LTD", "LIMITED", "PLC", "HOLDINGS",
    "HOLDING", "GROUP", "USA", "US", "AMERICA", "AMERICAS", "NORTH", "INTERNATIONAL", "AND", "SA", "NV", "AG", "SE", "NA",
}


def schluessel(name):
    # Grobe Entsprechung des Namensschluessels der App: ohne Rechtsform und Zusaetze am Ende, zusammengeschrieben
    w = re.sub(r"\([^)]*\)", " ", name.upper()).replace(".", "")
    w = [x for x in re.split(r"[^A-Z0-9&]+", w) if x]
    if len(w) > 1 and w[0] == "THE":
        w = w[1:]
    while len(w) > 1 and w[-1] in FORMEN:
        w.pop()
    return "".join(w)


def beschneiden(firmen):
    # Namensvarianten fruehere Jahre bleiben nur, wenn eine Variante mit gleichem Schluessel im letzten Jahr meldet
    aktuell = {}
    for f in firmen:
        if f["e"][-1] > 0:
            aktuell[schluessel(f["n"])] = True
    return [f for f in firmen if aktuell.get(schluessel(f["n"]))]


def mit_symbolen(firmen):
    # Setzt y (Kuerzel im Katalog) bei jedem Namen, der zu genau einer Firma des Katalogs passt
    from emissionen_eu import katalog, praefixe, zuordnen
    kat = katalog("app/src/main/assets/katalog.json")
    tab = praefixe(kat)
    treffer = 0
    for f in firmen:
        f.pop("y", None)
        e = zuordnen(f["n"], "US", kat, tab)
        if e:
            f["y"] = list(e["y"])
            treffer += 1
    return treffer


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "--zuordnung":
        pfad = sys.argv[2] if len(sys.argv) > 2 else "app/src/main/assets/firmen/emissionen.json"
        d = json.load(open(pfad, encoding="utf-8"))
        print(mit_symbolen(d["firmen"]), "von", len(d["firmen"]), "Namen mit Treffer im Katalog")
        json.dump(d, open(pfad, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
        return
    ziel = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/firmen/emissionen.json"
    letzt = datetime.date.today().year
    while letzt > 2010 and zahl("PUB_DIM_FACILITY", letzt) == 0:
        letzt -= 1
    jahr0 = letzt - JAHRE + 1
    firmen = {}
    for j in range(jahr0, letzt + 1):
        summe = {}
        for z in zeilen("PUB_FACTS_SECTOR_GHG_EMISSION", j):
            v = z.get("co2e_emission")
            if v is not None:
                summe[z["facility_id"]] = summe.get(z["facility_id"], 0.0) + float(v)
        anlagen = 0
        for f in zeilen("PUB_DIM_FACILITY", j):
            t = summe.get(f["facility_id"])
            if not t or t <= 0:
                continue
            anlagen += 1
            for name, anteil in muetter(f.get("parent_company")):
                e = firmen.setdefault(name, {"e": [0.0] * JAHRE, "z": [0] * JAHRE, "s": {}})
                i = j - jahr0
                e["e"][i] += t * anteil
                e["z"][i] += 1
                if j == letzt:
                    e["s"][f["facility_id"]] = [
                        (f.get("facility_name") or "").strip().title()[:60], (f.get("state") or "").strip().upper(),
                        (f.get("city") or "").strip().title()[:40], round(t), round(anteil * 100),
                    ]
        print(j, anlagen, "Anlagen mit Emissionen", flush=True)
    aus = []
    for name, e in firmen.items():
        if max(e["e"]) < MINDEST:
            continue
        standorte = sorted(e["s"].values(), key=lambda s: s[3], reverse=True)[:6]
        aus.append({"n": name[:80], "e": [round(x) for x in e["e"]], "z": e["z"], "s": standorte})
    aus = beschneiden(aus)
    mit_symbolen(aus)
    aus.sort(key=lambda x: max(x["e"]), reverse=True)
    json.dump({"format": 1, "stand": datetime.date.today().isoformat(), "jahr0": jahr0, "jahre": JAHRE, "firmen": aus},
              open(ziel, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print(len(aus), "Mutterfirmen, letztes Jahr", letzt)


if __name__ == "__main__":
    main()
