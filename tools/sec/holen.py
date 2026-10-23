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
"""
import csv
import datetime as dt
import io
import json
import sys
import zipfile

csv.field_size_limit(1 << 24)
MONATE = {m: i for i, m in enumerate(["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"], 1)}
SPITZE = ("chief executive", "ceo", "chief financial", "cfo", "president", "chair", "principal executive", "principal financial")


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
    if (officer or director) and any(w in t for w in SPITZE):
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
                                   "kauf": code == "P", "d": "", "f": m["f"], "n": 0.0, "wert": 0.0, "nach": None,
                                   "plan": m["plan"] and code == "S"}
        g["n"] += n
        g["wert"] += n * p
        g["d"] = max(g["d"], datum(r["TRANS_DATE"]))
        nach = zahl(r["SHRS_OWND_FOLWNG_TRANS"])
        if nach is not None:
            g["nach"] = nach
    print(pfad.rsplit("\\", 1)[-1].rsplit("/", 1)[-1], "->", len(aus), "Geschaefte bisher", file=sys.stderr)


def main():
    ziel = sys.argv[1]
    aus = {}
    for pfad in sys.argv[2:]:
        lesen(pfad, aus)
    # Nur Geschaefte ab dem ersten Quartal der Datensaetze (manche Meldungen tragen ein Datum aus frueheren Jahren)
    ab = min((g["f"] for g in aus.values() if g["f"]), default="")[:8] + "01"
    liste = []
    for g in aus.values():
        if g["wert"] <= 0 or not g["d"] or g["d"] < ab:
            continue
        g["preis"] = round(g["wert"] / g["n"], 4)
        g["wert"] = round(g["wert"], 2)
        g["n"] = round(g["n"], 4)
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
    stand = max((g.get("f", "") for g in liste), default="") or dt.date.today().isoformat()
    with open(ziel, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"stand": stand, "quelle": "U.S. Securities and Exchange Commission (EDGAR, Form 4)", "geschaefte": liste},
                  f, ensure_ascii=False, separators=(",", ":"))
    kaeufe = sum(1 for g in liste if g["kauf"])
    print("fertig:", len(liste), "Geschaefte,", kaeufe, "Kaeufe,", len(liste) - kaeufe, "Verkaeufe, Stand", stand, file=sys.stderr)


if __name__ == "__main__":
    main()
