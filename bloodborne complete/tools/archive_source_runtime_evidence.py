"""Copy stopped source-run primary evidence into the distributable reports tree."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--artifact", type=Path, required=True)
    args = parser.parse_args()
    assert re.fullmatch(r"v[1-9][0-9]*", args.revision)
    suffix = args.revision.upper()
    artifact_sha = digest(args.artifact)
    destination = ROOT / "reports/runtime"
    destination.mkdir(parents=True, exist_ok=True)
    copies = []
    wrappers = []
    for phase in ("AUTHOR", "REOPEN", "REPLAY"):
        wrapper = ROOT / f"reports/SERVER_SOURCE_REVIEW_{phase}_{suffix}.json"
        report = json.loads(wrapper.read_text(encoding="utf8"))
        assert report["status"] == "PASS" and report["exit_code"] == 0
        assert report["artifact_sha256"] == artifact_sha
        wrappers.append({"path": str(wrapper.relative_to(ROOT)), "sha256": digest(wrapper)})
        sources = [(Path(report["evidence"]), report["console_sha256"], "launch-console.log")]
        if report.get("bounded_source_output"):
            output = report["bounded_source_output"]
            assert json.loads(Path(output["path"]).read_text(encoding="utf8")) == output["result"]
            sources.append((Path(output["path"]), output["sha256"], "source-review-output.json"))
        for source, expected_sha, ending in sources:
            assert digest(source) == expected_sha
            target = destination / (wrapper.stem + "." + ending)
            data = source.read_bytes()
            if target.exists():
                assert target.read_bytes() == data, "Refusing to replace differing archived evidence"
            else:
                target.write_bytes(data)
            assert target.read_bytes() == data and digest(target) == expected_sha
            copies.append({"wrapper": str(wrapper.relative_to(ROOT)), "source": str(source.resolve()),
                           "archived_copy": str(target.relative_to(ROOT)), "bytes": len(data),
                           "sha256": expected_sha, "byte_exact": True})
    assert len(copies) == 5
    manifest = {"schema": "dreamwalker-source-primary-evidence-archive-v1",
                "status": "PASS_FIVE_SOURCE_PRIMARY_EVIDENCE_FILES_BYTE_EXACT",
                "revision": args.revision, "artifact_sha256": artifact_sha,
                "wrappers": wrappers, "files": copies,
                "source_run_files_changed": False}
    path = ROOT / f"reports/SOURCE_RUNTIME_PRIMARY_EVIDENCE_{suffix}.json"
    path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"status": manifest["status"], "files": len(copies), "manifest": str(path)}))


if __name__ == "__main__":
    main()
