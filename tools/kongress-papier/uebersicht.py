# -*- coding: utf-8 -*-
"""Uebersicht ueber die geladenen PDFs: Seitenzahl, Seitengroesse, Textebene, Bildaufloesung.
Aufruf: python -I uebersicht.py <meldungen.json> <pdf-ordner> <ziel.json>"""
import json
import os
import sys

import fitz


def main():
    liste = json.load(open(sys.argv[1], encoding="utf-8"))
    ordner, ziel = sys.argv[2], sys.argv[3]
    aus = {}
    for m in liste:
        p = os.path.join(ordner, m["id"] + ".pdf")
        if not os.path.exists(p):
            print("fehlt", m["id"])
            continue
        doc = fitz.open(p)
        seiten = []
        for pg in doc:
            bilder = pg.get_images()
            seiten.append({
                "w": round(pg.rect.width), "h": round(pg.rect.height), "rot": pg.rotation,
                "text": len(pg.get_text().strip()),
                "bild": [bilder[0][2], bilder[0][3]] if bilder else None,
            })
        aus[m["id"]] = {"name": m["name"], "seiten": len(seiten), "s": seiten}
        print(m["name"], m["id"], m["eingang"], len(seiten), "Seiten",
              "Text" if any(s["text"] for s in seiten) else "", seiten[0]["w"], seiten[0]["h"], seiten[0]["bild"])
    json.dump(aus, open(ziel, "w", encoding="utf-8"), ensure_ascii=False, indent=0)


if __name__ == "__main__":
    main()
