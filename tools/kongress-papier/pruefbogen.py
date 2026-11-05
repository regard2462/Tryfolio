# -*- coding: utf-8 -*-
"""Pruefbogen: schneidet Tabellenzeilen aus den entzerrten Seiten und setzt sie mit laufender Nummer untereinander,
damit sie am Bildschirm gegen die Lesung geprueft werden koennen (Stichprobe, Zeilen mit Befund „pruefen“).
Eingabe: JSON-Liste von Eintraegen {"id", "s", "z"} (Meldung, Seite, Zeile aus <cache>/zeilen/<id>.json).
Ausgabe: <ziel>_NN.png mit je bis zu 22 Zeilen und <ziel>.json mit der Lesung je Nummer.
Mit „name“ als viertem Argument wird nur die Namensspalte ausgeschnitten (doppelte Groesse, 30 je Bogen), mit
„datum“ der Anfang des Namens neben Datum und Benachrichtigung.
Aufruf: python -I pruefbogen.py <liste.json> <cache-ordner> <ziel-praefix> [name|datum]"""
import json
import os
import sys

from PIL import Image, ImageDraw

JE_BOGEN = 22


def main():
    liste = json.load(open(sys.argv[1], encoding="utf-8"))
    cache, ziel = sys.argv[2], sys.argv[3]
    nur_name = len(sys.argv) > 4 and sys.argv[4] == "name"
    nur_datum = len(sys.argv) > 4 and sys.argv[4] == "datum"
    global JE_BOGEN
    if nur_name or nur_datum:
        JE_BOGEN = 30
    zeilen_cache, gitter_cache = {}, {}
    teile, lesung = [], []
    for nr, e in enumerate(liste, start=1):
        mid, s, z = e["id"], int(e["s"]), int(e["z"])
        if mid not in zeilen_cache:
            zeilen_cache[mid] = json.load(open(os.path.join(cache, "zeilen", mid + ".json"), encoding="utf-8"))
            gitter_cache[mid] = json.load(open(os.path.join(cache, "gitter", mid + ".json"), encoding="utf-8"))
        r = next(x for x in zeilen_cache[mid]["zeilen"] if x["s"] == s and x["z"] == z)
        g = gitter_cache[mid]["seiten"][str(s)]
        cols = g["cols"]
        x0 = int(cols[0][0]) - 5
        x1 = int(cols[-1][1]) + 5
        if nur_name:
            x0, x1 = int(g["spalten"]["name"][0]) - 3, int(g["spalten"]["name"][1]) + 3
        img = Image.open(os.path.join(cache, "seiten", mid, "p%03d.png" % s)).convert("L")
        y0, y1 = max(0, r["y0"] - 6), min(img.height, r["y1"] + 6)
        if nur_datum:
            # Name (gekuerzt), Datum und Benachrichtigung nebeneinander, doppelte Groesse
            n0, n1 = int(g["spalten"]["name"][0]), int(g["spalten"]["name"][1])
            d0, d1 = int(g["spalten"]["datum"][0]) - 3, int(g["spalten"]["gemeldet"][1]) + 3
            nb = min(n1 - n0, 260)
            teil = Image.new("L", (nb + 20 + d1 - d0, y1 - y0), 255)
            teil.paste(img.crop((n0, y0, n0 + nb, y1)), (0, 0))
            teil.paste(img.crop((d0, y0, d1, y1)), (nb + 20, 0))
            teil = teil.resize((teil.width * 2, teil.height * 2))
        else:
            teil = img.crop((x0, y0, x1, y1))
        if nur_name:
            teil = teil.resize((teil.width * 2, teil.height * 2))
        # Kopf der Spalten (erste Zeile der Tabelle) fuer die Orientierung nicht noetig; Nummer links davor
        lesung.append({"nr": nr, "id": mid, "s": s, "z": z, "inhaber": r.get("inhaber"), "name": r.get("name_roh"),
                       "art": r.get("art"), "datum": r.get("datum"), "gemeldet": r.get("gemeldet"),
                       "betrag": r.get("betrag"), "probleme": r.get("probleme")})
        teile.append((nr, teil))
    for b in range(0, len(teile), JE_BOGEN):
        stueck = teile[b:b + JE_BOGEN]
        breite = max(t.width for _, t in stueck) + 70
        hoehe = sum(t.height + 8 for _, t in stueck)
        bogen = Image.new("L", (breite, hoehe), 255)
        dr = ImageDraw.Draw(bogen)
        y = 0
        for nr, t in stueck:
            bogen.paste(t, (70, y))
            klein = Image.new("L", (22, 12), 255)
            ImageDraw.Draw(klein).text((1, 0), str(nr), fill=0)
            gross = klein.resize((66, 36))
            bogen.paste(gross, (2, y + max(0, t.height // 2 - 18)))
            dr.line([0, y + t.height + 4, breite, y + t.height + 4], fill=128)
            y += t.height + 8
        bogen.save("%s_%02d.png" % (ziel, b // JE_BOGEN + 1))
    json.dump(lesung, open(ziel + ".json", "w", encoding="utf-8"), ensure_ascii=False, indent=0)
    print("Boegen:", (len(teile) + JE_BOGEN - 1) // JE_BOGEN)


if __name__ == "__main__":
    main()
