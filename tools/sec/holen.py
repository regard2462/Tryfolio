# -*- coding: utf-8 -*-
"""Mitgelieferter Stand der US-Insidergeschaefte fuer die App (assets/copytrading/sec.json).

Quelle sind die Form-345-Datensaetze der SEC (Insider Transactions Data Sets, je Quartal ein Zip):
https://www.sec.gov/data-research/sec-markets-data/insider-transactions-data-sets
Die SEC liefert sie nur an Abrufe mit Kontaktangabe in der Kennung; die Zips koennen auch im Browser geladen werden.

Uebernommen werden Form-4-Meldungen (keine Berichtigungen) mit Kaeufen (P) und Verkaeufen (S) an der Boerse ohne
Derivate, je Meldung und Richtung zusammengefasst: Stueck, Durchschnittspreis, Wert, Bestand danach. Dazu die Rolle der
meldenden Person (S Spitze: CEO, CFO, President, Chair; F andere Officers; A Directors; N 10 %-Aktionaere und andere)
und die Angabe, ob ein Verkauf aus einem Plan nach Rule 10b5-1 stammt.

Aufruf: python holen.py <ziel.json> <zip> [<zip> ...]

Nachtrag: Der Datensatz eines Quartals erscheint erst Wochen nach dessen Ende. Mit --nachtrag werden die Tage danach aus den
taeglichen EDGAR-Indexdateien (daily-index/<Jahr>/QTR<n>/form.<JJJJMMTT>.idx) ergaenzt: je Form-4-Meldung ein Abruf der
Einreichung (.txt mit dem XML), umgesetzt in dieselbe Struktur wie die Datensatzzeilen. Schluessel Meldung und Richtung,
so dass spaeter kommende Datensaetze dieselben Geschaefte nicht doppelt liefern.
  python holen.py <ziel.json> [--basis <alte sec.json>] --nachtrag <Zwischenspeicher-Ordner> [--bis JJJJ-MM-TT] [<zip> ...]
Ohne Zips dient --basis (bisheriger Stand) als Grundlage.
"""
import csv
import datetime as dt
import io
import json
import os
import re
import sys
import zipfile
import xml.etree.ElementTree as ET

csv.field_size_limit(1 << 24)
MONATE = {m: i for i, m in enumerate(["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"], 1)}
CEO_CFO = re.compile(r"chief executive|\bceo\b|chief financial|\bcfo\b|principal executive|principal financial")
VIZE = re.compile(r"\bvice\b|\bvp\b|\bsvp\b|\bevp\b|deputy|assistant|co-president")
PRAESIDENT = re.compile(r"\bpresident\b|\bchair(man|woman|person)?\b")


def datum(s):
    """'31-JUL-2026' -> '2026-07-31'"""
    try:
        t, m, j = s.strip().split("-")
        return "%04d-%02d-%02d" % (int(j), MONATE[m.upper()], int(t))
    except Exception:  # noqa: BLE001
        return ""


def tabelle(z, name):
    with z.open(name) as f:
        yield from csv.DictReader(io.TextIOWrapper(f, encoding="utf-8", errors="replace"), delimiter="\t", quoting=csv.QUOTE_NONE)


def rolle(beziehung, titel):
    b = beziehung.lower()
    t = titel.lower()
    officer = "officer" in b
    director = "director" in b
    spitze = bool(CEO_CFO.search(t)) or (not VIZE.search(t) and bool(PRAESIDENT.search(t)))
    if (officer or director) and spitze:
        return "S"
    if officer:
        return "F"
    if director:
        return "A"
    return "N"


GROSS_BLEIBT = {"LLC", "LP", "L.P.", "PLC", "NV", "N.V.", "SA", "S.A.", "AG", "SE", "ETF", "REIT", "USA", "US", "II", "III", "IV", "AI"}


def schoen(name):
    """Namen ganz in Grossbuchstaben wie „REPUBLIC SERVICES, INC.“ als „Republic Services, Inc.“; Zusaetze wie
    /NEW/ oder /DE/ fallen weg. Gemischt geschriebene Namen bleiben."""
    import re
    n = re.sub(r"\s*/[A-Z]{2,4}/\s*$", "", name.strip())
    if not n or n != n.upper() or not any(c.isalpha() for c in n):
        return n
    worte = []
    for w in n.split(" "):
        kern = w.strip(",.()")
        if kern in GROSS_BLEIBT or (len(kern) <= 3 and "." not in kern and kern.isalpha() and kern not in ("INC", "CO", "THE", "AND", "OF", "LTD", "CORP")):
            worte.append(w)
        else:
            worte.append("-".join("'".join(u[:1].upper() + u[1:].lower() for u in t.split("'")) for t in w.split("-")))
    return " ".join(worte)


def zahl(s):
    try:
        return float(s)
    except (TypeError, ValueError):
        return None


def lesen(pfad, aus):
    z = zipfile.ZipFile(pfad)
    meldungen = {}
    for r in tabelle(z, "SUBMISSION.tsv"):
        if r["DOCUMENT_TYPE"].strip() != "4":
            continue
        k = r["ISSUERTRADINGSYMBOL"].strip().upper()
        if not k or k in ("NONE", "N/A", "NA") or " " in k or len(k) > 10:
            continue
        meldungen[r["ACCESSION_NUMBER"]] = {
            "k": k, "e": schoen(r["ISSUERNAME"]), "c": r["ISSUERCIK"].strip().lstrip("0"),
            "f": datum(r["FILING_DATE"]), "plan": r.get("AFF10B5ONE", "").strip() in ("1", "true", "True"),
        }
    eigner = {}
    for r in tabelle(z, "REPORTINGOWNER.tsv"):
        a = r["ACCESSION_NUMBER"]
        if a not in meldungen:
            continue
        neu = (rolle(r["RPTOWNER_RELATIONSHIP"], r["RPTOWNER_TITLE"]), schoen(r["RPTOWNERNAME"]), r["RPTOWNER_TITLE"].strip())
        alt = eigner.get(a)
        if alt is None or "SFAN".index(neu[0]) < "SFAN".index(alt[0]):
            eigner[a] = neu
    for r in tabelle(z, "NONDERIV_TRANS.tsv"):
        a = r["ACCESSION_NUMBER"]
        code = r["TRANS_CODE"].strip()
        if a not in meldungen or a not in eigner or code not in ("P", "S"):
            continue
        n = zahl(r["TRANS_SHARES"])
        p = zahl(r["TRANS_PRICEPERSHARE"]) or 0.0
        if not n or n <= 0:
            continue
        schluessel = (a, code == "P")
        g = aus.get(schluessel)
        if g is None:
            m = meldungen[a]
            r0, name, titel = eigner[a]
            g = aus[schluessel] = {"a": a, "k": m["k"], "e": m["e"], "c": m["c"], "p": name, "r": r0, "t": titel,
                                   "kauf": code == "P", "d": "", "f": m["f"], "n": 0.0, "nd": 0.0, "wert": 0.0, "nach": None,
                                   "plan": m["plan"] and code == "S"}
        # D direkt, I indirekt; Bestand danach und Stueck fuer den Anteil nur aus direkten Zeilen
        direkt = r.get("DIRECT_INDIRECT_OWNERSHIP", "D").strip().upper() != "I"
        g["n"] += n
        if direkt:
            g["nd"] += n
        g["wert"] += n * p
        g["d"] = max(g["d"], datum(r["TRANS_DATE"]))
        nach = zahl(r["SHRS_OWND_FOLWNG_TRANS"])
        if nach is not None and direkt:
            g["nach"] = nach
    # Ausuebungen von Optionen (Code M) mit Ausuebungspreis: gilt als belegter Einstandspreis, wenn der Verkauf am selben Tag
    # in derselben Meldung steht (Feld ap, gewichtet nach Stueck). Ohne Preis (Zuteilungen) kein Eintrag.
    ausuebung = {}
    if "DERIV_TRANS.tsv" in z.namelist():
        for r in tabelle(z, "DERIV_TRANS.tsv"):
            a = r["ACCESSION_NUMBER"]
            if a not in meldungen or r["TRANS_CODE"].strip() != "M":
                continue
            n = zahl(r["TRANS_SHARES"])
            p = zahl(r.get("CONV_EXER_PRICE"))
            if not n or n <= 0 or not p or p <= 0:
                continue
            s = ausuebung.setdefault((a, datum(r["TRANS_DATE"])), [0.0, 0.0])
            s[0] += n
            s[1] += n * p
    for (a, tag), (n, summe) in ausuebung.items():
        g = aus.get((a, False))
        if g is not None and g["d"] == tag:
            g["ap"] = round(summe / n, 4)
    print(pfad.rsplit("\\", 1)[-1].rsplit("/", 1)[-1], "->", len(aus), "Geschaefte bisher", file=sys.stderr)




# ---------------------------------------------------------------- Nachtrag aus den taeglichen Indexdateien

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36 research contact@example.org"
EDGAR = "https://www.sec.gov/Archives/"
MAX_JE_SEKUNDE = 8
XML_BLOCK = re.compile(r"<XML>\s*(.*?)\s*</XML>", re.S)


def _wert(el, pfad):
    """Text eines Elements, auch wenn er in einem <value>-Kind steht (Form 4 schreibt viele Felder so)."""
    if el is None:
        return ""
    k = el.find(pfad)
    if k is None:
        return ""
    v = k.find("value")
    t = (v.text if v is not None else k.text) or ""
    return t.strip()


def _ja(s):
    return s.strip().lower() in ("1", "true")


def form4_geschaefte(xml, accession, eingang, aus):
    """Setzt die XML-Datei einer Form-4-Meldung (ownershipDocument) in dieselben Zeilen um wie lesen() die Datensaetze:
    je Meldung und Richtung (P Kauf, S Verkauf, ohne Derivate) eine Zeile in aus[(accession, kauf)] mit Rohsummen n, nd, wert."""
    try:
        wurzel = ET.fromstring(xml.encode("utf-8") if isinstance(xml, str) else xml)
    except ET.ParseError:
        return
    if wurzel.tag != "ownershipDocument" or _wert(wurzel, "documentType") not in ("4", ""):
        return
    emittent = wurzel.find("issuer")
    k = _wert(emittent, "issuerTradingSymbol").upper()
    if not k or k in ("NONE", "N/A", "NA") or " " in k or len(k) > 10:
        return
    name = schoen(_wert(emittent, "issuerName"))
    cik = _wert(emittent, "issuerCik").lstrip("0")
    plan = _ja(_wert(wurzel, "aff10b5One"))
    eigner = None
    for o in wurzel.findall("reportingOwner"):
        rel = o.find("reportingOwnerRelationship")
        beziehung = ",".join(n for n, f in (("Director", "isDirector"), ("Officer", "isOfficer")) if _ja(_wert(rel, f)))
        titel = _wert(rel, "officerTitle")
        neu = (rolle(beziehung, titel), schoen(_wert(o.find("reportingOwnerId"), "rptOwnerName")), titel)
        if eigner is None or "SFAN".index(neu[0]) < "SFAN".index(eigner[0]):
            eigner = neu
    if eigner is None:
        return
    for t in wurzel.findall("nonDerivativeTable/nonDerivativeTransaction"):
        code = _wert(t.find("transactionCoding"), "transactionCode")
        if code not in ("P", "S"):
            continue
        betrag = t.find("transactionAmounts")
        n = zahl(_wert(betrag, "transactionShares"))
        p = zahl(_wert(betrag, "transactionPricePerShare")) or 0.0
        if not n or n <= 0:
            continue
        schluessel = (accession, code == "P")
        g = aus.get(schluessel)
        if g is None:
            r0, person, titel = eigner
            g = aus[schluessel] = {"a": accession, "k": k, "e": name, "c": cik, "p": person, "r": r0, "t": titel,
                                   "kauf": code == "P", "d": "", "f": eingang, "n": 0.0, "nd": 0.0, "wert": 0.0, "nach": None,
                                   "plan": plan and code == "S"}
        eigentum = t.find("ownershipNature")
        direkt = _wert(eigentum, "directOrIndirectOwnership").upper() != "I"
        g["n"] += n
        if direkt:
            g["nd"] += n
        g["wert"] += n * p
        g["d"] = max(g["d"], _wert(t, "transactionDate")[:10])
        nach = zahl(_wert(t.find("postTransactionAmounts"), "sharesOwnedFollowingTransaction"))
        if nach is not None and direkt:
            g["nach"] = nach
    # Ausuebungen (Code M) mit Preis, gilt fuer den Verkauf am selben Tag
    ausuebung = {}
    for t in wurzel.findall("derivativeTable/derivativeTransaction"):
        if _wert(t.find("transactionCoding"), "transactionCode") != "M":
            continue
        n = zahl(_wert(t.find("transactionAmounts"), "transactionShares"))
        p = zahl(_wert(t, "conversionOrExercisePrice"))
        if not n or n <= 0 or not p or p <= 0:
            continue
        s = ausuebung.setdefault(_wert(t, "transactionDate")[:10], [0.0, 0.0])
        s[0] += n
        s[1] += n * p
    for tag, (n, summe) in ausuebung.items():
        g = aus.get((accession, False))
        if g is not None and g["d"] == tag:
            g["ap"] = round(summe / n, 4)


class Abrufer:
    """Abrufe bei der SEC: hoechstens MAX_JE_SEKUNDE je Sekunde, ueber alle Threads gemeinsam. 403 und 429 brechen ab."""

    def __init__(self):
        import threading
        self.sperre = threading.Lock()
        self.naechster = 0.0
        self.anfragen = 0

    def holen(self, url, fehlt_403=False):
        """fehlt_403: Fuer Tage ohne Indexdatei (Feiertage) antwortet die SEC mit 403 statt 404; dann gilt die Datei als nicht vorhanden."""
        import gzip
        import time
        import urllib.error
        import urllib.request
        for versuch in range(4):
            with self.sperre:
                jetzt = time.monotonic()
                warte = self.naechster - jetzt
                self.naechster = max(jetzt, self.naechster) + 1.0 / MAX_JE_SEKUNDE
                self.anfragen += 1
            if warte > 0:
                time.sleep(warte)
            req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Encoding": "gzip"})
            try:
                with urllib.request.urlopen(req, timeout=60) as r:
                    daten = r.read()
                    if r.headers.get("Content-Encoding") == "gzip":
                        daten = gzip.decompress(daten)
                    return daten
            except urllib.error.HTTPError as e:
                if e.code == 403 and fehlt_403:
                    return None
                if e.code in (403, 429):
                    print("ABBRUCH: HTTP %d bei %s" % (e.code, url), file=sys.stderr)
                    os._exit(3)
                if e.code == 404:
                    return None
                time.sleep(2 * (versuch + 1))
            except Exception:  # noqa: BLE001
                time.sleep(2 * (versuch + 1))
        raise RuntimeError("Abruf fehlgeschlagen: " + url)


def indexzeilen(text):
    """Form-4-Einreichungen (Typ genau '4') aus form.idx: {Accession: Dateipfad}. Die Spalten sind fest breit, der Pfad
    steht am Zeilenende und enthaelt die Accession."""
    aus = {}
    for z in text.splitlines():
        if not z.startswith("4 ") and not z.startswith("4\t"):
            continue
        m = re.search(r"(edgar/data/\d+/(\d{10}-\d{2}-\d{6})\.txt)\s*$", z)
        if m:
            aus.setdefault(m.group(2), m.group(1))
    return aus


def xml_aus_einreichung(daten):
    """Das XML-Dokument aus der vollstaendigen Einreichung (.txt); leer, wenn keines vorhanden ist."""
    m = XML_BLOCK.search(daten.decode("utf-8", errors="replace"))
    if not m:
        return ""
    x = m.group(1)
    return x[x.index("<ownershipDocument"):] if "<ownershipDocument" in x else ""


def werktage(von, bis):
    t = von
    while t <= bis:
        if t.weekday() < 5:
            yield t
        t += dt.timedelta(days=1)


def _nter(jahr, monat, wochentag, n):
    t = dt.date(jahr, monat, 1)
    t += dt.timedelta(days=(wochentag - t.weekday()) % 7 + 7 * (n - 1))
    return t


def feiertag(tag):
    """Bundesfeiertag, an dem die SEC keine Meldungen annimmt (auf Wochenenden fallende Tage rutschen auf Freitag oder Montag)."""
    j = tag.year
    letzter_mai = dt.date(j, 5, 31) - dt.timedelta(days=(dt.date(j, 5, 31).weekday()) % 7)
    festen = [dt.date(j, 1, 1), dt.date(j, 6, 19), dt.date(j, 7, 4), dt.date(j, 11, 11), dt.date(j, 12, 25), dt.date(j + 1, 1, 1)]
    beweglich = [_nter(j, 1, 0, 3), _nter(j, 2, 0, 3), letzter_mai, _nter(j, 9, 0, 1), _nter(j, 10, 0, 2), _nter(j, 11, 3, 4)]
    for f in festen:
        if f.weekday() == 5:
            f -= dt.timedelta(days=1)
        elif f.weekday() == 6:
            f += dt.timedelta(days=1)
        beweglich.append(f)
    return tag in beweglich


def nachtrag(aus, von, bis, ordner):
    """Ergaenzt aus um alle Form-4-Meldungen der Einreichungstage von..bis. Zwischenspeicher: ordner/idx und ordner/xml."""
    from concurrent.futures import ThreadPoolExecutor
    idx_dir = os.path.join(ordner, "idx")
    xml_dir = os.path.join(ordner, "xml")
    os.makedirs(idx_dir, exist_ok=True)
    os.makedirs(xml_dir, exist_ok=True)
    abruf = Abrufer()
    liste = []
    for tag in werktage(von, bis):
        name = "form.%s.idx" % tag.strftime("%Y%m%d")
        pfad = os.path.join(idx_dir, name)
        if not os.path.exists(pfad):
            url = "%sedgar/daily-index/%d/QTR%d/%s" % (EDGAR, tag.year, (tag.month - 1) // 3 + 1, name)
            daten = abruf.holen(url, fehlt_403=feiertag(tag))
            with open(pfad, "wb") as f:
                f.write(daten or b"")  # leer: Tag ohne Index (Feiertag)
        with open(pfad, "rb") as f:
            text = f.read().decode("utf-8", errors="replace")
        for acc, rel in indexzeilen(text).items():
            liste.append((tag.isoformat(), acc, rel))
    print("Nachtrag: %d Form-4-Einreichungen im Index" % len(liste), file=sys.stderr)

    def laden(e):
        _, acc, rel = e
        pfad = os.path.join(xml_dir, acc + ".xml")
        if not os.path.exists(pfad):
            daten = abruf.holen(EDGAR + rel)
            x = xml_aus_einreichung(daten) if daten else ""
            with open(pfad, "w", encoding="utf-8", newline="\n") as f:
                f.write(x)
        with open(pfad, encoding="utf-8") as f:
            return f.read()

    fertig = 0
    with ThreadPoolExecutor(max_workers=16) as pool:
        for e, x in zip(liste, pool.map(laden, liste)):
            if x:
                form4_geschaefte(x, e[1], e[0], aus)
            fertig += 1
            if fertig % 2000 == 0:
                print("  %d/%d, Anfragen %d, Geschaefte %d" % (fertig, len(liste), abruf.anfragen, len(aus)), file=sys.stderr)
    print("Nachtrag fertig, Anfragen:", abruf.anfragen, file=sys.stderr)
    return len(liste), abruf.anfragen


def fertigstellen(aus, ab):
    liste = []
    for g in aus.values():
        if g["wert"] <= 0 or not g["d"] or g["d"] < ab:
            continue
        g["preis"] = round(g["wert"] / g["n"], 4)
        g["wert"] = round(g["wert"], 2)
        g["n"] = round(g["n"], 4)
        g["nd"] = round(g["nd"], 4)
        if g["nd"] == g["n"]:
            g.pop("nd")
        for feld in ("t", "c", "f"):
            if not g[feld]:
                g.pop(feld)
        if g["nach"] is None:
            g.pop("nach")
        if not g["plan"]:
            g.pop("plan")
        if g["r"] == "N":
            g.pop("r")
        liste.append(g)
    liste.sort(key=lambda g: (g["d"], g.get("f", ""), g["a"]), reverse=True)
    return liste


def aus_basis(pfad, aus):
    """Bisherigen Stand (fertige sec.json) als Grundlage laden, in der Rohform von lesen()."""
    with open(pfad, encoding="utf-8") as f:
        for g in json.load(f)["geschaefte"]:
            z = dict(g)
            z.setdefault("t", "")
            z.setdefault("c", "")
            z.setdefault("f", "")
            z.setdefault("nach", None)
            z["plan"] = bool(z.get("plan"))
            z.setdefault("r", "N")
            z.setdefault("nd", z["n"])
            z["wert"] = float(z["wert"])
            aus[(z["a"], z["kauf"])] = z


def main():
    args = sys.argv[1:]
    ziel = args.pop(0)
    opt = {}
    for name in ("--basis", "--nachtrag", "--bis"):
        if name in args:
            i = args.index(name)
            opt[name] = args[i + 1]
            del args[i:i + 2]
    aus = {}
    if "--basis" in opt:
        aus_basis(opt["--basis"], aus)
    for pfad in args:
        lesen(pfad, aus)
    # Nur Geschaefte ab dem ersten Quartal der Datensaetze (manche Meldungen tragen ein Datum aus frueheren Jahren)
    ab = min((g["f"] for g in aus.values() if g["f"]), default="")[:8] + "01"
    stand_tag = ""
    if "--nachtrag" in opt:
        letzter = max((g["f"] for g in aus.values() if g["f"]), default="")
        von = dt.date.fromisoformat(letzter)
        bis = dt.date.fromisoformat(opt["--bis"]) if "--bis" in opt else dt.date.today() - dt.timedelta(days=1)
        nachtrag(aus, von, bis, opt["--nachtrag"])
        stand_tag = bis.isoformat()
    liste = fertigstellen(aus, ab)
    stand = max((g.get("f", "") for g in liste), default="") or dt.date.today().isoformat()
    if stand_tag and stand_tag > stand:
        stand = stand_tag
    with open(ziel, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"stand": stand, "quelle": "U.S. Securities and Exchange Commission (EDGAR, Form 4)", "geschaefte": liste},
                  f, ensure_ascii=False, separators=(",", ":"))
    kaeufe = sum(1 for g in liste if g["kauf"])
    print("fertig:", len(liste), "Geschaefte,", kaeufe, "Kaeufe,", len(liste) - kaeufe, "Verkaeufe, Stand", stand, file=sys.stderr)


if __name__ == "__main__":
    main()
