import unittest
from check_grid_physics import validate_state
class GridPhysicsTests(unittest.TestCase):
    def test_reject_decorative_helper(self):
        with self.assertRaisesRegex(ValueError,'HELPER_OUTSIDE_PHYSICAL_FOOTPRINT'):
            validate_state([[0,0,0]],{'1,0,0':{'collision':[]}})
    def test_reject_collision_overhang(self):
        with self.assertRaisesRegex(ValueError,'COLLISION_OUTSIDE_LOCAL_CELL'):
            validate_state([[0,0,0]],{'0,0,0':{'collision':[[0,0,0,1.1,1,1]]}})
    def test_explicit_multicell(self):
        validate_state([[0,0,0],[0,1,0]],{'0,0,0':{'collision':[[0,0,0,1,1,1]]},'0,1,0':{'collision':[]}})
import json,gzip,tempfile
from pathlib import Path
from grid_world_reconciliation import apply
from test_logical_world import assembly_source
from convert_logical_world import World
class GridReconciliationTests(unittest.TestCase):
    def test_only_proven_city_helper_removed_and_idempotent(self):
        rootdir=Path(__file__).resolve().parents[1]
        old=json.loads(gzip.decompress((rootdir/'docs/grid-physics/legacy-city-geometry.json.gz').read_bytes()))
        ident='brown_terracotta'; key,v=next(iter(old['blocks'][ident]['states'].items()));g=old['profiles'][v['ref']]
        offset=next(tuple(map(int,k.split(','))) for k in g['cells'] if k!='0,0,0')
        root=(4,70,4);p=tuple(root[i]+offset[i] for i in range(3));owner='bloodborne_blocks:'+ident
        with tempfile.TemporaryDirectory() as temp:
            source=Path(temp)/'world';assembly_source(source,{root:(owner,dict(x.split('=') for x in key.split(','))),p:('bloodborne_blocks:architecture_part',{}),(12,64,12):('minecraft:diamond_block',{})})
            world=World(source,{})
            world.add_helper('minecraft:overworld',p,root,owner)
            self.assertEqual(1,apply(world)['helperChanges'])
            self.assertEqual(('minecraft:air',()),world.get('minecraft:overworld',p))
            self.assertEqual(('minecraft:diamond_block',()),world.get('minecraft:overworld',(12,64,12)))
            self.assertEqual(0,apply(world)['helperChanges'])

if __name__=='__main__':unittest.main()

