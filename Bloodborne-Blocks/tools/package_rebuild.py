"""Package the verified rebuilt world locally. Never uploads private save data."""
from pathlib import Path
import argparse, hashlib, json, shutil, zipfile
from world_io import RegionFile, compound, lighting_cache_errors

ROOT=Path(__file__).resolve().parents[1]


def sha(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()


def package(world,delivery):
    world=world.resolve();delivery=delivery.resolve()
    if not (world/'level.dat').is_file():raise ValueError('Missing prepared world')
    checks=json.loads((ROOT/'docs/world-verification-v2.json').read_text(encoding='utf-8'))
    functional=json.loads((ROOT/'docs/world-functional-repair-v2.json').read_text(encoding='utf-8'))
    interactions=json.loads((ROOT/'docs/world-interactions-v2.json').read_text(encoding='utf-8'))
    assert not checks['errors'] and checks['counts']['chunks_verified']==10009
    assert functional['applied'] and functional['aggregate']['unresolved']==0
    assert interactions['issueCount']==0, 'Door audit still has unresolved issues'
    assert (ROOT/'docs/world-interactions-v2.json').stat().st_mtime>(ROOT/'docs/world-functional-repair-v2.json').stat().st_mtime, 'Doors must be audited after repairs'
    assert (ROOT/'docs/world-verification-v2.json').stat().st_mtime>(ROOT/'docs/world-functional-repair-v2.json').stat().st_mtime, 'World must be verified AFTER final edits'
    # Old verification reports predate the Starlight cache check. Do not allow
    # packaging the original 2.0 map merely because its block data passed.
    for path in world.glob('region/*.mca'):
        for chunk in RegionFile.open(path).chunks():
            assert not lighting_cache_errors(compound(chunk.nbt().root)), 'Invalid saved light cache in '+path.name
    jar=ROOT/'build/libs/bloodborne-blocks-2.0.0.jar'
    with zipfile.ZipFile(jar) as z:
        assert z.testzip() is None
        assert 'bloodborne_blocks/v2/meshes.json' not in z.namelist(), 'Obsolete uncompressed mesh resource'
        for resource in ('fabric.mod.json','bloodborne_blocks/v2/definitions.json','bloodborne_blocks/v2/geometry.json','bloodborne_blocks/v2/meshes.json.gz'):
            assert z.read(resource)==(ROOT/'src/main/resources'/resource).read_bytes(), 'Stale JAR resource '+resource
    delivery.mkdir(parents=True,exist_ok=True)
    jar_out=delivery/'bloodborne-blocks-2.0.0-mc1.20.1.jar';shutil.copy2(jar,jar_out)
    archive=delivery/'Ether-Bloodborne-2.0.zip'
    with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=4,allowZip64=True) as z:
        for path in sorted(world.rglob('*')):
            if path.is_file() and path.name!='session.lock':z.write(path,'Ether-Bloodborne-2.0/'+path.relative_to(world).as_posix())
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None
        assert 'Ether-Bloodborne-2.0/level.dat' in z.namelist()
    manifest={'files':{p.name:{'bytes':p.stat().st_size,'sha256':sha(p)} for p in (jar_out,archive)},
              'verification':checks['counts'],'minecraft_started':False,'remote_game_test_available':False}
    (delivery/'SHA256.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(manifest,ensure_ascii=False),flush=True)


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('world',type=Path);p.add_argument('delivery',type=Path);a=p.parse_args();package(a.world,a.delivery)
