# -*- coding: utf-8 -*-
"""Rendert alle Seiten der Papiermeldungen als Graustufen-PNG (200 dpi) fuer Sichtpruefung und Gitter.
Vorhandene Bilder bleiben. Aufruf: python -I seiten.py <meldungen.json> <pdf-ordner> <bild-ordner> [id ...]"""
import json
import os
import sys

import fitz

DPI = 200


def main():
    liste = json.load(open(sys.argv[1], encoding="utf-8"))
    pdfdir, ziel = sys.argv[2], sys.argv[3]
    nur = set(sys.argv[4:])
    for m in liste:
        if nur and m["id"] not in nur:
            continue
        p = os.path.join(pdfdir, m["id"] + ".pdf")
        if not os.path.exists(p):
            continue
        doc = fitz.open(p)
        od = os.path.join(ziel, m["id"])
        os.makedirs(od, exist_ok=True)
        for i, pg in enumerate(doc):
            f = os.path.join(od, "p%03d.png" % (i + 1))
            if os.path.exists(f):
                continue
            pg.get_pixmap(matrix=fitz.Matrix(DPI / 72, DPI / 72), colorspace=fitz.csGRAY, alpha=False).save(f)
    print("ok")


if __name__ == "__main__":
    main()
