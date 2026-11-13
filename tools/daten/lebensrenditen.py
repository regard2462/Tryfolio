#!/usr/bin/env python3
# Baut app/src/main/assets/indikatoren/lebensrenditen.json: Verteilung der Lebensrenditen aller US-Stammaktien seit 1926
# und die Verteilung des geschaffenen Vermoegens je Unternehmen, nur als Auszaehlung (Klassen und Anteile).
#
# Quelle und Bedingungen (am 12.11.2026 an der Quelle gelesen):
#   Hendrik Bessembinder, Arizona State University, Seite "Do Stocks Outperform Treasury Bills?"
#   https://wpcarey.asu.edu/department-finance/faculty-research/do-stocks-outperform-treasury-bills
#   Tabelle WCandRets.100years_0.xlsx ("Updated compound returns and wealth creation, 1926-2025").
#   Die Seite nennt keine Bedingungen fuer die Weitergabe der Tabelle; sie beruht auf Daten von CRSP. Darum liegt der App
#   KEINE Zeile der Tabelle bei (keine Firma, keine Einzelrendite), nur die eigene Auszaehlung: Zahl der Aktien je
#   Renditeklasse, Median, Zahl der Unternehmen mit positivem geschaffenem Vermoegen und Zahl der Unternehmen, die
#   zusammen die Haelfte und das Ganze des Nettozuwachses ergeben.
#
# Aufruf:
#   python tools/daten/lebensrenditen.py holen <Ordner>                      laedt die Tabelle (Browserkennung, kein Konto)
#   python tools/daten/lebensrenditen.py bauen <Ordner> --stand=JJJJ-MM-TT   liest die Tabelle aus dem Ordner
# Danach: python tools/daten/manifest.py bauen   (nicht signieren; das macht der Auftraggeber)
#
# Pflege (jaehrlich, Fruehjahr): Die Seite bekommt jedes Jahr eine Tabelle mit einem weiteren Jahr unter neuem Namen.
# ADRESSE, DATEI, die Blattnamen und BIS anpassen, neu bauen, die Zahlen im Test nachziehen.
#
# Regeln: Blatt BuyHoldRets: je Aktie die Spalte "Cumulative Compound Buy-and-Hold Return" als Anteil (-1 = Totalverlust).
# Klassen in Prozent, untere Grenze eingeschlossen. Blatt WealthCreation: je Unternehmen "Lifetime Wealth Creation" in
# Millionen US-Dollar, gemessen gegen einmonatige US-Schatzwechsel; absteigend aufsummiert bis zur Haelfte und bis zum
# Ganzen der Nettosumme.

import json
import os
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from historie import blaetter  # noqa: E402

SEITE = "https://wpcarey.asu.edu/department-finance/faculty-research/do-stocks-outperform-treasury-bills"
ADRESSE = "https://wpcarey.asu.edu/sites/g/files/litvpz246/files/2026-03/WCandRets.100years_0.xlsx"
DATEI = "WCandRets.100years_0.xlsx"
BLATT_RENDITEN = "BuyHoldRets.2025"
BLATT_VERMOEGEN = "WealthCreation.2025"
VON, BIS = 1926, 2025
KENNUNG = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
# Klassengrenzen der Lebensrendite in Prozent; die letzte Klasse ist nach oben offen
GRENZEN = [-100, -75, -50, -25, 0, 100, 500, 1000]


def repo():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def median(werte):
    s = sorted(werte)
    n = len(s)
    return s[n // 2] if n % 2 else (s[n // 2 - 1] + s[n // 2]) / 2.0


def bauen(pfad, stand):
    b = blaetter(pfad)
    z = b[BLATT_RENDITEN]
    kopf = {v: k for k, v in z[1].items() if isinstance(v, str)}
    spalte = kopf["Cumulative Compound Buy-and-Hold Return"]
    name = kopf["Company Name (Most Recent)"]
    renditen = [z[k][spalte] for k in sorted(z) if k > 1 and name in z[k] and isinstance(z[k].get(spalte), float)]
    if len(renditen) < 20000 or min(renditen) < -1.0:
        raise SystemExit("Blatt der Renditen unerwartet")
    klassen = []
    for i, von in enumerate(GRENZEN):
        bis = GRENZEN[i + 1] if i + 1 < len(GRENZEN) else None
        n = sum(1 for r in renditen if r * 100.0 >= von and (bis is None or r * 100.0 < bis))
        klassen.append({"von": von, "bis": bis, "n": n})
    if sum(k["n"] for k in klassen) != len(renditen):
        raise SystemExit("Klassen decken nicht alle Aktien")

    z = b[BLATT_VERMOEGEN]
    vermoegen = sorted((z[k][2] for k in sorted(z) if k > 2 and isinstance(z[k].get(1), str) and isinstance(z[k].get(2), float)), reverse=True)
    if len(vermoegen) < 20000:
        raise SystemExit("Blatt des Vermoegens unerwartet")
    summe = sum(vermoegen)
    laufend, haelfte, alles = 0.0, None, None
    for i, v in enumerate(vermoegen):
        laufend += v
        if haelfte is None and laufend >= summe / 2.0:
            haelfte = i + 1
        if alles is None and laufend >= summe:
            alles = i + 1
    return {
        "stand": stand, "schema": 1,
        "quelle": "Hendrik Bessembinder, Compound returns and wealth creation of U.S. common stocks, 1926 to 2025 (" + SEITE + "), abgerufen am " + stand,
        "hinweis": "Eigene Auszaehlung aus der veroeffentlichten Tabelle. Keine Einzelwerte.",
        "von": VON, "bis": BIS,
        "aktien": len(renditen), "negativ": sum(1 for r in renditen if r < 0), "median": round(median(renditen) * 100.0, 2),
        "klassen": klassen,
        "firmen": len(vermoegen), "mehrwert": sum(1 for v in vermoegen if v > 0), "haelfte": haelfte, "alles": alles,
    }


def hauptteil():
    if len(sys.argv) < 3 or sys.argv[1] not in ("holen", "bauen"):
        raise SystemExit("Aufruf: lebensrenditen.py holen|bauen <Ordner> [--stand=JJJJ-MM-TT]")
    ordner = sys.argv[2]
    if sys.argv[1] == "holen":
        os.makedirs(ordner, exist_ok=True)
        r = urllib.request.Request(ADRESSE, headers={"User-Agent": KENNUNG})
        with urllib.request.urlopen(r, timeout=120) as a:
            inhalt = a.read()
        with open(os.path.join(ordner, DATEI), "wb") as f:
            f.write(inhalt)
        print("%s: %d Bytes" % (DATEI, len(inhalt)))
        return
    stand = next((a[len("--stand="):] for a in sys.argv[3:] if a.startswith("--stand=")), None)
    if not stand:
        raise SystemExit("--stand=JJJJ-MM-TT angeben (Tag des Abrufs)")
    text = json.dumps(bauen(os.path.join(ordner, DATEI), stand), ensure_ascii=False, separators=(",", ":")) + chr(10)
    ziel = os.path.join(repo(), "app", "src", "main", "assets", "indikatoren", "lebensrenditen.json")
    with open(ziel, "w", encoding="utf-8", newline=chr(10)) as f:
        f.write(text)
    print("lebensrenditen.json: %d Bytes" % len(text.encode("utf-8")))
    print(text)


if __name__ == "__main__":
    hauptteil()
