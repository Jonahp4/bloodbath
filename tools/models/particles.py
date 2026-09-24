"""Custom blood particle sprites.

A resource pack can't add particle types, only redraw existing ones, so Bloodbath takes over a few
vanilla particles that are rare in normal play and not recoloured by the game, and the plugin
spawns those (for players with the pack only) in place of plain dust:

    sculk_charge       -> blood splat      (a drop that bursts into a splash; rolls to any angle)
    sculk_charge_pop   -> blood spark      (a hot glowing fleck)
    sculk_soul         -> blood wisp       (a rising crimson flame-soul)
    shriek             -> blood ring       (a ring of blood rising off the ground)
    *_obsidian_tear    -> blood drop       (hangs, falls and splashes; crying obsidian bleeds too)

The sculk ones only otherwise appear where sculk spreads in the deep dark, and the shriek over a
sculk shrieker. Everything is painted at 4x and filtered down, so edges are soft instead of the
square specks dust particles give.
"""
import json
import math
import os

import numpy as np
from PIL import Image

SS = 4  # supersampling

# Blood, from the hot highlight to the dried edge.
HOT = np.array([255, 196, 190], float)
BRIGHT = np.array([255, 58, 66], float)
BODY = np.array([196, 16, 30], float)
DEEP = np.array([112, 4, 14], float)
DRY = np.array([58, 2, 8], float)


def _hash(*v):
    h = 2166136261
    for x in v:
        h = ((h ^ (int(x) & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
    return (h % 10007) / 10007.0


def _canvas(size):
    n = size * SS
    ys, xs = np.mgrid[0:n, 0:n]
    # Centre-based unit coordinates: -1..1 across the sprite.
    return ((xs + 0.5) / n * 2 - 1), ((ys + 0.5) / n * 2 - 1)


def _finish(rgb, alpha, size):
    """Premultiplied downsample, so the soft edges don't pick up dark fringes."""
    n = size * SS
    a = np.clip(alpha, 0, 1)
    pre = rgb * a[..., None]
    pre = pre.reshape(size, SS, size, SS, 3).mean(axis=(1, 3))
    a = a.reshape(size, SS, size, SS).mean(axis=(1, 3))
    out = np.zeros((size, size, 4))
    safe = np.maximum(a, 1e-6)[..., None]
    out[..., :3] = np.where(a[..., None] > 0, pre / safe, 0)
    out[..., 3] = a * 255
    return Image.fromarray(np.clip(out, 0, 255).astype("uint8"), "RGBA")


def _shade(d, lx, ly, x, y):
    """Blood colour for a blob: depth d (0 edge .. 1 centre) plus a light from the top left."""
    light = np.clip(0.5 - 0.5 * (x * lx + y * ly), 0, 1)
    c = DEEP[None, None] * (1 - d[..., None]) + BODY[None, None] * d[..., None]
    c = c * (0.75 + 0.5 * light[..., None])
    return c


def _blob(x, y, cx, cy, r, squash=1.0):
    return np.hypot((x - cx), (y - cy) * squash) / r


def _teardrop(x, y, cx, cy, r, tip_y, tip_dx=0.0):
    """Depth (0 edge .. 1 centre, <0 outside) of a drop: round at the bottom (centre cx, cy, radius
    r), drawn up to a point at tip_y, the tip pushed sideways by tip_dx."""
    k = np.clip((cy - y) / max(1e-3, cy - tip_y), 0, 1)       # 0 at the bulb's centre, 1 at the tip
    axis = cx + tip_dx * k ** 2
    width = np.where(y < cy, r * (1 - k) ** 0.85, np.sqrt(np.clip(r * r - (y - cy) ** 2, 0, None)))
    return 1 - np.abs(x - axis) / np.maximum(width, 1e-4)


def splat_frame(i, n=7, size=16):
    """A drop that lands and spreads: a round drop, then a splash with flung droplets, drying out."""
    x, y = _canvas(size)
    t = i / (n - 1)
    grow = min(1.0, t * 1.6)
    r = 0.38 + 0.38 * grow
    ang = np.arctan2(y, x)
    # A wobbly rim: a few lobes that push out as it spreads.
    lobes = 1 + grow * (0.12 * np.sin(ang * 3 + 1.3) + 0.1 * np.sin(ang * 7 + 0.4) + 0.07 * np.sin(ang * 11 + 2.1))
    d = np.hypot(x, y) / (r * lobes)
    body = d < 1
    depth = np.clip(1 - d, 0, 1) ** 0.6
    rgb = _shade(depth, 0.7, 0.7, x, y)
    alpha = body.astype(float)
    # Flung droplets around the splash.
    if i > 0:
        for k in range(7):
            a = _hash(k, 7) * math.tau
            dist = min(0.86, r * (1.05 + 0.45 * grow + 0.2 * _hash(k, 3)))
            rr = 0.07 + 0.07 * _hash(k, 5)
            dd = _blob(x, y, math.cos(a) * dist, math.sin(a) * dist, rr)
            m = dd < 1
            alpha = np.maximum(alpha, m.astype(float))
            rgb = np.where(m[..., None], _shade(np.clip(1 - dd, 0, 1), 0.7, 0.7, x, y), rgb)
    # Wet highlight, fading as it dries.
    wet = max(0.0, 1 - t * 1.2)
    hl = _blob(x, y, -r * 0.35, -r * 0.4, r * 0.28, 1.3)
    rgb = np.where((hl < 1)[..., None] & body[..., None], rgb * (1 - wet) + HOT * wet * 0.9 + rgb * wet * 0.1, rgb)
    # Drying: the colour darkens and it goes see-through at the end.
    dry = max(0.0, (t - 0.55) / 0.45)
    rgb = rgb * (1 - dry) + DRY * dry
    alpha *= 1 - dry * 0.75
    return _finish(rgb, alpha, size)


def spark_frame(i, n=4, size=16):
    """A glowing fleck of blood with a four-point glint, burning down."""
    x, y = _canvas(size)
    t = i / (n - 1)
    r = 0.34 * (1 - 0.55 * t)
    d = np.hypot(x, y) / r
    core = np.clip(1 - d, 0, 1)
    glint = np.clip(1 - (np.minimum(np.abs(x), np.abs(y)) / (0.05 * (1 - t) + 0.02)), 0, 1) \
        * np.clip(1 - np.hypot(x, y) / (0.95 * (1 - 0.5 * t)), 0, 1)
    glow = np.clip(1 - np.hypot(x, y) / (r * 2.2), 0, 1) ** 2
    heat = np.clip(core * 1.6, 0, 1)
    rgb = BODY[None, None] * (1 - heat[..., None]) + BRIGHT[None, None] * heat[..., None]
    rgb = np.where((core > 0.55)[..., None], HOT * (1 - t * 0.6) + BRIGHT * t * 0.6, rgb)
    rgb = np.where((glint > core)[..., None], BRIGHT * 0.6 + HOT * 0.4, rgb)
    alpha = np.clip(np.maximum.reduce([core * 2, glint * (1 - t * 0.5), glow * 0.55]), 0, 1)
    return _finish(rgb, alpha, size)


def wisp_frame(i, n=11, size=16):
    """A rising crimson soul-flame: a teardrop with a hot core, its tip licking side to side,
    shrinking and cooling as it burns out."""
    x, y = _canvas(size)
    t = i / (n - 1)
    flick = 0.28 * math.sin(i * 1.9 + 0.4)
    r = 0.4 * (1 - 0.4 * t)
    cy = 0.42 - 0.1 * t
    depth = _teardrop(x, y, 0.0, cy, r, cy - r * (3.0 + 0.6 * math.sin(i * 1.3)), flick)
    shape = depth > 0
    d = np.clip(depth, 0, 1)
    rise = np.clip((cy - y) / 1.2, 0, 1)                      # cooler toward the tip
    hot = np.clip(d * 1.5 - rise * 0.9 - t * 0.6, 0, 1)
    rgb = DEEP[None, None] * (1 - d[..., None]) + BODY[None, None] * d[..., None]
    rgb = rgb * (1 - hot[..., None]) + (BRIGHT * 0.45 + HOT * 0.55) * hot[..., None]
    alpha = shape * np.clip(0.3 + d * 1.6, 0, 1) * np.clip(1.15 - rise * 0.7, 0, 1) * (1 - max(0.0, t - 0.6) * 1.7)
    return _finish(rgb, alpha, size)


def ring_frame(size=16):
    """A thin blood ring with a few runs of blood hanging off it (the shriek is drawn tilted)."""
    x, y = _canvas(size)
    d = np.hypot(x, y)
    band = np.clip(1 - np.abs(d - 0.72) / 0.13, 0, 1)
    ang = np.arctan2(y, x)
    beads = 0.5 + 0.5 * np.sin(ang * 11)
    rgb = DEEP[None, None] * (1 - band[..., None]) + BRIGHT[None, None] * band[..., None]
    rgb = np.where((band > 0.75)[..., None], BRIGHT * 0.7 + HOT * 0.3, rgb)
    alpha = np.clip(band * 1.6, 0, 1) * (0.7 + 0.3 * beads)
    return _finish(rgb, alpha, size)


def drop_frame(kind, size=16):
    """Red only: the game tints obsidian tears purple (x0.51, x0.03, x0.89), and purple times pure
    red is a dark, glowing blood red. Any green or blue would turn it purple."""
    x, y = _canvas(size)
    if kind == "hang":
        d = _blob(x, y, 0, -0.35, 0.34, 1.0)
        neck = (np.abs(x) < 0.12 * np.clip((0.0 - y) / 0.6, 0, 1) + 0.02) & (y < -0.35) & (y > -1)
        shape = (d < 1) | neck
        depth = np.clip(1 - d, 0, 1)
    elif kind == "fall":
        depth = _teardrop(x, y, 0.0, 0.3, 0.36, -0.75)
        shape = depth > 0
        depth = np.clip(depth, 0, 1)
    else:  # land: a flattened splash with a crown
        d = _blob(x, y, 0, 0.45, 0.62, 3.2)
        crown = np.zeros_like(x, dtype=bool)
        for k, cx in enumerate((-0.45, -0.15, 0.2, 0.5)):
            crown |= _blob(x, y, cx, 0.12 - 0.1 * (k % 2), 0.08, 0.8) < 1
        shape = (d < 1) | crown
        depth = np.clip(1 - d, 0, 1)
    red = 150 + 105 * np.clip(depth, 0, 1)
    hl = _blob(x, y, -0.12, {"hang": -0.45, "fall": 0.18, "land": 0.3}[kind], 0.12, 1.2) < 1
    red = np.where(hl, 255, red)
    rgb = np.zeros(x.shape + (3,))
    rgb[..., 0] = red
    rgb[..., 1] = np.where(hl, 90, 0)  # x0.03: barely there, it just warms the highlight
    return _finish(rgb, shape.astype(float), size)


def write(pack_root, ns):
    tex = os.path.join(pack_root, "assets", ns, "textures", "particle")
    defs = os.path.join(pack_root, "assets", "minecraft", "particles")
    os.makedirs(tex, exist_ok=True)
    os.makedirs(defs, exist_ok=True)

    def save(name, img):
        img.save(os.path.join(tex, name + ".png"))
        return f"{ns}:{name}"

    def define(particle, textures):
        with open(os.path.join(defs, particle + ".json"), "w") as f:
            json.dump({"textures": textures}, f, indent=2)

    define("sculk_charge", [save(f"blood_splat_{i}", splat_frame(i)) for i in range(7)])
    define("sculk_charge_pop", [save(f"blood_spark_{i}", spark_frame(i)) for i in range(4)])
    define("sculk_soul", [save(f"blood_wisp_{i}", wisp_frame(i)) for i in range(11)])
    define("shriek", [save("blood_ring", ring_frame())])
    for kind, particle in (("hang", "dripping_obsidian_tear"), ("fall", "falling_obsidian_tear"),
                           ("land", "landing_obsidian_tear")):
        define(particle, [save(f"blood_drop_{kind}", drop_frame(kind))])
    print("particles: blood splat, spark, wisp, ring, drop")


def preview(path, ns_dir):
    """A contact sheet of every sprite, big, on dark and light backgrounds."""
    names = sorted((f for f in os.listdir(ns_dir) if f.startswith(("blood_splat", "blood_spark", "blood_wisp",
                                                                  "blood_ring", "blood_drop"))),
                   key=lambda f: (f.rsplit("_", 1)[0], int(f.rsplit("_", 1)[1][:-4]) if f.rsplit("_", 1)[1][:-4].isdigit() else f))
    scale = 8
    tile = 16 * scale + 8
    sheet = Image.new("RGBA", (tile * 11, tile * 2 * 5), (0, 0, 0, 0))
    rows = {"blood_splat": 0, "blood_spark": 1, "blood_wisp": 2, "blood_ring": 3, "blood_drop": 4}
    col = {k: 0 for k in rows}
    for name in names:
        key = next(k for k in rows if name.startswith(k))
        img = Image.open(os.path.join(ns_dir, name)).convert("RGBA")
        if key == "blood_drop":  # show what the game draws: tinted by the obsidian-tear purple
            a = np.asarray(img).astype(float)
            a[..., 0] *= 0.51
            a[..., 1] *= 0.03
            a[..., 2] *= 0.89
            img = Image.fromarray(a.astype("uint8"), "RGBA")
        big = img.resize((16 * scale, 16 * scale), Image.NEAREST)
        for bg_row, bg in enumerate(((22, 22, 26, 255), (150, 150, 150, 255))):
            cell = Image.new("RGBA", big.size, bg)
            cell.alpha_composite(big)
            sheet.paste(cell, (col[key] * tile, (rows[key] * 2 + bg_row) * tile))
        col[key] += 1
    sheet.save(path)
