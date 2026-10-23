# -*- coding: utf-8 -*-
"""Waehlt aus den offenen Namen (kuerzel_papier.py vorbereiten) die aus, die fuer eine Kuerzelsuche taugen:
mindestens ein Wort mit drei oder mehr Zeichen, das kein Rechtsform- oder Gattungszusatz und keine reine Zahl ist.
Aufruf: python -I suchliste.py <offen.json> <suche-cache.json> <offen-suche.json>"""
import json
import os
import re
import sys

ZUSATZ = set("INC INCORPORATED CORP CORPORATION CO COS COMPANY COMPANIES LTD LIMITED PLC NV SA AG SE AB ASA LP LLC "
             "HLDGS HOLDINGS HLDG HOLDING GROUP GRP THE NEW CL CLASS COM COMMON STK STOCK SHS SH ORD REIT DEL TR ADR "
             "ADS SPONSORED SPON INTL AND OF PUBLIC GLOBAL CMN".split())


def main():
    offen = json.load(open(sys.argv[1], encoding="utf-8"))["offen"]
    gut = {}
    for n, k in offen.items():
        w = [x for x in re.sub(r"[^A-Z0-9]+", " ", n.upper()).split()
             if x not in ZUSATZ and len(x) >= 3 and not x.isdigit()]
        if w:
            gut[n] = k
    json.dump({"offen": gut}, open(sys.argv[3], "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    cache = json.load(open(sys.argv[2], encoding="utf-8")) if os.path.exists(sys.argv[2]) else {}
    print("offen:", len(offen), "fuer die Suche:", len(gut), "neu (nicht im Zwischenspeicher):",
          sum(1 for n in gut if n not in cache))


if __name__ == "__main__":
    main()
