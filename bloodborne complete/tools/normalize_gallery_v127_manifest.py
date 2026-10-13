"""Record completed V12.7 alias migrations in the gallery's presentation manifest.

Read-only for Minecraft region/entity data. Each changed row must be an exact
declared compatibility alias, retain its root/UUID and its rendered bounds.
"""
import argparse, hashlib, json, zipfile
from collections import defaultdict
from pathlib import Path
from world_io import RegionFile, Tag, compound, section_blocks
from verify_remaining_saved_world import owner
from verify_flat_gallery import plain
from prepare_gallery_v126_input import ArchiveGeometry, component_geometry
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--world',type=Path,required=True);a=p.parse_args();world=a.world.resolve()
assert world.is_relative_to(ROOT/'build')
path=world/'gallery-manifest.json';m=json.loads(path.read_text(encoding='utf8'))
rows=m['city']['placedOwners'];wanted=defaultdict(set)
for row in rows:
    pos=tuple(row['root']);wanted[pos[0]>>4,pos[2]>>4].add(pos)
actual={};states={}
for file in (world/'region').glob('r.*.*.mca'):
    for chunk in RegionFile.open(file).chunks():
        data=compound(chunk.nbt().root);asks=wanted.get((data['xPos'].value,data['zPos'].value),set())
        if not asks:continue
        for section in data.get('sections',Tag(9,[],10)).value:
            y=compound(section)['Y'].value;positions=[p for p in asks if p[1]>>4==y]
            if not positions:continue
            decoded=section_blocks(section)
            if decoded is None:continue
            palette,indices=decoded
            for pos in positions:
                state=compound(palette[indices[(pos[0]&15)+16*(pos[2]&15)+256*(pos[1]&15)]])
                states[pos]=(state['Name'].value,plain(state.get('Properties',Tag(10,{}))))
        for tag in data.get('block_entities',Tag(9,[],10)).value:
            raw=compound(tag)
            if 'resident' not in raw:continue
            position=tuple(raw[k].value for k in ('x','y','z'))
            if position in asks:actual[position]=(owner(raw['resident']),plain(raw['payload']))
changed=[]
with zipfile.ZipFile(ROOT/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7-final.jar') as jar:
    aliases=json.loads(jar.read('bloodborne_dw/imported-type-aliases-v127.json'))
    targets={member:g['primaryRegistryId'] for g in aliases['groups'] for member in g['members'] if member!=g['primaryRegistryId']}
    models=ArchiveGeometry(jar,Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'))
    for row in rows:
        position=tuple(row['root']);own,payload=actual[position];identifier,properties=states[position]
        assert own['uuid']==row['uuid'] and own['root']==row['root'] and own['registryId']==identifier
        if row['registry']==identifier and row['placedState']==properties:continue
        assert targets.get(row['registry'])==identifier, (row['registry'],identifier)
        updated=dict(row,registry=identifier,registryId=identifier,properties=properties,placedState=properties,payload=payload,
                     sourceShift=payload.get('SourceShift',[0,0,0]),mountY=payload.get('MountY',0))
        bounds=component_geometry(updated,jar,models);shift=m['city']['translation']
        expected=[bounds[i]+shift[i%3] for i in range(6)]
        assert all(abs(x-y)<1e-5 for x,y in zip(expected,row['bounds'])), (row['registry'],identifier,expected,row['bounds'])
        changed.append({'uuid':row['uuid'],'root':row['root'],'oldRegistry':row['registry'],'newRegistry':identifier,'oldState':row['placedState'],'newState':properties,'visualBoundsPreserved':True})
        updated['sourceOriginalRegistryId']=row['registry'];row.clear();row.update(updated)
m['completedV127AliasMigrations']={'status':'PASS_DECLARED_ALIAS_ROOT_UUID_AND_VISUAL_BOUNDS_PRESERVED','count':len(changed),'rows':changed,'regionOrEntityFilesModified':False}
path.write_text(json.dumps(m,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps({'status':m['completedV127AliasMigrations']['status'],'rows':len(changed),'regionOrEntityFilesModified':False}))
