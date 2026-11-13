#!/usr/bin/env python3
# Direkte Emissionen je börsennotierter Firma aus dem EU-Emissionshandel (EU ETS), Ergebnis ist die Datei
# app/src/main/assets/firmen/emissionen-eu.json.
#
#   python tools/daten/emissionen_eu.py [ziel.json] [arbeitsordner]
#
# Quelle ist das Unionsregister der Europaeischen Kommission (climate.ec.europa.eu, ohne Konto, ohne Schluessel):
#   - verified_emissions_JJJJ_en.xlsx: geprueften Emissionen je Anlage, Flugbetreiber und Reederei seit 2008
#   - policy_ets_registry_operators_ets_en.xlsx: Kontoinhaber (Betreiber) je Anlage
# Wiederverwendung nach Beschluss 2011/833/EU und CC BY 4.0 (https://commission.europa.eu/legal-notice_en).
# Die Rohdateien landen im Arbeitsordner (Standard: temporaer) und werden nicht eingecheckt.
#
# Der Betreiber ist nicht der Eigentuemer: Anlagen werden einer Firma zugeordnet, wenn der Name des Betreibers mit dem
# Namen der Firma im Katalog der App beginnt (Toechter wie "RWE Power AG", "Holcim (Deutschland) GmbH"). Eigentumsanteile
# kennt das Register nicht, eine Anlage zaehlt voll. Zuordnung nur bei eindeutigem Treffer. Grossbritannien (UK ETS seit
# 2021) bleibt draussen, damit die Reihe ein einheitliches Gebiet zeigt.
#
# Das Register nennt keinen Standort der Anlage (die Kontaktadresse ist der Sitz des Betreibers), deshalb bleibt der Ort leer;
# der Name der Anlage steht meist mit dem Ort im Register.
#
# Datei: {format, gebiet: "EU", stand, jahr0, jahre, firmen: [{y: [Kuerzel im Katalog], n: Name, e: [t je Jahr ab jahr0],
#         z: [Zahl der Anlagen je Jahr], s: [[Anlage, Land, Ort, t im letzten Jahr, t im Vorjahr, t im Jahr davor], ...]}]}
import datetime
import json
import os
import re
import sys
import tempfile
import time
import unicodedata
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

SEITE = "https://climate.ec.europa.eu/eu-action/eu-emissions-trading-system-eu-ets/union-registry_en"
KENNUNG = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"}
JAHRE = 10
# Unter dieser Jahressumme (Tonnen CO2e) fliegt eine Firma aus der Datei (wie bei den US-Anlagen)
MINDEST = 25000
NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"

# Rechtsformen und Zusaetze am Ende des Katalognamens
FORMEN = {
    "THE", "GROUP", "COMPANY", "COMPANIES", "CO", "CORP", "CORPORATION", "INC", "INCORPORATED", "LLC", "LP", "LTD", "LIMITED",
    "PLC", "HOLDINGS", "HOLDING", "SA", "NV", "AG", "SE", "NA", "AB", "ASA", "OYJ", "SPA", "SAB", "BV", "GMBH", "KG", "KGAA",
    "AKTIENGESELLSCHAFT", "SAS", "SARL", "DAC", "AS", "OY",
}

# Kurze Einzelwort-Marken (bis sechs Buchstaben), deren Betreiber in anderen Laendern unter dem Markennamen firmieren (BASF
# Antwerpen). Alle anderen kurzen Einzelwoerter zaehlen nur im Land der Firma, damit "Orion Engineered Carbons" nicht auf
# Orion (Finnland) und "Roehm GmbH" nicht auf Rohm (Japan) faellt. Laengere Einzelwoerter gelten ueberall.
WELT = {
    "BASF", "SHELL", "HOLCIM", "ARCELORMITTAL", "LINDE", "YARA", "ENEL", "ENGIE", "ENI", "DOW", "REPSOL", "OMV", "BP", "EON",
    "RWE", "NESTE", "UPM", "IBERDROLA", "SIEMENS", "BAYER", "DSM", "LUFTHANSA", "RYANAIR", "EASYJET", "AIRBUS", "BOREALIS",
    "TOTALENERGIES", "TOTAL", "BASELL", "HEIDELBERG", "AIR", "SAINT", "KGHM", "NESTLE", "CRH", "STORA", "BOLIDEN", "OUTOKUMPU", "SSAB", "VOESTALPINE",
    "THYSSENKRUPP", "SALZGITTER", "TATA", "NORSK", "ENDESA", "NATURGY", "ORLEN", "PKN", "ENI", "SNAM", "CEZ", "MOL", "EDF",
    "EDP", "VATTENFALL", "UNIPER", "ORSTED", "VERBUND", "FORTUM", "ELECTRABEL", "LAFARGE", "CEMEX", "BUZZI", "SIKA",
}

# Weitere Namen, unter denen Betreiber in den Registern auftreten (Kuerzel im Katalog -> Namen)
ANDERE = {
    "LHA.DE": ["Deutsche Lufthansa"],
    "TTE.PA": ["Total", "Total Energies"],
    "HOLN.SW": ["Lafarge", "LafargeHolcim"],
    "HEI.DE": ["HeidelbergCement", "Heidelberg Cement"],
    "EOAN.DE": ["E ON", "EON"],
    "ENGI.PA": ["GDF Suez", "Electrabel"],
    "MT.AS": ["Arcelor Mittal", "Arcelor"],
    "AF.PA": ["Air France"],
    "IAG.L": ["British Airways", "Iberia", "Vueling", "Aer Lingus"],
    "BP.L": ["BP"],
    "LYB": ["Basell", "LyondellBasell"],
    "ENI.MI": ["EniPower", "Versalis"],
    "BZU.MI": ["Dyckerhoff"],
    "NHY.OL": ["Hydro Aluminium", "Hydro Aluminum"],
}

# Marken, die zwar im Katalog stehen, deren Namen aber als Betreiber zu allgemein sind (Ergebnis der Stichprobe)
SPERRE = {"NEXT", "SEA", "BLOCK", "SAGE", "ORION", "BALL", "VISA", "FLEX", "ARM", "TOTO", "SHARP", "CANON", "ISS", "FORD", "HERA", "NEL", "HELLA", "MASCO", "ACCOR"}


# Namen, die als Anfang eines Betreibers nichts belegen (Continental Resources, Olympus Energy, Douglas County). Nur der
# volle Name samt Rechtsform zaehlt, nicht der Name mit beliebigem Zusatz.
GENERISCH = {"CONTINENTAL", "OLYMPUS", "DOUGLAS", "LUCID", "COMPASS", "COOPER", "SOUTHERN", "CORNING", "UNITED", "AMERICAN", "NATIONAL", "GENERAL", "FIRST", "PACIFIC", "ATLANTIC", "CENTRAL", "WESTERN", "NORTHERN", "EASTERN", "ENERGY", "STANDARD", "UNIVERSAL", "GLOBAL", "ROYAL", "OLD DOMINION", "BROOKFIELD"}


def holen(url):
    for versuch in range(4):
        try:
            req = urllib.request.Request(url, headers=KENNUNG)
            return urllib.request.urlopen(req, timeout=300).read()
        except Exception:  # noqa: BLE001
            if versuch == 3:
                raise
            time.sleep(5 * (versuch + 1))


def quellen(ordner):
    # Neueste Datei der geprueften Emissionen und die Betreiberliste von der Seite des Unionsregisters
    html = holen(SEITE).decode("utf-8", "replace")
    links = re.findall(r'href="(/document/download/[^"]*filename=([^"]*))"', html)
    ver = sorted(((int(re.search(r"verified_emissions_(\d{4})", f).group(1)), l) for l, f in links if re.search(r"verified_emissions_\d{4}", f)))
    ops = [l for l, f in links if f.startswith("policy_ets_registry_operators")]
    if not ver or not ops:
        raise SystemExit("Dateien auf der Seite des Unionsregisters nicht gefunden")
    os.makedirs(ordner, exist_ok=True)
    aus = {}
    for name, link in (("ver.xlsx", ver[-1][1]), ("ops.xlsx", ops[0])):
        pfad = os.path.join(ordner, name)
        if not os.path.exists(pfad):
            open(pfad, "wb").write(holen("https://climate.ec.europa.eu" + link.replace("&amp;", "&")))
            time.sleep(1)
        aus[name] = pfad
    return ver[-1][0], aus["ver.xlsx"], aus["ops.xlsx"]


def blatt_zeilen(pfad, blatt):
    # Zeilen eines Tabellenblatts (Name des Blatts, Kleinschreibung egal) als {Spalte: Text}; nur Standardbibliothek
    z = zipfile.ZipFile(pfad)
    ss = []
    if "xl/sharedStrings.xml" in z.namelist():
        for si in ET.fromstring(z.read("xl/sharedStrings.xml")):
            ss.append("".join(t.text or "" for t in si.iter(NS + "t")))
    wb = ET.fromstring(z.read("xl/workbook.xml"))
    rel = ET.fromstring(z.read("xl/_rels/workbook.xml.rels"))
    ziele = {r.get("Id"): r.get("Target") for r in rel}
    rid = None
    for s in wb.iter(NS + "sheet"):
        if blatt is None or s.get("name").lower() == blatt:
            rid = s.get("{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id")
            break
    ziel = ziele[rid].lstrip("/")
    with z.open(ziel if ziel.startswith("xl/") else "xl/" + ziel) as f:
        for _, el in ET.iterparse(f):
            if el.tag != NS + "row":
                continue
            r = {}
            for c in el.findall(NS + "c"):
                spalte = re.match(r"[A-Z]+", c.get("r")).group()
                v = c.find(NS + "v")
                t = c.get("t")
                if t == "inlineStr":
                    wert = "".join(x.text or "" for x in c.iter(NS + "t"))
                elif v is None:
                    continue
                elif t == "s":
                    wert = ss[int(v.text)]
                else:
                    wert = v.text
                r[spalte] = wert
            el.clear()
            yield r


def worte(name):
    n = unicodedata.normalize("NFKD", name or "").encode("ascii", "ignore").decode().upper()
    n = re.sub(r"\([^)]*\)", " ", n).replace("&", " AND ")
    n = re.sub(r"(?<![A-Z0-9])([A-Z])\.(?=[A-Z]\b|\s|$)", r"\1", n)
    n = n.replace(".", "").replace("'", "")
    return [x for x in re.split(r"[^A-Z0-9]+", n) if x]


def stamm(name):
    w = worte(name)
    if len(w) > 1 and w[0] == "THE":
        w = w[1:]
    while len(w) > 1 and w[-1] in FORMEN:
        w.pop()
    return w


def katalog(pfad):
    # Stamm (Wortliste) -> {Kuerzel, Name, Land}; gleiche Staemme mehrerer Zeilen (Aktiengattungen) bleiben beisammen
    k = json.load(open(pfad, encoding="utf-8"))
    aus = {}
    for a in k["aktien"]:
        namen = [a["n"]] + ANDERE.get(a["s"], [])
        for i, n in enumerate(namen):
            st = tuple(stamm(n))
            if not st or "".join(st) in SPERRE or len("".join(st)) < (2 if "".join(st) in WELT else 3):
                continue
            w = worte(n)
            if len(w) > 1 and w[0] == "THE":
                w = w[1:]
            # Ein Name, von dem Zusaetze wie "Company" oder "Group" abgetragen wurden, gilt nur als Ganzes
            tol = len(w) == len(st) and " ".join(st) not in GENERISCH
            e = aus.setdefault(st, {"y": [], "n": a["n"], "c": a.get("c", ""), "tol": tol})
            if a["s"] not in e["y"]:
                e["y"].append(a["s"])
    return aus


def praefixe(kat):
    # Alle echten Anfaenge laengerer Katalogstaemme (Siemens vor Siemens Energy)
    tabelle = {}
    for st in kat:
        for m in range(1, len(st)):
            tabelle[st[:m]] = True
    return tabelle


def zuordnen(holder, land, kat, tabelle):
    # Katalogeintrag zum Namen des Betreibers oder None. Das laengste passende Stammwort gewinnt. Ein Stamm, der Anfang eines
    # laengeren Katalogstamms ist (Siemens, Siemens Energy), zaehlt nur bei Namen ohne weiteren Zusatz.
    w = worte(holder)
    if len(w) > 1 and w[0] == "THE":
        w = w[1:]
    best = None
    for n in range(len(w), 0, -1):
        st = tuple(w[:n])
        e = kat.get(st)
        if e is None:
            continue
        rest = w[n:]
        nur_formen = all(x in FORMEN or x in ZUSATZ_LAND for x in rest)
        if not nur_formen and (tabelle.get(st) or not e["tol"]):  # Anfang eines laengeren Katalogstamms
            continue
        if n == 1 and len(st[0]) < 7 and st[0] not in WELT and land != e["c"]:
            continue
        best = e
        break
    return best


# Laender- und Ortszusaetze, die keinen eigenen Unternehmensnamen bilden
ZUSATZ_LAND = {
    "DEUTSCHLAND", "GERMANY", "OSTERREICH", "AUSTRIA", "NEDERLAND", "NETHERLANDS", "BELGIUM", "BELGIE", "BELGIQUE", "FRANCE", "ITALIA",
    "ITALY", "ESPANA", "SPAIN", "POLSKA", "POLAND", "SVERIGE", "SWEDEN", "SUOMI", "FINLAND", "DANMARK", "DENMARK", "NORGE", "NORWAY",
    "IRELAND", "PORTUGAL", "HELLAS", "GREECE", "EUROPE", "EUROPA", "INTERNATIONAL", "CZECH", "SLOVAKIA", "HUNGARY", "ROMANIA",
    "BULGARIA", "CROATIA", "SLOVENIA", "LUXEMBOURG", "SWISS", "SUISSE", "SCHWEIZ", "UK", "USA", "US",
}


def zahl(x):
    try:
        return float(x)
    except (TypeError, ValueError):
        return 0.0


def main():
    ziel = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/firmen/emissionen-eu.json"
    ordner = sys.argv[2] if len(sys.argv) > 2 else os.path.join(tempfile.gettempdir(), "ets_roh")
    letzt, ver_pfad, ops_pfad = quellen(ordner)
    kat = katalog("app/src/main/assets/katalog.json")
    tabelle = praefixe(kat)
    betreiber = {}
    for i, r in enumerate(blatt_zeilen(ops_pfad, None)):
        if i and r.get("D") and r.get("E") and r.get("A"):
            betreiber[(r["D"], r["E"])] = r["A"].strip()
    jahr0 = letzt - JAHRE + 1
    spalten = {}
    firmen = {}
    nicht = {}
    for i, r in enumerate(blatt_zeilen(ver_pfad, "data")):
        if i == 1:
            for sp, name in r.items():
                m = re.fullmatch(r"VERIFIED_EMISSIONS_(\d{4})", name)
                if m and jahr0 <= int(m.group(1)) <= letzt:
                    spalten[int(m.group(1))] = sp
            if len(spalten) != JAHRE:
                raise SystemExit("Spalten der geprueften Emissionen nicht gefunden")
        if i < 2:
            continue
        land = r.get("A", "")
        if land == "GB" or not land:
            continue
        reihe = [zahl(r.get(spalten[j])) for j in range(jahr0, letzt + 1)]
        if max(reihe) <= 0:
            continue
        art = r.get("CG", "")
        halter = betreiber.get((land, r.get("D"))) if art == "OPERATOR HOLDING ACCOUNT" else None
        if halter is None:
            halter = r.get("B", "")
        e = zuordnen(halter, land, kat, tabelle)
        gewicht = sum(reihe)
        if e is None:
            nicht[halter] = nicht.get(halter, 0) + gewicht
            continue
        f = firmen.setdefault(tuple(e["y"]), {"n": e["n"], "e": [0.0] * JAHRE, "z": [0] * JAHRE, "s": [], "halter": {}})
        for j, t in enumerate(reihe):
            if t > 0:
                f["e"][j] += t
                f["z"][j] += 1
        f["halter"][halter] = f["halter"].get(halter, 0) + gewicht
        if max(reihe[-3:]) > 0:
            name = re.sub(r"\s+", " ", (r.get("C") or r.get("B") or "")).strip()
            if art != "OPERATOR HOLDING ACCOUNT" or not name:
                name = halter
            f["s"].append([name[:60], land, "", round(reihe[-1]), round(reihe[-2]), round(reihe[-3])])
    aus = []
    for y, f in firmen.items():
        if max(f["e"]) < MINDEST:
            continue
        f["s"].sort(key=lambda s: max(s[3:]), reverse=True)
        aus.append({"y": list(y), "n": f["n"], "e": [round(x) for x in f["e"]], "z": f["z"], "s": f["s"][:6]})
    aus.sort(key=lambda x: max(x["e"]), reverse=True)
    json.dump({"format": 1, "gebiet": "EU", "stand": datetime.date.today().isoformat(), "jahr0": jahr0, "jahre": JAHRE, "firmen": aus},
              open(ziel, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print(len(aus), "Firmen, letztes Jahr", letzt)
    if os.environ.get("ETS_PRUEFEN"):
        for f in aus[:int(os.environ["ETS_PRUEFEN"])]:
            h = sorted(firmen[tuple(f["y"])]["halter"].items(), key=lambda x: -x[1])[:4]
            print(f["n"], f["y"], f["e"][-1], "|", "; ".join(f"{a} ({b/1e6:.1f})" for a, b in h))
        print("--- groesste Betreiber ohne Treffer")
        for a, b in sorted(nicht.items(), key=lambda x: -x[1])[:int(os.environ.get("ETS_NICHT", "60"))]:
            print(f"{b/1e6:7.1f} Mio t  {a}")


if __name__ == "__main__":
    main()
