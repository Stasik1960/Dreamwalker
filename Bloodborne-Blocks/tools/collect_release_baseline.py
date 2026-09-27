"""Read-only source/artifact census. Writes evidence only, never opens a world in Minecraft."""
from __future__ import annotations

import argparse
import datetime
import hashlib
import json
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent


def sha(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")


def git(*args, cwd=REPO):
    return subprocess.check_output(["git", *args], cwd=cwd).decode("utf-8").strip()


def entry(path):
    return {"path": path.relative_to(REPO).as_posix(), "sha256": sha(path)}


def tests(log, xml):
    text = log.read_text(encoding="utf-8", errors="replace")
    rows = [int(value) for value in re.findall(r"Ran (\d+) tests? in", text)]
    cases = list(ET.parse(xml).getroot().iter("testcase"))
    failures = re.findall(r"FAILED \(([^\n]+)\)", text)
    return {"unittestTests": sum(rows), "unittestRuns": len(rows), "unittestFailureSummaries": failures,
            "gameTestCases": len(cases),
            "gameTestFailures": sum(bool(list(case.iter("failure")) or list(case.iter("error"))) for case in cases),
            "gameTestLogCounts": [int(n) for n in re.findall(r"All (\d+) required tests passed", text)],
            "log": entry(log), "xml": entry(xml)}


def artifact(path):
    data = {"path": str(path), "bytes": path.stat().st_size, "sha256": sha(path)}
    with path.open("rb") as stream:
        pointer = stream.read(150)
    data["hydrated"] = not pointer.startswith(b"version https://git-lfs.github.com/spec/v1")
    if not data["hydrated"]:
        data["lfsTargetSha256"] = re.search(rb"oid sha256:([0-9a-f]+)", pointer).group(1).decode()
    elif path.suffix == ".jar":
        with zipfile.ZipFile(path) as jar:
            data["fabricVersion"] = json.loads(jar.read("fabric.mod.json"))["version"]
    return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repair-checkout", type=Path, required=True)
    parser.add_argument("--junit", type=Path, default=ROOT / "build/test-results/gametest/TEST-logical-gametest.xml")
    args = parser.parse_args()
    out = ROOT / "docs/release/evidence"
    out.mkdir(parents=True, exist_ok=True)
    commit = git("rev-parse", "HEAD")
    source_paths = set()
    for directory in ("src/main/java", "src/test", "src/gametest", "tools", "gradle"):
        source_paths.update(path for path in (ROOT / directory).rglob("*")
                            if path.is_file() and "__pycache__" not in path.parts)
    source_paths.update(ROOT / name for name in ("build.gradle", "settings.gradle", "gradle.properties", "gradlew", "gradlew.bat", "VERSION"))
    source_paths.add(REPO / ".github/workflows/bloodborne-blocks.yml")
    write(out / "source-snapshot.json", {"sourceCommit": commit, "scope": "working tree code/build/tests/tools; docs excluded to avoid self-reference",
          "files": [entry(path) for path in sorted(source_paths) if path.is_file()]})
    resources = sorted(path for path in (ROOT / "src/main/resources").rglob("*") if path.is_file())
    write(out / "resource-manifest.json", {"sourceCommit": commit, "files": [entry(path) for path in resources]})
    copied = out / "main-checks"
    copied.mkdir(exist_ok=True)
    for name in ("check-build-gametest.log", "check-build-gametest.json", "run_checks.py", "release-checks.log", "release-checks.json"):
        shutil.copyfile(ROOT / "build/release-audit" / name, copied / name)
    shutil.copyfile(args.junit, copied / "TEST-logical-gametest.xml")
    write(out / "main-tests.json", tests(copied / "check-build-gametest.log", copied / "TEST-logical-gametest.xml"))
    definitions = [json.loads((ROOT / f"src/main/resources/bloodborne_blocks/{kind}/definitions.json").read_bytes()) for kind in ("logical", "city")]
    block_counts = [len(document["blocks"]) for document in definitions]
    states = [sum(len(row["states"]) for row in document["blocks"]) for document in definitions]
    jars = sorted((ROOT / "build/libs").glob("*.jar"))
    write(out / "baseline-measurements.json", {
        "sourceCommit": commit, "runtimeChangesInThisTask": 0,
        "logicalBlocks": block_counts[0], "cityBlocks": block_counts[1], "helperBlocks": 1,
        "registeredBlocks": sum(block_counts) + 1, "registeredItems": sum(block_counts),
        "logicalStates": states[0], "cityStates": states[1], "helperStates": 1,
        "registeredStatesIncludingHelper": sum(states) + 1, "runtimeResourceFiles": len(resources),
        "jars": [artifact(path) for path in jars],
        "beforeAfter": "Identical runtime source/resources; no optimization or ID removal performed.",
        "startupSeconds": None, "peakRamBytes": None, "resourceReloadSeconds": None, "clientFps": None, "serverTps": None,
        "missingMeasurementsReason": "Production runtime/full-city QA blocked before migration and compatibility acceptance; GameTest timings are not production performance."})
    published = REPO / "releases/Bloodborne-Blocks/2.1.0-beta.3-grid-physics"
    write(out / "published-artifacts.json", [artifact(path) for path in sorted(published.iterdir()) if path.suffix in (".jar", ".zip")])
    report_zip = published / "Grid-Physics-Reports.zip"
    with zipfile.ZipFile(report_zip) as archive:
        write(out / "published-report-index.json", [{"path": info.filename, "bytes": info.file_size,
              "sha256": hashlib.sha256(archive.read(info)).hexdigest()} for info in archive.infolist() if not info.is_dir()])
        for info in archive.infolist():
            name = info.filename
            if info.file_size < 100000 and name.endswith((".log", ".xml", ".json")):
                destination = out / "published-beta3" / Path(name).name
                destination.parent.mkdir(exist_ok=True)
                destination.write_bytes(archive.read(name))
    write(out / "published-tests.json", {
        "buildAndGameTests": tests(out / "published-beta3/grid-final-build.log", out / "published-beta3/GameTests.xml"),
        "targetedPython": tests(out / "published-beta3/grid-final-python-targeted.log", out / "published-beta3/GameTests.xml"),
        "fullDiscovery": tests(out / "published-beta3/grid-python-all.log", out / "published-beta3/GameTests.xml"),
        "scope": "Historical beta3; separate runs, do not add their counts or infer graphical/restart acceptance."})
    local_jar = ROOT / "build/libs/bloodborne-blocks-2.1.0-beta.3.jar"
    with zipfile.ZipFile(published / "bloodborne-blocks-2.1.0-beta.3-mc1.20.1.jar") as old, zipfile.ZipFile(local_jar) as new:
        old_names = {name for name in old.namelist() if not name.endswith("/")}
        new_names = {name for name in new.namelist() if not name.endswith("/")}
        newline, semantic_json, other = [], [], []
        for name in sorted(old_names & new_names):
            a, b = old.read(name), new.read(name)
            if a == b:
                continue
            if a.replace(b"\r\n", b"\n") == b.replace(b"\r\n", b"\n"):
                newline.append(name)
            elif name.endswith(".json") and json.loads(a) == json.loads(b):
                semantic_json.append(name)
            else:
                other.append(name)
        write(out / "published-local-jar-comparison.json", {"published": artifact(published / "bloodborne-blocks-2.1.0-beta.3-mc1.20.1.jar"),
              "local": artifact(local_jar), "added": sorted(new_names - old_names), "removed": sorted(old_names - new_names),
              "lineEndingOnlyDifferences": newline, "jsonFormattingOnlyDifferences": semantic_json, "otherDifferences": other})
    old_root = args.repair_checkout.resolve()
    old_project = old_root / "Bloodborne-Blocks"
    histories = {}
    for name, folder, log_name in (("repair-test3", "releases/repair-test-3", "check-build-gametest.log"),
                                   ("repair-catalog-local", "docs/catalog-city-continuation/final", "check-build.log")):
        dest = out / name
        dest.mkdir(exist_ok=True)
        for filename in (log_name, "TEST-logical-gametest.xml", "verification.json", "check-build.json", "metadata-assemble.json", "metadata-assemble.log", "second-byte-comparison.json"):
            source = old_project / folder / filename
            if source.is_file():
                shutil.copyfile(source, dest / filename)
        histories[name] = tests(dest / log_name, dest / "TEST-logical-gametest.xml")
    write(out / "historical-test-counts.json", histories)
    old_paths = git("ls-files", "-m", "--others", "--exclude-standard", "Bloodborne-Blocks/src", "Bloodborne-Blocks/tools", "Bloodborne-Blocks/build.gradle", cwd=old_root).splitlines()
    write(out / "repair-wip-snapshot.json", {"sourceCommit": git("rev-parse", "HEAD", cwd=old_root),
          "collectedUtc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
          "scope": "Hash of modified/untracked code at AUDIT time; not an original build-time attestation. Existing artifacts retain their own SHA.",
          "files": [{"path": name, "sha256": sha(old_root / name)} for name in sorted(set(old_paths)) if (old_root / name).is_file()]})
    write(out / "repair-artifacts.json", [artifact(path) for folder in ("releases/repair-test-3", "releases/repair-catalog-city-1")
          for path in sorted((old_project / folder).iterdir()) if path.suffix in (".jar", ".zip")])
    refs = ["origin/main", "origin/archive/beta3-grid-fragmentation-broken", "origin/repair/composite-preserving-grid", "origin/codex/bloodborne-beta-world-20260925", "bloodborne-blocks-v2.1.0-beta.1"]
    branches = []
    for ref in refs:
        content = git("show", ref + ":Bloodborne-Blocks/build.gradle")
        branches.append({"ref": ref, "commit": git("rev-parse", ref), "version": re.search(r"version\s*=\s*'([^']+)'", content).group(1)})
    write(out / "branches.json", branches)
    print(json.dumps({"source": entry(out / "source-snapshot.json"), "resources": entry(out / "resource-manifest.json"),
          "input": entry(ROOT / "reference-inputs/latest-modded-world.zip"), "output": str(out)}, indent=2))


if __name__ == "__main__":
    main()
