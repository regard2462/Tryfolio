# -*- coding: utf-8 -*-
"""Gesamtbild aller 13F-Melder je Wertpapier (assets/copytrading/gesamt13f.json und daten/copytrading/gesamt13f.json).

Quelle sind die "Form 13F Data Sets" der SEC (je ein Zip fuer drei Monate Meldungen):
https://www.sec.gov/data-research/sec-markets-data/form-13f-data-sets
Das Skript laedt die zwei juengsten Zips in den Zwischenspeicher (nur wenn sie dort fehlen), rechnet je Wertpapier
Zahl der Halter, neue und ausgestiegene Halter, Aufstocker und Abbauer, Wert, Stueckzahl und die fuenf groessten Halter
und schreibt das Buendel.

Zaehlweise:
- Je Melder (CIK) und Stichtag gilt die juengste Meldung 13F-HR oder deren Neufassung (RESTATEMENT), dazu spaetere
  Nachmeldungen (NEW HOLDINGS). Meldungen ohne Bestandstabelle (13F-NT) zaehlen nicht.
- Nur Aktienpositionen: Stueckzahlen (SH) ohne PUT/CALL, keine Anleihen (PRN), keine Fonds (Namen mit ETF, iShares,
  SPDR, Vanguard u. a.), keine Optionsscheine, Vorzugsaktien und Anleihen nach dem Titel der Gattung.
- Zuordnung CUSIP zu Kuerzel: zuerst die Zuordnung der Anleger in copytrading/investoren.json, sonst der Name gegen
  katalog.json (nur eindeutige Treffer, nur Werte ohne Boersenkuerzel hinter dem Punkt). Der Rest bleibt draussen.
- Zu- und Abgaenge (neu, weg, auf, ab, Fluss, Aenderung) rechnen nur ueber Melder, die in beiden Quartalen gemeldet
  haben (Vergleichsgruppe); verspaetete Meldungen ausserhalb des Datensatzes verfaelschen sie so nicht. Halterzahl,
  Stueckzahl, Wert und die groessten Halter stehen fuer alle Melder des Stichtags.
- Aktiensplits zwischen den Stichtagen erkennt das Skript daran, dass die mittlere Aenderung der Stueckzahl der
  fortlaufenden Halter (mindestens acht) auf ein gaengiges Splitverhaeltnis faellt; das Vorquartal wird auf die neue Stueckelung umgerechnet.
- "cusip" am Ende des Buendels: CUSIP zu Kuerzel fuer alle Wertpapiere des Buendels.
- "x": 1 markiert Sonderfaelle (Stueckzahl der Vergleichsgruppe aendert sich um mehr als die Haelfte oder das Wertpapier
  war im Vorquartal bei kaum jemandem im Bestand, etwa Neunotierung); die App nimmt sie nicht in die Ranglisten.

Aufruf: python -I form13f_gesamt.py [<repo-wurzel>]
Der Zwischenspeicher (Zips) liegt in der Umgebungsvariable TRYFOLIO_CACHE, sonst im Temp-Ordner unter tryfolio-cache/13f.
"""
import csv
import datetime as dt
import io
import json
import os
import random
import re
import statistics
import string
import sys
import tempfile
import time
import urllib.request
import zipfile

csv.field_size_limit(1 << 24)
SEITE = "https://www.sec.gov/data-research/sec-markets-data/form-13f-data-sets"
MONATE = {m: i for i, m in enumerate(["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"], 1)}
FONDS = re.compile(
    r"\b(ETF|ETFS|ETN|ISHARES|SPDR|VANGUARD|INVESCO|PROSHARES|WISDOMTREE|VANECK|ULTRAPRO|DIREXION|GRAYSCALE|"
    r"SELECT SECTOR|GLOBAL X|AMPLIFY|ARK ETF|SCHWAB STRATEGIC|FIRST TR|FIRST TRUST|TEUCRIUM|ULTRASHORT|"
    r"DIMENSIONAL|MUTUAL FUND|INDEX FUND|FUND SHS|SECTOR SPDR)\b|\bTRUST UNITS?\b|\bQQQ\b"
)
TITEL_SPERRE = re.compile(
    r"WARRANT|\bWT\b|\bWTS\b|\bW EXP\b|\*W|\bPFD\b|PREF|NOTE|BOND|DEBENT|RIGHT|\bUNIT|\bSBI\b|DEP SH|CONV|\bPUT\b|\bCALL\b|\bETF\b|\bETN\b|DEPOSITARY SH|TR UNIT"
)
FIRMENWOERTER = {
    "INC", "INCORPORATED", "CORP", "CORPORATION", "CO", "COMPANY", "LTD", "LIMITED", "PLC", "HOLDINGS", "HOLDING",
    "HLDGS", "HLDG", "GROUP", "GRP", "THE", "NV", "SA", "AG", "SE", "LLC", "LP", "ADR", "SPONSORED", "INTL", "INTERNATIONAL",
    "CLASS", "CL", "COM", "NEW", "DE", "OF", "AND", "AMERICA", "USA", "INCORPORATION", "STK", "STOCK", "SHS", "SH",
    "ORD", "ORDINARY", "COMMON", "SHARES", "DEL", "MED", "MEDIUM", "TERM", "SPON", "ADS", "UNITS", "COMPANIES", "CMN",
    "USD", "PAR", "NATL",
}
GROSS_BLEIBT = {"LLC", "LP", "LLP", "L.P.", "PLC", "NV", "N.V.", "SA", "S.A.", "AG", "SE", "ETF", "REIT", "USA", "US", "UK", "II", "III", "IV", "AI", "NA", "N.A.", "BV", "AB", "ASA", "AS", "KG", "GMBH", "CRD", "UBS", "BNP", "HSBC", "TD", "RBC", "BMO", "JPMORGAN"}
SCHOENE = [1.25, 4 / 3, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 10.0, 12.0, 15.0, 20.0, 25.0, 30.0, 40.0, 50.0, 100.0]


def datum(s):
    """'31-JUL-2026' -> '2026-07-31'"""
    try:
        t, m, j = s.strip().split("-")
        return "%04d-%02d-%02d" % (int(j), MONATE[m.upper()], int(t))
    except Exception:  # noqa: BLE001
        return ""


def tabelle(z, name):
    with z.open(name) as f:
        yield from csv.DictReader(io.TextIOWrapper(f, encoding="utf-8", errors="replace"), delimiter="\t", quoting=csv.QUOTE_NONE)


def kennung():
    rest = "".join(random.choice(string.ascii_lowercase + string.digits) for _ in range(12))
    return "Tryfolio/0.77 %s@example.org" % rest


def holen_zips(ordner):
    """Gibt die Pfade der zwei juengsten Zips zurueck und laedt fehlende (hoechstens 5 Abrufe je Sekunde)."""
    vorhanden = sorted(f for f in os.listdir(ordner) if f.endswith("_form13f.zip"))
    if len(vorhanden) >= 2:
        return [os.path.join(ordner, f) for f in neueste(vorhanden)[:2]]
    ua = kennung()

    def abruf(url, ziel=None):
        req = urllib.request.Request(url, headers={"User-Agent": ua})
        with urllib.request.urlopen(req, timeout=600) as r:
            if ziel is None:
                return r.read().decode("utf-8", "replace")
            with open(ziel, "wb") as f:
                while True:
                    block = r.read(1 << 20)
                    if not block:
                        break
                    f.write(block)
        time.sleep(0.25)
        return None

    html = abruf(SEITE)
    links = re.findall(r'href="(/files/[^"]*?/(\d{2}[a-z]{3}\d{4}-\d{2}[a-z]{3}\d{4}_form13f\.zip))"', html)
    links = sorted(set(links), key=lambda l: zeitraum_ende(l[1]), reverse=True)[:2]
    ziele = []
    for pfad, name in links:
        ziel = os.path.join(ordner, name)
        if not os.path.exists(ziel):
            abruf("https://www.sec.gov" + pfad, ziel)
        ziele.append(ziel)
    return ziele


def zeitraum_ende(name):
    m = re.match(r"\d{2}[a-z]{3}\d{4}-(\d{2})([a-z]{3})(\d{4})_", name)
    return (int(m.group(3)), MONATE[m.group(2).upper()], int(m.group(1))) if m else (0, 0, 0)


def neueste(namen):
    return sorted(namen, key=zeitraum_ende, reverse=True)


def schluessel_name(n):
    """Firmenname ohne Rechtsform und Fuellwoerter, nur Grossbuchstaben und Ziffern."""
    n = n.upper().replace("&", " AND ")
    n = re.sub(r"\([^)]*\)", " ", n)
    woerter = [w for w in re.sub(r"[^A-Z0-9 ]", " ", n).split() if w not in FIRMENWOERTER]
    return " ".join(woerter)


def schoen(name):
    name = re.sub(r"\s+", " ", name.strip())
    if not name.isupper():
        return name
    teile = []
    for w in name.split(" "):
        if w in GROSS_BLEIBT or len(w) <= 1 or any(c.isdigit() for c in w) or "&" in w:
            teile.append(w)
        else:
            teile.append("-".join(x.capitalize() for x in w.split("-")))
    return " ".join(teile)


def woerter(n):
    """Firmenname als Woerter ohne Rechtsform und Fuellwoerter."""
    n = n.upper().replace("&", " AND ")
    n = re.sub(r"\([^)]*\)", " ", n)
    w = [x for x in re.sub(r"[^A-Z0-9 ]", " ", n).split() if x not in FIRMENWOERTER]
    while len(w) > 1 and len(w[-1]) == 1:
        w.pop()
    return w


def kurzform(kurz, lang):
    """Ist kurz eine Abkuerzung von lang? Gleich, Wortanfang (ab drei Buchstaben) oder Buchstaben in Reihenfolge mit
    gleichem ersten und letzten Buchstaben (SVCS, FINL, CDA); BK steht fuer BANK."""
    if kurz == lang:
        return True
    if kurz == "BK":
        return lang == "BANK"
    if len(kurz) < 3 or len(kurz) >= len(lang) or kurz[0] != lang[0]:
        return False
    if lang.startswith(kurz):
        return True
    if kurz[-1] != lang[-1]:
        return False
    i = 0
    for ch in lang:
        if ch == kurz[i]:
            i += 1
            if i == len(kurz):
                return True
    return False


def passt(katalog_woerter, name_woerter):
    """Gleich viele Woerter. Ein Katalogname aus einem Wort muss genau stimmen; bei mehreren stimmt das erste genau,
    die weiteren duerfen abgekuerzt sein."""
    if len(katalog_woerter) != len(name_woerter):
        return False
    if katalog_woerter[0] != name_woerter[0]:
        return False
    return all(kurzform(n, k) for k, n in zip(katalog_woerter[1:], name_woerter[1:]))


def lade_zuordnung(repo):
    """CUSIP -> Kuerzel aus den Anlegern und aus der Handliste; dazu die Katalogwerte fuer den Namensvergleich."""
    with open(os.path.join(repo, "app/src/main/assets/copytrading/investoren.json"), encoding="utf-8") as f:
        inv = json.load(f)
    cusip = {}
    for i in inv["investoren"]:
        for q in i["quartale"]:
            for p in q["positionen"]:
                if p.get("art") == "aktie" and p.get("kuerzel") and p.get("cusip"):
                    cusip[p["cusip"].strip().upper()] = p["kuerzel"]
    hand = os.path.join(repo, "tools/sec/zuordnung_hand.json")
    if os.path.exists(hand):
        with open(hand, encoding="utf-8") as f:
            for c, kz in json.load(f).items():
                if not c.startswith("_"):
                    cusip[c.strip().upper()] = kz
    with open(os.path.join(repo, "app/src/main/assets/katalog.json"), encoding="utf-8") as f:
        kat = json.load(f)
    katalog_namen = {}
    liste = []
    for a in kat["aktien"]:
        s = a["s"]
        katalog_namen[s] = a["n"]
        if "." in s or re.search(r"\([A-Z]\)\s*$", a["n"]):
            continue
        w = woerter(a["n"])
        if w:
            liste.append((s, w))
    return cusip, liste, katalog_namen


def meldungen(zips):
    """Je Zip die Kopfdaten; Rueckgabe: accession -> dict(cik, periode, datum, typ, name, eintraege)."""
    kopf = {}
    for pfad in zips:
        z = zipfile.ZipFile(pfad)
        for r in tabelle(z, "SUBMISSION.tsv"):
            kopf[r["ACCESSION_NUMBER"]] = {
                "cik": r["CIK"], "periode": datum(r["PERIODOFREPORT"]), "datum": datum(r["FILING_DATE"]), "typ": r["SUBMISSIONTYPE"],
            }
        for r in tabelle(z, "COVERPAGE.tsv"):
            k = kopf.get(r["ACCESSION_NUMBER"])
            if k is not None:
                k["name"] = r["FILINGMANAGER_NAME"]
                k["bericht"] = r["REPORTTYPE"]
                k["art"] = r["AMENDMENTTYPE"]
        for r in tabelle(z, "SUMMARYPAGE.tsv"):
            k = kopf.get(r["ACCESSION_NUMBER"])
            if k is not None:
                try:
                    k["eintraege"] = int(r["TABLEENTRYTOTAL"])
                except ValueError:
                    k["eintraege"] = 0
    return kopf


def monat_vorher(p):
    j, m, t = int(p[:4]), int(p[5:7]), int(p[8:])
    m -= 3
    if m < 1:
        m += 12
        j -= 1
    letzter = (dt.date(j + (m // 12), m % 12 + 1, 1) - dt.timedelta(days=1)).day
    return "%04d-%02d-%02d" % (j, m, letzter)


def auswahl(kopf, periode):
    """Je CIK die zu zaehlenden Accessions: juengste Meldung oder Neufassung plus spaetere Nachmeldungen."""
    je = {}
    for acc, k in kopf.items():
        if k.get("periode") != periode or k["typ"] not in ("13F-HR", "13F-HR/A"):
            continue
        if "NOTICE" in k.get("bericht", "") or k.get("eintraege", 0) <= 0:
            continue
        je.setdefault(k["cik"], []).append((k["datum"], acc, k.get("art", "")))
    ergebnis = {}
    for cik, liste in je.items():
        liste.sort()
        basis = [x for x in liste if x[2] != "NEW HOLDINGS"]
        if not basis:
            basis = liste[:1]
        b = basis[-1]
        dazu = [x for x in liste if x[2] == "NEW HOLDINGS" and x[0] >= b[0] and x[1] != b[1]]
        ergebnis[cik] = [b[1]] + [x[1] for x in dazu]
    return ergebnis


def lesen(zips, rollen, cusip_karte, katalog, statistik):
    """Liest die INFOTABLE beider Zips; rollen: accession -> 'jetzt' oder 'vorher'. Gibt je Rolle kuerzel -> cik -> [stueck, wert]."""
    daten = {"jetzt": {}, "vorher": {}}
    namen = {}
    zwischen = {}
    for pfad in zips:
        z = zipfile.ZipFile(pfad)
        for r in tabelle(z, "INFOTABLE.tsv"):
            rolle = rollen.get(r["ACCESSION_NUMBER"])
            if rolle is None:
                continue
            if r["PUTCALL"] or r["SSHPRNAMTTYPE"] != "SH":
                continue
            c = r["CUSIP"].strip().upper()
            if len(c) != 9:
                continue
            try:
                wert = float(r["VALUE"])
                stueck = float(r["SSHPRNAMT"])
            except ValueError:
                continue
            if stueck <= 0 or wert <= 0:
                continue
            kz = zwischen.get(c, FEHLT)
            if kz is FEHLT:
                kz = entscheide(c, r["NAMEOFISSUER"], r["TITLEOFCLASS"], cusip_karte, katalog)
                zwischen[c] = kz
            if kz is None:
                continue
            if rolle == "jetzt":
                statistik["wert_gesamt"] += wert
            if kz is False:
                if rolle == "jetzt":
                    statistik["nicht_zugeordnet_wert"] += wert
                    statistik["roh"].setdefault(c, [r["NAMEOFISSUER"], 0.0])[1] += wert
                continue
            cik = rollen_cik[r["ACCESSION_NUMBER"]]
            namen.setdefault(kz, r["NAMEOFISSUER"])
            e = daten[rolle].setdefault(kz, {}).setdefault(cik, [0.0, 0.0])
            e[0] += stueck
            e[1] += wert
            if rolle == "jetzt":
                statistik["zugeordnet_wert"] += wert
    return daten, namen, zwischen


rollen_cik = {}
FEHLT = object()
NAMENSTREFFER = []


def entscheide(c, name, titel, cusip_karte, katalog):
    """Kuerzel, False (Aktie ohne Zuordnung) oder None (kein Aktienwert im Sinn des Buendels: Fonds, Optionsschein usw.)."""
    if FONDS.search(name.upper()) or "ETF" in titel.upper() or TITEL_SPERRE.search(titel.upper()):
        return None
    if c in cusip_karte:
        return cusip_karte[c]
    nw = woerter(name)
    if not nw or (titel.strip().upper() == "SHS" and c[0].isdigit()):
        return False
    treffer = {s for s, kw in katalog if passt(kw, nw)}
    if len(treffer) == 1:
        NAMENSTREFFER.append((c, name, next(iter(treffer))))
        return next(iter(treffer))
    return False


def rechne(daten, melder_jetzt, melder_vorher):
    gruppe = set(melder_jetzt) & set(melder_vorher)
    ergebnis = {}
    splits = 0
    for kz, jetzt in daten["jetzt"].items():
        vorher = daten["vorher"].get(kz, {})
        faktor = 1.0
        fort = [c for c in jetzt if c in vorher and vorher[c][0] > 0]
        if len(fort) >= 8:
            verh = statistics.median(jetzt[c][0] / vorher[c][0] for c in fort)
            nah = any(abs(verh / s - 1) < 0.06 or abs(verh * s - 1) < 0.06 for s in SCHOENE)
            if nah and abs(verh - 1) > 0.2:
                faktor = verh
                splits += 1
        neu = weg = auf = ab = 0
        fluss = 0.0
        sg_jetzt = sg_vorher = 0.0
        preis = statistics.median(jetzt[c][1] / jetzt[c][0] for c in jetzt)
        for c in gruppe:
            a = jetzt.get(c)
            b = vorher.get(c)
            sj = a[0] if a else 0.0
            sv = b[0] * faktor if b else 0.0
            if sj == 0 and sv == 0:
                continue
            sg_jetzt += sj
            sg_vorher += sv
            if b is None:
                neu += 1
            elif a is None:
                weg += 1
            elif sj > sv * 1.005:
                auf += 1
            elif sj < sv * 0.995:
                ab += 1
            fluss += (sj - sv) * preis
        gross = sorted(jetzt.items(), key=lambda x: -x[1][1])[:5]
        ergebnis[kz] = {
            "h": len(jetzt), "hv": len(vorher), "neu": neu, "weg": weg, "auf": auf, "ab": ab,
            "w": round(sum(v[1] for v in jetzt.values())), "s": round(sum(v[0] for v in jetzt.values())),
            "sg": round(sg_jetzt), "sv": round(sg_vorher), "f": round(fluss),
            "_gross": [(c, v[0], v[1], (vorher[c][0] * faktor) if c in vorher else None) for c, v in gross],
        }
    return ergebnis, splits


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else os.getcwd()
    cache = os.environ.get("TRYFOLIO_CACHE") or os.path.join(tempfile.gettempdir(), "tryfolio-cache", "13f")
    os.makedirs(cache, exist_ok=True)
    zips = holen_zips(cache)
    print("Zips:", [os.path.basename(z) for z in zips])
    kopf = meldungen(zips)
    periode_zaehl = {}
    for k in kopf.values():
        if k["typ"] == "13F-HR":
            periode_zaehl[k.get("periode")] = periode_zaehl.get(k.get("periode"), 0) + 1
    jetzt_p = max(p for p, n in periode_zaehl.items() if n >= 1000)
    vorher_p = monat_vorher(jetzt_p)
    print("Stichtag", jetzt_p, "Vorquartal", vorher_p)
    a_jetzt = auswahl(kopf, jetzt_p)
    a_vorher = auswahl(kopf, vorher_p)
    rollen = {}
    for cik, accs in a_jetzt.items():
        for a in accs:
            rollen[a] = "jetzt"
            rollen_cik[a] = cik
    for cik, accs in a_vorher.items():
        for a in accs:
            rollen[a] = "vorher"
            rollen_cik[a] = cik
    cusip_karte, katalog, katalog_namen = lade_zuordnung(repo)
    statistik = {"wert_gesamt": 0.0, "zugeordnet_wert": 0.0, "nicht_zugeordnet_wert": 0.0, "roh": {}}
    daten, namen, zuordnung = lesen(zips, rollen, cusip_karte, katalog, statistik)
    # Melder mit mindestens einer Aktienposition im Buendel
    melder_jetzt = {c for d in daten["jetzt"].values() for c in d}
    melder_vorher = {c for d in daten["vorher"].values() for c in d}
    ergebnis, splits = rechne(daten, melder_jetzt, melder_vorher)
    letzte_meldung = max(kopf[a]["datum"] for a in rollen if rollen[a] == "jetzt")
    ausgabe = {}
    for kz, e in ergebnis.items():
        if e["h"] < 3:
            continue
        gross = []
        for c, s, w, sv in e.pop("_gross"):
            name = schoen(kopf[a_jetzt[c][0]].get("name", c))
            aend = None if sv is None or sv <= 0 else round((s / sv - 1) * 100, 1)
            gross.append([name, round(s), round(w), aend])
        e["t"] = gross
        if e["hv"] < 0.2 * e["h"] or (e["sv"] > 0 and abs(e["sg"] / e["sv"] - 1) > 0.5):
            e["x"] = 1
        e["n"] = katalog_namen.get(kz) or schoen(namen.get(kz, kz))
        ausgabe[kz] = e
    buendel = {
        "stichtag": jetzt_p, "vorher": vorher_p, "bis": letzte_meldung,
        "melder": len(a_jetzt), "melderVorher": len(a_vorher), "gruppe": len(set(a_jetzt) & set(a_vorher)),
        "aktien": dict(sorted(ausgabe.items())),
    }
    text = json.dumps(buendel, ensure_ascii=False, separators=(",", ":"))
    if len(text.encode("utf-8")) > 900_000:
        ausgabe = {k: v for k, v in ausgabe.items() if v["h"] >= 20}
        buendel["aktien"] = dict(sorted(ausgabe.items()))
        text = json.dumps(buendel, ensure_ascii=False, separators=(",", ":"))
    # Zuordnung CUSIP zu Kuerzel der Wertpapiere im Buendel (fuer die Suche nach beliebigen Meldern in der App)
    buendel["cusip"] = {c: kz for c, kz in sorted(zuordnung.items()) if isinstance(kz, str) and kz in ausgabe}
    text = json.dumps(buendel, ensure_ascii=False, separators=(",", ":"))
    for ziel in ("app/src/main/assets/copytrading/gesamt13f.json", "daten/copytrading/gesamt13f.json"):
        pfad = os.path.join(repo, ziel)
        os.makedirs(os.path.dirname(pfad), exist_ok=True)
        with open(pfad, "wb") as f:
            f.write(text.encode("utf-8"))
    roh = sorted(statistik["roh"].items(), key=lambda x: -x[1][1])
    print("Melder mit Bestandstabelle (Stichtag/Vorquartal/Vergleichsgruppe):", len(a_jetzt), len(a_vorher), buendel["gruppe"])
    print("Melder mit mindestens einer Aktie im Buendel:", len(melder_jetzt), len(melder_vorher))
    print("Letzte Meldung im Datensatz:", letzte_meldung)
    print("Wertpapiere im Buendel:", len(ausgabe), "erkannte Splits:", splits, "Bytes:", len(text.encode("utf-8")))
    ges = statistik["wert_gesamt"]
    print("Zuordnung nach Wert (Aktien ohne Fonds): %.1f %% von %.2f Bio. USD" % (100 * statistik["zugeordnet_wert"] / ges, ges / 1e12))
    print("Groesste nicht zugeordnete Aktien:")
    for c, (n, w) in roh[:int(os.environ.get("ANZAHL_ROH", "25"))]:
        nw = woerter(n)
        vorschlag = [s2 for s2, kw in katalog if nw and kw and kurzform(nw[0], kw[0])][:4]
        print("  ", c, n, "%.2f Mrd." % (w / 1e9), vorschlag)
    if os.environ.get("STICHPROBE"):
        random.seed(7)
        print("Namenstreffer gesamt:", len(NAMENSTREFFER))
        for c, n, kz in random.sample(NAMENSTREFFER, min(60, len(NAMENSTREFFER))):
            print("  PROBE", c, n, "->", kz, "|", katalog_namen.get(kz))
    # Quote unter den 1000 wertvollsten Aktien
    alle = {}
    for kz, d in daten["jetzt"].items():
        alle[kz] = sum(v[1] for v in d.values())
    top = sorted([(w, "z") for w in alle.values()] + [(w, "n") for _, (_, w) in roh], reverse=True)[:1000]
    print("Zugeordnet unter den 1000 wertvollsten Aktien:", sum(1 for _, t in top if t == "z"))


if __name__ == "__main__":
    main()
