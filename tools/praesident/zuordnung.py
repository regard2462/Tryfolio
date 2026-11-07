# Fuehrt die Zuordnungen Name -> Kuerzel zusammen: kuerzel.py (ohne Netz), suchen.py (Yahoo-Suche) und namen-hand.json
# (von Hand geprueft, hat Vorrang). Art „etf“, wenn Yahoo den Wert als ETF fuehrt (Eckdaten oder Suchtreffer).
# Aufruf: python -I zuordnung.py <namen.json> <suche.json> <suche-cache.json> <namen-hand.json> <yahoo-ordner> <ziel.json>

import json
import os
import sys

NAMEN, SUCHE, CACHE, HAND, YAHOO, ZIEL = sys.argv[1:7]
sys.stdout.reconfigure(encoding="utf-8")


def lesen(p, leer):
    return json.load(open(p, encoding="utf-8")) if os.path.exists(p) else leer


namen = lesen(NAMEN, {"namen": {}})["namen"]
suche = lesen(SUCHE, {"treffer": {}})["treffer"]
cache = lesen(CACHE, {})
hand = lesen(HAND, {})

etf = set()
for f in os.listdir(os.path.join(YAHOO, "quote")):
    for q in json.load(open(os.path.join(YAHOO, "quote", f), encoding="utf-8")):
        if q.get("quoteType") == "ETF":
            etf.add(q["symbol"])
for kandidaten in cache.values():
    for x in kandidaten:
        if x.get("quoteType") == "ETF" and x.get("symbol"):
            etf.add(x["symbol"])

aus = {}
for quelle in (namen, suche, hand):
    for n, e in quelle.items():
        aus[n] = {"k": e.get("k"), "art": e.get("art", "aktie"), "quelle": e.get("quelle", "hand")}
for e in aus.values():
    if e["k"] and e["art"] != "anleihe" and e["k"] in etf:
        e["art"] = "etf"
json.dump(aus, open(ZIEL, "w", encoding="utf-8"), ensure_ascii=False, indent=0)
arten = {}
for e in aus.values():
    arten[e["art"]] = arten.get(e["art"], 0) + 1
print("Zuordnungen:", len(aus), arten)
