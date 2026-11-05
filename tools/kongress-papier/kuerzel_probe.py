# -*- coding: utf-8 -*-
"""Zufallsstichprobe der Geschaefte mit Kuerzel (alle Herkuenfte ausser ganz von Hand gelesenen Meldungen), um die
Kuerzel am Seitenbild zu pruefen: Liste fuer pruefbogen.py (Modus name) mit Name, Kuerzel und Herkunft des Kuerzels.
Aufruf: python -I kuerzel_probe.py <meldungen.json> <cache-ordner> <hand.json> <namen-bereinigt.json> <kuerzel.json>
        <anzahl> <startwert> <ziel.json>"""
import json
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import zusammen  # noqa: E402


def main():
    liste = json.load(open(sys.argv[1], encoding="utf-8"))
    cache = sys.argv[2]
    hand = json.load(open(sys.argv[3], encoding="utf-8"))["meldungen"]
    bereinigt = json.load(open(sys.argv[4], encoding="utf-8"))
    zusammen.guete_laden(sys.argv[4])
    kuerzel = json.load(open(sys.argv[5], encoding="utf-8"))
    anzahl, start, ziel = int(sys.argv[6]), int(sys.argv[7]), sys.argv[8]
    alle = []
    for m in liste:
        g, _, _, _ = zusammen.zeilen_der_meldung(m, cache, hand, bereinigt)
        for x in g:
            if x.get("quelle") == "hand" or x.get("z") is None:
                continue
            ku = kuerzel.get(x["w"], {})
            k = x.get("k", ku.get("k"))
            if k:
                alle.append({"id": m["id"], "s": x["s"], "z": x["z"], "name": x["w"], "k": k,
                             "t": x.get("t", ku.get("t")), "herkunft": ku.get("quelle", "hand")})
    alle.sort(key=lambda e: (e["id"], e["s"], e["z"]))
    random.Random(start).shuffle(alle)
    aus = alle[:anzahl]
    for i, e in enumerate(aus):
        e["nr"] = i + 1
    json.dump(aus, open(ziel, "w", encoding="utf-8"), ensure_ascii=False, indent=0)
    print("Geschaefte mit Kuerzel:", len(alle), "Stichprobe:", len(aus))


if __name__ == "__main__":
    main()
