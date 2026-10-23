# -*- coding: utf-8 -*-
"""Bausteine fuer gescannte PTR-Seiten: Seite gedreht laden, Schraeglage ausgleichen, Tabellengitter finden,
Spalten deuten, Kreuze ueber den Anteil dunkler Pixel je Zelle messen.
Wird von gitter_alle.py und lesen.py eingebunden."""
import numpy as np
import fitz
from scipy import ndimage

DPI = 200
SCHWELLE = 160  # Grauwert, ab dem ein Pixel als dunkel zaehlt


def seite_laden(doc, seite, drehung):
    """Seite (0-basiert) als Graubild in DPI, um drehung Grad gegen den Uhrzeigersinn gedreht."""
    pix = doc[seite].get_pixmap(matrix=fitz.Matrix(DPI / 72, DPI / 72), colorspace=fitz.csGRAY, alpha=False)
    a = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width)
    return np.ascontiguousarray(np.rot90(a, k=(drehung // 90) % 4))


def schraeglage(d, grenze=2.0, schritt=0.05):
    """Winkel (Grad), um den die waagrechten Linien geneigt sind. Verfahren: Bild in senkrechte Streifen teilen,
    Zeilenprofil je Streifen, Profile je Winkel gegeneinander verschieben und die Schaerfe der Summe messen."""
    H, W = d.shape
    breite = 40
    n = W // breite
    prof = np.stack([d[:, i * breite:(i + 1) * breite].sum(axis=1) for i in range(n)]).astype(np.float64)
    mitte = (np.arange(n) * breite + breite / 2) - W / 2

    def guete(w):
        t = np.tan(np.radians(w))
        s = np.zeros(H + 400)
        for i in range(n):
            v = int(round(mitte[i] * t)) + 200
            s[v:v + H] += prof[i]
        return float((s ** 2).sum())

    winkel = np.arange(-grenze, grenze + 1e-9, 0.25)
    best = max(winkel, key=guete)
    fein = np.arange(best - 0.25, best + 0.25 + 1e-9, schritt)
    return float(max(fein, key=guete))


def entzerren(a, winkel):
    if abs(winkel) < 0.03:
        return a
    # Profilverschiebung um +mitte*tan(w) heisst: rechts liegt die Linie tiefer; Bild entsprechend zurueckdrehen
    return ndimage.rotate(a, -winkel, reshape=False, order=1, mode="constant", cval=255)


def laeufe(b):
    """b: 1D bool. Liste (start, ende) zusammenhaengender True-Laeufe."""
    p = np.concatenate(([0], b.view(np.int8) if b.dtype == bool else b.astype(np.int8), [0]))
    d = np.diff(p)
    return list(zip(np.flatnonzero(d == 1), np.flatnonzero(d == -1)))


def gruppen(idx, abstand=2):
    g = []
    for i in idx:
        if g and i - g[-1][1] <= abstand:
            g[-1][1] = i
        else:
            g.append([int(i), int(i)])
    return g


def waagrechte_linien(d, min_anteil=0.22):
    """Lange waagrechte Linien: je Pixelzeile laengster Lauf (kleine Luecken geschlossen)."""
    H, W = d.shape
    dd = d.copy()
    dd[1:, :] |= d[:-1, :]
    dd[:-1, :] |= d[1:, :]
    # Luecken bis 12 Pixel schliessen (blasse, unterbrochene Linien)
    dd = ndimage.binary_closing(dd, structure=np.ones((1, 13), bool))
    lang = np.zeros(H, dtype=np.int32)
    lage = [None] * H
    for y in range(H):
        best = (0, 0)
        for a0, b0 in laeufe(dd[y]):
            if b0 - a0 > best[1] - best[0]:
                best = (a0, b0)
        lang[y] = best[1] - best[0]
        lage[y] = best
    ys = np.flatnonzero(lang > W * min_anteil)
    aus = []
    for g0, g1 in gruppen(ys, 2):
        x0 = min(lage[y][0] for y in range(g0, g1 + 1))
        x1 = max(lage[y][1] for y in range(g0, g1 + 1))
        aus.append({"y0": g0, "y1": g1, "x0": int(x0), "x1": int(x1)})
    return aus


def senkrechte_im_band(d, y0, y1, x0, x1, deckung=0.88):
    """x-Positionen senkrechter Linien, die das Band [y0, y1] nahezu ganz durchlaufen."""
    if y1 - y0 < 6:
        return []
    teil = d[y0:y1, x0:x1]
    # 1 Pixel quer verbreitern gegen Restschraeglage
    tb = teil.copy()
    tb[:, 1:] |= teil[:, :-1]
    tb[:, :-1] |= teil[:, 1:]
    anteil = tb.mean(axis=0)
    # Kaestchenkanten reichen nicht bis an die Zeilenlinien: oberes und unteres Fuenftel muessen ebenfalls dunkel sein
    h = tb.shape[0]
    r = max(3, h // 5)
    oben = tb[:r].mean(axis=0)
    unten = tb[-r:].mean(axis=0)
    xs = np.flatnonzero((anteil >= deckung) & (oben >= 0.85) & (unten >= 0.85))
    return [((a + b) / 2 + x0, a + x0, b + x0) for a, b in gruppen(xs, 3)]


def waagrecht_maske(d, laenge):
    """Maske aller Pixel in waagrechten Laeufen von mindestens laenge Pixeln (vektorisiert)."""
    H, W = d.shape
    x = np.zeros((H, W + 2), dtype=np.int8)
    x[:, 1:-1] = d
    dd = np.diff(x, axis=1)
    st = np.argwhere(dd == 1)
    en = np.argwhere(dd == -1)
    m = np.zeros((H, W), dtype=bool)
    lang = (en[:, 1] - st[:, 1]) >= laenge
    for (y, a0), (_, b0) in zip(st[lang], en[lang]):
        m[y, a0:b0] = True
    return m


def lange_laeufe(z, laenge):
    """Maske aller Pixel, die zu waagrechten oder senkrechten Laeufen von mindestens laenge Pixeln gehoeren."""
    return waagrecht_maske(z, laenge) | waagrecht_maske(z.T, laenge).T


def tinte(d, x0, x1, y0, y1, rand=2, laenge=14, min_teil=10):
    """Dunkle Pixel einer Zelle ohne gerade Striche: Pixel in waagrechten oder senkrechten Laeufen ab laenge Pixeln
    (Zellrahmen, vorgedruckte Kaestchen samt Schattenkanten) zaehlen nicht, ebenso Kruemel unter min_teil Pixeln.
    Kreuze, Haken und kleine x bestehen aus schraegen Strichen und bleiben. Gibt (Pixelzahl, groesste Flaeche)."""
    x0, x1, y0, y1 = int(x0) + rand, int(x1) - rand, int(y0) + rand, int(y1) - rand
    if x1 - x0 < 4 or y1 - y0 < 4:
        return 0, 0
    z = d[y0:y1, x0:x1]
    rest = z & ~lange_laeufe(z, laenge)
    if not rest.any():
        return 0, 0
    # Reste in Gruppen zusammenfassen (Abstand bis 2 Pixel): ein fettes Kreuz zerfaellt beim Entfernen der langen
    # Laeufe in Stuecke, die nahe beieinander liegen. Eine Gruppe, die hoechstens 3 Pixel dick und deutlich laenger
    # als dick ist, ist ein Bruchstueck einer Linie und zaehlt nicht.
    naehe = ndimage.binary_dilation(rest, structure=np.ones((5, 5), bool))
    lab, k = ndimage.label(naehe, structure=np.ones((3, 3), bool))
    behalten = []
    for i, sl in enumerate(ndimage.find_objects(lab), start=1):
        if sl is None:
            continue
        teil = rest[sl] & (lab[sl] == i)
        n = int(teil.sum())
        if n < min_teil:
            continue
        ys, xs = np.nonzero(teil)
        h, w = ys.max() - ys.min() + 1, xs.max() - xs.min() + 1
        if min(h, w) <= 3 and max(h, w) >= 2.5 * min(h, w):
            continue
        behalten.append(n)
    if not behalten:
        return 0, 0
    return int(sum(behalten)), int(max(behalten))


def linien_weg(d, quer_min, laengs_min):
    """Entfernt lange waagrechte (>= quer_min) und danach senkrechte (>= laengs_min) Laeufe aus einem Bool-Bild."""
    d = d & ~waagrecht_maske(d, quer_min)
    return d & ~waagrecht_maske(d.T, laengs_min).T
