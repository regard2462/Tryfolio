# -*- coding: utf-8 -*-
"""Insidergeschaefte kanadischer Emittenten als Registerdatei fuer den Import in der App (Profil, Registerdaten importieren).

Grundlage sind die Wochenberichte von SEDI („Weekly Summary by Insider“, PDF), von Hand im Browser
heruntergeladen: SEDI sperrt automatische Abrufe. Je Geschaeft stehen im Bericht Emittent, Insider, Beziehung zum
Emittenten, Datum, Meldetag, Menge mit Vorzeichen, Wertpapier, Art (Code), Kennung, Preis und Waehrung.
Kauf oder Verkauf ist nur Code 10 („Acquisition or disposition in the public market“); Plaene, Zuteilungen,
Ausuebungen und Aehnliches sind Sonstiges. Rueckkaeufe des Emittenten selbst (Beziehung 1) fallen weg.
Mehrere Wochenberichte werden vereint; vorhandene Geschaefte der Zieldatei bleiben, solange sie im Zeitraum liegen.
Die Datei bleibt beim Nutzer und kommt nicht ins Repo: SEDI erlaubt keine Weitergabe gesammelter Daten.

Aufruf: python sedi.py <ziel.json> <bericht.pdf> [bericht.pdf ...] [--tage 365]
"""
import datetime as dt
import json
import os
import re
import sys

import fitz

DATUM = re.compile(r"^\d{4}-\d{2}-\d{2}$")
MENGE = re.compile(r"^([+-])\s*([\d,]+(?:\.\d+)?)$")
ART = re.compile(r"^(\d{1,2}) - ")
KENNUNG = re.compile(r"^\d{6,9}$")
PREIS = re.compile(r"^([\d,]+(?:\.\d+)?)\s*([A-Z]{3})?\s*$")
BEZIEHUNG = re.compile(r"(\d) - (Issuer|Subsidiary of Issuer|10% Security Holder|Director of Issuer|Senior Officer of Issuer|"
                       r"Director or Senior Officer of 10%|Director or Senior Officer of Insider|Deemed Insider)")
URL = "https://www.sedi.ca/sedi/SVTItdController?locale=en_CA"


def rolle(codes):
    """S Spitze (Vorstand mit Sitz im Board), A Board, F weitere Fuehrung, N Grossaktionaer (10 %)."""
    if "4" in codes and "5" in codes:
        return "S"
    if "5" in codes:
        return "F"
    if "4" in codes:
        return "A"
    if "3" in codes:
        return "N"
    return "F"


def zahl(s):
    try:
        return float(s.replace(",", ""))
    except ValueError:
        return None


def lesen(pfad):
    zeilen = []
    for seite in fitz.open(pfad):
        zeilen += [z.strip() for z in seite.get_text().split("\n")]
    aus = []
    emittent = person = ""
    codes = set()
    i = 0
    n = len(zeilen)
    while i < n:
        z = zeilen[i]
        if z.startswith("Issuer:"):
            emittent = z[len("Issuer:"):].strip()
            i += 1
            continue
        if z.startswith("Insider:"):
            person = z[len("Insider:"):].strip()
            codes = set()
            # Beziehung steht in den naechsten Zeilen („4 - Director of Issuer, 5 - Senior Officer of Issuer“)
            j = i + 1
            while j < n and j <= i + 4 and not DATUM.match(zeilen[j]):
                codes |= {m.group(1) for m in BEZIEHUNG.finditer(zeilen[j])}
                j += 1
            i += 1
            continue
        if DATUM.match(z) and i + 1 < n and DATUM.match(zeilen[i + 1]):
            tag, meldung = z, zeilen[i + 1]
            j = i + 2
            stueck = []
            while j < n and not (DATUM.match(zeilen[j]) and j + 1 < n and DATUM.match(zeilen[j + 1])) \
                    and not zeilen[j].startswith(("Insider:", "Issuer:", "Insider's Relationship")):
                stueck.append(zeilen[j])
                j += 1
            i = j
            menge = next((MENGE.match(s) for s in stueck if MENGE.match(s)), None)
            kennung = next((k for k, s in enumerate(stueck) if KENNUNG.match(s)), None)
            art = next((ART.match(s) for s in stueck if ART.match(s)), None)
            if not menge or kennung is None or not art or not emittent or not person:
                continue
            if codes == {"1"}:
                continue
            p = PREIS.match(stueck[kennung + 1]) if kennung + 1 < len(stueck) else None
            preis = zahl(p.group(1)) if p else None
            waehrung = (p.group(2) if p and p.group(2) else "CAD")
            n_ = zahl(menge.group(2)) or 0.0
            # Wertpapier: Zeilen nach der Menge bis zum Bestand (Zahl, gegebenenfalls mit Stern)
            k0 = stueck.index(menge.group(0)) + 1
            papier = []
            for s in stueck[k0:]:
                if re.match(r"^\*?[\d,]+(\.\d+)?$", s):
                    break
                papier.append(s)
            papier = " ".join(papier)
            aktie = bool(re.search(r"\bshares?\b", papier, re.I)) and not re.match(r"(?i)\s*(rights|options|warrants)", papier)
            code = int(art.group(1))
            if code == 10 and aktie and preis:
                a = "K" if menge.group(1) == "+" else "V"
            else:
                a = "S"
            g = {"e": emittent, "isin": "", "p": person, "r": rolle(codes),
                 "t": ", ".join(sorted(codes)), "i": "Aktie" if aktie else papier[:60], "art": a, "d": tag, "v": meldung,
                 "n": n_, "w": waehrung, "id": "SEDI-" + stueck[kennung], "q": URL}
            if a != "S":
                g["preis"] = preis
                g["wert"] = preis * n_
            aus.append(g)
            continue
        i += 1
    return aus


def main():
    args = sys.argv[1:]
    tage = 365
    if "--tage" in args:
        k = args.index("--tage")
        tage = int(args[k + 1])
        del args[k:k + 2]
    ziel, berichte = args[0], args[1:]
    ab = (dt.date.today() - dt.timedelta(days=tage)).isoformat()
    alt = []
    if os.path.exists(ziel):
        alt = json.load(open(ziel, encoding="utf-8")).get("geschaefte", [])
    alle = {g["id"]: g for g in alt}
    for b in berichte:
        neu = lesen(b)
        print(os.path.basename(b), len(neu), "Geschaefte,", sum(1 for g in neu if g["art"] == "K"), "Kaeufe,",
              sum(1 for g in neu if g["art"] == "V"), "Verkaeufe")
        for g in neu:
            alle[g["id"]] = g
    # Dasselbe Geschaeft melden oft Person und eigene Holdinggesellschaft („Sprott, Eric“ und „2176423 Ontario Ltd.“):
    # einmal behalten, bevorzugt die natuerliche Person (Name mit Komma)
    eins = {}
    for g in alle.values():
        if g["art"] == "S":
            eins[g["id"]] = g
            continue
        k = (g["e"], g["d"], g["n"], g.get("preis"), g["art"])
        alt_g = eins.get(k)
        if alt_g is None or ("," in g["p"] and "," not in alt_g["p"]):
            eins[k] = g
    liste = sorted((g for g in eins.values() if g["d"] >= ab), key=lambda g: (g["d"], g["v"]), reverse=True)
    stand = max((g["v"] for g in liste), default="")
    datei = {"stand": stand, "quelle": "SEDI, Weekly Summary by Insider (Canadian Securities Administrators)", "geschaefte": liste, "land": "CA"}
    json.dump(datei, open(ziel, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print(len(liste), "Geschaefte im Stand, Stand", stand)


if __name__ == "__main__":
    main()
