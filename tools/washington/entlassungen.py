# Entlassungsmeldungen nach dem WARN Act je Firma aus dem Katalog, seit Anfang des Vorjahres.
# Quelle: WARN Feed (https://github.com/APVentureEngine/warn-act-notices), CC BY 4.0, gesammelt aus den Listen der
# Arbeitsbehoerden der Bundesstaaten. Die App ergaenzt Texas live ueber data.texas.gov.
#
#   python entlassungen.py <katalog.json> <ziel.json>
import csv
import datetime
import io
import json
import sys
import urllib.request

from namen import index_aufbauen, katalog_us, treffer

QUELLE = "https://raw.githubusercontent.com/APVentureEngine/warn-act-notices/main/data/warn_notices.csv"
KENNUNG = {"User-Agent": "Tryfolio (open-source practice depot)"}


def main():
    katalog, ziel = sys.argv[1], sys.argv[2]
    heute = datetime.date.today()
    ab = f"{heute.year - 1}-01-01"
    idx = index_aufbauen(katalog_us(katalog))
    text = urllib.request.urlopen(urllib.request.Request(QUELLE, headers=KENNUNG), timeout=600).read().decode("utf-8", "replace").replace(chr(0), "")
    firmen, neueste = {}, ""
    for z in csv.DictReader(io.StringIO(text)):
        datum = (z.get("notice_date") or z.get("effective_date") or "").strip()[:10]
        if datum < ab:
            continue
        if datum > heute.isoformat():
            continue
        neueste = max(neueste, datum)
        # Nur der gemeldete Name: der vereinheitlichte Name ordnet etwa Abfueller der Marke zu
        symbole = treffer(z.get("company") or "", idx)
        if not symbole:
            continue
        try:
            n = int(float(z.get("employees_affected") or 0))
        except ValueError:
            n = 0
        e = {"d": datum, "s": (z.get("state") or "").strip().upper()[:2], "n": n,
             "o": (z.get("location") or "").strip()[:60], "w": (z.get("effective_date") or "").strip()[:10]}
        for s in symbole:
            firmen.setdefault(s, []).append(e)
    for s in firmen:
        gesehen, liste = set(), []
        for e in sorted(firmen[s], key=lambda e: e["d"], reverse=True):
            k = (e["d"], e["s"], e["n"], e["o"])
            if k not in gesehen:
                gesehen.add(k)
                liste.append(e)
        firmen[s] = liste[:40]
    json.dump({"format": 1, "stand": heute.isoformat(), "neueste": neueste, "firmen": firmen},
              open(ziel, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print(len(firmen), "Firmen,", sum(len(v) for v in firmen.values()), "Meldungen, neueste", neueste)


if __name__ == "__main__":
    main()
