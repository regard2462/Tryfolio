# -*- coding: utf-8 -*-
"""Lobbyregister ausserhalb der USA je Aktie des Katalogs (assets/behoerden/lobby_welt.json, seit 5.9).

Quellen:
- EU: Transparenzregister der EU (Gesamtexport als XML, Wiederverwendung nach dem Beschluss 2011/833/EU mit
  Quellenangabe): Kosten der Interessenvertretung als Spanne im letzten abgeschlossenen Geschaeftsjahr,
  Vollzeitstellen, Themen.
- DE: Lobbyregister des Deutschen Bundestages (JSON der Suche und der Registereintraege, gesetzlich maschinenlesbar):
  finanzielle Aufwendungen als Spanne, Vollzeitstellen, Interessenbereiche.
- FR: Repertoire der Interessenvertreter der HATVP (Open Data, Licence Ouverte): Ausgaben als Spanne, Beschaeftigte,
  Zahl der gemeldeten Taetigkeiten im letzten Geschaeftsjahr.
Zugeordnet wird ueber den Namensstamm ohne Rechtsform (tools/washington/namen.py), nur Unternehmen und nur bei
gleichem Stamm.

Aufruf: python welt.py <katalog.json> <eu.xml> <hatvp.json> <ziel.json> [--ohne-de]
"""
import datetime as dt
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "washington"))
from namen import stamm  # noqa: E402

UA = {"User-Agent": "Tryfolio/5.9 (open-source practice depot)", "Accept": "application/json"}
DE_SUCHE = "https://www.lobbyregister.bundestag.de/sucheJson?q="
DE_SEITE = "https://www.lobbyregister.bundestag.de/suche/"
EU_SEITE = "https://transparency-register.europa.eu/search-details_en?id="
FR_SEITE = "https://www.hatvp.fr/fiche-organisation/?organisation="


def katalog(pfad):
    k = json.load(open(pfad, encoding="utf-8"))
    aus = {}
    for a in k["aktien"]:
        s = stamm(a["n"])
        if len(s) >= 3:
            aus.setdefault(s, []).append(a["s"])
    return aus


def eu(pfad, staemme):
    """{Stamm: Eintrag} aus dem Gesamtexport, nur „Companies & groups“."""
    aus = {}
    # Der Export ist XML 1.1 mit Steuerzeichen als Zeichenreferenz (&#x1;), die der Parser (XML 1.0) ablehnt
    roh = open(pfad, "rb").read()
    roh = re.sub(rb"&#(x0*[0-8bBcCeEfF]|x0*1[0-9a-fA-F]|0*[0-8]|0*1[1-24-9]|0*2[0-9]|0*3[01]);", b" ", roh)
    roh = roh.replace(b"<?xml version='1.1'", b"<?xml version='1.0'", 1)
    import io
    for _, el in ET.iterparse(io.BytesIO(roh), events=("end",)):
        if el.tag.split("}")[-1] != "interestRepresentative":
            continue
        name = (el.findtext("name/originalName") or "").strip()
        s = stamm(name)
        if s in staemme and (el.findtext("registrationCategory") or "").startswith("Companies"):
            jahr = el.find("financialData/closedYear")
            von = bis = None
            if jahr is not None:
                von = jahr.findtext("costs/range/min")
                bis = jahr.findtext("costs/range/max")
                if von is None:
                    von = bis = jahr.findtext("costs/absoluteCost")
            fte = el.findtext("members/membersFTE")
            themen = [i.findtext("name") for i in el.findall("interests/interest")]
            e = {"l": "EU", "n": name, "w": "EUR",
                 "von": int(float(von)) if von else None, "bis": int(float(bis)) if bis else None,
                 "j": ((jahr.findtext("startDate") or "")[:10] + "/" + (jahr.findtext("endDate") or "")[:10]) if jahr is not None else "",
                 "fte": round(float(fte), 1) if fte else None, "themen": [t for t in themen if t][:8],
                 "q": EU_SEITE + (el.findtext("identificationCode") or "")}
            alt = aus.get(s)
            # Bei mehreren Eintraegen mit gleichem Stamm zaehlt der mit den hoechsten Kosten
            if alt is None or (e["bis"] or 0) > (alt["bis"] or 0):
                aus[s] = e
        el.clear()
    return aus


def spanne_fr(text):
    """„> = 10 000 euros et < 25 000 euros“ als (von, bis)."""
    z = [int(x.replace(" ", "").replace(" ", "")) for x in re.findall(r"\d[\d  ]*\d|\d", text or "")]
    if not z:
        return None, None
    return (z[0], z[1]) if len(z) > 1 else (z[0], None)


def fr(pfad, staemme):
    d = json.load(open(pfad, encoding="utf-8"))
    aus = {}
    for p in d["publications"]:
        if (p.get("categorieOrganisation") or {}).get("categorie") != "ENTREPRISE":
            continue
        name = (p.get("denomination") or "").strip()
        s = stamm(name)
        if s not in staemme:
            continue
        ex = [e.get("publicationCourante") or {} for e in p.get("exercices") or []]
        ex = [e for e in ex if e.get("dateFin")]
        if not ex:
            continue
        letzte = max(ex, key=lambda e: dt.datetime.strptime(e["dateFin"], "%d-%m-%Y"))
        von, bis = spanne_fr(letzte.get("montantDepense"))
        e = {"l": "FR", "n": name.title(), "w": "EUR", "von": von, "bis": bis,
             "j": dt.datetime.strptime(letzte["dateDebut"], "%d-%m-%Y").date().isoformat() + "/" + dt.datetime.strptime(letzte["dateFin"], "%d-%m-%Y").date().isoformat(),
             "fte": letzte.get("nombreSalaries"), "taetigkeiten": letzte.get("nombreActivite"),
             "themen": [x.get("label") for x in (p.get("activites") or {}).get("listSecteursActivites") or []][:8],
             "q": FR_SEITE + str(p.get("identifiantNational") or "")}
        alt = aus.get(s)
        if alt is None or (e["bis"] or e["von"] or 0) > (alt["bis"] or alt["von"] or 0):
            aus[s] = e
    return aus


def holen(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120) as r:
        return json.loads(r.read().decode("utf-8"))


def de(namen_je_stamm, pause=1.0):
    """Je Katalogname eine Suche; bei gleichem Stamm den Registereintrag mit Aufwendungen holen."""
    aus = {}
    for i, (s, name) in enumerate(namen_je_stamm.items()):
        try:
            r = holen(DE_SUCHE + urllib.parse.quote(name))
        except Exception as ex:  # noqa: BLE001
            print("DE Suche", name, ex, file=sys.stderr)
            time.sleep(pause * 3)
            continue
        time.sleep(pause)
        for t in r.get("results", []):
            ident = t.get("lobbyistIdentity") or {}
            if ident.get("identity") != "ORGANIZATION" or stamm(ident.get("name", "")) != s:
                continue
            nr = t.get("registerNumber")
            eid = (t.get("registerEntryDetails") or {}).get("registerEntryId")
            try:
                dd = holen(f"https://www.lobbyregister.bundestag.de/sucheJson/{nr}/{eid}")
            except Exception as ex:  # noqa: BLE001
                print("DE Eintrag", nr, ex, file=sys.stderr)
                continue
            time.sleep(pause)
            fe = (dd.get("financialExpenses") or {})
            euro = fe.get("financialExpensesEuro") or {}
            emp = dd.get("employeesInvolvedInLobbying") or {}
            themen = [x.get("de") for x in (dd.get("activitiesAndInterests") or {}).get("fieldsOfInterest") or []]
            e = {"l": "DE", "n": ident.get("name"), "w": "EUR", "von": euro.get("from"), "bis": euro.get("to"),
                 "j": (fe.get("relatedFiscalYearStart") or "") + "/" + (fe.get("relatedFiscalYearEnd") or ""),
                 "fte": emp.get("employeeFTE"), "themen": [x for x in themen if x][:8], "q": DE_SEITE + f"{nr}/{eid}"}
            alt = aus.get(s)
            if alt is None or (e["bis"] or 0) > (alt["bis"] or 0):
                aus[s] = e
        if i % 100 == 0:
            print("DE", i, "von", len(namen_je_stamm), "Treffer", len(aus), file=sys.stderr)
    return aus


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    kat, eu_pfad, fr_pfad, ziel = args[:4]
    staemme = katalog(kat)
    k = json.load(open(kat, encoding="utf-8"))
    namen_je_stamm = {}
    for a in k["aktien"]:
        s = stamm(a["n"])
        if len(s) >= 3:
            namen_je_stamm.setdefault(s, a["n"])
    quellen = [eu(eu_pfad, staemme), fr(fr_pfad, staemme)]
    if "--ohne-de" not in sys.argv:
        quellen.append(de(namen_je_stamm))
    firmen = {}
    for q in quellen:
        for s, e in q.items():
            e = {k2: v for k2, v in e.items() if v not in (None, "", [])}
            for sym in staemme[s]:
                firmen.setdefault(sym, []).append(e)
    datei = {"format": 1, "stand": dt.date.today().isoformat(), "firmen": dict(sorted(firmen.items()))}
    json.dump(datei, open(ziel, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print("fertig:", len(firmen), "Aktien;", {l: sum(1 for v in firmen.values() for e in v if e["l"] == l) for l in ("EU", "DE", "FR")}, file=sys.stderr)


if __name__ == "__main__":
    main()
