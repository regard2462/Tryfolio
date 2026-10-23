# Liest die Jahresmeldung des Praesidenten (OGE Form 278e) aus: Teil 6 (Bestaende und Ertraege) und Teil 7 (Geschaefte).
# Das Formular hat eine Textebene; die Zeilen entstehen aus den Wortpositionen (Spalten nach den Kopfzeilen der Seite).
# Aufruf: python -I jahresmeldung.py <278e.pdf> <ziel.json>
# Ergebnis: {"teil6": [{konto, nr, n, wert}], "teil7": [{konto, nr, n, a, d, von, bis}], "probleme": [...]}

import json
import re
import sys

import fitz

PDF, ZIEL = sys.argv[1:3]
sys.stdout.reconfigure(encoding="utf-8")

BETRAG = re.compile(r"\$?([\d,]+)\s*-\s*\$?([\d,]+)")
UEBER = re.compile(r"Over\s+\$?([\d,]+)", re.I)
DATUM = re.compile(r"^(\d{1,2})/(\d{1,2})/(\d{4})$")
NR = re.compile(r"^\d+(\.\d+)?$")


def spanne(text):
    """Wertspanne in Dollar: (von, bis); bis None bei „Over …“; None ohne Betrag."""
    t = text.replace(" ", "")
    if re.search(r"None\(orlessthan\$1,001\)", t, re.I):
        return (0, 1000)
    m = BETRAG.search(text)
    if m:
        return (int(m.group(1).replace(",", "")), int(m.group(2).replace(",", "")))
    m = UEBER.search(text)
    if m:
        return (int(m.group(1).replace(",", "")) + 1, None)
    return None


def spalten(woerter, namen):
    """x-Beginn der Spalten aus der Kopfzeile der Tabelle ('#', 'Description', ...): nur Woerter auf der Hoehe von
    'Description', sonst greift etwa 'Income' aus der Ueberschrift 'Part 6: Other Assets and Income'."""
    beschreibung = [w for w in woerter if w[4] == "Description"]
    if not beschreibung:
        return {}
    y = beschreibung[0][1]
    kopf = {}
    for x0, y0, x1, y1, t, *_ in sorted(woerter, key=lambda w: w[0]):
        if t in namen and t not in kopf and abs(y0 - y) < 6:
            kopf[t] = (x0, y0)
    return kopf


def zeilen(seite, teil):
    w = seite.get_text("words")
    if teil == 7:
        k = spalten(w, ("#", "Description", "Type", "Date", "Amount"))
        if len(k) < 5:
            return None, []
        grenzen = [("nr", -1), ("n", k["Description"][0] - 4), ("a", k["Type"][0] - 4), ("d", k["Date"][0] - 4), ("betrag", k["Amount"][0] - 4)]
    else:
        k = spalten(w, ("#", "Description", "EIF", "Value", "Income"))
        if len(k) < 5:
            return None, []
        grenzen = [("nr", -1), ("n", k["Description"][0] - 4), ("eif", k["EIF"][0] - 4), ("wert", k["Value"][0] - 4), ("ertrag", k["Income"][0] - 4)]
    oben = k["#"][1] + 4
    # bis an den unteren Rand: die letzte Zeile einer Seite steht knapp darueber (Querformat, 612 pt hoch)
    tabelle = [x for x in w if x[1] > oben and not x[4].startswith("OGE") and x[3] < seite.rect.height - 4]
    breite_nr = k["Description"][0] - 4

    def spalte(x0):
        s = grenzen[0][0]
        for name, xb in grenzen:
            if x0 >= xb:
                s = name
        return s

    anker = sorted([(x[1], x[3], x[4]) for x in tabelle if x[0] < breite_nr and NR.match(x[4])], key=lambda a: a[0])
    reihen = {a[2]: {"y": (a[0] + a[1]) / 2} for a in anker}
    kopfzeilen = []
    for x0, y0, x1, y1, t, *_ in tabelle:
        if x0 < breite_nr and NR.match(t):
            continue
        mitte = (y0 + y1) / 2
        naechster = min(anker, key=lambda a: abs((a[0] + a[1]) / 2 - mitte), default=None)
        if naechster is None or abs((naechster[0] + naechster[1]) / 2 - mitte) > 9:
            kopfzeilen.append((y0, x0, t))
            continue
        r = reihen[naechster[2]]
        r.setdefault(spalte(x0), []).append((y0, x0, t))
    aus = []
    for nr, r in reihen.items():
        z = {"nr": nr, "y": r["y"]}
        for feld, teile in r.items():
            if feld in ("y", "nr"):
                continue
            z[feld] = " ".join(t for _, _, t in sorted(teile))
        aus.append(z)
    return sorted(kopfzeilen), sorted(aus, key=lambda z: z["y"])


doc = fitz.open(PDF)
teil6, teil7, probleme = [], [], []
konto = {6: "", 7: ""}
for i, seite in enumerate(doc):
    text = seite.get_text()
    teil = 7 if "Part 7: Transactions" in text else 6 if "Part 6: Other Assets and Income" in text else None
    if teil is None:
        continue
    kopf, reihen = zeilen(seite, teil)
    if kopf is None:
        probleme.append({"seite": i + 1, "grund": "keine Kopfzeile"})
        continue
    # Zwischenueberschriften (Konten) ueber den Zeilen: Woerter ausserhalb jeder Zeile, nach Hoehe gruppiert
    ueber = {}
    for y0, x0, t in kopf:
        ueber.setdefault(round(y0 / 6), []).append((x0, t))
    titel = [(k * 6, " ".join(t for _, t in sorted(v))) for k, v in sorted(ueber.items())]
    for z in reihen:
        for y, t in titel:
            if y < z["y"] and "Instructions" not in t and "Filer" not in t:
                konto[teil] = t
        if teil == 7:
            a = (z.get("a") or "").strip().lower()
            m = DATUM.match((z.get("d") or "").strip())
            sp = spanne(z.get("betrag") or "")
            eintrag = {"konto": konto[teil], "nr": z["nr"], "n": (z.get("n") or "").strip(), "a": a,
                       "d": f"{m.group(3)}-{int(m.group(1)):02d}-{int(m.group(2)):02d}" if m else "",
                       "von": sp[0] if sp else None, "bis": sp[1] if sp else None, "seite": i + 1}
            if not eintrag["n"] or not m or sp is None or a not in ("purchase", "sale", "exchange", "sale (partial)"):
                probleme.append({"seite": i + 1, "zeile": z})
            teil7.append(eintrag)
        else:
            sp = spanne(z.get("wert") or "")
            teil6.append({"konto": konto[teil], "nr": z["nr"], "n": (z.get("n") or "").strip(), "wert": z.get("wert", ""),
                          "von": sp[0] if sp else None, "bis": sp[1] if sp else None, "ertrag": z.get("ertrag", ""), "seite": i + 1})

# Lueckenpruefung: Die Zeilen tragen je Depot eine fortlaufende Nummer; fehlt eine, hat das Lesen eine Zeile verloren
for name, liste in (("teil6", teil6), ("teil7", teil7)):
    je = {}
    for z in liste:
        if "." not in z["nr"]:
            k = re.sub(r"\s+", " ", z["konto"].upper().replace("#", " #")).replace("# ", "#").strip()
            je.setdefault(k, []).append(int(z["nr"]))
    for k, nrs in sorted(je.items()):
        fehlt = sorted(set(range(min(nrs), max(nrs) + 1)) - set(nrs))
        doppelt = len(nrs) - len(set(nrs))
        if fehlt or doppelt:
            probleme.append({"teil": name, "konto": k, "fehlt": fehlt[:50], "doppelt": doppelt})
        print(f"{name} {k}: {len(nrs)} Zeilen, fehlen {len(fehlt)}, doppelt {doppelt}")

json.dump({"teil6": teil6, "teil7": teil7, "probleme": probleme}, open(ZIEL, "w", encoding="utf-8"), ensure_ascii=False, indent=0)
print("Teil 6:", len(teil6), "Zeilen | Teil 7:", len(teil7), "Geschaefte | Probleme:", len(probleme))
