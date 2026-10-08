"""Planning only: current main8, actual QA22 and separate runtime GO.

Does not launch a helper, Minecraft, a build, or an independent world verifier.
"""
import argparse
import hashlib
import json
from pathlib import Path
from prepare_review_v10_candidate8_plan import plan

ROOT = Path(__file__).resolve().parents[1]
JAR = "build/frozen-artifacts/v10-attempt-8/dreamwalker-bb-fabric-1.20.1-0.1.0-prototype.5.jar"
QA = "build/frozen-artifacts/v10-qa-22/dreamwalker-first-set-review-qa-0.1.0-prototype.5.jar"


def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def archive_before_binding(path):
    raw = path.read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    history = ROOT / ("reports/input-history/" + path.stem.upper() + "_PRE_QA22_BINDING-" + digest + ".json")
    if history.exists() and history.read_bytes() != raw:
        raise ValueError("History collision")
    history.write_bytes(raw)
    return {"path": history.relative_to(ROOT).as_posix(), "sha256": digest}


def bind_qa(path):
    frozen = json.loads((ROOT / "build/frozen-artifacts/v10-attempt-8/manifest.json").read_text(encoding="utf-8"))
    qa_path = Path(path)
    if not qa_path.is_absolute():
        qa_path = ROOT / qa_path
    qa_raw = qa_path.read_bytes()
    qa = json.loads(qa_raw.decode("utf-8-sig"))
    if qa["schema"] != "dreamwalker-v10-qa-freeze-v1" or qa["status"] != "QA_ONLY_BUILT_ACTUAL_CLIENT_RUNTIME_PENDING":
        raise ValueError("Actual QA freeze metadata required; runtime PASS is not inferred")
    if qa["productionJarSha256"] != frozen["sha256"] or Path(qa["artifact"]).resolve() != (ROOT / QA).resolve():
        raise ValueError("QA22 must bind this exact production and artifact path")
    if not Path(qa["artifact"]).is_file():
        raise ValueError("Actual frozen QA artifact missing")
    master = json.loads((ROOT / "tools/review_v10_candidate8_runtime_bound.json").read_text(encoding="utf-8"))
    if master["productionJarSha256"] != frozen["sha256"] or master["qaJarSha256"] != qa["sha256"]:
        raise ValueError("Root-bound master plan differs from the supplied actual freeze")
    commands = master["commands"]
    aliases_path = ROOT / "tools/review_v10_alias_server_commands.json"
    diagnostic_path = ROOT / "tools/review_v10_diagnostics_release4_commands.json"
    aliases = json.loads(aliases_path.read_text(encoding="utf-8"))
    diagnostic = json.loads(diagnostic_path.read_text(encoding="utf-8"))
    for value in (aliases, diagnostic):
        if value["productionJarSha256"] != frozen["sha256"] or value["executionPerformed"]:
            raise ValueError("Only current unexecuted preparations may be bound")
        if value["qaJarSha256"] not in (None, qa["sha256"]):
            raise ValueError("Refusing a different previous QA binding")
    expected = [
        (aliases["prepareOnlyArgv"], commands["prepare_alias2_no_launch"]),
        (aliases["verifyOnlyAfterThreeActualNormalPassWrappersArgv"], commands["independent_alias2_after_all_three_stop"]),
        (diagnostic["serverPhaseArgvOnlyAfterSeparateRootGo"], commands["actual_dedicated_off_on_reenter4_separate_root_go"]),
        (diagnostic["independentArgvOnlyAfterThreeServersAndTwoClientsNormalPass"], commands["independent_linked_diagnostics4_after_actual_server3_client2_stop"]),
    ]
    if any(actual != required for actual, required in expected):
        raise ValueError("Prepared argv differs from root-bound master argv")
    freeze_reference = {"path": qa_path.relative_to(ROOT).as_posix(), "sha256": hashlib.sha256(qa_raw).hexdigest(),
                        "artifactSha256": qa["sha256"], "source": "Actual root freeze manifest; this preparation does not rehash the binary or claim runtime PASS"}
    for output, value, status in (
        (aliases_path, aliases, "PREPARED_CURRENT_MAIN8_QA22_BOUND_ALIAS2_NOT_RUN_WAITING_SEPARATE_ROOT_GO"),
        (diagnostic_path, diagnostic, "PREPARED_CURRENT_MAIN8_QA22_BOUND_DIAGNOSTICS4_NOT_RUN_WAITING_SEPARATE_ROOT_GO"),
    ):
        value["previousPendingQaPreparation"] = archive_before_binding(output)
        value["status"] = status
        value["qaJarSha256"] = qa["sha256"]
        value["qaFreezeManifest"] = freeze_reference
        value["qaBindingSource"] = "Actual QA22 root freeze; metadata/class compilation PASS, all requested new runtime proofs remain NOT_RUN"
    aliases["executeOnlyAfterRootGo"] = "Exact main8/QA22 freeze is bound. Invoke prepareOnlyArgv, then add --execute only on separate root GO after all clients/build/cost windows close."
    write(aliases_path, aliases)
    write(diagnostic_path, diagnostic)
    print(json.dumps({"status": "PREPARED_CURRENT_FREEZES_BOUND_NOT_RUN", "artifactSha256": frozen["sha256"], "qaSha256": qa["sha256"],
                      "aliases": str(aliases_path), "diagnostics": str(diagnostic_path)}))


def refresh_clients(path):
    source_path = Path(path)
    if not source_path.is_absolute():
        source_path = ROOT / source_path
    source_raw = source_path.read_bytes()
    source = json.loads(source_raw.decode("utf-8-sig"))
    output = ROOT / "tools/review_v10_diagnostics_release4_commands.json"
    value = json.loads(output.read_text(encoding="utf-8"))
    if (source["productionJarSha256"] != value["productionJarSha256"]
            or source["serverQaJarSha256"] != value["qaJarSha256"]
            or value["serverPhaseArgvOnlyAfterSeparateRootGo"] != source["commands"]["actual_dedicated_off_on_reenter4_separate_root_go"]
            or value["executionPerformed"]):
        raise ValueError("Current server freeze/argv may not change during a client-only reference refresh")
    argv = source["commands"]["independent_linked_diagnostics4_after_actual_server3_client2_stop"]
    value["previousClientPreparation"] = archive_before_binding(output)
    value["independentArgvOnlyAfterThreeServersAndTwoClientsNormalPass"] = argv
    value["plannedClientPathsNotEvidence"] = [argv[argv.index(option) + 1] for option in ("--client-report", "--client-reenter-report")]
    value["clientReferencePlan"] = {"path": source_path.relative_to(ROOT).as_posix(), "sha256": hashlib.sha256(source_raw).hexdigest(), "clientQaJarSha256": source["clientQaJarSha256"], "serverQaJarSha256": source["serverQaJarSha256"], "scope": "Reference argv only; actual runtime wrappers and linked ZIPs required independently"}
    write(output, value)
    print(json.dumps({"status": "PREPARED_CLIENT_REFERENCE_REFRESH_ONLY_NOT_RUN", "path": str(output), "clientReports": value["plannedClientPathsNotEvidence"], "serverArgvUnchanged": True}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bind-qa-manifest", help="Bind existing prepared argv to an actual root QA22 freeze; never launch runtime")
    parser.add_argument("--refresh-clients-from", help="Update standalone diagnostics reference argv from a mixed client/server retry plan; server argv remains exact")
    args = parser.parse_args()
    if args.bind_qa_manifest and args.refresh_clients_from:
        parser.error("Choose one metadata-only operation")
    if args.refresh_clients_from:
        refresh_clients(args.refresh_clients_from)
        return
    if args.bind_qa_manifest:
        bind_qa(args.bind_qa_manifest)
        return
    diagnostic_path = ROOT / "tools/review_v10_diagnostics_release4_commands.json"
    if diagnostic_path.exists():
        raise ValueError("Refusing historical diagnostics plan overwrite; use --bind-qa-manifest for metadata-only binding")
    frozen = json.loads((ROOT / "build/frozen-artifacts/v10-attempt-8/manifest.json").read_text(encoding="utf-8"))
    prepared = plan(JAR, QA, frozen["sha256"], None)
    commands = prepared["commands"]
    path = ROOT / "tools/review_v10_alias_server_commands.json"
    old = path.read_bytes()
    history = ROOT / ("reports/input-history/V10_ALIAS_COMMANDS_MAIN7_QA20_PENDING-" + hashlib.sha256(old).hexdigest() + ".json")
    if history.exists() and history.read_bytes() != old:
        raise ValueError("History collision")
    history.write_bytes(old)
    aliases = json.loads(old.decode("utf-8-sig"))
    aliases.update(
        status="PREPARED_CURRENT_MAIN8_NATIVE_PASS_ALIAS2_NOT_RUN_QA22_ACTUAL_HASH_PENDING_ROOT_GO",
        productionJarSha256=frozen["sha256"], qaJarSha256=None,
        productionBindingSource="build/frozen-artifacts/v10-attempt-8/manifest.json + actual133 native/core20; not a disk compatibility claim",
        qaBindingSource="QA22 correction compile/freeze is owned by root; bind actual hash only after compiled artifact exists",
        prepareOnlyArgv=commands["prepare_alias2_no_launch"],
        executeOnlyAfterRootGo="Bind actual QA22 SHA; invoke prepareOnlyArgv, then add --execute only on separate root GO after all clients/build/cost windows close.",
        expectedActualWrapperPaths=["reports/V10_ALIAS_AUTHOR_RELEASE2.json", "reports/V10_ALIAS_REENTER_RELEASE2.json", "reports/V10_ALIAS_PRODUCTION_REOPEN_RELEASE2.json"],
        expectedSavedWorldPaths=["build/runtime-server-v10-alias-author-release2/isolated-smoke-world", "build/runtime-server-v10-alias-reenter-release2/isolated-smoke-world", "build/runtime-server-v10-alias-production-reopen-release2/isolated-smoke-world"],
        verifyOnlyAfterThreeActualNormalPassWrappersArgv=commands["independent_alias2_after_all_three_stop"],
        executionPerformed=False,
        previousMain7PreparedCommands={"path": history.relative_to(ROOT).as_posix(), "sha256": hashlib.sha256(old).hexdigest()},
        typedEligibility={"savedTagRenamesInSnapshotFix": [], "newSnapshotPublicationToken": "transient; not written to NBT or picked items", "aliasSources": "QA20 complete inventory extension remains frozen; actual main8 AUTHOR/REENTER/QA-free disk proof NOT_RUN", "instanceDeduplication": "not performed; all14 separately identified saved instances required"},
    )
    write(path, aliases)
    diagnostic = {
        "schema": "dw-v10-diagnostics-release4-prepared-commands-v1",
        "status": "PREPARED_CURRENT_MAIN8_NATIVE_PASS_DIAGNOSTICS4_NOT_RUN_QA22_ACTUAL_HASH_PENDING_ROOT_GO",
        "productionJarSha256": frozen["sha256"], "productionJar": JAR,
        "qaJar": QA, "qaJarSha256": None, "executionPerformed": False,
        "baseline": "build/runtime-server-v10-author-min-attempt7/isolated-smoke-world",
        "authorReport": "reports/V10_SERVER_AUTHOR_MIN_ATTEMPT_7.json",
        "settingsReport": "reports/V10_SCENE_BEFORE_FIRST_ENTRY_SETTINGS_7.json",
        "markerDirectory": "tools/review-v10-diagnostics-markers-release4",
        "markerPreparationOwner": "candidate8 author-validation helper; actual marker4 exists only after actual Author7/settings7 normal PASS",
        "serverPhaseArgvOnlyAfterSeparateRootGo": commands["actual_dedicated_off_on_reenter4_separate_root_go"],
        "independentArgvOnlyAfterThreeServersAndTwoClientsNormalPass": commands["independent_linked_diagnostics4_after_actual_server3_client2_stop"],
        "expectedWrapperPaths": ["reports/DIAGNOSTICS_SERVER_DISABLED_V10_RELEASE4.json", "reports/DIAGNOSTICS_SERVER_ENABLED_V10_RELEASE4.json", "reports/DIAGNOSTICS_SERVER_REENTER_V10_RELEASE4.json"],
        "plannedClientPathsNotEvidence": ["reports/V10_CLIENT_MIN_ATTEMPT_15.json", "reports/V10_CLIENT_REENTER_RELEASE4_ATTEMPT_1.json"],
        "independentOutput": "reports/REVIEW_V10_DIAGNOSTICS_RELEASE4.json",
        "requiredGuards": ["exact actual main8 production and supplied QA freeze", "same immutable actual Author7 baseline for OFF60 and ON60; REENTER copied from actual ON saved world", "separate root GO after all MC/build/cost windows close", "normal save/exit, actual linked ZIPs and typed target preservation; never reuse main7 PASS", "raw costs descriptive; no GPU/objectFPS/causal percentage claim"],
    }
    write(diagnostic_path, diagnostic)
    print(json.dumps({"status": "PREPARED_NOT_RUN", "aliases": str(path), "diagnostics": str(diagnostic_path), "artifactSha256": frozen["sha256"]}))


if __name__ == "__main__":
    main()
