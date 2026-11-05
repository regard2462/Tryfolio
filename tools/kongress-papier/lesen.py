# -*- coding: utf-8 -*-
"""Liest je Tabellenzeile die Kreuze (Pixel je Zelle) und den Text (Windows-OCR der Spaltenstreifen in bis zu drei
Fassungen) und deutet die Zeile: Inhaber, Name, Art, Datum, Benachrichtigung, Betragsspalte.
Ergebnis je Meldung: <cache>/zeilen/<id>.json mit allen Zeilen samt Rohwerten und Befund
(geschaeft, pruefen, kopf, beispiel, leer, zwischenzeile).
Aufruf: python -I lesen.py <meldungen.json> <cache-ordner> [id ...]"""
import collections
import datetime
import functools
import json
import os
import re
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tabelle as T  # noqa: E402
import zellen as Z  # noqa: E402
import ziffern as ZF  # noqa: E402

RAND = 20
# Fassungen der OCR-Streifen: Dateiendung -> Vergroesserung
FASSUNGEN = {"": 1.5, "_v2": 2.0, "_v3": 2.5}
# Zellenblaetter aus zellen.py: Fassung -> Vergroesserung
BLATT_FASSUNGEN = {"f2": 2.0, "f3": 3.0, "f3w": 3.0, "f4": 4.0}
BETRAEGE = "ABCDEFGHIJK"
# Mindestwerte fuer ein Kreuz: groesste zusammenhaengende Flaeche und Gesamtzahl dunkler Pixel (200 dpi)
MIN_FLAECHE = 12
MIN_TINTE = 40


def ocr_woerter(pfad, faktor):
    """Woerter eines OCR-Ergebnisses als (y-Mitte in Streifenpixeln ohne Rand und Vergroesserung, x0, Text)."""
    if not os.path.exists(pfad):
        return None
    js = json.load(open(pfad, encoding="utf-8-sig"))
    aus = []
    for z in js.get("zeilen", []):
        for w in z["w"]:
            aus.append((((w[1] + w[3]) / 2) / faktor - RAND, w[0] / faktor, w[4]))
    return aus


def woerter_der_zeile(woerter, y_off, y0, y1):
    """Text einer Tabellenzeile [y0, y1] (Seitenkoordinaten), Textzeilen von oben nach unten."""
    if not woerter:
        return ""
    sel = sorted((ym + y_off, x0, t) for ym, x0, t in woerter if y0 - 2 <= ym + y_off <= y1 + 2)
    zeilen = []
    for ym, x0, t in sel:
        if zeilen and abs(zeilen[-1][0] - ym) < 9:
            zeilen[-1][1].append((x0, t))
        else:
            zeilen.append([ym, [(x0, t)]])
    return " ".join(" ".join(t for _, t in sorted(ws)) for _, ws in zeilen)


ZIFFER = str.maketrans({"O": "0", "o": "0", "D": "0", "Q": "0", "U": "0", "l": "1", "I": "1", "|": "1", "i": "1",
                        "!": "1", "L": "1", "J": "1", "j": "1", "]": "1", "[": "1", "t": "1", "S": "5", "s": "5",
                        "B": "8", "Z": "2", "z": "2", "g": "9", "q": "9", "G": "6", "b": "6", "T": "7", "?": "7"})
MONATE = ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"]
MONATSFORM = re.compile(r"^(\d{1,2})\W?([A-Za-z]{3,5})\W?(\d{2}|\d{4})$")


def monatsdatum(t):
    """Schreibweise 15-Jan-25 (Tag, Monatskuerzel, Jahr). Das Monatskuerzel darf um einen Buchstaben abweichen,
    wenn es dann nur zu einem Monat passt. Gibt ISO oder None."""
    m = MONATSFORM.match(t)
    if not m:
        return None
    w = m.group(2).upper()[:3]
    passend = [i for i, mo in enumerate(MONATE, start=1) if sum(a != b for a, b in zip(w, mo)) == 0]
    if not passend:
        passend = [i for i, mo in enumerate(MONATE, start=1) if sum(a != b for a, b in zip(w, mo)) == 1]
    if len(passend) != 1:
        return None
    ja = int(m.group(3)) + (2000 if len(m.group(3)) == 2 else 0)
    try:
        return datetime.date(ja, passend[0], int(m.group(1))).isoformat()
    except ValueError:
        return None


TRENNER_ECHT = set("/-.,\\")
TRENNER_LESEFEHLER = set("1lI|!]/7")


def datum_kandidaten(text):
    """Alle Lesarten eines OCR-Datums als (iso, guete). guete zaehlt echte Trenner; Zeichen, die oft fuer einen
    Schraegstrich gelesen werden (1, l, I, |), duerfen Trenner sein, kosten aber Guete; fehlende Trenner zwischen
    MMDD kosten mehr."""
    if not text:
        return []
    t = re.sub(r"\s+", "", text)
    mon = monatsdatum(t)
    if mon:
        return [(mon, 8)]
    t = "".join(ch for ch in t if ch.isalnum() or ch in TRENNER_ECHT or ch in "|!]")
    zeichen = []
    for ch in t:
        if ch in TRENNER_ECHT:
            zeichen.append(("/", 2))
        elif ch in TRENNER_LESEFEHLER:
            zeichen.append((ch.translate(ZIFFER) if ch != "/" else "/", 1))
        else:
            z = ch.translate(ZIFFER)
            if z.isdigit():
                zeichen.append((z, 0))
    aus = {}
    n = len(zeichen)

    def pruefen(mo, ta, ja, guete):
        if not (mo and ta and ja) or not (mo.isdigit() and ta.isdigit() and ja.isdigit()):
            return
        if len(mo) > 2 or len(ta) > 2 or len(ja) not in (2, 4):
            return
        j = int(ja) + (2000 if len(ja) == 2 else 0)
        try:
            dt = datetime.date(j, int(mo), int(ta))
        except ValueError:
            return
        if not 2020 <= j <= 2027:
            return
        iso = dt.isoformat()
        aus[iso] = max(aus.get(iso, -99), guete)

    # zwei Trennstellen i < k waehlen (echte Trenner oder Lesefehler-Zeichen)
    for i in range(1, n - 1):
        for k in range(i + 2, n):
            if zeichen[i][1] == 0 or zeichen[k][1] == 0:
                continue
            teile = ["".join(c for c, _ in zeichen[:i]), "".join(c for c, _ in zeichen[i + 1:k]),
                     "".join(c for c, _ in zeichen[k + 1:])]
            if any("/" in x for x in teile):
                continue
            g = sum(4 if zeichen[x][1] == 2 else 1 for x in (i, k))
            pruefen(teile[0], teile[1], teile[2], g)
    # ein Trenner fehlt: MMDD/YY oder MM/DDYY
    for i in range(1, n):
        if zeichen[i][1] != 2:
            continue
        a = "".join(c for c, _ in zeichen[:i])
        b = "".join(c for c, _ in zeichen[i + 1:])
        if "/" in a or "/" in b:
            continue
        if len(a) in (3, 4):
            pruefen(a[:-2], a[-2:], b, 1)
        if len(b) in (4, 6):
            pruefen(a, b[:2], b[2:], 1)
    # ganz ohne Trenner: MMDDYY oder MMDDYYYY
    roh = "".join(c for c, _ in zeichen if c != "/")
    if len(roh) in (6, 8) and roh.isdigit():
        pruefen(roh[:2], roh[2:4], roh[4:], 0)
    return sorted(aus.items(), key=lambda kv: -kv[1])


STRENG = re.compile(r"^\d{1,2}[/-]\d{1,2}[/-](\d{2}|\d{4})$|^\d{1,2}-[A-Z][a-z]{2}-\d{2}$")
# Datumsangaben der vorgedruckten Beispielzeile (02/05/015 bzw. 02/05/24 und 03/07/15, 8/14/12)
BEISPIEL_DATEN = {"0205015", "020515", "020524", "020520", "81412", "0307015", "030715", "030724", "030720"}


def beispiel_datum(r):
    for t in (r.get("datum_roh") or []) + (r.get("gemeldet_roh") or []):
        if t and "".join(ch for ch in t.translate(ZIFFER) if ch.isdigit()) in BEISPIEL_DATEN:
            return True
    return False


def datum_abstimmen(texte, von, bis):
    """texte: Rohtexte derselben Zelle aus mehreren OCR-Fassungen. Gibt (iso oder None, sicher, Begruendung).
    Angenommen wird eine Lesart, die in mindestens zwei Fassungen vorn liegt und keiner anderen Spitzenlesung
    unterliegt, oder die einzige Lesung, wenn ihr Rohtext sauber ist und die anderen Fassungen nichts liefern."""
    punkte = collections.Counter()
    spitzen = []
    sauber = set()
    vorhanden = collections.Counter()
    for t in texte:
        if t is None:
            continue
        k = [(iso, g) for iso, g in datum_kandidaten(t) if von <= iso <= bis]
        if not k:
            continue
        for iso, g in k:
            punkte[iso] += g
            vorhanden[iso] += 1
        if len(k) == 1 or k[0][1] > k[1][1]:
            spitzen.append(k[0][0])
            if STRENG.match(re.sub(r"\s+", "", t)):
                sauber.add(k[0][0])
    if not punkte:
        return None, False, "keine Lesart"
    best = max(punkte, key=lambda i: (spitzen.count(i), vorhanden[i], punkte[i]))
    andere = [s for s in spitzen if s != best]
    if spitzen.count(best) >= 2 and not andere:
        return best, True, "einig"
    if spitzen.count(best) >= 2 and spitzen.count(best) >= 3 * len(andere):
        return best, True, "mehrheit"
    if spitzen == [best] and best in sauber:
        return best, True, "einzeln sauber"
    return best, False, "uneinig: " + ",".join(sorted(set(spitzen))) + " roh " + " | ".join(t or "" for t in texte)


def inhaber_lesen(text):
    t = re.sub(r"[^A-Z0-9]", "", (text or "").upper())
    if not t:
        return ""
    for k, var in (("SP", ("SP", "SF", "SR", "5P", "SPP")), ("DC", ("DC", "OC", "DG", "0C", "BC", "DCC")),
                   ("JT", ("JT", "IT", "JI", "J1", "JF", "LT"))):
        if t in var:
            return k
    return "?" + t


def kreuz_wahl(werte, schwelle=MIN_TINTE, faktor=2.2):
    """werte: {spalte: (tinte, flaeche)}. Gibt (gewaehlt oder None, Liste aller Kandidaten ueber der Schwelle)."""
    kand = {k: v for k, v in werte.items() if v[1] >= min(MIN_FLAECHE, schwelle) and v[0] >= schwelle}
    if not kand:
        return None, []
    reihe = sorted(kand, key=lambda k: -kand[k][0])
    if len(reihe) == 1:
        return reihe[0], reihe
    a, b = kand[reihe[0]][0], kand[reihe[1]][0]
    if a >= faktor * b:
        return reihe[0], reihe
    return None, reihe


def schrift_maske(a, x0, x1, y0, y1, schwelle=150, min_teil=15):
    """Schrift einer Zelle ohne Linien und Kruemel als Bool-Bild, auf die Schrift zugeschnitten (oder None)."""
    from scipy import ndimage
    t = Z.zelle(a, None, x0, x1, y0, y1)
    m = t < schwelle
    lab, k = ndimage.label(m, structure=np.ones((3, 3), bool))
    if k:
        groesse = np.bincount(lab.ravel())
        behalten = groesse >= min_teil
        behalten[0] = False
        # Linienreste: duenne Teile, die den Zellrand beruehren
        h, w = m.shape
        for i, sl in enumerate(ndimage.find_objects(lab), start=1):
            if sl is None or not behalten[i]:
                continue
            ys, xs = sl
            dick_y, dick_x = ys.stop - ys.start, xs.stop - xs.start
            am_rand = ys.start <= 1 or xs.start <= 1 or ys.stop >= h - 1 or xs.stop >= w - 1
            if am_rand and (dick_x <= 6 or dick_y <= 6):
                behalten[i] = False
        m = behalten[lab]
    if m.sum() < 25:
        return None
    ys, xs = np.nonzero(m)
    return m[ys.min():ys.max() + 1, xs.min():xs.max() + 1]


def normiert(m, groesse):
    v = np.asarray(Image.fromarray((m * 255).astype(np.uint8)).resize(groesse, Image.BILINEAR), dtype=np.float32)
    v = v - v.mean()
    n = np.linalg.norm(v)
    return v / n if n else None


def zelle_bild(a, x0, x1, y0, y1):
    """Schrift der Inhaberzelle auf 16 x 32 gebracht und normiert (Vergleich der Kuerzel SP, DC, JT)."""
    m = schrift_maske(a, x0, x1, y0, y1)
    return normiert(m, (32, 16)) if m is not None else None


def blatt_woerter(cache, mid, s, feld, nr, fassung):
    f = os.path.join(cache, "blatt", mid, "p%03d_%s_%d_%s.json" % (s, feld, nr, fassung))
    return ocr_woerter(f, BLATT_FASSUNGEN[fassung]) if os.path.exists(f) else None


def seite_lesen(mid, s, g, zeilen_def, cache):
    a = np.array(Image.open(os.path.join(cache, "seiten", mid, "p%03d.png" % s)))
    d = a < T.SCHWELLE
    sp = g["spalten"]
    y_off = g.get("streifen_y0", 0)
    ocr = {}
    for key in ("inhaber", "name", "datum", "gemeldet"):
        for suffix, faktor in FASSUNGEN.items():
            ocr[key + suffix] = ocr_woerter(os.path.join(cache, "streifen", mid, "p%03d_%s%s.json" % (s, key, suffix)),
                                            faktor)
    blatt = {}
    arten = [k[4:] for k in sp if k.startswith("art_")]
    betr = [k[7:] for k in sp if k.startswith("betrag_")]
    zeilen = []
    for nr, z in enumerate(zeilen_def):
        r = {"s": s, "z": nr, "y0": z["y0"], "y1": z["y1"]}
        if z.get("geteilt"):
            r["geteilt"] = True
        if z["balken"]:
            r["befund"] = "zwischenzeile"
            r["name_roh"] = woerter_der_zeile(ocr["name"], y_off, z["y0"], z["y1"])
            zeilen.append(r)
            continue
        for key in ("inhaber", "datum", "gemeldet"):
            texte = []
            pos = z.get("blatt", {}).get(key)
            if pos:
                for fassung in BLATT_FASSUNGEN:
                    k = (key, pos[0], fassung)
                    if k not in blatt:
                        blatt[k] = blatt_woerter(cache, mid, s, key, pos[0], fassung)
                    w = blatt[k]
                    texte.append(woerter_der_zeile(w, 0, pos[1] - 10, pos[2] + 10) if w is not None else None)
            for suffix in FASSUNGEN:
                w = ocr[key + suffix]
                texte.append(woerter_der_zeile(w, y_off, z["y0"], z["y1"]) if w is not None else None)
            r[key + "_roh"] = texte
        r["name_roh"] = woerter_der_zeile(ocr["name"], y_off, z["y0"], z["y1"])
        kand = [r["name_roh"]]
        pos = z.get("blatt", {}).get("name")
        if pos:
            for fassung in ("f2", "f3"):
                k = ("name", pos[0], fassung)
                if k not in blatt:
                    blatt[k] = blatt_woerter(cache, mid, s, "name", pos[0], fassung)
                if blatt[k] is not None:
                    kand.append(woerter_der_zeile(blatt[k], 0, pos[1] - 10, pos[2] + 10))
        r["name_kand"] = kand
        r["art_tinte"] = {k: T.tinte(d, sp["art_" + k][0], sp["art_" + k][1], z["y0"], z["y1"]) for k in arten}
        r["betrag_tinte"] = {k: T.tinte(d, sp["betrag_" + k][0], sp["betrag_" + k][1], z["y0"], z["y1"])
                             for k in betr}
        r["name_tinte"] = T.tinte(d, sp["name"][0], sp["name"][1], z["y0"], z["y1"], laenge=40)[0]
        r["datum_tinte"] = T.tinte(d, sp["datum"][0], sp["datum"][1], z["y0"], z["y1"], laenge=40)[0]
        if sp.get("inhaber"):
            r["_inhaber_bild"] = zelle_bild(a, sp["inhaber"][0], sp["inhaber"][1], z["y0"], z["y1"])
        for key in ("datum", "gemeldet"):
            r["_" + key + "_bild"] = datum_bild(a, sp[key][0], sp[key][1], z["y0"], z["y1"])
        zeilen.append(r)
    # Schwelle fuer Kreuze je Seite: ueber dem Rauschen leerer Zellen (Kaestchenreste), mindestens 25 Pixel
    # Rauschen je Zeile: groesster Wert der Betragszellen ohne den hoechsten (dort steht hoechstens ein Kreuz);
    # Kopfzeilen mit Beschriftung in vielen Zellen zaehlen nicht
    rauschen = []
    for r in zeilen:
        b = sorted((v[0] for v in r.get("betrag_tinte", {}).values()), reverse=True)
        if len(b) >= 3 and sum(1 for x in b if x >= 100) < 4:
            rauschen.append(b[1])
    if rauschen:
        rauschen.sort()
        q = rauschen[len(rauschen) // 2]
        schwelle = max(24, 2.0 * q + 8)
        for r in zeilen:
            r["schwelle"] = round(schwelle, 1)
    return zeilen


def deuten(r, eingang):
    if r.get("befund") == "zwischenzeile":
        return r
    name = max(r.get("name_kand") or [r["name_roh"]], key=len)
    datum_alle = " ".join(t for t in r["datum_roh"] + r["gemeldet_roh"] if t)
    haupt = {k: v for k, v in r["art_tinte"].items() if k in ("P", "S", "E", "PS")}
    schwelle = r.get("schwelle", MIN_TINTE)
    art, art_k = kreuz_wahl(haupt, schwelle)
    betrag, betrag_k = kreuz_wahl(r["betrag_tinte"], schwelle)
    # Kopfzeile (Spaltenbeschriftung) oder vorgedrucktes Beispiel („Example: Mega Corp. Common Stock“)
    kopf = (re.search(r"(?i)provide|ticker|full\s*name|asset\s*name|name, not", name)
            or re.search(r"(?i)MM|DD|YY", datum_alle) or len(betrag_k) >= 5)
    # vorgedrucktes Beispiel; die Datumsspalte enthaelt dort 02/05/24 bzw. 8/14/12
    beispiel = ((re.search(r"(?i)\bmeg[ag]?\b|\bmep\b|mega\s*c|exam|xampl|ample:|\be\s*mpl", name)
                 and not re.search(r"(?i)megacorp|omega", name)) or beispiel_datum(r))
    pt = r["art_tinte"].get("PT")
    teil = bool(pt and pt[1] >= min(MIN_FLAECHE, schwelle) and pt[0] >= schwelle)
    d_ein = datetime.date.fromisoformat(eingang)
    # Lesefenster drei Jahre vor der Einreichung; aelter als etwa 15 Monate wird zur Pruefung vorgelegt
    von = (d_ein - datetime.timedelta(days=3 * 365)).isoformat()
    grenze = (d_ein - datetime.timedelta(days=470)).isoformat()
    r["datum"], r["datum_sicher"], r["datum_grund"] = datum_abstimmen(r["datum_roh"], von, eingang)
    r["gemeldet"], r["gemeldet_sicher"], _ = datum_abstimmen(r["gemeldet_roh"], r["datum"] or von, eingang)
    if not r["gemeldet_sicher"]:
        r["gemeldet"] = None
    lesungen = [inhaber_lesen(t) for t in r["inhaber_roh"] if t]
    gut = [x for x in lesungen if x in ("SP", "DC", "JT")]
    r["inhaber"] = collections.Counter(gut).most_common(1)[0][0] if gut else ("?" if lesungen else "")
    if gut and len(set(gut)) > 1:
        r["inhaber"] = "?"
    r["art_kand"], r["betrag_kand"] = art_k, betrag_k
    r["art"] = {"P": "KAUF", "S": "TEILVERKAUF" if teil else "VERKAUF", "PS": "TEILVERKAUF", "E": "TAUSCH"}.get(art)
    r["betrag"] = betrag
    leer = (not name.strip() and not art_k and not betrag_k and not datum_alle.strip()
            and r["name_tinte"] < 150 and r["datum_tinte"] < 150)
    # ohne Namen und ohne lesbares Datum: Kruemel oder Haken in sonst leeren Zeilen (gedruckte Formulare)
    leer = leer or (not name.strip() and not any(datum_kandidaten(t) for t in r["datum_roh"] if t)
                    and r["datum_tinte"] < 150)
    if kopf:
        r["befund"] = "kopf"
    elif beispiel:
        r["befund"] = "beispiel"
    elif leer:
        r["befund"] = "leer"
    elif not art_k and not betrag_k and not r["datum"] and not any(
            datum_kandidaten(t) for t in r["datum_roh"] if t):
        r["befund"] = "zwischenzeile"
    else:
        probleme = []
        if not art:
            probleme.append("art" if not art_k else "art mehrfach")
        if not betrag:
            probleme.append("betrag" if not betrag_k else "betrag mehrfach")
        if not r["datum"]:
            probleme.append("datum")
        elif not r["datum_sicher"]:
            probleme.append("datum unsicher")
        elif r["datum"] < grenze:
            probleme.append("datum aelter als 15 Monate")
        elif r["gemeldet"] and (datetime.date.fromisoformat(r["gemeldet"])
                                - datetime.date.fromisoformat(r["datum"])).days > 120:
            probleme.append("benachrichtigung ueber 120 Tage nach dem Geschaeft")
        if not name.strip():
            probleme.append("name")
        r["probleme"] = probleme
        r["befund"] = "geschaeft" if not probleme else "pruefen"
    return r


def datum_bild(a, x0, x1, y0, y1):
    """Schrift einer Datumszelle (ohne Linien), auf 24 x 96 gebracht und normiert."""
    t = Z.zelle(a, None, x0, x1, y0, y1)
    m = t < 150
    if m.sum() < 25:
        return None
    ys, xs = np.nonzero(m)
    t = t[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    v = np.asarray(Image.fromarray(255 - t).resize((96, 24), Image.BILINEAR), dtype=np.float32)
    v = v - v.mean()
    n = np.linalg.norm(v)
    return v / n if n else None


# Verwechslungen der OCR bei Ziffern und Trennern (Kosten 0,3 statt 1)
VERWECHSLUNG = {"0": "ODQUoDu@", "1": "lIi|!]t[jJ7/", "2": "Zz", "3": "B8", "4": "AqY", "5": "Ss$", "6": "Gb&",
                "7": "T?1/", "8": "B&3S", "9": "gq", "/": "1lIi|!t7(Jj\\"}


def zeichen_kosten(a, b):
    if a == b:
        return 0.0
    if a in VERWECHSLUNG.get(b, ""):
        return 0.3
    return 1.0


@functools.lru_cache(maxsize=500000)
def lese_abstand(roh, ziel):
    """Gewichteter Abstand zwischen OCR-Text und einer Schreibweise eines Datums (Einfuegen 1, Weglassen von
    Leerzeichen und Satzzeichen 0,3)."""
    a = re.sub(r"\s+", "", roh)
    n, m = len(a), len(ziel)
    d = [[0.0] * (m + 1) for _ in range(n + 1)]
    for i in range(1, n + 1):
        d[i][0] = d[i - 1][0] + (0.3 if not a[i - 1].isalnum() else 1.0)
    for j in range(1, m + 1):
        d[0][j] = d[0][j - 1] + 1.0
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            d[i][j] = min(d[i - 1][j] + (0.3 if not a[i - 1].isalnum() else 1.0), d[i][j - 1] + 1.0,
                          d[i - 1][j - 1] + zeichen_kosten(a[i - 1], ziel[j - 1]))
    return d[n][m]


FORMATE = {"mm/dd/yy": "%02d/%02d/%s2", "m/d/yy": "%d/%d/%s2", "mm/dd/yyyy": "%02d/%02d/%s4",
           "m/d/yyyy": "%d/%d/%s4", "mm-dd-yyyy": "%02d-%02d-%s4"}


def schreibweisen(t, formate=None):
    j2, j4 = t.strftime("%y"), t.strftime("%Y")
    aus = set()
    for name, f in FORMATE.items():
        if formate and name not in formate:
            continue
        muster, jahr = f[:-3] + "%s", (j2 if f.endswith("2") else j4)
        aus.add(muster % (t.month, t.day, jahr))
    return aus


def formate_der_meldung(zeilen):
    """Schreibweisen, in denen die sicher gelesenen Daten einer Meldung stehen (Anteil ab 10 Prozent)."""
    zaehl = collections.Counter()
    for r in zeilen:
        if not (r.get("datum") and r.get("datum_sicher")):
            continue
        t = datetime.date.fromisoformat(r["datum"])
        for x in r.get("datum_roh", []):
            x = re.sub(r"\s+", "", x or "")
            for name in FORMATE:
                if x in schreibweisen(t, {name}):
                    zaehl[name] += 1
    n = sum(zaehl.values())
    return {k for k, v in zaehl.items() if v >= 0.1 * n} if n >= 5 else None


def datum_woerterbuch(texte, von, bis, tage=None, formate=None):
    """Liest ein Datum gegen alle Tage im Zeitfenster: Summe der Abstaende aller OCR-Fassungen zur besten
    Schreibweise je Tag. Angenommen, wenn der beste Tag im Schnitt hoechstens 1,2 entfernt ist und der zweitbeste
    in der Summe mindestens 1,0 schlechter abschneidet."""
    texte = [t for t in texte if t and sum(ch.isalnum() for ch in t) >= 4]
    if len(texte) < 2:
        return None, None
    d0, d1 = datetime.date.fromisoformat(von), datetime.date.fromisoformat(bis)
    if tage is None:
        tage = [d0 + datetime.timedelta(days=i) for i in range((d1 - d0).days + 1)]
    texte = tuple(re.sub(r"\s+", "", x) for x in texte)
    werte = []
    for t in tage:
        if not d0 <= t <= d1:
            continue
        sw = schreibweisen(t, formate)
        werte.append((sum(min(lese_abstand(x, z) for z in sw) for x in texte), t.isoformat()))
    if len(werte) < 2:
        return None, None
    werte.sort()
    best, zweit = werte[0], werte[1]
    if best[0] / len(texte) <= 1.2 and zweit[0] - best[0] >= 1.0:
        return best[1], round(best[0] / len(texte), 2)
    return None, None


def ziffern_vergleichen(zeilen, mid, g, cache, eingang):
    """Ziffernvergleich (ziffern.py) fuer Meldungen im Format MM/DD/YY: lernt aus sicher gelesenen Datums- und
    Benachrichtigungszellen und liest die unsicheren. Widerspricht das Ergebnis allen Spitzenlesungen der OCR, muss
    der Abstand zur zweitbesten Ziffer gross sein."""
    sicher = [r for r in zeilen if r.get("befund") in ("geschaeft", "pruefen") and r.get("datum_sicher")
              and (r.get("datum_grund") or "").split(":")[0] in ("einig", "mehrheit", "einzeln sauber")]
    roh = [t for r in sicher for t in r["datum_roh"] if t and STRENG.match(re.sub(r"\s+", "", t))]
    if len(sicher) < 15 or not roh or sum(1 for t in roh if ZF.MUSTER.match(re.sub(r"\s+", "", t))) < 0.7 * len(roh):
        return 0
    seiten = {}

    def bild(r, feld):
        if r["s"] not in seiten:
            seiten.clear()
            seiten[r["s"]] = np.array(Image.open(os.path.join(cache, "seiten", mid, "p%03d.png" % r["s"])))
        c = g["seiten"][str(r["s"])]["spalten"][feld]
        return ZF.schrift(Z.zelle(seiten[r["s"]], None, c[0], c[1], r["y0"], r["y1"]))

    leser = ZF.Ziffernleser()
    for r in sorted(sicher, key=lambda r: r["s"]):
        leser.lernen(bild(r, "datum"), r["datum"])
        if r.get("gemeldet") and r.get("gemeldet_sicher"):
            leser.lernen(bild(r, "gemeldet"), r["gemeldet"])
    leser.fertig()
    d_ein = datetime.date.fromisoformat(eingang)
    von = (d_ein - datetime.timedelta(days=470)).isoformat()
    n = 0
    for r in sorted(zeilen, key=lambda r: r["s"]):
        if r.get("befund") not in ("geschaeft", "pruefen"):
            continue
        for feld, unten in (("datum", von), ("gemeldet", None)):
            nach_bild = feld == "datum" and (r.get("datum_grund") or "").startswith("bildvergleich")
            if r.get(feld + "_sicher") and not nach_bild:
                continue
            if feld == "gemeldet" and not (r.get("datum") and r.get("datum_sicher")):
                continue
            iso, q = leser.lesen(bild(r, feld), unten or r["datum"], eingang, 0.72, 0.05)
            if nach_bild:
                # Bildvergleich der ganzen Zelle gegen den Ziffernvergleich pruefen
                if iso and iso != r["datum"]:
                    r["datum_sicher"], r["datum_grund"] = False, "widerspruch bild/ziffern"
                continue
            if not iso:
                continue
            spitzen = []
            if feld == "datum" and (r.get("datum_grund") or "").startswith("uneinig"):
                spitzen = re.findall(r"\d{4}-\d{2}-\d{2}", r["datum_grund"].split(" roh ")[0])
            if spitzen and iso not in spitzen and q[1] < 0.10:
                continue
            r[feld], r[feld + "_sicher"] = iso, True
            if feld == "datum":
                r["datum_grund"] = "ziffern %.2f/%.2f" % q
            n += 1
    return n


def datum_vergleichen(zeilen, eingang):
    """Daten ohne sichere OCR-Lesung ueber den Bildvergleich mit sicher gelesenen Zellen derselben Meldung: Die
    Zelle muss einem Datum sehr aehnlich sein (Korrelation ab 0,90) und deutlich aehnlicher als jedem anderen
    (Abstand ab 0,04). Bei OCR-Lesarten wird nur unter diesen gewaehlt."""
    for key in ("datum", "gemeldet"):
        beispiele = collections.defaultdict(list)
        for r in zeilen:
            b = r.get("_" + key + "_bild")
            if b is not None and r.get(key) and r.get(key + "_sicher"):
                beispiele[r[key]].append(b)
        if not beispiele:
            for r in zeilen:
                r.pop("_" + key + "_bild", None)
            continue
        for r in zeilen:
            b = r.get("_" + key + "_bild")
            if b is None or r.get(key + "_sicher") or r.get("befund") not in ("geschaeft", "pruefen"):
                continue
            werte = sorted(((max(float((b * x).sum()) for x in v), iso) for iso, v in beispiele.items()),
                           reverse=True)
            erlaubt = None
            if key == "datum" and r.get("datum_grund", "").startswith("uneinig"):
                erlaubt = set(re.findall(r"\d{4}-\d{2}-\d{2}", r["datum_grund"].split(" roh ")[0]))
            if not werte:
                continue
            best, iso = werte[0]
            zweit = werte[1][0] if len(werte) > 1 else 0.0
            if best >= 0.90 and best - zweit >= 0.04 and (not erlaubt or iso in erlaubt):
                r[key] = iso
                r[key + "_sicher"] = True
                r[key + "_bild"] = round(best, 3)
                if key == "datum":
                    r["datum_grund"] = "bildvergleich %.3f" % best
        for r in zeilen:
            r.pop("_" + key + "_bild", None)


def befund_neu(r, eingang, gemeldet_ueblich=None):
    """Probleme nach dem Bildvergleich neu bestimmen."""
    if r.get("befund") not in ("geschaeft", "pruefen"):
        return
    d_ein = datetime.date.fromisoformat(eingang)
    grenze = (d_ein - datetime.timedelta(days=470)).isoformat()
    probleme = [p for p in r.get("probleme", []) if not p.startswith("datum") and not p.startswith("benachrichtigung")]
    if not r["datum"]:
        probleme.append("datum")
    elif not r["datum_sicher"]:
        probleme.append("datum unsicher")
    elif r["datum"] < grenze:
        probleme.append("datum aelter als 15 Monate")
    elif (r.get("gemeldet") or gemeldet_ueblich) and (
            datetime.date.fromisoformat(r.get("gemeldet") or gemeldet_ueblich)
            - datetime.date.fromisoformat(r["datum"])).days > 75:
        probleme.append("benachrichtigung ueber 75 Tage nach dem Geschaeft")
    if r.get("gemeldet") and r.get("datum") and r["gemeldet"] < r["datum"]:
        r["gemeldet"] = None
    r["probleme"] = probleme
    r["befund"] = "geschaeft" if not probleme else "pruefen"


def inhaber_vergleichen(zeilen):
    """Inhaber ohne sichere OCR-Lesung ueber den Bildvergleich mit sicher gelesenen Zellen derselben Meldung."""
    vorlagen = collections.defaultdict(list)
    for r in zeilen:
        if r.get("_inhaber_bild") is not None and r.get("inhaber") in ("SP", "DC", "JT"):
            vorlagen[r["inhaber"]].append(r["_inhaber_bild"])
    mittel = {}
    for k, v in vorlagen.items():
        if len(v) >= 2:
            m = np.mean(v, axis=0)
            n = np.linalg.norm(m)
            mittel[k] = m / n if n else m
    for r in zeilen:
        b = r.pop("_inhaber_bild", None)
        if r.get("befund") not in ("geschaeft", "pruefen") or r.get("inhaber") in ("SP", "DC", "JT"):
            continue
        if b is None:
            # keine Tinte in der Inhaberzelle: eigenes Depot
            r["inhaber"] = ""
            continue
        werte = sorted(((float((b * v).sum()), k) for k, v in mittel.items()), reverse=True)
        if werte and werte[0][0] >= 0.8 and (len(werte) == 1 or werte[0][0] - werte[1][0] >= 0.08):
            r["inhaber"] = werte[0][1]
            r["inhaber_bild"] = round(werte[0][0], 2)
        else:
            r["inhaber"] = "?"


def main():
    liste = json.load(open(sys.argv[1], encoding="utf-8"))
    cache = sys.argv[2]
    nur = set(sys.argv[3:])
    os.makedirs(os.path.join(cache, "zeilen"), exist_ok=True)
    for m in liste:
        if nur and m["id"] not in nur:
            continue
        g = json.load(open(os.path.join(cache, "gitter", m["id"] + ".json"), encoding="utf-8"))
        zdef = json.load(open(os.path.join(cache, "blatt", m["id"], "zeilen.json"), encoding="utf-8"))
        alle = []
        for s, info in sorted(g["seiten"].items(), key=lambda kv: int(kv[0])):
            if not info.get("spalten") or s not in zdef:
                continue
            for r in seite_lesen(m["id"], int(s), info, zdef[s], cache):
                alle.append(deuten(r, m["eingang"]))
        inhaber_vergleichen(alle)
        datum_vergleichen(alle, m["eingang"])
        ziffern_vergleichen(alle, m["id"], g, cache, m["eingang"])
        # was danach noch offen ist: Abgleich gegen alle Tage im Zeitfenster
        d_ein = datetime.date.fromisoformat(m["eingang"])
        von = (d_ein - datetime.timedelta(days=470)).isoformat()
        # Kandidaten: 45 Tage um den mittleren sicher gelesenen Tag (sonst die 120 Tage vor der Einreichung) und
        # alle sicher gelesenen Tage der Meldung
        sichere = sorted(datetime.date.fromisoformat(r[k]) for r in alle for k in ("datum", "gemeldet")
                         if r.get(k) and r.get(k + "_sicher"))
        if sichere:
            mitte = sichere[len(sichere) // 2]
            tage = {mitte + datetime.timedelta(days=i) for i in range(-45, 46)}
        else:
            tage = {d_ein - datetime.timedelta(days=i) for i in range(121)}
        tage = sorted(tage | set(sichere))
        formate = formate_der_meldung(alle)
        for r in alle:
            if r.get("befund") not in ("geschaeft", "pruefen"):
                continue
            if not r.get("datum_sicher"):
                iso, abst = datum_woerterbuch(r["datum_roh"], von, m["eingang"], tage, formate)
                if iso:
                    r["datum"], r["datum_sicher"], r["datum_grund"] = iso, True, "woerterbuch %.2f" % abst
            if not r.get("gemeldet_sicher") and r.get("datum"):
                iso, abst = datum_woerterbuch(r["gemeldet_roh"], r["datum"], m["eingang"], tage, formate)
                if iso:
                    r["gemeldet"], r["gemeldet_sicher"] = iso, True
        gm = collections.Counter(r["gemeldet"] for r in alle if r.get("gemeldet"))
        ueblich = gm.most_common(1)[0][0] if gm else None
        for r in alle:
            befund_neu(r, m["eingang"], ueblich)
        ziel = os.path.join(cache, "zeilen", m["id"] + ".json")
        with open(ziel + ".teil", "w", encoding="utf-8") as f:
            json.dump({"id": m["id"], "name": m["name"], "eingang": m["eingang"], "zeilen": alle}, f,
                      ensure_ascii=False, indent=0)
        os.replace(ziel + ".teil", ziel)
        print(m["name"], m["id"], dict(collections.Counter(r["befund"] for r in alle)), flush=True)


if __name__ == "__main__":
    main()
