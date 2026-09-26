"""Preconditions for explicit exceptions, not a claim of runtime integration."""
import json
import gzip
import unittest
from composite_world_oracle import ROOT

class RootExceptionEvidenceTests(unittest.TestCase):
    def check_case(self, root, family):
        data=json.loads((ROOT/'docs/composite-grid-repair/root-exception-evidence.json').read_bytes())
        case=next(c for c in data['cases'] if c['root']==list(root))
        self.assertEqual(family,case['family'])
        candidate=next(c for c in case['candidates'] if c['delta']==[0,1,0])
        self.assertTrue(candidate['own_source_cell'])
        source_cell=next(c for c in case['source_cells'] if c['position']==candidate['position'])
        self.assertEqual(['FOUND',source_cell['state']],candidate['source'])
        self.assertEqual('FOUND',candidate['modded'][0])
        self.assertEqual([{'family':family,'root':list(root)}],candidate['physical_owners'])
        # A vertical rebase commutes with every supported cardinal rotation.
        # Keeping mesh coordinates/pivot untouched and translating AFTER rotation
        # cancels the new root exactly, including non-central vertices.
        delta=(0,1,0)
        for turns in range(4):
            for p in ((0,0,0),(.5,0,.5),(-3.25,2.5,7.75),(1,4,-2)):
                x,y,z=p[0]-.5,p[1],p[2]-.5
                for _ in range(turns):x,z=-z,x
                rotated=(x+.5,y,z+.5)
                before=tuple(root[i]+rotated[i] for i in range(3))
                after=tuple(root[i]+delta[i]+rotated[i]-delta[i] for i in range(3))
                self.assertEqual(before,after)

        # Test every real, already-oriented mesh vertex, not only sample points.
        resources=ROOT/'src/main/resources/bloodborne_blocks/logical'
        contracts=json.loads((resources/'contracts-v2.json').read_bytes())
        meshes=json.loads(gzip.decompress((resources/'meshes.json.gz').read_bytes()))
        contract=next(f for f in contracts['families'] if f['id']==family)
        facings=set()
        for state_key,state in contract['states'].items():
            props=dict(p.split('=',1) for p in state_key.split(','))
            facings.add(props['facing'])
            mesh=meshes[state['render_mesh']['id']]
            offset=state['render_mesh']['offset']
            compensated=[offset[i]-delta[i] for i in range(3)]
            for polygon in mesh['polygons']:
                for vertex in polygon['vertices']:
                    before=tuple(root[i]+vertex[i]+offset[i] for i in range(3))
                    after=tuple(root[i]+delta[i]+vertex[i]+compensated[i] for i in range(3))
                    self.assertEqual(before,after,(state_key,vertex))
        self.assertEqual({'north','east','south','west'},facings)

    def test_bench_minus374_73_minus291(self):
        self.check_case((-374,73,-291),'o_bench')

    def test_balustrade_minus560_98_minus9(self):
        self.check_case((-560,98,-9),'o_high_balustrade')

if __name__=='__main__':unittest.main()
