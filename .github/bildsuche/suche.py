"""Sucht freie Fotos zu Personen ohne Bild (Copytrading). Laeuft nur im Ablauf „Bildsuche“.
Quellen: das Bild des Wikipedia-Artikels (englisch und deutsch) und die Eigenschaft P18 (Bild) bei Wikidata.
Ergebnis: bericht.json (Datei, Urheber, Lizenz, Quelle je Person) und Vorschaubilder; aufgenommen wird nichts von selbst."""
import json, sys, time, urllib.parse, urllib.request, urllib.error, os, re

UA = "Tryfolio-Bildsuche/1.1 (open-source practice depot)"
FREI = re.compile(r"^(public domain|pd|cc0|cc[ -]by(-sa)?( \d\.\d)?)", re.I)

def hole(url):
    for versuch in range(6):
        time.sleep(1.5)
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=30) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code != 429: raise
            time.sleep(int(e.headers.get("Retry-After") or 0) or 10 * (versuch + 1))
    raise RuntimeError("429 nach mehreren Versuchen")

def api(base, **p):
    p["format"] = "json"
    return json.loads(hole(base + "?" + urllib.parse.urlencode(p)))

personen = json.load(open(sys.argv[1], encoding="utf-8"))
os.makedirs("ausgabe", exist_ok=True)
bericht = []
for pid, titel in personen.items():
    eintrag = {"id": pid, "seite": titel, "kandidaten": []}
    dateien = []
    for wiki in ["en", "de"]:
        try:
            q = api(f"https://{wiki}.wikipedia.org/w/api.php", action="query", prop="pageimages|pageprops", piprop="name", titles=titel, redirects=1)
            for p in q["query"]["pages"].values():
                if p.get("pageimage"): dateien.append(p["pageimage"])
                qid = p.get("pageprops", {}).get("wikibase_item")
                if qid and wiki == "en":
                    w = api("https://www.wikidata.org/w/api.php", action="wbgetclaims", entity=qid, property="P18")
                    dateien += [c["mainsnak"]["datavalue"]["value"] for c in w.get("claims", {}).get("P18", []) if "datavalue" in c["mainsnak"]]
                    eintrag["wikidata"] = qid
        except Exception as e:
            eintrag.setdefault("fehler", []).append(f"{wiki}: {e}")
    # Commons: Kategorie der Person und Dateien mit dem vollen Namen im Dateinamen
    name = re.sub(r" \(.*\)$", "", titel)
    try:
        k = api("https://commons.wikimedia.org/w/api.php", action="query", list="categorymembers", cmtitle="Category:" + name, cmtype="file", cmlimit=10)
        dateien += [m["title"].removeprefix("File:") for m in k["query"]["categorymembers"]]
    except Exception as e:
        eintrag.setdefault("fehler", []).append(f"kategorie: {e}")
    try:
        s = api("https://commons.wikimedia.org/w/api.php", action="query", list="search", srsearch=f'intitle:"{name}"', srnamespace=6, srlimit=10)
        dateien += [h["title"].removeprefix("File:") for h in s["query"]["search"] if not h["title"].lower().endswith((".pdf", ".djvu", ".svg"))]
    except Exception as e:
        eintrag.setdefault("fehler", []).append(f"suche: {e}")
    for d in dict.fromkeys(x.replace(" ", "_") for x in dateien):
        try:
            i = api("https://commons.wikimedia.org/w/api.php", action="query", prop="imageinfo", iiprop="url|extmetadata", iiurlwidth=330, titles="File:" + d)
            info = list(i["query"]["pages"].values())[0]["imageinfo"][0]
        except Exception as e:
            eintrag.setdefault("fehler", []).append(f"{d}: {e}")
            continue
        m = info.get("extmetadata", {})
        wert = lambda k: re.sub(r"<[^>]+>", "", m.get(k, {}).get("value", "")).strip()
        k = {"datei": d, "quelle": info.get("descriptionurl", ""), "url": info.get("thumburl", ""), "lizenz": wert("LicenseShortName"),
             "autor": wert("Artist"), "beschreibung": wert("ImageDescription")[:300], "frei": bool(FREI.match(wert("LicenseShortName")))}
        if k["frei"] and k["url"]:
            name = f"{pid}-{len(eintrag['kandidaten'])}.jpg"
            try:
                open("ausgabe/" + name, "wb").write(hole(k["url"]))
                k["vorschau"] = name
            except Exception as e:
                k["vorschauFehler"] = str(e)
        eintrag["kandidaten"].append(k)
    bericht.append(eintrag)
    print(pid, len(eintrag["kandidaten"]), eintrag.get("fehler", ""), flush=True)
json.dump(bericht, open("ausgabe/bericht.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
