"""Prepare only a new FULL attempt; retain actual MIN/REENTER/server QA bindings."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = "tools/review_v10_candidate8_runtime_bound23.json"
OUTPUT = "tools/review_v10_candidate8_runtime_bound24.json"
QA24 = "build/frozen-artifacts/v10-qa-24/dreamwalker-first-set-review-qa-0.1.0-prototype.5.jar"


def read(path):
    path = Path(path)
    if not path.is_absolute(): path = ROOT / path
    raw = path.read_bytes()
    return json.loads(raw.decode("utf-8-sig")), raw, path


def prepare():
    source, raw, _ = read(BASE)
    if not source["clientQaJarSha256"] or source["executionPerformed"]:
        raise ValueError("Bound current MIN16/REENTER QA23 preparation required")
    value = json.loads(json.dumps(source))
    value["schema"] = "dw-v10-main8-server22-min23-full24-retry-planning-v1"
    value["status"] = "PREPARED_CURRENT_MAIN8_SERVER22_MIN23_FULL24_HASH_PENDING_NOT_RUN"
    value["previousQa23Plan"] = {"path": BASE, "sha256": hashlib.sha256(raw).hexdigest(), "preservedExact": True}
    value["minReenterClientQaJar"] = source["clientQaJar"]
    value["minReenterClientQaJarSha256"] = source["clientQaJarSha256"]
    value["fullClientQaJar"] = QA24
    value["fullClientQaJarSha256"] = None
    value["qaJar"] = QA24
    value["qaJarSha256"] = None
    for key, argv in value["commands"].items():
        is_full = len(argv) > 1 and argv[1] == "tools/run_final_client.py" and "full_client" in argv
        if is_full:
            argv[argv.index("--extra-mod") + 1] = QA24
        value["commands"][key] = [arg.replace("FULL_RELEASE4_ATTEMPT_1", "FULL_RELEASE4_ATTEMPT_2").replace("full-release4-attempt1", "full-release4-attempt2") for arg in argv]
    strict_actual_client_commands(value)
    value["historicalFullAttempt1Failure"] = {"wrapper": "reports/V10_CLIENT_FULL_RELEASE4_ATTEMPT_1.json", "wholeStatus": "FAIL_CLIENT_REVIEW", "phase": 0, "scope": "Joined current main8 scene and observed initial active Iris pipeline; QA proxy rejected Continuity transform bookkeeping before ordinary object/menu stages. No whole-client or final shader proof inferred.", "substitutionForRetry": "FORBIDDEN"}
    value["qaArtifactRoleBindings"] = {"serverAuthorAliasDiagnostics": "frozen QA22", "completedMin16AndReenter4Att1": "frozen QA23", "onlyNewFull4Att2": "QA24_PENDING_ACTUAL_ROOT_FREEZE"}
    value["requiredPriorRootFreeze"] = ["main8 unchanged/native133/core20 actual", "actual QA24 binary/compile manifest before new FULL4ATT2", "existing MIN16/REENTER4ATT1 remain QA23, server/alias/diagnostics remain QA22; no whole-JAR equality assumption"]
    value["sequence"] = ["Root already completed MIN16 and REENTER4ATT1 normally on main8/QA23; do not rerun or replace those wrappers", "Root separately authorizes alias2 main8/QA22 and independent typed audit", "Bind actual QA24 hash, then root separately launches only FULL4ATT2 from pristine Author7 with unchanged strict shader marker4", "Dedicated diagnostics4 main8/QA22 remains separate GO after all client cost windows/processes close", "Validators use actual FULL4ATT2 where full proof is required; preserve stage0 FAIL FULL4ATT1"]
    value["clientOnlyChanges"] = {"fullPrevious": "QA23", "fullRetry": "QA24_PENDING_ACTUAL_FREEZE", "scope": "QA geometry-render probe distinguishes wrapper push/pop-transform bookkeeping from emitted quads", "serverCommandsChanged": False, "minReenterCommandsChanged": False, "newRuntimeProof": "NOT_RUN"}
    return value


def strict_actual_client_commands(value):
    for argv in value["commands"].values():
        if len(argv) > 1 and argv[1] == "tools/verify_v10_actual_client.py":
            if "--require-middle-pick" not in argv: argv.append("--require-middle-pick")
            argv[argv.index("--output") + 1] = "reports/V10_CLIENT_MIN16_REENTER4_ROSTER_PARTS_MIDDLE_DIAGNOSTICS_INDEPENDENT.json"
    value["commands"]["independent_full4_att2_after_normal_stop_require_middle"] = [
        "C:/Users/vakir/miniconda3/python.exe", "tools/verify_v10_actual_client.py",
        "--wrapper", "reports/V10_CLIENT_FULL_RELEASE4_ATTEMPT_2.json",
        "--catalogue-audit", "reports/RP_TYPE_DUPLICATE_AUDIT_V10_CANDIDATE8_FINAL.json",
        "--require-middle-pick", "--output", "reports/V10_CLIENT_FULL4_ATT2_ROSTER_PARTS_MIDDLE_DIAGNOSTICS_INDEPENDENT.json",
    ]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bind-full-qa-manifest")
    parser.add_argument("--require-middle-pick-for-current-plan", action="store_true", help="Refresh current argv only; preserve previous plan bytes and leave actual reports unchanged")
    args = parser.parse_args()
    if args.bind_full_qa_manifest and args.require_middle_pick_for_current_plan: parser.error("Choose one metadata-only operation")
    output = ROOT / OUTPUT
    if args.require_middle_pick_for_current_plan:
        value, before, _ = read(OUTPUT)
        if value["executionPerformed"]: raise ValueError("Only unexecuted planned argv may be refreshed")
        history = ROOT / ("reports/input-history/V10_MAIN8_QA24_PLAN_PRE_REQUIRE_MIDDLE-" + hashlib.sha256(before).hexdigest() + ".json")
        if history.exists() and history.read_bytes() != before: raise ValueError("History collision")
        history.write_bytes(before)
        value["previousNonStrictMiddlePreparation"] = {"path": history.relative_to(ROOT).as_posix(), "sha256": hashlib.sha256(before).hexdigest()}
        strict_actual_client_commands(value)
    elif args.bind_full_qa_manifest:
        value, before, _ = read(OUTPUT)
        manifest, raw, path = read(args.bind_full_qa_manifest)
        if (manifest["schema"] != "dreamwalker-v10-qa-freeze-v1" or manifest["status"] != "QA_ONLY_BUILT_ACTUAL_CLIENT_RUNTIME_PENDING"
                or manifest["productionJarSha256"] != value["productionJarSha256"]
                or Path(manifest["artifact"]).resolve() != (ROOT / QA24).resolve()
                or value["executionPerformed"] or value["fullClientQaJarSha256"] not in (None, manifest["sha256"])):
            raise ValueError("Exact actual QA24 compile/freeze required; only unexecuted preparation may be bound")
        history = ROOT / ("reports/input-history/V10_MAIN8_QA24_FULL_RETRY_PRE_BINDING-" + hashlib.sha256(before).hexdigest() + ".json")
        if history.exists() and history.read_bytes() != before: raise ValueError("History collision")
        history.write_bytes(before)
        value["previousQa24PendingPreparation"] = {"path": history.relative_to(ROOT).as_posix(), "sha256": hashlib.sha256(before).hexdigest()}
        value["status"] = "PREPARED_CURRENT_MAIN8_SERVER22_MIN23_FULL24_ACTUAL_FREEZES_BOUND_NOT_RUN"
        value["qaJarSha256"] = value["fullClientQaJarSha256"] = manifest["sha256"]
        value["fullClientQaFreezeManifest"] = {"path": path.relative_to(ROOT).as_posix(), "sha256": hashlib.sha256(raw).hexdigest(), "source": "Actual root freeze metadata; no runtime PASS inferred"}
        value["qaArtifactRoleBindings"]["onlyNewFull4Att2"] = "QA24_ACTUAL_COMPILE_FREEZE"
        value["clientOnlyChanges"]["fullRetry"] = "QA24_ACTUAL_COMPILE_FREEZE"
    else:
        if output.exists(): raise ValueError("Refusing historical preparation overwrite")
        value = prepare()
    output.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": value["status"], "path": str(output), "fullClientSha": value["fullClientQaJarSha256"], "executionPerformed": False}))


if __name__ == "__main__": main()
