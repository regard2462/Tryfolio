#!/usr/bin/env python3
# Baut app/src/main/assets/globus/konflikte-reihe.json: bewaffnete Konflikte je Land und Jahr seit 1946 (Laenderblatt).
#
# Quelle und Bedingungen (am 14.11.2026 auf https://ucdp.uu.se/downloads/ gelesen): Alle UCDP-Datensaetze stehen kostenlos
# unter CC BY 4.0, Weitergabe ist erlaubt, wenn die zugehoerigen Veroeffentlichungen zitiert werden. Zitat zum Datensatz:
#   Davies, Shawn, Therese Pettersson, and Magnus Oberg, Organized violence 1989-2025, and violent political protests,
#   Journal of Peace Research, 2026. https://doi.org/10.1093/jopres/xjag046
#   Gleditsch, Nils Petter, Peter Wallensteen, Mikael Eriksson, Margareta Sollenberg, and Havard Strand (2002), Armed
#   Conflict 1946-2001: A New Dataset, Journal of Peace Research 39(5). (UCDP/PRIO Armed Conflict Dataset)
# Datensatz: UCDP/PRIO Armed Conflict Dataset, Version 26.1 (ucdp-prio-acd-261-csv.zip), 1946 bis 2025.
#
# Aufruf:
#   python tools/daten/ucdp_reihe.py holen <Ordner>                      laedt die Zip-Datei (Browserkennung, kein Konto)
#   python tools/daten/ucdp_reihe.py bauen <Ordner> --stand=JJJJ-MM-TT   liest sie und schreibt die Datei
# Danach: python tools/daten/manifest.py bauen   (nicht signieren)
#
# Pflege: Neue Fassung jedes Jahr im Fruehjahr; der Dateiname traegt die Versionsnummer (VERSION unten und URL anpassen).
#
# Zuordnung: Jede Zeile gilt fuer jedes Land in "gwno_loc" (dort wird gekaempft oder das Land wird angegriffen), wie die
# Datei konflikte.json. Aufgeloeste Staaten laufen unter dem heutigen Kennzeichen: Sowjetunion RUS, Jugoslawien
# SRB, Suedjemen YEM, Suedvietnam VNM, Hyderabad IND.
#
# Schnittstelle der Datei: {"v": "26.1", "a": 1946, "bis": 2025, "l": {ISO3: {"s": "0011220...", "n": [Anzahl Konflikte je Jahr]}}}
# s: groesste Stufe im Jahr als Ziffer (0 kein Konflikt, 1 = 25 bis 999 Gefechtstote, 2 = ab 1000), eine Ziffer je Jahr ab a.

import csv
import datetime
import io
import json
import os
import sys
import urllib.request
import zipfile

KENNUNG = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
VERSION = "26.1"
URL = "https://ucdp.uu.se/downloads/ucdpprio/ucdp-prio-acd-261-csv.zip"
DATEI = "ucdp-prio-acd-261-csv.zip"

# Gleditsch-Ward-Nummer zu ISO alpha-3
GW = {
    2: "USA", 40: "CUB", 41: "HTI", 42: "DOM", 52: "TTO", 55: "GRD", 70: "MEX", 90: "GTM", 91: "HND", 92: "SLV", 93: "NIC",
    94: "CRI", 95: "PAN", 100: "COL", 101: "VEN", 115: "SUR", 130: "ECU", 135: "PER", 145: "BOL", 150: "PRY", 155: "CHL",
    160: "ARG", 165: "URY", 200: "GBR", 210: "NLD", 220: "FRA", 230: "ESP", 310: "HUN", 339: "ALB", 343: "MKD", 344: "HRV",
    345: "SRB", 346: "BIH", 350: "GRC", 352: "CYP", 359: "MDA", 360: "ROU", 365: "RUS", 369: "UKR", 372: "GEO", 373: "AZE",
    404: "GNB", 420: "GMB", 432: "MLI", 433: "SEN", 434: "BEN", 435: "MRT", 436: "NER", 437: "CIV", 438: "GIN", 439: "BFA",
    450: "LBR", 451: "SLE", 452: "GHA", 461: "TGO", 471: "CMR", 475: "NGA", 481: "GAB", 482: "CAF", 483: "TCD", 484: "COG",
    490: "COD", 500: "UGA", 501: "KEN", 510: "TZA", 516: "BDI", 517: "RWA", 520: "SOM", 522: "DJI", 530: "ETH", 531: "ERI",
    540: "AGO", 541: "MOZ", 552: "ZWE", 560: "ZAF", 570: "LSO", 580: "MDG", 581: "COM", 600: "MAR", 615: "DZA", 616: "TUN",
    620: "LBY", 625: "SDN", 626: "SSD", 630: "IRN", 640: "TUR", 645: "IRQ", 651: "EGY", 652: "SYR", 660: "LBN", 663: "JOR",
    666: "ISR", 670: "SAU", 678: "YEM", 680: "YEM", 690: "KWT", 698: "OMN", 700: "AFG", 702: "TJK", 703: "KGZ", 704: "UZB",
    710: "CHN", 713: "TWN", 731: "PRK", 732: "KOR", 750: "IND", 751: "IND", 770: "PAK", 771: "BGD", 775: "MMR", 780: "LKA",
    790: "NPL", 800: "THA", 811: "KHM", 812: "LAO", 816: "VNM", 817: "VNM", 820: "MYS", 835: "BRN", 840: "PHL", 850: "IDN",
    900: "AUS", 910: "PNG",
}


def repo():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def holen(ordner):
    os.makedirs(ordner, exist_ok=True)
    r = urllib.request.Request(URL, headers={"User-Agent": KENNUNG, "Accept": "*/*"})
    with urllib.request.urlopen(r, timeout=300) as a:
        b = a.read()
    with open(os.path.join(ordner, DATEI), "wb") as f:
        f.write(b)
    print("%s: %d Bytes" % (DATEI, len(b)))


def bauen(ordner, stand):
    with zipfile.ZipFile(os.path.join(ordner, DATEI)) as z:
        name = next(n for n in z.namelist() if n.lower().endswith(".csv"))
        zeilen = list(csv.DictReader(io.TextIOWrapper(z.open(name), encoding="utf-8")))
    if not zeilen or zeilen[0].get("version") != VERSION:
        raise SystemExit("Version der Datei ist nicht " + VERSION)
    von = min(int(z["year"]) for z in zeilen)
    bis = max(int(z["year"]) for z in zeilen)
    je = {}  # ISO3 -> {Jahr: (hoechste Stufe, {Konflikt-IDs})}
    for z in zeilen:
        jahr = int(z["year"])
        stufe = int(z["intensity_level"])
        for g in z["gwno_loc"].split(","):
            g = int(g.strip())
            if g not in GW:
                raise SystemExit("Unbekannte Laendernummer %d (%s)" % (g, z["location"]))
            iso = GW[g]
            alt = je.setdefault(iso, {}).setdefault(jahr, [0, set()])
            alt[0] = max(alt[0], stufe)
            alt[1].add(z["conflict_id"])
    out = {"stand": stand, "schema": 1, "v": VERSION, "a": von, "bis": bis, "l": {}}
    for iso in sorted(je):
        jahre = je[iso]
        out["l"][iso] = {
            "s": "".join(str(jahre[j][0]) if j in jahre else "0" for j in range(von, bis + 1)),
            "n": [len(jahre[j][1]) if j in jahre else 0 for j in range(von, bis + 1)],
        }
    ziel = os.path.join(repo(), "app", "src", "main", "assets", "globus", "konflikte-reihe.json")
    with open(ziel, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    print("%s: %d Bytes, %d Laender, %d bis %d" % (ziel, os.path.getsize(ziel), len(out["l"]), von, bis))


def main():
    if len(sys.argv) < 3 or sys.argv[1] not in ("holen", "bauen"):
        raise SystemExit("Aufruf: ucdp_reihe.py holen <Ordner> | ucdp_reihe.py bauen <Ordner> [--stand=JJJJ-MM-TT]")
    if sys.argv[1] == "holen":
        holen(sys.argv[2])
        return
    stand = next((a.split("=", 1)[1] for a in sys.argv[3:] if a.startswith("--stand=")), datetime.date.today().isoformat())
    bauen(sys.argv[2], stand)


if __name__ == "__main__":
    main()
