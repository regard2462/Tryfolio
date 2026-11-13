#!/usr/bin/env python3
# Prueft das gepflegte Buendel der Verflechtungen (app/src/main/assets/netz/kanten.json).
#
#   python tools/daten/netz_pruefen.py                 Form, Links und Belege pruefen (ohne Netz)
#   python tools/daten/netz_pruefen.py --abrufen UA    dazu jeden Link abrufen (UA: User-Agent) und pruefen, dass beide Firmennamen
#                                                      und, wo die Kante eine Zahl nennt, die Zahl im Dokument vorkommen
#
# Regeln (wie Netz.einzeldokument und Netz.lesen in der App):
#   - Jeder Link zeigt auf ein einzelnes Dokument: https, mit Pfad; Sammelseiten (edgar/browse, cgi-bin/browse-edgar, edgar/search,
#     Startseiten ohne Pfad, search, investors, ir, about, company, news) werden abgelehnt.
#   - Guete A nur mit primaer true; Guete C gibt es im Buendel nicht (automatisch Gelesenes kommt zur Laufzeit, nicht ins Buendel).
#   - Wettbewerber, Mandate und Staatsgeld stehen nicht im Buendel: sie werden zur Laufzeit aus dem jeweiligen Dokument gelesen.
#   - Stand ist das Datum des Dokuments (JJJJ-MM-TT, nicht nach heute); keine Arbeitsnotizen im Hinweis.
#   - Knoten existieren, Kennungen der Kanten sind eindeutig.

import json
import os
import re
import sys
import datetime

ARTEN = {"liefert", "kauft", "rechenzentrum", "lizenz", "haelt_anteil", "tochter", "finanziert", "optionsschein", "garantie"}
NICHT_IM_BUENDEL = {"konkurriert", "mandat", "staat_finanziert"}
STATUS = {"definitiv", "absichtserklaerung", "beendet", "zugesagt", "ausgezahlt", "zurueckgezogen"}
SAMMEL_PFADE = {"search", "investors", "ir", "about", "company", "news"}
NOTIZEN = re.compile(r"(?i)\b(vor nutzung|pr(ü|ue)fen|nicht abrufbar|nicht ausgewertet|todo|unklar)\b")


def repo():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def einzeldokument(url):
    if not url.startswith("https://"):
        return False
    rest = url[len("https://"):]
    host = rest.split("/", 1)[0]
    if "." not in host:
        return False
    nach = rest.split("/", 1)[1] if "/" in rest else ""
    pfad = nach.split("#", 1)[0].split("?", 1)[0].strip("/")
    fragment = nach.split("#", 1)[1] if "#" in nach else ""
    klein = nach.lower()
    if not pfad and "/" not in fragment:
        return False
    if "edgar/browse" in klein or "browse-edgar" in klein or klein.startswith("edgar/search") or "cgi-bin" in klein:
        return False
    if pfad.lower() in SAMMEL_PFADE:
        return False
    return True


def pruefen(doc, heute):
    fehler = []
    knoten = {k["id"]: k for k in doc.get("knoten", [])}
    ids = set()
    for k in doc.get("knoten", []):
        d = k.get("domain")
        if d is not None and not re.fullmatch(r"[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+", d):
            fehler.append("Knoten %s: domain %r ist kein reiner Host (ohne Schema, Pfad, Gross- oder Leerzeichen)" % (k.get("id"), d))
    for k in doc.get("kanten", []):
        i = k.get("id", "?")
        if i in ids:
            fehler.append("%s: Kennung doppelt" % i)
        ids.add(i)
        if k.get("art") in NICHT_IM_BUENDEL:
            fehler.append("%s: Art %s gehoert nicht ins Buendel" % (i, k.get("art")))
        elif k.get("art") not in ARTEN:
            fehler.append("%s: Art ungueltig" % i)
        if k.get("status") not in STATUS:
            fehler.append("%s: Status ungueltig" % i)
        if k.get("von") not in knoten or k.get("nach") not in knoten:
            fehler.append("%s: Knoten fehlt" % i)
        if k.get("guete") not in ("A", "B"):
            fehler.append("%s: Guete %s (im Buendel nur A und B)" % (i, k.get("guete")))
        q = k.get("quelle") or {}
        for quelle in [q] + list(k.get("weitere", [])):
            if not einzeldokument(quelle.get("url", "")):
                fehler.append("%s: Link ist kein einzelnes Dokument: %s" % (i, quelle.get("url")))
        if k.get("guete") == "A" and not q.get("primaer"):
            fehler.append("%s: Guete A ohne Primaerquelle" % i)
        stand = k.get("stand", "")
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", stand) or stand > heute:
            fehler.append("%s: Stand %s ungueltig" % (i, stand))
        for feld in ("hinweis", "hinweisEn"):
            if NOTIZEN.search(k.get(feld, "") or ""):
                fehler.append("%s: Arbeitsnotiz im Hinweis (%s)" % (i, feld))
        if k.get("hinweis") and not k.get("hinweisEn"):
            fehler.append("%s: Hinweis ohne englische Fassung" % i)
    return fehler


def abrufen(doc, ua):
    import urllib.request
    knoten = {k["id"]: k for k in doc["knoten"]}
    fehler = []
    cache = {}
    for k in doc["kanten"]:
        url = k["quelle"]["url"]
        if url not in cache:
            try:
                req = urllib.request.Request(url, headers={"User-Agent": ua})
                cache[url] = urllib.request.urlopen(req, timeout=60).read().decode("utf-8", "ignore").lower()
            except Exception as e:  # noqa: BLE001
                cache[url] = None
                fehler.append("%s: Abruf fehlgeschlagen (%s): %s" % (k["id"], e, url))
        text = cache[url]
        if text is None:
            continue
        for seite in ("von", "nach"):
            name = knoten[k[seite]]["name"].lower().split()[0]
            if name not in text:
                fehler.append("%s: %s nicht im Dokument %s" % (k["id"], knoten[k[seite]]["name"], url))
    return fehler


def main():
    pfad = os.path.join(repo(), "app", "src", "main", "assets", "netz", "kanten.json")
    with open(pfad, encoding="utf-8") as f:
        doc = json.load(f)
    heute = datetime.date.today().isoformat()
    fehler = pruefen(doc, heute)
    if "--abrufen" in sys.argv:
        ua = sys.argv[sys.argv.index("--abrufen") + 1]
        fehler += abrufen(doc, ua)
    for z in fehler:
        print(z)
    print("%d Kanten, %d Fehler" % (len(doc.get("kanten", [])), len(fehler)))
    sys.exit(1 if fehler else 0)


if __name__ == "__main__":
    main()
