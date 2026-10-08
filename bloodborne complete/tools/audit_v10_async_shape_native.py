"""Bind the async shape regression to actual XML/build/source freeze evidence."""
import argparse
import hashlib
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NAMES = {
    "compositeasyncshapegametests.workerlightingqueriesmatchroothelperforeignandmountednativewithoutchunkloads",
    "compositeasyncshapegametests.saveddeferredownersknownuuidreplacementsandrollbackpublishexactshaperevisions",
}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for key in ("manifest", "baseline-manifest", "prebuild-report", "report"):
        ap.add_argument("--" + key, type=Path, required=True)
    args = ap.parse_args()
    if args.report.exists():
        raise ValueError("Refusing historical report overwrite")
    frozen = json.loads(args.manifest.read_text(encoding="utf-8"))
    baseline = json.loads(args.baseline_manifest.read_text(encoding="utf-8"))
    before = json.loads(args.prebuild_report.read_text(encoding="utf-8"))
    xml = Path(frozen["nativeXml"])
    log = Path(frozen["buildLog"])
    if digest(xml) != frozen["nativeXmlSha256"] or digest(log) != frozen["buildLogSha256"]:
        raise ValueError("Actual XML/log changed after production freeze")
    tree = ET.parse(xml)
    cases = tree.findall(".//testcase")
    failures = tree.findall(".//failure") + tree.findall(".//error")
    selected = [dict(row.attrib) for row in cases if row.get("name") in NAMES]
    if failures or len(cases) != frozen["nativeTests"] or {row["name"] for row in selected} != NAMES:
        raise ValueError("Actual native tests or two regressions did not PASS")
    core = []
    successful = False
    for line in log.read_text(encoding="utf-8", errors="strict").splitlines():
        match = re.fullmatch(r"PASS (\d+) transaction core checks; Java (.+)", line)
        if match:
            core.append({"checks": int(match[1]), "java": match[2]})
        if line.startswith("BUILD SUCCESSFUL"):
            successful = True
    if len(core) != 1 or core[0]["checks"] != frozen["coreChecks"] or not successful:
        raise ValueError("Actual core/build success evidence missing")
    source_rows = []
    current = {row["path"]: row for row in frozen["mainSourceInputs"]}
    for row in before["files"]:
        actual = digest(ROOT / row["path"])
        if actual != row["sha256"] or row["path"] in current and current[row["path"]]["sha256"] != actual:
            raise ValueError("Reviewed source changed: " + row["path"])
        source_rows.append({"path": row["path"], "sha256": actual, "reviewedSourceExact": True})
    old = {row["path"]: row for row in baseline["mainSourceInputs"]}
    assets = [name for name in set(old) | set(current) if "/resources/" in name]
    changed_assets = [name for name in assets if old.get(name, {}).get("sha256") != current.get(name, {}).get("sha256")]
    result = {
        "schema": "dw-v10-async-shape-native-proof-v1",
        "status": "PASS_CURRENT_NATIVE_ASYNC_QUERIES_AND_ATOMIC_SNAPSHOT_PUBLICATION_REQUIRES_ORDINARY_SAVED_SCENE_RUNTIME",
        "artifactSha256": frozen["sha256"], "artifactBytes": frozen["bytes"],
        "previousArtifactSha256": baseline["sha256"],
        "freezeManifest": {"path": str(args.manifest), "sha256": digest(args.manifest)},
        "nativeXml": {"path": str(xml), "sha256": digest(xml), "tests": len(cases), "failures": len(failures)},
        "buildLog": {"path": str(log), "sha256": digest(log), "buildSuccessful": successful},
        "actualCore": core[0], "workerCases": selected, "sources": source_rows,
        "resourceManifestComparison": {"checkedPaths": len(assets), "changedPaths": changed_assets,
            "scope": "Actual frozen source-resource hashes only; independent JAR art-delta audit belongs to resources agent; baked/render runtime pending"},
        "historicalFailure": before["historicalFailureWrapper"],
        "semanticsAndLimits": before["limits"],
        "ordinaryCurrentRuntime": "NOT_RUN; new exact-artifact authored-scene/full-client loading proof required",
        "manualReview": "PENDING_USER_REVIEW", "fullTask": "NOT_READY_FULL_TASK",
    }
    args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": result["status"], "tests": len(cases), "coreChecks": core[0]["checks"], "report": str(args.report)}))


if __name__ == "__main__":
    main()
