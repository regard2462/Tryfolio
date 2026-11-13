#!/usr/bin/env python3
# Setzt in den Lektionen des Finanzwissens das Feld rechtsstand (ISO-Datum), wo eine Lektion Rechts- oder Zahlenaussagen mit
# Jahresbezug enthaelt (Steuer- und Abgabenwerte, Sozialversicherungsgrenzen, "Stand ..."-Angaben). Die App zeigt ab dem
# 1. Januar des Folgejahres einen Hinweis im Kopf der Lektion.
#
#   python tools/daten/rechtsstand.py [JJJJ-MM-TT]       neue Lektionen bekommen das Datum (Standard: Stand in kurs.json,
#                                                       Monatsanfang); vorhandene Werte bleiben
#   python tools/daten/rechtsstand.py --pruefen         nur anzeigen, welche Lektionen neu einen Rechtsstand bekaemen
#   python tools/daten/rechtsstand.py --hochsetzen JJJJ-MM-TT ID [ID ...]
#                                                       nach Durchsicht: Rechtsstand dieser Lektionen auf das Datum setzen
#   python tools/daten/rechtsstand.py --hochsetzen JJJJ-MM-TT --alle
#                                                       nach Durchsicht aller Lektionen: jeden vorhandenen Rechtsstand hochsetzen
#
# Behandelt data/lektionen (deutsch), data/en/lektionen (dieselben Kennungen wie deutsch), data/us/lektionen und
# data/en/us/lektionen (alle Lektionen der US-Fassung). Die englische Fassung traegt immer dasselbe Datum wie die deutsche,
# die englische US-Fassung dasselbe wie die deutsche US-Fassung.

import glob
import json
import os
import re
import sys

WURZEL = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "assets", "finanzwissen"))

# Ausdruecklicher Stand im Text
STAND = re.compile(r"Rechtsstand|\bStand[: ]+(20\d\d|Januar|Februar|März|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember)\b", re.IGNORECASE)
# Begriffe, deren Werte jedes Jahr neu festgesetzt werden (Teilwort genuegt, also auch Beitragsbemessungsgrenze, Sparerpauschbetrag)
STARK = re.compile(
    r"Pauschbetrag|Basiszins|Bemessungsgrenze|Grundfreibetrag|Kindergeld|Abgeltungsteuer|Vorabpauschale|Teilfreistellung"
    r"|Zusatzbeitrag|Beitragssatz|Mindestlohn|Minijob|Rentenwert|Kinderfreibetrag|Freigrenze|Freibetrag|Höchstbetrag"
    r"|Versicherungspflichtgrenze|Jahresarbeitsentgeltgrenze|Bürgergeld|Regelsatz|Zulage|Spitzensteuersatz|Solidaritätszuschlag"
    r"|Standard Deduction|Wage Base|Annual Exclusion|Contribution Limit|Catch-up|Tax Bracket|Exemption Amount",
    re.IGNORECASE)
JAHR = re.compile(r"\b20(2[3-9])\b")
DATUM = re.compile(r"^\d{4}-\d{2}-\d{2}$")


def betroffen(text):
    return bool(STAND.search(text) or (STARK.search(text) and JAHR.search(text)))


def lesen(pfad):
    with open(pfad, "r", encoding="utf-8", newline="") as f:
        return f.read()


def schreiben(pfad, roh):
    json.loads(roh)
    with open(pfad, "w", encoding="utf-8", newline="\n") as f:
        f.write(roh)


def wert(roh):
    m = re.search(r'"rechtsstand"\s*:\s*"([^"]*)"', roh)
    return m.group(1) if m else None


def datei_setzen(pfad, stand, ueberschreiben=False):
    """Setzt rechtsstand; ohne ueberschreiben nur, wenn das Feld fehlt. True, wenn die Datei sich aendert."""
    roh = lesen(pfad)
    alt = wert(roh)
    if alt is not None:
        if not ueberschreiben or alt == stand:
            return False
        schreiben(pfad, re.sub(r'("rechtsstand"\s*:\s*)"[^"]*"', lambda m: m.group(1) + '"' + stand + '"', roh, count=1))
        return True
    # Textuell nach der Kennung einfuegen, damit Aufbau und Schreibweise der Datei bleiben
    m = re.match(r'^(\{\s*"id"\s*:\s*"[^"]*"\s*,)(\s*)', roh)
    if not m:
        raise SystemExit("Kennung nicht gefunden: " + pfad)
    sep = ": " if re.match(r'^\{\s*"id": ', roh) else ":"
    schreiben(pfad, m.group(1) + m.group(2) + '"rechtsstand"' + sep + '"' + stand + '",' + m.group(2) + roh[m.end():])
    return True


def ordner(*teile):
    return sorted(glob.glob(os.path.join(WURZEL, "data", *teile, "*.json")))


def gegenstueck(pfad, *teile):
    p = os.path.join(WURZEL, "data", *teile, os.path.basename(pfad))
    return p if os.path.exists(p) else None


def angleichen(quelle_teile, ziel_teile, zaehler):
    """Gegenstueck in der anderen Sprache bekommt dasselbe Datum wie die Quelle."""
    for p in ordner(*quelle_teile):
        s = wert(lesen(p))
        z = gegenstueck(p, *ziel_teile)
        if s and z and datei_setzen(z, s, True):
            zaehler.append(os.path.relpath(z, os.path.join(WURZEL, "data")).replace(os.sep, "/"))


def standard_stand():
    with open(os.path.join(WURZEL, "data", "kurs.json"), "r", encoding="utf-8") as f:
        return json.load(f)["stand"] + "-01"


def neue_setzen(stand, nur_zeigen):
    neu = []
    for p in ordner("lektionen"):
        roh = lesen(p)
        if wert(roh) is None and betroffen(roh):
            if nur_zeigen or datei_setzen(p, stand):
                neu.append("lektionen/" + os.path.basename(p))
    for p in ordner("us", "lektionen"):
        if wert(lesen(p)) is None and (nur_zeigen or datei_setzen(p, stand)):
            neu.append("us/lektionen/" + os.path.basename(p))
    if nur_zeigen:
        for n in neu:
            print("neu: " + n)
        return
    angleichen(("lektionen",), ("en", "lektionen"), neu)
    angleichen(("us", "lektionen"), ("en", "us", "lektionen"), neu)
    for n in neu:
        print("gesetzt: " + n)
    print("rechtsstand " + stand + ": " + str(len(neu)) + " Dateien")


def hochsetzen(stand, ids, alle):
    geaendert = []
    for teile in (("lektionen",), ("us", "lektionen")):
        for p in ordner(*teile):
            kennung = os.path.basename(p)[:-5]
            if wert(lesen(p)) is None:
                continue
            if (alle or kennung in ids) and datei_setzen(p, stand, True):
                geaendert.append("/".join(teile) + "/" + os.path.basename(p))
    angleichen(("lektionen",), ("en", "lektionen"), geaendert)
    angleichen(("us", "lektionen"), ("en", "us", "lektionen"), geaendert)
    for n in geaendert:
        print("hochgesetzt: " + n)
    print("rechtsstand " + stand + ": " + str(len(geaendert)) + " Dateien")


def main():
    args = sys.argv[1:]
    if args[:1] == ["--pruefen"]:
        neue_setzen(standard_stand(), True)
    elif args[:1] == ["--hochsetzen"]:
        if len(args) < 3 or not DATUM.match(args[1]):
            raise SystemExit("Aufruf: --hochsetzen JJJJ-MM-TT ID [ID ...] | --alle")
        hochsetzen(args[1], set(args[2:]), args[2:] == ["--alle"])
    else:
        stand = args[0] if args else standard_stand()
        if not DATUM.match(stand):
            raise SystemExit("Datum JJJJ-MM-TT erwartet")
        neue_setzen(stand, False)


if __name__ == "__main__":
    main()
