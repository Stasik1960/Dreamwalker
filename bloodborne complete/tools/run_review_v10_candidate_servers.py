"""Explicit fresh V10 author/reopen/full-startup and later diagnostic phases.

Default --phase prepare launches nothing. The diagnostics phase is deliberately
separate, so the caller can wait until every ordinary client has stopped. No
previous candidate runtime PASS substitutes for the supplied artifact SHA.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path, document):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def project_path(path):
    path = path.resolve()
    if not path.is_relative_to(ROOT.resolve()):
        raise ValueError("Evidence paths must remain in this project")
    return path


def child(argv, report=None, digest=None):
    print(json.dumps({"phase": "START", "argv": argv}), flush=True)
    result = subprocess.run(argv, cwd=ROOT)
    if result.returncode != 0:
        raise ValueError("Actual child process failed, exit " + str(result.returncode))
    if report is not None:
        value = json.loads(report.read_text(encoding="utf-8-sig"))
        if value.get("status") != "PASS" or value.get("exit_code") != 0 or value.get("artifact_sha256") != digest:
            raise ValueError("Normal current-artifact runtime wrapper did not PASS: " + str(report))
        return value
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("jar", "qa-jar", "scene-template", "scene-input", "accepted-eula-file", "author-report", "settings-report", "reopen-report", "full-report", "marker-dir", "plan-output"):
        parser.add_argument("--" + name, type=Path, required=True)
    for name in ("artifact-sha", "author-run-name", "reopen-run-name", "full-run-name", "diagnostics-suffix"):
        parser.add_argument("--" + name, required=True)
    parser.add_argument("--phase", choices=("prepare", "author-validation", "diagnostics"), default="prepare")
    parser.add_argument("--full-scene-copy", action="store_true",
                        help="Additive current-candidate mode: full profile reopens the fresh authored baseline without QA; default remains empty-world startup only")
    parser.add_argument("--background-concurrency-note", default="No overlapping Minecraft clients/Gradle intended; OS background load uncontrolled. Descriptive whole-tick costs, no causal FPS/GPU percentage.")
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{64}", args.artifact_sha):
        raise ValueError("Exact current SHA256 is required")
    for value in (args.author_run_name, args.reopen_run_name, args.full_run_name, args.diagnostics_suffix):
        if not re.fullmatch(r"[a-zA-Z0-9_-]+", value) or "v10" not in value.lower() and value != args.diagnostics_suffix:
            raise ValueError("Explicit safe fresh V10 names required")
    jar, qa = args.jar.resolve(strict=True), args.qa_jar.resolve(strict=True)
    if sha(jar) != args.artifact_sha:
        raise ValueError("Supplied production artifact differs from exact current SHA")
    paths = {name: project_path(getattr(args, name)) for name in ("scene_input", "author_report", "settings_report", "reopen_report", "full_report", "marker_dir", "plan_output")}
    baseline = ROOT / "build" / ("runtime-server-" + args.author_run_name) / "isolated-smoke-world"
    runner = str(ROOT / "tools/run_final_server.py")
    common = [sys.executable, runner, "--jar", str(jar), "--accepted-eula-file", str(args.accepted_eula_file.resolve(strict=True)), "--startup-timeout", "180", "--shutdown-timeout", "60"]
    author = common + ["--profile", "minimal", "--extra-mod", str(qa), "--scene-input", str(paths["scene_input"]), "--scene-timeout", "150", "--run-name", args.author_run_name, "--report", str(paths["author_report"])]
    settings = [sys.executable, str(ROOT / "tools/prepare_review_v10_creative_world.py"), "--world", str(baseline), "--report", str(paths["settings_report"])]
    reopen = common + ["--profile", "minimal", "--world-copy", str(baseline), "--run-name", args.reopen_run_name, "--report", str(paths["reopen_report"])]
    full = common + ["--profile", "full_server", "--run-name", args.full_run_name, "--report", str(paths["full_report"])]
    full_name = "fullServerStartupOnly"
    if args.full_scene_copy:
        full += ["--world-copy", str(baseline)]
        full_name = "fullServerProductionSceneReopen"
    markers = [sys.executable, str(ROOT / "tools/prepare_review_v10_diagnostics_markers.py"), "--artifact-sha", args.artifact_sha, "--author-report", str(paths["author_report"]), "--output-dir", str(paths["marker_dir"])]
    diagnostics = [sys.executable, str(ROOT / "tools/run_review_v10_diagnostics_servers.py"), "--jar", str(jar), "--qa-jar", str(qa), "--artifact-sha", args.artifact_sha, "--world", str(baseline), "--baseline-report", str(paths["author_report"]), "--settings-report", str(paths["settings_report"]), "--accepted-eula-file", str(args.accepted_eula_file.resolve()), "--marker-dir", str(paths["marker_dir"]), "--suffix", args.diagnostics_suffix, "--background-concurrency-note", args.background_concurrency_note]
    phases = {"author": author, "typedBeforeFirstEntryCreative": settings, "productionOnlyReopen": reopen, full_name: full, "freshDiagnosticsMarkers": markers, "diagnosticsRequiresSeparateGO": diagnostics}
    completed_server_status = ("PASS_AUTHOR_REOPEN_FULL_SCENE_FRESH_MARKERS_DIAGNOSTICS_WAITING_SEPARATE_GO"
                               if args.full_scene_copy else
                               "PASS_AUTHOR_REOPEN_FULL_STARTUP_FRESH_MARKERS_DIAGNOSTICS_WAITING_SEPARATE_GO")
    if args.phase == "diagnostics":
        if not paths["plan_output"].is_file():
            raise ValueError("Actual author-validation plan required first")
        plan = json.loads(paths["plan_output"].read_text(encoding="utf-8"))
        if plan.get("status") != completed_server_status or plan.get("artifactSha256") != args.artifact_sha or plan.get("commands") != phases:
            raise ValueError("Prior exact current artifact/argv preparation proof mismatch")
        child(diagnostics)
        plan["status"] = "PASS_SERVER_PHASES_ONLY_CLIENT_AND_INDEPENDENT_PROOFS_PENDING"
        plan["diagnosticsPipeline"] = str(ROOT / "build" / ("review-v10-diagnostics-pipeline-" + args.diagnostics_suffix) / "pipeline.json")
        write(paths["plan_output"], plan)
        print(json.dumps({"status": plan["status"], "report": str(paths["plan_output"])}))
        return
    prior_preparation = None
    for key in paths:
        if paths[key].exists():
            if key == "plan_output" and args.phase == "author-validation":
                prior_preparation = json.loads(paths[key].read_text(encoding="utf-8"))
                if (prior_preparation.get("status") == "PREPARED_NOT_RUN"
                        and prior_preparation.get("artifactSha256") == args.artifact_sha
                        and prior_preparation.get("qaSha256") == sha(qa)
                        and prior_preparation.get("commands") == phases):
                    continue
            raise FileExistsError("Refusing historical evidence overwrite: " + str(paths[key]))
    for name in (args.author_run_name, args.reopen_run_name, args.full_run_name):
        if (ROOT / "build" / ("runtime-server-" + name)).exists():
            raise FileExistsError("Refusing prior isolated run overwrite")
    template = args.scene_template.resolve(strict=True)
    if template.stat().st_size > 65536:
        raise ValueError("Bounded fresh scene marker required")
    source = json.loads(template.read_text(encoding="utf-8-sig"))
    if source.get("schema") != "dw-review-v10-scene-input-v1" or source.get("revision") != "V10" or source.get("authoringGuard") != "FRESH_FLAT_ISOLATED_WORLD_ONLY" or source.get("allowedLevelName") != "isolated-smoke-world":
        raise ValueError("Fresh isolated V10 template schema/guard mismatch")
    if prior_preparation is not None and (prior_preparation.get("sourceTemplate") != str(template)
            or prior_preparation.get("sourceTemplateSha256") != sha(template)):
        raise ValueError("Prepared template bytes changed before actual author phase")
    source["productionJarSha256"] = args.artifact_sha
    plan = {"schema": "dw-v10-current-candidate-server-plan-v1", "status": "PREPARED_NOT_RUN", "artifactSha256": args.artifact_sha, "qaSha256": sha(qa), "baselineWorld": str(baseline), "sourceTemplate": str(template), "sourceTemplateSha256": sha(template), "generatedSceneInput": str(paths["scene_input"]), "commands": phases, "completed": [], "scope": "Fresh current-artifact author, typed Creative setup, production-only restart,83-mod startup; diagnostics OFF/ON identical baseline and REENTER from saved ON in separate explicitly requested phase. No ordinary GUI/client/source-city acceptance inferred.", "manualReview": "NOT_RUN"}
    if args.full_scene_copy:
        plan["fullServerScope"] = "QA-free full-profile fresh authored scene copy, normal save/reopen; requires separate typed owner/native/RP/control-state audit. Not an empty-world startup or integrated-client rendering proof."
    if args.phase == "prepare":
        # A plan alone records proposed input bytes; author-validation generates
        # them after fresh-path preflight. It does not supply fake author results.
        plan["proposedSceneInput"] = source
        write(paths["plan_output"], plan)
        print(json.dumps({"status": plan["status"], "plan": str(paths["plan_output"])}))
        return
    write(paths["scene_input"], source)
    plan["generatedSceneInputSha256"] = sha(paths["scene_input"])
    write(paths["plan_output"], plan)
    try:
        for name, argv, wrapper in (("author", author, paths["author_report"]), ("typedBeforeFirstEntryCreative", settings, None), ("productionOnlyReopen", reopen, paths["reopen_report"]), (full_name, full, paths["full_report"]), ("freshDiagnosticsMarkers", markers, None)):
            result = child(argv, wrapper, args.artifact_sha)
            if name == "author":
                actual = result.get("review_scene_output", {}).get("result", {})
                if not str(actual.get("status", "")).startswith("PASS_SCENE_AUTHORED"):
                    raise ValueError("Actual V10 scene authoring did not PASS")
            if name in ("productionOnlyReopen", "fullServerProductionSceneReopen") and any(row.get("extra_mod") for row in result.get("modset", [])):
                raise ValueError("Production-only reopen unexpectedly includes QA")
            if name == "fullServerProductionSceneReopen":
                copied = result.get("derived_world_copy", {})
                if (Path(copied.get("source", "")).resolve() != baseline.resolve()
                        or copied.get("copy_byte_verification") != "PASS"
                        or copied.get("source_unchanged_after_run") is not True):
                    raise ValueError("Full production scene did not copy/preserve the exact fresh author baseline")
            plan["completed"].append({"phase": name, "wrapper": str(wrapper) if wrapper else None, "wrapperSha256": sha(wrapper) if wrapper else None, "actualChildExitCode": 0})
            write(paths["plan_output"], plan)
        plan["status"] = completed_server_status
        write(paths["plan_output"], plan)
        print(json.dumps({"status": plan["status"], "baseline": str(baseline), "plan": str(paths["plan_output"])}))
    except Exception as failure:
        plan["status"] = "FAIL_ACTUAL_CANDIDATE_SERVER_PHASE"
        plan["failure"] = str(failure)
        write(paths["plan_output"], plan)
        raise


if __name__ == "__main__":
    main()
