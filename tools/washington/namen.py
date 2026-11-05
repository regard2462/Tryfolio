# Firmennamen aus dem Katalog mit Namen in Behoerdendaten abgleichen (Lobbying, Entlassungsmeldungen).
import json
import re

# Rechtsformen und Zusaetze, die am Ende eines Firmennamens stehen duerfen
ZUSAETZE = {
    "INC", "INCORPORATED", "CORP", "CORPORATION", "CO", "COMPANY", "COMPANIES", "LLC", "LP", "LTD", "LIMITED", "PLC",
    "HOLDINGS", "HOLDING", "GROUP", "THE", "USA", "US", "AMERICA", "NORTH", "AMERICAS", "AND", "ITS", "AFFILIATES",
    "SUBSIDIARIES", "SUBSIDIARY", "SERVICES", "NA", "N", "A", "SA", "AG", "NV", "SE", "INTERNATIONAL", "ENTERPRISES",
    "OF", "TECHNOLOGIES", "SYSTEMS", "CLASS", "B", "COM", "DBA", "OPERATIONS", "STORES", "LOGISTICS", "CLIENT", "VARIOUS",
}

# Firmen, die in Behoerdendaten anders heissen als im Katalog
ANDERE_NAMEN = {
    "GOOGL": ["GOOGLE"], "GOOG": ["GOOGLE"], "META": ["FACEBOOK", "META PLATFORMS"], "BRK-B": ["BERKSHIRE HATHAWAY"],
    "BRK-A": ["BERKSHIRE HATHAWAY"], "MMM": ["3M"], "UNH": ["UNITEDHEALTH GROUP", "UNITEDHEALTHCARE"],
    "JPM": ["JPMORGAN CHASE", "JP MORGAN CHASE"], "GS": ["GOLDMAN SACHS"], "BAC": ["BANK OF AMERICA"],
    "T": ["AT&T"], "KO": ["COCA-COLA", "THE COCA-COLA COMPANY"], "PG": ["PROCTER & GAMBLE", "PROCTER AND GAMBLE"],
    "JNJ": ["JOHNSON & JOHNSON"], "MCD": ["MCDONALD'S", "MCDONALDS"], "WMT": ["WALMART", "WAL-MART"],
}


def grundform(name):
    n = name.upper().replace("&AMP;", "&")
    n = re.sub(r"\([^)]*\)", " ", n)
    n = n.replace("’", "'").replace(".", " ").replace(",", " ")
    n = re.sub(r"[^A-Z0-9&' -]", " ", n)
    return re.sub(r"\s+", " ", n).strip()


def stamm(name):
    w = grundform(name).split(" ")
    while len(w) > 1 and w[-1] in ZUSAETZE:
        w.pop()
    return " ".join(w)


def katalog_us(pfad):
    k = json.load(open(pfad, encoding="utf-8"))
    aus = {}
    for a in k["aktien"]:
        if a.get("c") != "US":
            continue
        namen = {stamm(a["n"])} | {stamm(x) for x in ANDERE_NAMEN.get(a["s"], [])}
        aus[a["s"]] = {"name": a["n"], "staemme": sorted(n for n in namen if len(n) >= 2)}
    return aus


def index_aufbauen(firmen):
    # Stamm -> Kuerzel; mehrdeutige Staemme (Alphabet A und C) behalten alle
    idx = {}
    for s, f in firmen.items():
        for st in f["staemme"]:
            idx.setdefault(st, []).append(s)
    return idx


def treffer(name, idx):
    # Der Name passt, wenn er nach Abzug der Zusaetze genau einem Stamm entspricht oder mit einem Stamm beginnt und
    # danach nur Zusaetze folgen. Kurze Staemme (bis 3 Zeichen) muessen genau passen.
    w = grundform(name).split(" ")
    while len(w) > 1 and w[-1] in ZUSAETZE:
        w.pop()
    s = " ".join(w)
    if s in idx:
        return idx[s]
    for i in range(len(w) - 1, 0, -1):
        kopf = " ".join(w[:i])
        if len(kopf) > 3 and kopf in idx and all(x in ZUSAETZE for x in w[i:]):
            return idx[kopf]
    return []
