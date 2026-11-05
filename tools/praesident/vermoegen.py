# Vermoegensspanne des Praesidenten aus der Jahresmeldung (OGE Form 278e), gerechnet wie bei den Abgeordneten
# (market/Vermoegen.kt): Summe der Wertspannen aller Vermoegenswerte minus Summe der Verbindlichkeiten.
# Vermoegen: Teil 2 (Schedule 1, eigene Firmen und Rechte), Teil 5 (Ehepartnerin) und Teil 6 (Anlagen, aus
# jahresmeldung.json von jahresmeldung.py). Werte ohne Spanne („Value not readily ascertainable“) zaehlen nicht.
# Verbindlichkeiten: Teil 8; im Jahr abbezahlte Kredite („Paid Off“) zaehlen nicht.
# Aufruf: python -I vermoegen.py <278e.pdf> <jahresmeldung.json> <regierung.json>
# Ergebnis: regierung.json bekommt bei trump das Feld vermoegen im Format von Vermoegen.E.

import json
import re
import sys

import fitz

PDF, JAHRESMELDUNG, REGIERUNG = sys.argv[1:4]
sys.stdout.reconfigure(encoding="utf-8")

SPANNE = re.compile(r"\$\s?([\d,]+)\s*(?:to|-)\s*\$\s?([\d,]+)|Over\s*\$\s?([\d,]+)|None\s*\(or\s*less\s*than\s*\$1,001\)", re.I)


def zahl(s):
    return int(s.replace(",", ""))


def spanne(m):
    """(von, bis, offen) einer Fundstelle."""
    if m.group(1):
        return zahl(m.group(1)), zahl(m.group(2)), False
    if m.group(3):
        v = zahl(m.group(3)) + 1
        return v, v, True
    return 0, 0, False


def spalte(seite, kopf, rechts):
    """Text der Spalte kopf (bis zur Spalte rechts) unterhalb der Kopfzeile."""
    w = seite.get_text("words")
    k = [x for x in w if x[4] == kopf]
    if not k:
        return None
    k = k[0]
    r = [x for x in w if x[4] == rechts and abs(x[1] - k[1]) < 4 and x[0] > k[0]]
    links = [x for x in w if abs(x[1] - k[1]) < 4 and x[2] < k[0]]
    x0 = max(x[2] for x in links) + 1 if links else k[0] - 10
    x1 = min(x[0] for x in r) - 2 if r else k[0] + 80
    return seite.get_text("text", clip=fitz.Rect(x0, k[3] + 1, x1, seite.rect.height - 20))


d = fitz.open(PDF)
teile = {}
for i in range(d.page_count):
    t = d[i].get_text()
    m = re.search(r"Part (\d+): ", t)
    if m:
        teile.setdefault(int(m.group(1)), []).append(i)
jahr = int(re.search(r"Year \(Annual Report only\):\s*(\d{4})", d[0].get_text()).group(1))

av = ab = z = 0
ao = False
# Teil 2 (nur die Schedule-Seiten mit Werten) und Teil 5
for i in teile.get(2, []) + teile.get(5, []):
    text = spalte(d[i], "Value", "Income")
    if text is None:
        continue
    for m in SPANNE.finditer(text):
        v, b, o = spanne(m)
        av += v
        ab += b
        ao = ao or o
        z += 1
teil25 = z
for e in json.load(open(JAHRESMELDUNG, encoding="utf-8"))["teil6"]:
    if e.get("von") is None:
        continue
    av += e["von"]
    ab += e["bis"] if e["bis"] is not None else e["von"]
    ao = ao or e["bis"] is None
    z += 1

sv = sb = 0
so = False
schulden = 0
for i in teile.get(8, []):
    zeilen = [s.strip() for s in d[i].get_text().split("\n") if s.strip()]
    for n, s in enumerate(zeilen):
        m = SPANNE.fullmatch(s)
        if not m or not (m.group(1) or m.group(3)):
            continue
        # Hinter dem Betrag: Jahr, Zins, Laufzeit
        if any("paid off" in x.lower() for x in zeilen[n + 1:n + 4]):
            continue
        v, b, o = spanne(m)
        sv += v
        sb += b
        so = so or o
        schulden += 1

print("Jahr", jahr, "Werte", z, "davon Teil 2 und 5:", teil25, "Vermoegen", av, ab, ao, "Verbindlichkeiten", schulden, sv, sb, so)
reg = json.load(open(REGIERUNG, encoding="utf-8"))
for p in reg["personen"]:
    if p["id"] == "trump":
        p["vermoegen"] = {"id": "278e-%d" % jahr, "n": "Trump", "v": "Donald J.", "w": "", "j": jahr, "av": av, "ab": ab, "ao": ao,
                          "sv": sv, "sb": sb, "so": so, "z": z}
json.dump(reg, open(REGIERUNG, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, separators=(",", ":"))
