# -*- coding: utf-8 -*-
"""Legt die endgueltigen Tabellenzeilen fest und baut fuer die kurzen Felder (Inhaber, Datum, Benachrichtigung)
Zellenblaetter fuer die Windows-OCR: jede Zelle einzeln ausgeschnitten, Linienreste entfernt, mit viel Abstand
untereinander gesetzt. Das liest sich deutlich sicherer als ein durchgehender Spaltenstreifen.
Baender ohne Zeilenlinie dazwischen (mehrere Geschaefte in einem Band) werden an den Textzeilen der Datumsspalte
geteilt.
Ergebnis: <cache>/blatt/<id>/zeilen.json (Zeilen je Seite samt Lage im Blatt) und
<cache>/blatt/<id>/pNNN_<feld>_<blatt>_<fassung>.png.
Aufruf: python -I zellen.py <meldungen.json> <cache-ordner> [id ...]"""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tabelle as T  # noqa: E402

FELDER = ("inhaber", "datum", "gemeldet", "name")
# Fassungen: Vergroesserung und Weichzeichnen (Windows-OCR liest je nach Groesse andere Zellen sicher)
FASSUNGEN = {"f2": (2.0, 0), "f3": (3.0, 0), "f3w": (3.0, 1.5), "f4": (4.0, 0)}
# Namen sind lang: zwei Fassungen genuegen
FELD_FASSUNGEN = {"name": ("f2", "f3")}
MAX_FAKTOR = 4.0
ABSTAND = 36  # Pixel zwischen den Zellen im Blatt (vor der Vergroesserung)
RAND_X = 40


def textzeilen(d, x0, x1, y0, y1):
    """Senkrechte Abschnitte mit Schrift in einer Zelle (ohne Linien): Liste (von, bis) in Seitenkoordinaten."""
    z = d[int(y0):int(y1), int(x0) + 3:int(x1) - 3]
    if z.size == 0:
        return []
    z = z & ~T.lange_laeufe(z, 30)
    prof = z.sum(axis=1) >= 2
    aus = []
    for a, b in T.laeufe(prof):
        if b - a >= 6:
            aus.append((int(y0) + a, int(y0) + b))
    # nahe Abschnitte (Unterlaengen, Punkte) zusammenfassen
    zus = []
    for a, b in aus:
        if zus and a - zus[-1][1] < 5:
            zus[-1] = (zus[-1][0], b)
        else:
            zus.append((a, b))
    return zus


def zeilen_festlegen(d, info):
    sp = info["spalten"]
    zeilen = info["zeilen"]
    hoehen = sorted(z["y1"] - z["y0"] for z in zeilen if not z["balken"])
    if not hoehen:
        return [dict(z) for z in zeilen]
    median = hoehen[len(hoehen) // 2]
    aus = []
    for z in zeilen:
        if z["balken"] or z["y1"] - z["y0"] < 1.7 * median:
            aus.append(dict(z))
            continue
        tz = textzeilen(d, sp["datum"][0], sp["datum"][1], z["y0"], z["y1"])
        if len(tz) < 2 or any(b - a > 40 for a, b in tz):
            aus.append(dict(z))
            continue
        mitten = [(a + b) / 2 for a, b in tz]
        grenzen = [z["y0"]] + [int((a + b) / 2) for a, b in zip(mitten, mitten[1:])] + [z["y1"]]
        for a, b in zip(grenzen, grenzen[1:]):
            aus.append({"y0": int(a), "y1": int(b), "balken": False, "geteilt": True})
    return aus


def zelle(a, d, x0, x1, y0, y1):
    """Zellinneres ohne Rahmen und Linienreste als Graubild. Entfernt werden nur Striche, die fast ueber die ganze
    Zelle laufen (senkrecht ueber 85 Prozent der Hoehe, waagrecht ueber 70 Prozent der Breite), damit Schrift, die
    den Rahmen beruehrt, erhalten bleibt."""
    x0, x1, y0, y1 = int(x0) + 1, int(x1) - 1, int(y0) - 1, int(y1) + 1
    if x1 - x0 < 5 or y1 - y0 < 5:
        return np.full((10, 10), 255, np.uint8)
    t = a[y0:y1, x0:x1].copy()
    # Linien auch in ihren hellen Randpixeln erfassen
    dz = t < 215
    h, w = dz.shape
    weg = T.waagrecht_maske(dz.T, max(10, int(0.85 * h))).T
    weg |= T.waagrecht_maske(dz, max(30, int(0.7 * w)))
    # einen Pixel um die Linien mitnehmen
    weg[:, 1:] |= weg[:, :-1]
    weg[:, :-1] |= weg[:, 1:]
    weg[1:, :] |= weg[:-1, :]
    weg[:-1, :] |= weg[1:, :]
    t[weg & (t > 110)] = 255
    t[weg & ~T.waagrecht_maske(dz, 3)] = 255
    t[t > 215] = 255
    return t


def main():
    liste = json.load(open(sys.argv[1], encoding="utf-8"))
    cache = sys.argv[2]
    args = sys.argv[3:]
    nur_felder = None
    if args and args[0].startswith("--felder="):
        nur_felder = set(args[0][9:].split(","))
        args = args[1:]
    nur = set(args)
    for m in liste:
        if nur and m["id"] not in nur:
            continue
        gpfad = os.path.join(cache, "gitter", m["id"] + ".json")
        g = json.load(open(gpfad, encoding="utf-8"))
        od = os.path.join(cache, "blatt", m["id"])
        os.makedirs(od, exist_ok=True)
        ziel = os.path.join(od, "zeilen.json")
        if os.path.exists(ziel) and os.path.getmtime(ziel) >= os.path.getmtime(gpfad) and not nur and not nur_felder:
            continue
        aus = {}
        for s, info in g["seiten"].items():
            if not info.get("spalten") or not info.get("zeilen"):
                continue
            a = np.array(Image.open(os.path.join(cache, "seiten", m["id"], "p%03d.png" % int(s))))
            d = a < T.SCHWELLE
            zeilen = zeilen_festlegen(d, info)
            for feld in FELDER:
                c = info["spalten"].get(feld)
                if not c:
                    continue
                breite = int(c[1] - c[0])
                # Zellen auf Blaetter verteilen; ein Blatt bleibt nach der groessten Vergroesserung unter 9000 Pixeln
                blaetter, teile, y = [], [], ABSTAND
                for z in zeilen:
                    if z["balken"]:
                        continue
                    t = zelle(a, d, c[0], c[1], z["y0"], z["y1"])
                    if teile and (y + t.shape[0] + ABSTAND) * MAX_FAKTOR > 9000:
                        blaetter.append((teile, y))
                        teile, y = [], ABSTAND
                    z.setdefault("blatt", {})[feld] = [len(blaetter) + 1, y, y + t.shape[0]]
                    teile.append((y, t))
                    y += t.shape[0] + ABSTAND
                if teile:
                    blaetter.append((teile, y))
                for nr, (teile, hoehe) in enumerate(blaetter, start=1):
                    blatt = np.full((hoehe, breite + 2 * RAND_X), 255, np.uint8)
                    for y0, t in teile:
                        blatt[y0:y0 + t.shape[0], RAND_X:RAND_X + t.shape[1]] = t
                    if nur_felder and feld not in nur_felder:
                        continue
                    for fassung, (faktor, weich) in FASSUNGEN.items():
                        if fassung not in FELD_FASSUNGEN.get(feld, FASSUNGEN):
                            continue
                        img = Image.fromarray(blatt)
                        img = img.resize((int(img.width * faktor), int(img.height * faktor)), Image.BICUBIC)
                        if weich:
                            img = img.filter(ImageFilter.GaussianBlur(weich))
                        f = os.path.join(od, "p%03d_%s_%d_%s.png" % (int(s), feld, nr, fassung))
                        img.save(f)
                        alt = os.path.splitext(f)[0] + ".json"
                        if os.path.exists(alt):
                            os.remove(alt)
            aus[s] = zeilen
        json.dump(aus, open(ziel, "w", encoding="utf-8"), indent=0)
        print("ok", m["id"], sum(len(v) for v in aus.values()), flush=True)


if __name__ == "__main__":
    main()
