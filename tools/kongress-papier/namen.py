# -*- coding: utf-8 -*-
"""Bereinigt offensichtliche OCR-Fehler in den gelesenen Namen. Grundlage ist ein Wortschatz aus den vorhandenen
Zuordnungen (Namen im selben Maklerstil) und den Namen aller Papiermeldungen selbst. Ein unbekanntes Wort wird nur
ersetzt, wenn ein bekanntes Wort sehr nahe liegt: typische Verwechslungen (I/L/1, O/0, U fuer LI, RN fuer M ...)
kosten wenig, echte Buchstabentausche viel. Ein sauber gelesenes Wort aus Grossbuchstaben (etwa AUTONATION) bleibt,
wenn es nur ueber einen echten Tausch zu einem anderen Wort passt. Nachbarwoerter, die in bekannten Namen zusammen
vorkommen, entscheiden zwischen gleich nahen Kandidaten. Zusammengewachsene lange Woerter werden getrennt, wenn
beide Teile bekannt sind. Woerter mit Ziffern und Kuerzel hinter „ - “ bleiben unveraendert.
Aufruf: python -I namen.py <meldungen.json> <cache-ordner> <zuordnung.json> <namen-hand.json> <ziel.json> [<hand.json>]
Mit hand.json werden Namen mit unbekannten Woertern zusaetzlich an gepruefte Namen (Handpruefung) und haeufig
sauber gelesene Namen angeglichen, wenn genau ein solcher Name sehr aehnlich ist (siehe angleichen).
Schreibt {"<id>/<seite>/<zeile>": bereinigter Name}; je Zeile wird unter den OCR-Lesarten (Spaltenstreifen,
Zellenblaetter) die mit den meisten bekannten Woertern gewaehlt."""
import collections
import difflib
import json
import math
import os
import re
import sys
import unicodedata

GRUPPEN = ["IL1TJ!|]", "O0QD", "S5$", "B8", "Z2", "G6", "A4", "EF", "UV", "C(", "NH", "RP", "VY", "KX"]
NAH = {}
for g in GRUPPEN:
    for a in g:
        for b in g:
            if a != b:
                NAH[(a, b)] = 0.3
MEHR = {("U", "LI"): 0.35, ("LI", "U"): 0.35, ("M", "RN"): 0.4, ("RN", "M"): 0.4, ("W", "VV"): 0.4, ("VV", "W"): 0.4,
        ("D", "CL"): 0.5, ("CL", "D"): 0.5, ("H", "LI"): 0.5, ("LI", "H"): 0.5, ("N", "IV"): 0.5, ("M", "IVI"): 0.5,
        ("K", "IC"): 0.5, ("UI", "LL"): 0.5, ("LL", "U"): 0.4, ("U", "LL"): 0.4, ("IU", "LL"): 0.4}
UNBEKANNT = "�"
FREMD = str.maketrans({"•": ".", "·": ".", "„": ",", "‚": ",", "—": "-", "–": "-",
                       "‘": "'", "’": "'", "“": '"', "”": '"', " ": " "})
MEHR_NACH_ANFANG = {}
for (_x, _y), _c in MEHR.items():
    MEHR_NACH_ANFANG.setdefault((_x[0], _y[0]), []).append((_x, _y, _c))
WORT = re.compile(r"[A-Z0-9&'\-" + UNBEKANNT + "]+")


def kosten(a, b, streng=False):
    """Gewichteter Abstand zweier Woerter (Grossbuchstaben). Ein Zeichen am Wortanfang zu viel (Rahmenrest) kostet
    0,5, Satzzeichen zu viel 0,3, ein unlesbares Zeichen passt zu jedem Buchstaben."""
    n, m = len(a), len(b)
    INF = 99.0
    d = [[INF] * (m + 1) for _ in range(n + 1)]
    d[0][0] = 0.0
    for i in range(n + 1):
        for j in range(m + 1):
            v = d[i][j]
            if v >= INF:
                continue
            if i < n:
                if not a[i].isalnum():
                    c = 0.3
                elif a[i] == UNBEKANNT:
                    c = 0.4
                elif i == 0 and j == 0:
                    c = 0.8 if streng else 0.5
                else:
                    c = 1.0
                if d[i + 1][j] > v + c:
                    d[i + 1][j] = v + c
            if j < m and d[i][j + 1] > v + 1.0:
                d[i][j + 1] = v + 1.0
            if i < n and j < m:
                if a[i] == b[j]:
                    c = 0.0
                elif a[i] == UNBEKANNT:
                    c = 0.2
                else:
                    c = NAH.get((a[i], b[j]), 1.0)
                if d[i + 1][j + 1] > v + c:
                    d[i + 1][j + 1] = v + c
            if i >= n or j >= m:
                continue
            for x, y, c in MEHR_NACH_ANFANG.get((a[i], b[j]), ()):
                if a.startswith(x, i) and b.startswith(y, j):
                    ii, jj = i + len(x), j + len(y)
                    if d[ii][jj] > v + c:
                        d[ii][jj] = v + c
    return d[n][m]


def woerter(name):
    return WORT.findall(name.upper())


def kern_teile(tok):
    m = re.match(r"^([^A-Za-z0-9&" + UNBEKANNT + r"]*)(.*?)([^A-Za-z0-9" + UNBEKANNT + r"]*)$", tok)
    return m.group(1), m.group(2), m.group(3)


def sauber(kern):
    """Wort ohne Anzeichen eines Lesefehlers: nur Grossbuchstaben (oder nur Kleinbuchstaben nach dem ersten)."""
    return bool(re.fullmatch(r"[A-Z][A-Z&'\-]*", kern) or re.fullmatch(r"[A-Z][a-z&'\-]+", kern))


CMN = re.compile(r"^[A-Za-z0-9.,'`()\-{}" + UNBEKANNT + r"]{1,5}[Nn]$")


class Wortschatz:
    def __init__(self, fremd, eigen, paare):
        self.anzahl = collections.Counter()
        self.anzahl.update(fremd)
        self.anzahl.update(eigen)
        self.paare = paare
        # Die Zuordnungen stammen teils selbst aus gescannten Meldungen: Woerter, die dort mindestens zweimal
        # vorkommen, gelten als sicher (bekannt); einmalige sind nur Kandidaten (schwach).
        self.bekannt = set(w for w in fremd if len(w) >= 2 and fremd[w] >= 2)
        self.schwach = set(w for w in fremd if len(w) >= 2 and fremd[w] < 2)
        self.fremd_anzahl = dict(fremd)
        nach_laenge = collections.defaultdict(list)
        for w in self.bekannt | self.schwach:
            nach_laenge[len(w)].append(w)
        # eigene Woerter gelten als bekannt, wenn sie oft gleich gelesen werden und kein Wort der Zuordnungen
        # (oder ein viel haeufigeres eigenes) ganz nahe liegt
        for w, k in sorted(eigen.items(), key=lambda kv: -kv[1]):
            if w in self.bekannt or w in self.schwach or k < 4 or not re.fullmatch(r"[A-Z&'\-]+", w):
                continue
            bi = {w[i:i + 2] for i in range(len(w) - 1)}
            nahe = [x for L in range(len(w) - 1, len(w) + 2) for x in nach_laenge.get(L, ())
                    if len(bi & {x[i:i + 2] for i in range(len(x) - 1)}) >= max(1, len(bi) // 3)
                    and ((x in fremd and kosten(w, x) <= 0.5) or (self.anzahl[x] >= 3 * k and kosten(w, x) <= 1.0))]
            if not nahe:
                self.bekannt.add(w)
                nach_laenge[len(w)].append(w)
        self.nach_laenge = nach_laenge
        self.merk = {}
        self.merk_k = {}
        self.bigramme = {}

    def kandidaten(self, w, grenze, streng):
        schl = (w, round(grenze, 2), streng)
        if schl not in self.merk_k:
            self.merk_k[schl] = self._kandidaten(w, grenze, streng)
        return self.merk_k[schl]

    def _kandidaten(self, w, grenze, streng):
        bi = {w[i:i + 2] for i in range(len(w) - 1)}
        noetig = max(1, len(bi) // 3) if len(w) > 5 else (1 if len(w) >= 3 else 0)
        aus = []
        for L in range(max(2, len(w) - 2), len(w) + 3):
            for x in self.nach_laenge.get(L, ()):
                if x == w:
                    continue
                bx = self.bigramme.get(x)
                if bx is None:
                    bx = self.bigramme[x] = {x[i:i + 2] for i in range(len(x) - 1)}
                if noetig and len(bi & bx) < noetig:
                    continue
                c = kosten(w, x, streng)
                if c <= grenze:
                    aus.append((c, x))
        return aus

    def bester(self, w, vor, nach, streng):
        schl = (w, vor, nach, streng)
        if schl not in self.merk:
            self.merk[schl] = self._bester(w, vor, nach, streng)
        return self.merk[schl]

    def _bester(self, w, vor, nach, streng):
        if w in self.schwach:
            # einmal in den Zuordnungen: nur durch ein sicheres, haeufigeres Wort in Verwechslungsnaehe ersetzen
            kand = [(c, x) for c, x in self.kandidaten(w, 0.4, True)
                    if x in self.bekannt and self.fremd_anzahl.get(x, 0) >= 2 * self.fremd_anzahl.get(w, 0)]
            return min(kand)[1] if len(kand) == 1 else None
        grenze = 0.7 if streng else (0.8 if len(w) <= 4 else (1.2 if len(w) <= 7 else 1.7))
        kand = self.kandidaten(w, grenze if streng else grenze + 0.45, streng)
        if not kand:
            return None
        wert = []
        for c, x in kand:
            kontext = 0.0
            if vor and self.paare.get((vor, x)):
                kontext += 0.4
            if nach and self.paare.get((x, nach)):
                kontext += 0.4
            if c - min(kontext, 0.45) > grenze:
                continue
            s = c - kontext - 0.1 * math.log10(1 + self.anzahl[x])
            wert.append((s, c, x))
        if not wert:
            return None
        wert.sort()
        if len(wert) > 1 and wert[1][0] - wert[0][0] < 0.08:
            return None
        return wert[0][2]

    def name(self, roh):
        # Umlaute und Akzente der deutschen OCR-Sprache zurueck in Grundbuchstaben
        n = unicodedata.normalize("NFKD", roh.replace("ß", "SS"))
        n = "".join(ch for ch in n if not unicodedata.combining(ch))
        # typische Fremdzeichen der OCR: Punkte und Striche in ASCII, alles andere als unlesbares Zeichen
        n = n.translate(FREMD)
        n = "".join(ch if ord(ch) < 128 else UNBEKANNT for ch in n)
        n = re.sub(r"\s+", " ", n.strip())
        n = re.sub(r"^[\[\]|!Il1i:;.,'`]\s+(?=\S{3,})", "", n)
        # CLASS-Zusatz in seinen Lesarten
        n = re.sub(r"(?i)\b\S{0,3}[AU" + UNBEKANNT + r"][S5$]{2}\s?([ABC])$", r"CLASS \1", n)
        n = re.sub(r"(?i)(CMN|INC\.?|CORP\.?)\s+(?!CMN)\S{1,3}\s+[AU" + UNBEKANNT + r"]?[S5$G]{2}\s?([ABC])$",
                   r"\1 CLASS \2", n)
        n = re.sub(r"(?i)\bCLASS([ABC])\b", r"CLASS \1", n)
        n = re.sub(r"(?i)\bCLAS ([ABC])$", r"CLASS \1", n)
        # „INC“ mit verlesenem I („'NC“, „(NC“, „[NC“, „$NC“) und „NC“ vor den Maklerzusaetzen
        n = re.sub(r"(?<=\S)\s[(\[{'$)|!]NC\b\.?", " INC", n)
        n = re.sub(r"(?<=\s)[Il1|]\s+NC\b", "INC", n)
        n = re.sub(r"(?<=\s)NC\.?(?=\s+(CMN|COM|CLASS|AVIN|CL)\b)", "INC", n)
        toks = n.split(" ")
        buchst = [ch for ch in n if ch.isalpha()]
        gross_name = bool(buchst) and sum(ch.isupper() for ch in buchst) >= 0.7 * len(buchst)
        aus = []
        for i, tok in enumerate(toks):
            vor_w, kern, nach_w = kern_teile(tok)
            if not kern:
                aus.append(tok)
                continue
            g = kern.upper()
            ziffern = sum(ch.isdigit() for ch in g)
            letztes = i == len(toks) - 1 or (i + 2 < len(toks) and toks[i + 1].upper() == "CLASS") or \
                (i + 1 < len(toks) and toks[i + 1].upper() == "CLASS")
            hinter_strich = i >= 1 and toks[i - 1] in ("-",) or re.match(r"^-[A-Z]{2,5}$", tok)
            vor_class = i + 1 < len(toks) and toks[i + 1].upper() == "CLASS"
            if letztes and gross_name and g not in self.bekannt and (
                    CMN.match(tok) or (vor_class and len(kern) <= 5 and not sauber(kern))):
                aus.append("CMN")
                continue
            if g in self.bekannt and gross_name and not hinter_strich:
                aus.append(vor_w + g + nach_w)
                continue
            if g in self.bekannt or hinter_strich or "&" in g or ziffern >= 2 or (ziffern and len(g) <= 3) \
                    or len(g) <= 1 or re.search(r"[^A-Z0-9'\-" + UNBEKANNT + r"]", g) \
                    or (sauber(kern) and len(g) <= 3) or re.fullmatch(r"[A-Z]+'S", g):
                aus.append(tok)
                continue
            vor = woerter(toks[i - 1])[-1] if i and woerter(toks[i - 1]) else None
            nach = woerter(toks[i + 1])[0] if i + 1 < len(toks) and woerter(toks[i + 1]) else None
            b = self.bester(g, vor, nach, streng=sauber(kern))
            if b is None and len(g) >= 9:
                for k in range(3, len(g) - 2):
                    links, rechts = g[:k], g[k:]
                    if links in self.bekannt and rechts in self.bekannt and self.paare.get((links, rechts)):
                        b = links + " " + rechts
                        break
            if b is None:
                aus.append(tok)
                continue
            if not gross_name and kern[:1].isupper() and kern[1:].islower():
                b = " ".join(x.capitalize() for x in b.split(" "))
            elif not gross_name and kern.islower():
                b = b.lower()
            aus.append(vor_w + b + nach_w)
        n = " ".join(aus)
        n = re.sub(r"\bCMN CMN\b", "CMN", n)
        return n.strip()


def guete(ws, name):
    """Wertung einer Lesart: bekannte Woerter zaehlen positiv, unbekannte und fremde Zeichen negativ."""
    p = 0.0
    for w in woerter(name):
        if w in ws.bekannt or w in ws.schwach or any(ch.isdigit() for ch in w):
            p += 1.0 + 0.02 * len(w)
        else:
            p -= 0.5 if len(w) <= 2 else 1.0
    p -= 0.5 * len(re.findall(r"[^A-Za-z0-9 &.,'()/\-%@$#+:]", name))
    return p


def anderes_wort(ws, b, h):
    """True, wenn die Angleichung von b an h ein echtes Wort der Zuordnungen durch ein anderes ersetzt (MSCI zu
    MKS) oder die Aktienklasse aendert (CL A zu CL C); solche Lesarten bleiben, wie sie sind."""
    wb, wh = [w.upper() for w in woerter(b)], [w.upper() for w in woerter(h)]
    for op, i1, i2, j1, j2 in difflib.SequenceMatcher(None, wb, wh).get_opcodes():
        if op == "equal":
            continue
        alt, neu = wb[i1:i2], wh[j1:j2]
        if "".join(alt) == "".join(neu):
            continue
        if any(w in ("A", "B", "C") for w in alt) and any(w in ("A", "B", "C") for w in neu):
            return True
        # ein gelesenes „&“ ist fast nie ein Lesefehler, ein „A“ an seiner Stelle oft (SS&C, nicht SSAC)
        if any("&" in w and len(w) <= 5 for w in alt) and "&" not in "".join(neu):
            return True
        # echtes Wort: in den Zuordnungen oder in mindestens zwei Zeilen gelesen (einmalige Lesungen sind meist
        # OCR-Fehler und duerfen angeglichen werden)
        if any(len(w) >= 3 and w.isalpha() and ws.anzahl.get(w, 0) >= 2 for w in alt):
            k = kosten("".join(alt), "".join(neu))
            if k >= 2.5 and k >= 0.5 * len("".join(neu)):
                return True
    return False


def _normname(n):
    return " ".join(re.sub(r"[^A-Z0-9&]+", " ", n.upper()).split())


def _ziffern(n):
    """Reine Zahlwoerter eines Namens (Ziffern in gemischten Woertern sind oft Lesefehler)."""
    return [w for w in _normname(n).split() if w.isdigit()]


def angleichen(aus, ziele, ws):
    """Namen mit unbekannten Woertern an einen Zielnamen angleichen, wenn: gleiche Zahlwoerter, alle bekannten
    Woerter und alle Einzelbuchstaben des Namens kommen im Ziel vor, Aehnlichkeit (difflib) ab 0,85 und der
    zweitbeste Zielname liegt mindestens 0,05 darunter."""
    ziel_info = []
    for t in ziele:
        nt = _normname(t)
        ziel_info.append((t, nt, set(nt.split()), _ziffern(t)))
    nach_wort = collections.defaultdict(list)
    for i, (t, nt, tw, tz) in enumerate(ziel_info):
        for w in tw:
            nach_wort[w].append(i)
    n_geaendert = 0
    merk = {}
    for schl, b in list(aus.items()):
        if b in merk:
            if merk[b]:
                aus[schl] = merk[b]
                n_geaendert += 1
            continue
        merk[b] = None
        nb = _normname(b)
        bw = nb.split()
        if not bw or b in ziele:
            continue
        if all(w in ws.bekannt or any(ch.isdigit() for ch in w) for w in bw):
            continue
        pflicht = [w for w in bw if (w in ws.bekannt and len(w) >= 2) or len(w) == 1 or w.isdigit()]
        bz = _ziffern(b)
        kand = set()
        for w in bw:
            kand.update(nach_wort.get(w, ()))
        werte = []
        for i in kand:
            t, nt, tw, tz = ziel_info[i]
            if tz != bz or abs(len(nt) - len(nb)) > 12:
                continue
            if not all(w in tw for w in pflicht):
                continue
            werte.append((difflib.SequenceMatcher(None, nb, nt).ratio(), t))
        werte.sort(reverse=True)
        if werte and werte[0][0] >= 0.85 and (len(werte) == 1 or werte[1][0] <= werte[0][0] - 0.05) \
                and not anderes_wort(ws, b, werte[0][1]):
            merk[b] = werte[0][1]
            aus[schl] = werte[0][1]
            n_geaendert += 1
    return n_geaendert


def main():
    liste = json.load(open(sys.argv[1], encoding="utf-8"))
    cache = sys.argv[2]
    fremd, paare = collections.Counter(), collections.Counter()
    for p in sys.argv[3:5]:
        if os.path.exists(p):
            for n in json.load(open(p, encoding="utf-8")):
                ws = woerter(n)
                for w in ws:
                    if re.fullmatch(r"[A-Z&'\-]+", w):
                        fremd[w] += 1
                for a, b in zip(ws, ws[1:]):
                    paare[(a, b)] += 1
    # Fachwoerter der Maklertabellen
    for w in ("CMN", "CLASS", "LINKED", "BASKET", "INDICES", "HYBRID", "PERPETUAL", "USD", "FLEX", "EURO", "EXP",
              "CALL", "PUT", "INDEX", "ADR", "SPONSORED", "REIT", "VOTING", "DELAWARE", "THE", "OF", "AND", "TO",
              "COMMON", "STOCK", "PLC", "INC", "CORP", "CORPORATION", "COMPANY", "INCORPORATED", "HOLDINGS", "LTD",
              "BOND", "BONDS", "MUNICIPAL", "TREASURY", "BILL", "BILLS", "NOTE", "NOTES", "FUND", "FUNDS", "ETF",
              "TRUST", "SHARES", "SERIES", "PREFERRED", "TECHNOLOGIES", "TECHNOLOGY", "INTERNATIONAL", "GROUP",
              "SHS", "JUL", "JUN", "AUG", "SEP", "OCT", "NOV", "DEC", "JAN", "FEB", "MAR", "APR", "MAY"):
        fremd[w] += 50
    zeilen = {}
    for m in liste:
        p = os.path.join(cache, "zeilen", m["id"] + ".json")
        if not os.path.exists(p):
            continue
        for r in json.load(open(p, encoding="utf-8"))["zeilen"]:
            if r.get("befund") in ("geschaeft", "pruefen"):
                kand = [k for k in (r.get("name_kand") or [r.get("name_roh", "")]) if k and k.strip()]
                zeilen["%s/%d/%d" % (m["id"], r["s"], r["z"])] = kand
    eigen = collections.Counter()
    for kand in zeilen.values():
        ws_zeile = set()
        for n in kand:
            ws_n = woerter(n)
            ws_zeile.update(ws_n)
            for a, b in zip(ws_n, ws_n[1:]):
                paare[(a, b)] += 1
        for w in ws_zeile:
            eigen[w] += 1
    ws = Wortschatz(fremd, eigen, paare)
    sauber_von = {}
    for kand in zeilen.values():
        for n in kand:
            if n not in sauber_von:
                sauber_von[n] = ws.name(n)
    gewaehlt = {}
    for schl, kand in zeilen.items():
        if not kand:
            gewaehlt[schl] = ""
            continue
        werte = [(guete(ws, sauber_von[n]), -i, sauber_von[n]) for i, n in enumerate(kand)]
        gewaehlt[schl] = max(werte)[2]
    # ganze Namen: seltene Lesart nahe an einer haeufigen, gleiche Ziffern
    zaehl = collections.Counter(gewaehlt.values())
    haeufig = sorted((n for n, k in zaehl.items() if k >= 3), key=lambda n: -zaehl[n])
    haeufig = [h for h in haeufig if all(w in ws.bekannt or any(ch.isdigit() for ch in w) for w in woerter(h))]
    hw = {h: set(woerter(h)) for h in haeufig}
    ersetzt = {}
    for b in set(gewaehlt.values()):
        if zaehl[b] >= 3:
            continue
        ziffern = re.sub(r"\D", "", b)
        bw = set(woerter(b))
        for h in haeufig:
            if re.sub(r"\D", "", h) != ziffern or abs(len(h) - len(b)) > 3 or not (bw & hw[h]):
                continue
            if difflib.SequenceMatcher(None, b.upper(), h.upper()).ratio() < 0.85:
                continue
            if zaehl[h] >= 3 * zaehl[b] and kosten(b.upper(), h.upper()) <= 1.2 and not anderes_wort(ws, b, h):
                ersetzt[b] = h
                break
    aus = {schl: ersetzt.get(n, n) for schl, n in gewaehlt.items()}
    ziele = set()
    if len(sys.argv) > 6 and os.path.exists(sys.argv[6]):
        hand = json.load(open(sys.argv[6], encoding="utf-8"))["meldungen"]
        geprueft = set()
        for h in hand.values():
            for e in h.get("ersetzen", []):
                geprueft.add(e["w"])
            for k in h.get("korrekturen", {}).values():
                if k.get("w"):
                    geprueft.add(k["w"])
        zaehl2 = collections.Counter(aus.values())
        ziele = geprueft | {n for n, k in zaehl2.items() if k >= 3 and all(
            w in ws.bekannt or any(ch.isdigit() for ch in w) for w in woerter(n))}
        angeglichen = angleichen(aus, ziele, ws)
        print("an gepruefte oder haeufige Namen angeglichen:", angeglichen)
    json.dump(aus, open(sys.argv[5], "w", encoding="utf-8"), ensure_ascii=False, indent=0)
    # Protokoll der Angleichungen ganzer Namen (gewaehlte Lesart -> Ergebnis) zur Durchsicht
    protokoll = {schl: [gewaehlt[schl], n] for schl, n in aus.items() if n != gewaehlt[schl]}
    json.dump(protokoll, open(os.path.splitext(sys.argv[5])[0] + "-angeglichen.json", "w", encoding="utf-8"),
              ensure_ascii=False, indent=0)
    # Anteil bekannter Woerter je Name (Woerter ab zwei Zeichen; Woerter mit Ziffern zaehlen als bekannt);
    # zusammen.py legt Namen unter 0,6 zur Pruefung vor
    anteil = {}
    for schl, n in aus.items():
        ws_n = [w for w in woerter(n) if len(w) >= 2]
        gut = sum(1 for w in ws_n if w in ws.bekannt or w in ws.schwach or any(ch.isdigit() for ch in w))
        anteil[schl] = 1.0 if n in ziele else (round(gut / len(ws_n), 2) if ws_n else 0.0)
    json.dump(anteil, open(os.path.splitext(sys.argv[5])[0] + "-guete.json", "w", encoding="utf-8"), indent=0)
    print("Zeilen:", len(aus), "Lesarten:", len(sauber_von), "ganze Namen angeglichen:", len(ersetzt))


if __name__ == "__main__":
    main()
