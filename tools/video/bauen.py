# -*- coding: utf-8 -*-
"""Vorstellungsvideo fuer eine neue Fassung in einem Schritt.

1. Fassung aus app/build.gradle.kts (versionName) lesen.
2. Bildschirmfotos gh_<sprache>_<name>.png aus dem Fotoordner uebernehmen und die Statusleiste (Uhrzeit,
   Benachrichtigungs- und Akkusymbole) uebermalen.
3. Je Sprache mit Blender rendern (szene.py, Kapitel aus kapitel.json), H.264-MP4 mit 60 Bildern je Sekunde.
4. Mit ffprobe pruefen, ob das Video vollstaendig ist.
5. Auf Wunsch an das Release haengen (gh), als tryfolio-video-<sprache>.mp4. Die README verlinkt
   releases/latest/download/tryfolio-video-<sprache>.mp4 und zeigt damit immer das neueste Video.

Aufruf:
  python bauen.py --fotos <ordner mit Bildschirmfotos> --ziel <arbeitsordner>
                  [--blender <blender oder Starter>] [--sprachen de,en] [--release vX.Y] [--vorschau 100,560,2800]
Fehlen Fotos, die kapitel.json nennt, bricht das Skript vorher ab und nennt sie.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time

HIER = os.path.dirname(os.path.abspath(__file__))
WURZEL = os.path.dirname(os.path.dirname(HIER))


def fassung():
    with open(os.path.join(WURZEL, "app", "build.gradle.kts"), encoding="utf-8") as f:
        m = re.search(r'versionName\s*=\s*"([^"]+)"', f.read())
    if not m:
        sys.exit("versionName nicht gefunden")
    return m.group(1)


def statusleiste_weg(quelle, ziel):
    """Uebermalt die Statusleiste (oberste 60 Pixel bei 1080 x 2400), wenn links die Uhrzeit und rechts Symbole
    stehen und dazwischen nichts, mit der Farbe darunter. Andere Bilder bleiben, wie sie sind."""
    from PIL import Image, ImageDraw

    im = Image.open(quelle).convert("RGB")
    w, _ = im.size
    faktor = w / 1080.0
    grund = im.getpixel((w // 2, int(30 * faktor)))

    def anders(x0, x1):
        n = 0
        for y in range(int(10 * faktor), int(56 * faktor), 2):
            for x in range(int(x0 * faktor), int(x1 * faktor), 3):
                if sum(abs(a - b) for a, b in zip(im.getpixel((x, y)), grund)) > 150:
                    n += 1
        return n

    if anders(30, 150) > 20 and anders(900, 1050) > 20 and anders(250, 850) == 0:
        ImageDraw.Draw(im).rectangle((0, 0, w, int(60 * faktor)), fill=im.getpixel((w // 2, int(62 * faktor))))
    im.save(ziel)


def fotos_vorbereiten(quelle, ziel, sprachen, kapitel):
    os.makedirs(ziel, exist_ok=True)
    fehlt = []
    for spr in sprachen:
        namen = ["depot", "copytrading", "heatmap"] + [b for k in kapitel[spr]["kapitel"] for b in k["bilder"]]
        for n in sorted(set(namen)):
            datei = "gh_%s_%s.png" % (spr, n)
            if not os.path.isfile(os.path.join(quelle, datei)):
                fehlt.append(datei)
                continue
            statusleiste_weg(os.path.join(quelle, datei), os.path.join(ziel, datei))
    if fehlt:
        sys.exit("Es fehlen Bildschirmfotos: " + ", ".join(fehlt))


def warten(marke, sekunden):
    ende = time.time() + sekunden
    while time.time() < ende:
        if os.path.isfile(marke):
            with open(marke, encoding="utf-8") as f:
                return json.load(f)
        time.sleep(10)
    sys.exit("Blender wurde nicht fertig: " + marke)


def rendern(blender, fotos, spr, ziel, version, vorschau):
    befehl = [blender, "--background", "--factory-startup", "--python", os.path.join(HIER, "szene.py"), "--",
              "--fotos", fotos, "--sprache", spr, "--ziel", ziel, "--version", version]
    if vorschau:
        befehl += ["--vorschau", vorschau]
        marke = os.path.join(ziel, "vorschau.fertig")
    else:
        marke = os.path.join(ziel, "tryfolio-video-%s.mp4.fertig" % spr)
    if os.path.exists(marke):
        os.remove(marke)
    print("Blender:", spr, flush=True)
    # Der Starter aus dem Microsoft Store kehrt sofort zurueck, deshalb auf die Marke warten
    subprocess.run(befehl, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return warten(marke, 6 * 3600)


def pruefen(video, bilder):
    ffprobe = shutil.which("ffprobe")
    if not ffprobe:
        print("ffprobe fehlt, Video nicht geprueft:", video)
        return
    aus = subprocess.run([ffprobe, "-v", "error", "-select_streams", "v:0", "-count_packets", "-show_entries",
                          "stream=codec_name,width,height,nb_read_packets", "-of", "json", video], capture_output=True, text=True)
    s = json.loads(aus.stdout)["streams"][0]
    print("Video:", os.path.basename(video), s)
    if s.get("codec_name") != "h264" or int(s.get("nb_read_packets", 0)) != bilder:
        sys.exit("Video unvollstaendig: %s (erwartet %d Bilder)" % (video, bilder))


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--fotos", required=True)
    p.add_argument("--ziel", required=True)
    p.add_argument("--blender", default=os.environ.get("BLENDER", "blender"))
    p.add_argument("--sprachen", default="de,en")
    p.add_argument("--release", default="")
    p.add_argument("--vorschau", default="")
    a = p.parse_args()
    version = fassung()
    sprachen = [s.strip() for s in a.sprachen.split(",") if s.strip()]
    with open(os.path.join(HIER, "kapitel.json"), encoding="utf-8") as f:
        kapitel = json.load(f)
    ziel = os.path.abspath(a.ziel)
    fotos = os.path.join(ziel, "fotos")
    fotos_vorbereiten(os.path.abspath(a.fotos), fotos, sprachen, kapitel)
    videos = []
    for spr in sprachen:
        aus = os.path.join(ziel, "vorschau-" + spr) if a.vorschau else ziel
        os.makedirs(aus, exist_ok=True)
        marke = rendern(a.blender, fotos, spr, aus, version, a.vorschau)
        if a.vorschau:
            print("Vorschau:", aus)
            continue
        video = os.path.join(ziel, "tryfolio-video-%s.mp4" % spr)
        pruefen(video, marke["bilder"])
        videos.append(video)
    if a.release and videos:
        subprocess.run(["gh", "release", "upload", a.release, *videos, "--clobber"], check=True, cwd=WURZEL)
        print("Hochgeladen zu", a.release)
    print("Fassung", version, "fertig:", ", ".join(videos) or "Vorschau")


if __name__ == "__main__":
    main()
