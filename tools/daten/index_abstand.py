#!/usr/bin/env python3
# Baut etf-abstand.json (app/src/main/assets/): Abstand der Rendite von US-Indexfonds zu ihrem Index je Geschaeftsjahr.
#
# Quelle: Form N-CEN Data Sets der SEC, je Quartal ein Zip mit Tabellen (TSV), seit 2018 Q3:
#   https://www.sec.gov/data-research/sec-markets-data/form-n-cen-data-sets
# Die SEC liefert nur an Abrufe mit Kontaktangabe in der Kennung; die Zips koennen auch im Browser geladen werden.
#
# Gelesen wird:
#   SUBMISSION.tsv           ACCESSION_NUMBER, SUBMISSION_TYPE, CIK, FILING_DATE, REPORT_ENDING_PERIOD, IS_REPORT_PERIOD_LT_12MONTH
#   FUND_REPORTED_INFO.tsv   Item C.3.b: IS_INDEX, RETURN_B4_FEES_AND_EXPENSES und RETURN_AFTR_FEES_AND_EXPENSES (Rendite des Fonds
#                            minus Rendite des Index, aufs Jahr gerechnet, in Prozentpunkten, vor und nach Kosten des Fonds),
#                            STDV_AFTR_FEES_AND_EXPENSES (Schwankung des taeglichen Abstands)
#   ETF.tsv                  dieselben Angaben fuer ETFs in Form eines Unit Investment Trust (ANNUAL_DIFF_...)
#   SECURITY_EXCHANGE.tsv    Boersenkuerzel je Fonds
# Den Namen des Index nennt das Formular nicht. Gehebelte und inverse Fonds (IS_MULTI_INVERSE_INDEX) und Berichte ueber
# weniger als zwoelf Monate bleiben weg. Je Fonds und Geschaeftsjahr zaehlt die zuletzt eingereichte Meldung.
#
# Die Datei traegt je Fonds mit Boersenkuerzel die Werte je Jahr (Hundertstel Prozentpunkte) und fuer alle Indexfonds
# zusammen je Jahr die Verteilung des Abstands nach Kosten (auch Fonds ohne Kuerzel).
#
# Aufruf: python tools/daten/index_abstand.py <Ordner mit den Zips> [JJJJ-MM-TT Stand]
# Danach: python tools/daten/manifest.py bauen --signieren
# Pflege: einmal im Jahr (die meisten Geschaeftsjahre enden im Dezember, die Meldungen stehen im Zip des zweiten Quartals).

import csv
import datetime
import glob
import io
import json
import os
import re
import sys
import zipfile

csv.field_size_limit(1 << 24)
MONATE = {m: i for i, m in enumerate(["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"], 1)}
# Grenzen der Verteilung in Prozentpunkten (Abstand nach Kosten); Klassen: unter -2, -2 bis -1, ... , ueber 1
GRENZEN = [-2.0, -1.0, -0.5, -0.25, -0.1, 0.0, 0.1, 0.25, 0.5, 1.0]
# Meldungen mit Abstaenden jenseits dieser Grenze oder mit mehr als MAX_KOSTEN Punkten zwischen "vor" und "nach Kosten"
# gelten als Eingabefehler (manche Fonds tragen ihre Rendite statt des Abstands ein)
MAX_ABSTAND = 15.0
MAX_KOSTEN = 5.0
KUERZEL = re.compile(r"^[A-Z]{1,5}$")


def repo():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def tabelle(z, name):
    treffer = [n for n in z.namelist() if n.upper().endswith(name)]
    if not treffer:
        return
    with z.open(treffer[0]) as f:
        yield from csv.DictReader(io.TextIOWrapper(f, encoding="utf-8", errors="replace"), delimiter="\t", quoting=csv.QUOTE_NONE)


def datum(s):
    # '31-DEC-2025' oder '2025-12-31' -> (2025, 12, 31)
    s = (s or "").strip()
    m = re.fullmatch(r"(\d{1,2})-([A-Za-z]{3})-(\d{4})", s)
    if m and m.group(2).upper() in MONATE:
        return int(m.group(3)), MONATE[m.group(2).upper()], int(m.group(1))
    m = re.fullmatch(r"(\d{4})-(\d{2})-(\d{2})", s)
    if m:
        return int(m.group(1)), int(m.group(2)), int(m.group(3))
    return None


def zahl(s):
    try:
        v = float((s or "").strip())
    except ValueError:
        return None
    return v if v == v and abs(v) != float("inf") else None


def lesen(pfad, funde):
    """funde: Schluessel (Series-ID oder CIK+Name) -> {"name", "kuerzel": set, "jahre": {(jahr, monat): (eingereicht, vor, nach, stdv)}}"""
    with zipfile.ZipFile(pfad) as z:
        meldung = {}
        for r in tabelle(z, "SUBMISSION.TSV"):
            ende = datum(r.get("REPORT_ENDING_PERIOD"))
            eingereicht = datum(r.get("FILING_DATE"))
            if ende is None or eingereicht is None or not (r.get("SUBMISSION_TYPE") or "").startswith("N-CEN"):
                continue
            meldung[r["ACCESSION_NUMBER"]] = (ende, eingereicht, (r.get("IS_REPORT_PERIOD_LT_12MONTH") or "").strip().upper() == "Y", r.get("CIK", ""))
        kuerzel = {}
        for r in tabelle(z, "SECURITY_EXCHANGE.TSV"):
            t = (r.get("FUND_TICKER_SYMBOL") or "").strip().upper()
            if KUERZEL.match(t):
                kuerzel.setdefault(r["FUND_ID"], set()).add(t)

        def merken(schluessel, name, fund_id, acc, vor, nach, stdv):
            m = meldung.get(acc)
            if m is None or m[2] or nach is None or abs(nach) > MAX_ABSTAND:
                return
            if vor is not None and (abs(vor) > MAX_ABSTAND or abs(vor - nach) > MAX_KOSTEN):
                return
            f = funde.setdefault(schluessel, {"name": name, "kuerzel": set(), "jahre": {}, "neu": (0, 0, 0)})
            if m[1] >= f["neu"]:
                f["neu"] = m[1]
                f["name"] = name
                if kuerzel.get(fund_id):
                    f["kuerzel"] = set(kuerzel[fund_id])
            j = (m[0][0], m[0][1])
            alt = f["jahre"].get(j)
            if alt is None or alt[0] <= m[1]:
                f["jahre"][j] = (m[1], vor, nach, stdv)

        for r in tabelle(z, "FUND_REPORTED_INFO.TSV"):
            if (r.get("IS_INDEX") or "").strip().upper() != "Y" or (r.get("IS_MULTI_INVERSE_INDEX") or "").strip().upper() == "Y":
                continue
            acc = r["ACCESSION_NUMBER"]
            name = " ".join((r.get("FUND_NAME") or "").split())
            serie = (r.get("SERIES_ID") or "").strip().upper()
            schluessel = serie if re.fullmatch(r"S\d{9}", serie) else (meldung.get(acc, (0, 0, 0, ""))[3] + "|" + name.upper())
            merken(schluessel, name, r["FUND_ID"], acc, zahl(r.get("RETURN_B4_FEES_AND_EXPENSES")), zahl(r.get("RETURN_AFTR_FEES_AND_EXPENSES")), zahl(r.get("STDV_AFTR_FEES_AND_EXPENSES")))
        # ETFs als Unit Investment Trust: ohne Series-ID und ohne Fondsnamen, FUND_ID beginnt mit der Meldung; der Name
        # steht beim Registranten, das Kuerzel unter der Meldung
        registrant = {r["ACCESSION_NUMBER"]: " ".join((r.get("REGISTRANT_NAME") or "").split()) for r in tabelle(z, "REGISTRANT.TSV")}
        for r in tabelle(z, "ETF.TSV"):
            acc = r["FUND_ID"].split("_")[0]
            if acc not in meldung:
                continue
            name = " ".join((r.get("FUND_NAME") or "").split()) or registrant.get(acc, "")
            serie = (r.get("SERIES_ID") or "").strip().upper()
            schluessel = serie if re.fullmatch(r"S\d{9}", serie) else ("U" + meldung[acc][3])
            if not name:
                continue
            merken(schluessel, name, r["FUND_ID"] if r["FUND_ID"] in kuerzel else acc, acc, zahl(r.get("ANNUAL_DIFF_B4_FEE_EXPENSE")), zahl(r.get("ANNUAL_DIFF_AFTER_FEE_EXPENSE")), zahl(r.get("ANNUAL_STDV_AFTER_FEE_EXPENSE")))


def klasse(v):
    for i, g in enumerate(GRENZEN):
        if v < g:
            return i
    return len(GRENZEN)


def median(werte):
    s = sorted(werte)
    n = len(s)
    return s[n // 2] if n % 2 else (s[n // 2 - 1] + s[n // 2]) / 2.0


def hundertstel(v):
    return None if v is None else int(round(v * 100))


def bauen(ordner, stand):
    funde = {}
    zips = sorted(glob.glob(os.path.join(ordner, "*ncen*.zip")))
    if not zips:
        raise SystemExit("keine Zips in " + ordner)
    for p in zips:
        lesen(p, funde)
    # Verteilung je Kalenderjahr des Geschaeftsjahresendes ueber alle Indexfonds
    je_jahr = {}
    for f in funde.values():
        for (jahr, _monat), (_e, vor, nach, _s) in f["jahre"].items():
            je_jahr.setdefault(jahr, []).append((vor, nach))
    verteilung = []
    for jahr in sorted(je_jahr):
        nach = [n for _v, n in je_jahr[jahr]]
        if len(nach) < 50:
            continue
        vor = [v for v, _n in je_jahr[jahr] if v is not None]
        k = [0] * (len(GRENZEN) + 1)
        for v in nach:
            k[klasse(v)] += 1
        verteilung.append({"j": jahr, "n": len(nach), "m": hundertstel(median(nach)), "mv": hundertstel(median(vor)) if vor else None, "k": k})
    # Fonds mit Kuerzel; ein Kuerzel gehoert dem Fonds mit der juengsten Meldung
    besitzer = {}
    for s, f in funde.items():
        for t in f["kuerzel"]:
            if t not in besitzer or funde[besitzer[t]]["neu"] < f["neu"]:
                besitzer[t] = s
    liste = []
    for s, f in funde.items():
        ts = sorted(t for t in f["kuerzel"] if besitzer.get(t) == s)
        if not ts or not f["jahre"]:
            continue
        jahre = sorted(f["jahre"])
        # Ein Wert je Kalenderjahr (bei gewechseltem Geschaeftsjahr der spaetere Bericht)
        je = {}
        for (jahr, monat) in jahre:
            je[jahr] = (monat,) + f["jahre"][(jahr, monat)][1:]
        ab = min(je)
        bis = max(je)
        eintrag = {
            "t": ts, "n": f["name"], "m": je[bis][0], "ab": ab,
            "v": [hundertstel(je[j][1]) if j in je else None for j in range(ab, bis + 1)],
            "a": [hundertstel(je[j][2]) if j in je else None for j in range(ab, bis + 1)],
        }
        liste.append(eintrag)
    liste.sort(key=lambda e: e["t"][0])
    aus = {
        "stand": stand,
        "quelle": "U.S. Securities and Exchange Commission, Form N-CEN Data Sets",
        "adresse": "https://www.sec.gov/data-research/sec-markets-data/form-n-cen-data-sets",
        "hinweis": "Abstand = Rendite des Fonds minus Rendite des Index im Geschaeftsjahr, Hundertstel Prozentpunkte; v vor Kosten, a nach Kosten; m Monat des Geschaeftsjahresendes; Jahr = Kalenderjahr des Geschaeftsjahresendes",
        "grenzen": [hundertstel(g) for g in GRENZEN],
        "verteilung": verteilung,
        "fonds": liste,
    }
    text = json.dumps(aus, ensure_ascii=False, separators=(",", ":"))
    ziel = os.path.join(repo(), "app", "src", "main", "assets", "etf-abstand.json")
    with open(ziel, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print("Zips", len(zips), "Indexfonds", len(funde), "mit Kuerzel", len(liste), "Bytes", len(text.encode("utf-8")))
    for v in verteilung:
        print(v)
    return aus


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit(__doc__ or "Aufruf: index_abstand.py <Ordner mit den Zips> [Stand]")
    bauen(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else datetime.date.today().isoformat())
