"""Explicit V10 default60 OFF/ON/reenter on copies of one frozen current scene.

No server is launched unless this script is invoked. The author world is read-only.
Raw comparison costs may include concurrent clients and are not causal percentages.
"""
from __future__ import annotations
import argparse, hashlib, json, re, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODES = ("DISABLED", "ENABLED", "REENTER")
MAX_MARKER_BYTES = 65536

def marker_inputs(directory, artifact_sha):
    """Validate all three bounded inputs before starting any server phase."""
    directory = directory.resolve()
    if not directory.is_relative_to(ROOT.resolve()) or not directory.is_dir():
        raise ValueError("Marker directory must exist inside this project")
    result = {}
    population = None
    for mode in MODES:
        marker = directory / ("review_v10_diagnostics_" + mode.lower() + "_input.json")
        if not marker.is_file() or not marker.resolve().is_relative_to(directory) or marker.stat().st_size > MAX_MARKER_BYTES:
            raise ValueError("Missing, external or unbounded V10 marker: " + str(marker))
        data = json.loads(marker.read_text(encoding="utf-8-sig"))
        if (data.get("schemaVersion") != 1 or data.get("revision") != "V10"
                or data.get("productionArtifactSha256") != artifact_sha or data.get("mode") != mode
                or data.get("seconds") != 60 or data.get("allowedLevelName") != "isolated-smoke-world"):
            raise ValueError("V10 marker exact schema/artifact/mode/default60 guard mismatch")
        targets = data.get("targets")
        if (not isinstance(targets, list) or len(targets) != 15
                or any(not isinstance(row, dict) or row.get("kind") not in ("block", "rp")
                       or not isinstance(row.get("name"), str) or len(row["name"]) > 160
                       or not isinstance(row.get("root"), list) or len(row["root"]) != 3
                       or any(type(v) is not int or abs(v) > 30000000 for v in row["root"])
                       for row in targets)
                or len({row["name"] for row in targets}) != len(targets)):
            raise ValueError("V10 marker requires the exact bounded15 named target population")
        if population is not None and targets != population:
            raise ValueError("OFF/ON/REENTER target definitions must be identical")
        population = targets
        result[mode] = marker
    return result

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

def files(world):
    return {p.relative_to(world).as_posix(): sha(p) for p in sorted(world.rglob("*"))
            if p.is_file() and p.name != "session.lock"}

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for key in ("jar", "qa-jar", "world", "baseline-report", "settings-report", "accepted-eula-file"):
        ap.add_argument("--" + key, type=Path, required=True)
    ap.add_argument("--artifact-sha", required=True)
    ap.add_argument("--suffix", required=True)
    ap.add_argument("--background-concurrency-note", required=True)
    ap.add_argument("--marker-dir", type=Path, default=ROOT / "tools",
                    help="Project-local directory with the three V10 marker filenames; defaults to unchanged tools inputs")
    args = ap.parse_args()
    if not re.fullmatch(r"[a-zA-Z0-9_-]+", args.suffix):
        raise ValueError("Unsafe suffix")
    jar, qa, world = args.jar.resolve(), args.qa_jar.resolve(), args.world.resolve()
    digest = sha(jar)
    if digest != args.artifact_sha:
        raise ValueError("Current production artifact SHA mismatch")
    if not world.is_relative_to((ROOT / "build").resolve()) or not (world / "level.dat").is_file():
        raise ValueError("Only a saved derived world under build is allowed")
    baseline = json.loads(args.baseline_report.read_text(encoding="utf-8-sig"))
    settings = json.loads(args.settings_report.read_text(encoding="utf-8-sig"))
    if baseline.get("status") != "PASS" or baseline.get("artifact_sha256") != digest or baseline.get("exit_code") != 0:
        raise ValueError("Author baseline must PASS the exact current production artifact")
    if settings.get("status") != "PASS_SAVED_WORLD_CREATIVE_CHEATS_ACTUAL_FIRST_CLIENT_ENTRY_PENDING" or Path(settings["world"]).resolve() != world:
        raise ValueError("Explicit before-entry Creative/cheats baseline proof required")
    if settings["files"][0]["afterSha256"] != sha(world / "level.dat"):
        raise ValueError("Baseline level.dat changed after explicit settings patch")
    markers = marker_inputs(args.marker_dir, digest)
    marker_hashes = {mode: sha(path) for mode, path in markers.items()}
    work = ROOT / "build" / ("review-v10-diagnostics-pipeline-" + args.suffix)
    if work.exists():
        raise ValueError("Historical pipeline exists; choose a fresh suffix")
    work.mkdir()
    initial = files(world)
    output = work / "pipeline.json"
    report = {"schema": "dw-review-v10-diagnostics-pipeline-v1", "status": "RUNNING",
              "artifactSha256": digest, "qaSha256": sha(qa), "sourceWorld": str(world),
              "sourceWorldSnapshot": initial, "baselineReport": str(args.baseline_report.resolve()),
              "settingsReport": str(args.settings_report.resolve()), "profile": "minimal", "phases": [],
              "markerDirectory": str(args.marker_dir.resolve()),
              "markerInputs": {mode: {"path": str(path), "sha256": marker_hashes[mode]} for mode, path in markers.items()},
              "backgroundConcurrency": args.background_concurrency_note,
              "costScope": "Descriptive raw costs only; clients may run concurrently. No exact causal percentage or GPU claim.",
              "manualReview": "NOT_RUN"}
    write(output, report)
    try:
        results = {}
        for mode in MODES:
            source = world if mode != "REENTER" else Path(results["ENABLED"]["run_directory"]) / "isolated-smoke-world"
            marker = markers[mode]
            if sha(marker) != marker_hashes[mode]:
                raise ValueError("Marker bytes changed after complete preflight: " + mode)
            data = json.loads(marker.read_text(encoding="utf-8-sig"))
            if data.get("revision") != "V10" or data.get("productionArtifactSha256") != digest or data.get("mode") != mode:
                raise ValueError("V10 marker current artifact/mode mismatch")
            wrapper = ROOT / "reports" / ("DIAGNOSTICS_SERVER_" + mode + "_V10_" + args.suffix.upper() + ".json")
            if wrapper.exists():
                raise ValueError("Refusing historical report overwrite: " + str(wrapper))
            argv = [sys.executable, str(ROOT / "tools/run_final_server.py"), "--jar", str(jar),
                    "--extra-mod", str(qa), "--accepted-eula-file", str(args.accepted_eula_file.resolve()),
                    "--profile", "minimal", "--startup-timeout", "180", "--shutdown-timeout", "60",
                    "--diagnostics-timeout", "100", "--world-copy", str(source), "--diagnostics-input", str(marker),
                    "--run-name", "v10-diag-" + mode.lower() + "-" + args.suffix, "--report", str(wrapper)]
            phase = {"mode": mode, "argv": argv, "markerSha256": sha(marker), "status": "RUNNING"}
            report["phases"].append(phase)
            write(output, report)
            child = subprocess.run(argv, cwd=ROOT)
            actual = json.loads(wrapper.read_text(encoding="utf-8-sig")) if wrapper.is_file() else {}
            raw = actual.get("diagnostics_review", {}).get("result", {})
            phase.update(processExitCode=child.returncode, wrapper=str(wrapper),
                         wrapperSha256=sha(wrapper) if wrapper.is_file() else None,
                         status=actual.get("status"), rawStatus=raw.get("status"))
            write(output, report)
            if child.returncode != 0 or actual.get("status") != "PASS" or actual.get("exit_code") != 0 or actual.get("artifact_sha256") != digest or raw.get("status") != "PASS_SERVER_DIAGNOSTICS_" + mode or raw.get("checksPassed") is not True:
                raise ValueError("Actual diagnostics phase failed: " + mode)
            results[mode] = actual
        report["sourceWorldFilesByteExactUnchanged"] = initial == files(world)
        if not report["sourceWorldFilesByteExactUnchanged"]:
            raise ValueError("Frozen baseline files changed")
        report["status"] = "PASS_THREE_NORMAL_SERVER_PHASES_REQUIRES_INDEPENDENT_CLIENT_EXPORT_AND_TYPED_AUDIT"
        write(output, report)
        print(json.dumps({"status": report["status"], "report": str(output)}, ensure_ascii=False))
        return 0
    except Exception as failure:
        report["status"] = "FAIL_DIAGNOSTICS_PIPELINE"
        report["failure"] = str(failure)
        report["sourceWorldFilesByteExactUnchanged"] = initial == files(world)
        write(output, report)
        print(json.dumps({"status": report["status"], "failure": str(failure), "report": str(output)}, ensure_ascii=False))
        return 1

if __name__ == "__main__":
    raise SystemExit(main())
