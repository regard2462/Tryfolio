# Kurzinfos zu Abgeordneten und Senatoren fuer Copytrading: Partei, Kammer, Staat und Wahlkreis, seit wann in der
# Kammer, Ausschuesse mit Funktion. Quelle: unitedstates/congress-legislators (CC0), Dateien legislators-current.json,
# committee-membership-current.json und committees-current.json von https://unitedstates.github.io/congress-legislators/
# Aufruf: python -I personen.py <ordner mit den drei Dateien> <ziel copytrading/personen.json>
# Ergebnis: {"stand", "quelle", "p": {Bioguide: {"pa", "k", "st", "d", "seit", "a": [[Ausschuss, Funktion]]}}}

import datetime
import json
import os
import sys

ORDNER, ZIEL = sys.argv[1:3]
sys.stdout.reconfigure(encoding="utf-8")


def laden(name):
    with open(os.path.join(ORDNER, name), encoding="utf-8") as f:
        return json.load(f)


PARTEI = {"Republican": "R", "Democrat": "D", "Independent": "I", "Libertarian": "L"}

legislators = laden("legislators-current.json")
mitglieder = laden("committee-membership-current.json")
ausschuesse = {c["thomas_id"]: c["name"] for c in laden("committees-current.json")}


def kurz(name):
    """„House Committee on Financial Services“ wird zu „Financial Services“."""
    for vorn in ("House Committee on ", "Senate Committee on ", "Senate Special Committee on ", "Senate Select Committee on ",
                 "House Permanent Select Committee on ", "Joint Committee on ", "Committee on ", "Senate ", "House "):
        if name.startswith(vorn):
            name = name[len(vorn):]
            break
    return name.replace("the ", "", 1) if name.startswith("the ") else name


je_person = {}
for kennung, liste in mitglieder.items():
    # Nur ganze Ausschuesse (vier Zeichen), keine Unterausschuesse
    if len(kennung) != 4 or kennung not in ausschuesse:
        continue
    for m in liste:
        b = m.get("bioguide")
        if not b:
            continue
        je_person.setdefault(b, []).append([kurz(ausschuesse[kennung]), m.get("title", "")])

p = {}
for x in legislators:
    b = x["id"]["bioguide"]
    t = x["terms"][-1]
    # Seit wann ohne Unterbrechung in derselben Kammer
    seit = t["start"]
    for frueher in reversed(x["terms"][:-1]):
        # Andere Kammer oder Luecke von mehr als einem Monat beendet die Folge
        luecke = (datetime.date.fromisoformat(seit) - datetime.date.fromisoformat(frueher["end"])).days
        if frueher["type"] != t["type"] or luecke > 31:
            break
        seit = frueher["start"]
    e = {"pa": PARTEI.get(t.get("party", ""), "I"), "k": t["type"], "st": t["state"], "seit": seit}
    if t["type"] == "rep" and t.get("district") is not None:
        e["d"] = t["district"]
    a = je_person.get(b)
    if a:
        # Vorsitz zuerst, dann nach Namen
        e["a"] = sorted(a, key=lambda z: (z[1] == "", z[0]))
    p[b] = e

with open(ZIEL, "w", encoding="utf-8", newline="\n") as f:
    json.dump({"stand": datetime.date.today().isoformat(), "quelle": "unitedstates/congress-legislators (CC0)", "p": p},
              f, ensure_ascii=False, separators=(",", ":"))
print(len(p), "Personen,", sum(1 for e in p.values() if e.get("a")), "mit Ausschuessen")
