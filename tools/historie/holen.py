#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Lange Reihen fuer Ausblick und Stimmung als kompakte Dateien im Datenbuendel (app/src/main/assets/indikatoren).

  rezession  NY Fed, Rezessionswahrscheinlichkeit aus der Zinskurve (monatlich ab 1959) mit der NBER-Spalte der Datei
             https://www.newyorkfed.org/medialibrary/media/research/capital_markets/allmonth.xls
             Bedingungen: https://www.newyorkfed.org/privacy/termsofuse (Nutzen, Kopieren, Verbreiten, Aendern, auch
             automatisch; Copyright- und Quellenhinweis beibehalten, Aenderungen kenntlich machen, gleiche Bedingungen
             bei Weitergabe, kein Anschein einer Billigung) -> indikatoren/rezession_nyfed.json
  cot        CFTC Commitments of Traders, Legacy Futures, ganzes Archiv je Markt (woechentlich ab 1986)
             https://publicreporting.cftc.gov/resource/6dca-aqww.json
             Bedingungen: https://www.cftc.gov/WebPolicy/index.htm (gemeinfrei, Nennung erbeten) -> indikatoren/cot_archiv.json
  krypto     alternative.me Crypto Fear and Greed Index, ganzes Archiv (taeglich ab 01.02.2018)
             https://api.alternative.me/fng/?limit=0&format=json
             Bedingungen: https://alternative.me/crypto/api/ (freie Nutzung, auch kommerziell) -> indikatoren/krypto_fng.json

Aufruf: python holen.py rezession|cot|krypto|alle
Die Markt-Kennungen der CFTC stehen in MAERKTE und muessen zu Positionierung.MAERKTE der App passen.
"""
import datetime as dt
import json
import os
import sys
import time
import urllib.parse
import urllib.request

import xlrd

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
NYFED = "https://www.newyorkfed.org/medialibrary/media/research/capital_markets/allmonth.xls"
CFTC = "https://publicreporting.cftc.gov/resource/6dca-aqww.json"
FNG = "https://api.alternative.me/fng/?limit=0&format=json"

# Schluessel der App und CFTC-Kennung (Legacy, nur Futures), in der Reihenfolge der Anzeige
MAERKTE = [
    ("sp500", "13874A"), ("nasdaq", "209742"), ("anleihen2", "042601"), ("anleihen", "043602"), ("anleihen30", "020601"),
    ("euro", "099741"), ("yen", "097741"), ("pfund", "096742"), ("franken", "092741"), ("kanada", "090741"), ("australien", "232741"),
    ("gold", "088691"), ("silber", "084691"), ("platin", "076651"), ("palladium", "075651"), ("kupfer", "085692"),
    ("oel", "067651"), ("gas", "023651"), ("weizen", "001602"), ("mais", "002602"),
    ("kaffee", "083731"), ("kakao", "073732"), ("zucker", "080732"), ("bitcoin", "133741"),
]

AB = {"euro": dt.date(1999, 1, 1)}

LIZENZ_NYFED = ("Copyright %d Federal Reserve Bank of New York. Content from the New York Fed subject to the Terms of Use at "
                "newyorkfed.org. Auszug: Spalten Rec_prob (in Prozent, auf 0,1 gerundet) und NBER_Rec (als Spannen von Monaten), "
                "weitergegeben unter denselben Bedingungen (https://www.newyorkfed.org/privacy/termsofuse).")


def repo():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def ziel(name):
    return os.path.join(repo(), "app", "src", "main", "assets", "indikatoren", name)


def holen(url, pause=1.0):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=120) as r:
        b = r.read()
    time.sleep(pause)
    return b


def schreiben(name, obj):
    text = json.dumps(obj, ensure_ascii=False, separators=(",", ":"))
    with open(ziel(name), "w", encoding="utf-8", newline="") as f:
        f.write(text)
    print(name, len(text.encode("utf-8")), "Bytes")


def monat(d):
    return "%04d-%02d" % (d.year, d.month)


def rezession():
    b = holen(NYFED)
    buch = xlrd.open_workbook(file_contents=b)
    blatt = buch.sheet_by_name("rec_prob")
    kopf = [str(x).strip().lower() for x in blatt.row_values(0)]
    i_p = kopf.index("rec_prob")
    i_n = kopf.index("nber_rec")
    i_s = kopf.index("spread")
    zeilen = []
    for r in range(1, blatt.nrows):
        z = blatt.row_values(r)
        if not isinstance(z[0], float):
            continue
        d = xlrd.xldate_as_datetime(z[0], buch.datemode).date()
        p = z[i_p] if isinstance(z[i_p], float) else None
        n = z[i_n] if isinstance(z[i_n], float) else None
        s = z[i_s] if isinstance(z[i_s], float) else None
        zeilen.append((d, p, n, s))
    zeilen.sort()
    # Monate ohne Luecke
    for a, c in zip(zeilen, zeilen[1:]):
        if (c[0].year * 12 + c[0].month) - (a[0].year * 12 + a[0].month) != 1:
            raise SystemExit("Luecke nach " + str(a[0]))
    p = [None if x[1] is None else round(x[1] * 100, 1) for x in zeilen]
    # Rezessionsmonate als Spannen [erster, letzter] (NBER-Spalte der Datei)
    spannen = []
    an = None
    letzter = None
    for d, _, n, _ in zeilen:
        if n == 1.0:
            if an is None:
                an = monat(d)
            letzter = monat(d)
        elif an is not None:
            spannen.append([an, letzter])
            an = None
    if an is not None:
        spannen.append([an, letzter])
    bis_nber = monat(max(x[0] for x in zeilen if x[2] is not None))
    bis_kurve = monat(max(x[0] for x in zeilen if x[3] is not None))
    schreiben("rezession_nyfed.json", {
        "stand": bis_kurve,
        "quelle": NYFED,
        "lizenz": LIZENZ_NYFED % dt.date.today().year,
        "von": monat(zeilen[0][0]),
        "bisNber": bis_nber,
        "p": p,
        "nber": spannen,
    })


def cot():
    maerkte = {}
    letzter = ""
    for schluessel, code in MAERKTE:
        q = urllib.parse.urlencode({
            "cftc_contract_market_code": code,
            "$select": "report_date_as_yyyy_mm_dd,open_interest_all,noncomm_positions_long_all,noncomm_positions_short_all",
            "$order": "report_date_as_yyyy_mm_dd ASC",
            "$limit": "5000",
        })
        roh = json.loads(holen(CFTC + "?" + q))
        tage, netto, offen = [], [], []
        for e in roh:
            try:
                d = dt.date.fromisoformat(e["report_date_as_yyyy_mm_dd"][:10])
                o = int(float(e["open_interest_all"]))
                n = int(float(e.get("noncomm_positions_long_all") or 0)) - int(float(e.get("noncomm_positions_short_all") or 0))
            except (KeyError, ValueError):
                continue
            if o <= 0 or (tage and d <= tage[-1]):
                continue
            tage.append(d)
            netto.append(n)
            offen.append(o)
        # Vor dem Euro fuehrte die CFTC unter derselben Kennung die ECU
        ab = AB.get(schluessel)
        if ab:
            behalten = [i for i, d in enumerate(tage) if d >= ab]
            tage, netto, offen = [tage[i] for i in behalten], [netto[i] for i in behalten], [offen[i] for i in behalten]
        if not tage:
            print("ohne Daten:", schluessel, file=sys.stderr)
            continue
        # Netto in Promille der offenen Kontrakte (Positionierung.Woche.anteil mal zehn, gerundet). Getrennt waeren
        # Netto und offene Kontrakte fast dreimal so gross, die App zeigt nur den Anteil.
        anteil = [round(n * 1000 / o) for n, o in zip(netto, offen)]
        # Wochenabstand 7 Tage; nur Abweichungen als [Platz, Tage seit dem Vorwert]
        ausnahmen = [[i, (tage[i] - tage[i - 1]).days] for i in range(1, len(tage)) if (tage[i] - tage[i - 1]).days != 7]
        maerkte[schluessel] = {"code": code, "ab": tage[0].isoformat(), "x": ausnahmen, "a": anteil}
        letzter = max(letzter, tage[-1].isoformat())
        print(schluessel, len(tage), tage[0], tage[-1])
    schreiben("cot_archiv.json", {"stand": letzter, "quelle": CFTC, "einheit": "Netto der Non-Commercials in Promille des Open Interest", "maerkte": maerkte})


def krypto():
    roh = json.loads(holen(FNG))["data"]
    werte = {}
    for e in roh:
        t = dt.datetime.utcfromtimestamp(int(e["timestamp"])).date()
        werte[t] = int(e["value"])
    tage = sorted(werte)
    reihe = []
    t = tage[0]
    while t <= tage[-1]:
        reihe.append(werte.get(t))
        t += dt.timedelta(days=1)
    print("krypto", len(tage), tage[0], tage[-1], "Luecken", reihe.count(None))
    schreiben("krypto_fng.json", {"stand": tage[-1].isoformat(), "quelle": "https://alternative.me/crypto/fear-and-greed-index/",
                                  "ab": tage[0].isoformat(), "w": reihe})


if __name__ == "__main__":
    was = sys.argv[1] if len(sys.argv) > 1 else ""
    if was not in ("rezession", "cot", "krypto", "alle"):
        raise SystemExit(__doc__)
    if was in ("rezession", "alle"):
        rezession()
    if was in ("cot", "alle"):
        cot()
    if was in ("krypto", "alle"):
        krypto()
