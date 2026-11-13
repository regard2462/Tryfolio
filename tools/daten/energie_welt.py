#!/usr/bin/env python3
# Baut welt/energie.json (und die Kopie in app/src/main/assets/welt): Oel und Erdgas je Land (Foerderung, Verbrauch) und
# Gasfackeln je Land als Schaetzung aus Satellitenbildern.
#
# Quellen:
#   1. U.S. Energy Information Administration, International Energy Data, Jahreswerte je Land. Gemeinfrei (U.S. government
#      work, https://www.eia.gov/about/copyrights_reuse.php). Sammeldatei https://www.eia.gov/opendata/bulk/INTL.zip, darin
#      INTL.txt, jede Zeile ein JSON-Objekt (series_id, name, units, data = [[Jahr, Wert], ...] neueste zuerst).
#      Reihen: INTL.55-1-<LAND>-TBPD.A (Erdoel und andere Fluessigkeiten, Foerderung), INTL.5-2 (Verbrauch),
#      INTL.26-1-<LAND>-BCF.A (trockenes Erdgas, Foerderung), INTL.26-2 (Verbrauch).
#   2. Earth Observation Group, Payne Institute, Colorado School of Mines: VIIRS Nightfire upstream gas-flaring volumes,
#      Zenodo, CC BY-NC 4.0 (https://creativecommons.org/licenses/by-nc/4.0/). Dateien annual_country.csv (country_iso, year,
#      bcm, ...) und country_codes.csv (country_iso, country_name, ...). Mengen in Milliarden Kubikmetern. Schaetzung aus
#      Waermebildern der Satelliten, keine Messung der Betreiber.
#
# Aufruf:
#   python tools/daten/energie_welt.py bauen --intl=<INTL.txt> --fackel=<annual_country.csv> --codes=<country_codes.csv>
#   python tools/daten/energie_welt.py zeigen --intl=<INTL.txt> ...     Uebersicht, nichts schreiben
#   python tools/daten/energie_welt.py leer                           gueltige Datei ohne Laender (Abschnitt bleibt unsichtbar)
# Danach: python tools/daten/manifest.py bauen --signieren
#
# Regeln: Nur Laender mit dreibuchstabigem Code und einem Wert in den letzten zwei Jahren der Reihe. Zusammenfassungen der
# EIA (Welt, Regionen) haben vier Buchstaben und fehlen. Das letzte Jahr ist das juengste, fuer das mindestens 30 Laender einen
# Wert haben. Erdgas in Milliarden Kubikmetern (1 Mrd. Kubikfuss = 0,0283168 Mio. Kubikmeter, also * 0,0283168), Oel in Tausend
# Barrel am Tag.

import csv
import datetime
import json
import os
import re
import sys

QUELLE = "U.S. Energy Information Administration, International Energy Data; Earth Observation Group, Payne Institute (CC BY-NC 4.0)"
JAHRE = 20
MIN_LAENDER = 30
BCF_ZU_BCM = 0.0283168

# (Produkt, Taetigkeit, Einheit) -> (Block, Reihe); f = Foerderung, v = Verbrauch
SERIEN = {
    ("55", "1", "TBPD"): ("oel", "f"), ("5", "2", "TBPD"): ("oel", "v"),
    ("26", "1", "BCF"): ("gas", "f"), ("26", "2", "BCF"): ("gas", "v"),
}
NAME = re.compile(r"^.*? (?:production|consumption), (.*), Annual$")
SERIE = re.compile(r"^INTL\.(\d+)-(\d+)-([A-Z0-9]+)-([A-Z]+)\.A$")

# Deutsche Kurznamen (ISO-3 wie in der EIA-Tabelle); fehlt ein Land, steht der englische Name
DE = {
    "USA": "USA", "SAU": "Saudi-Arabien", "RUS": "Russland", "CAN": "Kanada", "CHN": "China", "IRQ": "Irak", "ARE": "VAE",
    "BRA": "Brasilien", "IRN": "Iran", "KWT": "Kuwait", "NOR": "Norwegen", "MEX": "Mexiko", "KAZ": "Kasachstan", "QAT": "Katar",
    "NGA": "Nigeria", "AGO": "Angola", "LBY": "Libyen", "DZA": "Algerien", "VEN": "Venezuela", "GBR": "Großbritannien",
    "IND": "Indien", "IDN": "Indonesien", "OMN": "Oman", "COL": "Kolumbien", "AZE": "Aserbaidschan", "EGY": "Ägypten",
    "ARG": "Argentinien", "GUY": "Guyana", "MYS": "Malaysia", "AUS": "Australien", "JPN": "Japan", "DEU": "Deutschland",
    "FRA": "Frankreich", "ITA": "Italien", "ESP": "Spanien", "KOR": "Südkorea", "TUR": "Türkei", "THA": "Thailand",
    "SGP": "Singapur", "TWN": "Taiwan", "ZAF": "Südafrika", "POL": "Polen", "NLD": "Niederlande", "BEL": "Belgien",
    "CHE": "Schweiz", "AUT": "Österreich", "SWE": "Schweden", "FIN": "Finnland", "DNK": "Dänemark", "GRC": "Griechenland",
    "PRT": "Portugal", "ROU": "Rumänien", "UKR": "Ukraine", "BLR": "Belarus", "UZB": "Usbekistan", "TKM": "Turkmenistan",
    "PAK": "Pakistan", "BGD": "Bangladesch", "VNM": "Vietnam", "PHL": "Philippinen", "CHL": "Chile", "PER": "Peru",
    "ECU": "Ecuador", "BOL": "Bolivien", "TTO": "Trinidad und Tobago", "SYR": "Syrien", "YEM": "Jemen", "BHR": "Bahrain",
    "BRN": "Brunei", "GAB": "Gabun", "COG": "Rep. Kongo", "GNQ": "Äquatorialguinea", "SDN": "Sudan", "SSD": "Südsudan",
    "TCD": "Tschad", "GHA": "Ghana", "CIV": "Elfenbeinküste", "MRT": "Mauretanien", "TUN": "Tunesien", "MAR": "Marokko",
    "ISR": "Israel", "JOR": "Jordanien", "LBN": "Libanon", "NZL": "Neuseeland", "HUN": "Ungarn", "CZE": "Tschechien",
    "SVK": "Slowakei", "BGR": "Bulgarien", "HRV": "Kroatien", "SRB": "Serbien", "IRL": "Irland", "MMR": "Myanmar",
    "TJK": "Tadschikistan", "KGZ": "Kirgisistan", "GEO": "Georgien", "ARM": "Armenien", "MNG": "Mongolei", "NER": "Niger",
    "ETH": "Äthiopien", "KEN": "Kenia", "TZA": "Tansania", "MOZ": "Mosambik", "COD": "DR Kongo", "CMR": "Kamerun",
    "SUR": "Suriname", "BLZ": "Belize", "CUB": "Kuba", "DOM": "Dominikanische Rep.", "LKA": "Sri Lanka", "NPL": "Nepal",
    "PRK": "Nordkorea", "AFG": "Afghanistan", "ALB": "Albanien", "LTU": "Litauen", "LVA": "Lettland", "EST": "Estland",
}


def repo():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def zahl(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def rund(l, stellen):
    return [None if v is None else round(v, stellen) for v in l]


# ------------------------------------------------------------------------------------------------------------------ EIA
def intl_lesen(pfad):
    # {Block: {Reihe: {Land: {"en": Name, "w": {Jahr: Wert}}}}}, juengster Stand der Datei
    roh = {"oel": {"f": {}, "v": {}}, "gas": {"f": {}, "v": {}}}
    stand = ""
    gesehen = {}
    with open(pfad, encoding="utf-8") as f:
        for z in f:
            z = z.strip()
            if not z.startswith("{"):
                continue
            try:
                o = json.loads(z)
            except ValueError:
                continue
            sid = o.get("series_id") or ""
            m = SERIE.match(sid)
            if not m:
                continue
            gesehen.setdefault((m.group(1), m.group(2), m.group(4)), o.get("name", ""))
            ziel = SERIEN.get((m.group(1), m.group(2), m.group(4)))
            land = m.group(3)
            if not ziel or len(land) != 3:
                continue
            n = NAME.match(o.get("name", ""))
            if not n:
                continue
            w = {}
            for j, v in o.get("data") or []:
                x = zahl(v)
                if x is not None and str(j).isdigit():
                    w[int(j)] = x
            if w:
                roh[ziel[0]][ziel[1]][land] = {"en": n.group(1), "w": w}
            lu = (o.get("last_updated") or "")[:10]
            if re.fullmatch(r"\d{4}-\d{2}-\d{2}", lu) and lu > stand:
                stand = lu
    return roh, stand, gesehen


def block_aus(reihen, umrechnung, stellen):
    # reihen: {"f": {...}, "v": {...}}; letztes Jahr: juengstes mit mindestens MIN_LAENDER Werten in der Foerderung
    f = reihen["f"]
    jahre = {}
    for d in f.values():
        for j in d["w"]:
            jahre[j] = jahre.get(j, 0) + 1
    voll = [j for j, n in jahre.items() if n >= MIN_LAENDER]
    if not voll:
        raise SystemExit("Zu wenige Laender mit Foerderung, Reihen fehlen oder Aufbau der Datei ist ein anderer (zeigen aufrufen)")
    bis = max(voll)
    ab = bis - JAHRE + 1
    laender = []
    for c, d in f.items():
        if not any(j >= bis - 1 for j in d["w"]):
            continue
        v = reihen["v"].get(c)
        ff = [None if d["w"].get(j) is None else d["w"][j] * umrechnung for j in range(ab, bis + 1)]
        vv = [None if not v or v["w"].get(j) is None else v["w"][j] * umrechnung for j in range(ab, bis + 1)]
        laender.append({"c": c, "en": d["en"], "de": DE.get(c, d["en"]), "f": rund(ff, stellen), "v": rund(vv, stellen)})
    laender.sort(key=lambda x: -(x["f"][-1] or x["f"][-2] or 0))
    return {"ab": ab, "laender": laender}


# ------------------------------------------------------------------------------------------------------------------ EOG
def fackel_lesen(pfad, codes):
    namen = {}
    with open(codes, encoding="utf-8", newline="") as f:
        for r in csv.DictReader(f):
            namen[r["country_iso"]] = r["country_name"]
    w = {}
    with open(pfad, encoding="utf-8", newline="") as f:
        for r in csv.DictReader(f):
            c = r["country_iso"].strip()
            x = zahl(r.get("bcm"))
            if len(c) != 3 or x is None or not r.get("year", "").isdigit():
                continue
            w.setdefault(c, {})[int(r["year"])] = x
    if not w:
        raise SystemExit("annual_country.csv: keine Zeilen mit country_iso, year und bcm")
    jahre = sorted({j for d in w.values() for j in d})
    ab, bis = jahre[0], jahre[-1]
    laender = []
    for c, d in w.items():
        s = [d.get(j) for j in range(ab, bis + 1)]
        en = namen.get(c, c)
        laender.append({"c": c, "en": en, "de": DE.get(c, en), "f": rund(s, 4)})
    laender.sort(key=lambda x: -(x["f"][-1] or 0))
    return {"ab": ab, "laender": laender}


def bauen(a):
    roh, stand, _ = intl_lesen(a["intl"])
    d = {
        "stand": stand or datetime.date.today().isoformat(), "quelle": QUELLE,
        "oel": block_aus(roh["oel"], 1.0, 1), "gas": block_aus(roh["gas"], BCF_ZU_BCM, 3),
    }
    if a.get("fackel") and a.get("codes"):
        d["fackel"] = fackel_lesen(a["fackel"], a["codes"])
    return d


def schreiben(d):
    text = json.dumps(d, ensure_ascii=False, separators=(",", ":")) + "\n"
    for teile in (("daten", "welt"), ("app", "src", "main", "assets", "welt")):
        ziel = os.path.join(repo(), *teile, "energie.json")
        with open(ziel, "w", encoding="utf-8", newline="") as f:
            f.write(text)
    print("welt/energie.json", len(text.encode("utf-8")), "Bytes")


def zeigen(d):
    print("Stand", d["stand"])
    for k in ("oel", "gas", "fackel"):
        b = d.get(k)
        if not b:
            continue
        n = len(b["laender"][0]["f"]) if b["laender"] else 0
        print(k, "ab", b["ab"], "bis", b["ab"] + n - 1, "Laender", len(b["laender"]))
        for l in b["laender"][:8]:
            print("  ", l["c"], l["de"], l["f"][-1], l.get("v", [None])[-1:])


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in ("bauen", "zeigen", "leer"):
        raise SystemExit("Aufruf: energie_welt.py bauen|zeigen|leer --intl=... --fackel=... --codes=...")
    if sys.argv[1] == "leer":
        schreiben({"stand": datetime.date.today().isoformat(), "quelle": QUELLE})
        return
    a = {k: v for k, v in (x[2:].split("=", 1) for x in sys.argv[2:] if x.startswith("--") and "=" in x)}
    if "intl" not in a:
        raise SystemExit("--intl=<INTL.txt> fehlt")
    d = bauen(a)
    zeigen(d)
    if sys.argv[1] == "bauen":
        schreiben(d)


if __name__ == "__main__":
    main()
