"""Independent verifier rejects plausible but unauthorized migration evidence."""
import copy
import hashlib
import unittest
import json
import tempfile
from pathlib import Path
import verify_accepted_restore as v

DIM='minecraft:overworld'
P=(0,64,0)
OLD=('bloodborne_blocks:m_original',())
RC1=('bloodborne_blocks:city_carrier',())
TARGET=('bloodborne_blocks:o_test',())


class World:
    def __init__(self, cells): self.cells=cells
    def get(self, dim, p): return self.cells.get(p,v.AIR)
    def ticks_at(self, dim, points): return False


class VerifierTests(unittest.TestCase):
    def test_cross_platform_json_hash_only_allows_line_endings(self):
        with tempfile.TemporaryDirectory() as temp:
            resources=Path(temp)/'logical';resources.mkdir()
            path=resources/'definitions.json';path.write_bytes(b'{\r\n "blocks": []\r\n}\r\n')
            recorded=v.definition_hashes(resources)
            path.write_bytes(path.read_bytes().replace(b'\r\n',b'\n'))
            self.assertTrue(v.resource_hashes_match(resources,recorded))
            path.write_bytes(b'{"blocks":[{}]}')
            self.assertFalse(v.resource_hashes_match(resources,recorded))

    def test_orphan_part_without_nbt_is_not_a_clean_world(self):
        from test_logical_world import assembly_source
        from convert_logical_world import World as DiskWorld
        with tempfile.TemporaryDirectory() as temp:
            base=Path(temp);resources=base/'logical';city=base/'city'
            for folder in (resources,city):
                folder.mkdir();(folder/'definitions.json').write_text(json.dumps({'blocks':[]}))
            assembly_source(base/'world',{P:(v.PART,{})})
            with self.assertRaisesRegex(ValueError,'part without entity'):
                v.audit_world(DiskWorld(base/'world',{}),resources,{})

    def test_correct_group_with_pending_tick_is_rejected(self):
        args=self.fixture(True);args[3].ticks_at=lambda dim,points:True
        with self.assertRaisesRegex(ValueError,'scheduled tick'):v.verify_group(*args)

    def fixture(self, correct=False):
        identity='accepted-rc1-'+hashlib.sha256(b'0').hexdigest()[:20]
        row={'transaction':identity,'acceptedEntries':[0],'dimension':DIM,'origin':list(P),
             'rc1Dependencies':[],'bridgeHelperCells':[],'roots':[{'position':list(P),'state':v.state_text(TARGET)}],
             'result':'already_correct' if correct else 'restore'}
        entry={'decision':'converted','dimension':DIM,'changes':[{'position':list(P),
                'before':v.state_text(RC1),'after':v.state_text(TARGET)}],
               'source':[list(P)],'helpers':[],'outputs':[{'targetRoot':list(P),'target':v.state_text(TARGET)}]}
        ledger={} if correct else {identity:entry}
        args=[row,(DIM,{P:TARGET},{P:TARGET}),World({P:RC1}),World({P:TARGET if correct else RC1}),
              World({P:TARGET}),{},{},{},{},{},{TARGET:{(0,0,0)}},{},{(DIM,P)},ledger]
        return args

    def test_rc1_before_is_distinct_from_original_modded(self):
        args=self.fixture()
        self.assertEqual(v.verify_group(*args)[0],'restore')
        args[-1][args[0]['transaction']]['changes'][0]['before']=v.state_text(OLD)
        with self.assertRaisesRegex(ValueError,'actual RC1'):v.verify_group(*args)

    def test_unchanged_repeat_has_no_write_ledger(self):
        args=self.fixture(True)
        self.assertEqual(v.verify_group(*args)[0],'already_correct')
        args[-1][args[0]['transaction']]={}
        with self.assertRaisesRegex(ValueError,'already-correct'):v.verify_group(*args)

    def test_output_nbt_and_source_nbt_tampering_rejected(self):
        for index,reason in ((6,'source cell/NBT'),(7,'helper NBT')):
            args=self.fixture();args[index][(DIM,*P)]=v.entity(P,[((2,64,0),TARGET[0])])
            with self.assertRaisesRegex(ValueError,reason):v.verify_group(*args)

    def test_omitted_or_duplicate_groups_rejected(self):
        v.complete_partition([{'acceptedEntries':[0,1]}],'acceptedEntries',2)
        for ids in ([0],[0,0],[0,1,2],[False,1]):
            with self.subTest(ids=ids),self.assertRaises(ValueError):
                v.complete_partition([{'acceptedEntries':ids}],'acceptedEntries',2)

    def test_forced_partial_or_foreign_write_rejected(self):
        for mutate in ('forced','partial','extra','roots'):
            args=self.fixture();entry=args[-1][args[0]['transaction']]
            if mutate=='forced':entry['decision']='forced'
            elif mutate=='partial':entry['changes']=[]
            elif mutate=='extra':args[0]['bridgeHelperCells']=[[99,64,0]]
            else:args[0]['roots']=[]
            with self.subTest(mutate=mutate),self.assertRaises(ValueError):v.verify_group(*args)

    def test_composed_pass_chain_uses_original_once(self):
        entries=[]
        for before,after in ((OLD,RC1),(RC1,TARGET)):
            entries.append({'dimension':DIM,'decision':'converted','helpers':[],
                'changes':[{'position':list(P),'before':v.state_text(before),'after':v.state_text(after)}]})
        self.assertEqual(v.compose([0,1],entries,World({P:OLD}),{TARGET:{(0,0,0)}})[1],{P:TARGET})
        entries[1]['changes'][0]['before']=v.state_text(OLD)
        with self.assertRaisesRegex(ValueError,'MODDED chain'):v.compose([0,1],entries,World({P:OLD}),{TARGET:{(0,0,0)}})

    def test_seed_requires_complete_old_mask(self):
        shape={(0,0,0),(0,1,0)}
        with self.assertRaisesRegex(ValueError,'incomplete RC1 seed'):
            v.dependency_closure(DIM,{P:TARGET},{P:TARGET},World({P:TARGET}),{}, {},
                {TARGET:shape},{TARGET:{(0,0,0)}},{TARGET[0]:{}},set(),{P})


if __name__=='__main__':unittest.main()
