# Verlauf des Praesidenten aus allen gemeldeten Geschaeften, vorab gerechnet (seit 3.8).
#
# 1. Ergaenzt regierung.json: Zeilen der Einzelmeldungen ohne Kuerzel bekommen das zugeordnete Kuerzel, die Geschaefte
#    der Jahresmeldung (Teil 7) kommen als eigene Meldung dazu (Verkauf als Teilverkauf, die Jahresmeldung unterscheidet
#    nicht).
# 2. Rechnet den zeitgewichteten Verlauf wie Portfolioverlauf.kongress in der App, mit den Kursen aller Werte, und
#    schreibt praesident.json: Index je Handelstag bis zum Stichtag, Marken, Bestand am Stichtag (Anteile), Anteil der
#    Anleihen (Teil 6 Ende 2025 plus Anleihegeschaefte danach), Zahlen fuer den Hinweis.
# Kurse: Tagesschlusskurse im Format von yahoo_holen.py (Ordner hist/, je Kuerzel eine Datei), mehrere Ordner moeglich.
#
# Aufruf: python -I verlauf.py <regierung.json> <jahresmeldung.json> <zuordnung.json> <ziel-regierung.json> <ziel-praesident.json> <kursordner>...
#   zuordnung.json: {Name: {"k": Kuerzel oder null, "art": "aktie"|"etf"|"anleihe"}} aus kuerzel.py, suchen.py und Hand

import bisect
import collections
import datetime
import json
import os
import re
import sys
import urllib.parse

REG, JM, ZUORDNUNG, ZIEL_REG, ZIEL_P = sys.argv[1:6]
KURSE = sys.argv[6:]
sys.stdout.reconfigure(encoding="utf-8")
TAG = 86400000
JM_DATEI = "https://extapps2.oge.gov/201/Presiden.nsf/PAS+Index/69AEAA9D7455ACD585258E27002DDEE1/$FILE/Donald-J-Trump-2026-278ANNUAL.pdf"
KOMMUNAL = re.compile(r"\b(ST|CNTY|CITY|SCH|DIST|AUTH|REV|OBLIG|G O|GO|MUN|PUB|HSG|TWP|BORO|VLG|PORT|TRAN|UTIL|WTR|SWR)\b")
# wie in kuerzel.py: Anleihen, Vorzugsaktien, Einlagen, Geldmarkt (fuer die Bestaende in Teil 6)
ANLEIHE = re.compile(r"(\d+\.?\d*\s*%|\bDUE\b|\bREV\b|\bBDS?\b|\bNTS?\b|\bNOTES?\b|\bPFD\b|\bPERP\b|\bDEP\b|\bCD\b|\bMUNI|"
                     r"\bOBLIG|\bG\.?O\.?\b|\bSCH(OOL)?\s+DIST|\bAUTH\b|\bCNTY\b|\bTAXABLE\b|\bB/E\b|\bSER\s+[A-Z0-9]+\b|"
                     r"\bMONEY MARKET\b|\bTREAS|\bT-BILL|\bCERT\b|\bDEB\b|\bFRN\b|\d{1,2}/\d{1,2}/\d{2,4})", re.I)


def lesen(p):
    return json.load(open(p, encoding="utf-8"))


def gueltig(k):
    k = (k or "").strip()
    return k if k and not k.startswith("-") and any(c.isalpha() for c in k) else None


# Kuerzel, die seit der Meldung gewechselt haben (Yahoo fuehrt die Kurse unter dem neuen), siehe umbenannt.json
UMBENANNT = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "umbenannt.json"), encoding="utf-8"))


def yahoo(k):
    """Kuerzel der Meldungen wie CopyRouten.symbol: BRK.B und BF/B werden zu BRK-B und BF-B; umbenannte zum neuen."""
    s = re.sub(r"^([A-Z0-9]+)[./]([A-KM-Z])$", r"\1-\2", k.strip())
    return UMBENANNT.get(s, s)


def tag_von(iso):
    d = datetime.date.fromisoformat(iso)
    return (d - datetime.date(1970, 1, 1)).days


reg = lesen(REG)
jm = lesen(JM)
zu = lesen(ZUORDNUNG)
trump = next(p for p in reg["personen"] if p["id"] == "trump")

# ---------------------------------------------------------------- 1. regierung.json ergaenzen
gefuellt = 0
for m in trump["meldungen"]:
    for g in m["geschaefte"]:
        if not gueltig(g.get("k")) and g["n"] in zu and zu[g["n"]].get("k"):
            g["k"] = zu[g["n"]]["k"]
            if zu[g["n"]].get("art") == "etf":
                g["t"] = "EF"
            gefuellt += 1
        if gueltig(g.get("k")) and g["k"].strip() in UMBENANNT:
            g["k"] = UMBENANNT[g["k"].strip()]
trump["meldungen"] = [m for m in trump["meldungen"] if m.get("datei") != JM_DATEI]
neu = []
for z in jm["teil7"]:
    e = zu.get(z["n"], {})
    art = e.get("art")
    if art == "anleihe":
        t = "MB" if KOMMUNAL.search(z["n"].upper()) else "CS"
    else:
        t = "EF" if art == "etf" else "ST"
    k = e.get("k")
    neu.append({"d": z["d"], "a": "K" if z["a"].startswith("p") else "T", "n": z["n"], "k": UMBENANNT.get(k, k) if k else None, "t": t,
                "von": z["von"], "bis": z["bis"], "s": False})
trump["meldungen"].append({"datei": JM_DATEI, "unterschrieben": "2026-06-29", "eingang": "2026-06-30", "seiten": 927,
                           "zeilen": len(neu), "ausgelassen": 0, "geschaefte": neu})
reg["stand"] = datetime.date.today().isoformat()
json.dump(reg, open(ZIEL_REG, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
print("Einzelmeldungen: Kuerzel ergaenzt bei", gefuellt, "Zeilen | Jahresmeldung:", len(neu), "Geschaefte angehaengt")

# ---------------------------------------------------------------- 2. Verlauf
alle = []          # (tag, kuerzel, kauf, betrag, ganz, option, quelle)
anleihen = ohne_kuerzel = sonstige = 0
anleihe_netto_2026 = 0.0
for m in trump["meldungen"]:
    jahr = m.get("datei") == JM_DATEI
    for nr, g in enumerate(m["geschaefte"]):
        t = (g.get("t") or "").strip()
        k = gueltig(g.get("k"))
        mitte = g["von"] if g.get("bis") is None else (g["von"] + g["bis"]) / 2
        if t in ("MB", "CS", "GS") or zu.get(g["n"], {}).get("art") == "anleihe":
            anleihen += 1
            if not jahr and g["d"] >= "2026-01-01":
                anleihe_netto_2026 += mitte if g["a"] == "K" else -mitte
            continue
        if g.get("a") not in ("K", "V", "T"):
            sonstige += 1
            continue
        if k is None:
            ohne_kuerzel += 1
            continue
        if t not in ("ST", "OP", "EF", "OT") or not g.get("d") or mitte <= 0:
            sonstige += 1
            continue
        # Verkaeufe immer nach Betrag: Die Konten (acht Depots) stehen in den Einzelmeldungen nicht, ein „ganzer“
        # Verkauf betrifft nur eines davon; den ganzen Bestand des Werts zu streichen, liess den Aktienwert abstuerzen
        # Schluessel zum Zusammenfassen: Einzelmeldungen wiederholen Zeilen in berichtigten Fassungen (wie in der App);
        # in der Jahresmeldung sind gleiche Zeilen echte Geschaefte in verschiedenen Depots und bleiben getrennt
        schluessel = ("jahresmeldung", nr) if jahr else (tag_von(g["d"]), yahoo(k), g["a"] == "K", mitte, t == "OP")
        alle.append((tag_von(g["d"]), yahoo(k), g["a"] == "K", mitte, False, t == "OP", g["n"], schluessel))

gesehen, handel = set(), []
for h in alle:
    if h[7] not in gesehen:
        gesehen.add(h[7])
        handel.append(h)
zusammengefasst = len(alle) - len(handel)


def kurse_laden(sym):
    datei = urllib.parse.quote(sym, safe="").replace("%", "_") + ".json"
    for ordner in KURSE:
        p = os.path.join(ordner, "hist", datei)
        if os.path.exists(p):
            d = lesen(p)
            if not d.get("leer") and d.get("tage"):
                m = {}
                for ts, c in d["tage"]:
                    if c and c > 0:
                        m[int(ts) * 1000 // TAG] = c
                if m:
                    return m
    return None


karten = {}
for s in sorted({h[1] for h in handel}):
    m = kurse_laden(s)
    if m:
        karten[s] = (sorted(m), m)


def kurs_am(s, d, ceiling=False):
    keys, m = karten[s]
    i = bisect.bisect_right(keys, d) - 1
    if i >= 0:
        return m[keys[i]]
    if ceiling:
        j = bisect.bisect_left(keys, d)
        return m[keys[j]] if j < len(keys) else None
    return None


mit_kurs = [h for h in handel if h[1] in karten and kurs_am(h[1], h[0], ceiling=True) is not None]
ohne_kurs = len(handel) - len(mit_kurs)
ohne_kurs_werte = collections.Counter(h[1] for h in handel if h[1] not in karten)
# gleicher Tag: Kaeufe vor Verkaeufen (ein am selben Tag gekaufter Wert kann verkauft werden)
mit_kurs.sort(key=lambda h: (h[0], 0 if h[2] else 1))
stichtag = max(h[0] for h in mit_kurs)
erster = next(i for i, h in enumerate(mit_kurs) if h[2])
start = mit_kurs[erster][0]
tage = sorted({d for keys, _ in karten.values() for d in keys if start <= d <= stichtag})
stueck, letzter = {}, {}
index, vorher, ohne_bestand = 100.0, 0.0, 0
reihe = []
i = erster
wert_2025 = None


def wert():
    return sum(n * letzter.get(k, 0.0) for k, n in stueck.items())


for d in tage:
    for k, (keys, m) in karten.items():
        if d in m:
            letzter[k] = m[d]
    vor = wert()
    if vorher > 0:
        index *= vor / vorher
    while i < len(mit_kurs) and mit_kurs[i][0] <= d:
        h = mit_kurs[i]
        i += 1
        kurs = kurs_am(h[1], h[0], ceiling=True)
        letzter.setdefault(h[1], kurs)
        hat = stueck.get(h[1], 0.0)
        if h[2]:
            stueck[h[1]] = hat + h[3] / kurs
        elif hat <= 1e-9:
            ohne_bestand += 1
        else:
            stueck[h[1]] = 0.0 if h[4] else max(0.0, hat - h[3] / kurs)
    vorher = wert()
    reihe.append((d, round(index, 4)))
    if d <= tag_von("2025-12-31"):
        wert_2025 = (d, vorher, {k: n * letzter.get(k, 0.0) for k, n in stueck.items() if n > 1e-9})

summe = wert()
bestand = sorted(((k, n * letzter.get(k, 0.0) / summe) for k, n in stueck.items() if n > 1e-9 and letzter.get(k)), key=lambda x: -x[1])
bestand = [(k, round(g, 6)) for k, g in bestand if g >= 1e-6]

# Marken: je Tag und Richtung die sechs groessten Werte
gruppen = collections.defaultdict(list)
for h in mit_kurs:
    gruppen[(h[0], h[2])].append(h)
marken = []
for (d, kauf), l in sorted(gruppen.items()):
    je = collections.Counter()
    for h in l:
        je[h[1]] += h[3]
    marken.append({"t": d, "kauf": kauf, "n": len(l), "w": [{"k": k, "g": round(b)} for k, b in je.most_common(6)]})

# Anleihen am Stichtag: Teil 6 (Ende 2025) plus Anleihegeschaefte 2026 aus den Einzelmeldungen
anleihe_2025 = 0.0
for z in jm["teil6"]:
    if (ANLEIHE.search(z["n"]) or zu.get(z["n"], {}).get("art") == "anleihe") and z.get("von") is not None and z["von"] > 0:
        anleihe_2025 += z["von"] if z.get("bis") is None else (z["von"] + z["bis"]) / 2
anleihe_wert = max(0.0, anleihe_2025 + anleihe_netto_2026)
anleihe_anteil = anleihe_wert / (anleihe_wert + summe) if anleihe_wert + summe > 0 else 0.0

zahlen = {
    "geschaefte": sum(len(m["geschaefte"]) for m in trump["meldungen"]),
    "jahresmeldung": len(neu),
    "einzelmeldungen": sum(len(m["geschaefte"]) for m in trump["meldungen"] if m.get("datei") != JM_DATEI),
    "eingerechnet": len(mit_kurs),
    "anleihen": anleihen,
    "ohneKuerzel": ohne_kuerzel,
    "ohneKurs": ohne_kurs,
    "ohneBestand": ohne_bestand,
    "werte": len({h[1] for h in mit_kurs}),
}
heute = datetime.date.today().isoformat()
p = {
    "format": 1, "stand": heute,
    "stichtag": (datetime.date(1970, 1, 1) + datetime.timedelta(days=stichtag)).isoformat(),
    "tage": [d for d, _ in reihe], "werte": [v for _, v in reihe],
    "marken": marken,
    "bestand": [{"k": k, "g": g} for k, g in bestand],
    "anleihen": round(anleihe_anteil, 4),
    "zahlen": zahlen,
}
json.dump(p, open(ZIEL_P, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
print("Zahlen:", zahlen, "| sonstige (Tausch, ohne Datum):", sonstige, "| doppelte Zeilen der Einzelmeldungen:", zusammengefasst)
print("Verlauf:", len(reihe), "Handelstage,", p["stichtag"], "Index", reihe[-1][1], "| Bestand:", len(bestand), "Werte, Aktienwert",
      round(summe / 1e6, 1), "Mio. $ | Anleihen", round(anleihe_wert / 1e6, 1), "Mio. $ =", round(anleihe_anteil * 100, 1), "%")
print("Werte ohne Kurse:", len(ohne_kurs_werte), ohne_kurs_werte.most_common(15))
if wert_2025:
    print("Aktienwert Ende 2025 laut Rechnung:", round(wert_2025[1] / 1e6, 1), "Mio. $")
    teil6 = sum(((z["von"] + (z["bis"] or z["von"])) / 2) for z in jm["teil6"] if zu.get(z["n"], {}).get("art") in ("aktie", "etf") and z.get("von"))
    print("Aktien und Fonds Ende 2025 laut Teil 6 (Mitte der Spannen):", round(teil6 / 1e6, 1), "Mio. $")
