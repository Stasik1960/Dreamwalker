"""Read only V12.5 preview/descriptor analysis for the user approved V12.6 joins.

Never changes source map, production descriptors, IDs, or old release artifacts.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/v12.6/assemblies"


def read(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha(path: Path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


APPROVED = {
    "3152:3153:0,2,0": ("93066", "COMPLETE_ORNAMENTED_WINDOW", ["93069"]),
    "2672:2673:0,1,0": ("90179", "COMPLETE_STONE_PILASTER", ["90178"]),
    "929:1092:0,2,0": ("90923", "SINGLE_LEAF_DOOR_WITH_TALL_STATIC_ARCH_KEEP_SMALL_90458", []),
    "1435:2430:0,2,0": ("90301", "COMPLETE_ARCHED_WINDOW", ["90251"]),
    "2681:2231:0,3,0": ("90143", "COMPLETE_STATIC_ORNAMENTED_METAL_PANEL_NOT_DOOR", ["90142"]),
    "1677:1678:0,1,0": ("90586", "EXTENSIBLE_CLIMBABLE_LADDER_HEIGHT_THREE", []),
    "3589:2048:0,3,0": ("90160", "COMPLETE_TALL_VERTICAL_WOOD_DECOR", []),
    "2951:2953:0,2,0": ("90031", "COMPLETE_DOUBLE_LEAF_WOOD_DOOR_STATIC_STONE_ARCH", ["90623"]),
    "EXACT-THREE-PART-WINDOW-01": ("90797", "COMPLETE_THREE_PART_STAINED_GLASS_WINDOW", ["90799"]),
}
REJECTED = {"CUBEDECOR-001", "CUBEDECOR-002", "CUBEDECOR-027", "CUBEDECOR-062"}
WINDOW_PARTS = {"93113", "93117", "93116", "93115"}
NOT_DOORS = {"90247", "93156", "90143", "93159", "90777", "93147"}
DOORS = {
    "90031": {"leaves": 2, "screenshot": 1, "moving": "two wood leaves below completed stone arch", "static": "stone sides and stone arch from90623"},
    "93160": {"leaves": 1, "screenshot": 2, "moving": "complete narrow door leaf", "static": "none unless source model identifies hinge hardware"},
    "90266": {"leaves": 2, "screenshot": 3, "moving": "two central leaves below arched division", "static": "outer vertical panels and upper ornamental panel"},
    "93152": {"leaves": 2, "screenshot": 4, "moving": "two lower wood leaves", "static": "stone frame and top glazed transom"},
    "90458": {"leaves": 1, "screenshot": 5, "moving": "arched wood leaf", "static": "stone frame"},
    "93153": {"leaves": 2, "screenshot": 6, "moving": "two lower wood leaves", "static": "outer wooden panels and top header"},
    "90458+90923": {"leaves": 1, "screenshot": 7, "moving": "same arched wood leaf as90458", "static": "stone frame and additional tall arch90923"},
    "90001": {"leaves": "existing_V12_unchanged", "screenshot": None, "moving": "existing V12 mechanism", "static": "existing V12 behavior preserved byte for byte"},
}


def component_record(component):
    registry = component["registryId"]
    descriptor_path = ROOT / "src/architecture/resources/bloodborne_dw/composite" / (registry.split(":", 1)[1] + ".json")
    descriptor = read(descriptor_path)
    variant = int(component["properties"].get("variant", "0"))
    pose = descriptor["variants"][variant]["poses"]["closed"]
    result = dict(component)
    result["descriptor"] = str(descriptor_path.relative_to(ROOT)).replace("\\", "/")
    result["descriptorSha256"] = sha(descriptor_path)
    result["frozenSelectedDescriptorPose"] = pose
    result["frozenDescriptorMetadata"] = {k: v for k, v in descriptor.items() if k != "variants"}
    result["frozenDescriptorVariantCount"] = len(descriptor["variants"])
    result["sourcePositionShiftWorld"] = [component["relativePosition"][i] + component.get("sourceShift", [0, 0, 0])[i] for i in range(3)]
    return result


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    assemblies_path = ROOT / "docs/v12.5-city-review/assemblies/gallery-review-platforms-final.json"
    doors_path = ROOT / "docs/v12.5-city-review/doors/gallery-door-platforms-final.json"
    assemblies = read(assemblies_path)["platforms"]
    doors = read(doors_path)["platforms"]
    decisions = []
    merges = []
    for row in assemblies:
        ids = {c["mainId"] for c in row["components"]}
        key = row["key"]
        if key in REJECTED:
            disposition = "REJECTED_USER_KEEP_INDEPENDENT"
        elif ids & WINDOW_PARTS:
            disposition = "KEEP_REUSABLE_WINDOW_SECTIONS_USER_DIRECTIVE"
        elif key in APPROVED:
            disposition = "APPROVED_COMPLETE_ONE_INSTALLABLE_OBJECT"
        else:
            disposition = "ALREADY_COMPLETE_SINGLE_OWNER_CONTROL_NO_MERGE"
        decision = {"key": key, "displayLabel": row["russianLabel"], "componentIds": sorted(ids), "disposition": disposition}
        decisions.append(decision)
        if key not in APPROVED:
            continue
        main_id, behavior, retired_parts = APPROVED[key]
        components = [component_record(c) for c in row["components"]]
        anchor = components[0]
        anchor_shift = anchor.get("sourceShift", [0, 0, 0])
        for component in components:
            delta = [16 * (component["relativePosition"][i] + component.get("sourceShift", [0, 0, 0])[i] - anchor_shift[i]) for i in range(3)]
            component["composeOffsetRelativeToOldAnchorInModelUnits"] = delta
            component["translatedPartsKeepingOldAnchorSourceShift"] = [dict(part, offset=[part.get("offset", [0, 0, 0])[i] + delta[i] for i in range(3)]) for part in component["frozenSelectedDescriptorPose"]["parts"]]
        merges.append({
            **decision,
            "primaryIdSuggested": main_id,
            "primaryRegistryIdSuggested": next(c["registryId"] for c in components if c["mainId"] == main_id),
            "sourceAnchorId": anchor["mainId"],
            "sourceAnchorRegistryId": anchor["registryId"],
            "behavior": behavior,
            "hideOldIndependentPartIds": retired_parts,
            "sourceOccurrences": row.get("occurrences"),
            "sourceExamples": row.get("sourceExamples", []),
            "components": components,
            "doNotTreatAdjacencyAsUnconditionalReplacement": True,
            "migrationRequirement": "Match all source parts at recorded relative XYZ after common yaw and SourceShift. Keep root UUID, instance payload, bindings, events; remap consumed owner UUIDs to root UUID; preserve old unmatched saved fragments as hidden compatibility states. Never replace individual old fragment with a complete assembly in place without matching or explicit user90479 redirect.",
        })
    door_decisions = []
    for row in doors:
        ids = {c["mainId"] for c in row["components"]}
        door_decisions.append({"key": row["key"], "componentIds": sorted(ids), "disposition": "USER_NOT_A_DOOR" if ids & NOT_DOORS else "REDIRECT_TO_UNCHANGED_90001" if "90479" in ids else "CONSUMED_TOP_USE_COMPLETE_90031" if ids == {"90623"} else "USER_CONFIRMED_DOOR", "components": [dict(c) for c in row["components"]]})
    report = {
        "schemaVersion": 1,
        "status": "READ_ONLY_USER_APPROVAL_IMPLEMENTATION_PLAN",
        "baseline": "V12.5",
        "baselineCommit": "c52a06c9fa20007a1c06c8fc654b3c1b86f72791",
        "sourceAssemblyManifestSha256": sha(assemblies_path),
        "sourceDoorManifestSha256": sha(doors_path),
        "assemblyDecisions": decisions,
        "approvedMergePlans": merges,
        "doorDecisions": door_decisions,
        "doorMechanismPlans": DOORS,
        "originalV12Policy": "No production code, models, states, IDs, or behavior of original V12 objects may change.90001 copied for tests only, existing double-door interaction unchanged.",
        "specialCases": {
            "90031": "Only one completed working type. Existing completed preview contains90031 and90623, not another registered whole type. Hide90623 independent item after full assembly exists.",
            "90586": "Already ImportedLadderBlock with HEIGHT.ONE/TWO. Approved preview contains two owners (two-cell plus one-cell), requiring one three-cell assembled placement state while preserving extensible height states and climbability.",
            "90160": "Preview combines two different old variants under one number. Completed type must not present unrelated old fragments under shared working ID; hidden frozen compatibility preserves unmatched saved fragment art.",
            "90797": "Full approved window consumes old90797variant1 tiny lower end, old90797variant0 central frame and90799top. Keep raw old variants only as hidden compatibility states.",
            "90143": "User declares all instances non-doors. Approved90143+90142 completion remains static ornamented panel; art-bank audit must split genuinely distinct working art from mere rotation states.",
            "90479": "Explicit redirect to originalV12 door90001. If attached to existing90001 remove duplicate new fragment; standalone source replacement requires root/height compensation determined from source geometry and prior90001 placement; never edit90001 descriptor.",
            "90458": "Screenshots5 and7 are same moving wood leaf with materially different static exterior frame height. Root selected existing90923 as primary complete tall door, preserving small90458 as independent complete door. Tall source anchor is old90458 lower part, not old90923 upper root. Do not duplicate same completed model as two working types.",
        },
    }
    (OUT / "user-approved-assembly-and-door-plan.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": report["status"], "assemblyDecisions": len(decisions), "approvedMergePlans": len(merges), "rejected": sum(d["disposition"] == "REJECTED_USER_KEEP_INDEPENDENT" for d in decisions), "independentReusableWindows": sum(d["disposition"] == "KEEP_REUSABLE_WINDOW_SECTIONS_USER_DIRECTIVE" for d in decisions), "singleOwnerControls": sum(d["disposition"] == "ALREADY_COMPLETE_SINGLE_OWNER_CONTROL_NO_MERGE" for d in decisions), "doorDecisions": len(door_decisions), "output": str(OUT / "user-approved-assembly-and-door-plan.json")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
