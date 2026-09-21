"""Deterministic FIVE-family proof of concept, not a palette regenerator.

Only writes contracts-v2.json, transform-v2.json and docs/contract-poc-v2.json.
Meshes, definitions, textures, old geometry and migration files are read-only.
Gameplay primitives below are authored choices, NEVER model element AABBs.
"""
from __future__ import annotations
import gzip
import hashlib
import itertools
import json
from pathlib import Path
import zipfile
from logical_contract_v2 import MATRICES, rotate_box, rotate_cell, master_origin, load_contracts

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources/bloodborne_blocks/logical"
IDS = ("o_dead_tree_planter", "o_cases_0", "o_wall_deco_1", "o_iron_gate", "o_iron_railing")
FACINGS = ("north", "east", "south", "west")


def component(ident, offset=(0,0,0), **properties):
    return {"id": "minecraft:"+ident, "properties": properties, "offset": list(offset)}


def family_config(ident, props):
    """Explicit physics and interaction cells in canonical north coordinates."""
    if ident == "o_dead_tree_planter":
        boxes = [[-1,-1,-1,2,0,2], [.25,0,.25,.75,7,.75]]
        cells = [(x,-1,z) for x in range(-1,2) for z in range(-1,2)] + [(0,y,0) for y in range(7)]
        return "TRUNK", [0,-1,0], [-1,-1,-1,2,8,2], boxes, cells
    if ident == "o_cases_0":
        return "SIMPLE_BOX", [0,0,0], [-.5,0,-.5,1.5,1.125,1.75], [[0,0,0,1,1,1]], [(0,0,0)]
    if ident == "o_wall_deco_1":
        return "NONE", [0,0,0], [-1,-1,.0625,2,2,1], [], [(0,0,0)]
    if ident == "o_iron_gate":
        if props["open"] == "true":
            boxes = [[-1.0625,-.8125,.5,-.9375,5,2], [1.9375,-.8125,.5,2.0625,5,2]]
            # Explicit hinge strips (two cell columns per thin leaf at its seam).
            cells = [(x,y,z) for x in (-2,-1,1,2) for y in range(-1,5) for z in (0,1)] + [(0,0,0)]
            selection = [-1.0625,-.8125,.5,2.0625,5,2]
        else:
            boxes = [[-1,-.8125,.4375,.5,5,.5625], [.5,-.8125,.4375,2,5,.5625]]
            cells = [(x,y,0) for x in (-1,0,1) for y in range(-1,5)]
            selection = [-1,-.8125,.4375,2,5,.5625]
        return "GATE", [0,-1,0], selection, boxes, cells
    # Connected shapes are a post plus independently enabled arms, NOT polygons.
    boxes = [[.4375,0,.4375,.5625,1.4375,.5625]]
    arms = {"north": [.4375,0,0,.5625,1.4375,.5], "east": [.5,0,.4375,1,1.4375,.5625],
            "south": [.4375,0,.5,.5625,1.4375,1], "west": [0,0,.4375,.5,1.4375,.5625]}
    boxes += [b for name,b in arms.items() if props[name] == "true"]
    return "FENCE", [0,0,0], [0,0,0,1,1.4375,1], boxes, [(0,0,0),(0,1,0)]


def source_patterns(ident, props, rotation):
    if ident == "o_dead_tree_planter":
        # Vanilla wool has no orientation; vanilla default blockstates use model yaw0.
        return [[component("white_wool"),component("orange_wool",(0,3,0)),component("magenta_wool",(0,6,0))]] if rotation == 0 else []
    if ident == "o_cases_0":
        return [[component("oxidized_cut_copper_stairs", facing=FACINGS[(rotation//90+2)%4], half="bottom", shape="straight", waterlogged="false")]]
    if ident == "o_wall_deco_1":
        return [[component("dead_tube_coral_wall_fan", facing=props["facing"], waterlogged="false")]]
    if ident == "o_iron_gate":
        if props["open"] == "true" or rotation not in (0,90):
            return []  # Axis z/x encode yaw0/90, not four distinct vanilla states.
        axis = "z" if rotation == 0 else "x"
        return [[component("stripped_jungle_log",axis=axis),component("stripped_acacia_log",(0,3,0),axis=axis)]]
    # South/west arms are weighted alternatives in source pack. Fail closed until
    # exact original Minecraft positional RNG is reproduced; do not pick array[0].
    if props["south"] == "true" or props["west"] == "true":
        return []
    return [[component("iron_bars", **props, waterlogged="false")]]


def build():
    transform = {"schemaVersion":2,"rotations":MATRICES,"vectors":[]}
    for rotation in (0,90,180,270):
        for anchor in ([0,0,0],[0,-1,0],[2,-1,-3]):
            placement = [103,65,-47]
            transform["vectors"].append({"rotation":rotation,"placement_cell":placement,"anchor_cell":anchor,
                                         "expected_master":list(master_origin(placement,anchor,rotation,transform))})
    definitions = {d["id"]:d for d in json.loads((RES/"definitions.json").read_text(encoding="utf-8"))["blocks"]}
    with gzip.open(RES/"meshes.json.gz", "rt", encoding="utf-8") as f:
        meshes = json.load(f)
    old_geometry = json.loads((RES/"geometry.json").read_text(encoding="utf-8"))
    pack_path = ROOT/"reference-inputs/source-resource-pack.zip"
    with zipfile.ZipFile(pack_path) as pack:
        evidence_paths = ["assets/minecraft/blockstates/"+n+".json" for n in
                          ("oxidized_cut_copper_stairs","dead_tube_coral_wall_fan","stripped_jungle_log","stripped_acacia_log","iron_bars")]
        pack_evidence = {n:json.loads(pack.read(n)) for n in evidence_paths}
        # Verify exact non-random visual applications, not inferred object boundaries.
        for rotation, facing in enumerate(FACINGS):
            cases = pack_evidence[evidence_paths[0]]["variants"][f"facing={FACINGS[(rotation+2)%4]},half=bottom,shape=straight"]
            wall = pack_evidence[evidence_paths[1]]["variants"][f"facing={facing}"]
            assert cases["model"].endswith("/cases_0") and cases.get("y",0)==rotation*90
            assert wall["model"].endswith("/wall_deco_1") and wall.get("y",0)==rotation*90
        for carrier, model_name in (("stripped_jungle_log","gate_bottom"),("stripped_acacia_log","gate_top")):
            variants = pack_evidence[f"assets/minecraft/blockstates/{carrier}.json"]["variants"]
            for axis,yaw in (("z",0),("x",90)):
                assert variants[f"axis={axis}"]["model"].endswith("/"+model_name) and variants[f"axis={axis}"].get("y",0)==yaw
        for wool in ("white_wool","orange_wool","magenta_wool"):
            assert f"assets/minecraft/models/block/{wool}.json" in pack.namelist()
            assert f"assets/minecraft/blockstates/{wool}.json" not in pack.namelist()
    data = {"schemaVersion":2,"transform_contract":"transform-v2.json","families":[]}
    report = {"schemaVersion":2,"scope":"five-family POC; no full world conversion",
              "sourceWorldSha256":"4353737d536677469d3b895e3515496ab64fab7b224e43428eb96e8c09724a51",
              "sourcePackSha256":hashlib.sha256(pack_path.read_bytes()).hexdigest(),
              "preservedFiles":{name:hashlib.sha256((RES/name).read_bytes()).hexdigest() for name in ("definitions.json","geometry.json","migration.json","meshes.json.gz")},
              "packApplications":pack_evidence,"families":[],
              "limitations":["No directional state exists for source wool: direct tree matching supports yaw0 only; manual supports all four.",
                             "Source gate axis distinguishes yaw0/90 only; open/south/west states are runtime states, not invented original patterns.",
                             "Source iron bars with south/west weighted arms are deliberately not matched until positional RNG reproduction.",
                             "Book models are weighted alternatives; cases_0 selected as deterministic complex-decor representative.",
                             "Source patterns are authored POC boundaries, not proof all map instances follow them. No discovery or coverage claim.",
                             "Existing master-section renderer unchanged; long meshes still have section culling/lighting limitations.",
                             "Old logical saves may retain obsolete schema1 helpers; do not deploy to existing maps until explicit old-helper migration is implemented."]}
    for ident in IDS:
        definition = definitions[ident]
        family = {"id":ident,"canonical_anchor":{"cell":None,"pivot":[.5,0,.5]},
                  "placement_policy":"WALL_ADJACENT" if ident=="o_wall_deco_1" else "FLOOR",
                  "rotations":[0,90,180,270],"mirror_policy":"ROTATE_ONLY","states":{}}
        if ident == "o_dead_tree_planter":
            family["collision_justification"] = "Two authored primitives: broad stone base plus narrow trunk; branches never reserve cells."
        rows = []
        for key, mesh_id in definition["models"].items():
            props = dict(p.split("=",1) for p in key.split(","))
            rotation = FACINGS.index(props["facing"])*90 if "facing" in props else 0
            policy, anchor, selection, boxes, cells = family_config(ident,props)
            family["collision_policy"] = policy
            family["canonical_anchor"]["cell"] = anchor
            cells = sorted(rotate_cell(c,rotation,transform) for c in cells)
            vertices = [v for poly in meshes[mesh_id]["polygons"] for v in poly["vertices"]]
            bounds = [min(v[i] for v in vertices) for i in range(3)]+[max(v[i] for v in vertices) for i in range(3)]
            state = {"rotation":rotation,"render_mesh":{"id":mesh_id,"bounds":bounds,"offset":[0,0,0]},
                     "selection_footprint":{"boxes":[rotate_box(selection,rotation,transform)]},
                     "collision_footprint":{"boxes":[rotate_box(b,rotation,transform) for b in boxes]},
                     "interaction_footprint":{"cells":[list(c) for c in cells]},
                     "migration_source_pattern":[{"components":p} for p in source_patterns(ident,props,rotation)]}
            family["states"][key] = state
            old = old_geometry["blocks"][ident]["states"][key]
            old = old_geometry["profiles"][old["ref"]] if "ref" in old else old
            rows.append({"state":key,"oldVisualCells":len(old["cells"]),"newInteractionCells":len(cells),
                         "oldCollisionBoxes":sum(len(c.get("collision",[])) for c in old["cells"].values()),
                         "newCollisionPrimitives":len(boxes),"selectionPrimitives":1,
                         "polygonsPreserved":len(meshes[mesh_id]["polygons"]),"directPatterns":len(state["migration_source_pattern"])})
        data["families"].append(family)
        report["families"].append({"id":ident,"states":rows})
    for path, value in ((RES/"transform-v2.json",transform),(RES/"contracts-v2.json",data),(ROOT/"docs/contract-poc-v2.json",report)):
        path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    load_contracts(RES)
    print("POC contracts: 5 families,",sum(len(f["states"]) for f in data["families"]),"states; existing palette/meshes unchanged")


if __name__ == "__main__":
    build()
