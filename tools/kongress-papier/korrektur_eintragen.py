# -*- coding: utf-8 -*-
"""Uebertraegt die Ergebnisse einer Sichtpruefung (Pruefbogen) in hand.json.
Eingabe: die Liste des Pruefbogens (<praefix>.json, Nummer -> Meldung/Seite/Zeile) und eine Textdatei mit je
einer Zeile pro geprueftem Eintrag:  <nr> <angaben>
Statt der Nummer geht auch „<DocID> <Seite>/<Zeile>“ (Zeile ohne Pruefbogen, etwa Nachbarzeilen).
Angaben (beliebig kombinierbar): ok | unlesbar | streichen | aufnehmen (Zeile mit anderem Befund, etwa als Kopf
erkannt, als Geschaeft fuehren) | d=MM/DD/YY | g=MM/DD/YY | g=- | a=K|V|T|X
(Kauf, Verkauf, Teilverkauf, Tausch) | b=A..K | i=SP|DC|JT|- | w="Name"
Aufruf: python -I korrektur_eintragen.py <pruefbogen.json> <angaben.txt> <hand.json>"""
import json
import re
import shlex
import sys

ARTEN = {"K": "KAUF", "V": "VERKAUF", "T": "TEILVERKAUF", "X": "TAUSCH"}


def datum(t):
    m = re.fullmatch(r"(\d{1,2})/(\d{1,2})/(\d{2}|\d{4})", t)
    if not m:
        raise ValueError("Datum " + t)
    j = int(m.group(3)) + (2000 if len(m.group(3)) == 2 else 0)
    return "%04d-%02d-%02d" % (j, int(m.group(1)), int(m.group(2)))


def main():
    bogen = {e["nr"]: e for e in json.load(open(sys.argv[1], encoding="utf-8"))}
    hand = json.load(open(sys.argv[3], encoding="utf-8"))
    n = 0
    for zeile in open(sys.argv[2], encoding="utf-8"):
        zeile = zeile.strip()
        if not zeile or zeile.startswith("#"):
            continue
        teile = shlex.split(zeile)
        if re.fullmatch(r"\d{7,}", teile[0]):
            s_, z_ = teile[1].split("/")
            e = {"id": teile[0], "s": int(s_), "z": int(z_)}
            teile = teile[1:]
        else:
            e = bogen[int(teile[0])]
        k = {}
        for t in teile[1:]:
            if t in ("ok", "unlesbar", "streichen", "aufnehmen"):
                k[t] = True
            elif t.startswith("d="):
                k["d"] = datum(t[2:])
            elif t.startswith("g="):
                k["g"] = None if t[2:] == "-" else datum(t[2:])
            elif t.startswith("a="):
                k["a"] = ARTEN[t[2:]]
            elif t.startswith("b="):
                k["b"] = t[2:]
            elif t.startswith("i="):
                k["i"] = "" if t[2:] == "-" else t[2:]
            elif t.startswith("w="):
                k["w"] = t[2:]
            else:
                raise ValueError("unbekannt: " + t)
        eintrag = hand["meldungen"].setdefault(e["id"], {})
        # mit frueheren Angaben zur selben Zeile zusammenfuehren (spaetere gehen vor)
        alt = eintrag.setdefault("korrekturen", {}).get("%d/%d" % (e["s"], e["z"]), {})
        if k.get("unlesbar") or k.get("streichen"):
            alt = {}
        elif any(f in k for f in ("w", "a", "d", "b", "ok")):
            alt.pop("unlesbar", None)
            alt.pop("streichen", None)
        alt.update(k)
        eintrag["korrekturen"]["%d/%d" % (e["s"], e["z"])] = alt
        n += 1
    json.dump(hand, open(sys.argv[3], "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("eingetragen:", n)


if __name__ == "__main__":
    main()
