"""Preserve completed actual V10 subchecks without promoting a failed client run."""
from __future__ import annotations
import argparse
import collections
import hashlib
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return json.loads(path.read_text("utf-8"))

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def require(value, reason):
    if not value:
        raise AssertionError(reason)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--wrapper", required=True, type=Path)
    parser.add_argument("--catalogue-audit", required=True, type=Path)
    parser.add_argument("--expiry", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    require(not args.output.exists(), "Never overwrite historical proof")
    wrapper = read(args.wrapper)
    raw_path = Path(wrapper["client_review_output"]["path"])
    require(sha(raw_path) == wrapper["client_review_output"]["sha256"], "Actual raw hash differs from wrapper")
    actual = read(raw_path)
    require(actual == wrapper["client_review_output"]["result"], "Actual raw differs from embedded wrapper")
    require(wrapper["status"] == "FAIL_CLIENT_REVIEW" and wrapper["exit_code"] == 0, "Only failed review with normal child exit supported")
    require(actual["status"] == "FAIL_ACTUAL_CLIENT_V10" and actual["phase"] == 3 and "timeout phase=3" in actual["failure"], "Actual diagnostics-stage timeout evidence required")
    audit = read(args.catalogue_audit)
    current_sha = wrapper["artifact_sha256"]
    require(current_sha == actual["productionJarSha256"] == audit["productionJarSha256"], "Actual current artifact binding differs")
    canonical = {r["asset"] for r in audit["rows"] if r["offeredCanonical"]}
    rows = actual["ordinaryRpCases"]
    primary = [r for r in rows if r["part"] == "authored-first-part"]
    require(len(primary) == 69 and {r["asset"] for r in primary} == canonical, "All exact canonical69 actual cases required")
    uuids = set()
    for row in primary:
        require(row["status"] == "PASS_ACTUAL_ORDINARY_PLACE_AND_CREATIVE_PART_ATTACK", "Canonical case failed")
        require(row["uuid"] not in uuids, "Canonical cases must have distinct actual UUIDs")
        uuids.add(row["uuid"])
        require(row["serverRegistry"] == "bloodborne_rp:"+row["asset"], "Wrong actual accepted server type")
        for field in ("nativeBlockItemPlaced", "nativePlacementSameServerClientUuid", "nativeBlockSurvivesRpAttack", "nativeBlockOrdinaryCleanup"):
            require(row[field] is True, "Missing real native-inside/UUID/attack/cleanup proof: "+field)
        require(row["nativeStableRpBefore"] and row["nativeStableRpBefore"] == row["nativeStableRpAfter"], "Stable RP typed readback differs")
        require(row["nativeBlockRegistry"] == "minecraft:stone" and len(row["nativeBlockCell"]) == 3, "Actual ordinary BlockItem cell missing")
        require(row["nativeBlockRelation"] in ("INTERSECTS_SOURCE_SELECTION", "ADJACENT_ROOT"), "Declared relation required")
        require(row["serverThreadPlacementObservation"]["readThread"] == row["serverThreadRemovalObservation"]["readThread"] == "ACTUAL_SERVER_THREAD", "Coherent server readback missing")
        require(not row["serverThreadRemovalObservation"]["present"], "Actual RP removal not observed")
    extras = [r for r in rows if r["part"] != "authored-first-part"]
    require(len(extras) == 4 and {(r["asset"], r["part"]) for r in extras} == {("door_1", "opened-moving-part"), ("ladder", "collapsed-moving-lower"), ("ladder", "upper-platform"), ("ladder", "remote-working-lower")}, "All real source-purpose extras required")
    for row in extras:
        require(row["status"] == "PASS_ACTUAL_ORDINARY_PLACE_AND_CREATIVE_PART_ATTACK" and not row["serverThreadRemovalObservation"]["present"], "Source-purpose ordinary deletion failed")
    walk = next(r["actualPlatformWalk"] for r in extras if r["part"] == "upper-platform")
    require(walk["status"] == "PASS_ACTUAL_SURVIVAL_PLATFORM_WALK" and walk["serverMovedZ"] > .8, "Actual Survival platform displacement failed")
    require(abs(walk["serverFeetY"]-walk["platformTopY"]) < .06 and abs(walk["clientFeetY"]-walk["platformTopY"]) < .06, "Actual native gravity support failed")
    architecture = actual["ordinaryArchitectureCases"]
    entries = read(ROOT/"src/architecture/resources/bloodborne_dw/debug_catalogue.json")["entries"]
    architecture_set = {r["registryId"] for r in entries if r["kind"] == "architecture"}
    require(len(architecture) == 18 and {r["registry"] for r in architecture} == architecture_set, "Actual exact architecture18 cases required")
    for row in architecture:
        require(row["status"] == "PASS_ACTUAL_ORDINARY_ARCHITECTURE_ITEM_AND_CREATIVE_ROOT_OR_HELPER_ATTACK", "Architecture input case failed")
        require(row["serverThreadLedgerCleanup"]["remainingOwnerContributions"] == 0 and row["serverThreadRemovalObservation"]["air"], "Actual architecture cleanup failed")
    require(actual["ordinaryRpOverlap"]["status"].startswith("PASS_ACTUAL_ORDINARY_RP_ON_RP"), "Actual duplicate-overlap route missing")
    require(actual["temporaryConstructionFixtureImmutable"] is True and actual["preConstructionFixtureState"] == actual["postConstructionFixtureState"], "Known fixture typed state changed")
    actor = actual["originalActorRestorationBeforeMenus"]
    require(actor["readThread"] == "ACTUAL_SERVER_THREAD" and all(value is True for key, value in actor.items() if key != "readThread"), "Original actor hand/pose/abilities restoration failed")
    require(actual["firstEntrySnapshot"]["capturedBeforeAnyPrepare"] is True, "Untouched first-entry proof missing")
    menus = actual["menusActual"]
    require(menus["status"] == "PASS_ACTUAL_CLIENT_MENUS_PACKETS_RULES_COMMANDS" and menus["completedSteps"] == 237 and len(menus["steps"]) == 237, "Actual237 menu checks missing")
    expiry = read(args.expiry)
    marker_path = Path(expiry["originalMarkerPath"])
    require(sha(marker_path) == expiry["originalMarkerSha256"] == actual["markerSha256"], "Original disk marker was changed")
    require(expiry["phase"] == 3 and expiry["newMaxClientTicks"] == expiry["actualTicksAtExpiry"]+2 < expiry["originalMaxClientTicks"], "Authorized test-only bound mismatch")
    require(actual["clientTicks"] == expiry["newMaxClientTicks"]+1, "Original QA timeout path did not expire at declared bound")
    archive_raw = ROOT/"reports/runtime/V10_CLIENT_MIN13_DIAGNOSTICS_GUARD_FAILURE.json"
    archive_raw.parent.mkdir(parents=True, exist_ok=True)
    if archive_raw.exists():
        require(archive_raw.read_bytes() == raw_path.read_bytes(), "Primary raw history differs")
    else:
        shutil.copyfile(raw_path, archive_raw)
    result = {"schema": "dw-v10-completed-actual-subchecks-from-failed-client-v1",
              "status": "PASS_COMPLETED_ACTUAL_SUBCHECKS_ONLY_WHOLE_CLIENT_FAILED_DIAGNOSTICS_QA_GUARD",
              "productionJarSha256": current_sha, "wholeClientStatus": actual["status"], "wholeWrapperStatus": wrapper["status"], "ordinaryChildExitCode": wrapper["exit_code"],
              "checks": {"canonicalOrdinaryRp": 69, "nativeRelationCounts": dict(collections.Counter(r["nativeBlockRelation"] for r in primary)),
                         "sourcePurposeExtras": 4, "architectureOrdinaryTypes": 18, "menusActualSteps": 237,
                         "realSurvivalPlatformWalk": walk, "knownFixtureTypedSnapshotEqual": True, "originalActorRestored": True,
                         "diagnosticsActualClient": "FAIL_QA_CALLER_GUARD_HANG_NOT_FINAL_DIAGNOSTICS_PASS"},
              "inputs": [{"path": str(p.resolve()), "sha256": sha(p)} for p in (args.wrapper, raw_path, args.catalogue_audit, args.expiry)],
              "primaryRawCopy": {"path": str(archive_raw.relative_to(ROOT)), "sha256": sha(archive_raw)},
              "failure": {"phase": actual["phase"], "reason": actual["failure"], "authorizedExpiry": expiry},
              "limits": ["Partial subchecks do not pass final client/package gates.", "Selection-intersection and adjacent-root cells are distinct claims; nativeRelationCounts is authoritative, not69 visible-mesh-interior placements.", "Known fixture equality excludes global transient allocation/tombstone revisions; no whole-world byte identity claim.", "Manual visual acceptance and complete matched diagnostic windows NOT_RUN in this failed attempt."]}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
    print(json.dumps({"status": result["status"], "path": str(args.output)}, ensure_ascii=True))

if __name__ == "__main__":
    main()
