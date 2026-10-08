"""Archive the exact production-reopened bounded source copy after matching evidence gates."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REPORTS = ROOT / "reports"
PREFIX = "First-set-migrated-source-fixture/"


def digest(path):
    hasher = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for data in iter(lambda: stream.read(1024*1024), b""):
            hasher.update(data)
    return hasher.hexdigest()


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def inventory(world):
    return {p.relative_to(world).as_posix(): {"bytes": p.stat().st_size, "sha256": digest(p)}
            for p in sorted(world.rglob("*")) if p.is_file()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--revision", required=True, help="Evidence suffix, for example v5")
    parser.add_argument("--artifact", type=Path, default=ROOT / "build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-prototype.2.jar")
    parser.add_argument("--artifact-sha", help="Optional explicit frozen artifact SHA; supplied for V8 commands")
    parser.add_argument("--world", type=Path)
    parser.add_argument("--input", type=Path, default=ROOT / "build/source-review-prepared-v6/source-review-input.json")
    parser.add_argument("--preload-report", type=Path, default=REPORTS / "SOURCE_REVIEW_PRELOAD_INDEPENDENT_V6.json")
    parser.add_argument("--archive", type=Path, default=ROOT / "build/prototype/First-set-migrated-source-fixture.zip")
    parser.add_argument("--report", type=Path)
    parser.add_argument("--migration-plan", type=Path, default=REPORTS / "FIRST_SET_MIGRATION_PLAN.json")
    parser.add_argument("--original-fixture", type=Path, default=ROOT / "build/prototype/Source-coordinate-fixture.zip")
    for name in ("author-report", "reopen-report", "replay-report", "postsave-report", "postsave-reopen-report", "second-save-report", "persisted-replay-report"):
        parser.add_argument("--" + name, type=Path)
    args = parser.parse_args()
    assert re.fullmatch(r"v[1-9][0-9]*", args.revision)
    suffix = args.revision.upper()
    world = (args.world or ROOT / f"build/runtime-server-first-set-source-reopen-{args.revision}/isolated-smoke-world").resolve()
    output = args.report or REPORTS / f"FIRST_SET_SOURCE_REVIEW_SCENE_{suffix}.json"
    artifact_sha = digest(args.artifact)
    assert args.artifact_sha is None or artifact_sha == args.artifact_sha, "Artifact differs from explicit frozen SHA"
    input_sha = digest(args.input)
    manifest = read(args.input)
    assert len(manifest["objects"]) == 41 and manifest["sourceMemberCount"] == 110
    assert len({tuple(m["pos"]) for row in manifest["objects"] for m in row["members"]}) == 110
    gates = []
    wrappers = {}
    for phase in ("AUTHOR", "REOPEN", "REPLAY"):
        path = getattr(args, phase.lower() + "_report") or REPORTS / f"SERVER_SOURCE_REVIEW_{phase}_{suffix}.json"
        report = read(path)
        assert report["status"] == "PASS" and report["exit_code"] == 0, "Native server gate failed: " + str(path)
        assert report["artifact_sha256"] == artifact_sha, "Server evidence refers to another artifact: " + str(path)
        assert report["original_world_loaded"] is False and report["rp_initialization_count"] == 1
        wrappers[phase] = report
        gates.append({"path": str(path), "sha256": digest(path), "status": report["status"], "artifact_sha256": report["artifact_sha256"]})
    assert world == (Path(wrappers["REOPEN"]["run_directory"]) / "isolated-smoke-world").resolve()
    assert wrappers["REOPEN"]["bounded_source_input"] is None and wrappers["REOPEN"]["review_scene_input"] is None, "Archive requires production-only reopen"
    for phase in ("AUTHOR", "REPLAY"):
        bound = wrappers[phase]["bounded_source_input"]
        assert bound["sha256"] == input_sha
        assert bound["source_fixture_sha256"] == manifest["sourceFixtureSha256"]
        assert bound["source_archive_sha256"] == manifest["sourceArchiveSha256"]
        assert digest(bound["original_snapshot"]) == bound["original_snapshot_sha256"] == manifest["sourceFixtureSha256"]
        raw = wrappers[phase]["bounded_source_output"]
        assert digest(raw["path"]) == raw["sha256"]
        raw_report = read(raw["path"])
        assert raw_report == raw["result"]
        if phase == "AUTHOR":
            assert raw_report["status"] == "PASS_BOUNDED_SOURCE_MIGRATION_REQUIRES_PRODUCTION_REOPEN"
            assert raw_report["objectCount"] == 41 and raw_report["sourceMemberCount"] == 110
            assert raw_report["injectedWholeBatchRollback"] == "PASS_41_OBJECTS_110_SOURCE_CELLS_STATE_TYPED_NBT_LEDGER_NO_ITEM_DROPS"
            assert raw_report["nativeNonmemberChanges"] == []
        else:
            assert raw_report["status"] == "PASS_SOURCE_MIGRATION_ALREADY_APPLIED_NO_OP"
            assert raw_report["idempotence"] == "PASS_PERSISTED_SIGNATURE_UUID_AND_ALL_FOOTPRINT_BINDINGS"
    independent_names = (
        f"SOURCE_REVIEW_POSTSAVE_INDEPENDENT_{suffix}.json",
        f"SOURCE_REVIEW_POSTSAVE_REOPEN_INDEPENDENT_{suffix}.json",
        f"SOURCE_REVIEW_SECOND_SAVE_INDEPENDENT_{suffix}.json",
        f"SOURCE_REVIEW_PERSISTED_REPLAY_INDEPENDENT_{suffix}.json")
    independent_paths = [args.preload_report] + [getattr(args, field) or REPORTS / name for field, name in zip(
        ("postsave_report", "postsave_reopen_report", "second_save_report", "persisted_replay_report"), independent_names)]
    independent = {}
    for index, path in enumerate(independent_paths):
        report = read(path)
        assert report["status"].startswith("PASS_"), "Independent gate failed: " + str(path)
        assert report["input_sha256"] == input_sha and not report.get("failures"), "Independent input/failure mismatch: " + str(path)
        independent[path.name if index == 0 else independent_names[index-1]] = report
        gates.append({"path": str(path), "sha256": digest(path), "status": report["status"], "input_sha256": report["input_sha256"]})
    saved = independent[f"SOURCE_REVIEW_POSTSAVE_REOPEN_INDEPENDENT_{suffix}.json"]
    authored = independent[f"SOURCE_REVIEW_POSTSAVE_INDEPENDENT_{suffix}.json"]
    author_world = (Path(wrappers["AUTHOR"]["run_directory"]) / "isolated-smoke-world").resolve()
    original_world = (args.input.parent / "world").resolve()
    assert Path(authored["before"]).resolve() == Path(saved["before"]).resolve() == original_world
    assert Path(authored["after"]).resolve() == author_world
    assert authored["runtime_report_sha256"] == saved["runtime_report_sha256"] == wrappers["AUTHOR"]["bounded_source_output"]["sha256"]
    for phase, name in (("REOPEN", f"SOURCE_REVIEW_SECOND_SAVE_INDEPENDENT_{suffix}.json"), ("REPLAY", f"SOURCE_REVIEW_PERSISTED_REPLAY_INDEPENDENT_{suffix}.json")):
        proof = independent[name]
        assert Path(proof["author"]).resolve() == author_world
        assert Path(proof["after"]).resolve() == (Path(wrappers[phase]["run_directory"]) / "isolated-smoke-world").resolve()
        assert proof["counts"].get("unclassified_source_or_native_state_changes", proof["counts"]["logical_state_changes"]) == 0
    assert Path(saved["after"]).resolve() == world
    expected_owner_keys = {row["key"] for row in manifest["objects"] if row["kind"] != "prototype_wall"}
    checks = saved["owner_mask_art_provenance_checks"]
    assert all(row["status"] == "PASS" for row in checks) and len(checks) == len(expected_owner_keys) and {row["key"] for row in checks} == expected_owner_keys
    assert saved["counts"]["unplanned_nonmember_state_changes"] == saved["counts"]["foreign_be_data_changes"] == 0
    assert saved["counts"]["source_rp_entities_checked"] == 15 and saved["counts"]["source_rp_live_legacy_payload_failures"] == 0
    assert saved["counts"]["source_technical_light_cells_checked"] == 2
    original = args.original_fixture
    assert digest(original) == manifest["sourceFixtureSha256"]
    plan = read(args.migration_plan)
    assert digest(plan["source_archive"]) == manifest["sourceArchiveSha256"] == plan["source_sha256_before"] == plan["source_sha256_after"]
    before = inventory(world)
    archived = {name: row for name, row in before.items() if name != "session.lock"}
    omitted = [name for name in before if name not in archived]
    assert omitted in ([], ["session.lock"])
    args.archive.parent.mkdir(parents=True, exist_ok=True)
    assert not args.archive.exists(), "Never overwrite an existing source review archive; select a fresh revision path"
    with zipfile.ZipFile(args.archive, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        folder = zipfile.ZipInfo(PREFIX, (1980, 1, 1, 0, 0, 0)); folder.create_system = 3; folder.external_attr = (0o40755 << 16) | 0x10
        archive.writestr(folder, b"")
        for name in sorted(archived):
            info = zipfile.ZipInfo(PREFIX+name, (1980, 1, 1, 0, 0, 0)); info.create_system = 3; info.external_attr = 0o100644 << 16; info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, (world / name).read_bytes(), compresslevel=9)
    with zipfile.ZipFile(args.archive) as archive:
        assert set(archive.namelist()) == {PREFIX} | {PREFIX+name for name in archived}
        for name, row in archived.items():
            data = archive.read(PREFIX+name)
            assert len(data) == row["bytes"] and hashlib.sha256(data).hexdigest() == row["sha256"]
            assert data == (world / name).read_bytes()
    assert inventory(world) == before, "Source reopened world changed during archive creation"
    assert digest(args.artifact) == artifact_sha and digest(args.input) == input_sha
    result = {"schema": "dreamwalker-first-set-source-review-scene-v1", "status": "PASS_ARCHIVE_EXACT_PRODUCTION_REOPEN; PENDING_USER_REVIEW",
              "revision": args.revision, "artifact": str(args.artifact.resolve()), "artifact_sha256": artifact_sha,
              "source_world": str(world), "archive": str(args.archive.resolve()), "archive_sha256": digest(args.archive), "archive_bytes": args.archive.stat().st_size,
              "input": str(args.input.resolve()), "input_sha256": input_sha,
              "migration_plan": str(args.migration_plan.resolve()), "migration_plan_sha256": digest(args.migration_plan),
              "source_fixture": str(original), "source_fixture_sha256": manifest["sourceFixtureSha256"], "original_full_map_sha256": manifest["sourceArchiveSha256"],
              "counts": {"objects": 41, "source_member_cells": 110, "source_world_files": len(before), "archived_files": len(archived),
                         "archive_entries_including_root_directory": len(archived)+1, "uncompressed_bytes": sum(row["bytes"] for row in archived.values()),
                         "original_terrain_chunks": saved["counts"]["original_terrain_chunks"], "extra_generated_chunks_preserved": saved["counts"]["saved_extra_chunks"]},
              "omitted_files": omitted, "all_archived_file_bytes_verified": True, "reopened_world_unchanged": True,
              "all_chunk_and_generated_terrain_files_retained": True, "file_manifest": [{"path": name, **archived[name]} for name in sorted(archived)], "evidence_gates": gates,
              "physics_and_visual_acceptance": "PENDING_USER_REVIEW", "full_city_conversion": "NOT_RUN",
              "limits": ["This41-object/110-cell copied fixture contains additional ordinary generated terrain; it is not a converted full city",
                         "Production reopen and exact archive bytes do not approve proposed physics, client visuals, Creative UI or shader fidelity"]}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
    print(json.dumps({"status": result["status"], "archive_sha256": result["archive_sha256"], "counts": result["counts"]}))


if __name__ == "__main__":
    main()
