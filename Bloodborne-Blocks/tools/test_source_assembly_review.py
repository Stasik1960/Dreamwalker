import copy
import unittest
from source_assembly_review import clusters, fingerprint, normalized, rotate_cell, rotate_state, merge_records, active_apps


class SourceAssemblyReviewTests(unittest.TestCase):
    def test_rotated_translated_cluster_same_fingerprint(self):
        cells = {(10,2,5):('minecraft:stone',(('facing','north'),)), (11,4,5):('minecraft:dirt',(('axis','x'),))}
        signature = fingerprint(cells)[0]
        for turn in range(4):
            rotated = {tuple(x+30 for x in rotate_cell(pos,turn)):rotate_state(state,turn) for pos,state in cells.items()}
            self.assertEqual(signature,fingerprint(rotated)[0])
        changed = dict(cells); changed.pop((11,4,5))
        self.assertNotEqual(signature,fingerprint(changed)[0])

    def test_only_explicit_carriers_can_bridge_vertical_gap(self):
        cells = {(0,0,0):('minecraft:oak_log',()),(0,6,0):('minecraft:birch_log',()),(4,0,0):('minecraft:stone',())}
        self.assertEqual(sorted(map(len,clusters(cells))),[1,1,1])
        self.assertEqual(sorted(map(len,clusters(cells,{'minecraft:oak_log','minecraft:birch_log'}))),[1,2])

    def test_stable_ids_and_authority(self):
        fresh = [{'spatial_fingerprint':'a','components':[{'number':1}]},{'spatial_fingerprint':'b'}]
        first = merge_records([],copy.deepcopy(fresh))
        first[0]['user_decision'] = {'action':'OBJECT','components':[1]}
        first[0]['status'] = 'APPROVED'
        self.assertEqual(first,merge_records(first,list(reversed(fresh))))
        added = merge_records(first,[{'spatial_fingerprint':'c'}])
        self.assertEqual(added[-1]['review_id'],'C003')
        self.assertEqual(added[0],first[0])

    def test_source_transforms_and_alternative_annotation(self):
        raw = {'variants':{'facing=north':[{'model':'block/a','y':90},{'model':'block/b'}]},
               'multipart':[{'when':{'AND':[{'open':'true'},{'facing':'north|east'}]},'apply':{'model':'block/c','x':90}}]}
        apps = active_apps(raw,{'facing':'north','open':'true'})
        self.assertEqual([(a['model'],a.get('y'),a.get('x')) for a in apps],[('block/a',90,None),('block/c',None,90)])
        self.assertEqual(apps[0]['preview_alternatives'],2)


if __name__ == '__main__': unittest.main()
