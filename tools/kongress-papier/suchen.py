# Sucht fuer offene Namen (Ergebnis von kuerzel.py) ein Kuerzel ueber die Suche von Yahoo Finance. Angenommen wird ein
# Treffer nur, wenn er eine Aktie oder ein ETF an einer US-Boerse ist und jedes Wort des Namens (ohne Rechtsform) zu
# einem Wort des Treffers passt: gleicher Anfang oder Abkuerzung (Buchstaben in Reihenfolge, gleicher erster Buchstabe,
# etwa PRODS/PRODUCTS, CHEMS/CHEMICALS). Alles andere kommt auf die Pruefliste.
# Abrufe langsam (2 je Sekunde), mit Browser-Kennung ohne Angaben zur Person; Ergebnisse im Zwischenspeicher.
# Aufruf: python -I suchen.py <namen.json> <zwischenspeicher.json> <ziel.json> [--varianten-nur-sauber]
# Mit --varianten-nur-sauber sucht die zweite Runde (Varianten) nur fuer Namen, deren Woerter alle sauber gelesen
# aussehen (nur Buchstaben, keine OCR-Reste); das spart bei OCR-Namen viele Abrufe.

import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

NAMEN, CACHE, ZIEL = sys.argv[1:4]
NUR_SAUBER = "--varianten-nur-sauber" in sys.argv[4:]
sys.stdout.reconfigure(encoding="utf-8")
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
US = {"NMS", "NYQ", "NGM", "NCM", "ASE", "PCX", "BTS", "NYS", "PNK", "OQX", "OQB"}
ZUSATZ = set("""INC INCORPORATED CORP CORPORATION CO COS COMPANY COMPANIES LTD LIMITED PLC NV SA AG SE AB ASA LP LLC
HLDGS HOLDINGS HLDG HOLDING GROUP GRP THE NEW F CL CLASS COM COMMON STK STOCK SHS SH ORD REIT DEL TR ADR ADS SPONSORED
SPON INTL AND OF PUBLIC""".split())


def woerter(n):
    return [w for w in re.sub(r"[^A-Z0-9]+", " ", (n or "").upper()).split() if w not in ZUSATZ and len(w) > 1]


def passt(q, c):
    if c.startswith(q) or q.startswith(c):
        return True
    # gleicher Wortstamm (TECHNOLOGY/TECHNOLOGIES): mindestens sechs gleiche Anfangsbuchstaben
    gemein = 0
    while gemein < min(len(q), len(c)) and q[gemein] == c[gemein]:
        gemein += 1
    if gemein >= 6:
        return True
    if q[0] != c[0] or len(q) < 2:
        return False
    i = 0
    for ch in c:
        if i < len(q) and ch == q[i]:
            i += 1
    return i == len(q)


ABK = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "abkuerzungen.json"), encoding="utf-8"))


def ausgeschrieben(n):
    return [x for x in (ABK.get(w, w) for w in woerter(n)) if x]


def annehmbar(name, kandidat):
    """Jedes Wort des gemeldeten Namens passt zu einem Wort des Treffers, und umgekehrt deckt der Name mindestens 60
    Prozent der Woerter des Treffers ab (sonst wurde „UNDE PLC“, ein Lesefehler fuer Linde, zu Under Armour). Ein
    einzelnes Wort zaehlt nur mit gleichem Anfang, als Kuerzel oder als Initialen und nur bei einem Treffer ohne
    weitere Woerter."""
    q = ausgeschrieben(name)
    lang = kandidat.get("longname") or kandidat.get("shortname") or ""
    roh_q = set(re.sub(r"[^A-Z0-9]+", " ", name.upper()).split())
    roh_c = set(re.sub(r"[^A-Z0-9]+", " ", lang.upper()).split())
    # Einzelbuchstaben des Treffers zaehlen mit („U.S. Bancorp“ ist nicht „Bancorp“)
    c = [w for w in re.sub(r"[^A-Z0-9]+", " ", lang.upper()).split() if w not in ZUSATZ]
    if not q or not c:
        return False
    # Firmennamen nur gegen Aktien (sonst wurde „DANA INC“ ein ETF), CORP nicht gegen „Inc.“ und umgekehrt
    firma = roh_q & {"INC", "CORP", "CO", "COS", "PLC", "LTD", "HLDGS", "HOLDINGS", "GROUP", "NV", "SA", "AG", "LP"}
    if firma and kandidat.get("quoteType") != "EQUITY":
        return False
    corp_c = bool(roh_c & {"CORP", "CORPORATION"})
    inc_c = bool(roh_c & {"INC", "INCORPORATED"})
    if ("CORP" in roh_q and inc_c and not corp_c) or ("INC" in roh_q and corp_c and not inc_c):
        return False
    # abgeschnittene letzte Woerter („PHARMACEUTICALS I“) zaehlen nicht
    q = [w for i, w in enumerate(q) if not (i == len(q) - 1 and len(w) == 1)]
    if not q:
        return False
    initialen = "".join(x[0] for x in c if len(x) > 1)

    # ein Wort darf auch die Initialen sein (IBM fuer International Business Machines)
    def ok(w):
        return any(passt(w, x) for x in c) or (2 <= len(w) <= 4 and w == initialen)
    if not all(ok(w) for w in q):
        return False
    if len(q) == 1:
        w = q[0]
        if 2 <= len(w) <= 4 and w == initialen:
            return True
        # ein einzelnes Wort nur bei gleichem Namen (sonst wurde NEOGEN zu NeoGenomics)
        return len(set(c)) == 1 and (c[0] == w or (c[0].startswith(w) and len(c[0]) - len(w) <= 2)
                                     or (w.startswith(c[0]) and len(w) - len(c[0]) <= 2))
    abgedeckt = {x for x in c if any(passt(w, x) for w in q)}
    return len(abgedeckt) / len(set(c)) >= 0.6


def varianten(name):
    """Weitere Suchtexte fuer Namen ohne Treffer: ausgeschrieben, erstes Wort ans Ende (umgedrehte Personennamen),
    die ersten zwei Woerter."""
    w = ausgeschrieben(name)
    aus = []
    for v in (w, w[1:] + w[:1] if len(w) >= 2 else [], w[:2] if len(w) >= 3 else []):
        t = " ".join(v)
        if t and t != " ".join(woerter(name)) and t not in aus:
            aus.append(t)
    return aus


def suche(text):
    url = ("https://query2.finance.yahoo.com/v1/finance/search?q=" + urllib.parse.quote(text)
           + "&quotesCount=8&newsCount=0&listsCount=0&enableFuzzyQuery=false")
    for versuch in range(5):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"}), timeout=30) as r:
                return json.loads(r.read()).get("quotes") or []
        except urllib.error.HTTPError as e:
            if e.code == 429:
                print("Yahoo bremst, Pause", 60 * (versuch + 1), "s", flush=True)
                time.sleep(60 * (versuch + 1))
                continue
            return []
        except Exception:
            time.sleep(5 * (versuch + 1))
    return None


offen = json.load(open(NAMEN, encoding="utf-8"))["offen"]
cache = json.load(open(CACHE, encoding="utf-8")) if os.path.exists(CACHE) else {}
for i, name in enumerate(offen):
    if name in cache:
        continue
    text = " ".join(woerter(name)) or name
    q = suche(text)
    if q is None:
        print("abgebrochen bei", name)
        break
    cache[name] = [{k: x.get(k) for k in ("symbol", "shortname", "longname", "quoteType", "exchange")} for x in q]
    if i % 50 == 0:
        json.dump(cache, open(CACHE, "w", encoding="utf-8"), ensure_ascii=False)
        print(i, "von", len(offen), flush=True)
    time.sleep(0.5)
json.dump(cache, open(CACHE, "w", encoding="utf-8"), ensure_ascii=False)


def us(liste):
    return [x for x in liste if x.get("quoteType") in ("EQUITY", "ETF") and x.get("exchange") in US]


# zweite Runde: Varianten fuer Namen ohne annehmbaren Treffer (Schluessel im Zwischenspeicher: „?“ und Suchtext)
zweite = [n for n in offen if not any(annehmbar(n, x) for x in us(cache.get(n, [])))]
if NUR_SAUBER:
    zweite = [n for n in zweite if re.fullmatch(r"[A-Z][A-Z .,&'()\-/]*", n) and all(len(w) >= 2 for w in woerter(n))]
for i, name in enumerate(zweite):
    for text in varianten(name):
        if "?" + text in cache:
            continue
        q = suche(text)
        if q is None:
            break
        cache["?" + text] = [{k: x.get(k) for k in ("symbol", "shortname", "longname", "quoteType", "exchange")} for x in q]
        time.sleep(0.5)
        if any(annehmbar(name, x) for x in us(cache["?" + text])):
            break
    if i % 50 == 0:
        json.dump(cache, open(CACHE, "w", encoding="utf-8"), ensure_ascii=False)
        print("Varianten", i, "von", len(zweite), flush=True)
json.dump(cache, open(CACHE, "w", encoding="utf-8"), ensure_ascii=False)

treffer, pruefen = {}, {}
for name, anzahl in offen.items():
    kandidaten = us(cache.get(name, []))
    for text in varianten(name):
        kandidaten += us(cache.get("?" + text, []))
    gut = [x for x in kandidaten if annehmbar(name, x)]
    if gut:
        treffer[name] = {"k": gut[0]["symbol"], "art": "aktie", "quelle": "yahoo-suche", "treffer": gut[0].get("longname") or gut[0].get("shortname")}
    else:
        pruefen[name] = {"anzahl": anzahl, "kandidaten": [(x.get("symbol"), x.get("longname") or x.get("shortname"), x.get("exchange")) for x in kandidaten[:3]]}
json.dump({"treffer": treffer, "pruefen": dict(sorted(pruefen.items(), key=lambda kv: -kv[1]["anzahl"]))},
          open(ZIEL, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("Treffer:", len(treffer), "Namen mit", sum(offen[n] for n in treffer), "Geschaeften | zu pruefen:", len(pruefen),
      "Namen mit", sum(v["anzahl"] for v in pruefen.values()), "Geschaeften")
