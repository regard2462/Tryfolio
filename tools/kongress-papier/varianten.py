# -*- coding: utf-8 -*-
"""Weitere OCR-Fassungen der Datums- und Inhaberstreifen (andere Vergroesserung, schwarzweiss), damit lesen.py
zwischen mehreren Lesungen abstimmen kann. Liest <cache>/gitter/<id>.json und die entzerrten Seiten.
Schreibt <cache>/streifen/<id>/pNNN_<spalte>_v2.png (schwarzweiss, 2fach) und _v3.png (grau, 2,5fach).
Aufruf: python -I varianten.py <meldungen.json> <cache-ordner> [id ...]"""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tabelle as T  # noqa: E402

VARIANTEN = {"_v2": (2.0, 150), "_v3": (2.5, None)}
SPALTEN = ("inhaber", "datum", "gemeldet")


def streifen(a, x0, x1, y0, y1, faktor, schwelle, ziel):
    teil = a[int(y0):int(y1), int(x0) + 3:int(x1) - 2]
    if teil.size == 0:
        return
    d = teil < T.SCHWELLE
    ohne = T.linien_weg(d, quer_min=max(30, int(teil.shape[1] * 0.5)), laengs_min=40)
    if schwelle:
        out = np.where((teil < schwelle) & ~(d & ~ohne), 0, 255).astype(np.uint8)
    else:
        out = teil.copy()
        out[d & ~ohne] = 255
    img = Image.fromarray(np.pad(out, 20, constant_values=255))
    img = img.resize((int(img.width * faktor), int(img.height * faktor)), Image.BICUBIC)
    img.save(ziel)
    alt = os.path.splitext(ziel)[0] + ".json"
    if os.path.exists(alt):
        os.remove(alt)


def main():
    liste = json.load(open(sys.argv[1], encoding="utf-8"))
    cache = sys.argv[2]
    nur = set(sys.argv[3:])
    for m in liste:
        if nur and m["id"] not in nur:
            continue
        g = json.load(open(os.path.join(cache, "gitter", m["id"] + ".json"), encoding="utf-8"))
        st = os.path.join(cache, "streifen", m["id"])
        for s, info in g["seiten"].items():
            if not info.get("spalten") or not info.get("zeilen"):
                continue
            a = None
            y0 = min(z["y0"] for z in info["zeilen"])
            y1 = max(z["y1"] for z in info["zeilen"])
            for key in SPALTEN:
                c = info["spalten"].get(key)
                if not c:
                    continue
                for suffix, (faktor, schwelle) in VARIANTEN.items():
                    ziel = os.path.join(st, "p%03d_%s%s.png" % (int(s), key, suffix))
                    if os.path.exists(ziel) and os.path.getmtime(ziel) >= os.path.getmtime(
                            os.path.join(cache, "gitter", m["id"] + ".json")):
                        continue
                    if a is None:
                        a = np.array(Image.open(os.path.join(cache, "seiten", m["id"], "p%03d.png" % int(s))))
                    streifen(a, c[0], c[1], y0, y1, faktor, schwelle, ziel)
        print("ok", m["id"], flush=True)


if __name__ == "__main__":
    main()
