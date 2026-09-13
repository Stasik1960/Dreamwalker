"""Editable Blockbench source and static 5x3-block runtime models."""
import base64
import json
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/poolbilliards"
MODELS = ASSETS / "models/block"
TEXTURES = {"wood": "pool_wood", "rail": "pool_rail_top", "felt": "pool_felt",
            "leg": "pool_leg", "pocket": "pool_black", "brass": "pool_brass", "net": "pool_net"}
FOOTPRINT = [(x, z) for z in range(-1, 2) for x in range(-2, 3)]
boxes = []

def box(name, bounds, material, group):
    boxes.append((name, bounds, material, group))

# Six legs, inset wooden apron, open rail gaps and simple hanging nets follow
# the silhouette of the supplied reference. Cubes stay intentionally sparse.
box("cabinet", (-30, 3.3, -14, 46, 7.8, 30), "wood", "Cabinet")
box("lower moulding", (-31, 3, -15, 47, 3.55, 31), "rail", "Cabinet")
box("upper trim", (-31.2, 7.15, -15.2, 47.2, 7.8, 31.2), "rail", "Cabinet")
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
        box("leg collar", (x, 2.75, z, x+3.6, 3.35, z+3.6), "rail", "Legs")
box("slate", (-29, 7.8, -13, 45, 8.12, 29), "pocket", "Playfield")
box("green cloth", (-27.5, 8.12, -11.5, 43.5, 8.35, 27.5), "felt", "Playfield")
for z0, z1 in ((-16, -12), (28, 32)):
    for x0, x1 in ((-27, 5), (11, 43)):
        box("long rail", (x0, 8.1, z0, x1, 10, z1), "rail", "Rails")
        a, b = (-12.35, -11.45) if z0 < 0 else (27.45, 28.35)
        box("long cushion", (x0, 8.15, a, x1, 9.15, b), "felt", "Rails")
for x0, x1 in ((-32, -28), (44, 48)):
    box("end rail", (x0, 8.1, -11, x1, 10, 27), "rail", "Rails")
    a, b = (-28.35, -27.45) if x0 < 0 else (43.45, 44.35)
    box("end cushion", (a, 8.15, -11, b, 9.15, 27), "felt", "Rails")
for x0, x1 in ((-32, -27), (43, 48)):
    for z0, z1 in ((-16, -11), (27, 32)):
        box("corner pocket", (x0, 8.36, z0, x1, 8.65, z1), "pocket", "Pockets")
        box("corner net", (x0+.7, 5.1, z0+.7, x1-.7, 7.6, z1-.7), "net", "Pockets")
for z0, z1 in ((-16, -11.5), (27.5, 32)):
    box("side pocket", (5, 8.36, z0, 11, 8.65, z1), "pocket", "Pockets")
    box("side net", (6, 5.2, z0+.7, 10, 7.6, z1-.7), "net", "Pockets")
for x in (-20, -9, 0, 16, 25, 36):
    for z in (-14.2, 30.9):
        box("rail sight", (x, 10.01, z, x+.55, 10.07, z+.55), "brass", "Details")
for x in (-30.5, 46.5):
    for z in (-4, 8, 20):
        box("end sight", (x, 10.01, z, x+.55, 10.07, z+.55), "brass", "Details")

def faces(material, boundary=None):
    return {side: {"texture": "#"+material, "uv": [0, 0, 16, 16]}
            for side in ("up", "down", "north", "south", "west", "east")
            if boundary is None or boundary[side]}

def model(elements):
    return {"ambientocclusion": True,
            "textures": {**{k: "poolbilliards:block/"+v for k, v in TEXTURES.items()},
                         "particle": "poolbilliards:block/pool_felt"},
            "elements": elements}

def write(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False)+"\n", encoding="utf-8")

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
                         "faces": faces(material, boundary)})
    write(MODELS / f"billiards_table_part_{index}.json", model(elements))

# Miniature of the entire table for the inventory icon.
item = [{"name": name, "from": [(a+32)/5,b,(c+16)/3],
         "to": [(d+32)/5,e,(f+16)/3], "faces": faces(material)}
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
                     "faces": {side: {"uv": [0,0,16,16], "texture": keys.index(material)}
                               for side in ("north","east","south","west","up","down")}})
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
