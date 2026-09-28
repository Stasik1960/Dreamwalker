import gzip, hashlib, json, shutil, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'tools'))
import build_whole_owner_runtime as builder

RES = ROOT/'src/main/resources'
STAGE = ROOT/'build/catalog-city-storage-root-extension'
TARGET = STAGE/'resources'
CITY = 'bloodborne_blocks/city'
FILES = ['definitions.json', 'geometry.json', 'owner-meshes.json.gz', 'owner-runtime-mappings.json', 'reviewed-wall-family.json']

def read(root, name):
    data = (root/CITY/name).read_bytes()
    return json.loads(gzip.decompress(data) if name.endswith('.gz') else data)

def verify():
    before, after = {name: read(RES, name) for name in FILES}, {name: read(TARGET, name) for name in FILES}
    old_defs = {row['id']: row for row in before['definitions.json']['blocks']}
    new_defs = {row['id']: row for row in after['definitions.json']['blocks']}
    assert len(new_defs) == len(after['definitions.json']['blocks']), 'duplicate definition ID'
    assert all(new_defs.get(key) == value for key, value in old_defs.items()), 'existing definition changed'
    assert {k:v for k,v in before['definitions.json'].items() if k!='blocks'} == {k:v for k,v in after['definitions.json'].items() if k!='blocks'}
    for name, member in [('geometry.json','blocks'), ('owner-runtime-mappings.json','states')]:
        assert all(after[name][member].get(key) == value for key, value in before[name][member].items()), name+' changed existing records'
    assert {k:v for k,v in before['geometry.json'].items() if k!='blocks'} == {k:v for k,v in after['geometry.json'].items() if k!='blocks'}
    assert all(after['owner-meshes.json.gz'].get(key) == value for key,value in before['owner-meshes.json.gz'].items()), 'existing mesh changed'
    assert {k:v for k,v in before['reviewed-wall-family.json'].items() if k!='sourceMappingsSha256'} == {k:v for k,v in after['reviewed-wall-family.json'].items() if k!='sourceMappingsSha256'}, 'reviewed wall changed'
    added = sorted(new_defs.keys() - old_defs.keys())
    mappings = after['owner-runtime-mappings.json']['states']
    added_mappings = {key:value for key,value in mappings.items() if key not in before['owner-runtime-mappings.json']['states']}
    selected = {row['source'] for row in before['owner-runtime-mappings.json']['rejected'] if row['reason']=='historical root lacks physical cell'}
    assert set(added_mappings) == selected, 'not every proven missing-root mapping was added'
    assert after['owner-runtime-mappings.json']['rejected'] == [row for row in before['owner-runtime-mappings.json']['rejected'] if row['source'] not in selected], 'unrelated rejection changed'
    expected_ids = {'owner_'+hashlib.sha256(source.encode()).hexdigest()[:20] for source in added_mappings}
    assert set(added) == expected_ids == {row['id'].split(':')[1] for row in added_mappings.values()}, 'unexpected owner ID'
    assert set(after['geometry.json']['blocks']) - set(before['geometry.json']['blocks']) == set(added), 'unexpected geometry'
    assert set(after['owner-meshes.json.gz']) - set(before['owner-meshes.json.gz']) == {ident+'_'+facing for ident in added for facing in ('north','east','south','west')}, 'unexpected owner mesh'
    for ident in added:
        assert new_defs[ident]['whole_owner']
        for state in after['geometry.json']['blocks'][ident]['states'].values():
            assert state['cells']['0,0,0'] == {'collision': [], 'outline': []}, 'added root is not empty'
    old_lang = json.loads((RES/'assets/bloodborne_blocks/lang/ru_ru.json').read_bytes())
    new_lang = json.loads((TARGET/'assets/bloodborne_blocks/lang/ru_ru.json').read_bytes())
    assert all(new_lang.get(key)==value for key,value in old_lang.items()), 'existing translation changed'
    assert set(new_lang)-set(old_lang) == {'block.bloodborne_blocks.'+ident for ident in added}, 'unexpected translation'
    mutable_metadata = {CITY+'/'+name for name in FILES} | {'assets/bloodborne_blocks/lang/ru_ru.json'}
    allowed_new = {pattern.format(ident=ident) for ident in added for pattern in (
        'assets/bloodborne_blocks/models/block/city/{ident}.json',
        'assets/bloodborne_blocks/models/item/{ident}.json',
        'assets/bloodborne_blocks/blockstates/{ident}.json',
        'data/bloodborne_blocks/loot_tables/blocks/{ident}.json')}
    emitted_new = []
    for path in TARGET.rglob('*'):
        if not path.is_file():
            continue
        relative = path.relative_to(TARGET).as_posix()
        if relative in mutable_metadata:
            continue  # Field-level preservation was checked above.
        existing = RES/relative
        if existing.exists():
            assert existing.read_bytes()==path.read_bytes(), 'existing resource changed: '+str(existing)
        else:
            assert relative in allowed_new or (relative.startswith('assets/bloodborne_blocks/textures/') and relative.endswith('.png')), 'unexpected new resource: '+relative
            emitted_new.append(relative)
    assert allowed_new <= set(emitted_new), 'missing owner resources'
    result = {'newRegistryIds': added, 'newMappings': added_mappings,
              'allExistingDefinitionsGeometryMeshesMappingsWallArtPreserved': True,
              'allEmittedPathsCheckedAgainstProduction': True,
              'newResourcePaths': sorted(emitted_new),
              'newRootCellsHaveEmptyCollisionAndOutline': True,
              'remainingRejected': after['owner-runtime-mappings.json']['rejected']}
    (STAGE/'verification.json').write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    return result

if sys.argv[1] == 'prepare':
    TARGET.mkdir(parents=True, exist_ok=False)
    for name in FILES:
        destination = TARGET/CITY/name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(RES/CITY/name, destination)
    shutil.copytree(RES/'assets/bloodborne_blocks/textures', TARGET/'assets/bloodborne_blocks/textures')
    lang = Path('assets/bloodborne_blocks/lang/ru_ru.json')
    (TARGET/lang).parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(RES/lang, TARGET/lang)
    builder.RES, builder.CITY = TARGET, TARGET/CITY
    builder.build(storage_root_only=True)
    result = verify()
    print(json.dumps(result, ensure_ascii=False), flush=True)
elif sys.argv[1] == 'promote':
    result = verify()
    changed = []
    for path in sorted(TARGET.rglob('*')):
        if not path.is_file():
            continue
        target = RES/path.relative_to(TARGET)
        if target.exists() and target.read_bytes()==path.read_bytes():
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)
        changed.append(target.relative_to(RES).as_posix())
    (STAGE/'promoted-files.json').write_text(json.dumps(changed, indent=2)+'\n', encoding='utf8')
    print(json.dumps({'promoted': changed, 'newRegistryIds': result['newRegistryIds']}, ensure_ascii=False), flush=True)
else:
    raise ValueError('prepare or promote required')
