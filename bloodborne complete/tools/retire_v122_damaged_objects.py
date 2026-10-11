"""Retire only post-V12 real objects shown on the previous red platforms."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import stat

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT/'src/architecture/resources'
OUT = ROOT/'docs/v12.2-simplified-collisions'
EXPECTED = {'source_jungle_trapdoor_bottom':'93148','source_jungle_trapdoor_open':'93149'}


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def write(path, value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    prior = read(ROOT/'docs/gallery-v12.1/catalogue.json')
    damaged = [row for row in prior['exhibits'] if row['section']=='error' and row['kind']!='diagnostic']
    assert len(damaged)==3
    assert {row['registry'].split(':')[1]:row['id'] for row in damaged}==EXPECTED
    assert all(row['origin']=='NEW' for row in damaged)
    retirement = RES/'bloodborne_dw/retired-source-objects.json'
    assert not retirement.exists(), 'Preserve the first retirement evidence'
    objects=[]
    for path,number in EXPECTED.items():
        rows=[row for row in damaged if row['registry']=='bloodborne_dw:'+path]
        objects.append({'path':path,'registryId':'bloodborne_dw:'+path,'reservedNumber':number,
                        'variants':[row['variant'] for row in rows],
                        'reason':'USER_REQUEST_REMOVE_ALL_PREVIOUS_RED_DAMAGED_OBJECTS',
                        'sourceIssues':rows[0]['sourceIssues'],'offered':False})
    write(retirement,{'schemaVersion':1,'revision':'V12.2','numberPolicy':'Numbers remain reserved forever; do not reuse or restore on source reimport.',
                      'objects':objects,'diagnosticOnlyPathsNeverRegistered':57})
    catalogue_path=RES/'bloodborne_dw/remaining-catalogue.json'
    catalogue=read(catalogue_path)
    before=len(catalogue['objects'])
    catalogue['objects']=[row for row in catalogue['objects'] if row['path'] not in EXPECTED]
    assert before==1047 and len(catalogue['objects'])==1045
    write(catalogue_path,catalogue)
    debug_path=RES/'bloodborne_dw/debug_catalogue.json'
    debug=read(debug_path)
    retired_rows=[row for row in debug['entries'] if row['registryId'] in {row['registryId'] for row in objects}]
    assert len(retired_rows)==2
    debug['entries']=[row for row in debug['entries'] if row not in retired_rows]
    debug.setdefault('retiredEntries',[]).extend([{**row,'offered':False,'reason':'USER_REQUEST_REMOVE_DAMAGED_V12_2'} for row in retired_rows])
    write(debug_path,debug)
    files=[]
    for path in EXPECTED:
        files.extend([RES/'bloodborne_dw/composite'/(path+'.json'),
                      RES/'assets/bloodborne_dw/blockstates'/(path+'.json'),
                      RES/'assets/bloodborne_dw/models/item'/(path+'.json'),
                      RES/'assets/bloodborne_dw/models/block'/(path+'_empty.json'),
                      RES/'assets/bloodborne_dw/models/remaining/source/minecraft/block'/(path.removeprefix('source_')+'.json')])
    evidence=[]
    # Only explicit regular files under this resource tree; no directory cleanup.
    for file in files:
        resolved=file.resolve()
        assert resolved.is_relative_to(RES.resolve()) and resolved==file.absolute()
        info=file.lstat()
        assert stat.S_ISREG(info.st_mode) and not getattr(info,'st_file_attributes',0)&0x400
        evidence.append({'path':file.relative_to(ROOT).as_posix(),'sha256':hashlib.sha256(file.read_bytes()).hexdigest()})
    for file in files:
        file.unlink()
    write(OUT/'retirement.json',{'status':'PASS','baselineCommit':'4bcb19ce6',
          'previousGalleryDamagedActualVariants':3,'removedRegistryTypes':2,'newTypesRemaining':1045,
          'reservedIds':['93148','93149'],'removedObjects':objects,'removedFiles':evidence,
          'diagnosticOnlyPathsWereNeverModBlocks':57,'v12ObjectsChanged':0})


if __name__=='__main__':
    main()
