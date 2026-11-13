#!/usr/bin/env python3
# Baut die Mengen, Werte und Laenderlisten in daten/welt/rohstoffe.json (und der Kopie in app/src/main/assets/welt).
#
# Quellen (alle frei nutzbar):
#   - U.S. Geological Survey, Mineral Commodity Summaries 2026 (gemeinfrei): Weltfoerderung je Land, Tabelle "World ... Production
#     and Reserves" jedes Rohstoffkapitels (pubs.usgs.gov/periodicals/mcs2026/mcs2026-<rohstoff>.pdf)
#   - World Bank Commodity Price Data "Pink Sheet", Jahresmittel (CC BY 4.0): Preise der Basismetalle, Edelmetalle, Eisenerz, Phosphat
#   - USGS MCS 2026, Abschnitt "Salient Statistics", Zeile Price: Preise der uebrigen Metalle (Werte unten in PREISE)
#   - World Nuclear Association, World Uranium Mining Production (Seite als HTML gespeichert): Uran je Land und je Unternehmen
#   - U.S. Energy Information Administration, Uranium Marketing Annual Report 2024 (gemeinfrei): Uranpreis
#
# Aufruf (PyMuPDF wird zum Lesen der PDF-Tabellen gebraucht: pip install pymupdf):
#   python tools/daten/rohstoffe.py bauen --mcs=<Ordner mit mcs2026-*.pdf> --preise=<CMO-Historical-Data-Annual.xlsx> --wna=<wna.html>
#   python tools/daten/rohstoffe.py zeigen --mcs=<Ordner>        Tabelle je Rohstoff ausgeben, nichts schreiben
#
# Regeln: Menge je Land aus der USGS-Tabelle (Spalte des Jahres JAHR). Steht dort "Other countries", wird der Wert als Rest
# uebernommen, sonst ist der Rest die Differenz zur gerundeten Weltsumme (nie negativ). Die Weltmenge ist immer die Summe
# der Laender plus Rest. Laender mit "W" (zurueckgehalten) oder "NA" fallen in den Rest. Wert = Menge x Preis (US-Dollar).
# Danach: python tools/daten/manifest.py bauen --signieren

import json
import os
import re
import sys
import zipfile
import xml.etree.ElementTree as ET

JAHR = 2024
STAND = "2026-11-10"
QUELLE = "U.S. Geological Survey, Mineral Commodity Summaries 2026"
PREIS_QUELLEN = {
    "usgs": "U.S. Geological Survey, Mineral Commodity Summaries 2026",
    "wb": "World Bank Commodity Price Data (The Pink Sheet)",
    "eia": "U.S. Energy Information Administration, Uranium Marketing Annual Report 2024",
}

# BPL = Tricalciumphosphat Ca3(PO4)2, je Formeleinheit ein P2O5; Gestein mit 70 % BPL hat 32,03 % P2O5 (wie der Weltbankpreis)
AG = {"P": 30.974, "O": 15.999, "Ca": 40.078}
P2O5_JE_BPL = (2 * AG["P"] + 5 * AG["O"]) / (3 * AG["Ca"] + 2 * (AG["P"] + 4 * AG["O"]))
P2O5_JE_GESTEIN = 0.70 * P2O5_JE_BPL

LB_JE_T = 2204.62262
OZ_JE_KG = 32.1507466
OZ_JE_T = OZ_JE_KG * 1000.0


def repo():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


# ---------------------------------------------------------------------------------------------------------------- PDF
ZAHL = re.compile(r"^[\d,\.]+$")
SONDER = {"�", "--", "-", "W", "NA", "XX", "—", "–"}


def zeilen(pdf, klein=8.0):
    # Zeilen je Seite aus Textstuecken; Hochzahlen (Schrift kleiner als klein) entfallen
    import fitz
    d = fitz.open(pdf)
    out = []
    for pn, p in enumerate(d):
        toks = []
        for b in p.get_text("dict")["blocks"]:
            for l in b.get("lines", []):
                for s in l["spans"]:
                    if s["size"] < klein or not s["text"].strip():
                        continue
                    x0, _, x1, _ = s["bbox"]
                    txt = s["text"]
                    n = len(txt) or 1
                    pos = 0
                    for part in txt.split():
                        i = txt.index(part, pos)
                        pos = i + len(part)
                        toks.append((s["origin"][1], x0 + (x1 - x0) * i / n, part))
        toks.sort(key=lambda t: (t[0], t[1]))
        rows = []
        for y, x, t in toks:
            if rows and abs(rows[-1][0] - y) < 2.5:
                rows[-1][1].append((x, t))
            else:
                rows.append([y, [(x, t)]])
        for y, r in rows:
            r.sort(key=lambda w: w[0])
            out.append((pn, y, [t for x, t in r]))
    return out


def tabelle(pdf):
    # Zeilen der Weltfoerdertabelle: [(Name, [Zahlen oder None je Spalte])], None fuer "--" (null) als 0.0, "W"/"NA" als None
    zs = zeilen(pdf)
    start = None
    for i, (pn, y, r) in enumerate(zs):
        txt = " ".join(r)
        if start is None and pn >= 1 and re.match(r"^World (Mine|Smelter|Production|Primary|Alumina|Low|Sponge|Refinery)", txt) and ("Production" in txt or "Capacity" in txt):
            start = i
    if start is None:
        return None
    rows = []
    gesehen = False
    for pn, y, r in zs[start + 1:]:
        txt = " ".join(r)
        if not gesehen:
            if re.search(r"20\d\d", txt):
                gesehen = True
            continue
        name = []
        zahlen = []
        for t in r:
            if not zahlen and not (ZAHL.match(t) or t in SONDER):
                name.append(t)
            else:
                zahlen.append(t)
        label = " ".join(name)
        werte = []
        for t in zahlen:
            if ZAHL.match(t):
                try:
                    werte.append(float(t.replace(",", "")))
                except ValueError:
                    werte.append(None)
            elif t in ("--", "�", "-", "—", "–"):
                werte.append(0.0)
            else:
                werte.append(None)
        if not label or not werte:
            continue
        rows.append((label, werte))
        if label.startswith("World total"):
            break
    return rows


# ---------------------------------------------------------------------------------------------------------------- Laender
ALIAS = {
    "Burma": "MM", "Congo (Kinshasa)": "CD", "Congo (Brazzaville)": "CG", "Korea, North": "KP", "Korea, Republic of": "KR",
    "Russia": "RU", "Vietnam": "VN", "Turkey": "TR", "Iran": "IR", "Laos": "LA", "Syria": "SY", "Bolivia": "BO",
    "United States": "US", "United Arab Emirates": "AE", "New Caledonia": "NC", "Papua New Guinea": "PG", "Taiwan": "TW",
    "Tanzania": "TZ", "Venezuela": "VE", "Czechia": "CZ", "Cote d'Ivoire": "CI", "Greenland": "GL", "Nigeria": "NG",
    "Kazakhstan": "KZ", "Kyrgyzstan": "KG", "Tajikistan": "TJ", "Uzbekistan": "UZ", "Mongolia": "MN", "Rwanda": "RW",
    "Mozambique": "MZ", "Madagascar": "MG", "Zimbabwe": "ZW", "Zambia": "ZM", "Namibia": "NA", "Niger": "NE",
    "Mauritania": "MR", "Gabon": "GA", "Ghana": "GH", "Senegal": "SN", "Togo": "TG", "Tunisia": "TN", "Algeria": "DZ",
    "Egypt": "EG", "Jordan": "JO", "Israel": "IL", "Saudi Arabia": "SA", "Qatar": "QA", "Bahrain": "BH", "Iceland": "IS",
    "Bhutan": "BT", "Sri Lanka": "LK", "Malaysia": "MY", "Philippines": "PH", "Thailand": "TH", "Indonesia": "ID",
    "Portugal": "PT", "Spain": "ES", "Poland": "PL", "Austria": "AT", "Sweden": "SE", "Norway": "NO", "Finland": "FI",
    "Ukraine": "UA", "Germany": "DE", "France": "FR", "Japan": "JP", "India": "IN", "China": "CN", "Brazil": "BR",
    "Canada": "CA", "Mexico": "MX", "Peru": "PE", "Chile": "CL", "Argentina": "AR", "Australia": "AU", "Cuba": "CU",
    "Guatemala": "GT", "Pakistan": "PK", "Ethiopia": "ET", "Burundi": "BI", "South Africa": "ZA", "Morocco": "MA",
    "Syria": "SY", "Angola": "AO", "Kosovo": "XK", "Cuba": "CU",
}


_NAMEN = None


def namen():
    # Englische Ländernamen der App (globus/laender.json) -> alpha-2
    global _NAMEN
    if _NAMEN is None:
        with open(os.path.join(repo(), "app", "src", "main", "assets", "globus", "laender.json"), encoding="utf-8") as f:
            _NAMEN = {}
            for c in json.load(f)["c"]:
                _NAMEN.setdefault(c[3], c[0])
    return _NAMEN


def land(name):
    n = re.sub(r"\d+$", "", name.strip())
    n = n.replace("�", "o") if "te d" in n else n
    if n.startswith("C") and "Ivoire" in n:
        return "CI"
    if n in ALIAS:
        return ALIAS[n]
    if n in namen():
        return namen()[n]
    # Hochzahl-Buchstabe e am Ende (Russiae, Japane)
    if n.endswith("e") and n[:-1] in ALIAS:
        return ALIAS[n[:-1]]
    if n.endswith("e") and n[:-1] in namen():
        return namen()[n[:-1]]
    return None


# ---------------------------------------------------------------------------------------------------------------- Rohstoffe
# id: (Datei, Spalten der Menge (Index in der Zeile, werden addiert), Faktor zur Basiseinheit, Basiseinheit)
# Basiseinheit t, kg (Edelmetalle, Gallium) oder m3 (Helium)
SPEZ = {
    "LITHIUM": ("lithium", [0], 1.0, "t"),
    "KOBALT": ("cobalt", [0], 1.0, "t"),
    "SELTENE_ERDEN": ("rare-earths", [0], 1.0, "t"),
    "KUPFER": ("copper", [0], 1000.0, "t"),
    "NICKEL": ("nickel", [0], 1.0, "t"),
    "GRAPHIT": ("graphite", [0], 1.0, "t"),
    "GALLIUM": ("gallium", [0], 1.0, "kg"),
    "ZINN": ("tin", [0], 1.0, "t"),
    "WOLFRAM": ("tungsten", [0], 1.0, "t"),
    "ANTIMON": ("antimony", [0], 1.0, "t"),
    "ALUMINIUM": ("aluminum", [0], 1000.0, "t"),
    "MANGAN": ("manganese", [0], 1000.0, "t"),
    "EISENERZ": ("iron-ore", [0], 1000.0, "t"),
    "SILIZIUM": ("silicon", [0, 2], 1000.0, "t"),
    "VANADIUM": ("vanadium", [0], 1.0, "t"),
    "TITAN": ("titanium", [0], 1.0, "t"),
    "NIOB": ("niobium", [0], 1.0, "t"),
    "TANTAL": ("tantalum", [0], 1.0, "t"),
    "HELIUM": ("helium", [0], 1e6, "m3"),
    "PHOSPHAT": ("phosphate", [0], 1000.0, "t"),
    "FLUSSSPAT": ("fluorspar", [0], 1000.0, "t"),
    "ZINK": ("zinc", [0], 1000.0, "t"),
    "SILBER": ("silver", [0], 1000.0, "kg"),
    "GOLD": ("gold", [0], 1000.0, "kg"),
    "PLATIN": ("platinum-group", [2], 1.0, "kg"),
}

# Preise des Jahres JAHR in US-Dollar je Basiseinheit. Pink Sheet: Spaltenname, Umrechnung auf Tonne oder Kilogramm.
PINK = {
    "KUPFER": ("Copper ($/mt)", 1.0),
    "NICKEL": ("Nickel ($/mt)", 1.0),
    "ALUMINIUM": ("Aluminum ($/mt)", 1.0),
    "ZINK": ("Zinc ($/mt)", 1.0),
    "ZINN": ("Tin ($/mt)", 1.0),
    "EISENERZ": ("Iron ore, cfr spot ($/dmtu)", 1.0),
    "PHOSPHAT": ("Phosphate rock ($/mt)", 1.0),
    "GOLD": ("Gold ($/troy oz)", OZ_JE_KG),
    "SILBER": ("Silver ($/troy oz)", OZ_JE_KG),
    "PLATIN": ("Platinum ($/troy oz)", OZ_JE_KG),
}

# USGS MCS 2026, Salient Statistics, Preise 2024 (Wert, Umrechnung auf Basiseinheit)
# Lithium: Carbonat 11.800 $/t, Li2CO3 enthaelt 18,8 % Lithium. Kobalt: LME cash 11,84 $/lb. Antimon: 10,24 $/lb. Vanadium: V2O5 5,44 $/lb,
# V2O5 enthaelt 56,02 % Vanadium. Wolfram: 252 $ je mtu WO3 (1 t Wolfram = 126,1 mtu WO3). Mangan: 5,53 $ je dmtu Mangan (100 dmtu je t).
# Titan: Schwamm 13,30 $/kg. Niob: Ferroniob 26 $/kg Rohgewicht, 65 % Niob. Tantal: Tantalit 167 $/kg Ta2O5, Ta2O5 enthaelt 81,9 % Tantal.
# Gallium: 439 $/kg (Einfuhrwert).
PREISE = {
    "LITHIUM": (11800.0 / 0.18786, "usgs"),
    "KOBALT": (11.84 * LB_JE_T, "usgs"),
    "ANTIMON": (10.24 * LB_JE_T, "usgs"),
    "VANADIUM": (5.44 * LB_JE_T / 0.5602, "usgs"),
    "WOLFRAM": (252.0 * 100.0 / 0.7930, "usgs"),
    "MANGAN": (5.53 * 100.0, "usgs"),
    "TITAN": (13.30 * 1000.0, "usgs"),
    "NIOB": (26.0 / 0.65 * 1000.0, "usgs"),
    "TANTAL": (167.0 / 0.8190 * 1000.0, "usgs"),
    "GALLIUM": (439.0, "usgs"),
}
# Silizium: Ferrosilizium 75 % (131,96 US-Cent/lb Silizium) und Siliziummetall (170,34 US-Cent/lb), gewichtet nach den Weltmengen beider Formen
SILIZIUM_PREIS = (131.96 * 22.0462262, 170.34 * 22.0462262)

# Uran: EIA, gewichteter Durchschnittspreis der Lieferungen 2024, 52,71 $/lb U3O8; 1 t U = 1,17924 t U3O8
URAN_PREIS = 52.71 * LB_JE_T * 1.17924

# Unternehmen und ihre Kuerzel (Katalog) zur Urangewinnung laut WNA (Tabelle "Uranium production by company 2024")
URAN_FIRMEN = {
    "Kazatomprom": ["KAP.L", "KAP.IL", "KAZ.IL"],
    "Cameco": ["CCJ", "CCO.TO"],
    "BHP": ["BHP", "BHP.AX"],
}


def mcs_laender(pdf, spalten, faktor):
    rows = tabelle(pdf)
    if not rows:
        return None
    laender = []
    ueb = None
    welt = None
    for name, w in rows:
        if name.startswith("World total"):
            welt = [w[i] if i < len(w) else None for i in spalten]
            continue
        if name.startswith("Other"):
            ueb = [w[i] if i < len(w) else None for i in spalten]
            continue
        c = land(name)
        if c is None:
            raise SystemExit("Land ohne Kuerzel: %r in %s" % (name, pdf))
        vs = [w[i] if i < len(w) else None for i in spalten]
        if all(v is None for v in vs):
            continue
        m = sum(v or 0.0 for v in vs) * faktor
        if m > 0:
            laender.append([c, m])
    summe = sum(m for _, m in laender)
    if ueb is not None and not all(v is None for v in ueb):
        rest = sum(v or 0.0 for v in ueb) * faktor
    else:
        wsum = sum(v or 0.0 for v in (welt or [])) * faktor
        rest = max(0.0, wsum - summe)
        # Was die Rundung der Weltsumme allein erklaert, ist kein Rest
        if rest < 0.005 * max(wsum, 1.0):
            rest = 0.0
    laender.sort(key=lambda x: -x[1])
    return laender, rest


def gallium(pdf):
    # Tabelle "World Low-Purity Production and Production Capacity": Primaerproduktion 2024 in kg
    return mcs_laender(pdf, [0], 1.0)


def pink(pfad):
    z = zipfile.ZipFile(pfad)
    ns = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main", "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships"}
    ss = []
    for si in ET.fromstring(z.read("xl/sharedStrings.xml")).findall("m:si", ns):
        ss.append("".join(t.text or "" for t in si.iter("{%s}t" % ns["m"])))
    wb = ET.fromstring(z.read("xl/workbook.xml"))
    rels = {r.get("Id"): r.get("Target") for r in ET.fromstring(z.read("xl/_rels/workbook.xml.rels"))}
    ziel = None
    for s in wb.find("m:sheets", ns):
        if s.get("name") == "Annual Prices (Nominal)":
            t = rels[s.get("{%s}id" % ns["r"])].lstrip("/")
            ziel = t if t.startswith("xl/") else "xl/" + t
    zeilen_ = {}
    for c in ET.fromstring(z.read(ziel)).iter("{%s}c" % ns["m"]):
        m = re.match(r"([A-Z]+)(\d+)", c.get("r"))
        col = 0
        for ch in m.group(1):
            col = col * 26 + ord(ch) - 64
        v = c.find("m:v", ns)
        if v is None:
            continue
        val = v.text
        if c.get("t") == "s":
            val = ss[int(val)]
        else:
            try:
                val = float(val)
            except ValueError:
                pass
        zeilen_.setdefault(int(m.group(2)), {})[col] = val
    kopf = None
    for r, cs in sorted(zeilen_.items()):
        if any(isinstance(v, str) and v.startswith("Copper") for v in cs.values()) and any(isinstance(v, str) and v.startswith("Crude oil") for v in cs.values()):
            kopf = {v: k for k, v in cs.items() if isinstance(v, str)}
            break
    # Die Zeile darunter nennt die Einheit: Spaltenname = Titel plus Einheit ("Copper ($/mt)")
    einheiten = zeilen_[r + 1]
    ergebnis = {}
    for titel, col in kopf.items():
        ergebnis[(titel + " " + str(einheiten.get(col, ""))).strip()] = col
    jahr = None
    for r2, cs in zeilen_.items():
        if cs.get(1) == float(JAHR):
            jahr = cs
    return {n: jahr.get(c) for n, c in ergebnis.items() if jahr and c in jahr}


def wna(pfad):
    # Tabelle "Production from mines (tonnes U)": Land -> Menge 2024; Zeile "Others" ist der Rest
    import html
    t = open(pfad, encoding="utf-8", errors="replace").read()
    t = re.sub(r"(?s)<(script|style).*?</\1>", " ", t)
    i = t.find("Production from mines")
    j = t.find("World total tU", i)
    teil = t[i:j]
    zeilen_ = re.findall(r"(?s)<tr[^>]*>(.*?)</tr>", teil)
    land_ = []
    rest = 0.0
    for z in zeilen_:
        zellen = [html.unescape(re.sub(r"<[^>]+>", " ", c)).strip() for c in re.findall(r"(?s)<t[dh][^>]*>(.*?)</t[dh]>", z)]
        if len(zellen) < 11 or zellen[0] == "Country":
            continue
        name = re.sub(r"\s*\(est\.\)", "", zellen[0]).strip()
        v = float(zellen[-1].replace(",", ""))
        if name == "Others":
            rest = v
            continue
        c = {"USA": "US"}.get(name) or land(name)
        if c is None:
            raise SystemExit("Land ohne Kuerzel: %r (WNA)" % name)
        land_.append([c, v])
    land_.sort(key=lambda x: -x[1])
    return land_, rest


def wna_firmen(pfad):
    import html
    t = open(pfad, encoding="utf-8", errors="replace").read()
    i = t.find("Uranium production by company 2024")
    teil = t[i:t.find("Uranium production by mine", i)]
    out = {}
    for z in re.findall(r"(?s)<tr[^>]*>(.*?)</tr>", teil):
        zellen = [html.unescape(re.sub(r"<[^>]+>", " ", c)).strip() for c in re.findall(r"(?s)<t[dh][^>]*>(.*?)</t[dh]>", z)]
        if len(zellen) >= 2 and zellen[0] in URAN_FIRMEN:
            out[zellen[0]] = float(zellen[1].replace(",", ""))
    return out


# ---------------------------------------------------------------------------------------------------------------- MYB
# USGS Minerals Yearbook 2024, Volume III (gemeinfrei, CC0 1.0): Tabelle "Production" (2024MYBv3_Production_table.csv), Laender 2020 bis 2024.
# Je Rohstoff und Land gilt Jahr JAHR; ist der Wert dort nicht lesbar (-999, leer), gilt das juengste Jahr davor mit Wert (die Zeile
# steht dann im Bericht mit "Jahr"). Das Jahrbuch fuehrt 168 Laender, die Vereinigten Staaten gehoeren nicht dazu: fuer Laender ohne
# nutzbare Jahrbuchzeile bleibt der bisherige Wert (USGS MCS 2026, bei Uran WNA).
# Stufe je Rohstoff: Bergwerksfoerderung (Level 3 "Mine") im Inhalt des Metalls oder Oxids, auf das sich der Preis bezieht. Ohne
# Inhaltsangabe (nur Rohgewicht) gibt es keine Umrechnung; solche Zeilen zaehlen nicht. Sind je Land Konzentrat, Loesungsgewinnung
# und Erz getrennt gefuehrt, gelten Konzentrat und Loesungsgewinnung zusammen, Erz nur ohne beide (Erz ist dann dasselbe Metall vor
# der Aufbereitung). Weitere Formen (Seifen, Laterit, Pyrochlor, Columbit) sind eigene Zeilen und werden addiert.
# id: (Level 2, erlaubte Level 3, Level 4 (None: beliebig), Level 5 als Regex (None: beliebig), Commodity Unit, Basiseinheit,
#      Faktor auf die Bezugsgroesse des Preises, Erz nur ohne Konzentrat)
#   Eisenerz: Fe-Inhalt, Erz mit 62 % Fe (wie der Eisenerzpreis); Phosphat: P2O5-Inhalt, Gestein mit 70 % BPL (32,03 % P2O5, wie der
#   Weltbankpreis); Aluminium: Huettenaluminium (Primaer); Titan: Schwamm; Gallium und Germanium in kg.
# Nicht aus dem Jahrbuch (bleiben USGS MCS 2026, Feld mq "mcs"): Lithium (Spodumen, Sole und Carbonat in Rohgewicht, nur 3 Laender mit
# Li-Inhalt) und Silizium (Jahrbuch nur Metall, nicht Ferrosilizium).
MYB_SPEZ = {
    "KOBALT": ("Cobalt", {"Mine"}, None, None, "Co content", "t", 1.0, True),
    "SELTENE_ERDEN": ("Rare earths", {"Mine"}, None, None, "Oxide content", "t", 1.0, False),
    "KUPFER": ("Copper", {"Mine"}, None, None, "Cu content", "t", 1.0, True),
    "NICKEL": ("Nickel", {"Mine"}, None, None, "Ni content", "t", 1.0, False),
    "GRAPHIT": ("Graphite", {""}, None, None, "Gross weight", "t", 1.0, False),
    "GALLIUM": ("Gallium", {""}, None, None, "Gross weight", "kg", 1.0, False),
    "GERMANIUM": ("Germanium", {""}, None, None, "Ge content", "kg", 1.0, False),
    "ZINN": ("Tin", {"Mine"}, None, None, "Sn content", "t", 1.0, False),
    "WOLFRAM": ("Tungsten", {"Mine"}, None, None, "W content", "t", 1.0, False),
    "ANTIMON": ("Antimony", {"Mine"}, None, None, "Sb content", "t", 1.0, True),
    "ALUMINIUM": ("Aluminum", {""}, {"Primary"}, r"^Metal$", "Gross weight", "t", 1.0, False),
    "MANGAN": ("Manganese", {"Mine"}, None, None, "Mn content", "t", 1.0, True),
    "EISENERZ": ("Iron ore", {"Mine"}, None, None, "Fe content", "t", 1.0 / 0.62, False),
    "VANADIUM": ("Vanadium", {""}, None, None, "V content", "t", 1.0, False),
    "TITAN": ("Titanium", {""}, None, r"^Sponge$", "Gross weight", "t", 1.0, False),
    "NIOB": ("Niobium", {"Mine"}, None, None, "Nb content", "t", 1.0, False),
    "TANTAL": ("Tantalum", {"Mine"}, None, None, "Ta content", "t", 1.0, False),
    "HELIUM": ("Helium", {""}, None, None, "Gross volume", "m3", 1.0, False),
    "PHOSPHAT": ("Phosphate rock", {""}, None, None, "P2O5 content", "t", 1.0 / P2O5_JE_GESTEIN, False),
    "FLUSSSPAT": ("Fluorspar", {""}, None, None, "Gross weight", "t", 1.0, False),
    "ZINK": ("Zinc", {"Mine"}, None, None, "Zn content", "t", 1.0, True),
    "SILBER": ("Silver", {"Mine"}, None, None, "Ag content", "kg", 1.0, True),
    "GOLD": ("Gold", {"Mine"}, None, None, "Au content", "kg", 1.0, True),
    "PLATIN": ("Platinum", {"Mine"}, None, None, "Pt content", "kg", 1.0, False),
    "URAN": ("Uranium", {"Mine"}, None, None, "U content", "t", 1.0, False),
}
MYB_QUELLE = "U.S. Geological Survey, Minerals Yearbook 2024, Volume III"
MYB_URL = "https://doi.org/10.5066/P1KEQASH"
MYB_ALIAS = {
    "Burma": "MM", "Congo (Kinshasa)": "CD", "Congo (Brazzaville)": "CG", "Korea, Republic Of": "KR", "North Korea": "KP",
    "French Guiana": "FR", "Reunion": "FR", "Trinidad And Tobago": "TT", "Bosnia and Herzegovina": "BA", "Timor-Leste": "TL",
    "Eswatini": "SZ", "Kosovo": "XK", "Turkey": "TR", "Russia": "RU", "Syria": "SY", "Iran": "IR", "Laos": "LA", "Taiwan": "TW",
    "Vietnam": "VN", "Moldova": "MD", "Greenland": "GL", "Brunei": "BN", "Czechia": "CZ", "North Macedonia": "MK",
}
_EINHEIT_JE_BASIS = {("t", "Metric tons"): 1.0, ("t", "Kilograms"): 0.001, ("kg", "Kilograms"): 1.0, ("kg", "Metric tons"): 1000.0,
                     ("m3", "Cubic meters"): 1.0}


def myb_land(name):
    n = name.strip()
    if "te d" in n and n.startswith("C") and "Ivoire" in n:
        return "CI"
    if n in MYB_ALIAS:
        return MYB_ALIAS[n]
    c = land(n)
    if c is None:
        raise SystemExit("MYB: Land ohne Kuerzel: %r" % name)
    return c


def myb_zeilen(ordner):
    import csv
    with open(os.path.join(ordner, "2024MYBv3_Production_table.csv"), encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


def myb_wert(z):
    try:
        v = float(z["Value"])
    except ValueError:
        return None
    return v if v >= 0 else None


def myb_rohstoff(zeilen_, spez, jahr=JAHR):
    # -> ({alpha-2: Menge in Basiseinheit}, [(alpha-2, Hinweis)]); Menge je Land: Summe der gewaehlten Zeilen
    l2, l3, l4, l5, cu, basis, faktor, erz_alt = spez
    rx = re.compile(l5) if l5 else None
    je_land = {}
    for z in zeilen_:
        if z["Level 2 (Commodity)"] != l2 or z["Commodity Unit"] != cu:
            continue
        if z["Level 3 (Type)"] not in l3 or (l4 is not None and z["Level 4 (Phase)"] not in l4):
            continue
        if rx and not rx.search(z["Level 5 (Form)"]):
            continue
        schluessel = (z["Level 3 (Type)"], z["Level 4 (Phase)"], z["Level 5 (Form)"], z["Descriptor"], z["Other Notes"])
        je_land.setdefault(myb_land(z["Country (Short Form) or Locality"]), {}).setdefault(schluessel, {}).setdefault(int(z["Time Period"]), []).append(z)
    out = {}
    hinweise = []
    for c, reihen in je_land.items():
        if erz_alt and any(k[2] != "Ore" for k in reihen):
            reihen = {k: v for k, v in reihen.items() if k[2] != "Ore"}
        # Jahr JAHR gilt, solange eine der gewaehlten Zeilen dort einen Wert hat (Zeilen ohne Wert zaehlen dann nicht); nur wenn keine
        # Zeile im Jahr JAHR lesbar ist, gilt je Zeile das juengste Jahr davor mit Wert
        def lesbar(y, jahre_):
            return y in jahre_ and myb_wert(jahre_[y][0]) is not None
        summe = 0.0
        gueltig = False
        im_jahr = any(lesbar(jahr, jahre) for jahre in reihen.values())
        for k, jahre in reihen.items():
            ys = [jahr] if im_jahr else sorted((y for y in jahre if y <= jahr), reverse=True)
            for y in ys:
                if not lesbar(y, jahre):
                    continue
                zs = jahre[y]
                if len(zs) > 1:
                    hinweise.append((c, "doppelte Zeile, erste gilt"))
                summe += myb_wert(zs[0]) * _EINHEIT_JE_BASIS[(basis, zs[0]["Unit"])]
                gueltig = True
                if y != jahr:
                    hinweise.append((c, "Jahr %d" % y))
                break
        if gueltig:
            out[c] = summe * faktor
    return out, hinweise


def myb_anwenden(d, ordner, bericht):
    # Ersetzt Menge, Laenderliste, Rest und Anteile der Rohstoffe, die das Jahrbuch fuehrt; Preise und Unternehmensmengen bleiben
    zeilen_ = myb_zeilen(ordner)
    gueltig = set(namen().values()) | {"GL", "XK", "TL"}
    for r in d["rohstoffe"]:
        rid = r["id"]
        if rid not in MYB_SPEZ:
            if r.get("mg") is not None:
                r["mq"] = "mcs"
                bericht.append((rid, "USGS MCS 2026 (nicht im Jahrbuch), %d Laender" % len(r.get("lm", []))))
            continue
        roh, hinweise = myb_rohstoff(zeilen_, MYB_SPEZ[rid])
        alt = {x["c"]: x["m"] for x in r.get("lm", [])}
        alt_welt = r.get("mg")
        alt_rest = r.get("ru", 0)
        basis = MYB_SPEZ[rid][5]
        if "me" in r and r["me"] != basis:
            raise SystemExit("MYB: Einheit %s weicht von %s ab (%s)" % (basis, r["me"], rid))
        laender = dict(roh)
        uebernommen = []
        for c, m in alt.items():
            if c not in laender and m > 0:
                laender[c] = float(m)
                uebernommen.append(c)
        liste = sorted(([c, m] for c, m in laender.items() if m > 0), key=lambda x: (-x[1], x[0]))
        for c, _ in liste:
            if c not in gueltig:
                raise SystemExit("MYB: Kuerzel %s nicht im Laenderverzeichnis der App" % c)
        r["me"] = basis
        r["mj"] = JAHR
        r["lm"] = [{"c": c, "m": runden(m)} for c, m in liste]
        r["ru"] = 0
        welt_m = round(sum(x["m"] for x in r["lm"]), 2)
        r["mg"] = int(welt_m) if welt_m == int(welt_m) else welt_m
        r["mq"] = "myb"
        ex = [(c, m / welt_m * 100) for c, m in liste[:5] if round(m / welt_m * 100) >= 1]
        ps = [int(round(x)) for _, x in ex]
        while sum(ps) > 100:
            k = max(range(len(ps)), key=lambda i: ps[i] - ex[i][1])
            ps[k] -= 1
        neu = [{"c": c, "p": p} for (c, _), p in zip(ex, ps)]
        r["l"] = neu if neu else [{"c": liste[0][0], "p": 0}]
        bericht.append((rid, "MYB %s, %d Laender (vorher %d, Rest vorher %s), Welt %s %s (vorher %s), aus USGS MCS/WNA %s, Jahr-Hinweise %s" % (
            MYB_SPEZ[rid][0], len(liste), len(alt), runden(alt_rest), r["mg"], basis, alt_welt, ",".join(uebernommen) or "-",
            ",".join("%s:%s" % h for h in hinweise) or "-")))
    d["mengenQuellen"] = {"mcs": QUELLE, "myb": MYB_QUELLE}
    d["quelle"] = MYB_QUELLE + ", " + QUELLE


def runden(x):
    return int(round(x)) if abs(x) >= 100 else round(x, 2)


def bauen(mcs, preise, wnadatei, schreiben):
    pfad = os.path.join(repo(), "daten", "welt", "rohstoffe.json")
    with open(pfad, encoding="utf-8") as f:
        d = json.load(f)
    pk = pink(preise) if preise else {}
    firmen = {}
    bericht = []
    for r in d["rohstoffe"]:
        rid = r["id"]
        for k in ("mg", "me", "mj", "lm", "ru", "pr", "pj", "pq"):
            r.pop(k, None)
        laender = None
        einheit = "t"
        if rid == "URAN":
            laender, rest = wna(wnadatei)
            einheit = "t"
            quelle = "World Nuclear Association"
            preis = (URAN_PREIS, "eia")
        elif rid in SPEZ:
            datei, spalten, faktor, einheit = SPEZ[rid]
            pdf = os.path.join(mcs, "mcs2026-%s.pdf" % datei)
            res = mcs_laender(pdf, spalten, faktor)
            if res is None:
                bericht.append((rid, "Tabelle nicht gefunden"))
                continue
            laender, rest = res
            if rid == "GALLIUM":
                # Die Zahlen der Tabelle stehen versetzt zu den Namen (Japan ohne Wert); Primaerproduktion 2024 in kg: China 839.000,
                # Russland 3.000, uebrige Laender 6.000 (Summe 848.000 wie die Weltsumme)
                laender, rest = [["CN", 839000.0], ["RU", 3000.0]], 6000.0
            quelle = "USGS MCS 2026"
            preis = None
            if rid in PINK and pk:
                spalte, f = PINK[rid]
                v = pk.get(spalte)
                if v is None:
                    raise SystemExit("Pink Sheet ohne Spalte %s" % spalte)
                preis = (v * (OZ_JE_KG if rid in ("GOLD", "SILBER", "PLATIN") else 1.0), "wb")
            elif rid in PREISE:
                preis = PREISE[rid]
            elif rid == "SILIZIUM":
                # gewichtet nach Ferrosilizium (Spalte 0) und Siliziummetall (Spalte 2)
                rows = tabelle(pdf)
                welt = [w for n, w in rows if n.startswith("World total")][0]
                a, b = welt[0] * 1000.0, welt[2] * 1000.0
                preis = ((a * SILIZIUM_PREIS[0] + b * SILIZIUM_PREIS[1]) / (a + b), "usgs")
        else:
            bericht.append((rid, "keine Mengen (USGS nennt keine Weltfoerderung)"))
            continue
        summe = sum(m for _, m in laender)
        welt_m = summe + rest
        r["mg"] = runden(welt_m)
        r["me"] = einheit
        r["mj"] = JAHR
        r["lm"] = [{"c": c, "m": runden(m)} for c, m in laender]
        r["ru"] = runden(rest)
        if preis:
            r["pr"] = round(preis[0], 4)
            r["pj"] = JAHR
            r["pq"] = preis[1]
        # Anteile (Prozent, ganz) aus den Mengen; Rohstoffe ohne veroeffentlichte Anteile (p = 0) behalten sie nicht
        if welt_m > 0:
            neu = [{"c": c, "p": int(round(m / welt_m * 100))} for c, m in laender[:5] if round(m / welt_m * 100) >= 1]
            if neu:
                r["l"] = neu
        bericht.append((rid, "%s, %d Laender, Rest %s, Welt %s %s, Preis %s" % (quelle, len(laender), runden(rest), runden(welt_m), einheit, "%.0f/%s %s" % (preis[0], einheit, preis[1]) if preis else "-")))
        if rid == "URAN":
            fm = wna_firmen(wnadatei)
            for name, m in fm.items():
                for s in URAN_FIRMEN[name]:
                    firmen.setdefault(s, []).append({"r": "URAN", "m": runden(m), "j": JAHR})
    d["v"] = 2
    d["stand"] = STAND
    d["jahr"] = JAHR
    d["quelle"] = QUELLE
    d["preisQuellen"] = PREIS_QUELLEN
    d["firmen"] = firmen
    d.pop("mengenQuellen", None)
    speichern(d, bericht, schreiben)


def speichern(d, bericht, schreiben):
    text = json.dumps(d, ensure_ascii=False, separators=(",", ":"))
    for z in bericht:
        print("%-14s %s" % z)
    if schreiben:
        for p in (os.path.join(repo(), "daten", "welt", "rohstoffe.json"), os.path.join(repo(), "app", "src", "main", "assets", "welt", "rohstoffe.json")):
            with open(p, "w", encoding="utf-8", newline="") as f:
                f.write(text)
        print("geschrieben:", len(text), "Bytes")


def nur_myb(ordner, schreiben):
    # Jahrbuch auf den vorhandenen Stand der Datei anwenden (Preise, Firmenmengen und Rohstoffe ohne Jahrbuch bleiben)
    with open(os.path.join(repo(), "daten", "welt", "rohstoffe.json"), encoding="utf-8") as f:
        d = json.load(f)
    bericht = []
    myb_anwenden(d, ordner, bericht)
    d["v"] = 3
    speichern(d, bericht, schreiben)


def main():
    a = sys.argv[1:]
    if not a or a[0] not in ("bauen", "zeigen", "myb"):
        print(__doc__)
        return
    opt = {}
    for x in a[1:]:
        if x.startswith("--") and "=" in x:
            k, v = x[2:].split("=", 1)
            opt[k] = v
    if a[0] == "myb":
        nur_myb(opt["myb"], "--schreiben" in a)
        return
    bauen(opt.get("mcs"), opt.get("preise"), opt.get("wna"), a[0] == "bauen")


if __name__ == "__main__":
    main()
