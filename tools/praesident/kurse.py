# Holt Tagesschlusskurse fuer zugeordnete Kuerzel, die in den vorhandenen Kursordnern fehlen, im Format von
# yahoo_holen.py (hist/<Kuerzel>.json mit "tage": [[Zeit in s, Schluss], ...]). Ab Dezember 2024, langsam (3 je Sekunde),
# mit Browser-Kennung ohne Angaben zur Person.
# Aufruf: python -I kurse.py <zuordnung.json> <regierung.json> <ziel-ordner> <vorhandene kursordner>...

import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ZUORDNUNG, REG, ZIEL = sys.argv[1:4]
VORHANDEN = sys.argv[4:] + [ZIEL]
sys.stdout.reconfigure(encoding="utf-8")
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
VON = 1733011200  # 2024-12-01


UMBENANNT = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "umbenannt.json"), encoding="utf-8"))


def yahoo(k):
    s = re.sub(r"^([A-Z0-9]+)[./]([A-KM-Z])$", r"\1-\2", k.strip())
    return UMBENANNT.get(s, s)


def datei(ordner, sym):
    return os.path.join(ordner, "hist", urllib.parse.quote(sym, safe="").replace("%", "_") + ".json")


zu = json.load(open(ZUORDNUNG, encoding="utf-8"))
reg = json.load(open(REG, encoding="utf-8"))
symbole = {yahoo(e["k"]) for e in zu.values() if e.get("k") and e.get("art") != "anleihe"}
for p in reg["personen"]:
    for m in p.get("meldungen", []):
        for g in m["geschaefte"]:
            k = (g.get("k") or "").strip()
            if k and not k.startswith("-") and any(c.isalpha() for c in k):
                symbole.add(yahoo(k))
fehlt = sorted(s for s in symbole if not any(os.path.exists(datei(o, s)) for o in VORHANDEN))
os.makedirs(os.path.join(ZIEL, "hist"), exist_ok=True)
print("Kuerzel:", len(symbole), "| ohne Kurse in den Ordnern:", len(fehlt), flush=True)
ok = leer = 0
for i, s in enumerate(fehlt):
    url = ("https://query1.finance.yahoo.com/v8/finance/chart/" + urllib.parse.quote(s, safe="") + "?interval=1d&period1="
           + str(VON) + "&period2=" + str(int(time.time())) + "&includePrePost=false")
    inhalt = None
    for versuch in range(5):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"}), timeout=40) as r:
                inhalt = json.loads(r.read())
            break
        except urllib.error.HTTPError as e:
            if e.code == 429 or e.code >= 500:
                print("Yahoo bremst, Pause", 60 * (versuch + 1), "s", flush=True)
                time.sleep(60 * (versuch + 1))
                continue
            inhalt = {}
            break
        except Exception:
            time.sleep(5 * (versuch + 1))
    try:
        res = inhalt["chart"]["result"][0]
        q = res["indicators"]["quote"][0]
        tage = [[t, c] for t, c in zip(res.get("timestamp") or [], q.get("close") or []) if c is not None]
        aus = {"meta": res.get("meta") or {}, "tage": tage} if tage else {"leer": True}
    except (TypeError, KeyError, IndexError):
        aus = {"leer": True}
    ok += 0 if aus.get("leer") else 1
    leer += 1 if aus.get("leer") else 0
    json.dump(aus, open(datei(ZIEL, s), "w", encoding="utf-8"), separators=(",", ":"))
    if i % 100 == 0:
        print(i, "von", len(fehlt), flush=True)
    time.sleep(1 / 3)
print("geholt:", ok, "mit Kursen,", leer, "ohne", flush=True)
