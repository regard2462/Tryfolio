#!/usr/bin/env python3
# Zuschuesse und Zahlungen der EU-Kommission je boersennotierter Firma, Ergebnis ist die Datei
# app/src/main/assets/firmen/staatsgeld-eu.json.
#
#   python tools/daten/staatsgeld_eu.py [ziel.json] [arbeitsordner]
#
# Quelle ist das Financial Transparency System der Europaeischen Kommission (ohne Konto, ohne Schluessel): je Jahr eine
# Excel-Datei mit den Empfaengern von Mitteln aus dem EU-Haushalt, Titel "JJJJ_FTS_dataset_en.xlsx" unter
#   https://ec.europa.eu/budget/financial-transparency-system/download/
# Verzeichnis der Dateien: https://data.europa.eu/data/datasets/fts (Lizenz CC BY 4.0). Die Rohdateien (je rund 15 MB gepackt)
# landen im Arbeitsordner (Standard: temporaer) und werden nicht eingecheckt.
#
# Gezaehlt wird der vertraglich zugesagte Betrag je Empfaenger ("Beneficiary's contracted amount (EUR)") nach dem Jahr der
# Zusage. Es zaehlen nur Foerderarten laut Spalte "Type of contract" (Zuschuesse, Beitragsvereinbarungen, Preise, Finanzierungs-
# instrumente, Hilfen ohne Rueckzahlung). Beschaffungen der Kommission (Beratung, Veranstaltungen, Gebaeude, IT) fehlen: Sie
# stehen als Zuschlaege schon in TED.
#
# Zuordnung wie bei den Emissionen (emissionen_eu.py, zuordnen): Der Name des Empfaengers muss mit dem Namen der Firma im
# Katalog der App beginnen und darf danach nur Rechtsformen und Laenderzusaetze tragen. Zuordnung nur bei eindeutigem Treffer.
# Universitaeten, Institute und Vereine fallen weg, weil sie nicht im Katalog stehen.
#
# Datei: {format, gebiet: "EU", stand, jahr0, jahre, firmen: [{y: [Kuerzel im Katalog], n: Name, f: [Euro je Jahr ab jahr0],
#         p: [[Programm, Euro im letzten Jahr], ...]}]}
import datetime
import json
import os
import re
import shutil
import sys
import tempfile
import time
import urllib.error
import urllib.request

from emissionen_eu import KENNUNG, katalog, praefixe, zuordnen, blatt_zeilen

SEITE = "https://ec.europa.eu/budget/financial-transparency-system/download/"
JAHRE = 5
# Unter diesem Betrag (Euro) in jedem Jahr fliegt eine Firma aus der Datei
MINDEST = 100000

# Teile der Spalte "Type of contract", die eine Foerderung kennzeichnen
FOERDERUNG = ("grant", "contribution", "non-repayable", "financial instr", "prize", "subsid", "loan", "guarantee", "equity", "donation")

# Laender im Kopfwort der Spalte "Beneficiary country" (Name oder Kuerzel) zu ISO-2, damit kurze Einzelwoerter nur im Land der
# Firma gelten (siehe zuordnen); unbekannte Laender bleiben leer und zaehlen dann nur fuer lange Namen
LAENDER = {
    "AT": "AT", "AUSTRIA": "AT", "BE": "BE", "BELGIUM": "BE", "BG": "BG", "BULGARIA": "BG", "CY": "CY", "CYPRUS": "CY",
    "CZ": "CZ", "CZECHIA": "CZ", "CZECH REPUBLIC": "CZ", "DE": "DE", "GERMANY": "DE", "DK": "DK", "DENMARK": "DK",
    "EE": "EE", "ESTONIA": "EE", "ES": "ES", "SPAIN": "ES", "FI": "FI", "FINLAND": "FI", "FR": "FR", "FRANCE": "FR",
    "GR": "GR", "EL": "GR", "GREECE": "GR", "HR": "HR", "CROATIA": "HR", "HU": "HU", "HUNGARY": "HU", "IE": "IE",
    "IRELAND": "IE", "IT": "IT", "ITALY": "IT", "LT": "LT", "LITHUANIA": "LT", "LU": "LU", "LUXEMBOURG": "LU",
    "LV": "LV", "LATVIA": "LV", "MT": "MT", "MALTA": "MT", "NL": "NL", "NETHERLANDS": "NL", "PL": "PL", "POLAND": "PL",
    "PT": "PT", "PORTUGAL": "PT", "RO": "RO", "ROMANIA": "RO", "SE": "SE", "SWEDEN": "SE", "SI": "SI", "SLOVENIA": "SI",
    "SK": "SK", "SLOVAKIA": "SK", "NO": "NO", "NORWAY": "NO", "CH": "CH", "SWITZERLAND": "CH", "GB": "GB",
    "UNITED KINGDOM": "GB", "UK": "GB", "US": "US", "UNITED STATES": "US", "USA": "US",
}


def laden(jahr, ordner):
    # Datei eines Jahres in den Arbeitsordner; None, wenn die Kommission sie (noch) nicht hat
    pfad = os.path.join(ordner, f"{jahr}_FTS_dataset_en.xlsx")
    if os.path.exists(pfad):
        return pfad
    for versuch in range(4):
        try:
            req = urllib.request.Request(SEITE + os.path.basename(pfad), headers=KENNUNG)
            with urllib.request.urlopen(req, timeout=300) as r, open(pfad + ".teil", "wb") as f:
                shutil.copyfileobj(r, f)
            os.replace(pfad + ".teil", pfad)
            time.sleep(1)
            return pfad
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            if versuch == 3:
                raise
        except Exception:  # noqa: BLE001
            if versuch == 3:
                raise
        time.sleep(5 * (versuch + 1))


def spalten(kopf):
    # Spaltenbuchstaben nach den Kopfzeilen der Datei
    z = {}
    for sp, t in kopf.items():
        n = re.sub(r"\s+", " ", re.sub(r"[^a-z0-9()+ ]", " ", t.lower())).strip()
        if n == "year":
            z["jahr"] = sp
        elif n == "name of beneficiary":
            z["name"] = sp
        elif n == "beneficiary country":
            z["land"] = sp
        elif n.startswith("beneficiary") and "contracted amount (eur)" in n and "estimated" not in n:
            z["betrag"] = sp
        elif n == "expense type":
            z["art"] = sp
        elif n.startswith("type of contract"):
            z["vertrag"] = sp
        elif n == "programme name":
            z["programm"] = sp
        elif n == "budget line name":
            z["linie"] = sp
    fehlt = [k for k in ("jahr", "name", "betrag", "art") if k not in z]
    if fehlt:
        raise SystemExit("Spalten nicht gefunden: " + ", ".join(fehlt) + " in " + json.dumps(kopf, ensure_ascii=False)[:600])
    return z


def zahl(x):
    try:
        return float(x)
    except (TypeError, ValueError):
        return 0.0


def main():
    ziel = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/firmen/staatsgeld-eu.json"
    ordner = sys.argv[2] if len(sys.argv) > 2 else os.path.join(tempfile.gettempdir(), "fts_roh")
    os.makedirs(ordner, exist_ok=True)
    heute = datetime.date.today()
    dateien = []
    for j in range(heute.year, heute.year - JAHRE - 1, -1):
        p = laden(j, ordner)
        if p:
            dateien.append((j, p))
    if not dateien:
        raise SystemExit("Keine Datei des Financial Transparency System gefunden")
    letzt = max(j for j, _ in dateien)
    jahr0 = letzt - JAHRE + 1
    kat = katalog("app/src/main/assets/katalog.json")
    tabelle = praefixe(kat)
    firmen = {}
    nicht = {}
    gezaehlt = 0
    for _, pfad in dateien:
        z = None
        for i, r in enumerate(blatt_zeilen(pfad, None)):
            if i == 0:
                z = spalten(r)
                continue
            name = (r.get(z["name"]) or "").strip()
            if not name:
                continue
            if "vertrag" in z:
                vertrag = (r.get(z["vertrag"]) or "").lower()
                if not any(k in vertrag for k in FOERDERUNG):
                    continue
            elif "procure" in (r.get(z["art"]) or "").lower():
                continue
            try:
                jahr = int(float(r.get(z["jahr"]) or 0))
            except ValueError:
                continue
            if not jahr0 <= jahr <= letzt:
                continue
            betrag = zahl(r.get(z["betrag"]))
            if betrag == 0:
                continue
            land = LAENDER.get((r.get(z.get("land", "")) or "").strip().upper(), "")
            # Zweisprachige Namen stehen als "NAME EN*NAME FR"; jeder Teil zaehlt
            e = None
            for teil in name.split("*"):
                e = zuordnen(teil, land, kat, tabelle)
                if e is not None:
                    break
            if e is None:
                nicht[name] = nicht.get(name, 0) + max(betrag, 0)
                continue
            f = firmen.setdefault(tuple(e["y"]), {"n": e["n"], "f": [0.0] * JAHRE, "p": {}, "halter": {}})
            f["f"][jahr - jahr0] += betrag
            f["halter"][name] = f["halter"].get(name, 0) + max(betrag, 0)
            if jahr == letzt:
                prog = re.sub(r"\s+", " ", (r.get(z.get("programm", "")) or r.get(z.get("linie", "")) or "").strip())
                if prog:
                    f["p"][prog] = f["p"].get(prog, 0) + betrag
            gezaehlt += 1
    aus = []
    for y, f in firmen.items():
        reihe = [max(x, 0.0) for x in f["f"]]
        if max(reihe) < MINDEST:
            continue
        prog = sorted(((p, v) for p, v in f["p"].items() if v > 0), key=lambda x: -x[1])[:5]
        aus.append({"y": list(y), "n": f["n"], "f": [round(x) for x in reihe], "p": [[p[:80], round(v)] for p, v in prog]})
    aus.sort(key=lambda x: max(x["f"]), reverse=True)
    json.dump({"format": 1, "gebiet": "EU", "stand": heute.isoformat(), "jahr0": jahr0, "jahre": JAHRE, "firmen": aus},
              open(ziel, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print(len(aus), "Firmen aus", gezaehlt, "Zeilen, letztes Jahr", letzt)
    if os.environ.get("FTS_PRUEFEN"):
        for f in aus[:int(os.environ["FTS_PRUEFEN"])]:
            h = sorted(firmen[tuple(f["y"])]["halter"].items(), key=lambda x: -x[1])[:4]
            print(f["n"], f["y"], f["f"][-1], "|", "; ".join(f"{a} ({b / 1e6:.1f})" for a, b in h))
        print("--- groesste Empfaenger ohne Treffer")
        for a, b in sorted(nicht.items(), key=lambda x: -x[1])[:int(os.environ.get("FTS_NICHT", "60"))]:
            print(f"{b / 1e6:7.1f} Mio EUR  {a}")


if __name__ == "__main__":
    main()
