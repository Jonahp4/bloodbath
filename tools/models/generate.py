#!/usr/bin/env python3
"""Generates the Bloodbath 3D weapon models, their painted textures and item definitions.

Style: chunky Blockbench-style weapons - wide mottled blood-steel blades with bright highlights and
white shine, banded gray grips, diamond (45-degree) gems in gray frames, diamond tips and caps.

Every weapon is a list of cuboids in "upright" local space: centred on x=8/z=8, grip at roughly
y=2.3, blade/head pointing +Y, flat face towards +Z (the side the camera sees). Vanilla handheld
display transforms expect a sprite drawn diagonally (handle bottom-left, tip top-right), so
instead of tilting every cube we fold a -45 degree Z pre-rotation into the display transforms:
Minecraft applies display rotations as Rx*Ry*Rz, so R_display * Rz(-45) is just "subtract 45
from the Z angle". (The bow is the exception: the vanilla bow sprite points up-left, so it adds 45.)

Each weapon gets its own texture atlas, painted per face at 1 texel per model unit. Noise is
sampled in model space, so patterns run continuously across neighbouring cubes.

Outputs (relative to the repo root):
  resourcepack/pack.mcmeta, resourcepack/pack.png                  resource pack metadata
  resourcepack/assets/unchartedsmp/items/<id>.json                 item model definitions
  resourcepack/assets/minecraft/items/{netherite_sword,bow}.json   Paper: custom_model_data overrides
  resourcepack/assets/unchartedsmp/textures/gui/sprites/tooltip/   the blood tooltip frame
  resourcepack/assets/unchartedsmp/models/item/<id>.json           3D models (+ bow pull stages)
  resourcepack/assets/unchartedsmp/textures/item/<id>.png          painted atlases
  docs/preview/models.json, docs/preview/textures/                 data for the web preview

Usage: python3 tools/models/generate.py   (needs numpy + pillow)
"""
import hashlib
import json
import math
import os
import shutil

import numpy as np
from PIL import Image, ImageColor

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
PACK = os.path.join(ROOT, "resourcepack")          # shared by the Paper plugin and the Fabric mod
ASSETS = os.path.join(PACK, "assets", "unchartedsmp")
NS = "unchartedsmp"


# --------------------------------------------------------------------------------------------
# Noise (deterministic, model-space) so textures flow across cubes.
# --------------------------------------------------------------------------------------------

def _hash(ix, iy, iz, seed):
    h = (ix * 374761393 + iy * 668265263 + iz * 2147483647 + seed * 144269504) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def value_noise(x, y, z, seed):
    ix, iy, iz = math.floor(x), math.floor(y), math.floor(z)
    fx, fy, fz = x - ix, y - iy, z - iz
    sx, sy, sz = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy), fz * fz * (3 - 2 * fz)
    def lerp(a, b, t):
        return a + (b - a) * t
    c = [[[_hash(ix + i, iy + j, iz + k, seed) for k in (0, 1)] for j in (0, 1)] for i in (0, 1)]
    x00 = lerp(c[0][0][0], c[1][0][0], sx)
    x10 = lerp(c[0][1][0], c[1][1][0], sx)
    x01 = lerp(c[0][0][1], c[1][0][1], sx)
    x11 = lerp(c[0][1][1], c[1][1][1], sx)
    return lerp(lerp(x00, x10, sy), lerp(x01, x11, sy), sz)


def fbm(x, y, z, seed, octaves=3):
    total, amp, freq, norm = 0.0, 1.0, 1.0, 0.0
    for o in range(octaves):
        total += amp * value_noise(x * freq, y * freq, z * freq, seed + o * 17)
        norm += amp
        amp *= 0.5
        freq *= 2.0
    return total / norm


def hexrgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=float)


def ramp(colors, t):
    t = min(0.999, max(0.0, t)) * (len(colors) - 1)
    i = int(t)
    f = t - i
    return colors[i] * (1 - f) + colors[i + 1] * f


def pal(*hexes):
    return [hexrgb(h) for h in hexes]


BLOOD = pal("#1e0104", "#3d050c", "#650a14", "#8f0f1c", "#bb1624", "#e3202f", "#ff4453")
BLACKSTEEL = pal("#0b0708", "#170d0f", "#241317", "#351a1f", "#4a2228")
STEEL = pal("#17161a", "#29282d", "#3f3e44", "#58575e", "#77767d", "#a09fa6", "#cbcad0")
FRAME = pal("#4a494f", "#6d6c73", "#8e8d94", "#b1b0b7", "#d6d5db")
GEM = pal("#3a0208", "#6e0612", "#a80c1c", "#e0182b", "#ff5a66")
GLOW = pal("#b00818", "#e0142a", "#ff3040", "#ff7580", "#ffd0d4")
BONE = pal("#5c5242", "#7e7360", "#a39780", "#c7bca2", "#e6dcc3")
LEATHER = pal("#170605", "#260b08", "#3a120c", "#511a12", "#692418")
GOLD = pal("#4a3208", "#76561a", "#a07c2a", "#caa441", "#f2d98a")
STONE = pal("#1f1e22", "#2d2b30", "#3c3a40", "#4d4a51", "#615d65")
MIRROR = pal("#3a1016", "#6e2831", "#a44b56", "#d98e97", "#fbe3e6")
CLOT = pal("#120103", "#240306", "#38060b", "#520912", "#7a0f1a")
PARCHMENT = pal("#6b5a3e", "#8f7a55", "#b39d70", "#d4c192", "#efe2b8")
HIDE = pal("#1c0205", "#33050b", "#520912", "#721020", "#95192c")
STRING = pal("#5a0a10", "#8c1019", "#bf1c28", "#ef4a57", "#ffb0b6")
STAIN = hexrgb("#7a0d18")
STAIN_DARK = hexrgb("#4a0610")
WHITE = hexrgb("#ffffff")
PINK = hexrgb("#f4b3ba")


def paint_texel(mat, face, u, v, w, h, p, el):
    """Colour of one texel. (u, v) = texel index on the face (w x h texels); p = model-space point."""
    x, y, z = p
    seed = el["seed"]
    edge = u == 0 or v == 0 or u == w - 1 or v == h - 1
    top_left = (u == 0 or v == 0) and w > 2 and h > 2
    bottom_right = (u == w - 1 or v == h - 1) and w > 2 and h > 2
    broad = face in ("north", "south")

    if mat == "blade":
        if not broad:
            # Honed edges: bright arterial red with pink catches.
            n = fbm(x * 0.6, y * 0.6, z * 0.6, seed)
            c = ramp(BLOOD, 0.62 + 0.35 * n)
            if _hash(int(x * 4), int(y * 4), int(z * 4), seed) > 0.9:
                c = PINK
            return c
        n = fbm(x * 0.28, y * 0.12, z * 0.28, 11, 4)            # long vertical streaks
        m = fbm(x * 0.7, y * 0.7, z * 0.7, 23, 2)
        t = 0.15 + 0.85 * (0.65 * n + 0.35 * m)
        c = ramp(BLOOD, t)
        spot = fbm(x * 0.35 + 7, y * 0.35, z * 0.35, 41)
        if spot > 0.72:                                          # glowing wet spots
            c = ramp(GLOW, (spot - 0.72) * 3.0)
        if n > 0.7 and m > 0.55:                                 # white shine
            c = c * 0.3 + WHITE * 0.7
        if top_left:
            c = c * 0.6 + PINK * 0.4
        if bottom_right:
            c = c * 0.55
        return c
    if mat == "blackblade":
        n = fbm(x * 0.3, y * 0.15, z * 0.3, 5, 3)
        c = ramp(BLACKSTEEL, n)
        vein = fbm(x * 0.5 + 3, y * 0.25, z * 0.5, 9)
        if vein > 0.62:
            c = ramp(BLOOD, 0.3 + (vein - 0.62) * 2.5)
        if not broad:
            c = ramp(BLOOD, 0.45 + 0.3 * n)
        if top_left:
            c = c * 0.7 + FRAME[2] * 0.3
        return c
    if mat in ("steel", "grip"):
        long_axis = el["long_axis"]
        coord = (x, y, z)[long_axis]
        if mat == "grip":
            band = int(math.floor(coord * 1.0)) % 2
            t = 0.28 + 0.34 * band + 0.18 * fbm(x, y, z, seed)
            c = ramp(STEEL, t)
        else:
            t = 0.35 + 0.35 * fbm(x * 0.5, y * 0.5, z * 0.5, seed)
            c = ramp(STEEL, t)
            if top_left:
                c = ramp(STEEL, 0.85)
            if bottom_right:
                c = c * 0.6
        stain = fbm(x * 0.6 + 13, y * 0.6, z * 0.6, 29)
        if stain > 0.66:
            c = STAIN if stain < 0.74 else STAIN_DARK
        return c
    if mat == "frame":
        t = 0.45 + 0.3 * fbm(x, y, z, seed)
        c = ramp(FRAME, t)
        if top_left:
            c = ramp(FRAME, 0.95)
        if bottom_right:
            c = ramp(FRAME, 0.1)
        return c
    if mat == "gem":
        cx = (u + 0.5) / w
        cy = (v + 0.5) / h
        d = math.hypot(cx - 0.35, cy - 0.35)
        c = ramp(GEM, 0.95 - d * 1.2 + 0.15 * fbm(x * 2, y * 2, z * 2, seed))
        if d < 0.12 and w >= 2:
            c = WHITE
        if edge and w > 2:
            c = c * 0.7
        return c
    if mat == "glow":
        cx = abs((u + 0.5) / w - 0.5)
        cy = abs((v + 0.5) / h - 0.5)
        core = 1.0 - max(cx, cy) * 1.6
        return ramp(GLOW, 0.25 + 0.6 * core + 0.2 * fbm(x * 1.5, y * 1.5, z * 1.5, seed))
    if mat == "bone":
        c = ramp(BONE, 0.45 + 0.4 * fbm(x * 0.7, y * 0.7, z * 0.7, seed))
        crack = fbm(x * 1.4, y * 1.4, z * 1.4, 77)
        if 0.49 < crack < 0.52:
            c = ramp(BONE, 0.05)
        stain = fbm(x * 0.5 + 3, y * 0.5, z * 0.5, 31)
        if stain > 0.68:
            c = STAIN
        if top_left:
            c = ramp(BONE, 0.95)
        return c
    if mat == "leather":
        coord = (x, y, z)[el["long_axis"]]
        band = (coord * 1.2 + (x + z) * 0.5) % 2.0
        c = ramp(LEATHER, 0.3 + 0.5 * (band > 1.0) + 0.2 * fbm(x, y, z, seed))
        if band % 1.0 < 0.2:
            c = LEATHER[0]
        return c
    if mat == "gold":
        c = ramp(GOLD, 0.35 + 0.45 * fbm(x * 0.8, y * 0.8, z * 0.8, seed))
        if top_left:
            c = GOLD[4]
        if bottom_right:
            c = GOLD[0]
        return c
    if mat == "stone":
        c = ramp(STONE, 0.2 + 0.7 * fbm(x * 0.6, y * 0.6, z * 0.6, seed, 4))
        crack = fbm(x * 0.9, y * 0.9, z * 0.9, 55)
        if 0.47 < crack < 0.53:
            c = ramp(BLOOD, 0.35)
        if top_left:
            c = c * 0.8 + STONE[4] * 0.2
        return c
    if mat == "mirror":
        band = ((x - y) * 0.5) % 4.0
        c = ramp(MIRROR, 0.3 + 0.2 * fbm(x, y, z, seed) + (0.5 if band < 0.9 else 0.0))
        if top_left:
            c = MIRROR[4]
        return c
    if mat == "clot":
        c = ramp(CLOT, 0.2 + 0.7 * fbm(x * 0.9, y * 0.9, z * 0.9, seed))
        if _hash(int(x * 3), int(y * 3), int(z * 3), seed) > 0.93:
            c = ramp(BLOOD, 0.85)
        return c
    if mat == "parchment":
        # Page edges: stacked lines, with the odd red scribble bleeding through.
        c = ramp(PARCHMENT, 0.45 + 0.35 * fbm(x * 0.8, y * 0.8, z * 0.8, seed))
        if not broad and int(math.floor((z if face in ("east", "west", "up", "down") else y) * 3)) % 2 == 0:
            c = c * 0.86
        if broad and fbm(x * 1.3, y * 2.5, z, 91) > 0.62:
            c = ramp(BLOOD, 0.45)
        return c
    if mat == "hide":
        # Crimson leather binding with a stitched border.
        c = ramp(HIDE, 0.3 + 0.55 * fbm(x * 0.9, y * 0.9, z * 0.9, seed, 3))
        if edge and w > 3 and h > 3:
            c = ramp(GOLD, 0.55) if (u + v) % 2 == 0 else ramp(HIDE, 0.1)
        return c
    if mat == "string":
        return ramp(STRING, 0.4 + 0.5 * fbm(x, y * 2, z, seed))
    raise ValueError(mat)


# --------------------------------------------------------------------------------------------
# Geometry helpers.
# --------------------------------------------------------------------------------------------

GLOWING = {"glow"}


class Model:
    def __init__(self):
        self.elements = []

    def box(self, x1, y1, z1, x2, y2, z2, mat, rot=None, name=None):
        """rot = (axis, angle, (ox, oy, oz))."""
        self.elements.append({"from": [x1, y1, z1], "to": [x2, y2, z2], "mat": mat, "rot": rot,
                              "name": name or mat, "glow": mat in GLOWING})
        return self

    def centered(self, cx, y1, y2, w, d, mat, cz=8.0, **kw):
        return self.box(cx - w / 2, y1, cz - d / 2, cx + w / 2, y2, cz + d / 2, mat, **kw)

    def diamond(self, cx, cy, size, depth, mat, cz=8.0, name=None):
        """A square rotated 45 degrees in the XY plane: gems, tips, caps."""
        h = size / 2
        return self.box(cx - h, cy - h, cz - depth / 2, cx + h, cy + h, cz + depth / 2, mat,
                        rot=("z", 45, (cx, cy, cz)), name=name)

    def gem(self, cx, cy, size, depth=2.2, cz=8.0, name="gem"):
        """Gray frame diamond with a red gem set into it (proud on both faces)."""
        self.diamond(cx, cy, size, depth, "frame", cz=cz, name=name + "_frame")
        self.diamond(cx, cy, size * 0.62, depth + 0.5, "gem", cz=cz, name=name)
        return self

    def segment(self, x0, y0, direction, length, thickness, depth, mat, overlap=0.5, cz=8.0, name=None):
        """Straight bar from (x0, y0) heading `direction` degrees (0 = +X, 90 = +Y), built from an
        axis-aligned box plus one legal Z rotation (a multiple of 22.5, at most 45). Returns its end."""
        rad = math.radians(direction)
        x1, y1 = x0 + length * math.cos(rad), y0 + length * math.sin(rad)
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        base, delta = min(((b, ((direction - b + 90) % 180) - 90) for b in (0, 90)), key=lambda t: abs(t[1]))
        assert abs(delta) <= 45 and abs(delta) % 22.5 == 0, (direction, delta)
        half = length / 2 + overlap / 2
        if base == 90:
            box = (cx - thickness / 2, cy - half, cx + thickness / 2, cy + half)
        else:
            box = (cx - half, cy - thickness / 2, cx + half, cy + thickness / 2)
        rot = ("z", delta, (cx, cy, cz)) if delta else None
        self.box(box[0], box[1], cz - depth / 2, box[2], box[3], cz + depth / 2, mat, rot=rot, name=name)
        return x1, y1

    def path(self, x, y, steps, thickness, depth, mat, name, edge=None):
        """A chain of segments. steps = [(direction, length), ...]; optional glowing inner edge
        offset to the right of travel by `edge` units."""
        for i, (direction, length) in enumerate(steps):
            t = thickness(i) if callable(thickness) else thickness
            if edge:
                inward = math.radians(direction - 90)
                self.segment(x + edge * math.cos(inward), y + edge * math.sin(inward), direction, length, 0.6,
                             depth + 0.3, "glow", overlap=0.1, name=f"{name}_edge_{i}")
            x, y = self.segment(x, y, direction, length, t, depth, mat, name=f"{name}_{i}")
        return x, y


# --------------------------------------------------------------------------------------------
# The ten weapons.
# --------------------------------------------------------------------------------------------

def riftblade():
    m = Model()
    m.gem(8, -3.2, 3.8, name="pommel")
    m.centered(8, -1.6, 5.6, 2.0, 2.0, "grip", name="grip")
    m.centered(8, 5.4, 7.9, 11.0, 1.8, "steel", name="crossguard")
    m.diamond(2.5, 6.65, 2.2, 2.0, "steel", name="guard_cap_l")
    m.diamond(13.5, 6.65, 2.2, 2.0, "steel", name="guard_cap_r")
    m.gem(8, 6.65, 4.8, depth=2.4, name="guard_gem")
    m.centered(8, 8.0, 27.5, 5.0, 1.0, "blade", name="blade")
    m.diamond(8, 27.5, 3.54, 1.0, "blade", name="blade_tip")
    m.centered(8, 9.5, 25.5, 0.8, 1.3, "glow", name="rift")
    return m


def bloodhook():
    m = Model()
    for i, y in enumerate((-10.5, -8.9, -7.3)):
        w, d = (1.6, 0.6) if i % 2 == 0 else (0.6, 1.6)
        m.centered(8, y, y + 1.8, w, d, "steel", name=f"chain_{i}")
    m.gem(8, -4.4, 3.0, name="ring_gem")
    m.centered(8, -2.8, 6.2, 2.0, 2.0, "grip", name="grip")
    m.centered(8, 6.0, 7.6, 2.8, 2.8, "steel", name="collar")
    m.centered(8, 7.6, 15.6, 1.8, 1.4, "steel", name="shaft")
    m.diamond(6.4, 13.6, 2.0, 1.2, "blade", name="back_spike")
    m.path(8.0, 15.2, [(67.5, 3.5), (22.5, 3.5), (-22.5, 3.5), (-67.5, 3.5), (-112.5, 3.0), (-157.5, 2.6)],
           lambda i: 2.6 if i < 4 else 2.0, 1.2, "blade", "hook", edge=1.0)
    return m


def nullblade():
    m = Model()
    m.gem(8, -3.0, 3.4, name="pommel")
    m.centered(8, -1.5, 4.8, 1.8, 1.8, "grip", name="grip")
    m.path(8.0, 5.4, [(22.5, 3.0), (67.5, 2.2)], 1.6, 1.8, "bone", "horn_r")
    m.path(8.0, 5.4, [(157.5, 3.0), (112.5, 2.2)], 1.6, 1.8, "bone", "horn_l")
    m.gem(8, 5.6, 3.6, depth=2.2, name="guard_clot")
    m.centered(8, 6.5, 21.0, 4.2, 1.0, "blackblade", name="blade")
    m.diamond(8, 21.0, 2.97, 1.0, "blackblade", name="blade_tip")
    for i, y in enumerate((8.0, 10.6, 13.2, 15.8, 18.4)):
        m.diamond(5.9, y, 1.3, 0.9, "blade", name=f"tooth_l{i}")
        m.diamond(10.1, y + 1.3, 1.3, 0.9, "blade", name=f"tooth_r{i}")
    m.centered(7.4, 9.0, 14.5, 1.1, 1.4, "clot", name="clot_a")
    m.centered(8.7, 12.5, 18.0, 0.9, 1.35, "clot", name="clot_b")
    m.centered(8.1, 15.0, 16.4, 0.7, 1.5, "glow", name="clot_wet")
    return m


def meteor_gauntlet():
    m = Model()
    m.centered(8, -2.5, 3.0, 6.4, 6.4, "steel", name="cuff")
    m.centered(8, -0.4, 0.8, 6.9, 6.9, "gold", name="cuff_trim")
    m.centered(8, -3.1, -2.5, 5.2, 5.2, "grip", name="cuff_lining")
    m.centered(8, 3.0, 8.2, 6.0, 5.6, "blade", name="hand")
    m.gem(8, 5.6, 4.0, depth=6.6, name="meteor")
    for i, x in enumerate((5.6, 7.2, 8.8, 10.4)):
        m.box(x - 0.7, 8.2, 5.6, x + 0.7, 11.4, 7.4, "steel", name=f"finger_{i}")
        m.box(x - 0.7, 11.0, 5.4, x + 0.7, 12.0, 7.2, "blade", name=f"claw_{i}")
        m.diamond(x, 9.0, 1.1, 1.2, "frame", cz=10.3, name=f"knuckle_{i}")
    m.segment(11.0, 4.0, 67.5, 4.0, 1.6, 2.2, "steel", name="thumb")
    m.box(6.0, 1.0, 10.6, 10.0, 3.0, 11.2, "steel", name="wrist_plate")
    return m


def gravestone():
    m = Model()
    m.gem(8, -9.2, 3.0, name="pommel")
    m.centered(8, -7.8, 12.0, 2.0, 2.0, "grip", name="haft")
    m.centered(8, 10.4, 12.0, 3.0, 3.0, "steel", name="collar")
    m.centered(8, 12.0, 22.0, 9.0, 4.0, "stone", name="slab")
    m.centered(8, 22.0, 23.3, 7.2, 4.0, "stone", name="slab_round_1")
    m.centered(8, 23.3, 24.3, 4.8, 4.0, "stone", name="slab_round_2")
    for y in (13.0, 19.6):
        m.centered(8, y, y + 1.0, 9.5, 4.5, "steel", name=f"strap_{y}")
    m.centered(8, 14.2, 21.6, 1.4, 4.6, "glow", name="cross_v")
    m.centered(8, 18.0, 19.4, 5.0, 4.6, "glow", name="cross_h")
    m.centered(6.6, 12.2, 14.0, 0.7, 4.4, "clot", name="drip_l")
    m.centered(9.5, 12.6, 14.8, 0.7, 4.4, "clot", name="drip_r")
    m.diamond(3.5, 17.0, 1.6, 4.2, "frame", name="stud_l")
    m.diamond(12.5, 17.0, 1.6, 4.2, "frame", name="stud_r")
    return m


def chronos():
    m = Model()
    m.gem(8, -11.0, 2.8, name="pommel")
    m.centered(8, -9.8, 16.0, 1.6, 1.6, "grip", name="staff")
    for y in (-2.0, 3.0, 14.4):
        m.centered(8, y, y + 1.2, 2.3, 2.3, "gold", name=f"collar_{y}")
    # Clock head: an octagon ring of gold with a blood-glass face on both sides.
    m.box(3.5, 16.5, 7.1, 12.5, 25.5, 8.9, "gold", name="rim")
    m.box(3.5, 16.5, 7.1, 12.5, 25.5, 8.9, "gold", rot=("z", 45, (8, 21, 8)), name="rim_diag")
    for side, (z1, z2) in (("front", (8.9, 9.2)), ("back", (6.8, 7.1))):
        m.box(4.4, 17.4, z1, 11.6, 24.6, z2, "mirror", name=f"face_{side}")
        m.box(4.4, 17.4, z1, 11.6, 24.6, z2, "mirror", rot=("z", 45, (8, 21, 8)), name=f"face_{side}_diag")
    m.box(7.7, 20.7, 9.2, 11.0, 21.3, 9.5, "steel", name="hand_hour")
    m.box(7.7, 20.7, 9.2, 8.3, 24.0, 9.5, "steel", name="hand_minute")
    m.centered(8, 20.5, 21.5, 1.1, 0.6, "glow", cz=9.5, name="hub")
    for i, (hx, hy) in enumerate(((8, 23.6), (10.6, 21), (8, 18.4), (5.4, 21))):
        m.centered(hx, hy - 0.35, hy + 0.35, 0.7, 0.3, "glow", cz=9.35, name=f"pip_{i}")
    m.gem(8, 27.2, 3.2, name="crown_gem")
    m.box(7.8, 12.8, 7.8, 8.2, 16.6, 8.2, "string", name="pendulum_rod")
    m.diamond(8, 12.0, 1.8, 1.6, "glow", name="pendulum_drop")
    return m


def thunder_pike():
    m = Model()
    m.diamond(8, -14.2, 2.2, 1.6, "blade", name="butt_spike")
    m.centered(8, -13.2, 20.0, 1.6, 1.6, "grip", name="shaft")
    for y in (-3.0, 7.0):
        m.centered(8, y, y + 1.0, 2.2, 2.2, "steel", name=f"collar_{y}")
    m.centered(8, 19.0, 21.0, 2.8, 2.8, "steel", name="socket")
    m.gem(8, 19.6, 3.0, depth=3.2, name="socket_gem")
    m.diamond(8, 24.4, 6.0, 1.2, "blade", name="head")
    m.diamond(8, 28.4, 3.4, 1.2, "blade", name="head_tip")
    m.centered(8, 21.4, 29.4, 0.8, 1.5, "glow", name="head_core")
    # Crimson lightning prongs, zig-zagging out of the socket.
    m.path(9.2, 20.4, [(45, 2.4), (0, 1.4), (45, 2.2)], 0.8, 1.0, "glow", "bolt_r")
    m.path(6.8, 20.4, [(135, 2.4), (180, 1.4), (135, 2.2)], 0.8, 1.0, "glow", "bolt_l")
    return m


def mirrorfang():
    m = Model()
    m.gem(8, -2.8, 3.0, name="pommel")
    m.centered(8, -1.4, 4.4, 1.8, 1.8, "grip", name="grip")
    m.centered(8, 4.4, 5.8, 6.4, 2.0, "gold", name="guard")
    m.diamond(4.6, 5.1, 1.8, 1.8, "gold", name="guard_cap_l")
    m.diamond(11.4, 5.1, 1.8, 1.8, "gold", name="guard_cap_r")
    m.gem(8, 5.1, 3.0, depth=2.4, name="guard_gem")
    x, y = m.path(8.0, 5.8, [(90, 6.0), (67.5, 4.0), (45, 3.4)], lambda i: (3.6, 3.2, 2.6)[i], 1.0, "mirror", "blade", edge=-1.3)
    m.path(x, y, [(22.5, 2.6), (0, 1.6)], lambda i: (1.8, 1.2)[i], 1.1, "bone", "fang")
    return m


def void_scythe():
    m = Model()
    m.gem(8, -14.0, 2.8, name="pommel")
    m.centered(8, -12.6, 22.0, 1.8, 1.8, "grip", name="snath")
    for y in (-4.0, 12.0):
        m.centered(8, y, y + 1.0, 2.4, 2.4, "steel", name=f"collar_{y}")
    m.box(8.8, 5.0, 7.2, 11.6, 6.2, 8.8, "grip", name="nib")
    m.centered(8, 20.4, 24.0, 3.2, 3.2, "bone", name="skull")
    m.centered(8, 21.8, 22.8, 2.4, 0.5, "glow", cz=9.8, name="skull_eyes")
    m.diamond(11.0, 22.0, 2.2, 1.2, "blade", name="back_spike")
    m.path(7.0, 22.6, [(180, 5.0), (202.5, 4.6), (225, 4.0), (247.5, 3.2)],
           lambda i: (3.6, 3.2, 2.6, 1.8)[i], 1.2, "blade", "blade", edge=-1.4)
    return m


def vampire_fang():
    m = Model()
    m.gem(8, -2.6, 2.8, name="pommel")
    m.centered(8, -1.3, 3.8, 1.8, 1.8, "grip", name="grip")
    m.centered(8, 3.6, 4.8, 4.6, 2.2, "gold", name="guard")
    # Two little bone fangs hanging off the guard, like a vampire's bite.
    m.path(6.2, 4.2, [(247.5, 1.8), (270, 1.4)], lambda i: (1.1, 0.8)[i], 1.0, "bone", "fang_l")
    m.path(9.8, 4.2, [(292.5, 1.8), (270, 1.4)], lambda i: (1.1, 0.8)[i], 1.0, "bone", "fang_r")
    m.gem(8, 4.2, 2.4, depth=2.6, name="guard_gem")
    x, y = m.path(8.0, 4.8, [(90, 5.0), (67.5, 3.4), (45, 2.4)], lambda i: (3.0, 2.4, 1.6)[i], 1.0, "blade", "blade", edge=-1.0)
    m.path(x, y, [(22.5, 1.8)], 1.0, 1.1, "bone", "tip")
    m.diamond(10.4, 2.4, 1.2, 1.2, "glow", name="blood_drop")
    return m


def blood_grimoire():
    m = Model()
    x1, x2, y1, y2 = 4.5, 11.5, 3.5, 12.5
    m.box(x1 + 0.4, y1 + 0.4, 7.0, x2 - 0.2, y2 - 0.4, 9.0, "parchment", name="pages")
    m.box(x1, y1, 9.0, x2, y2, 9.6, "hide", name="front_cover")
    m.box(x1, y1, 6.4, x2, y2, 7.0, "hide", name="back_cover")
    m.box(x1 - 0.5, y1, 6.4, x1 + 0.4, y2, 9.6, "hide", name="spine")
    for i, y in enumerate((y1 + 1.2, (y1 + y2) / 2 - 0.4, y2 - 2.0)):
        m.box(x1 - 0.7, y, 6.2, x1 + 0.5, y + 0.8, 9.8, "gold", name=f"spine_band_{i}")
    for i, (cx, cy) in enumerate(((x2 - 0.5, y1 + 0.5), (x2 - 0.5, y2 - 0.5), (x1 + 0.9, y1 + 0.5), (x1 + 0.9, y2 - 0.5))):
        m.box(cx - 0.6, cy - 0.6, 9.4, cx + 0.6, cy + 0.6, 9.9, "gold", name=f"corner_front_{i}")
        m.box(cx - 0.6, cy - 0.6, 6.1, cx + 0.6, cy + 0.6, 6.6, "gold", name=f"corner_back_{i}")
    m.gem(8.3, 8.4, 3.4, depth=0.8, cz=9.8, name="cover_gem")
    m.box(7.9, 3.0, 9.6, 8.7, 6.2, 9.95, "glow", name="blood_run")
    m.box(9.6, 0.6, 7.7, 10.2, 3.6, 8.1, "string", name="bookmark")
    m.diamond(9.9, 0.6, 0.9, 0.5, "glow", name="bookmark_drop")
    return m


# ---- the Blood Knight's armour (inventory models; the worn look is painted separately) ------

# The Blood Knight's armour icons: plain vanilla-shaped 16x16 sprites (a 3D model of a chestplate
# just looks like a box in the inventory). Each is a silhouette: '#' plate, 'r' crimson trim,
# 'g' glow, 'k' the visor slit. shade_icon() outlines it and lights it from the top left the
# same way for every piece, so the four read as one set.
ICON_COLORS = {"o": "#0a0a0c", "a": "#26262c", "b": "#36363e", "c": "#4a4a54", "h": "#70707c",
               "k": "#0d0707", "g": "#ff3b45", "G": "#ffa6ac", "r": "#b3141f", "d": "#7a0d16"}
ARMOR_ICONS = {
 "blood_knight_helm": [
  "................",
  "................",
  "................",
  "....########....",
  "...##########...",
  "..############..",
  "..############..",
  "..rrrrrrrrrrrr..",
  "..##kggkkggk##..",
  "..#####..#####..",
  "..#####..#####..",
  "..rrrrr..rrrrr..",
  "..#####..#####..",
  "................",
  "................",
  "................"],
 "blood_knight_cuirass": [
  "................",
  "..####....####..",
  ".######..######.",
  ".##############.",
  ".##############.",
  ".rrr########rrr.",
  ".###.######.###.",
  ".###.##gg##.###.",
  ".###.#gggg#.###.",
  ".rrr.##gg##.rrr.",
  ".###.######.###.",
  ".....rrrrrr.....",
  ".....######.....",
  ".....######.....",
  "................",
  "................"],
 "blood_knight_greaves": [
  "................",
  "................",
  "...##########...",
  "...rrrrggrrrr...",
  "...##########...",
  "...####..####...",
  "...####..####...",
  "...####..####...",
  "...#gg#..#gg#...",
  "...####..####...",
  "...####..####...",
  "...####..####...",
  "...####..####...",
  "...rrrr..rrrr...",
  "...####..####...",
  "................"],
 "blood_knight_sabatons": [
  "................",
  "................",
  "................",
  "...####...####..",
  "...rrrr...rrrr..",
  "...####...####..",
  "...####...####..",
  "...####...####..",
  ".######.######..",
  ".######.######..",
  ".#g####.#g####..",
  ".######.######..",
  "................",
  "................",
  "................",
  "................"],
}


def shade_icon(rows):
    H, W = len(rows), 16
    assert all(len(r) == W for r in rows), [len(r) for r in rows]
    inm = lambda x, y: 0 <= x < W and 0 <= y < H and rows[y][x] != "."
    out = [[None] * W for _ in range(H)]
    xs = [x for y in range(H) for x in range(W) if inm(x, y)]
    cx = (min(xs) + max(xs)) / 2
    for y in range(H):
        for x in range(W):
            ch = rows[y][x]
            if ch == ".":
                continue
            edge = not (inm(x - 1, y) and inm(x + 1, y) and inm(x, y - 1) and inm(x, y + 1))
            if edge:
                out[y][x] = "o"; continue
            lit = not inm(x - 1, y - 1) or not inm(x - 2, y) or not inm(x, y - 2)
            dark = not inm(x + 2, y) or not inm(x, y + 2) or not inm(x + 1, y + 1)
            if ch == "#":
                if lit and not dark:
                    out[y][x] = "h"
                elif dark and not lit:
                    out[y][x] = "a" if not inm(x, y + 2) else "b"
                else:
                    out[y][x] = "c" if x <= cx else "b"
            elif ch == "r":
                out[y][x] = "d" if dark and not lit else "r"
            elif ch == "g":
                out[y][x] = "G" if not rows[y - 1][x] == "g" and not rows[y][x - 1] == "g" else "g"
            else:
                out[y][x] = ch
    return out


def armor_icon(name):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(shade_icon(ARMOR_ICONS[name])):
        for x, ch in enumerate(row):
            if ch:
                img.putpixel((x, y), ImageColor.getrgb(ICON_COLORS[ch]) + (255,))
    return img


BOW_LIMB = (2.6, 3.0, 3.0, 2.6, 1.8)       # riser to tip, per segment
BOW_BENDS = {                              # segment directions (right limb), per stage
    0: (0, -22.5, -22.5, -45, 0),          # at rest: a recurve, the tips flick forward
    1: (0, -22.5, -22.5, -45, 0),
    2: (0, -22.5, -45, -45, -22.5),
    3: (0, -22.5, -45, -67.5, -22.5),      # full draw: the limbs bend deep
}


def paradox_bow(stage):
    """stage 0 = idle (no arrow), 1..3 = pulling_0..pulling_2.

    A recurve of dark blood-steel limbs with a glowing vein along the inside, bone spurs on the
    back, a gem riser and a blood-red string."""
    m = Model()
    # Unlike a sword (held by the end), a bow is held in the middle: the vanilla bow sprite's
    # resting string runs through the centre of the item. Put ours there too, so vanilla bow
    # display transforms hold it the same way; the riser sits ahead of it by the limbs' drop.
    idle_drop = -sum(length * math.sin(math.radians(bend)) for bend, length in zip(BOW_BENDS[0], BOW_LIMB))
    gy = 8.0 + idle_drop
    m.centered(8, gy - 1.5, gy + 1.5, 4.4, 2.0, "grip", name="riser_grip")
    m.centered(8, gy + 1.3, gy + 1.9, 5.0, 2.4, "frame", name="riser_cap_top")
    m.centered(8, gy - 1.9, gy - 1.3, 5.0, 2.4, "frame", name="riser_cap_low")
    m.gem(8, gy, 2.6, depth=2.8, name="riser_gem")
    thickness = (1.6, 1.5, 1.3, 1.1, 1.0)
    tips = []
    for side, sign in (("right", 1), ("left", -1)):
        x, y = 8 + sign * 2.2, gy
        for i, (bend, length) in enumerate(zip(BOW_BENDS[stage], BOW_LIMB)):
            d = bend if sign > 0 else 180 - bend
            # The vein runs along the belly (the string side) of each limb.
            inward = math.radians(d - 90 * sign)
            off = thickness[i] / 2 - 0.1
            m.segment(x + off * math.cos(inward), y + off * math.sin(inward), d, length, 0.4, 1.4, "glow",
                      overlap=0.1, name=f"vein_{side}_{i}")
            nx, ny = m.segment(x, y, d, length, thickness[i], 1.3, "blackblade", name=f"limb_{side}_{i}")
            if i in (1, 3):  # bone spurs out of the back of the limb
                back = d + 90 * sign
                m.segment(x + (nx - x) * 0.5, y + (ny - y) * 0.5, back, 2.0 if i == 1 else 1.5, 0.8, 0.8, "bone",
                          overlap=1.0, name=f"spur_{side}_{i}")
            x, y = nx, ny
        m.diamond(x, y, 1.3, 1.5, "glow", name=f"tip_{side}")
        tips.append((x, y))
    tip_x, tip_y = tips[0]
    span = tip_x - 8
    if stage == 0:
        m.box(8 - span, tip_y - 0.15, 7.9, tip_x, tip_y + 0.15, 8.1, "string", name="string")
        return m
    # The string is drawn back into a V (22.5 degrees each side). Deeper stages bend the limbs
    # further, which drops the tips and so the nock, and pull the arrowhead back to the riser.
    pull = span * math.tan(math.radians(22.5))
    nock_y = tip_y - pull
    length = math.hypot(span, pull)
    m.segment(8 + span, tip_y, 202.5, length, 0.35, 0.3, "string", overlap=0.2, name="string_r")
    m.segment(8 - span, tip_y, -22.5, length, 0.35, 0.3, "string", overlap=0.2, name="string_l")
    head_y = gy + {1: 8.0, 2: 6.0, 3: 4.2}[stage]
    m.box(7.8, nock_y, 7.8, 8.2, head_y, 8.2, "steel", name="arrow_shaft")
    m.diamond(8, head_y + 0.6, 2.0 if stage < 3 else 2.3, 0.8, "glow", name="arrow_head")
    m.box(7.1, nock_y + 0.2, 7.9, 8.9, nock_y + 2.6, 8.1, "blade", name="fletching")
    if stage == 3:
        m.diamond(8, head_y + 0.6, 3.4, 0.4, "gem", name="arrow_flare")
    return m


WEAPONS = {
    "riftblade": ("Bloodrift Blade", riftblade),
    "bloodhook": ("Bloodhook", bloodhook),
    "nullblade": ("Clotblade", nullblade),
    "meteor_gauntlet": ("Blood Meteor Gauntlet", meteor_gauntlet),
    "gravestone": ("Crimson Gravestone", gravestone),
    "chronos": ("Bleeding Chronos", chronos),
    "thunder_pike": ("Crimson Thunder Pike", thunder_pike),
    "mirrorfang": ("Blood Mirrorfang", mirrorfang),
    "void_scythe": ("Hemorrhage Scythe", void_scythe),
    "paradox_bow": ("Sanguine Paradox Bow", lambda: paradox_bow(0)),
    "vampire_fang": ("Vampire Fang", vampire_fang),
    "blood_grimoire": ("Blood Grimoire", blood_grimoire),
}
ARMOR = {  # flat icons, see ARMOR_ICONS
    "blood_knight_helm": ("Blood Knight Helm", None, "helmet"),
    "blood_knight_cuirass": ("Blood Knight Cuirass", None, "chestplate"),
    "blood_knight_greaves": ("Blood Knight Greaves", None, "leggings"),
    "blood_knight_sabatons": ("Blood Knight Sabatons", None, "boots"),
}
BOW_IDLE = None  # set in main(): framing reference for the bow's pull stages
VANILLA_BOW_SPAN = 18.4  # tip to tip of the vanilla bow sprite, (1,14) to (14,1)
BOW_STAGES = {"paradox_bow_pulling_0": 1, "paradox_bow_pulling_1": 2, "paradox_bow_pulling_2": 3}


# --------------------------------------------------------------------------------------------
# Texturing: pack every face into a per-weapon atlas and paint it.
# --------------------------------------------------------------------------------------------

FACES = ("north", "south", "east", "west", "up", "down")


def face_dims(el, face):
    (x1, y1, z1), (x2, y2, z2) = el["from"], el["to"]
    dx, dy, dz = x2 - x1, y2 - y1, z2 - z1
    return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[face]


def texel_point(el, face, u, v, w, h):
    """Model-space point under texel (u, v), using Minecraft's face UV orientation."""
    (x1, y1, z1), (x2, y2, z2) = el["from"], el["to"]
    fu, fv = (u + 0.5) / w, (v + 0.5) / h
    if face == "south":
        return (x1 + fu * (x2 - x1), y2 - fv * (y2 - y1), z2)
    if face == "north":
        return (x2 - fu * (x2 - x1), y2 - fv * (y2 - y1), z1)
    if face == "east":
        return (x2, y2 - fv * (y2 - y1), z2 - fu * (z2 - z1))
    if face == "west":
        return (x1, y2 - fv * (y2 - y1), z1 + fu * (z2 - z1))
    if face == "up":
        return (x1 + fu * (x2 - x1), y2, z1 + fv * (z2 - z1))
    return (x1 + fu * (x2 - x1), y1, z2 - fv * (z2 - z1))


def texel_size(d):
    return max(1, int(math.ceil(d - 0.05)))


GUTTER = 1  # texels of padding around every face in the atlas


def pack(rects, width):
    """Shelf packer: rects = [(key, w, h)] -> {key: (x, y)}, total height."""
    placed, x, y, shelf = {}, 0, 0, 0
    for key, w, h in sorted(rects, key=lambda r: (-r[2], -r[1])):
        if x + w > width:
            x, y, shelf = 0, y + shelf, 0
        placed[key] = (x, y)
        x += w
        shelf = max(shelf, h)
    return placed, y + shelf


# --------------------------------------------------------------------------------------------
# Z-fighting: two cubes whose faces lie in the same plane, facing the same way, flicker between
# each other's textures ("texture clipping"). Every model is checked in world space (element
# rotations applied) and the smaller face of each clash is pushed out by a hair so one face
# always wins.
# --------------------------------------------------------------------------------------------

ZF_EPS = 1e-4
ZF_NUDGE = 0.02


def _rotate(el, p, about_origin=True):
    if not el["rot"]:
        return p
    axis, angle, origin = el["rot"]
    a = math.radians(angle)
    o = list(origin) if about_origin else [0.0, 0.0, 0.0]
    # Right-handed about the axis, like Minecraft: +angle about Z turns +X toward +Y, about X
    # turns +Y toward +Z, about Y turns +Z toward +X.
    i, j = {"x": (1, 2), "y": (2, 0), "z": (0, 1)}[axis]
    q = list(p)
    di, dj = p[i] - o[i], p[j] - o[j]
    q[i] = o[i] + di * math.cos(a) - dj * math.sin(a)
    q[j] = o[j] + di * math.sin(a) + dj * math.cos(a)
    return tuple(q)


def _world_face(el, axis, sign):
    lo, hi = el["from"], el["to"]
    u, v = [i for i in range(3) if i != axis]
    corners = []
    for a, b in ((0, 0), (1, 0), (1, 1), (0, 1)):
        q = [0.0, 0.0, 0.0]
        q[axis] = hi[axis] if sign > 0 else lo[axis]
        q[u] = (lo, hi)[a][u]
        q[v] = (lo, hi)[b][v]
        corners.append(_rotate(el, q))
    n = [0.0, 0.0, 0.0]
    n[axis] = float(sign)
    return np.array(_rotate(el, n, about_origin=False)), [np.array(c) for c in corners]


def _clip(subject, clipper):
    """Sutherland-Hodgman: convex polygon intersection in 2D."""
    def inside(p, a, b):
        return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]) >= -1e-9
    def cross_point(p1, p2, a, b):
        d1, d2 = p2 - p1, b - a
        den = d1[0] * d2[1] - d1[1] * d2[0]
        t = ((a[0] - p1[0]) * d2[1] - (a[1] - p1[1]) * d2[0]) / den
        return p1 + d1 * t
    out = subject
    for i in range(len(clipper)):
        a, b = clipper[i], clipper[(i + 1) % len(clipper)]
        inp, out = out, []
        for j in range(len(inp)):
            cur, prev = inp[j], inp[j - 1]
            if inside(cur, a, b):
                if not inside(prev, a, b):
                    out.append(cross_point(prev, cur, a, b))
                out.append(cur)
            elif inside(prev, a, b):
                out.append(cross_point(prev, cur, a, b))
        if not out:
            return []
    return out


def _area(poly):
    return abs(sum(poly[i][0] * poly[i - 1][1] - poly[i - 1][0] * poly[i][1] for i in range(len(poly)))) / 2


def _ccw(poly):
    return poly if sum(poly[i - 1][0] * poly[i][1] - poly[i][0] * poly[i - 1][1] for i in range(len(poly))) > 0 else poly[::-1]


def zfighting(model):
    """[(i, axis_i, sign_i, j, axis_j, sign_j, overlap_area)] for every clashing pair of faces."""
    faces = [(i, axis, sign) + _world_face(el, axis, sign)
             for i, el in enumerate(model.elements) for axis in range(3) for sign in (-1, 1)]
    clashes = []
    for k, (i, ai, si, ni, ci) in enumerate(faces):
        for j, aj, sj, nj, cj in faces[k + 1:]:
            if i == j or np.dot(ni, nj) < 1 - 1e-6 or abs(np.dot(ni, ci[0]) - np.dot(nj, cj[0])) > ZF_EPS:
                continue
            # 2D basis in the shared plane.
            e1 = ci[1] - ci[0]
            e1 = e1 / np.linalg.norm(e1)
            e2 = np.cross(ni, e1)
            to2d = lambda pts: _ccw([np.array([np.dot(p, e1), np.dot(p, e2)]) for p in pts])
            overlap = _clip(to2d(ci), to2d(cj))
            if len(overlap) >= 3 and _area(overlap) > ZF_EPS:
                clashes.append((i, ai, si, j, aj, sj, _area(overlap)))
    return clashes


def resolve_zfighting(model, name):
    """Pushes the smaller face of every clash outward until the model is clean."""
    fixed = 0
    for _ in range(12):
        clashes = zfighting(model)
        if not clashes:
            if fixed:
                print(f"  {name}: fixed {fixed} flickering face pair(s)")
            return
        seen = set()
        for i, ai, si, j, aj, sj, _ in clashes:
            area = lambda e, ax: math.prod(e["to"][d] - e["from"][d] for d in range(3) if d != ax)
            k, ax, sg = (i, ai, si) if area(model.elements[i], ai) <= area(model.elements[j], aj) else (j, aj, sj)
            if (k, ax, sg) in seen:
                continue
            seen.add((k, ax, sg))
            el = model.elements[k]
            if sg > 0:
                el["to"] = el["to"][:ax] + [el["to"][ax] + ZF_NUDGE] + el["to"][ax + 1:]
            else:
                el["from"] = el["from"][:ax] + [el["from"][ax] - ZF_NUDGE] + el["from"][ax + 1:]
            fixed += 1
    raise AssertionError(f"{name}: z-fighting didn't converge: {zfighting(model)[:3]}")


def build_texture(models, weapon):
    """Paints one atlas shared by `models` (a weapon and, for the bow, its pull stages)."""
    rects, meta = [], {}
    for mi, model in enumerate(models):
        for ei, el in enumerate(model.elements):
            el["seed"] = int(hashlib.md5(f"{weapon}:{el['name']}".encode()).hexdigest()[:6], 16) % 997
            size = [el["to"][i] - el["from"][i] for i in range(3)]
            el["long_axis"] = int(np.argmax(size[:2]))
            for face in FACES:
                w, h = (texel_size(d) for d in face_dims(el, face))
                key = (mi, ei, face)
                rects.append((key, w + 2 * GUTTER, h + 2 * GUTTER))
                meta[key] = (el, w, h)
    for size in (32, 64, 128, 256):
        placed, height = pack(rects, size)
        if height <= size:
            break
    img = np.zeros((size, size, 4), dtype=float)
    uvs = {}
    for key, (gx, gy) in placed.items():
        el, w, h = meta[key]
        face = key[2]
        px, py = gx + GUTTER, gy + GUTTER
        for v in range(h):
            for u in range(w):
                c = paint_texel(el["mat"], face, u, v, w, h, texel_point(el, face, u, v, w, h), el)
                img[py + v, px + u, :3] = np.clip(c, 0, 255)
                img[py + v, px + u, 3] = 255
        # Repeat each face's edge texels into its gutter, so filtering and mipmaps at a face's edge
        # sample its own colours instead of the neighbouring face's.
        for d in range(1, GUTTER + 1):
            img[py - d, px:px + w] = img[py, px:px + w]
            img[py + h - 1 + d, px:px + w] = img[py + h - 1, px:px + w]
        for d in range(1, GUTTER + 1):
            img[py - GUTTER:py + h + GUTTER, px - d] = img[py - GUTTER:py + h + GUTTER, px]
            img[py - GUTTER:py + h + GUTTER, px + w - 1 + d] = img[py - GUTTER:py + h + GUTTER, px + w - 1]
        s = 16.0 / size
        uvs[key] = [round(px * s, 4), round(py * s, 4), round((px + w) * s, 4), round((py + h) * s, 4)]
    return Image.fromarray(img.astype("uint8"), "RGBA"), uvs, size


# --------------------------------------------------------------------------------------------
# Export.
# --------------------------------------------------------------------------------------------

def r3(v):
    return round(v, 3)


def element_json(el, mi, ei, uvs):
    out = {"name": el["name"], "from": [r3(v) for v in el["from"]], "to": [r3(v) for v in el["to"]]}
    if el["rot"]:
        axis, angle, origin = el["rot"]
        out["rotation"] = {"angle": angle, "axis": axis, "origin": [r3(v) for v in origin]}
    if el["glow"]:
        out["light_emission"] = 15
    out["faces"] = {face: {"uv": uvs[(mi, ei, face)], "texture": "#atlas"} for face in FACES}
    return out


def extent(model):
    """Longest extent of the model once pre-rotated by -45 degrees (as shown in hand/GUI)."""
    pts = []
    for e in model.elements:
        (x1, y1, z1), (x2, y2, z2) = e["from"], e["to"]
        pts += [_rotate(e, (x, y, z))[:2] for x in (x1, x2) for y in (y1, y2) for z in (z1, z2)]
    a = math.radians(-45)
    rot = [((x - 8) * math.cos(a) - (y - 8) * math.sin(a), (x - 8) * math.sin(a) + (y - 8) * math.cos(a)) for x, y in pts]
    xs, ys = [p[0] for p in rot], [p[1] for p in rot]
    return max(max(xs) - min(xs), max(ys) - min(ys))


def v3(s):
    return [r3(s)] * 3


def model_corners(model):
    """Every cube corner in model space (element rotations applied)."""
    pts = []
    for e in model.elements:
        (x1, y1, z1), (x2, y2, z2) = e["from"], e["to"]
        pts += [_rotate(e, (x, y, z)) for x in (x1, x2) for y in (y1, y2) for z in (z1, z2)]
    return np.array(pts) - 8.0


def fit_to_slot(transform, model, fill=15.0, max_scale=1.0, base=(0.0, 0.0, 0.0)):
    """Scale and centre `transform` so the model, rotated exactly as Minecraft will rotate it,
    fills `fill` of the 16-unit slot and sits in its middle."""
    projected = model_corners(model) @ euler_xyz(*transform["rotation"]).T
    lo, hi = projected.min(axis=0), projected.max(axis=0)
    scale = min(max_scale, fill / max(hi[0] - lo[0], hi[1] - lo[1]))
    mid = (lo + hi) / 2 * scale
    transform["scale"] = v3(scale)
    transform["translation"] = [r3(max(-80.0, min(80.0, base[i] - mid[i]))) for i in (0, 1)] + [base[2]]
    return transform


def model_center(model):
    """Centre of the model's bounding box in model space (element rotations included)."""
    pts = []
    for e in model.elements:
        (x1, y1, z1), (x2, y2, z2) = e["from"], e["to"]
        pts += [_rotate(e, (x, y, z)) for x in (x1, x2) for y in (y1, y2) for z in (z1, z2)]
    return [(min(p[i] for p in pts) + max(p[i] for p in pts)) / 2 for i in range(3)]


def euler_xyz(rx, ry, rz):
    """Minecraft's display rotation: Quaternionf.rotationXYZ, i.e. Rx * Ry * Rz."""
    ax, ay, az = (math.radians(v) for v in (rx, ry, rz))
    rot_x = np.array([[1, 0, 0], [0, math.cos(ax), -math.sin(ax)], [0, math.sin(ax), math.cos(ax)]])
    rot_y = np.array([[math.cos(ay), 0, math.sin(ay)], [0, 1, 0], [-math.sin(ay), 0, math.cos(ay)]])
    rot_z = np.array([[math.cos(az), -math.sin(az), 0], [math.sin(az), math.cos(az), 0], [0, 0, 1]])
    return rot_x @ rot_y @ rot_z


def recentered(transform, center, base=(0.0, 0.0, 0.0), keep_z=False):
    """Adds the translation that puts the model's bounding-box centre where the item's centre
    would be, so icons, item frames and dropped items sit in the middle of their slot."""
    offset = (np.array(center) - 8.0) * transform["scale"][0]
    moved = euler_xyz(*transform["rotation"]) @ offset
    t = [base[i] - moved[i] for i in range(3)]
    if not keep_z:
        t[2] = base[2]
    transform["translation"] = [r3(max(-80.0, min(80.0, v))) for v in t]
    return transform


def display(model, weapon):
    size = extent(model)
    gui = min(1.0, 15.0 / size)
    ground = min(0.5, 8.0 / size)
    hand = 0.85 * max(0.62, min(1.0, 26.0 / size))
    fp = 0.68 * max(0.62, min(1.0, 26.0 / size))
    # Right-hand values are vanilla item/handheld with Z shifted by -45 (see module docstring).
    # Left-hand entries are omitted on purpose: Minecraft mirrors the right-hand transform.
    disp = {
        "thirdperson_righthand": {"rotation": [0, -90, 10], "translation": [0, 4, 0.5], "scale": v3(hand)},
        "firstperson_righthand": {"rotation": [0, -90, -20], "translation": [1.13, 3.2, 1.13], "scale": v3(fp)},
        "gui": {"rotation": [-20, 30, -45], "translation": [0, 0, 0], "scale": v3(gui)},
        "ground": {"rotation": [0, 0, -45], "translation": [0, 2, 0], "scale": v3(ground)},
        "fixed": {"rotation": [0, 180, -45], "translation": [0, 0, 0], "scale": v3(gui)},
        "head": {"rotation": [0, 180, -45], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    }
    if weapon.startswith("paradox_bow"):
        # Vanilla item/bow transforms. The vanilla bow sprite is not drawn like a sword: its arrow
        # points up-LEFT (the limbs run bottom-left to top-right), while ours points +Y. So the
        # fold here is +45, not the swords' -45 (with -45 the bow was held 90 degrees off, lying
        # across the screen). Scaled so tip to tip it spans what the vanilla sprite does (about
        # 18.4 units, corner to corner), whatever the pull stage.
        corners = model_corners(BOW_IDLE)
        bow = VANILLA_BOW_SPAN / (corners[:, 0].max() - corners[:, 0].min())
        disp["thirdperson_righthand"] = {"rotation": [-80, 260, -40 + 45], "translation": [-1, -2, 2.5], "scale": v3(0.9 * bow)}
        disp["firstperson_righthand"] = {"rotation": [0, -90, 25 + 45], "translation": [1.13, 3.2, 1.13], "scale": v3(0.68 * bow)}
    if weapon == "meteor_gauntlet":
        # Worn over the fist rather than swung: sit it upright on the hand.
        disp["thirdperson_righthand"] = {"rotation": [75, 0, 0], "translation": [0, 1.5, 1.5], "scale": v3(0.6)}
        disp["firstperson_righthand"] = {"rotation": [-10, -80, 10], "translation": [2, 0, -2], "scale": v3(0.7)}
        disp["gui"] = {"rotation": [25, -35, 0], "translation": [0, 0, 0], "scale": v3(0.9)}
        disp["ground"] = {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": v3(0.5)}
        disp["fixed"] = {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": v3(0.9)}
    if weapon == "blood_grimoire":
        # Held like a book (vanilla item/generated poses), shown cover-first in the inventory.
        disp["thirdperson_righthand"] = {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": v3(0.55)}
        disp["firstperson_righthand"] = {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": v3(0.68)}
        disp["gui"] = {"rotation": [15, -25, 0], "translation": [0, 0, 0], "scale": v3(1.0)}
        disp["ground"] = {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": v3(0.5)}
        disp["fixed"] = {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": v3(1.0)}
        disp["head"] = {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]}
    # The bow's pull stages must keep the idle model's framing, or the icon would jump around.
    framing = BOW_IDLE if weapon.startswith("paradox_bow") else model
    fit_to_slot(disp["gui"], framing, max_scale=disp["gui"]["scale"][0] if weapon in ("meteor_gauntlet", "blood_grimoire") else 1.0)
    fit_to_slot(disp["fixed"], framing, fill=14.0)
    fit_to_slot(disp["ground"], framing, fill=7.0, max_scale=0.5, base=(0.0, 2.0, 0.0))
    return disp


def item_definition(weapon):
    model = lambda path: {"type": "minecraft:model", "model": f"{NS}:item/{path}"}
    if weapon != "paradox_bow":
        return {"model": model(weapon)}
    # Same structure as vanilla assets/minecraft/items/bow.json: the client swaps in the pull
    # stages from the use duration, so the draw animates like a vanilla bow.
    return {"model": {
        "type": "minecraft:condition",
        "property": "minecraft:using_item",
        "on_false": model("paradox_bow"),
        "on_true": {
            "type": "minecraft:range_dispatch",
            "property": "minecraft:use_duration",
            "scale": 0.05,
            "entries": [
                {"threshold": 0.65, "model": model("paradox_bow_pulling_1")},
                {"threshold": 0.9, "model": model("paradox_bow_pulling_2")},
            ],
            "fallback": model("paradox_bow_pulling_0"),
        },
    }}


def check(name, elements):
    for e in elements:
        for c in e["from"] + e["to"]:
            assert -16 <= c <= 32, f"{name}/{e['name']} out of bounds: {c}"
        if "rotation" in e:
            assert e["rotation"]["angle"] in (-45, -22.5, 0, 22.5, 45), f"{name}/{e['name']} bad angle"


def main():
    models_out = os.path.join(ASSETS, "models", "item")
    items_out = os.path.join(ASSETS, "items")
    tex_out = os.path.join(ASSETS, "textures", "item")
    prev_dir = os.path.join(ROOT, "docs", "preview")
    for d in (models_out, items_out, tex_out, os.path.join(prev_dir, "textures")):
        os.makedirs(d, exist_ok=True)
    # Clean up the v1 shared material textures.
    shutil.rmtree(os.path.join(tex_out, "blood"), ignore_errors=True)
    for f in os.listdir(os.path.join(prev_dir, "textures")):
        os.remove(os.path.join(prev_dir, "textures", f))

    preview = {"textures": {}, "weapons": {}}
    definitions = {}
    global BOW_IDLE
    BOW_IDLE = paradox_bow(0)
    items = {**{k: v for k, v in WEAPONS.items()}, **{k: (t, b) for k, (t, b, _) in ARMOR.items()}}
    for weapon, (title, build) in items.items():
        if build is None:
            write_flat_item(weapon, models_out, tex_out)
            definitions[weapon] = item_definition(weapon)
            with open(os.path.join(items_out, f"{weapon}.json"), "w") as f:
                json.dump(definitions[weapon], f, indent=2)
            print(f"{weapon:24s} flat 16x16 icon")
            continue
        variants = [(weapon, build())]
        if weapon == "paradox_bow":
            variants += [(name, paradox_bow(stage)) for name, stage in BOW_STAGES.items()]
        for name, model in variants:
            resolve_zfighting(model, name)
        atlas, uvs, size = build_texture([m for _, m in variants], weapon)
        atlas.save(os.path.join(tex_out, f"{weapon}.png"))
        atlas.save(os.path.join(prev_dir, "textures", f"{weapon}.png"))
        preview["textures"][weapon] = f"textures/{weapon}.png"
        for mi, (name, model) in enumerate(variants):
            elements = [element_json(el, mi, ei, uvs) for ei, el in enumerate(model.elements)]
            check(name, elements)
            texture = f"{NS}:item/{weapon}"
            model_json = {
                "credit": "Bloodbath 3D weapons - generated by tools/models/generate.py",
                "texture_size": [size, size],
                "textures": {"atlas": texture, "particle": texture},
                "elements": elements,
                "display": display(model, name),
            }
            with open(os.path.join(models_out, f"{name}.json"), "w") as f:
                json.dump(model_json, f, indent=1)
            preview["weapons"][name] = {"title": title, "texture": weapon, "elements": elements}
            print(f"{name:24s} {len(elements):3d} cubes  atlas {size}x{size}")
        definitions[weapon] = item_definition(weapon)
        with open(os.path.join(items_out, f"{weapon}.json"), "w") as f:
            json.dump(definitions[weapon], f, indent=2)

    with open(os.path.join(prev_dir, "models.json"), "w") as f:
        json.dump(preview, f, separators=(",", ":"))
    write_vanilla_overrides(definitions)
    write_armor_overrides(definitions)
    write_knight_equipment()
    write_blood_core()
    write_blood_nova()
    write_boss_bar()
    write_hud_and_gui()
    import boss  # the Blood Knight boss model and rig (tools/models/boss.py)
    boss.main()
    write_tooltip_sprites()
    write_pack_meta()


def write_flat_item(name, models_out, tex_out):
    armor_icon(name).save(os.path.join(tex_out, f"{name}.png"))
    with open(os.path.join(models_out, f"{name}.json"), "w") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}}, f, indent=1)


def vanilla_model(path):
    return {"type": "minecraft:model", "model": f"minecraft:item/{path}"}


# The Paper plugin's weapons are real netherite swords and bows carrying a custom_model_data string
# "bloodbath:<id>". Overriding the vanilla definitions (rather than pointing the item_model
# component at our own) means a player without the pack sees a normal netherite sword or bow
# instead of a missing-model cube. Everything else falls through to the vanilla model.
def write_vanilla_overrides(definitions):
    out = os.path.join(PACK, "assets", "minecraft", "items")
    os.makedirs(out, exist_ok=True)

    def select(cases, fallback):
        return {"model": {
            "type": "minecraft:select",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "cases": [{"when": f"bloodbath:{w}", "model": definitions[w]["model"]} for w in cases],
            "fallback": fallback,
        }}

    swords = [w for w in definitions if w in WEAPONS and w != "paradox_bow"]
    vanilla_bow = {
        "type": "minecraft:condition",
        "property": "minecraft:using_item",
        "on_false": vanilla_model("bow"),
        "on_true": {
            "type": "minecraft:range_dispatch",
            "property": "minecraft:use_duration",
            "scale": 0.05,
            "entries": [
                {"threshold": 0.65, "model": vanilla_model("bow_pulling_1")},
                {"threshold": 0.9, "model": vanilla_model("bow_pulling_2")},
            ],
            "fallback": vanilla_model("bow_pulling_0"),
        },
    }
    with open(os.path.join(out, "netherite_sword.json"), "w") as f:
        json.dump(select(swords, vanilla_model("netherite_sword")), f, indent=2)
    with open(os.path.join(out, "bow.json"), "w") as f:
        json.dump(select(["paradox_bow"], vanilla_bow), f, indent=2)


# The Blood Core: a nether star underneath, drawn as a thorned blood crystal whose heart beats
# (lub-dub, rest). Animated item texture; the nether star definition swaps it in only for items
# with custom_model_data "bloodbath:blood_core", so real nether stars keep their look.
CORE_BEAT = [0.15, 0.95, 0.55, 1.0, 0.6, 0.35, 0.22, 0.15, 0.12, 0.12]
CORE_FRAME_TICKS = [4, 2, 2, 2, 2, 3, 3, 4, 6, 6]


def _core_shape(x, y):
    ax, ay = abs(x), abs(y)
    body = ax + ay <= 5.0
    arm_x = ax <= 7.5 and ay <= 2.3 * (1 - ax / 8.6) + 0.35
    arm_y = ay <= 7.5 and ax <= 2.3 * (1 - ay / 8.6) + 0.35
    diag = abs(ax - ay) <= 1.1 and ax + ay <= 8.6
    return body or arm_x or arm_y or diag


def _core_frame(p):
    c = {k: ImageColor.getrgb(v) for k, v in {
        "out": "#2a040a", "rim": "#6e0a15", "dark": "#4a0710", "mid": "#7d0c18", "red": "#b3121f",
        "hi": "#e0303c", "shine": "#ff8a92", "white": "#ffe6e8"}.items()}
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    inside = [[_core_shape(x - 7.5, y - 7.5) for x in range(16)] for y in range(16)]
    inn = lambda x, y: 0 <= x < 16 and 0 <= y < 16 and inside[y][x]
    for y in range(16):
        for x in range(16):
            if not inside[y][x]:
                continue
            dx, dy = x - 7.5, y - 7.5
            if not (inn(x + 1, y) and inn(x - 1, y) and inn(x, y + 1) and inn(x, y - 1)):
                lit = (not inn(x - 1, y) or not inn(x, y - 1)) and dx + dy < 0
                img.putpixel((x, y), c["rim" if lit else "out"] + (255,))
                continue
            # Four facets lit from the top left, with ridges along the axes.
            col = c["hi"] if dx < 0 and dy < 0 else c["red"] if dy < 0 else c["mid"] if dx < 0 else c["dark"]
            if abs(dx) < 0.6 and dy < 0 or abs(dy) < 0.6 and dx < 0:
                col = c["shine"] if abs(dx) + abs(dy) > 3 else c["hi"]
            elif abs(dx) < 0.6 or abs(dy) < 0.6:
                col = c["mid"]
            d = abs(dx) + abs(dy)
            r = 1.6 + 1.4 * p                      # the heart swells with each beat
            if d <= r:
                t = d / r
                col = c["white"] if t < 0.35 and p > 0.5 else c["shine"] if t < 0.6 else c["hi"]
            elif d <= r + 1.0 and p > 0.45:
                col = tuple((a + b) // 2 for a, b in zip(col, c["hi"]))
            img.putpixel((x, y), col + (255,))
    return img


def write_blood_core():
    tex = os.path.join(ASSETS, "textures", "item")
    strip = Image.new("RGBA", (16, 16 * len(CORE_BEAT)))
    for i, p in enumerate(CORE_BEAT):
        strip.paste(_core_frame(p), (0, 16 * i))
    strip.save(os.path.join(tex, "blood_core.png"))
    with open(os.path.join(tex, "blood_core.png.mcmeta"), "w") as f:
        json.dump({"animation": {"frames": [{"index": i, "time": t} for i, t in enumerate(CORE_FRAME_TICKS)]}}, f, indent=2)
    with open(os.path.join(ASSETS, "models", "item", "blood_core.json"), "w") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/blood_core"}}, f, indent=1)
    with open(os.path.join(ASSETS, "items", "blood_core.json"), "w") as f:
        json.dump({"model": {"type": "minecraft:model", "model": f"{NS}:item/blood_core"}}, f, indent=2)
    with open(os.path.join(PACK, "assets", "minecraft", "items", "nether_star.json"), "w") as f:
        json.dump({"model": {
            "type": "minecraft:select",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "cases": [{"when": "bloodbath:blood_core", "model": {"type": "minecraft:model", "model": f"{NS}:item/blood_core"}}],
            "fallback": vanilla_model("nether_star"),
        }}, f, indent=2)


# ---- custom HUD, GUI, boss bar and particle art ------------------------------------------
# All of it is only ever shown to players the plugin knows have the pack loaded.

PAL = {k: ImageColor.getrgb(v) for k, v in {
    "void": "#0b0506", "deep": "#170709", "well": "#0e0405", "rim": "#5c0b13", "crimson": "#a3121c",
    "bright": "#e0303c", "glow": "#ff5a64", "pale": "#ffb3b8", "steel": "#3a3a42", "steel_hi": "#6a6a76",
    "bone": "#c7bca2"}.items()}


def _px(img, x, y, key_or_rgb, a=255):
    rgb = PAL[key_or_rgb] if isinstance(key_or_rgb, str) else key_or_rgb
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), tuple(rgb) + (a,))


def _nova_frame(i, n=16, size=32):
    """A blood nova: a hot core, then a ring that tears outward into droplets and thins away."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    t = i / (n - 1)
    ease = 1 - (1 - t) ** 2
    c = (size - 1) / 2
    ring = 3 + 11.5 * ease
    width = max(0.8, 3.2 * (1 - t))
    for y in range(size):
        for x in range(size):
            dx, dy = x - c, y - c
            d = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            h = _hash(x, y, i, 7)
            jag = 1.0 + 0.9 * math.sin(ang * 7 + i * 0.7) * t
            if abs(d - ring) <= width * jag:
                # The ring breaks up as it travels.
                if h > t * 0.7:
                    col = "pale" if t < 0.2 and abs(d - ring) < 0.6 else "bright" if t < 0.35 else "crimson" if t < 0.85 else "rim"
                    _px(img, x, y, col)
            elif d < ring - width and t < 0.3:
                core = 1 - d / max(1, ring - width)
                if h < 0.35 + core * 0.6:
                    _px(img, x, y, "pale" if core > 0.6 and t < 0.15 else "glow" if core > 0.3 else "bright")
    # Droplets flung past the ring.
    for k in range(10):
        ang = k * 2 * math.pi / 10 + _hash(k, 3, 1, 9) * 0.5
        dist = ring + 2 + 6 * ease * (0.6 + 0.4 * _hash(k, 5, 2, 9))
        if dist < c and t > 0.1 and _hash(k, i, 4, 9) > t * 0.7:
            x, y = int(round(c + math.cos(ang) * dist)), int(round(c + math.sin(ang) * dist))
            col = "bright" if t < 0.5 else "crimson"
            _px(img, x, y, col)
            if t < 0.6:
                _px(img, x + 1, y, col)
                _px(img, x, y + 1, col)
    return img


def write_blood_nova():
    """Redraws the warden's sonic boom (nothing else uses it) as a blood nova, 16 frames."""
    tex = os.path.join(ASSETS, "textures", "particle")
    os.makedirs(tex, exist_ok=True)
    names = []
    for i in range(16):
        _nova_frame(i).save(os.path.join(tex, f"blood_nova_{i}.png"))
        names.append(f"{NS}:blood_nova_{i}")
    out = os.path.join(PACK, "assets", "minecraft", "particles")
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, "sonic_boom.json"), "w") as f:
        json.dump({"textures": names}, f, indent=2)


def write_boss_bar():
    """The Blood Knight's bar: the yellow boss bar (unused by vanilla) redrawn in blood."""
    out = os.path.join(PACK, "assets", "minecraft", "textures", "gui", "sprites", "boss_bar")
    os.makedirs(out, exist_ok=True)
    w, h = 182, 5
    bg = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    fg = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for x in range(w):
        for y in range(h):
            edge = y in (0, h - 1) or x in (0, w - 1)
            _px(bg, x, y, "rim" if edge else "well")
            if edge:
                _px(fg, x, y, "crimson" if y == 0 else "rim")
            else:
                wave = 0.5 + 0.5 * math.sin(x * 0.35) * math.sin(x * 0.07 + 1.3)
                col = "glow" if y == 1 and wave > 0.55 else "bright" if y == 1 else "crimson" if y == 2 else "rim"
                if y == 2 and _hash(x, y, 1, 3) > 0.86:
                    col = "bright"
                _px(fg, x, y, col)
    # Little drips hanging off the bottom of the frame.
    for x in range(6, w - 6, 13):
        _px(bg, x, h - 1, "crimson")
    bg.save(os.path.join(out, "yellow_background.png"))
    fg.save(os.path.join(out, "yellow_progress.png"))


# HUD glyphs (font unchartedsmp:hud): blood-drop bar segments and a few icons.
HUD_GLYPHS = {
    "\ue000": ("bar_full", [
        "..#..",
        ".###.",
        "#####",
        "#####",
        ".###.",
    ]),
    "\ue001": ("bar_empty", [
        "..#..",
        ".#.#.",
        "#...#",
        "#...#",
        ".###.",
    ]),
    "\ue002": ("ready", [
        "...#...",
        "..###..",
        ".#####.",
        "###o###",
        "##ooo##",
        ".#####.",
        "..###..",
    ]),
    "\ue003": ("skull", [
        ".#####.",
        "#######",
        "#.###.#",
        "#######",
        ".##.##.",
        ".#.#.#.",
    ]),
}


def _glyph(name, rows):
    img = Image.new("RGBA", (len(rows[0]), len(rows)), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "#":
                if name == "bar_empty":
                    _px(img, x, y, "rim")
                elif name == "skull":
                    _px(img, x, y, "bone" if y < 4 else "pale")
                else:
                    _px(img, x, y, "glow" if y <= 1 or (x == 1 and y == 2) else "bright" if y < len(rows) - 1 else "crimson")
            elif ch == "o":
                _px(img, x, y, "pale")
    return img


# The armory's backdrop (font unchartedsmp:gui): drawn by the container title, under the items.
GUI_WIDTH = 176


def _armory_background(rows, slots):
    """176 wide, down to just past the last container row. {slot index: style} gets a framed well."""
    height = 17 + rows * 18 + 4
    img = Image.new("RGBA", (GUI_WIDTH, height), (0, 0, 0, 0))
    for y in range(height):
        for x in range(GUI_WIDTH):
            n = fbm(x * 0.15, y * 0.15, 0.0, 17)
            col = tuple(int(a + (b - a) * n * 0.6) for a, b in zip(PAL["void"], PAL["deep"]))
            border = x < 2 or x >= GUI_WIDTH - 2 or y < 2 or y >= height - 2
            inner = x in (2, GUI_WIDTH - 3) or y in (2, height - 3)
            if border:
                col = PAL["rim"] if (x + y) % 7 else PAL["crimson"]
            elif inner:
                col = PAL["crimson"] if y == 2 else PAL["rim"]
            img.putpixel((x, y), col + (255,))
    # Blood running down from the top edge.
    for x in range(4, GUI_WIDTH - 4):
        length = int(_hash(x // 3, 1, 1, 21) * 9) if _hash(x // 3, 2, 1, 21) > 0.55 else 0
        for y in range(3, 3 + length):
            _px(img, x, y, "crimson" if y < 3 + length - 1 else "bright")
    # A thin crimson rule under the title.
    for x in range(8, GUI_WIDTH - 8):
        _px(img, x, 15, "rim")
    for slot, style in slots.items():
        sx, sy = 7 + (slot % 9) * 18, 17 + (slot // 9) * 18
        for y in range(18):
            for x in range(18):
                edge = x in (0, 17) or y in (0, 17)
                if style == "weapon":
                    col = "rim" if edge else "deep" if x == 1 or y == 1 else "well"
                    if (x, y) in ((0, 0), (17, 0), (0, 17), (17, 17)):
                        col = "bright"
                elif style == "armor":
                    col = "steel" if edge else "well"
                    if y == 0 and not x in (0, 17):
                        col = "steel_hi"
                elif style == "core":
                    glow = max(0.0, 1 - math.hypot(x - 8.5, y - 8.5) / 9.0)
                    col = "bright" if edge else tuple(int(a + (b - a) * glow) for a, b in zip(PAL["well"], PAL["rim"]))
                else:  # header / close: a quieter well
                    col = "rim" if edge else "deep"
                _px(img, sx + x, sy + y, col)
    return img


def write_hud_and_gui():
    font_tex = os.path.join(ASSETS, "textures", "font")
    os.makedirs(font_tex, exist_ok=True)
    providers = []
    for char, (name, rows) in HUD_GLYPHS.items():
        _glyph(name, rows).save(os.path.join(font_tex, f"hud_{name}.png"))
        providers.append({"type": "bitmap", "file": f"{NS}:font/hud_{name}.png", "ascent": len(rows) - 1,
                          "height": len(rows), "chars": [json.loads('"' + char + '"')]})
    fonts = os.path.join(ASSETS, "font")
    os.makedirs(fonts, exist_ok=True)
    with open(os.path.join(fonts, "hud.json"), "w") as f:
        json.dump({"providers": providers}, f, indent=2)

    weapon_slots = [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25]
    gui = []
    for rows, char, extra in ((5, "\ue100", {29: "armor", 30: "armor", 32: "armor", 33: "armor", 31: "core", 40: "plain"}),
                              (4, "\ue101", {31: "plain"})):
        slots = {s: "weapon" for s in weapon_slots}
        slots.update(extra)
        slots[4] = "plain"
        img = _armory_background(rows, slots)
        name = f"armory_{rows * 9}"
        img.save(os.path.join(font_tex, name + ".png"))
        # ascent 13: the title is drawn 6px down with a 7px ascent, so the art's top meets the GUI's.
        gui.append({"type": "bitmap", "file": f"{NS}:font/{name}.png", "ascent": 13, "height": img.height,
                    "chars": [json.loads('"' + char + '"')]})
    gui.append({"type": "space", "advances": {json.loads('"\\uf001"'): -8, json.loads('"\\uf002"'): -(GUI_WIDTH + 1 - 8)}})
    with open(os.path.join(fonts, "gui.json"), "w") as f:
        json.dump({"providers": gui}, f, indent=2)


def lerp_rgba(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


# Tooltip style "unchartedsmp:bloodbath": same geometry as vanilla's tooltip/background and
# tooltip/frame (a 100x100 nine-slice with the box 8px in from each edge), in blood colours.
# Decoration only goes in the corner cells: edges and centre are stretched to the tooltip's size.
def write_tooltip_sprites():
    out = os.path.join(ASSETS, "textures", "gui", "sprites", "tooltip")
    os.makedirs(out, exist_ok=True)
    size, lo, hi = 100, 8, 91

    bg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = bg.load()
    for y in range(lo, hi + 1):
        for x in range(lo, hi + 1):
            if (x in (lo, hi)) and (y in (lo, hi)):
                continue  # trimmed corner, like vanilla
            px[x, y] = lerp_rgba((30, 4, 9, 242), (11, 1, 3, 242), (y - lo) / (hi - lo))
    bg.save(os.path.join(out, "bloodbath_background.png"))
    with open(os.path.join(out, "bloodbath_background.png.mcmeta"), "w") as f:
        json.dump({"gui": {"scaling": {"type": "nine_slice", "width": size, "height": size, "border": 9,
                                       "stretch_inner": True}}}, f, indent=2)

    frame = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = frame.load()
    top_outer, bottom_outer = (122, 6, 16, 235), (58, 2, 8, 225)
    top_inner, bottom_inner = (236, 38, 52, 230), (120, 6, 18, 215)
    for y in range(lo, hi + 1):
        t = (y - lo) / (hi - lo)
        for x in range(lo, hi + 1):
            ring = min(x - lo, hi - x, y - lo, hi - y)
            if ring > 1:
                continue
            corner = min(x - lo, hi - x) + min(y - lo, hi - y)
            if corner < 2:
                continue  # rounded corner
            px[x, y] = lerp_rgba(top_inner, bottom_inner, t) if ring == 1 else lerp_rgba(top_outer, bottom_outer, t)
    # A small gem riveted into each corner, kept inside the 10px corner cells.
    gem = {(0, 0): (255, 60, 72, 255), (0, -1): (255, 200, 205, 255), (-1, 0): (255, 200, 205, 255),
           (1, 0): (150, 8, 20, 255), (0, 1): (150, 8, 20, 255)}
    for cx, cy in ((7, 7), (92, 7), (7, 92), (92, 92)):
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                if abs(dx) + abs(dy) <= 2:
                    px[cx + dx, cy + dy] = gem.get((dx, dy), (255, 214, 122, 255))  # gold rim
    frame.save(os.path.join(out, "bloodbath_frame.png"))
    with open(os.path.join(out, "bloodbath_frame.png.mcmeta"), "w") as f:
        json.dump({"gui": {"scaling": {"type": "nine_slice", "width": size, "height": size, "border": 10,
                                       "stretch_inner": True}}}, f, indent=2)


TRIMS = ("quartz", "iron", "netherite", "redstone", "copper", "gold", "emerald", "diamond", "lapis", "amethyst", "resin")


def write_armor_overrides(definitions):
    """Netherite armour items: our model for Blood Knight pieces, vanilla (with trims) otherwise."""
    out = os.path.join(PACK, "assets", "minecraft", "items")
    for piece, (_, _, kind) in ARMOR.items():
        vanilla = {  # verbatim structure of vanilla assets/minecraft/items/netherite_<kind>.json
            "type": "minecraft:select",
            "property": "minecraft:trim_material",
            "cases": [{"when": f"minecraft:{t}", "model": vanilla_model(f"netherite_{kind}_{t}_trim")} for t in TRIMS],
            "fallback": vanilla_model(f"netherite_{kind}"),
        }
        with open(os.path.join(out, f"netherite_{kind}.json"), "w") as f:
            json.dump({"model": {
                "type": "minecraft:select",
                "property": "minecraft:custom_model_data",
                "index": 0,
                "cases": [{"when": f"bloodbath:{piece}", "model": definitions[piece]["model"]}],
                "fallback": vanilla,
            }}, f, indent=2)


# The worn look: the vanilla humanoid armour layout at 4x (256x128), so plates can be shaded
# properly instead of reading as flat blocks: brushed gunmetal lit from the top left, bevelled
# rims, rivets, engraved crimson filigree, a real gem at the heart, chainmail rings.
# Everything below is drawn in model units (1 unit = KS texels) on each face of each box.
KS = 4  # texels per armour-texture unit
WORN = {k: hexrgb(v) for k, v in {
    "o": "#07070a", "d": "#17171c", "m": "#2a2a32", "l": "#3a3a44", "h": "#5d5d6a", "s": "#9a9aa8",
    "t": "#a3121c", "T": "#e0303c", "t0": "#55080f", "g": "#ff3b45", "G": "#ffc0c4", "k": "#040203",
    "blood": "#3f050a", "drop": "#8c0f18", "leather": "#2e1512", "leather_hi": "#4a221c",
}.items()}


def _box_faces(u, v, w, h, d):
    """Vanilla box UV layout: {face: (x, y, width, height)} in texture units."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


class _Face:
    """Draws on one face of a box. Coordinates are model units (0..w, 0..h), from the face's top left."""

    def __init__(self, img, x, y, w, h, seed):
        self.img, self.x, self.y, self.w, self.h, self.seed = img, x, y, w, h, seed
        self.tw, self.th = w * KS, h * KS

    def _t(self, u):
        return int(round(u * KS))

    def put(self, px, py, color, alpha=1.0):
        if 0 <= px < self.tw and 0 <= py < self.th:
            gx, gy = self.x * KS + px, self.y * KS + py
            if alpha < 1.0 and self.img[gy, gx, 3] > 0:
                color = self.img[gy, gx, :3] * (1 - alpha) + np.asarray(color) * alpha
            self.img[gy, gx, :3] = np.clip(color, 0, 255)
            self.img[gy, gx, 3] = 255

    def get(self, px, py):
        return self.img[self.y * KS + py, self.x * KS + px, :3].copy()

    def plate(self, u0, v0, u1, v1, tone="m", rivets=False):
        """Brushed gunmetal: a soft top-to-bottom falloff, fine horizontal grain, a lit top/left
        bevel two texels deep, a dark bottom/right edge, the odd scratch."""
        x0, y0, x1, y1 = self._t(u0), self._t(v0), self._t(u1), self._t(v1)
        base = WORN[tone]
        for py in range(y0, y1):
            fy = (py - y0) / max(1, y1 - y0 - 1)
            for px in range(x0, x1):
                fx = (px - x0) / max(1, x1 - x0 - 1)
                g = 1.16 - 0.34 * fy - 0.08 * fx
                grain = 0.94 + 0.12 * value_noise((self.x * KS + px) * 0.35, (self.y * KS + py) * 2.1, 0.0, self.seed)
                c = base * g * grain
                if py == y0:
                    c = c * 0.3 + WORN["s"] * 0.7
                elif py == y0 + 1:
                    c = c * 0.65 + WORN["h"] * 0.35
                elif px == x0:
                    c = c * 1.22
                if py == y1 - 1:
                    c = WORN["o"] * 0.75 + base * 0.25
                elif py == y1 - 2:
                    c = c * 0.7
                elif px == x1 - 1:
                    c = c * 0.62
                self.put(px, py, c)
        # Scratches: short, pale, diagonal.
        for k in range(max(1, (x1 - x0) * (y1 - y0) // 180)):
            sx = x0 + 2 + int(_hash(k, self.seed, x0, y0) * max(1, x1 - x0 - 6))
            sy = y0 + 3 + int(_hash(self.seed, k, y0, x0) * max(1, y1 - y0 - 7))
            for i in range(3):
                if sx + i < x1 - 1 and sy + i < y1 - 2:
                    self.put(sx + i, sy + i, self.get(sx + i, sy + i) * 0.55 + WORN["h"] * 0.45)
        if rivets:
            for rx, ry in ((x0 + 2, y0 + 3), (x1 - 4, y0 + 3), (x0 + 2, y1 - 5), (x1 - 4, y1 - 5)):
                self.rivet(rx, ry)

    def rivet(self, px, py):
        self.put(px, py, WORN["s"])
        self.put(px + 1, py, WORN["h"])
        self.put(px, py + 1, WORN["h"])
        self.put(px + 1, py + 1, WORN["o"])

    def trim(self, u0, v0, u1, v1):
        """A crimson band: lit top edge, shadowed bottom, a notch every so often."""
        x0, y0, x1, y1 = self._t(u0), self._t(v0), self._t(u1), self._t(v1)
        for py in range(y0, y1):
            for px in range(x0, x1):
                f = (py - y0) / max(1, y1 - y0 - 1)
                c = WORN["T"] * (1 - f) + WORN["t0"] * f if y1 - y0 > 2 else WORN["t"]
                if py == y0:
                    c = WORN["T"] * 0.6 + WORN["G"] * 0.4
                if py == y1 - 1:
                    c = WORN["t0"] * 0.7
                if (px - x0) % 10 == 5 and y0 < py < y1 - 1:
                    c = WORN["t0"]
                self.put(px, py, c)

    def fill(self, u0, v0, u1, v1, key):
        for py in range(self._t(v0), self._t(v1)):
            for px in range(self._t(u0), self._t(u1)):
                self.put(px, py, WORN[key])

    def gem(self, cu, cv, r):
        """A round blood gem: a dark setting, a crimson ring, a glowing body, a hot highlight."""
        cx, cy, rr = cu * KS, cv * KS, r * KS
        for py in range(int(cy - rr - 3), int(cy + rr + 4)):
            for px in range(int(cx - rr - 3), int(cx + rr + 4)):
                d = math.hypot(px + 0.5 - cx, py + 0.5 - cy)
                if d <= rr:
                    t = d / rr
                    c = WORN["G"] * (1 - t) ** 2 + WORN["g"] * (1 - (1 - t) ** 2)
                    if t > 0.75:
                        c = c * 0.75
                    self.put(px, py, c)
                elif d <= rr + 1.2:
                    self.put(px, py, WORN["k"])
                elif d <= rr + 2.6:
                    self.put(px, py, WORN["t"] if py < cy else WORN["t0"])
        self.put(int(cx - rr * 0.4), int(cy - rr * 0.45), WORN["G"])
        self.put(int(cx - rr * 0.4) + 1, int(cy - rr * 0.45), (WORN["G"] + WORN["g"]) / 2)

    def engrave(self, points, color="t0"):
        """A thin engraved line (in units): a dark groove with a lit lip beneath it."""
        for (u0, v0), (u1, v1) in zip(points, points[1:]):
            steps = int(max(abs(u1 - u0), abs(v1 - v0)) * KS * 2) + 1
            for i in range(steps + 1):
                t = i / steps
                px, py = int(round((u0 + (u1 - u0) * t) * KS)), int(round((v0 + (v1 - v0) * t) * KS))
                self.put(px, py, WORN[color])
                self.put(px, py + 1, self.get(min(px, self.tw - 1), min(py + 1, self.th - 1)) * 0.6 + WORN["h"] * 0.4
                         if py + 1 < self.th else WORN["h"])

    def swirl(self, cu, cv, size, mirror=False):
        """A curling filigree flourish."""
        pts = []
        for i in range(24):
            t = i / 23
            a = t * math.pi * 2.2
            r = size * (1 - t * 0.75)
            pts.append((cu + (-1 if mirror else 1) * math.cos(a) * r, cv + math.sin(a) * r * 0.8 + t * size * 0.4))
        self.engrave(pts)

    def mail(self, u0, v0, u1, v1):
        """Chainmail: rows of little rings, alternate rows offset."""
        for py in range(self._t(v0), self._t(v1)):
            for px in range(self._t(u0), self._t(u1)):
                row = py // 2
                cx = (px + (row % 2)) % 3
                ring = (cx != 1) != (py % 2 == 1)
                c = WORN["h"] * 0.8 if ring and py % 2 == 0 else WORN["m"] if ring else WORN["o"]
                self.put(px, py, c)

    def drip(self, u, v, length):
        px, py = self._t(u), self._t(v)
        n = int(length * KS)
        for i in range(n):
            self.put(px, py + i, WORN["blood"])
            if i < n - 2:
                self.put(px + 1, py + i, WORN["blood"], 0.5)
        self.put(px, py + n, WORN["drop"])
        self.put(px + 1, py + n, WORN["drop"], 0.6)
        self.put(px, py + n + 1, WORN["drop"], 0.7)

    def holes(self, u0, v0, cols, rows, gap):
        for r in range(rows):
            for c in range(cols):
                px, py = self._t(u0 + c * gap), self._t(v0 + r * gap)
                self.put(px, py, WORN["k"])
                self.put(px + 1, py, WORN["k"])
                self.put(px, py + 1, WORN["k"])
                self.put(px + 1, py + 1, WORN["o"])
                self.put(px, py + 2, WORN["h"])
                self.put(px + 1, py + 2, WORN["h"])


def _faces(img, u, v, w, h, d, seed):
    return {face: _Face(img, fx, fy, fw, fh, seed + i)
            for i, (face, (fx, fy, fw, fh)) in enumerate(_box_faces(u, v, w, h, d).items())}


def _paint_helm(img):
    f = _faces(img, 0, 0, 8, 8, 8, 11)
    front = f["front"]
    front.plate(0, 0, 8, 2.1, "l")                        # brow
    front.trim(0, 2.1, 8, 2.8)                            # the band
    front.fill(0, 2.8, 8, 3.8, "k")                       # visor slit
    for cu in (1.9, 6.1):                                 # burning eyes
        for py in range(front._t(2.9), front._t(3.7)):
            for px in range(front._t(cu - 1.0), front._t(cu + 1.0)):
                d = math.hypot((px + 0.5) / KS - cu, ((py + 0.5) / KS - 3.3) * 2.2)
                if d < 1.0:
                    front.put(px, py, WORN["G"] * (1 - d) + WORN["g"] * d if d > 0.3 else WORN["G"])
    front.plate(0, 3.8, 3.7, 8, "l", rivets=True)         # cheek plates, with a raised nose ridge
    front.plate(4.3, 3.8, 8, 8, "m", rivets=True)
    front.plate(3.7, 3.8, 4.3, 8, "h")
    front.holes(0.9, 4.7, 3, 4, 0.8)
    front.holes(5.0, 4.7, 3, 4, 0.8)
    for face, near_front in (("right", True), ("left", False)):
        side = f[face]
        side.plate(0, 0, 8, 2.1, "m")
        side.trim(0, 2.1, 8, 2.8)
        side.plate(0, 2.8, 8, 8, "m")
        lo, hi = (5.6, 8) if near_front else (0, 2.4)
        side.fill(lo, 2.8, hi, 3.8, "k")                  # the slit wraps round
        cheek = (4.6, 3.8, 8, 8) if near_front else (0, 3.8, 3.4, 8)
        side.plate(*cheek, "l", rivets=True)              # cheek guard
        side.engrave([(1.0, 5.0), (2.5, 4.4), (4.0, 5.2)] if near_front else [(4.0, 5.0), (5.5, 4.4), (7.0, 5.2)])
    back = f["back"]
    back.plate(0, 0, 8, 2.1, "m")
    back.trim(0, 2.1, 8, 2.8)
    back.plate(0, 2.8, 8, 5.0, "m")
    back.plate(0, 5.0, 8, 6.5, "l")                       # neck guard lames
    back.plate(0, 6.5, 8, 8, "m")
    top = f["top"]
    top.plate(0, 0, 8, 8, "l")
    top.trim(3.5, 0, 4.5, 8)                              # crest ridge
    top.engrave([(1.5, 1.0), (1.2, 4.0), (1.8, 7.0)])
    top.engrave([(6.5, 1.0), (6.8, 4.0), (6.2, 7.0)])
    f["bottom"].fill(0, 0, 8, 8, "d")


def _paint_torso(img, u, v, seed, legs_only=False):
    f = _faces(img, u, v, 8, 12, 4, seed)
    if legs_only:
        # Leggings only show the belt and faulds at the bottom of the body box.
        for face in ("front", "back", "left", "right"):
            s = f[face]
            s.fill(0, 8, s.w, 9.2, "leather")
            s.fill(0, 8, s.w, 8.3, "leather_hi")
            s.plate(0, 9.2, s.w, 10.6, "m")
            s.plate(0, 10.6, s.w, 12, "l")
            s.trim(0, 11.5, s.w, 12)
        buckle = f["front"]
        buckle.fill(3.1, 7.9, 4.9, 9.3, "h")
        buckle.gem(4.0, 8.6, 0.45)
        return
    front = f["front"]
    front.plate(0, 0, 8, 1.0, "d")                        # gorget
    front.trim(0, 1.0, 8, 1.5)
    front.plate(0, 1.5, 4.1, 6.8, "l", rivets=True)       # breastplate halves
    front.plate(3.9, 1.5, 8, 6.8, "m", rivets=True)
    front.swirl(2.0, 3.3, 1.2)
    front.swirl(6.0, 3.3, 1.2, mirror=True)
    front.gem(4.0, 4.3, 1.05)                             # the Knight's blood, still glowing
    front.drip(4.55, 5.5, 1.3)
    front.drip(2.6, 6.2, 0.9)
    for v0 in (6.8, 8.5, 10.2):                           # lames
        front.plate(0, v0, 8, v0 + 1.8, "m" if v0 != 8.5 else "l")
    front.trim(0, 11.5, 8, 12)
    back = f["back"]
    back.plate(0, 0, 8, 6.8, "m", rivets=True)
    back.trim(3.6, 0.5, 4.4, 6.5)                         # spine
    back.engrave([(1.0, 1.5), (2.2, 3.2), (1.2, 5.5)])
    back.engrave([(7.0, 1.5), (5.8, 3.2), (6.8, 5.5)])
    for v0 in (6.8, 8.5, 10.2):
        back.plate(0, v0, 8, v0 + 1.8, "m")
    back.trim(0, 11.5, 8, 12)
    for face in ("left", "right"):
        s = f[face]
        s.plate(0, 0, 4, 6.8, "m")
        for v0 in (6.8, 8.5, 10.2):
            s.plate(0, v0, 4, v0 + 1.8, "m")
        s.trim(0, 11.5, 4, 12)
    top = f["top"]
    top.plate(0, 0, 8, 4, "l")
    top.fill(2.5, 0, 5.5, 1.2, "d")                       # collar
    f["bottom"].fill(0, 0, 8, 4, "d")


def _paint_arm(img):
    f = _faces(img, 40, 16, 4, 12, 4, 37)
    for face in ("front", "back", "left", "right"):
        s = f[face]
        s.plate(0, 0, 4, 2.2, "l")                        # pauldron, two layers
        s.trim(0, 2.2, 4, 2.7)
        s.plate(0, 2.7, 4, 4.3, "m")
        s.fill(0, 4.3, 4, 4.6, "t0")
        s.mail(0, 4.6, 4, 7.0)                            # upper arm
        s.trim(0, 7.0, 4, 7.5)
        s.plate(0, 7.5, 4, 12, "m")                       # vambrace
        s.engrave([(0.6, 8.2), (2.0, 8.8), (3.4, 8.2)])
        if face in ("front", "right"):
            for px in range(s._t(1.0), s._t(3.0)):
                for py in range(s._t(9.6), s._t(10.0)):
                    s.put(px, py, WORN["g"] if s._t(1.4) <= px < s._t(2.6) else WORN["t"])
        s.trim(0, 11.6, 4, 12)
    top = f["top"]
    top.plate(0, 0, 4, 4, "l", rivets=True)
    top.trim(0, 0, 4, 0.35)
    f["bottom"].fill(0, 0, 4, 4, "d")


def _paint_boots(img):
    f = _faces(img, 0, 16, 4, 12, 4, 53)
    for face in ("front", "back", "left", "right"):
        s = f[face]                                       # only the lowest 5 units show
        s.trim(0, 7.0, 4, 7.8)                            # cuff
        s.plate(0, 7.8, 4, 10.0, "m")
        s.plate(0, 10.0, 4, 12, "l" if face == "front" else "m", rivets=face == "front")
        s.fill(0, 11.6, 4, 12, "o")                       # sole edge
    toe = f["front"]
    toe.gem(2.0, 10.9, 0.4)
    f["right"].rivet(f["right"]._t(3.0), f["right"]._t(9.0))
    f["left"].rivet(f["left"]._t(0.8), f["left"]._t(9.0))
    f["bottom"].fill(0, 0, 4, 4, "d")


def _paint_legs(img):
    f = _faces(img, 0, 16, 4, 12, 4, 79)
    for face in ("front", "back", "left", "right"):
        s = f[face]
        s.plate(0, 0, 4, 5.0, "m")                        # thigh
        s.plate(0, 5.0, 4, 7.4, "l" if face == "front" else "m")   # knee cop
        s.plate(0, 7.4, 4, 12, "m")                       # greave
    for face in ("front", "left", "right"):
        f[face].trim(0, 4.5, 4, 5.0)                      # above the knee
    knee = f["front"]
    knee.swirl(1.0, 1.4, 0.8)
    knee.swirl(3.0, 1.4, 0.8, mirror=True)
    knee.gem(2.0, 6.2, 0.55)
    knee.plate(1.7, 8.0, 2.3, 11.5, "h")                  # shin ridge
    knee.drip(1.2, 7.4, 0.8)
    f["bottom"].fill(0, 0, 4, 4, "d")


def write_knight_equipment():
    tex = os.path.join(ASSETS, "textures", "entity", "equipment")
    os.makedirs(os.path.join(tex, "humanoid"), exist_ok=True)
    os.makedirs(os.path.join(tex, "humanoid_leggings"), exist_ok=True)

    outer = np.zeros((32 * KS, 64 * KS, 4), dtype=float)
    _paint_helm(outer)                                    # helmet (head)
    _paint_torso(outer, 16, 16, 23)                       # chestplate (body)
    _paint_arm(outer)                                     # chestplate (arms)
    _paint_boots(outer)                                   # boots (legs, lower part)
    Image.fromarray(outer.astype("uint8"), "RGBA").save(os.path.join(tex, "humanoid", "blood_knight.png"))

    inner = np.zeros((32 * KS, 64 * KS, 4), dtype=float)
    _paint_torso(inner, 16, 16, 67, legs_only=True)       # leggings (waist)
    _paint_legs(inner)                                    # leggings (legs)
    Image.fromarray(inner.astype("uint8"), "RGBA").save(os.path.join(tex, "humanoid_leggings", "blood_knight.png"))

    eq = os.path.join(ASSETS, "equipment")
    os.makedirs(eq, exist_ok=True)
    with open(os.path.join(eq, "blood_knight.json"), "w") as f:
        json.dump({"layers": {"humanoid": [{"texture": f"{NS}:blood_knight"}],
                              "humanoid_leggings": [{"texture": f"{NS}:blood_knight"}]}}, f, indent=2)


# The Blood Core: a nether star underneath, drawn as a thorned blood crystal whose heart beats
# (lub-dub, rest). Animated item texture; the nether star definition swaps it in only for items
# with custom_model_data "bloodbath:blood_core", so real nether stars keep their look.
CORE_BEAT = [0.15, 0.95, 0.55, 1.0, 0.6, 0.35, 0.22, 0.15, 0.12, 0.12]
CORE_FRAME_TICKS = [4, 2, 2, 2, 2, 3, 3, 4, 6, 6]


def _core_shape(x, y):
    ax, ay = abs(x), abs(y)
    body = ax + ay <= 5.0
    arm_x = ax <= 7.5 and ay <= 2.3 * (1 - ax / 8.6) + 0.35
    arm_y = ay <= 7.5 and ax <= 2.3 * (1 - ay / 8.6) + 0.35
    diag = abs(ax - ay) <= 1.1 and ax + ay <= 8.6
    return body or arm_x or arm_y or diag


def _core_frame(p):
    c = {k: ImageColor.getrgb(v) for k, v in {
        "out": "#2a040a", "rim": "#6e0a15", "dark": "#4a0710", "mid": "#7d0c18", "red": "#b3121f",
        "hi": "#e0303c", "shine": "#ff8a92", "white": "#ffe6e8"}.items()}
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    inside = [[_core_shape(x - 7.5, y - 7.5) for x in range(16)] for y in range(16)]
    inn = lambda x, y: 0 <= x < 16 and 0 <= y < 16 and inside[y][x]
    for y in range(16):
        for x in range(16):
            if not inside[y][x]:
                continue
            dx, dy = x - 7.5, y - 7.5
            if not (inn(x + 1, y) and inn(x - 1, y) and inn(x, y + 1) and inn(x, y - 1)):
                lit = (not inn(x - 1, y) or not inn(x, y - 1)) and dx + dy < 0
                img.putpixel((x, y), c["rim" if lit else "out"] + (255,))
                continue
            # Four facets lit from the top left, with ridges along the axes.
            col = c["hi"] if dx < 0 and dy < 0 else c["red"] if dy < 0 else c["mid"] if dx < 0 else c["dark"]
            if abs(dx) < 0.6 and dy < 0 or abs(dy) < 0.6 and dx < 0:
                col = c["shine"] if abs(dx) + abs(dy) > 3 else c["hi"]
            elif abs(dx) < 0.6 or abs(dy) < 0.6:
                col = c["mid"]
            d = abs(dx) + abs(dy)
            r = 1.6 + 1.4 * p                      # the heart swells with each beat
            if d <= r:
                t = d / r
                col = c["white"] if t < 0.35 and p > 0.5 else c["shine"] if t < 0.6 else c["hi"]
            elif d <= r + 1.0 and p > 0.45:
                col = tuple((a + b) // 2 for a, b in zip(col, c["hi"]))
            img.putpixel((x, y), col + (255,))
    return img


PACK_DESCRIPTION = "\u00a74Bloodbath\u00a7r 3D weapons"


def write_pack_meta():
    # 1.21.4 (46) is the first version with item model definitions; 1.21.11 is 75 and 26.3 is 97.
    # pack_format + supported_formats are read by 1.21.4-1.21.8, min/max_format by 1.21.9+.
    meta = {"pack": {"description": PACK_DESCRIPTION, "pack_format": 75, "supported_formats": [46, 99],
                     "min_format": 46, "max_format": 99}}
    with open(os.path.join(PACK, "pack.mcmeta"), "w") as f:
        json.dump(meta, f, indent=2)
    icon = os.path.join(ROOT, "fabric", "src", "main", "resources", "assets", "unchartedsmp", "icon.png")
    if os.path.exists(icon):
        shutil.copy(icon, os.path.join(PACK, "pack.png"))


if __name__ == "__main__":
    main()
