"""Freeze a newly built V9 candidate and its actual native/core evidence."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--attempt", type=int, required=True)
    parser.add_argument("--build-log", type=Path, required=True)
    args = parser.parse_args()
    target = ROOT / "build/frozen-artifacts" / f"v9-attempt-{args.attempt}"
    xml_target = ROOT / "reports" / f"FIRST_SET_GAMETEST_V9_ATTEMPT_{args.attempt}.xml"
    if target.exists() or xml_target.exists():
        raise ValueError("Never overwrite an earlier frozen candidate or XML")
    log = args.build_log.resolve()
    text = log.read_text(encoding="utf8", errors="replace")
    core = re.findall(r"PASS (\d+) transaction core checks", text)
    if "BUILD SUCCESSFUL" not in text or len(core) != 1:
        raise ValueError("Current actual successful build/core log required")
    xml = ROOT / "build/test-results/gametest/TEST-first-set.xml"
    suite = ET.parse(xml).getroot()
    cases = suite.findall(".//testcase")
    if not cases or suite.findall(".//failure") or suite.findall(".//error"):
        raise ValueError("Current actual native GameTests must pass")
    if len({case.attrib.get("name") for case in cases}) != len(cases):
        raise ValueError("Duplicate native test names")
    target.mkdir(parents=True)
    shutil.copyfile(xml, xml_target)
    names = ["dreamwalker-bb-fabric-1.20.1-0.1.0-prototype.4.jar",
             "dreamwalker-bb-fabric-1.20.1-0.1.0-prototype.4-sources.jar",
             "dreamwalker-first-set-review-qa-0.1.0-prototype.4.jar"]
    files = []
    for name in names:
        source = ROOT / "build/libs" / name
        destination = target / name
        shutil.copyfile(source, destination)
        data = destination.read_bytes()
        if data != source.read_bytes():
            raise ValueError("Frozen bytes differ")
        files.append({"name": name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()})
    result = {"schema": "dreamwalker-frozen-artifacts-v1", "attempt": args.attempt,
              "status": "BUILD_PASS_REQUIRES_ACTUAL_CURRENT_RUNTIME", "native_tests": len(cases), "native_failures": 0,
              "core_checks": int(core[0]), "files": files, "native_xml": xml_target.relative_to(ROOT).as_posix(),
              "native_log": log.relative_to(ROOT).as_posix(), "build_log": log.relative_to(ROOT).as_posix(),
              "sourcesJarScope": "Build checkpoint only; final full-project source ZIP is created after final docs/evidence freeze"}
    (target / "manifest.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
