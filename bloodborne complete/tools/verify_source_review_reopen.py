"""Independently compare author and second-save ownership, native data and RP provenance."""
from __future__ import annotations
import argparse
from collections import Counter
import json
from pathlib import Path
import world_io as wi
from verify_source_review_saved import World, typed_diff, expected_root, sha, rp_v9_role_differences, unhandled_source_differences


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--author", type=Path, required=True)
    parser.add_argument("--after", type=Path, required=True)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--original", type=Path, help="Immutable prepared original, required to classify exact generated flat-layer progress")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    before, after = World(args.author), World(args.after)
    manifest = json.loads(args.input.read_text(encoding="utf-8"))
    failures, state_changes, be_changes, entity_changes = [], [], [], []
    counts = Counter()
    for chunk in before.chunks:
        if chunk not in after.chunks:
            failures.append("Missing previously saved chunk: " + str(chunk))
            continue
        old_sections, new_sections = before.sections(chunk), after.sections(chunk)
        for sy in old_sections.keys() | new_sections.keys():
            old = wi.compound(old_sections[sy]).get("block_states") if sy in old_sections else None
            new = wi.compound(new_sections[sy]).get("block_states") if sy in new_sections else None
            counts["logical_cells_compared"] += 4096
            if old == new:
                counts["exact_typed_block_state_sections"] += 1
                continue
            counts["sections_requiring_expanded_palette_comparison"] += 1
            for index, (a, b) in enumerate(zip(before.states(chunk, sy), after.states(chunk, sy))):
                if a != b:
                    state_changes.append({"pos": [chunk[0]*16+index%16, sy*16+index//256, chunk[1]*16+(index//16)%16], "before": a, "after": b})
    generated_progress = []
    protected_changes = state_changes.copy()
    if state_changes and args.original:
        original = World(args.original)
        before_level = wi.compound(wi.compound(wi.read_nbt(args.author / "level.dat").root)["Data"])
        after_level = wi.compound(wi.compound(wi.read_nbt(args.after / "level.dat").root)["Data"])
        assert before_level["WorldGenSettings"] == after_level["WorldGenSettings"], "Generator changed during replay"
        settings = wi.compound(before_level["WorldGenSettings"])
        dimensions = wi.compound(settings["dimensions"])
        generator = wi.compound(wi.compound(dimensions["minecraft:overworld"])["generator"])
        assert generator["type"].value == "minecraft:flat", "Only exact flat-layer generation is classified here"
        flat = wi.compound(generator["settings"])
        layers = []
        for layer in flat["layers"].value:
            values = wi.compound(layer)
            name = values["block"].value
            if name == "minecraft:grass_block":
                name += "[snowy=false]"
            layers += [name] * values["height"].value
        assert len(layers) == 4 and layers == ["minecraft:bedrock", "minecraft:dirt", "minecraft:dirt", "minecraft:grass_block[snowy=false]"]
        ledger = wi.compound(wi.compound(wi.read_nbt(args.author / "data/bloodborne_dw_composite_owners.dat").root)["data"])["cells"]
        owned_chunks = {(wi.compound(cell)["pos"].value[0]//16, wi.compound(cell)["pos"].value[2]//16) for cell in ledger.value}
        source_chunks = {(m["pos"][0]//16, m["pos"][2]//16) for row in manifest["objects"] for m in row["members"]}
        changed_chunks = {(row["pos"][0]//16, row["pos"][2]//16) for row in state_changes}
        status_order = ["empty", "structure_starts", "structure_references", "biomes", "noise", "surface", "carvers", "features", "initialize_light", "light", "spawn", "full"]
        accepted_positions = set()
        for chunk in changed_chunks:
            rows = [row for row in state_changes if (row["pos"][0]//16,row["pos"][2]//16) == chunk]
            if chunk in original.chunks or chunk in owned_chunks or chunk in source_chunks:
                continue
            old_status = before.chunks[chunk]["Status"].value.removeprefix("minecraft:")
            new_status = after.chunks[chunk]["Status"].value.removeprefix("minecraft:")
            if old_status not in status_order or new_status not in status_order or status_order.index(old_status) > status_order.index("biomes") or status_order.index(new_status) <= status_order.index(old_status):
                continue
            expected = {(chunk[0]*16+x, -64+y, chunk[1]*16+z): name for y,name in enumerate(layers) for x in range(16) for z in range(16)}
            if {tuple(row["pos"]): row["after"] for row in rows} != expected or any(row["before"] != "minecraft:air" for row in rows):
                continue
            accepted_positions.update(expected)
            generated_progress.append({"chunk": list(chunk), "original_prepared_chunk_absent": True, "no_source_or_owned_cells": True,
                "before_status": old_status, "after_status": new_status, "changes": len(rows),
                "exact_saved_flat_generator_layers": layers, "classification": "ORDINARY_GENERATION_SEPARATE_FROM_PERSISTED_SOURCE_NO_OP"})
        protected_changes = [row for row in state_changes if tuple(row["pos"]) not in accepted_positions]
    if protected_changes:
        failures.append("Unclassified source/native logical block changes on reopen: " + str(len(protected_changes)))
    for pos in before.block_entities.keys() | after.block_entities.keys():
        diffs = []
        typed_diff(before.block_entities.get(pos), after.block_entities.get(pos), "BE/"+str(pos), diffs)
        if diffs:
            be_changes.append({"pos": list(pos), "typed_differences": diffs})
    if be_changes:
        failures.append("Typed native/DW block entity changes on reopen: " + str(len(be_changes)))
    persistent_checks = []
    for name in ("bloodborne_dw_composite_owners.dat", "dreamwalker_review_source_migrations.dat"):
        old_path, new_path = args.author / "data" / name, args.after / "data" / name
        diffs = []
        typed_diff(wi.read_nbt(old_path).root, wi.read_nbt(new_path).root, "data/"+name, diffs)
        persistent_checks.append({"path": name, "typed_equal": not diffs, "differences": diffs,
                                  "compressed_bytes_equal": old_path.read_bytes() == new_path.read_bytes()})
        if diffs:
            failures.append("Ownership or persisted source marker changed: " + name)
    root_checks = []
    for row in manifest["objects"]:
        state = after.state(tuple(row["root"]))
        ok = state == expected_root(row, manifest.get("reviewRevision", "V7"))
        root_checks.append({"key": row["key"], "exact_frozen_state": ok, "state": state})
        if not ok:
            failures.append("Frozen source root changed: " + row["key"])
    source_rp_rows = []
    for identity, old in before.entities.items():
        fields = wi.compound(old)
        if not fields["id"].value.startswith("bloodborne_rp:"):
            continue
        saved = after.entities.get(identity)
        if saved is None:
            failures.append("Source RP UUID missing after reopen: " + str(identity))
            continue
        new_fields = wi.compound(saved)
        envelope_before, envelope_after = fields.get("SourceLegacyPayload"), new_fields.get("SourceLegacyPayload")
        envelope_ok = envelope_before == envelope_after and envelope_after is not None
        envelope_fields = wi.compound(envelope_after) if envelope_after is not None and envelope_after.type == wi.TAG_COMPOUND else {}
        original = envelope_fields.get("Original")
        schema_ok = envelope_fields.get("Schema") == wi.Tag(wi.TAG_INT, 1)
        no_nested_snapshot = original is not None and original.type == wi.TAG_COMPOUND and "SourceLegacyPayload" not in wi.compound(original)
        id_ok = fields["id"] == new_fields["id"]
        passthrough_diffs = []
        if original is not None and original.type == wi.TAG_COMPOUND:
            passthrough_diffs = unhandled_source_differences(wi.compound(original), new_fields)
        role_diffs = rp_v9_role_differences(fields, new_fields, reopening=True) if manifest.get("reviewRevision") == "V9" else []
        source_rp_rows.append({"id": fields["id"].value, "uuid_int_array": list(identity), "same_registry_id": id_ok,
                              "exact_same_typed_original_envelope": envelope_ok, "schema_int1": schema_ok,
                              "no_recursive_envelope": no_nested_snapshot,
                              "unhandled_source_field_differences": passthrough_diffs,
                              "v9_persistent_role_schema_differences": role_diffs})
        if not (envelope_ok and schema_ok and no_nested_snapshot and id_ok) or passthrough_diffs or role_diffs:
            failures.append("RP typed original envelope/identity, unhandled source fields or persistent roles changed: " + str(identity))
        diffs = []
        typed_diff(old, saved, "entity/"+str(identity), diffs)
        if diffs:
            entity_changes.append({"id": fields["id"].value, "uuid_int_array": list(identity), "typed_differences": diffs})
    if len(source_rp_rows) != 15:
        failures.append("Expected exactly15 source RP entities, found " + str(len(source_rp_rows)))
    result = {"schema": "dreamwalker-source-review-reopen-independent-v1",
              "status": "PASS_PERSISTED_OWNERSHIP_NATIVE_DATA_AND_NONRECURSIVE_RP_SOURCE_PAYLOAD" if not failures else "FAIL_REOPEN_PRESERVATION",
              "author": str(args.author), "after": str(args.after), "input_sha256": sha(args.input),
              "counts": {**counts, "author_chunks": len(before.chunks), "saved_chunks": len(after.chunks),
                  "additional_chunks": len(after.chunks.keys()-before.chunks.keys()), "native_and_dw_BEs_compared": len(before.block_entities.keys() | after.block_entities.keys()),
                  "source_roots_checked": len(root_checks), "source_rp_envelopes_checked": len(source_rp_rows),
                  "logical_state_changes": len(state_changes), "unclassified_source_or_native_state_changes": len(protected_changes),
                  "proven_generated_flat_layer_progress_cells": sum(row["changes"] for row in generated_progress),
                  "typed_be_changes": len(be_changes), "source_rp_entities_with_other_runtime_changes": len(entity_changes)},
              "failures": failures, "logical_state_changes": state_changes, "typed_be_changes": be_changes,
              "persistent_ownership_and_marker_checks": persistent_checks, "source_root_checks": root_checks,
              "source_rp_identity_envelope_checks": source_rp_rows, "other_live_entity_runtime_differences": entity_changes,
              "proven_generated_chunk_progress": generated_progress, "unclassified_source_or_native_state_changes": protected_changes,
              "limits": ["Chunk metadata, lighting and ordinary entity runtime fields are separate from persisted ownership/source envelope identity",
                         "No client visual, UI, shader or full-city performance verdict is inferred from server reopen"]}
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
    print(json.dumps({"status": result["status"], "counts": result["counts"], "failures": failures}))


if __name__ == "__main__":
    main()
