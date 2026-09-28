"""Coordinate-scoped root rules: never move arbitrary objects on conflict."""
import tempfile
import unittest
from pathlib import Path
from composite_world_oracle import ROOT, EvidenceReader, key
from convert_logical_world import DEFAULT_RESOURCES, World, add, convert
from modded_world_adapter import compile_modded_rules
from test_logical_world import assembly_source, write_chunk, tree_hash
from world_io import RegionFile, compound
from check_logical_world import check

class ExplicitRootExceptionsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.rules,cls.defaults,_=compile_modded_rules(DEFAULT_RESOURCES)

    def test_only_two_authorized_origins_and_no_shared_root_consumption(self):
        exceptions=[r for r in self.rules if r.allowed_origins]
        self.assertTrue(exceptions)
        self.assertEqual({(-374,73,-291),(-560,98,-9)}, {r.allowed_origins[0][1] for r in exceptions})
        for rule in exceptions:
            dim,origin=rule.allowed_origins[0]
            self.assertFalse(rule.accepts_origin(dim,add(origin,(1,0,0))))
            self.assertFalse(rule.accepts_origin('minecraft:the_nether',origin))
            canonical=(rule.root_offset[0],rule.root_offset[1]-1,rule.root_offset[2])
            self.assertNotIn(canonical,{p.offset for p in (rule.source,)+rule.members})
            self.assertNotIn((0,-1,0),rule.shape)

    def test_balustrade_minus560_98_minus9_remains_unresolved_without_exact_input_match(self):
        reader=EvidenceReader(ROOT/'reference-inputs/latest-modded-world.zip')
        try:
            origin=(-560,98,-9)
            rules=[r for r in self.rules if r.allowed_origins and r.allowed_origins[0][1]==origin]
            self.assertTrue(rules)
            self.assertFalse(any(all(reader.state('minecraft:overworld',add(origin,p.offset))[1]==key(p.state)
                                     for p in (r.source,)+r.members) for r in rules))
        finally:reader.close()

    def test_bench_minus374_73_minus291_rebase_is_idempotent_and_preserves_other_root(self):
        origin=(-374,73,-291)
        rule=next(r for r in self.rules if r.allowed_origins and r.allowed_origins[0][1]==origin)
        reader=EvidenceReader(ROOT/'reference-inputs/latest-modded-world.zip')
        try:
            for p in (rule.source,)+rule.members:
                self.assertEqual(key(p.state),reader.state('minecraft:overworld',add(origin,p.offset))[1])
            other_text=reader.state('minecraft:overworld',origin)[1]
        finally:reader.close()
        other_id,_,suffix=other_text.partition('[')
        other_props=dict(v.split('=',1) for v in suffix.rstrip(']').split(',') if v)
        blocks={add(origin,p.offset):(p.state[0],dict(p.state[1])) for p in (rule.source,)+rule.members}
        blocks[origin]=(other_id,other_props)
        with tempfile.TemporaryDirectory() as temporary:
            base=Path(temporary);source=base/'source';output=base/'first';second=base/'second'
            assembly_source(source,{})
            cx,cz=origin[0]//16,origin[2]//16
            path=source/f'region/r.{cx//32}.{cz//32}.mca'
            write_chunk(path,cx,cz,blocks)
            # Both bench cells lie in section 4, as expected by this fixture writer.
            report=convert(source,output,source_mode='modded',report_root=base,report_path=base/'first.json')
            world=World(output,self.defaults)
            self.assertEqual(rule.target,world.get('minecraft:overworld',add(origin,(0,1,0))),report['rejected'])
            self.assertEqual(other_text,key(world.get('minecraft:overworld',origin)))
            self.assertTrue(check(source,output,base/'first.json',DEFAULT_RESOURCES)['ok'])
            again=convert(output,second,source_mode='modded',report_root=base,report_path=base/'second.json')
            self.assertEqual(0,again['counts']['converted'])
            self.assertEqual(tree_hash(output),tree_hash(second))
            # The same coordinates with a user-replaced neighbor are NOT the
            # approved historical conflict. Do not silently enable relocation.
            changed=base/'changed';assembly_source(changed,{})
            changed_blocks={**blocks,origin:('minecraft:diamond_block',{})}
            write_chunk(changed/f'region/r.{cx//32}.{cz//32}.mca',cx,cz,changed_blocks)
            changed_out=base/'changed-out'
            rejected=convert(changed,changed_out,source_mode='modded',report_root=base,report_path=base/'changed.json')
            self.assertTrue(any(r['reason']=='explicit_root_context_mismatch' for r in rejected['rejected']))
            changed_world=World(changed_out,self.defaults)
            self.assertEqual(('minecraft:diamond_block',()),changed_world.get('minecraft:overworld',origin))
            self.assertNotEqual(rule.target,changed_world.get('minecraft:overworld',add(origin,(0,1,0))))

if __name__=='__main__':unittest.main()
