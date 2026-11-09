"""Merge a Studio RP delta and align existing instances on a new world copy."""

import argparse
from collections import Counter, defaultdict
from copy import deepcopy
from hashlib import file_digest, sha256
from math import cos, floor, isclose, isfinite, radians, sin
from pathlib import Path
from zipfile import ZipFile
import json
import os
import sys

from map_io.studio_save import list_tag
from map_io.world_io import NbtFile, RegionFile, Tag, compound
from update_city_followup_map import copy_world


sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "Dreamwalker-Collision-Studio/v22"))
from check_export import read_document, validate

LAYOUT = "bloodborne_dw/studio-layouts-v22.json"
ZERO = (0, 0, 0)
CAGE_HIDDEN_BONES = {"cage_obj_1": ["main"], "cage_obj_2": ["main_2"], "cage_obj_3": ["main"]}


def digest(path):
    with path.open("rb") as stream:
        return file_digest(stream, "sha256").hexdigest()


def write_json(path, value):
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write("\n")


def volumes_changed(old, new):
    previous = {item["id"]: item for item in old}
    edited = {item["id"]: item for item in new}
    return {"added": len(edited.keys() - previous.keys()), "removed": len(previous.keys() - edited.keys()),
            "updated": sum(previous[key] != edited[key] for key in previous.keys() & edited.keys())}


def share_dog_cage_colliders(project):
    shared = {}
    for model in project["models"]:
        registry = model["registryId"]
        bones = CAGE_HIDDEN_BONES.get(registry.removeprefix("bloodborne_rp:"))
        if bones is None:
            continue
        states = {state["key"]: state for state in model["states"]}
        source = states["default_dogs"]
        target = states.get("default_empty")
        if target is None:
            target = {
                **source, "key": "default_empty", "selector": {"DogsVisible": False},
                "pose": {**source["pose"], "hiddenBones": bones}, "modelOffset": list(ZERO), "seats": [],
            }
            model["states"].append(target)
            edits = {"addedState": True}
        else:
            edits = volumes_changed(target["colliders"], source["colliders"])
        target["colliders"] = deepcopy(source["colliders"])
        shared[registry] = {"reference": "default_dogs", "appliedTo": "default_empty", **edits}
    return shared


def merge(baseline, delta):
    merged = deepcopy(baseline)
    models = {model["registryId"]: model for model in merged["models"]}
    changes = {}
    for model in delta["models"]:
        registry = model["registryId"]
        if model["kind"] != "rp" or not registry.startswith("bloodborne_rp:"):
            raise ValueError(f"Expected a Studio RP prop: {registry}")
        target = models.get(registry)
        if target is None:
            target = {key: value for key, value in model.items() if key not in ("states", "anchorCell")}
            target["states"] = []
            models[registry] = target
            merged["models"].append(target)
        model_report = {"states": {}}
        if "anchorCell" in model and model["anchorCell"] != target.get("anchorCell", list(ZERO)):
            model_report["anchorCell"] = [target.get("anchorCell", list(ZERO)), model["anchorCell"]]
            target["anchorCell"] = model["anchorCell"]
        indexes = {state["key"]: index for index, state in enumerate(target["states"])}
        for state in model["states"]:
            if any(any(box["rotation"]) for box in state["colliders"]):
                raise ValueError(f"Rotated RP collider requires a different runtime shape: {registry} {state['key']}")
            key = state["key"]
            if set(state["selector"]) not in (set(), {"Open"}, {"DogsVisible"}):
                raise ValueError(f"Unsupported RP state selector for {registry} {key}")
            prior = target["states"][indexes[key]] if key in indexes else None
            if prior == state:
                continue
            if prior is not None and prior["selector"] != state["selector"]:
                raise ValueError(f"Selector changed for an existing pose: {registry} {key}")
            if prior is None:
                target["states"].append(state)
            else:
                target["states"][indexes[key]] = state
            model_report["states"][key] = {
                "selector": state["selector"], "hadAuthoredLayout": prior is not None,
                "modelOffset": [(prior or {}).get("modelOffset", list(ZERO)), state["modelOffset"]],
                "colliderModes": dict(Counter(box["mode"] for box in state["colliders"])),
                "seats": len(state["seats"]),
                **({"colliderChanges": volumes_changed(prior["colliders"], state["colliders"]),
                    "seatChanges": volumes_changed(prior["seats"], state["seats"])} if prior is not None else {}),
            }
        if model_report["states"] or "anchorCell" in model_report:
            changes[registry] = model_report
    merged["source"] = delta["source"]
    merged["createdAt"] = delta["createdAt"]
    return merged, changes


def entity_state(entity):
    registry = entity["id"].value
    opened = entity.get("Open")
    if opened is not None and opened.type == 1:
        is_open = bool(opened.value)
    else:
        is_open = (registry in {"bloodborne_rp:main_gate", "bloodborne_rp:small_gate"}
                   and entity.get("EntityState", Tag(3, 0)).value != 0)
    visible = entity.get("DogsVisible")
    dogs = visible is None or visible.type != 1 or bool(visible.value)
    return is_open, dogs


def selected(model, entity):
    if model is None:
        return None
    is_open, dogs = entity_state(entity)
    for selector in ({"DogsVisible": dogs}, {"Open": is_open}, {}):
        for state in model["states"]:
            if state["selector"] == selector:
                return state
    return None


def vector(tag):
    return tuple(item.value for item in tag.value)


def art_offset(model, pose):
    anchor = model.get("anchorCell", ZERO) if model is not None else ZERO
    offset = pose["modelOffset"] if pose is not None else ZERO
    return tuple(value / 16 - cell for value, cell in zip(offset, anchor))


def transform(local, rotation, scale):
    yaw, pitch = rotation
    x, y, z = (value * scale for value in local)
    pitch = radians(pitch)
    yaw = radians(yaw - 90)
    x, y = x * cos(pitch) - y * sin(pitch), x * sin(pitch) + y * cos(pitch)
    return x * cos(yaw) + z * sin(yaw), y, -x * sin(yaw) + z * cos(yaw)


def entity_uid(tag):
    uid = compound(tag).get("UUID")
    return tuple(uid.value) if uid is not None and uid.type == 11 else None


def inventory(world, wanted):
    result = {}
    needles = tuple(registry.encode() for registry in wanted)
    for directory, subdirs, files in os.walk(world, followlinks=False):
        folder = Path(directory)
        subdirs[:] = [child for child in subdirs if not (folder / child).is_symlink()
                      and not (folder / child).is_junction()]
        if folder.name != "entities":
            continue
        dimension = folder.parent.relative_to(world)
        for filename in sorted(files):
            path = folder / filename
            if not filename.startswith("r.") or path.suffix != ".mca" or path.is_symlink() or not path.stat().st_size:
                continue
            _, rx, rz, _ = filename.split(".")
            for chunk in RegionFile.open(path).chunks():
                raw = chunk.raw_nbt()
                if not any(needle in raw for needle in needles):
                    continue
                fields = compound(chunk.nbt().root)
                key = int(rx) * 32 + chunk.x, int(rz) * 32 + chunk.z
                if tuple(fields["Position"].value) != key:
                    raise ValueError(f"Mismatched entity chunk at {path} {key}")
                pending = [(tag, False) for tag in fields["Entities"].value]
                while pending:
                    tag, passenger = pending.pop()
                    entity = compound(tag)
                    if "Passengers" in entity:
                        pending.extend((child, True) for child in entity["Passengers"].value)
                    if entity["id"].value not in wanted:
                        continue
                    uid = entity_uid(tag)
                    if uid is None or uid in result:
                        raise ValueError(f"Missing or repeated UUID at {path} {key}")
                    result[uid] = (dimension, key, tag, fields["DataVersion"].value, passenger)
    return result


def update_regions(world, removals, additions, versions):
    regions = {}
    for dimension, cx, cz in sorted(removals.keys() | additions.keys()):
        path = world / dimension / "entities" / f"r.{cx // 32}.{cz // 32}.mca"
        if path not in regions:
            regions[path] = RegionFile.open(path) if path.is_file() else RegionFile()
        region = regions[path]
        chunk = region.get_chunk(cx % 32, cz % 32)
        key = dimension, cx, cz
        if chunk is None:
            nbt = NbtFile("", Tag(10, {"DataVersion": Tag(3, versions[key]), "Position": Tag(11, [cx, cz])}))
        else:
            nbt = chunk.nbt()
        fields = compound(nbt.root)
        pending = dict(removals.get(key, {}))
        entities = []
        for tag in fields.get("Entities", list_tag()).value:
            next_tag = pending.pop(entity_uid(tag), tag)
            if next_tag is not None:
                entities.append(next_tag)
        if pending:
            raise ValueError(f"Missing original entity UUIDs in {key}")
        fields["Entities"] = list_tag([*entities, *additions.get(key, [])])
        region.set_chunk(cx % 32, cz % 32, nbt, compression=chunk.compression if chunk else 2,
                         timestamp=chunk.timestamp if chunk else None)
    for path, region in regions.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        region.save(path)
    return sorted(path.relative_to(world).as_posix() for path in regions)


def patch_world(source, output, baseline, merged, changes, assets):
    old = {model["registryId"]: model for model in baseline["models"]}
    new = {model["registryId"]: model for model in merged["models"]}
    before = inventory(source, changes)
    expected = dict(before)
    updated, stock = Counter(), Counter()
    removals, additions = defaultdict(dict), defaultdict(list)
    versions, moves = {}, []
    for uid, (dimension, chunk, tag, version, passenger) in before.items():
        entity = compound(tag)
        registry = entity["id"].value
        previous = selected(old.get(registry), entity)
        current = selected(new[registry], entity)
        change = changes[registry]
        is_open, dogs = entity_state(entity)
        if current is not None:
            state_key = current["key"]
        elif registry.split(":", 1)[1] in CAGE_HIDDEN_BONES:
            state_key = "default_dogs" if dogs else "default_empty"
        else:
            state_key = "open" if is_open else "closed"
        if state_key in change["states"] or "anchorCell" in change:
            updated[registry, state_key] += 1
        else:
            # A working state absent from this delta retains its prior behavior.
            stock[registry, state_key] += 1
            continue
        rotation = vector(entity["Rotation"])
        scale = entity.get("Scale", Tag(5, 1)).value
        if not isfinite(scale) or not 0 <= scale <= 8:
            raise ValueError(f"Unexpected entity scale at {uid}")
        scale *= assets[registry.split(":", 1)[1]]["scale"]
        old_offset = transform(art_offset(old.get(registry), previous), rotation, scale)
        new_offset = transform(art_offset(new[registry], current), rotation, scale)
        if all(isclose(a, b, rel_tol=0, abs_tol=1e-10) for a, b in zip(old_offset, new_offset)):
            continue
        if passenger or "Passengers" in entity:
            raise ValueError(f"Cannot independently re-anchor a mounted object: {uid}")
        pos = vector(entity["Pos"])
        fixed = tuple(p + a - b for p, a, b in zip(pos, old_offset, new_offset))
        if not all(isclose(p + a, q + b, rel_tol=0, abs_tol=1e-8)
                   for p, a, q, b in zip(pos, old_offset, fixed, new_offset)):
            raise ValueError(f"Art origin shifted at {uid}")
        target = floor(fixed[0]) // 16, floor(fixed[2]) // 16
        replacement = deepcopy(tag)
        compound(replacement)["Pos"] = list_tag([Tag(6, value) for value in fixed], 6)
        origin_key, target_key = (dimension, *chunk), (dimension, *target)
        removals[origin_key][uid] = replacement if chunk == target else None
        if chunk != target:
            additions[target_key].append(replacement)
            versions[target_key] = version
        expected[uid] = (dimension, target, replacement, version, False)
        moves.append({"uuid": uid, "model": registry, "state": state_key, "dimension": dimension.as_posix(),
                      "before": pos, "after": fixed, "previousArt": old_offset, "newArt": new_offset})
    copy_world(source, output)
    files = update_regions(output, removals, additions, versions)
    if inventory(output, changes) != expected:
        raise ValueError("Saved RP positions or entity metadata differ from the planned migration")
    return {"source": str(source), "present": len(before), "updated": sum(updated.values()),
            "matched": [[*key, count] for key, count in sorted(updated.items())],
            "unlisted": [[*key, count] for key, count in sorted(stock.items())],
            "moved": moves, "changedEntityRegions": files}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ("project", "studio", "baseline-jar", "world", "output-world", "layouts-output", "effective-project", "report"):
        parser.add_argument("--" + flag, type=Path, required=True)
    args = parser.parse_args()
    if any(path.exists() for path in (args.output_world, args.layouts_output, args.effective_project, args.report)):
        raise ValueError("Choose fresh migration output paths")
    delta = read_document(args.project, "dreamwalker-collisions.json")
    studio = read_document(args.studio, "catalogue.json")
    stats = validate(delta, studio)
    if delta.get("customAssets") or delta.get("editor", {}).get("excludedModels"):
        raise ValueError("Expected a reviewed Codex export for existing RP props")
    if digest(args.baseline_jar) != delta["source"]["jarSha256"]:
        raise ValueError("Base JAR does not match the editor")
    with ZipFile(args.baseline_jar) as jar:
        raw = jar.read(LAYOUT)
        assets = json.loads(jar.read("assets/bloodborne_rp/catalog.json"))
    if sha256(raw).hexdigest() != delta["source"]["appliedLayoutsSha256"]:
        raise ValueError("Base layouts do not match the editor")
    baseline = json.loads(raw)
    shared = share_dog_cage_colliders(delta)
    effective_stats = validate(delta, studio)
    merged, changes = merge(baseline, delta)
    world = patch_world(args.world, args.output_world, baseline, merged, changes, assets)
    write_json(args.layouts_output, merged)
    write_json(args.effective_project, delta)
    report = {"status": "PASS", "archiveSha256": digest(args.project), "archive": stats, "effective": effective_stats,
              "mergedModels": len(merged["models"]), "mergedStates": sum(len(m["states"]) for m in merged["models"]),
              "mergedSha256": digest(args.layouts_output), "sharedDogCages": shared, "changes": changes, "world": world}
    write_json(args.report, report)
    print(json.dumps({key: value for key, value in report.items() if key not in ("changes", "world")}))
    print(f"Saved objects with new layouts: {world['updated']}; compensated positions: {len(world['moved'])}")


if __name__ == "__main__":
    main()
