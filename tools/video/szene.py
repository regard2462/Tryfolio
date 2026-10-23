# Vorstellungsvideo der App fuer Blender: ein 3D-Telefon mit echten Bildschirmen, daneben ruhige Texte.
# Kapitel und Texte stehen in kapitel.json. Gerendert wird mit EEVEE direkt als H.264-MP4; am Ende schreibt das Skript
# <datei>.fertig mit der Zahl der Bilder (der Blender-Starter aus dem Microsoft Store wartet nicht auf das Ende).
# Aufruf (bauen.py erledigt das):
#   blender --background --factory-startup --python szene.py -- --fotos <ordner> --sprache de --ziel <ordner>
#           --version 5.5 [--von 1 --bis 0] [--vorschau 100,560,2800]
import argparse
import json
import math
import os
import sys

import bpy

p = argparse.ArgumentParser()
p.add_argument("--fotos", required=True)
p.add_argument("--sprache", required=True)
p.add_argument("--ziel", required=True)
p.add_argument("--version", default="")
p.add_argument("--von", type=int, default=1)
p.add_argument("--bis", type=int, default=0)
p.add_argument("--vorschau", default="")
p.add_argument("--kapitel", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "kapitel.json"))
a = p.parse_args(sys.argv[sys.argv.index("--") + 1:])
FOTOS, SPR, ZIEL = a.fotos, a.sprache, a.ziel
VORSCHAU = bool(a.vorschau)
EINZELN = [int(v) for v in a.vorschau.split(",")] if VORSCHAU else None
VON = 1 if VORSCHAU else a.von
BIS = 0 if VORSCHAU else a.bis

FPS = 60
with open(a.kapitel, encoding="utf-8") as f:
    INHALT = json.load(f)[SPR]
TEXTE = {
    "titel": INHALT["titel"],
    "kapitel": [(k["titel"], k["satz"]) for k in INHALT["kapitel"]],
    "ende": [t.replace("{version}", a.version) for t in INHALT["ende"] if a.version or "{version}" not in t],
}
BILDER = [k["bilder"] for k in INHALT["kapitel"]]


def foto(name):
    return os.path.join(FOTOS, "gh_%s_%s.png" % (SPR, name))


# Zeitplan in Bildern
INTRO = 360
KAPITEL = 390
ENDE = 360
START_KAPITEL = INTRO
START_ENDE = INTRO + KAPITEL * len(BILDER)
GESAMT = START_ENDE + ENDE

bpy.ops.wm.read_factory_settings(use_empty=True)
szene = bpy.context.scene
for name in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE"):
    try:
        szene.render.engine = name
        break
    except TypeError:
        continue
szene.render.resolution_x = 1920 if not VORSCHAU else 960
szene.render.resolution_y = 1080 if not VORSCHAU else 540
szene.render.fps = FPS
szene.frame_start = VON
szene.frame_end = BIS or GESAMT
szene.render.image_settings.file_format = "PNG"
szene.view_settings.view_transform = "Standard"
try:
    szene.eevee.taa_render_samples = 24
except AttributeError:
    pass

welt = bpy.data.worlds.new("Welt")
szene.world = welt
welt.use_nodes = True
hg = next(n for n in welt.node_tree.nodes if n.type == "BACKGROUND")
hg.inputs[0].default_value = (0.012, 0.012, 0.016, 1)



def schrift(*pfade):
    for pfad in pfade:
        if os.path.isfile(pfad):
            return bpy.data.fonts.load(pfad)
    return None


SCHRIFT = schrift(r"C:\Windows\Fonts\segoeuib.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", "/System/Library/Fonts/Helvetica.ttc")
SCHRIFT_DUENN = schrift(r"C:\Windows\Fonts\segoeui.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/System/Library/Fonts/Helvetica.ttc")


def taste(sock, wert, bild):
    sock.default_value = wert
    sock.keyframe_insert("default_value", frame=bild)


def material_koerper():
    m = bpy.data.materials.new("Gehaeuse")
    m.use_nodes = True
    p = next(n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    p.inputs["Base Color"].default_value = (0.035, 0.035, 0.04, 1)
    p.inputs["Metallic"].default_value = 0.7
    p.inputs["Roughness"].default_value = 0.45
    return m


def material_bildschirm(namen):
    # Bilder nacheinander ueber Mischknoten; der Mischwert jedes Bildes wird spaeter mit Tasten gesetzt
    m = bpy.data.materials.new("Bildschirm")
    m.use_nodes = True
    nt = m.node_tree
    p = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
    p.inputs["Base Color"].default_value = (0, 0, 0, 1)
    # Matter Bildschirm: kaum Spiegelung, das Bild selbst leuchtet
    p.inputs["Roughness"].default_value = 0.7
    p.inputs["Specular IOR Level" if "Specular IOR Level" in p.inputs else "Specular"].default_value = 0.0
    farbe = None
    mischer = []
    for i, n in enumerate(namen):
        tex = nt.nodes.new("ShaderNodeTexImage")
        tex.image = bpy.data.images.load(foto(n), check_existing=True)
        tex.interpolation = "Cubic"
        if farbe is None:
            farbe = tex.outputs["Color"]
            continue
        mix = nt.nodes.new("ShaderNodeMix")
        mix.data_type = "RGBA"
        nt.links.new(farbe, mix.inputs[6])
        nt.links.new(tex.outputs["Color"], mix.inputs[7])
        mix.inputs[0].default_value = 0.0
        farbe = mix.outputs[2]
        mischer.append(mix.inputs[0])
    emi = p.inputs.get("Emission Color") or p.inputs.get("Emission")
    nt.links.new(farbe, emi)
    if "Emission Strength" in p.inputs:
        p.inputs["Emission Strength"].default_value = 1.0
    return m, mischer


B, H, T = 0.78, 1.66, 0.085
SB, SH = 0.73, 1.6222


def telefon(name, namen, x, z, drehung):
    halter = bpy.data.objects.new(name, None)
    szene.collection.objects.link(halter)
    halter.location = (x, 0, z)
    halter.rotation_euler = (0, 0, math.radians(drehung))
    bpy.ops.mesh.primitive_cube_add(size=1, location=(0, 0, 0))
    k = bpy.context.object
    k.scale = (B, T, H)
    bpy.ops.object.transform_apply(scale=True)
    bev = k.modifiers.new("Rundung", "BEVEL")
    bev.width = 0.075
    bev.segments = 16
    k.data.materials.append(material_koerper())
    bpy.ops.object.shade_smooth()
    k.parent = halter
    bpy.ops.mesh.primitive_plane_add(size=1, location=(0, -T / 2 - 0.0015, 0))
    s = bpy.context.object
    s.scale = (SB, SH, 1)
    s.rotation_euler = (math.radians(90), 0, 0)
    bpy.ops.object.transform_apply(scale=True, rotation=True)
    mat, mischer = material_bildschirm(namen)
    s.data.materials.append(mat)
    s.parent = halter
    return halter, mischer


def lage(obj, bild, x=None, z=None, dreh=None, y=None):
    if x is not None or z is not None or y is not None:
        l = list(obj.location)
        if x is not None: l[0] = x
        if y is not None: l[1] = y
        if z is not None: l[2] = z
        obj.location = l
        obj.keyframe_insert("location", frame=bild)
    if dreh is not None:
        obj.rotation_euler = (0, 0, math.radians(dreh))
        obj.keyframe_insert("rotation_euler", frame=bild)


def text(inhalt, groesse, x, z, duenn=False, farbe=(1, 1, 1), breite=2.35):
    kurve = bpy.data.curves.new("Text", "FONT")
    kurve.body = inhalt
    if (SCHRIFT_DUENN if duenn else SCHRIFT) is not None:
        kurve.font = SCHRIFT_DUENN if duenn else SCHRIFT
    kurve.size = groesse
    kurve.align_x = "LEFT"
    # Zeilenumbruch innerhalb der Breite rechts neben dem Telefon
    kurve.text_boxes[0].width = breite
    kurve.space_line = 1.05
    obj = bpy.data.objects.new("Text", kurve)
    szene.collection.objects.link(obj)
    obj.location = (x, -0.2, z)
    obj.rotation_euler = (math.radians(90), 0, 0)
    m = bpy.data.materials.new("Textfarbe")
    m.use_nodes = True
    try:
        m.surface_render_method = "BLENDED"
    except AttributeError:
        m.blend_method = "BLEND"
    nt = m.node_tree
    for n in list(nt.nodes):
        if n.type != "OUTPUT_MATERIAL":
            nt.nodes.remove(n)
    aus = next(n for n in nt.nodes if n.type == "OUTPUT_MATERIAL")
    em = nt.nodes.new("ShaderNodeEmission")
    em.inputs[0].default_value = (*farbe, 1)
    tr = nt.nodes.new("ShaderNodeBsdfTransparent")
    mix = nt.nodes.new("ShaderNodeMixShader")
    nt.links.new(tr.outputs[0], mix.inputs[1])
    nt.links.new(em.outputs[0], mix.inputs[2])
    nt.links.new(mix.outputs[0], aus.inputs[0])
    mix.inputs[0].default_value = 0.0
    kurve.materials.append(m)
    return obj, mix.inputs[0]


def einblenden(sock, obj, von, bis, z):
    # Weich ein, kurz nach oben gleitend, und wieder aus
    taste(sock, 0.0, von)
    taste(sock, 1.0, von + 20)
    taste(sock, 1.0, bis - 20)
    taste(sock, 0.0, bis)
    obj.location.z = z - 0.05
    obj.keyframe_insert("location", frame=von)
    obj.location.z = z
    obj.keyframe_insert("location", frame=von + 24)


# Telefone: mittleres mit allen Bildschirmen der Kapitel, dazu zwei fuer Anfang und Ende
alle = ["depot"] + [n for k in BILDER for n in k]
mitte, mischer = telefon("Mitte", alle, 0.0, 0.05, 0)
links, _ = telefon("Links", ["copytrading"], -1.5, 0.0, -22)
rechts, _ = telefon("Rechts", ["heatmap"], 1.5, 0.0, 22)
links.location.y = rechts.location.y = 1.0

# Kamera und Licht wie beim Titelbild
bpy.ops.object.camera_add(location=(0, -6.6, 0.25), rotation=(math.radians(89), 0, 0))
kam = bpy.context.object
kam.data.lens = 52
szene.camera = kam
for ort, energie, groesse, rot in [((-3.2, -4.0, 3.2), 900, 5, (55, 0, -38)), ((3.5, -2.5, 1.5), 300, 3, (70, 0, 55)), ((0, 2.5, 2.0), 250, 4, (-60, 0, 0))]:
    bpy.ops.object.light_add(type="AREA", location=ort)
    l = bpy.context.object
    l.data.energy = energie
    l.data.size = groesse
    l.rotation_euler = tuple(math.radians(v) for v in rot)

# Anfang: drei Telefone, Kamera faehrt heran, dann gleiten die aeusseren hinaus und das mittlere nach links
LX = -0.95
NAH = -5.8
lage(kam, 1, y=-6.6)
lage(kam, 150, y=-6.1)
lage(mitte, 1, x=0.0, z=0.05, dreh=0)
lage(mitte, 150, x=0.0, z=0.05, dreh=0)
lage(mitte, 240, x=LX, z=0.0, dreh=8)
for obj, aussen in [(links, -4.2), (rechts, 4.2)]:
    lage(obj, 150, x=obj.location.x)
    lage(obj, 230, x=aussen)
kam.location.z = 0.25
kam.keyframe_insert("location", frame=150)
lage(kam, 240, y=NAH, z=0.05)
t1, s1 = text(TEXTE["titel"][0], 0.28, -0.25, 0.12, breite=1.95)
t2, s2 = text(TEXTE["titel"][1], 0.12, -0.25, -0.12, duenn=True, farbe=(0.72, 0.72, 0.75), breite=1.95)
einblenden(s1, t1, 230, INTRO + 10, 0.12)
einblenden(s2, t2, 240, INTRO + 10, -0.12)

# Kapitel: Bildschirme blenden ineinander, das Telefon schwenkt langsam, daneben Titel und Satz
index = 1  # Bild 0 ist das Depot vom Anfang
for k, namen in enumerate(BILDER):
    beginn = START_KAPITEL + k * KAPITEL
    schritt = KAPITEL // len(namen)
    for j, _ in enumerate(namen):
        f = beginn + j * schritt
        sock = mischer[index - 1]
        taste(sock, 0.0, max(1, f - 1))
        taste(sock, 1.0, f + 18)
        index += 1
    lage(mitte, beginn + 10, x=LX, z=0.0, dreh=8 if k % 2 == 0 else -6)
    lage(mitte, beginn + KAPITEL - 10, x=LX, z=0.0, dreh=-6 if k % 2 == 0 else 8)
    # Zeilen des Titels grob aus der Laenge geschaetzt, der Satz steht mit festem Abstand darunter
    zeilen = math.ceil(len(TEXTE["kapitel"][k][0]) * 0.3 * 0.18 / 1.95)
    oben = 0.12 + (zeilen - 1) * 0.11
    unten = oben - zeilen * 0.205 - 0.04
    ti, si = text(TEXTE["kapitel"][k][0], 0.18, -0.25, oben, breite=1.95)
    st, ss = text(TEXTE["kapitel"][k][1], 0.095, -0.25, unten, duenn=True, farbe=(0.72, 0.72, 0.75), breite=1.95)
    einblenden(si, ti, beginn + 8, beginn + KAPITEL - 4, oben)
    einblenden(ss, st, beginn + 16, beginn + KAPITEL - 4, unten)

# Ende: Text, dann kommen die drei Telefone wieder zusammen
e1, es1 = text(TEXTE["ende"][0], 0.22, -0.25, 0.12, breite=1.95)
e2, es2 = text(TEXTE["ende"][1], 0.1, -0.25, -0.13, duenn=True, farbe=(0.72, 0.72, 0.75), breite=1.95)
einblenden(es1, e1, START_ENDE + 6, START_ENDE + 120, 0.12)
einblenden(es2, e2, START_ENDE + 14, START_ENDE + 120, -0.13)
if len(TEXTE["ende"]) > 2:
    e3, es3 = text(TEXTE["ende"][2], 0.075, -0.25, -0.3, duenn=True, farbe=(0.5, 0.5, 0.53), breite=1.95)
    einblenden(es3, e3, START_ENDE + 20, START_ENDE + 120, -0.3)
lage(mitte, START_ENDE + 110, x=LX, z=0.0, dreh=0)
lage(mitte, START_ENDE + 200, x=0.0, z=0.05, dreh=0)
for obj, aussen, innen in [(links, -4.2, -1.5), (rechts, 4.2, 1.5)]:
    lage(obj, START_ENDE + 110, x=aussen)
    lage(obj, START_ENDE + 210, x=innen)
lage(kam, START_ENDE + 110, y=NAH, z=0.05)
lage(kam, START_ENDE + 210, y=-6.6, z=0.25)
lage(kam, GESAMT, y=-6.35)

if VORSCHAU:
    for f in EINZELN:
        szene.frame_set(f)
        szene.render.filepath = os.path.join(ZIEL, "vorschau_%04d.png" % f)
        bpy.ops.render.render(write_still=True)
    with open(os.path.join(ZIEL, "vorschau.fertig"), "w", encoding="utf-8") as f:
        json.dump({"bilder": len(EINZELN)}, f)
else:
    # Direkt als H.264 in MP4 (Blenders eingebautes FFmpeg), hohe Qualitaet; Name nach Sprache
    bild = szene.render.image_settings
    if hasattr(bild, "media_type"):
        bild.media_type = "VIDEO"
    bild.file_format = "FFMPEG"
    ff = szene.render.ffmpeg
    ff.format = "MPEG4"
    ff.codec = "H264"
    ff.constant_rate_factor = "HIGH"
    ff.ffmpeg_preset = "GOOD"
    ff.gopsize = FPS
    ff.audio_codec = "NONE"
    ganz = szene.frame_start == 1 and szene.frame_end == GESAMT
    datei = os.path.join(ZIEL, ("tryfolio-video-%s.mp4" % SPR) if ganz else ("tryfolio-video-%s-%d-%d.mp4" % (SPR, szene.frame_start, szene.frame_end)))
    if os.path.exists(datei + ".fertig"):
        os.remove(datei + ".fertig")
    szene.render.filepath = datei
    bpy.ops.render.render(animation=True)
    with open(datei + ".fertig", "w", encoding="utf-8") as f:
        json.dump({"bilder": szene.frame_end - szene.frame_start + 1, "fps": FPS}, f)
print("fertig", szene.frame_start, szene.frame_end)
