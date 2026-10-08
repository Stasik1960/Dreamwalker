"""Selection-only correction: narrow lower tree, broad upper crossed crown.

The four bounds are source-backed by the original18-panel assembly. This script
does not rebuild art, change model transforms/UV/textures or change collision.
"""
import copy
import hashlib
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
PATH=ROOT/'src/architecture/resources/bloodborne_dw/composite/prototype_tree.json'

def tree_selection():
    return [
        {'from':[-16,0,7.875],'to':[32,96,8.125]},
        {'from':[7.875,0,-16],'to':[8.125,96,32]},
        {'from':[-64,96,7.875],'to':[80,288,8.125]},
        {'from':[7.875,96,-64],'to':[8.125,288,80]},
    ]

def without_selection(document):
    result=copy.deepcopy(document)
    for variant in result['variants']:
        for pose in variant['poses'].values():pose.pop('selection',None)
    return result

def fingerprint(value):return hashlib.sha256(json.dumps(value,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def main():
    before=json.loads(PATH.read_text(encoding='utf8'));after=copy.deepcopy(before)
    assert len(after['variants'])==2
    for variant in after['variants']:
        for pose in variant['poses'].values():
            assert pose['collision']==[{'from':[6,0,6],'to':[10,96,10]}]
            pose['selection']=tree_selection()
    assert without_selection(before)==without_selection(after)
    PATH.write_text(json.dumps(after,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'status':'APPLIED_SELECTION_ONLY_FOUR_BOUNDS','variants':2,'selectionBoxes':4,
        'allNonSelectionDescriptorFieldsUnchanged':True,'artAndPhysicsFingerprint':fingerprint(without_selection(after))}))

if __name__=='__main__':main()
