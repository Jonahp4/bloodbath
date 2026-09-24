"""Custom blood particle sprites.

A resource pack can't add particle types, only redraw existing ones, so Bloodbath takes over a few
vanilla particles that are rare in normal play, and the plugin spawns those (for players with the
pack only) in place of plain dust:

    sculk_charge             -> blood splat   (a drop that bursts into a splash and dries; any angle)
    sculk_charge_pop         -> blood spark   (a hot glowing fleck)
    sculk_soul               -> blood wisp    (a rising crimson flame-soul)
    shriek                   -> blood ring    (a beaded ring of blood rising off the ground)
    trail                    -> blood trail   (a soft glowing bead that streams to a target)
    *_dripstone_lava, landing_lava -> blood drop (falls at full weight and splashes, with a plip)

The sculk ones only otherwise appear where sculk spreads in the deep dark, the shriek over a sculk
shrieker, the trail from a creaking heart, the dripstone lava drops under pointed dripstone.

Drops use the dripstone lava particle because it falls like a real drop (full gravity; the
obsidian tear used before drifts down like snow) and makes a sound where it lands. The game tints
lava drops orange (x1.0 red, x0.286 green, x0.083 blue), so the drop textures are painted "untinted":
each colour divided by that tint, so what reaches the screen is blood red. The trail is tinted by
the colour the plugin gives it, so it's painted in greys.

Everything is painted at 4x and filtered down, so edges are soft instead of the square specks
dust particles give.
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

# The game's tint on lava drops, and the drop colours as they should appear on screen (the most a
# lava tint allows: green up to 72, blue up to 21).
LAVA_TINT = np.array([1.0, 0.2857143, 0.0833333])
DROP_SHINE = np.array([255, 68, 21], float)
DROP_BODY = np.array([206, 12, 19], float)
DROP_DEEP = np.array([104, 3, 8], float)


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
    a = np.clip(alpha, 0, 1)
    pre = rgb * a[..., None]
    pre = pre.reshape(size, SS, size, SS, 3).mean(axis=(1, 3))
    a = a.reshape(size, SS, size, SS).mean(axis=(1, 3))
    out = np.zeros((size, size, 4))
    safe = np.maximum(a, 1e-6)[..., None]
    out[..., :3] = np.where(a[..., None] > 0, pre / safe, 0)
    out[..., 3] = a * 255
    return Image.fromarray(np.clip(out, 0, 255).astype("uint8"), "RGBA")


def _untint(img, tint):
    """The texture that, multiplied by the game's tint, shows the colours painted in img."""
    a = np.asarray(img).astype(float)
    a[..., :3] = np.clip(a[..., :3] / tint[None, None], 0, 255)
    return Image.fromarray(a.astype("uint8"), "RGBA")


def tinted(img, tint):
    """What the game draws for a texture under a tint (for previews)."""
    a = np.asarray(img).astype(float)
    a[..., :3] = a[..., :3] * np.asarray(tint, float)[None, None]
    return Image.fromarray(np.clip(a, 0, 255).astype("uint8"), "RGBA")


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
    """A drop that lands and spreads: a round drop, then a splash with flung droplets and runs,
    drying out."""
    x, y = _canvas(size)
    t = i / (n - 1)
    grow = min(1.0, t * 1.6)
    r = 0.36 + 0.36 * grow
    ang = np.arctan2(y, x)
    # A wobbly rim: a few lobes that push out as it spreads.
    lobes = 1 + grow * (0.13 * np.sin(ang * 3 + 1.3) + 0.1 * np.sin(ang * 7 + 0.4) + 0.07 * np.sin(ang * 11 + 2.1))
    d = np.hypot(x, y) / (r * lobes)
    body = d < 1
    depth = np.clip(1 - d, 0, 1) ** 0.6
    rgb = _shade(depth, 0.7, 0.7, x, y)
    alpha = body.astype(float)
    # Runs of blood sliding down from the splash.
    if i > 1:
        for k, (cx, length) in enumerate(((-0.22, 0.34), (0.16, 0.5), (0.34, 0.24))):
            run = min(1.0, (t - 0.2) * 1.6) * length
            top = r * 0.6
            m = (np.abs(x - cx) < 0.06 - 0.03 * np.clip((y - top) / max(run, 1e-3), 0, 1)) & (y > 0) & (y < top + run)
            bead = _blob(x, y, cx, top + run, 0.08) < 1
            m = m | bead
            alpha = np.maximum(alpha, m.astype(float))
            rgb = np.where(m[..., None] & ~body[..., None], DEEP * 1.15, rgb)
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


def ring_frame(size=32):
    """A ring of blood beads with short runs hanging off its lower half (the game draws the shriek
    tilted and rising, so the lower half reads as the front)."""
    x, y = _canvas(size)
    d = np.hypot(x, y)
    ang = np.arctan2(y, x)
    band = np.clip(1 - np.abs(d - 0.7) / (0.07 + 0.03 * (0.5 + 0.5 * np.sin(ang * 5 + 0.7))), 0, 1)
    rgb = DEEP[None, None] * (1 - band[..., None]) + BRIGHT[None, None] * band[..., None]
    alpha = np.clip(band * 1.8, 0, 1)
    for k in range(12):
        a = k * math.tau / 12 + 0.13 * _hash(k, 2)
        bx, by = math.cos(a) * 0.7, math.sin(a) * 0.7
        bead = _blob(x, y, bx, by, 0.1 + 0.03 * _hash(k, 4))
        m = bead < 1
        shade = _shade(np.clip(1 - bead, 0, 1), 0.7, 0.7, x - bx, y - by)
        rgb = np.where(m[..., None], shade * 1.25, rgb)
        alpha = np.maximum(alpha, m.astype(float))
        hl = _blob(x, y, bx - 0.03, by - 0.035, 0.035) < 1
        rgb = np.where(hl[..., None], HOT, rgb)
        if by > 0.1 and _hash(k, 9) > 0.35:              # a run hanging off the front of the ring
            length = 0.12 + 0.16 * _hash(k, 11)
            run = (np.abs(x - bx) < 0.035) & (y > by) & (y < by + length)
            tip = _blob(x, y, bx, by + length, 0.05) < 1
            m = run | tip
            rgb = np.where(m[..., None] & ~(bead < 1)[..., None], DEEP * 1.3, rgb)
            alpha = np.maximum(alpha, m.astype(float))
    return _finish(rgb, alpha, size)


def trail_frame(k, size=16):
    """A soft glowing bead in greys: the game tints it with the colour the plugin asks for, so the
    centre shows that colour and the rim a darker shade of it, fading out."""
    x, y = _canvas(size)
    stretch = (1.0, 1.25, 0.85)[k]
    d = np.hypot(x * stretch, y / stretch) / 0.62
    core = np.clip(1 - d, 0, 1)
    grey = 150 + 105 * np.clip(core * 1.8, 0, 1)
    rgb = np.repeat(grey[..., None], 3, axis=2)
    alpha = np.clip(core * 2.2, 0, 1) ** 1.2
    hl = _blob(x, y, -0.16, -0.18, 0.14) < 1
    rgb = np.where(hl[..., None] & (core > 0.3)[..., None], 255.0, rgb)
    return _finish(rgb, alpha, size)


def drop_frame(kind, size=16):
    """A blood drop as it should look on screen (untinted before saving): hanging from a thread,
    falling, or splashed on the ground. The lava tint can't make a pale highlight, so the light is
    a brighter scarlet rim on the lit side rather than a white glint."""
    x, y = _canvas(size)
    if kind == "hang":
        depth = _teardrop(x, y, 0.0, 0.28, 0.52, -1.05)
        cy = 0.28
    elif kind == "fall":
        depth = _teardrop(x, y, 0.0, 0.34, 0.5, -0.95)
        cy = 0.34
    else:  # land: a low splash with a crown of droplets
        depth = 1 - _blob(x, y, 0, 0.5, 0.9, 3.0)
        for cx, cy_, r in ((-0.66, 0.12, 0.12), (-0.32, -0.08, 0.13), (0.06, -0.2, 0.12), (0.4, -0.02, 0.13),
                           (0.7, 0.16, 0.11)):
            depth = np.maximum(depth, 1 - _blob(x, y, cx, cy_, r))
        cy = 0.5
    shape = depth > 0
    d = np.clip(depth, 0, 1)
    # Lit from the upper left: the lit side of the rim runs toward the shine colour.
    lit = np.clip(0.5 - 0.7 * x - 0.35 * (y - cy), 0, 1)
    rim = np.clip(1 - d * 3.2, 0, 1)
    rgb = DROP_DEEP[None, None] * (1 - d[..., None]) + DROP_BODY[None, None] * d[..., None]
    rgb = rgb * (0.85 + 0.3 * lit[..., None])
    glow = (rim * lit)[..., None]
    rgb = rgb * (1 - glow) + DROP_SHINE[None, None] * glow
    return _untint(_finish(rgb, shape.astype(float), size), LAVA_TINT)


def _clear_stale(tex, defs):
    """Removes overrides and sprites earlier versions wrote that aren't used any more."""
    for particle in ("dripping_obsidian_tear", "falling_obsidian_tear", "landing_obsidian_tear"):
        path = os.path.join(defs, particle + ".json")
        if os.path.exists(path):
            os.remove(path)


def write(pack_root, ns):
    tex = os.path.join(pack_root, "assets", ns, "textures", "particle")
    defs = os.path.join(pack_root, "assets", "minecraft", "particles")
    os.makedirs(tex, exist_ok=True)
    os.makedirs(defs, exist_ok=True)
    _clear_stale(tex, defs)

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
    define("trail", [save(f"blood_trail_{k}", trail_frame(k)) for k in range(3)])
    hang, fall, land = (save(f"blood_drop_{kind}", drop_frame(kind)) for kind in ("hang", "fall", "land"))
    define("dripping_dripstone_lava", [hang])
    define("falling_dripstone_lava", [fall])
    define("landing_lava", [land])

    # Where a dripstone lava drop lands, the game plays the lava drip sound: make that a soft,
    # low plip of liquid instead of a sizzle.
    with open(os.path.join(pack_root, "assets", "minecraft", "sounds.json"), "w") as f:
        json.dump({"block.pointed_dripstone.drip_lava": {"replace": True, "sounds": [
            {"name": "block.pointed_dripstone.drip_water", "type": "event", "volume": 0.55, "pitch": 0.72}]}}, f, indent=2)
    print("particles: blood splat, spark, wisp, ring, trail, drop")


def strip(images, scale):
    """Frames side by side, each scaled up (nearest) for previews."""
    w, h = images[0].size
    out = Image.new("RGBA", (w * scale * len(images), h * scale), (0, 0, 0, 0))
    for i, img in enumerate(images):
        out.paste(img.resize((w * scale, h * scale), Image.NEAREST), (i * w * scale, 0))
    return out
