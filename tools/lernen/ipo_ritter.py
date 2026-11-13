# -*- coding: utf-8 -*-
"""Baut assets/lernen/ipo.json aus den Tabellen von Jay R. Ritter (University of Florida, IPO Data).

Eingaben (PDF, von https://site.warrington.ufl.edu/ritter/ipo-data/ geladen):
  IPOs-Underpricing.pdf               Tabelle 1a: Anzahl, mittlere Rendite am ersten Tag, Anteil mit Verlust am ersten Tag
  IPOs-long-run-returns-on-IPOs.pdf   Tabelle 19: Anzahl, erster Tag, ein Jahr, drei Jahre (ab erstem Schluss, gegen den Markt)
Ausgabe je Jahr: n, t1 (Mittel erster Tag, %), neg (Anteil unter Ausgabepreis am ersten Tag, %), j1 (Mittel ein Jahr, %),
j3 (Mittel drei Jahre, %), m3 (drei Jahre minus Markt, %); fehlende Werte fehlen.

Aufruf: python -I ipo_ritter.py <Underpricing.pdf> <long-run.pdf> <ziel.json>
"""
import json
import re
import sys

import fitz


def zeilen(seite):
    w = seite.get_text("words")
    jahre = [(round((y0 + y1) / 2), x0, t) for x0, y0, x1, y1, t, *_ in w if re.fullmatch(r"(19|20)\d\d", t) and x0 < 110]
    aus = {}
    for y, _, t in jahre:
        aus[int(t)] = []
    for x0, y0, x1, y1, t, *_ in w:
        ym = (y0 + y1) / 2
        if re.fullmatch(r"(19|20)\d\d", t) and x0 < 110:
            continue
        best = min(jahre, key=lambda j: abs(j[0] - ym))
        if abs(best[0] - ym) <= 5 and x0 > 110:
            aus[int(best[2])].append((x0, t))
    return {j: [t for _, t in sorted(v)] for j, v in aus.items()}


def zahl(t):
    return float(t.replace("%", "").replace(",", ""))


def seite_mit(pdf, *teile):
    d = fitz.open(pdf)
    for i, p in enumerate(d):
        s = p.get_text()
        if all(t in s for t in teile):
            yield p


def main():
    up, lr, ziel = sys.argv[1:4]
    a = {}
    for p in seite_mit(up, "Table 1a", "Percentage with"):
        for j, v in zeilen(p).items():
            if len(v) >= 3 and 1980 <= j <= 2026:
                a[j] = v
    b = {}
    for p in seite_mit(lr, "Number", "adjusted", "Year"):
        z = zeilen(p)
        for j, v in z.items():
            if len(v) >= 6 and 1980 <= j <= 2026:
                b[j] = v
    jahre = []
    for j in sorted(a):
        va = a[j]
        e = {"j": j, "n": int(va[0].replace(",", "")), "t1": zahl(va[1]), "neg": zahl(va[2])}
        vb = b.get(j)
        if vb and int(vb[0].replace(",", "")) == e["n"] and abs(zahl(vb[1]) - e["t1"]) < 1.0:
            e["j1"] = zahl(vb[2])
            e["j3"] = zahl(vb[3])
            e["m3"] = zahl(vb[4])
        jahre.append(e)
    with open(ziel, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"quelle": "Jay R. Ritter, University of Florida", "jahre": jahre}, f, ensure_ascii=False, separators=(",", ":"))
    print(len(jahre), jahre[0], jahre[-1], sum(1 for e in jahre if "m3" in e))


main()
