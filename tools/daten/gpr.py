#!/usr/bin/env python3
# Baut app/src/main/assets/indikatoren/gpr.json: den Geopolitical Risk Index (GPR) als Monatsreihe.
#
# Quelle und Bedingungen (am 14.11.2026 auf https://www.matteoiacoviello.com/gpr.htm gelesen): Das Material der Seite steht
# unter der Creative Commons BY Lizenz ("completely open access"), Nutzung, Weitergabe und Vervielfaeltigung in jedem Medium
# sind erlaubt, wenn Quelle und Autoren genannt werden. Zitat: Caldara, Dario and Matteo Iacoviello (2022), "Measuring
# Geopolitical Risk", American Economic Review, April, 112(4), pp.1194-1225. Die Seite erbittet zusaetzlich die Angabe von
# Seite und Abrufdatum (Feld "stand").
#
# Aufruf:
#   python tools/daten/gpr.py holen <Ordner>                      laedt data_gpr_export.xls (Browserkennung, kein Konto)
#   python tools/daten/gpr.py bauen <Ordner> --stand=JJJJ-MM-TT   liest die Tabelle und schreibt die Datei
# Danach: python tools/daten/manifest.py bauen   (nicht signieren)
#
# Pflege: Die Autoren aktualisieren monatlich. Fuer die App genuegt ein Abruf im Quartal. Aendern sich Spaltennamen, bricht
# "bauen" mit einer Meldung ab.
#
# Schnittstelle der Datei:
#   h  = {"a": "1900-01", "w": [...]}   GPRH, historischer Index (3 Zeitungen) ab 1900, Monatswerte
#   g  = {"a": "1985-01", "w": [...]}   GPR (10 Zeitungen) ab 1985; t = Drohungen (GPRT), d = Handlungen (GPRA), gleiche Laenge
#   lj = {ISO3: {"a": 1900, "w": [...]}}  Landesindex historisch (GPRHC), Jahresmittel
#   lm = {ISO3: {"a": "1985-01", "w": [...]}} Landesindex ab 1985 (GPRC), Monatswerte
# Werte unveraendert gerundet auf eine Stelle (Gesamtindex) bzw. drei Stellen (Laender); null bei Luecke.

import datetime
import json
import os
import sys
import urllib.request

KENNUNG = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
URL = "https://www.matteoiacoviello.com/gpr_files/data_gpr_export.xls"
DATEI = "data_gpr_export.xls"


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


def zahl(v, stellen):
    return round(v, stellen) if isinstance(v, float) else None


def bauen(ordner, stand):
    import xlrd  # nur fuer die .xls der Quelle

    wb = xlrd.open_workbook(os.path.join(ordner, DATEI))
    sh = wb.sheet_by_index(0)
    kopf = sh.row_values(0)
    spalte = {n: i for i, n in enumerate(kopf) if n}
    for n in ("month", "GPR", "GPRT", "GPRA", "GPRH"):
        if n not in spalte:
            raise SystemExit("Spalte fehlt: " + n)
    # Die Monate stehen als Excel-Datum, der erste (1) ist vor dem 1. Maerz 1900 mehrdeutig: die Zeilen sind lueckenlos
    # monatlich ab Januar 1900, die Zaehlung wird an den eindeutigen Daten geprueft.
    zeilen = []
    for r in range(1, sh.nrows):
        v = sh.row_values(r)
        if not isinstance(v[0], float):
            continue
        i = len(zeilen)
        k = (1900 + i // 12, i % 12 + 1)
        if v[0] > 61:
            d = xlrd.xldate_as_tuple(v[0], wb.datemode)
            if (d[0], d[1]) != k:
                raise SystemExit("Monatsfolge gestoert bei Zeile %d: %s statt %s" % (r, (d[0], d[1]), k))
        zeilen.append((k, v))
    erster = zeilen[0][0]
    if erster != (1900, 1):
        raise SystemExit("Reihe beginnt nicht im Januar 1900: %s" % (erster,))
    def reihe(name, stellen, ab=None):
        w = [(k, zahl(v[spalte[name]], stellen)) for k, v in zeilen]
        if ab:
            w = [x for x in w if x[0] >= ab]
        # Luecken am Ende abschneiden
        while w and w[-1][1] is None:
            w.pop()
        return w

    def monat(k):
        return "%04d-%02d" % k

    h = reihe("GPRH", 1)
    g = reihe("GPR", 1, (1985, 1))
    t = reihe("GPRT", 1, (1985, 1))
    d = reihe("GPRA", 1, (1985, 1))
    if len(h) < 1500 or len(g) < 480:
        raise SystemExit("Reihe zu kurz")
    n = min(len(g), len(t), len(d))
    out = {
        "stand": stand, "schema": 1,
        "quelle": {
            "name": "Caldara und Iacoviello, Geopolitical Risk Index", "url": "https://www.matteoiacoviello.com/gpr.htm",
            "lizenz": "CC BY (https://creativecommons.org/licenses/by/4.0/)",
            "zitat": "Caldara, Dario and Matteo Iacoviello (2022), \"Measuring Geopolitical Risk\", American Economic Review, April, 112(4), pp.1194-1225.",
        },
        "bis": monat(h[-1][0]),
        "h": {"a": monat(h[0][0]), "w": [x[1] for x in h]},
        "g": {"a": monat(g[0][0]), "w": [x[1] for x in g[:n]]},
        "t": {"a": monat(t[0][0]), "w": [x[1] for x in t[:n]]},
        "d": {"a": monat(d[0][0]), "w": [x[1] for x in d[:n]]},
        "lj": {}, "lm": {},
    }
    laender = sorted(n[len("GPRC_"):] for n in spalte if n.startswith("GPRC_"))
    if len(laender) < 40:
        raise SystemExit("zu wenige Laenderspalten")
    for iso in laender:
        mon = reihe("GPRC_" + iso, 3, (1985, 1))
        if mon:
            out["lm"][iso] = {"a": monat(mon[0][0]), "w": [x[1] for x in mon]}
        if "GPRHC_" + iso in spalte:
            je = {}
            for k, v in zeilen:
                x = zahl(v[spalte["GPRHC_" + iso]], 6)
                if x is not None:
                    je.setdefault(k[0], []).append(x)
            jahre = sorted(j for j, xs in je.items() if len(xs) >= 6)
            if jahre:
                out["lj"][iso] = {"a": jahre[0], "w": [round(sum(je[j]) / len(je[j]), 3) if j in je and len(je[j]) >= 6 else None for j in range(jahre[0], jahre[-1] + 1)]}
    ziel = os.path.join(repo(), "app", "src", "main", "assets", "indikatoren", "gpr.json")
    with open(ziel, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    print("%s: %d Bytes, %d Monate historisch bis %s, %d Laender" % (ziel, os.path.getsize(ziel), len(h), out["bis"], len(out["lm"])))


def main():
    if len(sys.argv) < 3 or sys.argv[1] not in ("holen", "bauen"):
        raise SystemExit("Aufruf: gpr.py holen <Ordner> | gpr.py bauen <Ordner> [--stand=JJJJ-MM-TT]")
    if sys.argv[1] == "holen":
        holen(sys.argv[2])
        return
    stand = next((a.split("=", 1)[1] for a in sys.argv[3:] if a.startswith("--stand=")), datetime.date.today().isoformat())
    bauen(sys.argv[2], stand)


if __name__ == "__main__":
    main()
