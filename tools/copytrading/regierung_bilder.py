# Freie Profilbilder und Partei fuer die weiteren Mitglieder der US-Regierung (Copytrading, Liste der OGE und des
# Weissen Hauses). Sucht je Person den Eintrag bei Wikidata und nimmt ihn nur, wenn er sicher passt: Mensch, US-Buerger
# oder „American“ in der Beschreibung, lebend, Nachname in der Bezeichnung, ein Amt (P39) oder eine passende
# Beschreibung (Politik, Verwaltung, Recht, Wirtschaft) und ein Bild (P18). Dann die Lizenz der Datei bei Wikimedia
# Commons pruefen (gemeinfrei, CC0, CC BY, CC BY-SA) und eine verkleinerte Fassung unter assets/copytrading/bilder
# ablegen, so wie bei Investoren und Praesident.
# Aufruf: python -I regierung_bilder.py <namen.json> <bilder.json> <bilderordner> <personen.json> [hand.json]
#   namen.json: {kennung: [Anzeigename, Amt, Behoerde]} (Kennung wie RegierungOge.kennung, etwa „bessent-scott“)
#   hand.json:  {kennung: Suchbegriff oder ""} fuer Namen, unter denen die Person bekannter ist ("" = kein Bild)
# Ergebnis: bilder.json bekommt personen[kennung], personen.json bekommt x[kennung] = {"pa": "R"|"D"} (Partei P102).

import io
import json
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request

from PIL import Image

NAMEN, BILDER, ORDNER, PERSONEN = sys.argv[1:5]
HAND = json.load(open(sys.argv[5], encoding="utf-8")) if len(sys.argv) > 5 else {}
sys.stdout.reconfigure(encoding="utf-8", line_buffering=True)

# Allgemeine Kennung ohne Angaben zum Nutzer
UA = "TryfolioBuild/1.0 (Bildnachweise fuer Copytrading)"
FREI = re.compile(r"^(Public domain|PD.*|CC0.*|CC BY(-SA)? \d\.\d.*)$", re.I)
PARTEI = {"Q29468": "R", "Q29552": "D"}
BERUF = re.compile(r"politic|official|advis|lawyer|attorney|government|secretary|administrator|director|business|economist|"
                   r"diplomat|banker|executive|commissioner|chief|staff|investor|financ|officer|aide|strategist|governor|"
                   r"chair|deputy|counsel|judge|prosecutor|ambassador|consultant|press", re.I)


def holen(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for versuch in range(4):
        try:
            with urllib.request.urlopen(req, timeout=40) as r:
                time.sleep(1.2)
                return r.read()
        except urllib.error.HTTPError as e:
            # Zu viele Anfragen: so lange warten, wie der Server sagt (mindestens 30 Sekunden)
            warte = int(e.headers.get("Retry-After", "30") or 30) if e.code == 429 else 5
            print("  Fehler", e.code, "warte", warte)
            time.sleep(max(warte, 30 if e.code == 429 else 5))
        except Exception as e:  # noqa: BLE001
            print("  Fehler", e)
            time.sleep(5)
    return None


def api(basis, **p):
    p["format"] = "json"
    for versuch in range(3):
        b = holen(basis + "?" + urllib.parse.urlencode(p))
        if not b:
            return {}
        r = json.loads(b)
        # Wikidata meldet Drosselung auch als Fehler im Inhalt
        if "error" not in r:
            return r
        print("  API-Fehler", r["error"].get("code"), "warte 30")
        time.sleep(30)
    return {}


def wert(claims, prop):
    return [c["mainsnak"].get("datavalue", {}).get("value") for c in claims.get(prop, []) if c["mainsnak"].get("datavalue")]


def norm(s):
    return "".join(c for c in unicodedata.normalize("NFKD", s).lower() if "a" <= c <= "z")


def eintraege(ids):
    e = api("https://www.wikidata.org/w/api.php", action="wbgetentities", ids="|".join(ids), props="claims|descriptions|sitelinks|labels", languages="en")
    return e.get("entities", {})


def suchen(name, nachname):
    r = api("https://www.wikidata.org/w/api.php", action="wbsearchentities", search=name, language="en", type="item", limit=7)
    ids = [k["id"] for k in r.get("search", [])]
    if not ids:
        return None
    alle = eintraege(ids)
    passend = []
    for i, qid in enumerate(ids):
        e = alle.get(qid)
        if not e:
            continue
        c = e.get("claims", {})
        beschreibung = e.get("descriptions", {}).get("en", {}).get("value", "")
        label = e.get("labels", {}).get("en", {}).get("value", "")
        mensch = any(v.get("id") == "Q5" for v in wert(c, "P31"))
        us = any(v.get("id") == "Q30" for v in wert(c, "P27")) or "American" in beschreibung
        lebend = not c.get("P570")
        amt = bool(c.get("P39"))
        if mensch and us and lebend and wert(c, "P18") and norm(nachname) in norm(label) and (amt or BERUF.search(beschreibung)):
            passend.append((0 if amt else 1, i, e))
    return min(passend, key=lambda t: (t[0], t[1]))[2] if passend else None


def kopf(datei):
    r = api("https://commons.wikimedia.org/w/api.php", action="query", titles="File:" + datei, prop="imageinfo",
            iiprop="url|extmetadata", iiurlwidth=330)
    for s in r.get("query", {}).get("pages", {}).values():
        ii = (s.get("imageinfo") or [None])[0]
        if ii:
            return ii
    return None


def ohne_html(t):
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", t or "")).strip()


def verkleinern(roh):
    """Hoehe 256 Pixel, hoechstens 4:5 breit (Mitte), wie die mitgelieferten Bilder."""
    im = Image.open(io.BytesIO(roh)).convert("RGB")
    w, h = im.size
    if w > h * 0.8:
        neu = int(h * 0.8)
        links = (w - neu) // 2
        im = im.crop((links, 0, links + neu, h))
        w = neu
    im = im.resize((max(1, round(w * 256 / h)), 256), Image.LANCZOS)
    out = io.BytesIO()
    im.save(out, "JPEG", quality=82, optimize=True)
    return out.getvalue()


def sichern():
    """Zwischenstand sichern, falls die Abfragen spaeter abbrechen."""
    with open(BILDER, "w", encoding="utf-8", newline="\n") as f:
        json.dump(bilder, f, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    with open(PERSONEN, "w", encoding="utf-8", newline="\n") as f:
        json.dump(personen, f, ensure_ascii=False, separators=(",", ":"))


namen = json.load(open(NAMEN, encoding="utf-8"))
bilder = json.load(open(BILDER, encoding="utf-8"))
personen = json.load(open(PERSONEN, encoding="utf-8"))
personen.setdefault("x", {})
for kennung, (name, amt, behoerde) in namen.items():
    suche = HAND.get(kennung, name)
    if suche == "":
        continue
    e = suchen(suche, kennung.split("-")[0])
    if not e:
        print("--", kennung, suche, "kein passender Eintrag mit Bild")
        continue
    c = e["claims"]
    # Partei nur aus Angaben ohne Enddatum (P582) und nur, wenn sie eindeutig ist; Parteiwechsel bleiben so aussen vor
    partei = {PARTEI.get(v["mainsnak"].get("datavalue", {}).get("value", {}).get("id")) for v in c.get("P102", []) if "P582" not in v.get("qualifiers", {})}
    partei.discard(None)
    if len(partei) == 1:
        personen["x"][kennung] = {"pa": partei.pop()}
    else:
        personen["x"].pop(kennung, None)
    if kennung in bilder["personen"]:
        print("Partei", kennung, personen["x"].get(kennung))
        sichern()
        continue
    datei = wert(c, "P18")[0]
    ii = kopf(datei)
    if not ii:
        print("--", kennung, "kein Bildkopf", datei)
        continue
    meta = ii.get("extmetadata", {})
    lizenz = ohne_html(meta.get("LicenseShortName", {}).get("value"))
    if not FREI.match(lizenz):
        print("--", kennung, "Lizenz nicht frei:", lizenz, datei)
        continue
    autor = ohne_html(meta.get("Artist", {}).get("value"))[:80]
    roh = holen(ii["thumburl"])
    if not roh:
        continue
    lokal = "copytrading/bilder/" + kennung + ".jpg"
    with open(ORDNER + "/" + kennung + ".jpg", "wb") as f:
        f.write(verkleinern(roh))
    seite = e.get("sitelinks", {}).get("enwiki", {}).get("title") or e.get("labels", {}).get("en", {}).get("value", name)
    bilder["personen"][kennung] = {
        "autor": autor, "datei": datei, "lizenz": "Public domain" if lizenz.lower().startswith(("public domain", "pd")) else lizenz,
        "lokal": lokal, "quelle": ii.get("descriptionurl", ""), "seite": seite, "url": ii["thumburl"],
    }
    print("ok", kennung, "|", seite, "|", e.get("descriptions", {}).get("en", {}).get("value", ""), "|", lizenz, "|", autor[:40], "|", personen["x"].get(kennung))
    sichern()
