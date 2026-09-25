"""The Blood Knight's armour, as players wear it: the boss's own look, cut down to a set a player can wear.

Black iron plate lacquered in blood red, a closed great helm with a slit of burning eyes and a
fanged mouth guard, a Blood Core set into the breastplate, a glow in each knee. The palette and
the shapes come from the boss (tools/models/assets/blood_knight.gltf) so the set reads as his.

What this writes into the pack:
  - models/item/blood_knight_helm.json   a real 3D helm (worn on the head when the item has its
                                         own id: an equippable with no equipment asset makes the
                                         game draw the item's model there), plus its texture
  - textures/entity/equipment/...        the worn layers for the cuirass, greaves and sabatons
                                         (and the helm, for servers on the netherite fallback)
  - textures/item/blood_knight_*.png     inventory sprites for the other three pieces
And, outside the pack, Blockbench projects of the helm and of both worn layers
(tools/blockbench/*.bbmodel), so the art can be opened, repainted in 3D and exported by hand.

Everything is drawn pixel by pixel: shapes are char grids or explicit coordinates, shading is
by rule (light from the top left, a highlight on every upper edge, a shadow under every plate),
never noise.
"""
import base64
import io
import json
import os
import uuid

from PIL import Image

NS = "unchartedsmp"

PAL = {
    ".": None,
    "K": (11, 8, 9),           # outline / the dark inside a slit
    "1": (24, 19, 22),         # iron, shadow
    "2": (38, 31, 35),         # iron
    "3": (58, 49, 54),         # iron, lit
    "4": (92, 80, 86),         # iron, bevel highlight
    "5": (150, 138, 142),      # iron, glint
    "a": (40, 5, 9),           # lacquer, deepest
    "b": (70, 9, 15),          # lacquer, shadow
    "c": (104, 14, 23),        # lacquer
    "d": (140, 20, 31),        # lacquer, lit
    "e": (178, 32, 44),        # lacquer, highlight
    "f": (214, 70, 78),        # lacquer, specular
    "g": (224, 32, 46),        # glow
    "h": (255, 70, 82),        # glow, bright
    "j": (255, 190, 195),      # glow, white-hot
    "B": (232, 222, 206),      # bone
    "C": (186, 172, 154),      # bone, shade
    "D": (120, 108, 96),       # bone, deep
    "w": (120, 10, 18),        # wet blood
    "x": (58, 4, 8),           # dried blood
}


def rgba(key):
    c = PAL[key]
    return (0, 0, 0, 0) if c is None else c + (255,)


class Canvas:
    def __init__(self, w, h):
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0))

    def put(self, x, y, key):
        if 0 <= x < self.img.width and 0 <= y < self.img.height and key is not None:
            self.img.putpixel((x, y), rgba(key))

    def get(self, x, y):
        return self.img.getpixel((x, y))

    def rect(self, x, y, w, h, key):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.put(xx, yy, key)

    def grid(self, x, y, rows):
        """Hand-drawn pixels: one character per pixel, '.' left as it is."""
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch != ".":
                    self.put(x + i, y + j, ch)

    def lacquer(self, x, y, w, h, top=True, bottom=True, bright=0):
        """A lacquered plate: lit along the top, darker toward the bottom and the right edge."""
        for j in range(h):
            t = j / max(1, h - 1)
            for i in range(w):
                s = i / max(1, w - 1)
                v = 1.0 - 0.75 * t - 0.35 * s + bright * 0.3
                key = "e" if v > 0.95 else "d" if v > 0.62 else "c" if v > 0.3 else "b"
                self.put(x + i, y + j, key)
        if top:
            for i in range(w):
                self.put(x + i, y, "f" if i < w // 3 else "e")
        if bottom:
            for i in range(w):
                self.put(x + i, y + h - 1, "a")

    def plate(self, x, y, w, h, shine=True, rim=True):
        """A calm lacquered plate: lit along the top, shadowed at the bottom and down the right,
        one streak of shine a third of the way in. Big surfaces stay quiet; detail goes on top."""
        for j in range(h):
            t = j / max(1, h - 1)
            for i in range(w):
                key = "d" if t < 0.3 else "c" if t < 0.8 else "b"
                if i == w - 1 and j > 0:
                    key = "b" if key != "b" else "a"
                self.put(x + i, y + j, key)
        if rim:
            self.rect(x, y, w, 1, "e")
            if h > 2:
                self.rect(x, y + h - 1, w, 1, "a")
        if shine and w >= 4 and h >= 4:
            sx = x + max(1, w // 3)
            for j in range(1, max(2, h // 2)):
                self.put(sx, y + j, "e")
            self.put(sx, y + 1, "f")

    def iron(self, x, y, w, h, top=True, bottom=True):
        for j in range(h):
            for i in range(w):
                self.put(x + i, y + j, "3" if j < h / 2 and i < w * 0.6 else "2")
        if top:
            for i in range(w):
                self.put(x + i, y, "4")
        if bottom and h > 1:
            for i in range(w):
                self.put(x + i, y + h - 1, "1")

    def rivet(self, x, y):
        self.put(x, y, "5")
        self.put(x + 1, y + 1, "1")

    def drip(self, x, y, length, wet=True):
        """Blood running down from (x, y): a thin line that swells into a bead."""
        for j in range(length):
            self.put(x, y + j, "w" if wet and j < length - 1 else "x")
        self.put(x, y + length, "x")
        if wet:
            self.put(x, y, "e")

    def scratch(self, pts):
        """Paint chipped off the lacquer, showing the iron: an explicit polyline of pixels."""
        for (x, y) in pts:
            self.put(x, y, "4")


# =============================================================================================
# The helm: a 3D model, 16 units = the 10 px cube a helmet fills on the head.
# =============================================================================================
# A great helm: a lacquered bowl with a stepped crown, a black iron visor with a slit for the
# eyes (which burn), breaths cut either side of a red chevron, fangs hanging from the chin like
# the Knight's own mask, and a swept fin crest. Every face has its own rectangle in a 64x64
# atlas (1 texel per model unit), painted as seen from outside, upright.

HELM_ATLAS = 64


def helm_elements():
    """(name, from, to, rotation or None, {face: atlas rect (x, y, w, h)}, extra)."""
    E = []
    E.append(("shell", [1, 1, 1], [15, 14, 15], None,
              {"north": (0, 0, 14, 13), "south": (14, 0, 14, 13), "east": (28, 0, 14, 13),
               "west": (42, 0, 14, 13), "up": (0, 13, 14, 14), "down": (14, 13, 14, 14)}, {}))
    E.append(("crown", [2, 14, 2], [14, 16, 14], None,
              {"up": (28, 13, 12, 12), "north": (40, 13, 12, 2), "south": (40, 15, 12, 2),
               "east": (40, 17, 12, 2), "west": (40, 19, 12, 2)}, {}))
    E.append(("visor", [1.5, 2, 0], [14.5, 12, 1], None,
              {"north": (0, 27, 13, 10), "up": (13, 27, 13, 1), "down": (13, 28, 13, 1),
               "east": (26, 27, 1, 10), "west": (27, 27, 1, 10)}, {}))
    E.append(("brow", [0.5, 11, -0.5], [15.5, 13, 1], None,
              {"north": (28, 27, 15, 2), "up": (28, 29, 15, 2), "down": (28, 31, 15, 2),
               "east": (43, 27, 2, 2), "west": (45, 27, 2, 2)}, {}))
    E.append(("nasal", [7.5, 7, -0.3], [8.5, 11, 0], None,
              {"north": (28, 33, 1, 4), "east": (29, 33, 1, 4), "west": (30, 33, 1, 4),
               "down": (31, 33, 1, 1)}, {}))
    E.append(("eyes", [2.5, 8, -0.05], [13.5, 10, -0.05], None, {"north": (0, 39, 11, 2)}, {"light_emission": 15}))
    E.append(("brow_gem", [7, 11.5, -0.7], [9, 12.5, -0.5], None, {"north": (11, 39, 2, 1)}, {"light_emission": 12}))
    E.append(("crest_fin", [8, 15.5, 2], [8, 22.5, 15], None,
              {"east": (0, 41, 13, 7), "west": (14, 41, 13, 7)}, {}))
    E.append(("crest_ridge", [7, 15.5, 3], [9, 16.5, 14], None,
              {"up": (28, 41, 2, 11), "east": (31, 41, 11, 1), "west": (31, 43, 11, 1),
               "north": (43, 41, 2, 1), "south": (46, 41, 2, 1)}, {}))
    E.append(("neck_guard", [1, 0.5, 12.5], [15, 3.5, 16], None,
              {"south": (0, 48, 14, 3), "east": (14, 48, 4, 3), "west": (18, 48, 4, 3),
               "down": (22, 48, 14, 4), "up": (36, 48, 14, 4)}, {}))
    ear = {"west": (50, 48, 2, 2), "north": (52, 48, 1, 2), "south": (53, 48, 1, 2), "up": (54, 48, 1, 2), "down": (55, 48, 1, 2)}
    E.append(("ear_right", [0.4, 5, 6], [1, 7, 8], None, ear, {}))
    E.append(("ear_left", [15, 5, 6], [15.6, 7, 8], None,
              {("east" if k == "west" else k): v for k, v in ear.items()}, {"mirror": True}))
    return E


def paint_helm():
    c = Canvas(HELM_ATLAS, HELM_ATLAS)

    # The bowl. Rows 1-2 carry the brow band all the way round; the visor hides most of the front.
    for x0, band_rivets in ((0, ()), (14, (1, 5, 8, 12)), (28, (2, 6, 10)), (42, (3, 7, 11))):
        c.plate(x0, 0, 14, 13)
        c.rect(x0, 1, 14, 1, "4")
        c.rect(x0, 2, 14, 1, "2")
        for r in band_rivets:
            c.put(x0 + r, 2, "5")
    for x0 in (0, 14, 28, 42):
        for xx in range(x0, x0 + 14):
            if c.get(xx, 2)[:3] == PAL["5"]:
                c.put(xx, 2, "4")      # rivets on the band: a glint, not a stud
    # Back: the crest's ridge runs down the spine to the neck guard.
    c.rect(14 + 6, 3, 2, 7, "2")
    c.rect(14 + 6, 3, 1, 7, "4")
    c.drip(14 + 3, 3, 3)
    # Sides: a strap behind the ear, a drip from the band (east is seen with the back on the left).
    c.rect(28 + 2, 3, 1, 10, "2")
    c.drip(28 + 9, 3, 2)
    c.rect(42 + 11, 3, 1, 10, "2")
    c.drip(42 + 4, 3, 2)
    c.scratch([(42 + 8, 8), (42 + 9, 9)])
    # Top of the bowl: only a rim shows around the crown.
    c.rect(0, 13, 14, 14, "c")
    c.rect(0, 13, 14, 1, "e")
    c.rect(14, 13, 14, 14, "1")
    # The crown: lacquer, lit from the front left, a line of shine along the front.
    for j in range(12):
        for i in range(12):
            c.put(28 + i, 13 + j, "d" if i + j < 9 else "c" if i + j < 17 else "b")
    c.rect(28, 13, 12, 1, "e")
    c.rect(28 + 2, 13 + 1, 4, 1, "f")
    for k, yy in enumerate((13, 15, 17, 19)):
        c.rect(40, yy, 12, 1, "e" if k != 2 else "d")
        c.rect(40, yy + 1, 12, 1, "c" if k != 2 else "b")

    # The visor: black iron. The eye slit either side of the nasal bar; below it the Knight's
    # grin, a black mouth with long fangs hanging into it and two rising to meet them.
    c.grid(0, 27, [
        "4444444444444",
        "3333333333333",
        "2KKKKK3KKKKK1",
        "2KKKKK3KKKKK1",
        "3444444444443",
        "2KKBKKBKKBKK1",
        "2KKCKCKCKCKK1",
        "22222B2B22221",
        "2323232323231",
        "1111111111111",
    ])
    c.rect(13, 27, 13, 1, "4")
    c.rect(13, 28, 13, 1, "1")
    c.rect(26, 27, 1, 10, "3")
    c.rect(27, 27, 1, 10, "2")
    # The brow band: black iron, four rivets; a blood gem sits over the nasal bar.
    c.grid(28, 27, ["444444444444444", "252522K3K225252"])
    c.rect(28, 29, 15, 2, "3")
    c.rect(28, 29, 15, 1, "4")
    c.rect(28, 31, 15, 2, "1")
    c.grid(43, 27, ["44", "21"])
    c.grid(45, 27, ["44", "21"])
    c.grid(28, 33, ["4", "4", "4", "3"])
    c.grid(29, 33, ["3"] * 4)
    c.grid(30, 33, ["2"] * 4)
    c.put(31, 33, "1")
    # The eyes (emissive; the rest of the strip is clear) and the brow gem.
    c.grid(0, 39, [
        ".gjh...hjg.",
        "..gh...hg..",
    ])
    c.grid(11, 39, ["hj"])
    # The fin crest: a blade swept back, a bright leading edge, an iron foot on the ridge.
    fin = [
        "..........fee",
        "........ffedd",
        "......feeddc.",
        "....ffeddcc..",
        ".ffeeddccb...",
        "feeddccbba...",
        "4333332222111",
    ]
    c.grid(14, 41, fin)                      # west: front on the left
    c.grid(0, 41, [r[::-1] for r in fin])    # east: back on the left
    c.rect(28, 41, 2, 11, "3")
    c.rect(28, 41, 1, 11, "4")
    c.rect(31, 41, 11, 1, "4")
    c.rect(31, 43, 11, 1, "4")
    c.grid(43, 41, ["43"])
    c.grid(46, 41, ["21"])
    # The neck guard: one lacquered lame flaring over the nape.
    c.grid(0, 48, ["44444444444444", "eddddddddddddc", "aaaaaaaaaaaaaa"])
    c.grid(14, 48, ["4444", "dddc", "aaaa"])
    c.grid(18, 48, ["4444", "dddc", "aaaa"])
    c.rect(22, 48, 14, 4, "1")
    c.rect(36, 48, 14, 4, "c")
    # The ear bosses: an iron stud with a spark of blood in it.
    c.grid(50, 48, ["4g", "31"])
    c.grid(52, 48, ["4", "2"])
    c.grid(53, 48, ["4", "2"])
    c.grid(54, 48, ["4", "4"])
    c.grid(55, 48, ["1", "1"])
    return c.img


HELM_DISPLAY = {
    "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "gui": {"rotation": [18, 200, 0], "translation": [0, -1.25, 0], "scale": [0.56, 0.56, 0.56]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.28, 0.28, 0.28]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, -1, 0], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_righthand": {"rotation": [75, 225, 0], "translation": [0, 1.5, 1.5], "scale": [0.36, 0.36, 0.36]},
    "thirdperson_lefthand": {"rotation": [75, 225, 0], "translation": [0, 1.5, 1.5], "scale": [0.36, 0.36, 0.36]},
    "firstperson_righthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.38, 0.38, 0.38]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.38, 0.38, 0.38]},
}


def _uv(rect, mirror=False):
    x, y, w, h = rect
    s = 16.0 / HELM_ATLAS
    u = [x * s, y * s, (x + w) * s, (y + h) * s]
    return [u[2], u[1], u[0], u[3]] if mirror else u


def helm_model():
    elements = []
    for name, frm, to, rot, faces, extra in helm_elements():
        el = {"name": name, "from": frm, "to": to, "faces": {}}
        if rot:
            el["rotation"] = rot
        if extra.get("light_emission"):
            el["light_emission"] = extra["light_emission"]
            el["shade"] = False
        for face, rect in faces.items():
            el["faces"][face] = {"uv": _uv(rect, extra.get("mirror", False)), "texture": "#helm"}
        elements.append(el)
    return {
        "credit": "Bloodbath - tools/models/armor.py (Blockbench project: tools/blockbench/blood_knight_helm.bbmodel)",
        "texture_size": [HELM_ATLAS, HELM_ATLAS],
        "textures": {"helm": f"{NS}:item/blood_knight_helm_model", "particle": f"{NS}:item/blood_knight_helm_model"},
        "elements": elements,
        "display": HELM_DISPLAY,
    }


# =============================================================================================
# The worn layers: the vanilla humanoid armour layout (64x32 units) at 2 texels per unit.
# =============================================================================================
S = 2  # texels per unit


class Layer(Canvas):
    def __init__(self):
        super().__init__(64 * S, 32 * S)

    def at(self, ux, uy):
        return ux * S, uy * S


def box_faces(u, v, w, h, d):
    """Texel rects of a vanilla box-UV cube at (u, v) sized w x h x d units: {face: (x, y, w, h)}."""
    return {
        "up": ((u + d) * S, v * S, w * S, d * S),
        "down": ((u + d + w) * S, v * S, w * S, d * S),
        "right": (u * S, (v + d) * S, d * S, h * S),              # the wearer's right side (outer, for a right limb)
        "front": ((u + d) * S, (v + d) * S, w * S, h * S),
        "left": ((u + d + w) * S, (v + d) * S, d * S, h * S),
        "back": ((u + d + w + d) * S, (v + d) * S, w * S, h * S),
    }


HEAD = box_faces(0, 0, 8, 8, 8)
BODY = box_faces(16, 16, 8, 12, 4)
ARM = box_faces(40, 16, 4, 12, 4)
LEG = box_faces(0, 16, 4, 12, 4)


def paint_layer1():
    """Helm (fallback), cuirass (body and arms) and sabatons (the lower legs)."""
    L = Layer()

    # --- helm, flattened onto the head box (only drawn on the netherite fallback): the 3D helm's
    # visor and eyes on its front, its bowl and brow band round the rest.
    x, y, w, h = HEAD["front"]
    L.plate(x, y, w, h)
    L.grid(x, y + 3, [
        "4444444444444444",
        "2525252K35252522",
        "2KKKKKKK3KKKKKK1",
        "2KgjhKKK3KKhjgK1",
        "3444444434444443",
        "22K2K22d3d22K2K1",
        "22K2K22cdc22K2K1",
        "22K2K222c222K2K1",
        "2222222222222221",
        "1CB1BC1111CB1BC1",
        ".C...D....D...C.",
    ])
    L.rect(x, y + 13, w, 1, "1")
    for face in ("right", "left", "back"):
        fx, fy, fw, fh = HEAD[face]
        L.plate(fx, fy, fw, fh)
        L.rect(fx, fy + 3, fw, 1, "4")
        L.rect(fx, fy + 4, fw, 1, "2")
    fx, fy, fw, fh = HEAD["up"]
    L.plate(fx, fy, fw, fh, shine=False)
    L.rect(fx + 7, fy, 2, fh, "3")
    L.rect(fx + 7, fy, 1, fh, "4")

    # --- cuirass, front (16x24): an iron gorget, a lacquered breastplate split by an iron keel with
    # the Blood Core set in it, an iron waist band, two fauld lames.
    x, y, w, h = BODY["front"]
    L.iron(x, y, w, 3)
    L.rivet(x + 2, y + 1)
    L.rivet(x + 12, y + 1)
    L.plate(x, y + 3, 8, 11)
    L.plate(x + 8, y + 3, 8, 11, shine=False)
    L.rect(x + 7, y + 3, 2, 11, "2")
    L.rect(x + 7, y + 3, 1, 11, "4")
    L.grid(x + 4, y + 6, [
        "..4KK4..",
        ".4Kghe4.",
        "4KhjjhK1",
        "4KghhgK1",
        ".1KggK1.",
        "..1KK1..",
    ])
    L.drip(x + 8, y + 12, 2)
    L.scratch([(x + 2, y + 9), (x + 3, y + 10)])
    L.grid(x, y + 14, ["4444444444444444", "2222222552222222"])
    L.plate(x, y + 16, w, 4)
    L.plate(x, y + 20, w, 4, shine=False)

    # --- cuirass, back: the backplate with the spine down its middle, the same band and lames.
    x, y, w, h = BODY["back"]
    L.iron(x, y, w, 3)
    L.plate(x, y + 3, 8, 11, shine=False)
    L.plate(x + 8, y + 3, 8, 11)
    L.rect(x + 7, y + 3, 2, 11, "2")
    L.rect(x + 8, y + 3, 1, 11, "4")
    L.drip(x + 4, y + 4, 4, wet=False)
    L.grid(x, y + 14, ["4444444444444444", "2222222222222222"])
    L.plate(x, y + 16, w, 4, shine=False)
    L.plate(x, y + 20, w, 4, shine=False)

    # --- cuirass, sides: bare iron where the plates meet, a strap with its buckle.
    for face in ("right", "left"):
        x, y, w, h = BODY[face]
        L.iron(x, y, w, 16)
        L.rect(x + 3, y + 3, 2, 11, "1")
        L.grid(x + 2, y + 6, ["4554", "4..4", "4114"])
        L.plate(x, y + 16, w, 4, shine=False)
        L.plate(x, y + 20, w, 4, shine=False)
    x, y, w, h = BODY["up"]
    L.iron(x, y, w, h, bottom=False)
    x, y, w, h = BODY["down"]
    L.rect(x, y, w, h, "1")

    # --- the arms: one big pauldron, the black upper arm, an iron couter, a vambrace with a
    # lacquered panel, the gauntlet's cuff.
    for face in ("right", "front", "back", "left"):
        x, y, w, h = ARM[face]
        inner = face == "left"
        if inner:
            L.iron(x, y, w, 7)
        else:
            L.plate(x, y, w, 7, shine=face == "right")
        L.grid(x, y + 7, ["4" * w, "1" * w])
        L.rect(x, y + 9, w, 1, "2")
        L.grid(x, y + 10, ["4" * w, "3" * w])
        if face == "right":
            L.grid(x + 2, y + 2, ["45", "31"])      # the pauldron's boss
            L.drip(x + 5, y + 7, 2)
            L.put(x + w // 2, y + 11, "g")          # a spark in the couter
        L.iron(x, y + 12, w, 9)
        if not inner:
            L.plate(x + 2, y + 13, 4, 7, shine=False, rim=False)
        L.grid(x, y + 21, ["4" * w, ("d" if not inner else "3") * w, "1" * w])
    x, y, w, h = ARM["up"]
    L.plate(x, y, w, h, shine=False)
    L.rect(x, y + h - 1, w, 1, "1")
    x, y, w, h = ARM["down"]
    L.rect(x, y, w, h, "2")

    # --- sabatons: the lower third of each leg box; the greaves show above.
    for face in ("right", "front", "back", "left"):
        x, y, w, h = LEG[face]
        L.grid(x, y + 15, ["4" * w, "2" * w])       # the cuff
        L.plate(x, y + 17, w, 4, shine=face == "front")
        L.iron(x, y + 21, w, 2)                     # the foot
        L.rect(x, y + 23, w, 1, "x")                # the sole's edge, soaked
        if face == "front":
            L.put(x + w // 2, y + 21, "g")
        if face == "right":
            L.rivet(x + 3, y + 15)
    x, y, w, h = LEG["down"]
    L.rect(x, y, w, h, "x")
    L.rect(x + 1, y + 1, w - 2, h - 2, "w")
    return L.img


def paint_layer2():
    """Greaves on the legs, and a belt with tassets on the body."""
    L = Layer()
    for face in ("right", "front", "back", "left"):
        x, y, w, h = LEG[face]
        inner = face == "left"
        back = face == "back"
        # The cuisse over the thigh.
        if back:
            L.rect(x, y, w, 9, "b")
            L.rect(x, y + 4, w, 1, "a")
        elif inner:
            L.iron(x, y, w, 9)
        else:
            L.plate(x, y, w, 9, shine=face == "front")
        # The poleyn: an iron knee cup, the glow in its heart.
        L.iron(x, y + 9, w, 4)
        if face == "front":
            L.grid(x + w // 2 - 2, y + 9, [".44.", "4hj3", "4gh1", ".11."])
        elif face == "right":
            L.rivet(x + 3, y + 10)
        # The greave down the shin (the sabatons cover its lower half).
        if back:
            L.rect(x, y + 13, w, 11, "2")
        else:
            L.plate(x, y + 13, w, 11, shine=False)
            if face == "front":
                L.rect(x + w // 2, y + 13, 1, 11, "2")
                L.rect(x + w // 2 - 1, y + 13, 1, 11, "4")
    x, y, w, h = LEG["up"]
    L.rect(x, y, w, h, "2")

    # The belt and its tassets, on the lower part of the body box.
    for face in ("front", "back", "right", "left"):
        x, y, w, h = BODY[face]
        L.grid(x, y + 14, ["4" * w, "1" * w, "2" * w])
        if face in ("front", "back"):
            L.plate(x, y + 17, w // 2 - 1, 7, shine=face == "front")
            L.plate(x + w // 2 + 1, y + 17, w // 2 - 1, 7, shine=False)
            L.rect(x + w // 2 - 1, y + 17, 2, 7, "a")
            if face == "front":
                L.grid(x + w // 2 - 2, y + 14, ["4554", "5KK5", "4554"])   # the buckle
        else:
            L.plate(x, y + 17, w, 7, shine=False)
    return L.img


# =============================================================================================
# Inventory sprites (16x16), drawn by hand in the same palette.
# =============================================================================================
# Each sprite is a map of regions; one shading rule colours every region the same way (lit along
# its top edge, shadowed along its bottom, outlined where it meets the air), so the three read as
# one set and as the pieces on the body: pauldrons, gorget, keel and core; knees that glow.
ICONS = {
    "blood_knight_cuirass": [
        "................",
        ".pppp......pppp.",
        "pppppgg..ggppppp",
        "pppppggggggppppp",
        "ppppccckkCCCpppp",
        ".pppccckkCCCppp.",
        "..pcccooooCCCp..",
        "...cccooooCCC...",
        "...ccckookCCC...",
        "...cccckkCCCC...",
        "...wwwwwwwwww...",
        "...ffffffffff...",
        "...FFFFFFFFFF...",
        "....ffffffff....",
        "................",
        "................",
    ],
    "blood_knight_greaves": [
        "................",
        "..wwwwwwwwwwww..",
        "..wwwwwwwwwwww..",
        "..lllll..RRRRR..",
        "..lllll..RRRRR..",
        "..lllll..RRRRR..",
        "..lllll..RRRRR..",
        "..nnnnn..NNNNN..",
        "..nnqnn..NNqNN..",
        "..nnnnn..NNNNN..",
        "..sssss..SSSSS..",
        "..sssss..SSSSS..",
        "..sssss..SSSSS..",
        "..sssss..SSSSS..",
        "................",
        "................",
    ],
    "blood_knight_sabatons": [
        "................",
        "................",
        "................",
        "..iiii....iiii..",
        "..iiii....iiii..",
        "..bbbb....BBBB..",
        "..bbbb....BBBB..",
        "..bbbb....BBBB..",
        "ttbbbb..TTBBBB..",
        "ttbbbb..TTBBBB..",
        "tttbbb..TTTBBB..",
        "zzzzzz..zzzzzz..",
        "................",
        "................",
        "................",
        "................",
    ],
}
# region -> (base, lit top edge, shadowed bottom edge)
REGIONS = {
    "p": ("d", "e", "b"), "c": ("d", "e", "b"), "C": ("c", "d", "b"), "l": ("d", "e", "b"), "R": ("c", "d", "b"),
    "f": ("d", "e", "b"), "F": ("c", "d", "a"), "s": ("c", "d", "b"), "S": ("b", "c", "a"),
    "b": ("d", "e", "b"), "B": ("c", "d", "b"), "t": ("e", "f", "c"), "T": ("d", "e", "b"),
    "g": ("2", "4", "1"), "k": ("3", "4", "2"), "w": ("2", "4", "1"), "n": ("3", "4", "2"), "N": ("2", "3", "1"),
    "i": ("3", "4", "2"), "o": ("h", "j", "g"), "q": ("h", "j", "g"), "z": ("x", "w", "x"),
}


def icon(name):
    rows = ICONS[name]
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), name
    at = lambda x, y: rows[y][x] if 0 <= x < 16 and 0 <= y < 16 else "."
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            r = at(x, y)
            if r == ".":
                continue
            if "." in (at(x - 1, y), at(x + 1, y), at(x, y - 1), at(x, y + 1)):
                c.put(x, y, "K")
                continue
            base, lit, dark = REGIONS[r]
            key = lit if at(x, y - 1) != r or at(x - 1, y) == "." else dark if at(x, y + 1) != r else base
            c.put(x, y, key)
    return c.img


# =============================================================================================
# Blockbench projects
# =============================================================================================

def _png_data(img):
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def _uid(*parts):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "bloodbath:" + ":".join(parts)))


def _texture_entry(name, img, uv_w, uv_h, index):
    return {
        "path": "", "name": name, "folder": "", "namespace": NS, "id": str(index), "group": "",
        "width": img.width, "height": img.height, "uv_width": uv_w, "uv_height": uv_h,
        "particle": index == 0, "use_as_default": False, "layers_enabled": False, "sync_to_project": "",
        "render_mode": "default", "render_sides": "auto", "pbr_channel": "color", "frame_time": 1,
        "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False, "visible": True,
        "internal": True, "saved": False, "uuid": _uid("tex", name), "source": _png_data(img),
    }


def helm_bbmodel(texture):
    elements, children = [], []
    for name, frm, to, rot, faces, extra in helm_elements():
        uid = _uid("helm", name)
        faces_bb = {}
        for face in ("north", "east", "south", "west", "up", "down"):
            if face in faces:
                x, y, w, h = faces[face]
                uv = [x + w, y, x, y + h] if extra.get("mirror") else [x, y, x + w, y + h]
                faces_bb[face] = {"uv": uv, "texture": 0}
            else:
                faces_bb[face] = {"uv": [0, 0, 0, 0], "texture": None}
        el = {"name": name, "box_uv": False, "rescale": False, "locked": False, "light_emission": extra.get("light_emission", 0),
              "render_order": "default", "allow_mirror_modeling": True, "from": frm, "to": to, "autouv": 0,
              "color": 1 if name.startswith("crest") else 0, "origin": (rot or {}).get("origin", [8, 8, 8]),
              "faces": faces_bb, "type": "cube", "uuid": uid, "shade": not extra.get("light_emission")}
        if rot:
            r = [0, 0, 0]
            r["xyz".index(rot["axis"])] = rot["angle"]
            el["rotation"] = r
        elements.append(el)
        children.append(uid)
    return {
        "meta": {"format_version": "4.10", "model_format": "java_block", "box_uv": False},
        "name": "blood_knight_helm", "parent": "", "ambientocclusion": True, "front_gui_light": False,
        "visible_box": [1, 1, 0], "variable_placeholders": "", "variable_placeholder_buttons": [],
        "unhandled_root_fields": {}, "resolution": {"width": HELM_ATLAS, "height": HELM_ATLAS},
        "elements": elements,
        "outliner": [{"name": "blood_knight_helm", "origin": [8, 8, 8], "color": 0, "uuid": _uid("helm", "group"),
                      "export": True, "mirror_uv": False, "isOpen": True, "locked": False, "visibility": True,
                      "autouv": 0, "children": children}],
        "textures": [_texture_entry("blood_knight_helm_model.png", texture, HELM_ATLAS, HELM_ATLAS, 0)],
        "display": HELM_DISPLAY,
    }


# The player model's boxes, in Blockbench's entity space (y up from the feet), with vanilla
# armour's inflation: layer 1 at 1.0, layer 2 (leggings) at 0.5.
PLAYER_BOXES = [
    ("head", [-4, 24, -4], [4, 32, 4], [0, 0], [0, 24, 0], False, 0),
    ("body", [-4, 12, -2], [4, 24, 2], [16, 16], [0, 24, 0], False, 0),
    ("right_arm", [-8, 12, -2], [-4, 24, 2], [40, 16], [-5, 22, 0], False, 0),
    ("left_arm", [4, 12, -2], [8, 24, 2], [40, 16], [5, 22, 0], True, 0),
    ("right_boot", [-4, 0, -2], [0, 12, 2], [0, 16], [-2, 12, 0], False, 0),
    ("left_boot", [0, 0, -2], [4, 12, 2], [0, 16], [2, 12, 0], True, 0),
    ("belt", [-4, 12, -2], [4, 24, 2], [16, 16], [0, 24, 0], False, 1),
    ("right_leg", [-4, 0, -2], [0, 12, 2], [0, 16], [-2, 12, 0], False, 1),
    ("left_leg", [0, 0, -2], [4, 12, 2], [0, 16], [2, 12, 0], True, 1),
]


def layers_bbmodel(layer1, layer2):
    elements, groups = [], {0: [], 1: []}
    for name, frm, to, uvo, origin, mirror, tex in PLAYER_BOXES:
        uid = _uid("layers", name)
        w, h, d = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
        u, v = uvo
        faces = {
            "north": [u + d, v + d, u + d + w, v + d + h], "east": [u, v + d, u + d, v + d + h],
            "south": [u + d + w + d, v + d, u + d + w + d + w, v + d + h], "west": [u + d + w, v + d, u + d + w + d, v + d + h],
            "up": [u + d + w, v + d, u + d, v], "down": [u + d + w + w, v, u + d + w, v + d],
        }
        elements.append({
            "name": name, "box_uv": True, "rescale": False, "locked": False, "render_order": "default",
            "allow_mirror_modeling": True, "from": frm, "to": to, "autouv": 0, "color": tex * 3,
            "inflate": 1.0 if tex == 0 else 0.5, "origin": origin, "uv_offset": uvo, "mirror_uv": mirror,
            "faces": {k: {"uv": v_, "texture": tex} for k, v_ in faces.items()}, "type": "cube", "uuid": uid,
        })
        groups[tex].append(uid)
    return {
        "meta": {"format_version": "4.10", "model_format": "free", "box_uv": True},
        "name": "blood_knight_armor", "model_identifier": "", "visible_box": [1, 1, 0],
        "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [],
        "unhandled_root_fields": {}, "resolution": {"width": 64, "height": 32},
        "elements": elements,
        "outliner": [
            {"name": "humanoid (helm, cuirass, sabatons)", "origin": [0, 0, 0], "color": 0, "uuid": _uid("layers", "g0"),
             "export": True, "mirror_uv": False, "isOpen": True, "locked": False, "visibility": True, "autouv": 0,
             "children": groups[0]},
            {"name": "humanoid_leggings (greaves)", "origin": [0, 0, 0], "color": 3, "uuid": _uid("layers", "g1"),
             "export": True, "mirror_uv": False, "isOpen": True, "locked": False, "visibility": True, "autouv": 0,
             "children": groups[1]},
        ],
        "textures": [_texture_entry("humanoid/blood_knight.png", layer1, 64, 32, 0),
                     _texture_entry("humanoid_leggings/blood_knight.png", layer2, 64, 32, 1)],
    }


# =============================================================================================

def write(pack, blockbench_dir):
    assets = os.path.join(pack, "assets", NS)
    tex_item = os.path.join(assets, "textures", "item")
    os.makedirs(tex_item, exist_ok=True)

    helm_tex = paint_helm()
    helm_tex.save(os.path.join(tex_item, "blood_knight_helm_model.png"))
    with open(os.path.join(assets, "models", "item", "blood_knight_helm.json"), "w") as f:
        json.dump(helm_model(), f, indent=2)

    for name in ICONS:
        icon(name).save(os.path.join(tex_item, f"{name}.png"))

    eq = os.path.join(assets, "textures", "entity", "equipment")
    os.makedirs(os.path.join(eq, "humanoid"), exist_ok=True)
    os.makedirs(os.path.join(eq, "humanoid_leggings"), exist_ok=True)
    layer1, layer2 = paint_layer1(), paint_layer2()
    layer1.save(os.path.join(eq, "humanoid", "blood_knight.png"))
    layer2.save(os.path.join(eq, "humanoid_leggings", "blood_knight.png"))

    os.makedirs(blockbench_dir, exist_ok=True)
    with open(os.path.join(blockbench_dir, "blood_knight_helm.bbmodel"), "w") as f:
        json.dump(helm_bbmodel(helm_tex), f, indent=1)
    with open(os.path.join(blockbench_dir, "blood_knight_armor.bbmodel"), "w") as f:
        json.dump(layers_bbmodel(layer1, layer2), f, indent=1)
