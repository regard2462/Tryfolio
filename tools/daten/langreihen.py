#!/usr/bin/env python3
# Baut app/src/main/assets/globus/langreihen.json: sehr lange Jahresreihen einzelner Laender fuer das Laenderblatt
# (Verbraucherpreise, kurzfristiger Zins, Rendite von Staatsanleihen), aus den historischen Statistiken der Notenbanken.
#
# Quellen und Bedingungen (am 12.11.2026 an den Quellen gelesen):
#   - Sveriges Riksbank, Historical Monetary Statistics of Sweden
#     https://www.riksbank.se/en-gb/statistics/historical-monetary-statistics-of-sweden/
#     Bedingungen https://www.riksbank.se/en-gb/about-the-riksbank/about-the-website/open-data--information-available-for-re-use/ :
#     veroeffentlichtes Material der Riksbank ist "freely available and may be further used without any special consent
#     or agreement being required", mit Quelle "Sveriges riksbank" und Datum. Bearbeitete Werte duerfen nicht als Angabe
#     der Riksbank erscheinen: die Datei gibt die Werte deshalb unveraendert wieder.
#   - Norges Bank, Historical Monetary Statistics for Norway
#     https://www.norges-bank.no/en/topics/Statistics/Historical-monetary-statistics/
#     Bedingungen https://www.norges-bank.no/en/Disclaimer/ : Kopien des Materials sind erlaubt, "provided that Norges Bank
#     is quoted as the source"; der Inhalt darf nicht veraendert werden. Die Werte stehen deshalb wie in der Quelle
#     (gleiche Basis, gleiche Einheit, keine Rundung). Gerundet wird erst bei der Anzeige in der App.
#   Beide Quellen bitten um das Zitat der Autoren; es steht je Land in der Datei (Feld "zitat") und wird aus den Tabellen
#   selbst gelesen.
#
# Aufruf:
#   python tools/daten/langreihen.py holen <Ordner>                      laedt die Tabellen nach <Ordner>/riksbank und <Ordner>/norges
#   python tools/daten/langreihen.py bauen <Ordner> --stand=JJJJ-MM-TT   liest die vorhandenen Unterordner
# Die Tabellen der Riksbank sind .xls: dafuer braucht "bauen" das Paket xlrd. Danach: python tools/daten/manifest.py bauen
#
# Pflege: Die Quellen ergaenzen die Reihen unregelmaessig (Norwegen zuletzt bis 2017, Schweden bis 2012). Einmal im Jahr
# "holen" und "bauen"; aendern sich Blattnamen oder Spalten, bricht "bauen" mit einer Meldung ab.
#
# Schnittstelle der Datei: l.<ISO3>.<art> = {"a": erstes Jahr, "w": [Wert je Jahr, null bei Luecke]} mit art preise
# (Index der Verbraucherpreise in der Basis der Quelle), zins und anleihe (Prozent im Jahresmittel).

import json
import os
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from historie import blaetter  # noqa: E402

KENNUNG = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
RIKSBANK = "https://www.riksbank.se/globalassets/media/forskning/monetar-statistik/"
NORGES = "https://www.norges-bank.no/globalassets/upload/hms/data/"
DATEIEN = {
    "riksbank": {
        "volumeich8consumerpriceindex.xls": RIKSBANK + "volym1/volumeich8consumerpriceindex.xls",
        "volumeiich6stocksandbonds.xls": RIKSBANK + "volym2/volumeiich6stocksandbonds.xls",
    },
    "norges": {
        "cpi.xlsx": NORGES + "cpi.xlsx",
        "shortterm_ir.xlsx": NORGES + "shortterm_ir.xlsx",
    },
}


def repo():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def reihe(je_jahr):
    # {Jahr: Wert} zu {"a": erstes Jahr, "w": [...]} ohne Luecken am Rand
    jahre = sorted(j for j, v in je_jahr.items() if v is not None)
    if len(jahre) < 30:
        raise SystemExit("Reihe zu kurz")
    return {"a": jahre[0], "w": [je_jahr.get(j) for j in range(jahre[0], jahre[-1] + 1)]}


def zahl(v):
    return v if isinstance(v, float) else None


def schweden(ordner):
    import xlrd  # nur fuer die .xls der Riksbank

    def blatt(wb, name):
        sh = wb.sheet_by_name(name)
        return [sh.row_values(r) for r in range(sh.nrows)]

    wb = xlrd.open_workbook(os.path.join(ordner, "volumeich8consumerpriceindex.xls"))
    zeilen = blatt(wb, "Table I.A8.1")
    if not str(zeilen[3][2]).startswith("Inflation index"):
        raise SystemExit("Riksbank: Preistabelle unerwartet")
    preise = {int(z[0]): zahl(z[2]) for z in zeilen[4:] if isinstance(z[0], float)}
    zitat_preise = next(str(z[2]).strip() for z in blatt(wb, "Front") if str(z[2]).startswith("Rodney Edvinsson and Johan"))

    wb = xlrd.open_workbook(os.path.join(ordner, "volumeiich6stocksandbonds.xls"))
    zeilen = blatt(wb, "Table II.A6.3")
    if not (str(zeilen[3][1]).startswith("Short-term government bill yield") and str(zeilen[3][3]).startswith("Long-term government bond yield")):
        raise SystemExit("Riksbank: Zinstabelle unerwartet")
    zins = {int(z[0]): zahl(z[1]) for z in zeilen[5:] if isinstance(z[0], float)}
    anleihe = {int(z[0]): zahl(z[3]) for z in zeilen[5:] if isinstance(z[0], float)}
    zitat_zins = next(str(z[2]).strip() for z in blatt(wb, "Front") if str(z[2]).startswith("Daniel Waldenstr") and "(2014)" in str(z[2]))
    return {
        "quelle": "Sveriges Riksbank, Historical Monetary Statistics of Sweden",
        "adresse": "https://www.riksbank.se/en-gb/statistics/historical-monetary-statistics-of-sweden/",
        "zitat": [zitat_preise, zitat_zins],
        "preise": reihe(preise), "zins": reihe(zins), "anleihe": reihe(anleihe),
    }


def norwegen(ordner):
    b = blaetter(os.path.join(ordner, "cpi.xlsx"))
    z = b["p1_c3_table_14"]
    if not any("2015=100" in str(z[k].get(1, "")) for k in z):
        raise SystemExit("Norges Bank: Preistabelle unerwartet")
    preise = {}
    for k in sorted(z):
        jahr = str(z[k].get(1, "")).strip()
        if len(jahr) == 4 and jahr.isdigit():
            preise[int(jahr)] = zahl(z[k].get(2))
    zitat_preise = next(str(v).strip() for k in sorted(b["Front"]) for v in [b["Front"][k].get(1, "")] if str(v).startswith("Grytten, Ola H."))

    b = blaetter(os.path.join(ordner, "shortterm_ir.xlsx"))
    z = b["p2c7_table_7A1"]
    kopf = next(k for k in sorted(z) if z[k].get(1) == "Year")
    if z[kopf].get(2) != "Marginal liquidity rate" or z[kopf].get(5) != "Average bond yield":
        raise SystemExit("Norges Bank: Zinstabelle unerwartet")
    zins, anleihe = {}, {}
    for k in sorted(z):
        jahr = str(z[k].get(1, "")).strip()
        if k > kopf and len(jahr) == 4 and jahr.isdigit():
            zins[int(jahr)] = zahl(z[k].get(2))
            anleihe[int(jahr)] = zahl(z[k].get(5))
    zitat_zins = next(str(v).strip() for k in sorted(b["Front"]) for v in [b["Front"][k].get(1, "")] if str(v).startswith("Eitrheim,") and "(2007)" in str(v))
    return {
        "quelle": "Norges Bank, Historical Monetary Statistics for Norway",
        "adresse": "https://www.norges-bank.no/en/topics/Statistics/Historical-monetary-statistics/",
        "zitat": [zitat_preise, zitat_zins],
        "preise": reihe(preise), "zins": reihe(zins), "anleihe": reihe(anleihe),
    }


def hauptteil():
    if len(sys.argv) < 3 or sys.argv[1] not in ("holen", "bauen"):
        raise SystemExit("Aufruf: langreihen.py holen|bauen <Ordner> [--stand=JJJJ-MM-TT]")
    ordner = sys.argv[2]
    if sys.argv[1] == "holen":
        for unter, dateien in DATEIEN.items():
            os.makedirs(os.path.join(ordner, unter), exist_ok=True)
            for name, adresse in dateien.items():
                r = urllib.request.Request(adresse, headers={"User-Agent": KENNUNG})
                with urllib.request.urlopen(r, timeout=120) as a:
                    inhalt = a.read()
                with open(os.path.join(ordner, unter, name), "wb") as f:
                    f.write(inhalt)
                print("%s: %d Bytes" % (name, len(inhalt)))
        return
    stand = next((a[len("--stand="):] for a in sys.argv[3:] if a.startswith("--stand=")), None)
    if not stand:
        raise SystemExit("--stand=JJJJ-MM-TT angeben (Tag des Abrufs)")
    laender = {}
    if os.path.isdir(os.path.join(ordner, "norges")):
        laender["NOR"] = norwegen(os.path.join(ordner, "norges"))
    if os.path.isdir(os.path.join(ordner, "riksbank")):
        laender["SWE"] = schweden(os.path.join(ordner, "riksbank"))
    if not laender:
        raise SystemExit("Keine Tabellen im Ordner")
    datei = {
        "stand": stand, "schema": 1,
        "hinweis": "Werte der Quellen unveraendert in deren Basis und Einheit.",
        "l": laender,
    }
    text = json.dumps(datei, ensure_ascii=False, separators=(",", ":")) + chr(10)
    ziel = os.path.join(repo(), "app", "src", "main", "assets", "globus", "langreihen.json")
    with open(ziel, "w", encoding="utf-8", newline=chr(10)) as f:
        f.write(text)
    print("langreihen.json: %d Bytes, Laender %s" % (len(text.encode("utf-8")), ", ".join(sorted(laender))))
    for a3, l in sorted(laender.items()):
        for art in ("preise", "zins", "anleihe"):
            print(a3, art, l[art]["a"], l[art]["a"] + len(l[art]["w"]) - 1, sum(1 for x in l[art]["w"] if x is None), l[art]["w"][:2], l[art]["w"][-2:])
        print(l["zitat"])


if __name__ == "__main__":
    hauptteil()
