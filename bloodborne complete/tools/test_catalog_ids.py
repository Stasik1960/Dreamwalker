import unittest
from catalog_ids import allocate
def obj(k):return {'stable_key':k,'semantic_status':'CONFIRMED','independent_installable':True}
def instance(k,i,dim='minecraft:overworld',cells=1):
    return {'stable_key':k,'instance_key':i,'dimension':dim,'recognition_status':'CONFIRMED','source_members':[[n,0,0] for n in range(cells)]}
class IdChecks(unittest.TestCase):
    def test_helpers_rotations_dimensions_do_not_replace_object_frequency(self):
        rows=[instance('tree','big',cells=400),instance('window','north'),instance('window','south'),instance('window','north','minecraft:the_end')]
        result=allocate([obj('tree'),obj('window'),obj('unused')],rows)
        self.assertEqual([(r['number'],r['stable_key'],r['initial_instance_count']) for r in result['entries']],
                         [('00001','window',3),('00002','tree',1),('00003','unused',0)])
    def test_changed_map_and_tombstone_never_renumber_or_reuse(self):
        first=allocate([obj('a'),obj('b')],[instance('a','a1'),instance('b','b1')])
        second=allocate([obj('b'),obj('c')],[instance('c',str(n)) for n in range(20)],first)
        self.assertEqual([(r['number'],r['stable_key'],r['active']) for r in second['entries']],
                         [('00001','a',False),('00002','b',True),('00003','c',True)])
        self.assertEqual(first['next_id'],3)
    def test_full_range_fails_without_truncation(self):
        ledger={'next_id':99999,'entries':[]}
        with self.assertRaisesRegex(ValueError,'exhausted'):allocate([obj('a'),obj('b')],[],ledger)
    def test_ambiguous_recognition_and_double_consumption_do_not_become_counts(self):
        with self.assertRaises(ValueError):allocate([obj('a')],[instance('a','one'),instance('a','one')])
        row=instance('a','one');row['recognition_status']='AMBIGUOUS'
        with self.assertRaises(ValueError):allocate([obj('a')],[row])
        row=obj('a');row['semantic_status']='CANDIDATE'
        with self.assertRaises(ValueError):allocate([row],[])
    def test_tie_break_is_reproducible_and_ids_remain_five_digits(self):
        a=allocate([obj('z'),obj('a')],[]);b=allocate([obj('a'),obj('z')],[])
        self.assertEqual(a,b);self.assertEqual([e['registry_id'] for e in a['entries']],['bloodborne_dw:00001','bloodborne_dw:00002'])
if __name__=='__main__':unittest.main()
