#!/usr/bin/env python3
# Baut app/src/main/assets/welt/damodaran.json: Branchen-KGV der USA (Jahreswerte) und die implizite Aktien-Risikopraemie.
#
# Quelle und Bedingungen (am 14.11.2026 auf https://pages.stern.nyu.edu/~adamodar/New_Home_Page/datahistory.html gelesen,
# Abschnitt "Usage Rules"): Aswath Damodaran (NYU Stern) will, dass die Daten breit genutzt werden, und macht kaum Auflagen;
# eine Nennung der Seite ist freiwillig und wird mit Dank erwartet. Firmendaten einzelner Unternehmen werden nicht
# wiedergegeben, nur Branchendurchschnitte und der Marktwert der Risikopraemie.
#   - Branchen: pedata.xls (Stand Januar 2026) und die Archivdateien archives/pedataJJ.xls (Januar JJ+1), Kennzahl
#     "Aggregate Mkt Cap / Trailing Net Income (only money making firms)": Marktwert aller Firmen der Branche durch ihren
#     Gewinn der letzten zwoelf Monate, nur Firmen mit Gewinn (ein gewichtetes KGV). Gleich definiert ab Januar 2016 (JJ = 15).
#   - Risikopraemie: histimpl.xls, "Implied ERP (FCFE)" je Jahresende des S&P 500, ab 1961.
#
# Aufruf:
#   python tools/daten/damodaran.py holen <Ordner>                      laedt pedata.xls, pedata15..24.xls, histimpl.xls
#   python tools/daten/damodaran.py bauen <Ordner> --stand=JJJJ-MM-TT   liest sie und schreibt die Datei
# Danach: python tools/daten/manifest.py bauen   (nicht signieren)
#
# Pflege: Anfang Januar erscheint die neue pedata.xls; die alte wandert ins Archiv (pedataJJ.xls mit dem Vorjahr). Einmal im
# Jahr "holen" (JAHRE unten um das neue Jahr erweitern) und "bauen". Aendern sich Spaltennamen, bricht "bauen" ab.
#
# Schnittstelle der Datei:
#   erp = {"a": 1961, "w": [Prozent je Jahr, null bei Luecke], "r": [Rendite 10-jaehriger US-Staatsanleihen in Prozent]}
#   jahre = [2015, ..., 2025] (Stand am Jahresende; die Datei vom Januar des Folgejahres)
#   b = {Branchenname: {"w": [KGV je Jahr in jahre, null wenn die Branche fehlte oder kein Gewinn], "n": Zahl Firmen im letzten Jahr}}
#   z = {Yahoo-Branche: Branchenname}  Zuordnung der Branchen der Wertpapierseite

import datetime
import json
import os
import re
import sys
import time
import urllib.request

KENNUNG = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
BASIS = "https://pages.stern.nyu.edu/~adamodar/pc/"
JAHRE = list(range(2015, 2026))  # 2025 = pedata.xls (Januar 2026), davor pedataJJ.xls
GESAMT = "Total Market"


def datei_jahr(jahr):
    return "pedata.xls" if jahr == JAHRE[-1] else "pedata%02d.xls" % (jahr % 100)


def url_jahr(jahr):
    return BASIS + ("datasets/pedata.xls" if jahr == JAHRE[-1] else "archives/pedata%02d.xls" % (jahr % 100))


def repo():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def holen_eine(url, ziel):
    r = urllib.request.Request(url, headers={"User-Agent": KENNUNG, "Accept": "*/*"})
    with urllib.request.urlopen(r, timeout=300) as a:
        b = a.read()
    with open(ziel, "wb") as f:
        f.write(b)
    print("%s: %d Bytes" % (os.path.basename(ziel), len(b)))


def holen(ordner):
    os.makedirs(ordner, exist_ok=True)
    for j in JAHRE:
        holen_eine(url_jahr(j), os.path.join(ordner, datei_jahr(j)))
        time.sleep(1)
    holen_eine(BASIS + "datasets/histimpl.xls", os.path.join(ordner, "histimpl.xls"))


def norm(s):
    return re.sub(r"\s+", " ", str(s)).strip()


def branchen_lesen(pfad, jahr):
    import xlrd

    wb = xlrd.open_workbook(pfad)
    sh = wb.sheet_by_name("Industry Averages") if "Industry Averages" in wb.sheet_names() else wb.sheet_by_index(0)
    kopf = None
    spalte = None
    aus = {}
    for r in range(sh.nrows):
        v = sh.row_values(r)
        if norm(v[0]) == "Industry Name":
            kopf = [norm(x).lower() for x in v]
            spalte = next((i for i, x in enumerate(kopf) if "aggregate" in x and "trailing" in x), None)
            anzahl = next((i for i, x in enumerate(kopf) if x.startswith("number of firms")), None)
            if spalte is None or anzahl is None:
                raise SystemExit("%s: Spalten nicht gefunden: %s" % (pfad, kopf))
            continue
        if kopf is None or not isinstance(v[1], float):
            continue
        w = v[spalte]
        aus[norm(v[0])] = (round(w, 1) if isinstance(w, float) and w > 0 else None, int(v[anzahl]))
    # Plausibilitaet: Datum der Datei liegt im Januar des Folgejahres
    for r in range(min(8, sh.nrows)):
        v = sh.row_values(r)
        if norm(v[0]).startswith("Date updated") and isinstance(v[1], float):
            d = xlrd.xldate_as_tuple(v[1], wb.datemode)
            if d[0] != jahr + 1:
                raise SystemExit("%s: Stand %s passt nicht zum Jahr %d" % (pfad, d[:3], jahr))
    if len(aus) < 90 or GESAMT not in aus:
        raise SystemExit("%s: zu wenige Branchen" % pfad)
    return aus


def risikopraemie(pfad):
    import xlrd

    sh = xlrd.open_workbook(pfad).sheet_by_index(0)
    kopf = next(r for r in range(sh.nrows) if norm(sh.cell_value(r, 0)) == "Year")
    k = [norm(x) for x in sh.row_values(kopf)]
    if k[15] != "Implied ERP (FCFE)" or k[10] != "T.Bond Rate":
        raise SystemExit("histimpl.xls: Spalten verschoben: %s" % k)
    je = {}
    for r in range(kopf + 1, sh.nrows):
        v = sh.row_values(r)
        if isinstance(v[0], float) and isinstance(v[15], float):
            je[int(v[0])] = (round(v[15] * 100, 2), round(v[10] * 100, 2) if isinstance(v[10], float) else None)
    erster, letzter = min(je), max(je)
    if letzter < 2025:
        raise SystemExit("histimpl.xls endet vor 2025")
    return {
        "a": erster,
        "w": [je[j][0] if j in je else None for j in range(erster, letzter + 1)],
        "r": [je[j][1] if j in je else None for j in range(erster, letzter + 1)],
    }


# Branchen der Wertpapierseite (Yahoo) zu den Branchen von Damodaran. Nicht aufgefuehrte Branchen bekommen keinen Vergleich.
ZUORDNUNG = {
    "Agricultural Inputs": "Chemical (Basic)", "Aluminum": "Metals & Mining", "Building Materials": "Building Materials",
    "Chemicals": "Chemical (Basic)", "Coking Coal": "Coal & Related Energy", "Copper": "Metals & Mining",
    "Gold": "Precious Metals", "Lumber & Wood Production": "Paper/Forest Products",
    "Other Industrial Metals & Mining": "Metals & Mining", "Other Precious Metals & Mining": "Precious Metals",
    "Paper & Paper Products": "Paper/Forest Products", "Silver": "Precious Metals", "Specialty Chemicals": "Chemical (Specialty)",
    "Steel": "Steel",
    "Advertising Agencies": "Advertising", "Broadcasting": "Broadcasting", "Electronic Gaming & Multimedia": "Software (Entertainment)",
    "Entertainment": "Entertainment", "Internet Content & Information": "Software (Internet)", "Publishing": "Publishing & Newspapers",
    "Telecom Services": "Telecom. Services",
    "Apparel Manufacturing": "Apparel", "Apparel Retail": "Retail (Special Lines)", "Auto & Truck Dealerships": "Retail (Automotive)",
    "Auto Manufacturers": "Auto & Truck", "Auto Parts": "Auto Parts", "Department Stores": "Retail (General)",
    "Footwear & Accessories": "Shoe", "Furnishings, Fixtures & Appliances": "Furn/Home Furnishings", "Gambling": "Hotel/Gaming",
    "Home Improvement Retail": "Retail (Building Supply)", "Internet Retail": "Retail (General)", "Leisure": "Recreation",
    "Lodging": "Hotel/Gaming", "Luxury Goods": "Apparel", "Packaging & Containers": "Packaging & Container",
    "Personal Services": "Business & Consumer Services", "Recreational Vehicles": "Recreation", "Residential Construction": "Homebuilding",
    "Resorts & Casinos": "Hotel/Gaming", "Restaurants": "Restaurant/Dining", "Specialty Retail": "Retail (Special Lines)",
    "Textile Manufacturing": "Apparel", "Travel Services": "Recreation",
    "Beverages - Brewers": "Beverage (Alcoholic)", "Beverages - Non-Alcoholic": "Beverage (Soft)",
    "Beverages - Wineries & Distilleries": "Beverage (Alcoholic)", "Confectioners": "Food Processing", "Discount Stores": "Retail (General)",
    "Education & Training Services": "Education", "Farm Products": "Farming/Agriculture", "Food Distribution": "Food Wholesalers",
    "Grocery Stores": "Retail (Grocery and Food)", "Household & Personal Products": "Household Products", "Packaged Foods": "Food Processing",
    "Tobacco": "Tobacco",
    "Oil & Gas Drilling": "Oilfield Svcs/Equip.", "Oil & Gas E&P": "Oil/Gas (Production and Exploration)",
    "Oil & Gas Equipment & Services": "Oilfield Svcs/Equip.", "Oil & Gas Integrated": "Oil/Gas (Integrated)",
    "Oil & Gas Midstream": "Oil/Gas Distribution", "Oil & Gas Refining & Marketing": "Oil/Gas (Integrated)",
    "Thermal Coal": "Coal & Related Energy", "Uranium": "Metals & Mining",
    "Asset Management": "Investments & Asset Management", "Banks - Diversified": "Bank (Money Center)", "Banks - Regional": "Banks (Regional)",
    "Capital Markets": "Brokerage & Investment Banking", "Credit Services": "Financial Svcs. (Non-bank & Insurance)",
    "Financial Conglomerates": "Financial Svcs. (Non-bank & Insurance)", "Financial Data & Stock Exchanges": "Information Services",
    "Insurance Brokers": "Insurance (General)", "Insurance - Diversified": "Insurance (General)", "Insurance - Life": "Insurance (Life)",
    "Insurance - Property & Casualty": "Insurance (Prop/Cas.)", "Insurance - Reinsurance": "Reinsurance",
    "Insurance - Specialty": "Insurance (Prop/Cas.)", "Mortgage Finance": "Financial Svcs. (Non-bank & Insurance)",
    "Biotechnology": "Drugs (Biotechnology)", "Diagnostics & Research": "Healthcare Products",
    "Drug Manufacturers - General": "Drugs (Pharmaceutical)", "Drug Manufacturers - Specialty & Generic": "Drugs (Pharmaceutical)",
    "Health Information Services": "Heathcare Information and Technology", "Healthcare Plans": "Healthcare Support Services",
    "Medical Care Facilities": "Hospitals/Healthcare Facilities", "Medical Devices": "Healthcare Products",
    "Medical Distribution": "Healthcare Support Services", "Medical Instruments & Supplies": "Healthcare Products",
    "Pharmaceutical Retailers": "Retail (Grocery and Food)",
    "Aerospace & Defense": "Aerospace/Defense", "Airlines": "Air Transport", "Airports & Air Services": "Transportation",
    "Building Products & Equipment": "Construction Supplies", "Business Equipment & Supplies": "Office Equipment & Services",
    "Conglomerates": "Diversified", "Consulting Services": "Business & Consumer Services", "Electrical Equipment & Parts": "Electrical Equipment",
    "Engineering & Construction": "Engineering/Construction", "Farm & Heavy Construction Machinery": "Machinery",
    "Industrial Distribution": "Retail (Distributors)", "Infrastructure Operations": "Transportation",
    "Integrated Freight & Logistics": "Transportation", "Marine Shipping": "Shipbuilding & Marine", "Metal Fabrication": "Machinery",
    "Pollution & Treatment Controls": "Environmental & Waste Services", "Railroads": "Transportation (Railroads)",
    "Rental & Leasing Services": "Business & Consumer Services", "Security & Protection Services": "Business & Consumer Services",
    "Specialty Business Services": "Business & Consumer Services", "Specialty Industrial Machinery": "Machinery",
    "Staffing & Employment Services": "Business & Consumer Services", "Tools & Accessories": "Machinery", "Trucking": "Trucking",
    "Waste Management": "Environmental & Waste Services",
    "Real Estate - Development": "Real Estate (Development)", "Real Estate - Diversified": "Real Estate (General/Diversified)",
    "Real Estate Services": "Real Estate (Operations & Services)", "REIT - Diversified": "R.E.I.T.", "REIT - Healthcare Facilities": "R.E.I.T.",
    "REIT - Hotel & Motel": "R.E.I.T.", "REIT - Industrial": "R.E.I.T.", "REIT - Mortgage": "R.E.I.T.", "REIT - Office": "R.E.I.T.",
    "REIT - Residential": "R.E.I.T.", "REIT - Retail": "Retail (REITs)", "REIT - Specialty": "R.E.I.T.",
    "Communication Equipment": "Telecom. Equipment", "Computer Hardware": "Computers/Peripherals",
    "Consumer Electronics": "Computers/Peripherals", "Electronic Components": "Electronics (General)",
    "Electronics & Computer Distribution": "Retail (Distributors)", "Information Technology Services": "Computer Services",
    "Scientific & Technical Instruments": "Electronics (General)", "Semiconductor Equipment & Materials": "Semiconductor Equip",
    "Semiconductors": "Semiconductor", "Software - Application": "Software (System & Application)",
    "Software - Infrastructure": "Software (System & Application)", "Solar": "Green & Renewable Energy",
    "Utilities - Diversified": "Utility (General)", "Utilities - Independent Power Producers": "Power",
    "Utilities - Regulated Electric": "Utility (General)", "Utilities - Regulated Gas": "Utility (General)",
    "Utilities - Regulated Water": "Utility (Water)", "Utilities - Renewable": "Green & Renewable Energy",
}


def bauen(ordner, stand):
    je_jahr = {j: branchen_lesen(os.path.join(ordner, datei_jahr(j)), j) for j in JAHRE}
    neu = je_jahr[JAHRE[-1]]
    fehlt = sorted({z for z in ZUORDNUNG.values()} - set(neu))
    if fehlt:
        raise SystemExit("Branchen von Damodaran fehlen in der aktuellen Datei: %s" % fehlt)
    b = {}
    for name in sorted(neu):
        w = [je_jahr[j][name][0] if name in je_jahr[j] else None for j in JAHRE]
        if sum(1 for x in w if x is not None) >= 3:
            b[name] = {"w": w, "n": neu[name][1]}
    out = {
        "stand": stand, "schema": 1,
        "quelle": {
            "name": "Aswath Damodaran, NYU Stern", "url": "https://pages.stern.nyu.edu/~adamodar/New_Home_Page/datahistory.html",
            "bedingungen": "https://pages.stern.nyu.edu/~adamodar/New_Home_Page/datahistory.html#rules",
        },
        "erp": risikopraemie(os.path.join(ordner, "histimpl.xls")),
        "jahre": JAHRE,
        "gesamt": GESAMT,
        "b": b,
        "z": {k: v for k, v in sorted(ZUORDNUNG.items()) if v in b and b[v]["w"][-1] is not None},
    }
    ziel = os.path.join(repo(), "app", "src", "main", "assets", "welt", "damodaran.json")
    with open(ziel, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    print("%s: %d Bytes, %d Branchen, %d Zuordnungen, Risikopraemie %d bis %d" % (
        ziel, os.path.getsize(ziel), len(b), len(out["z"]), out["erp"]["a"], out["erp"]["a"] + len(out["erp"]["w"]) - 1))


def main():
    if len(sys.argv) < 3 or sys.argv[1] not in ("holen", "bauen"):
        raise SystemExit("Aufruf: damodaran.py holen <Ordner> | damodaran.py bauen <Ordner> [--stand=JJJJ-MM-TT]")
    if sys.argv[1] == "holen":
        holen(sys.argv[2])
        return
    stand = next((a.split("=", 1)[1] for a in sys.argv[3:] if a.startswith("--stand=")), datetime.date.today().isoformat())
    bauen(sys.argv[2], stand)


if __name__ == "__main__":
    main()
