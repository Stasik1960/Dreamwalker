"""Fail-closed verifier and deterministic ZIP packager for a Nightmare gallery."""
from __future__ import annotations

import argparse
import hashlib
import json
import zipfile
from collections import Counter
from pathlib import Path

import build_nightmare_gallery as gallery
from build_reviewed_gallery import platform_block
from convert_logical_world import PART, World, block_pos_long
from fixture_level_metadata import validate_level_metadata
from world_io import compound, read_nbt


DIMENSION = "minecraft:overworld"
MASTER_STATES = 1160
FORBIDDEN_PARTS = {"playerdata", "history"}


def require(value: bool, message: str) -> None:
    if not value:
        raise ValueError(message)


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def expected_specimens(scope: str) -> tuple[list[dict], list[dict], dict[str, dict]]:
    specimens, definitions, contracts = gallery.specimens_for_scope(scope)
    if scope == gallery.ALL_VISIBLE_SCOPE:
        require(len(specimens) == MASTER_STATES,
                f"generator expectation is {len(specimens)}, not {MASTER_STATES} master states")
    return specimens, definitions, contracts


def verify(world_path: Path) -> dict:
    world_path = world_path.resolve()
    require(world_path.is_dir() and (world_path / "level.dat").is_file(), "gallery must be a world directory with level.dat")
    forbidden = [path.relative_to(world_path).as_posix() for path in world_path.rglob("*")
                 if path.is_file() and (path.name == "session.lock" or FORBIDDEN_PARTS & set(path.relative_to(world_path).parts))]
    require(not forbidden, "gallery contains forbidden runtime data: " + ", ".join(sorted(forbidden)))
    positions_path = world_path / "gallery-positions.json"
    proof_path = world_path / "alt-proof-positions.json"
    scope_path = world_path / "gallery-scope.json"
    require(positions_path.is_file() and proof_path.is_file(), "gallery metadata files are required")
    legacy = not scope_path.is_file()
    if legacy:
        scope_metadata = {"scope": gallery.ALL_VISIBLE_SCOPE}
    else:
        scope_metadata = json.loads(scope_path.read_text(encoding="utf8"))
        require(isinstance(scope_metadata, dict) and scope_metadata.get("schema_version") == 1 and
                scope_metadata.get("scope") in gallery.SCOPES and isinstance(scope_metadata.get("family_ids"), list) and
                isinstance(scope_metadata.get("hidden_compatibility_family_ids"), list),
                "invalid gallery scope metadata")
    scope = scope_metadata["scope"]
    expected, definitions, contracts = expected_specimens(scope)
    expected_ids = [row["id"] for row in definitions]
    if not legacy:
        require(scope_metadata["family_ids"] == expected_ids and
                scope_metadata["hidden_compatibility_family_ids"] == gallery.hidden_compatibility_ids(definitions, gallery._load()[0]),
                "gallery scope family set differs from current definitions")
    positions = json.loads(positions_path.read_text(encoding="utf8"))
    require(isinstance(positions, list), "gallery positions must be a list")
    if scope == gallery.ALL_VISIBLE_SCOPE:
        require(len(positions) == MASTER_STATES, "all-visible gallery must list every 1160 master state")
    # Older metadata lacks only the scope/hidden label, not physical QA. Never
    # skip world-cell/ownership checks and then report a fictitious PASS.
    require(len(expected) == len(positions), "generator and gallery master counts differ")
    expected_keys = Counter((row["id"], row["state"], row["purpose"], tuple(sorted(row["properties"].items())), row["hidden_compatibility"])
                            for row in expected)
    actual_keys = Counter((row.get("id"), row.get("state"), row.get("purpose"), tuple(sorted(row.get("properties", {}).items())),
                           row.get("hidden_compatibility", False if legacy else None)) for row in positions)
    require(actual_keys == expected_keys, "gallery state/purpose records differ from the current generator")
    require({row["id"] for row in positions} == set(expected_ids), "gallery family set differs from current scope")

    defaults = {"bloodborne_blocks:" + row["id"]: row["default"] for row in definitions}
    floor = platform_block()  # Source/mapping audit is expensive; resolve once, not per floor cell.
    world = World(world_path, defaults)
    entities = world.block_entities()
    helpers = floor_specimens = platform_cells = 0
    roots = set()
    for position, specimen in zip(positions, expected):
        require((position["id"], position["state"], position["purpose"], position["properties"]) ==
                (specimen["id"], specimen["state"], specimen["purpose"], specimen["properties"]),
                "gallery metadata order differs from generator")
        root = tuple(position.get("position", ()))
        require(len(root) == 3 and all(isinstance(value, int) for value in root), "invalid master position")
        require(root[1] == gallery.ROOT_Y and root not in roots, "shifted or duplicate gallery master")
        roots.add(root)
        if not legacy:
            require(tuple(position.get("bounds", ())) == tuple(specimen["bounds"]), "stale gallery render bounds")
        name, properties = world.get(DIMENSION, root) or (None, ())
        require(name == "bloodborne_blocks:" + specimen["id"] and dict(properties) == specimen["properties"],
                f"master state mismatch at {root}")
        for offset in specimen["footprint"]:
            if offset == (0, 0, 0):
                continue
            point = tuple(root[index] + offset[index] for index in range(3))
            require((world.get(DIMENSION, point) or (None, ()))[0] == PART, f"missing helper at {point}")
            entity = entities.get((DIMENSION, *point))
            require(entity is not None, f"missing helper block entity at {point}")
            data = compound(entity)
            require(data.get("Owner") is not None and data.get("Root") is not None and
                    data["Owner"].value == name and data["Root"].value == block_pos_long(*root),
                    f"helper ownership mismatch at {point}")
            helpers += 1
        if scope == gallery.CONTRACT_V2_SCOPE and contracts[specimen["id"]]["placement_policy"] == "FLOOR":
            floor_specimens += 1
            low_x, low_z, high_x, high_z = gallery._horizontal_extent(specimen["bounds"], specimen["footprint"])
            for x in range(root[0] + low_x, root[0] + high_x + 1):
                for z in range(root[2] + low_z, root[2] + high_z + 1):
                    require((world.get(DIMENSION, (x, gallery.FLOOR_Y, z)) or (None, ()))[0] == floor,
                            f"platform does not cover FLOOR root/helper/render extent at {(x, gallery.FLOOR_Y, z)}")
                    platform_cells += 1

    proof = json.loads(proof_path.read_text(encoding="utf8"))
    proof_rows = [row for row in positions if row["purpose"].startswith("visual_proof_")]
    require(proof == proof_rows and len(proof_rows) == len(gallery.VISUAL_PROOF_IDS) * 2,
            "gallery must contain exactly six physical BASE/ALT proofs")
    require(Counter(row["purpose"] for row in proof_rows) == Counter({"visual_proof_base": 3, "visual_proof_alt": 3}),
            "visual proof purpose counts differ")

    metadata = read_nbt(world_path / "level.dat")
    validate_level_metadata(metadata)
    level = compound(compound(metadata.root)["Data"])
    require(level.get("LevelName") is not None and level["LevelName"].value == gallery.level_name(scope),
            "unexpected gallery level metadata")
    require(all(key in level for key in ("SpawnX", "SpawnY", "SpawnZ")), "gallery spawn metadata is incomplete")
    spawn = tuple(int(level[key].value) for key in ("SpawnX", "SpawnY", "SpawnZ"))
    require(spawn == (16, 66, 6), "unexpected gallery spawn metadata")
    require((world.get(DIMENSION, (spawn[0], gallery.FLOOR_Y, spawn[2])) or (None, ()))[0] == floor,
            "spawn pad is not safe at the known gallery floor Y")
    return {"scope": scope, "legacy_scope_metadata": legacy, "families": len(expected_ids), "master_states": len(positions), "helpers": helpers,
            "floor_specimens": floor_specimens, "verified_platform_cells": platform_cells,
            "floor_platform": "PASS" if scope == gallery.CONTRACT_V2_SCOPE else "NOT_CHECKED_LEGACY",
            "visual_proofs": len(proof_rows), "level_metadata": "PASS", "helper_ownership": "PASS",
            "client_visual": "NOT_PERFORMED"}


def package(world_path: Path, output_zip: Path, report_path: Path, *, force: bool = False) -> dict:
    world_path, output_zip, report_path = world_path.resolve(), output_zip.resolve(), report_path.resolve()
    if not force:
        require(not output_zip.exists(), "refusing to overwrite existing ZIP; pass --force")
        require(not report_path.exists(), "refusing to overwrite existing report; pass --force")
    counts = verify(world_path)
    output_zip.parent.mkdir(parents=True, exist_ok=True)
    root = world_path.name
    with zipfile.ZipFile(output_zip, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9, strict_timestamps=True) as archive:
        for path in sorted(item for item in world_path.rglob("*") if item.is_file()):
            relative = path.relative_to(world_path)
            require(path.name != "session.lock" and not (FORBIDDEN_PARTS & set(relative.parts)), "forbidden ZIP member")
            info = zipfile.ZipInfo(root + "/" + relative.as_posix(), date_time=(1980, 1, 1, 0, 0, 0))
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(output_zip) as archive:
        names = archive.namelist()
        require(archive.testzip() is None and names and all(name.startswith(root + "/") for name in names), "invalid packaged ZIP")
        require({name.split("/", 1)[0] for name in names} == {root}, "ZIP root must equal gallery folder name")
    report = {"result": "PASS", "gallery": str(world_path), "zip": {"path": str(output_zip), "root": root,
              "bytes": output_zip.stat().st_size, "sha256": sha256(output_zip)}, "counts": counts}
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps(report, ensure_ascii=False), flush=True)
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("gallery", type=Path)
    parser.add_argument("output_zip", type=Path)
    parser.add_argument("report_json", type=Path)
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()
    package(args.gallery, args.output_zip, args.report_json, force=args.force)
