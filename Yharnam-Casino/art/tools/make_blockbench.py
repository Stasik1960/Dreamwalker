"""Convert Blender's _bb_source.json into editable Blockbench 4.10 projects."""
from __future__ import annotations
import base64
import json
from pathlib import Path
from uuid import NAMESPACE_URL, uuid5

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
MODELS, TEX = ROOT / "models", ROOT / "textures"
SRC = ROOT / ".build" / "_bb_source.json"
ATLAS_SIZE = (512, 1024)


def uid(seed):
    return str(uuid5(NAMESPACE_URL, "bloodborne-slots/" + seed))


def atlas_for(style, colors, surfaces):
    atlas = Image.new("RGB", ATLAS_SIZE, (32, 32, 40))
    d = ImageDraw.Draw(atlas)
    regions = {}
    for key, part, (x,y) in (("reel","reel",(8,8)),("sign","marquee",(152,8))):
        with Image.open(TEX / f"{style}_{part}.png") as source:
            im = source.convert("RGB")
        w,h = im.size
        atlas.paste(im, (x,y))
        for bounds, loc, size in (
            ((0,0,1,h),(x-3,y),(3,h)), ((w-1,0,w,h),(x+w,y),(3,h)),
            ((0,h-1,w,h) if key=="reel" else (0,0,w,1),(x,y-3),(w,3)),
            ((0,0,w,1) if key=="reel" else (0,h-1,w,h),(x,y+h),(w,3)),
        ):
            atlas.paste(im.crop(bounds).resize(size), loc)
        regions[key] = (x,y,w,h)
    for index, role in enumerate(surfaces):
        x, y = 8+(index%3)*146, 552+(index//3)*146
        with Image.open(TEX/"surfaces"/f"{style}_{role}.png") as source:
            surface = source.convert("RGB")
        assert surface.size == (128,128)
        atlas.paste(surface,(x,y))
        regions[role] = (x,y,128,128)
    for index, (role,color) in enumerate((key,val) for key,val in colors.items() if key not in surfaces):
        x, y = 454, 545+index*43
        d.rectangle((x,y,x+31,y+31),fill="#"+color)
        regions[role] = (x+7,y+7,16,16)
    atlas.save(TEX / f"{style}_atlas.png")
    return regions


def bb_uv(uv, region):
    # Blender uses normalized bottom-left UVs; Blockbench uses top-left atlas pixels.
    x, y, w, h = region
    return [round(x + uv[0]*w, 4), round(y + (1-uv[1])*h, 4)]


def nested_group(gid, groups):
    g = groups[gid]; children = []
    for child in g.get("children", []):
        if child in groups: children.append(nested_group(child, groups))
        else: children.append(child)
    return {"name": g["name"], "uuid": g["uuid"], "origin": g.get("origin", [0,0,0]), "children": children}


def make_project(model):
    style = model["id"]
    textured = {"reel","sign",*model["surfaces"]}
    regions = atlas_for(style, model["colors"], model["surfaces"])
    width, height = ATLAS_SIZE
    elements = []
    for e in model.get("elements", []):
        faces = {}
        for fk, f in e.get("faces", {}).items():
            role = f["role"]
            points = f["uv"] if role in textured else [(0,0),(1,0),(0,1)]
            assert len(points) == len(f["vertices"])
            uvmap = {v: bb_uv(p,regions[role]) for v,p in zip(f["vertices"],points)}
            faces[fk] = {"vertices": f["vertices"], "uv": uvmap, "texture": 0}
        elements.append({"name": e["name"], "type": "mesh", "uuid": e["uuid"],
                         "origin": e.get("origin", [0,0,0]), "rotation": [0,0,0],
                         "shading": "flat", "visibility": True, "export": True,
                         "vertices": e.get("vertices", {}), "faces": faces})
    groups = model.get("groups", {})
    roots = [model.get("root")] if model.get("root") else []
    outliner = [nested_group(r, groups) for r in roots if r in groups]
    if not outliner: outliner = [e["uuid"] for e in elements]
    raw = TEX / f"{style}_atlas.png"
    embedded = "data:image/png;base64," + base64.b64encode(raw.read_bytes()).decode("ascii")
    tex = {"name": f"{style}_atlas.png", "relative_path": f"../textures/{style}_atlas.png", "source": embedded,
           "id": "0", "uuid": uid(style+"/atlas"), "mode": "bitmap", "internal": True,
           "saved": True, "width": width, "height": height, "uv_width": width, "uv_height": height, "visible": True}
    project = {"meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
               "name": model.get("name", style), "model_identifier": style,
               "resolution": {"width": width, "height": height}, "elements": elements,
               "outliner": outliner, "textures": [tex]}
    # Validation before writing: every face vertex, element and group reference must be resolvable.
    ids = {e["uuid"] for e in elements} | set(groups)
    for e in elements:
        for f in e["faces"].values():
            assert all(v in e["vertices"] for v in f["vertices"])
            assert all(0 <= u < width and 0 <= v < height for u,v in f["uv"].values())
    def check(node):
        if isinstance(node, str): assert node in ids, node
        else:
            assert node["uuid"] in ids, node["uuid"]
            for ch in node.get("children", []): check(ch)
    for n in outliner: check(n)
    (MODELS / f"{style}.bbmodel").write_text(json.dumps(project, separators=(",", ":")), encoding="utf8")


def main():
    if not SRC.is_file(): raise FileNotFoundError(SRC)
    data = json.loads(SRC.read_text(encoding="utf8"))
    if isinstance(data, dict): data = [data]
    for model in data: make_project(model)
    print("Wrote", len(data), "Blockbench projects")


if __name__ == "__main__": main()
