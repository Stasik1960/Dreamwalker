"""Author the Bestiary's GeckoLib models, UV atlases, and keyframed motion."""

import json
import math
from pathlib import Path
import random

from PIL import Image

ASSETS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/dw_npc_bestiary"
PALETTES = {
    "shell": ((48, 48, 47), (81, 76, 69), (114, 109, 91)),
    "chitin": ((32, 29, 32), (56, 52, 54), (86, 78, 73)),
    "flesh": ((71, 51, 48), (110, 79, 67), (151, 108, 79)),
    "bone": ((111, 101, 75), (162, 148, 111), (201, 184, 144)),
    "spore": ((26, 80, 68), (61, 161, 124), (155, 234, 177)),
    "eye": ((53, 114, 86), (133, 220, 159), (238, 249, 170)),
    "boot": ((31, 28, 36), (49, 43, 53), (84, 72, 84)),
    "lining": ((52, 36, 51), (81, 54, 72), (125, 87, 105)),
    "velvet": ((50, 42, 62), (83, 68, 94), (127, 105, 132)),
    "trim": ((86, 56, 41), (138, 95, 62), (189, 144, 87)),
    "mask": ((115, 104, 91), (171, 155, 134), (214, 196, 164)),
    "wing": ((49, 37, 56), (91, 63, 83), (158, 103, 110)),
    "tip": ((53, 41, 60), (104, 69, 90), (168, 112, 105)),
    "ember": ((147, 63, 35), (236, 136, 53), (255, 226, 137)),
}


def json_file(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


class Model:
    def __init__(self, name, bounds):
        self.name = name
        self.bounds = bounds
        self.bones = {}
        self.atlas = Image.new("RGBA", (256, 128))
        self.x = self.y = self.row_height = 0
        self.rng = random.Random(name)

    def bone(self, name, pivot, parent="root"):
        self.bones[name] = {"name": name, "pivot": list(pivot), "cubes": []}
        if name != "root":
            self.bones[name]["parent"] = parent
        return name

    def face(self, w, h, material, face):
        w, h = max(1, math.ceil(w)), max(1, math.ceil(h))
        if self.x + w + 2 > self.atlas.width:
            self.y += self.row_height
            self.x = self.row_height = 0
        if self.y + h + 2 > self.atlas.height:
            raise ValueError(f"{self.name}: atlas overflow on {material}")
        u, v = self.x + 1, self.y + 1
        self.x += w + 2
        self.row_height = max(self.row_height, h + 2)
        shadow, base, light = PALETTES[material]
        shade = {"up": 7, "down": -8, "east": 2, "west": -3}.get(face, 0)
        # Paint at the texel scale and extend edge texels to prevent UV seams.
        tile = Image.new("RGBA", (w, h))
        for y in range(h):
            for x in range(w):
                edge = (x == 0 or x == w - 1) and w >= 4
                pigment = base
                if material in ("wing", "tip") and w >= 4 and h >= 5:
                    spread = abs(2 * x / (w - 1) - 1)
                    if abs(y / (h - 1) - (0.30 + spread * 0.33)) < 0.13:
                        pigment = light
                    elif y >= h - 2 or edge:
                        pigment = shadow
                elif material == "shell" and h >= 3 and y % 3 == 0:
                    pigment = light if y == 0 else shadow
                elif material in ("spore", "ember", "eye"):
                    pigment = light if x in range(w // 3, w - w // 3) and y < h * 0.7 else base
                elif material in ("velvet", "lining") and w >= 4 and h >= 4:
                    if y == h - 2:
                        pigment = light
                    elif edge or y == h - 1:
                        pigment = shadow
                elif edge or y == h - 1 and h >= 4:
                    pigment = shadow
                elif y == 0 and h >= 3:
                    pigment = light
                grain = shade + self.rng.randint(-6, 6)
                tile.putpixel((x, y), (*[min(255, max(0, c + grain)) for c in pigment], 255))
        for y in range(-1, h + 1):
            for x in range(-1, w + 1):
                self.atlas.putpixel((u + x, v + y), tile.getpixel((min(w - 1, max(0, x)), min(h - 1, max(0, y)))))
        return {"uv": [u, v], "uv_size": [w, h]}

    def cube(self, bone, origin, size, material, rotation=None, pivot=None, *, front=None):
        x, y, z = size
        faces = {
            "north": (x, y), "south": (x, y), "east": (z, y),
            "west": (z, y), "up": (x, z), "down": (x, z),
        }
        cube = {
            "origin": list(origin),
            "size": list(size),
            "uv": {f: self.face(w, h, front if f == "north" and front else material, f) for f, (w, h) in faces.items()},
        }
        if rotation:
            cube.update(rotation=list(rotation), pivot=list(pivot))
        self.bones[bone]["cubes"].append(cube)

    def limb(self, bone, start, end, width, material):
        x, y, z = start
        dx, dy, dz = (b - a for a, b in zip(start, end))
        # GeckoLib reflects X and applies cube pitch before yaw. Start the
        # segment along +Z so pitch changes height independently of heading.
        pitch = math.degrees(math.atan2(dy, math.hypot(dx, dz)))
        yaw = math.degrees(math.atan2(dx, dz))
        self.cube(bone, (x - width / 2, y - width / 2, z),
                  (width, width, round(math.dist(start, end), 4)), material,
                  (round(pitch, 4), round(yaw, 4), 0), start)

    def save(self, animations):
        json_file(ASSETS / f"geo/{self.name}.geo.json", {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": f"geometry.{self.name}",
                    "texture_width": self.atlas.width,
                    "texture_height": self.atlas.height,
                    "visible_bounds_width": self.bounds[0],
                    "visible_bounds_height": self.bounds[1],
                    "visible_bounds_offset": [0, self.bounds[2], 0],
                },
                "bones": list(self.bones.values()),
            }],
        })
        path = ASSETS / f"textures/entity/{self.name}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        self.atlas.save(path, optimize=True)
        json_file(ASSETS / f"animations/{self.name}.animation.json", {
            "format_version": "1.8.0",
            "animations": {f"animation.{self.name}.{key}": value for key, value in animations.items()},
        })


def track(duration, poses):
    return {str(round(fraction * duration, 4)): list(value) for fraction, value in poses}


def axis(duration, values, index=0):
    return track(duration, [(time, tuple(value if i == index else 0 for i in range(3))) for time, value in values])


def arc(duration, value, index=0):
    return axis(duration, [(0, 0), (0.25, value), (0.75, -value), (1, 0)], index)


def clip(seconds, bones, loop=False):
    return {"animation_length": seconds, "loop": loop, "bones": bones}


def mire_weaver():
    model = Model("mire_weaver", (2.5, 1.7, 0.6))
    model.bone("root", (0, 3, 0))
    model.bone("thorax", (0, 7, 0))
    model.cube("thorax", (-3.9, 5.4, -3.5), (7.8, 5.2, 8), "chitin")
    model.cube("thorax", (-4.5, 8.1, -3.8), (9, 3, 8.1), "shell")
    model.cube("thorax", (-2.8, 4.8, -2.8), (5.6, 1.5, 6), "flesh")
    model.bone("sac", (0, 8, 3), "thorax")
    model.cube("sac", (-3.8, 6.2, 3.3), (7.6, 5.4, 6.1), "spore")
    model.cube("sac", (-4.2, 10.5, 3.1), (8.4, 3.8, 6.6), "shell")
    model.cube("sac", (-3, 12.9, 3.6), (6, 3.1, 5.2), "shell")
    for offset in (4.5, 6.9):
        model.cube("sac", (-4, 6.2, offset), (8, 1, 0.85), "chitin")
    model.limb("sac", (1.6, 14.8, 5.2), (3.6, 17.2, 7.9), 0.9, "bone")
    model.limb("sac", (-1.9, 13.7, 6), (-3.3, 15.7, 8.3), 0.85, "shell")
    model.bone("head", (0, 8, -3.2), "thorax")
    model.cube("head", (-2.9, 6, -7.5), (5.8, 3.8, 4.5), "chitin")
    model.cube("head", (-2.3, 9.1, -7.2), (4.6, 1.5, 3.9), "shell")
    model.cube("head", (-2.3, 6.8, -7.75), (1.1, 0.8, 0.35), "eye")
    model.cube("head", (1.2, 6.8, -7.75), (1.1, 0.8, 0.35), "eye")
    model.cube("head", (-0.7, 7.7, -7.7), (1.4, 1.25, 0.35), "spore")
    walk = {"thorax": {"position": axis(0.72, [(0, 0), (0.25, 0.32), (0.5, 0), (0.75, 0.32), (1, 0)], 1)}}
    fold = {}
    for side, sign in (("l", -1), ("r", 1)):
        joint = model.bone(f"fang_{side}", (sign * 2.15, 6.4, -6.8), "head")
        model.limb(joint, (sign * 2.15, 6.4, -6.8), (sign * 2.6, 5.4, -9.0), 1.25, "flesh")
        model.limb(joint, (sign * 2.6, 5.4, -9.0), (sign * 1.55, 5.0, -9.7), 0.75, "bone")
        for i, (z, front) in enumerate(((-3.0, -4.2), (0.1, 0.5), (3.3, 4.0))):
            hip = (sign * 3.45, 7.6, z)
            knee = (sign * (7.4 + (i == 1)), 9.8, z + front * 0.58)
            foot = (sign * (8.8 + (i == 1)), 1.1, z + front)
            thigh = model.bone(f"leg_{side}_{i}", hip, "thorax")
            model.limb(thigh, hip, knee, 1.65 if i != 1 else 1.9, "shell")
            shin = model.bone(f"shin_{side}_{i}", knee, thigh)
            model.limb(shin, knee, foot, 1.1, "chitin")
            model.limb(shin, foot, (foot[0] - sign * 0.35, 0.45, foot[2] - 1.25), 0.75, "bone")
            direction = sign * (-1 if i == 1 else 1)
            walk[thigh] = {"rotation": arc(0.72, direction * 17, 1)}
            walk[shin] = {"rotation": arc(0.72, direction * -12, 2)}
            fold[thigh] = {"rotation": axis(1.05, [(0, 0), (0.53, -sign * 9), (1, sign * 26)], 2)}
            fold[shin] = {"rotation": axis(1.05, [(0, 0), (0.62, -sign * 24), (1, -sign * 40)], 2)}
    model.save({
        "idle": clip(2.8, {
            "sac": {"scale": track(2.8, [(0, (1, 1, 1)), (0.5, (1.045, 1.09, 1.04)), (1, (1, 1, 1))])},
            "head": {"rotation": arc(2.8, 4, 1)},
            "fang_l": {"rotation": arc(2.8, -6, 1)},
            "fang_r": {"rotation": arc(2.8, 6, 1)},
        }, True),
        "walk": clip(0.72, walk, True),
        "attack1": clip(0.85, {
            "thorax": {"rotation": axis(0.85, [(0, 0), (0.40, -18), (0.52, 15), (0.72, 7), (1, 0)]),
                       "position": track(0.85, [(0, (0, 0, 0)), (0.4, (0, 1.6, 1.2)), (0.52, (0, 0.25, -4.2)), (1, (0, 0, 0))])},
            "fang_l": {"rotation": axis(0.85, [(0, 0), (0.42, -39), (0.57, 16), (1, 0)], 1)},
            "fang_r": {"rotation": axis(0.85, [(0, 0), (0.42, 39), (0.57, -16), (1, 0)], 1)},
        }),
        "attack2": clip(0.76, {
            "thorax": {"rotation": axis(0.76, [(0, 0), (0.33, -19), (0.61, 26), (1, 0)], 1)},
            "leg_l_0": {"rotation": track(0.76, [(0, (0, 0, 0)), (0.33, (-22, -20, -34)), (0.52, (23, 36, -15)), (1, (0, 0, 0))])},
            "leg_r_0": {"rotation": axis(0.76, [(0, 0), (0.35, 25), (0.58, -14), (1, 0)], 2)},
        }),
        "hit": clip(0.32, {"thorax": {"rotation": axis(0.32, [(0, 0), (0.2, -14), (0.65, 6), (1, 0)], 2)}}),
        "death": clip(1.05, {
            "root": {"rotation": axis(1.05, [(0, 0), (0.32, -12), (0.72, 91), (1, 96)], 2)},
            "head": {"rotation": axis(1.05, [(0, 0), (0.28, -14), (1, 23)])},
            **fold,
        }, "hold_on_last_frame"),
    })


def vesper_warden():
    model = Model("vesper_warden", (2.9, 2.65, 1.05))
    model.bone("root", (0, 2, 0))
    model.bone("body", (0, 15, 0))
    model.cube("body", (-3.8, 15.3, -2.2), (7.6, 8.7, 4.5), "velvet")
    model.cube("body", (-2.15, 17.2, -2.55), (4.3, 6.6, 0.75), "lining")
    model.cube("body", (-4.15, 14.35, -2.55), (8.3, 1.6, 5.1), "trim")
    model.cube("body", (-1.25, 14.3, -3), (2.5, 1.75, 0.65), "mask")
    model.cube("body", (-4.65, 22.8, -2.45), (9.3, 2.2, 5.1), "lining")
    model.cube("body", (-2.2, 20.2, -2.95), (0.9, 4.1, 0.52), "trim", (0, 0, 18), (-1.5, 22, -2.8))
    model.bone("head", (0, 24.8, 0), "body")
    model.cube("head", (-3.65, 24.8, -2.65), (7.3, 6.3, 5.55), "velvet")
    model.cube("head", (-2.8, 25.4, -3.05), (5.6, 4.9, 1.75), "mask")
    model.cube("head", (-1.4, 24.7, -3.45), (2.8, 2.6, 1.15), "mask")
    for sign in (-1, 1):
        model.cube("head", (sign * 1.45 - 0.7, 27.65, -3.23), (1.4, 0.62, 0.22), "boot")
        model.cube("head", (sign * 1.55 - 0.26, 27.71, -3.29), (0.52, 0.36, 0.1), "ember")
        model.limb("head", (sign * 1.4, 30.3, -0.9), (sign * 3.55, 33.15, -0.35), 0.62, "trim")
        model.limb("head", (sign * 3.45, 32.95, -0.35), (sign * 4.35, 33.15, 0.3), 0.53, "mask")
    idle = {"body": {"rotation": arc(2.8, 1.6, 2)}, "head": {"rotation": arc(2.8, 4, 1)}}
    walk = {"body": {"position": axis(0.72, [(0, 0), (0.25, 0.55), (0.5, 0), (0.75, 0.55), (1, 0)], 1)}}
    for side, sign in (("l", -1), ("r", 1)):
        leg = model.bone(f"leg_{side}", (sign * 2.05, 14.8, 0))
        model.cube(leg, (sign * 2.05 - 1.6, 4.8, -1.85), (3.2, 10.0, 3.7), "lining")
        model.cube(leg, (sign * 2.05 - 1.85, 0.2, -2.7), (3.7, 4.9, 4.9), "boot")
        model.cube(leg, (sign * 2.05 - 1.65, 4.1, -2.0), (3.3, 0.95, 4.0), "trim")
        coat = model.bone(f"coat_{side}", (sign * 2.0, 15.2, 0.6), "body")
        model.cube(coat, (sign * 2.25 - 1.85, 8.6, 0.75), (3.7, 7.6, 1.55), "velvet",
                   (-13, 0, sign * 6), (sign * 2.0, 15.2, 0.6))
        arm = model.bone(f"arm_{side}", (sign * 4.0, 22.1, 0), "body")
        model.cube(arm, (sign * 5.45 - 1.57, 17.8, -1.58), (3.14, 4.9, 3.32), "velvet")
        model.cube(arm, (sign * 5.8 - 1.5, 14.4, -1.32), (3, 4.6, 2.94), "lining")
        model.cube(arm, (sign * 5.8 - 1.57, 14.36, -1.45), (3.14, 0.72, 3.15), "trim")
        model.cube(arm, (sign * 5.8 - 1.16, 12.6, -1.11), (2.32, 2.27, 2.2), "mask")
        wing = model.bone(f"wing_{side}", (sign * 2.75, 21.9, 2.0), "body")
        model.cube(wing, (sign * 4.0 - 1.7, 12.7, 2.25), (3.4, 11.5, 1.3), "wing",
                   (8, sign * 13, sign * 14), (sign * 2.75, 21.9, 2.0))
        model.cube(wing, (sign * 6.0 - 1.41, 11.25, 2.62), (2.82, 10.1, 1.07), "wing",
                   (8, sign * 13, sign * 15), (sign * 2.75, 21.9, 2.0))
        model.cube(wing, (sign * 7.7 - 0.95, 12.5, 2.97), (1.9, 7.8, 0.83), "tip",
                   (8, sign * 13, sign * 17), (sign * 2.75, 21.9, 2.0))
        model.cube(wing, (sign * 4.75 - 1.18, 7.45, 3.6), (2.36, 6.35, 0.83), "tip",
                   (6, sign * 15, sign * 12), (sign * 3.75, 14.2, 3.4))
        walk[leg] = {"rotation": arc(0.72, sign * 22)}
        walk[arm] = {"rotation": arc(0.72, -sign * (14 if side == "l" else 7))}
        walk[coat] = {"rotation": arc(0.72, sign * 8)}
        idle[wing] = {"rotation": arc(2.8, sign * 8, 1)}
        walk[wing] = {"rotation": arc(0.72, sign * 5, 1)}
    model.bone("lantern", (5.8, 13.1, -0.15), "arm_r")
    model.cube("lantern", (5.42, 10.7, -0.53), (0.76, 2.6, 0.76), "trim")
    model.cube("lantern", (4.25, 7.1, -1.73), (3.1, 3.7, 3.1), "ember")
    for y in (6.55, 10.64):
        model.cube("lantern", (4, y, -1.98), (3.6, 0.63, 3.6), "trim")
    for x in (4.08, 7.02):
        for z in (-1.9, 1.04):
            model.cube("lantern", (x, 7.12, z), (0.48, 3.56, 0.48), "trim")
    idle["lantern"] = {"rotation": arc(2.8, 3, 2)}
    walk["lantern"] = {"rotation": arc(0.72, -9)}
    model.save({
        "idle": clip(2.8, idle, True),
        "walk": clip(0.72, walk, True),
        "attack1": clip(0.92, {
            "body": {"rotation": axis(0.92, [(0, 0), (0.36, 30), (0.58, -42), (1, 0)], 1)},
            "arm_r": {"rotation": track(0.92, [(0, (0, 0, 0)), (0.34, (-80, -22, 18)), (0.58, (-105, 24, -15)), (1, (0, 0, 0))])},
            "lantern": {"rotation": axis(0.92, [(0, 0), (0.33, 45), (0.56, -38), (1, 0)], 2)},
            "wing_l": {"rotation": axis(0.92, [(0, 0), (0.43, -31), (1, 0)], 1)},
            "wing_r": {"rotation": axis(0.92, [(0, 0), (0.43, 31), (1, 0)], 1)},
        }),
        "attack2": clip(0.88, {
            "body": {"rotation": axis(0.88, [(0, 0), (0.31, -13), (0.54, 21), (1, 0)])},
            "arm_l": {"rotation": track(0.88, [(0, (0, 0, 0)), (0.30, (-164, -14, -20)), (0.54, (-68, 18, -13)), (1, (0, 0, 0))])},
            "wing_l": {"rotation": axis(0.88, [(0, 0), (0.45, -27), (1, 0)], 1)},
            "wing_r": {"rotation": axis(0.88, [(0, 0), (0.45, 27), (1, 0)], 1)},
        }),
        "hit": clip(0.32, {"body": {"rotation": axis(0.32, [(0, 0), (0.24, -13), (0.6, 6), (1, 0)])}}),
        "death": clip(1.05, {
            "root": {"rotation": track(1.05, [(0, (0, 0, 0)), (0.31, (8, 0, -12)), (0.75, (-77, 0, 14)), (1, (-89, 0, 11))])},
            "head": {"rotation": axis(1.05, [(0, 0), (0.28, 11), (1, -10)])},
            "arm_l": {"rotation": axis(1.05, [(0, 0), (0.47, -18), (1, -24)], 2)},
            "arm_r": {"rotation": axis(1.05, [(0, 0), (0.47, 12), (1, 17)], 2)},
            "wing_l": {"rotation": axis(1.05, [(0, 0), (0.55, -22), (1, -26)], 1)},
            "wing_r": {"rotation": axis(1.05, [(0, 0), (0.55, 22), (1, 26)], 1)},
        }, "hold_on_last_frame"),
    })


if __name__ == "__main__":
    mire_weaver()
    vesper_warden()
