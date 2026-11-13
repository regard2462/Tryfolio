#!/usr/bin/env python3
# Baut die Monatsreihen fuer die lange Sicht:
#
#   app/src/main/assets/indikatoren/lange_reihen.json   Verbraucherpreise, Zinsen und Goldpreis der USA, monatlich
#
# Quellen und Bedingungen (am 12.11.2026 auf den Seiten der Quellen gelesen):
#   - U.S. Bureau of Labor Statistics, Verbraucherpreisindex CPI-U (CUUR0000SA0, alle Posten, Schnitt der US-Staedte, nicht
#     saisonbereinigt, 1982-84 = 100), ab Januar 1913. https://www.bls.gov/bls/linksite.htm: Veroeffentlichungen der Behoerde
#     sind gemeinfrei, Nutzung ohne besondere Erlaubnis, die Behoerde bittet um Nennung als Quelle.
#   - Board of Governors of the Federal Reserve System, H.15 Selected Interest Rates: Schatzwechsel mit drei Monaten Laufzeit
#     am Sekundaermarkt (RIFSGFSM03_N.M, Diskontsatz, ab Januar 1934) und Renditen von Staatsanleihen konstanter Laufzeit
#     mit 5 und 10 Jahren (RIFLGFCY05_N.M, RIFLGFCY10_N.M, ab April 1953), Monatsmittel in Prozent.
#     https://www.federalreserve.gov/disclaimer.htm: gemeinfrei, darf ohne Erlaubnis kopiert und verbreitet werden, der Board
#     bittet um Nennung als Quelle.
#   - World Bank, Commodity Price Data (The Pink Sheet): Gold in US-Dollar je Feinunze, Monatsmittel ab Januar 1960.
#     Lizenz CC BY 4.0 (https://datacatalog.worldbank.org/search/dataset/0037712/).
#     Dieselbe Tabelle (Blatt Monthly Indices): Gesamtindex der Rohstoffpreise (Total Index, 2010 = 100, nominal, Spotpreise).
#   - Board of Governors of the Federal Reserve System, H.10 Foreign Exchange Rates: Monatsmittel der Wechselkurse (Datei
#     H10.zip, Reihen RXI_N.M.JA Yen, SZ Franken, CA kanadischer Dollar je US-Dollar, RXI$US_N.M.UK Dollar je Pfund, EU Dollar
#     je Euro) ab Januar 1971, Euro ab Januar 1999. Fuer die D-Mark (Mark je US-Dollar, Januar 1971 bis Dezember 1998) stehen
#     auf den Seiten des H.10 die Tageskurse (dat89_ge.htm bis 1989, dat96_ge.htm 1990 bis 1998); daraus wird das Monatsmittel
#     gebildet (Tage ohne Kurs, "ND", zaehlen nicht). Nutzung wie bei H.15: gemeinfrei, Nennung als Quelle.
# Die Werte stehen unveraendert in der Datei (Zahl wie in der Quelle); Renditen und Verlaeufe rechnet die App
# (engine/LangeReihen.kt). Fehlt ein Monat in der Quelle, steht null.
#
# Aufruf:
#   python tools/daten/lange_reihen.py holen <Ordner>                      laedt die drei Dateien (Browserkennung, kein Konto)
#   python tools/daten/lange_reihen.py bauen <Ordner> --stand=JJJJ-MM-TT   liest H15.zip, cpi.html und die Preistabelle
#   python tools/daten/lange_reihen.py zeigen <Ordner> JJJJ-MM             Werte eines Monats aus den Quelldateien (Stichprobe)
#   python tools/daten/lange_reihen.py waehrung <Ordner>                   holt H10.zip und die D-Mark-Seiten, haengt die Wechselkurse
#                                                                          an die vorhandene lange_reihen.json (Stand bleibt)

import io
import json
import os
import re
import sys
import urllib.request
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from historie import KENNUNG, blaetter, repo  # noqa: E402

H15_URL = "https://www.federalreserve.gov/datadownload/Output.aspx?rel=H15&filetype=zip"
CPI_URL = "https://data.bls.gov/timeseries/CUUR0000SA0?years_option=all_years"
H10_URL = "https://www.federalreserve.gov/datadownload/Output.aspx?rel=H10&filetype=zip"
DM_SEITEN = ("https://www.federalreserve.gov/releases/h10/hist/dat89_ge.htm", "https://www.federalreserve.gov/releases/h10/hist/dat96_ge.htm")
DM_DATEIEN = ("dm_bis1989.html", "dm_1990_1998.html")
WB_SEITE = "https://www.worldbank.org/en/research/commodity-markets"
WB_DATEI = "CMO-Historical-Data-Monthly.xlsx"

H15_REIHEN = {"tb3": "RIFSGFSM03_N.M", "y5": "RIFLGFCY05_N.M", "y10": "RIFLGFCY10_N.M"}

H10_REIHEN = {"jpy": "RXI_N.M.JA", "chf": "RXI_N.M.SZ", "cad": "RXI_N.M.CA", "gbp": "RXI$US_N.M.UK", "eur": "RXI$US_N.M.EU"}

QUELLE_KURSE = {
    "name": "Board of Governors of the Federal Reserve System", "url": "https://www.federalreserve.gov/releases/h10/",
    "reihe": "H.10 Foreign Exchange Rates: monthly averages of noon buying rates in New York, yen, Swiss francs, Canadian dollars "
             "and D-marks per US dollar, US dollars per pound and per euro; D-mark from daily rates of the H.10 historical data",
    "lizenz": "public domain",
}

QUELLEN = {
    "cpi": {
        "name": "U.S. Bureau of Labor Statistics", "url": "https://www.bls.gov/cpi/",
        "reihe": "CPI-U, All items in U.S. city average, not seasonally adjusted (CUUR0000SA0), 1982-84 = 100",
        "lizenz": "public domain",
    },
    "zins": {
        "name": "Board of Governors of the Federal Reserve System", "url": "https://www.federalreserve.gov/releases/h15/",
        "reihe": "H.15 Selected Interest Rates: 3-month Treasury bill secondary market rate (discount basis), "
                 "5-year and 10-year Treasury constant maturity yields, monthly averages, percent",
        "lizenz": "public domain",
    },
    "rohstoffe": {
        "name": "World Bank", "url": "https://www.worldbank.org/en/research/commodity-markets",
        "reihe": "Commodity Price Data (The Pink Sheet): Total Index of commodity prices, monthly, nominal US dollars, 2010 = 100",
        "lizenz": "CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/)",
    },
    "gold": {
        "name": "World Bank", "url": "https://www.worldbank.org/en/research/commodity-markets",
        "reihe": "Commodity Price Data (The Pink Sheet): gold, US dollars per troy ounce, monthly average",
        "lizenz": "CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/)",
    },
}


def holen_bytes(url):
    r = urllib.request.Request(url, headers={"User-Agent": KENNUNG, "Accept": "text/html,*/*"})
    with urllib.request.urlopen(r, timeout=300) as a:
        return a.read()


def holen(ordner):
    os.makedirs(ordner, exist_ok=True)
    for name, url in (("H15.zip", H15_URL), ("cpi.html", CPI_URL)):
        b = holen_bytes(url)
        with open(os.path.join(ordner, name), "wb") as f:
            f.write(b)
        print("%s: %d Bytes" % (name, len(b)))
    seite = holen_bytes(WB_SEITE).decode("utf-8", "replace")
    link = re.search(r'https://thedocs\.worldbank\.org/[^"\']+/' + re.escape(WB_DATEI), seite)
    if not link:
        raise SystemExit("Preistabelle der Weltbank nicht gefunden")
    b = holen_bytes(link.group(0))
    with open(os.path.join(ordner, WB_DATEI), "wb") as f:
        f.write(b)
    print("%s: %d Bytes" % (WB_DATEI, len(b)))


def holen_fx(ordner):
    os.makedirs(ordner, exist_ok=True)
    for name, url in (("H10.zip", H10_URL),) + tuple(zip(DM_DATEIEN, DM_SEITEN)):
        b = holen_bytes(url)
        with open(os.path.join(ordner, name), "wb") as f:
            f.write(b)
        print("%s: %d Bytes" % (name, len(b)))


def zahl(text):
    # Zahl wie in der Quelle: ganze Zahlen ohne Komma, sonst mit den Stellen der Quelle
    x = float(text)
    return int(x) if x == int(x) else x


def h15_lesen(pfad):
    # {Kuerzel: {(Jahr, Monat): Wert}}; Werte ohne Beobachtung (Status ungleich A) fehlen
    gesucht = {v: k for k, v in H15_REIHEN.items()}
    aus = {k: {} for k in H15_REIHEN}
    with zipfile.ZipFile(pfad) as z:
        aktuell = None
        for roh in io.TextIOWrapper(z.open("H15_data.xml"), encoding="utf-8"):
            m = re.search(r'SERIES_NAME="([^"]+)"', roh)
            if m:
                aktuell = gesucht.get(m.group(1))
                continue
            if aktuell is None or "<frb:Obs" not in roh:
                continue
            status = re.search(r'OBS_STATUS="([^"]+)"', roh).group(1)
            wert = re.search(r'OBS_VALUE="([^"]+)"', roh).group(1)
            tag = re.search(r'TIME_PERIOD="(\d{4})-(\d{2})-\d{2}"', roh)
            if status == "A":
                aus[aktuell][(int(tag.group(1)), int(tag.group(2)))] = zahl(wert)
    return aus


def h10_lesen(pfad):
    # {Kuerzel: {(Jahr, Monat): Wert}}, Monatsmittel; Werte ohne Beobachtung (Status ungleich A) fehlen
    gesucht = {v: k for k, v in H10_REIHEN.items()}
    aus = {k: {} for k in H10_REIHEN}
    with zipfile.ZipFile(pfad) as z:
        aktuell = None
        for roh in io.TextIOWrapper(z.open("h10_data.xml"), encoding="utf-8"):
            m = re.search(r'SERIES_NAME="([^"]+)"', roh)
            if m:
                aktuell = gesucht.get(m.group(1))
                continue
            if aktuell is None or "<frb:Obs" not in roh:
                continue
            status = re.search(r'OBS_STATUS="([^"]+)"', roh).group(1)
            wert = re.search(r'OBS_VALUE="([^"]+)"', roh).group(1)
            tag = re.search(r'TIME_PERIOD="(\d{4})-(\d{2})-\d{2}"', roh)
            if status == "A":
                aus[aktuell][(int(tag.group(1)), int(tag.group(2)))] = zahl(wert)
    return aus


MONATE_EN = {n: i + 1 for i, n in enumerate(("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"))}


def dm_lesen(ordner):
    # Mark je US-Dollar: Monatsmittel der Tageskurse 1971 bis 1998 aus den beiden Seiten des H.10
    tage = {}
    for name in DM_DATEIEN:
        with open(os.path.join(ordner, name), encoding="utf-8", errors="replace") as f:
            t = f.read()
        if "DM/US$" not in t:
            raise SystemExit("%s ist nicht die Reihe DM/US$" % name)
        t = re.sub(r"<script.*?</script>", "", t, flags=re.S)
        t = re.sub(r"<[^>]+>", " ", t).replace("&nbsp;", " ")
        t = re.sub(r"\s+", " ", t)
        for d, m, y, v in re.findall(r"(\d{1,2}) ?- ?([A-Z][a-z]{2}) ?- ?(\d{2}) +([0-9]+\.[0-9]+|ND)", t):
            if v == "ND":
                continue
            j = 1900 + int(y)
            tage.setdefault((j, MONATE_EN[m]), {})[int(d)] = float(v)
    aus = {}
    for k, w in tage.items():
        if k[0] <= 1998:
            aus[k] = round(sum(w.values()) / len(w), 4)
    return aus


def fx_lesen(ordner):
    aus = h10_lesen(os.path.join(ordner, "H10.zip"))
    aus["dem"] = dm_lesen(ordner)
    return aus


def waehrung(ordner):
    # Haengt die Wechselkursreihen an die vorhandene Datei; Stand und die uebrigen Reihen bleiben
    ziel = os.path.join(repo(), "app", "src", "main", "assets", "indikatoren", "lange_reihen.json")
    with open(ziel, encoding="utf-8") as f:
        datei = json.load(f)
    fx = fx_lesen(ordner)
    for k in ("dem", "eur", "gbp", "jpy", "chf", "cad"):
        datei["r"]["fx_" + k] = reihe(fx[k])
    datei["quellen"]["kurse"] = QUELLE_KURSE
    text = json.dumps(datei, ensure_ascii=False, separators=(",", ":")) + chr(10)
    with open(ziel, "w", encoding="utf-8", newline=chr(10)) as f:
        f.write(text)
    for k, r in datei["r"].items():
        if k.startswith("fx_"):
            print("%s: ab %s, %d Monate, %d Luecken" % (k, r["ab"], len(r["w"]), sum(1 for x in r["w"] if x is None)))
    print("lange_reihen.json: %d Bytes" % len(text.encode("utf-8")))


def cpi_lesen(pfad):
    with open(pfad, encoding="utf-8", errors="replace") as f:
        t = f.read()
    if "CUUR0000SA0" not in t or "1982-84=100" not in t:
        raise SystemExit("cpi.html ist nicht die Reihe CUUR0000SA0")
    aus = {}
    for jahr, rest in re.findall(r'<TH scope="row">(\d{4})</TH>(.*?)</TR>', t, flags=re.S):
        zellen = re.findall(r"<TD>(.*?)</TD>", rest, flags=re.S)
        for i, z in enumerate(zellen[:12]):
            z = re.sub(r"<[^>]*>", "", z).replace("&nbsp;", "").strip()
            if re.fullmatch(r"\d+(\.\d+)?", z):
                aus[(int(jahr), i + 1)] = zahl(z)
    return aus


def gold_lesen(pfad):
    t = blaetter(pfad)["Monthly Prices"]
    kopf = next(n for n in sorted(t) if any(isinstance(v, str) and v.strip() == "Gold" for v in t[n].values()))
    spalte = next(c for c, v in t[kopf].items() if isinstance(v, str) and v.strip() == "Gold")
    if "troy oz" not in str(t[kopf + 1].get(spalte)):
        raise SystemExit("Goldspalte ohne Einheit $/troy oz")
    aus = {}
    for n in sorted(t):
        m = re.fullmatch(r"(\d{4})M(\d{2})", str(t[n].get(1)))
        v = t[n].get(spalte)
        if m and isinstance(v, float):
            aus[(int(m.group(1)), int(m.group(2)))] = zahl(repr(v))
    return aus


def rohstoffe_lesen(pfad):
    t = blaetter(pfad)["Monthly Indices"]
    kopf = next(n for n in sorted(t) if any(isinstance(v, str) and v.strip() == "Total Index" for v in t[n].values()))
    spalte = next(c for c, v in t[kopf].items() if isinstance(v, str) and v.strip() == "Total Index")
    aus = {}
    for n in sorted(t):
        m = re.fullmatch(r"(\d{4})M(\d{2})", str(t[n].get(1)))
        v = t[n].get(spalte)
        if m and isinstance(v, float):
            aus[(int(m.group(1)), int(m.group(2)))] = zahl(repr(v))
    return aus


def reihe(werte):
    # Vom ersten bis zum letzten Monat mit Wert, Luecken als null
    monate = sorted(j * 12 + m - 1 for j, m in werte)
    a, b = monate[0], monate[-1]
    return {"ab": "%04d-%02d" % (a // 12, a % 12 + 1), "w": [werte.get((k // 12, k % 12 + 1)) for k in range(a, b + 1)]}


def lesen(ordner):
    aus = h15_lesen(os.path.join(ordner, "H15.zip"))
    aus["cpi"] = cpi_lesen(os.path.join(ordner, "cpi.html"))
    aus["gold"] = gold_lesen(os.path.join(ordner, WB_DATEI))
    aus["comm"] = rohstoffe_lesen(os.path.join(ordner, WB_DATEI))
    if os.path.exists(os.path.join(ordner, "H10.zip")):
        aus.update({"fx_" + k: v for k, v in fx_lesen(ordner).items()})
    return aus


def bauen(ordner, stand):
    alle = lesen(ordner)
    schluessel = ["cpi", "tb3", "y5", "y10", "gold", "comm"] + sorted(k for k in alle if k.startswith("fx_"))
    datei = {
        "stand": stand, "schema": 1,
        "quellen": dict(QUELLEN, **({"kurse": QUELLE_KURSE} if len(schluessel) > 6 else {})),
        "r": {k: reihe(alle[k]) for k in schluessel},
    }
    ziel = os.path.join(repo(), "app", "src", "main", "assets", "indikatoren", "lange_reihen.json")
    text = json.dumps(datei, ensure_ascii=False, separators=(",", ":")) + chr(10)
    with open(ziel, "w", encoding="utf-8", newline=chr(10)) as f:
        f.write(text)
    for k, r in datei["r"].items():
        print("%s: ab %s, %d Monate, %d Luecken" % (k, r["ab"], len(r["w"]), sum(1 for x in r["w"] if x is None)))
    print("lange_reihen.json: %d Bytes" % len(text.encode("utf-8")))


def hauptteil():
    if len(sys.argv) < 3 or sys.argv[1] not in ("holen", "bauen", "zeigen", "waehrung"):
        raise SystemExit("holen <Ordner> | bauen <Ordner> --stand=JJJJ-MM-TT | zeigen <Ordner> JJJJ-MM | waehrung <Ordner>")
    ordner = sys.argv[2]
    if sys.argv[1] == "holen":
        holen(ordner)
        holen_fx(ordner)
    elif sys.argv[1] == "waehrung":
        if not os.path.exists(os.path.join(ordner, "H10.zip")):
            holen_fx(ordner)
        waehrung(ordner)
    elif sys.argv[1] == "zeigen":
        j, m = sys.argv[3].split("-")
        print({k: v.get((int(j), int(m))) for k, v in lesen(ordner).items()})
    else:
        stand = next((a[len("--stand="):] for a in sys.argv[3:] if a.startswith("--stand=")), None)
        if not stand:
            raise SystemExit("--stand=JJJJ-MM-TT angeben (Tag des Abrufs)")
        bauen(ordner, stand)


if __name__ == "__main__":
    hauptteil()
