#!/usr/bin/env python3
# Baut daten/etf-rueckwaerts.json (und die Kopie unter app/src/main/assets/): in welchen ETFs aus katalog.json eine Aktie steckt.
#
# Quelle: Yahoo Finance, quoteSummary Modul topHoldings (wie MarketApi.etfBestand der App), je ETF die groessten Positionen
# (Yahoo liefert hoechstens zehn). SEC N-PORT scheidet aus: Der Katalog enthaelt nur in Europa zugelassene UCITS-ETFs (ISIN IE, LU, DE, FR, NL),
# die keine Form N-PORT einreichen.
#
# Aufruf:
#   python tools/daten/etf_rueckwaerts.py holen     ETFs abrufen (hoechstens 2 Abrufe je Sekunde), Antworten nach <cache>/etf/<symbol>.json
#   python tools/daten/etf_rueckwaerts.py bauen     Buendel aus dem Zwischenspeicher schreiben
#   python tools/daten/etf_rueckwaerts.py alles     beides
# Zwischenspeicher: Umgebungsvariable TRYFOLIO_CACHE, sonst ein Ordner im Temp-Verzeichnis
# Danach: python tools/daten/manifest.py bauen --signieren

import http.cookiejar
import json
import os
import sys
import tempfile
import time
import urllib.parse
import urllib.request

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
STAND = "2026-11-11"
MIN_GEWICHT = 0.0


def repo():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def cache():
    wurzel = os.environ.get("TRYFOLIO_CACHE") or os.path.join(tempfile.gettempdir(), "tryfolio-cache")
    p = os.path.join(wurzel, "etf")
    os.makedirs(p, exist_ok=True)
    return p


def katalog():
    with open(os.path.join(repo(), "daten", "katalog.json"), encoding="utf-8") as f:
        return json.load(f)


class Yahoo:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.op.addheaders = [("User-Agent", UA), ("Accept", "*/*"), ("Accept-Language", "en-US,en;q=0.9")]
        self.letzter = 0.0
        self.crumb = None

    def get(self, url):
        warte = 0.55 - (time.time() - self.letzter)
        if warte > 0:
            time.sleep(warte)
        self.letzter = time.time()
        try:
            with self.op.open(url, timeout=30) as r:
                return r.status, r.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as e:
            return e.code, ""
        except Exception:
            return 0, ""

    def start(self):
        self.get("https://finance.yahoo.com/")
        self.get("https://fc.yahoo.com/")
        s, c = self.get("https://query1.finance.yahoo.com/v1/test/getcrumb")
        c = c.strip()
        if s != 200 or not c or len(c) >= 40 or c.startswith("<"):
            raise SystemExit("Kein Crumb (Status %s)" % s)
        self.crumb = c

    def bestand(self, symbol):
        url = "https://query1.finance.yahoo.com/v10/finance/quoteSummary/%s?modules=topHoldings&crumb=%s" % (
            urllib.parse.quote(symbol), urllib.parse.quote(self.crumb))
        return self.get(url)


def holen():
    y = Yahoo()
    y.start()
    ordner = cache()
    fehler = 0
    for e in katalog()["etfs"]:
        datei = os.path.join(ordner, e["s"] + ".json")
        if os.path.exists(datei):
            continue
        status, body = y.bestand(e["s"])
        if status in (403, 429):
            print("Sperre bei %s: Status %s, Abbruch" % (e["s"], status))
            sys.exit(2)
        if status != 200:
            print("%s Status %s" % (e["s"], status))
            fehler += 1
            if status == 401:
                y.start()
            continue
        with open(datei, "w", encoding="utf-8") as f:
            f.write(body)
    print("Abruf fertig, Fehler:", fehler)


def positionen(body):
    try:
        res = json.loads(body)["quoteSummary"]["result"][0]["topHoldings"]["holdings"]
    except Exception:
        return []
    aus = []
    for h in res:
        s = h.get("symbol") or ""
        p = (h.get("holdingPercent") or {}).get("raw")
        if s and p and p > MIN_GEWICHT:
            aus.append((s, round(p * 100, 2)))
    return aus


def bauen():
    kat = katalog()
    ordner = cache()
    etfs = []
    aktien = {}
    ohne = []
    for e in kat["etfs"]:
        datei = os.path.join(ordner, e["s"] + ".json")
        pos = positionen(open(datei, encoding="utf-8").read()) if os.path.exists(datei) else []
        if not pos:
            ohne.append(e["s"])
            continue
        idx = len(etfs)
        etfs.append([e["s"], (e.get("a", "") + " " + e["n"]).strip()])
        for s, g in pos:
            aktien.setdefault(s, []).append([idx, g])
    for l in aktien.values():
        l.sort(key=lambda x: -x[1])
    aus = {"stand": STAND, "quelle": "yahoo-topHoldings", "tiefe": 10, "etfs": etfs, "aktien": dict(sorted(aktien.items()))}
    text = json.dumps(aus, ensure_ascii=False, separators=(",", ":"))
    for pfad in (os.path.join(repo(), "daten", "etf-rueckwaerts.json"),
                 os.path.join(repo(), "app", "src", "main", "assets", "etf-rueckwaerts.json")):
        os.makedirs(os.path.dirname(pfad), exist_ok=True)
        with open(pfad, "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
    print("ETFs mit Daten: %d von %d, ohne: %s" % (len(etfs), len(kat["etfs"]), ",".join(ohne)))
    print("Aktien: %d, Zeilen: %d, Bytes: %d" % (len(aktien), sum(len(v) for v in aktien.values()), len(text.encode("utf-8"))))


if __name__ == "__main__":
    w = sys.argv[1] if len(sys.argv) > 1 else ""
    if w == "holen":
        holen()
    elif w == "bauen":
        bauen()
    elif w == "alles":
        holen()
        bauen()
    else:
        print(__doc__)
