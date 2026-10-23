# -*- coding: utf-8 -*-
"""Anzeigenamen und weitere Kuerzel fuer die Ergebnisdatei (Schritt 17b, seit 4.2). Die Zwischenstaende behalten die
gelesenen Namen; die Ergebnisdatei bekommt Namen, wie sie in einer Depotuebersicht stehen:

1. Geschaefte mit Kuerzel (ausser Optionen, deren Beschreibung der Derivateleser der App braucht) tragen den Namen des
   Wertpapiers aus dem Verzeichnis der US-Boersen (nasdaqlisted.txt und otherlisted.txt von nasdaqtrader.com, Zusatz
   wie „Common Stock“ entfernt), sonst den Namen aus den Treffern der Yahoo-Suche (suche-cache.json).
2. Aktien und Fonds ohne Kuerzel werden mit dem Verzeichnis, den Yahoo-Treffern, den vorhandenen Zuordnungen und den
   Namen der eigenen Geschaefte mit Kuerzel verglichen. Der Abstand beruecksichtigt die typischen Verwechslungen der
   OCR (namen.kosten). Uebernommen wird ein Treffer nur, wenn er sehr nahe liegt und deutlich vor dem naechsten
   Kandidaten einer anderen Firma; Anleihen, Kommunalanleihen, Vorzugs- und strukturierte Papiere bleiben aussen vor.
3. Uebrige Namen: Maklerzusatz „CMN“ und Rahmenreste weg, roemische Ziffern („FUND 11“ -> „FUND II“), „LINKED TO
   S&P 500“, einzelne Woerter mit dem Wortschatz aus Verzeichnis und Zuordnungen berichtigt. Bleibt nur eine Rechtsform
   uebrig („CORPORATION“), wird der Name leer; die App zeigt dann „Name nicht lesbar“.

Aufruf: python -I anzeige.py <papier.json> <verzeichnis-ordner> <suche-cache.json> <zuordnung.json> <ziel.json> <protokoll.json>
        python -I anzeige.py --probe <stichprobe.json> <stichprobe-wahr.json> <kuerzel.json> <verzeichnis-ordner>
               <suche-cache.json> <zuordnung.json>     (Vergleich mit der Sichtpruefung, ohne zu schreiben)
"""
import collections
import json
import os
import re
import sys
import unicodedata

HIER = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HIER)
import namen as N  # noqa: E402
import kuerzel_papier as KP  # noqa: E402

HAND = json.load(open(os.path.join(HIER, "kuerzel-hand.json"), encoding="utf-8"))

# Woerter, die nicht zum Kern eines Firmennamens gehoeren
FUELL = set("""INC INCORPORATED CORP CORPORATION CO COS COMPANY COMPANIES LTD LIMITED PLC NV SA AG SE AB ASA LP LLC THE
CMN CNN CMM AVIN COM COMMON STOCK STK SHS SH SHARES SHARE ORD ORDINARY ADR ADRS ADS SPONSORED SPON SPONS NEW DEL DELAWARE
CL CLASS REG HLDGS HOLDINGS HOLDING GROUP GRP TR TRUST N V I""".split())
KLASSE = re.compile(r"\bCL(?:ASS)?[\s.\-]*([A-C])\b")
TYP_ZUSATZ = re.compile(
    r"\s*[-,]?\s*(?:(Class [A-Z])\s+)?(Common Stock|Common Shares|Ordinary Shares?|Subordinate Voting Shares|"
    r"American Depositary Shares?.*|American Depository Shares?.*|Depositary Shares.*|ADS.*|"
    r"Shares of Beneficial Interest|Common Units.*|Limited Partnership Units?)\s*$", re.I)
KEIN_WERT = re.compile(r"warrant|\bright|\bunits?\b|preferred|depositary shares?, each|notes due|debentures|%|\btest\b", re.I)


def wertname(n):
    """Name aus dem Verzeichnis ohne Zusatz zur Wertpapierart: „Alphabet Inc. - Class A Common Stock“ -> „Alphabet Inc. Class A“."""
    n = " ".join(n.split())
    m = TYP_ZUSATZ.search(n)
    if m:
        klasse = (m.group(1) or "").strip()
        n = n[:m.start()].rstrip(" ,-")
        if klasse:
            n += " " + klasse
    n = re.sub(r"\s+-\s*$", "", n).strip()
    return re.sub(r"\s+New$", "", n)


def kern(name):
    """Firmenname ohne Rechtsform und Zusaetze, ohne Leer- und Satzzeichen (Grossbuchstaben und Ziffern)."""
    u = unicodedata.normalize("NFKD", name.upper())
    u = "".join(c for c in u if not unicodedata.combining(c))
    u = KLASSE.sub(" ", u).replace("&", " ")
    toks = re.findall(r"[A-Z0-9" + N.UNBEKANNT + "]+", u)
    return "".join(t for t in toks if t not in FUELL)


def klasse(name):
    m = KLASSE.search(name.upper())
    return m.group(1) if m else None


def symbol(k):
    return re.sub(r"^([A-Z0-9]+)[./]([A-Z])$", r"\1-\2", k.strip().upper())


def verzeichnis(ordner):
    """Symbol -> (Name, ETF) aus den Verzeichnissen der US-Boersen."""
    aus = {}
    for datei, i_sym, i_name, i_etf, i_test in (("nasdaqlisted.txt", 0, 1, 6, 3), ("otherlisted.txt", 0, 1, 4, 6)):
        p = os.path.join(ordner, datei)
        if not os.path.exists(p):
            continue
        for zeile in open(p, encoding="utf-8", errors="replace"):
            f = zeile.rstrip("\n").split("|")
            if len(f) < 7 or f[0] in ("Symbol", "ACT Symbol") or f[0].startswith("File Creation"):
                continue
            if f[i_test].strip() == "Y" or not re.fullmatch(r"[A-Z]{1,5}(\.[A-Z])?", f[i_sym].strip()):
                continue
            aus[symbol(f[i_sym])] = (f[i_name].strip(), f[i_etf].strip() == "Y")
    return aus


def yahoo_namen(suche_cache):
    """Symbol -> Name aus den Treffern der Yahoo-Suche (nur US-Symbole, Aktien, ETFs und Fonds)."""
    aus = {}
    for treffer in suche_cache.values():
        for t in treffer or []:
            s = (t.get("symbol") or "").upper()
            if not re.fullmatch(r"[A-Z]{1,5}(-[A-Z])?", s) or t.get("quoteType") not in ("EQUITY", "ETF", "MUTUALFUND"):
                continue
            n = t.get("longname") or t.get("shortname")
            if n and s not in aus:
                aus[s] = (n.strip(), t.get("quoteType"))
    return aus


class Abgleich:
    """Kandidaten fuer den Vergleich (Kern, Symbol, Art) mit einem Index ueber Dreiergruppen von Buchstaben."""

    def __init__(self):
        self.eintraege = []
        self.index = collections.defaultdict(set)

    @staticmethod
    def gruppen(k):
        k = "^" + k + "$"
        return {k[i:i + 3] for i in range(len(k) - 2)}

    def dazu(self, name, sym, art):
        k = kern(name)
        if len(k) < 3:
            return
        i = len(self.eintraege)
        self.eintraege.append((k, sym, art, name))
        for g in self.gruppen(k):
            self.index[g].add(i)

    def bester(self, name):
        """(Symbol, Art, Name des Kandidaten, Kosten) oder None, wenn kein Kandidat sicher genug ist."""
        q = kern(name)
        if len(q) < 4:
            return None
        # fuer die Suche Ziffern als Buchstaben lesen (I1, O0, S5 ...)
        such = q.translate(str.maketrans("015826|4", "OISBZGIA"))
        zaehl = collections.Counter()
        for g in self.gruppen(such):
            for i in self.index.get(g, ()):
                zaehl[i] += 1
        if not zaehl:
            return None
        noetig = max(2, len(self.gruppen(such)) // 3)
        kandidaten = [i for i, n in zaehl.most_common(80) if n >= noetig]
        bewertet = []
        for i in kandidaten:
            k, sym, art, nm = self.eintraege[i]
            if abs(len(k) - len(q)) > max(3, len(k) // 3):
                continue
            c = N.kosten(q, k)
            bewertet.append((c, sym, art, nm, k))
        if not bewertet:
            return None
        bewertet.sort()
        c, sym, art, nm, k = bewertet[0]
        grenze = 0.6 if len(k) <= 5 else (1.0 if len(k) <= 8 else 0.14 * len(k))
        if c > grenze:
            return None
        # der naechste Kandidat einer anderen Firma muss deutlich weiter weg sein
        wurzel = sym.split("-")[0]
        andere = [b for b in bewertet[1:] if b[1].split("-")[0] != wurzel and b[4] != k]
        if andere and andere[0][0] - c < 0.8:
            return None
        # Aktienklasse: bei mehreren Gattungen derselben Firma entscheidet die gelesene Klasse
        gleiche = collections.Counter(b[1] for b in bewertet if b[4] == k or b[1].split("-")[0] == wurzel)
        kl = klasse(name)
        if len(gleiche) > 1:
            passend = [s for s in gleiche if kl and (s.endswith("-" + kl) or ("CLASS " + kl) in self.name_von(s).upper())]
            if len(passend) == 1:
                sym = passend[0]
            else:
                # sonst das Kuerzel, das die Quellen am haeufigsten fuer diesen Namen nennen; bei Gleichstand keines
                (s1, n1), (_, n2) = gleiche.most_common(2)
                if n1 == n2:
                    return None
                sym = s1
        return sym, art, nm, c

    def name_von(self, sym):
        for k, s, a, nm in self.eintraege:
            if s == sym:
                return nm
        return ""


KEINE_AKTIE = [KP.ANLEIHE, KP.KOMMUNAL, KP.STAAT, KP.VORZUG, KP.STRUKTUR, KP.ZINSTERMIN]


def anleiheartig(name):
    return any(rx.search(name) for rx in KEINE_AKTIE) or bool(KP.STADT_STAAT.match(name.upper()))


ROEMISCH = {"11": "II", "111": "III", "1V": "IV", "V1": "VI", "V11": "VII", "1X": "IX"}


def aufraeumen(name, woerter):
    """Name ohne Kuerzel: Maklerzusatz, Rahmenreste, roemische Ziffern, Indexanleihen, einzelne Woerter berichtigt."""
    n = " ".join(name.replace("?", " ").split())
    n = re.sub(r"\b[LU]\W?I?N\S{0,3}\s+T[OD0]\s+(?:S&P\s+|SAP\s+|IS&P\s+)?(?:SOO|5OO|S00|500|SO0|SOD)\b", "LINKED TO S&P 500", n, flags=re.I)
    n = re.sub(r"(S&P 500)\s+[IL1]?NDEX\b", r"\1 INDEX", n, flags=re.I)
    n = re.sub(r"\b[LU]\W?I?N\S{0,3}\s+T[OD0]\b", "LINKED TO", n, flags=re.I)
    n = re.sub(r"(\b(?:FUND|FD|CAPITAL|PARTNERS|OPP|OPPORTUNITIES|INVESTORS|SPV|LP|L\.P\.)\s+)(1V|V11|V1|111|11|1X)\b",
               lambda m: m.group(1) + ROEMISCH[m.group(2).upper()], n, flags=re.I)
    n = re.sub(r"\b(1V|V11|V1|111|11|1X)(\s+(?:LP|L\.P\.|LLC))\b", lambda m: ROEMISCH[m.group(1).upper()] + m.group(2), n, flags=re.I)
    n = n.replace("_", "").translate(N.FREMD)
    # „INC“ mit verlesenem I („'NC“, „(NC“) wie in namen.py
    n = re.sub(r"(?<=\S)\s[(\[{'$)|!]NC\b\.?", " INC", n)
    toks = [t for t in n.split(" ") if t]
    # Lesereste am Ende: Maklerzusatz in seinen Lesarten („CMN“, „av1N“, „a?AN“) und einzelne Zeichen
    while toks and (re.fullmatch(r"[1Il|!.,'`]", toks[-1]) or toks[-1].upper() in ("CMN", "CNN", "CMM", "AVIN")
                    or (N.CMN.match(toks[-1]) and toks[-1].upper() not in woerter) or not re.search(r"[A-Za-z0-9]", toks[-1])):
        toks.pop()
    aus = []
    for i, tok in enumerate(toks):
        g = re.sub(r"[^A-Za-z0-9&'\-]", "", tok).upper()
        # Rahmenrest vor dem ersten Wort: „ICOLUMBUS“, „iPRUDEN11AL“
        if i == 0 and len(g) >= 5 and g[0] in "I1L|" and woerter.fremd[g[1:]] >= 1 and woerter.fremd[g] == 0:
            tok = tok[1:] if tok[0] in "Ii1lL|[" else tok
            g = g[1:]
        if len(g) >= 4 and g not in woerter and sum(c.isalpha() for c in g) >= len(g) - 3 and not re.search(r"\d{2,}/|%", tok):
            # sauber gelesene Woerter (nur Buchstaben) nur ueber typische Verwechslungen aendern, nicht ueber fehlende Buchstaben
            b = bestes_wort(g, woerter, streng=g.isalpha())
            if b:
                tok = b if tok.isupper() or not tok.isalpha() else b.capitalize()
        aus.append(tok)
    n = " ".join(aus)
    n = re.sub(r"\s+(?:CMN|CNN|CMM|AVIN)(?=\s+CLASS\s+[A-C]\s*$)", "", n, flags=re.I)
    if not kern(n) or not re.search(r"[A-Za-z]{2}", kern(n)):
        return ""
    return n.strip()


WORT_MERK = {}


def bestes_wort(w, woerter, streng=False):
    if (w, streng) in WORT_MERK:
        return WORT_MERK[(w, streng)]
    grenze = 0.6 if streng else (0.7 if len(w) <= 5 else (1.0 if len(w) <= 8 else 1.4))
    best = []
    for x in woerter.get_len(len(w)):
        if streng and len(x) != len(w):
            continue
        c = N.kosten(w, x, streng)
        if c <= grenze:
            best.append((c, -woerter.anzahl[x], x))
    best.sort()
    r = None
    if best and (len(best) == 1 or best[1][0] - best[0][0] >= 0.4):
        r = best[0][2]
    WORT_MERK[(w, streng)] = r
    return r


class Woerter(dict):
    """Wortschatz mit Haeufigkeit; Woerter mit mindestens drei Vorkommen gelten als bekannt. [fremd]: Woerter aus
    Verzeichnis und Zuordnungen (nicht aus den Papiermeldungen selbst)."""

    def __init__(self, zaehl, fremd=None):
        super().__init__({w: n for w, n in zaehl.items() if n >= 3 and len(w) >= 2})
        self.anzahl = zaehl
        self.fremd = fremd if fremd is not None else collections.Counter()
        self.nach_laenge = collections.defaultdict(list)
        for w in self:
            self.nach_laenge[len(w)].append(w)

    def get_len(self, n):
        for L in (n - 1, n, n + 1):
            yield from self.nach_laenge.get(L, ())


class Anzeige:
    def __init__(self, ordner, suche_cache, zuordnung, papier_geschaefte):
        self.verz = verzeichnis(ordner)
        self.yahoo = yahoo_namen(suche_cache)
        self.abgleich = Abgleich()
        for sym, (nm, etf) in self.verz.items():
            if not KEIN_WERT.search(nm):
                self.abgleich.dazu(wertname(nm), sym, "EF" if etf else "ST")
        for sym, (nm, typ) in self.yahoo.items():
            self.abgleich.dazu(nm, sym, "ST" if typ == "EQUITY" else "EF")
        for nm, z in list(zuordnung.items()) + [(k, {"k": v.get("k"), "art": v.get("t")}) for k, v in HAND.items()]:
            k = (z or {}).get("k")
            if k and re.fullmatch(r"[A-Z]{1,5}([.\-][A-Z])?", k):
                self.abgleich.dazu(nm, symbol(k), "EF" if (z.get("art") in ("fonds", "etf", "EF")) else "ST")
        for g in papier_geschaefte:
            if g.get("k") and g.get("t") in ("ST", "EF") and re.fullmatch(r"[A-Z]{1,5}([.\-][A-Z])?", g["k"]):
                self.abgleich.dazu(g["w"], symbol(g["k"]), g["t"])
        zaehl = collections.Counter()
        for sym, (nm, _) in self.verz.items():
            zaehl.update(re.findall(r"[A-Z][A-Z&'\-]+", nm.upper()))
        for nm in zuordnung:
            zaehl.update(re.findall(r"[A-Z][A-Z&'\-]+", nm.upper()))
        fremd = collections.Counter(zaehl)
        for g in papier_geschaefte:
            zaehl.update(re.findall(r"[A-Z][A-Z&'\-]+", g["w"].upper()))
        self.woerter = Woerter(zaehl, fremd)

    def name_fuer(self, k):
        s = symbol(k)
        if s in self.verz:
            return wertname(self.verz[s][0])
        if s in self.yahoo:
            return self.yahoo[s][0]
        return None

    def geschaeft(self, g):
        """(neuer Name, neues Kuerzel, neuer Typ, Grund)."""
        w, k, t = g["w"], g.get("k"), g.get("t", "OT")
        if t == "OP":
            return w, k, t, None
        if k:
            n = self.name_fuer(k)
            return (n, k, t, "verzeichnis") if n else (aufraeumen(w, self.woerter) or w, k, t, "aufgeraeumt")
        if t in ("ST", "EF") or (t == "OT" and KP.FIRMA.search(w.upper()) and not anleiheartig(w)):
            if not anleiheartig(w) and not KP.PRIVAT.search(w):
                b = self.abgleich.bester(w)
                if b:
                    sym, art, _, _ = b
                    # nicht mehr notierte Werte (Uebernahmen) stehen nicht im Verzeichnis: Kuerzel ja, Name aufgeraeumt
                    n = self.name_fuer(sym) or aufraeumen(w, self.woerter) or w
                    return n, sym, art, "abgleich"
        return aufraeumen(w, self.woerter), None, t, "aufgeraeumt"


def main():
    if sys.argv[1] == "--probe":
        return probe(*sys.argv[2:])
    papier_p, ordner, cache_p, zuord_p, ziel, protokoll_p = sys.argv[1:7]
    papier = json.load(open(papier_p, encoding="utf-8"))
    alle = [g for m in papier["meldungen"].values() for g in m["geschaefte"]]
    a = Anzeige(ordner, json.load(open(cache_p, encoding="utf-8")), json.load(open(zuord_p, encoding="utf-8")), alle)
    protokoll = []
    zaehl = collections.Counter()
    for doc, m in papier["meldungen"].items():
        for g in m["geschaefte"]:
            n, k, t, grund = a.geschaeft(g)
            if n != g["w"] or k != g.get("k") or t != g.get("t"):
                protokoll.append({"id": doc, "alt": g["w"], "neu": n, "k_alt": g.get("k"), "k_neu": k, "grund": grund})
                zaehl[grund] += 1
            g["w"] = n
            if k:
                g["k"] = k
            g["t"] = t
    json.dump(papier, open(ziel, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    json.dump(protokoll, open(protokoll_p, "w", encoding="utf-8"), ensure_ascii=False, indent=0)
    mit = sum(1 for m in papier["meldungen"].values() for g in m["geschaefte"] if g.get("k"))
    print("geaendert:", dict(zaehl), "| mit Kuerzel:", mit, "von", len(alle))


def probe(probe_p, wahr_p, kuerzel_p, ordner, cache_p, zuord_p):
    """Anzeigenamen der Stichprobe gegen die am Bild gelesenen Namen (Kern ohne Rechtsform und Zusaetze)."""
    probe_l = json.load(open(probe_p, encoding="utf-8"))
    wahr = {e["nr"]: e for e in json.load(open(wahr_p, encoding="utf-8"))["zeilen"]}
    kz = json.load(open(kuerzel_p, encoding="utf-8"))
    gs = [{"w": p["w"], "k": (kz.get(p["w"]) or {}).get("k"), "t": (kz.get(p["w"]) or {}).get("t", "ST")} for p in probe_l]
    a = Anzeige(ordner, json.load(open(cache_p, encoding="utf-8")), json.load(open(zuord_p, encoding="utf-8")), gs)
    vorher = nachher = 0
    for nr, (p, g) in enumerate(zip(probe_l, gs), start=1):
        w = wahr[nr]["w"]
        n, k, t, grund = a.geschaeft(dict(g))
        ok_v = kern(p["w"]) == kern(w)
        kn, kw = kern(n), kern(w)
        ok_n = kn == kw or (k is not None and grund in ("verzeichnis", "abgleich") and bool(kn) and (kw.startswith(kn) or kn.startswith(kw) or kn[:5] == kw[:5]))
        vorher += ok_v
        nachher += ok_n
        if not ok_n:
            print("%2d | %s | %s | %s (%s)" % (nr, w, p["w"], n, grund))
    print("Kern richtig vorher %d, nachher %d von %d" % (vorher, nachher, len(probe_l)))


if __name__ == "__main__":
    main()
