"""Prepare fresh V10 diagnostics markers from an actual successful current author.

This tool never starts Minecraft or changes a world, template or historical marker.
The output directory must not exist. All three filenames remain compatible with
run_review_v10_diagnostics_servers.py --marker-dir OUTPUT.
"""
from __future__ import annotations
import argparse
import copy
import hashlib
import json
import math
import re
import uuid
from pathlib import Path
from run_review_v10_diagnostics_servers import ROOT, MODES, marker_inputs


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def documents(author, artifact_sha, templates):
    """Pure bounded mapping from authoritative fixture/sample rows."""
    require(re.fullmatch(r"[0-9a-f]{64}", artifact_sha) is not None, "Exact SHA256 required")
    require(author.get("productionJarSha256") == artifact_sha
            and author.get("status") == "PASS_SCENE_AUTHORED_REQUIRES_PRODUCTION_REOPEN",
            "Actual successful author must bind the current artifact")
    rows = author.get("fixtures", [])
    require(isinstance(rows, list) and 15 <= len(rows) <= 256, "Bounded actual fixture rows required")
    fixtures = {row["key"]: row for row in rows}
    require(len(fixtures) == len(rows), "Duplicate actual fixture key")
    samples = author.get("architectureSamples", [])
    require(isinstance(samples, list) and len(samples) <= 64, "Bounded actual architecture samples required")
    by_uuid = {row["uuid"]: row for row in samples}
    require(len(by_uuid) == len(samples), "Duplicate actual sample UUID")
    output, population = {}, None
    for mode in MODES:
        document = copy.deepcopy(templates[mode])
        require(document.get("mode") == mode and document.get("revision") == "V10"
                and document.get("schemaVersion") == 1 and document.get("seconds") == 60,
                "Source template mode/revision/default60 differs")
        targets = document.get("targets")
        require(isinstance(targets, list) and len(targets) == 15, "Original bounded15 target template required")
        for target in targets:
            key = target.get("fixtureKey")
            require(key in fixtures, "Actual author lacks required fixture: " + str(key))
            fixture = fixtures[key]
            root = fixture.get("root")
            require(isinstance(root, list) and len(root) == 3
                    and all(type(v) is int and abs(v) <= 30000000 for v in root), "Invalid actual root: " + key)
            identity = fixture.get("uuid", "")
            require(isinstance(identity, str) and str(uuid.UUID(identity)) == identity,
                    "Actual canonical fixture UUID missing: " + key)
            target["root"] = list(root)
            if target["kind"] == "rp":
                require(fixture.get("kind") == "RP", "RP target became another kind: " + key)
                target["asset"] = fixture["registry"]
            else:
                require(target["kind"] == "block" and fixture.get("kind") == "ARCHITECTURE",
                        "Architecture target became another kind: " + key)
                target["expectedRegistry"] = fixture["registryId"]
                if key.startswith("sample_"):
                    sample = by_uuid.get(fixture["uuid"])
                    require(sample is not None and sample.get("root") == root
                            and sample.get("registry") == fixture["registryId"],
                            "Actual authored sample UUID/root/registry differs: " + key)
                    offset = sample.get("expectedOffset")
                    require(type(offset) in (int, float) and math.isfinite(offset) and abs(offset) <= 32,
                            "Actual sample offset is not finite/bounded: " + key)
                    target["expectedMountOffset"] = offset
        require(population is None or targets == population, "Source modes request different target populations")
        population = targets
        document["productionArtifactSha256"] = artifact_sha
        document["fixturePrecondition"] = (
            "All15 targets must already exist in this exact current-JAR successful author. "
            "Roots, registries and sample offsets are copied from its actual fixture UUID bindings. "
            "Preparation is not runtime/GUI/diagnostic PASS.")
        output[mode] = document
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact-sha", required=True)
    parser.add_argument("--author-report", type=Path, required=True)
    parser.add_argument("--source-marker-dir", type=Path, default=ROOT / "tools")
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    report_path, output = args.author_report.resolve(), args.output_dir.resolve()
    require(report_path.is_relative_to(ROOT.resolve()) and output.is_relative_to(ROOT.resolve()),
            "Only explicit project-local report/output paths allowed")
    require(not output.exists(), "Refusing existing output directory/historical marker overwrite")
    wrapper = read(report_path)
    require(wrapper.get("status") == "PASS" and wrapper.get("exit_code") == 0
            and wrapper.get("artifact_sha256") == args.artifact_sha and "termination" not in wrapper,
            "Actual normally stopped current author wrapper required")
    raw_row = wrapper.get("review_scene_output", {})
    raw = Path(raw_row.get("path", "")).resolve()
    run = Path(wrapper.get("run_directory", "")).resolve()
    require(run.is_relative_to((ROOT / "build").resolve()) and raw.is_relative_to(run)
            and raw.is_file() and raw.stat().st_size <= 1048576,
            "Actual bounded author output must be inside its isolated run")
    require(raw_row.get("sha256") == sha(raw), "Author output bytes differ from wrapper")
    actual = read(raw)
    require(raw_row.get("result") == actual, "Author wrapper result differs from actual raw output")
    source_dir = args.source_marker_dir.resolve()
    require(source_dir.is_relative_to(ROOT.resolve()) and source_dir.is_dir(), "Project-local source templates required")
    templates, references = {}, []
    for mode in MODES:
        path = source_dir / ("review_v10_diagnostics_" + mode.lower() + "_input.json")
        require(path.is_file() and path.resolve().is_relative_to(source_dir) and path.stat().st_size <= 65536,
                "Missing/external/unbounded source template")
        templates[mode] = read(path)
        references.append({"mode": mode, "path": str(path), "sha256": sha(path)})
    result = documents(actual, args.artifact_sha, templates)
    # Validate the complete in-memory population before creating any output.
    serialized = {mode: (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
                  for mode, value in result.items()}
    for content in serialized.values():
        require(len(content) <= 65536, "Prepared marker exceeds64KiB")
    output.mkdir(parents=True)
    written = []
    for mode, value in result.items():
        path = output / ("review_v10_diagnostics_" + mode.lower() + "_input.json")
        path.write_bytes(serialized[mode])
        written.append({"mode": mode, "path": str(path), "sha256": sha(path)})
    marker_inputs(output, args.artifact_sha)
    manifest = {"schema": "dw-v10-fresh-diagnostics-markers-v1", "status": "PREPARED_NOT_RUN",
                "artifactSha256": args.artifact_sha, "authorReport": {"path": str(report_path), "sha256": sha(report_path)},
                "authorRaw": {"path": str(raw), "sha256": sha(raw)}, "templates": references,
                "files": written, "targetCount": 15, "historicalInputsUnchanged": True,
                "scope": "Pure current author fixture/sample bindings; no server launched or world changed"}
    (output / "preparation.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": manifest["status"], "markerDirectory": str(output), "targetCount": 15}))


if __name__ == "__main__":
    main()
