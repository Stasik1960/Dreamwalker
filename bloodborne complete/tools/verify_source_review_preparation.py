"""Independent typed-field/whole-file verification before Minecraft loads a source copy.

Does not invoke the preparation converter or its differences implementation.
"""
from __future__ import annotations
import argparse
import base64
from collections import Counter
import hashlib
import json
from pathlib import Path
import uuid
import zipfile
import world_io as wi

ROOT = Path(__file__).resolve().parents[1]
SOURCE_LIGHT = "bloodborne:hunter_lamp_light_source"
TARGET_LIGHT = "bloodborne_dw:source_hunter_lamp_light"
LIGHT_CELLS = {(228, 69, -932), (229, 69, -931)}


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def plain(tag):
    if isinstance(tag, wi.Tag):
        return plain(tag.value)
    if isinstance(tag, dict):
        return {k: plain(v) for k, v in tag.items()}
    if isinstance(tag, (list, tuple)):
        return [plain(v) for v in tag]
    if isinstance(tag, bytes):
        return list(tag)
    return tag


def differences(before, after, path, changes):
    if before == after:
        return
    if before is None or after is None:
        changes[path] = (before, after)
        return
    if before.type != after.type:
        raise AssertionError(f"NBT tag type changed at {path}: {before.type} -> {after.type}")
    if before.type == wi.TAG_COMPOUND:
        for key in before.value.keys() | after.value.keys():
            differences(before.value.get(key), after.value.get(key), path + "/" + key, changes)
    elif before.type == wi.TAG_LIST:
        assert before.list_type == after.list_type, "NBT list element type changed: " + path
        assert len(before.value) == len(after.value), "NBT list length changed: " + path
        for index, (a, b) in enumerate(zip(before.value, after.value)):
            differences(a, b, f"{path}/{index}", changes)
    else:
        changes[path] = (before, after)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, default=ROOT / "reports/SOURCE_REVIEW_PREPARATION.json")
    parser.add_argument("--output", type=Path, default=ROOT / "reports/SOURCE_REVIEW_PRELOAD_INDEPENDENT.json")
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    source_path = Path(report["sourceFixture"])
    world = Path(report["world"])
    manifest_path = Path(report["input"])
    source_bytes = source_path.read_bytes()
    source_sha = sha(source_bytes)
    assert source_sha == report["sourceFixtureSha256"]
    assert Path(report["provenanceArchive"]).read_bytes() == source_bytes
    assert sha(manifest_path.read_bytes()) == report["inputSha256"]
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("reviewRevision", "V7") != "V7":
        plan_path = Path(manifest["sourcePlan"])
        assert sha(plan_path.read_bytes()) == manifest["sourcePlanSha256"] == report["sourcePlanSha256"]
        plan = json.loads(plan_path.read_text(encoding="utf-8"))
        assert plan["review_revision"] == manifest["reviewRevision"] == report["reviewRevision"]
        assert plan["descriptor_sha256"] == manifest["descriptorSha256"]
        if manifest["reviewRevision"] == "V9":
            # Independently verify all supplied descriptors, including the non-migrated new ordinary glass type.
            descriptor_files = {p.stem: p for p in (ROOT / "src/architecture/resources/bloodborne_dw/composite").glob("*.json")}
            assert set(descriptor_files) == set(manifest["descriptorSha256"]), "V9 descriptor coverage must include every current composite type"
            artifact = plan["production_descriptor_artifact"]
            artifact_path = Path(artifact["path"])
            assert sha(artifact_path.read_bytes()) == artifact["sha256"]
            with zipfile.ZipFile(artifact_path) as jar:
                prefix = "bloodborne_dw/composite/"
                jar_descriptors = {n[len(prefix):-5]: jar.read(n) for n in jar.namelist() if n.startswith(prefix) and n.endswith(".json")}
            assert set(jar_descriptors) == set(manifest["descriptorSha256"])
            for kind, payload in jar_descriptors.items():
                assert payload == descriptor_files[kind].read_bytes() and sha(payload) == manifest["descriptorSha256"][kind]
                assert json.loads(payload)["id"] == "bloodborne_dw:" + kind
        for kind, expected in manifest["descriptorSha256"].items():
            assert sha((ROOT / "src/architecture/resources/bloodborne_dw/composite" / (kind + ".json")).read_bytes()) == expected
        for row in manifest["objects"]:
            if row["kind"] == "prototype_wall":
                art = row["art"]
                assert set(art) == {"connections", "rotation", "profile", "material", "up", "waterlogged", "north", "east", "south", "west"}
                assert art["connections"] == "manual" and art["up"] in {"true", "false"}
                assert all(art[side] in {"none", "low", "tall"} for side in ("north", "east", "south", "west"))
                member = row["members"][0]["properties"]
                assert all(art[key] == member[key] for key in ("up", "waterlogged", "north", "east", "south", "west"))
            elif row["kind"] == "prototype_thin_window":
                assert row["mount"] == "vertical" and row["sourceShift"] == [0, 0, 0]
                assert "GlazingMounted" not in row
            elif row["kind"] == "prototype_tree":
                assert row["variant"] == 0, "Optional lower UV proposal must never silently replace source variant0"
    physics_reference = manifest.get("sourcePhysicsReference")
    if physics_reference:
        physics_bytes = Path(physics_reference).read_bytes()
        assert sha(physics_bytes) == manifest["sourcePhysicsReferenceSha256"]
        physics = json.loads(physics_bytes)
        assert physics["status"] == "MEASURED_ACTUAL_SOURCE_SERVER" and physics["runtime_mode"] == "integrated-client"
        assert manifest["sourcePhysicsMovements"] == physics["actual_fake_player_movements"]
        assert len(manifest["sourcePhysicsMovements"]) == 7
        assert all(row["allSweptMovementChunksPreloaded"] for row in manifest["sourcePhysicsMovements"])
    logged = {}
    light_records = {}
    for record in report["preloadAudit"]["records"]:
        if record["kind"] == "technical_light_block":
            pos = tuple(record["pos"])
            assert pos in LIGHT_CELLS and pos not in light_records
            assert record["rule"] == "EXACT_TWO_SOURCE_AIRBLOCK_LIGHT9_CELLS"
            assert record["beforeState"] == SOURCE_LIGHT and record["afterState"] == TARGET_LIGHT
            light_records[pos] = record
            continue
        if record["kind"] == "fixture_configuration":
            logged[record["path"]] = {"before": record["before"], "after": record["after"], "rule": "fixture_name_only"}
        else:
            for change in record["changes"]:
                assert change["path"] not in logged, "Duplicate logged field " + change["path"]
                logged[change["path"]] = change
    with zipfile.ZipFile(source_path) as archive:
        original = {n: archive.read(n) for n in archive.namelist() if not n.endswith("/")}
    actual_names = {p.relative_to(world).as_posix() for p in world.rglob("*") if p.is_file()}
    additions = actual_names - original.keys()
    assert additions == {"dreamwalker-source-copy.json", "data/bloodborne_rp_lamps.dat"}
    assert original.keys() <= actual_names, "Original files removed"
    assert {r["path"] for r in report["worldFiles"]} == actual_names - {"dreamwalker-source-copy.json"}
    for row in report["worldFiles"]:
        assert sha((world / row["path"]).read_bytes()) == row["sha256"]
    observed = {}
    counts = Counter()
    entities = []
    source_chunks = {}
    prepared_chunks = {}
    light_palette_changes = {}
    light_positions = set()
    for name, before in original.items():
        after = (world / name).read_bytes()
        if name.endswith(".mca"):
            a, b = wi.RegionFile(before), wi.RegionFile(after)
            chunks_a = {(c.x, c.z): c for c in a.chunks()}
            chunks_b = {(c.x, c.z): c for c in b.chunks()}
            assert chunks_a.keys() == chunks_b.keys(), "Chunk membership changed: " + name
            for cell, old in chunks_a.items():
                new = chunks_b[cell]
                assert old.compression == new.compression and old.timestamp == new.timestamp
                old_nbt, new_nbt = old.nbt(), new.nbt()
                assert old_nbt.name == new_nbt.name
                differences(old_nbt.root, new_nbt.root, f"{name}/chunk/{old.x},{old.z}", observed)
                if old_nbt == new_nbt:
                    assert old.compressed_payload == new.compressed_payload
                    counts["unchanged_compressed_chunks"] += 1
                else:
                    counts["changed_typed_chunks"] += 1
                    if name.startswith("entities/"):
                        counts["changed_entity_chunks"] += 1
                fields = wi.compound(old_nbt.root)
                if name.startswith("region/"):
                    chunk = (fields["xPos"].value, fields["zPos"].value)
                    source_chunks[chunk] = fields
                    updated = wi.compound(new_nbt.root)
                    prepared_chunks[chunk] = updated
                    if old_nbt == new_nbt:
                        counts["byte_exact_terrain_chunks"] += 1
                    else:
                        counts["explicitly_mapped_technical_light_chunks"] += 1
                    assert fields.get("block_entities") == updated.get("block_entities"), "Native BE bytes changed before load"
                    for section_index, section in enumerate(fields.get("sections", wi.Tag(9, [], 10)).value):
                        section_fields = wi.compound(section)
                        states = section_fields.get("block_states")
                        if states is None:
                            continue
                        palette, indices = wi.section_blocks(section)
                        for value in palette:
                            name_id = wi.compound(value)["Name"].value
                            assert name_id.startswith("minecraft:") or name_id == SOURCE_LIGHT, "Unreviewed source palette provider: " + name_id
                        prepared_section = wi.compound(updated["sections"].value[section_index])
                        prepared_palette = wi.compound(prepared_section["block_states"])["palette"].value
                        for value in prepared_palette:
                            name_id = wi.compound(value)["Name"].value
                            assert name_id.startswith("minecraft:") or name_id == (TARGET_LIGHT if light_records else SOURCE_LIGHT), "Unreviewed prepared palette provider: " + name_id
                        for palette_index, entry in enumerate(palette):
                            entry_fields = wi.compound(entry)
                            if entry_fields["Name"].value != SOURCE_LIGHT:
                                continue
                            assert set(entry_fields) == {"Name"}, "Unexpected source light state properties"
                            path = f"{name}/chunk/{old.x},{old.z}/sections/{section_index}/block_states/palette/{palette_index}/Name"
                            light_palette_changes[path] = {"before": SOURCE_LIGHT, "after": TARGET_LIGHT, "rule": "exact_source_light_palette_name"}
                            for index, value in enumerate(indices):
                                if value == palette_index:
                                    pos = (chunk[0]*16+index%16, section_fields["Y"].value*16+index//256, chunk[1]*16+(index//16)%16)
                                    assert pos in LIGHT_CELLS, "Unreviewed occupied legacy light cell: " + str(pos)
                                    light_positions.add(pos)
                    for be in fields.get("block_entities", wi.Tag(9, [], 10)).value:
                        counts["byte_exact_native_block_entities"] += 1
                if name.startswith("entities/"):
                    for entity in wi.compound(new_nbt.root).get("Entities", wi.Tag(9, [], 10)).value:
                        entities.append(entity)
                        counts["source_rp_entities"] += 1
        elif name == "level.dat":
            a = wi.decode_nbt(before, compressed="gzip")
            b = wi.decode_nbt(after, compressed="gzip")
            assert a.name == b.name
            differences(a.root, b.root, name, observed)
        else:
            assert before == after, "Unlogged file rewrite: " + name
            counts["byte_exact_other_files"] += 1
    if light_records:
        assert light_positions == LIGHT_CELLS == light_records.keys()
        assert {tuple(row["pos"]) for row in manifest["technicalLightCells"]} == LIGHT_CELLS
        logged.update(light_palette_changes)
    assert observed.keys() == logged.keys(), "Unlogged/missing typed fields: " + str(observed.keys() ^ logged.keys())
    for path, (old, new) in observed.items():
        log = logged[path]
        assert plain(old) == log["before"] and plain(new) == log["after"], "Logged value mismatch: " + path
        if log["rule"] == "fixture_name_only":
            assert path == "level.dat/Data/LevelName" and new.type == wi.TAG_STRING
        elif log["rule"] == "explicit_legacy_weapon_form":
            assert old is None and new.type == wi.TAG_BYTE and new.value == 1
        elif log["rule"] == "exact_source_light_palette_name":
            assert old == wi.Tag(wi.TAG_STRING, SOURCE_LIGHT) and new == wi.Tag(wi.TAG_STRING, TARGET_LIGHT)
        else:
            assert log["rule"] in {"validated_item_registry_map", "validated_entity_registry_map"}
            assert old.type == new.type == wi.TAG_STRING
            assert old.value.startswith("bloodborne:") and new.value.startswith("bloodborne_rp:")

    def cell(pos, chunks=source_chunks):
        x, y, z = pos
        fields = chunks[(x // 16, z // 16)]
        state = "minecraft:air"
        for section in fields.get("sections", wi.Tag(9, [], 10)).value:
            if wi.compound(section)["Y"].value != y // 16:
                continue
            values = wi.section_blocks(section)
            if values:
                palette, indices = values
                state = wi.block_state_key(palette[indices[y % 16 * 256 + z % 16 * 16 + x % 16]])
        be = next((e for e in fields.get("block_entities", wi.Tag(9, [], 10)).value
                   if tuple(wi.compound(e)[key].value for key in ("x", "y", "z")) == tuple(pos)), None)
        return state, be

    for pos, record in light_records.items():
        assert cell(pos)[0] == SOURCE_LIGHT and cell(pos, prepared_chunks)[0] == TARGET_LIGHT
        assert cell(pos)[1] is None and cell(pos, prepared_chunks)[1] is None
        source_typed = wi.decode_nbt(base64.b64decode(record["originalTypedState"])).root
        target_typed = wi.decode_nbt(base64.b64decode(record["targetTypedState"])).root
        assert source_typed == wi.Tag(wi.TAG_COMPOUND, {"Name": wi.Tag(wi.TAG_STRING, SOURCE_LIGHT)})
        assert target_typed == wi.Tag(wi.TAG_COMPOUND, {"Name": wi.Tag(wi.TAG_STRING, TARGET_LIGHT)})

    recognized = set()
    for obj in manifest["objects"]:
        assert obj["ownerUuid"] == str(uuid.uuid5(uuid.NAMESPACE_URL, source_sha + "/" + obj["key"]))
        unsigned = {k: v for k, v in obj.items() if k != "migrationSignature"}
        assert obj["migrationSignature"] == sha(json.dumps(unsigned, sort_keys=True, separators=(",", ":")).encode())
        for row in obj["members"]:
            assert tuple(row["pos"]) not in recognized
            recognized.add(tuple(row["pos"]))
    all_preconditions = [m for o in manifest["objects"] for m in o["members"]]
    all_preconditions += [o["expectedRoot"] for o in manifest["objects"] if "expectedRoot" in o]
    all_preconditions += manifest["nonmemberPreconditions"]
    for row in all_preconditions:
        state, entity = cell(row["pos"])
        assert state == row["state"], "Source precondition wrong: " + str(row["pos"])
        raw = None if entity is None else wi.encode_nbt(wi.NbtFile("", entity))
        assert (None if raw is None else base64.b64encode(raw).decode()) == row["sourceNbt"]
        assert (None if raw is None else sha(raw)) == row["sourceNbtSha256"]
    assert len(manifest["objects"]) == 41 and len(recognized) == 110

    old_manager = wi.compound(wi.compound(wi.decode_nbt(original["data/hunter_lamp_manager.dat"], compressed="gzip").root)["data"])["HunterLamps"]
    manager = wi.compound(wi.compound(wi.read_nbt(world / "data/bloodborne_rp_lamps.dat").root)["data"])
    nodes = manager["Nodes"]
    assert manager["Schema"] == wi.Tag(wi.TAG_INT, 1) and len(nodes.value) == 1
    node = wi.compound(nodes.value[0])
    old_registration = wi.compound(old_manager.value[0])
    pos = tuple(old_registration[key].value for key in ("X", "Y", "Z"))
    lamp = next(wi.compound(e) for e in entities
                if wi.compound(e).get("id", wi.Tag(8, "")).value == "bloodborne_rp:hunterlamp")
    assert node["Id"] == old_registration["hunterLampId"] and node["Lamp"] == lamp["UUID"]
    assert node["Id"] != node["Lamp"] and node["Name"] == old_registration["area"]
    assert tuple(t.value for t in lamp["Pos"].value) == pos
    packed = ((pos[0] & 0x3FFFFFF) << 38) | ((pos[2] & 0x3FFFFFF) << 12) | (pos[1] & 0xFFF)
    if packed >= 1 << 63:
        packed -= 1 << 64
    assert node["Pos"] == wi.Tag(wi.TAG_LONG, packed)
    assert node["Dimension"] == wi.Tag(wi.TAG_STRING, "minecraft:overworld") and nodes.list_type == wi.TAG_COMPOUND
    assert node["Routes"].type == wi.TAG_LIST and not node["Routes"].value
    assert len(old_manager.value) == 2 and old_manager.value[0] == old_manager.value[1]
    assert sha(source_path.read_bytes()) == source_sha
    result = {"schema": "dreamwalker-source-review-preload-independent-v1", "status": "PASS_PRELOAD_PRESERVATION",
              "source_fixture_sha256": source_sha, "input_sha256": sha(manifest_path.read_bytes()),
              "prepared_world": str(world), "counts": {**counts, "logged_typed_field_changes": len(observed),
                 "recognized_objects": 41, "recognized_source_members": 110,
                 "explicit_source_technical_light_cells": len(light_records),
                 "manifest_preconditions_checked": len(all_preconditions)},
              "checks": ["Original/provenance ZIP byte identity", "Every unlogged typed NBT field preserved",
                         "All unlogged typed terrain/NBT fields unchanged; only exact two technical light cells renamed in one palette",
                         "Packed block indices/data and all native BE bytes retained", "Chunk membership/timestamps retained",
                         "Exact members/root/nonmember preconditions independently decoded", "Deterministic owner UUID/signature",
                         "Corrected original runtime movement reference SHA and seven fully preloaded sweep records exact",
                         "Separate lamp custom ID versus actual entity UUID and source position", "Original duplicate lamp registration retained"],
              "limits": ["Pre-load RP preparation only; architecture runtime conversion and DFU/game-save diffs are separate",
                         "Actual entity count15 across13 source IDs, not16", "No full-city conversion or user visual/physics acceptance"]}
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": result["status"], **result["counts"]}))


if __name__ == "__main__":
    main()
