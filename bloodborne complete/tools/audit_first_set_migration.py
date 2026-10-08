"""Read-only independent audit of bounded recognized source members and evidence.

This does not convert a save, approve proposed physics, or count the whole city.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
REPORTS = ROOT / "reports"


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    path = REPORTS / "FIRST_SET_MIGRATION_PLAN.json"
    plan = json.loads(path.read_text(encoding="utf-8"))
    objects = plan["coordinate_instances"]
    ladders = plan["ladder_pairs"]
    members = [m for obj in objects for m in obj["source_members"]]
    members += [m for pair in ladders for m in pair["source_members"]]
    positions = {tuple(m["pos"]) for m in members}
    caps = {tuple(m["pos"]) for m in plan["unconsumed_ladder_caps"]}
    assert len(objects) == 8 and len(ladders) == 34
    assert len(members) == len(positions) == 112
    assert not positions & caps, "Unrecognized honey0 cap must not be consumed"
    assert plan["source_sha256_before"] == plan["source_sha256_after"]
    source_bes = [m for m in members if "source_block_entity_nbt" in m]
    assert len(source_bes) == 34
    assert all(m["source_block_entity_nbt"]["type"] == 10 for m in source_bes)
    roots = []
    for obj in objects:
        context = obj.get("target_root_context", obj.get("owned_source_root_candidate"))
        assert context["ordinary_root_occupancy"] in {"AIR", "OWNED_SOURCE_MEMBER"}
        roots.append({"key": obj["key"], "root": context["root"]["pos"],
                      "classification": context["ordinary_root_occupancy"],
                      "support_state": context["below"]["state"],
                      "support_required": context["required_support"]})
    protected_tree_grass = [o["canonical_descriptor_anchor"]["root"]
                            for o in objects if o["key"].startswith("architecture_tree_")]
    assert all(tuple(m["pos"]) not in positions and m["state"].startswith("minecraft:grass_block")
               for m in protected_tree_grass)
    attempts = []
    for report in sorted(REPORTS.glob("FIRST_SET_GAMETEST_ATTEMPT_*.xml"), key=lambda p: int(re.search(r"_(\d+)\.xml$", p.name).group(1))):
        if report.exists():
            parsed = ET.parse(report)
            cases = parsed.findall(".//testcase")
            failures = parsed.findall(".//failure") + parsed.findall(".//error")
            attempts.append({"report": str(report.relative_to(ROOT)), "sha256": digest(report),
                             "tests": len(cases), "failures": len(failures),
                             "status": "PASS" if cases and not failures else "FAIL"})
    result = {
        "schema": "dreamwalker-bounded-first-set-preservation-audit-v1",
        "status": "SOURCE_RECOGNITION_AUDITED; BOUNDED_COPIED_SAVE_QA_SEPARATE; USER_ACCEPTANCE_PENDING",
        "plan": {"path": str(path.relative_to(ROOT)), "sha256": digest(path)},
        "source_archive_sha256": plan["source_sha256_before"],
        "counts": {"recognized_objects": 42, "non_ladder_objects": 8,
                   "recognized_source_member_cells": 112, "unique_source_member_cells": len(positions),
                   "source_member_chunks": len({tuple(m["terrain_chunk"]) for m in members}),
                   "recognized_beehive_block_entities": len(source_bes),
                   "additional_air_door_roots": sum(r["classification"] == "AIR" for r in roots),
                   "protected_honey0_caps": len(caps)},
        "roots": roots,
        "protected_tree_grass": protected_tree_grass,
        "exact_member_whitelist": [{"pos": m["pos"], "expected_state": m["state"],
                                    **({"expected_typed_be": m["source_block_entity_nbt"],
                                        "expected_be_sha256": m["source_block_entity_nbt_sha256"]}
                                       if "source_block_entity_nbt" in m else {})} for m in members],
        "runtime_contracts": {
            "source_shift": "IMPLEMENTED; NBT DOUBLE list3, block units, global XZ rotation once; render/collision/selection share vector",
            "tree_source_anchor": "Owned lowest melon at Y119; SourceShift [0,-1,0]; grass Y118 remains foreign",
            "roof_source_anchor": "Source cell north0; SourceShift [0,-0.1875,0.3125]; global rotation applies once",
            "source_ladder": "Typed recognized pair and static UUID-linked backing; frozen source RNG variant; caps excluded",
            "ordinary_pick_drop": "Strips SourceShift/MountY/source technical ownership for canonical fresh new build",
            "tree_physics": "USER_PENDING: narrow continuous lower stem and passable central upper billboard differ from measured source wool/melon",
        },
        "technical_gametest_evidence": attempts,
        "migration_requirements": [
            {"check": "Exact expected source state and typed BE validation before writes", "status": "REQUIRED"},
            {"check": "Single rollback journal for exact members plus new root and effective sparse target footprint", "status": "REQUIRED"},
            {"check": "Preserve native foreign state/BE identity and other-owner ledger entries", "status": "REQUIRED"},
            {"check": "Final publish covers union of consumed source cells and target writes", "status": "REQUIRED"},
            {"check": "Persist source key/signature and verify same UUID/masks on second-run no-op", "status": "REQUIRED"},
            {"check": "Copied save before/after offline whitelist and exact nonmember typed NBT verification", "status": "NOT_RUN"},
            {"check": "Separate Minecraft DataFixer/tick changes from conversion-stage changes", "status": "REQUIRED"},
            {"check": "Actual migrated bounded source load and unload/reload persistence", "status": "NOT_RUN"},
            {"check": "Client art/UV/lighting/UI and user first-set acceptance", "status": "NOT_RUN"},
        ],
        "source_world_written": False,
        "full_city_conversion": "NOT_RUN; requires accepted first set and later spatial/network scaling",
        "numeric_ids_frozen": False,
    }
    preload_candidates = sorted((p for p in REPORTS.glob("SOURCE_REVIEW_PRELOAD_INDEPENDENT_V*.json") if re.search(r"_V(\d+)\.json$", p.name)), key=lambda p: int(re.search(r"_V(\d+)\.json$", p.name).group(1)))
    preload = preload_candidates[-1] if preload_candidates else REPORTS / "SOURCE_REVIEW_PRELOAD_INDEPENDENT.json"
    if preload.exists():
        evidence = json.loads(preload.read_text(encoding="utf-8"))
        result["preload_preparation_evidence"] = {"path": str(preload.relative_to(ROOT)),
            "sha256": digest(preload), "status": evidence["status"], "counts": evidence["counts"],
            "scope_difference": "Frozen-subset derivative excludes absent EAST door;41 objects/110 members versus42/112 recognized plan"}
    post_candidates = sorted((p for p in REPORTS.glob("SOURCE_REVIEW_POSTSAVE_INDEPENDENT_V*.json") if re.search(r"_V(\d+)\.json$", p.name)), key=lambda p: int(re.search(r"_V(\d+)\.json$", p.name).group(1)))
    if post_candidates:
        post = post_candidates[-1]
        evidence = json.loads(post.read_text(encoding="utf-8"))
        suffix = re.search(r"_V(\d+)\.json$", post.name).group(1)
        paths = [post] + [REPORTS / (name + suffix + ".json") for name in (
            "SOURCE_REVIEW_POSTSAVE_REOPEN_INDEPENDENT_V", "SOURCE_REVIEW_SECOND_SAVE_INDEPENDENT_V", "SOURCE_REVIEW_PERSISTED_REPLAY_INDEPENDENT_V")]
        result["bounded_copied_save_evidence"] = []
        for item in paths:
            if item.exists():
                row = json.loads(item.read_text(encoding="utf-8"))
                result["bounded_copied_save_evidence"].append({"path": str(item.relative_to(ROOT)), "sha256": digest(item), "status": row["status"], "counts": row["counts"]})
        server = REPORTS / ("SERVER_SOURCE_REVIEW_AUTHOR_V" + suffix + ".json")
        if server.exists():
            row = json.loads(server.read_text(encoding="utf-8"))
            result["bounded_evidence_artifact_sha256"] = row["artifact_sha256"]
        if all(row["status"].startswith("PASS_") for row in result["bounded_copied_save_evidence"]) and len(result["bounded_copied_save_evidence"]) == 4:
            result["status"] = "SOURCE_RECOGNITION_AND_BOUNDED_COPIED_SAVE_QA_PASS; USER_ACCEPTANCE_PENDING"
            for row in result["migration_requirements"]:
                if row["check"] in {"Copied save before/after offline whitelist and exact nonmember typed NBT verification", "Actual migrated bounded source load and unload/reload persistence"}:
                    row["status"] = "PASS_BOUNDED_DERIVATIVE_ONLY"
    (REPORTS / "FIRST_SET_MIGRATION_PRESERVATION_AUDIT.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"source_recognition": "PASS", **result["counts"],
                      "migration_archive_qa": result["status"], "world_written": False}))


if __name__ == "__main__":
    main()
