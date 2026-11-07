# Kongressmitglieder fuer Copytrading: amtierende Abgeordnete und Senatoren, dazu beendete Folgen als Ehemalige,
# damit die App ab Werk weiss, wer die Kammer verlassen hat. Quelle: unitedstates/congress-legislators (CC0),
# Dateien legislators-current.json und legislators-historical.json von https://unitedstates.github.io/congress-legislators/
# Aufruf: python -I kongress.py <ordner mit den beiden Dateien> <ziel copytrading/kongress.json> [stichtag JJJJ-MM-TT]
# Ergebnis: {"format", "stand", "geprueft", "aktuell", "ehemalig"} im Format von KongressBestand.Bestand

import datetime
import json
import os
import re
import sys

ORDNER, ZIEL = sys.argv[1:3]
STICHTAG = sys.argv[3] if len(sys.argv) > 3 else datetime.date.today().isoformat()
sys.stdout.reconfigure(encoding="utf-8")

PARTEI = {"Republican": "R", "Democrat": "D", "Independent": "I", "Libertarian": "L"}
# Die Kennung wird Teil von Bildadressen, alles andere passt nicht
KENNUNG = re.compile(r"^[A-Z][0-9]{6}$")
# Vor diesem Tag gibt es keine mitgelieferten Meldungen (die aeltesten beginnen im Herbst 2023)
FRUEHEST = "2023-07-01"


def laden(name):
    with open(os.path.join(ORDNER, name), encoding="utf-8") as f:
        return json.load(f)


def tage(s):
    """ISO-Datum als Tagezahl, None bei ungueltigem Datum."""
    try:
        return datetime.date.fromisoformat(s).toordinal()
    except (TypeError, ValueError):
        return None


def seit_wert(terms, i):
    """Beginn der laufenden Folge: rueckwaerts bis zur anderen Kammer oder zu einer Luecke ueber 31 Tage."""
    seit = terms[i]["start"]
    for vorige in reversed(terms[:i]):
        a, e = tage(seit), tage(vorige.get("end"))
        if vorige.get("type") != terms[i]["type"] or a is None or e is None or a - e > 31:
            break
        seit = vorige["start"]
    return seit


def folgen(terms):
    """Amtszeiten je Kammer zu Folgen mit hoechstens 31 Tagen Abstand gruppieren."""
    ergebnis, folge = [], []
    for t in terms:
        if t.get("type") not in ("rep", "sen"):
            folge = []
            continue
        if folge:
            a, e = tage(t.get("start")), tage(folge[-1].get("end"))
            if t["type"] != folge[-1]["type"] or a is None or e is None or a - e > 31:
                ergebnis.append(folge)
                folge = []
        folge.append(t)
    if folge:
        ergebnis.append(folge)
    return ergebnis


def eintrag(x, t, seit, bis):
    """Ein Mitglied: Kammer, Staat, Partei und Wahlkreis aus der letzten Amtszeit t der Folge."""
    name = x.get("name", {})
    e = {"id": x.get("id", {}).get("bioguide", ""), "n": name.get("last", ""), "v": name.get("first", "")}
    if name.get("nickname"):
        e["r"] = name["nickname"]
    e["k"] = t["type"]
    e["st"] = t.get("state", "")
    if t["type"] == "rep" and t.get("district") is not None:
        e["d"] = t["district"]
    e["pa"] = PARTEI.get(t.get("party", ""), "I")
    e["seit"] = seit
    e["bis"] = bis
    return e


def aufnehmen(x, b, folge):
    """Beendete Folge als Ehemaligen merken, wenn ihre Zeit noch im Bereich der mitgelieferten Meldungen liegt."""
    ende = folge[-1].get("end", "")
    if not ende or ende < FRUEHEST or (b, folge[-1]["type"], folge[0]["start"]) in laufend:
        return
    ehemalig.append(eintrag(x, folge[-1], folge[0]["start"], min(ende, STICHTAG)))


aktuell, laufend, ehemalig = [], set(), []
for x in laden("legislators-current.json"):
    b = x.get("id", {}).get("bioguide", "")
    if not KENNUNG.match(b):
        continue
    terms = x.get("terms", [])
    # Die letzte angefangene Amtszeit; noch nicht eingeschworene Wahlsieger zaehlen nicht
    i = None
    for j, t in enumerate(terms):
        if t.get("start", "") <= STICHTAG:
            i = j
    if i is None or terms[i].get("type") not in ("rep", "sen"):
        continue
    m = eintrag(x, terms[i], seit_wert(terms, i), terms[i].get("end", ""))
    aktuell.append(m)
    laufend.add((b, m["k"], m["seit"]))
    # Beendete Folgen derselben Person, etwa der Wechsel vom Haus in den Senat
    for folge in folgen(terms[:i]):
        aufnehmen(x, b, folge)

for x in laden("legislators-historical.json"):
    b = x.get("id", {}).get("bioguide", "")
    if not KENNUNG.match(b):
        continue
    for folge in folgen(x.get("terms", [])):
        aufnehmen(x, b, folge)

ehemalig.sort(key=lambda m: m["bis"], reverse=True)

with open(ZIEL, "w", encoding="utf-8", newline="\n") as f:
    json.dump({"format": 1, "stand": STICHTAG, "geprueft": 0, "aktuell": aktuell, "ehemalig": ehemalig},
              f, ensure_ascii=False, separators=(",", ":"))
print(len(aktuell), "aktuell,", len(ehemalig), "ehemalig, Stand", STICHTAG)
