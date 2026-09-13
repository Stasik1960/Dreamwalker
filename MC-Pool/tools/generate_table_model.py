"""Build the large 3 x 2 block model for the pool table.

Coordinates are in model pixels. The felt and ball positions must stay aligned
with BilliardsTableBlockEntityRenderer's [-1.25, 1.25] x [-0.25, 1.25].
"""

import json
from pathlib import Path


TEXTURES = {
    "wood": "poolbilliards:block/pool_wood",
    "rail": "poolbilliards:block/pool_rail_top",
    "felt": "poolbilliards:block/pool_felt",
    "leg": "poolbilliards:block/pool_leg",
    "pocket": "poolbilliards:block/pool_black",
    "particle": "poolbilliards:block/pool_felt",
}
elements = []


def box(name, bounds, top, sides=None):
    x0, y0, z0, x1, y1, z1 = bounds
    side = sides or top
    faces = {}
    for direction in ("up", "down", "north", "south", "west", "east"):
        texture = top if direction in ("up", "down") else side
        faces[direction] = {"texture": f"#{texture}", "uv": [0, 0, 16, 16]}
    elements.append({"name": name, "from": [x0, y0, z0], "to": [x1, y1, z1], "faces": faces})


# Deep cabinet, a slim decorative lip and four visibly tapered-looking feet.
box("cabinet", (-15.5, 3.0, 0.5, 31.5, 6.8, 31.5), "wood")
box("cabinet lower band", (-15.7, 2.7, 0.3, 31.7, 3.3, 31.7), "rail", "wood")
box("cabinet upper band", (-15.8, 6.5, 0.2, 31.8, 7.1, 31.8), "rail", "wood")
for x in (-14.4, 28.4):
    for z in (2.0, 28.0):
        box("foot", (x, 0, z, x + 2.4, 0.65, z + 2.4), "rail", "leg")
        box("carved leg", (x - 0.2, 0.65, z - 0.2, x + 2.6, 3.0, z + 2.6), "leg")
        box("leg collar", (x - 0.4, 2.85, z - 0.4, x + 2.8, 3.4, z + 2.8), "rail", "wood")

# The playing bed stops short of the six pockets, leaving dark openings visible.
box("slate bed", (-12.8, 7.0, 3.2, 28.8, 7.55, 28.8), "pocket", "wood")
box("playing felt", (-11.4, 7.55, 4.4, 27.4, 7.8, 27.6), "felt")
for x0, x1 in ((-12.2, -11.4), (27.4, 28.2)):
    box("side cloth", (x0, 7.55, 6.0, x1, 7.8, 26.0), "felt")
for z0, z1 in ((3.8, 4.4), (27.6, 28.2)):
    for x0, x1 in ((-10.5, 5.0), (11.0, 26.5)):
        box("end cloth", (x0, 7.55, z0, x1, 7.8, z1), "felt")

# Pocket wells sit below the cloth; black top surfaces read as actual holes.
for x0, x1 in ((-15.6, -11.0), (27.0, 31.6)):
    for z0, z1 in ((0.4, 4.9), (27.1, 31.6)):
        box("corner pocket", (x0, 7.25, z0, x1, 7.72, z1), "pocket")
for z0, z1 in ((0.4, 4.1), (27.9, 31.6)):
    box("side pocket", (5.15, 7.25, z0, 10.85, 7.72, z1), "pocket")

# Split the long rails at the side pockets and leave corner openings.
for z0, z1 in ((0.2, 3.8), (28.2, 31.8)):
    for x0, x1 in ((-11.0, 5.15), (10.85, 27.0)):
        box("hardwood rail", (x0, 7.8, z0, x1, 9.5, z1), "rail", "wood")
        inner0, inner1 = (3.4, 4.15) if z0 < 10 else (27.85, 28.6)
        box("rubber cushion", (x0, 7.9, inner0, x1, 8.9, inner1), "felt")
for x0, x1 in ((-15.8, -12.2), (28.2, 31.8)):
    box("short rail", (x0, 7.8, 4.9, x1, 9.5, 27.1), "rail", "wood")
    inner0, inner1 = (-12.55, -11.8) if x0 < 0 else (27.8, 28.55)
    box("short cushion", (inner0, 7.9, 4.9, inner1, 8.9, 27.1), "felt")

# Brass-like aiming diamonds along the rails, using the bright rail material.
for x in (-8.0, -2.5, 2.5, 13.5, 18.5, 24.0):
    for z in (1.2, 30.3):
        box("rail sight", (x, 9.51, z, x + 0.45, 9.56, z + 0.45), "leg")
for x in (-14.8, 30.3):
    for z in (9.5, 16.0, 22.5):
        box("rail sight", (x, 9.51, z, x + 0.45, 9.56, z + 0.45), "leg")

model = {"ambientocclusion": True, "textures": TEXTURES, "elements": elements}
path = Path(__file__).resolve().parents[1] / "src/main/resources/assets/poolbilliards/models/block/billiards_table.json"
path.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
print(f"Wrote {len(elements)} model elements to {path}")
