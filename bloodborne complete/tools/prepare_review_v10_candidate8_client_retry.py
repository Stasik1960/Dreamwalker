"""Prepare a QA-only client retry without replacing frozen server QA or launching tools."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = "tools/review_v10_candidate8_runtime_bound.json"
CLIENT_QA = "build/frozen-artifacts/v10-qa-23/dreamwalker-first-set-review-qa-0.1.0-prototype.5.jar"
OUTPUT = "tools/review_v10_candidate8_runtime_bound23.json"


def read(path):
    value = Path(path)
    if not value.is_absolute():
        value = ROOT / value
    raw = value.read_bytes()
    return json.loads(raw.decode("utf-8-sig")), raw, value


def prepare():
    base, raw, _ = read(SOURCE)
    result = json.loads(json.dumps(base))
    result["schema"] = "dw-v10-candidate8-server22-client23-retry-planning-v1"
    result["status"] = "PREPARED_MAIN8_SERVER_QA22_CLIENT_QA23_HASH_PENDING_NOT_RUN"
    result["serverQaJar"] = base["qaJar"]
    result["serverQaJarSha256"] = base["qaJarSha256"]
    result["qaJar"] = CLIENT_QA
    result["qaJarSha256"] = None
    result["clientQaJar"] = CLIENT_QA
    result["clientQaJarSha256"] = None
    result["executionPerformed"] = False
    result["previousMain8Qa22Plan"] = {"path": SOURCE, "sha256": hashlib.sha256(raw).hexdigest(), "preservedExact": True}
    result["historicalMin15Failure"] = {"wrapper": "reports/V10_CLIENT_MIN_ATTEMPT_15.json", "status": "FAIL_CLIENT_REVIEW", "completedSubsetAudit": "reports/V10_CLIENT_MIN15_PARTIAL_MIDDLE_PICK_TYPED_AUDIT.json", "substitutionForRetry": "FORBIDDEN"}
    updated = {}
    for key, argv in base["commands"].items():
        key = key.replace("min15", "min16")
        values = []
        is_client_launch = len(argv) > 1 and argv[1] == "tools/run_final_client.py"
        for value in argv:
            if is_client_launch and value == base["qaJar"]:
                value = CLIENT_QA
            value = value.replace("MIN_ATTEMPT_15", "MIN_ATTEMPT_16").replace("min-attempt15", "min-attempt16").replace("MIN15_REENTER4", "MIN16_REENTER4")
            values.append(value)
        updated[key] = values
    result["commands"] = updated
    for argv in result["commands"].values():
        if len(argv) > 1 and argv[1] == "tools/verify_v10_actual_client.py" and "--require-middle-pick" not in argv:
            argv.append("--require-middle-pick")
    result["commands"]["independent_actual_middle_keys_after_all_three_clients_stop"] = [
        "C:/Users/vakir/miniconda3/python.exe", "tools/verify_v10_middle_pick.py",
        "--wrapper", "reports/V10_CLIENT_MIN_ATTEMPT_16.json",
        "--reenter-wrapper", "reports/V10_CLIENT_REENTER_RELEASE4_ATTEMPT_1.json",
        "--full-wrapper", "reports/V10_CLIENT_FULL_RELEASE4_ATTEMPT_1.json",
        "--catalogue-audit", "reports/RP_TYPE_DUPLICATE_AUDIT_V10_CANDIDATE8_FINAL.json",
        "--output", "reports/V10_CLIENT_MIDDLE_PICK_CANDIDATE8_INDEPENDENT.json",
    ]
    result["requiredPriorRootFreeze"] = [
        "actual main8 133/native20 core binary manifest remains unchanged",
        "actual client QA23 compile/freeze hash before MIN16 launch; source QA-only inventory sync and typed pre-assert readbacks",
        "server author/reopen/alias/diagnostics commands retain actual QA22; root confirms unchanged relevant server-class bytes rather than assuming whole JAR equality",
    ]
    result["sequence"] = [
        "Root already completed Author7/Creative7/minimal prod reopen5/full owned-scene reopen4 with main8 and QA22; do not re-author from this retry plan",
        "MIN16 with actual QA23 -> REENTER4ATT1 from actual MIN16 saved world -> FULL4ATT1 from pristine actual Author7; only advance after actual normal PASS/save",
        "No alias2/dedicated diagnostics4 launch until all client cost windows/processes close and separate root GO; those commands retain QA22",
        "Alias2 three-process disk proof, then diagnostic OFF60/ON60/REENTER8s only in separately authorized bounded phases",
        "All independent validators and scene archive bind actual MIN16/reenter/full current reports; old MIN15 completed subsets cannot be used as whole PASS",
    ]
    result["clientOnlyChanges"] = {"previous": "QA22", "current": "QA23_PENDING_ACTUAL_FREEZE", "kind": "QA fixture inventory synchronization/readbacks; production unchanged", "serverCommandsChanged": False, "newRuntimeProof": "NOT_RUN"}
    return result


def bind(path):
    manifest, raw, full = read(path)
    if manifest.get("schema") != "dreamwalker-v10-qa-freeze-v1" or manifest.get("status") != "QA_ONLY_BUILT_ACTUAL_CLIENT_RUNTIME_PENDING":
        raise ValueError("Actual QA23 freeze manifest required")
    target = ROOT / OUTPUT
    result, previous, _ = read(OUTPUT)
    if manifest["productionJarSha256"] != result["productionJarSha256"] or Path(manifest["artifact"]).resolve() != (ROOT / CLIENT_QA).resolve():
        raise ValueError("Actual QA23 artifact/production binding differs")
    if result["executionPerformed"] or result["clientQaJarSha256"] not in (None, manifest["sha256"]):
        raise ValueError("Only current unexecuted retry metadata may be bound")
    history = ROOT / ("reports/input-history/V10_MAIN8_QA23_CLIENT_RETRY_PRE_BINDING-" + hashlib.sha256(previous).hexdigest() + ".json")
    if history.exists() and history.read_bytes() != previous:
        raise ValueError("History collision")
    history.write_bytes(previous)
    result["previousQa23PendingPreparation"] = {"path": history.relative_to(ROOT).as_posix(), "sha256": hashlib.sha256(previous).hexdigest()}
    result["status"] = "PREPARED_CURRENT_MAIN8_SERVER_QA22_CLIENT_QA23_ACTUAL_FREEZES_BOUND_NOT_RUN"
    result["qaJarSha256"] = result["clientQaJarSha256"] = manifest["sha256"]
    result["clientQaFreezeManifest"] = {"path": full.relative_to(ROOT).as_posix(), "sha256": hashlib.sha256(raw).hexdigest(), "source": "Actual root compile/freeze manifest; no binary rehash or runtime PASS inferred"}
    result["clientOnlyChanges"]["current"] = "QA23_ACTUAL_COMPILE_FREEZE"
    return result, target


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bind-client-qa-manifest")
    args = parser.parse_args()
    if args.bind_client_qa_manifest:
        result, output = bind(args.bind_client_qa_manifest)
    else:
        output = ROOT / OUTPUT
        if output.exists():
            raise ValueError("Refusing existing preparation overwrite")
        result = prepare()
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": result["status"], "path": str(output), "main": result["productionJarSha256"], "serverQa": result["serverQaJarSha256"], "clientQa": result["clientQaJarSha256"], "executionPerformed": False}))


if __name__ == "__main__":
    main()
