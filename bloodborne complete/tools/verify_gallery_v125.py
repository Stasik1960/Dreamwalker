"""Independent read-only NBT proof: main catalogue, source assemblies, city."""
import argparse,hashlib,json,math
from collections import defaultdict
from pathlib import Path
import zipfile
import verify_flat_gallery as base
from verify_gallery_v124 import grid,visual_saved
from world_io import RegionFile,Tag,compound,read_nbt,section_blocks
from verify_remaining_saved_world import owner,contribution,typed_hash
from prepare_gallery_v125_input import ArchiveGeometry,component_geometry

ROOT=Path(__file__).resolve().parents[1]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def near(a,b):assert len(a)==len(b) and all(abs(x-y)<1e-5 for x,y in zip(a,b)),(a,b)
def main():
    p=argparse.ArgumentParser();p.add_argument('--world',type=Path,required=True);p.add_argument('--manifest',type=Path,required=True);p.add_argument('--production-jar',type=Path,required=True);p.add_argument('--report',type=Path,required=True);p.add_argument('--compare',type=Path);a=p.parse_args()
    m=base.read(a.manifest);assert m['schema']=='dreamwalker-flat-gallery-v125' and m['status']=='PASS_AUTHORED_REQUIRES_PRODUCTION_REOPEN'
    assert a.world.resolve().is_relative_to((ROOT/'build').resolve()) and m['productionJarSha256']==sha(a.production_jar)
    before={str(f.relative_to(a.world)):sha(f) for f in base.saved_files(a.world)}
    mainrows=m['exhibits'];groups=m['reviewPlatforms'];city=m['city'];parts=list(mainrows)
    for group in groups:
        assert not group['registrationsMerged'] and not group['doorPropertiesChanged']
        parts.extend({**c,'key':group['key']+'/'+str(i),'kind':'architecture'} for i,c in enumerate(group['components']))
    parts.extend({**c,'key':'city/'+c['uuid'],'kind':'architecture'} for c in city['placedOwners'])
    arch=[r for r in parts if r['kind']=='architecture'];expected={r['uuid']:r for r in arch};assert len(expected)==len(arch)
    floors={};positions={tuple(r['root']) for r in arch};states={};residents={};cells=defaultdict(list);signs={};entities={}
    for r in mainrows+groups:
        x,z,w,d=r['platform'];floor=r.get('floor','minecraft:purple_concrete' if r['section']=='doors' else 'minecraft:magenta_concrete')
        for X in range(x,x+w):
            for Z in range(z,z+d):floors[X,63,Z]=floor
        positions.add(tuple(r['labelPos']))
        if r.get('surfaceTileReplacesPlatformCell'):
            assert r['root'][1]==63 and r['kind']=='architecture' and r['origin']!='V12'
            floors[tuple(r['root'])]=r['registry']
    x,z,w,h=city['platform']
    for X in range(x,x+w):
        for Z in range(z,z+h):floors[X,63,Z]='minecraft:gray_concrete'
    passthrough={}
    for r in city['passThroughBlocks']:
        pos=tuple(int(r['relativePosition'][i]+city['translation'][i]) for i in range(3));passthrough[pos]=r;positions.add(pos)
    positions.update(floors)
    wanted=defaultdict(set)
    for pos in positions:wanted[pos[0]>>4,pos[2]>>4].add(pos)
    for file in (a.world/'region').glob('r.*.*.mca'):
        for chunk in RegionFile.open(file).chunks():
            data=compound(chunk.nbt().root);asks=wanted.get((data['xPos'].value,data['zPos'].value),set())
            for section in data.get('sections',Tag(9,[],10)).value:
                y=compound(section)['Y'].value;sample=[pos for pos in asks if pos[1]>>4==y]
                if not sample:continue
                decoded=section_blocks(section)
                if decoded is None:continue
                palette,indices=decoded
                for pos in sample:
                    s=compound(palette[indices[(pos[0]&15)+16*(pos[2]&15)+256*(pos[1]&15)]])
                    states[pos]={'registry':s['Name'].value,'properties':base.plain(s.get('Properties',Tag(10,{})))}
            for tag in data.get('block_entities',Tag(9,[],10)).value:
                raw=compound(tag);pos=tuple(raw[k].value for k in ('x','y','z'))
                if 'resident' in raw:
                    own=owner(raw['resident']);assert own['uuid'] not in residents;residents[own['uuid']]={'position':list(pos),'owner':own,'typedSha256':typed_hash(tag),'nbt':base.plain(tag)}
                if raw.get('id',Tag(8,'')).value.endswith(':sign'):signs[pos]=base.plain(tag)
                for c in raw.get('owners',Tag(9,[],10)).value:
                    value=contribution(c);cells[value['owner']['uuid']].append({'position':list(pos),'storage':'be','value':value})
    ledger=a.world/'data/bloodborne_dw_composite_owners.dat'
    if ledger.exists():
        for tag in compound(compound(read_nbt(ledger).root)['data']).get('cells',Tag(9,[],10)).value:
            raw=compound(tag);pos=list(raw['pos'].value)
            for c in raw.get('owners',Tag(9,[],10)).value:
                value=contribution(c);cells[value['owner']['uuid']].append({'position':pos,'storage':'ledger','value':value})
    for file in (a.world/'entities').glob('r.*.*.mca'):
        if not file.stat().st_size:continue
        for chunk in RegionFile.open(file).chunks():
            for tag in compound(chunk.nbt().root).get('Entities',Tag(9,[],10)).value:entities[base.entity_uuid(tag)]=base.plain(tag)
    assert set(residents)==set(expected),'Unplanned/missing saved residents '+str(set(residents)^set(expected))
    for pos,floor in floors.items():assert states[pos]['registry']==floor,(pos,floor,states[pos])
    for pos,r in passthrough.items():assert states[pos]['registry']==r['registryId'] and states[pos]['properties']==r['properties'],(pos,r,states[pos])
    stable=[]
    for uuid,row in sorted(expected.items()):
        actual=residents[uuid];assert actual['position']==row['root'] and actual['owner']['registryId']==row['registry']
        assert states[tuple(row['root'])]['registry']==row['registry'] and states[tuple(row['root'])]['properties']==row['placedState']
        owned=sorted(cells[uuid],key=lambda c:(c['position'],c['storage']))
        for c in owned:assert c['value']['owner']['registryId']==row['registry'] and c['value']['owner']['root']==row['root']
        stable.append({'key':row['key'],'uuid':uuid,'root':row['root'],'state':states[tuple(row['root'])],'residentTypedSha256':actual['typedSha256'],'ownerCells':owned})
    for row in mainrows+groups:
        sign=signs[tuple(row['labelPos'])];assert sign['is_waxed'] and sign['front_text']==sign['back_text']
        assert json.loads(sign['front_text']['messages'][0])['text']==row.get('name',row.get('russianLabel'))[:26]
    for row in mainrows:
        if row['kind']!='architecture':
            e=entities[row['uuid']];assert e['id']==('minecraft:item_frame' if row['kind']=='item_frame' else row['registry'])
            stable.append({'key':row['key'],'uuid':row['uuid'],'entity':{k:e[k] for k in ('id','UUID','Pos','Rotation','Item','Facing','Fixed','Scale','Open','Locked','VerticalOffset') if k in e}})
    with zipfile.ZipFile(a.production_jar) as jar:
        retirement=base.read(ROOT/'src/architecture/resources/bloodborne_dw/imported-style-retirements.json')
        aliases=json.loads(jar.read('bloodborne_dw/imported-type-aliases.json'));remaining=json.loads(jar.read('bloodborne_dw/remaining-catalogue.json'))
        retired={r for g in aliases['groups'] for r in g['members'] if r!=g['primaryRegistryId']}|{r for g in retirement['retirements'] for r in g['aliases']}
        working={'bloodborne_dw:'+r['path'] for r in remaining['objects']}-retired
        new=[r['registry'] for r in mainrows if r.get('origin')!='V12' and r['kind']=='architecture'];assert len(new)==len(set(new)) and set(new)==working
        original=base.read(ROOT/'docs/gallery-v12.1/catalogue.json');frozen={r['key']:(r['registry'],r['item'],r['id']) for r in original['exhibits'] if r['origin']=='V12' and r['section']!='error'}
        current={r['key']:(r['registry'],r['item'],r['id']) for r in mainrows if r['origin']=='V12' and r['kind']!='item_frame'};assert frozen==current
        models=ArchiveGeometry(jar,Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'))
        geometryChecks=0
        for group in groups+[{'components':city['placedOwners'],'translation':city['translation']}]:
            t=group['translation']
            for component in group['components']:
                local=dict(component);bounds=component_geometry(local,jar,models);near([bounds[i]+t[i%3] for i in range(6)],component['bounds']);geometryChecks+=1
        for row in arch:
            payload=residents[row['uuid']]['nbt']['payload']
            saved={'registryId':row['registry'],'properties':row['placedState'],'relativePosition':row['root'],
                   'payload':payload,'sourceShift':payload.get('SourceShift',[0,0,0]),'mountY':payload.get('MountY',0)}
            bounds=component_geometry(saved,jar,models);near(bounds,row['bounds']);geometryChecks+=1
    stable.sort(key=lambda r:r['key']);compared=False
    if a.compare:
        old=base.read(a.compare);assert old['status']=='PASS' and old['productionJarSha256']==sha(a.production_jar);assert old['stableExhibits']==stable,'Saved state/UUID/payload/owner geometry changed after production reopen';compared=True
    assert all(sha(a.world/rel)==digest for rel,digest in before.items()),'Verifier changed world'
    result={'schema':'dreamwalker-flat-gallery-v125-disk-verification','status':'PASS','productionJarSha256':sha(a.production_jar),'world':str(a.world),'productionReopenCompared':compared,'verifiedExhibits':len(parts),'stableExhibits':stable,
            'mainGrid':grid(mainrows),'functionalCatalogue':{'status':'PASS','newWorkingTypes':len(new),'styleRetiredWorkingTypes':len(retirement['retirements']),'originalV12CatalogueEqual':True},
            'savedReviewAndCityGeometryChecks':geometryChecks,'reviewPlatforms':len(groups),'cityOwners':len(city['placedOwners']),'cityPassthroughStatesVerified':len(passthrough),'citySourceCoverage':city['sourceCoverageCount'],'cityAllSourceCellsClassified':city['allSourceCellsClassified'],'platformCells':len(floors),'inputFilesUnchanged':True,'visualAcceptance':'NOT_RUN_BY_DISK_PROOF'}
    base.write(a.report,result);print(json.dumps({k:v for k,v in result.items() if k!='stableExhibits'},ensure_ascii=False))

if __name__=='__main__':
    try:main()
    except BaseException as error:
        import sys
        if '--report' in sys.argv:base.write(Path(sys.argv[sys.argv.index('--report')+1]),{'schema':'dreamwalker-flat-gallery-v125-disk-verification','status':'FAIL','failure':repr(error)})
        raise
