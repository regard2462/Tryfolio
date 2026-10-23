# -*- coding: utf-8 -*-
"""Zweiter Durchgang fuer die Inhaberspalte (SP, DC, JT). Die Windows-OCR liest kurze Kuerzel wie SP und JT oft gar
nicht. Hier wird jede Inhaberzelle mit Schrift als Bild mit den Mittelbildern der sicher gelesenen Kuerzel
verglichen: zuerst aus derselben Meldung, sonst aus allen Meldungen desselben Abgeordneten. Angenommen wird ein
Kuerzel bei einer Korrelation ab 0,80 und mindestens 0,10 Abstand zum naechsten. Zellen ohne Schrift bedeuten
eigenes Depot (leer). Schreibt das Ergebnis in <cache>/zeilen/<id>.json (Felder inhaber, inhaber_quelle).
Aufruf: python -I inhaber.py <meldungen.json> <cache-ordner>"""
import collections
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lesen as L  # noqa: E402

KUERZEL = ("SP", "DC", "JT")


def mittel(liste):
    m = np.mean(liste, axis=0)
    n = np.linalg.norm(m)
    return m / n if n else m


def main():
    liste = json.load(open(sys.argv[1], encoding="utf-8"))
    cache = sys.argv[2]
    daten = {}
    for m in liste:
        p = os.path.join(cache, "zeilen", m["id"] + ".json")
        z = json.load(open(p, encoding="utf-8"))
        g = json.load(open(os.path.join(cache, "gitter", m["id"] + ".json"), encoding="utf-8"))
        bilder = {}
        seiten = collections.defaultdict(list)
        for r in z["zeilen"]:
            if r.get("befund") in ("geschaeft", "pruefen"):
                seiten[r["s"]].append(r)
        for s, rs in seiten.items():
            sp = g["seiten"][str(s)].get("spalten") or {}
            if not sp.get("inhaber"):
                continue
            a = np.array(Image.open(os.path.join(cache, "seiten", m["id"], "p%03d.png" % s)))
            for r in rs:
                bilder[(r["s"], r["z"])] = L.zelle_bild(a, sp["inhaber"][0], sp["inhaber"][1], r["y0"], r["y1"])
        daten[m["id"]] = (m, z, bilder)
    # sichere Beispiele: Einigkeit der OCR-Fassungen (mindestens zwei Lesungen, keine abweichende)
    beispiele = collections.defaultdict(lambda: collections.defaultdict(list))
    je_person = collections.defaultdict(lambda: collections.defaultdict(list))
    for mid, (m, z, bilder) in daten.items():
        for r in z["zeilen"]:
            b = bilder.get((r["s"], r["z"]))
            if b is None:
                continue
            les = [L.inhaber_lesen(t) for t in r.get("inhaber_roh", []) if t]
            gut = [x for x in les if x in KUERZEL]
            if len(gut) >= 2 and len(set(gut)) == 1:
                beispiele[mid][gut[0]].append(b)
                je_person[m["name"]][gut[0]].append(b)
    stat = collections.Counter()
    for mid, (m, z, bilder) in daten.items():
        eigene = {k: mittel(v) for k, v in beispiele[mid].items() if len(v) >= 2}
        person = {k: mittel(v) for k, v in je_person[m["name"]].items() if len(v) >= 3}
        for r in z["zeilen"]:
            if r.get("befund") not in ("geschaeft", "pruefen"):
                continue
            b = bilder.get((r["s"], r["z"]))
            les = [L.inhaber_lesen(t) for t in r.get("inhaber_roh", []) if t]
            gut = [x for x in les if x in KUERZEL]
            if b is None:
                r["inhaber"], r["inhaber_quelle"] = ("", "leer") if not gut else (gut[0], "ocr")
                stat[r["inhaber_quelle"]] += 1
                continue
            vorlagen = dict(person)
            vorlagen.update(eigene)
            werte = sorted(((float((b * v).sum()), k) for k, v in vorlagen.items()), reverse=True)
            bild = None
            if werte and werte[0][0] >= 0.80 and (len(werte) == 1 or werte[0][0] - werte[1][0] >= 0.10):
                # bei nur einer Vorlage muss der Abstand zur Schwelle gross sein
                if len(werte) > 1 or werte[0][0] >= 0.85:
                    bild = werte[0][1]
            if len(gut) >= 2 and len(set(gut)) == 1:
                ocr = gut[0]
            elif len(gut) == 1 and len(les) <= 2:
                ocr = gut[0]
            else:
                ocr = None
            if ocr and (bild is None or bild == ocr):
                r["inhaber"], r["inhaber_quelle"] = ocr, "ocr"
            elif bild and not ocr:
                r["inhaber"], r["inhaber_quelle"] = bild, "bild %.2f" % werte[0][0]
            else:
                r["inhaber"], r["inhaber_quelle"] = "?", "offen"
            stat[r["inhaber_quelle"].split(" ")[0]] += 1
        p = os.path.join(cache, "zeilen", mid + ".json")
        with open(p + ".teil", "w", encoding="utf-8") as f:
            json.dump(z, f, ensure_ascii=False, indent=0)
        os.replace(p + ".teil", p)
    print(dict(stat))


if __name__ == "__main__":
    main()
