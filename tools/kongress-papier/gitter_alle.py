# -*- coding: utf-8 -*-
"""Findet auf jeder Seite der Papiermeldungen die Geschaeftstabelle (Zeilen und Spalten), deutet die Spalten
(Inhaber, Name, Art, Datum, Benachrichtigung, Betragsspalten) und schneidet Spaltenstreifen fuer die Windows-OCR aus.
Ergebnis je Meldung: <cache>/gitter/<id>.json, entzerrte Seitenbilder <cache>/seiten/<id>/pNNN.png und
OCR-Streifen <cache>/streifen/<id>/pNNN_<spalte>.png.
Aufruf: python -I gitter_alle.py <meldungen.json> <pdf-ordner> <lage.json> <cache-ordner> [id ...]"""
import json
import os
import sys

import fitz
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tabelle as T  # noqa: E402

ARTEN = {3: ["P", "S", "E"], 4: ["P", "S", "PS", "E"], 5: ["P", "S", "E", "CG", "PT"]}
BETRAEGE = "ABCDEFGHIJK"
OCR_FAKTOR = 1.5


def baender(linien, H):
    """Baender zwischen aufeinanderfolgenden Linien. Dicke Linien (ueber 12 Pixel) sind schwarz hinterlegte
    Zwischenzeilen (Depotname) und werden selbst als Band mit balken=True gefuehrt."""
    ls = sorted(linien, key=lambda l: l["y0"])
    aus = []
    for l in ls:
        if l["y1"] - l["y0"] > 25:
            aus.append({"y0": l["y0"], "y1": l["y1"], "x0": l["x0"], "x1": l["x1"], "balken": True})
    for a, b in zip(ls, ls[1:]):
        y0, y1 = a["y1"] + 1, b["y0"] - 1
        if 12 <= y1 - y0 <= 300:
            x0, x1 = min(a["x0"], b["x0"]), max(a["x1"], b["x1"])
            if x1 - x0 > 200:
                aus.append({"y0": int(y0), "y1": int(y1), "x0": int(x0), "x1": int(x1), "balken": False})
    return sorted(aus, key=lambda b: b["y0"])


def zusammenfassen(xs, abstand=10):
    xs = sorted(xs)
    g = []
    for x in xs:
        if g and x - g[-1][-1] <= abstand:
            g[-1].append(x)
        else:
            g.append([x])
    return [sum(v) / len(v) for v in g]


def tabelle_finden(d, breite=2, nur_rumpf=False):
    H, W = d.shape
    alle_linien = T.waagrechte_linien(d)
    # Zeilenlinien reichen ueber den groessten Teil der Tabelle; kuerzere Linien sind Kaestchenreihen im Betragsblock
    if alle_linien:
        laengste = max(l["x1"] - l["x0"] for l in alle_linien)
        linien = [l for l in alle_linien if l["x1"] - l["x0"] >= 0.55 * laengste]
    else:
        linien = []
    bs = baender(linien, H)
    for b in bs:
        if b["balken"]:
            b["sep"] = []
            continue
        teil = d[b["y0"]:b["y1"], b["x0"]:b["x1"]]
        if teil[:, teil.shape[1] // 2:].mean() > 0.5:
            b["balken"] = True
            b["sep"] = []
            continue
        seps = T.senkrechte_im_band(d, b["y0"], b["y1"], max(0, b["x0"] - 8), min(W, b["x1"] + 8))
        b["sep"] = zusammenfassen([s[0] for s in seps])
    viele = [b for b in bs if len(b["sep"]) >= 13]
    if not viele:
        return {"linien": linien, "baender": bs, "spalten": None}
    if nur_rumpf:
        # nur der Rumpf: Baender mit der haeufigsten Zahl an Trennlinien (Kopf mit abweichenden Linien faellt weg)
        import collections
        modus = collections.Counter(len(b["sep"]) for b in viele).most_common(1)[0][0]
        laeufe, lauf = [], []
        for b in bs:
            if b["balken"]:
                continue
            if len(b["sep"]) >= modus - 1:
                lauf.append(b)
            elif lauf:
                laeufe.append(lauf)
                lauf = []
        if lauf:
            laeufe.append(lauf)
        viele = max(laeufe, key=len)
    # Tabellenbereich: vom ersten bis zum letzten Band mit vielen Trennlinien
    y_oben = min(b["y0"] for b in viele)
    y_unten = max(b["y1"] for b in viele)
    x0 = max(0, min(b["x0"] for b in viele) - 10)
    x1 = min(W, max(b["x1"] for b in viele) + 10)
    # Trennlinien: Spalten, die ueber die ganze Tabellenhoehe zu mindestens 80 Prozent dunkel sind (Kaestchenkanten
    # fehlen im Kopf und zwischen den Zeilen)
    teil = d[y_oben:y_unten, x0:x1].copy()
    tb = teil.copy()
    for k in range(1, breite + 1):
        tb[:, k:] |= teil[:, :-k]
        tb[:, :-k] |= teil[:, k:]
    deck = tb.mean(axis=0)
    xs = np.flatnonzero(deck >= 0.8)
    gr = [((a + b) / 2 + x0, b - a + 1) for a, b in T.gruppen(xs, 3)]
    # zwei Linien unter 30 Pixeln Abstand: die duennere ist ein Scannerstreifen oder eine Doppellinie
    while True:
        eng = [i for i in range(len(gr) - 1) if gr[i + 1][0] - gr[i][0] < 30]
        if not eng:
            break
        i = eng[0]
        if gr[i + 1][0] - gr[i][0] <= 10:
            gr[i:i + 2] = [((gr[i][0] + gr[i + 1][0]) / 2, gr[i][1] + gr[i + 1][1])]
        else:
            del gr[i if gr[i][1] < gr[i + 1][1] else i + 1]
    kons = [x for x, _ in gr]
    # Scannerstreifen: senkrechte Linien, die auch im leeren Rand ueber der ersten bzw. unter der letzten Linie
    # durchlaufen, sind keine Spaltengrenzen
    oben = min(l["y0"] for l in alle_linien) - 5
    unten = max(l["y1"] for l in alle_linien) + 5
    streifen = []
    for x in kons:
        xa, xb = int(max(0, x - 3)), int(min(W, x + 4))
        for ya, yb in ((5, oben), (unten, H - 5)):
            if yb - ya >= 60 and d[ya:yb, xa:xb].any(axis=1).mean() >= 0.6:
                streifen.append(x)
                break
    kons = [x for x in kons if x not in streifen]
    return {"linien": linien, "baender": bs, "spalten": kons, "y_oben": int(y_oben), "y_unten": int(y_unten),
            "streifen": streifen}


def spalten_deuten(kons):
    cols = [(kons[i], kons[i + 1]) for i in range(len(kons) - 1)]
    w = [b - a for a, b in cols]
    links = kons[0]
    breite = kons[-1] - kons[0]
    kand = [i for i, (a, b) in enumerate(cols) if (a + b) / 2 - links < 0.5 * breite]
    if not kand:
        return None
    name = max(kand, key=lambda i: w[i])
    nach = list(range(name + 1, len(cols)))
    best = None
    for i in (3, 4, 5):
        if i + 1 >= len(nach):
            continue
        a = len(nach) - i - 2
        if a not in (10, 11):
            continue
        guete = min(w[nach[i]], w[nach[i + 1]])
        if best is None or guete > best[0]:
            best = (guete, i, a)
    if best is None:
        return {"fehler": "Spaltenzahl %d nach dem Namen passt zu keiner Vorlage" % len(nach), "cols": cols}
    _, t, a = best
    sp = {"name": cols[name], "inhaber": cols[name - 1] if name >= 1 else None}
    for k, art in enumerate(ARTEN[t]):
        sp["art_" + art] = cols[nach[k]]
    sp["datum"] = cols[nach[t]]
    sp["gemeldet"] = cols[nach[t + 1]]
    for k in range(a):
        sp["betrag_" + BETRAEGE[k]] = cols[nach[t + 2 + k]]
    return {"vorlage": "t%d_a%d" % (t, a), "spalten": sp, "cols": cols}


def streifen_speichern(a, x0, x1, y0, y1, ziel):
    teil = a[int(y0):int(y1), int(x0) + 3:int(x1) - 2]
    if teil.size == 0:
        return
    d = teil < T.SCHWELLE
    ohne = T.linien_weg(d, quer_min=max(30, int(teil.shape[1] * 0.5)), laengs_min=40)
    out = teil.copy()
    out[d & ~ohne] = 255
    img = Image.fromarray(np.pad(out, 20, constant_values=255))
    img = img.resize((int(img.width * OCR_FAKTOR), int(img.height * OCR_FAKTOR)), Image.BICUBIC)
    img.save(ziel)
    # altes OCR-Ergebnis zu diesem Streifen ist nicht mehr gueltig
    alt = os.path.splitext(ziel)[0] + ".json"
    if os.path.exists(alt):
        os.remove(alt)


def inhaber_nachtragen(seiten, mid, cache):
    """Seiten ohne erkannte Inhaberspalte (Trennlinie zu blass oder linker Rand nicht erkannt): Breite von Inhaber-
    und Namensspalte aus den uebrigen Seiten derselben Meldung uebernehmen. Ist die Namensspalte so breit wie beide
    zusammen, wird sie geteilt, sonst liegt die Inhaberspalte links davor. Streifen werden neu geschnitten."""
    breiten = [(i["spalten"]["inhaber"][1] - i["spalten"]["inhaber"][0], i["spalten"]["name"][1] - i["spalten"]["name"][0])
               for i in seiten.values() if i.get("spalten") and i["spalten"].get("inhaber")]
    if len(breiten) < 2:
        return []
    wi = sorted(b[0] for b in breiten)[len(breiten) // 2]
    wn = sorted(b[1] for b in breiten)[len(breiten) // 2]
    geaendert = []
    for s, info in seiten.items():
        sp = info.get("spalten")
        if not sp or sp.get("inhaber"):
            continue
        n0, n1 = sp["name"]
        if n1 - n0 > wn + 0.6 * wi:
            sp["inhaber"], sp["name"] = [n0, n1 - wn], [n1 - wn, n1]
        elif n0 - wi > 0:
            sp["inhaber"] = [n0 - wi, n0]
        else:
            continue
        info["inhaber_nachgetragen"] = True
        geaendert.append(s)
        if info.get("zeilen"):
            a = np.array(Image.open(os.path.join(cache, "seiten", mid, "p%03d.png" % int(s))))
            y0 = min(z["y0"] for z in info["zeilen"])
            y1 = max(z["y1"] for z in info["zeilen"])
            st = os.path.join(cache, "streifen", mid)
            for key in ("inhaber", "name"):
                c = sp[key]
                streifen_speichern(a, c[0], c[1], y0, y1, os.path.join(st, "p%03d_%s.png" % (int(s), key)))
    return geaendert


def _breiten(kons, sp_name):
    """Anteile der Spaltenbreiten rechts der Namensspalte (normiert auf ihre Summe)."""
    rechts = [x for x in kons if x >= sp_name[1] - 2]
    w = [b - a for a, b in zip(rechts, rechts[1:])]
    t = sum(w)
    return [x / t for x in w] if t else []


def seiten_angleichen(seiten, mid, cache):
    """Seiten, deren Spaltenvorlage von der vorherrschenden der Meldung abweicht und deren Zahl der Trennlinien um
    genau eins abweicht: eine fehlende (zu blasse) Linie einfuegen oder eine ueberzaehlige (Scannerstreifen) entfernen,
    wenn danach die vorherrschende Vorlage herauskommt und die Breitenverhaeltnisse zur naechsten Seite mit dieser
    Vorlage passen. Streifen der Seite werden neu geschnitten."""
    import collections
    vorlagen = collections.Counter(i.get("vorlage") for i in seiten.values() if i.get("vorlage"))
    if not vorlagen:
        return []
    dom, anz = vorlagen.most_common(1)[0]
    if anz < 2:
        return []
    geaendert = []
    for s, info in seiten.items():
        if not info.get("vorlage") or info["vorlage"] == dom or not info.get("zeilen"):
            continue
        ref_s = min((t for t, i in seiten.items() if i.get("vorlage") == dom), key=lambda t: abs(int(t) - int(s)))
        ref = seiten[ref_s]
        kons_ref = [c[0] for c in ref["cols"]] + [ref["cols"][-1][1]]
        kons = [c[0] for c in info["cols"]] + [info["cols"][-1][1]]
        if not 1 <= abs(len(kons) - len(kons_ref)) <= 2 or (len(kons) > len(kons_ref) and
                                                            len(kons) - len(kons_ref) > 1):
            continue
        soll = _breiten(kons_ref, ref["spalten"]["name"])
        kandidaten = []
        if len(kons) > len(kons_ref):
            for k in range(1, len(kons) - 1):
                kandidaten.append(kons[:k] + kons[k + 1:])
        else:
            a = np.array(Image.open(os.path.join(cache, "seiten", mid, "p%03d.png" % int(s))))
            d = a < T.SCHWELLE
            y0 = min(z["y0"] for z in info["zeilen"])
            y1 = max(z["y1"] for z in info["zeilen"])
            teil = d[y0:y1]
            tb = teil.copy()
            for k in (1, 2):
                tb[:, k:] |= teil[:, :-k]
                tb[:, :-k] |= teil[:, k:]
            deck = tb.mean(axis=0)
            spitzen = [float(x) for x in range(int(kons[0]) + 10, int(kons[-1]) - 10)
                       if deck[x] >= 0.3 and deck[x] == deck[x - 3:x + 4].max() and all(abs(x - k) > 12 for k in kons)]
            fehlend = len(kons_ref) - len(kons)
            import itertools
            for neu in itertools.combinations(spitzen, fehlend):
                if fehlend == 2 and abs(neu[0] - neu[1]) <= 12:
                    continue
                kandidaten.append(sorted(kons + list(neu)))
        beste = None
        for kk in kandidaten:
            dd = spalten_deuten(kk)
            if not dd or dd.get("vorlage") != dom:
                continue
            ist = _breiten(kk, dd["spalten"]["name"])
            if len(ist) != len(soll):
                continue
            fehler = sum(abs(x - y) for x, y in zip(ist, soll))
            if beste is None or fehler < beste[0]:
                beste = (fehler, kk, dd)
        if beste is None or beste[0] > 0.12:
            continue
        info.update(beste[2])
        info["angeglichen"] = round(beste[0], 3)
        geaendert.append(s)
        a = np.array(Image.open(os.path.join(cache, "seiten", mid, "p%03d.png" % int(s))))
        y0 = min(z["y0"] for z in info["zeilen"])
        y1 = max(z["y1"] for z in info["zeilen"])
        for key in ("inhaber", "name", "datum", "gemeldet"):
            c = info["spalten"].get(key)
            if c:
                streifen_speichern(a, c[0], c[1], y0, y1, os.path.join(cache, "streifen", mid, "p%03d_%s.png" % (int(s), key)))
    return geaendert


def main():
    if sys.argv[1] == "--angleichen":
        # Spaltenvorlage abweichender Seiten an die Meldung angleichen: gitter_alle.py --angleichen <cache> <id> ...
        cache = sys.argv[2]
        for mid in sys.argv[3:]:
            ziel = os.path.join(cache, "gitter", mid + ".json")
            g = json.load(open(ziel, encoding="utf-8"))
            print(mid, seiten_angleichen(g["seiten"], mid, cache))
            json.dump(g, open(ziel, "w", encoding="utf-8"), indent=0)
        return
    if sys.argv[1] == "--nachtragen":
        # nur Inhaberspalte in vorhandenen Gitterdateien nachtragen: gitter_alle.py --nachtragen <cache> <id> ...
        cache = sys.argv[2]
        for mid in sys.argv[3:]:
            ziel = os.path.join(cache, "gitter", mid + ".json")
            g = json.load(open(ziel, encoding="utf-8"))
            print(mid, inhaber_nachtragen(g["seiten"], mid, cache))
            json.dump(g, open(ziel, "w", encoding="utf-8"), indent=0)
        return
    liste = json.load(open(sys.argv[1], encoding="utf-8"))
    pdfdir, lagef, cache = sys.argv[2:5]
    nur = set(sys.argv[5:])
    lage = json.load(open(lagef, encoding="utf-8"))
    os.makedirs(os.path.join(cache, "gitter"), exist_ok=True)
    for m in liste:
        if nur and m["id"] not in nur:
            continue
        ziel = os.path.join(cache, "gitter", m["id"] + ".json")
        if os.path.exists(ziel) and not nur:
            continue
        doc = fitz.open(os.path.join(pdfdir, m["id"] + ".pdf"))
        sd = os.path.join(cache, "seiten", m["id"])
        st = os.path.join(cache, "streifen", m["id"])
        os.makedirs(sd, exist_ok=True)
        os.makedirs(st, exist_ok=True)
        seiten = {}
        for s in range(doc.page_count):
            dreh = lage[m["id"]][str(s + 1)]["drehung"]
            a = T.seite_laden(doc, s, dreh)
            winkel = T.schraeglage(a < T.SCHWELLE)
            a = T.entzerren(a, winkel)
            Image.fromarray(a).save(os.path.join(sd, "p%03d.png" % (s + 1)))
            d = a < T.SCHWELLE
            tb = tabelle_finden(d)
            info = {"drehung": dreh, "winkel": round(winkel, 2), "h": a.shape[0], "w": a.shape[1]}
            deut = spalten_deuten(tb["spalten"]) if tb["spalten"] else None
            if tb["spalten"] and (not deut or "fehler" in deut):
                # leicht verzogene Scans: senkrechte Linien mit mehr Spielraum suchen
                for breite, rumpf in ((4, False), (2, True), (4, True)):
                    tb2 = tabelle_finden(d, breite=breite, nur_rumpf=rumpf)
                    deut2 = spalten_deuten(tb2["spalten"]) if tb2["spalten"] else None
                    if deut2 and "vorlage" in deut2:
                        tb, deut = tb2, deut2
                        info["breite"], info["rumpf"] = breite, rumpf
                        break
            if tb["spalten"]:
                info.update(deut or {"fehler": "kein Namensfeld"})
                zeilen = [b for b in tb["baender"] if b["y0"] >= tb["y_oben"] - 2 and b["y1"] <= tb["y_unten"] + 2]
                info["zeilen"] = [{"y0": int(b["y0"]), "y1": int(b["y1"]), "balken": bool(b["balken"])} for b in zeilen]
                if deut and "spalten" in deut and zeilen:
                    y0 = min(b["y0"] for b in zeilen)
                    y1 = max(b["y1"] for b in zeilen)
                    info["streifen_y0"] = int(y0)
                    for key in ("inhaber", "name", "datum", "gemeldet"):
                        c = deut["spalten"].get(key)
                        if c:
                            streifen_speichern(a, c[0], c[1], y0, y1, os.path.join(st, "p%03d_%s.png" % (s + 1, key)))
            else:
                info["fehler"] = "keine Tabelle"
            seiten[str(s + 1)] = info
        inhaber_nachtragen(seiten, m["id"], cache)
        seiten_angleichen(seiten, m["id"], cache)
        json.dump({"id": m["id"], "seiten": seiten}, open(ziel, "w", encoding="utf-8"), indent=0)
        print(m["name"], m["id"], {k: (v.get("vorlage") or v.get("fehler")) for k, v in seiten.items()}, flush=True)


if __name__ == "__main__":
    main()
