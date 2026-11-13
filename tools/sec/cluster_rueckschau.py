# -*- coding: utf-8 -*-
"""Rueckschau der Insider-Cluster-Kaeufe fuer die Lernseite (assets/copytrading/cluster.json).

Eingabe ist der mitgelieferte Stand der Insidergeschaefte (assets/copytrading/sec.json, siehe holen.py). Ein Cluster
liegt vor, sobald mindestens drei verschiedene Insider innerhalb von zehn Tagen eine Kaufmeldung (Form 4, Code P)
eingereicht haben. Gerechnet wird ab dem Tag der Meldung des dritten Insiders: Einstieg zum Schlusskurs des ersten
Handelstags danach, Ausstieg nach 60 Handelstagen, verglichen mit dem S&P 500 (^GSPC) im selben Zeitraum. Je Aktie
zaehlt hoechstens ein Cluster je 90 Tage.

Die Kurse kommen von Yahoo Finance (Tagesschluss). Aktien ohne Kursreihe (zum Beispiel inzwischen eingestellte)
fallen heraus; die Zahl steht im Ergebnis.

Aufruf: python cluster_rueckschau.py <sec.json> <ziel.json> [<kurse-cache.json>]
"""
import bisect
import collections
import datetime as dt
import json
import sys
import time
import urllib.parse
import urllib.request

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
HALTEN = 60
FENSTER = 10
MINDEST = 3
SPERRE = 90


def kurse(sym, cache):
    if sym in cache:
        return cache[sym]
    url = "https://query1.finance.yahoo.com/v8/finance/chart/%s?range=5y&interval=1d" % urllib.parse.quote(sym.replace(".", "-"), safe="^")
    reihe = None
    for versuch in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=30) as r:
                j = json.load(r)
            res = j["chart"]["result"][0]
            t = res["timestamp"]
            c = res["indicators"]["quote"][0]["close"]
            reihe = [[dt.datetime.fromtimestamp(a, dt.timezone.utc).strftime("%Y-%m-%d"), b] for a, b in zip(t, c) if b]
            break
        except urllib.error.HTTPError as e:
            if e.code == 404:
                break
            time.sleep(2 + 3 * versuch)
        except Exception:  # noqa: BLE001
            time.sleep(2 + 3 * versuch)
    cache[sym] = reihe
    time.sleep(0.25)
    return reihe


def cluster(geschaefte):
    nach = collections.defaultdict(list)
    for g in geschaefte:
        if g["kauf"] and g.get("f"):
            nach[g["k"]].append(g)
    aus = []
    for k, l in nach.items():
        l.sort(key=lambda x: x["f"])
        letzter = None
        for x in l:
            t = dt.date.fromisoformat(x["f"])
            w = {y["p"] for y in l if 0 <= (t - dt.date.fromisoformat(y["f"])).days <= FENSTER}
            if len(w) >= MINDEST and (letzter is None or (t - letzter).days >= SPERRE):
                aus.append((k, x["f"], len(w)))
                letzter = t
    return sorted(aus, key=lambda a: a[1])


def main():
    quelle, ziel = sys.argv[1], sys.argv[2]
    pfad = sys.argv[3] if len(sys.argv) > 3 else None
    cache = {}
    if pfad:
        try:
            cache = json.load(open(pfad, encoding="utf-8"))
        except Exception:  # noqa: BLE001
            cache = {}
    d = json.load(open(quelle, encoding="utf-8"))
    cl = cluster(d["geschaefte"])
    index = kurse("^GSPC", cache)
    itage = [a for a, _ in index]
    ipreis = [b for _, b in index]
    ergebnisse = []
    ohne = 0
    offen = 0
    for k, f, n in cl:
        r = kurse(k, cache)
        if not r:
            ohne += 1
            continue
        tage = [a for a, _ in r]
        i = bisect.bisect_right(tage, f)
        if i + HALTEN >= len(r):
            offen += 1
            continue
        e = r[i][1]
        pfade = [r[i + s][1] / e - 1 for s in range(HALTEN + 1)]
        j = bisect.bisect_left(itage, tage[i])
        if j + HALTEN >= len(index):
            offen += 1
            continue
        ie = ipreis[j]
        ip = [ipreis[j + s] / ie - 1 for s in range(HALTEN + 1)]
        ergebnisse.append({"k": k, "f": f, "n": n, "r": pfade[-1], "i": ip[-1], "pfad": pfade, "ip": ip})
    if pfad:
        json.dump(cache, open(pfad, "w", encoding="utf-8"))
    if not ergebnisse:
        raise SystemExit("keine Ergebnisse")
    jahre = []
    for j in sorted({e["f"][:4] for e in ergebnisse}):
        l = [e for e in ergebnisse if e["f"][:4] == j]
        jahre.append({
            "j": int(j), "n": len(l),
            "r": round(sum(e["r"] for e in l) / len(l), 4),
            "i": round(sum(e["i"] for e in l) / len(l), 4),
            "t": round(sum(1 for e in l if e["r"] > e["i"]) / len(l), 4),
        })
    n = len(ergebnisse)
    kurve = [[s, round(sum(e["pfad"][s] for e in ergebnisse) / n, 4), round(sum(e["ip"][s] for e in ergebnisse) / n, 4)] for s in range(0, HALTEN + 1, 3)]
    if kurve[-1][0] != HALTEN:
        kurve.append([HALTEN, round(sum(e["pfad"][HALTEN] for e in ergebnisse) / n, 4), round(sum(e["ip"][HALTEN] for e in ergebnisse) / n, 4)])
    schlecht = min(ergebnisse, key=lambda e: e["r"])
    gut = max(ergebnisse, key=lambda e: e["r"])
    aus = {
        "stand": d["stand"],
        "ab": min(g["f"] for g in d["geschaefte"] if g.get("f")),
        "halten": HALTEN, "fenster": FENSTER, "mindest": MINDEST,
        "cluster": len(cl), "ausgewertet": n, "ohneKurse": ohne, "offen": offen,
        "rendite": round(sum(e["r"] for e in ergebnisse) / n, 4),
        "index": round(sum(e["i"] for e in ergebnisse) / n, 4),
        "median": round(sorted(e["r"] for e in ergebnisse)[n // 2], 4),
        "indexMedian": round(sorted(e["i"] for e in ergebnisse)[n // 2], 4),
        "treffer": round(sum(1 for e in ergebnisse if e["r"] > e["i"]) / n, 4),
        "schlechtester": {"k": schlecht["k"], "f": schlecht["f"], "r": round(schlecht["r"], 4)},
        "bester": {"k": gut["k"], "f": gut["f"], "r": round(gut["r"], 4)},
        "jahre": jahre, "kurve": kurve,
    }
    with open(ziel, "w", encoding="utf-8", newline="\n") as f:
        json.dump(aus, f, ensure_ascii=False, separators=(",", ":"))
    print(json.dumps({k: v for k, v in aus.items() if k not in ("kurve",)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
