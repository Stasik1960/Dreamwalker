"""Editable Blockbench source and static 5x3-block runtime models."""
import base64
import json
import math
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/poolbilliards"
MODELS = ASSETS / "models/block"
TEXTURES = {"wood": "pool_wood", "rail": "pool_rail_top", "felt": "pool_felt",
            "leg": "pool_leg", "pocket": "pool_black", "brass": "pool_brass", "net": "pool_net"}
FOOTPRINT = [(x, z) for z in range(-1, 2) for x in range(-2, 3)]
boxes = []
pockets = []

# A single cut-out definition drives the cloth, slate, cushions, wooden frame,
# GUI and server capture volumes. Keep a solid wooden back behind every well.
for px in (-27.4, 43.4):
    for pz in (-11.4, 27.4):
        pockets.append({"x": px, "z": pz, "radius": 2.1, "innerZ": None})
for pz in (-12.0, 28.0):
    pockets.append({"x": 8, "z": pz, "radius": 2.15,
                    "innerZ": pz + (2.15 if pz < 0 else -2.15)})

# Quarter-unit slices are baked once. Merge unchanged strips so the long,
# straight sections stay single cuboids rather than a grid of tiny voxels.
def cutouts(bounds):
    x0, y0, z0, x1, y1, z1 = bounds
    relevant = [p for p in pockets if p["x"] + p["radius"] > x0
                and p["x"] - p["radius"] < x1
                and p["z"] + p["radius"] > z0 and p["z"] - p["radius"] < z1]
    cuts = sorted({z0, z1, *[i / 4 for i in range(math.ceil(z0*4), math.floor(z1*4)+1)]})
    active, result = {}, []
    for za, zb in zip(cuts, cuts[1:]):
        zm = (za + zb) / 2
        intervals = [(x0, x1)]
        for p in relevant:
            squared = p["radius"]**2 - (zm - p["z"])**2
            if squared <= 0:
                continue
            half = math.sqrt(squared)
            lo, hi = round(p["x"] - half, 5), round(p["x"] + half, 5)
            remaining = []
            for a, b in intervals:
                if hi <= a or lo >= b:
                    remaining.append((a, b))
                else:
                    if lo > a:
                        remaining.append((a, lo))
                    if hi < b:
                        remaining.append((hi, b))
            intervals = remaining
        next_active = {}
        for interval in intervals:
            a, b = interval
            start = active.pop(interval, za)
            next_active[interval] = start
        for (a, b), start in active.items():
            result.append((a, y0, start, b, y1, za))
        active = next_active
    result.extend((a, y0, start, b, y1, z1) for (a, b), start in active.items())
    return result

def box(name, bounds, material, group):
    boxes.append((name, bounds, material, group))

def perforated(name, bounds, material, group):
    for part in cutouts(bounds):
        box(name, part, material, group)

# Six legs, inset wooden apron, open rail gaps and simple hanging nets follow
# the silhouette of the supplied reference. Cubes stay intentionally sparse.
box("cabinet", (-30, 3.3, -14, 46, 7.7, 30), "wood", "Cabinet")
box("lower moulding", (-31, 3, -15, 47, 3.55, 31), "rail", "Cabinet")
# The apron moulding is a ring, not a solid board hiding the pocket liners.
for trim in ((-31.2, 7.15, -15.2, 47.2, 7.8, -14.6),
             (-31.2, 7.15, 30.6, 47.2, 7.8, 31.2),
             (-31.2, 7.15, -14.6, -30.6, 7.8, 30.6),
             (46.6, 7.15, -14.6, 47.2, 7.8, 30.6)):
    box("upper trim", trim, "rail", "Cabinet")
for name, bounds in (
    ("front inset", (-28, 4.15, -15.32, 44, 6.8, -15.16)),
    ("back inset", (-28, 4.15, 31.16, 44, 6.8, 31.32)),
    ("left inset", (-31.32, 4.15, -12, -31.16, 6.8, 28)),
    ("right inset", (47.16, 4.15, -12, 47.32, 6.8, 28))):
    box(name, bounds, "leg", "Cabinet")
for x in (-28.8, 6.8, 42.4):
    for z in (-12.8, 25.2):
        box("brass foot", (x, 0, z, x+3.6, .55, z+3.6), "brass", "Legs")
        box("leg base", (x+.2, .55, z+.2, x+3.4, 1.25, z+3.4), "leg", "Legs")
        box("leg shaft", (x+.55, 1.25, z+.55, x+3.05, 3.15, z+3.05), "leg", "Legs")
        box("leg collar", (x, 2.75, z, x+3.6, 3.28, z+3.6), "rail", "Legs")
perforated("slate", (-29, 7.8, -13, 45, 8.12, 29), "pocket", "Playfield")
perforated("green cloth", (-27.45, 8.12, -11.45, 43.45, 8.35, 27.45), "felt", "Playfield")
# Four continuous, non-overlapping frame sections; the circular cuts no longer
# remove the outside wall. The cushions terminate at exactly the same openings.
for z0, z1 in ((-16, -12.35), (28.35, 32)):
    perforated("long rail", (-32, 8.12, z0, 48, 10, z1), "rail", "Rails")
    a, b = (-12.35, -11.45) if z0 < 0 else (27.45, 28.35)
    perforated("long cushion", (-28.35, 8.15, a, 44.35, 9.15, b), "felt", "Rails")
for x0, x1 in ((-32, -28.35), (44.35, 48)):
    perforated("end rail", (x0, 8.12, -12.35, x1, 10, 28.35), "rail", "Rails")
    a, b = (-28.35, -27.45) if x0 < 0 else (43.45, 44.35)
    perforated("end cushion", (a, 8.15, -11.45, b, 9.15, 27.45), "felt", "Rails")

# Recessed dark liners are below the cloth, not raised black discs covering it.
# Their rectangular backing is hidden by the perforated bed and frame.
for p in pockets:
    x, z, r = p["x"], p["z"], p["radius"]
    box("recessed pocket liner", (x-r-.05, 7.71, z-r-.05, x+r+.05, 7.79, z+r+.05), "pocket", "Pockets")
    box("pocket net", (x-r+.25, 5.2, z-r+.25, x+r-.25, 7.6, z+r-.25), "net", "Pockets")
for x in (-20, -9, 0, 16, 25, 36):
    for z in (-14.2, 30.9):
        box("rail sight", (x, 10.01, z, x+.55, 10.07, z+.55), "brass", "Details")
for x in (-30.5, 46.5):
    for z in (-4, 8, 20):
        box("end sight", (x, 10.01, z, x+.55, 10.07, z+.55), "brass", "Details")

# Thin framing on the inset apron panels makes the cabinet read as joinery
# instead of one dark slab. The ornaments sit just outside the inset faces.
for z0, z1 in ((-15.55, -15.38), (31.38, 31.55)):
    for y0, y1 in ((4.0, 4.18), (6.58, 6.76)):
        box("long panel trim", (-28, y0, z0, 44, y1, z1), "rail", "Details")
    for x in (-8, 16):
        box("long panel divider", (x, 4.18, z0, x+.55, 6.58, z1), "wood", "Details")
for x0, x1 in ((-31.55, -31.38), (47.38, 47.55)):
    for y0, y1 in ((4.0, 4.18), (6.58, 6.76)):
        box("end panel trim", (x0, y0, -12, x1, y1, 28), "rail", "Details")

# Raise the playing bed and grow the legs without increasing the footprint.
# The 3D cue-ball height and block collision shape use the same new scale.
boxes = [(name, (a, b*1.9, c, d, e*1.9, f) if group == "Legs"
          else (a, b+3.2, c, d, e+3.2, f), material, group)
         for name, (a,b,c,d,e,f), material, group in boxes]

# Every opening must remain clear above its recessed liner. This also catches
# hidden cabinet/trim boards that look like a wooden plug from above.
for p in pockets:
    for fraction in (0, .5, .85):
        for angle in range(0, 360, 15):
            x = p["x"] + p["radius"] * fraction * math.cos(math.radians(angle))
            z = p["z"] + p["radius"] * fraction * math.sin(math.radians(angle))
            for name, (a,b,c,d,e,f), _, _ in boxes:
                if a < x < d and c < z < f and e > 11.01:
                    raise ValueError(f"Pocket covered by {name} at {x}, {z}")
    # The outside rail must remain intact behind the round hole.
    x = p["x"]
    z = -15.75 if p["z"] < 0 else 31.75
    assert any(a <= x <= d and c <= z <= f and e >= 13.2
               for _, (a,b,c,d,e,f), material, _ in boxes if material == "rail"), "Missing pocket back wall"

assert len(boxes) <= 350, "Static model exceeded its geometry budget"
assert all(-32 <= a < d <= 48 and 0 <= b < e <= 16 and -16 <= c < f <= 32
           for _, (a,b,c,d,e,f), _, _ in boxes), "Invalid model bounds"

# Same-facing coplanar faces cause depth flicker. Catch them before export.
axes = ((0, 3), (1, 4), (2, 5))
for index, (name, first, _, _) in enumerate(boxes):
    for other_name, second, _, _ in boxes[index+1:]:
        for axis, (minimum, maximum) in enumerate(axes):
            projected = [d for d in range(3) if d != axis]
            for face in (minimum, maximum):
                if first[face] != second[face]:
                    continue
                overlap = 1.0
                for dimension in projected:
                    lo, hi = axes[dimension]
                    overlap *= max(0, min(first[hi], second[hi]) - max(first[lo], second[lo]))
                if overlap > 0.01:
                    raise ValueError(f"Coplanar faces: {name} / {other_name}")

def faces(material, bounds, boundary=None):
    a, b, c, d, e, f = bounds
    # Continuous planar mapping: cut-out strips and cell splits retain exactly
    # the same grain instead of stretching an entire texture over every sliver.
    u0, u1 = (a+32)/5, (d+32)/5
    v0, v1 = (c+16)/3, (f+16)/3
    uv = {"up": [u0, v0, u1, v1], "down": [u0, 16-v1, u1, 16-v0],
          "north": [16-u1, 16-e, 16-u0, 16-b], "south": [u0, 16-e, u1, 16-b],
          "west": [v0, 16-e, v1, 16-b], "east": [16-v1, 16-e, 16-v0, 16-b]}
    outside = {"north": c == -16, "south": f == 32,
               "west": a == -32, "east": d == 48}
    result = {}
    for side, coords in uv.items():
        if boundary is not None and not boundary[side]:
            continue
        texture = material
        # Continuous dark facing under the wooden rail cap and inside wells.
        # Texture every step face alike; testing just its midpoint leaves a
        # zebra pattern where long strips meet the circular cut-out.
        if material == "rail" and b >= 11.3 and side in outside and not outside[side]:
            texture = "pocket"
        result[side] = {"texture": "#"+texture, "uv": [round(n, 5) for n in coords]}
    return result

def model(elements):
    return {"ambientocclusion": True,
            "textures": {**{k: "poolbilliards:block/"+v for k, v in TEXTURES.items()},
                         "particle": "poolbilliards:block/pool_felt"},
            "elements": elements}

def write(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False)+"\n", encoding="utf-8")

# Export the same rail rectangles and well arcs for server physics. Model
# coordinates map through the exact transform used by the ball renderer.
write(ASSETS / "table_geometry.json", {
    "pockets": pockets,
    "rails": [[a, c, d, f] for name, (a,b,c,d,e,f), material, group in boxes
              if group in ("Rails", "Pockets") and material in ("rail", "felt")]
})

# Split on cell borders, omitting the newly created internal faces.
for index, (cx, cz) in enumerate(FOOTPRINT):
    elements = []
    x0, x1, z0, z1 = cx*16, (cx+1)*16, cz*16, (cz+1)*16
    for name, (a,b,c,d,e,f), material, _ in boxes:
        lo_x, hi_x, lo_z, hi_z = max(a,x0), min(d,x1), max(c,z0), min(f,z1)
        if hi_x <= lo_x or hi_z <= lo_z:
            continue
        boundary = {"up": True, "down": True, "north": lo_z == c,
                    "south": hi_z == f, "west": lo_x == a, "east": hi_x == d}
        elements.append({"name": name, "from": [round(lo_x-x0,4), b, round(lo_z-z0,4)],
                         "to": [round(hi_x-x0,4), e, round(hi_z-z0,4)],
                         "faces": faces(material, (lo_x,b,lo_z,hi_x,e,hi_z), boundary)})
    write(MODELS / f"billiards_table_part_{index}.json", model(elements))

# Miniature of the entire table for the inventory icon.
item = [{"name": name, "from": [(a+32)/5,b,(c+16)/3],
         "to": [(d+32)/5,e,(f+16)/3], "faces": faces(material, (a,b,c,d,e,f))}
        for name, (a,b,c,d,e,f), material, _ in boxes]
write(MODELS/"billiards_table_item.json", model(item))
def variants(index):
    return {f"facing={direction}": {"model": f"poolbilliards:block/billiards_table_part_{index}",
                                    "y": angle}
            for direction,angle in (("north",0),("east",90),("south",180),("west",270))}
write(ASSETS/"blockstates/billiards_table.json", {"variants": variants(FOOTPRINT.index((0,0)))})
write(ASSETS/"blockstates/billiards_table_part.json",
      {"variants": {f"part={i},{key}": value for i in range(len(FOOTPRINT))
                    for key,value in variants(i).items()}})

# Blockbench free format supports the complete 80x48-unit model. Embed textures
# so the .bbmodel remains editable even outside the repository.
keys = list(TEXTURES)
textures = []
for index,key in enumerate(keys):
    filename = TEXTURES[key]+".png"
    encoded = base64.b64encode((ASSETS/"textures/block"/filename).read_bytes()).decode("ascii")
    textures.append({"name": filename, "id": str(index),
                     "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, filename)),
                     "mode": "bitmap", "source": "data:image/png;base64,"+encoded,
                     "width": 64, "height": 64, "uv_width": 64, "uv_height": 64,
                     "visible": True})
elements, groups = [], {}
for name,bounds,material,group in boxes:
    eid = str(uuid.uuid5(uuid.NAMESPACE_URL, f"pool/{group}/{name}/{bounds}"))
    elements.append({"name": name, "type": "cube", "box_uv": False,
                     "from": list(bounds[:3]), "to": list(bounds[3:]),
                     "origin": [8,0,8], "uuid": eid,
                     "faces": {side: {"uv": [n*4 for n in face["uv"]],
                                      "texture": keys.index(face["texture"][1:])}
                               for side, face in faces(material, bounds).items()}})
    groups.setdefault(group, []).append(eid)
outliner = [{"name": name, "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL,"pool/group/"+name)),
             "origin": [8,0,8], "rotation": [0,0,0], "children": children,
             "isOpen": True, "visibility": True, "export": True}
            for name,children in groups.items()]
write(ROOT/"billiards-table.bbmodel",
      {"meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
       "name": "Billiards Table 5x3", "resolution": {"width": 64, "height": 64},
       "elements": elements, "outliner": outliner, "textures": textures})
print(f"Blockbench: {len(boxes)} cubes; runtime: {len(FOOTPRINT)} block models")
