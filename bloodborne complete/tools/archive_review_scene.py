"""Package only a normally saved new-build test scene, never the original city."""
import hashlib, json, zipfile
from pathlib import Path
from world_io import RegionFile, compound, section_blocks, block_state_key

ROOT=Path(__file__).resolve().parents[1]

def digest(data): return hashlib.sha256(data).hexdigest()

def main():
    report=json.loads((ROOT/'reports/SERVER_MINIMAL.json').read_text(encoding='utf-8'))
    assert report['status']=='PASS' and report['exit_code']==0 and report['original_world_loaded'] is False
    scene=Path(report['run_directory'])/'isolated-smoke-world'
    assert scene.is_relative_to(ROOT/'build') and (scene/'level.dat').is_file()
    cells=[];entities=[];empty_region_files=[]
    for path in sorted((scene/'region').glob('*.mca')):
        for chunk in RegionFile(path.read_bytes()).chunks():
            fields=compound(chunk.nbt().root)
            for section in fields.get('sections').value:
                decoded=section_blocks(section)
                if not decoded: continue
                palette,indices=decoded
                keys=[block_state_key(s) for s in palette]
                applicable={i for i,key in enumerate(keys) if key.startswith('bloodborne_dw:prototype_ladder')}
                if not applicable: continue
                sy=compound(section)['Y'].value
                for i,pi in enumerate(indices):
                    if pi not in applicable: continue
                    cells.append({'pos':[fields['xPos'].value*16+i%16,sy*16+i//256,fields['zPos'].value*16+i//16%16],'state':keys[pi]})
    for path in sorted((scene/'entities').glob('*.mca')):
        if path.stat().st_size==0:
            empty_region_files.append(path.relative_to(scene).as_posix())
            continue
        for chunk in RegionFile(path.read_bytes()).chunks():
            fields=compound(chunk.nbt().root)
            for entity in fields.get('Entities').value:
                data=compound(entity)
                entities.append({'id':data['id'].value,'pos':[n.value for n in data['Pos'].value]})
    assert len(cells)==12, 'Scene must contain exactly 12 ladder samples'
    assert {'bloodborne_rp:tree1','bloodborne_rp:hunterlamp'}.issubset({e['id'] for e in entities}), entities
    destination=ROOT/'build/prototype/Prototype-new-placement-scene.zip'
    destination.parent.mkdir(parents=True,exist_ok=True)
    records=[]
    with zipfile.ZipFile(destination,'w',zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(scene.rglob('*')):
            if not path.is_file() or path.name=='session.lock': continue
            data=path.read_bytes();relative=path.relative_to(scene).as_posix()
            info=zipfile.ZipInfo('Prototype-new-placement-scene/'+relative,(2026,10,7,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
            archive.writestr(info,data)
            records.append({'path':relative,'bytes':len(data),'sha256':digest(data)})
    with zipfile.ZipFile(destination) as archive:
        for record in records: assert digest(archive.read('Prototype-new-placement-scene/'+record['path']))==record['sha256']
    result={'schema':'dreamwalker-review-scene-v1','status':'PACKAGED_NOT_VISUALLY_ACCEPTED',
        'kind':'Separate newly generated Creative test scene, not migrated city',
        'source_server_report':'reports/SERVER_MINIMAL.json','jar_sha256':report['artifact_sha256'],
        'normal_server_stop':'PASS','zip_byte_verification':'PASS','files':records,'ladder_samples':cells,'entities':entities,
        'omitted_files':['session.lock'],'preserved_empty_entity_region_files':empty_region_files,
        'spawn':['-2','64','0'],'spawn_radius':0,
        'visuals':'NOT_RUN','creative_ui':'NOT_RUN','output':str(destination),'sha256':digest(destination.read_bytes())}
    (ROOT/'reports/REVIEW_SCENE.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'scene':str(destination),'ladder_samples':len(cells),'entities':len(entities),'sha256':result['sha256']}))

if __name__=='__main__': main()
