# -*- coding: utf-8 -*-
"""Mitgelieferter Stand der BaFin-Daten fuer die App (assets/copytrading/bafin.json).

1. Eigengeschaefte von Fuehrungskraeften nach Art. 19 MAR (Directors' Dealings) aus der Datenbank der BaFin:
   Suchergebnis aller Meldungen der letzten zwoelf Monate, je meldepflichtiger Person die Liste ihrer Meldungen,
   je Meldung die Einzelheiten (Preis und Volumen).
2. Bedeutende Stimmrechtsanteile nach §§ 33, 38, 39 WpHG aus dem Gesamtexport der BaFin.
3. Boersenkuerzel je ISIN ueber die Yahoo-Suche (deutsche Notierung bevorzugt), damit die App den Wert oeffnen kann.

Die Datenbank braucht keine Anmeldung. Abrufe nacheinander mit Pause. Quelle laut Nutzungsbedingungen:
„© Bundesanstalt fuer Finanzdienstleistungsaufsicht / www.bafin.de“.

Aufruf: python holen.py <ziel.json> [--alt <bisherige bafin.json>]
Mit --alt werden Einzelheiten bekannter Meldungen und bekannte Kuerzel uebernommen statt neu abgerufen.
"""
import html
import json
from concurrent.futures import ThreadPoolExecutor
import re
import sys
import time
import urllib.parse
import urllib.request

BASIS = "https://portal.mvp.bafin.de/database/"
UA = {"User-Agent": "Tryfolio/1.0 (open-source practice depot)"}
YAHOO_UA = {"User-Agent": "Mozilla/5.0"}
PAUSE = 0.35
# Hoechstens drei Abrufe gleichzeitig, jeder mit Pause.
PARALLEL = 3


def holen(url, kopf=UA, versuche=4):
    for i in range(versuche):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=kopf), timeout=60) as r:
                daten = r.read()
            time.sleep(PAUSE)
            return daten.decode("utf-8", "replace")
        except Exception as e:  # noqa: BLE001
            print("  Fehler", e, url[:120], file=sys.stderr)
            time.sleep(3 * (i + 1))
    raise RuntimeError("nicht erreichbar: " + url)


def zellen(zeile):
    return [re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", "", z))).strip() for z in re.findall(r"<t[dh][^>]*>(.*?)</t[dh]>", zeile, re.S)]


def datum(s):
    m = re.match(r"(\d{2})\.(\d{2})\.(\d{4})", s.strip())
    return "%s-%s-%s" % (m.group(3), m.group(2), m.group(1)) if m else ""


def zahl(s):
    """'174.549,99 EUR' -> (174549.99, 'EUR')"""
    m = re.match(r"\s*(-?[\d.]+(?:,\d+)?)\s*([A-Z]{3})?", s or "")
    if not m:
        return None, ""
    return float(m.group(1).replace(".", "").replace(",", ".")), (m.group(2) or "")


def personen():
    """Kennungen aus dem Suchergebnis (Gesamtzeitraum = zwoelf Monate). Die BaFin vergibt sie je Meldung."""
    ids = {}
    seite, letzte = 1, 1
    while seite <= letzte:
        url = BASIS + "DealingsInfo/sucheForm.do?" + urllib.parse.urlencode({
            "meldepflichtigerName": "", "zeitraum": "0", "emittentName": "", "zeitraumVon": "", "emittentIsin": "",
            "zeitraumBis": "", "d-5010980-p": str(seite)})
        s = holen(url)
        letzte = max([letzte] + [int(x) for x in re.findall(r"d-5010980-p=(\d+)", s)])
        for pid in re.findall(r"meldepflichtigerId=(\d+)", s):
            ids[pid] = True
        print("Suche Seite", seite, "von", letzte, "Personen", len(ids), file=sys.stderr)
        seite += 1
    return list(ids)


def meldungen_der_person(pid):
    s = holen(BASIS + "DealingsInfo/ergebnisListe.do?cmd=loadEmittentenAction&meldepflichtigerId=" + pid)
    aus = []
    for zeile in re.findall(r"<tr class=\"(?:odd|even)\">.*?</tr>", s, re.S):
        link = re.search(r"transaktionListe\.do\?cmd=loadTransaktionenAction&amp;emittentBafinId=(\d+)&amp;meldungId=(\d+)", zeile)
        z = zellen(zeile)
        if not link or len(z) < 10:
            continue
        aus.append({
            "id": link.group(2), "pid": pid, "bafin": link.group(1), "e": z[0], "isin": z[2], "p": z[3], "r": z[4], "i": z[5],
            "art": z[6], "d": datum(z[7]), "o": z[8], "v": datum(z[9]),
        })
    return aus


def einzelheiten(bafin, mid):
    """Preis und Volumen aus den aggregierten Angaben; bei mehreren Geschaeften Volumen addiert."""
    s = holen(BASIS + "DealingsInfo/transaktionListe.do?cmd=loadTransaktionenAction&emittentBafinId=%s&meldungId=%s" % (bafin, mid))
    preise = re.findall(r"<td>Preis:</td>\s*<td>([^<]*)</td>", s)
    volumen = re.findall(r"<td>Aggregiertes Volumen:</td>\s*<td>([^<]*)</td>", s)
    summe, waehrung, preis = 0.0, "", None
    for v in volumen:
        x, w = zahl(v)
        if x is not None:
            summe += x
            waehrung = waehrung or w
    for p in preise:
        x, w = zahl(p)
        if x is not None:
            preis = x
            waehrung = waehrung or w
            break
    return preis, (summe if volumen else None), waehrung


def stimmrechte():
    s = holen(BASIS + "AnteileInfo/aktiengesellschaft.do?d-3611442-e=1&cmd=zeigeGesamtExport&6578706f7274=1").lstrip("﻿")
    aus = []
    for zeile in s.splitlines():
        f = zeile.split(";")
        if len(f) < 10 or not f[0].strip():
            continue

        def p(x):
            return zahl(x)[0] if x.strip() else None

        aus.append({"e": f[0].strip(), "es": f[1].strip(), "m": f[3].strip(), "ms": f[4].strip(), "ml": f[5].strip(),
                    "s": p(f[6]), "inst": p(f[7]), "summe": p(f[8]), "d": datum(f[9])})
    return aus


def kuerzel(isin):
    url = "https://query2.finance.yahoo.com/v1/finance/search?" + urllib.parse.urlencode({"q": isin, "quotesCount": "6", "newsCount": "0"})
    try:
        d = json.loads(holen(url, YAHOO_UA, versuche=2))
    except Exception:  # noqa: BLE001
        return None
    treffer = [q.get("symbol", "") for q in d.get("quotes", []) if q.get("symbol")]
    for endung in (".DE", ".F"):
        for t in treffer:
            if t.endswith(endung):
                return t
    return treffer[0] if treffer else None


def main():
    ziel = sys.argv[1]
    alt = {}
    if "--alt" in sys.argv:
        with open(sys.argv[sys.argv.index("--alt") + 1], encoding="utf-8") as f:
            alt = json.load(f)
    bekannt = {g["id"]: g for g in alt.get("eigengeschaefte", [])}
    symbole = {g["isin"]: g.get("k") for g in alt.get("eigengeschaefte", []) if g.get("k")}

    geschaefte = {}
    pids = personen()
    with ThreadPoolExecutor(PARALLEL) as pool:
        for n, liste in enumerate(pool.map(meldungen_der_person, pids), 1):
            for g in liste:
                geschaefte[g["id"]] = g
            if n % 100 == 0:
                print("Kennungen", n, "von", len(pids), "Meldungen", len(geschaefte), file=sys.stderr)
    offen = []
    for g in geschaefte.values():
        b = bekannt.get(g["id"])
        if b and "vol" in b:
            g["preis"], g["vol"], g["w"] = b.get("preis"), b.get("vol"), b.get("w", "")
        else:
            offen.append(g)
    with ThreadPoolExecutor(PARALLEL) as pool:
        for n, (g, e) in enumerate(zip(offen, pool.map(lambda x: einzelheiten(x["bafin"], x["id"]), offen)), 1):
            g["preis"], g["vol"], g["w"] = e
            if n % 200 == 0:
                print("Einzelheiten", n, "von", len(offen), file=sys.stderr)
    isins = sorted({g["isin"] for g in geschaefte.values() if re.fullmatch(r"[A-Z]{2}[A-Z0-9]{9}\d", g["isin"])})
    for isin in isins:
        if isin not in symbole:
            symbole[isin] = kuerzel(isin)
    for g in geschaefte.values():
        k = symbole.get(g["isin"])
        if k:
            g["k"] = k
        g.pop("bafin", None)
    liste = sorted(geschaefte.values(), key=lambda g: (g["d"], g["v"]), reverse=True)
    daten = {
        "stand": time.strftime("%Y-%m-%d"),
        "quelle": "© Bundesanstalt für Finanzdienstleistungsaufsicht / www.bafin.de",
        "eigengeschaefte": liste,
        "stimmrechte": stimmrechte(),
    }
    with open(ziel, "w", encoding="utf-8", newline="\n") as f:
        json.dump(daten, f, ensure_ascii=False, separators=(",", ":"))
    print("fertig:", len(liste), "Eigengeschaefte,", len(daten["stimmrechte"]), "Stimmrechtsanteile,",
          sum(1 for g in liste if g.get("k")), "mit Kuerzel", file=sys.stderr)


if __name__ == "__main__":
    main()
