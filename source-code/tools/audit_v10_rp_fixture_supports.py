"""Read-only source/fixture audit; no game, mutation, or runtime PASS.

The motion report is a separate numeric audit. This tool independently derives
ordinary mount origins and physical boxes from the retained source resources.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import struct
from collections import Counter
from pathlib import Path

from audit_rp_motion_envelopes import static_cubes, corners, bounds, union

ROOT = Path(__file__).resolve().parents[1]
CUSTOM = {"stairs", "ladder", "npc_window", "chandelier_small", "wood_gate"}
NON_COLLIDING = {"curtainsmall", "curtain_half", "curtains", "door_empty", "gate_empty", "trapdoor", "wood_gate"}
ACTOR = (-63.5, 100.0, 60.5)
SURFACE = (-63.5, 100.0, 64.5)
ACTOR_BOX = (-63.8, 100.0, 60.2, -63.2, 101.8, 60.8)
YAW = -180.0


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def f32(value):
    return struct.unpack("f", struct.pack("f", value))[0]


def rotate(point):
    a = math.radians(YAW - 90)
    c, s = math.cos(a), math.sin(a)
    x, y, z = point
    return x * c + z * s, y, -x * s + z * c


def world_box(box, origin, scale):
    return bounds([tuple(rotate(tuple(v * scale for v in p))[i] + origin[i] for i in range(3)) for p in corners(box)])


def positive_overlap(a, b):
    return all(min(a[i + 3], b[i + 3]) - max(a[i], b[i]) > 1e-6 for i in range(3))


def physical_local(asset, cubes):
    if asset == "stairs":
        return [b for bone, b in cubes if bone == "stairs" and all(b[i + 3] - b[i] > 1e-8 for i in range(3))]
    if asset == "npc_window":
        return [(-16.5 / 16, -30 / 16, 12 / 16, 16.5 / 16, 34.5 / 16, 15 / 16)]
    if asset == "wood_gate":
        return [(-119 / 16, 16 / 16, -8 / 16, 119 / 16, 171 / 16, 8 / 16)]
    if asset == "ladder":
        return [(-9 / 16, -192 / 16, -25.75 / 16, 9 / 16, 14 / 16, -23.75 / 16),
                (-9 / 16, -473 / 16, -25.75 / 16, 9 / 16, -192 / 16, -23.75 / 16),
                (-11 / 16, 11 / 16, -24 / 16, 11 / 16, 12 / 16, 24 / 16)]
    return []


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--motion-report", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError("Refuse to overwrite evidence")
    motion = json.loads(args.motion_report.read_text("utf-8"))
    resources = ROOT / "src/rp/resources/assets/bloodborne_rp"
    catalogue_path = resources / "catalog.json"
    catalogue = json.loads(catalogue_path.read_text("utf-8"))
    if digest(catalogue_path) != motion["catalogueSha256"]:
        raise ValueError("Resource catalogue differs from source motion audit")
    rows = []
    for audited in motion["assets"]:
        asset, spec = audited["asset"], catalogue[audited["asset"]]
        path = resources / spec["model"]
        if digest(path) != audited["modelSha256"]:
            raise ValueError("Source model changed: " + asset)
        _, cubes = static_cubes(json.loads(path.read_text("utf-8")))
        static = union([b for _, b in cubes])
        scale = spec["scale"]
        if asset in CUSTOM:
            anchor = ((static[0] + static[3]) / 2, static[1], (static[2] + static[5]) / 2)
            if asset == "stairs":
                anchor = (0, -12, -13.3125)
            elif asset == "ladder":
                anchor = (0, -473 / 16, -24.75 / 16)
            rotated_anchor = rotate(tuple(v * scale for v in anchor))
            origin = tuple(SURFACE[i] - rotated_anchor[i] for i in range(3))
            physical = [world_box(b, origin, scale) for b in physical_local(asset, cubes)]
        else:
            anchor = None
            origin = (-64.0, 94.5 if asset == "chandelier_large" else 100.0, 64.0)
            width, height = max(.1, f32(spec["width"])), max(.1, f32(spec["height"]))
            physical = [] if asset in NON_COLLIDING else [(origin[0] - width / 2, origin[1], origin[2] - width / 2,
                                                          origin[0] + width / 2, origin[1] + height, origin[2] + width / 2)]
        default_motion = asset.startswith("cage_obj_") or asset == "ladder"
        visual_source = audited["currentMotionUnionLocal"] if default_motion else static
        visual = world_box(visual_source, origin, scale)
        query = union([visual] + physical)
        overlap = [b for b in physical if positive_overlap(b, ACTOR_BOX)]
        rows.append({"asset": asset, "defaultOpen": asset == "ladder", "defaultDogsVisible": asset.startswith("cage_obj_"),
                     "defaultMotionEnvelopeActive": default_motion, "sourceCubeCount": len(cubes),
                     "placementMode": "REVIEWED_UP_SURFACE_ANCHOR" if asset in CUSTOM else "LEGACY_INTEGER_ORIGIN",
                     "sourceAnchorLocal": anchor, "origin": origin, "staticWorldVisual": world_box(static, origin, scale),
                     "physicalBoxes": physical, "physicalBoxCount": len(physical), "actorPositivePhysicalOverlap": bool(overlap),
                     "actorOverlappingBoxes": overlap, "current6DefaultQueryBounds": query,
                     "current6DefaultHeightGuardRefusal": query[1] < -64 or query[4] > 320,
                     "queryFloorRelation": "Default author pad has native 5x5 support cells X[-66,-62],Z[62,66] atY99; this is a clicked surface, not a requirement for all visual/selection cells to have floor.",
                     "nativeSurfacePreservation": "No native block write by RP placement; visual/selection overlap is not a movement obstruction."})
    debug = json.loads((ROOT / "src/architecture/resources/bloodborne_dw/debug_catalogue.json").read_text("utf-8"))
    resource = json.loads((ROOT / "reports/RESOURCE_AUDIT.json").read_text("utf-8"))
    candidates = json.loads((ROOT / "reports/CATALOG_CANDIDATES.json").read_text("utf-8"))
    gate = next(x for x in rows if x["asset"] == "wood_gate")
    replacement = (-64.8, 100.0, 60.5)
    new_actor = (replacement[0] - .3, replacement[1], replacement[2] - .3,
                 replacement[0] + .3, replacement[1] + 1.8, replacement[2] + .3)
    eye = (replacement[0], replacement[1] + 1.62, replacement[2])
    gate_camera = {"asset": "wood_gate", "feet": replacement, "eyeAssumingStandingHeight1_62": eye,
                   "target": SURFACE, "eyeToActualSurfaceDistance": math.dist(eye, SURFACE),
                   "feetToPlacementSurfaceDistance": math.dist(replacement, SURFACE),
                   "physicalOverlap": any(positive_overlap(new_actor, b) for b in gate["physicalBoxes"]),
                   "withinCreativeBlockReach5": math.dist(eye, SURFACE) <= 5,
                   "withinServerPlacementReach8": math.dist(replacement, SURFACE) <= 8,
                   "support": "Actor may fly; target remains original native pad.down UP surface, with no QA support write.",
                   "runtimeProof": "NOT_RUN; actual ordinary use-key and floor ray must independently confirm this camera."}
    report = {"schema": "dw-v10-rp-ordinary-fixture-support-audit-v1",
              "status": "STATIC_SOURCE_DEFAULT_POSE_AND_PHYSICAL_GUARD_AUDIT_NOT_RUNTIME_PASS",
              "productionJarSha256": motion["productionJarSha256"], "motionAudit": str(args.motion_report),
              "motionAuditSha256": digest(args.motion_report), "canonicalPopulation": len(rows),
              "actorFeet": ACTOR, "actorAssumedNormalBoundingBox": ACTOR_BOX,
              "customSurface": SURFACE, "placedYaw": YAW, "clickedSupport": [-64, 99, 64],
              "sourceAlgorithms": ["ObjectRegistry.place non-custom integer origin and legacy chandelier Y−5.5", "placementAnchor custom ordinary UP", "localPhysical cardinal boxes", "EntityDimensions.fixed legacy square X/Z collider", "PlacementPhysics.entityConflict positive actual living-volume rejection"],
              "physicalActorConflictAssets": [x["asset"] for x in rows if x["actorPositivePhysicalOverlap"]],
              "current6DefaultBuildHeightRefusalAssets": [x["asset"] for x in rows if x["current6DefaultHeightGuardRefusal"]],
              "woodGateAlternativeCamera": gate_camera, "assets": rows,
              "catalogueScope": {"temporaryTableRows": len(debug["entries"]), "kindCounts": dict(Counter(x["kind"] for x in debug["entries"])),
                                  "canonicalConstructionRpTypes": 69, "retainedDuplicateRpAliases": 7,
                                  "architectureCanonicalTypes": 18, "independentOfferedTemporaryRows": len(debug["entries"]) - 7,
                                  "temporaryFinalNumericIdsAssigned": False,
                                  "originalModelRecords": candidates["source_models_accounted"], "unreferencedGeometryRecords": candidates["unreferenced_geometry_model_records"],
                                  "candidateEvidenceClassificationCounts": candidates["classification_counts"],
                                  "originalIndependentObjectCount": resource["semantic_catalog"]["independent_objects_count"],
                                  "originalIndependentObjectCountStatus": resource["semantic_catalog"]["status"],
                                  "currentScope": "18 architectural types plus canonical retained RP object/mob/item resources; bounded new stand and historical source41-member110 derivative only, not complete source city conversion.",
                                  "remaining": ["Finalize independent source object membership/assemblies/material forms; one model is not one object.", "Resolve original missing dependencies and ambiguous/unreferenced candidates with evidence.", "Freeze final numeric IDs and explicit all-source migration map.", "Full-city gameplay/permissions/lighting/entities/foreignNBT/reopen validation and user art/visual acceptance."]},
              "limitations": ["This derives boxes from source resources and declared legacy dimensions; no Minecraft actor pose or native obstruction result is fabricated.",
                              "Source motion report reproduces candidate6; proposed source geometry correction is owned separately and requires new freeze/runtime proof.",
                              "Chunk rectangle is a bounded loaded-check cost, not a claim that this method loads or generates those chunks.",
                              "No global performance/FPS/whole-catalogue gameplay claim follows from cube counts or static bounds.",
                              "Default ordinary stack has no custom Open/Scale/DogsVisible tags. Picked altered stacks and all active animations require separate states/runtime checks."]}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", "utf-8")
    print(json.dumps({"status": report["status"], "assets": len(rows), "physicalActorConflicts": report["physicalActorConflictAssets"],
                      "current6DefaultHeightRefusals": report["current6DefaultBuildHeightRefusalAssets"], "alternativeGateCamera": gate_camera,
                      "catalogueScope": report["catalogueScope"], "report": str(args.output.resolve()), "sha256": digest(args.output)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
