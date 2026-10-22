"""Extend the existing shared catalog from prepared owners and native pose definitions.

No world reads, original conversion, packing, or source-section decoding. The native
author remains responsible for exact live owner/foreign-membership eligibility.
"""
import argparse
import collections
import gzip
import hashlib
import importlib.util
import itertools
import json
from pathlib import Path
import time


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def canon(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def dump(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def digest(path):
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def sig(row):
    payload = row.get("payload") or {}
    return (row["registryId"], tuple(sorted(row.get("properties", {}).items())),
            tuple(payload.get("SourceShift", [0, 0, 0])), float(payload.get("MountY", 0)))


def pattern(row):
    return tuple(sorted((x["pose"], tuple(x["origin"])) for x in row["fragments"]))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", required=True, type=Path)
    parser.add_argument("--geometry", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--max-poses", type=int, default=4096)
    parser.add_argument("--max-recipes", type=int, default=32768)
    args = parser.parse_args()
    root, output = args.root.resolve(), args.output.resolve()
    receipt = read(root / ".handoff-transfer/RESTORE-RECEIPT.json")
    if receipt["status"] != "VERIFIED" or output.exists() or not output.is_relative_to(root / ".handoff-local/work"):
        raise RuntimeError("Verified restoration and a new local operation directory required")
    output.mkdir(parents=True)
    start = time.monotonic()
    original = root / "project"
    stage = original / "build/v1274-static-architecture-20261015"
    spec = importlib.util.spec_from_file_location("preserved_catalog_functions", stage / "build_static_catalog.py")
    legacy = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(legacy)
    base = read(stage / "surface-v2/static-architecture-catalog.json")
    poses = {x["key"]: x for x in base["poses"]}
    geometry = {x["key"]: x for x in read(stage / "surface-v2/native-geometry-with-whole.json")["poses"]}
    exported = read(args.geometry)
    if exported.get("status") != "PASS_NATIVE_PREPARED_FINITE_GEOMETRY_NOT_WORLD_ANALYSIS" or exported.get("worldCreatedOrOpened") is not False:
        raise RuntimeError("A successful world-free native definition export is required")
    geometry.update({x["key"]: x for x in exported["poses"]})
    excluded = collections.Counter()
    candidates = sorted(exported["poses"], key=lambda x: (-x.get("preparedOwnerCount", x.get("frequency", 0)), x["key"]))
    for native in candidates:
        key = native["key"]
        if key in poses:
            continue
        payload = native.get("payload") or {}
        props = native["properties"]
        if (native["openable"] or native["requiredSupport"] or native["functionalDoorPhysics"] or not native["fluidEmpty"]
                or props.get("open", "false") != "false" or props.get("profile", "base") != "base"
                or set(payload) - {"ImportedTypeVersion", "SourceShift", "MountY"} or payload.get("ImportedTypeVersion") != 1):
            excluded["FUNCTIONAL_OR_PRIVATE_POSE"] += 1
            continue
        if len(poses) >= args.max_poses:
            excluded["SHARED_POSE_BUDGET"] += 1
            continue
        try:
            bounds = legacy.visual_bounds(native)
        except (AssertionError, KeyError, ValueError) as error:
            excluded["VISUAL_BOUNDS_UNSUPPORTED"] += 1
            continue
        poses[key] = {"key": key, "registryId": native["registryId"], "properties": props,
                      "shift": payload.get("SourceShift", [0, 0, 0]), "mountY": native["mountY"],
                      "reviewedVisualBounds": bounds}
    signatures = {sig({"registryId": x["registryId"], "properties": x["properties"],
        "payload": {"SourceShift": x["shift"], "MountY": x["mountY"]}}): key for key, x in poses.items()}
    footprints = {key: [tuple(int(n) for n in row["cell"]) for row in geometry[key]["footprint"]] for key in poses}
    for key, cells in footprints.items():
        if (0, 0, 0) not in cells or len(cells) != len(set(cells)):
            raise RuntimeError(f"Unsupported native root footprint: {key}")
    prepared = original / "build/v1271-city-preparation/runtime"
    manifest = read(prepared / "manifest.json")
    private_roots = {(x["dimension"], tuple(x["ownerPosition"])) for x in manifest.get("originalSourceBlockEntities", []) if x["decisionAction"] == "OWNER"}
    owners, parents, members, variants = [], [], {}, []
    variant_ids = {}
    counts = collections.Counter()
    def find(index):
        while parents[index] != index:
            parents[index] = parents[parents[index]]
            index = parents[index]
        return index
    def union(left, right):
        left, right = find(left), find(right)
        if left != right:
            parents[right] = left
    with gzip.open(prepared / "owners.jsonl.gz", "rt", encoding="utf-8") as source:
        for line in source:
            row = json.loads(line)
            counts["preparedOwnerRows"] += 1
            key = signatures.get(sig(row))
            if key is None:
                counts["outsideSupportedPoses"] += 1
                continue
            payload = row.get("payload") or {}
            position = tuple(row["position"])
            if (row.get("sourceNativeModelOffsetState") is not None or (row["dimension"], position) in private_roots
                    or set(payload) - {"ImportedTypeVersion", "SourceShift", "MountY"} or payload.get("ImportedTypeVersion") != 1):
                counts["privateOrFunctionalOwner"] += 1
                continue
            variant_key = canon([row["registryId"], row["properties"], payload])
            variant = variant_ids.get(variant_key)
            if variant is None:
                variant = len(variants)
                variant_ids[variant_key] = variant
                variants.append((row["registryId"], row["properties"], payload))
            index = len(owners)
            owners.append((row["dimension"], position, row["instanceUuid"], key, variant))
            parents.append(index)
            for delta in footprints[key]:
                at = (row["dimension"], *(position[i] + delta[i] for i in range(3)))
                bucket = members.setdefault(at, [])
                if bucket:
                    union(index, bucket[0])
                bucket.append(index)
    print(canon({"stage": "indexed_prepared_owners", "selectedOwners": len(owners), "cells": len(members)}), flush=True)
    groups = collections.defaultdict(list)
    for index in range(len(owners)):
        groups[find(index)].append(index)
    recipes = list(base["recipes"])
    indices = {pattern(row): index for index, row in enumerate(recipes)}
    patterns = [pattern(row) for row in recipes]
    planned_families = collections.Counter()
    skips = collections.Counter()
    def closure(full):
        pending, added = list(full), set()
        while pending:
            item = pending.pop()
            if item in indices or item in added:
                continue
            added.add(item)
            if len(indices) + len(added) > args.max_recipes:
                return None
            if len(item) > 1:
                pending.extend(item[:j] + item[j + 1:] for j in range(len(item)))
        return sorted(added, key=canon)
    stream = output / "static-architecture-components.jsonl.gz"
    def pilot(group):
        return any(17 <= owners[i][1][0] >> 4 <= 18 and -42 <= owners[i][1][2] >> 4 <= -41 for i in group)
    with stream.open("xb") as raw:
        with gzip.GzipFile(fileobj=raw, mode="wb", filename="", mtime=0) as zipped:
            for first in (True, False):
                for group in groups.values():
                    if pilot(group) != first:
                        continue
                    cells = set()
                    for index in group:
                        dim, pos, uid, key, variant = owners[index]
                        cells.update((dim, *(pos[j] + delta[j] for j in range(3))) for delta in footprints[key])
                    if len(group) > 4096 or len(cells) > 8192 or len({(c[0], c[1] >> 4, c[3] >> 4) for c in cells}) > 16:
                        skips["COMPONENT_BUDGET"] += len(group)
                        continue
                    placements, full = [], set()
                    for at in sorted(cells):
                        item = tuple(sorted((owners[i][3], tuple(owners[i][1][j] - at[j + 1] for j in range(3))) for i in members[at]))
                        if len(item) > 32 or any(abs(origin[0]) > 16 or abs(origin[1]) > 32 or abs(origin[2]) > 16 for key, origin in item):
                            break
                        full.add(item)
                        placements.append((at, item))
                    if len(placements) != len(cells):
                        skips["FRAGMENT_BUDGET"] += len(group)
                        continue
                    extra = closure(full)
                    if extra is None:
                        skips["CLOSED_RECIPE_BUDGET"] += len(group)
                        continue
                    # Add only complete removal closures. No persistent per-position data enters the catalog.
                    for item in extra:
                        indices[item] = len(patterns)
                        patterns.append(item)
                    source = []
                    for index in sorted(group, key=lambda i: owners[i][2]):
                        dim, pos, uid, key, variant = owners[index]
                        registry, props, payload = variants[variant]
                        source.append({"dimension": dim, "position": list(pos), "instanceUuid": uid,
                                       "registryId": registry, "properties": props, "payload": payload, "poseKey": key})
                        planned_families[registry] += 1
                    uid = "c" + hashlib.sha256("|".join(x["instanceUuid"] for x in source).encode()).hexdigest()[:16]
                    component = {"componentId": uid, "dimension": source[0]["dimension"], "position": list(placements[0][0][1:]),
                        "owners": source, "cells": [{"position": list(at[1:]), "recipe": indices[item]} for at, item in placements]}
                    zipped.write((canon(component) + "\n").encode())
                    counts["plannedComponents"] += 1
                    counts["plannedOwners"] += len(group)
                    counts["plannedCells"] += len(cells)
                    if first:
                        counts["pilotAreaComponents"] += 1
    used = set(x["key"] for x in base["poses"])
    for item in patterns[len(recipes):]:
        collision, selection, fragments, resident = [], [], [], -1
        for j, (key, origin) in enumerate(item):
            native = geometry[key]
            offset = tuple(-n for n in origin)
            collision.extend(legacy.boxes(native, offset, "collision"))
            selection.extend(legacy.boxes(native, offset, "selection"))
            fragments.append({"pose": key, "origin": list(origin), "member": True})
            used.add(key)
            if origin == (0, 0, 0):
                if resident != -1:
                    raise RuntimeError("Two resident roots in the same native cell")
                resident = j
        if len(collision) > 2048 or len(selection) > 2048:
            raise RuntimeError("Native recipe box budget exceeded; preserve plan as failed, do not run")
        native = geometry[item[resident][0]] if resident >= 0 else None
        recipes.append({"key": "r" + hashlib.sha256(canon(item).encode()).hexdigest()[:20],
            "collision": collision, "selection": selection, "opacity": native["opacity"] if native else 0,
            "luminance": native["luminance"] if native else 0, "resident": resident, "fragments": fragments,
            "remove": [indices[item[:j] + item[j + 1:]] if len(item) > 1 else -1 for j in range(len(item))]})
    catalog = {"schemaVersion": 1, "scope": "Shared stationary architecture; exact native geometry, no world positions/UUIDs/NBT", 
        "poses": base["poses"] + [poses[key] for key in sorted(used - {x["key"] for x in base["poses"]})], "recipes": recipes}
    dump(output / "static-architecture-v1274.json", catalog)
    dump(output / "selection-proof.json", {"status": "DERIVED_SHARED_CATALOG_AND_STATIC_STREAM_NATIVE_ELIGIBILITY_PENDING", **counts,
        "sourceRestoreReceiptReused": True, "sourceSectionsDecoded": False, "worldRead": False, "gameplayVerified": False,
        "basePoseCount": len(base["poses"]), "baseRecipeCount": len(base["recipes"]), "baseRecipeIdsPreserved": recipes[:len(base["recipes"])] == base["recipes"],
        "poseCount": len(catalog["poses"]), "recipeCount": len(recipes), "ownerPlanCoverage": counts["plannedOwners"] / counts["preparedOwnerRows"],
        "excludedPoseCounts": dict(excluded), "skippedOwnerCounts": dict(skips), "plannedFamilyCounts": dict(planned_families),
        "nativeGeometrySha256": digest(args.geometry), "catalogSha256": digest(output / "static-architecture-v1274.json"), "streamSha256": digest(stream),
        "limits": {"poses": args.max_poses, "recipes": args.max_recipes, "ownersPerComponent": 4096, "cellsPerComponent": 8192, "workingChunks": 16},
        "existingPilotAreaFirst": [17, 18, -42, -41], "seconds": time.monotonic() - start})
    print(str(output / "selection-proof.json"), flush=True)


if __name__ == "__main__":
    main()
