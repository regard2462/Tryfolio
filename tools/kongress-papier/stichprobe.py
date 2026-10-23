# -*- coding: utf-8 -*-
"""Zieht eine Zufallsstichprobe aus den rein automatisch gelesenen Geschaeften (ohne Handkorrektur, fester Startwert) und schreibt sie als
Liste fuer pruefbogen.py. Die Pruefung am Seitenbild wird von Hand in <stichprobe-ergebnis.json> eingetragen:
je Nummer die Felder, die falsch gelesen sind (leere Liste = alles richtig).
Aufruf: python -I stichprobe.py <meldungen.json> <cache-ordner> <hand.json> <namen-bereinigt.json> <anzahl> <startwert>
        <ziel-liste.json>"""
import json
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import zusammen as Z  # noqa: E402


def main():
    liste = json.load(open(sys.argv[1], encoding="utf-8"))
    cache = sys.argv[2]
    hand = json.load(open(sys.argv[3], encoding="utf-8"))["meldungen"]
    bereinigt = json.load(open(sys.argv[4], encoding="utf-8"))
    Z.guete_laden(sys.argv[4])
    anzahl, start, ziel = int(sys.argv[5]), int(sys.argv[6]), sys.argv[7]
    alle = []
    for m in liste:
        g, _, _, _ = Z.zeilen_der_meldung(m, cache, hand, bereinigt)
        for x in g:
            if x["quelle"] == "ocr":
                alle.append({"id": m["id"], "name": m["name"], "s": x["s"], "z": x["z"], "i": x["i"], "w": x["w"],
                             "a": x["a"], "d": x["d"], "g": x.get("g"), "betrag": x["betrag"]})
    random.seed(start)
    probe = random.sample(alle, min(anzahl, len(alle)))
    json.dump(probe, open(ziel, "w", encoding="utf-8"), ensure_ascii=False, indent=0)
    print("Grundgesamtheit:", len(alle), "Stichprobe:", len(probe))


if __name__ == "__main__":
    main()
