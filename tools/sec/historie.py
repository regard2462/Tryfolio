# -*- coding: utf-8 -*-
"""Verdichtete Kaufhistorie je Firma fuer "Seltene Kaeufe" (assets/copytrading/sec_historie.json).

Quelle sind dieselben Form-345-Datensaetze der SEC wie bei holen.py, aeltere Quartale (Standard ab 2016). Die Rohdaten
kommen nicht in die App: je Firma bleibt, was InsiderSeltenheit fuer ihre Aussagen braucht.

Je Ticker: [n, kaeufe, wochen]
  n       Zahl der Kaeufe an der Boerse im Zeitraum (0: nur Verkaeufe, die Firma war gemeldet)
  kaeufe  Kette der Rekordkaeufe, neueste zuerst: [Tag, Dollar]; jeder Eintrag ist groesser als alle Kaeufe nach ihm.
          Der erste ist der letzte Kauf, der letzte der groesste. "Letzter Kauf mit mindestens X Dollar" ist der erste
          Eintrag mit Wert >= X.
  wochen  Kette der Wochen mit mindestens 3 verschiedenen Kaeufern, neueste zuerst: [Tag, Kaeufer]; jeder Eintrag hat
          mehr Kaeufer als alle spaeteren Wochen. Tage als Zahl JJJJMMTT, Meldetag (sonst Tag des Geschaefts).

Die Bereinigung entspricht SecInsider.bereinigen: steht im Preisfeld der Gesamterloes, gilt er als Wert; dasselbe
Geschaeft mehrerer gemeinsam Meldender zaehlt einmal.

Aufruf: python historie.py <ziel.json> <von JJJJ-MM-TT> <bis JJJJ-MM-TT> <zip> [<zip> ...]
        python historie.py --test
"""
import datetime as dt
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import holen  # noqa: E402

MIN_KAEUFER = 3
FONDS_KUERZEL = re.compile(r"^[A-Z]{4}X$")
TICKER = re.compile(r"^[A-Z0-9][A-Z0-9.-]*$")
QUELLE = "U.S. Securities and Exchange Commission (EDGAR, Form 4)"


def tagnr(iso):
    return int(iso.replace("-", ""))


def epoche(nr):
    return dt.date(nr // 10000, nr // 100 % 100, nr % 100).toordinal()


def bereinigen(liste):
    """Wie SecInsider.bereinigen. Geschaefte: Dicts mit k, d, kauf, n, preis, wert, nach, p, r."""
    gerade = []
    for g in liste:
        if g["preis"] > 10000.0 and g["n"] > 0.0 and g["preis"] / g["n"] < 1000.0 and not g["k"].startswith("BRK"):
            g = dict(g, wert=g["preis"], preis=g["preis"] / g["n"])
        gerade.append(g)
    gruppen = {}
    for g in gerade:
        if g["wert"] > 0 and g.get("nach") is not None:
            gruppen.setdefault((g["k"], g["d"], g["kauf"], g["n"], g["preis"], g["nach"]), []).append(g)
    bleibt = {}
    for k, v in gruppen.items():
        if len(v) > 1 and len({x["p"].strip().lower() for x in v}) > 1:
            bleibt[k] = next((x for x in v if x.get("r", "N") != "N"), v[0])
    if not bleibt:
        return gerade
    aus = []
    for g in gerade:
        if g["wert"] > 0 and g.get("nach") is not None:
            k = (g["k"], g["d"], g["kauf"], g["n"], g["preis"], g["nach"])
            if k in bleibt and bleibt[k] is not g:
                continue
        aus.append(g)
    return aus


def kette(eintraege):
    """Eintraege (Tag, Wert) beliebig geordnet -> Rekordkette, neueste zuerst, Wert strikt steigend."""
    aus = []
    hoechster = None
    for tag, wert in sorted(eintraege, key=lambda e: (e[0], e[1]), reverse=True):
        if hoechster is None or wert > hoechster:
            aus.append([tag, wert])
            hoechster = wert
    return aus


def verdichten(kaeufe, aktiv):
    """kaeufe: bereinigte Kaeufe (Dicts k, tag als Zahl, wert, p); aktiv: alle Ticker mit Meldungen. -> {ticker: [...]}"""
    je = {}
    for g in kaeufe:
        if g["wert"] > 0:
            je.setdefault(g["k"], []).append(g)
    aus = {}
    for k in aktiv:
        if FONDS_KUERZEL.match(k) or not TICKER.match(k):
            continue
        gs = je.get(k, [])
        if not gs:
            aus[k] = [0, [], []]
            continue
        rekorde = kette([(g["tag"], int(round(g["wert"]))) for g in gs])
        woche = []
        for t in sorted({g["tag"] for g in gs}):
            e = epoche(t)
            personen = {g["p"].strip().lower() for g in gs if e - 6 <= epoche(g["tag"]) <= e}
            if len(personen) >= MIN_KAEUFER:
                woche.append((t, len(personen)))
        aus[k] = [len(gs), rekorde, kette(woche)]
    return aus


def sammeln(zips, von, bis):
    kaeufe, aktiv = [], set()
    for pfad in zips:
        aus = {}
        holen.lesen(pfad, aus)
        for g in aus.values():
            aktiv.add(g["k"])
            if not g["kauf"] or g["wert"] <= 0 or not g["d"] or not (von <= g["d"] <= bis) or g["n"] <= 0:
                continue
            kaeufe.append({"k": g["k"], "d": g["d"], "kauf": True, "n": round(g["n"], 4), "preis": round(g["wert"] / g["n"], 4),
                           "wert": round(g["wert"], 2), "nach": g.get("nach"), "p": g["p"], "r": g["r"],
                           "tag": tagnr(g.get("f") or g["d"])})
        del aus
    return kaeufe, aktiv


def schreiben(ziel, von, bis, firmen):
    inhalt = {"stand": bis, "von": von, "bis": bis, "quelle": QUELLE, "firmen": firmen}
    text = json.dumps(inhalt, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    with open(ziel, "wb") as f:
        f.write(text.encode("utf-8"))


def selbsttest():
    def k(t, tag, wert, p, n=100.0, nach=1000.0, r="N", preis=None):
        return {"k": t, "d": "2020-01-01", "kauf": True, "n": n, "preis": preis if preis is not None else wert / n, "wert": wert,
                "nach": nach, "p": p, "r": r, "tag": tag}

    # Gesamterloes im Preisfeld: der Wert wird der gemeldete Betrag
    b = bereinigen([k("AAA", 20200101, 50000000.0, "x", n=1000.0, preis=50000.0)])
    assert b[0]["wert"] == 50000.0 and abs(b[0]["preis"] - 50.0) < 1e-9, b
    # gemeinsam Meldende: einmal, mit Amt
    b = bereinigen([k("BBB", 20200102, 1000.0, "Anna"), k("BBB", 20200102, 1000.0, "Bert", r="S")])
    assert len(b) == 1 and b[0]["p"] == "Bert", b
    # dieselbe Person zweimal bleibt zweimal
    assert len(bereinigen([k("BBB", 20200102, 1000.0, "Anna"), k("BBB", 20200102, 1000.0, "Anna")])) == 2
    # Rekordkette: neueste zuerst, Wert steigend
    assert kette([(20180101, 5), (20190101, 3), (20200101, 2), (20170101, 9)]) == [[20200101, 2], [20190101, 3], [20180101, 5], [20170101, 9]]
    assert kette([(20180101, 5), (20190101, 7), (20200101, 2)]) == [[20200101, 2], [20190101, 7]]
    # Verdichtung: Woche mit drei Kaeufern, nur Verkaeufer, Fonds
    kaeufe = [k("CCC", 20190301, 1000.0, "a"), k("CCC", 20190303, 2000.0, "b"), k("CCC", 20190305, 500.0, "c"),
              k("CCC", 20210101, 300.0, "a"), k("FUNDX", 20190301, 10.0, "a")]
    v = verdichten(kaeufe, {"CCC", "DDD", "FUNDX"})
    assert v["CCC"][0] == 4 and v["CCC"][1] == [[20210101, 300], [20190305, 500], [20190303, 2000]], v
    assert v["CCC"][2] == [[20190305, 3]], v
    assert v["DDD"] == [0, [], []] and "FUNDX" not in v, v
    # Wochengrenze: sieben Tage einschliesslich
    assert verdichten([k("E", 20190101, 1.0, "a"), k("E", 20190104, 1.0, "b"), k("E", 20190108, 1.0, "c")], {"E"})["E"][2] == []
    assert verdichten([k("E", 20190101, 1.0, "a"), k("E", 20190104, 1.0, "b"), k("E", 20190107, 1.0, "c")], {"E"})["E"][2] == [[20190107, 3]]
    print("Selbsttest ok")


def main():
    if sys.argv[1:] == ["--test"]:
        selbsttest()
        return
    ziel, von, bis = sys.argv[1:4]
    kaeufe, aktiv = sammeln(sys.argv[4:], von, bis)
    firmen = verdichten(bereinigen(kaeufe), aktiv)
    schreiben(ziel, von, bis, firmen)
    print("fertig:", len(firmen), "Firmen,", sum(1 for v in firmen.values() if v[0]), "mit Kaeufen,", len(kaeufe), "Kaeufe", file=sys.stderr)


if __name__ == "__main__":
    main()
