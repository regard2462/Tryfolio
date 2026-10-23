# Ordnet den Namen der Jahresmeldung (Teil 7, nur Namen) und den Zeilen ohne Kuerzel aus den Einzelmeldungen ein
# Boersenkuerzel zu. Reihenfolge: gleicher Name mit Kuerzel in den Einzelmeldungen, dann bereinigter Name (ohne
# Rechtsform und Zusaetze) gegen Einzelmeldungen und Yahoo-Eckdaten, dann Namen aus namen-hand.json (von Hand
# geprueft). Anleihen, Vorzugsaktien, Einlagen und Geldmarkt bleiben ohne Kuerzel (Art „anleihe“).
# Aufruf: python -I kuerzel.py <jahresmeldung.json> <regierung.json> <yahoo-ordner> <namen-hand.json> <ziel.json>

import collections
import json
import os
import re
import sys

JM, REG, YAHOO, HAND, ZIEL = sys.argv[1:6]
sys.stdout.reconfigure(encoding="utf-8")

ZUSATZ = set("""INC INCORPORATED CORP CORPORATION CO COS COMPANY COMPANIES LTD LIMITED PLC NV SA AG SE AB ASA LP LLC
HLDGS HOLDINGS HLDG HOLDING GROUP GRP THE NEW F CL CLASS A B C COM COMMON STK STOCK SHS SH ORD REIT N I DEL TR
ADR ADS SPONSORED SPON CAP INTL""".split())
# Abkuerzungen der Maklerbezeichnungen (PRODS, CHEMS, WKS …) ausgeschrieben; leere Werte fallen weg (Staaten wie OH, MASS)
ABKUERZUNG = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "abkuerzungen.json"), encoding="utf-8"))
ANLEIHE = re.compile(r"(\d+\.?\d*\s*%|\bDUE\b|\bREV\b|\bBDS?\b|\bNTS?\b|\bNOTES?\b|\bPFD\b|\bPERP\b|\bDEP\b|\bCD\b|\bMUNI|"
                     r"\bOBLIG|\bG\.?O\.?\b|\bSCH(OOL)?\s+DIST|\bAUTH\b|\bCNTY\b|\bTAXABLE\b|\bB/E\b|\bSER\s+[A-Z0-9]+\b|"
                     r"\bMONEY MARKET\b|\bTREAS|\bT-BILL|\bCERT\b|\bDEB\b|\bFRN\b|\d{1,2}/\d{1,2}/\d{2,4})", re.I)


def norm(n):
    return re.sub(r"[^A-Z0-9]+", " ", (n or "").upper()).strip()


def kern(n):
    """Name ohne Rechtsform und Zusaetze, Abkuerzungen ausgeschrieben, Woerter sortiert: Die Makler schreiben Namen
    von Personen umgedreht („DISNEY WALT“, „PRICE T ROWE“), Yahoo in der ueblichen Folge."""
    w = [ABKUERZUNG.get(x, x) for x in norm(n).split()]
    w = {x for x in w if x and x not in ZUSATZ}
    return " ".join(sorted(w))


def gueltig(k):
    k = (k or "").strip()
    return k if k and not k.startswith("-") and any(c.isalpha() for c in k) else None


jm = json.load(open(JM, encoding="utf-8"))
reg = json.load(open(REG, encoding="utf-8"))
hand = json.load(open(HAND, encoding="utf-8")) if os.path.exists(HAND) else {}
trump = next(p for p in reg["personen"] if p["id"] == "trump")

# Paare Name -> Kuerzel aus den Einzelmeldungen (nur Aktien, ETFs, Optionen)
genau, nach_kern = collections.defaultdict(collections.Counter), collections.defaultdict(collections.Counter)
for m in trump["meldungen"]:
    for g in m["geschaefte"]:
        k = gueltig(g.get("k"))
        if k and g.get("t") in ("ST", "EF", "OP", "OT", "Stock", "Stock Option"):
            genau[norm(g["n"])][k] += 1
            nach_kern[kern(g["n"])][k] += 1
# Yahoo-Eckdaten: Kurz- und Langname
yahoo_kern = collections.defaultdict(set)
for f in os.listdir(os.path.join(YAHOO, "quote")):
    for q in json.load(open(os.path.join(YAHOO, "quote", f), encoding="utf-8")):
        if q.get("quoteType") not in ("EQUITY", "ETF") or "." in q["symbol"]:
            continue
        for feld in ("shortName", "longName"):
            if q.get(feld):
                yahoo_kern[kern(q[feld])].add(q["symbol"])

namen = collections.Counter(z["n"] for z in jm["teil7"])
for m in trump["meldungen"]:
    for g in m["geschaefte"]:
        if not gueltig(g.get("k")):
            namen[g["n"]] += 1

aus, offen = {}, collections.Counter()
for n, anzahl in namen.items():
    if n in hand:
        aus[n] = {**hand[n], "quelle": "hand"}
    elif ANLEIHE.search(n):
        aus[n] = {"k": None, "art": "anleihe", "quelle": "muster"}
    elif len(genau.get(norm(n), {})) == 1:
        aus[n] = {"k": next(iter(genau[norm(n)])), "art": "aktie", "quelle": "einzelmeldung"}
    elif len(nach_kern.get(kern(n), {})) == 1 and kern(n):
        aus[n] = {"k": next(iter(nach_kern[kern(n)])), "art": "aktie", "quelle": "einzelmeldung-kern"}
    elif len(yahoo_kern.get(kern(n), set())) == 1 and kern(n):
        aus[n] = {"k": next(iter(yahoo_kern[kern(n)])), "art": "aktie", "quelle": "yahoo-kern"}
    else:
        offen[n] = anzahl

json.dump({"namen": aus, "offen": dict(offen.most_common())}, open(ZIEL, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
quellen = collections.Counter(v["quelle"] for v in aus.values())
print("Namen:", len(namen), "| zugeordnet:", dict(quellen), "| offen:", len(offen), "Namen mit", sum(offen.values()), "Geschaeften")
print("haeufigste offene:", list(offen.most_common(30)))
