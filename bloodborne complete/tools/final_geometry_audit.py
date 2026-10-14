"""Check imported geometry against released revisions and repair the door default.

The V12.7 preparation and every existing pose remain intact. The tall-door
catalogue default uses the already-proved source-city mounting, rather than the
earlier preview mounting. This tool never changes an archived release or map.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/architecture/resources"
DOOR = "bloodborne_dw:source_smooth_quartz_top"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def dump(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def archive_path(reference_root, revision):
    directory = reference_root / "releases/Dreamwalker-BB" / revision
    choices = [p for p in directory.glob("*.jar") if not p.name.endswith("-sources.jar")]
    if len(choices) != 1:
        raise ValueError(f"Expected one immutable production JAR in {directory}: {choices}")
    return choices[0]


def descriptors(archive):
    return {name: json.loads(archive.read(name)) for name in archive.namelist()
            if name.startswith("bloodborne_dw/composite/") and name.endswith(".json")}


def transforms(parts):
    """Material ALT is intentionally excluded; all spatial fields are retained."""
    defaults = {"offset": [0, 0, 0], "yaw": 0, "pitch": 0,
                "pivot": [8, 8, 8], "extraYaw": 0, "extraPivot": [8, 8, 8]}
    return [{**defaults, **{k: v for k, v in part.items() if k != "altModel"}} for part in parts]


def existing_pose_diff(before, after):
    changed, checked = [], 0
    for path in sorted(before.keys() & after.keys()):
        old, new = before[path], after[path]
        for index, variant in enumerate(old["variants"]):
            if index >= len(new["variants"]):
                changed.append({"descriptor": path, "pose": index, "change": "removed_existing_pose"})
                continue
            for mode, pose in variant["poses"].items():
                checked += 1
                other = new["variants"][index]["poses"].get(mode)
                if other is None:
                    changed.append({"descriptor": path, "pose": index, "mode": mode,
                                    "change": "removed_existing_open_or_closed_state"})
                elif transforms(pose["parts"]) != transforms(other["parts"]):
                    changed.append({"descriptor": path, "pose": index, "mode": mode,
                                    "before": transforms(pose["parts"]),
                                    "after": transforms(other["parts"])})
    return {"checkedExistingPoseModes": checked, "changes": changed}


def mesh_diff(before, after):
    prefix = "assets/bloodborne_dw/models/"
    common = {name for name in before.namelist() if name.startswith(prefix) and name.endswith(".json")}.intersection(after.namelist())
    changes = [{"model": name, "beforeSha256": sha(before.read(name)), "afterSha256": sha(after.read(name))}
               for name in sorted(common) if before.read(name) != after.read(name)]
    return {"checkedExistingModelResources": len(common), "changes": changes}


def assembly_geometry(recipes):
    """Prove all whole recipes retain their source component vertices and UVs."""
    import numpy as np
    from collections import Counter
    from v124_visual_audit import Visuals, rot
    visuals = Visuals(resource_roots=[RES], vanilla_jar=Path("C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar"))

    def signature(quads, yaw):
        rows = []
        for vertices, uvs, texture, tint in quads:
            points = rot(vertices, 1, -yaw * 45, [8, 8, 8])
            corners = sorted(tuple(round(float(v), 7) for v in list(point) + list(uv))
                             for point, uv in zip(points, uvs))
            rows.append((tuple(corners), texture, tint))
        return Counter(rows)

    result = []
    for recipe in recipes:
        target = read_json(RES / "bloodborne_dw/composite" / (recipe["primaryRegistryId"].split(":")[1] + ".json"))
        whole = [q for part in target["variants"][recipe["installationPose"]]["poses"]["closed"]["parts"]
                 for q in visuals.quads(part)]
        original = []
        anchor_shift = np.array(recipe["components"][0]["sourceShift"]) * 16
        for component in recipe["components"]:
            source = read_json(RES / "bloodborne_dw/composite" / (component["registryId"].split(":")[1] + ".json"))
            shift = np.array(component["relativePosition"]) * 16 + np.array(component["sourceShift"]) * 16 - anchor_shift
            for part in source["variants"][component["pose"]]["poses"]["closed"]["parts"]:
                for vertices, uv, texture, tint in visuals.quads(part):
                    transformed = rot(vertices, 1, -component.get("componentYawDelta", 0) * 45, [8, 8, 8]) + shift
                    original.append((transformed, uv, texture, tint))
        checks = []
        for yaw in range(8):
            same = signature(whole, yaw) == signature(original, yaw)
            assert same, (recipe["key"], yaw, "Whole source component vertex/UV displacement")
            checks.append({"yaw": yaw, "status": "PASS_SOURCE_COMPONENT_VERTEX_UV_TINT_TEXTURE"})
        result.append({"recipe": recipe["key"], "installationPose": recipe["installationPose"],
                       "closedQuadCount": len(whole), "sourceQuadCount": len(original), "checks": checks})
    return result


def source_anchor_checks(reference_root):
    """Reuse the already-prepared read-only source index; do not rescan the map."""
    import sqlite3
    import numpy as np
    from v124_visual_audit import rot
    index = reference_root / "bloodborne complete/build/v125-original-map.sqlite"
    connection = sqlite3.connect(index.resolve().as_uri() + "?mode=ro", uri=True)
    statement = "SELECT s.state,a.apps FROM cells c JOIN states s ON s.id=c.state JOIN applications a ON a.id=c.applications WHERE c.dimension='minecraft:overworld' AND c.x=? AND c.y=? AND c.z=?"
    proposal = read_json(ROOT / "docs/v12.6/city/mount-depth-state-proposal.json")
    doors = []
    for pair in proposal["actualSourceChecks"]:
        lower, upper = pair["lowerSourcePosition"], pair["upperSourcePosition"]
        delta = np.array(upper) - np.array(lower)
        expected = rot(np.array([[8, 8, 8]]) + np.array([0, 32, -16]), 1,
                       -pair["rotation"] * 45, [8, 8, 8])[0] - [8, 8, 8]
        assert np.max(np.abs(delta * 16 - expected)) < 1e-7
        found = []
        for position, model in ((lower, "minecraft:block/crimson_top"), (upper, "minecraft:block/smooth_quartz_top")):
            row = connection.execute(statement, position).fetchone()
            assert row is not None, position
            applications = json.loads(row[1])
            assert any(application["model"] == model for application in applications), (position, model, applications)
            found.append({"position": position, "sourceState": row[0], "sourceApplications": applications})
        doors.append({"rotation": pair["rotation"], "sourceComponents": found,
                      "status": "PASS_ORIGINAL_CITY_ANCHOR_AND_MODEL"})
    assert len(doors) == proposal["existingSourcePairCount"] == 12
    offset_policy = read_json(ROOT / "docs/v12.7/conversion/source-native-model-offset-policy.json")
    flowers = []
    for row in offset_policy["rows"]:
        actual = connection.execute(statement, row["position"]).fetchone()
        assert actual is not None and actual[0] == row["sourceState"], row
        flowers.append({"position": row["position"], "sourceState": actual[0],
                        "targetState": row["targetState"], "status": "PASS_ORIGINAL_SOURCE_COORDINATE"})
    assert len(flowers) == 64
    connection.close()
    return {"preparedReadOnlyIndex": str(index), "mapRescanned": False,
            "doorPairs": doors, "nativeSourceOffsetCells": flowers,
            "offsetPolicy": "Only native flower-carrier source offsets at their original coordinates; unrelated architecture is not shifted"}


def repair_default():
    path = RES / "bloodborne_dw/imported-assembly-v126.json"
    doc = read_json(path)
    old, city = [r for r in doc["recipes"] if r["primaryRegistryId"] == DOOR]
    if old["installationPose"] == 2:
        return False
    assert old["installationPose"] == 1 and city["installationPose"] == 2
    assert city["components"][1]["relativePosition"] == [0, 2, -1]
    # Runtime and gallery both select the first recipe of a primary family.
    # Change only that preference; matching old pairs still uses its exact recipe.
    doc["recipes"].remove(city)
    doc["recipes"].insert(doc["recipes"].index(old), city)
    city["sameFunctionalType"] = "90923 existing primary; exact source-city pose2 is the default; preview pose1 remains compatible; no new numeric ID"
    dump(path, doc)
    return True


def run(args):
    changed = repair_default() if args.apply_default else False
    recipes = read_json(RES / "bloodborne_dw/imported-assembly-v126.json")["recipes"]
    selected = [r for r in recipes if r["primaryRegistryId"] == DOOR]
    assert [r["installationPose"] for r in selected] == [2, 1]
    references = {}
    archives = {}
    for revision in ("v12.5-city-review", "v12.6-functional-assemblies", "v12.7-final"):
        path = archive_path(args.reference_root, revision)
        archives[revision] = zipfile.ZipFile(path)
        references[revision] = {"path": str(path), "sha256": sha(path.read_bytes())}
    specs = {revision: descriptors(archive) for revision, archive in archives.items()}
    current = {"bloodborne_dw/composite/" + p.name: read_json(p)
               for p in (RES / "bloodborne_dw/composite").glob("*.json")}
    comparisons = {}
    meshes = {}
    for before, after, after_specs in (("v12.5-city-review", "v12.6-functional-assemblies", specs["v12.6-functional-assemblies"]),
                                      ("v12.6-functional-assemblies", "v12.7-final", specs["v12.7-final"]),
                                      ("v12.7-final", "current", current)):
        comparisons[before + " -> " + after] = existing_pose_diff(specs[before], after_specs)
        if after != "current":
            meshes[before + " -> " + after] = mesh_diff(archives[before], archives[after])
    before = archives["v12.7-final"]
    common_models = [name for name in before.namelist() if name.startswith("assets/bloodborne_dw/models/") and name.endswith(".json") and (RES / name).is_file()]
    meshes["v12.7-final -> current"] = {"checkedExistingModelResources": len(common_models),
        "changes": [{"model": name, "beforeSha256": sha(before.read(name)), "afterSha256": sha((RES / name).read_bytes())}
                    for name in common_models if before.read(name) != (RES / name).read_bytes()]}
    for archive in archives.values():
        archive.close()
    return {"status": "SPATIAL_DESCRIPTOR_AUDIT_COMPLETE", "repairApplied": changed,
            "references": references, "comparisons": comparisons, "meshComparisons": meshes,
            "wholeRecipeProof": assembly_geometry(recipes),
            "sourceAnchorChecks": source_anchor_checks(args.reference_root),
            "doorDefaultPose": 2, "oldDoorPosesRetained": [0, 1, 2, 3],
            "geometryMutation": False,
            "scope": "Existing imported composite pose part models and all spatial transforms; not a native screenshot or face-lighting proof"}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--reference-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--apply-default", action="store_true")
    args = parser.parse_args()
    try:
        result = run(args)
        dump(args.output, result)
        print(json.dumps({"status": result["status"], "repairApplied": result["repairApplied"],
                          "comparisonCounts": {k: {"checked": v["checkedExistingPoseModes"], "changes": len(v["changes"])}
                                               for k, v in result["comparisons"].items()}}))
    except Exception as failure:
        dump(args.output, {"status": "FAIL", "error": repr(failure)})
        raise


if __name__ == "__main__":
    main()
