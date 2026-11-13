#!/usr/bin/env python3
# Baut die Haus-Rangliste der Analysten (daten/analysten/haeuser.json und die Kopie in app/src/main/assets/analysten).
#
# Quelle: Yahoo Finance, upgradeDowngradeHistory (quoteSummary, mit crumb) und Tageskurse (chart) der Aktien aus S&P 500,
# Nasdaq 100, DAX und Euro Stoxx 50 laut daten/katalog.json, dazu die Tageskurse der Vergleichsindizes. Yahoo fuehrt
# Urteile fast nur fuer US-Aktien; Aktien ohne Urteile bleiben leer.
#
# Aufruf:
#   python tools/daten/analystenhaeuser.py holen [--cache=<Ordner>] [--max=N]   Abrufe in den Zwischenspeicher (ein Abruf je Aktie nur einmal)
#   python tools/daten/analystenhaeuser.py bauen [--cache=<Ordner>]             aus dem Zwischenspeicher rechnen und die Buendeldatei schreiben
#   python tools/daten/analystenhaeuser.py alles                                holen und bauen
# Zwischenspeicher: Umgebungsvariable TRYFOLIO_CACHE, sonst <Temp>/tryfolio-cache (nicht im Repo).
# Danach: python tools/daten/manifest.py bauen --signieren
#
# Messung wie Analystenbilanz.kt: Schlusskurs am Urteilstag bis 63, 126 und 252 Handelstage spaeter, Rendite der Aktie geteilt
# durch die des Vergleichsindex minus 1. Eine Hochstufung trifft, wenn die Aktie besser lief als der Index, eine Abstufung,
# wenn sie schlechter lief. Gezaehlt werden nur Hoch- und Abstufungen (action up und down) mit mindestens einem
# verstrichenen Horizont; ein Urteil, dessen Stufe der Richtung widerspricht, zaehlt nicht. Trefferanteil und Vorsprung
# ueber alle verstrichenen Horizonte gepoolt. Haeuser ab MIN_URTEILE abgeschlossenen Urteilen.
#
# Netz: Chrome-Kennung, hoechstens zwei Abrufe je Sekunde, bei einer Sperre (403, dauerhaft 429, Pruefseite) Abbruch und
# Weiterrechnen mit dem bis dahin geladenen Teil.

import bisect
import http.cookiejar
import json
import os
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

STAND_ZEIT = None
MIN_URTEILE = 20
HORIZONTE = [63, 126, 252]
INDIZES = ["SP500", "NDX", "DAX", "ESTX50"]
TAG = 86400
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36"
Q1 = "https://query1.finance.yahoo.com"
PAUSE = 0.55

# Vergleichsindex je Land wie Aktienkennzahlen.index in der App (nur die Laender der vier Indizes), sonst MSCI World
INDEX_LAND = {"US": "^GSPC", "DE": "^GDAXI", "FR": "^FCHI", "NL": "^AEX", "IT": "FTSEMIB.MI", "ES": "^IBEX", "FI": "^OMXH25"}
INDEX_NAME = {"^GSPC": "S&P 500", "^GDAXI": "DAX", "^FCHI": "CAC 40", "^AEX": "AEX", "FTSEMIB.MI": "FTSE MIB", "^IBEX": "IBEX 35", "^OMXH25": "OMX Helsinki 25", "URTH": "MSCI World"}


def repo():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def vorgabe_cache():
    return os.environ.get("TRYFOLIO_CACHE") or os.path.join(tempfile.gettempdir(), "tryfolio-cache")


class Gesperrt(Exception):
    pass


class Yahoo:
    def __init__(self, cache):
        self.cache = cache
        os.makedirs(cache, exist_ok=True)
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.opener.addheaders = [("User-Agent", UA), ("Accept", "*/*"), ("Accept-Language", "en-US,en;q=0.9")]
        self.crumb = None
        self.letzter = 0.0
        self.abrufe = 0
        self.fehler429 = 0

    def roh(self, url):
        # ein Abruf, gedrosselt; (Status, Text)
        warte = PAUSE - (time.time() - self.letzter)
        if warte > 0:
            time.sleep(warte)
        self.letzter = time.time()
        self.abrufe += 1
        try:
            with self.opener.open(url, timeout=30) as r:
                return r.status, r.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as e:
            try:
                t = e.read().decode("utf-8", "replace")
            except Exception:
                t = ""
            return e.code, t
        except Exception as e:
            return 0, str(e)

    def hole(self, url):
        for versuch in range(5):
            s, t = self.roh(url)
            if s == 200:
                self.fehler429 = 0
                return t
            if s == 403:
                raise Gesperrt("403 " + url.split("?")[0])
            if s == 429:
                self.fehler429 += 1
                if self.fehler429 >= 5:
                    raise Gesperrt("429 dauerhaft")
                time.sleep(10 * (versuch + 1))
                continue
            if s == 404:
                return None
            if "consent" in t[:2000].lower() and s != 200:
                raise Gesperrt("Pruefseite")
            time.sleep(2)
        return None

    def crumb_holen(self):
        if self.crumb:
            return self.crumb
        self.roh("https://fc.yahoo.com/")
        s, t = self.roh(Q1 + "/v1/test/getcrumb")
        if s == 200 and t and len(t) < 40 and not t.startswith("<"):
            self.crumb = t.strip()
            return self.crumb
        self.roh("https://finance.yahoo.com/")
        s, t = self.roh(Q1 + "/v1/test/getcrumb")
        if s == 200 and t and len(t) < 40 and not t.startswith("<"):
            self.crumb = t.strip()
            return self.crumb
        raise Gesperrt("kein crumb (Status %s)" % s)

    def datei(self, name):
        return os.path.join(self.cache, name.replace("^", "_").replace("/", "_") + ".json")

    def stufen(self, sym):
        pfad = self.datei(sym + ".ud")
        if os.path.exists(pfad):
            return json.load(open(pfad, encoding="utf-8"))
        c = self.crumb_holen()
        url = "%s/v10/finance/quoteSummary/%s?modules=upgradeDowngradeHistory&crumb=%s" % (Q1, urllib.parse.quote(sym), urllib.parse.quote(c))
        t = self.hole(url)
        if t is None:
            # keine Daten fuer diese Aktie: leer merken
            erg = []
        else:
            try:
                r = json.loads(t)["quoteSummary"]["result"]
                h = (r[0].get("upgradeDowngradeHistory") or {}).get("history") or []
                erg = [{"f": e.get("firm", ""), "t": e.get("epochGradeDate", 0), "n": e.get("toGrade", ""), "v": e.get("fromGrade", ""), "a": e.get("action", "")} for e in h]
            except Exception:
                erg = []
        json.dump(erg, open(pfad, "w", encoding="utf-8"), ensure_ascii=False)
        return erg

    def kurse(self, sym):
        pfad = self.datei(sym + ".chart")
        if os.path.exists(pfad):
            return json.load(open(pfad, encoding="utf-8"))
        url = "%s/v8/finance/chart/%s?interval=1d&range=15y&includePrePost=false" % (Q1, urllib.parse.quote(sym))
        t = self.hole(url)
        erg = []
        if t is not None:
            try:
                r = json.loads(t)["chart"]["result"][0]
                ts = r.get("timestamp") or []
                cl = r["indicators"]["quote"][0].get("close") or []
                erg = [[a, round(b, 4)] for a, b in zip(ts, cl) if b]
            except Exception:
                erg = []
        json.dump(erg, open(pfad, "w", encoding="utf-8"))
        return erg


def katalog():
    d = json.load(open(os.path.join(repo(), "daten", "katalog.json"), encoding="utf-8"))
    return [a for a in d["aktien"] if any(x in INDIZES for x in a["x"])]


def index_fuer(a):
    return INDEX_LAND.get(a["c"], "URTH")


# Einstufung als Zahl von -2 bis +2 wie Analystenbilanz.stufe; None, wenn unbekannt
def stufe(e):
    e = e.strip().lower().replace("_", " ").replace("-", " ")
    if not e:
        return None
    if e in ("strong buy", "top pick", "conviction buy", "strong outperform"):
        return 2
    if e == "strong sell":
        return -2
    if e == "sell" or "underperform" in e or "underweight" in e or e in ("reduce", "moderate sell", "negative", "cautious", "trim"):
        return -1
    if "outperform" in e or "overweight" in e or "buy" in e or e in ("accumulate", "add", "positive", "long term buy", "speculative buy"):
        return 1
    if "perform" in e or "weight" in e or "neutral" in e or "hold" in e or e in ("in line", "inline", "mixed", "fair value", "market", "sector"):
        return 0
    return None


# +1 Hochstufung, -1 Abstufung, None sonst (nur up und down; widerspricht die Stufe der Richtung, zaehlt das Urteil nicht)
def richtung(u):
    nach = stufe(u["n"])
    von = stufe(u["v"])
    a = u["a"].lower()
    if a == "up":
        return None if (nach is not None and von is not None and nach < von) else 1
    if a == "down":
        return None if (nach is not None and von is not None and nach > von) else -1
    return None


class Reihe:
    def __init__(self, daten):
        self.t = [x[0] // TAG for x in daten]
        self.c = [x[1] for x in daten]

    def ab(self, tag):
        i = bisect.bisect_left(self.t, tag)
        return i if i < len(self.t) else -1

    def bis(self, tag):
        return bisect.bisect_right(self.t, tag) - 1


def mehr_rendite(k, idx, zeit, tage):
    i = k.ab(zeit // TAG)
    if i < 0:
        return None
    j = i + tage
    if j >= len(k.t):
        return None
    a, b = k.c[i], k.c[j]
    if not (a > 0 and b > 0):
        return None
    r = b / a
    if idx is None:
        return r - 1.0
    ia, ib = idx.bis(k.t[i]), idx.bis(k.t[j])
    if ia < 0 or ib < 0 or ib <= ia:
        return None
    x, y = idx.c[ia], idx.c[ib]
    if not (x > 0 and y > 0):
        return None
    return r / (y / x) - 1.0


def bauen(yahoo, jetzt):
    aktien = katalog()
    kurse_idx = {}
    haeuser = {}
    ausgewertet = 0
    mit_urteilen = 0
    urteile_gesamt = 0
    frueh = None
    spaet = None
    for a in aktien:
        sym = a["s"]
        ud = os.path.exists(yahoo.datei(sym + ".ud")) and json.load(open(yahoo.datei(sym + ".ud"), encoding="utf-8"))
        ck = os.path.exists(yahoo.datei(sym + ".chart")) and json.load(open(yahoo.datei(sym + ".chart"), encoding="utf-8"))
        if not ud or not ck:
            if ud is not False and ck is not False:
                pass
            continue
        ausgewertet += 1
        mit_urteilen += 1
        ix = index_fuer(a)
        if ix not in kurse_idx:
            p = yahoo.datei(ix + ".chart")
            kurse_idx[ix] = Reihe(json.load(open(p, encoding="utf-8"))) if os.path.exists(p) else None
        k = Reihe(ck)
        for u in ud:
            r = richtung(u)
            if r is None or not u["f"].strip() or u["t"] <= 0:
                continue
            m = [mehr_rendite(k, kurse_idx[ix], u["t"], h) for h in HORIZONTE]
            if all(x is None for x in m):
                continue
            h = haeuser.setdefault(u["f"].strip(), {"hoch": 0, "ab": 0, "t": [0, 0, 0], "n": [0, 0, 0], "summe": 0.0, "zahl": 0, "aktien": set(), "letztes": 0})
            if r > 0:
                h["hoch"] += 1
            else:
                h["ab"] += 1
            for i, x in enumerate(m):
                if x is None:
                    continue
                h["n"][i] += 1
                if x * r > 0:
                    h["t"][i] += 1
                h["summe"] += x * r
                h["zahl"] += 1
            h["aktien"].add(sym)
            h["letztes"] = max(h["letztes"], u["t"])
            urteile_gesamt += 1
            frueh = u["t"] if frueh is None else min(frueh, u["t"])
            spaet = u["t"] if spaet is None else max(spaet, u["t"])
    zeilen = []
    for name, h in haeuser.items():
        abgeschlossen = h["hoch"] + h["ab"]
        if abgeschlossen < MIN_URTEILE:
            continue
        treffer = sum(h["t"])
        bewertet = sum(h["n"])
        zeilen.append({
            "h": name,
            "u": abgeschlossen,
            "hoch": h["hoch"],
            "ab": h["ab"],
            "q": round(treffer / bewertet, 4),
            "q3": round(h["t"][0] / h["n"][0], 4) if h["n"][0] else None,
            "q6": round(h["t"][1] / h["n"][1], 4) if h["n"][1] else None,
            "q12": round(h["t"][2] / h["n"][2], 4) if h["n"][2] else None,
            "v": round(h["summe"] / h["zahl"] * 100, 2),
            "a": len(h["aktien"]),
            "z": time.strftime("%Y-%m", time.gmtime(h["letztes"])),
        })
    zeilen.sort(key=lambda z: (-z["q"], -z["u"], z["h"]))
    ohne_index = [i for i, r in kurse_idx.items() if r is None]
    return {
        "schema": 1,
        "stand": time.strftime("%Y-%m-%d", time.gmtime(jetzt)),
        "von": time.strftime("%Y-%m-%d", time.gmtime(frueh)) if frueh else "",
        "bis": time.strftime("%Y-%m-%d", time.gmtime(spaet)) if spaet else "",
        "aktien": ausgewertet,
        "urteile": urteile_gesamt,
        "minUrteile": MIN_URTEILE,
        "horizonte": [3, 6, 12],
        "index": {k: INDEX_NAME[k] for k in kurse_idx if kurse_idx[k] is not None},
        "ohneIndex": ohne_index,
        "haeuser": zeilen,
    }


def holen(yahoo, maximal):
    aktien = katalog()
    indizes = sorted({index_fuer(a) for a in aktien})
    ziel = [(i, "chart") for i in indizes] + [(a["s"], "ud") for a in aktien]
    neu = 0
    try:
        for name, art in ziel:
            if art == "chart" and os.path.exists(yahoo.datei(name + ".chart")):
                continue
            if art == "ud":
                if os.path.exists(yahoo.datei(name + ".ud")) and os.path.exists(yahoo.datei(name + ".chart")):
                    continue
                ud = yahoo.stufen(name)
                if ud and not os.path.exists(yahoo.datei(name + ".chart")):
                    yahoo.kurse(name)
                elif not ud and not os.path.exists(yahoo.datei(name + ".chart")):
                    # ohne Urteile keine Kurse noetig, leer merken
                    json.dump([], open(yahoo.datei(name + ".chart"), "w"))
            else:
                yahoo.kurse(name)
            neu += 1
            if neu % 25 == 0:
                print("geladen:", neu, "von", len(ziel), "Abrufe", yahoo.abrufe, flush=True)
            if maximal and neu >= maximal:
                break
    except Gesperrt as e:
        print("GESPERRT:", e, "- weiter mit dem geladenen Teil", flush=True)
        return False
    return True


def main(argv):
    cmd = argv[1] if len(argv) > 1 else "alles"
    cache = vorgabe_cache()
    maximal = 0
    for a in argv[2:]:
        if a.startswith("--cache="):
            cache = a[8:]
        if a.startswith("--max="):
            maximal = int(a[6:])
    y = Yahoo(cache)
    if cmd in ("holen", "alles"):
        holen(y, maximal)
    if cmd in ("bauen", "alles"):
        erg = bauen(y, int(time.time()))
        text = json.dumps(erg, ensure_ascii=False, separators=(",", ":"))
        for ziel in (os.path.join(repo(), "app", "src", "main", "assets", "analysten", "haeuser.json"), os.path.join(repo(), "daten", "analysten", "haeuser.json")):
            os.makedirs(os.path.dirname(ziel), exist_ok=True)
            with open(ziel, "w", encoding="utf-8", newline="\n") as f:
                f.write(text)
        print("Aktien", erg["aktien"], "Urteile", erg["urteile"], "Haeuser", len(erg["haeuser"]), "Zeitraum", erg["von"], erg["bis"], "Bytes", len(text.encode("utf-8")))


if __name__ == "__main__":
    main(sys.argv)
