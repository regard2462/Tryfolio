# -*- coding: utf-8 -*-
"""Netto-Leerverkaufspositionen in schwedischen Aktien fuer die App (assets/copytrading/leerpositionen_se.json).

Quelle ist das Blankningsregister der Finansinspektionen (FI), offene Daten zur freien Nutzung mit Angabe von Quelle
und Datum. Uebernommen werden je Aktie die offenen Positionen ab 0,5 % (Halter, Anteil, Datum) und die Summe der
veroeffentlichten Positionen fuer jede Woche des letzten Jahres, gerechnet aus der Datei der frueheren Positionen.
Die App holt danach nur die aktuellen Positionen nach (GetAktuellFile) und haengt die Summe des Tages an.

Aufruf: python holen.py <ziel.json>
"""
import bisect
import datetime as dt
import io
import json
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

UA = {"User-Agent": "Tryfolio/1.0 (open-source practice depot)"}
AKTUELL = "https://www.fi.se/BlankningsRegister/GetAktuellFile"
HISTORISCH = "https://www.fi.se/BlankningsRegister/GetHistFile"
WOCHEN = 52

T = "{urn:oasis:names:tc:opendocument:xmlns:table:1.0}"
O = "{urn:oasis:names:tc:opendocument:xmlns:office:1.0}"
X = "{urn:oasis:names:tc:opendocument:xmlns:text:1.0}"


def holen(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=300) as r:
        return r.read()


def zeilen(ods):
    """Zeilen der ersten Tabelle als Listen von Texten; Zahlenzellen als Zahl im Text."""
    with zipfile.ZipFile(io.BytesIO(ods)) as z:
        roh = z.read("content.xml")
    for _, el in ET.iterparse(io.BytesIO(roh), events=("end",)):
        if el.tag != T + "table-row":
            continue
        zeile = []
        for zelle in el:
            if zelle.tag not in (T + "table-cell", T + "covered-table-cell"):
                continue
            n = int(zelle.get(T + "number-columns-repeated", "1"))
            wert = zelle.get(O + "value")
            if wert is None:
                wert = zelle.get(O + "date-value")
            if wert is None:
                wert = " ".join("".join(p.itertext()) for p in zelle.findall(X + "p"))
            zeile.extend([wert] * min(n, 20))
        el.clear()
        yield zeile


def prozent(s):
    s = (s or "").strip().replace("\xa0", "")
    if s.startswith("<"):
        return 0.0
    try:
        return float(s.replace(",", "."))
    except ValueError:
        return None


def positionen(ods):
    """(Halter, Emittent, ISIN, Prozent, Datum) ab der Kopfzeile mit ISIN."""
    kopf = None
    for z in zeilen(ods):
        if kopf is None:
            if any((c or "").strip() == "ISIN" for c in z):
                kopf = [(c or "").strip() for c in z]
            continue
        if len(z) < 5 or not (z[2] or "").strip():
            continue
        r = prozent(z[3])
        if r is None:
            continue
        yield (z[0].replace("\xa0", " ").strip(), z[1].replace("\xa0", " ").strip(), z[2].strip(), r, z[4].strip()[:10])


def main():
    heute = dt.date.today()
    aktuell = list(positionen(holen(AKTUELL)))
    historisch = list(positionen(holen(HISTORISCH)))
    reihen = {}
    namen = {}
    for h, e, isin, r, d in historisch + aktuell:
        namen[isin] = e
        reihen.setdefault((isin, h.lower()), []).append((d, r))
    offen_paare = {(isin, h.lower()) for h, e, isin, r, d in aktuell if r > 0}
    tage = [(heute - dt.timedelta(days=7 * w)).isoformat() for w in range(WOCHEN, -1, -1)]
    summen = {}
    for paar, liste in reihen.items():
        isin = paar[0]
        liste.sort()
        # Nicht mehr offene Positionen ohne Schlussmeldung enden nach ihrer letzten Meldung
        if paar not in offen_paare and liste[-1][1] > 0:
            ende = (dt.date.fromisoformat(liste[-1][0]) + dt.timedelta(days=1)).isoformat()
            liste.append((ende, 0.0))
        starts = [x[0] for x in liste]
        s = summen.setdefault(isin, [0.0] * len(tage))
        for j, tag in enumerate(tage):
            k = bisect.bisect_right(starts, tag) - 1
            if k >= 0:
                s[j] += liste[k][1]
    offen = {}
    for h, e, isin, r, d in aktuell:
        if r > 0:
            offen.setdefault(isin, []).append({"h": h, "r": r, "d": d})
    titel = {}
    for isin, s in summen.items():
        pos = sorted(offen.get(isin, []), key=lambda p: -p["r"])
        if pos:
            s[-1] = sum(p["r"] for p in pos)
        else:
            s[-1] = 0.0
        if not pos and not any(s):
            continue
        titel[isin] = {"e": namen[isin], "positionen": pos, "verlauf": [{"t": t, "s": round(v, 2)} for t, v in zip(tage, s)]}
    with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
        json.dump({"stand": heute.isoformat(), "land": "SE", "titel": titel}, f, ensure_ascii=False, separators=(",", ":"))
    print("fertig:", len(titel), "Aktien,", sum(len(t["positionen"]) for t in titel.values()), "offene Positionen", file=sys.stderr)


if __name__ == "__main__":
    main()
