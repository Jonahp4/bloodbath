#!/usr/bin/env python3
"""Turns the Blood Knight boss model (a Blockbench glTF export) into something Minecraft can show.

Minecraft item models only allow axis-aligned cubes with at most one 22.5-degree-step rotation
each, but the boss is posed: its 168 cubes sit at arbitrary angles. So each bone's cubes are
grouped by rotation. Cubes that share a rotation (within a few degrees), or differ from it by one
legal element rotation, become one item model drawn in that rotation's frame, and the plugin
shows each such group on an item display entity rotated to match. Rigid attachments are folded
into the bone that carries them (horns into the head, the sword into the right hand...), which
leaves 16 animated bones.

Outputs:
  resourcepack/assets/unchartedsmp/models/item/boss/<part>.json   one item model per group
  resourcepack/assets/unchartedsmp/items/boss/<part>.json         their item definitions
  resourcepack/assets/unchartedsmp/textures/item/boss/tex<n>.png  the model's textures
  paper/src/main/resources/boss/blood_knight.json                 the rig: bones and parts

Usage: python3 tools/models/boss.py (run by generate.py)
"""
import base64
import io
import json
import math
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
SOURCE = os.path.join(HERE, "assets", "blood_knight.gltf")
ASSETS = os.path.join(ROOT, "resourcepack", "assets", "unchartedsmp")
RIG_OUT = os.path.join(ROOT, "paper", "src", "main", "resources", "boss", "blood_knight.json")
NS = "unchartedsmp"

# Bones that move together are merged into the bone that carries them.
MERGE = {"root": "body", "waist": "body", "stomach": "body", "horns": "head", "shield": "l_hand",
         "sword": "r_hand", "main_blade": "r_hand", "main_handle": "r_hand"}
# Blockbench names -> ours (the left leg's children were named r_*2 in the source).
RENAME = {"left_leg": "l_leg", "r_foreleg2": "l_foreleg", "r_foot2": "l_foot", "right_leg": "r_leg",
          "left_arm": "l_arm", "right_arm": "r_arm", "lower_jaw": "jaw", "chest": "chest", "head": "head",
          "l_forearm": "l_forearm", "l_hand": "l_hand", "r_forearm": "r_forearm", "r_hand": "r_hand",
          "r_foreleg": "r_foreleg", "r_foot": "r_foot"}
SAME_DEG = float(os.environ.get("BOSS_SAME_DEG", 10.0))    # rotations closer than this share a frame as they are
SNAP_DEG = float(os.environ.get("BOSS_SNAP_DEG", 12.0))    # an element rotation may be off by this much from a legal one
LEGAL = (-45.0, -22.5, 22.5, 45.0)
MAX_HALF_PX = 24.0  # item model elements must stay within [-16, 32]: +-24 around the centre


def quat_to_matrix(q):
    x, y, z, w = q
    return np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def matrix_to_quat(m):
    t = m[0, 0] + m[1, 1] + m[2, 2]
    if t > 0:
        s = math.sqrt(t + 1.0) * 2
        return [(m[2, 1] - m[1, 2]) / s, (m[0, 2] - m[2, 0]) / s, (m[1, 0] - m[0, 1]) / s, 0.25 * s]
    if m[0, 0] > m[1, 1] and m[0, 0] > m[2, 2]:
        s = math.sqrt(1.0 + m[0, 0] - m[1, 1] - m[2, 2]) * 2
        return [0.25 * s, (m[0, 1] + m[1, 0]) / s, (m[0, 2] + m[2, 0]) / s, (m[2, 1] - m[1, 2]) / s]
    if m[1, 1] > m[2, 2]:
        s = math.sqrt(1.0 + m[1, 1] - m[0, 0] - m[2, 2]) * 2
        return [(m[0, 1] + m[1, 0]) / s, 0.25 * s, (m[1, 2] + m[2, 1]) / s, (m[0, 2] - m[2, 0]) / s]
    s = math.sqrt(1.0 + m[2, 2] - m[0, 0] - m[1, 1]) * 2
    return [(m[0, 2] + m[2, 0]) / s, (m[1, 2] + m[2, 1]) / s, 0.25 * s, (m[1, 0] - m[0, 1]) / s]


def angle_of(m):
    return math.degrees(math.acos(max(-1.0, min(1.0, (np.trace(m) - 1) / 2))))


def axis_rotation(axis, degrees):
    a = math.radians(degrees)
    c, s = math.cos(a), math.sin(a)
    return {"x": np.array([[1, 0, 0], [0, c, -s], [0, s, c]]),
            "y": np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]]),
            "z": np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])}[axis]


def local_matrix(node):
    m = np.eye(4)
    if "rotation" in node:
        m[:3, :3] = quat_to_matrix(node["rotation"])
    if "scale" in node:
        m[:3, :3] = m[:3, :3] @ np.diag(node["scale"])
    if "translation" in node:
        m[:3, 3] = node["translation"]
    return m


class Gltf:
    def __init__(self, path):
        self.g = json.load(open(path))
        uri = self.g["buffers"][0]["uri"]
        self.buffer = base64.b64decode(uri.split(",", 1)[1])

    def accessor(self, index):
        acc = self.g["accessors"][index]
        view = self.g["bufferViews"][acc["bufferView"]]
        comps = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[acc["type"]]
        dtype = {5126: np.float32, 5123: np.uint16, 5125: np.uint32}[acc["componentType"]]
        start = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
        data = np.frombuffer(self.buffer, dtype=dtype, count=acc["count"] * comps, offset=start)
        return data.reshape(acc["count"], comps) if comps > 1 else data

    def image(self, index):
        uri = self.g["images"][index]["uri"]
        return Image.open(io.BytesIO(base64.b64decode(uri.split(",", 1)[1]))).convert("RGBA")


# MC face corners (TL, TR, BR, BL) as seen looking at the face, from its from/to box.
def face_corners(face, f, t):
    return {
        "north": [(t[0], t[1], f[2]), (f[0], t[1], f[2]), (f[0], f[1], f[2]), (t[0], f[1], f[2])],
        "south": [(f[0], t[1], t[2]), (t[0], t[1], t[2]), (t[0], f[1], t[2]), (f[0], f[1], t[2])],
        "east": [(t[0], t[1], t[2]), (t[0], t[1], f[2]), (t[0], f[1], f[2]), (t[0], f[1], t[2])],
        "west": [(f[0], t[1], f[2]), (f[0], t[1], t[2]), (f[0], f[1], t[2]), (f[0], f[1], f[2])],
        "up": [(f[0], t[1], f[2]), (t[0], t[1], f[2]), (t[0], t[1], t[2]), (f[0], t[1], t[2])],
        "down": [(f[0], f[1], t[2]), (t[0], f[1], t[2]), (t[0], f[1], f[2]), (f[0], f[1], f[2])],
    }[face]


FACE_OF_NORMAL = {(1, 0, 0): "east", (-1, 0, 0): "west", (0, 1, 0): "up", (0, -1, 0): "down", (0, 0, 1): "south", (0, 0, -1): "north"}


def cube_faces(gl, mesh_index):
    """{face: [(position, uv)] * 4} in the cube's own frame, plus its texture (image) index."""
    prim = gl.g["meshes"][mesh_index]["primitives"][0]
    pos = gl.accessor(prim["attributes"]["POSITION"])
    nrm = gl.accessor(prim["attributes"]["NORMAL"])
    uv = gl.accessor(prim["attributes"]["TEXCOORD_0"])
    material = gl.g["materials"][prim["material"]]
    texture = gl.g["textures"][material["pbrMetallicRoughness"]["baseColorTexture"]["index"]]["source"]
    faces = {}
    for p, n, t in zip(pos, nrm, uv):
        key = tuple(int(round(v)) for v in n)
        faces.setdefault(FACE_OF_NORMAL[key], []).append((np.array(p, dtype=float), (float(t[0]), float(t[1]))))
    return faces, texture, pos.min(axis=0).astype(float), pos.max(axis=0).astype(float)


def face_uv(face, verts, lo, hi):
    """MC uv [u1, v1, u2, v2] (0-16) and rotation for a face, from the glTF's per-vertex UVs."""
    actual = []
    for corner in face_corners(face, lo, hi):
        best = min(verts, key=lambda v: np.linalg.norm(v[0] - np.array(corner)))
        actual.append((best[1][0] * 16.0, best[1][1] * 16.0))
    for k in range(4):
        unrot = [actual[(i + k) % 4] for i in range(4)]
        (a, b), (c, b2), (c2, d), (a2, d2) = unrot
        if abs(b - b2) < 1e-3 and abs(c - c2) < 1e-3 and abs(d - d2) < 1e-3 and abs(a - a2) < 1e-3:
            return [round(a, 4), round(b, 4), round(c, 4), round(d, 4)], k * 90
    raise ValueError(f"face {face}: UVs aren't a rectangle: {actual}")


def main():
    gl = Gltf(SOURCE)
    nodes = gl.g["nodes"]
    parents = {}
    for i, n in enumerate(nodes):
        for c in n.get("children", []):
            parents[c] = i
    world = {}

    def world_of(i):
        if i not in world:
            m = local_matrix(nodes[i])
            world[i] = (world_of(parents[i]) @ m) if i in parents else m
        return world[i]

    def bone_name(i):
        name = nodes[i].get("name")
        return MERGE.get(name, RENAME.get(name, name))

    # Bones: every non-mesh node from "root" down, merged and renamed.
    root = next(i for i, n in enumerate(nodes) if n.get("name") == "root")
    bones = {}      # name -> (parent name, world position)
    order = []

    def walk(i, parent):
        n = nodes[i]
        if "mesh" in n:
            return
        name = bone_name(i)
        if name not in bones:
            bones[name] = (parent, world_of(i)[:3, 3].copy())
            order.append(name)
        for c in n.get("children", []):
            walk(c, name if name != parent or name not in bones else parent)
    walk(root, None)

    # The model faces -Z in Blockbench; everything below is in its own space (blocks).
    cubes = {name: [] for name in order}
    for i, n in enumerate(nodes):
        if "mesh" not in n:
            continue
        bone = bone_name(parents[i])
        faces, texture, lo, hi = cube_faces(gl, n["mesh"])
        w = world_of(i)
        bone_pos = bones[bone][1]
        rot = w[:3, :3]
        center_local = (lo + hi) / 2
        center = w[:3, :3] @ center_local + w[:3, 3] - bone_pos
        cubes[bone].append({"name": n.get("name"), "rot": rot, "center": center, "half": (hi - lo) / 2, "lo": lo, "hi": hi,
                            "faces": faces, "texture": texture})

    textures_used = set()
    parts = []
    tex_dir = os.path.join(ASSETS, "textures", "item", "boss")
    model_dir = os.path.join(ASSETS, "models", "item", "boss")
    item_dir = os.path.join(ASSETS, "items", "boss")
    for d in (tex_dir, model_dir, item_dir):
        os.makedirs(d, exist_ok=True)
        for f in os.listdir(d):
            os.remove(os.path.join(d, f))

    for bone in order:
        groups = []   # [{"rot": R, "members": [(cube, element_rotation or None)]}]
        for cube in sorted(cubes[bone], key=lambda c: -np.prod(c["half"])):
            placed = False
            for group in groups:
                rel = group["rot"].T @ cube["rot"]
                if angle_of(rel) < SAME_DEG:
                    group["members"].append((cube, None))
                    placed = True
                    break
                for axis in "xyz":
                    for deg in LEGAL:
                        if angle_of(axis_rotation(axis, deg).T @ rel) < SNAP_DEG:
                            group["members"].append((cube, (axis, deg)))
                            placed = True
                            break
                    if placed:
                        break
                if placed:
                    break
            if not placed:
                groups.append({"rot": cube["rot"], "members": [(cube, None)]})

        for gi, group in enumerate(groups):
            rot = group["rot"]
            members = group["members"]
            origin = np.mean([c["center"] for c, _ in members], axis=0)
            # Element boxes in the group's frame, in pixels, centred on the group origin.
            boxes = []
            extent = 0.0
            for cube, erot in members:
                c = rot.T @ (cube["center"] - origin) * 16.0
                h = cube["half"] * 16.0
                boxes.append((cube, erot, c, h))
                extent = max(extent, float(np.max(np.abs(c) + np.linalg.norm(h))))
            scale = 1
            while extent / scale > MAX_HALF_PX:
                scale *= 2
            elements = []
            for cube, erot, c, h in boxes:
                f = (c - h) / scale + 8.0
                t = (c + h) / scale + 8.0
                el = {"from": [round(v, 4) for v in f], "to": [round(v, 4) for v in t], "faces": {}}
                if erot:
                    el["rotation"] = {"angle": erot[1], "axis": erot[0], "origin": [round(v, 4) for v in c / scale + 8.0]}
                for face, verts in cube["faces"].items():
                    uv, rotation = face_uv(face, verts, cube["lo"], cube["hi"])
                    spec = {"uv": uv, "texture": f"#{cube['texture']}"}
                    if rotation:
                        spec["rotation"] = rotation
                    el["faces"][face] = spec
                textures_used.add(cube["texture"])
                elements.append(el)
            name = f"{bone}_{gi}"
            used = sorted({c["texture"] for c, _ in members})
            model = {"textures": {**{str(t): f"{NS}:item/boss/tex{t}" for t in used}, "particle": f"{NS}:item/boss/tex{used[0]}"},
                     "elements": elements}
            with open(os.path.join(model_dir, name + ".json"), "w") as out:
                json.dump(model, out, separators=(",", ":"))
            with open(os.path.join(item_dir, name + ".json"), "w") as out:
                json.dump({"model": {"type": "minecraft:model", "model": f"{NS}:item/boss/{name}"}}, out)
            q = matrix_to_quat(rot)
            parts.append({"bone": bone, "model": f"{NS}:boss/{name}", "offset": [round(v, 5) for v in origin],
                          "rotation": [round(v, 6) for v in q], "scale": scale, "cubes": len(members)})

    for t in sorted(textures_used):
        gl.image(t).save(os.path.join(tex_dir, f"tex{t}.png"))

    rig = {"source": "tools/models/assets/blood_knight.gltf", "facing": "-z", "bones": [], "parts": parts}
    for name in order:
        parent, pos = bones[name]
        pivot = pos - (bones[parent][1] if parent else np.zeros(3))
        rig["bones"].append({"name": name, "parent": parent, "pivot": [round(float(v), 5) for v in pivot]})
    os.makedirs(os.path.dirname(RIG_OUT), exist_ok=True)
    with open(RIG_OUT, "w") as out:
        json.dump(rig, out, indent=1)
    print(f"boss: {len(order)} bones, {len(parts)} parts from {sum(len(v) for v in cubes.values())} cubes, "
          f"textures {sorted(textures_used)}")
    for p in parts:
        if p["scale"] > 1:
            print(f"  {p['model']} drawn at 1/{p['scale']} scale")


if __name__ == "__main__":
    main()
