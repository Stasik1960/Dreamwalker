"""Append missing straight wall poses without changing any existing variant IDs."""
from copy import deepcopy
import json
from pathlib import Path

FAMILIES = ("source_assembly_brick_wall", "source_andesite_slab_top", "source_cut_copper")

def generate(base, resources, profile_resources):
    resources, profile_resources = Path(resources), Path(profile_resources)
    def read(path):
        return json.loads(base.read(path))
    def save(path, obj):
        target = resources / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(obj, ensure_ascii=False, separators=(",", ":")), encoding="utf8")
    def profile(path):
        return json.loads((profile_resources / path).read_text(encoding="utf8"))
    mapping_path = "bloodborne_dw/stone-fence-connections.json"
    compact_path = "bloodborne_dw/imported-compact-v127.json"
    mapping, compact = profile(mapping_path), profile(compact_path)
    entries = {item["registryId"]: item for item in compact["types"]}
    # Canonical no-post wall geometry; all wall materials share these dimensions.
    brick = entries["bloodborne_dw:source_assembly_brick_wall"]
    wall_shape = deepcopy(brick["shapes"]["87"])
    for name in FAMILIES:
        registry = "bloodborne_dw:" + name
        spec_path = "bloodborne_dw/composite/" + name + ".json"
        state_path = "assets/bloodborne_dw/blockstates/" + name + ".json"
        spec = profile(spec_path)
        blockstate = read(state_path)
        entry = entries[registry]
        for group in mapping["families"][registry]["groups"]:
            if "straight" in group:
                continue
            old = group["poses"][2]
            pose = len(spec["variants"])
            limit = 480 if name == "source_assembly_brick_wall" else 128
            if pose >= limit:
                raise ValueError(f"{registry}: no free art_bank state for straight pose {pose}")
            variant = deepcopy(spec["variants"][old])
            for geometry in variant["poses"].values():
                geometry["parts"] = [part for part in geometry["parts"] if not part["model"].endswith("/" + group["post"]) ]
                if len(geometry["parts"]) != 2:
                    raise ValueError(f"{registry}: expected two opposing sides in pose {old}")
                geometry["collision"] = [{"from": [5, 0, 0], "to": [11, 24, 16]}]
                geometry["selection"] = [{"from": [5, 0, 0], "to": [11, 14, 16]}]
                # Preserve per-rotation shape data from a reviewed straight wall.
                original = profile("bloodborne_dw/composite/source_assembly_brick_wall.json")["variants"][87]["poses"]["closed"]
                for key in ("simpleCollisionByRotation", "simpleSelectionByRotation"):
                    if key in original: geometry[key] = deepcopy(original[key])
            spec["variants"].append(variant)
            entry["poses"].append(pose)
            entry["shapes"][str(pose)] = deepcopy(wall_shape)
            group["straight"] = pose
            for part in list(blockstate["multipart"]):
                when = part.get("when", {})
                if when.get("variant") != str(old % 16) or when.get("art_bank", "0") != str(old // 16):
                    continue
                apply = part.get("apply", {})
                if isinstance(apply, dict) and apply.get("model", "").endswith("/" + group["post"]):
                    continue
                added = deepcopy(part)
                added["when"]["variant"] = str(pose % 16)
                added["when"]["art_bank"] = str(pose // 16)
                blockstate["multipart"].append(added)
        save(spec_path, spec)
        save(state_path, blockstate)
    save(mapping_path, mapping)
    save(compact_path, compact)
