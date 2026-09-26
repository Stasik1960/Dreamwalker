import json
import tempfile
import unittest
from pathlib import Path
from city_palette import audit_helpers, helper_bindings
from convert_logical_world import World, PART
from test_logical_world import assembly_source
from world_io import compound


class SharedHelperBindingsTests(unittest.TestCase):
    def fixture(self, carrier):
        tmp=tempfile.TemporaryDirectory();self.addCleanup(tmp.cleanup)
        base=Path(tmp.name);city=base/'city';logical=base/'logical'
        city.mkdir();logical.mkdir()
        geometry={'blocks':{}}
        for name, offsets in [('a',('0,0,0','1,0,0')),('b',('0,0,0',) if carrier else ('0,0,0','-1,0,0'))]:
            geometry['blocks'][name]={'states':{'':{'cells':{p:{} for p in offsets}}}}
        (logical/'geometry.json').write_text(json.dumps(geometry))
        (logical/'definitions.json').write_text(json.dumps({'blocks':[{'id':n,'logical':True} for n in ('a','b')]}))
        (city/'geometry.json').write_text('{"blocks":{}}')
        dim='minecraft:overworld';a='bloodborne_blocks:a';b='bloodborne_blocks:b'
        blocks={(0,64,0):(a,{}),(1 if carrier else 2,64,0):(b,{})}
        if not carrier:blocks[(1,64,0)]=(PART,{})
        assembly_source(base/'world',blocks);world=World(base/'world',{})
        owners=[(a,(0,64,0))]
        if not carrier:owners.append((b,(2,64,0)))
        world.add_shared_helper(dim,(1,64,0),owners)
        return world,city,dim

    def test_root_guest_and_shared_helper_require_every_live_owner(self):
        for carrier in (True,False):
            with self.subTest(carrier=carrier):
                world,city,dim=self.fixture(carrier)
                self.assertTrue(audit_helpers(world,city)['ok'])
                world.set(dim,(0,64,0),('minecraft:air',()))
                self.assertFalse(audit_helpers(world,city)['ok'])

    def test_foreign_carrier_is_not_allowed_by_owner_metadata(self):
        world,city,dim=self.fixture(True)
        world.set(dim,(1,64,0),('minecraft:diamond_block',()))
        self.assertFalse(audit_helpers(world,city)['ok'])

    def test_duplicate_or_inconsistent_plural_metadata_rejected(self):
        world,city,dim=self.fixture(False)
        data=compound(world.block_entities()[(dim,1,64,0)])
        self.assertEqual(2,len(helper_bindings(data)))
        data['Owners'].value[1]=data['Owners'].value[0]
        with self.assertRaises(ValueError):helper_bindings(data)
        self.assertFalse(audit_helpers(world,city)['ok'])

    def test_world_gate_requires_complete_set_and_exact_live_roots(self):
        from check_composite_world import shared_helper_matches
        dim='minecraft:overworld';cell=(1,64,0);a=((0,64,0),'bloodborne_blocks:a');b=((2,64,0),'bloodborne_blocks:b')
        roots={(dim,a[0]):a[1],(dim,b[0]):b[1]}
        expected={(dim,cell):{a,b}}
        class Reader:
            bindings={a,b}
            states={**roots,(dim,cell):PART}
            def helper_owners(self,dim,p):return self.bindings
            def state(self,dim,p):return None,self.states.get((dim,p))
        reader=Reader()
        self.assertTrue(shared_helper_matches(reader,dim,cell,a,expected,roots))
        reader.bindings={a}
        self.assertFalse(shared_helper_matches(reader,dim,cell,a,expected,roots))
        reader.bindings={a,((3,64,0),'bloodborne_blocks:foreign')}
        self.assertFalse(shared_helper_matches(reader,dim,cell,a,expected,roots))
        reader.bindings={a,b};reader.states[(dim,b[0])]='minecraft:air'
        self.assertFalse(shared_helper_matches(reader,dim,cell,a,expected,roots))
        reader.states[(dim,b[0])]=b[1];reader.states[(dim,cell)]='minecraft:diamond_block'
        self.assertFalse(shared_helper_matches(reader,dim,cell,a,expected,roots))


if __name__=='__main__':unittest.main()
