#!/usr/bin/env python3
# Baut netz/zulieferer.json: Firmen, die in ihrem Jahresbericht (10-K, 20-F, 40-F) einen Grosskunden mit Namen nennen, samt
# Anteil am Umsatz, Berichtsjahr und Einreichung. Die App liest die Datei rueckwaerts: fuer Firma X erscheinen alle Zulieferer,
# die X als Kunden melden (Kante "liefert", Guete B, Link auf die Einreichung).
#
# Quelle: Financial Statement and Notes Data Sets der SEC (XBRL-Zahlen samt Dimensionen aus den Anhangsangaben, gemeinfrei):
#   https://www.sec.gov/data-research/sec-markets-data/financial-statement-notes-data-sets   (Dateien JJJJ_MM_notes.zip, je Monat)
#   https://www.sec.gov/files/company_tickers.json                                           (Kuerzel je CIK)
# Aufruf (die Rohdaten liegen ausserhalb des Repos; zwoelf aufeinanderfolgende Monate decken jeden Jahresbericht ab):
#   python tools/daten/zulieferer.py bauen --notes=<Ordner mit den Monats-Zips> --kuerzel=<company_tickers.json> [--stand=JJJJ-MM-TT]
#   python tools/daten/zulieferer.py zeigen --notes=<Ordner> --kuerzel=<Datei>        Bericht, nichts schreiben
# Danach: python tools/daten/manifest.py bauen --signieren
#
# Regeln (nichts wird geschaetzt):
#  - Nur Jahreswerte (qtrs = 4) aus 10-K, 20-F und 40-F ohne Berichtigungen, Konzernwerte ohne Beteiligungsangabe (coreg leer).
#  - Kunde: Mitglied der Dimension MajorCustomers. Anteil entweder als ausgewiesener Prozentwert (ConcentrationRiskPercentage*,
#    Bezug Umsatz, Art Kundenkonzentration, nur die Achsen MajorCustomers, ConcentrationRiskByBenchmark, ConcentrationRiskByType)
#    oder als Umsatz mit dem Kunden geteilt durch den Gesamtumsatz derselben Einreichung, desselben Stichtags und derselben Kennzahl
#    (Kundenmitglied als einzige Dimension). Der ausgewiesene Wert hat Vorrang. Anteile unter 1 % oder ueber 100 % zaehlen nicht.
#  - Namenlose Kunden (Kunde A, Distributor 2, Government, Segmente wie "Industrial") fallen weg; die App gleicht die uebrigen
#    Namen streng mit Firmen des Netzes ab und laesst Mehrdeutiges weg.
#  - Je Zulieferer und Kunde zaehlt der juengste Stichtag (nicht aelter als drei Jahre).

import datetime
import json
import os
import re
import sys
import zipfile

UMSATZ_KENNZAHLEN = [
    "RevenueFromContractWithCustomerExcludingAssessedTax", "RevenueFromContractWithCustomerIncludingAssessedTax", "Revenues",
    "Revenue", "SalesRevenueNet", "SalesRevenueGoodsNet", "SalesRevenueServicesNet", "RevenueFromContractsWithCustomers",
    "OperatingRevenue", "RegulatedAndUnregulatedOperatingRevenue",
]
FORMEN = {"10-K", "20-F", "40-F"}
ACHSEN_PROZENT = {"MajorCustomers", "ConcentrationRiskByBenchmark", "ConcentrationRiskByType"}
BEZUG_UMSATZ = re.compile(r"(?i)revenue|sales")
BEZUG_NICHT = re.compile(r"(?i)receivable|asset|invest|purchase|cost|payable|expense")

ANONYM = re.compile(
    r"(?i)^(customer|client|distributor|reseller|vendor|partner|licensee|collaborator|company|entity|party|retailer|dealer|payor|payer|oem|agent)s?"
    r"[ _-]*([a-z0-9]{1,3}|one|two|three|four|five|six|seven|eight|nine|ten)?$",
)
ALLGEMEIN = {
    "customer", "customers", "client", "clients", "distributor", "distributors", "reseller", "resellers", "vendor", "vendors",
    "government", "governments", "federal", "other", "others", "all", "total", "related", "party", "parties", "largest", "major",
    "top", "five", "four", "three", "two", "one", "ten", "single", "individual", "direct", "indirect", "commercial", "industrial",
    "residential", "retail", "wholesale", "channel", "channels", "domestic", "foreign", "export", "product", "products", "segment",
    "segments", "utility", "utilities", "construction", "military", "consumer", "consumers", "oem", "oems", "agency", "department",
    "ministry", "medicare", "medicaid", "patients", "insurance", "payors", "payers", "hospital", "hospitals", "licensee",
    "collaboration", "affiliate", "affiliates", "subsidiary", "unrelated", "external", "internal", "intersegment", "eliminations",
    "eliminated", "unallocated", "corporate", "geographic", "region", "regions", "asia", "europe", "americas", "pacific", "revenue",
    "revenues", "sales", "concentration", "group", "groups", "category", "categories", "type", "types", "class", "classes",
}
FORMWORTE = {"inc", "incorporated", "corp", "corporation", "co", "company", "ltd", "limited", "plc", "nv", "se", "ag", "sa", "llc", "lp", "the"}


def name_aus_mitglied(m):
    s = m.strip()
    if s.endswith("Member"):
        s = s[: -len("Member")]
    s = re.sub(r"(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])", " ", s)
    s = s.replace("_", " ").replace("  ", " ").strip()
    return s


def schluessel(name):
    w = [x for x in re.sub(r"[^a-z0-9 ]", " ", name.lower().replace("&", " and ")).split(" ") if x]
    while len(w) > 1 and w[-1] in FORMWORTE:
        w.pop()
    return " ".join(w)


def brauchbar(name):
    if len(name) < 3 or len(name) > 60:
        return False
    if ANONYM.match(name.replace(" ", "")) or ANONYM.match(name):
        return False
    worte = [w.lower() for w in re.findall(r"[A-Za-z]+", name)]
    if not worte or any(w in ALLGEMEIN for w in worte):
        return False
    if re.fullmatch(r"[A-Za-z]\d*", name.replace(" ", "")):
        return False
    # Zahlenkolonnen (Objekt- und Standortnummern), Sammelnennungen mehrerer Kunden und Zusaetze in der Bezeichnung
    if re.search(r"\d{2,}", name) or len(name.split()) > 6 or re.search(r"(?i)formerly|affiliatesof|and other", name):
        return False
    return True


def tag_ist_prozent(tag):
    return tag.startswith("ConcentrationRiskPercentage")


def achsen(segmente):
    r = {}
    for teil in segmente.split(";"):
        if "=" in teil:
            a, m = teil.split("=", 1)
            r[a] = m
    return r


def tag_umsatz(t):
    return t in UMSATZ_KENNZAHLEN


def zeilen_lesen(z, name):
    with z.open(name) as f:
        kopf = f.readline().decode("utf-8").rstrip("\n").split("\t")
        for l in f:
            yield kopf, l.decode("utf-8", "replace").rstrip("\n").split("\t")


def lesen_monat(pfad, funde):
    z = zipfile.ZipFile(pfad)
    sub = {}
    ix = None
    for kopf, p in zeilen_lesen(z, "sub.tsv"):
        if ix is None:
            ix = {n: i for i, n in enumerate(kopf)}
        if len(p) < len(kopf) or p[ix["form"]] not in FORMEN:
            continue
        sub[p[ix["adsh"]]] = (p[ix["cik"]].lstrip("0"), p[ix["name"]], p[ix["form"]], p[ix["fy"]], p[ix["filed"]])
    dims = {}
    for kopf, p in zeilen_lesen(z, "dim.tsv"):
        if len(p) >= 2 and "MajorCustomers=" in p[1]:
            dims[p[0]] = p[1]
    totals = {}
    umsatz = []
    prozent = []
    ix = None
    for kopf, p in zeilen_lesen(z, "num.tsv"):
        if ix is None:
            ix = {n: i for i, n in enumerate(kopf)}
            ia, it, id_, iq, iu, ih, ic, iv = (ix[n] for n in ("adsh", "tag", "ddate", "qtrs", "uom", "dimh", "coreg", "value"))
        if len(p) <= iv or p[iq] != "4" or p[ic] != "" or p[iv] == "" or p[ia] not in sub:
            continue
        tag = p[it]
        dimh = p[ih]
        try:
            wert = float(p[iv])
        except ValueError:
            continue
        if tag_ist_prozent(tag):
            if p[iu] == "pure" and dimh in dims:
                prozent.append((p[ia], p[id_], dims[dimh], wert))
        elif tag_umsatz(tag) and p[iu] == "USD":
            if dimh == "0x00000000":
                totals[(p[ia], p[id_], tag)] = wert
            elif dimh in dims and dims[dimh].count("=") == 1:
                umsatz.append((p[ia], p[id_], tag, dims[dimh][len("MajorCustomers="):].rstrip(";"), wert))
    for adsh, dd, seg, wert in prozent:
        a = achsen(seg)
        if not set(a) <= ACHSEN_PROZENT:
            continue
        if "ConcentrationRiskByType" in a and "Customer" not in a["ConcentrationRiskByType"]:
            continue
        b = a.get("ConcentrationRiskByBenchmark", "")
        if not BEZUG_UMSATZ.search(b) or BEZUG_NICHT.search(b):
            continue
        if not 0.01 <= wert <= 1.0:
            continue
        funde.append((sub[adsh], adsh, dd, a["MajorCustomers"], wert, True))
    for adsh, dd, tag, m, wert in umsatz:
        gesamt = totals.get((adsh, dd, tag))
        if not gesamt or gesamt <= 0 or wert <= 0:
            continue
        anteil = wert / gesamt
        if not 0.01 <= anteil <= 1.0:
            continue
        funde.append((sub[adsh], adsh, dd, m, anteil, False))


def tag_datum(d):
    return "%s-%s-%s" % (d[0:4], d[4:6], d[6:8])


def bauen(ordner, kuerzel, stand, schreiben):
    funde = []
    zips = sorted(f for f in os.listdir(ordner) if re.fullmatch(r"(n)?\d{4}_\d{2}(_notes)?\.zip", f))
    if not zips:
        raise SystemExit("keine Monats-Zips in " + ordner)
    for n in zips:
        lesen_monat(os.path.join(ordner, n), funde)
    ticker = {}
    with open(kuerzel, "rb") as f:
        for e in json.loads(f.read().decode("utf-8")).values():
            ticker.setdefault(str(e["cik_str"]), e["ticker"])
    grenze = (datetime.date.fromisoformat(stand) - datetime.timedelta(days=3 * 366)).isoformat()
    beste = {}
    for (cik, name, form, fy, filed), adsh, dd, m, anteil, ausgewiesen in funde:
        kunde = name_aus_mitglied(m)
        if not brauchbar(kunde):
            continue
        sk = schluessel(kunde)
        if not sk or sk == schluessel(name):
            continue
        datum = tag_datum(dd)
        if datum < grenze or datum > stand:
            continue
        rang = (datum, filed, ausgewiesen, anteil)
        k = (cik, sk)
        if k not in beste or rang > beste[k][0]:
            beste[k] = (rang, cik, name, kunde, round(anteil * 100.0, 1), datum, adsh, form, fy)
    zeilen = []
    for (cik, sk), (rang, cik_, name, kunde, pct, datum, adsh, form, fy) in beste.items():
        if pct < 1.0:
            continue
        zeilen.append([cik, re.sub(r"\s*/[A-Za-z]{2,3}/?$", "", name).strip(), ticker.get(cik, ""), kunde, pct, datum, adsh, form, fy])
    zeilen.sort(key=lambda z: (schluessel(z[3]), -z[4], z[0]))
    firmen = len({z[0] for z in zeilen})
    kunden = len({schluessel(z[3]) for z in zeilen})
    print("%d Zeilen, %d Zulieferer, %d verschiedene Kunden, Stand %s" % (len(zeilen), firmen, kunden, stand))
    if not schreiben:
        for z in zeilen[:25]:
            print(z)
        return
    text = '{"schema":1,"stand":"%s","zeilen":[\n' % stand + ",\n".join(json.dumps(z, ensure_ascii=False, separators=(",", ":")) for z in zeilen) + "\n]}\n"
    ziel = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "assets", "netz", "zulieferer.json")
    with open(ziel, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print("geschrieben:", os.path.normpath(ziel), len(text.encode("utf-8")), "Bytes")


if __name__ == "__main__":
    befehl = sys.argv[1] if len(sys.argv) > 1 else ""
    opt = dict(a[2:].split("=", 1) for a in sys.argv[2:] if a.startswith("--") and "=" in a)
    if befehl not in ("bauen", "zeigen") or "notes" not in opt or "kuerzel" not in opt:
        raise SystemExit(__doc__)
    bauen(opt["notes"], opt["kuerzel"], opt.get("stand", datetime.date.today().isoformat()), befehl == "bauen")
