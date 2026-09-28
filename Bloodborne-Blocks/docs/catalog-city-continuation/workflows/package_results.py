import hashlib, json, shutil, subprocess, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
RES=ROOT/'src/main/resources'
RUN=ROOT/'build/catalog-city-diagnostic-20260927-storage-fixed'
DOC=ROOT/'docs/catalog-city-continuation'
OUT=ROOT/'releases/repair-catalog-city-1'
VERSION='2.1.0-repair-catalog.1'

def sha(path):
    result=hashlib.sha256()
    with path.open('rb') as stream:
        for data in iter(lambda:stream.read(1024*1024),b''):
            result.update(data)
    return result.hexdigest()

OUT.mkdir(parents=True,exist_ok=True)
build=json.loads((ROOT/'build/test3-verification/catalog-city-final-check-build.json').read_bytes())
assert build['exitCode']==0, 'final check/build failed'
metadata_build=json.loads((ROOT/'build/test3-verification/catalog-city-metadata-assemble.json').read_bytes())
assert metadata_build['exitCode']==0, 'final metadata assemble/server checks failed'
summary=json.loads((RUN/'summary.json').read_bytes())
assert summary['preservation']['result']=='PASS' and summary['helpers']['ok'] and summary['secondPass']['byteIdentical']
jar_name=f'bloodborne-blocks-{VERSION}.jar'
shutil.copy2(ROOT/'build/libs'/jar_name,OUT/jar_name)
with zipfile.ZipFile(OUT/jar_name) as jar:
    mismatches=[]; checked=0
    for path in sorted(RES.rglob('*')):
        if not path.is_file(): continue
        name=path.relative_to(RES).as_posix()
        if name=='fabric.mod.json':
            assert json.loads(jar.read(name))['version']==VERSION
            continue
        content=path.read_bytes()
        try: packed=jar.read(name)
        except KeyError: mismatches.append(name+' missing'); continue
        if name=='bloodborne_blocks.mixins.json':
            generated=json.loads(packed)
            refmap=generated.pop('refmap',None)
            assert refmap=='bloodborne-blocks-refmap.json' and refmap in jar.namelist()
            assert generated==json.loads(content), 'unexpected mixin metadata change'
        elif content!=packed: mismatches.append(name+' differs')
        checked+=1
    assert not mismatches, mismatches[:10]
    required_classes=('GuiItemBounds','GuiItemBounds$Cache','GuiItemModel','CityVariantItemModel','ArchitectureCreativeCatalog')
    assert all('dev/dreamwalker/bloodborneblocks/'+name+'.class' in jar.namelist() for name in required_classes)

world_name='Bloodborne-City-Catalog-DIAGNOSTIC-NOT-READY.zip'
world_files={path.relative_to(RUN/'world').as_posix():path for path in sorted((RUN/'world').rglob('*')) if path.is_file()}
with zipfile.ZipFile(OUT/world_name,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as world:
    world.writestr('READ-FIRST.txt','DIAGNOSTIC ONLY. Protected composite and legacy registry gates FAIL. This is not a fully converted world. Do not replace a working save or load/save it using the new JAR; unresolved IDs can be lost. See REPORT.md.\n')
    for name,path in world_files.items():
        world.write(path,'Bloodborne-City-DIAGNOSTIC/'+name)
with zipfile.ZipFile(OUT/world_name) as world:
    assert world.testzip() is None
    for name,path in world_files.items():
        assert hashlib.sha256(world.read('Bloodborne-City-DIAGNOSTIC/'+name)).hexdigest()==sha(path)

art_name='Bloodborne-Current-Art-Catalog-1.zip'
with zipfile.ZipFile(OUT/art_name) as art:
    assert art.testzip() is None
    manifest=json.loads(art.read('manifest.json'))
    for row in manifest['files']:
        payload=art.read(row['path'])
        assert len(payload)==row['bytes'] and hashlib.sha256(payload).hexdigest()==row['sha256'],row['path']
    exported={row['path'] for row in manifest['files']}
    expected={'runtime/'+path.relative_to(RES).as_posix() for path in (RES/'assets').rglob('*') if path.is_file() and (('/models/' in path.as_posix() and path.suffix=='.json') or ('/textures/' in path.as_posix() and (path.suffix=='.png' or path.name.endswith('.png.mcmeta'))))}
    assert expected <= exported, 'missing current art assets'
    for name in expected:
        assert art.read(name)==(RES/name.removeprefix('runtime/')).read_bytes(),name
    for name in ('ALT-Art-Kit.zip','ALT-ResourcePack-Template.zip'):
        import io
        with zipfile.ZipFile(io.BytesIO(art.read('artist-kit/'+name))) as nested:
            assert nested.testzip() is None,name

frozen=subprocess.check_output(['git','diff','HEAD','--name-only','--','Bloodborne-Blocks/src/main/resources/bloodborne_blocks/logical','Bloodborne-Blocks/src/main/resources/assets/bloodborne_blocks/models/block/logical'],cwd=ROOT.parent,text=True)
assert not frozen.strip(),'TEST3 logical/world/window resources changed'
parity={'version':VERSION,'jarRuntimeResourceFiles':checked,'jarResourceParity':'PASS','worldArchiveFiles':len(world_files),'worldArchiveMatchesDiagnosticCopy':True,'artPayloadFilesVerified':len(manifest['files']),'allCurrentArtAssetsVerified':len(expected),'nestedArtistKits':'PASS','test3LogicalResourcesUnchangedFromHead':True,'head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'branch':subprocess.check_output(['git','branch','--show-current'],cwd=ROOT,text=True).strip(),'productionReadyWorld':False,'fullWorldLaunch':'NOT_RUN: protected/composite and legacy/unknown registry gates FAIL; avoid lossy registry loading'}
(DOC/'artifact-verification.json').write_text(json.dumps(parity,indent=2)+'\n',encoding='utf8')
hashes={name:{'bytes':(OUT/name).stat().st_size,'sha256':sha(OUT/name)} for name in (jar_name,world_name,art_name)}
(OUT/'artifacts.json').write_text(json.dumps({'status':'DIAGNOSTIC_ONLY_CITY_FAIL','artifacts':hashes,'verification':parity},indent=2)+'\n',encoding='utf8')
(OUT/'SHA256SUMS.txt').write_text(''.join(data['sha256']+'  '+name+'\n' for name,data in hashes.items()),encoding='utf8')
print(json.dumps({'artifacts':hashes,'verification':parity},ensure_ascii=False))
