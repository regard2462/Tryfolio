#!/usr/bin/env python3
# Baut indikatoren/fed_punkte.json (app/src/main/assets/): Zinsprojektionen der Mitglieder des Offenmarktausschusses der
# Federal Reserve (Punktdiagramm), je Sitzung mit Projektionen.
#
# Quelle: Board of Governors of the Federal Reserve System, Summary of Economic Projections, Figure 2
# ("FOMC participants' assessments of appropriate monetary policy: Midpoint of target range or target level for the
# federal funds rate"), als Tabelle auf den Seiten
#   https://www.federalreserve.gov/monetarypolicy/fomcprojtablJJJJMMTT.htm
# Die Seiten stehen im Sitzungskalender https://www.federalreserve.gov/monetarypolicy/fomccalendars.htm (gemeinfrei).
#
# Je Sitzung: die Spalten (Jahre und "L" fuer den laengerfristigen Satz) und je Zinssatz mit mindestens einer Stimme die
# Zahl der Teilnehmer je Spalte. Saetze in Tausendstel Prozent.
#
# Aufruf:
#   python tools/daten/fed_punkte.py holen <Ordner>            Kalender und fehlende Seiten laden (eine Seite je Sekunde)
#   python tools/daten/fed_punkte.py bauen <Ordner> [Stand]    Datei aus den Seiten im Ordner bauen
#   python tools/daten/fed_punkte.py vergleichen <Ordner> <dotplot.csv>
#                                                             Gegenprobe mit einer CSV der Form date,midpoint,<Jahre>,longer_run
# Danach: python tools/daten/manifest.py bauen --signieren
# Pflege: nach den Sitzungen im Maerz, Juni, September und Dezember; die App holt eine neue Seite auch selbst.

import csv
import datetime
import glob
import html
import json
import os
import re
import sys
import time
import urllib.request

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
BASIS = "https://www.federalreserve.gov/monetarypolicy/"
SEITE = re.compile(r"fomcprojtabl(\d{4})(\d{2})(\d{2})\.htm")


def repo():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def laden(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "text/html, */*"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def holen(ordner):
    os.makedirs(ordner, exist_ok=True)
    kalender = laden(BASIS + "fomccalendars.htm")
    with open(os.path.join(ordner, "kalender.htm"), "wb") as f:
        f.write(kalender)
    for name in sorted(set(m.group(0) for m in SEITE.finditer(kalender.decode("utf-8", "replace")))):
        ziel = os.path.join(ordner, name)
        if os.path.exists(ziel):
            continue
        time.sleep(1)
        with open(ziel, "wb") as f:
            f.write(laden(BASIS + name))
        print(name)


def zelle(s):
    return html.unescape(re.sub(r"<[^>]+>", "", s)).replace("\xa0", " ").strip()


def tabelle(text):
    """Figure 2 einer Seite: (Spalten, {Satz in Tausendstel: [Stimmen je Spalte]}); None ohne Tabelle"""
    i = text.find("Midpoint of target range or target level for the federal funds rate")
    if i < 0:
        return None
    a = text.find("<table", i)
    b = text.find("</table>", a)
    if a < 0 or b < 0:
        return None
    zeilen = re.findall(r"<tr[^>]*>(.*?)</tr>", text[a:b], re.S | re.I)
    if len(zeilen) < 2:
        return None
    kopf = [zelle(z) for z in re.findall(r"<t[hd][^>]*>(.*?)</t[hd]>", zeilen[0], re.S | re.I)][1:]
    spalten = []
    for k in kopf:
        if re.fullmatch(r"\d{4}", k):
            spalten.append(k)
        elif k.lower().startswith("longer"):
            spalten.append("L")
        else:
            return None
    punkte = {}
    for z in zeilen[1:]:
        t = [zelle(c) for c in re.findall(r"<t[hd][^>]*>(.*?)</t[hd]>", z, re.S | re.I)]
        if len(t) != len(spalten) + 1:
            continue
        try:
            satz = int(round(float(t[0]) * 1000))
        except ValueError:
            continue
        stimmen = [int(x) if x.isdigit() else 0 for x in t[1:]]
        if any(stimmen):
            punkte[satz] = stimmen
    return (spalten, punkte) if punkte else None


def sitzungen(ordner):
    aus = []
    for p in sorted(glob.glob(os.path.join(ordner, "fomcprojtabl*.htm"))):
        m = SEITE.search(os.path.basename(p))
        if not m:
            continue
        with open(p, encoding="utf-8", errors="replace") as f:
            t = tabelle(f.read())
        if t is None:
            print("ohne Tabelle:", os.path.basename(p))
            continue
        aus.append(("%s-%s-%s" % m.groups(), t[0], t[1]))
    return aus


def bauen(ordner, stand):
    liste = []
    for tag, spalten, punkte in sitzungen(ordner):
        liste.append({"tag": tag, "j": spalten, "p": [[s] + punkte[s] for s in sorted(punkte, reverse=True)]})
    if not liste:
        raise SystemExit("keine Seiten in " + ordner)
    aus = {
        "stand": stand,
        "quelle": "Board of Governors of the Federal Reserve System, Summary of Economic Projections",
        "adresse": BASIS + "fomccalendars.htm",
        "hinweis": "je Sitzung: j Spalten (Jahr, L = laengerfristig), p Zeilen aus Satz in Tausendstel Prozent und Zahl der Teilnehmer je Spalte",
        "sitzungen": liste,
    }
    text = json.dumps(aus, ensure_ascii=False, separators=(",", ":"))
    ziel = os.path.join(repo(), "app", "src", "main", "assets", "indikatoren", "fed_punkte.json")
    with open(ziel, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print("Sitzungen", len(liste), liste[0]["tag"], "bis", liste[-1]["tag"], "Bytes", len(text.encode("utf-8")))


def vergleichen(ordner, pfad):
    eigene = {tag: (spalten, punkte) for tag, spalten, punkte in sitzungen(ordner)}
    fremd = {}
    with open(pfad, encoding="utf-8") as f:
        for r in csv.DictReader(f):
            satz = int(round(float(r["midpoint"]) * 1000))
            for k, v in r.items():
                if k in ("date", "midpoint") or not (v or "").strip():
                    continue
                fremd.setdefault(r["date"], {})[(satz, "L" if k == "longer_run" else k)] = int(float(v))
    gleich = 0
    for tag in sorted(set(eigene) | set(fremd)):
        if tag not in eigene or tag not in fremd:
            print(tag, "nur in", "den Seiten" if tag in eigene else "der CSV")
            continue
        spalten, punkte = eigene[tag]
        e = {(s, spalten[i]): n for s, st in punkte.items() for i, n in enumerate(st) if n}
        if e == fremd[tag]:
            gleich += 1
        else:
            print(tag, "weicht ab:", sorted(set(e.items()) ^ set(fremd[tag].items()))[:6])
    print("gleich", gleich, "von", len(set(eigene) | set(fremd)))


if __name__ == "__main__":
    if len(sys.argv) < 3 or sys.argv[1] not in ("holen", "bauen", "vergleichen"):
        raise SystemExit("Aufruf: fed_punkte.py holen <Ordner> | bauen <Ordner> [Stand] | vergleichen <Ordner> <dotplot.csv>")
    if sys.argv[1] == "holen":
        holen(sys.argv[2])
    elif sys.argv[1] == "bauen":
        bauen(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else datetime.date.today().isoformat())
    else:
        vergleichen(sys.argv[2], sys.argv[3])
