"""Read exact reviewed source examples from the supplied ZIP and prove their v2 migration."""
from __future__ import annotations

import json
import tempfile
import unittest
import zipfile
from dataclasses import dataclass
from pathlib import Path

from check_logical_world import check
from convert_logical_world import AIR_NAME, World, convert, hash_tree, parse_rules, state_of
from reviewed_migration_fixture import write_fixture
from source_review_decisions import DECISIONS
from source_variant_rng import guards_match
from world_io import RegionFile, TAG_COMPOUND, TAG_LONG_ARRAY, Tag, compound, unpack_palette_indices

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources/bloodborne_blocks/logical"
SOURCE = ROOT / "reference-inputs/source-world.zip"
MANIFEST = ROOT / "docs/manual-review/source-assemblies/batch-02/batch-02-manifest.json"
EVIDENCE = ROOT / "build/test-results/reviewed-batch-02-source-examples.json"
DIM = "minecraft:overworld"
PROVEN_ALIASES = {"o_c009_a": "o_c001_a", "o_c009_b": "o_c001_b", "o_c561": "o_c046"}
# These are real reviewed placements whose destination footprint contains
# protected, non-logical world blocks.  Conversion must leave the complete
# candidate alone; they are evidence of a safe refusal, not a migrated case.
EXPECTED_SAFE_BLOCKED = {
    ("C282", "A"), ("C282", "B"), ("C282", "C"), ("C282", "D"),
    ("C654", "A"), ("C654", "B"), ("C654", "C"),
    ("C1491", "A"),
}


def state_key(state): return state[0], tuple(sorted(state[1]))
def add(origin, offset): return tuple(origin[index] + offset[index] for index in range(3))
def parts(rule): return rule.components if rule.components is not None else (rule.source,) + rule.members
def target_id(rule): return rule.target[0].split(":", 1)[1]


class SourceZip:
    """Targeted reader: each lookup reads only its one region member and chunk."""
    def __init__(self, path):
        from manual_review_world import _sha256, EXPECTED_SHA256
        self.sha256=_sha256(path)
        if self.sha256!=EXPECTED_SHA256:raise ValueError('Wrong original source-world.zip')
        self.archive = zipfile.ZipFile(path); self.regions = {}; self.used = set()
    def close(self): self.archive.close()
    def _region(self, dimension, x, z):
        cx, cz = x // 16, z // 16
        member = "ether/dimensions/" + dimension.replace(":", "/") + f"/region/r.{cx // 32}.{cz // 32}.mca"
        if member not in self.regions:
            self.regions[member] = RegionFile(self.archive.read(member)); self.used.add(member)
        return self.regions[member], cx, cz
    def state(self, dimension, point):
        region, cx, cz = self._region(dimension, point[0], point[2])
        chunk = region.get_chunk(cx % 32, cz % 32)
        if chunk is None: raise AssertionError("review example has no source chunk: " + repr(point))
        root = compound(chunk.nbt().root)
        section = next((entry for entry in root["sections"].value if int(compound(entry)["Y"].value) == point[1] // 16), None)
        if section is None: return AIR_NAME, ()
        block_states = compound(section)["block_states"].value; palette = block_states["palette"].value
        packed = block_states.get("data", Tag(TAG_LONG_ARRAY, [])).value
        index = ((point[1] & 15) * 256 + (point[2] & 15) * 16 + (point[0] & 15))
        return state_of(palette[unpack_palette_indices(packed, len(palette))[index]], {})


@dataclass(frozen=True)
class Example:
    review_id: str
    family: str
    pattern: str
    dimension: str
    anchor: tuple[int, int, int]
    selected: tuple[tuple[tuple[int, int, int], tuple[str, tuple[tuple[str, str], ...]]], ...]
    context: tuple[tuple[tuple[int, int, int], tuple[str, tuple[tuple[str, str], ...]]], ...]
    protected: tuple = ()
    siblings: tuple = ()


def family_id(review_id, split_index): return "o_" + review_id.lower() + ("_" + chr(ord("a") + split_index) if DECISIONS[review_id]["kind"] == "SPLIT" else "")


def examples(reader):
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8")); rows = {row["review_id"]: row for row in manifest["candidates"]}
    result = []
    for review_id, decision in DECISIONS.items():
        if review_id in ('C001', 'C009'):
            # Historical six-cell candidates contain wings of TWO neighboring
            # trees. QA2's separate 16-cell fixtures exercise the corrected boundary.
            continue
        row = rows[review_id]
        for pattern_index, pattern in enumerate(row["source_patterns"]):
            anchor = tuple(pattern["example"]["anchor"]); dimension = pattern["example"]["dimension"]
            actual_components = {}
            for component in pattern["components"]:
                point = add(anchor, component["relative"]); actual = reader.state(dimension, point)
                expected = (component["source"]["id"], tuple(sorted(component["source"]["properties"].items())))
                if actual != expected: raise AssertionError(f"{review_id}/{pattern_index} source mismatch at {point}: {actual} != {expected}")
                actual_components[component["number"]] = (point, actual)
            actual_context = []
            for context in pattern.get("context", []):
                point = add(anchor, context["relative"]); actual = reader.state(dimension, point)
                expected = (context["source"]["id"], tuple(sorted(context["source"]["properties"].items())))
                if actual != expected: raise AssertionError(f"{review_id}/{pattern_index} context mismatch at {point}: {actual} != {expected}")
                actual_context.append((point, actual))
            for split_index, group in enumerate(decision["groups"]):
                protected=tuple(actual_components[number] for number in decision.get('context',[]))
                sibling_numbers={n for g in decision['groups'] for n in g}-set(group)
                result.append(Example(review_id, family_id(review_id, split_index), str(pattern.get("pattern_id", pattern_index)), dimension, anchor,
                                      tuple(actual_components[number] for number in group), tuple(actual_context), protected,
                                      tuple(actual_components[n] for n in sorted(sibling_numbers))))
    return result


def matching_rule(rules, example):
    selected = dict(example.selected)
    matches = []
    for rule in rules:
        for part in parts(rule):
            for point, state in example.selected:
                if part.state != state: continue
                origin = tuple(point[index] - part.offset[index] for index in range(3))
                if len(parts(rule)) != len(selected): continue
                if {add(origin, value.offset): value.state for value in parts(rule)} != selected: continue
                if rule.variant_guards and not guards_match(rule.variant_guards, origin): continue
                matches.append((rule, origin))
    unique = {(rule.number, origin): (rule, origin) for rule, origin in matches}
    if len(unique) != 1: raise AssertionError(f"{example.family}/{example.pattern} has {len(unique)} eligible direct rules")
    return next(iter(unique.values()))


class ReviewedSourceExamples(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rules, cls.defaults = parse_rules(RES, "original-v2")
        report_path = ROOT / "docs/nightmare-qa-report.json"
        report = json.loads(report_path.read_text(encoding="utf8")) if report_path.is_file() else {"families": []}
        cls.replacements = {row["old_id"]: set(row["outputs"]) for row in report["families"]}
        if report.get("c654", {}).get("b_manual_only"):
            cls.replacements["o_c654"] = {report["c654"]["replacement"]}
        cls.reader = SourceZip(SOURCE); cls.examples = examples(cls.reader); cls.records = []
    @classmethod
    def tearDownClass(cls): cls.reader.close()

    def test_exact_source_examples_convert_without_context_effects(self):
        failures = []
        with tempfile.TemporaryDirectory(prefix="reviewed-source-examples-") as temporary:
            base = Path(temporary)
            for index, example in enumerate(self.examples):
                try:
                    rule, origin = matching_rule(self.rules, example)
                    cells = {point: (state[0], dict(state[1])) for point, state in example.selected + example.context + example.protected + example.siblings}
                    roots = [(add(origin, output.root_offset), output.shape) for output in rule.outputs] if rule.outputs else [(add(origin, rule.root_offset), rule.shape)]
                    for root, shape in roots:
                        for offset in shape:
                            # Only the reviewed source cells/context are copied
                            # from the original world.  Destination-only cells
                            # must be air in this synthetic exact-pattern test.
                            cells.setdefault(add(root,offset),("minecraft:air",{}))
                    primary_root = roots[0][0]
                    source, output, reports = base / f"source-{index}", base / f"output-{index}", base / "reports"
                    write_fixture(source, cells, floor=False)
                    report_path = reports / f"{index}.json"
                    report = convert(source, output, resources=RES, report_path=report_path, report_root=reports, source_mode="original-v2")
                    blocked = (example.review_id, example.pattern) in EXPECTED_SAFE_BLOCKED
                    if blocked:
                        self.assertEqual(0, report["counts"]["converted"], report["ledger"])
                        self.assertFalse(report["ledger"])
                        rejected = [entry for entry in report["rejected"] if entry["rule"] == rule.number and tuple(entry["origin"]) == origin]
                        self.assertEqual(["target_would_overwrite_foreign_block"], [entry["reason"] for entry in rejected])
                        world = World(output, self.defaults)
                        conflicts = []
                        source_points = {point for point, _state in example.selected}
                        for output_root, _target, shape in ([(add(origin, output.root_offset), output.target, output.shape) for output in rule.outputs] if rule.outputs else [(primary_root, rule.target, rule.shape)]):
                            for offset in shape:
                                point = add(output_root, offset)
                                state = world.get(DIM, point)
                                if point not in source_points and state[0] != AIR_NAME:
                                    conflicts.append({"position": list(point), "state": [state[0], dict(state[1])]})
                        self.assertTrue(conflicts, "blocked reviewed placement has no foreign destination state")
                        for point, state in example.selected + example.context + example.protected + example.siblings:
                            self.assertEqual(world.get(DIM, point), state, point)
                        self.assertEqual(hash_tree(source), hash_tree(output), "safe refusal changed the fixture")
                        status, detail = "blocked", {"reason": "target_would_overwrite_foreign_block", "rule": rule.number,
                                                     "origin": list(origin), "conflicts": conflicts}
                        self.records.append({"review_id": example.review_id, "family": example.family, "pattern": example.pattern,
                                             "anchor": list(example.anchor), "selectedCellCount": len(example.selected),
                                             "contextCellCount": len(example.context), "status": status, **detail})
                        continue
                    actual_target = target_id(rule); expected = PROVEN_ALIASES.get(example.family, example.family)
                    old_id = next((candidate for candidate in self.replacements if expected == candidate or expected.startswith(candidate + "_")), expected)
                    expected_targets = self.replacements.get(old_id, {expected})
                    if actual_target not in expected_targets or not any(e['rule']==rule.number and tuple(e['targetRoot'])==primary_root for e in report['ledger']):
                        raise AssertionError(json.dumps({"expected": expected, "target": actual_target,
                                                         "counts": report["counts"],
                                                         "convertedRules": [entry["rule"] for entry in report["ledger"]],
                                                         "rejectionReasons": sorted({entry["reason"] for entry in report["rejected"]})}))
                    world = World(output, self.defaults)
                    if any(world.get(DIM, output_root) != target for output_root, target, _shape in ([(add(origin, output.root_offset), output.target, output.shape) for output in rule.outputs] if rule.outputs else [(primary_root, rule.target, rule.shape)])):
                        raise AssertionError(json.dumps({"targetRoot": list(primary_root), "actual": world.get(DIM, primary_root),
                                                         "convertedRules": [entry["rule"] for entry in report["ledger"]]}))
                    target_entry=next(e for e in report['ledger'] if e['rule']==rule.number and tuple(e['targetRoot'])==primary_root)
                    touched={tuple(e['position']) for e in target_entry['changes']}
                    protected={point for point,_state in example.protected}
                    if any(protected & {tuple(e['position']) for e in entry['changes']} for entry in report['ledger']):raise AssertionError('numbered user CONTEXT touched')
                    for point,state in example.protected:
                        if world.get(DIM,point)!=state:raise AssertionError('numbered CONTEXT state changed')
                    if example.siblings:
                        sibling_set={point for point,_ in example.siblings}
                        sibling_entries=[e for e in report['ledger'] if set(map(tuple,e['source']))==sibling_set]
                        if len(sibling_entries)!=1:raise AssertionError('co-located SPLIT sibling not converted exactly once')
                    changed_context = []
                    for point, state in example.context:
                        if point in touched:raise AssertionError('preview context included in target object')
                        if world.get(DIM, point) != state:
                            independent=[e for e in report['ledger'] if point in set(map(tuple,e['source'])) and not touched & set(map(tuple,e['source']))]
                            if len(independent)!=1:changed_context.append(list(point))
                    if changed_context: raise AssertionError(json.dumps({"contextChanged": changed_context}))
                    check(source, output, report_path, RES)
                    status, detail = "passed", {"target": actual_target, "rule": rule.number, "origin": list(origin), "variantGuards": list(rule.variant_guards),
                        'protectedNumberedContext':len(protected),'coLocatedSplitSibling':bool(example.siblings),'independentlyConvertedObjects':report['counts']['converted']-1}
                except Exception as error:
                    status, detail = "failed", {"error": str(error)}; failures.append(f"{example.family}/{example.pattern}: {error}")
                self.records.append({"review_id": example.review_id, "family": example.family, "pattern": example.pattern,
                                     "anchor": list(example.anchor), "selectedCellCount": len(example.selected),
                                     "contextCellCount": len(example.context), "status": status, **detail})
        evidence = {"schemaVersion": 1, "source": {"path": "reference-inputs/source-world.zip", 'sha256':self.reader.sha256,"readRegionMembers": sorted(self.reader.used)}, "cases": self.records}
        EVIDENCE.parent.mkdir(parents=True,exist_ok=True)
        EVIDENCE.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        self.assertFalse(failures, "\n".join(failures))


if __name__ == "__main__": unittest.main()
