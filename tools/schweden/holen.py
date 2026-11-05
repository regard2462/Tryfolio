# -*- coding: utf-8 -*-
"""Insidergeschaefte schwedischer Emittenten fuer die App (assets/copytrading/insider_se.json).

Quelle ist das oeffentliche Insynsregister der Finansinspektionen (FI): Meldungen nach Art. 19 MAR, als CSV ohne
Anmeldung abrufbar. Das Register ist gesetzlich oeffentlich. Uebernommen werden aktuelle Meldungen (keine ersetzten)
mit Person, Rolle, Art, Instrument, ISIN, Datum, Stueck, Preis und Waehrung, zusammengefasst je Person, ISIN, Tag und
Art. Die App holt neuere Meldungen selbst nach (derselbe Export).

Aufruf: python holen.py <ziel.json> [monate]
"""
import csv
import datetime as dt
import io
import json
import sys
import time
import urllib.request

UA = {"User-Agent": "Tryfolio/1.0 (open-source practice depot)"}
ADRESSE = ("https://marknadssok.fi.se/Publiceringsklient/sv-SE/Search/Search?SearchFunctionType=Insyn"
           "&Publiceringsdatum.From={von}&Publiceringsdatum.To={bis}&button=export")


def rolle(befattning, naerstaende):
    """S Spitze (VD, Finanzchef), F weitere Fuehrung, A Verwaltungsrat/Aufsicht, N nahestehend."""
    if naerstaende.strip().lower() == "ja":
        return "N"
    b = befattning.lower().replace("\xa0", " ")
    if "verkställande direktör" in b and "vice" not in b or "finanschef" in b or "ekonomichef" in b:
        return "S"
    if "styrelse" in b or "arbetstagarrepresentant" in b or "revisor" in b:
        return "A"
    return "F"


def schoen(name):
    """Namen ganz in Grossbuchstaben wie „ATLAS COPCO AKTIEBOLAG“ als „Atlas Copco Aktiebolag“; kurze Kuerzel
    (AB, ASA, SE) bleiben gross. Gemischt geschriebene Namen bleiben."""
    n = name.strip()
    if not n or n != n.upper() or not any(c.isalpha() for c in n):
        return n
    worte = []
    for w in n.split(" "):
        kern = w.strip(",.()")
        if len(kern) <= 3 and kern.isalpha():
            worte.append(w)
        else:
            worte.append("-".join(t[:1].upper() + t[1:].lower() for t in w.split("-")))
    return " ".join(worte)


def art(karaktaer):
    k = karaktaer.strip().lower()
    if k == "förvärv":
        return "K"
    if k == "avyttring":
        return "V"
    return "S"


def zahl(s):
    try:
        return float(s.replace("\xa0", "").replace(" ", "").replace(",", "."))
    except ValueError:
        return None


def holen(von, bis):
    url = ADRESSE.format(von=von.isoformat(), bis=bis.isoformat())
    for i in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120) as r:
                roh = r.read()
            time.sleep(1)
            # Der Export ist UTF-16, teils ohne Byte-Order-Mark
            if roh[:2] in (b"\xff\xfe", b"\xfe\xff"):
                return roh.decode("utf-16")
            if b"\x00" in roh[:200]:
                return roh.decode("utf-16-le")
            return roh.decode("utf-8", "replace")
        except Exception as e:  # noqa: BLE001
            print("Fehler", e, file=sys.stderr)
            time.sleep(5 * (i + 1))
    raise RuntimeError("nicht erreichbar")


def lesen(text, aus):
    for z in csv.DictReader(io.StringIO(text), delimiter=";"):
        if (z.get("Status") or "").strip() != "Aktuell":
            continue
        isin = (z.get("ISIN") or "").strip()
        n = zahl(z.get("Volym") or "")
        p = zahl(z.get("Pris") or "")
        if not isin or n is None or n <= 0:
            continue
        a = art(z.get("Karaktär") or "")
        d = (z.get("Transaktionsdatum") or "")[:10]
        person = (z.get("Person i ledande ställning") or "").strip()
        meldet = (z.get("Anmälningsskyldig") or "").strip()
        schluessel = (meldet, isin, d, a)
        g = aus.get(schluessel)
        if g is None:
            g = aus[schluessel] = {
                "e": schoen(z.get("Emittent") or ""), "isin": isin, "p": meldet,
                "fuer": person if person and person != meldet else "",
                "r": rolle(z.get("Befattning") or "", z.get("Närstående") or ""), "t": (z.get("Befattning") or "").replace("\xa0", " ").strip(),
                "i": (z.get("Instrumenttyp") or "").strip(), "art": a, "d": d, "v": (z.get("Publiceringsdatum") or "")[:10],
                "n": 0.0, "wert": 0.0, "w": (z.get("Valuta") or "").strip(), "o": (z.get("Handelsplats") or "").strip(),
            }
        g["n"] += n
        g["wert"] += n * (p or 0.0)


def main():
    ziel = sys.argv[1]
    monate = int(sys.argv[2]) if len(sys.argv) > 2 else 18
    heute = dt.date.today()
    beginn = heute - dt.timedelta(days=int(monate * 30.5))
    aus = {}
    von = beginn
    while von <= heute:
        bis = min(von + dt.timedelta(days=30), heute)
        lesen(holen(von, bis), aus)
        print(von, "bis", bis, "->", len(aus), file=sys.stderr)
        von = bis + dt.timedelta(days=1)
    liste = []
    for g in aus.values():
        g["preis"] = round(g["wert"] / g["n"], 6) if g["n"] else 0.0
        g["wert"] = round(g["wert"], 2)
        g["n"] = round(g["n"], 4)
        for feld in ("fuer", "o"):
            if not g[feld]:
                g.pop(feld)
        liste.append(g)
    liste.sort(key=lambda g: (g["d"], g["v"]), reverse=True)
    with open(ziel, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"stand": heute.isoformat(), "quelle": "Finansinspektionen, Insynshandel", "geschaefte": liste},
                  f, ensure_ascii=False, separators=(",", ":"))
    print("fertig:", len(liste), "Geschaefte,", sum(1 for g in liste if g["art"] == "K"), "Kaeufe,",
          sum(1 for g in liste if g["art"] == "V"), "Verkaeufe", file=sys.stderr)


if __name__ == "__main__":
    main()
