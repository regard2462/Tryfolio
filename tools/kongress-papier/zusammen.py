# -*- coding: utf-8 -*-
"""Fuehrt die gelesenen Zeilen, die Handpruefung (hand.json), die bereinigten Namen und die Kuerzel zur Ergebnisdatei
zusammen. Zeilen mit offenem Befund, die nicht von Hand geklaert sind, zaehlen als unlesbar.
Schritt 1: python -I zusammen.py namen <meldungen.json> <cache-ordner> <hand.json> <namen-bereinigt.json> <namen-liste.json>
           (Liste aller endgueltigen Namen mit Anzahl, Eingabe fuer kuerzel_papier.py)
Schritt 2: python -I zusammen.py bauen <meldungen.json> <cache-ordner> <hand.json> <namen-bereinigt.json> <kuerzel.json>
           <stand JJJJ-MM-TT> <ziel.json> [<details.json>]"""
import collections
import datetime
import json
import os
import re
import sys

SPANNEN = {"A": (1001, 15000), "B": (15001, 50000), "C": (50001, 100000), "D": (100001, 250000),
           "E": (250001, 500000), "F": (500001, 1000000), "G": (1000001, 5000000), "H": (5000001, 25000000),
           "I": (25000001, 50000000), "J": (50000001, None), "K": (1000001, None)}
ARTEN = {"KAUF", "VERKAUF", "TEILVERKAUF", "TAUSCH"}
NICHTS = re.compile(r"(?i)nothing\s+to\s+report|no\s+transactions\s+to\s+report")


def iso_ok(d):
    try:
        datetime.date.fromisoformat(d)
        return True
    except (TypeError, ValueError):
        return False


def inhaber_ergaenzen(zeilen):
    """Inhaber „?“: Steht im Abschnitt (zwischen zwei Depot-Zwischenzeilen, ueber Seitengrenzen hinweg) oder in der
    ganzen Meldung nur ein einziger sicher gelesener Inhaber, gilt er auch fuer diese Zeile; sonst bleibt er offen."""
    abschnitt, nr = {}, 0
    for r in sorted(zeilen, key=lambda r: (r["s"], r["z"])):
        if r.get("befund") == "zwischenzeile":
            nr += 1
        abschnitt[(r["s"], r["z"])] = nr
    sicher = collections.defaultdict(set)
    alle = set()
    for r in zeilen:
        if r.get("befund") in ("geschaeft", "pruefen") and r.get("inhaber") in ("SP", "DC", "JT", ""):
            sicher[abschnitt[(r["s"], r["z"])]].add(r["inhaber"])
            alle.add(r["inhaber"])
    for r in zeilen:
        if r.get("befund") in ("geschaeft", "pruefen") and r.get("inhaber") not in ("SP", "DC", "JT", ""):
            werte = sicher.get(abschnitt[(r["s"], r["z"])], set()) - {""}
            if len(werte) == 1:
                r["inhaber"] = next(iter(werte))
                r["inhaber_quelle"] = "abschnitt"
            elif not werte and len(alle - {""}) == 1:
                r["inhaber"] = next(iter(alle - {""}))
                r["inhaber_quelle"] = "meldung"


NAMEN_GUETE = {}


def guete_laden(pfad_bereinigt):
    """Anteil bekannter Woerter je Name (von namen.py neben namen-bereinigt.json abgelegt)."""
    p = os.path.splitext(pfad_bereinigt)[0] + "-guete.json"
    if os.path.exists(p):
        NAMEN_GUETE.update(json.load(open(p, encoding="utf-8")))


def zeilen_der_meldung(m, cache, hand, bereinigt):
    """Liefert (geschaefte ohne Kuerzel, unlesbar, grund, details)."""
    h = hand.get(m["id"], {})
    if "ersetzen" in h:
        aus = []
        for e in h["ersetzen"]:
            x = {"i": e.get("i", ""), "w": e["w"], "a": e["a"], "d": e["d"], "betrag": e["b"], "quelle": "hand",
                 "s": e.get("s")}
            if e.get("g"):
                x["g"] = e["g"]
            for k in ("k", "t"):
                if k in e:
                    x[k] = e[k]
            aus.append(x)
        return aus, h.get("unlesbar", 0), h.get("grund"), []
    p = os.path.join(cache, "zeilen", m["id"] + ".json")
    zeilen = json.load(open(p, encoding="utf-8"))["zeilen"] if os.path.exists(p) else []
    inhaber_ergaenzen(zeilen)
    korr = h.get("korrekturen", {})
    aus, unlesbar, details = [], 0, []
    # Jahreslesefehler der OCR (26 als 25): Daten weit vor dem Mittel der Meldung pruefen
    tage = sorted(r["datum"] for r in zeilen if r.get("befund") in ("geschaeft", "pruefen") and r.get("datum"))
    mitte = datetime.date.fromisoformat(tage[len(tage) // 2]) if tage else None
    for r in zeilen:
        schl = "%d/%d" % (r["s"], r["z"])
        k = korr.get(schl, {})
        if r.get("befund") not in ("geschaeft", "pruefen") and not k.get("aufnehmen"):
            continue
        if k.get("streichen"):
            continue
        name = bereinigt.get("%s/%d/%d" % (m["id"], r["s"], r["z"]),
                             bereinigt.get(r.get("name_roh", ""), r.get("name_roh", ""))).strip()
        # unlesbare Zeichen sichtbar als „?“
        name = " ".join(name.replace("�", "?").split())
        x = {"i": r.get("inhaber") if r.get("inhaber") in ("SP", "DC", "JT", "") else "?", "w": name,
             "a": r.get("art"), "d": r.get("datum"), "betrag": r.get("betrag"), "quelle": "ocr", "s": r["s"],
             "z": r["z"]}
        if r.get("gemeldet"):
            x["g"] = r["gemeldet"]
        offen = list(r.get("probleme", []))
        if name and NAMEN_GUETE.get("%s/%d/%d" % (m["id"], r["s"], r["z"]), 1.0) < 0.6:
            offen.append("name unsicher")
        if mitte and r.get("datum") and (mitte - datetime.date.fromisoformat(r["datum"])).days > 150:
            offen.append("datum weit vor den uebrigen der Meldung")
        if k:
            x["quelle"] = "ocr+hand"
            for feld, ziel in (("i", "i"), ("w", "w"), ("a", "a"), ("d", "d"), ("b", "betrag"), ("g", "g")):
                if feld in k:
                    x[ziel] = k[feld]
            if "g" in k and not k["g"]:
                x.pop("g", None)
            if k.get("ok") or any(f in k for f in ("d", "a", "b", "w")):
                offen = [p for p in offen
                         if not ((p.startswith("datum") or p.startswith("benachrichtigung")) and ("d" in k or k.get("ok")))
                         and not (p.startswith("art") and ("a" in k or k.get("ok")))
                         and not (p.startswith("betrag") and ("b" in k or k.get("ok")))
                         and not (p.startswith("name") and ("w" in k or k.get("ok")))]
            if k.get("unlesbar"):
                offen = offen or ["von Hand unlesbar"]
        gueltig = (x["a"] in ARTEN and x["betrag"] in SPANNEN and iso_ok(x["d"]) and x["w"]
                   and x["i"] in ("SP", "DC", "JT", ""))
        if offen or not gueltig or k.get("unlesbar"):
            unlesbar += 1
            details.append({"s": r["s"], "z": r["z"], "probleme": offen or ["unvollstaendig"], "w": name})
            continue
        aus.append(x)
    for e in h.get("zusatz", []):
        x = {"i": e.get("i", ""), "w": e["w"], "a": e["a"], "d": e["d"], "betrag": e["b"], "quelle": "hand",
             "s": e.get("s")}
        if e.get("g"):
            x["g"] = e["g"]
        for kk in ("k", "t"):
            if kk in e:
                x[kk] = e[kk]
        aus.append(x)
    unlesbar += h.get("unlesbar", 0)
    return aus, unlesbar, h.get("grund"), details


def main():
    modus = sys.argv[1]
    liste = json.load(open(sys.argv[2], encoding="utf-8"))
    cache = sys.argv[3]
    hand = json.load(open(sys.argv[4], encoding="utf-8"))["meldungen"]
    bereinigt = json.load(open(sys.argv[5], encoding="utf-8"))
    guete_laden(sys.argv[5])
    if modus == "namen":
        zaehl = collections.Counter()
        for m in liste:
            g, _, _, _ = zeilen_der_meldung(m, cache, hand, bereinigt)
            for x in g:
                zaehl[x["w"]] += 1
        json.dump(dict(zaehl.most_common()), open(sys.argv[6], "w", encoding="utf-8"), ensure_ascii=False, indent=0)
        print("Namen:", len(zaehl), "Geschaefte:", sum(zaehl.values()))
        return
    kuerzel = json.load(open(sys.argv[6], encoding="utf-8"))
    stand, ziel = sys.argv[7], sys.argv[8]
    details_pfad = sys.argv[9] if len(sys.argv) > 9 else None
    uebersicht = json.load(open(os.path.join(cache, "uebersicht.json"), encoding="utf-8"))
    lage = json.load(open(os.path.join(cache, "lage.json"), encoding="utf-8"))
    meldungen, details = {}, {}
    for m in liste:
        g, unlesbar, grund, det = zeilen_der_meldung(m, cache, hand, bereinigt)
        geschaefte = []
        for x in g:
            ku = kuerzel.get(x["w"], {})
            k = x.get("k", ku.get("k"))
            t = x.get("t", ku.get("t", "OT"))
            v, b = SPANNEN[x["betrag"]]
            e = {"i": x["i"] if x["i"] != "?" else "", "w": x["w"]}
            if k:
                e["k"] = k
            e["t"] = t
            e["a"] = x["a"]
            e["d"] = x["d"]
            if x.get("g"):
                e["g"] = x["g"]
            e["v"] = v
            if b:
                e["b"] = b
            geschaefte.append(e)
        eintrag = {"seiten": uebersicht[m["id"]]["seiten"], "unlesbar": unlesbar, "geschaefte": geschaefte}
        if not grund and not geschaefte and not unlesbar:
            # nichts zu melden? Text der Seiten (OCR der Lageerkennung) pruefen
            texte = []
            for s, v in lage.get(m["id"], {}).items():
                f = os.path.join(cache, "lage", m["id"], "p%03d_r%03d.json" % (int(s), v["drehung"]))
                if os.path.exists(f):
                    js = json.load(open(f, encoding="utf-8-sig"))
                    texte.append(" ".join(z["t"] for z in js["zeilen"]))
            if NICHTS.search(" ".join(texte)):
                grund = "keine Geschaefte gemeldet (Nothing to report)"
            else:
                grund = "keine Geschaeftszeilen erkannt"
        if grund and not geschaefte:
            eintrag["grund"] = grund
        meldungen[m["id"]] = eintrag
        details[m["id"]] = {"name": m["name"], "unlesbar": det, "quellen": dict(collections.Counter(x["quelle"] for x in g))}
    aus = {"format": 1, "stand": stand, "meldungen": meldungen}
    with open(ziel, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(aus, ensure_ascii=False, separators=(",", ":")))
    if details_pfad:
        json.dump(details, open(details_pfad, "w", encoding="utf-8"), ensure_ascii=False, indent=0)
    n = sum(len(e["geschaefte"]) for e in meldungen.values())
    u = sum(e["unlesbar"] for e in meldungen.values())
    mk = sum(1 for e in meldungen.values() for x in e["geschaefte"] if x.get("k"))
    print("Meldungen:", len(meldungen), "Geschaefte:", n, "unlesbar:", u, "mit Kuerzel:", mk)


if __name__ == "__main__":
    main()
