#!/usr/bin/env python3
"""Generates the Bloodbath 3D weapon models, their painted textures and item definitions.

Style: chunky Blockbench-style weapons - wide mottled blood-steel blades with bright highlights and
white shine, banded gray grips, diamond (45-degree) gems in gray frames, diamond tips and caps.

Every weapon is a list of cuboids in "upright" local space: centred on x=8/z=8, grip at roughly
y=2.3, blade/head pointing +Y, flat face towards +Z (the side the camera sees). Vanilla handheld
display transforms expect a sprite drawn diagonally (handle bottom-left, tip top-right), so
instead of tilting every cube we fold a -45 degree Z pre-rotation into the display transforms:
Minecraft applies display rotations as Rx*Ry*Rz, so R_display * Rz(-45) is just "subtract 45
from the Z angle".

Each weapon gets its own texture atlas, painted per face at 1 texel per model unit. Noise is
sampled in model space, so patterns run continuously across neighbouring cubes.

Outputs (relative to the repo root):
  src/main/resources/assets/unchartedsmp/items/<id>.json           item model definitions
  src/main/resources/assets/unchartedsmp/models/item/<id>.json     3D models (+ bow pull stages)
  src/main/resources/assets/unchartedsmp/textures/item/<id>.png    painted atlases
  docs/preview/models.json, docs/preview/textures/                 data for the web preview

Usage: python3 tools/models/generate.py   (needs numpy + pillow)
"""
import hashlib
import json
import math
import os
import shutil

import numpy as np
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "unchartedsmp")
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


def paradox_bow(stage):
    """stage 0 = idle (no arrow), 1..3 = pulling_0..pulling_2."""
    m = Model()
    gy = 2.3
    m.centered(8, gy - 1.1, gy + 1.1, 4.4, 2.0, "grip", name="riser_grip")
    m.gem(8, gy, 2.6, depth=2.8, name="riser_gem")
    bends = {0: (0, -22.5, -22.5, -45), 1: (0, -22.5, -22.5, -45), 2: (0, -22.5, -45, -45), 3: (0, -22.5, -45, -67.5)}[stage]
    tips = []
    for side, sign in (("right", 1), ("left", -1)):
        x, y = 8 + sign * 2.2, gy
        for i, (direction, length) in enumerate(zip(bends, (3.2, 3.2, 3.0, 2.4))):
            d = direction if sign > 0 else 180 - direction
            x, y = m.segment(x, y, d, length, 1.7 if i < 2 else 1.3, 1.3, "blade", name=f"limb_{side}_{i}")
        m.diamond(x, y, 1.6, 1.6, "glow", name=f"tip_{side}")
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
    m.box(7.1, nock_y + 0.2, 7.9, 8.9, nock_y + 2.6, 8.1, "string", name="fletching")
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
}
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
                rects.append((key, w, h))
                meta[key] = (el, w, h)
    for size in (32, 64, 128, 256):
        placed, height = pack(rects, size)
        if height <= size:
            break
    img = np.zeros((size, size, 4), dtype=float)
    uvs = {}
    for key, (px, py) in placed.items():
        el, w, h = meta[key]
        face = key[2]
        for v in range(h):
            for u in range(w):
                c = paint_texel(el["mat"], face, u, v, w, h, texel_point(el, face, u, v, w, h), el)
                img[py + v, px + u, :3] = np.clip(c, 0, 255)
                img[py + v, px + u, 3] = 255
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
        (x1, y1, _), (x2, y2, _) = e["from"], e["to"]
        corners = [(x1, y1), (x1, y2), (x2, y1), (x2, y2)]
        if e["rot"] and e["rot"][0] == "z":
            a = math.radians(e["rot"][1])
            ox, oy = e["rot"][2][:2]
            corners = [(ox + (x - ox) * math.cos(a) - (y - oy) * math.sin(a), oy + (x - ox) * math.sin(a) + (y - oy) * math.cos(a)) for x, y in corners]
        pts += corners
    a = math.radians(-45)
    rot = [((x - 8) * math.cos(a) - (y - 8) * math.sin(a), (x - 8) * math.sin(a) + (y - 8) * math.cos(a)) for x, y in pts]
    xs, ys = [p[0] for p in rot], [p[1] for p in rot]
    return max(max(xs) - min(xs), max(ys) - min(ys))


def v3(s):
    return [r3(s)] * 3


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
        # Vanilla item/bow transforms (held upright, aimed forward), same -45 Z fold.
        disp["thirdperson_righthand"] = {"rotation": [-80, 260, -85], "translation": [-1, -2, 2.5], "scale": v3(0.9 * max(0.7, min(1.0, 22.0 / size)))}
        disp["firstperson_righthand"] = {"rotation": [0, -90, -20], "translation": [1.13, 3.2, 1.13], "scale": v3(0.68 * max(0.7, min(1.0, 22.0 / size)))}
    if weapon == "meteor_gauntlet":
        # Worn over the fist rather than swung: sit it upright on the hand.
        disp["thirdperson_righthand"] = {"rotation": [75, 0, 0], "translation": [0, 1.5, 1.5], "scale": v3(0.6)}
        disp["firstperson_righthand"] = {"rotation": [-10, -80, 10], "translation": [2, 0, -2], "scale": v3(0.7)}
        disp["gui"] = {"rotation": [25, -35, 0], "translation": [0, 0, 0], "scale": v3(0.9)}
        disp["ground"] = {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": v3(0.5)}
        disp["fixed"] = {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": v3(0.9)}
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
    for weapon, (title, build) in WEAPONS.items():
        variants = [(weapon, build())]
        if weapon == "paradox_bow":
            variants += [(name, paradox_bow(stage)) for name, stage in BOW_STAGES.items()]
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
        with open(os.path.join(items_out, f"{weapon}.json"), "w") as f:
            json.dump(item_definition(weapon), f, indent=2)

    with open(os.path.join(prev_dir, "models.json"), "w") as f:
        json.dump(preview, f, separators=(",", ":"))


if __name__ == "__main__":
    main()
