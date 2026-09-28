import unittest
import copy
from accepted_repair_coverage import Observed, Runtime as CoverageRuntime, audit_coverage, classify_output, SOURCE_SHA
from world_io import Tag, TAG_COMPOUND, TAG_INT, TAG_LONG, TAG_STRING

DIM = 'minecraft:overworld'
NAME = 'bloodborne_blocks:o_test'
TARGET = NAME+'[facing=north]'
OUTPUT = {'family': 'o_test', 'canonical_root': [0,64,0], 'expected_logical_state': TARGET}
ROW = {'dimension': DIM, 'outputs': [OUTPUT]}

def helper(owner=NAME, root=64):
    return Tag(TAG_COMPOUND, {'id': Tag(TAG_STRING, 'bloodborne_blocks:architecture_part'),
        'x': Tag(TAG_INT, 0), 'y': Tag(TAG_INT, 65), 'z': Tag(TAG_INT, 0),
        'Root': Tag(TAG_LONG, root), 'Owner': Tag(TAG_STRING, owner)})

class Runtime:
    definitions = {NAME: {}}
    def normalized(self, v):
        if v is None: return None
        return (NAME, (('facing','north'),)) if v == TARGET or isinstance(v,tuple) else (v, ())
    def shape(self, v): return {(0,0,0), (0,1,0)}

class ContractRuntime(CoverageRuntime):
    definitions = {}
    contracts = {'o_test': {'id': 'o_test', 'states': {
        'facing=east': {'migration_source_pattern': [{'components': [
            {'id': 'minecraft:trapdoor', 'offset': [0, 0, 0],
             'properties': {'facing': 'east'}}]}]},
        'facing=north': {'migration_source_pattern': [{'components': [
            {'id': 'minecraft:trapdoor', 'offset': [0, 0, 0],
             'properties': {'facing': 'north'}}]}]}}}}

    def __init__(self):
        pass

class World:
    def __init__(self, cells, entities): self.cells, self.entities = cells, entities
    def get(self, dim, p): return self.cells.get(tuple(p), 'minecraft:air')
    def block_entities(self): return {(DIM,*p): t for p,t in self.entities.items()}

class CoverageTests(unittest.TestCase):
    def observed(self, top='bloodborne_blocks:architecture_part', tag=None):
        return Observed(World({(0,64,0): TARGET, (0,65,0): top}, {(0,65,0): tag} if tag else {}))

    def test_exact_root_requires_real_binding(self):
        self.assertEqual(classify_output(OUTPUT,DIM,self.observed(tag=helper()),Runtime()), [])
        self.assertTrue(classify_output(OUTPUT,DIM,self.observed(),Runtime()))

    def test_stone_cannot_stand_in_for_upper_helper(self):
        errors = classify_output(OUTPUT,DIM,self.observed(top='minecraft:stone',tag=helper()),Runtime())
        self.assertIn('invalid_helper_carrier', {e['reason'] for e in errors})

    def test_wrong_owner_is_not_complete(self):
        errors = classify_output(OUTPUT,DIM,self.observed(tag=helper('bloodborne_blocks:o_other')),Runtime())
        self.assertIn('required_helper_binding_missing', {e['reason'] for e in errors})

    def test_extra_owned_cell_is_fragmentation(self):
        world = World({(0,64,0): TARGET, (0,65,0): 'bloodborne_blocks:architecture_part'}, {(0,65,0): helper(), (0,66,0): helper()})
        self.assertIn('owner_has_extra_helpers', {e['reason'] for e in classify_output(OUTPUT,DIM,Observed(world),Runtime())})

    def test_missing_known_object_never_counts_as_absent(self):
        oracle = {'source_sha256':SOURCE_SHA,'families':{'o_test':{}},'occurrences':[ROW], 'superseded_matches':[]}
        report = audit_coverage(oracle,Observed(World({},{})),Runtime())
        self.assertEqual(report['counts']['unresolvedKnown'],1)
        self.assertEqual(report['counts']['trulyAbsent'],0)
        self.assertEqual(report['coverageCompleteness'],'FAIL')

    def test_current_pattern_replaces_stale_oracle_facing(self):
        row = {'origin': [0, 64, 0], 'source_cells': [{'position': [0, 64, 0],
            'state': 'minecraft:trapdoor[facing=east]'}]}
        target, error = ContractRuntime().current_target(row, OUTPUT)
        self.assertEqual(target, NAME+'[facing=east]')
        self.assertIsNone(error)

    def test_ambiguous_current_pattern_is_unresolved(self):
        runtime = ContractRuntime()
        runtime.contracts = copy.deepcopy(runtime.contracts)
        runtime.contracts['o_test']['states']['facing=south'] = runtime.contracts['o_test']['states']['facing=east']
        row = {'origin': [0, 64, 0], 'source_cells': [{'position': [0, 64, 0],
            'state': 'minecraft:trapdoor[facing=east]'}]}
        target, error = runtime.current_target(row, OUTPUT)
        self.assertIsNone(target)
        self.assertEqual(error, 'current_pattern_ambiguous')

    def test_split_outputs_are_each_retained(self):
        oracle = {'source_sha256': SOURCE_SHA, 'families': {'o_test': {}},
            'occurrences': [{'origin': [0, 64, 0], 'source_cells': [],
                'dimension': DIM, 'outputs': [OUTPUT, dict(OUTPUT, canonical_root=[1,64,0])]}],
            'superseded_matches': []}
        report = audit_coverage(oracle, Observed(World({}, {})), Runtime())
        self.assertEqual(report['counts']['candidates'], 2)

    def test_split_output_uses_declared_negative_root_offset(self):
        runtime = ContractRuntime()
        runtime.contracts = copy.deepcopy(runtime.contracts)
        pattern = runtime.contracts['o_test']['states']['facing=east']['migration_source_pattern'][0]
        pattern['split_transaction'] = {'id': 'C1491', 'outputs': [
            {'family': 'o_test', 'root_offset': [-1, 0, 0], 'properties': {'facing': 'north'}}]}
        row = {'origin': [10, 64, 10], 'source_cells': [{'position': [10, 64, 10],
            'state': 'minecraft:trapdoor[facing=east]'}]}
        output = dict(OUTPUT, canonical_root=[9, 64, 10])
        target, error = runtime.current_target(row, output)
        self.assertEqual(target, NAME+'[facing=north]')
        self.assertIsNone(error)

    def test_flat_pending_census_is_known_and_not_dropped(self):
        oracle = {'source_sha256':SOURCE_SHA,'families':{},'occurrences':[]}
        row = {'dimension':DIM,'family':'authored_grass_7','canonical_root':[4,64,8]}
        report = audit_coverage(oracle,Observed(World({},{})),ContractRuntime(),supplemental=[row])
        self.assertEqual(1,report['counts']['candidates'])
        self.assertEqual(1,report['counts']['unresolvedKnown'])
        self.assertEqual('current_family_missing',report['residuals'][0]['errors'][0]['reason'])

    def test_wall_equivalence_requires_identical_mesh_and_entire_geometry(self):
        runtime = ContractRuntime()
        wall='bloodborne_blocks:building_stone_brick_wall'
        a=runtime.normalized(wall+'[connection=tall_13,facing=north]')
        b=runtime.normalized(wall+'[connection=tall_7,facing=south]')
        keys=[','.join(k+'='+v for k,v in state[1]) for state in (a,b)]
        geometry={'cells':{'0,0,0':{'collision':[[0,0,0,1,1,1]],'outline':[]}}}
        runtime.wall_signatures={key:('existing-mesh',copy.deepcopy(geometry)) for key in keys}
        self.assertTrue(runtime.equivalent_state(a,b))
        runtime.wall_signatures[keys[1]][1]['cells']['0,0,0']['collision']=[]
        self.assertFalse(runtime.equivalent_state(a,b))
        runtime.wall_signatures[keys[1]]=('another-mesh',geometry)
        self.assertFalse(runtime.equivalent_state(a,b))
        self.assertFalse(runtime.equivalent_state(a,('bloodborne_blocks:owner_unknown',b[1])))

if __name__ == '__main__': unittest.main()
