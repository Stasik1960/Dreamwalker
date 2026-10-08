"""Dry-run typed RP migration against the original ZIP; never write a world."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import zipfile
from world_io import Tag, NbtFile, RegionFile, decode_nbt, encode_nbt, compound, TAG_COMPOUND as C, TAG_LIST as L
from rp_migration import registry_schema, migrate_entity, migrate_inventory_record, migrate_legacy_lamp_state
from analyze_world import dimension, plain

ROOT = Path(__file__).resolve().parents[1]


def differences(before, after, path):
    if before.type != after.type:
        return [path]
    if before.type == C:
        result = []
        for key in sorted(set(before.value) | set(after.value)):
            old, new = before.value.get(key), after.value.get(key)
            next_path = path + "/" + key
            if old is None and new.type == C:
                result.extend(differences(Tag(C, {}), new, next_path))
            elif old is None or new is None:
                result.append(next_path)
            else:
                result.extend(differences(old, new, next_path))
        return result
    if before.type == L:
        if before.list_type != after.list_type or len(before.value) != len(after.value):
            return [path]
        return [p for index, (old, new) in enumerate(zip(before.value, after.value)) for p in differences(old, new, path + "/" + str(index))]
    return [] if before == after else [path]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--world", type=Path, default=Path("C:/Users/vakir/Downloads/bbmc_v16_map (1).zip"))
    parser.add_argument("--output", type=Path, default=ROOT / "reports/RP_MIGRATION_DRYRUN.json")
    args = parser.parse_args()
    before_sha = hashlib.sha256(args.world.read_bytes()).hexdigest()
    schema = registry_schema()
    records, issues, errors, lamps, counters = [], [], [], [], Counter()
    legacy_lamp_file = None

    def check(tag, result, path, kind):
        counters[kind + "_contexts"] += 1
        expected = {change["path"] for change in result.changes}
        actual = set(differences(tag, result.tag, path))
        if expected != actual:
            raise AssertionError("Unlogged NBT changes at " + path + ": " + repr(actual ^ expected))
        second = migrate_entity(result.tag, path=path, schema=schema) if kind == "entity" else migrate_inventory_record(result.tag, kind=kind, path=path, schema=schema)
        if second.changed or second.tag != result.tag:
            raise AssertionError("Second migration pass changed " + path)
        issues.extend(result.issues)
        if result.changed:
            counters[kind + "_changed"] += 1
            records.append({"path": path, "kind": kind, "changes": result.changes, "issues": result.issues,
                "before_sha256": hashlib.sha256(encode_nbt(NbtFile("", tag))).hexdigest(),
                "candidate_sha256": hashlib.sha256(encode_nbt(NbtFile("", result.tag))).hexdigest(),
                "preservation": "PASS_ALL_UNLOGGED_TYPED_FIELDS_EQUAL", "second_pass": "PASS"})

    with zipfile.ZipFile(args.world) as source:
        level = next(name for name in source.namelist() if name == "level.dat" or name.endswith("/level.dat"))
        prefix = level[:-len("level.dat")]
        for name in sorted(source.namelist()):
            relative = name[len(prefix):] if name.startswith(prefix) else name
            if name.endswith(".mca"):
                kind = Path(relative).parent.name
                if kind not in ("entities", "region"):
                    continue
                try:
                    region = RegionFile(source.read(name))
                except Exception as exc:
                    errors.append({"path": relative, "error": str(exc), "category": "SOURCE_REGION_DEFECT"})
                    continue
                for stored in region.chunks():
                    nbt = stored.nbt()
                    root = compound(nbt.root)
                    if "Level" in root:
                        root = compound(root["Level"])
                    entities = root.get("Entities", Tag(L, [], C))
                    for index, entity in enumerate(entities.value):
                        path = relative + f"/chunk[{stored.x},{stored.z}]/Entities/{index}"
                        result = migrate_entity(entity, path=path, schema=schema)
                        check(entity, result, path, "entity")
                        if compound(entity).get("id", Tag(8, "")).value == "bloodborne:hunterlamp":
                            lamps.append((dimension(relative), entity))
                    block_entities = root.get("block_entities", root.get("TileEntities", Tag(L, [], C)))
                    for index, entity in enumerate(block_entities.value):
                        path = relative + f"/chunk[{stored.x},{stored.z}]/block_entities/{index}"
                        result = migrate_inventory_record(entity, kind="block_entity", path=path, schema=schema)
                        check(entity, result, path, "block_entity")
            elif relative.endswith((".dat", ".dat_old")) and relative != "uid.dat":
                raw = source.read(name)
                try:
                    nbt = decode_nbt(raw, compressed="gzip" if raw.startswith(b"\x1f\x8b") else None)
                except Exception as exc:
                    errors.append({"path": relative, "error": str(exc)})
                    continue
                if relative == "data/hunter_lamp_manager.dat":
                    legacy_lamp_file = (relative, nbt)
                if relative.startswith("playerdata/"):
                    check(nbt.root, migrate_inventory_record(nbt.root, kind="player", path=relative, schema=schema), relative, "player")
                elif relative in ("level.dat", "level.dat_old"):
                    player = compound(compound(nbt.root)["Data"]).get("Player")
                    if player is not None:
                        path = relative + "/Data/Player"
                        check(player, migrate_inventory_record(player, kind="player", path=path, schema=schema), path, "player")
    lamp_candidate = None
    if legacy_lamp_file:
        path, nbt = legacy_lamp_file
        migration = migrate_legacy_lamp_state(nbt.root, entities=lamps)
        issues.extend(migration.issues)
        lamp_candidate = {"source": path, "candidate": plain(migration.tag), "changes": migration.changes,
                          "issues": migration.issues, "application": "NOT_APPLIED"}
    after_sha = hashlib.sha256(args.world.read_bytes()).hexdigest()
    if before_sha != after_sha:
        raise AssertionError("Original world changed")
    blocking = [issue for issue in issues if issue["code"] != "SOURCE_DUPLICATE_LAMP_REGISTRATION"]
    result = {"schema": "dreamwalker-rp-migration-dryrun-v1", "source": str(args.world),
              "source_sha256_before": before_sha, "source_sha256_after": after_sha,
              "status": "PASS" if not errors and not blocking else "FAIL", "application": "NOT_APPLIED",
              "counters": dict(counters), "records": records, "lamp_candidate": lamp_candidate,
              "issues": issues, "errors": errors, "game_load_save_preservation": "NOT_RUN"}
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"status": result["status"], "counters": dict(counters), "issues": len(issues), "errors": len(errors), "application": "NOT_APPLIED"}))
    if result["status"] != "PASS":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
