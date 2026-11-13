#!/usr/bin/env python3
# Baut zwei verdichtete Dateien fuer das Laenderblatt des Globus:
#
#   app/src/main/assets/globus/jst.json          Laenderrenditen und Krisen seit 1870 (18 Laender, jaehrlich)
#   app/src/main/assets/globus/hauspreise.json   Hauspreise und verfuegbare Einkommen real seit 1975 (vierteljaehrlich)
#
# Quellen und Bedingungen (am 12.11.2026 auf den Seiten der Quellen gelesen):
#   - Jorda-Schularick-Taylor Macrohistory Database, Release 6, https://www.macrohistory.net/database/
#     Lizenz CC BY-NC-SA 4.0: Nutzung und Weitergabe nur zu nicht kommerziellen Zwecken, mit Namensnennung und Zitat der
#     Autoren, Weitergabe nur unter gleichen Lizenzbedingungen. Die abgeleitete Datei traegt darum dieselbe Lizenz
#     (Feld "lizenz" in der Datei) und das vorgegebene Zitat (Feld "zitat").
#   - Federal Reserve Bank of Dallas, International House Price Database, https://www.dallasfed.org/research/international/houseprice
#     Bedingungen https://www.dallasfed.org/fed/disclaimer: Vervielfaeltigung ohne Mengengrenze, solange sie nicht zu
#     privatem Gewinn verbreitet wird und die Bank genannt ist. Die Seite der Datenbank erbittet ein Zitat des Arbeitspapiers
#     (Mack und Martinez-Garcia 2011) und das Datum des Abrufs.
#
# Aufruf:
#   python tools/daten/historie.py holen <Ordner>                      laedt beide Tabellen (Browserkennung, kein Konto)
#   python tools/daten/historie.py bauen <Ordner> --stand=JJJJ-MM-TT   liest JSTdatasetR6.xlsx und hpJJQQ.xlsx aus dem Ordner
# Danach: python tools/daten/manifest.py bauen   (nicht signieren; das macht der Auftraggeber)
#
# Regeln JST: reale Rendite = (1 + nominale Rendite) / (1 + Inflation) - 1, Inflation aus dem Verbraucherpreisindex (cpi) gegen
# das Vorjahr. Aktien eq_tr, Anleihen bond_tr, Immobilien housing_tr, kurzfristiger Zins stir (Prozent, real gerechnet).
# Kredit zu BIP = tloans / gdp. Krisenjahre = crisisJST gleich 1. Renditen in Hundertstel Prozent (ganze Zahlen), Luecken null.
# Deutschland 1922 bis 1924 bleibt leer (Hyperinflation: der Preisindex springt im Jahresdurchschnitt um den Faktor 10^12).
# Kanada und Irland haben in der Datenbank keine Renditespalten (nur Zins, Preise, Kredit und Krisen).

import json
import os
import re
import sys
import urllib.request
import zipfile
import xml.etree.ElementTree as ET

JST_URL = "https://www.macrohistory.net/app/download/9834512569/JSTdatasetR6.xlsx"
DALLAS_SEITE = "https://www.dallasfed.org/research/international/houseprice"
DALLAS_URL = "https://www.dallasfed.org/-/media/documents/research/international/houseprice/hp{quartal}.xlsx"
KENNUNG = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"


def u(s):
    # Sonderzeichen der Zitate: @O = O mit Gravis, @a = a mit Gravis, @i = i mit Akut, @( und @) = Anfuehrungszeichen, @- = Gedankenstrich
    for k, v in (("@O", 0xD2), ("@a", 0xE0), ("@i", 0xED), ("@(", 0x201C), ("@)", 0x201D), ("@-", 0x2013)):
        s = s.replace(k, chr(v))
    return s


JST_ZITAT = [
    u("@Oscar Jord@a, Moritz Schularick, and Alan M. Taylor. 2017. @(Macrofinancial History and the New Business Cycle Facts.@) "
      "NBER Macroeconomics Annual 2016, volume 31, edited by Martin Eichenbaum and Jonathan A. Parker. Chicago: University of Chicago Press."),
    u("@Oscar Jord@a, Katharina Knoll, Dmitry Kuvshinov, Moritz Schularick, and Alan M. Taylor. 2019. "
      "@(The Rate of Return on Everything, 1870@-2015.@) Quarterly Journal of Economics, 134(3), 1225-1298."),
]
JST_LIZENZ = u("CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/): nicht kommerzielle Nutzung, Namensnennung, "
               "Weitergabe nur unter gleichen Bedingungen. Abgeleitet aus der Jord@a-Schularick-Taylor Macrohistory Database, "
               "R6 (https://www.macrohistory.net/database/); die Zahlen sind umgerechnet, nicht die Originalwerte.")
DALLAS_ZITAT = [
    u("Mack, A., and E. Mart@inez-Garc@ia. 2011. @(A Cross-Country Quarterly Database of Real House Prices: A Methodological Note.@) "
      "Globalization and Monetary Policy Institute Working Paper No. 99, Federal Reserve Bank of Dallas."),
]
DALLAS_HINWEIS = u("The authors acknowledge use of the dataset described in Mack and Mart@inez-Garc@ia (2011). "
                   "Quelle: Federal Reserve Bank of Dallas, International House Price Database "
                   "(https://www.dallasfed.org/research/international/houseprice). Verbreitung nicht zu privatem Gewinn.")

AUSGELASSEN = {"DEU": (1922, 1924)}
BEGINN = 1871
ENDE = 2020

DALLAS_LAENDER = {
    "Australia": "AUS", "Belgium": "BEL", "Canada": "CAN", "Switzerland": "CHE", "Germany": "DEU", "Denmark": "DNK", "Spain": "ESP",
    "Finland": "FIN", "France": "FRA", "UK": "GBR", "Ireland": "IRL", "Italy": "ITA", "Japan": "JPN", "S. Korea": "KOR",
    "Luxembourg": "LUX", "Netherlands": "NLD", "Norway": "NOR", "New Zealand": "NZL", "Sweden": "SWE", "US": "USA",
    "S. Africa": "ZAF", "Croatia": "HRV", "Israel": "ISR", "Slovenia": "SVN", "Colombia": "COL", "Portugal": "PRT",
}

NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main", "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships"}


def repo():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def holen_datei(url, ziel):
    r = urllib.request.Request(url, headers={"User-Agent": KENNUNG})
    with urllib.request.urlopen(r, timeout=120) as a:
        b = a.read()
    with open(ziel, "wb") as f:
        f.write(b)
    print("%s: %d Bytes" % (os.path.basename(ziel), len(b)))


def blaetter(pfad):
    # Alle Tabellenblaetter einer xlsx als {Name: {Zeile: {Spalte: Wert}}}
    z = zipfile.ZipFile(pfad)
    ss = []
    if "xl/sharedStrings.xml" in z.namelist():
        for si in ET.fromstring(z.read("xl/sharedStrings.xml")).findall("m:si", NS):
            ss.append("".join(t.text or "" for t in si.iter("{%s}t" % NS["m"])))
    wb = ET.fromstring(z.read("xl/workbook.xml"))
    rels = {r.get("Id"): r.get("Target") for r in ET.fromstring(z.read("xl/_rels/workbook.xml.rels"))}
    aus = {}
    for s in wb.find("m:sheets", NS):
        t = rels[s.get("{%s}id" % NS["r"])].lstrip("/")
        t = t if t.startswith("xl/") else "xl/" + t
        zeilen = {}
        for c in ET.fromstring(z.read(t)).iter("{%s}c" % NS["m"]):
            m = re.match("([A-Z]+)([0-9]+)", c.get("r"))
            col = 0
            for ch in m.group(1):
                col = col * 26 + ord(ch) - 64
            v = c.find("m:v", NS)
            if v is None:
                continue
            val = v.text
            if c.get("t") == "s":
                val = ss[int(val)]
            elif c.get("t") in ("str", "e", "b"):
                pass
            else:
                try:
                    val = float(val)
                except ValueError:
                    pass
            zeilen.setdefault(int(m.group(2)), {})[col] = val
        aus[s.get("name")] = zeilen
    return aus


def hundertstel(x):
    return None if x is None else int(round(x * 10000))


def jst_bauen(pfad, stand):
    tab = list(blaetter(pfad).values())[0]
    kopf = {v: k for k, v in tab[1].items()}
    je = {}
    for n in sorted(tab):
        if n == 1:
            continue
        z = tab[n]
        je.setdefault(z[kopf["iso"]], {})[int(z[kopf["year"]])] = z

    def zahl(z, spalte):
        v = z.get(kopf[spalte]) if z else None
        return v if isinstance(v, float) else None

    laender = {}
    for iso, jahre in sorted(je.items()):
        aus = AUSGELASSEN.get(iso)
        reihen = {k: [] for k in ("akt", "anl", "imm", "zins", "infl", "kredit")}
        krisen = sorted(j for j, z in jahre.items() if zahl(z, "crisisJST") == 1.0)
        for j in range(BEGINN, ENDE + 1):
            z, v = jahre.get(j), jahre.get(j - 1)
            leer = bool(aus and aus[0] <= j <= aus[1])
            cpi, cpi0 = zahl(z, "cpi"), zahl(v, "cpi")
            infl = None
            if cpi is not None and cpi0 is not None and cpi0 > 0 and not leer:
                infl = cpi / cpi0 - 1.0

            def real(nominal):
                if nominal is None or infl is None:
                    return None
                return (1.0 + nominal) / (1.0 + infl) - 1.0

            zins = zahl(z, "stir")
            reihen["akt"].append(hundertstel(real(zahl(z, "eq_tr"))))
            reihen["anl"].append(hundertstel(real(zahl(z, "bond_tr"))))
            reihen["imm"].append(hundertstel(real(zahl(z, "housing_tr"))))
            reihen["zins"].append(hundertstel(real(None if zins is None else zins / 100.0)))
            reihen["infl"].append(hundertstel(infl))
            kr, bip = zahl(z, "tloans"), zahl(z, "gdp")
            reihen["kredit"].append(None if leer or kr is None or bip is None or bip <= 0 else round(kr / bip * 100.0, 1))
        o = {"a": BEGINN}
        for k, r in reihen.items():
            if any(x is not None for x in r):
                o[k] = r
        o["krise"] = krisen
        if aus:
            o["leer"] = [aus[0], aus[1]]
        laender[iso] = o
    return {
        "stand": stand, "schema": 1,
        "quelle": u("Jord@a-Schularick-Taylor Macrohistory Database, R6 (https://www.macrohistory.net/database/), abgerufen am ") + stand,
        "lizenz": JST_LIZENZ, "zitat": JST_ZITAT, "ende": ENDE, "l": laender,
    }


def dallas_bauen(pfad, stand):
    b = blaetter(pfad)

    def tafel(name):
        zeilen = b[name]
        namen = {c: v for c, v in zeilen[1].items() if c != 1}
        quartale, werte = [], {}
        for n in sorted(zeilen):
            q = zeilen[n].get(1)
            if n < 3 or not isinstance(q, str) or ":Q" not in q:
                continue
            quartale.append(q.replace(":Q", "Q"))
            for c, land in namen.items():
                v = zeilen[n].get(c)
                werte.setdefault(land, []).append(v if isinstance(v, float) else None)
        return quartale, werte

    qh, h = tafel("RHPI")
    qe, e = tafel("RPDI")
    if qh != qe:
        raise SystemExit("Quartale von RHPI und RPDI weichen ab")
    laender = {}
    for name, a3 in DALLAS_LAENDER.items():
        ph, pe = h[name], e[name]
        if any(x is None for x in ph) or any(x is None for x in pe):
            raise SystemExit("Luecke in " + name)
        laender[a3] = {"p": [int(round(x * 100)) for x in ph], "e": [int(round(x * 100)) for x in pe]}
    return {
        "stand": stand, "schema": 1, "ab": qh[0], "bis": qh[-1],
        "quelle": "Federal Reserve Bank of Dallas, International House Price Database (" + DALLAS_SEITE + "), abgerufen am " + stand,
        "hinweis": DALLAS_HINWEIS, "zitat": DALLAS_ZITAT, "einheit": "Index (2005 = 100) in Hundertsteln", "l": laender,
    }


def schreiben(datei, name):
    ziel = os.path.join(repo(), "app", "src", "main", "assets", "globus", name)
    text = json.dumps(datei, ensure_ascii=False, separators=(",", ":")) + chr(10)
    with open(ziel, "w", encoding="utf-8", newline=chr(10)) as f:
        f.write(text)
    print("%s: %d Bytes" % (name, len(text.encode("utf-8"))))


def hauptteil():
    if len(sys.argv) < 3 or sys.argv[1] not in ("holen", "bauen"):
        raise SystemExit(__doc__)
    ordner = sys.argv[2]
    stand = next((a[len("--stand="):] for a in sys.argv[3:] if a.startswith("--stand=")), None)
    if sys.argv[1] == "holen":
        os.makedirs(ordner, exist_ok=True)
        holen_datei(JST_URL, os.path.join(ordner, "JSTdatasetR6.xlsx"))
        # Neueste Ausgabe: Dateiname je Quartal (hpJJQQ), von hinten probiert
        for jahr in (2027, 2026, 2025):
            for q in (4, 3, 2, 1):
                n = "%02d%02d" % (jahr % 100, q)
                try:
                    holen_datei(DALLAS_URL.format(quartal=n), os.path.join(ordner, "hp%s.xlsx" % n))
                    return
                except Exception:
                    pass
        raise SystemExit("Keine Dallas-Datei gefunden")
    if not stand:
        raise SystemExit("--stand=JJJJ-MM-TT angeben (Tag des Abrufs)")
    hp = sorted(f for f in os.listdir(ordner) if f.startswith("hp") and f.endswith(".xlsx"))[-1]
    schreiben(jst_bauen(os.path.join(ordner, "JSTdatasetR6.xlsx"), stand), "jst.json")
    schreiben(dallas_bauen(os.path.join(ordner, hp), stand), "hauspreise.json")


if __name__ == "__main__":
    hauptteil()
