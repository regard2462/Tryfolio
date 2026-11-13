#!/usr/bin/env python3
# Baut das Datenmanifest daten/manifest.json aus den gepflegten Dateien der App.
#
# Die Dateien stehen in tools/daten/dateien.json (Name wie in assets/, Schema-Nummer, Mindest-App-Fassung). Je Datei
# traegt das Manifest: fassung (zaehlt hoch, sobald sich der Inhalt aendert), sha256, bytes, stand (Datum der Daten),
# schema und minApp. Die Dateien werden nach daten/<name> kopiert (Byte fuer Byte), das Manifest wird zusaetzlich als
# eingebauter Stand nach app/src/main/assets/daten/manifest.json gelegt: daran erkennt die App, ob eine Datei aus der
# Ferne neuer ist als die eingebaute.
#
#   python tools/daten/manifest.py bauen [--stand=name=JJJJ-MM-TT ...]
#                                             Manifest neu bauen (version zaehlt nur hoch, wenn sich etwas aendert); --stand setzt
#                                             den Stand einer geaenderten Datei, wenn die Datei selbst keinen nennt
#   python tools/daten/manifest.py bauen --signieren
#                                             dazu gleich signieren (Datenschluessel unter ~/.tryfolio-signatur) und die Kette pruefen
#   python tools/daten/manifest.py zeigen     Stand der Dateien anzeigen, nichts schreiben
#
# Danach mit tools/quellen/signieren.py daten-signieren signieren und daten-pruefen laufen lassen.

import datetime
import hashlib
import json
import os
import re
import shutil
import sys


def repo():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def assets():
    return os.path.join(repo(), "app", "src", "main", "assets")


def lesen(pfad):
    with open(pfad, "rb") as f:
        return f.read()


def stand_aus_datei(b):
    # Datum der Daten aus dem obersten Feld stand (JJJJ-MM-TT oder JJJJ-MM), sonst leer
    try:
        o = json.loads(b.decode("utf-8"))
    except ValueError:
        return ""
    if isinstance(o, dict):
        for k in ("stand", "Stand", "asOf"):
            v = o.get(k)
            if isinstance(v, str) and re.fullmatch(r"\d{4}-\d{2}(-\d{2})?", v):
                return v if len(v) == 10 else v + "-01"
    return ""


def bauen(schreiben, staende):
    plan = json.loads(lesen(os.path.join(repo(), "tools", "daten", "dateien.json")).decode("utf-8"))
    pfad = os.path.join(repo(), "daten", "manifest.json")
    alt = json.loads(lesen(pfad).decode("utf-8")) if os.path.exists(pfad) else {"version": 0, "dateien": {}}
    heute = datetime.date.today().isoformat()
    neu = {}
    geaendert = False
    for e in plan["dateien"]:
        name = e["name"]
        b = lesen(os.path.join(assets(), *name.split("/")))
        sha = hashlib.sha256(b).hexdigest()
        v = alt["dateien"].get(name)
        if v and v["sha256"] == sha:
            eintrag = dict(v)
        else:
            stand = staende.get(name) or stand_aus_datei(b) or heute
            eintrag = {"fassung": (v["fassung"] + 1) if v else 1, "sha256": sha, "bytes": len(b), "stand": stand}
            geaendert = True
        eintrag["schema"] = e.get("schema", 1)
        eintrag["minApp"] = e.get("minApp", "0.73")
        if v and (v.get("schema") != eintrag["schema"] or v.get("minApp") != eintrag["minApp"]):
            geaendert = True
        neu[name] = eintrag
    if set(alt["dateien"]) != set(neu):
        geaendert = True
    version = alt["version"] + (1 if geaendert else 0)
    erzeugt = alt.get("erzeugt", heute) if not geaendert else heute
    m = {"manifest": 1, "version": version, "erzeugt": erzeugt, "dateien": neu}
    text = json.dumps(m, indent=1, sort_keys=True, ensure_ascii=False) + "\n"
    if not schreiben:
        for n, v in sorted(neu.items()):
            print("%-34s fassung %-3d stand %s  %d Bytes" % (n, v["fassung"], v["stand"], v["bytes"]))
        print("Manifest version %d%s" % (version, " (geaendert)" if geaendert else " (unveraendert)"))
        return
    for name in neu:
        ziel = os.path.join(repo(), "daten", *name.split("/"))
        os.makedirs(os.path.dirname(ziel), exist_ok=True)
        shutil.copyfile(os.path.join(assets(), *name.split("/")), ziel)
    os.makedirs(os.path.dirname(pfad), exist_ok=True)
    with open(pfad, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    eingebaut = os.path.join(assets(), "daten", "manifest.json")
    os.makedirs(os.path.dirname(eingebaut), exist_ok=True)
    with open(eingebaut, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print("Manifest version %d mit %d Dateien geschrieben" % (version, len(neu)))


if __name__ == "__main__":
    befehl = sys.argv[1] if len(sys.argv) > 1 else "zeigen"
    if befehl not in ("bauen", "zeigen"):
        raise SystemExit(__doc__)
    staende = dict(a[len("--stand="):].split("=", 1) for a in sys.argv[2:] if a.startswith("--stand="))
    bauen(befehl == "bauen", staende)
    if befehl == "bauen" and "--signieren" in sys.argv[2:]:
        sys.path.insert(0, os.path.join(repo(), "tools", "quellen"))
        import signieren
        signieren.selbsttest()
        pfad = signieren.manifest_pfad()
        seed = signieren.seed_laden("daten")
        zert = json.loads(signieren.datei_lesen(signieren.zertifikat_pfad()).decode("utf-8"))
        if zert["schluessel"] != signieren.oeffentlich(seed).hex():
            raise SystemExit("Der Datenschluessel passt nicht zum Zertifikat daten/schluessel.json")
        signieren.sig_schreiben(pfad, signieren.signieren(seed, signieren.datei_lesen(pfad)))
        signieren.kette_pruefen(pfad)
