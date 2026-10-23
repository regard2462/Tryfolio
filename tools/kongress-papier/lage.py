# -*- coding: utf-8 -*-
"""Lage jeder Seite bestimmen (0, 90, 180 oder 270 Grad gegen den Uhrzeigersinn drehen, damit die Schrift waagrecht
von links nach rechts laeuft). Verfahren: Seite klein in jeder Drehung rendern, Windows-OCR laufen lassen und
zaehlen, wie viele erkannte Woerter bekannte Formularwoerter, Daten oder Betraege sind. Die Drehung mit den
meisten Treffern gewinnt.
Schritt 1 (Bilder): python -I lage.py bilder <meldungen.json> <pdf-ordner> <lage-ordner>
Schritt 2 (OCR):    powershell -NoProfile -ExecutionPolicy Bypass -File winocr.ps1 <lage-ordner>\\<id>  (je Meldung)
Schritt 3 (Wahl):   python -I lage.py werten <meldungen.json> <lage-ordner> <pdf-ordner> <lage.json>"""
import json
import os
import re
import sys

import fitz
import numpy as np

DPI = 150
DREHUNGEN = (0, 90, 180, 270)
WORTE = set("""PURCHASE SALE EXCHANGE ASSET NAME TRANSACTION TRANSACTIONS AMOUNT DATE FULL INC CMN CORP CORPORATION
STOCK COMMON PLC TRUST FUND ETF LLC CO PARTIAL OVER NOTIFIED PROVIDE TICKER SYMBOL OF THE AND PERIODIC REPORT
HOUSE REPRESENTATIVES UNITED STATES SPOUSE DEPENDENT CHILD BOND BONDS SHARES CLASS GROUP HOLDINGS INCOME
MUNICIPAL TAX REVENUE BANK INDEX SERIES FINANCIAL ENERGY CAPITAL GAINS EXCEED TYPE ACTION OFFICE TELEPHONE MEMBER
STATE DISTRICT FILE ORIGINAL COPIES INITIAL AMENDMENT PAGE LINKED BASKET INDICES HYBRID NOTE NOTES DUE SELL BUY
SOLD BOUGHT SECURITY DESCRIPTION QUANTITY PRICE ACCOUNT TOTAL TRADE SETTLEMENT SHARE TECHNOLOGIES INTERNATIONAL
SYSTEMS PUBLIC OFFERING YES NO COMMITTEE ETHICS GUIDANCE PENALTY ASSESSED LATE DAYS EMPLOYING OFFICER EMPLOYEE""".split())
DATUM = re.compile(r"^\d{1,2}/\d{1,2}/\d{2,4}$")
BETRAG = re.compile(r"^\$?\d{1,3}(,\d{3})+")


def bilder(meldungen, pdfdir, ziel):
    for m in json.load(open(meldungen, encoding="utf-8")):
        p = os.path.join(pdfdir, m["id"] + ".pdf")
        if not os.path.exists(p):
            continue
        od = os.path.join(ziel, m["id"])
        os.makedirs(od, exist_ok=True)
        doc = fitz.open(p)
        for i, pg in enumerate(doc):
            fertig = all(os.path.exists(os.path.join(od, "p%03d_r%03d.png" % (i + 1, r))) for r in DREHUNGEN)
            if fertig:
                continue
            pix = pg.get_pixmap(matrix=fitz.Matrix(DPI / 72, DPI / 72), colorspace=fitz.csGRAY, alpha=False)
            a = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width)
            for r in DREHUNGEN:
                b = np.ascontiguousarray(np.rot90(a, k=r // 90))
                f = os.path.join(od, "p%03d_r%03d.png" % (i + 1, r))
                fitz.Pixmap(fitz.csGRAY, b.shape[1], b.shape[0], b.tobytes(), False).save(f)
    print("ok")


def punkte(js):
    n = 0
    for z in js.get("zeilen", []):
        for w in z["w"]:
            t = w[4].strip()
            tu = re.sub(r"[^A-Z]", "", t.upper())
            if tu in WORTE and len(tu) >= 2:
                n += 1
            elif DATUM.match(t) or BETRAG.match(t):
                n += 2
    return n


def werten(meldungen, ordner, pdfdir, ziel):
    aus = json.load(open(ziel, encoding="utf-8")) if os.path.exists(ziel) else {}
    for m in json.load(open(meldungen, encoding="utf-8")):
        od = os.path.join(ordner, m["id"])
        if not os.path.isdir(od):
            continue
        seiten = {}
        for f in sorted(os.listdir(od)):
            mt = re.match(r"p(\d{3})_r(\d{3})\.json$", f)
            if not mt:
                continue
            s, r = int(mt.group(1)), int(mt.group(2))
            seiten.setdefault(s, {})[r] = punkte(json.load(open(os.path.join(od, f), encoding="utf-8-sig")))
        # Seiten mit wenig Text oder Gleichstand: die haeufigste sichere Drehung der Meldung bei gleichem Seitenformat
        doc = fitz.open(os.path.join(pdfdir, m["id"] + ".pdf"))
        hoch = {}
        for s in seiten:
            r = doc[s - 1].rect
            hoch[s] = r.height > r.width
        sicher, unsicher = {}, set()
        for s, v in seiten.items():
            p = sorted(v.values(), reverse=True)
            if p[0] >= 5 and p[1] < 0.6 * p[0]:
                sicher[s] = max(v, key=v.get)
            else:
                unsicher.add(s)
        aus[m["id"]] = {}
        for s, v in sorted(seiten.items()):
            dreh, grund = max(v, key=v.get), "ocr"
            if s in unsicher:
                gleich = [sicher[t] for t in sicher if hoch[t] == hoch[s]]
                if gleich:
                    dreh, grund = max(set(gleich), key=gleich.count), "meldung"
            aus[m["id"]][str(s)] = {"drehung": dreh, "punkte": v, "grund": grund}
    json.dump(aus, open(ziel, "w", encoding="utf-8"), indent=0)
    print("ok", len(aus))


if __name__ == "__main__":
    if sys.argv[1] == "bilder":
        bilder(*sys.argv[2:5])
    else:
        werten(*sys.argv[2:6])
