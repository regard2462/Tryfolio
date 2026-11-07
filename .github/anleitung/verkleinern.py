"""Fotos des Fotolaufs fuer die App verkleinern: 480 Pixel breit, WebP. Aufruf: verkleinern.py <roh> <ziel>."""
import pathlib
import sys

from PIL import Image

roh, ziel = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
for png in sorted(roh.glob("*/*.png")):
    aus = ziel / png.parent.name / (png.stem + ".webp")
    aus.parent.mkdir(parents=True, exist_ok=True)
    with Image.open(png) as bild:
        bild = bild.convert("RGB")
        hoehe = round(bild.height * 480 / bild.width)
        bild.resize((480, hoehe), Image.LANCZOS).save(aus, "WEBP", quality=82, method=6)
    print(aus, aus.stat().st_size)
