# -*- coding: utf-8 -*-
"""Laesst winocr.ps1 (Windows-OCR, lokal) ueber alle Unterordner eines Ordners laufen, drei Prozesse gleichzeitig,
ohne Fenster. Vorhandene OCR-Ergebnisse (JSON neben dem PNG) werden uebersprungen.
Aufruf: python -I ocr_ordner.py <wurzel> [unterordner ...]"""
import os
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor

HIER = os.path.dirname(os.path.abspath(__file__))
KEIN_FENSTER = 0x08000000


def lauf(ordner):
    r = subprocess.run(["powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File",
                        os.path.join(HIER, "winocr.ps1"), ordner], capture_output=True, text=True,
                       creationflags=KEIN_FENSTER)
    return ordner, r.returncode, (r.stdout + r.stderr).strip()[-300:]


def main():
    wurzel = sys.argv[1]
    nur = set(sys.argv[2:])
    ordner = [os.path.join(wurzel, d) for d in sorted(os.listdir(wurzel))
              if os.path.isdir(os.path.join(wurzel, d)) and (not nur or d in nur)]
    with ThreadPoolExecutor(3) as ex:
        for o, rc, txt in ex.map(lauf, ordner):
            print(os.path.basename(o), rc, txt.replace("\n", " ")[-160:], flush=True)


if __name__ == "__main__":
    main()
