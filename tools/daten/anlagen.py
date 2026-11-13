#!/usr/bin/env python3
# Baut welt/anlagen.json: Kapazitaet der Bergwerke je Unternehmen, Rohstoff und Land.
#
# Quelle: U.S. Geological Survey, Minerals Yearbook 2024, Volume III (gemeinfrei, CC0 1.0), Tabelle "Facilities"
# (2024MYBv3_Facilities_table.csv). Aufruf:
#   python tools/daten/anlagen.py bauen --myb=<Ordner mit der Facilities-Tabelle>      schreibt daten/welt und die Kopie in assets/welt
#   python tools/daten/anlagen.py zeigen --myb=<Ordner>                                 Bericht, nichts schreiben
# Danach: python tools/daten/manifest.py bauen --signieren
#
# Regeln (nichts wird geschaetzt):
#  - Anlagen: Stufe wie bei den Foerdermengen (Bergwerk, Inhalt des Metalls oder Oxids, siehe ANL_SPEZ), Status "Assumed active".
#    Kapazitaet in anderer Bezugsgroesse (nur Rohgewicht), "NA" als Einheit oder -999 zaehlt nicht. Umrechnung nur stoechiometrisch
#    (Li2CO3 -> Li, V2O5 -> V, Nb2O5 -> Nb, Ta2O5 -> Ta), Eisenerz Fe -> Erz mit 62 % Fe, Phosphat P2O5 -> Gestein mit 70 % BPL.
#  - Eintraege, deren Kapazitaetsvermerk eine Gesamtzahl fuer alle Betriebe oder unbekannte Kapazitaet je Bergwerk nennt (NICHT_JE_ANLAGE),
#    zaehlen nicht.
#  - Teilen Anlageneintraege eine Kapazitaet (Spalte Shared Capacity), zaehlt sie einmal, wenn Kapazitaet und Anteil des
#    Unternehmens in allen Eintraegen gleich sind; sonst ist die Gruppe nicht verwertbar.
#  - Anteil des Unternehmens: Eigentuemer mit bekanntem Anteil zaehlt mit diesem Anteil; Betreiber ohne jeden genannten Eigentuemer
#    zaehlt mit 100 %; Anteil unbekannt (-999) oder Betreiber neben anderen Eigentuemern: Anlage nicht verwertbar.
#  - Je Unternehmen und Rohstoff: m = Summe der anteiligen Kapazitaet, l = je Land, n = verwertbare Anlagen, t = Anlagen des
#    Unternehmens (inklusive nicht verwertbarer).
#  - Zuordnung zu Aktien: Name von Betreiber oder Eigentuemer = Name im Katalog (Rechtsform, Gross-/Kleinschreibung und
#    Zeichensetzung ausgenommen, "Group" nur in FIRMEN_GRUPPE), nur bei genau einem Katalogeintrag mit dem Namen; dazu die
#    festen Zuordnungen in FIRMEN_ALIAS (gleiche Firma unter anderem Namen) und die weiteren Kuerzel derselben Firma (EXTRA).

import json
import os
import re
import sys
import unicodedata

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rohstoffe as R  # noqa: E402

JAHR = 2024
STAND = "2026-11-10"

# Anteil Metall je Verbindung (Atomgewichte aus rohstoffe.py)
AGW = {"Li": 6.94, "O": 15.999, "V": 50.9415, "Nb": 92.906, "Ta": 180.948}
LI_JE_LI2CO3 = 2 * AGW["Li"] / (2 * AGW["Li"] + 12.011 + 3 * AGW["O"])
V_JE_V2O5 = 2 * AGW["V"] / (2 * AGW["V"] + 5 * AGW["O"])
NB_JE_NB2O5 = 2 * AGW["Nb"] / (2 * AGW["Nb"] + 5 * AGW["O"])
TA_JE_TA2O5 = 2 * AGW["Ta"] / (2 * AGW["Ta"] + 5 * AGW["O"])

# id: (Level 2, Level 3, Level 4 (None: beliebig), Level 5 als Regex (None: beliebig), {Commodity Unit: Faktor} in Reihenfolge der
#      Vorrang, Basiseinheit, Faktor auf die Bezugsgroesse des Preises, Erz nur ohne Konzentrat derselben Anlage)
ANL_SPEZ = {
    "LITHIUM": ("Lithium", {"Mine"}, None, None, {"Li content": 1.0, "Li2CO3 content": LI_JE_LI2CO3}, "t", 1.0, False),
    "KOBALT": ("Cobalt", {"Mine"}, None, None, {"Co content": 1.0}, "t", 1.0, True),
    "SELTENE_ERDEN": ("Rare earths", {"Mine"}, None, None, {"Oxide content": 1.0}, "t", 1.0, False),
    "KUPFER": ("Copper", {"Mine"}, None, None, {"Cu content": 1.0}, "t", 1.0, True),
    "NICKEL": ("Nickel", {"Mine"}, None, None, {"Ni content": 1.0}, "t", 1.0, True),
    "GRAPHIT": ("Graphite", {""}, None, None, {"Gross weight": 1.0}, "t", 1.0, False),
    "GALLIUM": ("Gallium", {""}, None, None, {"Ga content": 1.0, "Gross weight": 1.0}, "kg", 1.0, False),
    "GERMANIUM": ("Germanium", {""}, None, None, {"Ge content": 1.0}, "kg", 1.0, False),
    "ZINN": ("Tin", {"Mine"}, None, None, {"Sn content": 1.0}, "t", 1.0, True),
    "WOLFRAM": ("Tungsten", {"Mine"}, None, None, {"W content": 1.0}, "t", 1.0, True),
    "ANTIMON": ("Antimony", {"Mine"}, None, None, {"Sb content": 1.0}, "t", 1.0, True),
    "ALUMINIUM": ("Aluminum", {""}, {"Primary"}, r"^Metal$", {"Gross weight": 1.0}, "t", 1.0, False),
    "MANGAN": ("Manganese", {"Mine"}, None, None, {"Mn content": 1.0}, "t", 1.0, True),
    "EISENERZ": ("Iron ore", {"Mine"}, None, None, {"Fe content": 1.0}, "t", 1.0 / 0.62, True),
    "VANADIUM": ("Vanadium", {""}, None, None, {"V content": 1.0, "V2O5 content": V_JE_V2O5}, "t", 1.0, False),
    "TITAN": ("Titanium", {""}, None, r"^Sponge$", {"Ti content": 1.0, "Gross weight": 1.0}, "t", 1.0, False),
    "NIOB": ("Niobium", {"Mine"}, None, None, {"Nb content": 1.0, "Nb2O5 content": NB_JE_NB2O5}, "t", 1.0, True),
    "TANTAL": ("Tantalum", {"Mine"}, None, None, {"Ta content": 1.0, "Ta2O5 content": TA_JE_TA2O5}, "t", 1.0, True),
    "HELIUM": ("Helium", {""}, None, None, {"Gross volume": 1.0}, "m3", 1.0, False),
    "PHOSPHAT": ("Phosphate rock", {""}, None, None, {"P2O5 content": 1.0}, "t", 1.0 / R.P2O5_JE_GESTEIN, False),
    "FLUSSSPAT": ("Fluorspar", {""}, None, None, {"Gross weight": 1.0}, "t", 1.0, False),
    "ZINK": ("Zinc", {"Mine"}, None, None, {"Zn content": 1.0}, "t", 1.0, True),
    "SILBER": ("Silver", {"Mine"}, None, None, {"Ag content": 1.0}, "kg", 1.0, True),
    "GOLD": ("Gold", {"Mine"}, None, None, {"Au content": 1.0}, "kg", 1.0, True),
    "PLATIN": ("Platinum", {"Mine"}, None, None, {"Pt content": 1.0}, "kg", 1.0, False),
    "URAN": ("Uranium", {"Mine"}, None, None, {"U content": 1.0}, "t", 1.0, True),
}

NICHT_JE_ANLAGE = re.compile(r"(?i)totals for all enterprises|capacity of individual mines? (is|are) unknown")

# ---------------------------------------------------------------------------------------------------------------- Firmen
# Banken und Versicherer treten im Jahrbuch als Kapitaleigner auf; sie bekommen keine Anlagenkapazitaet
KEINE_KAPAZITAET = {"BANKEN", "VERSICHERUNG"}
SUFFIX = re.compile(
    r"\b(ltd|limited|inc|incorporated|corp|corporation|plc|ag|sa|nv|co|company|holdings?|se|asa|ab|oyj|llc|spa|pjsc|jsc|pcl|bhd|tbk|"
    r"kgaa|ltda|sas|gmbh|as|oy|bv|the|and|de|cv)\b"
)


def norm(s, ohne_group=False):
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode().lower().replace("&", " ")
    s = re.sub(r"\(.*?\)", " ", s)
    s = re.sub(r"[^a-z0-9+ ]+", " ", s)
    s = SUFFIX.sub(" ", s)
    if ohne_group:
        s = re.sub(r"\bgroup\b", " ", s)
    return re.sub(r"\s+", " ", s).strip()


# Gleiche Firma, Name mit "Group" im Betreibernamen des Jahrbuchs (Konzernname statt Rechtsform; Handelshaeuser-Gruppen wie
# "Mitsubishi Group" sind nicht dieselbe Firma wie die Aktie und stehen hier nicht)
FIRMEN_GRUPPE = {"BHP Group Ltd.", "Rio Tinto Group", "Aurubis Group", "K+S Group", "Saint-Gobain Group", "CRH Group"}
# Gleiche Firma unter anderem Namen: Name im Jahrbuch -> Kuerzel
FIRMEN_ALIAS = {
    "Freeport-McMoRan Copper and Gold Inc.": ["FCX"],
    "Sumitomo Metal Mining Co., Ltd. (SMM)": ["5713.T"],
    "Mitsui Mining & Smelting Co., Ltd.": ["5706.T"],
    "Barrick Gold Corp.": ["B"],
    "Newmont Mining Corp.": ["NEM"],
    "NMC Kazatomprom JSC": ["KAP.L", "KAP.IL", "KAZ.IL"],
}
# Weitere Kuerzel derselben Firma (wie URAN_FIRMEN in rohstoffe.py und die Profile in Rohstoffprofil.kt)
EXTRA = {"BHP": ["BHP.AX"], "CCJ": ["CCO.TO"]}


def firmen_zuordnung():
    # -> {Name im Jahrbuch: [Kuerzel]}; nur eindeutige Treffer
    with open(os.path.join(R.repo(), "daten", "katalog.json"), encoding="utf-8") as f:
        kat = json.load(f)["aktien"]
    index = {}
    for a in kat:
        if a["i"] not in KEINE_KAPAZITAET:
            index.setdefault(norm(a["n"]), []).append(a)
    return index, {a["s"]: a for a in kat}


def namen_zuordnen(namen_, index):
    out = {}
    for n in namen_:
        if n in FIRMEN_ALIAS:
            out[n] = list(FIRMEN_ALIAS[n])
            continue
        k = norm(n, ohne_group=n in FIRMEN_GRUPPE)
        if not k or k not in index:
            continue
        treffer = index[k]
        if len({a["c"] for a in treffer}) != 1 or len(k) < 3:
            continue
        syms = [a["s"] for a in treffer]
        for s in list(syms):
            syms += EXTRA.get(s, [])
        out[n] = syms
    return out


# ---------------------------------------------------------------------------------------------------------------- Anlagen
BASIS_JE_EINHEIT = {("t", "Metric tons"): 1.0, ("t", "Kilograms"): 0.001, ("kg", "Kilograms"): 1.0, ("kg", "Metric tons"): 1000.0,
                    ("m3", "Cubic meters"): 1.0}
INHABER = ["Major Operating Company"] + ["Equity Owner %d" % i for i in range(1, 9)]


def zahl(s):
    try:
        v = float(s)
    except (TypeError, ValueError):
        return None
    return v if v >= 0 else None


def lesen(ordner):
    import csv
    with open(os.path.join(ordner, "2024MYBv3_Facilities_table.csv"), encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


def anteil_von(z, gruppe_namen):
    # Anteil des Unternehmens (Namen in gruppe_namen) an dem Eintrag: (Prozent, Betreiber ja/nein) oder None (nicht beteiligt);
    # Prozent None = unbekannt
    eigner = [(z["Equity Owner %d" % i].strip(), zahl(z["Share of Equity Owner %d" % i])) for i in range(1, 9) if z["Equity Owner %d" % i].strip()]
    summe = 0.0
    beteiligt = False
    unbekannt = False
    for n, s in eigner:
        if n in gruppe_namen:
            beteiligt = True
            if s is None or s <= 0:
                unbekannt = True
            else:
                summe += s
    if beteiligt:
        return (None if unbekannt else min(summe, 100.0)), False
    if z["Major Operating Company"].strip() in gruppe_namen:
        return (100.0 if not eigner else None), True
    return None


def kapazitaet(zeilen_, spez):
    # Verwertbare Eintraege eines Rohstoffs: [(Zeile, Kapazitaet in Basiseinheit des Rohstoffs je Eintrag)]; Kapazitaet None: nicht verwertbar
    l2, l3, l4, l5, cus, basis, faktor, erz_alt = spez
    rx = re.compile(l5) if l5 else None
    kand = [z for z in zeilen_ if z["Level 2 (Commodity)"] == l2 and z["Level 3 (Type)"] in l3 and (l4 is None or z["Level 4 (Phase)"] in l4)
            and (not rx or rx.search(z["Level 5 (Form)"])) and z["Facility Status"].strip().lower() == "assumed active"]
    # Je Anlage und Form der Vorrang der Bezugsgroessen (Inhalt vor Rohgewicht)
    je_anlage = {}
    for z in kand:
        je_anlage.setdefault(z["USGS Facility ID"], []).append(z)
    gewaehlt = []
    for fid, zs in je_anlage.items():
        if erz_alt and any(z["Level 5 (Form)"] != "Ore" and z["Commodity Unit"] in cus for z in zs):
            zs = [z for z in zs if z["Level 5 (Form)"] != "Ore"]
        formen = {}
        for z in zs:
            formen.setdefault((z["Level 3 (Type)"], z["Level 4 (Phase)"], z["Level 5 (Form)"], z["Descriptor"]), []).append(z)
        for gz in formen.values():
            for cu in cus:
                hit = [z for z in gz if z["Commodity Unit"] == cu]
                if hit:
                    gewaehlt += [(z, cus[cu]) for z in hit]
                    break
    out = []
    for z, cf in gewaehlt:
        v = zahl(z["Annual Production Capacity"])
        e = BASIS_JE_EINHEIT.get((basis, z["Capacity Unit"]))
        ungeeignet = v is None or e is None or NICHT_JE_ANLAGE.search(z["Capacity Notes"])
        out.append((z, None if ungeeignet else v * e * cf * faktor))
    return out


def je_firma(zeilen_, zuordnung):
    # {Kuerzelgruppe (Tuple): {Rohstoff: {"m":, "l": {Land: m}, "n":, "t":}}}
    # Namen je Kuerzelgruppe
    gruppen = {}
    for n, syms in zuordnung.items():
        gruppen.setdefault(tuple(sorted(set(syms))), set()).add(n)
    ergebnis = {}
    bericht = []
    for rid, spez in ANL_SPEZ.items():
        eintraege = kapazitaet(zeilen_, spez)
        for syms, gnamen in gruppen.items():
            # Gruppen von Eintraegen mit geteilter Kapazitaet
            je_gruppe = {}
            for z, cap in eintraege:
                a = anteil_von(z, gnamen)
                if a is None:
                    continue
                je_gruppe.setdefault(z["Shared Capacity"] or z["Global Entry"], []).append((z, cap, a[0]))
            m_land = {}
            n = t = 0
            for key, mitglieder in je_gruppe.items():
                t += 1
                werte = {(cap, s) for _, cap, s in mitglieder}
                cap, s = mitglieder[0][1], mitglieder[0][2]
                if len(werte) != 1 or cap is None or s is None or s <= 0:
                    continue
                n += 1
                c = R.myb_land(mitglieder[0][0]["Country (Short Form) or Locality"])
                m_land[c] = m_land.get(c, 0.0) + cap * s / 100.0
            if t:
                ergebnis.setdefault(syms, {})[rid] = {"m_land": m_land, "n": n, "t": t}
    return ergebnis


def bauen(ordner, schreiben):
    zeilen_ = lesen(ordner)
    index, _ = firmen_zuordnung()
    namen_ = set()
    for z in zeilen_:
        for k in INHABER:
            v = z[k].strip()
            if v and v != "-999":
                namen_.add(v)
    zuordnung = namen_zuordnen(namen_, index)
    erg = je_firma(zeilen_, zuordnung)
    firmen = []
    for syms in sorted(erg, key=lambda s: s[0]):
        r = {}
        for rid, x in sorted(erg[syms].items()):
            if not x["m_land"]:
                continue
            laender = sorted(x["m_land"].items(), key=lambda kv: (-kv[1], kv[0]))
            r[rid] = {"m": R.runden(sum(m for _, m in laender)), "l": [{"c": c, "m": R.runden(m)} for c, m in laender if R.runden(m) > 0],
                      "n": x["n"], "t": x["t"]}
        if r:
            firmen.append({"s": list(syms), "r": r})
    d = {"v": 1, "stand": STAND, "jahr": JAHR, "quelle": R.MYB_QUELLE, "firmen": firmen}
    text = json.dumps(d, ensure_ascii=False, separators=(",", ":"))
    for f in firmen:
        print("%-28s %s" % (",".join(f["s"]), " ".join("%s:%s(%d/%d)" % (k, v["m"], v["n"], v["t"]) for k, v in f["r"].items())))
    print("Namen zugeordnet:", len(zuordnung), "Firmen mit Kapazitaet:", len(firmen), "Rohstoffzeilen:", sum(len(f["r"]) for f in firmen), "Bytes:", len(text.encode("utf-8")))
    if schreiben:
        for p in (os.path.join(R.repo(), "daten", "welt", "anlagen.json"), os.path.join(R.repo(), "app", "src", "main", "assets", "welt", "anlagen.json")):
            with open(p, "w", encoding="utf-8", newline="") as f:
                f.write(text)
        print("geschrieben:", len(text.encode("utf-8")), "Bytes")


def main():
    a = sys.argv[1:]
    if not a or a[0] not in ("bauen", "zeigen"):
        print(__doc__)
        return
    opt = {}
    for x in a[1:]:
        if x.startswith("--") and "=" in x:
            k, v = x[2:].split("=", 1)
            opt[k] = v
    bauen(opt["myb"], a[0] == "bauen")


if __name__ == "__main__":
    main()
