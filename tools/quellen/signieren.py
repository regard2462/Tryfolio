#!/usr/bin/env python3
# Signiert das Quellen-Buendel quellen.json mit Ed25519 (RFC 8032) und legt die abgetrennte Signatur quellen.json.sig
# (128 Hex-Zeichen) daneben. Ohne Zusatzpakete: Ed25519 nach der Referenzumsetzung aus RFC 8032, Abschnitt 6.
#
# Der private Schluessel liegt NIE im Repo, sondern nur lokal unter ~/.tryfolio-signatur/quellen-ed25519.key
# (anderer Ordner ueber die Umgebungsvariable TRYFOLIO_SIGNATUR). Der oeffentliche Schluessel steht fest in
# app/src/main/java/de/troetenrepublik/app/market/Quellen.kt (SCHLUESSEL).
#
#   python tools/quellen/signieren.py neu                  neuen Schluessel anlegen (nie ueberschreiben)
#   python tools/quellen/signieren.py signieren [datei]    datei (Standard quellen.json) signieren
#   python tools/quellen/signieren.py pruefen [datei]      Signatur gegen den Schluessel im Code pruefen
#   python tools/quellen/signieren.py oeffentlich          oeffentlichen Schluessel ausgeben

import hashlib
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


def schluesseldatei():
    return os.path.join(ordner(), "quellen-ed25519.key")


def seed_laden():
    with open(schluesseldatei(), "r", encoding="ascii") as f:
        seed = bytes.fromhex(f.read().strip())
    if len(seed) != 32:
        raise SystemExit("Schluesseldatei kaputt: " + schluesseldatei())
    return seed


def repo():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def schluessel_im_code():
    pfad = os.path.join(repo(), "app", "src", "main", "java", "de", "troetenrepublik", "app", "market", "Quellen.kt")
    with open(pfad, "r", encoding="utf-8") as f:
        m = re.search(r'SCHLUESSEL\s*=\s*"([0-9a-f]{64})"', f.read())
    return bytes.fromhex(m.group(1)) if m else None


def selbsttest():
    # RFC 8032, Abschnitt 7.1, Test 2
    seed = bytes.fromhex("4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb")
    sig = signieren(seed, b"\x72")
    assert oeffentlich(seed).hex() == "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c"
    assert sig.hex().startswith("92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da")
    assert pruefen(oeffentlich(seed), b"\x72", sig)
    assert not pruefen(oeffentlich(seed), b"\x73", sig)


def main():
    selbsttest()
    befehl = sys.argv[1] if len(sys.argv) > 1 else "signieren"
    datei = sys.argv[2] if len(sys.argv) > 2 else os.path.join(repo(), "quellen.json")
    if befehl == "neu":
        if os.path.exists(schluesseldatei()):
            raise SystemExit("Schluessel besteht schon: " + schluesseldatei())
        os.makedirs(ordner(), exist_ok=True)
        seed = os.urandom(32)
        with open(schluesseldatei(), "w", encoding="ascii") as f:
            f.write(seed.hex() + "\n")
        print("Schluessel angelegt: " + schluesseldatei())
        print("Oeffentlicher Schluessel fuer Quellen.kt: " + oeffentlich(seed).hex())
    elif befehl == "oeffentlich":
        print(oeffentlich(seed_laden()).hex())
    elif befehl == "signieren":
        seed = seed_laden()
        im_code = schluessel_im_code()
        if im_code is not None and im_code != oeffentlich(seed):
            raise SystemExit("Der Schluessel passt nicht zum oeffentlichen Schluessel in Quellen.kt")
        with open(datei, "rb") as f:
            daten = f.read()
        sig = signieren(seed, daten)
        with open(datei + ".sig", "w", encoding="ascii", newline="\n") as f:
            f.write(sig.hex() + "\n")
        print("Signiert: " + datei + ".sig")
    elif befehl == "pruefen":
        pk = schluessel_im_code()
        if pk is None:
            raise SystemExit("Kein Schluessel in Quellen.kt gefunden")
        with open(datei, "rb") as f:
            daten = f.read()
        with open(datei + ".sig", "r", encoding="ascii") as f:
            sig = bytes.fromhex(f.read().strip())
        ok = pruefen(pk, daten, sig)
        print("Signatur gueltig" if ok else "Signatur UNGUELTIG")
        sys.exit(0 if ok else 1)
    else:
        raise SystemExit(__doc__ or "Befehle: neu, signieren, pruefen, oeffentlich")


if __name__ == "__main__":
    main()
