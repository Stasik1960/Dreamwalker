"""Stage approved complete assemblies without touching production or old releases.

The V12.5 JAR is the authoritative snapshot. Composed parts preserve native
FaceBakery geometry, UV, full texture content and old source anchor shifts.
Compatibility fragments remain available in staged snapshots/hidden variants.
Door leaf partition, runtime migration, final physics and Creative filtering are
the root agent's subsequent implementation, not claimed complete here.
"""
from __future__ import annotations
import argparse
import copy
import hashlib
import io
import json
from functools import lru_cache
from pathlib import Path
import zipfile

import numpy as np
from PIL import Image, ImageDraw
from prepare_gallery_v125_input import ArchiveGeometry
from v124_visual_audit import render, rot

ROOT = Path(__file__).resolve().parents[1]
BASELINE_SHA = "76690ecea281ecd5ac59d9b00493546af1978b0819bb2923da977b176f16b845"
DEFAULT_STAGE = ROOT / "build/v126-assembly-plan"
PLAN = ROOT / "docs/v12.6/assemblies/user-approved-assembly-and-door-plan.json"
TARGETS = {
    "3152:3153:0,2,0": ("93066", "Каменное окно с орнаментом", False),
    "2672:2673:0,1,0": ("90179", "Цельная каменная пилястра", False),
    "1435:2430:0,2,0": ("90301", "Цельное арочное окно", False),
    "2681:2231:0,3,0": ("90143", "Высокая металлическая декоративная панель", False),
    "3589:2048:0,3,0": ("90160", "Высокое деревянное украшение", False),
    "EXACT-THREE-PART-WINDOW-01": ("90797", "Большое витражное окно с завершением", False),
    "2951:2953:0,2,0": ("90031", "Двустворчатая деревянная дверь в каменной арке", True),
    "929:1092:0,2,0": ("90923", "Деревянная дверь с высокой каменной аркой", True),
}
NATIVE_FRAMES = {
    "3152:3153:0,2,0": "v125-034-3152_3153_0_2_0.png",
    "2672:2673:0,1,0": "v125-038-2672_2673_0_1_0.png",
    "1435:2430:0,2,0": "v125-041-1435_2430_0_2_0.png",
    "2681:2231:0,3,0": "v125-042-2681_2231_0_3_0.png",
    "3589:2048:0,3,0": "v125-044-3589_2048_0_3_0.png",
    "EXACT-THREE-PART-WINDOW-01": "v125-046-EXACT-THREE-PART-WINDOW-01.png",
    "2951:2953:0,2,0": "v125-045-2951_2953_0_2_0.png",
    "929:1092:0,2,0": "v125-040-929_1092_0_2_0.png",
}


def sha(data: bytes):
    return hashlib.sha256(data).hexdigest()


def dump(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


class PackagedVisuals(ArchiveGeometry):
    @lru_cache(maxsize=None)
    def image(self, identifier):
        ns, path = identifier.split(":", 1)
        entry = "assets/" + ns + "/textures/" + path + ".png"
        if entry in self.archive.namelist():
            return np.asarray(Image.open(io.BytesIO(self.archive.read(entry))).convert("RGBA"))
        return super().image(identifier)


def contains(a, b):
    return all(a["from"][i] <= b["from"][i] + 1e-8 and a["to"][i] >= b["to"][i] - 1e-8 for i in range(3))


def coalesce(boxes):
    """Only exact cuboid unions or containment; never fills an arch's opening."""
    boxes = copy.deepcopy(boxes)
    again = True
    while again:
        again = False
        for i, a in enumerate(boxes):
            for j in range(i + 1, len(boxes)):
                b = boxes[j]
                if contains(a, b):
                    boxes.pop(j); again = True; break
                if contains(b, a):
                    boxes[i] = b; boxes.pop(j); again = True; break
                for axis in range(3):
                    others = [d for d in range(3) if d != axis]
                    if not all(abs(a[key][d] - b[key][d]) < 1e-8 for key in ("from", "to") for d in others):
                        continue
                    if max(a["from"][axis], b["from"][axis]) > min(a["to"][axis], b["to"][axis]) + 1e-8:
                        continue
                    joined = copy.deepcopy(a)
                    joined["from"][axis] = min(a["from"][axis], b["from"][axis])
                    joined["to"][axis] = max(a["to"][axis], b["to"][axis])
                    boxes[i] = joined; boxes.pop(j); again = True; break
                if again:
                    break
            if again:
                break
    return sorted(boxes, key=lambda b: b["from"] + b["to"])


def signatures(visuals, quads, yaw):
    result = []
    for vertices, uv, texture, tint in quads:
        vertices = rot(vertices, 1, yaw * 45, [8, 8, 8])
        pixels = visuals.image(texture)
        result.append((tuple(round(float(n), 7) for n in vertices.reshape(-1)), tuple(round(float(n), 7) for n in uv.reshape(-1)), sha(pixels.tobytes()), tint))
    return sorted(result)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--production-jar", type=Path, default=ROOT / "build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.5-city-review.jar")
    parser.add_argument("--output", type=Path, default=DEFAULT_STAGE)
    parser.add_argument("--vanilla-jar", type=Path, default=Path("C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar"))
    args = parser.parse_args()
    assert sha(args.production_jar.read_bytes()) == BASELINE_SHA, "Unrecognized baseline JAR; refusing to snapshot altered production"
    stage = args.output.resolve()
    assert stage.is_relative_to(ROOT / "build"), "Staging must remain inside project build directory"
    stage.mkdir(parents=True, exist_ok=True)
    plan = json.loads(PLAN.read_text(encoding="utf-8"))
    rows = []
    snapshots = {}
    images = []
    with zipfile.ZipFile(args.production_jar) as archive:
        assert archive.testzip() is None
        catalogue = json.loads(archive.read("bloodborne_dw/debug_catalogue.json"))
        id_to_registry = {e["temporaryId"]: e["registryId"] for e in catalogue["entries"]}
        physics = json.loads(archive.read("bloodborne_dw/imported-collision-v125.json"))
        visuals = PackagedVisuals(archive, args.vanilla_jar)
        for row in plan["approvedMergePlans"]:
            if row["key"] not in TARGETS:
                continue
            target_id, name, door = TARGETS[row["key"]]
            target_registry = id_to_registry[target_id]
            primary_entry = "bloodborne_dw/composite/" + target_registry.split(":", 1)[1] + ".json"
            baseline_spec = json.loads(archive.read(primary_entry))
            snapshots[target_registry] = {"resourcePath": primary_entry, "sha256": sha(archive.read(primary_entry)), "descriptor": baseline_spec}
            spec = copy.deepcopy(baseline_spec)
            spec["displayName"] = name
            spec["openable"] = False  # Root adds confirmed door open poses after leaf subdivision.
            selected_parts = []
            expected_quads = []
            all_boxes = []
            components = copy.deepcopy(row["components"])
            anchor_shift = components[0].get("sourceShift", [0, 0, 0])
            anchor_yaw = int(components[0]["properties"].get("rotation", "0"))
            anchor_world_shift = rot(np.asarray(anchor_shift), 1, -anchor_yaw * 45, [0, 0, 0])
            for component in components:
                registry = component["registryId"]
                entry = component["descriptor"].replace("src/architecture/resources/", "")
                old_bytes = archive.read(entry)
                assert sha(old_bytes) == component["descriptorSha256"], "Snapshot differs from approved frozen pose"
                old_spec = json.loads(old_bytes)
                snapshots[registry] = {"resourcePath": entry, "sha256": sha(old_bytes), "descriptor": old_spec}
                variant = int(component["properties"].get("variant", "0"))
                component_yaw = int(component["properties"].get("rotation", "0"))
                yaw_delta = (component_yaw - anchor_yaw) & 7
                relative = np.asarray(component["relativePosition"], dtype=float)
                shift = np.asarray(component.get("sourceShift", [0, 0, 0]), dtype=float)
                component_world_shift = rot(shift, 1, -component_yaw * 45, [0, 0, 0])
                part_delta = 16 * (rot(relative, 1, anchor_yaw * 45, [0, 0, 0]) + rot(shift, 1, -yaw_delta * 45, [0, 0, 0]) - np.asarray(anchor_shift))
                component["componentYawDelta"] = yaw_delta
                component["composeOffsetRelativeToOldAnchorInModelUnits"] = part_delta.tolist()
                old_pose = old_spec["variants"][variant]["poses"]["closed"]
                for part in old_pose["parts"]:
                    moved = copy.deepcopy(part)
                    pivot = np.asarray(part.get("pivot", [8, 8, 8]), dtype=float)
                    moved["offset"] = (rot(pivot + np.asarray(part.get("offset", [0, 0, 0])) - np.asarray([8, 8, 8]), 1, -yaw_delta * 45, [0, 0, 0]) + np.asarray([8, 8, 8]) - pivot + part_delta).tolist()
                    if yaw_delta:
                        moved["yaw"] = part.get("yaw", 0) + yaw_delta * 45
                    selected_parts.append(moved)
                    for verts, uv, texture, tint in visuals.quads(part):
                        source_world = rot(verts, 1, -component_yaw * 45, [8, 8, 8]) + 16 * (relative + component_world_shift)
                        expected_quads.append((source_world, uv, texture, tint))
                selector = physics["objects"][registry][variant]["closedBase"]
                component["frozenV125CollisionShapeSelector"] = selector
                component["oldFragmentCompatibility"] = {"registryId": registry, "variant": variant, "oldSourceShift": component.get("sourceShift", [0, 0, 0]), "payload": component.get("payload", {})}
                for box in physics["shapes"][selector][yaw_delta]["collision"]:
                    all_boxes.append({key: [box[key][d] + part_delta[d] for d in range(3)] for key in ("from", "to")})
            new_quads = [q for part in selected_parts for q in visuals.quads(part)]
            translated_new = [(rot(v, 1, -anchor_yaw * 45, [8, 8, 8]) + anchor_world_shift * 16, uv, t, tint) for v, uv, t, tint in new_quads]
            proofs = []
            for yaw in range(8):
                a, b = signatures(visuals, expected_quads, yaw), signatures(visuals, translated_new, yaw)
                assert a == b, "Composition changed visible surface, UV or RGBA pixels"
                proofs.append({"yaw": yaw, "status": "PASS_SOURCE_VERTICES_UV_FULL_RGBA", "surfaceSignatures": len(a), "signatureSha256": sha(repr(a).encode())})
            points = np.concatenate([q[0] for q in new_quads])
            bounds = {"from": points.min(axis=0).tolist(), "to": points.max(axis=0).tolist()}
            boxes = coalesce(all_boxes)
            pose = {"parts": selected_parts, "collision": boxes, "selection": copy.deepcopy(boxes) if boxes else [bounds]}
            whole = {"poses": {"closed": pose}, "visibleBounds": bounds}
            # Append the complete installation pose after every old index. Old
            # saved state numbers and their V12.5 helper shapes remain exact.
            installation_pose = len(baseline_spec["variants"])
            spec["variants"] = copy.deepcopy(baseline_spec["variants"]) + [whole]
            assert spec["variants"][:installation_pose] == baseline_spec["variants"]
            spec["v126AssemblyPolicy"] = "Completed installation pose appended after all unchanged old indices. Creative, placement and art cycling expose only completed pose; old fragment poses remain hidden compatibility states. Root runtime performs neighborhood recognition and preserves unmatched fragments."
            spec["v126DefaultPhysicsStatus"] = "STAGED_COMPONENT_UNION_REQUIRES_ROOT_FINAL_FITTED_PHYSICS_AND_NATIVE_TESTS"
            staged_file = stage / "resources" / primary_entry
            dump(staged_file, spec)
            retirement_ids = list(row["hideOldIndependentPartIds"])
            if target_id == "90923":
                retirement_ids = []  #90923 old top survives hidden state;90458 remains working small door.
            native = ROOT / "docs/v12.5-city-review/native/captures/v125-flatgallery-native-1" / NATIVE_FRAMES[row["key"]]
            records = {
                "key": row["key"], "primaryId": target_id, "primaryRegistryId": target_registry, "displayName": name,
                "sourceAnchorId": components[0]["mainId"], "sourceAnchorRegistryId": components[0]["registryId"],
                "sourceAnchorOldVariant": int(components[0]["properties"].get("variant", "0")),
                "wholeVariant": installation_pose, "installationPose": installation_pose,
                "oldPrimaryVariantToHiddenVariant": {str(v): v for v in range(len(baseline_spec["variants"]))},
                "oldPrimaryHiddenVariantIndices": list(range(len(baseline_spec["variants"]))),
                "oldVariantPrefixPreservedExactly": True,
                "stagedDescriptor": str(staged_file.relative_to(ROOT)).replace("\\", "/"), "stagedDescriptorSha256": sha(staged_file.read_bytes()),
                "frozenPrimaryDescriptorSha256": snapshots[target_registry]["sha256"],
                "doorRequiresLeafPartitionAndOpenPose": door, "hideIndependentPartIds": retirement_ids,
                "components": components, "completeVisualBoundsModelUnits": bounds,
                "physicsRecipe": {"status": "PROPOSED_SIMPLE_CUBOID_UNION_FOR_ROOT_REGENERATION", "cardinalBoxCount": len(boxes), "cardinalComponentUnion": boxes, "coalescing": "Only exact rectangular unions and containedboxes removed; never an outwardempty-opening fill", "maximumFinalBoxes": 12, "finalEightYawShapes": "ROOT_REGENERATE_AND_TEST; V125shapesmustnotremainforchangeddefaultposes", "doorRule": "Movingwoodleavesrotate90degreesaboutouterhinges; staticframesstayfixed; openpathclear" if door else "Keepcomponent-sourcepassability, reduce toasfewsource-containedordinarycuboidsaspossible", "authorExport": "NOT_SUPPLIED_NOT_APPLIED"},
                "closedCompositionProof": proofs, "sourceProofIncludesActualComponentYawAndShift": True, "nativeV125Preview": str(native.relative_to(ROOT)).replace("\\", "/"), "nativeV125PreviewSha256": sha(native.read_bytes()),
                "nativeV126Result": "NOT_RUN", "migration": {"oldSavedUnmatchedFragments": "Hidden old descriptors/variants at unchanged state indices and unchanged instance data", "matchedWhole": "Primary target registry at appended installationPose; root UUID from source anchor; references of consumed part UUIDs mapped to primary UUID; preserve all payload/bindings/events", "renderAnchor": "Retain old anchor SourceShift and mountY; no individual reseating", "conditionalSourcePoseMigration": "Translate aliases old registry/variant first; match all recorded parts and positions after common rotation before consuming", "small90458Preserved": target_id == "90923"},
            }
            rows.append(records)
            for label, quads in (("V125 parts", expected_quads), ("V126 complete", translated_new)):
                array = render(visuals, quads, 0, size=192)
                images.append((target_id + " " + label, Image.fromarray(array)))
        assert len(rows) == 8
    snapshot_path = stage / "old-v125-descriptor-snapshots.json"
    dump(snapshot_path, {"schemaVersion": 1, "baselineJarSha256": BASELINE_SHA, "descriptors": snapshots})
    sheet = Image.new("RGB", (2 * 224, len(rows) * 216), "#303438")
    draw = ImageDraw.Draw(sheet)
    for i, (label, im) in enumerate(images):
        x, y = (i % 2) * 224, (i // 2) * 216
        sheet.paste(im, (x + 16, y + 20), im.getchannel("A"))
        draw.text((x + 6, y + 4), label, fill="white")
    png = stage / "closed-composition-comparison.png"
    sheet.save(png)
    metadata = {"schemaVersion": 1, "status": "STAGED_NOT_APPLIED_TO_PRODUCTION", "baselineCommit": "c52a06c9fa20007a1c06c8fc654b3c1b86f72791", "baselineJarSha256": BASELINE_SHA, "assemblies": rows, "oldDescriptorSnapshots": str(snapshot_path.relative_to(ROOT)).replace("\\", "/"), "oldDescriptorSnapshotSha256": sha(snapshot_path.read_bytes()), "compatibilityPolicy": "No old registrations deleted, no old poses lost or renumbered; hidden compatibility state map is explicit. Working item exposes only appended complete installationPose. Source UUID/root references/events/payload migration done by root.", "originalV12Policy": "UNCHANGED", "sourceWorldPolicy": "READ_ONLY", "comparisonImage": str(png.relative_to(ROOT)).replace("\\", "/"), "comparisonImageSha256": sha(png.read_bytes()), "notClaimed": ["nativeV126clientrender", "finalphysics", "runtimegroupmigration", "Creativefiltering", "dooropenmechanism"]}
    dump(stage / "assembly-manifest.json", metadata)
    docs = ROOT / "docs/v12.6/assemblies/generated"
    dump(docs / "assembly-manifest.json", metadata)
    dump(docs / "old-v125-descriptor-snapshots.json", {"schemaVersion": 1, "baselineJarSha256": BASELINE_SHA, "descriptors": snapshots})
    for row in rows:
        target = ROOT / row["stagedDescriptor"]
        dump(docs / "descriptors" / target.name, json.loads(target.read_text(encoding="utf-8")))
    (docs / png.name).write_bytes(png.read_bytes())
    print(json.dumps({"status": metadata["status"], "completeDescriptors": len(rows), "static": sum(not r["doorRequiresLeafPartitionAndOpenPose"] for r in rows), "doorClosedCompositionStages": sum(r["doorRequiresLeafPartitionAndOpenPose"] for r in rows), "oldSnapshots": len(snapshots), "sourceWorldSurfaceUvRgbaProofs": 8 * len(rows), "sourceFacesPerAssembly": {r["primaryId"]: r["closedCompositionProof"][0]["surfaceSignatures"] for r in rows}, "cardinalCollisionRecipeCounts": {r["primaryId"]: r["physicsRecipe"]["cardinalBoxCount"] for r in rows}, "manifest": str(stage / "assembly-manifest.json")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
