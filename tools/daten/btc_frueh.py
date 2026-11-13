#!/usr/bin/env python3
# Baut app/src/main/assets/indikatoren/btc_frueh.json: Tagesschlusskurse von Bitcoin in US-Dollar aus der Zeit, bevor der
# gewoehnliche Kursweg der App beginnt (dort setzt die Reihe am 17.09.2014 ein).
#
# Quelle und Bedingungen (am 12.11.2026 an der Quelle gelesen):
#   Coin Metrics Community Data, https://github.com/coinmetrics/data (Datei csv/btc.csv, Spalte PriceUSD)
#   "Data is made available under the CC BY-NC 4.0 license." Nicht kommerzielle Nutzung, Namensnennung, Aenderungen
#   kenntlich machen. Die Datei hier ist ein Auszug: nur Datum und PriceUSD, bis ENDE, gerundet auf 0,0001 USD.
#
# Aufruf:
#   python tools/daten/btc_frueh.py holen <Ordner>                      laedt btc.csv (Browserkennung, kein Konto)
#   python tools/daten/btc_frueh.py bauen <Ordner> --stand=JJJJ-MM-TT   liest btc.csv aus dem Ordner
# Danach: python tools/daten/manifest.py bauen   (nicht signieren; das macht der Auftraggeber)
#
# Pflege: nur noetig, wenn die Quelle fruehe Kurse berichtigt. ENDE liegt mit Absicht hinter dem Beginn des gewoehnlichen
# Kurswegs: die Ueberlappung dient dem Vergleich im Test, die App nimmt nur Tage vor dem ersten eigenen Kurs.

import csv
import datetime
import json
import os
import sys
import urllib.request

ADRESSE = "https://raw.githubusercontent.com/coinmetrics/data/master/csv/btc.csv"
KENNUNG = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
ENDE = datetime.date(2014, 12, 31)
QUELLE = "Coin Metrics Community Data (https://github.com/coinmetrics/data), Datei csv/btc.csv, Spalte PriceUSD, abgerufen am "
LIZENZ = ("CC BY-NC 4.0 (https://creativecommons.org/licenses/by-nc/4.0/): nicht kommerzielle Nutzung, Namensnennung. "
          "Auszug aus Coin Metrics Community Data: nur Datum und Schlusskurs in US-Dollar, gerundet auf 0,0001 USD.")


def repo():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def bauen(pfad, stand):
    kurse = {}
    with open(pfad, newline="", encoding="utf-8") as f:
        for z in csv.DictReader(f):
            p = (z.get("PriceUSD") or "").strip()
            if not p:
                continue
            tag = datetime.date.fromisoformat(z["time"][:10])
            if tag <= ENDE:
                kurse[tag] = float(p)
    if not kurse:
        raise SystemExit("Keine Kurse gelesen")
    von = min(kurse)
    werte = []
    tag = von
    while tag <= ENDE:
        v = kurse.get(tag)
        werte.append(None if v is None or v <= 0 else int(round(v * 10000)))
        tag += datetime.timedelta(days=1)
    if werte[0] is None or werte[-1] is None:
        raise SystemExit("Reihe beginnt oder endet mit einer Luecke")
    return {
        "stand": stand, "schema": 1, "quelle": QUELLE + stand, "lizenz": LIZENZ, "symbol": "BTC-USD",
        "von": von.isoformat(), "bis": ENDE.isoformat(), "einheit": "USD in Zehntausendsteln", "w": werte,
    }


def hauptteil():
    if len(sys.argv) < 3 or sys.argv[1] not in ("holen", "bauen"):
        raise SystemExit(__doc__ or "Aufruf: btc_frueh.py holen|bauen <Ordner> [--stand=JJJJ-MM-TT]")
    ordner = sys.argv[2]
    datei = os.path.join(ordner, "btc.csv")
    if sys.argv[1] == "holen":
        os.makedirs(ordner, exist_ok=True)
        r = urllib.request.Request(ADRESSE, headers={"User-Agent": KENNUNG})
        with urllib.request.urlopen(r, timeout=120) as a:
            b = a.read()
        with open(datei, "wb") as f:
            f.write(b)
        print("btc.csv: %d Bytes" % len(b))
        return
    stand = next((a[len("--stand="):] for a in sys.argv[3:] if a.startswith("--stand=")), None)
    if not stand:
        raise SystemExit("--stand=JJJJ-MM-TT angeben (Tag des Abrufs)")
    text = json.dumps(bauen(datei, stand), ensure_ascii=False, separators=(",", ":")) + chr(10)
    ziel = os.path.join(repo(), "app", "src", "main", "assets", "indikatoren", "btc_frueh.json")
    with open(ziel, "w", encoding="utf-8", newline=chr(10)) as f:
        f.write(text)
    print("btc_frueh.json: %d Bytes" % len(text.encode("utf-8")))


if __name__ == "__main__":
    hauptteil()
