"""Read-only artifact-bound validation of dedicated native save migration/reopen."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import zipfile
from pathlib import Path

BASELINE_SHA = "34efa3a0f8a1506c97a754fb5c887db977ca489b3cc514c84748de07e3688054"
COUNT = 1281
PARTIAL_NOT_RUN = "NOT_RUN_NO_UNLOADED_CROSS_CHUNK_SAVED_ASSEMBLY_AFTER_STARTUP"
REPARSE_POINT = 0x400


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def ordinary_files(root: Path) -> dict[str, Path]:
    """Reject reparse points; do not traverse a source through a link."""
    found = {}
    pending = [root]
    while pending:
        directory = pending.pop()
        info = directory.stat(follow_symlinks=False)
        require(not getattr(info, "st_file_attributes", 0) & REPARSE_POINT,
                f"Source directory is a reparse point: {directory}")
        with os.scandir(directory) as entries:
            for entry in entries:
                info = entry.stat(follow_symlinks=False)
                require(not entry.is_symlink() and not getattr(info, "st_file_attributes", 0) & REPARSE_POINT,
                        f"Source entry is a reparse point: {entry.path}")
                path = Path(entry.path)
                if entry.is_dir(follow_symlinks=False):
                    pending.append(path)
                elif entry.is_file(follow_symlinks=False):
                    found[path.relative_to(root).as_posix()] = path
                else:
                    raise AssertionError(f"Source has a non-ordinary entry: {path}")
    return found


def copied_source(profile: dict) -> dict:
    copied = profile.get("derived_world_copy") or {}
    require(copied.get("copy_byte_verification") == "PASS", "World copy byte verification did not pass")
    require(copied.get("source_unchanged_after_run") is True, "Profile did not prove source unchanged")
    root = Path(copied["source"])
    records = copied.get("files", [])
    require(records, "Empty source-world snapshot")
    omitted = set(copied.get("omitted_files", []))
    require(omitted <= {"session.lock"}, f"Unexpected omitted source files: {omitted}")
    expected = {record["path"]: record for record in records}
    require(len(expected) == len(records), "Duplicate source-world snapshot paths")
    actual = ordinary_files(root)
    require(set(actual) - omitted == set(expected), "Source file set differs from the pre-copy snapshot")
    total = 0
    for name, record in expected.items():
        path = actual[name]
        require(path.stat().st_size == record["bytes"], f"Source size changed: {name}")
        require(sha(path) == record["sha256"], f"Source bytes changed: {name}")
        total += record["bytes"]
    return {"status": "PASS", "source": str(root), "files": len(records),
            "bytes": total, "omittedFiles": sorted(omitted),
            "snapshotRecordsSha256": hashlib.sha256(json.dumps(records, sort_keys=True).encode()).hexdigest()}


def checked_profile(path: Path, native_path: Path, label: str, production_sha: str) -> tuple[dict, dict]:
    profile = read_json(path)
    require(profile.get("status") == "PASS", f"{label} profile is not PASS")
    require(profile.get("artifact_sha256") == production_sha, f"{label} profile does not bind the actual candidate JAR")
    require(profile.get("minecraft") == "1.20.1" and profile.get("loader") == "0.16.10", f"{label} wrong game/loader")
    require('17.0.' in profile.get("java_version", ""), f"{label} did not use Java 17")
    require(profile.get("world_startup") == "PASS" and profile.get("exit_code") == 0,
            f"{label} did not complete a successful native server lifecycle")
    require(not profile.get("errors") and not profile.get("termination"), f"{label} has runtime errors/forced termination")
    artifact = Path(profile["artifact"])
    require(sha(artifact) == production_sha, f"{label} production artifact bytes differ from the candidate JAR")
    run = Path(profile["run_directory"])
    console = Path(profile["evidence"])
    require(console.resolve() == (run / "launch-console.log").resolve(), f"{label} console belongs to another run")
    require(sha(console) == profile["console_sha256"], f"{label} console bytes changed")
    log = console.read_text(encoding="utf8", errors="replace")
    optional_reach = re.compile(r'^\[[^\]]+\] \[main/WARN\]: Error loading class: '
        r'com/jamieswhiteshirt/reachentityattributes/ReachEntityAttributes '
        r'\(java\.lang\.ClassNotFoundException: com/jamieswhiteshirt/reachentityattributes/ReachEntityAttributes\)$')
    errors = [line for line in log.splitlines() if ('/ERROR]' in line or 'Exception' in line or 'OutOfMemoryError' in line)
              and not optional_reach.fullmatch(line)]
    require(not errors, f"{label} native console errors: {errors[:3]}")
    require(re.search(r'Done \([\d.]+s\)!', log) is not None, f"{label} console has no startup completion")
    stop = log.rfind("Stopping server")
    saved = log.rfind("Saving worlds")
    all_saved = log.rfind("All dimensions are saved")
    require(0 <= stop < saved < all_saved, f"{label} lacks normal stop followed by complete world save")
    require((run / "qa-normal-stop.request").is_file(), f"{label} lacks normal QA stop request")
    require(read_json(run / "v124-native-mode.json") == {"mode": "audit", "schemaVersion": 1},
            f"{label} wrong isolated QA mode")
    require(sha(native_path) == sha(run / "v124-native-output.json"), f"{label} native evidence does not match actual run output")
    addons = [record for record in profile.get("extraMods", []) if record.get("id") == "dreamwalker_v124_qa"]
    require(len(addons) == 1, f"{label} has no unique bound QA addon")
    addon = addons[0]
    require(addon.get("copyByteVerification") == "PASS" and addon.get("copiedSha256") == addon.get("sha256"),
            f"{label} QA addon was not copied exactly")
    require(sha(run / addon["runtimePath"]) == addon["sha256"], f"{label} runtime QA addon bytes changed")
    world_source = copied_source(profile)
    return profile, {"status": "PASS", "profile": str(path), "profileSha256": sha(path),
        "native": str(native_path), "nativeSha256": sha(native_path), "runDirectory": str(run),
        "consoleSha256": profile["console_sha256"], "normalStopAndCompleteSave": "PASS",
        "unexpectedRuntimeErrors": [], "expectedWarnings": profile.get("expectedWarnings", []),
        "qaAddonSha256": addon["sha256"], "sourceWorldBytePreservation": world_source}


def checked_native(native: dict, reopen: bool, production_sha: str) -> dict:
    require(native.get("status") == ("PASS_REOPEN" if reopen else "PASS"), "Native status differs from expected phase")
    require(native.get("reopen") is reopen, "Native evidence belongs to wrong phase")
    require(native.get("beforeProductionJarSha256") == BASELINE_SHA and native.get("afterProductionJarSha256") == production_sha,
            "Native evidence has wrong before/after production artifact")
    for field in ("actualV123SavedFixtures", "loadedFixtures", "verifiedFixtures"):
        require(native.get(field) == COUNT, f"Native {field} is not {COUNT}")
    for field in ("savedUuidRootStateTypedNbtProfileMountAndSourceShift", "globalLedgerNoRetiredOwnersOrOrphans",
                  "oldToolSelectionCommandsAndCounters"):
        require(native.get(field) == "PASS", f"Native {field} is not PASS")
    require(native.get("savedCommandBindingsChecked") == 8, "Native saved commands did not check all eight counters")
    require(native.get("sourceWorldChanged") is False, "Native evidence reports source-world mutation")
    require(not native.get("sourceBoundsDifferences"), "Native evidence reports source vertex bounds differences")
    require(native.get("exactOwnerCellsChecked", 0) > COUNT, "Native owner membership evidence is incomplete")
    require(native.get("savedMechanismLinksChecked") == 0 and native.get("oldMechanismLinksPoliciesAndTransitionEvents") == "NOT_APPLICABLE",
            "Final cohort mechanism links changed from the explicit non-applicable baseline")
    partial_status = native.get("nativePendingChunkOrdering") if reopen else native.get("nativePendingChunkProbeSelection")
    require(partial_status == ("NOT_RUN_REOPEN_ALREADY_CANONICAL" if reopen else PARTIAL_NOT_RUN)
            and native.get("nativePendingChunkEvidence") == [],
            "Natural partial-chunk status differs from the explicitly unobserved final phase")
    if reopen:
        require(native.get("ordinaryInteractions") == 0 and not native.get("interactions"), "Reopen evidence unexpectedly repeated ordinary item interactions")
        require(native.get("ordinaryAliasExactStateInteractions") == "NOT_RUN_REOPEN_ONLY", "Reopen ordinary interaction limit is not explicit")
    else:
        require(native.get("ordinaryInteractions") == COUNT and native.get("ordinaryAliasExactStateInteractions") == str(COUNT),
                "Native ordinary item count is incomplete")
        interactions = native.get("interactions", [])
        require(len(interactions) == COUNT, "Native individual ordinary item evidence is incomplete")
        uuids = set()
        for row in interactions:
            for field in ("status", "inventoryFromNbtRawItemPlaceNativeRayPickBreakCopy", "exactSourceShiftHalfMaterialMountAndArtState"):
                require(row.get(field) == "PASS", f"Ordinary item {row.get('oldId')}:{row.get('oldVariant')} {field} failed")
            require(row.get("protectedInstancePayload") == "PASS_ART_ONLY", "Copied item leaked instance payload")
            require(row.get("newUuid") and row["newUuid"] not in uuids, "Ordinary fresh placements reused a UUID")
            uuids.add(row["newUuid"])
    return {"savedFixturesChecked": COUNT, "ordinaryInteractionsChecked": 0 if reopen else COUNT,
            "uuidRootStateTypedPayloadAndLedger": "PASS", "savedCommandBindings": 8,
            "savedMechanismLinks": "NOT_APPLICABLE", "sourceVertexBoundsCompared": native.get("sourceVertexBoundsCompared"),
            "maximumSourceVertexBoundsDeviation": native.get("sourceBoundsMaximumDeviation"),
            "naturalPartialChunkProbe": partial_status,
            "physicalMouseKeyboard": native.get("physicalMouseKeyboard"),
            "clientVisualAcceptance": "SEPARATE_NATIVE_CLIENT_IMAGE_REVIEW_REQUIRED"}


def checked_saved_rows(profiles: list[dict], manifest: dict, native: dict, production_sha: str) -> dict:
    baseline = read_json(Path(profiles[0]["derived_world_copy"]["source"]) / "v124-fixture.json")
    migration = read_json(Path(profiles[0]["run_directory"]) / "isolated-smoke-world/v124-fixture.json")
    reopened = read_json(Path(profiles[1]["run_directory"]) / "isolated-smoke-world/v124-fixture.json")
    require(baseline.get("status") == "PASS_LEGACY_SAVE" and baseline.get("artExpectationVersion") == 1,
            "Source fixture lacks freshly authored independent legacy art expectations")
    mappings = {(row["oldRegistryId"], row["oldVariant"]): row for row in manifest["mappings"]}
    require(len(mappings) == COUNT, "Production manifest has duplicate/missing exact mappings")
    baseline_rows = baseline.get("fixtures", [])
    require(len(baseline_rows) == COUNT, "Baseline saved fixture is incomplete")
    by_uuid = {row["uuid"]: row for row in baseline_rows}
    require(len(by_uuid) == COUNT, "Baseline saved fixtures reused owner UUIDs")
    key_rows = {(row["oldRegistry"], int(row["oldState"]["variant"])): row for row in baseline_rows}
    require(set(key_rows) == set(mappings), "Genuine saved baseline coverage differs from final production mappings")
    for key, row in key_rows.items():
        require(row["mapping"] == mappings[key], f"Saved mapping differs from frozen production: {key}")
    for saved, label in ((migration, "migration"), (reopened, "reopen")):
        require(saved.get("beforeProductionJarSha256") == BASELINE_SHA and saved.get("afterProductionJarSha256") == production_sha,
                f"{label} saved fixture artifact binding differs")
        require(saved.get("status") == ("PASS_REOPEN" if label == "reopen" else "PASS_SAVED_MIGRATION_AND_ORDINARY_ITEMS"),
                f"{label} saved fixture status did not finish")
        rows = {row["uuid"]: row for row in saved.get("fixtures", [])}
        require(len(saved.get("fixtures", [])) == COUNT and set(rows) == set(by_uuid), f"{label} saved UUID set changed")
        for uuid, old in by_uuid.items():
            row = rows[uuid]
            for field in ("root", "oldRegistry", "newRegistry", "oldState", "mapping", "oldPayloadNbt", "expectedPayloadNbt"):
                require(row.get(field) == old.get(field), f"{label} fixture {uuid} changed {field}")
            require(row.get("afterState") == row.get("expectedState"), f"{label} saved canonical state differs {uuid}")
            require(row.get("afterPayloadNbt") == old.get("expectedPayloadNbt"), f"{label} saved typed payload differs {uuid}")
            require(row.get("canonicalCellCount", 0) > 0, f"{label} owner footprint is empty {uuid}")
            if old.get("commandOwnerUuid"):
                require(row.get("commandGlobalUsedAfterAudit") == 2, f"{label} saved command count changed {uuid}")
    item_keys = {(str(row["oldId"]), row["oldVariant"]): str(row["primaryId"]) for row in native["interactions"]}
    expected_keys = {(str(row["oldId"]), row["oldVariant"]): str(row["newId"]) for row in mappings.values()}
    require(len(item_keys) == COUNT and item_keys == expected_keys, "Native ordinary old-state coverage/primary IDs differ from manifest")
    return {"status": "PASS", "exactProductionMappings": COUNT, "uniqueSavedOwnerUuids": COUNT,
            "baselineMigrationReopenRootStateTypedPayload": "PASS", "ordinaryOldItemStateCoverage": "PASS"}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("migration-profile", "reopen-profile", "migration-native", "reopen-native", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    output = {"schema": "dreamwalker-v124-native-save-bound-evidence-v1", "status": "FAIL",
              "beforeProductionJarSha256": BASELINE_SHA,
              "readOnlyValidation": True}
    try:
        # The candidate is the actual production JAR named by the requested
        # migration profile. Every report/native output and the reopen artifact
        # must bind its bytes; old PASS JSON cannot pass after a rebuild.
        production_sha = sha(Path(read_json(args.migration_profile)["artifact"]))
        output["afterProductionJarSha256"] = production_sha
        output["productionJarSha256"] = production_sha
        migration_profile, migration_proof = checked_profile(args.migration_profile, args.migration_native, "migration", production_sha)
        reopen_profile, reopen_proof = checked_profile(args.reopen_profile, args.reopen_native, "reopen", production_sha)
        require(migration_proof["qaAddonSha256"] == reopen_proof["qaAddonSha256"], "Migration/reopen used different QA addons")
        with zipfile.ZipFile(migration_profile["artifact"]) as jar:
            manifest = json.loads(jar.read("bloodborne_dw/imported-type-aliases.json"))
        require(len(manifest["mappings"]) == COUNT, "Final production manifest mapping count differs")
        migration_native, reopen_native = read_json(args.migration_native), read_json(args.reopen_native)
        migration_proof["nativeChecks"] = checked_native(migration_native, False, production_sha)
        reopen_proof["nativeChecks"] = checked_native(reopen_native, True, production_sha)
        expected_source = Path(migration_profile["run_directory"]) / "isolated-smoke-world"
        require(Path(reopen_profile["derived_world_copy"]["source"]).resolve() == expected_source.resolve(),
                "Reopen did not copy the genuinely migrated save")
        output.update({"status": "PASS", "qaAddonSha256": migration_proof["qaAddonSha256"],
            "migration": migration_proof, "reopen": reopen_proof,
            "savedFixtureCrossCheck": checked_saved_rows([migration_profile, reopen_profile], manifest, migration_native, production_sha),
            "naturalPartialChunkProbe": PARTIAL_NOT_RUN,
            "scopeLimit": "Native dedicated save/items proof; appearance and physical input require separate client evidence."})
    except Exception as error:
        output["failure"] = f"{type(error).__name__}: {error}"
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"status": output["status"], "output": str(args.output), "failure": output.get("failure")}, ensure_ascii=False))
    return 0 if output["status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
