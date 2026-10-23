# Lobbying-Meldungen (LD-2) des Repraesentantenhauses je Firma aus dem Katalog.
# Quelle: Quartalsdateien des Clerk of the House unter disclosurespreview.house.gov/data/LD (XML in ZIP, oeffentlich).
# Die Schnittstelle von LDA.gov sperrt Abrufe von ausserhalb der USA, die Dateien des Hauses nicht.
#
#   python lobbying.py <katalog.json> <zwischenordner> <ziel.json> [jahre]
#
# Je Firma und Quartal der Betrag: Meldet die Firma selbst (eigene Lobbyisten), gilt deren Angabe expenses, die laut
# LDA-Leitfaden auch Zahlungen an beauftragte Lobbyfirmen enthaelt. Sonst die Summe der income-Angaben der Lobbyfirmen.
# Bei Aenderungsmeldungen zaehlt die neueste je Lobbyfirma, Klient und Quartal.
import datetime
import io
import json
import os
import re
import sys
import urllib.request
import zipfile
from collections import Counter, defaultdict

from namen import index_aufbauen, katalog_us, treffer

BASIS = "https://disclosurespreview.house.gov/data/LD/"
KENNUNG = {"User-Agent": "Tryfolio (open-source practice depot)"}
QUARTAL = {"1st": 1, "2nd": 2, "3rd": 3, "4th": 4}
GESETZ = re.compile(r"\b(H\.\s?R\.|H\.\s?Res\.|S\.\s?Res\.|S\.)\s?(\d{1,5})\b")


def holen(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers=KENNUNG), timeout=600).read()


def feld(x, n):
    m = re.search(rf"<{n}>(.*?)</{n}>", x, re.S)
    return m.group(1).strip() if m else ""


def text(s):
    return (s.replace("&amp;", "&").replace("&apos;", "'").replace("&quot;", '"').replace("&lt;", "<").replace("&gt;", ">")
            .replace("\r", " ").replace("\n", " ").strip())


def betrag(s):
    try:
        return float(s.replace(",", "")) if s else 0.0
    except ValueError:
        return 0.0


def gesetz(art, nr):
    art = art.replace(" ", "")
    return {"H.R.": "H.R.", "H.Res.": "H.Res.", "S.Res.": "S.Res.", "S.": "S."}.get(art, art) + " " + nr


def main():
    katalog, zwischen, ziel = sys.argv[1], sys.argv[2], sys.argv[3]
    heute = datetime.date.today()
    jahre = [int(j) for j in sys.argv[4].split(",")] if len(sys.argv) > 4 else [heute.year - 1, heute.year]
    os.makedirs(zwischen, exist_ok=True)
    firmen = katalog_us(katalog)
    idx = index_aufbauen(firmen)
    liste = json.loads(holen(BASIS + "LdSearchPastFilings.json"))
    dateien = []
    for e in liste:
        m = re.match(r"(\d{4})_(1st|2nd|3rd|4th)Quarter_XML\.zip$", e["file"])
        if m and int(m.group(1)) in jahre:
            dateien.append((int(m.group(1)), QUARTAL[m.group(2)], e["file"]))
    # (Lobbyfirma, Klient, Jahr, Quartal) -> neueste Meldung
    meldungen = {}
    for jahr, q, datei in sorted(dateien):
        pfad = os.path.join(zwischen, datei)
        if not os.path.exists(pfad):
            print("lade", datei, flush=True)
            open(pfad, "wb").write(holen(BASIS + datei))
        z = zipfile.ZipFile(pfad)
        n = 0
        for name in z.namelist():
            if not name.endswith(".xml"):
                continue
            x = z.read(name).decode("utf-8", "replace")
            klient = text(feld(x, "clientName"))
            symbole = treffer(klient, idx)
            if not symbole:
                continue
            typ = feld(x, "reportType")
            if not re.match(r"^(Q[1-4]|[1-4]T)A?$", typ):
                continue
            firma = text(feld(x, "organizationName"))
            selbst = feld(x, "selfSelect") == "Y"
            unterschrieben = feld(x, "signedDate")
            try:
                zeit = datetime.datetime.strptime(unterschrieben, "%m/%d/%Y %I:%M:%S %p")
            except ValueError:
                zeit = datetime.datetime(jahr, 1, 1)
            themen, beschreibungen, gesetze, lobbyisten = [], [], set(), 0
            for ali in re.findall(r"<ali_info>(.*?)</ali_info>", x, re.S):
                code = feld(ali, "issueAreaCode")
                if code:
                    themen.append(code)
                b = text(feld(ali, "description"))
                if b:
                    beschreibungen.append(b)
                    gesetze |= {gesetz(a, nr) for a, nr in GESETZ.findall(b)}
                lobbyisten += sum(1 for l in re.findall(r"<lobbyistLastName>(.*?)</lobbyistLastName>", ali, re.S) if l.strip())
            schluessel = (firma.upper(), klient.upper(), jahr, q)
            alt = meldungen.get(schluessel)
            if alt is None or zeit >= alt["zeit"]:
                meldungen[schluessel] = {
                    "zeit": zeit, "symbole": symbole, "selbst": selbst, "firma": firma,
                    "betrag": betrag(feld(x, "expenses")) if selbst else betrag(feld(x, "income")),
                    "themen": themen, "beschreibungen": beschreibungen, "gesetze": gesetze, "lobbyisten": lobbyisten,
                }
            n += 1
        print(datei, n, "Treffer", flush=True)

    aus = defaultdict(lambda: {"quartale": defaultdict(lambda: {"selbst": 0.0, "fremd": 0.0, "firmen": set()}),
                               "themen": Counter(), "gesetze": Counter(), "beispiele": []})
    for (firma, klient, jahr, q), m in meldungen.items():
        for s in m["symbole"]:
            a = aus[s]
            qt = a["quartale"][(jahr, q)]
            if m["selbst"]:
                qt["selbst"] += m["betrag"]
            else:
                qt["fremd"] += m["betrag"]
                qt["firmen"].add(m["firma"])
            a["themen"].update(set(m["themen"]))
            a["gesetze"].update(m["gesetze"])
            for b in m["beschreibungen"]:
                if len(a["beispiele"]) < 400:
                    a["beispiele"].append((jahr, q, b))

    ergebnis = {}
    for s, a in aus.items():
        quartale = []
        for (jahr, q), qt in sorted(a["quartale"].items()):
            # Eigenmeldung schliesst Zahlungen an Lobbyfirmen ein; ohne sie zaehlen die Lobbyfirmen
            summe = qt["selbst"] if qt["selbst"] > 0 else qt["fremd"]
            quartale.append({"j": jahr, "q": q, "b": round(summe), "f": len(qt["firmen"])})
        if not any(x["b"] > 0 for x in quartale):
            continue
        neueste = sorted(a["beispiele"], key=lambda t: (t[0], t[1]), reverse=True)
        beispiele, gesehen = [], set()
        for _, _, b in neueste:
            k = b[:60].upper()
            if k in gesehen:
                continue
            gesehen.add(k)
            beispiele.append(b[:400])
            if len(beispiele) == 4:
                break
        ergebnis[s] = {
            "quartale": quartale,
            "themen": [{"c": c, "n": n} for c, n in a["themen"].most_common(8)],
            "gesetze": [{"c": g, "n": n} for g, n in a["gesetze"].most_common(12)],
            "beispiele": beispiele,
        }
    json.dump({"format": 1, "stand": heute.isoformat(), "firmen": ergebnis}, open(ziel, "w", encoding="utf-8"),
              ensure_ascii=False, separators=(",", ":"))
    print(len(ergebnis), "Firmen mit Lobbying", flush=True)


if __name__ == "__main__":
    main()
