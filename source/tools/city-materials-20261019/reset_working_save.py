"""Restore identical comparison inputs in the ONE existing QA save, not copies.

Only ordinary files inside the exact QA save may be replaced/removed. Original
map and backup are read-only. No reparse traversal or recursive deletion.
"""
import argparse
import hashlib
import json
import shutil
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

PROJECT=Path(__file__).absolute().parents[2]
ROOT=PROJECT.parent
OUT=ROOT/'research/city-materials-20261019'
sys.path.insert(0,str(PROJECT/'tools/collision-streaming-20261018'))
from prepare_route import active_files, ordinary, sha
sys.path.insert(0,str(PROJECT/'tools/city-streaming-20261018'))
from world_io import read_nbt, write_nbt


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--case',required=True)
    a=ap.parse_args()
    assert a.case.replace('-','').isalnum()
    receipt=OUT/(a.case+'-reset.json')
    assert not receipt.exists()
    source=ordinary(ROOT/'deliverables/collision-streaming-20261018/map/world')
    target=ordinary(PROJECT/'build/runtime-client-materials/saves/prototype-fixture')
    assert target.resolve()==target.absolute() and target.is_relative_to(PROJECT/'build/runtime-client-materials')
    for parent in [target,*list(target.parents)[:3]]:
        ordinary(parent)
    manifest={r['path']:r for r in json.loads((OUT/'original-world-files.json').read_text())}
    paths=list(active_files(source))
    expected={p.relative_to(source).as_posix() for p in paths}
    # Verify the final absolute target of every individual file operation.
    def inside(path):
        assert path.resolve().is_relative_to(target.resolve()),path
        if path.exists():ordinary(path)
        return path
    removed=[]
    for p in active_files(target):
        if p.relative_to(target).as_posix() not in expected:
            inside(p).unlink()
            removed.append(p.relative_to(target).as_posix())
    def restore(path):
        relative=path.relative_to(source).as_posix()
        row=manifest[relative]
        dest=inside(target/relative)
        changed=not dest.exists() or sha(dest)!=row['sha256']
        if changed:
            assert sha(path)==row['sha256'],'Original source changed: '+relative
            dest.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(path,dest)
            assert sha(dest)==row['sha256']
        return {'path':relative,'sha256':row['sha256'],'restored':changed}
    with ThreadPoolExecutor(max_workers=8) as pool:
        rows=list(pool.map(restore,paths))
    level=target/'level.dat'
    nbt=read_nbt(level)
    inventory=nbt.root.value['Data'].value['Player'].value['Inventory'].value
    pumpkin=[x for x in inventory if x.value.get('Slot') and x.value['Slot'].value==103 and x.value['id'].value=='minecraft:carved_pumpkin']
    inventory[:]=[x for x in inventory if x not in pumpkin]
    write_nbt(level,nbt)
    for row in rows:
        if row['path']=='level.dat':row['sha256']=sha(level)
    matched=[{k:r[k] for k in ['path','sha256']} for r in sorted(rows,key=lambda r:r['path'])]
    receipt.write_text(json.dumps({'status':'PASS_IDENTICAL_ACTIVE_INPUTS_IN_ONE_WORKING_SAVE','workingSave':str(target),
        'source':str(source),'files':len(rows),'manifestSha256':hashlib.sha256(json.dumps(matched,sort_keys=True).encode()).hexdigest(),
        'restoredFiles':sum(r['restored'] for r in rows),'removedQaExtraFiles':removed,'qaPumpkinRemoved':len(pumpkin)},indent=2)+'\n')


if __name__=='__main__':main()
