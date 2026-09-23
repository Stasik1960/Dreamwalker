"""Reviewed-batch Contract V2 migration fixtures; never reads or writes a city world."""
from __future__ import annotations

import json
import shutil
import tempfile
import unittest
from collections import Counter, defaultdict
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
UNCHANGED_FAMILIES = {"o_c001", "o_c003", "o_c046", "o_c474", "o_c561", "o_c1319"}
ALIASES = {'o_c561': 'o_c046'}
CONTEXT = {"o_c046": (1, 0, 0), "o_c1979": (-1, 1, 0)}
# Independent acceptance contract for reviewed atomic migrations.  Do not
# derive this from nightmare-qa-report.json: that report is useful metadata,
# but cannot prove that a generator did not drop both a rule and its report row.
EXPECTED_REVIEWED_OUTPUTS = {
    "o_c008": ("minecraft:waxed_cut_copper_stairs", 4,
                Counter({"o_c008_1": 1, "o_c008_2": 1, "o_c008_3": 2, "o_c008_4": 1, "o_c008_5": 1}), True),
    "o_c471": ("minecraft:cracked_nether_bricks", 2, Counter({"o_c471_a": 1, "o_c471_b": 1}), True),
    "o_c1962": ("minecraft:dead_bubble_coral_fan", 2, Counter({"o_c1962_a": 1, "o_c1962_b": 1}), True),
    "o_c1979": ("minecraft:red_nether_bricks", 2,
                 Counter({"o_c1979_1": 1, "o_c1979_2": 1, "o_c1979_3": 1, "o_c1979_4": 1, "o_c1979_5": 1}), True),
    "o_c282": ("minecraft:dark_oak_stairs", 4, Counter({"o_c282_a": 1, "o_c282_b": 1}), True),
    "o_c1491": ("minecraft:dark_oak_button", 1,
                 Counter({"o_c1491_a": 1, "o_c1491_b": 1, "o_c1491_c": 1}), True),
    "o_c654": ("minecraft:blackstone_stairs", 4, Counter({"o_c654_a": 1}), False),
}


def target_id(rule): return rule.target[0].split(":", 1)[1]
def target_ids(rule): return {output.target[0].split(":", 1)[1] for output in rule.outputs} if rule.outputs else {target_id(rule)}
def output_counts(rule): return Counter(output.target[0].split(":", 1)[1] for output in rule.outputs) if rule.outputs else Counter({target_id(rule): 1})
def outputs(rule, origin): return [(add(origin, output.root_offset), output.target, output.shape) for output in rule.outputs] if rule.outputs else [(add(origin, rule.root_offset), rule.target, rule.shape)]
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
    # Destination chunks must exist before conversion; explicit air is harmless
    # and makes wide/tall shapes exercise section and chunk boundaries.
    for root, _target, shape in outputs(rule, origin):
        for offset in shape: cells.setdefault(add(root, offset), ("minecraft:air", {}))
    retained = []
    if foreign:
        point = add(origin, (24, 2, 24))
        cells[point] = ("minecraft:chest", {"facing": "north", "type": "single", "waterlogged": "false"})
        retained.append(point)
    if target_id(rule) in CONTEXT:
        root = add(origin, rule.root_offset); point = add(root, CONTEXT[target_id(rule)])
        if any(point in {add(output_root, offset) for offset in shape} for output_root, _target, shape in outputs(rule, origin)): raise AssertionError("review context became a target write")
        cells[point] = ("minecraft:stone", {}); retained.append(point)
    return cells, retained


def tick(point):
    return Tag(TAG_COMPOUND, {"i": Tag(TAG_STRING, "minecraft:water"), "x": Tag(TAG_INT, point[0]), "y": Tag(TAG_INT, point[1]), "z": Tag(TAG_INT, point[2]), "t": Tag(TAG_INT, 17), "p": Tag(TAG_INT, 0)})


class ReviewedMigrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rules, cls.defaults = parse_rules(RES, "original-v2")
        report = json.loads((ROOT / "docs/nightmare-qa-report.json").read_text(encoding="utf8"))
        cls.expected = UNCHANGED_FAMILIES | {output for family in report["families"] for output in family["outputs"]}
        if report.get("c654", {}).get("b_manual_only"):
            cls.expected.discard("o_c654_b")
        grouped = defaultdict(list)
        for rule in cls.rules:
            if target_ids(rule) & (cls.expected | set(ALIASES.values())): grouped[rule.number].append(rule)
        cls.selected = [choose_rule(group, index) for index, group in enumerate(grouped.values())]
        cls.covered = {target for rule, _origin in cls.selected for target in target_ids(rule)}

    def test_all_reviewed_families_have_proven_rule_or_documented_alias(self):
        self.assertTrue(self.covered | set(ALIASES) >= self.expected)
        for alias, canonical in ALIASES.items():
            self.assertNotIn(alias, self.covered)
            self.assertIn(canonical, self.covered)

    def test_reviewed_atomic_output_contract_is_independent_of_generated_report(self):
        for old_id, (source_id, expected_count, expected_outputs, transactional) in EXPECTED_REVIEWED_OUTPUTS.items():
            rules = [rule for rule in self.rules
                     if rule.source.state[0] == source_id and bool(rule.transaction_id) == transactional
                     and (target_id(rule).startswith(old_id + "_") or old_id == "o_c654" and target_id(rule) == "o_c654_a")]
            self.assertEqual(expected_count, len(rules), old_id)
            for rule in rules:
                self.assertEqual(expected_outputs, output_counts(rule), (old_id, rule.number))
                self.assertEqual(transactional, bool(rule.outputs), (old_id, rule.number))

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
                    for output_root, target, _shape in outputs(rule, origin): self.assertEqual(world.get(DIM, output_root), target)
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
