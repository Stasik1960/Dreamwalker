"""Independent checks for the bounded TEST2 construction-family adapter."""
import json
import gzip
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/bloodborne_blocks/city'
DIRS=('north','east','south','west')

class ReviewedWallTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.proof=json.loads((ROOT/'reviewed-wall-family.json').read_bytes())
        cls.defs={d['id']:d for d in json.loads((ROOT/'definitions.json').read_bytes())['blocks']}
        cls.geometry=json.loads((ROOT/'geometry.json').read_bytes())['blocks']
        cls.mapping=json.loads((ROOT/'owner-runtime-mappings.json').read_bytes())['states']
        cls.meshes=json.loads(gzip.decompress((ROOT/'owner-meshes.json.gz').read_bytes()))

    def test_aliases_are_exact_source_family_not_visual_similarity(self):
        from build_reviewed_wall_family import accepted_aliases
        expected=accepted_aliases()
        self.assertEqual(expected,self.proof['aliases'])
        self.assertEqual(66,len(expected))
        for ident, source in expected.items():
            self.assertEqual('bloodborne_blocks:'+ident,self.mapping[source]['id'])
        self.assertEqual({'bloodborne_blocks:block/stone_brick_wall_'+p for p in ('post','side','side_tall')},set(self.proof['artProof']['models']))

    def test_all_building_states_reuse_exact_art_and_collision(self):
        new=self.defs[self.proof['id']]
        self.assertEqual(set(self.proof['connections']),set(new['properties']['connection']))
        self.assertEqual(len(self.proof['connections'])*4,len(new['states']))
        for connection,row in self.proof['connections'].items():
            for turn,facing in enumerate(DIRS):
                oldkey='facing='+DIRS[(DIRS.index(row['facing'])+turn)%4]
                key='connection='+connection+',facing='+facing
                owner=row['owner']
                self.assertEqual(self.defs[owner]['models'][oldkey],new['models'][key])
                self.assertIn(new['models'][key],self.meshes)
                self.assertEqual(self.geometry[owner]['states'][oldkey],self.geometry[new['id']]['states'][key])
                self.assertEqual({'0,0,0'},set(self.geometry[new['id']]['states'][key]['cells']))

    def test_every_alias_state_has_one_exact_successor_including_retained_art(self):
        expected={ident+'|facing='+facing for ident in self.proof['aliases'] for facing in DIRS}
        self.assertEqual(expected,set(self.proof['aliasStates']))
        for alias_state,successor in self.proof['aliasStates'].items():
            owner,oldkey=alias_state.split('|',1);key='connection='+successor['connection']+',facing='+successor['facing']
            self.assertEqual(self.defs[owner]['models'][oldkey],self.defs[self.proof['id']]['models'][key],alias_state)
            self.assertEqual(self.geometry[owner]['states'][oldkey],self.geometry[self.proof['id']]['states'][key],alias_state)
        retained=set(self.proof['retainedConnections'])
        self.assertEqual(len(self.proof['aliases'])-len({row['owner'] for key,row in self.proof['connections'].items() if key in self.proof['canonicalConnections']}),len(retained))
        for connection in retained:
            self.assertTrue(connection.startswith('retained_'))
            self.assertEqual('north',self.proof['connections'][connection]['facing'])

    def test_pair_corner_t_cross_are_proved_not_invented(self):
        from atomic_owner_groups import state
        for level in ('low','tall'):
            for mask in range(16):
                row=self.proof['connections'][level+'_'+str(mask)]
                props=dict(state(row['source'])[1]);rotation=DIRS.index(row['facing'])
                actual=[props[DIRS[(i-rotation)%4]] for i in range(4)]
                self.assertEqual([level if mask&(1<<i) else 'none' for i in range(4)],actual)
        cross=self.proof['connections']['low_15']
        self.assertIn('up=false',cross['source'])
        self.assertTrue(self.proof['unsupported'])

if __name__=='__main__':unittest.main()
