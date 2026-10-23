# -*- coding: utf-8 -*-
"""Ziffernvergleich fuer gedruckte Daten im Format MM/DD/YY, die die Windows-OCR nicht sicher liest (fette, unscharfe
Scans). Die Ziffern der Druckschrift sind gleich breit, die Schraegstriche schmaler: Die sechs Ziffern stehen daher an
festen Anteilen der Schriftbreite. Aus den sicher gelesenen Zellen derselben Meldung entstehen Vorlagen je Ziffer;
eine unsichere Zelle wird Stelle fuer Stelle mit den Vorlagen verglichen. Angenommen wird ein Datum nur, wenn jede
Stelle eindeutig ist (Korrelation ab 0,85, Abstand zur zweitbesten Ziffer ab 0,06) und das Datum im Zeitfenster liegt.
Wird von lesen.py eingebunden."""
import collections
import datetime
import re

import numpy as np
from PIL import Image

# Anteile der Schriftbreite: Ziffer 0,507 em, Schraegstrich 0,39 em (MM/DD/YY)
_B = [0.507, 0.507, 0.39, 0.507, 0.507, 0.39, 0.507, 0.507]
_S = np.concatenate(([0.0], np.cumsum(_B))) / sum(_B)
STELLEN = (0, 1, 3, 4, 6, 7)
GROESSE = (14, 22)
MUSTER = re.compile(r"^\d\d/\d\d/\d\d$")


def schrift(t, schwelle=150):
    """Graubild auf die Schriftzeile zugeschnitten oder None. Linienreste (flache oder schmale lange Teile) und
    abseits liegende Flecken fallen weg; es bleibt die Textzeile mit der meisten Tinte."""
    from scipy import ndimage
    m = t < schwelle
    lab, k = ndimage.label(m, structure=np.ones((3, 3), bool))
    if not k:
        return None
    teile = []
    for i, sl in enumerate(ndimage.find_objects(lab), start=1):
        ys, xs = sl
        h, w = ys.stop - ys.start, xs.stop - xs.start
        n = int((lab[sl] == i).sum())
        if n < 12 or h <= 4:
            continue
        teile.append([ys.start, ys.stop, xs.start, xs.stop, n, i])
    if not teile:
        return None
    hoehen = sorted(x[1] - x[0] for x in teile)
    hm = hoehen[len(hoehen) // 2]
    # schmale, sehr hohe Teile sind senkrechte Linienreste
    teile = [x for x in teile if not (x[3] - x[2] <= 3 and x[1] - x[0] > 1.6 * hm)]
    if not teile:
        return None
    # Textzeile: Teile, deren Mitte nahe der tintengewichteten Mitte liegt
    mitte = sum((x[0] + x[1]) / 2 * x[4] for x in teile) / sum(x[4] for x in teile)
    teile = [x for x in teile if abs((x[0] + x[1]) / 2 - mitte) <= 0.6 * hm]
    if not teile:
        return None
    # abseits liegende Teile (Abstand ueber zwei Zeichenhoehen) weglassen, groesste Gruppe behalten
    teile.sort(key=lambda x: x[2])
    gruppen = [[teile[0]]]
    for x in teile[1:]:
        if x[2] - max(y[3] for y in gruppen[-1]) > 1.2 * hm:
            gruppen.append([x])
        else:
            gruppen[-1].append(x)
    g = max(gruppen, key=lambda gr: sum(x[4] for x in gr))
    if sum(x[4] for x in g) < 40:
        return None
    y0, y1 = min(x[0] for x in g), max(x[1] for x in g)
    x0, x1 = min(x[2] for x in g), max(x[3] for x in g)
    behalten = np.isin(lab[y0:y1, x0:x1], [x[5] for x in g])
    aus = np.where(behalten, t[y0:y1, x0:x1], 255).astype(np.uint8)
    return aus


def _vektor(t):
    v = np.asarray(Image.fromarray(255 - t).resize(GROESSE, Image.BILINEAR), dtype=np.float32)
    v = v - v.mean()
    n = np.linalg.norm(v)
    return v / n if n else None


def stellen(t, verschiebung=0):
    """Sechs Ziffernbilder (normiert) einer zugeschnittenen Datumszelle, Stellen um verschiebung Pixel versetzt."""
    h, w = t.shape
    aus = []
    for k in STELLEN:
        a = int(round(_S[k] * w)) + verschiebung
        b = int(round(_S[k + 1] * w)) + verschiebung
        a, b = max(0, a), min(w, b)
        if b - a < 3:
            return None
        aus.append(_vektor(t[:, a:b]))
    return aus


class Ziffernleser:
    def __init__(self):
        self.vorlagen = collections.defaultdict(list)
        self.seitenverhaeltnis = []

    def lernen(self, t, iso):
        """Sicher gelesene Zelle (zugeschnittenes Graubild) mit Datum ISO als Vorlage aufnehmen."""
        if t is None:
            return
        d = datetime.date.fromisoformat(iso)
        ziffern = "%02d%02d%s" % (d.month, d.day, d.strftime("%y"))
        st = stellen(t)
        if not st or any(v is None for v in st):
            return
        self.seitenverhaeltnis.append(t.shape[1] / t.shape[0])
        for z, v in zip(ziffern, st):
            self.vorlagen[z].append(v)

    def fertig(self):
        self.mittel = {}
        for z, vs in self.vorlagen.items():
            if len(vs) >= 2:
                m = np.mean(vs, axis=0)
                n = np.linalg.norm(m)
                self.mittel[z] = m / n if n else m
        if self.seitenverhaeltnis:
            s = sorted(self.seitenverhaeltnis)
            self.verhaeltnis = s[len(s) // 2]
        else:
            self.verhaeltnis = None

    def lesen(self, t, von, bis, min_korr=0.85, min_abstand=0.06):
        """Datum ISO oder None, dazu die schwaechste Stelle (Korrelation, Abstand). Jede Stelle darf sich um bis zu
        drei Pixel verschieben (die Schrift ist nicht ganz gleichmaessig)."""
        if t is None or not self.mittel or not self.verhaeltnis:
            return None, None
        if abs(t.shape[1] / t.shape[0] - self.verhaeltnis) > 0.15 * self.verhaeltnis:
            return None, None
        h, w = t.shape
        ziffern, schwach = "", (9.0, 9.0)
        for k in STELLEN:
            beste = None
            for vs in range(-3, 4):
                a = max(0, int(round(_S[k] * w)) + vs)
                b = min(w, int(round(_S[k + 1] * w)) + vs)
                if b - a < 3:
                    continue
                v = _vektor(t[:, a:b])
                if v is None:
                    continue
                werte = sorted(((float((v * m).sum()), z) for z, m in self.mittel.items()), reverse=True)
                if beste is None or werte[0][0] > beste[0][0]:
                    beste = werte
            if not beste:
                return None, None
            abstand = beste[0][0] - (beste[1][0] if len(beste) > 1 else 0.0)
            ziffern += beste[0][1]
            if (beste[0][0], abstand) < schwach:
                schwach = (beste[0][0], abstand)
        korr, abstand = schwach
        if korr < min_korr or abstand < min_abstand:
            return None, (round(korr, 3), round(abstand, 3))
        try:
            d = datetime.date(2000 + int(ziffern[4:6]), int(ziffern[0:2]), int(ziffern[2:4]))
        except ValueError:
            return None, (round(korr, 3), round(abstand, 3))
        iso = d.isoformat()
        if not von <= iso <= bis:
            return None, (round(korr, 3), round(abstand, 3))
        return iso, (round(korr, 3), round(abstand, 3))
