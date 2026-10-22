"""Package one finished render-fix world and verify one exact restoration.

All mutable/output locations are explicit CLI arguments.  The caller supplies
a curated evidence directory and an evidence summary containing the gameplay
assessment; packaging never promotes a build or a successful restore to a
gameplay PASS.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import portable_static as c


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--world", type=Path, required=True)
    parser.add_argument("--release", type=Path, required=True)
    parser.add_argument("--verify", type=Path, required=True)
    parser.add_argument("--game", type=Path, required=True)
    parser.add_argument("--sources", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--evidence-summary", type=Path, required=True)
    parser.add_argument("--cleanup-report", type=Path, required=True)
    parser.add_argument("--baseline-world-sha", required=True)
    parser.add_argument("--world-archive-name", default="Dreamwalker-BB-static-city-render-fix-world.zip")
    return parser.parse_args()


def is_reparse(path: Path) -> bool:
    return bool(getattr(path.lstat(), "st_file_attributes", 0) & 1024)


def main() -> None:
    args = parse_args()
    c.need(re.fullmatch(r"[a-f0-9]{64}", args.baseline_world_sha) is not None,
           "--baseline-world-sha must be lowercase SHA256")
    c.need(re.fullmatch(r"[A-Za-z0-9._-]{1,128}\.zip", args.world_archive_name) is not None,
           "Unsafe world archive name")

    world = c.ordinary(args.world)
    game = c.ordinary(args.game)
    sources = c.ordinary(args.sources)
    evidence_dir = c.ordinary(args.evidence_dir)
    evidence_summary = c.ordinary(args.evidence_summary)
    cleanup_report = c.ordinary(args.cleanup_report)
    c.need(world.is_dir() and evidence_dir.is_dir(), "World and evidence inputs must be directories")
    c.need(game.is_file() and sources.is_file() and evidence_summary.is_file() and cleanup_report.is_file(),
           "Game, sources and reports must be ordinary files")

    release = args.release.absolute()
    verify = args.verify.absolute()
    c.need(not release.exists() and not verify.exists(), "Release and verification destinations must not exist")
    release_parent = c.ordinary(release.parent)
    verify_parent = c.ordinary(verify.parent)
    c.need(release != verify and release_parent != release and verify_parent != verify,
           "Invalid output destinations")

    summary = c.read(evidence_summary)
    c.need(isinstance(summary, dict) and isinstance(summary.get("gameplayAssessment"), dict),
           "Evidence summary must contain gameplayAssessment")
    assessment = summary["gameplayAssessment"]
    c.need(isinstance(assessment.get("status"), str)
           and isinstance(assessment.get("gameplayVerified"), bool),
           "Gameplay assessment needs explicit status and gameplayVerified")

    # The evidence directory is deliberately curated and shallow.  Refuse a
    # recursive tree so build/runtime profiles, tokens and historical reports
    # cannot be picked up accidentally.
    evidence_inputs = sorted(evidence_dir.iterdir(), key=lambda p: p.name)
    c.need(len(evidence_inputs) <= 64 and all(p.is_file() and not is_reparse(p) for p in evidence_inputs),
           "Evidence directory must contain at most 64 top-level ordinary files")
    explicit_evidence = [*evidence_inputs, evidence_summary, cleanup_report]
    by_name: dict[str, Path] = {}
    for source in explicit_evidence:
        source = c.ordinary(source)
        prior = by_name.get(source.name)
        c.need(prior is None or prior.resolve() == source.resolve(),
               f"Evidence filename collision: {source.name}")
        by_name[source.name] = source

    release.mkdir()
    artifacts: list[dict[str, object]] = []

    def record(path: Path, role: str) -> dict[str, object]:
        row = {"file": path.relative_to(release).as_posix(), "bytes": path.stat().st_size,
               "sha256": c.sha(path), "role": role}
        artifacts.append(row)
        return row

    def copy(source: Path, relative: Path, role: str) -> dict[str, object]:
        target = release / relative
        c.need(not target.exists(), f"Duplicate package target: {relative}")
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        return record(target, role)

    archive = release / args.world_archive_name
    world_entries: list[dict[str, object]] = []
    # Windows byte-range locking can reject a second handle's read even in
    # this process. Preserve the native lock file before holding its first byte,
    # as in the original one-pass packager.
    lock_bytes = (world / "session.lock").read_bytes()
    with c.stopped_world(world):
        with zipfile.ZipFile(archive, "x", compression=zipfile.ZIP_DEFLATED,
                             compresslevel=6, allowZip64=True) as output:
            for parent_raw, dirs, files in os.walk(world):
                parent = Path(parent_raw)
                if parent == world:
                    dirs[:] = [name for name in dirs if name != ".dw-static-runtime"]
                for name in dirs:
                    c.need(not is_reparse(parent / name), f"World contains a reparse directory: {parent / name}")
                dirs.sort()
                for name in sorted(files):
                    path = parent / name
                    c.need(not is_reparse(path), f"World contains a reparse file: {path}")
                    relative = path.relative_to(world).as_posix()
                    digest = hashlib.sha256()
                    size = 0
                    info = zipfile.ZipInfo.from_file(path, "world/" + relative)
                    info.compress_type = zipfile.ZIP_DEFLATED
                    with output.open(info, "w", force_zip64=True) as target:
                        if relative == "session.lock":
                            target.write(lock_bytes)
                            digest.update(lock_bytes)
                            size = len(lock_bytes)
                        else:
                            with path.open("rb") as source:
                                for block in iter(lambda: source.read(4 * 1024 * 1024), b""):
                                    target.write(block)
                                    digest.update(block)
                                    size += len(block)
                    world_entries.append({"path": relative, "bytes": size,
                                          "sha256": digest.hexdigest()})

    archive_row = record(archive, "render-fix world; matching game JAR required")
    world_manifest = release / "WORLD-MANIFEST.json"
    c.atomic_json(world_manifest, {
        "schemaVersion": 1,
        "files": world_entries,
        "fileCount": len(world_entries),
        "logicalBytes": sum(int(row["bytes"]) for row in world_entries),
        "excludedFolders": [
            "world/.dw-static-runtime (migration server binaries; not Minecraft world data)"
        ],
    })
    record(world_manifest, "world restoration file manifest")

    # Exactly one completeness/SHA256/restoration pass for the world archive.
    verify.mkdir()
    expected = {"world/" + str(row["path"]): row for row in world_entries}
    with zipfile.ZipFile(archive) as source:
        c.need(set(source.namelist()) == set(expected) and len(source.infolist()) == len(expected),
               "Archive completeness differs from world manifest")
        for entry in source.infolist():
            parts = Path(entry.filename).parts
            c.need(not Path(entry.filename).is_absolute() and ".." not in parts and parts[0] == "world",
                   f"Unsafe archive path: {entry.filename}")
            target = verify.joinpath(*parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            digest = hashlib.sha256()
            size = 0
            with source.open(entry) as incoming, target.open("xb") as outgoing:
                for block in iter(lambda: incoming.read(4 * 1024 * 1024), b""):
                    outgoing.write(block)
                    digest.update(block)
                    size += len(block)
            row = expected[entry.filename]
            c.need(size == row["bytes"] and digest.hexdigest() == row["sha256"],
                   "Restored world data mismatch: " + entry.filename)

    # Remove only the exact verification directory created by this invocation,
    # and only after every restored entry matched.
    resolved_verify = verify.resolve()
    c.need(resolved_verify == verify and resolved_verify.parent == verify_parent.resolve()
           and resolved_verify.name == verify.name,
           "Temporary cleanup path escapes the explicit verification destination")
    c.need(not is_reparse(resolved_verify), "Verification directory became a reparse point")
    shutil.rmtree(resolved_verify)
    c.need(not verify.exists(), "Verified temporary restoration was not removed")

    copy(game, Path("game") / game.name, "compatible production game JAR")
    copy(sources, Path("sources") / sources.name, "production sources")
    evidence_rows: dict[str, dict[str, object]] = {}
    for name, source in sorted(by_name.items()):
        evidence_rows[name] = copy(source, Path("evidence") / name, "curated existing evidence")

    receipt_path = release / "PACKAGE-RESTORE-RECEIPT.json"
    c.atomic_json(receipt_path, {
        "schemaVersion": 1,
        "status": "PASS_ONE_COMPLETENESS_SHA256_AND_RESTORATION",
        "verifiedUtc": datetime.now(timezone.utc).isoformat(),
        "archive": archive_row,
        "restoredFiles": len(world_entries),
        "restoredBytes": sum(int(row["bytes"]) for row in world_entries),
        "restorationPasses": 1,
        "nativeWorldSessionLockHeldDuringPackaging": True,
        "minecraftLaunched": False,
        "gameplayVerified": assessment["gameplayVerified"],
        "temporaryRestorationRemovedAfterSuccess": True,
    })
    record(receipt_path, "single package completeness/SHA256/restoration receipt")

    manifest_path = release / "MANIFEST.json"
    c.atomic_json(manifest_path, {
        "schemaVersion": 1,
        "status": "PACKAGED_RENDER_FIX_RESULT",
        "createdUtc": datetime.now(timezone.utc).isoformat(),
        "baselineWorldSha256": args.baseline_world_sha,
        "worldArchive": archive_row,
        "gameSha256": next(row["sha256"] for row in artifacts if row["file"] == "game/" + game.name),
        "sourcesSha256": next(row["sha256"] for row in artifacts if row["file"] == "sources/" + sources.name),
        "evidenceSummary": evidence_rows[evidence_summary.name],
        "cleanupReport": evidence_rows[cleanup_report.name],
        "gameplayAssessment": assessment,
        "artifacts": artifacts,
    })
    sums_path = release / "SHA256SUMS.txt"
    sums_path.write_text("".join(str(row["sha256"]) + "  " + str(row["file"]) + "\n"
                                  for row in artifacts)
                          + c.sha(manifest_path) + "  MANIFEST.json\n", encoding="utf-8")
    print(json.dumps({
        "status": "PACKAGED_AND_RESTORED_ONCE",
        "release": str(release),
        "worldFiles": len(world_entries),
        "worldLogicalBytes": sum(int(row["bytes"]) for row in world_entries),
        "worldZipBytes": archive.stat().st_size,
        "worldZipSha256": archive_row["sha256"],
        "gameplayAssessment": assessment,
        "temporaryRestoreRemoved": not verify.exists(),
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
