"""Extract one actual v12.7.3 saved door and its exact typed ledger membership.

Only the four bounded release inputs are read; the old world is never changed.
The native test relocates this owner while retaining its original UUID/payload.
"""
from pathlib import Path
import ast
import copy
import csv
import hashlib
import json
import traceback
from verify_v1271_final_readiness import ordinary
from world_io import RegionFile, NbtFile, Tag, compound, read_nbt, write_nbt

ROOT = Path(__file__).resolve().parents[1]
WORLD = Path(r"C:\Users\vakir\.codex\worktrees\3f07\DW\releases\Dreamwalker-BB\v12.7.3-final-checks\World")
TARGET = ROOT / "src/gametest/resources/bloodborne_dw/door90986-v1273-saved-owner.nbt"
REPORT = ROOT / "build/door-90986-repair/legacy-fixture-proof.json"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    index = ordinary(WORLD / "GALLERY-INDEX.csv")
    with index.open(encoding="utf-8-sig", newline="") as source:
        rows = [r for r in csv.DictReader(source) if r["id"] == "90986"]
    assert len(rows) == 1
    root = ast.literal_eval(rows[0]["root"])
    cx, cz = root[0] >> 4, root[2] >> 4
    region_path = ordinary(WORLD / "region" / f"r.{cx>>5}.{cz>>5}.mca")
    ledger_path = ordinary(WORLD / "data/bloodborne_dw_composite_owners.dat")
    level_path = ordinary(WORLD / "level.dat")
    before = {str(p): sha(p) for p in (index, region_path, ledger_path, level_path)}
    region = RegionFile.open(region_path)
    chunk = region.get_chunk(cx & 31, cz & 31)
    assert chunk is not None
    entities = compound(chunk.nbt().root)["block_entities"].value
    matches = [be for be in entities if [compound(be)[axis].value for axis in ("x", "y", "z")] == root]
    assert len(matches) == 1
    entity = matches[0]
    resident = compound(compound(entity)["resident"])
    registry = resident["id"].value
    assert registry == rows[0]["registry"] == "bloodborne_dw:source_stairs_dark_prismarine_stairs_inner_right"
    uuid = resident["uuid"]
    assert resident["root"].value == root
    saved_cells = []
    for value in compound(compound(read_nbt(ledger_path).root)["data"])["cells"].value:
        row = compound(value)
        selected = [entry for entry in row["owners"].value
                    if compound(compound(entry)["owner"])["uuid"] == uuid]
        if not selected:
            continue
        assert len(selected) == 1
        old = compound(selected[0])
        assert compound(old["owner"])["id"].value == registry
        saved_cells.append(Tag(10, {"pos": copy.deepcopy(row["pos"]), "owners": Tag(9, copy.deepcopy(selected), 10)}))
    assert len(saved_cells) == 24, ("Actual v12.7.3 saved membership count", len(saved_cells), "root", root, "uuid", uuid)
    fixture = NbtFile("", Tag(10, {"sourceRoot": Tag(11, root),
        "sourceRegistry": Tag(8, registry), "rootBlockEntity": copy.deepcopy(entity),
        "cells": Tag(9, saved_cells, 10)}))
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    write_nbt(TARGET, fixture)
    assert all(sha(Path(path)) == digest for path, digest in before.items())
    report = {"status": "PASS_ACTUAL_V1273_NATIVE_SAVED_DOOR_FIXTURE", "numericId": "90986",
        "sourceWorld": str(WORLD), "sourceRoot": root, "ownerCells": len(saved_cells),
        "fixture": str(TARGET), "fixtureSha256": sha(TARGET), "typedPayloadAndMembershipPreserved": True,
        "savedRelativeCells": [[compound(row)["pos"].value[i]-root[i] for i in range(3)] for row in saved_cells],
        "sourceWorldFilesUnchanged": True, "sourceHashes": before}
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except BaseException:
        REPORT.parent.mkdir(parents=True, exist_ok=True)
        REPORT.write_text(json.dumps({"status": "FAIL", "error": traceback.format_exc()}, indent=2) + "\n", encoding="utf8")
        raise
