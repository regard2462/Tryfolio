# -*- coding: utf-8 -*-
"""Art des Wertpapiers (t) und Boersenkuerzel (k) fuer die Namen der Papiermeldungen.
Reihenfolge: Kuerzel im Namen selbst („- HACK“, „(GEV)“), Basiswert bei Optionen („CALL/JNJ ...“), von Hand
gepruefte Namen (kuerzel-hand.json), vorhandene Zuordnungen (gleicher bereinigter Name, dann gleicher Namenskern),
zuletzt die strenge Yahoo-Suche (suchen.py, eigener Zwischenspeicher). Anleihen, Vorzugsaktien, Hybridpapiere und
strukturierte Produkte („LINKED TO ...“) bekommen kein Kuerzel.
Schritt 1: python -I kuerzel_papier.py vorbereiten <namen-liste.json> <zuordnung.json> <namen-hand.json> <offen.json>
           (schreibt die offenen Namen fuer suchen.py)
Schritt 2: python -I suchen.py <offen.json> <suche-cache.json> <suche.json>
Schritt 3: python -I kuerzel_papier.py zusammen <namen-liste.json> <zuordnung.json> <namen-hand.json> <suche.json>
           <ziel.json>"""
import collections
import json
import os
import re
import sys

HIER = os.path.dirname(os.path.abspath(__file__))
ABK = json.load(open(os.path.join(HIER, "abkuerzungen.json"), encoding="utf-8"))
HAND = os.path.join(HIER, "kuerzel-hand.json")

ZUSATZ = set("""INC INCORPORATED CORP CORPORATION CO COS COMPANY COMPANIES LTD LIMITED PLC NV SA AG SE AB ASA LP LLC
HLDGS HOLDINGS HLDG HOLDING GROUP GRP THE NEW F CL CLASS A B C COM COMMON STK STOCK SHS SH ORD REIT N I DEL TR
ADR ADS SPONSORED SPON CAP INTL CMN""".split())

OPTION = re.compile(r"^\s*(CALL|PUT)\s*/\s*([A-Z][A-Z.]{0,5})\b|^\s*(CALL|PUT)\s*\(\s*([A-Z]{1,5})\s*\)", re.I)
STRUKTUR = re.compile(r"\bLINKED\s+TO\b|\bLINKED\b|\bSTRUCTURED\b|\bAUTOCALL|\bBUFFER(ED)?\b|\bCONTINGENT\b|"
                      r"\b[LU]I?N\S{0,3}\s+TO\s+(BASKET|S&P|SAP|S\s*AND\s*P|500|INDEX)|\bBASKET\s+OF\s+INDICES\b|"
                      r"\b[LU]I?N[KE?]\S{0,2}\s+TO\b|"
                      r"\bTO\s+(S&P|SAP)\s*500\b|\b[LU]I?NKE\S*\s+(S&P|SAP)\b", re.I)
INDEX_PAPIER = re.compile(r"[LU]\W?I?N\S{0,3}TO\b|\b(500|SOO|SO|SOD|SAP|S&P|IS&P)\s+INDEX\b", re.I)
# Tausch einer Aktie gegen Fondsanteile: das Geschaeft betrifft die Aktie vor „EXCHANGED FOR“
TAUSCH_GEGEN = re.compile(r"^(.*?)\s+EX\S{0,3}ANGED\s+FOR\b", re.I)
# Zinstermine der Kommunalanleihen am Namensende (JJ Januar/Juli, FA Februar/August, MS, AO, MN, JD)
ZINSTERMIN = re.compile(r"(\d{1,2}/\d{2}/\d{2,4}|%).*\b(JJ|FA|MS|AO|MN|JD)\s*$")
VORZUG = re.compile(r"\bHYBR(ID\b|\S{0,2}\s)|\bPERPETUAL\b|\bPERP\b|\bPFD\b|\bPREFERRED\b|\bPREF\b|\bDEP\s*SHS?\b|"
                    r"\bDEPOSITARY\s+SH", re.I)
STAAT = re.compile(r"\bTSY\b|TREASURY\b|\bTREAS\b|\bT[- ]?BILLS?\b|\bU\.?\s?S\.?\s+TREAS|\bUS\s+TREASURY|\bUNITED STATES TREAS",
                   re.I)
KOMMUNAL = re.compile(r"\bBE\W?[IRJN]\S{0,3}(?=[\s,.]|$)|\bINDE?PT\s+SCH|\bG\.?O\.?\b|\bGO\s+BDS?\b|\bREV\b|\bRV\b|\bSCH(OOL)?\s+DIST|\bSD\s*#|"
                      r"\bAUTH\b|\bCNTY\b|\bCOUNTY\b|\bMUNICIPAL\b|\bMUNI\b|\bTWP\b|\bDORM\b|\bFING\s+AGY|\bPORT\b|"
                      r"\bWTR\b|\bST\s+(FOR|VAR|SPL|HIGHER|PORT|DORM|GO)\b|\bSTATE\s+OF\b|\bHIGHER\s+ED\b|"
                      r"\bGEN\s+FUND\b|\bIMPT\b|\bUTIL\s+DIST|\bPUB\s+FIN|\bFIN\s+AUTH|\bHSG\b|\bSPL\s+TAX\b|"
                      r"\bCMNTY\b|\bISD\b|\bUSD\b\s*#|\bCITY\s+OF\b|\bCONVENTION\b|\bSTGO\b|\bAREA\s+SCH|\bREVS\b|"
                      r"\bDIST\b|\bSD\s+NO\b", re.I)
# eindeutige Merkmale, die auch neben CORP oder INC gelten (Krankenhaus- und Foerdergesellschaften der Kommunen)
KOMMUNAL_STARK = re.compile(r"\bCNTY\b|\bFIN\s+AUTH\b|\bHOSP\s+REV\b|\bFACS\s+CORP\s+REV\b|\bREVS?\b.*\bBDS\b", re.I)
# Bundesstaat ausgeschrieben oder alt abgekuerzt, dazu ein Anleihemerkmal (OSWEGO ILL BDS, SAN ANTONIO TEX TAX NTS)
STAATSNAME = re.compile(r"\b(TEX|OHIO|COLO|MASS|MICH|MINN|CALIF|FLA|ILL|IOWA|WIS|ARIZ|CONN|KANS|NEB|OKLA|TENN|HAWAII|"
                        r"ALASKA|FLORIDA|KENTUCKY|TEXAS|LOUISIANA|MARYLAND|COLUMBIA|INDIANA|UTAH|ORE|WASH)", re.I)
ANLEIHE_MERKMAL = re.compile(r"\b(BDS|DB|GO|NTS|REV|ROI)\b|%|\d{1,2}/\d{1,2}/\d{2,4}", re.I)
# Kommunalanleihe als „Stadt Bundesstaat …“ (ATLANTA GA, TULPEHOCKEN PA AREA SCH); NC fehlt, weil die OCR „INC“ oft
# als „NC“ liest, CO, DE, IN, ME, NV und OR sind auch gewoehnliche Woerter in Firmennamen
STAATEN = ("AL AK AZ AR CA CT FL GA HI ID IL IA KS KY LA MD MA MI MN MS MO MT NE NH NJ NM NY ND OH OK PA RI SC SD TN TX "
           "UT VT VA WA WV WI WY").split()
STADT_STAAT = re.compile(r"^[A-Z][A-Z.' ]*?\s(" + "|".join(STAATEN) + r")(\s|$)")
FIRMA = re.compile(r"\b(INC|CORP|CMN|PLC|LTD|HOLDINGS?|CO|COM|ADR|LLC|LP|TRUST|BANCORP)\b")
ANLEIHE = re.compile(r"\d+\.?\d*\s*%|\bDUE\b|\bBONDS?\b|\bBDS\b|\bNOTES?\b|\bNTS\b|\bSR\s+NT|\bDEB\b|\bFRN\b|"
                     r"\b\d{1,2}/\d{1,2}/\d{2,4}\b|\d\.\d+\d/\d{2}/20\d\d\b", re.I)
FONDS = re.compile(r"\bETF\b|\bISHARES\b|\bSPDR\b|\bVANGUARD\b|\bINVESCO\b|\bSELECT SECTOR\b|\bINDEX\b|\bFUNDS?\b|"
                   r"\bFDS\b|\bTR\b.*\b(YR|YEARS?|SHS|ETF)\b|\bPROSHARES\b|\bWISDOMTREE\b|\bGLOBAL X\b|\bAMPLIFY\b|"
                   r"\bSCHWAB\s+(US|U\.S\.|STR|INTL)|\bFIRST TRUST\b|\bVANECK\b|\bQQQ\b|\bPORTFOLIO\b", re.I)
PRIVAT = re.compile(r"\bLP\b|\bL\.P\.|\bPRIVATE\b|\bPARTNERS\b|\bINVESTMENTS\s+LP\b|\bALLOCATE\b|\bOPP\s+FUND\b|"
                    r"\bCREDIT\s+FUND\b|\bPRIVATE\)", re.I)
IM_NAMEN = [re.compile(r"\(([A-Z]{1,5}(?:\.[A-Z])?)\)\s*$"), re.compile(r"\s-\s*([A-Z]{1,5})\s*$"),
            re.compile(r"[a-z]\s*-\s*([A-Z]{2,5})\s*$"), re.compile(r"\bYr-\s*([A-Z]{2,5})\s*$", re.I)]


# Zusaetze der Maklerbezeichnungen, die nicht zum Firmennamen gehoeren
FUELL = {"CMN", "AVIN", "COM", "COMMON", "STOCK", "SHS", "SH", "CL", "CLASS", "DELAWARE", "DEL", "REG", "NEW", "ORD",
         "ADR", "ADS", "SPONSORED", "SPON", "VOTING", "NV", "EXCHANGED", "FOR", "ISIN"}


HAND_NORM = {}


def norm(n):
    """Suchname: Grossbuchstaben ohne Satzzeichen, ohne Maklerzusaetze (CMN, CLASS A, (DELAWARE), USD0.01 ...)."""
    n = (n or "").upper().replace("&", " AND ")
    n = re.sub(r"\b(USD|EUR|GBP|CHF)\s?[\dOI][\dOI.]*", " ", n)
    n = re.sub(r"(INC|CORP|CO)(CMN|COM)\b", r"\1", n)
    n = re.sub(r"\bINCTTHE\b", "INC", n)
    n = re.sub(r"\bPV\s?\$?\d[\d.]*", " ", n)
    n = re.sub(r"\bINC\s*/\s*THE\b", "INC", n)
    n = re.sub(r"\(THE\)", " ", n)
    w = re.sub(r"[^A-Z0-9]+", " ", n).split()
    w = [x for x in w if x not in FUELL]
    while w and w[-1] in ("A", "B", "C"):
        w = w[:-1]
    return " ".join(w)


def kern(n):
    w = [ABK.get(x, x) for x in norm(n).split()]
    return " ".join(sorted({x for x in w if x and x not in ZUSATZ and x != "AND"}))


def art(name):
    """Typ aus dem Namen: OP (Option), OT (strukturiert, Vorzug, Hybrid), MB (Kommunalanleihe), EF (Fonds, ETF),
    GS (Staatsanleihe), CS (Unternehmensanleihe), sonst ST."""
    if OPTION.search(name):
        return "OP"
    if STRUKTUR.search(name) or VORZUG.search(name):
        return "OT"
    # an den S&P 500 gebundene Bankpapiere mit verlesenem „LINKED TO“ („UNKEDTO SOO INDEX“), keine Indexfonds
    if INDEX_PAPIER.search(name) and not re.search(r"\b(FUNDS?|FDS|FD|ETF|ADMIRAL|VANGUARD|SPDR|ISHARES)\b", name, re.I):
        return "OT"
    if re.search(r"\b(ETF|SPDR|ISHARES|PROSHARES|VANGUARD|INVESCO|FDS)\b", name, re.I):
        return "EF"
    if KOMMUNAL.search(name) and not re.search(r"\b(INC|CORP|CMN|PLC)\b", name, re.I):
        return "MB"
    if KOMMUNAL_STARK.search(name):
        return "MB"
    if re.search(r"\b(ETF|FUNDS?|FDS)\b", name, re.I):
        return "EF"
    gross = name.upper()
    w = gross.split()
    m = STADT_STAAT.search(gross)
    # Unternehmensanleihen der Maklertabellen enden auf die Waehrung („AEP TEXAS 4.7% 05/15/2032 USD“)
    firma = FIRMA.search(gross) or re.search(r"\b(USD|USO|N\.V\.)\s*$|\bN\.V\.", gross)
    if m and not firma and len(w) <= 8 and (any(x == m.group(1) for x in w[1:3]) or (
            any(x == m.group(1) for x in w[3:4]) and ANLEIHE_MERKMAL.search(gross))):
        return "MB"
    if STAATSNAME.search(gross) and ANLEIHE_MERKMAL.search(gross) and not firma and not STAAT.search(gross):
        return "MB"
    if STAAT.search(name):
        return "GS"
    if ZINSTERMIN.search(name) and not re.search(r"\b(INC|PLC|LLC|LTD|CMN)\b", name, re.I):
        return "MB"
    if ANLEIHE.search(name) and not re.search(r"\bCMN\b", name, re.I):
        return "CS"
    if FONDS.search(name):
        return "EF"
    return "ST"


KEIN_KUERZEL = {"II", "III", "IV", "VI", "VII", "LP", "LLC", "ETF", "INC", "CO", "NA", "ADR", "SHS", "USD", "PLC", "NV",
                "SA", "AG", "SE", "LTD", "TR", "FD", "FDS", "BE", "GO", "RV", "CMN", "COM", "CL", "THE", "CLASS",
                "CORP", "NEW", "DEL", "ORD", "REIT", "SPON", "UNIT", "UNITS", "STOCK", "FUND", "INDEX", "AVIN",
                # Lesarten von CMN, PLC und INC
                "CNN", "CMM", "CWN", "CW", "CM", "AQN", "PCC", "PTC", "NC"}


def kuerzel_im_namen(name):
    m = OPTION.search(name)
    if m:
        return (m.group(2) or m.group(4)).upper().rstrip(".")
    for rx in IM_NAMEN:
        m = rx.search(name.strip())
        if m and m.group(1).upper() not in KEIN_KUERZEL:
            return m.group(1).upper()
    # Formular in gemischter Schreibweise mit dem Kuerzel als letztem Wort in Grossbuchstaben („Global X Uranium URA“)
    # (nur bei Namen, die wirklich klein geschrieben sind: einzelne Kleinbuchstaben sind meist Lesefehler)
    w = name.strip().split()
    buchst = [ch for ch in " ".join(w[:-1]) if ch.isalpha()]
    klein = sum(ch.islower() for ch in buchst) / len(buchst) if buchst else 0.0
    if len(w) >= 3 and klein >= 0.3 and re.fullmatch(r"[A-Z]{2,5}", w[-1]) \
            and w[-1] not in KEIN_KUERZEL and (not re.fullmatch(r"[A-Z]{2,5}", w[-2]) or w[-2] == "ETF"):
        return w[-1]
    # boersengehandelte Fonds in Grossbuchstaben mit dem Kuerzel am Ende („SPDR SER TR 500 ETF SPYG“)
    if len(w) >= 3 and re.search(r"\b(ETF|SPDR|ISHARES)\b", name, re.I) and re.fullmatch(r"[A-Z]{3,5}", w[-1]) \
            and w[-1] not in KEIN_KUERZEL and w[-1] not in ("SPDR", "ETF", "INSTL", "TRUST", "SHARES", "MSCI"):
        return w[-1]
    m = re.search(r"\bETF\s*[-,]\s*([A-Z]{3,5})\s*$|[a-z],([A-Z]{3,5})\s*$", name)
    if m:
        return m.group(1) or m.group(2)
    return None


def lesen(p, leer):
    return json.load(open(p, encoding="utf-8")) if p and os.path.exists(p) else leer


def tabellen(zuordnung, namen_hand):
    genau, nach_kern = collections.defaultdict(set), collections.defaultdict(set)
    for quelle in (zuordnung, namen_hand):
        for n, e in quelle.items():
            if e.get("k") and e.get("art") != "anleihe":
                genau[norm(n)].add(e["k"])
                if kern(n):
                    nach_kern[kern(n)].add(e["k"])
    return genau, nach_kern


def ohne_netz(name, t, hand, genau, nach_kern):
    if name in hand:
        return hand[name].get("k"), hand[name].get("t", t), "hand"
    hn = HAND_NORM.get(norm(name))
    if hn is not None:
        return hn.get("k"), hn.get("t", t), "hand"
    if t in ("OT", "GS", "MB", "CS"):
        return None, t, "art"
    k = kuerzel_im_namen(name)
    if k:
        return k, t, "im-namen"
    if len(genau.get(norm(name), ())) == 1:
        return next(iter(genau[norm(name)])), t, "zuordnung"
    kk = kern(name)
    if kk and len(nach_kern.get(kk, ())) == 1:
        return next(iter(nach_kern[kk])), t, "zuordnung-kern"
    return None, t, None


def woerter(n):
    return [w for w in re.sub(r"[^A-Z0-9&]+", " ", n.upper()).split() if not w.isdigit()]


KLASSE = re.compile(r"\bCL(?:ASS)?[\s.\-]*([ABC])\b", re.I)
# Woerter, die auf ein Papier des Emittenten statt auf die Aktie deuten
EMITTENT = {"FINANCE", "CREDIT", "NOTE", "NOTES", "BOND", "BONDS", "DEB", "MTN", "SENIOR", "CONVENTION", "PREF"}


def praefix_ergaenzen(aus):
    """Aktien ohne Kuerzel, deren erste zwei Woerter (je mindestens drei Zeichen) genau mit denen eines sicher
    zugeordneten Aktiennamens uebereinstimmen („WALT DISNEY MPANY (THE)“ wie „WALT DISNEY COMPANY (THE)“), bekommen
    dessen Kuerzel, wenn alle so beginnenden Namen dasselbe Kuerzel haben und keine abweichende Aktienklasse
    genannt ist. Verstuemmelte Rechtsform-Zusaetze am Ende sind der haeufigste Grund, warum der Name sonst offen bleibt."""
    gruppe = collections.defaultdict(lambda: collections.defaultdict(set))
    for n, v in aus.items():
        if v["k"] and v["t"] == "ST" and v["quelle"] in ("hand", "zuordnung", "zuordnung-kern", "yahoo-suche"):
            w = woerter(n)
            if len(w) >= 2:
                kl = KLASSE.search(n)
                gruppe[tuple(w[:2])][v["k"]].add(kl.group(1).upper() if kl else "")
    for n, v in aus.items():
        if v["k"] or v["t"] != "ST" or PRIVAT.search(n):
            continue
        w = woerter(n)
        if len(w) < 2 or len(w[0]) < 3 or len(w[1]) < 3 or EMITTENT & set(w[2:]):
            continue
        g = gruppe.get(tuple(w[:2]))
        if not g or len(g) != 1:
            continue
        k, klassen = next(iter(g.items()))
        kl = KLASSE.search(n)
        if kl and (klassen - {""}) and kl.group(1).upper() not in klassen:
            continue
        v["k"], v["quelle"] = k, "praefix"


def basisname(name, hand):
    """Name, nach dem Art und Kuerzel bestimmt werden: bei „X EXCHANGED FOR Y“ das hingegebene Papier X."""
    if name in hand:
        return name
    m = TAUSCH_GEGEN.search(name)
    return m.group(1).strip() if m and m.group(1).strip() else name


# Aktien mit mehreren Gattungen: Kuerzel je genannter Klasse
FAMILIEN = [{"A": "GOOGL", "C": "GOOG"}, {"A": "NWSA", "B": "NWS"}, {"A": "FOXA", "B": "FOX"},
            {"A": "BRK-A", "B": "BRK-B"}, {"A": "BF-A", "B": "BF-B"}]
KLASSE_GENANNT = re.compile(r"\bCL(?:ASS)?[\s.\-]*([ABC])\b", re.I)


def klasse_angleichen(aus):
    """Nennt der Name eine Aktienklasse (CLASS C, CL A, „ALPHABET INC. CMN C“), bekommt er das Kuerzel dieser
    Klasse, auch wenn die Zuordnung ueber einen Namen ohne Klasse kam."""
    for n, v in aus.items():
        if not v["k"] or v["t"] != "ST":
            continue
        fam = next((f for f in FAMILIEN if v["k"] in f.values()), None)
        if not fam:
            continue
        kl = KLASSE_GENANNT.findall(n)
        if kl:
            klasse = kl[-1].upper()
        else:
            m = re.search(r"[\s\-]([ABC])\s*$", n)
            klasse = m.group(1) if m else None
        if klasse in fam and fam[klasse] != v["k"]:
            v["k"], v["quelle"] = fam[klasse], v["quelle"] + "+klasse"


def main():
    modus = sys.argv[1]
    namen = lesen(sys.argv[2], {})
    zuordnung = lesen(sys.argv[3], {})
    namen_hand = lesen(sys.argv[4], {})
    hand = lesen(HAND, {})
    for n, e in hand.items():
        HAND_NORM[norm(n)] = e
    genau, nach_kern = tabellen(zuordnung, namen_hand)
    if modus == "vorbereiten":
        offen = {}
        for name, anzahl in namen.items():
            n = basisname(name, hand)
            k, t, q = ohne_netz(n, art(n), hand, genau, nach_kern)
            if q is None:
                offen[norm(n)] = offen.get(norm(n), 0) + anzahl
        json.dump({"offen": offen}, open(sys.argv[5], "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        print("offen fuer die Suche:", len(offen))
        return
    suche = lesen(sys.argv[5], {"treffer": {}})["treffer"]
    aus = {}
    for name, anzahl in namen.items():
        n = basisname(name, hand)
        t = art(n)
        k, t, q = ohne_netz(n, t, hand, genau, nach_kern)
        if q is None:
            tr = suche.get(norm(n))
            if tr:
                k, q = tr["k"], "yahoo-suche"
        if t == "ST" and k and q == "yahoo-suche" and FONDS.search(n):
            t = "EF"
        # nicht boersennotierte Beteiligungen (Partnerships, Privatfonds) ohne Kuerzel
        if not k and t in ("ST", "EF") and PRIVAT.search(name):
            t = "OT"
        aus[name] = {"k": k, "t": t, "quelle": q or "ohne"}
    praefix_ergaenzen(aus)
    klasse_angleichen(aus)
    json.dump(aus, open(sys.argv[6], "w", encoding="utf-8"), ensure_ascii=False, indent=0)
    c = collections.Counter(v["quelle"] for v in aus.values())
    mit = sum(namen[n] for n, v in aus.items() if v["k"])
    print("Namen:", len(aus), dict(c), "| Geschaefte mit Kuerzel:", mit, "von", sum(namen.values()))


if __name__ == "__main__":
    main()
