#!/usr/bin/env python3
# Baut die verdichtete Datei fuer den Abschnitt "Schuldentragfaehigkeit" des Laenderblatts:
#
#   app/src/main/assets/globus/schulden.json   je Land der juengste Wert von fuenf Groessen der Staatsfinanzen
#
# Quelle und Bedingungen:
#   - Weltbank, World Development Indicators (https://data.worldbank.org/), CC BY 4.0. Die Lizenz je Reihe steht in den
#     Metadaten der Schnittstelle (License_Type) und wird beim Holen mitgeladen und beim Bauen geprueft: Eine Reihe mit
#     anderer Lizenz als "CC BY-4.0" bricht den Bau ab. Liefert die Schnittstelle fuer eine Reihe keine Metadaten (leere
#     Antwort), meldet der Bau sie als ungeprueft; dann vor einer Veroeffentlichung erneut holen.
#       GC.REV.XGRT.GD.ZS   Einnahmen des Zentralstaats ohne Zuschuesse in % des BIP
#       GC.XPN.INTP.RV.ZS   Zinsausgaben in % der Einnahmen
#       NY.GDP.MKTP.CN      BIP in Landeswaehrung zu laufenden Preisen (daraus das nominale Wachstum zum Vorjahr)
#       NY.GDP.MKTP.CD      BIP in US-Dollar zu laufenden Preisen
#       FI.RES.TOTL.CD      Waehrungsreserven mit Gold in US-Dollar (daraus die Reserven in % des BIP)
#       DT.DOD.DSTC.IR.ZS   kurzfristige Auslandsschulden in % der Reserven
#
# Aufruf:
#   python tools/daten/schulden.py holen <Ordner>                      laedt die Tabellen (Browserkennung, kein Konto)
#   python tools/daten/schulden.py bauen <Ordner> --stand=JJJJ-MM-TT   liest die Tabellen aus dem Ordner
# Danach: python tools/daten/manifest.py bauen   (nicht signieren)

import json
import os
import sys
import time
import urllib.request

KENNUNG = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
WB = "https://api.worldbank.org/v2/country/all/indicator/%s?format=json&per_page=20000&date=%d:%d"
WB_LAENDER = "https://api.worldbank.org/v2/country?format=json&per_page=400"
WB_META = "https://api.worldbank.org/v2/sources/2/series/%s/metadata?format=json"

BIS = 2026
# So viele Jahre zurueck darf der juengste Wert eines Landes liegen
MAX_ALTER = 6
EINNAHMEN = "GC.REV.XGRT.GD.ZS"
ZINSEN = "GC.XPN.INTP.RV.ZS"
BIP_LW = "NY.GDP.MKTP.CN"
BIP_USD = "NY.GDP.MKTP.CD"
RESERVEN = "FI.RES.TOTL.CD"
KURZFRISTIG = "DT.DOD.DSTC.IR.ZS"
REIHEN = [EINNAHMEN, ZINSEN, BIP_LW, BIP_USD, RESERVEN, KURZFRISTIG]
LIZENZ = "CC BY-4.0"
# Kuerzel der Weltbank -> Kuerzel der Karte
UMBENANNT = {"XKX": "KOS"}


def repo():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def holen_datei(url, ziel):
    # Schon geladene Tabellen bleiben (ein zweiter Lauf holt nur, was fehlt)
    if os.path.exists(ziel) and os.path.getsize(ziel) > 0:
        return
    time.sleep(3)
    r = urllib.request.Request(url, headers={"User-Agent": KENNUNG})
    with urllib.request.urlopen(r, timeout=180) as a:
        b = a.read()
    with open(ziel, "wb") as f:
        f.write(b)
    print("%s: %d Bytes" % (os.path.basename(ziel), len(b)))


def lesen_json(pfad):
    with open(pfad, "rb") as f:
        return json.loads(f.read().decode("utf-8-sig"))


def rund(x, stellen=4):
    v = float("%.*g" % (stellen, x))
    return int(v) if v == int(v) and abs(v) < 1e15 else v


def laender(ordner):
    # Nur Laender und Gebiete, keine Regionen und Einkommensgruppen
    aus = set()
    for e in lesen_json(os.path.join(ordner, "wb-laender.json"))[1]:
        if e["region"]["id"] != "NA" and len(e["id"]) == 3:
            aus.add(e["id"])
    return aus


def wb_reihe(ordner, kennung, gueltig):
    # {Land: {Jahr: Wert}}
    aus = {}
    for e in lesen_json(os.path.join(ordner, "wb-%s.json" % kennung))[1] or []:
        code = e.get("countryiso3code") or ""
        if code not in gueltig or e.get("value") is None:
            continue
        aus.setdefault(UMBENANNT.get(code, code), {})[int(e["date"][:4])] = float(e["value"])
    return aus


def lizenz(ordner, kennung):
    # License_Type aus den Metadaten der Reihe
    pfad = os.path.join(ordner, "wb-meta-%s.json" % kennung)
    if not os.path.exists(pfad) or os.path.getsize(pfad) == 0:
        return ""
    m = lesen_json(pfad)
    for q in m.get("source", []):
        for c in q.get("concept", []):
            for v in c.get("variable", []):
                for feld in v.get("metatype", []):
                    if feld.get("id") == "License_Type":
                        return (feld.get("value") or "").strip()
    return ""


def juengster(jahre, ab):
    # [Jahr, Wert] des juengsten Jahres ab der Altersgrenze
    j = max((x for x in jahre if x >= ab), default=None)
    return None if j is None else [j, rund(jahre[j])]


def bauen(ordner, stand):
    for kennung in REIHEN:
        l = lizenz(ordner, kennung)
        if l and l != LIZENZ:
            raise SystemExit("%s: Lizenz '%s' statt %s" % (kennung, l, LIZENZ))
        print("%s: Lizenz %s" % (kennung, l or "ungeprueft (keine Metadaten)"))
    gueltig = laender(ordner)
    je = {k: wb_reihe(ordner, k, gueltig) for k in REIHEN}
    ab = int(stand[:4]) - MAX_ALTER
    aus = {}
    for land in sorted(set().union(*[set(r) for r in je.values()])):
        o = {}
        for feld, kennung in (("e", EINNAHMEN), ("z", ZINSEN), ("k", KURZFRISTIG)):
            w = juengster(je[kennung].get(land, {}), ab)
            if w is not None and w[1] >= 0:
                o[feld] = w
        # Nominales Wachstum: im Jahr der Zinsausgaben (beide werden gegeneinander gestellt), sonst das juengste Jahr,
        # dessen Vorjahr ebenfalls einen Wert hat
        lw = je[BIP_LW].get(land, {})
        jahre = sorted((x for x in lw if x >= ab), reverse=True)
        if "z" in o:
            jahre = [o["z"][0]] + jahre
        for j in jahre:
            if lw.get(j - 1, 0) > 0 and lw.get(j, 0) > 0:
                o["w"] = [j, rund((lw[j] / lw[j - 1] - 1) * 100)]
                break
        # Reserven in % des BIP: juengstes Jahr mit beiden Werten
        res = je[RESERVEN].get(land, {})
        usd = je[BIP_USD].get(land, {})
        for j in sorted((x for x in res if x >= ab), reverse=True):
            if usd.get(j, 0) > 0 and res[j] >= 0:
                o["r"] = [j, rund(res[j] / usd[j] * 100)]
                break
        if o:
            aus[land] = o
    return {
        "stand": stand, "schema": 1,
        "quelle": "World Bank, World Development Indicators (https://data.worldbank.org/), CC BY 4.0, abgerufen am " + stand,
        "einheit": "je Feld [Jahr, Wert]. e: Einnahmen des Zentralstaats ohne Zuschuesse in % des BIP; z: Zinsausgaben in % "
                   "der Einnahmen; w: nominales Wachstum des BIP zum Vorjahr in % (im Jahr von z, sonst das juengste); "
                   "r: Waehrungsreserven mit Gold in % des BIP; k: kurzfristige Auslandsschulden in % der Reserven",
        "l": aus,
    }


def schreiben(datei, name):
    ziel = os.path.join(repo(), "app", "src", "main", "assets", "globus", name)
    text = json.dumps(datei, ensure_ascii=False, separators=(",", ":")) + chr(10)
    with open(ziel, "w", encoding="utf-8", newline=chr(10)) as f:
        f.write(text)
    print("%s: %d Bytes" % (name, len(text.encode("utf-8"))))


def hauptteil():
    if len(sys.argv) < 3 or sys.argv[1] not in ("holen", "bauen"):
        raise SystemExit(__doc__)
    ordner = sys.argv[2]
    stand = next((a[len("--stand="):] for a in sys.argv[3:] if a.startswith("--stand=")), None)
    if sys.argv[1] == "holen":
        os.makedirs(ordner, exist_ok=True)
        holen_datei(WB_LAENDER, os.path.join(ordner, "wb-laender.json"))
        for kennung in REIHEN:
            holen_datei(WB % (kennung, BIS - MAX_ALTER - 2, BIS), os.path.join(ordner, "wb-%s.json" % kennung))
            holen_datei(WB_META % kennung, os.path.join(ordner, "wb-meta-%s.json" % kennung))
        return
    if not stand:
        raise SystemExit("--stand=JJJJ-MM-TT angeben (Tag des Abrufs)")
    schreiben(bauen(ordner, stand), "schulden.json")


if __name__ == "__main__":
    hauptteil()
