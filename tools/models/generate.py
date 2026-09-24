#!/usr/bin/env python3
"""Generates the Bloodbath 3D weapon models, their textures and item definitions.

Every weapon is described as a list of cuboids in "upright" local space: centred on x=8/z=8,
grip at roughly y=2.3, blade/head pointing +Y. Vanilla handheld display transforms expect a
sprite drawn diagonally (handle bottom-left, tip top-right), so instead of tilting every cube
we fold a -45 degree Z pre-rotation into the display transforms: Minecraft applies display
rotations as Rx*Ry*Rz, so R_display * Rz(-45) is just "subtract 45 from the Z angle".

Outputs (relative to the repo root):
  src/main/resources/assets/unchartedsmp/items/<id>.json           item model definitions
  src/main/resources/assets/unchartedsmp/models/item/<id>.json     3D models
  src/main/resources/assets/unchartedsmp/textures/item/blood/*.png material textures
  docs/preview/models.json                                         data for the web preview

Usage: python3 tools/models/generate.py
"""
import hashlib
import json
import math
import os
import random

import numpy as np
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "unchartedsmp")
NS = "unchartedsmp"
TEX_DIR = "item/blood"

# --------------------------------------------------------------------------------------------
# Materials: 16x16 procedurally painted textures in a shared blood palette.
# --------------------------------------------------------------------------------------------

def hexrgb(h):
    h = h.lstrip("#")
    return [int(h[i:i + 2], 16) for i in (0, 2, 4)]


PALETTES = {
    "blood_steel": ["#2a0508", "#4a0a10", "#6e1018", "#94161f", "#bd2530", "#e04a52"],
    "black_iron": ["#0d0a0b", "#1a1416", "#281d20", "#3a2a2e", "#4f3a3f"],
    "bone": ["#6f6553", "#8f846c", "#b0a488", "#cfc3a3", "#e6dcc0"],
    "leather": ["#1f0907", "#2e0f0b", "#44170f", "#5a2016", "#6e2a1d"],
    "blood_glow": ["#8b0000", "#b00010", "#d4101c", "#ff2a2a", "#ff7070"],
    "clot": ["#1a0204", "#2b0508", "#40080d", "#5a0c13", "#7a1119"],
    "gold": ["#5e4312", "#8a6a1f", "#b8902e", "#e0b64a", "#fff1a0"],
    "stone": ["#2b2a2e", "#3a383d", "#4a474d", "#5b575e", "#6e6970"],
    "mirror": ["#3a0d12", "#6b1f27", "#a33a44", "#d9737c", "#ffd0d4"],
    "string": ["#6a0a12", "#8f1019", "#b3202a", "#e04a52", "#ff8a8f"],
}


def paint(name, seed):
    rng = np.random.default_rng(seed)
    pal = np.array([hexrgb(c) for c in PALETTES[name]], dtype=float)
    n = len(pal) - 1
    y, x = np.mgrid[0:16, 0:16]
    noise = rng.random((16, 16))
    if name in ("blood_steel", "black_iron"):
        # Brushed metal: vertical streaks, lit from the top-left, bright bevel on two edges.
        streak = rng.random(16)[None, :].repeat(16, 0)
        v = 0.45 + 0.25 * streak + 0.15 * noise - 0.012 * (x + y)
        v[0, :] += 0.25
        v[:, 0] += 0.2
        v[15, :] -= 0.2
        v[:, 15] -= 0.15
    elif name == "bone":
        v = 0.6 + 0.2 * noise - 0.01 * y
        for _ in range(3):  # hairline cracks
            cx, cy = rng.integers(2, 14, 2)
            for i in range(rng.integers(3, 7)):
                px, py = cx + i, cy + rng.integers(-1, 2)
                if 0 <= px < 16 and 0 <= py < 16:
                    v[py, px] = 0.05
    elif name == "leather":
        # Diagonal wrapping with dark seams.
        band = ((x + y) // 3) % 2
        v = 0.35 + 0.35 * band + 0.15 * noise
        v[((x + y) % 3) == 0] = 0.08
    elif name == "blood_glow":
        # Molten, pulsing blood: bright core veins.
        v = 0.4 + 0.35 * noise
        for _ in range(4):
            cx = rng.integers(0, 16)
            for yy in range(16):
                cx = int(np.clip(cx + rng.integers(-1, 2), 0, 15))
                v[yy, cx] = 1.0
    elif name == "clot":
        v = 0.3 + 0.4 * noise
        # Wet highlights.
        for _ in range(6):
            px, py = rng.integers(0, 16, 2)
            v[py, px] = 1.0
    elif name == "gold":
        v = 0.45 + 0.3 * noise - 0.01 * y
        v[0, :] += 0.3
        v[:, 0] += 0.2
    elif name == "stone":
        v = 0.3 + 0.45 * noise
    elif name == "mirror":
        # Reflective sheen bands running diagonally.
        v = 0.35 + 0.15 * noise + 0.5 * (np.abs(((x - y) % 8) - 4) < 1)
    else:
        v = 0.3 + 0.6 * noise
    v = np.clip(v, 0, 1)
    idx = v * n
    lo = np.floor(idx).astype(int)
    hi = np.clip(lo + 1, 0, n)
    t = (idx - lo)[..., None]
    img = pal[lo] * (1 - t) + pal[hi] * t
    if name == "stone":  # blood-filled cracks
        for _ in range(3):
            cx, cy = rng.integers(0, 16, 2)
            for _ in range(10):
                img[cy % 16, cx % 16] = hexrgb("#7a0d14")
                cx += rng.integers(-1, 2)
                cy += 1
    if name in ("blood_steel", "black_iron", "bone"):  # a few blood spatters on everything
        for _ in range(2 if name != "bone" else 4):
            px, py = rng.integers(0, 16, 2)
            img[py, px] = hexrgb("#8b0000")
            if py + 1 < 16:
                img[py + 1, px] = hexrgb("#5c0000")
    return Image.fromarray(np.clip(img, 0, 255).astype("uint8"), "RGB").convert("RGBA")


# --------------------------------------------------------------------------------------------
# Geometry helpers.
# --------------------------------------------------------------------------------------------

class Model:
    def __init__(self):
        self.elements = []

    def box(self, x1, y1, z1, x2, y2, z2, mat, rot=None, glow=False, name=None):
        """rot = (angle, (ox, oy, oz)) about the Z axis."""
        self.elements.append({
            "from": [x1, y1, z1], "to": [x2, y2, z2], "mat": mat,
            "rot": rot, "glow": glow, "name": name or mat,
        })
        return self

    def centered(self, cx, y1, y2, w, d, mat, cz=8.0, **kw):
        return self.box(cx - w / 2, y1, cz - d / 2, cx + w / 2, y2, cz + d / 2, mat, **kw)

    def taper(self, cx, y1, y2, w1, w2, d, mat, steps, **kw):
        """Stacked boxes narrowing from w1 to w2 - a pixel-art taper."""
        h = (y2 - y1) / steps
        for i in range(steps):
            w = w1 + (w2 - w1) * (i / max(1, steps - 1))
            self.centered(cx, y1 + i * h, y1 + (i + 1) * h, w, d, mat, **kw)
        return self


def z_rot(angle, ox, oy):
    return (angle, (ox, oy, 8.0))


def segment(m, x0, y0, direction, length, thickness, depth, mat, overlap=0.4, **kw):
    """A straight bar from (x0, y0) heading `direction` degrees (0 = +X, 90 = +Y), built from an
    axis-aligned box plus one legal Z rotation (multiples of 22.5, at most 45). Returns the end."""
    rad = math.radians(direction)
    x1, y1 = x0 + length * math.cos(rad), y0 + length * math.sin(rad)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    # Start from whichever axis-aligned bar (horizontal or vertical) is closest to the heading.
    base, delta = min(((b, ((direction - b + 90) % 180) - 90) for b in (0, 90)), key=lambda t: abs(t[1]))
    assert abs(delta) <= 45 and abs(delta) % 22.5 == 0, (direction, delta)
    half = length / 2 + overlap / 2
    if base == 90:
        box = (cx - thickness / 2, cy - half, cx + thickness / 2, cy + half)
    else:
        box = (cx - half, cy - thickness / 2, cx + half, cy + thickness / 2)
    rot = z_rot(delta, cx, cy) if delta else None
    m.box(box[0], box[1], 8 - depth / 2, box[2], box[3], 8 + depth / 2, mat, rot=rot, **kw)
    return x1, y1


# --------------------------------------------------------------------------------------------
# The ten weapons.
# --------------------------------------------------------------------------------------------

def riftblade():
    m = Model()
    m.centered(8, -3.5, -1.5, 2.4, 2.4, "blood_glow", glow=True, name="pommel_gem")
    m.centered(8, -4, -3.5, 1.6, 1.6, "black_iron", name="pommel_cap")
    m.centered(8, -1.5, 4.5, 1.6, 1.6, "leather", name="grip")
    m.centered(8, 1.5, 2.0, 1.9, 1.9, "gold", name="grip_band")
    m.centered(8, 4.5, 6, 7, 2.4, "black_iron", name="guard")
    m.box(10.5, 4.2, 7.2, 13, 5.2, 8.8, "blood_steel", rot=z_rot(22.5, 11.5, 5), name="guard_spike_r")
    m.box(3, 4.2, 7.2, 5.5, 5.2, 8.8, "blood_steel", rot=z_rot(-22.5, 4.5, 5), name="guard_spike_l")
    m.centered(8, 6, 20, 3.4, 1.0, "blood_steel", name="blade")
    m.taper(8, 20, 25, 2.8, 1.0, 1.0, "blood_steel", 3, name="blade_tip")
    m.centered(8, 25, 26.5, 0.5, 0.8, "blood_steel", name="point")
    m.centered(8, 7, 21, 0.8, 1.3, "blood_glow", glow=True, name="rift")
    m.centered(8, 21, 23.5, 0.5, 1.2, "blood_glow", glow=True, name="rift_tail")
    return m


def bloodhook():
    m = Model()
    for i, y in enumerate((-9, -7.2, -5.4)):  # dangling chain
        w, d = (1.4, 0.6) if i % 2 == 0 else (0.6, 1.4)
        m.centered(8, y, y + 1.6, w, d, "black_iron", name=f"chain_{i}")
    m.centered(8, -3.8, -1.8, 2.0, 2.0, "blood_glow", glow=True, name="ring_gem")
    m.centered(8, -2, 9, 1.8, 1.8, "leather", name="handle")
    m.centered(8, -0.5, 0.5, 2.2, 2.2, "black_iron", name="band_low")
    m.centered(8, 6.5, 7.5, 2.2, 2.2, "black_iron", name="band_high")
    m.centered(8, 9, 16.8, 1.6, 1.4, "blood_steel", name="shaft")
    # The hook: a "?" curling over to the right and back down, blood edge on the inside.
    x, y = 8.0, 16.5
    for i, (direction, length) in enumerate(((67.5, 3), (22.5, 3), (-22.5, 3), (-67.5, 3), (-112.5, 2.6), (-157.5, 2.2))):
        nx, ny = segment(m, x, y, direction, length, 1.7 if i < 4 else 1.3, 1.2, "blood_steel", name=f"hook_{i}")
        if 2 <= i <= 5:
            inward = math.radians(direction - 90)
            segment(m, x + 0.75 * math.cos(inward), y + 0.75 * math.sin(inward), direction, length, 0.4, 1.4,
                    "blood_glow", overlap=0.0, glow=True, name=f"hook_edge_{i}")
        x, y = nx, ny
    m.centered(x, y - 1.6, y, 0.5, 0.5, "blood_glow", glow=True, name="hook_drip")
    return m


def nullblade():
    m = Model()
    m.centered(8, -3.6, -1.2, 2.6, 2.6, "clot", name="pommel")
    m.centered(8, -1.2, 4.2, 1.6, 1.6, "bone", name="grip")
    m.box(8, 4, 7, 12.5, 5.6, 9, "bone", rot=z_rot(22.5, 8, 4.8), name="guard_r")
    m.box(3.5, 4, 7, 8, 5.6, 9, "bone", rot=z_rot(-22.5, 8, 4.8), name="guard_l")
    m.centered(8, 5.4, 17, 3.2, 1.0, "black_iron", name="blade")
    m.taper(8, 17, 21, 2.6, 0.8, 1.0, "black_iron", 3, name="blade_tip")
    for i, y in enumerate((7, 9.5, 12, 14.5)):  # serrations
        side = 1 if i % 2 == 0 else -1
        x = 8 + side * 1.6
        m.box(x - 0.5, y, 7.7, x + 0.5, y + 1.3, 8.3, "blood_steel", name=f"tooth_{i}")
    m.centered(7.5, 8, 13, 1.0, 1.3, "clot", name="clot_a")
    m.centered(8.6, 11, 16, 0.8, 1.25, "clot", name="clot_b")
    m.centered(8.1, 13.5, 15, 0.6, 1.4, "blood_glow", glow=True, name="clot_wet")
    return m


def meteor_gauntlet():
    m = Model()
    m.centered(8, -2, 3.5, 6.2, 6.2, "black_iron", name="cuff")
    m.centered(8, 0, 1, 6.6, 6.6, "gold", name="cuff_trim")
    m.centered(8, 3.5, 8, 5.6, 5.0, "blood_steel", name="hand")
    m.centered(8, 4.5, 7, 3, 5.6, "blood_glow", glow=True, name="meteor_core")
    for i, x in enumerate((5.9, 7.3, 8.7, 10.1)):
        m.box(x - 0.6, 8, 5.9, x + 0.6, 10.2, 7.3, "blood_steel", name=f"finger_{i}")
        m.box(x - 0.4, 8.6, 5.3, x + 0.4, 9.6, 5.9, "clot", name=f"knuckle_spike_{i}")
    m.box(10.8, 4, 6, 12.2, 7.5, 8.6, "blood_steel", rot=z_rot(-22.5, 11.5, 5.8), name="thumb")
    m.box(6.8, 1.5, 4.8, 9.2, 6.5, 5.3, "black_iron", name="plate")
    m.centered(8, -2.5, -2, 5, 5, "clot", name="cuff_drip")
    return m


def gravestone():
    m = Model()
    m.centered(8, -6, 12, 1.8, 1.8, "bone", name="haft")
    m.centered(8, -7, -5.5, 2.4, 2.4, "clot", name="haft_cap")
    for y in (-2, 3, 8):
        m.centered(8, y, y + 0.8, 2.1, 2.1, "leather", name=f"wrap_{y}")
    m.centered(8, 12, 21, 8, 3.0, "stone", name="slab")
    m.centered(8, 21, 22.5, 6.4, 3.0, "stone", name="slab_round_1")
    m.centered(8, 22.5, 23.5, 4.2, 3.0, "stone", name="slab_round_2")
    m.centered(8, 13, 20, 1.2, 3.3, "blood_glow", glow=True, name="cross_v")
    m.centered(8, 17, 18.2, 4.4, 3.3, "blood_glow", glow=True, name="cross_h")
    m.centered(4.6, 13.5, 16, 0.6, 3.2, "clot", name="drip_l")
    m.centered(11.3, 14.5, 18, 0.6, 3.2, "clot", name="drip_r")
    m.centered(8, 11.2, 12, 9, 3.6, "black_iron", name="base")
    return m


def chronos():
    m = Model()
    m.centered(8, -8, 16, 1.4, 1.4, "black_iron", name="staff")
    m.centered(8, -8.8, -7.5, 2.0, 2.0, "gold", name="ferrule")
    m.centered(8, 2, 3, 1.9, 1.9, "gold", name="band")
    m.centered(8, 14.5, 16.5, 2.4, 2.4, "gold", name="collar")
    # Clock head: octagon made of a square and a 45-degree square, gold rim behind blood glass.
    m.box(4, 16, 7.4, 12, 24, 8.6, "gold", name="rim")
    m.box(4, 16, 7.4, 12, 24, 8.6, "gold", rot=z_rot(45, 8, 20), name="rim_diag")
    for i, (hx, hy) in enumerate(((8, 22.3), (10.3, 20), (8, 17.7), (5.7, 20))):  # hour pips
        m.centered(hx, hy - 0.3, hy + 0.3, 0.6, 0.3, "blood_glow", glow=True, cz=9.0, name=f"pip_{i}")
    for side, (z1, z2) in (("front", (8.6, 8.9)), ("back", (7.1, 7.4))):
        m.box(4.9, 16.9, z1, 11.1, 23.1, z2, "mirror", name=f"face_{side}")
        m.box(4.9, 16.9, z1, 11.1, 23.1, z2, "mirror", rot=z_rot(45, 8, 20), name=f"face_{side}_diag")
    m.box(7.7, 19.7, 8.9, 10.8, 20.3, 9.2, "black_iron", name="hand_hour")
    m.box(7.7, 19.7, 8.9, 8.3, 22.8, 9.2, "black_iron", name="hand_minute")
    m.centered(8, 19.5, 20.5, 1.0, 1.0, "blood_glow", glow=True, cz=9.2, name="hub")
    m.centered(8, 24, 25.5, 1.2, 1.2, "gold", name="crown")
    m.box(7.8, 12, 7.8, 8.2, 16, 8.2, "string", name="pendulum_rod")
    m.centered(8, 10.6, 12, 1.6, 1.6, "blood_glow", glow=True, name="pendulum_drop")
    return m


def thunder_pike():
    m = Model()
    m.centered(8, -12, 21, 1.4, 1.4, "black_iron", name="shaft")
    m.centered(8, -13, -12, 1.8, 1.8, "blood_steel", name="butt_spike")
    for y in (-2, 0, 2):
        m.centered(8, y, y + 1, 1.7, 1.7, "leather", name=f"grip_{y}")
    m.centered(8, 19, 21, 2.4, 2.4, "gold", name="socket")
    m.centered(8, 21, 25, 3.2, 1.0, "blood_steel", name="head")
    m.taper(8, 25, 30, 2.6, 0.6, 1.0, "blood_steel", 4, name="head_tip")
    m.centered(8, 22, 28.5, 0.6, 1.3, "blood_glow", glow=True, name="head_vein")
    # Crimson lightning prongs.
    m.box(9.4, 20.5, 7.6, 12.2, 21.3, 8.4, "blood_glow", rot=z_rot(45, 9.5, 21), glow=True, name="bolt_r1")
    m.box(11, 22.4, 7.6, 13, 23.2, 8.4, "blood_glow", rot=z_rot(-22.5, 11.5, 22.8), glow=True, name="bolt_r2")
    m.box(3.8, 20.5, 7.6, 6.6, 21.3, 8.4, "blood_glow", rot=z_rot(-45, 6.5, 21), glow=True, name="bolt_l1")
    m.box(3, 22.4, 7.6, 5, 23.2, 8.4, "blood_glow", rot=z_rot(22.5, 4.5, 22.8), glow=True, name="bolt_l2")
    m.centered(8, 16, 16.8, 2.0, 2.0, "blood_steel", name="lower_ring")
    return m


def mirrorfang():
    m = Model()
    m.centered(8, -3, -1.4, 2.2, 2.2, "blood_glow", glow=True, name="pommel_eye")
    m.centered(8, -1.4, 4.2, 1.6, 1.6, "leather", name="grip")
    m.centered(8, 4.2, 5.6, 5.4, 2.4, "gold", name="guard")
    m.box(7.2, 5.4, 7.6, 12.6, 6.4, 8.4, "gold", rot=z_rot(45, 8, 5.9), name="guard_wing")
    m.centered(8, 5.6, 13, 3.0, 0.9, "mirror", name="blade_low")
    m.box(6.4, 12.4, 7.55, 9.4, 18, 8.45, "mirror", rot=z_rot(-22.5, 8, 12.6), name="blade_curve")
    m.box(8.6, 16.8, 7.6, 10.6, 21, 8.4, "bone", rot=z_rot(-45, 9.2, 17.5), name="fang_tip")
    m.box(6.2, 6, 7.4, 6.8, 13, 8.6, "blood_glow", glow=True, name="edge_low")
    m.box(6.2, 13, 7.4, 6.8, 17.4, 8.6, "blood_glow", rot=z_rot(-22.5, 8, 12.6), glow=True, name="edge_curve")
    return m


def void_scythe():
    m = Model()
    m.centered(8, -12, 22, 1.5, 1.5, "black_iron", name="snath")
    m.centered(8, -13, -12, 2.2, 2.2, "clot", name="snath_cap")
    m.centered(8, 1, 3.5, 1.8, 1.8, "leather", name="grip")
    m.box(8, 8, 7.2, 11, 9, 8.8, "bone", name="nib")
    m.centered(8, 20, 23, 2.4, 2.4, "bone", name="skull_mount")
    m.centered(8, 21, 22.2, 2.6, 0.6, "blood_glow", glow=True, cz=9.2, name="skull_eyes")
    # Blade sweeping out to the left and curling down.
    m.box(1, 21, 7.5, 8, 24, 8.5, "blood_steel", name="blade_root")
    m.box(-4.5, 19.5, 7.5, 2, 23, 8.5, "blood_steel", rot=z_rot(22.5, 1, 22.5), name="blade_mid")
    m.box(-8, 15.5, 7.55, -3.5, 19.5, 8.45, "blood_steel", rot=z_rot(45, -3.5, 19.5), name="blade_tip")
    m.box(1, 20.2, 7.4, 8, 21, 8.6, "blood_glow", glow=True, name="edge_root")
    m.box(-4.5, 18.7, 7.4, 2, 19.5, 8.6, "blood_glow", rot=z_rot(22.5, 1, 22.5), glow=True, name="edge_mid")
    return m


def paradox_bow():
    m = Model()
    gy = 2.3  # grip height
    m.box(6.2, gy - 1, 7.1, 9.8, gy + 1, 8.9, "leather", name="riser_grip")
    m.box(9.8, gy - 1.2, 7, 11, gy + 1.2, 9, "gold", name="riser_right")
    m.box(5, gy - 1.2, 7, 6.2, gy + 1.2, 9, "gold", name="riser_left")
    # Limbs sweep outward, then recurve back toward the string (-Y).
    for side, sign in (("right", 1), ("left", -1)):
        x, y = 8 + sign * 3, gy
        for i, (direction, length) in enumerate(((0, 3), (-22.5, 3), (-45, 2.6), (-67.5, 2.0))):
            d = direction if sign > 0 else 180 - direction
            x, y = segment(m, x, y, d, length, 1.4 if i < 2 else 1.1, 1.2, "blood_steel", name=f"limb_{side}_{i}")
        m.centered(x, y - 0.7, y + 0.7, 1.4, 1.4, "blood_glow", glow=True, name=f"tip_{side}")
        tip = (x, y)
    string_y = tip[1]
    m.box(8 - (tip[0] - 8), string_y - 0.15, 7.9, tip[0], string_y + 0.15, 8.1, "string", glow=True, name="string")
    # Nocked blood arrow, aimed forward.
    m.box(7.8, string_y - 0.4, 7.8, 8.2, gy + 11, 8.2, "black_iron", name="arrow_shaft")
    m.taper(8, gy + 11, gy + 13.5, 1.8, 0.5, 0.8, "blood_glow", 3, glow=True, name="arrow_head")
    m.box(7.2, string_y, 7.9, 8.8, string_y + 2.2, 8.1, "string", name="fletching")
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
    "paradox_bow": ("Sanguine Paradox Bow", paradox_bow),
}


# --------------------------------------------------------------------------------------------
# Export.
# --------------------------------------------------------------------------------------------

def r2(v):
    return round(v, 3)


def face_uv(axis_dims, seed):
    """UV rectangle sized to the face (1 texel per model unit), offset pseudo-randomly."""
    w, h = (min(16.0, max(0.5, d)) for d in axis_dims)
    rng = random.Random(seed)
    u = rng.uniform(0, 16 - w)
    v = rng.uniform(0, 16 - h)
    return [r2(u), r2(v), r2(u + w), r2(v + h)]


def element_json(e, index, weapon):
    (x1, y1, z1), (x2, y2, z2) = e["from"], e["to"]
    dx, dy, dz = x2 - x1, y2 - y1, z2 - z1
    tex = "#" + e["mat"]
    dims = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}
    faces = {}
    for face, d in dims.items():
        seed = int(hashlib.md5(f"{weapon}:{index}:{face}".encode()).hexdigest()[:8], 16)
        faces[face] = {"uv": face_uv(d, seed), "texture": tex}
    out = {"name": e["name"], "from": [r2(v) for v in e["from"]], "to": [r2(v) for v in e["to"]]}
    if e["rot"]:
        angle, origin = e["rot"]
        out["rotation"] = {"angle": angle, "axis": "z", "origin": [r2(v) for v in origin]}
    if e["glow"]:
        out["light_emission"] = 15
    out["faces"] = faces
    return out


def bounds(model):
    """Extent of the model once pre-rotated by -45 degrees (how it's shown in hand/GUI)."""
    pts = []
    for e in model.elements:
        (x1, y1, _), (x2, y2, _) = e["from"], e["to"]
        corners = [(x1, y1), (x1, y2), (x2, y1), (x2, y2)]
        if e["rot"]:
            a = math.radians(e["rot"][0])
            ox, oy = e["rot"][1][:2]
            corners = [(ox + (x - ox) * math.cos(a) - (y - oy) * math.sin(a), oy + (x - ox) * math.sin(a) + (y - oy) * math.cos(a)) for x, y in corners]
        pts += corners
    a = math.radians(-45)
    rot = [((x - 8) * math.cos(a) - (y - 8) * math.sin(a), (x - 8) * math.sin(a) + (y - 8) * math.cos(a)) for x, y in pts]
    xs, ys = [p[0] for p in rot], [p[1] for p in rot]
    return max(max(xs) - min(xs), max(ys) - min(ys))


def display(model, weapon):
    extent = bounds(model)
    gui = r2(min(1.0, 15.0 / extent))
    ground = r2(min(0.5, 8.0 / extent))
    # Right-hand values are vanilla item/handheld with Z shifted by -45 (see module docstring).
    # Left-hand entries are omitted on purpose: Minecraft mirrors the right-hand transform.
    disp = {
        "thirdperson_righthand": {"rotation": [0, -90, 10], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
        "firstperson_righthand": {"rotation": [0, -90, -20], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
        "gui": {"rotation": [-20, 30, -45], "translation": [0, 0, 0], "scale": [gui, gui, gui]},
        "ground": {"rotation": [0, 0, -45], "translation": [0, 2, 0], "scale": [ground, ground, ground]},
        "fixed": {"rotation": [0, 180, -45], "translation": [0, 0, 0], "scale": [gui, gui, gui]},
        "head": {"rotation": [0, 180, -45], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    }
    if weapon == "meteor_gauntlet":
        # Worn over the fist rather than swung: sit it upright on the hand.
        disp["thirdperson_righthand"] = {"rotation": [75, 0, 0], "translation": [0, 1.5, 1.5], "scale": [0.6, 0.6, 0.6]}
        disp["firstperson_righthand"] = {"rotation": [-10, -80, 10], "translation": [2, 0, -2], "scale": [0.7, 0.7, 0.7]}
        disp["gui"] = {"rotation": [25, -35, 0], "translation": [0, 0, 0], "scale": [0.95, 0.95, 0.95]}
        disp["ground"] = {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]}
        disp["fixed"] = {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}
    return disp


def main():
    tex_out = os.path.join(ASSETS, "textures", TEX_DIR)
    os.makedirs(tex_out, exist_ok=True)
    for i, name in enumerate(PALETTES):
        paint(name, 1000 + i).save(os.path.join(tex_out, f"{name}.png"))

    models_out = os.path.join(ASSETS, "models", "item")
    items_out = os.path.join(ASSETS, "items")
    os.makedirs(models_out, exist_ok=True)
    os.makedirs(items_out, exist_ok=True)
    preview = {"textures": {}, "weapons": {}}
    for name in PALETTES:
        preview["textures"][name] = f"textures/{name}.png"

    for weapon, (title, build) in WEAPONS.items():
        model = build()
        used = sorted({e["mat"] for e in model.elements})
        elements = [element_json(e, i, weapon) for i, e in enumerate(model.elements)]
        for e in elements:
            for coord in e["from"] + e["to"]:
                assert -16 <= coord <= 32, f"{weapon}/{e['name']} out of bounds"
            if "rotation" in e:
                assert e["rotation"]["angle"] in (-45, -22.5, 0, 22.5, 45), f"{weapon}/{e['name']} bad angle"
        textures = {mat: f"{NS}:{TEX_DIR}/{mat}" for mat in used}
        textures["particle"] = textures.get("blood_steel", next(iter(textures.values())))
        model_json = {
            "credit": "Bloodbath 3D weapons - generated by tools/models/generate.py",
            "texture_size": [16, 16],
            "textures": textures,
            "elements": elements,
            "display": display(model, weapon),
        }
        with open(os.path.join(models_out, f"{weapon}.json"), "w") as f:
            json.dump(model_json, f, indent=1)
        with open(os.path.join(items_out, f"{weapon}.json"), "w") as f:
            json.dump({"model": {"type": "minecraft:model", "model": f"{NS}:item/{weapon}"}}, f, indent=2)
        preview["weapons"][weapon] = {"title": title, "elements": elements}
        print(f"{weapon:16s} {len(elements):3d} cubes  {', '.join(used)}")

    prev_dir = os.path.join(ROOT, "docs", "preview")
    os.makedirs(os.path.join(prev_dir, "textures"), exist_ok=True)
    for name in PALETTES:
        Image.open(os.path.join(tex_out, f"{name}.png")).save(os.path.join(prev_dir, "textures", f"{name}.png"))
    with open(os.path.join(prev_dir, "models.json"), "w") as f:
        json.dump(preview, f, separators=(",", ":"))


if __name__ == "__main__":
    main()
