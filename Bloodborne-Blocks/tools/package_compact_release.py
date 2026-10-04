"""Deliver a matched, checked compact mod/world pair and local modpack profiles."""
import argparse,csv,hashlib,json,shutil,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
def read(p):return json.loads(Path(p).read_bytes())
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def dump(p,v):Path(p).write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

def main():
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True)
    p.add_argument('--client-run',type=Path,required=True);p.add_argument('--server-run',type=Path,required=True)
    p.add_argument('--low-memory-run',type=Path);a=p.parse_args()
    out=a.output.resolve()
    if out.exists()and any(out.iterdir()):raise ValueError('output must be empty; choose a fresh directory')
    out.mkdir(parents=True,exist_ok=True)
    jar=ROOT/'build/libs/bloodborne-blocks-2.1.0-compact-gallery.1.jar'
    source=ROOT/'build/compact-city-gallery';catalog=read(ROOT/'src/main/resources/bloodborne_blocks/city/compact-catalog.json')
    log=ROOT/'build/compact-check-build-render.log'
    if 'BUILD SUCCESSFUL'not in log.read_text(errors='replace'):raise ValueError('full check/build not passed')
    pack=ROOT/'build/supplied-modpack-recovered.zip'
    config_rel='kubejs/config/probejs.json';config_source=ROOT/'docs/compact-gallery/client-config'/config_rel
    config_override={config_rel:sha(config_source)}
    runtime={}
    for label,run in [('client',a.client_run),('server',a.server_run),('lowMemory',a.low_memory_run)]:
        if run is None:continue
        proof=read(run/'launch-proof.json')
        if not proof['passed']or proof['jarSha256']!=sha(jar):raise ValueError('runtime is not bound to final mod '+label)
        if proof['packSha256']!=sha(pack):raise ValueError('runtime pack differs from packaged source '+label)
        if proof['client']!=(label!='server')or proof['distantHorizonsIncluded']:raise ValueError('wrong runtime role/profile '+label)
        if label=='server'and proof['heap']!='3G':raise ValueError('wrong server heap')
        if label=='lowMemory'and(proof['heap']!='4G'or proof['additionalMods']!={'ferritecore-6.0.1-fabric.jar':'c7ba1118a05b2da900d1c369fbe1017a3e3ec3cafe5e51398b851279b9becf24'}):raise ValueError('unverified low memory profile')
        if label=='lowMemory'and(proof.get('configurationOverrides')!=config_override or proof.get('probejsAutomaticDumpMessages')!=0):raise ValueError('untested low memory config')
        if proof['worldeditProof']['states']!=catalog['runtimeStatesIncludingHelper']:raise ValueError('wrong runtime state count')
        if proof.get('bakedModelModifierFailures')!=0:raise ValueError('model modifier failed '+label)
        if label!='server':
            models=proof.get('clientModelProof')or{}
            if len(models)!=catalog['publicBlocks']or any(v<=0 for v in models.values()):raise ValueError('invisible/missing client models '+label)
        runtime[label]=proof
    from test_compact_assets import check
    check(jar)
    with zipfile.ZipFile(jar)as z:
        for scope in ('city','logical'):
            for name in ('definitions.json','geometry.json'):
                rel=f'bloodborne_blocks/{scope}/{name}'
                if z.read(rel)!=(ROOT/'src/main/resources'/rel).read_bytes():raise ValueError('jar resources stale '+rel)
    preservation=read(ROOT/'build/compact-source-preservation.json')
    all_state=read(ROOT/'build/compact-gallery-all-state-preservation.json')
    helper=read(ROOT/'build/compact-helper-audit.json')
    inspection=read(ROOT/'build/compact-gallery-inspection.json')
    if not preservation['passed']or not all_state['passed']or not all_state['allStates']or not helper['ok']or helper['orphans']:raise ValueError('world proof failed')
    if any(r['classification']not in ('unchanged','service-unchanged')for r in inspection['specimens']):raise ValueError('gallery baseline not intact')
    from verify_compact_city import world_hashes
    persisted=world_hashes(source)
    for proof,baseline in ((preservation,ROOT/'build/compact-city-input'),(all_state,ROOT/'build/compact-city')):
        if Path(proof['baseline']).resolve()!=baseline.resolve()or Path(proof['target']).resolve()!=source.resolve():raise ValueError('wrong preservation world pair')
        if persisted!=proof['targetWorldHashes']:raise ValueError('world persistence changed after preservation proof')
    gallery=read(source/'editable-gallery-manifest.json')
    if gallery['coverage']['count']!=catalog['publicBlocks']+1:raise ValueError('gallery does not cover all public IDs plus helper')
    if len({r['sourceBlockId']for r in gallery['specimens']})!=len(gallery['specimens']):raise ValueError('multiple platforms per ID')
    if gallery['editContract']['spacing']!=3:raise ValueError('wrong spacing')
    model_ids={s['sourceBlockId']for s in gallery['specimens']if s['editable']}
    for label,proof in runtime.items():
        if label!='server'and set(proof['clientModelProof'])!=model_ids:raise ValueError('client model IDs differ from gallery '+label)
    conversion=read(source/'compact-city-conversion.json')
    # Later light metadata changes do not alter placement. Bind those structural
    # resources to the conversion inputs and keep the original input hashes.
    for rel in ('city/geometry.json','city/raw-source-mapping.json','logical/definitions.json','logical/geometry.json'):
        expected=next(v for k,v in conversion['inputHashes'].items()if k.replace('\\','/').endswith('src/main/resources/bloodborne_blocks/'+rel))
        if sha(ROOT/'src/main/resources/bloodborne_blocks'/rel)!=expected:raise ValueError('migration structure changed '+rel)
    for rel,digest in conversion['outputHashes'].items():
        if sha(ROOT/'build/compact-city'/rel)!=digest:raise ValueError('city changed after conversion/helper audit '+rel)
    shutil.copy2(jar,out/jar.name)
    world_name='Bloodborne-Compact-City-Gallery-2026-10-04'
    mapzip=out/(world_name+'.zip')
    files={p.relative_to(source).as_posix():sha(p)for p in source.rglob('*')if p.is_file()}
    if any(Path(f).parts[0]in ('playerdata','stats','advancements')for f in files):raise ValueError('private player files in package')
    note={'version':'2.1.0-compact-gallery.1','modSha256':sha(jar),'publicBlocks':catalog['publicBlocks'],
        'statesIncludingHelper':catalog['runtimeStatesIncludingHelper'],'wholeCityConverted':True,
        'oneTimeForcedOverlapCells':conversion['counts']['forcedSharedCollisionCells'],
        'helperAudit':helper,'preservationProofSha256':sha(ROOT/'build/compact-source-preservation.json'),
        'currentCityDefinitionsSha256':sha(ROOT/'src/main/resources/bloodborne_blocks/city/definitions.json'),
        'postConversionChange':'lighting/emissive metadata only; placement mapping and geometry hashes verified unchanged',
        'galleryManifestSha256':sha(source/'editable-gallery-manifest.json')}
    with zipfile.ZipFile(mapzip,'w',zipfile.ZIP_DEFLATED,compresslevel=6)as z:
        for rel in sorted(files):z.write(source/rel,world_name+'/'+rel)
        z.writestr(world_name+'/compact-mod-version.json',json.dumps(note,ensure_ascii=False,indent=2)+'\n')
    with zipfile.ZipFile(mapzip)as z:
        if z.testzip():raise ValueError('invalid world zip')
        for rel,digest in files.items():
            if hashlib.sha256(z.read(world_name+'/'+rel)).hexdigest()!=digest:raise ValueError('world zip changed '+rel)
    shots=out/'Screenshots';shots.mkdir(exist_ok=True)
    for label,run in [('client',a.client_run)]:
        if run:
            for i,shot in enumerate(sorted((run/'screenshots').glob('*.png'))):shutil.copy2(shot,shots/f'{label}-{i+1:02d}.png')
    if not list(shots.glob('*.png')):raise ValueError('no real game screenshots')
    reports=out/'Verification';reports.mkdir(exist_ok=True)
    for filename in ('compact-source-preservation.json','compact-city-preservation.json','compact-gallery-all-state-preservation.json','compact-helper-audit.json','compact-gallery-inspection.json'):
        shutil.copy2(ROOT/'build'/filename,reports/filename)
    (reports/'check-build.log').write_text('\n'.join(line.rstrip()for line in log.read_text(errors='replace').splitlines())+'\n',encoding='utf8')
    dump(reports/'runtime.json',runtime);dump(reports/'catalog.json',catalog)
    shutil.copy2(ROOT/'docs/compact-gallery/README.md',out/'README.md')
    materials={}
    for state,row in read(ROOT/'src/main/resources/bloodborne_blocks/city/raw-source-mapping.json')['states'].items():
        materials.setdefault(row['id'].removeprefix('bloodborne_blocks:'),[]).append(state)
    with(out/'Gallery-Objects.csv').open('w',encoding='utf-8-sig',newline='')as f:
        writer=csv.writer(f,delimiter=';');writer.writerow(['Numeric ID','Block ID','Editable','Teleport','Original state','Source materials'])
        for s in gallery['specimens']:
            writer.writerow([s['numericId'],'bloodborne_blocks:'+s['sourceBlockId'],s['editable'],s['tp'],s['originalFullState'],' | '.join(materials.get(s['sourceBlockId'],[]))])
    overlaps=read(ROOT/'build/compact-foreign-overlap-cases.json')
    if len(overlaps['cases'])!=conversion['counts']['foreignCollisionCells']:raise ValueError('incomplete foreign overlap inventory')
    shutil.copy2(ROOT/'build/compact-foreign-overlap-cases.json',reports/'compact-foreign-overlap-cases.json')
    with(out/'Foreign-Collision-Cases.csv').open('w',encoding='utf-8-sig',newline='')as f:
        writer=csv.writer(f,delimiter=';');writer.writerow(['Block ID','Root X','Root Y','Root Z','Foreign block','Cell X','Cell Y','Cell Z','Teleport'])
        for case in overlaps['cases']:
            x,y,z=case['root'];writer.writerow([case['id'],x,y,z,case['foreign'],*case['cell'],f'/tp @s {x} {y+2} {z}'])
    profiles={}
    for label,extra in [('Mods-Compact-2026-10-04.zip',None),('Client-Compact-LowMemory-2026-10-04.zip',a.low_memory_run)]:
        if 'LowMemory'in label and extra is None:continue
        preserved={};replaced=0;removed=[];path=out/label
        prefix='mods/'if extra else''
        with zipfile.ZipFile(pack)as old,zipfile.ZipFile(path,'w',zipfile.ZIP_STORED)as new:
            for item in old.infolist():
                name=Path(item.filename).name
                if item.filename.endswith('.jar')and'bloodborne'in name.lower():
                    replaced+=1;new.writestr(prefix+jar.name,jar.read_bytes());continue
                if extra and'distanthorizons'in name.lower():removed.append(item.filename);continue
                raw=old.read(item);destination=prefix+item.filename;new.writestr(destination,raw);preserved[destination]=hashlib.sha256(raw).hexdigest()
            if extra:
                for name,digest in runtime['lowMemory']['additionalMods'].items():
                    addon=extra/'mods'/name
                    if sha(addon)!=digest:raise ValueError('extra mod proof mismatch')
                    new.write(addon,prefix+name)
                if config_rel in new.namelist():raise ValueError('config conflicts with supplied archive')
                new.write(config_source,config_rel)
        if replaced!=1:raise ValueError('expected one replaced Bloodborne JAR')
        if extra and len(removed)!=1:raise ValueError('expected exactly one omitted Distant Horizons JAR')
        with zipfile.ZipFile(path)as z:
            if z.testzip():raise ValueError('invalid modpack zip')
            for name,digest in preserved.items():
                if hashlib.sha256(z.read(name)).hexdigest()!=digest:raise ValueError('other mod changed '+name)
            if extra and hashlib.sha256(z.read(config_rel)).hexdigest()!=config_override[config_rel]:raise ValueError('packaged config differs from tested config')
        profiles[label]={'unchangedFiles':len(preserved),'removed':removed,'localOnly':True,'sha256':sha(path),
            'runtimeVerified':bool(extra),'verifiedBy':['lowMemory']if extra else[],
            'preservedOnly':not bool(extra),'configurationOverrides':config_override if extra else{}}
    manifest={'version':note['version'],'catalog':{k:catalog[k]for k in ('publicBlocks','runtimeStatesIncludingHelper','previousRuntimeStatesIncludingHelper','sourceWholeFamilies')},
        'world':note,'runtime':runtime,'localModpackProfiles':profiles,
        'files':{p.relative_to(out).as_posix():{'bytes':p.stat().st_size,'sha256':sha(p)}for p in out.rglob('*')if p.is_file()and p.name not in ('package-manifest.json','SHA256.txt')}}
    dump(out/'package-manifest.json',manifest)
    (out/'SHA256.txt').write_text('\n'.join(v['sha256']+'  '+k for k,v in sorted(manifest['files'].items()))+'\n',encoding='utf8')
    print(json.dumps({'mod':jar.name,'map':mapzip.name,'publicBlocks':catalog['publicBlocks'],'galleryPlatforms':len(gallery['specimens'])},ensure_ascii=False))

if __name__=='__main__':main()
