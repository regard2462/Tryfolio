#!/usr/bin/env python3
# Signiert das Quellen-Buendel quellen.json mit Ed25519 (RFC 8032) und legt die abgetrennte Signatur quellen.json.sig
# (128 Hex-Zeichen) daneben. Ohne Zusatzpakete: Ed25519 nach der Referenzumsetzung aus RFC 8032, Abschnitt 6.
#
# Der private Schluessel liegt NIE im Repo, sondern nur lokal unter ~/.tryfolio-signatur/quellen-ed25519.key
# (anderer Ordner ueber die Umgebungsvariable TRYFOLIO_SIGNATUR). Der oeffentliche Schluessel steht fest in
# app/src/main/java/de/troetenrepublik/app/market/Quellen.kt (SCHLUESSEL).
#
# Drei Schluessel (alle nur lokal, nie im Repo):
#   haupt   quellen-ed25519.key          signiert quellen.json und das Zertifikat des Datenschluessels
#   ersatz  quellen-ersatz-ed25519.key   Ersatz-Hauptschluessel, dessen oeffentlicher Teil ebenfalls in Quellen.kt steht
#                                        (ERSATZ_SCHLUESSEL); ein Buendel oder Zertifikat gilt mit einem von beiden
#   daten   daten-ed25519.key            signiert nur daten/manifest.json, nie quellen.json; sein oeffentlicher Teil
#                                        steht nur im Zertifikat daten/schluessel.json (vom Hauptschluessel signiert)
#
#   python tools/quellen/signieren.py neu [haupt|ersatz|daten]   Schluessel anlegen (nie ueberschreiben)
#   python tools/quellen/signieren.py signieren [datei]          datei (Standard quellen.json) mit dem Hauptschluessel signieren
#   python tools/quellen/signieren.py signieren-ersatz [datei]   dasselbe mit dem Ersatzschluessel
#   python tools/quellen/signieren.py pruefen [datei]            Signatur gegen die Schluessel im Code pruefen
#   python tools/quellen/signieren.py oeffentlich [art]          oeffentlichen Schluessel ausgeben
#   python tools/quellen/signieren.py zertifikat [monate] [--ersatz]
#                                                                Zertifikat daten/schluessel.json fuer den Datenschluessel
#                                                                ausstellen (Standard 48 Monate); jede Ausstellung hat eine
#                                                                hoehere version, ein neuer Datenschluessel ruft den alten zurueck
#   python tools/quellen/signieren.py daten-signieren [manifest] daten/manifest.json mit dem Datenschluessel signieren
#   python tools/quellen/signieren.py daten-pruefen [manifest]   Kette und Dateien pruefen (Zertifikat, Manifest, Pruefsummen)

import datetime
import hashlib
import json
import os
import re
import sys

P = 2 ** 255 - 19
L = 2 ** 252 + 27742317777372353535851937790883648493
D = -121665 * pow(121666, P - 2, P) % P
SQRT_M1 = pow(2, (P - 1) // 4, P)


def sha512(b):
    return hashlib.sha512(b).digest()


def punkt_add(a, b):
    x1, y1, z1, t1 = a
    x2, y2, z2, t2 = b
    aa = (y1 - x1) * (y2 - x2) % P
    bb = (y1 + x1) * (y2 + x2) % P
    cc = t1 * 2 * D * t2 % P
    dd = z1 * 2 * z2 % P
    e, f, g, h = bb - aa, dd - cc, dd + cc, bb + aa
    return (e * f % P, g * h % P, f * g % P, e * h % P)


def mal(s, p):
    q = (0, 1, 1, 0)
    while s > 0:
        if s & 1:
            q = punkt_add(q, p)
        p = punkt_add(p, p)
        s >>= 1
    return q


def gleich(a, b):
    x1, y1, z1, _ = a
    x2, y2, z2, _ = b
    return (x1 * z2 - x2 * z1) % P == 0 and (y1 * z2 - y2 * z1) % P == 0


def x_aus(y, vorzeichen):
    if y >= P:
        return None
    x2 = (y * y - 1) * pow(D * y * y + 1, P - 2, P)
    if x2 == 0:
        return None if vorzeichen else 0
    x = pow(x2, (P + 3) // 8, P)
    if (x * x - x2) % P != 0:
        x = x * SQRT_M1 % P
    if (x * x - x2) % P != 0:
        return None
    if (x & 1) != vorzeichen:
        x = P - x
    return x


GY = 4 * pow(5, P - 2, P) % P
GX = x_aus(GY, 0)
G = (GX, GY, 1, GX * GY % P)


def packen(p):
    x, y, z, _ = p
    zi = pow(z, P - 2, P)
    x, y = x * zi % P, y * zi % P
    return int.to_bytes(y | ((x & 1) << 255), 32, "little")


def entpacken(s):
    if len(s) != 32:
        return None
    y = int.from_bytes(s, "little")
    vz = y >> 255
    y &= (1 << 255) - 1
    x = x_aus(y, vz)
    if x is None:
        return None
    return (x, y, 1, x * y % P)


def geheim_zu(seed):
    h = sha512(seed)
    a = int.from_bytes(h[:32], "little")
    a &= (1 << 254) - 8
    a |= 1 << 254
    return a, h[32:]


def oeffentlich(seed):
    a, _ = geheim_zu(seed)
    return packen(mal(a, G))


def signieren(seed, nachricht):
    a, praefix = geheim_zu(seed)
    pk = packen(mal(a, G))
    r = int.from_bytes(sha512(praefix + nachricht), "little") % L
    rs = packen(mal(r, G))
    h = int.from_bytes(sha512(rs + pk + nachricht), "little") % L
    s = (r + h * a) % L
    return rs + int.to_bytes(s, 32, "little")


def pruefen(pk, nachricht, sig):
    if len(pk) != 32 or len(sig) != 64:
        return False
    a = entpacken(pk)
    r = entpacken(sig[:32])
    if a is None or r is None:
        return False
    s = int.from_bytes(sig[32:], "little")
    if s >= L:
        return False
    h = int.from_bytes(sha512(sig[:32] + pk + nachricht), "little") % L
    return gleich(mal(s, G), punkt_add(r, mal(h, a)))


def ordner():
    return os.environ.get("TRYFOLIO_SIGNATUR") or os.path.join(os.path.expanduser("~"), ".tryfolio-signatur")


DATEIEN = {"haupt": "quellen-ed25519.key", "ersatz": "quellen-ersatz-ed25519.key", "daten": "daten-ed25519.key"}


def schluesseldatei(art="haupt"):
    return os.path.join(ordner(), DATEIEN[art])


def seed_laden(art="haupt"):
    try:
        with open(schluesseldatei(art), "r", encoding="ascii") as f:
            seed = bytes.fromhex(f.read().strip())
    except FileNotFoundError:
        raise SystemExit("Schluesseldatei fehlt: " + schluesseldatei(art))
    if len(seed) != 32:
        raise SystemExit("Schluesseldatei kaputt: " + schluesseldatei(art))
    return seed


def repo():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def schluessel_im_code(name="SCHLUESSEL"):
    pfad = os.path.join(repo(), "app", "src", "main", "java", "de", "troetenrepublik", "app", "market", "Quellen.kt")
    with open(pfad, "r", encoding="utf-8") as f:
        m = re.search(r"\b" + name + r'\s*=\s*"([0-9a-f]{64})"', f.read())
    return bytes.fromhex(m.group(1)) if m else None


def schluessel_aus_code():
    # Haupt- und Ersatzschluessel aus Quellen.kt (nicht vorhandene fallen weg)
    return [k for k in (schluessel_im_code("SCHLUESSEL"), schluessel_im_code("ERSATZ_SCHLUESSEL")) if k is not None]


def selbsttest():
    # RFC 8032, Abschnitt 7.1, Test 2
    seed = bytes.fromhex("4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb")
    sig = signieren(seed, b"\x72")
    assert oeffentlich(seed).hex() == "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c"
    assert sig.hex().startswith("92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da")
    assert pruefen(oeffentlich(seed), b"\x72", sig)
    assert not pruefen(oeffentlich(seed), b"\x73", sig)


def datei_lesen(pfad):
    with open(pfad, "rb") as f:
        return f.read()


def sig_schreiben(datei, sig):
    with open(datei + ".sig", "w", encoding="ascii", newline="\n") as f:
        f.write(sig.hex() + "\n")


def sig_lesen(datei):
    with open(datei + ".sig", "r", encoding="ascii") as f:
        return bytes.fromhex(f.read().strip())


def daten_ordner():
    return os.path.join(repo(), "daten")


def zertifikat_pfad():
    return os.path.join(daten_ordner(), "schluessel.json")


def manifest_pfad():
    return os.path.join(daten_ordner(), "manifest.json")


def monate_dazu(tag, monate):
    m = tag.month - 1 + monate
    j = tag.year + m // 12
    m = m % 12 + 1
    # Monatsende beachten (31. Januar plus ein Monat)
    for t in (tag.day, 30, 29, 28):
        try:
            return datetime.date(j, m, t)
        except ValueError:
            continue


def zertifikat_ausstellen(argumente):
    monate = 48
    ersatz = "--ersatz" in argumente
    neue_version = "--neu-version" in argumente
    for a in argumente:
        if a.isdigit():
            monate = int(a)
    art = "ersatz" if ersatz else "haupt"
    seed = seed_laden(art)
    erwartet = schluessel_im_code("ERSATZ_SCHLUESSEL" if ersatz else "SCHLUESSEL")
    if erwartet is not None and erwartet != oeffentlich(seed):
        raise SystemExit("Der " + art + "-Schluessel passt nicht zum oeffentlichen Schluessel in Quellen.kt")
    daten_pub = oeffentlich(seed_laden("daten")).hex()
    version = 1
    if os.path.exists(zertifikat_pfad()):
        alt = json.loads(datei_lesen(zertifikat_pfad()).decode("utf-8"))
        # Jede Neuausstellung hat eine hoehere version, sonst nimmt die App sie nicht an
        version = alt["version"] + 1
    heute = datetime.date.today()
    cert = {"zertifikat": 1, "version": version, "schluessel": daten_pub,
            "ausgestellt": heute.isoformat(), "gueltigBis": monate_dazu(heute, monate).isoformat()}
    os.makedirs(daten_ordner(), exist_ok=True)
    text = json.dumps(cert, indent=2) + "\n"
    with open(zertifikat_pfad(), "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    sig_schreiben(zertifikat_pfad(), signieren(seed, text.encode("utf-8")))
    print("Zertifikat ausgestellt: " + zertifikat_pfad() + " (version " + str(version) + ", gueltig bis " + cert["gueltigBis"] + ", signiert mit " + art + ")")


def kette_pruefen(manifest):
    # Zertifikat mit Haupt- oder Ersatzschluessel, Manifest mit dem Schluessel des Zertifikats, Dateien nach Pruefsumme
    haupt = schluessel_aus_code()
    if not haupt:
        raise SystemExit("Kein Hauptschluessel in Quellen.kt gefunden")
    ztext = datei_lesen(zertifikat_pfad())
    if not any(pruefen(k, ztext, sig_lesen(zertifikat_pfad())) for k in haupt):
        raise SystemExit("Zertifikat: Signatur UNGUELTIG")
    zert = json.loads(ztext.decode("utf-8"))
    mtext = datei_lesen(manifest)
    if not pruefen(bytes.fromhex(zert["schluessel"]), mtext, sig_lesen(manifest)):
        raise SystemExit("Manifest: Signatur UNGUELTIG")
    m = json.loads(mtext.decode("utf-8"))
    if m["erzeugt"] > zert["gueltigBis"]:
        raise SystemExit("Manifest ist nach Ablauf des Zertifikats erzeugt")
    for name, e in m["dateien"].items():
        b = datei_lesen(os.path.join(daten_ordner(), *name.split("/")))
        if hashlib.sha256(b).hexdigest() != e["sha256"] or len(b) != e["bytes"]:
            raise SystemExit("Pruefsumme stimmt nicht: " + name)
    print("Kette gueltig: Zertifikat version " + str(zert["version"]) + " bis " + zert["gueltigBis"] + ", Manifest version " + str(m["version"]) + ", " + str(len(m["dateien"])) + " Dateien")


def main():
    selbsttest()
    befehl = sys.argv[1] if len(sys.argv) > 1 else "signieren"
    arg2 = sys.argv[2] if len(sys.argv) > 2 else None
    datei = arg2 if arg2 and not arg2.startswith("--") else os.path.join(repo(), "quellen.json")
    if befehl == "neu":
        art = arg2 or "haupt"
        if art not in DATEIEN:
            raise SystemExit("Art: haupt, ersatz oder daten")
        if os.path.exists(schluesseldatei(art)):
            raise SystemExit("Schluessel besteht schon: " + schluesseldatei(art))
        os.makedirs(ordner(), exist_ok=True)
        seed = os.urandom(32)
        with open(schluesseldatei(art), "w", encoding="ascii") as f:
            f.write(seed.hex() + "\n")
        print("Schluessel angelegt: " + schluesseldatei(art))
        if art == "daten":
            print("Oeffentlicher Schluessel (steht nur im Zertifikat): " + oeffentlich(seed).hex())
        else:
            print("Oeffentlicher Schluessel fuer Quellen.kt (" + ("ERSATZ_SCHLUESSEL" if art == "ersatz" else "SCHLUESSEL") + "): " + oeffentlich(seed).hex())
    elif befehl == "oeffentlich":
        print(oeffentlich(seed_laden(arg2 or "haupt")).hex())
    elif befehl in ("signieren", "signieren-ersatz"):
        ersatz = befehl == "signieren-ersatz"
        seed = seed_laden("ersatz" if ersatz else "haupt")
        im_code = schluessel_im_code("ERSATZ_SCHLUESSEL" if ersatz else "SCHLUESSEL")
        if im_code is not None and im_code != oeffentlich(seed):
            raise SystemExit("Der Schluessel passt nicht zum oeffentlichen Schluessel in Quellen.kt")
        sig_schreiben(datei, signieren(seed, datei_lesen(datei)))
        print("Signiert: " + datei + ".sig")
    elif befehl == "pruefen":
        pks = schluessel_aus_code()
        if not pks:
            raise SystemExit("Kein Schluessel in Quellen.kt gefunden")
        daten = datei_lesen(datei)
        sig = sig_lesen(datei)
        ok = any(pruefen(pk, daten, sig) for pk in pks)
        print("Signatur gueltig" if ok else "Signatur UNGUELTIG")
        sys.exit(0 if ok else 1)
    elif befehl == "zertifikat":
        zertifikat_ausstellen(sys.argv[2:])
    elif befehl == "daten-signieren":
        pfad = arg2 or manifest_pfad()
        seed = seed_laden("daten")
        zert = json.loads(datei_lesen(zertifikat_pfad()).decode("utf-8"))
        if zert["schluessel"] != oeffentlich(seed).hex():
            raise SystemExit("Der Datenschluessel passt nicht zum Zertifikat daten/schluessel.json")
        sig_schreiben(pfad, signieren(seed, datei_lesen(pfad)))
        print("Signiert: " + pfad + ".sig")
    elif befehl == "daten-pruefen":
        kette_pruefen(arg2 or manifest_pfad())
    else:
        raise SystemExit(__doc__ or "Befehle: neu, signieren, signieren-ersatz, pruefen, oeffentlich, zertifikat, daten-signieren, daten-pruefen")


if __name__ == "__main__":
    main()
