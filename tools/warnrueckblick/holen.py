#!/usr/bin/env python3
"""Holt die freien Rohreihen fuer den Rueckblick "Haette man es sehen koennen?" und legt sie als einfache
Zeilendateien "JJJJ-MM-TT,Wert" in einen Ordner. Aufruf: python holen.py <Ordner>
Quellen (alle ohne Anmeldung): Yahoo (Kurse), US-Finanzministerium (Renditen), BLS (Arbeitslosenquote), Chicago Fed (NFCI),
Shiller (CAPE, Gewinne), OECD ueber DBnomics (Fruehindikator), EIA (WTI), DOL (Erstantraege, saisonbereinigt),
Federal Reserve Board (Kreditaufschlag nach Gilchrist und Zakrajsek, Finanzierungsrechnung Z.1), BEA (BIP).
FRED antwortet von hier nicht und wird nicht umgangen; die Reihen, die die App ueber FRED laedt, kommen hier von ihren
Herausgebern."""
import datetime
import io
import json
import os
import sys
import time
import urllib.parse
import urllib.request
import zipfile

import xlrd

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
ZIEL = sys.argv[1]
os.makedirs(ZIEL, exist_ok=True)
EPOCHE = datetime.datetime(1970, 1, 1)


def holen(url, daten=None, kopf=None, pause=0.7):
    k = {"User-Agent": UA}
    k.update(kopf or {})
    req = urllib.request.Request(url, data=daten, headers=k)
    for versuch in range(3):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                b = r.read()
            time.sleep(pause)
            return b
        except Exception as e:  # noqa: BLE001
            print("Fehler", url[:90], e, file=sys.stderr)
            time.sleep(3)
    return None


def schreiben(name, zeilen):
    zeilen = sorted(set(zeilen))
    with open(os.path.join(ZIEL, name + ".csv"), "w", encoding="utf-8") as f:
        f.write("\n".join(f"{d},{w}" for d, w in zeilen) + "\n")
    print(name, len(zeilen), zeilen[0][0] if zeilen else "-", zeilen[-1][0] if zeilen else "-")


def yahoo(symbol, name):
    s = urllib.request.quote(symbol, safe="")
    url = f"https://query1.finance.yahoo.com/v8/finance/chart/{s}?period1=-1325635200&period2={int(time.time()) + 86400}&interval=1d"
    b = holen(url)
    if not b:
        return
    r = json.loads(b)["chart"]["result"][0]
    ts = r["timestamp"]
    cl = r["indicators"]["quote"][0]["close"]
    z = []
    for t, c in zip(ts, cl):
        if c is None or c <= 0:
            continue
        d = (EPOCHE + datetime.timedelta(seconds=t)).date().isoformat()
        z.append((d, round(c, 4)))
    schreiben("y_" + name, z)


for sym, name in [("^GSPC", "gspc"), ("^GDAXI", "gdaxi"), ("^990100-USD-STRD", "msciworld"), ("^VIX", "vix"), ("^VIX3M", "vix3m"),
                  ("^TNX", "tnx"), ("^IRX", "irx"), ("IEF", "ief"), ("CL=F", "clf"), ("SPY", "spy"), ("RSP", "rsp"), ("IPO", "ipo"), ("^W5000", "w5000")]:
    yahoo(sym, name)

# US-Finanzministerium: Tagesrenditen je Jahr ab 1990, 3 Monate, 2 Jahre, 10 Jahre
m3, j2, j10 = [], [], []
for jahr in range(1990, datetime.date.today().year + 1):
    url = ("https://home.treasury.gov/resource-center/data-chart-center/interest-rates/daily-treasury-rates.csv/"
           f"{jahr}/all?type=daily_treasury_yield_curve&field_tdr_date_value={jahr}&page&_format=csv")
    b = holen(url)
    if not b:
        continue
    zeilen = b.decode("utf-8-sig").splitlines()
    kopf = [x.strip().strip('"').lower() for x in zeilen[0].split(",")]
    for z in zeilen[1:]:
        f = [x.strip().strip('"') for x in z.split(",")]
        try:
            mm, tt, jj = f[0].split("/")
            d = f"{jj}-{mm}-{tt}"
            for lst, spalte in ((m3, "3 mo"), (j2, "2 yr"), (j10, "10 yr")):
                v = f[kopf.index(spalte)]
                if v:
                    lst.append((d, float(v)))
        except (ValueError, IndexError):
            pass
schreiben("t_3m", m3)
schreiben("t_2j", j2)
schreiben("t_10j", j10)

# BLS: Arbeitslosenquote (saisonbereinigt), zehn Jahre je Abruf
z = []
for start in range(1948, datetime.date.today().year + 1, 10):
    body = json.dumps({"seriesid": ["LNS14000000"], "startyear": str(start), "endyear": str(start + 9)}).encode()
    b = holen("https://api.bls.gov/publicAPI/v1/timeseries/data/", daten=body, kopf={"Content-type": "application/json"})
    if not b:
        continue
    antwort = json.loads(b)
    if not antwort.get("Results", {}).get("series"):
        print("BLS ohne Daten:", antwort.get("message"), file=sys.stderr)
        z = None
        break
    for e in antwort["Results"]["series"][0]["data"]:
        if e["period"].startswith("M") and e["period"] != "M13" and e["value"] not in ("-", ""):
            z.append((f"{e['year']}-{e['period'][1:]}-01", float(e["value"])))
if z:
    schreiben("bls_unrate", z)

# Chicago Fed: NFCI (woechentlich, Freitag); seit 2026 ueber api.data.chicagofed.org, die alte Datei unter /-/media endet im April 2026
b = holen("https://api.data.chicagofed.org/NFCI/nfci-data-series-csv.csv")
if b:
    z = []
    for zeile in b.decode("utf-8-sig").splitlines()[1:]:
        f = zeile.split(",")
        try:
            mm, tt, jj = f[0].split("/")
            z.append((f"{jj}-{mm}-{tt}", float(f[1])))
        except ValueError:
            pass
    schreiben("nfci", z)

# Shiller: Kurs, Gewinn, CAPE, Zins (Monatswerte); erst die Seite, dann die verlinkte Datei
seite = (holen("https://shillerdata.com/", kopf={"Accept": "text/html"}) or b"").decode("utf-8", "replace")
import re  # noqa: E402
link = re.search(r'href="((?:https?:)?//[^"]+/ie_data\.xls[^"]*)"', seite)
if link:
    u = link.group(1).replace("&amp;", "&")
    b = holen("https:" + u if u.startswith("//") else u)
    if b:
        s = xlrd.open_workbook(file_contents=b).sheet_by_name("Data")
        cape, gewinn, kurs, dividende, zins = [], [], [], [], []
        for r in range(s.nrows):
            d = s.cell_value(r, 0)
            if not isinstance(d, float) or not 1871 <= d < 2200:
                continue
            j = int(d)
            m = round((d - j) * 100)
            if not 1 <= m <= 12:
                continue
            tag = f"{j}-{m:02d}-01"

            def zahl(c):
                v = s.cell_value(r, c)
                return float(v) if isinstance(v, float) else None
            if zahl(12) is not None:
                cape.append((tag, round(zahl(12), 3)))
            if zahl(3) is not None:
                gewinn.append((tag, round(zahl(3), 4)))
            if zahl(1) is not None:
                kurs.append((tag, round(zahl(1), 4)))
            if zahl(2) is not None:
                dividende.append((tag, round(zahl(2), 4)))
            if zahl(6) is not None:
                zins.append((tag, round(zahl(6), 4)))
        schreiben("shiller_cape", cape)
        schreiben("shiller_gewinn", gewinn)
        schreiben("shiller_kurs", kurs)
        schreiben("shiller_dividende", dividende)
        schreiben("shiller_zins", zins)

# OECD Fruehindikator (CLI, amplitudenbereinigt) ueber DBnomics
for land in ("USA", "DEU"):
    b = holen(f"https://api.db.nomics.world/v22/series/OECD/DSD_STES@DF_CLI/{land}.M.LI.IX._Z.AA.IX._Z.H?observations=1&metadata=0")
    if b:
        d = json.loads(b)["series"]["docs"][0]
        schreiben("oecd_cli_" + land.lower(), [(p + "-01", v) for p, v in zip(d["period"], d["value"]) if isinstance(v, (int, float))])

# EIA: WTI Spot, Tageswerte
b = holen("https://www.eia.gov/dnav/pet/hist_xls/RWTCd.xls")
if b:
    s = xlrd.open_workbook(file_contents=b).sheet_by_index(1)
    z = []
    for r in range(3, s.nrows):
        d, w = s.cell_value(r, 0), s.cell_value(r, 1)
        if isinstance(d, float) and isinstance(w, float):
            z.append((xlrd.xldate.xldate_as_datetime(d, 0).date().isoformat(), w))
    schreiben("eia_wti", z)

# DOL: Erstantraege je Bundesstaat (nicht saisonbereinigt), je Woche aufsummiert
b = holen("https://oui.doleta.gov/unemploy/csv/ar539.csv", pause=0)
if b:
    summen = {}
    for zeile in b.decode("utf-8", "replace").splitlines()[1:]:
        f = zeile.replace('"', "").split(",")
        try:
            summen[f[3]] = summen.get(f[3], 0) + int(f[4])  # c2 Woche, c3 Erstantraege
        except (ValueError, IndexError):
            pass
    schreiben("dol_erstantraege_nsa", [(k, v) for k, v in summen.items()])


def quartalsende(text):
    """'1999:Q4' oder '1999Q4' als letzter Tag des Quartals"""
    j, q = text.replace(":", "").split("Q")
    return {"1": f"{j}-03-31", "2": f"{j}-06-30", "3": f"{j}-09-30", "4": f"{j}-12-31"}[q]


# DOL: Erstantraege saisonbereinigt (Woche bis Samstag), wie die Reihe ICSA
b = holen("https://oui.doleta.gov/unemploy/wkclaims/report.asp", pause=0, daten=urllib.parse.urlencode(
    {"level": "US", "final_yr": str(datetime.date.today().year), "strtdate": "1967", "enddate": str(datetime.date.today().year),
     "filetype": "xml"}).encode())
if b:
    import xml.etree.ElementTree as ET
    z = []
    for w in ET.fromstring(b).iter("week"):
        try:
            mm, tt, jj = w.findtext("weekEnded").split("/")
            v = w.find("InitialClaims").findtext("SA").replace(",", "").strip()
            if v:
                z.append((f"{jj}-{mm}-{tt}", int(v)))
        except (AttributeError, ValueError):
            pass
    schreiben("dol_erstantraege_sa", z)

# Federal Reserve Board: Kreditaufschlag nach Gilchrist und Zakrajsek (Monatsmittel, Prozentpunkte)
b = holen("https://www.federalreserve.gov/econres/notes/feds-notes/ebp_csv.csv")
if b:
    z = []
    zeilen = b.decode("utf-8-sig").splitlines()
    kopf = zeilen[0].split(",")
    for zeile in zeilen[1:]:
        f = zeile.split(",")
        try:
            z.append((f[0], round(float(f[kopf.index("gz_spread")]), 4)))
        except (ValueError, IndexError):
            pass
    schreiben("fed_gz_spread", z)

# Federal Reserve Board: Finanzierungsrechnung Z.1 (Millionen Dollar, Quartalsende). Margin-Kredite der Broker
# (FL663067005, Nachfolger der FRED-Reihe BOGZ1FL663067003Q) und Boersenwert der Aktien nicht-finanzieller Unternehmen
# (LM103164115, oeffentlich gehandelt)
b = holen("https://www.federalreserve.gov/releases/z1/current/z1_csv_files.zip", pause=0)
if b:
    import csv
    zf = zipfile.ZipFile(io.BytesIO(b))
    for datei, code, name in (("csv/F4_6_s.csv", "FL663067005.Q", "z1_margin"), ("csv/F51_1_s.csv", "LM103164115.Q", "z1_aktien")):
        try:
            r = list(csv.reader(io.StringIO(zf.read(datei).decode("latin1"))))
            i = r[0].index(code)
        except (KeyError, ValueError):
            print("Z.1 ohne", code, file=sys.stderr)
            continue
        z = []
        for zeile in r[1:]:
            try:
                z.append((quartalsende(zeile[0]), float(zeile[i])))
            except (ValueError, IndexError, KeyError):
                pass
        schreiben(name, z)

# BEA: BIP in jeweiligen Preisen, aufs Jahr gerechnet (A191RC, Millionen Dollar, Quartalsende)
b = holen("https://apps.bea.gov/national/Release/TXT/NipaDataQ.txt", pause=0)
if b:
    z = []
    for zeile in b.decode("utf-8", "replace").splitlines():
        if zeile.startswith("A191RC,"):
            f = zeile.split(",", 2)
            try:
                z.append((quartalsende(f[1]), float(f[2].strip().strip('"').replace(",", ""))))
            except (ValueError, KeyError):
                pass
    schreiben("bea_bip", z)
