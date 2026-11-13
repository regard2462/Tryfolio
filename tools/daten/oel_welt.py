#!/usr/bin/env python3
# Baut daten/welt/oel.json (und die Kopie in app/src/main/assets/welt): Oelfoerderung, Rohoelfoerderung der OPEC+,
# Verbrauch und Lager weltweit je Monat, mit Laendern je Region.
#
# Quelle (gemeinfrei, U.S. government work): U.S. Energy Information Administration, Short-Term Energy Outlook,
# Monatsdatei https://www.eia.gov/outlooks/steo/xls/STEO_m.xlsx (rund 1 MB, erscheint monatlich), Tabellen 3a bis 3e.
# Die Datei traegt Monatswerte ab Januar des vierten Vorjahres und die Prognose der EIA bis Ende des Folgejahres; das Blatt
# "Dates" nennt den letzten Monat mit Bestandsdaten. Die juengsten Monate der Laenderwerte sind Schaetzungen der EIA.
#
# Aufruf:
#   python tools/daten/oel_welt.py bauen --steo=<STEO_m.xlsx>
#   python tools/daten/oel_welt.py zeigen --steo=<STEO_m.xlsx>     Uebersicht ausgeben, nichts schreiben
#
# Regeln: Foerderung (Rohoel, Kondensat, Fluessiggase, Biokraftstoffe, Raffineriegewinne) in Millionen Barrel am Tag je Land
# aus 3b (Laender ausserhalb der OPEC) und 3c (OPEC und OPEC+). Die Regionen sind die der EIA; die OPEC-Laender kommen zu
# ihrer Region dazu, damit jede Region alle ihre Laender traegt. Summe der Region = Summe der EIA ohne OPEC plus ihre
# OPEC-Laender; die Summe aller Regionen ist die Weltfoerderung (Pruefung unten). Rohoel der OPEC+ je Land aus 3d,
# Verbrauch aus 3e, Lager der OECD aus 3a. OPEC+-Quoten liefert die EIA nicht; QUOTEN bleibt leer, bis die Zahlen einer
# Vereinbarung von Hand eingetragen werden (Tausend Barrel am Tag je Land, mit Monat der Gueltigkeit).
# Danach: python tools/daten/manifest.py bauen --signieren

import json
import os
import re
import sys
import zipfile
import xml.etree.ElementTree as ET

M = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
R = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}"

QUELLE = "U.S. Energy Information Administration, Short-Term Energy Outlook"

# Kennung der EIA (Endung der Reihe) -> ISO-Code, deutscher Kurzname, englischer Name
LAENDER = {
    "us": ("US", "USA", "United States"), "ca": ("CA", "Kanada", "Canada"), "mx": ("MX", "Mexiko", "Mexico"),
    "ar": ("AR", "Argentinien", "Argentina"), "br": ("BR", "Brasilien", "Brazil"), "co": ("CO", "Kolumbien", "Colombia"),
    "gy": ("GY", "Guyana", "Guyana"), "ve": ("VE", "Venezuela", "Venezuela"),
    "no": ("NO", "Norwegen", "Norway"), "uk": ("GB", "Großbritannien", "United Kingdom"),
    "aj": ("AZ", "Aserbaidschan", "Azerbaijan"), "kz": ("KZ", "Kasachstan", "Kazakhstan"), "rs": ("RU", "Russland", "Russia"),
    "mu": ("OM", "Oman", "Oman"), "qa": ("QA", "Katar", "Qatar"), "tc": ("AE", "VAE", "UAE"),
    "ir": ("IR", "Iran", "Iran"), "iz": ("IQ", "Irak", "Iraq"), "ku": ("KW", "Kuwait", "Kuwait"),
    "sa": ("SA", "Saudi-Arabien", "Saudi Arabia"), "ba": ("BH", "Bahrain", "Bahrain"),
    "ao": ("AO", "Angola", "Angola"), "eg": ("EG", "Ägypten", "Egypt"), "ag": ("DZ", "Algerien", "Algeria"),
    "cf": ("CG", "Rep. Kongo", "Rep. Congo"), "ek": ("GQ", "Äquatorialguinea", "Equatorial Guinea"),
    "gb": ("GA", "Gabun", "Gabon"), "ly": ("LY", "Libyen", "Libya"), "ni": ("NG", "Nigeria", "Nigeria"),
    "od": ("SS", "Südsudan", "South Sudan"), "su": ("SD", "Sudan", "Sudan"),
    "ch": ("CN", "China", "China"), "in": ("IN", "Indien", "India"), "id": ("ID", "Indonesien", "Indonesia"),
    "my": ("MY", "Malaysia", "Malaysia"), "bx": ("BN", "Brunei", "Brunei"), "ja": ("JP", "Japan", "Japan"),
}

# Regionen der EIA (Tabellen 3b und 3e): Kennung der Summe, Schluessel der App
REGIONEN = [
    ("r01", "NORDAMERIKA"), ("r02", "SUEDAMERIKA"), ("r03", "EUROPA"), ("r04", "EURASIEN"),
    ("r05", "NAHOST"), ("r06", "AFRIKA"), ("r07", "ASIEN"),
]

# Region der Laender aus Tabelle 3c, die 3b nicht je Region auffuehrt (OPEC-Mitglieder und weitere Teilnehmer der OPEC+)
REGION_ZUSATZ = {
    "ve": "SUEDAMERIKA", "ir": "NAHOST", "iz": "NAHOST", "ku": "NAHOST", "sa": "NAHOST", "ba": "NAHOST",
    "ag": "AFRIKA", "cf": "AFRIKA", "ek": "AFRIKA", "gb": "AFRIKA", "ly": "AFRIKA", "ni": "AFRIKA", "od": "AFRIKA", "su": "AFRIKA",
    "bx": "ASIEN",
}

# OPEC+-Vorgaben in Tausend Barrel am Tag: {"monat": "JJJJ-MM", "laender": {"SA": 10478, ...}}. Leer: Die EIA liefert sie
# nicht, die Zahlen stehen in den Mitteilungen der OPEC und werden nur von Hand aus der Mitteilung selbst uebernommen.
QUOTEN = {}


def repo():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


# --------------------------------------------------------------------------------------------------------------- xlsx
def spalte(ref):
    n = 0
    for c in re.match(r"[A-Z]+", ref).group(0):
        n = n * 26 + ord(c) - 64
    return n


def blaetter(pfad):
    # {Blattname: {Zeile: {Spalte (1 = A): Text}}}
    z = zipfile.ZipFile(pfad)
    texte = []
    if "xl/sharedStrings.xml" in z.namelist():
        for si in ET.fromstring(z.read("xl/sharedStrings.xml")).findall(M + "si"):
            texte.append("".join(t.text or "" for t in si.iter(M + "t")))
    ziel = {r.get("Id"): r.get("Target") for r in ET.fromstring(z.read("xl/_rels/workbook.xml.rels"))}
    out = {}
    for s in ET.fromstring(z.read("xl/workbook.xml")).find(M + "sheets"):
        t = ziel[s.get(R + "id")].lstrip("/")
        if not t.startswith("xl/"):
            t = "xl/" + t
        zeilen = {}
        for row in ET.fromstring(z.read(t)).iter(M + "row"):
            d = {}
            for c in row.findall(M + "c"):
                v = c.find(M + "v")
                if v is None or v.text is None:
                    continue
                d[spalte(c.get("r"))] = texte[int(v.text)] if c.get("t") == "s" else v.text
            zeilen[int(row.get("r"))] = d
        out[s.get("name")] = zeilen
    return out


def monat_text(m):
    return "%04d-%02d" % (m // 12, m % 12 + 1)


def lesen(pfad):
    b = blaetter(pfad)
    dates = b["Dates"]
    erster = None
    letzter_ist = None
    stand = None
    for z in dates.values():
        a = (z.get(1) or "").lower()
        if a.startswith("table beginning month"):
            erster = int(z[4])
        if a.startswith("last historical month"):
            letzter_ist = int(z[4])
        if a.startswith("modeling and analysis completion"):
            # Excel-Tageszahl
            import datetime
            stand = (datetime.date(1899, 12, 30) + datetime.timedelta(days=int(float(z[4])))).isoformat()
    if erster is None or letzter_ist is None or stand is None:
        raise SystemExit("Blatt Dates: Beginn, letzter Bestandsmonat oder Stand fehlt")
    ab = (erster // 100) * 12 + erster % 100 - 1
    ist = (letzter_ist // 100) * 12 + letzter_ist % 100 - 1
    # Reihen je Tabelle: Kennung (klein) -> Werte ab Spalte C ("-" ohne Wert)
    tab = {}
    for name in ("3atab", "3btab", "3ctab", "3dtab", "3etab"):
        reihen = {}
        for nr in sorted(b[name]):
            z = b[name][nr]
            kennung = (z.get(1) or "").strip()
            if not kennung or " " in kennung or 3 not in z:
                continue
            werte = []
            sp = 3
            while sp in z:
                try:
                    werte.append(float(z[sp]))
                except ValueError:
                    werte.append(None)
                sp += 1
            reihen.setdefault(kennung.lower(), werte)
        tab[name] = reihen
    return ab, ist, stand, tab


def rund(l, stellen=3):
    return [None if v is None else round(v, stellen) for v in l]


def plus(a, b):
    return [None if x is None or y is None else x + y for x, y in zip(a, b)]


def land(k, werte, opecplus, opec):
    a2, de, en = LAENDER[k]
    d = {"a2": a2, "de": de, "en": en, "w": rund(werte)}
    if opec:
        d["opec"] = True
    if opecplus:
        d["opecplus"] = True
    return d


def bauen(pfad):
    ab, ist, stand, tab = lesen(pfad)
    # Nur Bestand und Schaetzung bis zum letzten Bestandsmonat: Die Prognose nennt die OPEC-Laender nicht einzeln. Die Prognose
    # der Welt (Foerderung, Verbrauch) steht getrennt unter "prognose".
    n = ist - ab + 1
    voll = {name: dict(tab[name]) for name in tab}
    for name in tab:
        tab[name] = {k: w[:n] for k, w in tab[name].items()}
    t3a, t3b, t3c, t3d, t3e = (tab[n2] for n2 in ("3atab", "3btab", "3ctab", "3dtab", "3etab"))

    # Mitglieder: OPEC (Tabelle 3c, erster Block) und OPEC+ (zweiter Block ohne Iran, Libyen, Venezuela)
    opec = ["ag", "cf", "ek", "gb", "ir", "iz", "ku", "ly", "ni", "sa", "ve"]
    weitere = ["aj", "ba", "bx", "kz", "my", "mx", "mu", "rs", "od", "su"]
    ohne_vorgabe = {"ir", "ly", "ve"}
    for k in opec + weitere:
        if "papr_" + k not in t3c:
            raise SystemExit("Tabelle 3c: Land fehlt: " + k)
    opecplus = set(k for k in opec if k not in ohne_vorgabe) | set(weitere)

    # Foerderung je Region
    gruppen = []
    summe_welt = [0.0] * n
    for rk, rid in REGIONEN:
        summe = list(t3b["t3b_papr_" + rk])
        laender = {}
        # Laender der Region in 3b: die Zeilen zwischen dieser und der naechsten Regionssumme
        namen = list(t3b.keys())
        i = namen.index("t3b_papr_" + rk) + 1
        while i < len(namen) and namen[i].startswith("papr_") and namen[i][5:] in LAENDER:
            k = namen[i][5:]
            laender[k] = t3b[namen[i]]
            i += 1
        for k, r in REGION_ZUSATZ.items():
            if r != rid:
                continue
            werte = t3c["papr_" + k]
            laender[k] = werte
            if k in opec:
                summe = plus(summe, werte)
        gruppen.append({
            "id": rid, "w": rund(summe),
            "laender": sorted((land(k, w, k in opecplus, k in opec) for k, w in laender.items()), key=lambda d: -(d["w"][min(ist - ab, n - 1)] or 0)),
        })
        summe_welt = plus(summe_welt, summe)
    welt = t3a["papr_world"]
    for i in range(n):
        if abs(summe_welt[i] - welt[i]) > 0.02:
            raise SystemExit("Regionen ergeben nicht die Welt: Monat %s, %.3f gegen %.3f" % (monat_text(ab + i), summe_welt[i], welt[i]))

    # Rohoel der OPEC+ je Land (3d)
    rohoel_laender = [land(k, t3d["copr_" + k], k in opecplus, k in opec) for k in opec + weitere]
    rohoel_laender.sort(key=lambda d: -(d["w"][min(ist - ab, n - 1)] or 0))

    # Verbrauch je Region (3e)
    verbrauch = []
    namen = list(t3e.keys())
    for rk, rid in REGIONEN:
        i = namen.index("patc_" + rk) + 1
        laender = []
        while i < len(namen) and namen[i].startswith("patc_") and namen[i][5:] in LAENDER:
            laender.append(land(namen[i][5:], t3e[namen[i]], False, False))
            i += 1
        laender.sort(key=lambda d: -(d["w"][min(ist - ab, n - 1)] or 0))
        verbrauch.append({"id": rid, "w": rund(t3e["patc_" + rk]), "laender": laender})

    d = {
        "stand": stand, "quelle": QUELLE, "ab": monat_text(ab), "ist": monat_text(ist),
        "foerderung": {
            "welt": rund(welt), "opec": rund(t3c["papr_opec"]), "opecplus": rund(t3c["papr_opecplus"]), "gruppen": gruppen,
        },
        "rohoel": {
            "welt": rund(t3d["copr_world"]), "opecplus": rund(t3d["copr_opecplus"]), "laender": rohoel_laender,
            "kapazitaet": rund(t3d["copc_opec"], 2), "frei": rund(t3d["cops_opec"], 2),
            "ausfallOpec": rund(t3d["padi_opec"]), "ausfallAndere": rund(t3b["padi_nonopec"]),
        },
        "verbrauch": {"welt": rund(t3e["patc_world"]), "gruppen": verbrauch},
        "lager": {
            "oecd": rund(t3a["pasc_oecd_t3"], 1), "usa": rund(t3a["pasc_us"], 1), "oecdOhneUsa": rund(t3a["pasc_ooecd_t3"], 1),
            "abbauWelt": rund(t3a["t3_stchange_world"]),
        },
    }
    d["prognose"] = {"foerderung": rund(voll["3atab"]["papr_world"][n:]), "verbrauch": rund(voll["3atab"]["patc_world"][n:])}
    if QUOTEN:
        d["quoten"] = QUOTEN
    return d


def schreiben(d):
    text = json.dumps(d, ensure_ascii=False, separators=(",", ":")) + "\n"
    for ziel in ("daten/welt/oel.json", "app/src/main/assets/welt/oel.json"):
        p = os.path.join(repo(), ziel.replace("/", os.sep))
        with open(p, "w", encoding="utf-8", newline="") as f:
            f.write(text)
        print(ziel, len(text.encode("utf-8")), "Bytes")


def zeigen(d):
    n = len(d["foerderung"]["welt"])
    ab = int(d["ab"][:4]) * 12 + int(d["ab"][5:]) - 1
    ist = int(d["ist"][:4]) * 12 + int(d["ist"][5:]) - 1 - ab
    print("Stand", d["stand"], "ab", d["ab"], "Bestand bis", d["ist"], "Monate", n)
    print("Welt %.2f, OPEC+ %.2f" % (d["foerderung"]["welt"][ist], d["foerderung"]["opecplus"][ist]))
    for g in d["foerderung"]["gruppen"]:
        print(" ", g["id"], "%.2f" % g["w"][ist], ", ".join("%s %.2f" % (l["de"], l["w"][ist]) for l in g["laender"]))
    print("Verbrauch Welt %.2f" % d["verbrauch"]["welt"][ist])
    for g in d["verbrauch"]["gruppen"]:
        print(" ", g["id"], "%.2f" % g["w"][ist], ", ".join("%s %.2f" % (l["de"], l["w"][ist]) for l in g["laender"]))
    print("Lager OECD %.0f Mio. Barrel, USA %.0f" % (d["lager"]["oecd"][ist], d["lager"]["usa"][ist]))


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in ("bauen", "zeigen"):
        raise SystemExit("Aufruf: oel_welt.py bauen|zeigen --steo=<STEO_m.xlsx>")
    steo = next((a.split("=", 1)[1] for a in sys.argv[2:] if a.startswith("--steo=")), None)
    if not steo:
        raise SystemExit("--steo=<STEO_m.xlsx> fehlt")
    d = bauen(steo)
    zeigen(d)
    if sys.argv[1] == "bauen":
        schreiben(d)


if __name__ == "__main__":
    main()
