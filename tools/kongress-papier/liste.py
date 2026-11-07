# -*- coding: utf-8 -*-
"""Liest die Jahresverzeichnisse des House Clerk (<Jahr>FD.zip mit XML) und schreibt die Liste der
Papier-PTRs (FilingType P, DocID nicht elektronisch) als JSON.
Elektronische Meldungen haben eine DocID, die mit 2 beginnt und mindestens 8 Stellen hat.
Aufruf: python -I liste.py <ziel.json> <2025FD.zip> [<2026FD.zip> ...]"""
import json
import os
import re
import sys
import zipfile
import xml.etree.ElementTree as ET


def main():
    ziel = sys.argv[1]
    aus = []
    for zpfad in sys.argv[2:]:
        jahr = re.match(r"(\d{4})", os.path.basename(zpfad)).group(1)
        z = zipfile.ZipFile(zpfad)
        for n in z.namelist():
            if not n.lower().endswith(".xml"):
                continue
            wurzel = ET.fromstring(z.read(n))
            for m in wurzel.findall("Member"):
                d = {c.tag: (c.text or "").strip() for c in m}
                if d.get("FilingType") != "P":
                    continue
                doc = d.get("DocID", "")
                if doc.startswith("2") and len(doc) >= 8:
                    continue
                mo, ta, ja = (int(x) for x in d["FilingDate"].split("/"))
                aus.append({
                    "id": doc,
                    "jahr": jahr,
                    "name": d.get("Last", ""),
                    "vorname": d.get("First", ""),
                    "staat": d.get("StateDst", ""),
                    "eingang": "%04d-%02d-%02d" % (ja, mo, ta),
                    "url": "https://disclosures-clerk.house.gov/public_disc/ptr-pdfs/%s/%s.pdf" % (jahr, doc),
                })
    aus.sort(key=lambda x: (x["name"], x["eingang"], x["id"]))
    with open(ziel, "w", encoding="utf-8") as f:
        json.dump(aus, f, ensure_ascii=False, indent=1)
    print("Papiermeldungen:", len(aus))


if __name__ == "__main__":
    main()
