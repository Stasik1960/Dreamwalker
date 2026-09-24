"""Synthetic atomic split-transaction coverage, including a chunk boundary."""
from __future__ import annotations

import tempfile
import unittest
import copy
import gzip
import json
from pathlib import Path

from convert_logical_world import (Expected, Output, Rule, World, apply, candidates,
                                   reject_overlaps)
from test_logical_world import write_chunk
from logical_contract_v2 import direct_rules, load_contracts
from world_io import TAG_BYTE, TAG_COMPOUND, TAG_STRING, NbtFile, RegionFile, Tag, compound, write_nbt

DIM = "minecraft:overworld"
NS = "bloodborne_blocks:"
ROOT = Path(__file__).resolve().parents[1]
HISTORICAL_INPUTS = ROOT / "docs/production-authoring-inputs.json.gz"


def historical_resources(folder: Path) -> Path:
    """Frozen pre-pruning contract fixture for compiler-invariant tests."""
    data = json.load(gzip.open(HISTORICAL_INPUTS, "rt", encoding="utf8"))
    folder.mkdir()
    (folder / "definitions.json").write_text(json.dumps(data["definitions"]), encoding="utf8")
    (folder / "contracts-v2.json").write_text(json.dumps(data["contracts"]), encoding="utf8")
    (folder / "transform-v2.json").write_bytes((ROOT / "src/main/resources/bloodborne_blocks/logical/transform-v2.json").read_bytes())
    return folder


def state(name):
    return name, ()


def transaction(number=1, second_offset=(1, 0, 0)):
    source = Expected((0, 0, 0), state("minecraft:stone"))
    member = Expected((1, 0, 0), state("minecraft:dirt"))
    first = Output(state(NS + "o_split_a"), (0, 0, 0), frozenset({(0, 0, 0)}))
    second = Output(state(NS + "o_split_b"), second_offset, frozenset({(0, 0, 0)}))
    return Rule(number, source, first.target, first.root_offset, (member,), None, first.shape,
                transaction_id="test-split", outputs=(first, second))


class SplitTransactionTests(unittest.TestCase):
    def world(self, base, blocks):
        source = base / "world"
        source.mkdir(parents=True)
        # The two raw source cells deliberately straddle chunks 0 and 1.
        write_chunk(source / "region/r.0.0.mca", 0, 0, {p: v for p, v in blocks.items() if p[0] < 16})
        write_chunk(source / "region/r.0.0.mca", 1, 0, {p: v for p, v in blocks.items() if p[0] >= 16})
        return World(source, {})

    @staticmethod
    def _set_section_y(path, cx, section_y):
        region = RegionFile.open(path)
        stored = region.get_chunk(cx & 31, 0)
        nbt = stored.nbt()
        compound(compound(nbt.root)["sections"].value[0])["Y"] = Tag(TAG_BYTE, section_y)
        region.set_chunk(cx & 31, 0, nbt, timestamp=stored.timestamp)
        region.save(path)

    def world_at_y(self, base, blocks, y, *, dimension=DIM):
        source = base / "world"
        source.mkdir(parents=True)
        relative = "region" if dimension == DIM else "dimensions/example/void/region"
        path = source / relative / "r.0.0.mca"
        write_chunk(path, 0, 0, {p: v for p, v in blocks.items() if p[0] < 16})
        write_chunk(path, 1, 0, {p: v for p, v in blocks.items() if p[0] >= 16})
        self._set_section_y(path, 0, y // 16)
        self._set_section_y(path, 1, y // 16)
        return source, World(source, {})

    def test_cross_chunk_outputs_are_one_atomic_ledger_entry_and_idempotent(self):
        with tempfile.TemporaryDirectory() as folder:
            world = self.world(Path(folder), {(15, 64, 0): ("minecraft:stone", {}),
                                              (16, 64, 0): ("minecraft:dirt", {})})
            items, _, _ = candidates(world, [transaction()])
            self.assertEqual(1, len(items))  # both inverse cells discover one transaction
            reject_overlaps(items)
            self.assertIsNone(items[0].reason)
            ledger = apply(world, items)
            self.assertEqual(1, len(ledger))
            self.assertEqual("test-split", ledger[0]["transaction"])
            self.assertEqual(2, len(ledger[0]["source"]))
            self.assertEqual(state(NS + "o_split_a"), world.get(DIM, (15, 64, 0)))
            self.assertEqual(state(NS + "o_split_b"), world.get(DIM, (16, 64, 0)))
            world.save()
            again, _, _ = candidates(World(world.root, {}), [transaction()])
            self.assertEqual([], again)  # a persisted repeat has no raw source left

    def test_blocked_member_rejects_every_output_without_writing(self):
        with tempfile.TemporaryDirectory() as folder:
            world = self.world(Path(folder), {(15, 64, 0): ("minecraft:stone", {}),
                                              (16, 64, 0): ("minecraft:dirt", {}),
                                              (17, 64, 0): ("minecraft:gold_block", {})})
            items, _, _ = candidates(world, [transaction(second_offset=(2, 0, 0))])
            self.assertEqual("target_would_overwrite_foreign_block", items[0].reason)
            self.assertEqual([], apply(world, items))
            self.assertEqual(state("minecraft:stone"), world.get(DIM, (15, 64, 0)))
            self.assertEqual(state("minecraft:dirt"), world.get(DIM, (16, 64, 0)))

    def test_shared_source_is_rejected_without_an_explicit_single_transaction(self):
        with tempfile.TemporaryDirectory() as folder:
            world = self.world(Path(folder), {(15, 64, 0): ("minecraft:stone", {}),
                                              (16, 64, 0): ("minecraft:dirt", {})})
            items, _, _ = candidates(world, [transaction(1), transaction(2, (2, 0, 0))])
            reject_overlaps(items)
            self.assertTrue(all(item.reason == "ambiguous_overlap_or_double_consumption" for item in items))

    def test_build_height_rejects_overworld_top_and_bottom_before_any_write(self):
        with tempfile.TemporaryDirectory() as folder:
            _, top = self.world_at_y(Path(folder) / "top", {(15, 319, 0): ("minecraft:stone", {}),
                                                              (16, 319, 0): ("minecraft:dirt", {})}, 319)
            top_items, _, _ = candidates(top, [transaction(second_offset=(1, 1, 0))])
            self.assertEqual("target_outside_dimension_build_height", top_items[0].reason)
            _, bottom = self.world_at_y(Path(folder) / "bottom", {(15, -64, 0): ("minecraft:stone", {}),
                                                                    (16, -64, 0): ("minecraft:dirt", {})}, -64)
            bottom_items, _, _ = candidates(bottom, [transaction(second_offset=(1, -1, 0))])
            self.assertEqual("target_outside_dimension_build_height", bottom_items[0].reason)

    def test_custom_dimension_datapack_height_is_resolved_and_unknown_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source, world = self.world_at_y(root, {(15, 127, 0): ("minecraft:stone", {}),
                                                   (16, 127, 0): ("minecraft:dirt", {})}, 127,
                                           dimension="example:void")
            custom = source / "datapacks/test/data/example/dimension_type/void.json"
            custom.parent.mkdir(parents=True)
            custom.write_text(json.dumps({"min_y": 0, "height": 128}), encoding="utf-8")
            level = Tag(TAG_COMPOUND, {"Data": Tag(TAG_COMPOUND, {"WorldGenSettings": Tag(TAG_COMPOUND, {
                "dimensions": Tag(TAG_COMPOUND, {"example:void": Tag(TAG_COMPOUND, {
                    "type": Tag(TAG_STRING, "example:void")})})})})})
            write_nbt(source / "level.dat", NbtFile("", level))
            world = World(source, {})
            self.assertEqual((0, 128), world.build_height("example:void"))
            items, _, _ = candidates(world, [transaction(second_offset=(1, 1, 0))])
            self.assertEqual("target_outside_dimension_build_height", items[0].reason)
            _, unknown = self.world_at_y(root / "unknown", {(15, 64, 0): ("minecraft:stone", {}),
                                                              (16, 64, 0): ("minecraft:dirt", {})}, 64,
                                           dimension="example:void")
            missing = Tag(TAG_COMPOUND, {"Data": Tag(TAG_COMPOUND, {"WorldGenSettings": Tag(TAG_COMPOUND, {
                "dimensions": Tag(TAG_COMPOUND, {"example:void": Tag(TAG_COMPOUND, {
                    "type": Tag(TAG_STRING, "example:missing")})})})})})
            write_nbt(unknown.root / "level.dat", NbtFile("", missing))
            unknown = World(unknown.root, {})
            unknown_items, _, _ = candidates(unknown, [transaction()])
            self.assertEqual("unknown_dimension_build_height", unknown_items[0].reason)

    def test_contract_group_compiles_once_and_disabled_family_is_not_reintroduced(self):
        with tempfile.TemporaryDirectory() as folder:
            resources = historical_resources(Path(folder) / "resources")
            contract = json.loads((resources / "contracts-v2.json").read_text(encoding="utf-8"))
            families = {family["id"]: family for family in contract["families"]}
            left, right = families["o_c002"], families["o_c003"]
            left_state = next(state for state in left["states"].values() if state["migration_source_pattern"])
            right_state = next(state for state in right["states"].values() if state["migration_source_pattern"])
            shared = copy.deepcopy(left_state["migration_source_pattern"][0])
            transaction = {"id": "synthetic-split", "outputs": [
                {"family": "o_c002", "root_offset": [0, 0, 0]},
                {"family": "o_c003", "root_offset": [32, 0, 0]}]}
            left_state["migration_source_pattern"][0]["split_transaction"] = transaction
            shared["split_transaction"] = copy.deepcopy(transaction)
            right_state["migration_source_pattern"] = [shared]
            (resources / "contracts-v2.json").write_text(json.dumps(contract), encoding="utf-8")
            rules, _ = direct_rules(resources)
            grouped = [rule for rule in rules if rule.transaction_id == "synthetic-split"]
            self.assertEqual(1, len(grouped))
            self.assertEqual([NS + "o_c002", NS + "o_c003"], [output.target[0] for output in grouped[0].outputs])
            left["migration_disabled"] = True
            (resources / "contracts-v2.json").write_text(json.dumps(contract), encoding="utf-8")
            disabled, _ = direct_rules(resources)
            self.assertNotIn(NS + "o_c002", {rule.target[0] for rule in disabled})

    def test_split_pattern_requires_one_member_per_unique_output_family(self):
        with tempfile.TemporaryDirectory() as folder:
            resources = historical_resources(Path(folder) / "resources")
            contract = json.loads((resources / "contracts-v2.json").read_text(encoding="utf-8"))
            families = {family["id"]: family for family in contract["families"]}
            left, right = families["o_c002"], families["o_c003"]
            left_state = next(state for state in left["states"].values() if state["migration_source_pattern"])
            right_state = next(state for state in right["states"].values() if state["migration_source_pattern"])
            transaction = {"id": "repeated-family-split", "outputs": [
                {"family": "o_c002", "root_offset": [0, 0, 0]},
                {"family": "o_c003", "root_offset": [32, 0, 0]},
                {"family": "o_c002", "root_offset": [64, 0, 0]}]}
            left_state["migration_source_pattern"][0]["split_transaction"] = transaction
            shared = copy.deepcopy(left_state["migration_source_pattern"][0])
            shared["split_transaction"] = copy.deepcopy(transaction)
            right_state["migration_source_pattern"] = [shared]
            (resources / "contracts-v2.json").write_text(json.dumps(contract), encoding="utf-8")
            compiled, _ = direct_rules(resources)
            self.assertEqual(3, len(next(rule for rule in compiled if rule.transaction_id == "repeated-family-split").outputs))
            duplicate_state = next(state for state in left["states"].values() if state is not left_state)
            duplicate_state["migration_source_pattern"] = [copy.deepcopy(left_state["migration_source_pattern"][0])]
            (resources / "contracts-v2.json").write_text(json.dumps(contract), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "exactly one shared source pattern"):
                load_contracts(resources)
            with self.assertRaisesRegex(ValueError, "exactly one shared source pattern"):
                direct_rules(resources)


if __name__ == "__main__":
    unittest.main()
