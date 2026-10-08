"""Preserve a failed client's completed middle-key checks without a whole-run PASS."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from verify_v10_middle_pick import check_pick

ROOT = Path(__file__).resolve().parents[1]


def load(path):
    path = Path(path)
    if not path.is_absolute():
        path = ROOT / path
    raw = path.read_bytes()
    if len(raw) > 8 * 1024 * 1024:
        raise ValueError("Bounded raw client/wrapper JSON required")
    return json.loads(raw.decode("utf-8-sig")), {"path": path.relative_to(ROOT).as_posix(), "sha256": hashlib.sha256(raw).hexdigest(), "bytes": len(raw)}


def require(value, reason):
    if not value:
        raise ValueError(reason)


def pick(row, roster, aliases):
    value = row["actualMiddlePick"]
    source = value["sourceRegistry"].removeprefix("bloodborne_rp:")
    canonical = aliases.get(source, source)
    check_pick(value, value["sourceUuid"], source, canonical, roster[canonical]["source"]["model"]["path"])
    require(value["status"] == "PASS_ACTUAL_MIDDLE_KEY_CANONICAL_ITEM_SETTINGS_SOURCE_UNCHANGED", "Actual middle-key result missing")
    require(value["actualSourceHit"]["type"] == "ENTITY" and value["actualSourceHit"]["entityUuid"] == value["sourceUuid"], "Exact real source ray UUID required")
    require(value["sourceStableTypedBefore"] == value["sourceStableTypedAfter"], "Picked source typed data changed")
    require(value["serverClientSettingsMatch"] and value["noInstanceIdentityCopied"] and value["sourceUnchanged"], "Observed pick invariants missing")
    require(value["pickedItem"] == "bloodborne_rp:" + value["canonicalAsset"] + "_placer", "Picked item must be canonical")
    require(value["inputPath"] == "actual pickItemKey queued once; MinecraftClient native doItemPick and Creative inventory packet", "Direct getPickBlockStack is not actual key proof")
    return {key: value[key] for key in ("sourceUuid", "sourceRegistry", "canonicalAsset", "pickedItem", "status", "canonicalClientModel")}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--wrapper", required=True)
    parser.add_argument("--raw", required=True)
    parser.add_argument("--artifact-sha", required=True)
    parser.add_argument("--catalogue-audit", default="reports/RP_TYPE_DUPLICATE_AUDIT_V10_CANDIDATE8_FINAL.json")
    parser.add_argument("--report", required=True)
    args = parser.parse_args()
    output = ROOT / args.report
    require(not output.exists(), "Refusing historical partial proof overwrite")
    wrapper, wrapper_ref = load(args.wrapper)
    raw, raw_ref = load(args.raw)
    catalogue, catalogue_ref = load(args.catalogue_audit)
    require(catalogue["productionJarSha256"] == args.artifact_sha, "Current canonical model roster identity differs")
    roster = {row["asset"]: row for row in catalogue["rows"] if row["offeredCanonical"]}
    alias_map = catalogue["existingAliasImplementation"]["mapping"]
    require(wrapper["artifact_sha256"] == args.artifact_sha == raw["productionJarSha256"], "Current production identity differs")
    require(wrapper["status"] == "FAIL_CLIENT_REVIEW" and raw["status"] == "FAIL_ACTUAL_CLIENT_V10", "This scoped report must preserve whole-client failure")
    require(wrapper["exit_code"] == 0 and wrapper["integrated_save_messages_present"], "Observed normal failure shutdown/save evidence required")
    require(raw["phase"] == 7 and raw["failure"] == "Middle-pick hotbar/inventory/selected slot must restore before menus", "Different failure boundary requires a fresh audit")
    require("menusActual" not in raw and "diagnosticsActualClient" not in raw, "Later phases unexpectedly executed")
    rp = raw["ordinaryRpCases"]
    require(len(rp) == 73 and all(row["status"] == "PASS_ACTUAL_ORDINARY_PLACE_AND_CREATIVE_PART_ATTACK" for row in rp), "Completed 69 canonical +4 extra RP cases missing")
    canonical = [pick(row, roster, alias_map) for row in rp if "actualMiddlePick" in row]
    require(len(canonical) == 69 and {row["canonicalAsset"] for row in canonical} == set(roster), "Unique canonical roster mismatch")
    aliases = [pick(row, roster, alias_map) for row in raw["ordinaryAliasPickCases"]]
    require(len(aliases) == 7 and {row["sourceRegistry"].removeprefix("bloodborne_rp:") for row in aliases} == set(alias_map), "Old alias roster mismatch")
    architecture = raw["ordinaryArchitectureCases"]
    require(len(architecture) == 18 and all(row["status"] == "PASS_ACTUAL_ORDINARY_ARCHITECTURE_ITEM_AND_CREATIVE_ROOT_OR_HELPER_ATTACK" for row in architecture), "Architecture cases incomplete")
    reinstall = raw["actualPickedItemReinstallation"]
    require(reinstall["status"] == "PASS_ACTUAL_MIDDLE_PICK_CANONICAL_STACK_ORDINARY_REPLACE_NEW_UUID_SETTINGS_AND_CLEANUP", "Ordinary picked-item reinstallation incomplete")
    require(reinstall["actualServerThreadPlacement"]["linksEmpty"] and reinstall["actualCleanupServerThread"]["creativeDropCount"] == 0 and not reinstall["actualCleanupServerThread"]["present"], "New instance cleanup or link isolation failed")
    server_readback = raw["lastActualServerThreadReadback"]
    require(server_readback["serverInventoryRestored"] and server_readback["readThread"] == "ACTUAL_SERVER_THREAD", "Server inventory readback not authoritative")
    report = {
        "schema": "dw-v10-failed-client-partial-middle-proof-v1",
        "status": "PASS_COMPLETED_MIDDLE_KEY_SUBSET_ONLY_WHOLE_CLIENT_FAILED_BEFORE_MENUS",
        "artifactSha256": args.artifact_sha,
        "wrapper": wrapper_ref, "raw": raw_ref, "currentCatalogue": catalogue_ref,
        "perPickTypedChecks": "Shared independent check_pick verifies actual key/sentinel, real ray UUID, exact supported typed settings/name, canonical model/item and absent instance identity/provenance fields; no whole-run success is inferred.",
        "wholeClientStatus": wrapper["status"], "rawClientStatus": raw["status"],
        "normalExitCode": wrapper["exit_code"], "normalShutdownSaveMessagesObserved": True,
        "wholeQaSaveStageCompleted": False,
        "completed": {"canonicalMiddleKeys": len(canonical), "oldAliasMiddleKeys": len(aliases), "pickedItemOrdinaryReinstallation": 1, "ordinaryRpCases": len(rp), "ordinaryArchitectureCases": len(architecture)},
        "canonicalMiddleRows": canonical, "oldAliasMiddleRows": aliases,
        "pickedItemReinstallation": {"status": reinstall["status"], "newUuid": reinstall["newUuid"], "actualServerThreadPlacement": reinstall["actualServerThreadPlacement"], "cleanup": reinstall["actualCleanupServerThread"]},
        "failureBoundary": {"phase": raw["phase"], "failure": raw["failure"], "serverReadback": server_readback, "interpretation": "The checked server inventory is restored. The joint server/client inventory assertion failed, locating the observed failure to client inventory/selected-slot restoration. Exact residual slot/sentinel cause is not present in this raw report; no production defect is inferred from this partial evidence."},
        "notCompleted": ["original client inventory restoration before menus", "237 GUI menu operations", "actual client diagnostics OFF/ON/OFF and exports", "final whole-QA save assertions", "distinct persisted REENTER proof", "full-client Starlight loading proof", "14-instance old alias disk roundtrip"],
        "manualReview": "NOT_RUN", "wholeTask": "NOT_COMPLETE",
    }
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": report["status"], "report": str(output), "completed": report["completed"]}))


if __name__ == "__main__":
    main()
