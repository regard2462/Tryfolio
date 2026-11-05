# -*- coding: utf-8 -*-
"""Pruefbild: erkannte Zeilen (gruen, Balken rot) und gedeutete Spalten (blau, Beschriftung) ueber der Seite.
Aufruf: python -I zeigen.py <cache-ordner> <id> <seite> <ziel.png> [y0 y1]"""
import json
import os
import sys

from PIL import Image, ImageDraw


def main():
    cache, mid, seite, ziel = sys.argv[1:5]
    g = json.load(open(os.path.join(cache, "gitter", mid + ".json"), encoding="utf-8"))["seiten"][seite]
    img = Image.open(os.path.join(cache, "seiten", mid, "p%03d.png" % int(seite))).convert("RGB")
    dr = ImageDraw.Draw(img)
    for z in g.get("zeilen", []):
        dr.rectangle([5, z["y0"], img.width - 5, z["y1"]], outline=(255, 0, 0) if z["balken"] else (0, 170, 0), width=2)
    for key, c in (g.get("spalten") or {}).items():
        if not c:
            continue
        dr.line([c[0], 0, c[0], img.height], fill=(0, 0, 255), width=1)
        dr.text((c[0] + 2, 5 + 12 * (hash(key) % 4)), key.replace("betrag_", "").replace("art_", ""), fill=(0, 0, 255))
    for c in g.get("cols", []) if not g.get("spalten") else []:
        dr.line([c[0], 0, c[0], img.height], fill=(255, 0, 255), width=1)
    if len(sys.argv) > 6:
        img = img.crop((0, int(sys.argv[5]), img.width, int(sys.argv[6])))
    img.save(ziel)


if __name__ == "__main__":
    main()
