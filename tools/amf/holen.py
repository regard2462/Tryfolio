# -*- coding: utf-8 -*-
"""Netto-Leerverkaufspositionen an der Pariser Boerse fuer die App (assets/copytrading/leerpositionen_fr.json).

Quelle ist die Liste der Autorite des marches financiers (AMF) auf data.gouv.fr unter der Licence Ouverte 2.0: alle
veroeffentlichten Positionen ab 0,5 % des Grundkapitals nach der EU-Leerverkaufsverordnung, ohne Anmeldung. Uebernommen
werden je Aktie die offenen Positionen (Halter, Anteil, Beginn) und die Summe der veroeffentlichten Positionen fuer jede
Woche des letzten Jahres. Die App rechnet dasselbe aus der Datei, wenn sie neuere Daten holt (Leerpositionen.kt).

Aufruf: python holen.py <ziel.json>
"""
import bisect
import csv
import datetime as dt
import io
import json
import sys
import urllib.request

ADRESSE = "https://www.data.gouv.fr/api/1/datasets/r/c2539d1c-8531-4937-9cba-3bd8e9786cc5"
UA = {"User-Agent": "Tryfolio/1.0 (open-source practice depot)"}
WOCHEN = 52


def auswerten(text, heute):
    zeilen = list(csv.reader(io.StringIO(text.lstrip("﻿")), delimiter=";"))
    kopf = [k.strip() for k in zeilen[0]]
    i_h, i_e, i_r, i_isin, i_beginn, i_ende = (kopf.index(k) for k in (
        "Detenteur de la position courte nette", "Emetteur / issuer", "Ratio", "code ISIN",
        "Date de debut position", "Date de fin de publication position"))
    grenze = (heute - dt.timedelta(days=7 * WOCHEN + 7)).isoformat()
    reihen = {}
    namen = {}
    for z in zeilen[1:]:
        if len(z) <= max(i_h, i_e, i_r, i_isin, i_beginn, i_ende):
            continue
        ende = z[i_ende].strip()
        if ende and ende < grenze:
            continue
        try:
            r = float(z[i_r].replace(",", "."))
        except ValueError:
            continue
        isin = z[i_isin].strip()
        namen[isin] = z[i_e].strip()
        reihen.setdefault((isin, z[i_h].strip()), []).append((z[i_beginn].strip()[:10], r, ende))
    tage = [(heute - dt.timedelta(days=7 * w)).isoformat() for w in range(WOCHEN, -1, -1)]
    titel = {}
    for (isin, halter), liste in reihen.items():
        liste.sort()
        starts = [x[0] for x in liste]
        t = titel.setdefault(isin, {"e": namen[isin], "positionen": [], "summen": [0.0] * len(tage)})
        for j, tag in enumerate(tage):
            k = bisect.bisect_right(starts, tag) - 1
            if k < 0:
                continue
            beginn, r, ende = liste[k]
            if not ende or ende > tag:
                t["summen"][j] += r
        letzte = liste[-1]
        if not letzte[2]:
            t["positionen"].append({"h": halter, "r": letzte[1], "d": letzte[0]})
    aus = {}
    for isin, t in titel.items():
        if not t["positionen"] and not any(t["summen"]):
            continue
        t["positionen"].sort(key=lambda p: -p["r"])
        aus[isin] = {"e": t["e"], "positionen": t["positionen"],
                     "verlauf": [{"t": tag, "s": round(s, 2)} for tag, s in zip(tage, t["summen"])]}
    return aus


def main():
    with urllib.request.urlopen(urllib.request.Request(ADRESSE, headers=UA), timeout=180) as r:
        text = r.read().decode("utf-8")
    heute = dt.date.today()
    titel = auswerten(text, heute)
    with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
        json.dump({"stand": heute.isoformat(), "titel": titel}, f, ensure_ascii=False, separators=(",", ":"))
    print("fertig:", len(titel), "Aktien,", sum(len(t["positionen"]) for t in titel.values()), "offene Positionen", file=sys.stderr)


if __name__ == "__main__":
    main()
