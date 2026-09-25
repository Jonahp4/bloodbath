"""
The Bloodlands and the Blood Anvil in the resource pack.

  assets/minecraft/textures/block/reinforced_deepslate_*   Bloodstone Frame (the portal frame block),
                                                           cracked black stone whose cracks glow and pulse
  assets/unchartedsmp/textures/block/blood_portal.png      the portal's surface: flowing blood, tileable,
                                                           animated (drawn by item displays, full bright)
  assets/unchartedsmp/models/item/blood_anvil.json         the Blood Anvil: black iron on a stone plinth,
                                                           glowing crimson cracks and a groove of blood
  assets/unchartedsmp/textures/item/blood_drop.png         the Blood Drop: a glossy bead, its shine moving
  assets/unchartedsmp/textures/font/blood_anvil.png        the anvil screen's backdrop (font unchartedsmp:gui)
  assets/unchartedsmp/{items,models,textures}/gui/...      the screen's own pictures: the Bleed Weapon
                                                           button (a model wider than its slot, one per
                                                           state), level pips, a blood vial, ghosts, arrows
  assets/minecraft/items/{anvil,firework_star,red_stained_glass_pane}.json
                                                           custom_model_data overrides, vanilla otherwise

Everything is drawn here from code (no vanilla art is copied), in the same palette as the rest of
the pack. Run by generate.py.
"""
import json
import math
import os

from PIL import Image

NS = "unchartedsmp"


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(x + (y - x) * t)) for x, y in zip(a, b))


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c)


def h2(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 2246822519) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def vnoise(x, y, seed):
    xi, yi = math.floor(x), math.floor(y)
    fx, fy = x - xi, y - yi
    sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = h2(xi, yi, seed), h2(xi + 1, yi, seed)
    c, d = h2(xi, yi + 1, seed), h2(xi + 1, yi + 1, seed)
    return (a + (b - a) * sx) * (1 - sy) + (c + (d - c) * sx) * sy


def fbm(x, y, seed, octaves=3):
    total, amp, norm = 0.0, 1.0, 0.0
    for o in range(octaves):
        total += vnoise(x * (2 ** o), y * (2 ** o), seed + o * 17) * amp
        norm += amp
        amp *= 0.5
    return total / norm


# ---- palette -----------------------------------------------------------------------------------
VOID = rgb("#0b0506")
STONE = [rgb(c) for c in ("#120d0f", "#1a1316", "#231a1d", "#2e2226", "#3d2e33")]
IRON = [rgb(c) for c in ("#0f0d11", "#18151b", "#221e25", "#2e2931", "#3f3943", "#5a535e")]
CRACK = [rgb(c) for c in ("#3a040b", "#6e0914", "#a8121f", "#e0303c", "#ff7a82")]
BLOOD = [rgb(c) for c in ("#2a0306", "#4e0710", "#7c0c18", "#b31623", "#e8323f", "#ff8a92")]
PALE = rgb("#e2b8b3")
GOLD = rgb("#caa441")


def img(w, h, fill=(0, 0, 0, 0)):
    return Image.new("RGBA", (w, h), fill)


def put(im, x, y, c, a=255):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), tuple(c[:3]) + (a,))


def save(im, path, mcmeta=None):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path, optimize=True)
    if mcmeta is not None:
        with open(path + ".mcmeta", "w") as f:
            json.dump(mcmeta, f, indent=2)


def dump(obj, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=1)


# ---- a small pixel font (5x7 capitals) for labels and the button ---------------------------------
GLYPHS = {
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "B": ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
    "C": ["01111", "10000", "10000", "10000", "10000", "10000", "01111"],
    "D": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "F": ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
    "G": ["01111", "10000", "10000", "10011", "10001", "10001", "01111"],
    "H": ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
    "I": ["111", "010", "010", "010", "010", "010", "111"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "M": ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "P": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "U": ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    "V": ["10001", "10001", "10001", "10001", "10001", "01010", "00100"],
    "W": ["10001", "10001", "10001", "10101", "10101", "10101", "01010"],
    "X": ["10001", "10001", "01010", "00100", "01010", "10001", "10001"],
    "Y": ["10001", "10001", "01010", "00100", "00100", "00100", "00100"],
    ".": ["0", "0", "0", "0", "0", "0", "1"],
    " ": ["000", "000", "000", "000", "000", "000", "000"],
}


def glyph(ch, tight=False):
    # Tight lettering (for a label that only just fits) uses a one-pixel space.
    return ["0"] * 7 if tight and ch == " " else GLYPHS[ch]


def text_width(text, tight=False):
    return sum(len(glyph(ch, tight)[0]) + 1 for ch in text) - 1


def draw_text(im, x, y, text, color, shadow=None, alpha=255, tight=False):
    cx = x
    for ch in text:
        rows = glyph(ch, tight)
        for gy, row in enumerate(rows):
            for gx, bit in enumerate(row):
                if bit == "1":
                    if shadow is not None:
                        put(im, cx + gx + 1, y + gy + 1, shadow, alpha)
                    put(im, cx + gx, y + gy, color, alpha)
        cx += len(rows[0]) + 1


# ---- Bloodstone Frame: the portal frame block ------------------------------------------------------

def crack_mask(w, h, seed, starts, length, branch=0.25):
    """Random-walk cracks: {(x, y): depth} where depth 0 is the glowing core."""
    cells = {}
    for si, (sx, sy, dx, dy) in enumerate(starts):
        walkers = [(sx, sy, dx, dy, length)]
        step = 0
        while walkers:
            x, y, ddx, ddy, left = walkers.pop()
            for i in range(left):
                if not (0 <= x < w and 0 <= y < h):
                    break
                cells[(int(x), int(y))] = 0
                r = h2(int(x) + step, int(y) + si * 31, seed)
                step += 1
                if r < 0.33:
                    x += ddx + (1 if h2(step, si, seed + 1) < 0.5 else -1) * (1 - abs(ddx))
                    y += ddy + (1 if h2(step, si, seed + 2) < 0.5 else -1) * (1 - abs(ddy))
                else:
                    x += ddx
                    y += ddy
                if h2(step, i, seed + 3) < branch * 0.12 and left - i > 3:
                    walkers.append((x, y, ddy or ddx, ddx or ddy, (left - i) // 2))
    # A darker halo around each crack.
    halo = {}
    for (x, y) in cells:
        for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + ox, y + oy) not in cells:
                halo[(x + ox, y + oy)] = 1
    cells.update({k: v for k, v in halo.items() if k not in cells})
    return cells


def vein_mask(paths, seed):
    """Cracks along hand-placed waypoints, one pixel wide and 4-connected, with a dark halo."""
    cells = {}
    for path in paths:
        for (ax, ay), (bx, by) in zip(path, path[1:]):
            x, y = ax, ay
            cells[(x, y)] = 0
            while (x, y) != (bx, by):
                # Step along whichever axis is further off, wobbling now and then.
                if abs(bx - x) > abs(by - y) or (abs(bx - x) == abs(by - y) and h2(x, y, seed) < 0.5):
                    x += 1 if bx > x else -1
                else:
                    y += 1 if by > y else -1
                cells[(x, y)] = 0
    halo = {}
    for (x, y) in cells:
        for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + ox, y + oy) not in cells:
                halo[(x + ox, y + oy)] = 1
    cells.update(halo)
    return cells


def frame_face(seed, kind, glow):
    size = 16
    im = img(size, size)
    for y in range(size):
        for x in range(size):
            n = fbm(x * 0.35, y * 0.35, seed)
            c = mix(STONE[1], STONE[3], n * 1.1)
            # Carved border: a bevel, then a groove one pixel in.
            if x == 0 or y == 0:
                c = STONE[4] if kind != "bottom" else STONE[3]
            elif x == size - 1 or y == size - 1:
                c = STONE[0]
            elif x == 1 or y == 1 or x == size - 2 or y == size - 2:
                c = shade(c, 0.72)
            put(im, x, y, c)
    # Veins meet every edge at the same spots (x 6 top and bottom, y 9 left and right), so a wall of
    # frame blocks reads as one network of cracks running through the whole ring rather than a stamp
    # repeated on each block.
    if kind == "side":
        paths = [[(6, 0), (6, 3), (8, 5), (7, 8), (9, 11), (8, 13), (6, 15)],
                 [(0, 9), (3, 9), (5, 8), (7, 8)],
                 [(9, 11), (12, 10), (15, 9)]]
    elif kind == "top":
        paths = [[(0, 9), (4, 8), (7, 9), (10, 7), (15, 9)],
                 [(6, 0), (6, 3), (7, 5), (7, 9)],
                 [(10, 7), (11, 11), (13, 13)]]
    else:
        paths = [[(0, 9), (5, 10), (9, 8), (15, 9)],
                 [(6, 0), (5, 4), (5, 10)]]
    cells = vein_mask(paths, seed + 7)
    for (x, y), depth in cells.items():
        if not (0 <= x < size and 0 <= y < size):
            continue
        if depth == 0:
            core = CRACK[3] if h2(x, y, seed + 9) > 0.8 else CRACK[2]
            if 5 <= x <= 10 and 5 <= y <= 10 and kind == "side":
                core = mix(core, CRACK[4], 0.35)  # hottest where the veins cross
            put(im, x, y, mix(CRACK[1], core, glow))
        else:
            put(im, x, y, mix(STONE[0], CRACK[0], 0.6 * glow))
    return im


def write_frame(pack):
    out = os.path.join(pack, "assets", "minecraft", "textures", "block")
    # Four frames of a slow pulse: the cracks breathe.
    pulse = [0.55, 0.8, 1.0, 0.8]
    for kind, seed in (("side", 11), ("top", 23), ("bottom", 37)):
        strip = img(16, 16 * len(pulse))
        for i, g in enumerate(pulse):
            strip.paste(frame_face(seed, kind, g), (0, 16 * i))
        save(strip, os.path.join(out, f"reinforced_deepslate_{kind}.png"),
             {"animation": {"interpolate": True, "frametime": 16}})


# ---- the portal's surface ---------------------------------------------------------------------------

def periodic(x, y, t, period, seed):
    """Tileable noise on a torus, flowing with time t (0..1 loops)."""
    total = 0.0
    for k, (fx, fy, amp) in enumerate(((1, 2, 0.5), (2, 1, 0.3), (3, 2, 0.2), (1, 3, 0.25), (4, 3, 0.12))):
        ph = h2(k, 3, seed) * math.tau
        total += amp * math.sin(math.tau * (fx * x / period + fy * y / period) + ph + math.tau * t * (1 + k % 2))
    return total


def write_portal(assets):
    size, frames = 64, 16
    strip = img(size, size * frames)
    for f in range(frames):
        t = f / frames
        for y in range(size):
            for x in range(size):
                # Domain-warped flow, drifting upwards and curling: blood rising through the gate.
                wx = periodic(x, y, t, size, 5) * 6
                wy = periodic(y, x, t, size, 9) * 6
                v = periodic(x + wx, y + wy + t * size, t, size, 13)
                v = (v + 1.4) / 2.8
                vein = abs(periodic(x + wx * 1.5, y - wy * 1.5, t, size, 21))
                c = mix(BLOOD[0], BLOOD[2], v)
                if vein < 0.05:
                    c = mix(c, BLOOD[3], (1 - vein / 0.05) * 0.7)
                if v > 0.82:
                    c = mix(c, BLOOD[5], (v - 0.82) / 0.18 * 0.6)
                put(strip, x, f * size + y, c, 232)
    save(strip, os.path.join(assets, "textures", "block", "blood_portal.png"),
         {"animation": {"interpolate": True, "frametime": 3}})
    dump({
        "credit": "Bloodbath - tools/models/bloodlands.py",
        "textures": {"surface": f"{NS}:block/blood_portal", "particle": f"{NS}:block/blood_portal"},
        "elements": [{
            "from": [0, 0, 8], "to": [16, 16, 8], "shade": False, "light_emission": 15,
            "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#surface"},
                      "south": {"uv": [0, 0, 16, 16], "texture": "#surface"}}}],
    }, os.path.join(assets, "models", "block", "blood_portal.json"))
    dump({"model": {"type": "minecraft:model", "model": f"{NS}:block/blood_portal"}},
         os.path.join(assets, "items", "blood_portal.json"))


# ---- the Blood Anvil ---------------------------------------------------------------------------------

def write_anvil(assets):
    tex = os.path.join(assets, "textures", "block")
    # Black iron: hammered, scratched, worn bright at the edges.
    iron = img(32, 32)
    for y in range(32):
        for x in range(32):
            n = fbm(x * 0.18, y * 0.18, 41)
            c = mix(IRON[1], IRON[3], n)
            if h2(x, y, 42) > 0.94:
                c = mix(c, IRON[4], 0.6)  # hammer dents catching light
            if h2(x // 3, y, 43) > 0.985:
                c = IRON[5]  # a scratch
            if x in (0, 31) or y in (0, 31):
                c = mix(c, IRON[4], 0.5)
            put(iron, x, y, c)
    save(iron, os.path.join(tex, "blood_anvil_iron.png"))
    stone = img(32, 32)
    for y in range(32):
        for x in range(32):
            n = fbm(x * 0.25, y * 0.25, 51)
            c = mix(STONE[0], STONE[2], n)
            if (x // 8 + y // 8) % 2 == 0 and (x % 8 == 0 or y % 8 == 0):
                c = STONE[0]  # blocks of cut stone
            put(stone, x, y, c)
    save(stone, os.path.join(tex, "blood_anvil_stone.png"))
    # Glowing cracks on transparent: overlaid (outset a hair) on the iron.
    cracks = img(32, 32)
    cells = crack_mask(32, 32, 61, [(4, 0, 0, 1), (20, 31, 0, -1), (31, 12, -1, 0), (0, 22, 1, 0)], 18, branch=0.6)
    for (x, y), depth in cells.items():
        if depth == 0:
            put(cracks, x, y, CRACK[3] if h2(x, y, 62) > 0.6 else CRACK[2])
        else:
            put(cracks, x, y, CRACK[0], 170)
    save(cracks, os.path.join(tex, "blood_anvil_cracks.png"))
    # The groove on top: blood, slowly moving.
    frames = 8
    groove = img(16, 16 * frames)
    for f in range(frames):
        for y in range(16):
            for x in range(16):
                v = (math.sin((x + f * 2) * 0.8 + math.sin(y * 0.7) * 1.5) + 1) / 2
                c = mix(BLOOD[2], BLOOD[4], v * 0.8)
                if h2(x + f, y, 71) > 0.9:
                    c = BLOOD[5]
                put(groove, x, f * 16 + y, c)
    save(groove, os.path.join(tex, "blood_anvil_blood.png"), {"animation": {"interpolate": True, "frametime": 4}})

    def box(frm, to, texture, faces=("north", "south", "east", "west", "up", "down"), light=0, uv=None):
        el = {"from": frm, "to": to, "faces": {}}
        if light:
            el["light_emission"] = light
        for face in faces:
            if uv is not None:
                u = uv
            elif face in ("north", "south"):
                u = [frm[0] % 16, 16 - to[1] % 16 if to[1] % 16 else 0, (frm[0] % 16) + (to[0] - frm[0]), 16 - frm[1] % 16]
            elif face in ("east", "west"):
                u = [frm[2] % 16, 16 - to[1] % 16 if to[1] % 16 else 0, (frm[2] % 16) + (to[2] - frm[2]), 16 - frm[1] % 16]
            else:
                u = [frm[0] % 16, frm[2] % 16, (frm[0] % 16) + (to[0] - frm[0]), (frm[2] % 16) + (to[2] - frm[2])]
            u = [max(0, min(16, v)) for v in u]
            if u[0] == u[2]:
                u[2] = min(16, u[0] + 1)
            if u[1] == u[3]:
                u[3] = min(16, u[1] + 1)
            el["faces"][face] = {"uv": u, "texture": texture}
        return el

    e = 0.02
    elements = [
        box([0, 0, 1], [16, 2, 15], "#stone"),                    # the plinth
        box([2, 2, 3], [14, 5, 13], "#iron"),                     # the foot
        box([4.5, 5, 5], [11.5, 10, 11], "#iron"),                # the waist
        box([0, 10, 3], [16, 15, 13], "#iron"),                   # the face
        box([0.5, 15, 3.5], [15.5, 16, 12.5], "#iron"),           # the worn top
        box([-4, 11, 5], [0, 14.5, 11], "#iron"),                 # the horn
        box([-6, 12, 6.5], [-4, 13.5, 9.5], "#iron"),             # its tip
        box([16, 11, 4.5], [18, 15, 11.5], "#iron"),              # the heel
        # A groove of blood along the top, glowing.
        box([2, 16, 6.5], [14, 16 + e, 9.5], "#blood", faces=("up",), light=11, uv=[0, 0, 16, 4]),
        # Cracks, outset a hair, glowing through the iron.
        box([4.5, 5, 5 - e], [11.5, 10, 5 - e], "#cracks", faces=("north",), light=9, uv=[2, 4, 9, 9]),
        box([4.5, 5, 11 + e], [11.5, 10, 11 + e], "#cracks", faces=("south",), light=9, uv=[9, 12, 16, 17 - 1]),
        box([0, 10, 3 - e], [16, 15, 3 - e], "#cracks", faces=("north",), light=9, uv=[0, 0, 16, 5]),
        box([0, 10, 13 + e], [16, 15, 13 + e], "#cracks", faces=("south",), light=9, uv=[0, 8, 16, 13]),
        box([2, 2, 3 - e], [14, 5, 3 - e], "#cracks", faces=("north",), light=8, uv=[2, 11, 14, 14]),
        # Blood run down the face.
        box([6, 11.5, 3 - 2 * e], [6.6, 15, 3 - 2 * e], "#blood", faces=("north",), light=10, uv=[0, 0, 1, 6]),
        box([10.4, 12.5, 3 - 2 * e], [11, 15, 3 - 2 * e], "#blood", faces=("north",), light=10, uv=[4, 0, 5, 5]),
    ]
    model = {
        "credit": "Bloodbath - tools/models/bloodlands.py",
        "texture_size": [32, 32],
        "textures": {"iron": f"{NS}:block/blood_anvil_iron", "stone": f"{NS}:block/blood_anvil_stone",
                     "cracks": f"{NS}:block/blood_anvil_cracks", "blood": f"{NS}:block/blood_anvil_blood",
                     "particle": f"{NS}:block/blood_anvil_iron"},
        "elements": elements,
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0.5, 0, 0], "scale": [0.5, 0.5, 0.5]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
            "fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [0.45, 0.45, 0.45]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.35, 0.35, 0.35]},
            "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
        },
    }
    dump(model, os.path.join(assets, "models", "item", "blood_anvil.json"))
    dump({"model": {"type": "minecraft:model", "model": f"{NS}:item/blood_anvil"}}, os.path.join(assets, "items", "blood_anvil.json"))


# ---- the Blood Drop ---------------------------------------------------------------------------------

DROP_SHAPE = [
    "......##........",
    "......##........",
    ".....####.......",
    ".....####.......",
    "....######......",
    "....######......",
    "...########.....",
    "..##########....",
    "..##########....",
    ".############...",
    ".############...",
    ".############...",
    "..##########....",
    "..##########....",
    "...########.....",
    ".....####.......",
]


def drop_frame(shine, size=16):
    im = img(size, size)
    cells = {(x + 2, y) for y, row in enumerate(DROP_SHAPE) for x, ch in enumerate(row) if ch == "#"}
    cx, cy = 8.5, 10.0
    for (x, y) in cells:
        if not (0 <= x < size):
            continue
        edge = any((x + ox, y + oy) not in cells for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        d = math.hypot(x - cx, (y - cy) * 1.1) / 6.0
        c = mix(BLOOD[3], BLOOD[1], d)
        if edge:
            c = BLOOD[0] if x > cx or y > cy + 2 else BLOOD[2]
        put(im, x, y, c)
    # A highlight that slides round the bead, and a fixed pinpoint of light.
    hx = 6 + int(round(math.cos(shine) * 1.0))
    hy = 8 + int(round(math.sin(shine) * 1.5))
    for (x, y) in ((hx, hy), (hx, hy + 1), (hx + 1, hy)):
        if (x, y) in cells:
            put(im, x, y, BLOOD[5])
    put(im, 6, 11, rgb("#ffe6e8"))
    return im


def write_drop(assets):
    frames = 8
    strip = img(16, 16 * frames)
    for f in range(frames):
        strip.paste(drop_frame(f / frames * math.tau), (0, 16 * f))
    save(strip, os.path.join(assets, "textures", "item", "blood_drop.png"), {"animation": {"interpolate": True, "frametime": 5}})
    dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/blood_drop"}},
         os.path.join(assets, "models", "item", "blood_drop.json"))
    dump({"model": {"type": "minecraft:model", "model": f"{NS}:item/blood_drop"}}, os.path.join(assets, "items", "blood_drop.json"))
    return strip.crop((0, 0, 16, 16))


# ---- the anvil screen -------------------------------------------------------------------------------
GUI_WIDTH = 176
ROWS = 5
SLOT = lambda i: (7 + (i % 9) * 18, 17 + (i // 9) * 18)


def socket(im, i, style):
    """A slot's frame, drawn under the item. style: weapon, result, drop, small, pip."""
    sx, sy = SLOT(i)
    for y in range(18):
        for x in range(18):
            edge = x in (0, 17) or y in (0, 17)
            inner = x in (1, 16) or y in (1, 16)
            if style in ("weapon", "result", "drop"):
                c = CRACK[1] if edge else mix(VOID, STONE[0], 0.5) if not inner else STONE[0]
                if edge and (x, y) in ((0, 0), (17, 0), (0, 17), (17, 17)):
                    c = CRACK[3]
                if style == "drop" and not edge:
                    g = max(0.0, 1 - math.hypot(x - 8.5, y - 8.5) / 9.5)
                    c = mix(c, CRACK[0], g * 0.9)
            elif style == "pip":
                c = STONE[3] if edge else STONE[0]
            else:
                c = STONE[3] if edge else mix(VOID, STONE[1], 0.5)
            put(im, sx + x, sy + y, c)
    if style in ("weapon", "result", "drop"):
        # An outer ring of carved iron, and corner studs.
        for x in range(-2, 20):
            for y in (-2, 19):
                put(im, sx + x, sy + y, IRON[3] if y < 0 else IRON[1])
        for y in range(-2, 20):
            for x in (-2, 19):
                put(im, sx + x, sy + y, IRON[3] if x < 0 else IRON[1])
        for (x, y) in ((-2, -2), (19, -2), (-2, 19), (19, 19)):
            put(im, sx + x, sy + y, GOLD)


def write_gui(assets):
    height = 17 + ROWS * 18 + 4
    bg = img(GUI_WIDTH, height)
    for y in range(height):
        for x in range(GUI_WIDTH):
            n = fbm(x * 0.12, y * 0.12, 81)
            c = mix(VOID, STONE[1], 0.35 + n * 0.5)
            # Faint veins of old blood through the iron.
            v = abs(fbm(x * 0.05, y * 0.09, 82) - 0.5)
            if v < 0.02:
                c = mix(c, CRACK[0], 0.7)
            border = x < 2 or x >= GUI_WIDTH - 2 or y < 2 or y >= height - 2
            if border:
                c = CRACK[1] if (x + y) % 9 else CRACK[2]
            elif x in (2, GUI_WIDTH - 3) or y in (2, height - 3):
                c = IRON[3] if y == 2 or x == 2 else IRON[1]
            put(bg, x, y, c)
    # The rule under the title.
    for x in range(8, GUI_WIDTH - 8):
        put(bg, x, 15, CRACK[0])
    socket(bg, 11, "weapon")
    socket(bg, 15, "result")
    socket(bg, 22, "drop")
    socket(bg, 13, "small")
    socket(bg, 20, "small")
    socket(bg, 24, "small")
    # The button's trough: slots 29-33, one long inset.
    x0, y0 = SLOT(29)
    for y in range(-1, 19):
        for x in range(-1, 91):
            edge = x in (-1, 90) or y in (-1, 18)
            put(bg, x0 + x, y0 + y, IRON[3] if edge and (y == -1 or x == -1) else IRON[0] if edge else VOID)
    # The level pips' rail.
    for i in range(38, 43):
        socket(bg, i, "pip")
    px, py = SLOT(38)
    for x in range(0, 90):
        put(bg, px + x, py + 8, CRACK[0])
    # Labels.
    label = mix(CRACK[2], PALE, 0.25)
    for text, slot in (("WEAPON", 11), ("RESULT", 15)):
        sx, sy = SLOT(slot)
        draw_text(bg, sx + 9 - text_width(text) // 2, sy - 12, text, label, shadow=VOID)
    lx, ly = SLOT(36)
    draw_text(bg, lx + 3, ly + 5, "LEVEL", mix(CRACK[1], PALE, 0.2), shadow=VOID)
    font = os.path.join(assets, "textures", "font")
    save(bg, os.path.join(font, "blood_anvil.png"))
    return {"type": "bitmap", "file": f"{NS}:font/blood_anvil.png", "ascent": 13, "height": height, "chars": [""]}, bg


def gui_item(assets, name, texture, animation=None, frame_height=None):
    """A 16x16 menu picture: texture -> model -> item definition."""
    path = os.path.join(assets, "textures", "item", "gui", name + ".png")
    meta = None
    if animation is not None:
        meta = {"animation": animation}
    save(texture, path, meta)
    dump({"parent": "minecraft:item/generated", "gui_light": "front", "textures": {"layer0": f"{NS}:item/gui/{name}"}},
         os.path.join(assets, "models", "gui", name + ".json"))
    dump({"model": {"type": "minecraft:model", "model": f"{NS}:gui/{name}"}}, os.path.join(assets, "items", "gui", name + ".json"))


BUTTON_W, BUTTON_H = 88, 20
# The texture is 96x32 (sizes that keep the block atlas's mipmaps whole) with the 88x20 face in
# the middle; the model shows it at exactly one texel per menu pixel, so the lettering stays crisp.
TEX_W, TEX_H = 96, 32
PAD_X, PAD_Y = (TEX_W - BUTTON_W) // 2, (TEX_H - BUTTON_H) // 2


def button_texture(state, frame=0, frames=1):
    face = button_face(state, frame, frames)
    im = img(TEX_W, TEX_H)
    im.paste(face, (PAD_X, PAD_Y))
    return im


def button_face(state, frame=0, frames=1):
    im = img(BUTTON_W, BUTTON_H)
    t = frame / max(1, frames)
    plates = {
        "idle": (IRON[1], IRON[2], mix(PALE, IRON[4], 0.55)),
        "cannot": (IRON[0], IRON[1], IRON[4]),
        "need": (mix(IRON[1], CRACK[0], 0.3), mix(IRON[2], CRACK[0], 0.4), mix(CRACK[2], PALE, 0.2)),
        "ready": (CRACK[1], CRACK[2], rgb("#fff0f1")),
        "bleeding": (CRACK[0], CRACK[1], rgb("#ffd0d4")),
        "max": (mix(IRON[1], GOLD, 0.15), mix(IRON[2], GOLD, 0.25), GOLD),
    }
    low, high, ink = plates[state]
    pulse = 0.5 + 0.5 * math.sin(t * math.tau) if state == "ready" else 0.0
    for y in range(BUTTON_H):
        for x in range(BUTTON_W):
            v = y / (BUTTON_H - 1)
            c = mix(high, low, v)
            if state == "ready":
                c = mix(c, CRACK[3], pulse * 0.35 * (1 - v))
            if state == "bleeding":
                # Blood filling the button left to right, dripping at its edge.
                fill = t * BUTTON_W
                if x < fill:
                    c = mix(BLOOD[3], BLOOD[2], v)
                elif x < fill + 2 and y > BUTTON_H * 0.5 + h2(x, frame, 91) * 6:
                    c = BLOOD[4]
            edge = x in (0, BUTTON_W - 1) or y in (0, BUTTON_H - 1)
            if edge:
                corner = (x in (0, BUTTON_W - 1)) and (y in (0, BUTTON_H - 1))
                if corner:
                    continue
                c = shade(high, 1.5) if y == 0 or x == 0 else shade(low, 0.45)
            put(im, x, y, c)
    labels = {"idle": "BLEED WEAPON", "cannot": "CANNOT BLEED", "need": "NEED MORE BLOOD", "ready": "BLEED WEAPON",
              "bleeding": "BLEEDING...", "max": "MAXIMUM LEVEL"}
    text = labels[state]
    tight = text_width(text) > BUTTON_W - 8
    x = (BUTTON_W - text_width(text, tight)) // 2
    draw_text(im, x, 6, text, ink, shadow=shade(low, 0.4), tight=tight)
    if state == "cannot":
        for xx in range(x - 2, x + text_width(text, tight) + 2):
            put(im, xx, 9, IRON[4])
    return im


def write_buttons(assets):
    for state, frames, frametime in (("idle", 1, 0), ("cannot", 1, 0), ("need", 1, 0), ("ready", 8, 3), ("bleeding", 16, 2), ("max", 1, 0)):
        strip = img(TEX_W, TEX_H * frames)
        for f in range(frames):
            strip.paste(button_texture(state, f, frames), (0, TEX_H * f))
        name = "button_" + state
        path = os.path.join(assets, "textures", "item", "gui", name + ".png")
        meta = {"animation": {"interpolate": state == "ready", "frametime": frametime, "width": TEX_W, "height": TEX_H}} if frames > 1 else None
        save(strip, path, meta)
        # A flat panel 48x16 units, shown at 2x: 96x32 menu pixels centred on the middle slot, the face
        # filling the five slots of the trough.
        dump({
            "credit": "Bloodbath - tools/models/bloodlands.py",
            "gui_light": "front",
            "textures": {"face": f"{NS}:item/gui/{name}", "particle": f"{NS}:item/gui/{name}"},
            "elements": [{"from": [-16, 0, 8], "to": [32, 16, 8], "shade": False,
                          "faces": {"south": {"uv": [0, 0, 16, 16], "texture": "#face"},
                                    "north": {"uv": [0, 0, 16, 16], "texture": "#face"}}}],
            "display": {"gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [2, 2, 1]}},
        }, os.path.join(assets, "models", "gui", name + ".json"))
        dump({"model": {"type": "minecraft:model", "model": f"{NS}:gui/{name}"}}, os.path.join(assets, "items", "gui", name + ".json"))
    # The rest of the button's slots: nothing to draw (the face spans them).
    dump({"textures": {"particle": f"{NS}:item/gui/button_idle"}, "elements": []}, os.path.join(assets, "models", "gui", "blank.json"))
    dump({"model": {"type": "minecraft:model", "model": f"{NS}:gui/blank"}}, os.path.join(assets, "items", "gui", "blank.json"))


def write_gui_icons(assets, drop_icon):
    def canvas():
        return img(16, 16)

    # Level pips: an empty socket, a gem of blood, and the next one pulsing.
    def pip(fill, ring=None):
        im = canvas()
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if d <= 5.5:
                    if fill is None:
                        put(im, x, y, STONE[0] if d < 4.5 else STONE[3])
                    else:
                        c = mix(fill[0], fill[1], d / 5.5)
                        if d > 4.6:
                            c = shade(fill[1], 0.6)
                        put(im, x, y, c)
                if ring is not None and 5.5 < d <= 6.6:
                    put(im, x, y, ring[0], ring[1])
        if fill is not None:
            put(im, 5, 5, BLOOD[5])
            put(im, 6, 5, BLOOD[4])
        return im

    gui_item(assets, "pip_empty", pip(None))
    gui_item(assets, "pip_full", pip((BLOOD[4], BLOOD[1])))
    frames = 8
    strip = img(16, 16 * frames)
    for f in range(frames):
        a = int(90 + 165 * (0.5 + 0.5 * math.sin(f / frames * math.tau)))
        strip.paste(pip(None, (CRACK[3], a)), (0, 16 * f))
    gui_item(assets, "pip_next", strip, {"interpolate": True, "frametime": 3})

    # The vial: glass, filling with blood in eighths.
    for level in range(9):
        im = canvas()
        for y in range(16):
            for x in range(16):
                inside = 5 <= x <= 10 and 4 <= y <= 14 or 6 <= x <= 9 and 1 <= y <= 3
                edge = inside and not (6 <= x <= 9 and 5 <= y <= 13 or 7 <= x <= 8 and 1 <= y <= 3)
                if not inside:
                    continue
                if edge:
                    put(im, x, y, mix(PALE, IRON[4], 0.5), 200)
                    continue
                fill_top = 13 - level
                if y > fill_top and level > 0:
                    put(im, x, y, BLOOD[4] if y == fill_top + 1 else BLOOD[3] if x < 8 else BLOOD[2])
                else:
                    put(im, x, y, IRON[1], 90)
        if level == 8:
            put(im, 7, 1, BLOOD[5])
        gui_item(assets, f"meter_{level}", im)

    # Arrows: dim, and flowing red when the anvil is ready.
    def arrow(color, offset=0):
        im = canvas()
        for x in range(2, 12):
            for y in (7, 8):
                c = color if (x + offset) % 4 else shade(color, 1.4)
                put(im, x, y, c)
        for i in range(5):
            for y in range(7 - i, 9 + i):
                put(im, 12 - (4 - i) + 1, y, color)
        return im

    gui_item(assets, "arrow_idle", arrow(IRON[4]))
    strip = img(16, 16 * 4)
    for f in range(4):
        strip.paste(arrow(CRACK[2], f), (0, 16 * f))
    gui_item(assets, "arrow_ready", strip, {"interpolate": False, "frametime": 3})

    # Ghosts for the empty slots: a sword, a drop, a question of what will be.
    def ghost(points, color=(226, 184, 179), alpha=70):
        im = canvas()
        for (x, y) in points:
            put(im, x, y, color, alpha)
        return im

    sword = [(3 + i, 12 - i) for i in range(10)] + [(4 + i, 12 - i) for i in range(9)] + [(2, 10), (3, 11), (5, 13), (6, 14), (1, 14), (2, 13)]
    gui_item(assets, "weapon_ghost", ghost(sword))
    drop_pts = [(x, y) for y in range(16) for x in range(16) if drop_icon.getpixel((x, y))[3] > 0]
    gui_item(assets, "drop_ghost", ghost(drop_pts))
    q = [(6, 4), (7, 3), (8, 3), (9, 4), (9, 5), (8, 6), (7, 7), (7, 8), (7, 10)]
    gui_item(assets, "result_ghost", ghost(q + [(x, y) for (x, y) in sword if x > 9 or y > 11], alpha=60))
    gui_item(assets, "cost", drop_icon)
    gui_item(assets, "cost_none", ghost([(5 + i, 8) for i in range(6)]))
    # The header: the anvil itself.
    dump({"model": {"type": "minecraft:model", "model": f"{NS}:item/blood_anvil"}}, os.path.join(assets, "items", "gui", "header.json"))


# ---- vanilla item definitions that swap in the Bloodlands models ---------------------------------------

def write_overrides(pack):
    out = os.path.join(pack, "assets", "minecraft", "items")

    def select(case, model, fallback):
        return {"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                          "cases": [{"when": f"bloodbath:{case}", "model": {"type": "minecraft:model", "model": model}}],
                          "fallback": fallback}}

    dump(select("blood_anvil", f"{NS}:item/blood_anvil", {"type": "minecraft:model", "model": "minecraft:block/anvil"}),
         os.path.join(out, "anvil.json"))
    dump(select("blood_drop", f"{NS}:item/blood_drop", {
        "type": "minecraft:model", "model": "minecraft:item/firework_star",
        "tints": [{"type": "minecraft:constant", "value": -1}, {"type": "minecraft:firework", "default": -7697782}]}),
         os.path.join(out, "firework_star.json"))
    dump(select("blood_portal", f"{NS}:block/blood_portal", {"type": "minecraft:model", "model": "minecraft:item/red_stained_glass_pane"}),
         os.path.join(out, "red_stained_glass_pane.json"))


def write(pack):
    """Everything above, into the pack. Returns the anvil screen's font provider for generate.py's gui font."""
    assets = os.path.join(pack, "assets", NS)
    write_frame(pack)
    write_portal(assets)
    write_anvil(assets)
    drop = write_drop(assets)
    provider, _ = write_gui(assets)
    write_buttons(assets)
    write_gui_icons(assets, drop)
    write_overrides(pack)
    return provider
