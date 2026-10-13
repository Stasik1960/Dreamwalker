"""Read-only audit of reserved numeric IDs and visible functional type identities.

Compatibility aliases and installation objects share a type number intentionally;
they do not become a second independent offered type. Scene copies have UUIDs.
This audit does not rewrite the catalogue, models, worlds or registrations.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/architecture/resources/bloodborne_dw"
BASELINE = "c52a06c9fa20007a1c06c8fc654b3c1b86f72791"
V12 = "821de077917f696a13600e900a244ffc05230c2f"


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def old_json(commit, path):
    # Git paths are repository-relative even though this script lives in the mod.
    return json.loads(subprocess.check_output([
        "git", "show", f"{commit}:bloodborne complete/{path}"
    ], cwd=ROOT))


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def audit():
    debug = read(RESOURCES / "debug_catalogue.json")
    catalogue = read(RESOURCES / "remaining-catalogue.json")
    aliases = read(RESOURCES / "imported-type-aliases.json")
    retirements = read(RESOURCES / "imported-style-retirements.json")
    entries = debug["entries"]
    by_registry = {row["registryId"]: row for row in entries}
    by_number = {row["temporaryId"]: row for row in entries}
    problems = []
    number_counts = Counter(row["temporaryId"] for row in entries)
    registry_counts = Counter(row["registryId"] for row in entries)
    duplicates = {
        "numbers": sorted(key for key, count in number_counts.items() if count != 1),
        "primaryRegistries": sorted(key for key, count in registry_counts.items() if count != 1),
    }
    for kind, repeated in duplicates.items():
        if repeated:
            problems.append({"code": "DUPLICATE_" + kind.upper(), "values": repeated})
    malformed = [row["temporaryId"] for row in entries if not re.fullmatch(r"[0-9]{5}", row["temporaryId"])]
    if malformed:
        problems.append({"code": "MALFORMED_FIVE_DIGIT_ID", "values": malformed})

    alias_owners = defaultdict(list)
    for row in entries:
        for registry in row["aliases"]:
            alias_owners[registry].append(row["temporaryId"])
    repeated_aliases = {key: value for key, value in alias_owners.items() if len(value) > 1}
    if repeated_aliases:
        problems.append({"code": "DUPLICATE_RAW_REGISTRY_ALIAS", "values": repeated_aliases})

    history = old_json(BASELINE, "src/architecture/resources/bloodborne_dw/debug_catalogue.json")
    v12 = old_json(V12, "src/architecture/resources/bloodborne_dw/debug_catalogue.json")
    history_reserved = {row["temporaryId"]: row for row in history["entries"]}
    old_missing_or_rebound = [number for number, old in history_reserved.items()
                             if number not in by_number or by_number[number]["registryId"] != old["registryId"]]
    original_changed = [row["temporaryId"] for row in v12["entries"]
                        if by_number.get(row["temporaryId"]) != row]
    if old_missing_or_rebound:
        problems.append({"code": "HISTORICAL_ID_REMOVED_OR_REUSED", "values": old_missing_or_rebound})
    if original_changed:
        problems.append({"code": "ORIGINAL_V12_NUMERIC_ROW_CHANGED", "values": original_changed})

    canonical = {}
    group_rows = {}
    for group in aliases["groups"]:
        primary = group["primaryRegistryId"]
        group_rows[primary] = group
        if primary not in by_registry or by_registry[primary]["temporaryId"] != group["primaryId"]:
            problems.append({"code": "GROUP_PRIMARY_NUMBER_MISMATCH", "registry": primary})
        for member in group["members"]:
            if member in canonical:
                problems.append({"code": "MEMBER_IN_MULTIPLE_GROUPS", "registry": member})
            canonical[member] = primary
    style_retired = {registry for row in retirements["retirements"] for registry in row["aliases"]}
    paths = [row["path"] for row in catalogue["objects"]]
    repeated_paths = [key for key, count in Counter(paths).items() if count > 1]
    if repeated_paths:
        problems.append({"code": "DUPLICATE_REGISTERED_CATALOGUE_PATH", "values": repeated_paths})
    working = []
    multipose = []
    for row in catalogue["objects"]:
        registry = "bloodborne_dw:" + row["path"]
        entry = by_registry.get(registry)
        if entry is None:
            problems.append({"code": "REGISTERED_TYPE_WITHOUT_NUMERIC_ID", "registry": registry})
            continue
        spec_path = RESOURCES / "composite" / (row["path"] + ".json")
        if not spec_path.is_file():
            problems.append({"code": "CATALOGUE_DESCRIPTOR_MISSING", "registry": registry})
            continue
        spec = read(spec_path)
        if spec["id"] != registry:
            problems.append({"code": "DESCRIPTOR_REGISTRY_MISMATCH", "registry": registry, "actual": spec["id"]})
        if registry in style_retired or canonical.get(registry, registry) != registry:
            continue
        offered = {"id": entry["temporaryId"], "registryId": registry,
                   "name": row["displayName"], "poseCount": len(spec["variants"])}
        working.append(offered)
        if len(spec["variants"]) > 1:
            group = group_rows.get(registry, {})
            roles = [variant.get("sourceVariant", "") for variant in spec["variants"]]
            multipose.append({**offered, "functionalKind": group.get("kind", "artwork"),
                              "memberRegistries": group.get("members", [registry]),
                              "stateRoles": roles,
                              "reason": "Documented orientation, mount, connection, height or reviewed equivalent artwork states; registry and installation item remain one functional type."})
    offered_counts = Counter(row["id"] for row in working)
    repeated_offered = sorted(key for key, count in offered_counts.items() if count > 1)
    if repeated_offered:
        problems.append({"code": "INDEPENDENT_OFFERED_TYPES_SHARE_NUMBER", "values": repeated_offered})

    preview_copies = []
    for folder, name in (("assemblies", "gallery-review-platforms-final.json"),
                         ("doors", "gallery-door-platforms-final.json")):
        source = ROOT / "docs/v12.5-city-review" / folder / name
        for platform in read(source)["platforms"]:
            for component in platform["components"]:
                if component["mainId"] == "90143":
                    preview_copies.append({
                        "section": folder, "platformKey": platform["key"],
                        "groupMainIds": [part["mainId"] for part in platform["components"]],
                        "registryId": component["registryId"],
                        "properties": component["properties"],
                        "relativePosition": component["relativePosition"],
                        "sourceShift": component["sourceShift"],
                    })
    subject = by_number["90143"]
    subject_spec = read(RESOURCES / "composite/source_addon_gate2_1.json")
    subject_variants = subject_spec["variants"]
    highest_reserved = max(int(number) for number in by_number)
    result = {
        "schemaVersion": 1,
        "baselineCommit": BASELINE,
        "originalV12Commit": V12,
        "status": "PASS" if not problems else "FAIL",
        "scope": "OFFLINE_ID_AND_CATALOGUE_AUDIT; runtime registrations/Creative are verified independently in the final GameTests.",
        "authoritativeFiles": {name: digest(RESOURCES / name) for name in
                               ("debug_catalogue.json", "remaining-catalogue.json", "imported-type-aliases.json", "imported-style-retirements.json")},
        "reservedNumericRows": len(entries),
        "distinctNumbers": len(number_counts),
        "distinctPrimaryRegistries": len(registry_counts),
        "rowKindCounts": dict(Counter(row["kind"] for row in entries)),
        "rawAliasRegistries": len(alias_owners),
        "duplicates": duplicates,
        "repeatedRawAliases": repeated_aliases,
        "historicalReservedRows": len(history["entries"]),
        "historicalMissingOrRebound": old_missing_or_rebound,
        "frozenOriginalV12Rows": len(v12["entries"]),
        "originalV12ChangedRows": original_changed,
        "registeredNewCataloguePaths": len(paths),
        "workingNewTypes": len(working),
        "workingNewNumericDuplicates": repeated_offered,
        "hiddenCompatibilityAliasRegistries": sum(key != value for key, value in canonical.items()),
        "styleRetiredRegistries": len(style_retired),
        "sameTypePlacerAndEntityAliases": sum(len(row["aliases"]) > 1 for row in entries),
        "v12StateAliasRows": debug.get("stateAliases", []),
        "newStateFamilies": multipose,
        "90143": {
            "entry": subject,
            "descriptorVariantCount": len(subject_variants),
            "closedParts": subject_variants[0]["poses"]["closed"]["parts"],
            "sourceStateRefs": subject_variants[0]["sourceStateRefs"],
            "importedAliasMappings": [row for row in aliases["mappings"] if row["oldId"] == "90143" or row["newId"] == "90143"],
            "v125ReviewCopies": preview_copies,
            "mainCatalogueCopies": 1,
            "nativeRawImagesInspected": [
                "docs/v12.5-city-review/native/captures/v125-flatgallery-native-front-2/v125-front-DOOR-REVIEW-90143.png",
                "docs/v12.5-city-review/native/captures/v125-flatgallery-native-1/v125-042-2681_2231_0_3_0.png",
            ],
            "finding": "One independent lower grille type with one source model is shown alone and reused below upper grille90142 in two multipart review copies. The whole preview did not yet have its own registry, item or number. Targeting its lower component correctly displayed90143. No separate different registered90143 type or multi-art variant exists in V12.5.",
            "userDecision": "All90143 presentations, including90143+90142, are non-doors. Approved joined construction must receive a single working identity; its old loose elements must be hidden and migrated. Do not invent separate IDs for yaw/copies of the same grille.",
            "practicalPlan": "Keep existing primary90143 if replacing the loose lower type with the joined non-door grille and removing90142 from workingCreative. If any genuinely independent lower grille must remain, keep90143 for it and allocate a never-used append-only number>=93230 for the joined whole. Preserve exact old source yaw/mount/SourceShift and component UUID/settings during migration; never allocate a number per gallery copy.",
        },
        "allocation": {"highestReservedId": str(highest_reserved),
                       "nextAppendOnlyId": str(highest_reserved + 1),
                       "policy": "Do not reuse holes below the highest historical reserved number; reserve every new number before adding a registration."},
        "workingTypes": working,
        "problems": problems,
    }
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=ROOT / "docs/v12.6-functional-assemblies/id-audit/baseline-audit.json")
    args = parser.parse_args()
    result = audit()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: result[key] for key in ("status", "reservedNumericRows", "workingNewTypes", "duplicates", "originalV12ChangedRows", "allocation", "problems")}, ensure_ascii=True))
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
