from __future__ import annotations
import json
import tempfile
import unittest
from pathlib import Path
from convert_logical_world import PART, World, block_pos_long, convert, hash_tree, parse_old_logical_c654_rules, parse_rules
from reviewed_migration_fixture import write_fixture
from test_logical_world import entity
from world_io import TAG_LONG, TAG_STRING, Tag

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources/bloodborne_blocks/logical'
DIM = 'minecraft:overworld'

class OldLogicalC654Tests(unittest.TestCase):
 def rows(self):
  return [{'kind':'c654_to_a','source':{'id':'o_c654','properties':{'facing':facing,'variant':variant}},'target':{'id':'o_c654_a','properties':{'facing':facing,'variant':variant,'visual':'base'}},'root_offset':[0,0,0],'source_shape':[[0,0,0],[0,1,0]]} for facing in ('north','east','south','west') for variant in ('surface','source_depth')]
 def test_all_old_states_preserve_facing_variant_and_target_base(self):
  with tempfile.TemporaryDirectory() as folder:
   resources=Path(folder);(resources/'old-logical-migrations.json').write_text(json.dumps({'schemaVersion':1,'rules':self.rows()}),encoding='utf8')
   defaults={'bloodborne_blocks:o_c654':{},'bloodborne_blocks:o_c654_a':{'visual':'base'}}
   target=('bloodborne_blocks:o_c654_a',(('facing','north'),('variant','surface'),('visual','base')))
   geometry={target:{(0,0,0)}}
   # Supply every target signature, as real generated geometry does.
   for row in self.rows():
    props=tuple(sorted((str(k),str(v)) for k,v in row['target']['properties'].items()));geometry[('bloodborne_blocks:o_c654_a',props)]={(0,0,0)}
   rules=parse_old_logical_c654_rules(resources,defaults,geometry,71)
   self.assertEqual(8,len(rules));self.assertEqual(list(range(71,79)),[rule.number for rule in rules])
   for rule in rules:
    source=dict(rule.source.state[1]);target=dict(rule.target[1]);self.assertNotIn('visual',source);self.assertEqual(source['facing'],target['facing']);self.assertEqual(source['variant'],target['variant']);self.assertEqual('base',target['visual']);self.assertEqual(frozenset({(0,0,0),(0,1,0)}),rule.source.shape)
 def test_rejects_non_base_or_wrong_source_shape(self):
  with tempfile.TemporaryDirectory() as folder:
   resources=Path(folder);rows=self.rows();rows[0]['target']['properties']['visual']='alt';(resources/'old-logical-migrations.json').write_text(json.dumps({'schemaVersion':1,'rules':rows}),encoding='utf8')
   with self.assertRaises(ValueError):parse_old_logical_c654_rules(resources,{'bloodborne_blocks:o_c654':{},'bloodborne_blocks:o_c654_a':{}},{},0)

 def test_generated_manifest_converts_all_eight_old_states(self):
  rules, defaults = parse_rules(RES, 'legacy')
  rules = [rule for rule in rules if rule.source.state[0] == 'bloodborne_blocks:o_c654']
  self.assertEqual(8, len(rules))
  with tempfile.TemporaryDirectory(prefix='old-c654-positive-') as folder:
   source, output, reports = Path(folder) / 'source', Path(folder) / 'output', Path(folder) / 'reports'
   cells = {}
   roots = {}
   for index, rule in enumerate(rules):
    root = (index * 32, 64, 0); roots[rule.number] = root
    cells[root] = (rule.source.state[0], dict(rule.source.state[1]))
    for offset in rule.shape:
     cells.setdefault(tuple(root[axis] + offset[axis] for axis in range(3)), ('minecraft:air', {}))
   write_fixture(source, cells, floor=False)
   before = hash_tree(source)
   report = convert(source, output, resources=RES, report_path=reports / 'conversion.json', report_root=reports, source_mode='legacy')
   self.assertEqual(8, report['counts']['converted'], report['rejected'])
   self.assertEqual(before, hash_tree(source))
   world = World(output, defaults)
   for rule in rules:
    self.assertEqual(world.get(DIM, roots[rule.number]), rule.target)

 def test_stale_owned_and_foreign_destination_cells_reject_without_mutation(self):
  rules, defaults = parse_rules(RES, 'legacy')
  rules = [rule for rule in rules if rule.source.state[0] == 'bloodborne_blocks:o_c654']
  with tempfile.TemporaryDirectory(prefix='old-c654-foreign-') as folder:
   source, output, reports = Path(folder) / 'source', Path(folder) / 'output', Path(folder) / 'reports'
   cells = {}; entities = []; roots = {}
   for index, rule in enumerate(rules):
    root = (index * 32, 64, 0); roots[rule.number] = root
    cells[root] = (rule.source.state[0], dict(rule.source.state[1]))
    stale_offset = next(offset for offset in rule.shape if offset != (0, 0, 0))
    stale = tuple(root[axis] + stale_offset[axis] for axis in range(3))
    cells[stale] = (PART, {})
    entities.append(entity(stale, PART, Root=Tag(TAG_LONG, block_pos_long(*root)), Owner=Tag(TAG_STRING, rule.source.state[0])))
    # A separate foreign block proves neither old-owner metadata nor arbitrary
    # destination state may be silently consumed by this compatibility rule.
    foreign_offset = next(offset for offset in rule.shape if offset not in {(0, 0, 0), stale_offset})
    foreign = tuple(root[axis] + foreign_offset[axis] for axis in range(3))
    cells[foreign] = ('minecraft:chest', {})
    for offset in rule.shape:
     cells.setdefault(tuple(root[axis] + offset[axis] for axis in range(3)), ('minecraft:air', {}))
   write_fixture(source, cells, block_entities=entities, floor=False)
   report = convert(source, output, resources=RES, report_path=reports / 'conversion.json', report_root=reports, source_mode='legacy')
   self.assertEqual(0, report['counts']['converted'])
   self.assertEqual(8, len(report['rejected']))
   self.assertTrue(all(entry['reason'] == 'target_would_overwrite_foreign_block' for entry in report['rejected']))
   self.assertEqual(hash_tree(source), hash_tree(output))
   world = World(output, defaults)
   for rule in rules:
    self.assertEqual(world.get(DIM, roots[rule.number]), rule.source.state)

if __name__=='__main__': unittest.main()
