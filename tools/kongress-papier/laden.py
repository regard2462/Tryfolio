# -*- coding: utf-8 -*-
"""Laedt die PDFs der Papiermeldungen vom House Clerk (nacheinander, kurze Pausen).
Vorhandene Dateien werden nicht erneut geladen.
Aufruf: python -I laden.py <meldungen.json> <pdf-ordner>"""
import json
import os
import sys
import time
import urllib.request

UA = "Tryfolio/1.0 (open-source practice depot; educational)"


def main():
    liste = json.load(open(sys.argv[1], encoding="utf-8"))
    ordner = sys.argv[2]
    os.makedirs(ordner, exist_ok=True)
    neu = 0
    for m in liste:
        ziel = os.path.join(ordner, m["id"] + ".pdf")
        if os.path.exists(ziel) and os.path.getsize(ziel) > 1000:
            continue
        for versuch in range(4):
            try:
                req = urllib.request.Request(m["url"], headers={"User-Agent": UA})
                with urllib.request.urlopen(req, timeout=60) as r:
                    daten = r.read()
                if not daten.startswith(b"%PDF"):
                    raise ValueError("keine PDF-Datei")
                with open(ziel + ".teil", "wb") as f:
                    f.write(daten)
                os.replace(ziel + ".teil", ziel)
                neu += 1
                print("ok", m["id"], len(daten), flush=True)
                break
            except Exception as e:  # noqa: BLE001
                print("Fehler", m["id"], e, flush=True)
                time.sleep(5 * (versuch + 1))
        time.sleep(1.0)
    print("neu geladen:", neu)


if __name__ == "__main__":
    main()
