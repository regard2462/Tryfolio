# -*- coding: utf-8 -*-
"""Wertpapiergeschaefte von US-Senatorinnen und -Senatoren aus dem Senate-eFD-System (efdsearch.senate.gov).

Laeuft im GitHub-Workflow „Senat“ auf einem Rechner in den USA: eFD weist Abrufe von ausserhalb der USA ab, deshalb
holt die App die Daten nicht selbst, sondern bekommt mit jeder Fassung einen Schnappschuss (senat.json).

Ablauf wie im Browser: Startseite laden, den Hinweis auf 5 U.S.C. § 13107 bestaetigen (nicht kommerzielle Nutzung),
die Periodic Transaction Reports der letzten Jahre suchen und jede elektronische Meldung als Tabelle lesen.
Papiermeldungen sind eingescannte Bilder; sie stehen nur mit Datum in der Datei. Der Abruf meldet sich offen als
Lernprojekt an und umgeht keine Sperre: Weist eFD ihn ab, endet das Skript mit Code 75.

Aufruf:  python holen.py --jahre 3 --aus senat.json
Nur Python-Standardbibliothek.
"""
import argparse
import datetime as dt
import html
import http.cookiejar
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASIS = "https://efdsearch.senate.gov"
UA = "Tryfolio/1.0 (open-source practice depot; educational)"
PAUSE = 1.0


class Gesperrt(Exception):
    pass


class Sitzung:
    def __init__(self):
        self.kekse = http.cookiejar.CookieJar()
        self.oeffner = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.kekse))
        self.letzter = 0.0

    def keks(self, name):
        for c in self.kekse:
            if c.name == name:
                return c.value
        return ""

    def abruf(self, pfad, daten=None, kopf=None):
        warte = PAUSE - (time.time() - self.letzter)
        if warte > 0:
            time.sleep(warte)
        self.letzter = time.time()
        k = {"User-Agent": UA, "Accept": "text/html,application/json;q=0.9,*/*;q=0.8"}
        k.update(kopf or {})
        body = urllib.parse.urlencode(daten).encode() if daten is not None else None
        req = urllib.request.Request(BASIS + pfad, data=body, headers=k)
        for versuch in range(3):
            try:
                with self.oeffner.open(req, timeout=60) as r:
                    text = r.read().decode("utf-8", "replace")
                    return r.geturl(), text
            except urllib.error.HTTPError as e:
                text = e.read().decode("utf-8", "replace")
                if e.code == 403 or "errors.edgesuite.net" in text or "Access Denied" in text:
                    raise Gesperrt("HTTP %s bei %s" % (e.code, pfad))
                if e.code in (429, 500, 502, 503, 504) and versuch < 2:
                    time.sleep(10 * (versuch + 1))
                    continue
                raise
            except urllib.error.URLError:
                if versuch < 2:
                    time.sleep(10 * (versuch + 1))
                    continue
                raise

    def zustimmen(self):
        _, s = self.abruf("/search/home/")
        m = re.search(r'name="csrfmiddlewaretoken"\s+value="([^"]+)"', s)
        if not m:
            raise RuntimeError("Startseite ohne Formular")
        self.abruf("/search/home/", {"csrfmiddlewaretoken": m.group(1), "prohibition_agreement": "1"},
                   {"Referer": BASIS + "/search/home/", "Origin": BASIS})

    def suche(self, von, bis, start, laenge=100):
        token = self.keks("csrftoken") or self.keks("csrf")
        daten = {
            "start": str(start), "length": str(laenge), "report_types": "[11]", "filer_types": "[1]",
            "submitted_start_date": von.strftime("%m/%d/%Y 00:00:00"), "submitted_end_date": bis.strftime("%m/%d/%Y 23:59:59"),
            "candidate_state": "", "senator_state": "", "office_id": "", "first_name": "", "last_name": "",
            "csrfmiddlewaretoken": token,
        }
        url, text = self.abruf("/search/report/data/", daten, {
            "Referer": BASIS + "/search/", "X-CSRFToken": token, "X-Requested-With": "XMLHttpRequest", "Origin": BASIS})
        if "/search/home" in url:
            raise RuntimeError("Sitzung abgelaufen")
        return json.loads(text)


def klartext(s):
    s = re.sub(r"<br\s*/?>", " ", s or "", flags=re.I)
    s = re.sub(r"<[^>]+>", " ", s)
    return re.sub(r"\s+", " ", html.unescape(s)).strip()


def datum(s):
    m = re.search(r"(\d{1,2})/(\d{1,2})/(\d{4})", s or "")
    if not m:
        return ""
    try:
        return dt.date(int(m.group(3)), int(m.group(1)), int(m.group(2))).isoformat()
    except ValueError:
        return ""


def betrag(s):
    t = (s or "").replace(",", "")
    zahlen = [int(x) for x in re.findall(r"\$?\s*(\d+)", t)]
    if not zahlen:
        return None, None
    if re.search(r"over|more than|\+\s*$", t, re.I) or len(zahlen) == 1:
        return zahlen[0], None
    return zahlen[0], zahlen[1]


ART = {"purchase": "K", "sale (full)": "V", "sale": "V", "sale (partial)": "T", "exchange": "X"}
INHABER = {"self": "", "spouse": "SP", "joint": "JT", "child": "DC", "dependent child": "DC"}


def tabelle(seite):
    """Zeilen der Geschaeftstabelle einer elektronischen Meldung."""
    m = re.search(r"<table[^>]*>(.*?)</table>", seite, re.S | re.I)
    if not m:
        return []
    aus = []
    for zeile in re.findall(r"<tr[^>]*>(.*?)</tr>", m.group(1), re.S | re.I):
        zellen = [klartext(z) for z in re.findall(r"<t[dh][^>]*>(.*?)</t[dh]>", zeile, re.S | re.I)]
        if len(zellen) < 8 or not re.match(r"^\d+$", zellen[0]):
            continue
        _, tag, inhaber, kuerzel, name, typ, art, summe = zellen[:8]
        kommentar = zellen[8] if len(zellen) > 8 else ""
        von, bis = betrag(summe)
        a = ART.get(art.strip().lower())
        if a is None or von is None or not datum(tag):
            continue
        k = kuerzel.strip()
        k = None if k in ("", "--", "-") else re.split(r"\s", k)[0].strip()
        if k and (k.startswith("-") or not re.search(r"[A-Za-z]", k)):
            k = None
        aus.append({
            "d": datum(tag), "i": INHABER.get(inhaber.strip().lower(), ""), "k": k, "n": name, "t": typ.strip(), "a": a,
            "von": von, "bis": bis, "c": None if kommentar in ("", "--") else kommentar[:200],
        })
    return aus


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--jahre", type=int, default=3)
    ap.add_argument("--aus", default="senat.json")
    a = ap.parse_args()
    s = Sitzung()
    bis = dt.date.today()
    von = bis - dt.timedelta(days=365 * a.jahre)
    try:
        s.zustimmen()
        zeilen, start = [], 0
        while True:
            antwort = s.suche(von, bis, start)
            daten = antwort.get("data") or []
            zeilen += daten
            start += len(daten)
            gesamt = int(antwort.get("recordsFiltered") or antwort.get("recordsTotal") or 0)
            if not daten or start >= gesamt:
                break
        print("Meldungen im Verzeichnis:", len(zeilen))
        meldungen = []
        for z in zeilen:
            vorname, nachname, amt, link, eingang = (list(z) + [""] * 5)[:5]
            href = re.search(r'href="([^"]+)"', link or "")
            if not href:
                continue
            pfad = href.group(1)
            titel = klartext(link)
            papier = "/paper/" in pfad
            m = {"id": pfad.rstrip("/").split("/")[-1], "vorname": klartext(vorname), "nachname": klartext(nachname),
                 "amt": klartext(amt), "eingang": datum(eingang), "titel": titel, "papier": papier, "geschaefte": []}
            if not papier:
                url, seite = s.abruf(pfad)
                if "/search/home" in url:
                    s.zustimmen()
                    url, seite = s.abruf(pfad)
                m["geschaefte"] = tabelle(seite)
            meldungen.append(m)
    except Gesperrt as e:
        print("eFD hat den Abruf abgewiesen:", e)
        sys.exit(75)
    # Aenderungen ersetzen die urspruengliche Meldung desselben Tages derselben Person
    def grund(m):
        return (m["nachname"].lower(), m["vorname"].lower(), re.sub(r"\s*\(Amendment.*\)", "", m["titel"]))
    aenderungen = {}
    for m in meldungen:
        if "Amendment" in m["titel"] and not m["papier"]:
            aenderungen.setdefault(grund(m), m)
    behalten = [m for m in meldungen if "Amendment" in m["titel"] or m["papier"] or grund(m) not in aenderungen]
    behalten.sort(key=lambda m: (m["eingang"], m["nachname"]), reverse=True)
    erg = {"stand": dt.date.today().isoformat(), "quelle": "U.S. Senate, Electronic Financial Disclosures (efdsearch.senate.gov)",
           "von": von.isoformat(), "meldungen": behalten}
    with open(a.aus, "w", encoding="utf-8", newline="\n") as f:
        json.dump(erg, f, ensure_ascii=False, separators=(",", ":"))
    g = sum(len(m["geschaefte"]) for m in behalten)
    print("Meldungen: %d (davon Papier %d), Geschaefte: %d" % (len(behalten), sum(m["papier"] for m in behalten), g))
    if g == 0:
        sys.exit("keine Geschaefte gelesen")


if __name__ == "__main__":
    main()
