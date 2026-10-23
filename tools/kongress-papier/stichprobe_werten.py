# -*- coding: utf-8 -*-
"""Vergleicht die Stichprobe (Meldung, Seite, Zeile) mit den am Seitenbild gelesenen Werten und schreibt je Zeile die
falschen Felder. Grundlage ist der aktuelle Stand der Kette (zusammen.zeilen_der_meldung) und, fuer das Kuerzel,
die Ergebnisdatei. Namen gelten als richtig, wenn Buchstaben und Ziffern gleich sind (Gross- und Kleinschreibung,
Leer- und Satzzeichen zaehlen nicht). Eine fehlende Benachrichtigung zaehlt nicht als Fehler, eine falsche schon.
Aufruf: python -I stichprobe_werten.py <meldungen.json> <cache-ordner> <hand.json> <namen-bereinigt.json>
        <stichprobe.json> <stichprobe-wahr.json> <ziel.json>"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import zusammen as Z  # noqa: E402


def gleich_name(a, b):
    return re.sub(r"[^A-Z0-9]", "", (a or "").upper()) == re.sub(r"[^A-Z0-9]", "", (b or "").upper())


def main():
    liste = {m["id"]: m for m in json.load(open(sys.argv[1], encoding="utf-8"))}
    cache = sys.argv[2]
    hand = json.load(open(sys.argv[3], encoding="utf-8"))["meldungen"]
    bereinigt = json.load(open(sys.argv[4], encoding="utf-8"))
    Z.guete_laden(sys.argv[4])
    probe = json.load(open(sys.argv[5], encoding="utf-8"))
    wahr = {e["nr"]: e for e in json.load(open(sys.argv[6], encoding="utf-8"))["zeilen"]}
    zeilen_cache = {}
    aus = []
    for nr, p in enumerate(probe, start=1):
        if p["id"] not in zeilen_cache:
            g, _, _, det = Z.zeilen_der_meldung(liste[p["id"]], cache, hand, bereinigt)
            zeilen_cache[p["id"]] = ({(x.get("s"), x.get("z")): x for x in g},
                                     {(d["s"], d["z"]): d for d in det})
        g, det = zeilen_cache[p["id"]]
        w = wahr[nr]
        x = g.get((p["s"], p["z"]))
        e = {"nr": nr, "id": p["id"], "s": p["s"], "z": p["z"]}
        if x is None:
            e["fehler"] = []
            e["anm"] = "jetzt unlesbar: " + ", ".join(det.get((p["s"], p["z"]), {}).get("probleme", ["?"]))
            e["unlesbar"] = True
            aus.append(e)
            continue
        fehler = []
        if (x["i"] if x["i"] != "?" else "") != w["i"]:
            fehler.append("i")
        if not gleich_name(x["w"], w["w"]):
            fehler.append("w")
        if x["a"] != w["a"]:
            fehler.append("a")
        if x["d"] != w["d"]:
            fehler.append("d")
        if x.get("g") and x["g"] != w.get("g"):
            fehler.append("g")
        if x["betrag"] != w["b"]:
            fehler.append("b")
        e["fehler"] = fehler
        if fehler:
            e["anm"] = "; ".join("%s: %s statt %s" % (f, {"b": x["betrag"]}.get(f, x.get(f)), w.get(f))
                                 for f in fehler)
        aus.append(e)
    json.dump(aus, open(sys.argv[7], "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    import collections
    c = collections.Counter(f for e in aus for f in e["fehler"])
    print("Zeilen:", len(aus), "unlesbar geworden:", sum(1 for e in aus if e.get("unlesbar")), "Fehler je Feld:", dict(c))


if __name__ == "__main__":
    main()
