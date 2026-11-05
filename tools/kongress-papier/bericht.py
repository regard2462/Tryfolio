# -*- coding: utf-8 -*-
"""Schreibt bericht.md: je Abgeordnetem Meldungen, Seiten, gelesene Geschaefte, unlesbare Zeilen und Anteil mit
Kuerzel, dazu die Gruende ohne Geschaefte und die Auswertung der Stichprobe (Fehlerquote je Feld).
Aufruf: python -I bericht.py <meldungen.json> <papier.json> <stichprobe.json> <stichprobe-ergebnis.json> <ziel.md>
        [<details.json>] [<zusatz.md>]
Der Zusatz (von Hand geschriebene Abschnitte, etwa Pruefung der Kuerzel und offene Probleme) wird angehaengt."""
import collections
import json
import os
import sys

FELDER = [("i", "Inhaber"), ("w", "Name"), ("a", "Art"), ("d", "Datum"), ("g", "Benachrichtigung"),
          ("b", "Betragsspalte")]


def prozent(a, b):
    return "%.1f %%" % (100.0 * a / b) if b else "-"


def main():
    liste = json.load(open(sys.argv[1], encoding="utf-8"))
    papier = json.load(open(sys.argv[2], encoding="utf-8"))
    probe = json.load(open(sys.argv[3], encoding="utf-8"))
    ergebnis = json.load(open(sys.argv[4], encoding="utf-8"))
    details = json.load(open(sys.argv[6], encoding="utf-8")) if len(sys.argv) > 6 else {}
    ziel = sys.argv[5]
    per = collections.OrderedDict()
    for m in sorted(liste, key=lambda m: m["name"]):
        e = papier["meldungen"][m["id"]]
        p = per.setdefault(m["name"], collections.Counter())
        p["meldungen"] += 1
        p["seiten"] += e["seiten"]
        p["geschaefte"] += len(e["geschaefte"])
        p["unlesbar"] += e["unlesbar"]
        p["kuerzel"] += sum(1 for g in e["geschaefte"] if g.get("k"))
    z = []
    z.append("# Papiermeldungen (PTR) des Repraesentantenhauses: Auswertung")
    z.append("")
    z.append("Stand %s. Eingescannte Periodic Transaction Reports aus den Jahresverzeichnissen 2025 und 2026 des "
             "House Clerk (FilingType P, DocID nicht elektronisch). Ergebnis: `papier.json`." % papier["stand"])
    z.append("")
    gesamt = collections.Counter()
    for p in per.values():
        gesamt.update(p)
    z.append("## Je Abgeordnetem")
    z.append("")
    z.append("| Abgeordnete(r) | Meldungen | Seiten | Geschaefte gelesen | unlesbare Zeilen | Anteil lesbar | mit Kuerzel |")
    z.append("|---|---:|---:|---:|---:|---:|---:|")
    for name, p in per.items():
        z.append("| %s | %d | %d | %d | %d | %s | %s |" % (
            name, p["meldungen"], p["seiten"], p["geschaefte"], p["unlesbar"],
            prozent(p["geschaefte"], p["geschaefte"] + p["unlesbar"]), prozent(p["kuerzel"], p["geschaefte"])))
    z.append("| **zusammen** | %d | %d | %d | %d | %s | %s |" % (
        gesamt["meldungen"], gesamt["seiten"], gesamt["geschaefte"], gesamt["unlesbar"],
        prozent(gesamt["geschaefte"], gesamt["geschaefte"] + gesamt["unlesbar"]),
        prozent(gesamt["kuerzel"], gesamt["geschaefte"])))
    z.append("")
    arten = collections.Counter(g["t"] for e in papier["meldungen"].values() for g in e["geschaefte"])
    z.append("Arten der Wertpapiere: " + ", ".join("%s %d" % kv for kv in arten.most_common()) + ".")
    ohne_k = collections.Counter(g["t"] for e in papier["meldungen"].values() for g in e["geschaefte"]
                                 if not g.get("k"))
    z.append("Ohne Kuerzel: " + ", ".join("%s %d" % kv for kv in ohne_k.most_common()) + ".")
    z.append("")
    z.append("## Meldungen ohne gelesene Geschaefte")
    z.append("")
    m_name = {m["id"]: m["name"] for m in liste}
    for mid, e in sorted(papier["meldungen"].items(), key=lambda kv: (m_name[kv[0]], kv[0])):
        if not e["geschaefte"]:
            z.append("- %s, %s (%d Seiten): %s%s" % (m_name[mid], mid, e["seiten"], e.get("grund", "-"),
                                                    ", %d unlesbare Zeilen" % e["unlesbar"] if e["unlesbar"] else ""))
    z.append("")
    if details:
        quellen = collections.Counter()
        for d in details.values():
            quellen.update(d.get("quellen", {}))
        z.append("## Herkunft der Geschaefte")
        z.append("")
        z.append("- automatisch gelesen (Pixel der Kreuzzellen, Windows-OCR fuer Text und Daten): %d" % quellen.get("ocr", 0))
        z.append("- automatisch gelesen, ein oder mehrere Felder am Seitenbild berichtigt oder bestaetigt: %d"
                 % quellen.get("ocr+hand", 0))
        z.append("- ganz von Hand am Seitenbild gelesen (handschriftliche Formulare, Briefe): %d" % quellen.get("hand", 0))
        z.append("")
        gruende = collections.Counter()
        for d in details.values():
            for u in d.get("unlesbar", []):
                gruende[u["probleme"][0] if u["probleme"] else "?"] += 1
        z.append("## Gruende fuer unlesbare Zeilen")
        z.append("")
        for g, n in gruende.most_common():
            z.append("- %s: %d" % (g, n))
        z.append("")
    z.append("## Stichprobe")
    z.append("")
    z.append("%d Zeilen, zufaellig aus den rein automatisch gelesenen Geschaeften (ohne jede Handkorrektur) gezogen "
             "(fester Startwert), am Seitenbild geprueft (`skript/pruefung/stichprobe-wahr.json`). Gezaehlt wird je "
             "Feld, wie oft der Wert im Ergebnis vom Seitenbild abweicht. Namen zaehlen als richtig, wenn Buchstaben "
             "und Ziffern uebereinstimmen; eine fehlende Benachrichtigung zaehlt nicht als Fehler, eine falsche schon."
             % len(probe))
    z.append("")
    fehler = collections.Counter()
    for e in ergebnis:
        for f in e.get("fehler", []):
            fehler[f] += 1
    z.append("| Feld | Fehler | Quote richtig |")
    z.append("|---|---:|---:|")
    for k, name in FELDER:
        z.append("| %s | %d | %s |" % (name, fehler[k], prozent(len(ergebnis) - fehler[k], len(ergebnis))))
    z.append("")
    anm = [e for e in ergebnis if e.get("fehler") or e.get("anm")]
    if anm:
        z.append("Abweichungen und Anmerkungen:")
        z.append("")
        for e in anm:
            z.append("- Nr. %d (%s, Seite %d): %s%s" % (e["nr"], e["id"], e["s"],
                                                     ", ".join(dict(FELDER)[f] for f in e.get("fehler", [])) or "kein Fehler",
                                                     " – " + e["anm"] if e.get("anm") else ""))
        z.append("")
    if len(sys.argv) > 7 and os.path.exists(sys.argv[7]):
        z.append(open(sys.argv[7], encoding="utf-8").read().rstrip("\n"))
    open(ziel, "w", encoding="utf-8", newline="\n").write("\n".join(z) + "\n")
    print("ok", ziel)


if __name__ == "__main__":
    main()
