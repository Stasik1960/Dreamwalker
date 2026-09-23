"""Reviewed-batch Contract V2 migration fixtures; never reads or writes a city world."""
from __future__ import annotations

import json
import shutil
import tempfile
import unittest
from collections import defaultdict
from pathlib import Path

from check_logical_world import check
from convert_logical_world import PART, World, convert, hash_tree, parse_rules
from reviewed_migration_fixture import write_fixture
from source_variant_rng import guards_match
from test_logical_world import entity
from world_io import TAG_COMPOUND, TAG_INT, TAG_STRING, Tag, compound

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources/bloodborne_blocks/logical"
BUILD = ROOT / "build/reviewed-batch-02"
DIM = "minecraft:overworld"
FAMILIES = {"o_c001", "o_c002", "o_c003", "o_c008", "o_c471", "o_c046", "o_c1680", "o_c474", "o_c1962", "o_c1979", "o_c028", "o_c282", "o_c561", "o_c618", "o_c654", "o_c1319", "o_c1491"}
ALIASES = {'o_c561': 'o_c046'}
CONTEXT = {"o_c046": (1, 0, 0), "o_c1979": (-1, 1, 0)}


def target_id(rule): return rule.target[0].split(":", 1)[1]
def parts(rule): return rule.components if rule.components is not None else (rule.source,) + rule.members
def add(origin, offset): return tuple(origin[index] + offset[index] for index in range(3))
def rule_source_key(rule): return target_id(rule), tuple(sorted((part.offset, part.state) for part in parts(rule)))


def choose_rule(group, serial):
    """Select a real positional-RNG outcome, not an arbitrary weighted choice."""
    base_x, base_z = 14 + (serial % 4) * 48, 14 + (serial // 4) * 48
    for step in range(65536):
        origin = (base_x + (step & 63), 63, base_z + (step >> 6))
        for rule in group:
            if not rule.variant_guards or guards_match(rule.variant_guards, origin): return rule, origin
    raise AssertionError("no guard-satisfying origin for reviewed source pattern")


def cells_for(rule, origin, foreign=True):
    cells = {add(origin, part.offset): (part.state[0], dict(part.state[1])) for part in parts(rule)}
    root = add(origin, rule.root_offset)
    # Destination chunks must exist before conversion; explicit air is harmless
    # and makes wide/tall shapes exercise section and chunk boundaries.
    for offset in rule.shape: cells.setdefault(add(root, offset), ("minecraft:air", {}))
    retained = []
    if foreign:
        point = add(origin, (24, 2, 24))
        cells[point] = ("minecraft:chest", {"facing": "north", "type": "single", "waterlogged": "false"})
        retained.append(point)
    if target_id(rule) in CONTEXT:
        point = add(root, CONTEXT[target_id(rule)])
        if point in {add(root, offset) for offset in rule.shape}: raise AssertionError("review context became a target write")
        cells[point] = ("minecraft:stone", {}); retained.append(point)
    return cells, retained


def tick(point):
    return Tag(TAG_COMPOUND, {"i": Tag(TAG_STRING, "minecraft:water"), "x": Tag(TAG_INT, point[0]), "y": Tag(TAG_INT, point[1]), "z": Tag(TAG_INT, point[2]), "t": Tag(TAG_INT, 17), "p": Tag(TAG_INT, 0)})


class ReviewedMigrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        rules, cls.defaults = parse_rules(RES, "original-v2")
        grouped = defaultdict(list)
        for rule in rules:
            if target_id(rule) in FAMILIES | set(ALIASES.values()): grouped[rule.number].append(rule)
        cls.selected = [choose_rule(group, index) for index, group in enumerate(grouped.values())]
        cls.covered = {target_id(rule) for rule, _origin in cls.selected}

    def test_all_reviewed_families_have_proven_rule_or_documented_alias(self):
        self.assertTrue(self.covered | set(ALIASES) >= FAMILIES)
        for alias, canonical in ALIASES.items():
            self.assertNotIn(alias, self.covered)
            self.assertIn(canonical, self.covered)

    def test_each_proven_pattern_converts_independently_and_idempotently(self):
        with tempfile.TemporaryDirectory(prefix="reviewed-batch-02-") as temporary:
            base = Path(temporary)
            for index, (rule, origin) in enumerate(self.selected):
                source, output, reports = base / f"source-{index}", base / f"output-{index}", base / "reports"
                cells, retained = cells_for(rule, origin)
                foreign = retained[0] if retained else add(origin, (24, 2, 24))
                write_fixture(source, cells, block_entities=[entity(foreign, "minecraft:chest")], fluid_ticks=[tick(foreign)])
                if target_id(rule) == "o_c474":
                    source_world = World(source, self.defaults)
                    self.assertGreaterEqual(len(source_world.chunks), 2, "fixture crosses chunks")
                    source_chunk = source_world.chunk(DIM, origin[0], origin[2])
                    sections = {int(compound(section)["Y"].value) for section in source_chunk.root()["sections"].value}
                    self.assertTrue({3, 4}.issubset(sections), "tall post crosses Anvil sections")
                before = hash_tree(source)
                report_path = reports / f"{index}.json"
                report = convert(source, output, resources=RES, report_path=report_path, report_root=reports, source_mode="original-v2")
                self.assertEqual(report["counts"]["converted"], 1, (rule.number, report["rejected"]))
                self.assertEqual(hash_tree(source), before)
                world = World(output, self.defaults); root = add(origin, rule.root_offset)
                if report["counts"]["converted"]:
                    self.assertEqual(world.get(DIM, root), rule.target)
                for point in retained:
                    self.assertEqual(world.get(DIM, point)[0], "minecraft:stone" if point != foreign else "minecraft:chest")
                self.assertIn((DIM, *foreign), world.block_entities())
                self.assertTrue(world.ticks_at(DIM, {foreign}))
                check(source, output, report_path, RES)
                repeated = base / f"repeat-{index}"
                second = convert(output, repeated, resources=RES, report_path=reports / f"repeat-{index}.json", report_root=reports, source_mode="original-v2")
                self.assertEqual(second["counts"]["converted"], 0)
                self.assertEqual(hash_tree(output), hash_tree(repeated))

    def test_human_flat_fixture_and_combined_conflict_report(self):
        if BUILD.exists():
            if BUILD.resolve().parent != (ROOT/'build').resolve():raise ValueError('unsafe fixture output')
            shutil.rmtree(BUILD)
        source, output, reports = BUILD / "flat-source", BUILD / "migrated-copy", BUILD / "reports"
        cells = {}; expected = 0
        for rule, origin in self.selected:
            extra, _retained = cells_for(rule, origin, foreign=False)
            if set(cells).isdisjoint(extra): cells.update(extra); expected += 1
        write_fixture(source, cells)
        report = convert(source, output, resources=RES, report_path=reports / "flat.json", report_root=reports, source_mode="original-v2")
        self.assertGreater(report["counts"]["converted"], 0)
        self.assertLessEqual(report["counts"]["converted"], expected)
        self.assertFalse(any(item["reason"] == "ambiguous_overlap_or_double_consumption" for item in report["rejected"]), report["rejected"])
        check(source, output, reports / "flat.json", RES)
        (BUILD / "README.txt").write_text("Synthetic flat reviewed-batch fixture; no city data.\n", encoding="utf-8")


if __name__ == "__main__":
    unittest.main()
