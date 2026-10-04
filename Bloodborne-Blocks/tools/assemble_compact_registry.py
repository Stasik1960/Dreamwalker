"""Install the staged whole-source catalog while retaining approved objects."""
import argparse, copy, gzip, hashlib, json, shutil
from pathlib import Path
from build_unified_registry import stream_members,serial,protected_model_dependencies

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
BASELINE_JAR=ROOT.parent/'releases/Bloodborne-Blocks/unified-gallery-optimized-2026-10-04/bloodborne-blocks-2.1.0-unified-gallery.3.jar'
BASELINE_SHA='3e73a9bb6380fa30448f6705f12ccc664c5892d9d0ab52729571cc827da000ab'

def assemble(source, *, apply=False):
    source=Path(source).resolve();old=RES/'bloodborne_blocks/city'
    backup=ROOT/'build/compact-old/resources/bloodborne_blocks'
    if not backup.exists():
        import zipfile
        if hashlib.sha256(BASELINE_JAR.read_bytes()).hexdigest()!=BASELINE_SHA:raise ValueError('wrong frozen compact baseline JAR')
        backup.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(BASELINE_JAR)as archive:
            for name in archive.namelist():
                if not name.startswith('bloodborne_blocks/')or name.endswith('/'):continue
                relative=Path(name).relative_to('bloodborne_blocks')
                if relative.is_absolute()or '..'in relative.parts:raise ValueError('unsafe baseline resource')
                destination=backup/relative;destination.parent.mkdir(parents=True,exist_ok=True)
                destination.write_bytes(archive.read(name))
    if hashlib.sha256((backup/'city/definitions.json').read_bytes()).hexdigest()!='8ff4d792584dc05682a39fe063927c4e6bdadd53667e409d7b49b90a269c415e':
        raise ValueError('snapshot is not the frozen pre-compact catalog')
    original=json.loads((backup/'city/definitions.json').read_bytes())
    old_geo=json.loads((backup/'city/geometry.json').read_bytes())
    keep=[copy.deepcopy(d)for d in original['blocks']if not d.get('unified')]
    current=json.loads((old/'definitions.json').read_bytes())
    current_keep=[d for d in current['blocks']if not d.get('unified')]
    if serial(current_keep)!=serial(keep):raise ValueError('approved definitions changed since snapshot')
    current_geo=json.loads((old/'geometry.json').read_bytes())
    def retained_physics(data):
        return {d['id']:{s:data.get('profiles',{}).get(g.get('ref'),g)
            for s,g in data['blocks'][d['id']]['states'].items()}for d in keep}
    if serial(retained_physics(current_geo))!=serial(retained_physics(old_geo)):
        raise ValueError('approved geometry changed since snapshot')
    for name in ('definitions.json','geometry.json','contracts-v2.json'):
        current_path=old.parent/'logical'/name
        if current_path.read_bytes()!= (backup/'logical'/name).read_bytes():
            raise ValueError('approved logical resource changed since snapshot '+name)
    new_data=json.loads((source/'definitions.json').read_bytes());new=new_data['blocks']
    definitions={**original,'blocks':keep+new}
    definitions['emissive_textures']={**original.get('emissive_textures',{}),**new_data.get('emissive_textures',{})}
    if len({d['id']for d in definitions['blocks']})!=len(definitions['blocks']):raise ValueError('ID collision')
    new_geo=json.loads((source/'geometry.json').read_bytes())
    geometry={**old_geo,'blocks':{d['id']:old_geo['blocks'][d['id']]for d in keep},'profiles':{}}
    for block in geometry['blocks'].values():
        for g in block['states'].values():
            if 'ref'in g:geometry['profiles'][g['ref']]=old_geo['profiles'][g['ref']]
    geometry['blocks'].update(new_geo['blocks']);geometry['profiles'].update(new_geo['profiles'])
    meshes_needed={m for d in keep for m in(d.get('models')or{}).values()}
    stage=ROOT/'build/compact-generated/resources'
    stage.mkdir(parents=True,exist_ok=True);city=stage/'bloodborne_blocks/city';city.mkdir(parents=True,exist_ok=True)
    all_meshes={}
    for name in ('meshes.json.gz','owner-meshes.json.gz'):
        for mid,mesh in stream_members(backup/'city'/name):
            if mid in meshes_needed:all_meshes[mid]=mesh
    if meshes_needed-all_meshes.keys():raise ValueError('approved art missing')
    for mid,mesh in stream_members(source/'owner-meshes.json.gz'):all_meshes[mid]=mesh
    for name,data in (('definitions.json',definitions),('geometry.json',geometry)):(city/name).write_bytes(serial(data))
    (city/'owner-meshes.json.gz').write_bytes(gzip.compress(serial(all_meshes),mtime=0))
    (city/'meshes.json.gz').write_bytes(gzip.compress(b'{}\n',mtime=0))
    shutil.copy2(source/'raw-source-mapping.json',city/'raw-source-mapping.json')
    logical=json.loads((backup/'logical/definitions.json').read_bytes())['blocks']
    count=sum(len(d['states'])for d in keep+new+logical)+1
    previous=sum(len(d['states'])for d in original['blocks']+logical)+1
    summary={'schemaVersion':1,'basis':'beta.1 approved logical palette plus separate dry bush and current final-document fixes',
        'retainedIds':sorted(d['id']for d in keep+logical),'sourceWholeFamilies':len(new),
        'retiredRegistryIds':sorted(d['id']for d in original['blocks']if d.get('unified')),
        'publicBlocks':len(keep+new+logical),'runtimeStatesIncludingHelper':count,
        'previousRuntimeStatesIncludingHelper':previous,'removedPrivateVariants':True,
        'sourceCatalogSummary':json.loads((source/'summary.json').read_bytes()),
        'worldMigrationRequired':True,'normalPlacementCollisionsUnchanged':True}
    (city/'compact-catalog.json').write_bytes(serial(summary))
    wall=json.loads((backup/'city/reviewed-wall-family.json').read_bytes())
    building=next(d for d in keep if d['id']=='building_stone_brick_wall')
    wall['compactStates']={s:{'mesh':building['models'][s],'state':v}for s,v in building['states'].items()}
    wall['compactProofSourceSha256']=hashlib.sha256((backup/'city/definitions.json').read_bytes()).hexdigest()
    (city/'reviewed-wall-family.json').write_bytes(serial(wall))
    # The frozen owner closure evidence keeps its original source coordinates.
    # Its executable outputs now refer to whole compact objects; former targets
    # are provenance, never fallback registry entries.
    historical=json.loads((backup/'city/owner-runtime-mappings.json').read_bytes())
    raw=json.loads((source/'raw-source-mapping.json').read_bytes())['states']
    live=copy.deepcopy(historical);live['states']={}
    live['migrationPolicy']='whole source object at the same historical root; first authored weighted model'
    for source_state,previous_target in historical['states'].items():
        if source_state not in raw:raise ValueError('missing compact historical source '+source_state)
        target=copy.deepcopy(raw[source_state])
        if target.get('rootOffset')!=[0,0,0]:raise ValueError('historical source root moved '+source_state)
        ident=target['id'].split(':')[1]
        key=','.join(k+'='+str(v)for k,v in sorted(target['properties'].items()))
        physics=geometry['blocks'][ident]['states'][key]
        physics=geometry['profiles'].get(physics.get('ref'),physics)
        target['shape']=sorted([list(map(int,p.split(',')))for p in physics['cells']])
        target['historicalTarget']=copy.deepcopy(previous_target)
        live['states'][source_state]=target
    (city/'owner-runtime-mappings.json').write_bytes(serial(live))
    evidence_dir=ROOT/'docs/compact-gallery';evidence_dir.mkdir(parents=True,exist_ok=True)
    wall_evidence={'sourceDefinitionsSha256':wall['compactProofSourceSha256'],
        'definition':building,'geometry':retained_physics(old_geo)['building_stone_brick_wall'],
        'historicalMapping':historical,'historicalProof':json.loads((backup/'city/reviewed-wall-family.json').read_bytes())}
    (evidence_dir/'reviewed-wall-evidence.json.gz').write_bytes(gzip.compress(serial(wall_evidence),mtime=0))
    if apply:
        # Retired registry assets have no fallback registrations. The world must
        # be migrated offline with the matching compact catalog before opening.
        dependencies=protected_model_dependencies({'blocks':keep})
        retired={d['id']for d in original['blocks']if d.get('unified')}
        valid={d['id']for d in keep+new+logical}
        # Also remove unregistered assets from previous staged generator runs.
        # They are not in the frozen registry and cannot be found via `retired`.
        for directory in ('assets/bloodborne_blocks/blockstates','assets/bloodborne_blocks/models/item',
                          'data/bloodborne_blocks/loot_tables/blocks'):
            for file in (RES/directory).glob('owner_unified_*.json'):
                if file.stem not in valid:file.unlink()
        for file in (RES/'assets/bloodborne_blocks/models/block/city').glob('owner_unified_*.json'):
            if file.stem not in valid and file.stem not in dependencies:file.unlink()
        for ident in retired:
            for rel in (f'assets/bloodborne_blocks/blockstates/{ident}.json',
                        f'assets/bloodborne_blocks/models/item/{ident}.json',
                        f'data/bloodborne_blocks/loot_tables/blocks/{ident}.json'):
                (RES/rel).unlink(missing_ok=True)
            if ident not in dependencies:(RES/f'assets/bloodborne_blocks/models/block/city/{ident}.json').unlink(missing_ok=True)
        for file in source.rglob('*'):
            if file.is_file()and file.relative_to(source).parts[0]in {'assets','data'}:
                dest=RES/file.relative_to(source);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(file,dest)
        for file in city.iterdir():shutil.copy2(file,old/file.name)
        langfile=RES/'assets/bloodborne_blocks/lang/ru_ru.json';lang=json.loads(langfile.read_bytes())
        for entry in list(lang):
            if entry.startswith('block.bloodborne_blocks.owner_unified_')and entry[len('block.bloodborne_blocks.'):]not in valid:lang.pop(entry)
        for d in new:lang['block.bloodborne_blocks.'+d['id']]='Цельная модель: '+', '.join(d['originalcarrier'])
        langfile.write_bytes(serial(lang))
    return summary

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--source',type=Path,required=True);p.add_argument('--apply',action='store_true');a=p.parse_args()
    result=assemble(a.source,apply=a.apply)
    print(json.dumps({k:v for k,v in result.items()if k!='sourceCatalogSummary'},ensure_ascii=False,indent=2))
