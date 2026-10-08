"""Independently inspect a stopped QA-free V10 pristine scene restart.

Reuse the typed saved-world audit without creating an archive or requiring
future client results. This never launches a process or modifies a world.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from archive_review_v10_scene import verify_saved, raw_output
from package_review_v10 import ROOT, digest, exact_artifact, read, require, resolve


def verify(author_report: Path, reopen_report: Path, artifact_sha: str) -> dict:
    author = read(author_report)
    reopen = read(reopen_report)
    for label, wrapper in (("fresh author", author), ("production-only reopen", reopen)):
        exact_artifact(wrapper, artifact_sha, label)
        require(wrapper.get("status") == "PASS" and wrapper.get("exit_code") == 0
                and not wrapper.get("termination"), label + " must save and stop normally")
    raw = raw_output(author, "review_scene_output")
    exact_artifact(raw, artifact_sha, "author raw output")
    require(raw.get("status") == "PASS_SCENE_AUTHORED_REQUIRES_PRODUCTION_REOPEN", "Fresh author incomplete")
    require(not any(row.get("extra_mod") or row.get("id") == "bloodborne_dw_review"
                    for row in reopen.get("modset", [])), "Reopen contains a QA add-on")
    copied = reopen.get("derived_world_copy", {})
    source = resolve(copied.get("source", ""), ROOT)
    require(source == resolve(author["run_directory"], ROOT) / "isolated-smoke-world", "Reopen source is not this fresh author")
    require(copied.get("copy_byte_verification") == "PASS"
            and copied.get("source_unchanged_after_run") is True, "Fresh source copy was not byte verified/unchanged")
    world = resolve(reopen["run_directory"], ROOT) / "isolated-smoke-world"
    require(source.is_relative_to(ROOT / "build") and world.is_relative_to(ROOT / "build"), "Only derived build worlds may be inspected")
    for row in copied["files"]:
        path = source / row["path"]
        require(path.stat().st_size == row["bytes"] and digest(path) == row["sha256"], "Source bytes changed after copy: " + row["path"])
    saved = verify_saved(world, source, raw, None, fresh_baseline=True)
    return {"schema": "dw-v10-pristine-production-reopen-independent-v1",
            "status": "PASS_CURRENT_TYPED_PRISTINE_SCENE_REOPEN_PENDING_CLIENT_AND_USER_REVIEW",
            "productionJarSha256": artifact_sha, "profile": reopen["profile"],
            "actualChildExitCode": 0, "qaExcluded": True,
            "sourceWorld": str(source), "world": str(world), "saved": saved,
            "inputs": [{"path": str(path.resolve()), "sha256": digest(path)}
                       for path in (author_report, reopen_report)],
            "scope": "All inventoried native roots/full typed BEs and owner ledger equal; explicit RP stable identity/pose/control fields and pristine control state equal. Generated chunks, metadata, ordinary vanilla timers and full transient RP NBT are not claimed byte-identical.",
            "clientRenderingOrWorkerRaceReproduction": "NOT_PROVED_BY_THIS_SAVED_WORLD_AUDIT",
            "manualReview": "NOT_RUN"}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--author-report", type=Path, required=True)
    parser.add_argument("--reopen-report", type=Path, required=True)
    parser.add_argument("--artifact-sha", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    require(not args.output.exists(), "Refuse historical evidence overwrite")
    value = verify(args.author_report, args.reopen_report, args.artifact_sha)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"status": value["status"], "path": str(args.output), "profile": value["profile"]}))


if __name__ == "__main__":
    main()
