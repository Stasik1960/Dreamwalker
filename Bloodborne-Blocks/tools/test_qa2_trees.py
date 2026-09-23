"""Whole-tree QA2 regression: exact raw assemblies, never a city conversion."""
import copy
import gzip
import hashlib
import json
import tempfile
import unittest
from collections import defaultdict
from pathlib import Path

from build_qa2_trees import AUTHORING, COLLISION, SELECTION, RETIRED, TREE_ID, build
from check_contract_orientation import mesh_tokens
from check_logical_world import check
from compile_reviewed_migration import choices, identified
from convert_logical_world import World, convert, parse_rules, hash_tree, PART
from logical_contract_v2 import load_contracts, master_origin
from reviewed_migration_fixture import write_fixture
from source_assembly_visuals import source_polys
from source_variant_rng import guards_match
from test_reviewed_source_examples import SourceZip, SOURCE, add, parts, target_id
from test_reviewed_migration import choose_rule, cells_for

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources/bloodborne_blocks/logical'
DIM = 'minecraft:overworld'


class WholeTreeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.authored = json.loads(AUTHORING.read_text(encoding='utf8'))
        cls.contracts, cls.transform = load_contracts(RES)
        cls.family = next(f for f in cls.contracts['families'] if f['id'] == TREE_ID)
        cls.definitions = {d['id']: d for d in json.loads((RES / 'definitions.json').read_text(encoding='utf8'))['blocks']}
        with gzip.open(RES / 'meshes.json.gz', 'rt', encoding='utf8') as stream:
            cls.meshes = json.load(stream)
        cls.rules, cls.defaults = parse_rules(RES, 'original-v2')
        cls.tree_rules = [r for r in cls.rules if target_id(r) == TREE_ID]
        cls.records = []

    def test_one_active_item_with_compatibility_inventory_redirects(self):
        aliases = json.loads((RES / 'migration.json').read_text(encoding='utf8'))['item_aliases']
        hidden = set(json.loads((RES / 'hidden-items.json').read_text(encoding='utf8')))
        self.assertNotIn(TREE_ID, hidden)
        for ident in RETIRED:
            self.assertIn(ident, hidden)
            self.assertEqual(aliases[ident]['id'], TREE_ID)
            self.assertIn(ident, self.definitions)  # never erase old saves' registry IDs
        palette_aliases = json.loads((RES.parent / 'aliases.json').read_text(encoding='utf8'))['aliases']
        self.assertFalse(set(RETIRED) & set(palette_aliases), 'no per-wing automatic world alias')
        self.assertFalse(set(RETIRED) & {target_id(r) for r in self.rules})

    def test_complete_mesh_contains_all_sixteen_sources_for_every_weighted_choice(self):
        actual = {tuple(sorted(mesh_tokens(identified(self.meshes[s['render_mesh']['id']]['polygons']), 0, self.transform).items()))
                  for s in self.family['states'].values() if s['rotation'] == 0}
        expected = set()
        for pattern in self.authored['patterns']:
            self.assertEqual(len(pattern['components']), 16)
            self.assertEqual(len({tuple(p['offset']) for p in pattern['components']}), 16)
            for selection in choices(pattern['components']):
                mesh = source_polys([app for group in selection for app in group])[0]
                expected.add(tuple(sorted(mesh_tokens(identified(mesh), 0, self.transform).items())))
        self.assertEqual(actual, expected)
        self.assertEqual(len(actual), 6)
        self.assertEqual(len(self.tree_rules), 6)

    def test_fixed_base_rotations_trunk_only_and_no_visual_helpers(self):
        definition = self.definitions[TREE_ID]
        self.assertEqual(definition['properties']['facing'], ['north', 'east', 'south', 'west'])
        self.assertEqual(self.family['canonical_anchor'], {'cell': [0, 0, 0], 'pivot': [.5, 0, .5]})
        self.assertEqual(self.family['collision_policy'], 'TRUNK')
        for yaw in (0, 90, 180, 270):
            self.assertEqual(master_origin((103, 70, -29), [0, 0, 0], yaw, self.transform), (103, 70, -29))
        for state in self.family['states'].values():
            self.assertEqual(state['collision_footprint']['boxes'], [COLLISION])
            self.assertEqual(state['selection_footprint']['boxes'], [SELECTION])
            self.assertEqual(state['interaction_footprint']['cells'], [[0, y, 0] for y in range(10)])
            self.assertEqual(state['render_mesh']['bounds'][1], 0)
            self.assertEqual(state['render_mesh']['bounds'][4], 12)
            self.assertEqual(state['render_mesh']['offset'], [0, 0, 0])

    def test_every_complete_rule_is_atomic_and_idempotent(self):
        with tempfile.TemporaryDirectory(prefix='qa2-tree-rules-') as temp:
            base = Path(temp)
            for index, rule in enumerate(self.tree_rules):
                _, origin = choose_rule([rule], index)
                cells, _ = cells_for(rule, origin, foreign=False)
                foreign = add(origin, (3, 2, 0))  # visual branch area, not a carrier/helper
                cells[foreign] = ('minecraft:stone', {})
                source, out = base / f's{index}', base / f'o{index}'
                write_fixture(source, cells, floor=False)
                before = hash_tree(source)
                report_path = base / f'{index}.json'
                report = convert(source, out, resources=RES, report_path=report_path, report_root=base, source_mode='original-v2')
                self.assertEqual(report['counts']['converted'], 1, report['rejected'])
                self.assertEqual(before, hash_tree(source))
                world = World(out, self.defaults)
                self.assertEqual(world.get(DIM, origin), rule.target)
                self.assertEqual(world.get(DIM, foreign)[0], 'minecraft:stone')
                for p in parts(rule):
                    if p.offset not in rule.shape:
                        self.assertEqual(world.get(DIM, add(origin, p.offset))[0], 'minecraft:air')
                check(source, out, report_path, RES)
                repeat = convert(out, base / f'r{index}', resources=RES, report_path=base / f'r{index}.json', report_root=base, source_mode='original-v2')
                self.assertEqual(repeat['counts']['converted'], 0)
                self.records.append({'case': 'exact_rule', 'rule': rule.number, 'status': 'passed'})

    def test_partial_and_foreign_trunk_fail_closed(self):
        rule, origin = choose_rule([self.tree_rules[0]], 0)
        for mode in ('missing_wing', 'missing_trunk', 'foreign_trunk', 'wings_only'):
            with tempfile.TemporaryDirectory(prefix='qa2-tree-negative-') as temp:
                base = Path(temp)
                cells, _ = cells_for(rule, origin, foreign=False)
                if mode == 'missing_wing': cells[add(origin, parts(rule)[-1].offset)] = ('minecraft:air', {})
                if mode == 'missing_trunk': cells[add(origin, parts(rule)[0].offset)] = ('minecraft:air', {})
                if mode == 'foreign_trunk': cells[add(origin, (0, 2, 0))] = ('minecraft:stone', {})
                if mode == 'wings_only':
                    for p in parts(rule):
                        if p.offset[0] == p.offset[2] == 0: cells[add(origin, p.offset)] = ('minecraft:air', {})
                write_fixture(base / 's', cells, floor=False)
                report = convert(base / 's', base / 'o', resources=RES, report_path=base / 'report.json', report_root=base, source_mode='original-v2')
                self.assertEqual(report['counts']['converted'], 0, (mode, report))
                self.assertEqual(hash_tree(base / 's'), hash_tree(base / 'o'))

    def test_eight_real_saved_trees_include_neighbor_context_without_merging(self):
        reader = SourceZip(SOURCE)
        try:
            with tempfile.TemporaryDirectory(prefix='qa2-tree-examples-') as temp:
                base = Path(temp)
                all_examples = [(p, e) for p in self.authored['patterns'] for e in p['examples']]
                self.assertEqual(len(all_examples), 8)
                for index, (pattern, example) in enumerate(all_examples):
                    origin, dimension = tuple(example['master']), example['dimension']
                    cells = {}
                    for p in pattern['components']:
                        pos = add(origin, p['offset']); actual = reader.state(dimension, pos)
                        self.assertEqual(actual, (p['id'], tuple(sorted(p['properties'].items()))))
                        cells[pos] = (actual[0], dict(actual[1]))
                    matching = [r for r in self.tree_rules if
                                {add(origin, p.offset): (p.state[0], dict(p.state[1])) for p in parts(r)} == cells and
                                guards_match(r.variant_guards, origin)]
                    self.assertEqual(len(matching), 1)
                    rule = matching[0]
                    # Preserve actual context, including the neighboring tree,
                    # in a bounded box. It may convert independently, never as
                    # extra members of this tree or get erased with its wings.
                    for y in range(12):
                        for x in range(-5, 6):
                            for z in range(-5, 6):
                                pos = add(origin, (x, y, z))
                                if pos in cells: continue
                                # Only destinations and nearby carriers are needed;
                                # retain a full central line plus selected neighboring
                                # tree cells as foreign evidence without city extraction.
                                if x == z == 0 or (y in (1, 4, 7, 10) and abs(x) + abs(z) in (3, 4, 5)):
                                    actual = reader.state(dimension, pos)
                                    cells[pos] = (actual[0], dict(actual[1]))
                    source, out, path = base / f's{index}', base / f'o{index}', base / f'{index}.json'
                    write_fixture(source, cells, floor=False)
                    report = convert(source, out, resources=RES, report_path=path, report_root=base, source_mode='original-v2')
                    entry = next((e for e in report['ledger'] if e['rule'] == rule.number and tuple(e['targetRoot']) == origin), None)
                    self.assertIsNotNone(entry, report['rejected'])
                    self.assertEqual(len(entry['source']), 16)
                    self.assertEqual(World(out, self.defaults).get(DIM, origin), rule.target)
                    check(source, out, path, RES)
                    self.records.append({'case': 'real_example', 'review_id': example['review_id'],
                                         'review_pattern': example['review_pattern'], 'master': list(origin), 'status': 'passed'})
        finally:
            reader.close()

    def test_saved_context_tree_pairs_convert_independently(self):
        reader = SourceZip(SOURCE)
        try:
            with tempfile.TemporaryDirectory(prefix='qa2-tree-pairs-') as temp:
                base = Path(temp)
                pairs = defaultdict(list)
                for pattern in self.authored['patterns']:
                    for example in pattern['examples']:
                        pairs[(example['review_id'], example['review_pattern'])].append((pattern, example))
                self.assertEqual(set(pairs), {('C001', 'A'), ('C001', 'B'), ('C001', 'C'), ('C009', 'A')})
                self.assertTrue(all(len(pair) == 2 for pair in pairs.values()))
                for index, ((review_id, review_pattern), pair) in enumerate(sorted(pairs.items())):
                    cells, expected = {}, []
                    for pattern, example in pair:
                        origin, dimension = tuple(example['master']), example['dimension']
                        tree_cells = {}
                        for component in pattern['components']:
                            point = add(origin, component['offset'])
                            actual = reader.state(dimension, point)
                            self.assertEqual(actual, (component['id'], tuple(sorted(component['properties'].items()))))
                            tree_cells[point] = (actual[0], dict(actual[1]))
                        self.assertFalse(set(cells) & set(tree_cells), 'saved trees share source cells')
                        cells.update(tree_cells)
                        matching = [rule for rule in self.tree_rules
                                    if {add(origin, part.offset): (part.state[0], dict(part.state[1])) for part in parts(rule)} == tree_cells
                                    and guards_match(rule.variant_guards, origin)]
                        self.assertEqual(len(matching), 1)
                        expected.append((matching[0], origin))
                    source, out, report_path = base / f's{index}', base / f'o{index}', base / f'{index}.json'
                    write_fixture(source, cells, floor=False)
                    report = convert(source, out, resources=RES, report_path=report_path, report_root=base, source_mode='original-v2')
                    self.assertEqual(report['counts']['converted'], 2, report['rejected'])
                    entries = []
                    for rule, origin in expected:
                        entry = next((entry for entry in report['ledger']
                                      if entry['rule'] == rule.number and tuple(entry['targetRoot']) == origin), None)
                        self.assertIsNotNone(entry, report['ledger'])
                        self.assertEqual(len(entry['source']), 16)
                        entries.append(entry)
                        self.assertEqual(World(out, self.defaults).get(DIM, origin), rule.target)
                    self.assertNotEqual(tuple(entries[0]['targetRoot']), tuple(entries[1]['targetRoot']))
                    self.assertFalse(set(map(tuple, entries[0]['source'])) & set(map(tuple, entries[1]['source'])))
                    check(source, out, report_path, RES)
                    self.records.append({'case': 'saved_context_pair', 'review_id': review_id,
                                         'review_pattern': review_pattern, 'status': 'passed'})
        finally:
            reader.close()

    def test_compiler_is_idempotent(self):
        names = ('definitions.json', 'contracts-v2.json', 'geometry.json', 'meshes.json.gz', 'hidden-items.json', 'migration.json')
        assets = RES.parents[1] / 'assets/bloodborne_blocks'
        generated = [assets / 'blockstates/o_c001.json', assets / 'models/item/o_c001.json',
                     RES.parents[1] / 'data/bloodborne_blocks/loot_tables/blocks/o_c001.json',
                     assets / 'lang/en_us.json', assets / 'lang/ru_ru.json',
                     ROOT / 'docs/reviewed-batch-02-qa2-tree-evidence.json']
        models = sorted((assets / 'models/block/logical').glob('qa2_complete_tree_*.json'))
        self.assertEqual(len(models), 24)
        snapshots = [RES / name for name in names] + generated + models
        before = {path: hashlib.sha256(path.read_bytes()).hexdigest() for path in snapshots}
        build()
        self.assertEqual(before, {path: hashlib.sha256(path.read_bytes()).hexdigest() for path in snapshots})

    @classmethod
    def tearDownClass(cls):
        target = ROOT / 'build/test-results/qa2-trees.json'
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps({'cases': cls.records, 'scope': 'synthetic copies and eight bounded source examples; no city conversion'}, indent=2) + '\n', encoding='utf8')


if __name__ == '__main__':
    unittest.main()
