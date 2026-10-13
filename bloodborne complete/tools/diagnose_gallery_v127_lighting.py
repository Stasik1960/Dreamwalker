"""Read-only lighting evidence from the saved gallery, not a world repair."""
import collections,json
from pathlib import Path
from world_io import RegionFile,Tag,compound

ROOT=Path(__file__).resolve().parents[1]
WORLD=ROOT/'build/runtime-client-v127-new-gallery-client6-reopen/saves/prototype-fixture'
manifest=json.loads((WORLD/'gallery-manifest.json').read_text(encoding='utf8'))
optics=json.loads((ROOT/'src/architecture/resources/bloodborne_dw/imported-source-lighting-v127.json').read_text(encoding='utf8'))
by_pose={(r['registryId'],r['variant'],r['open']):r for r in optics['objects']}
chunks={}
for f in (WORLD/'region').glob('r.*.*.mca'):
    for chunk in RegionFile.open(f).chunks():
        raw=compound(chunk.nbt().root)
        chunks[raw['xPos'].value,raw['zPos'].value]={compound(s)['Y'].value:compound(s) for s in raw.get('sections',Tag(9,[],10)).value}
def light(pos):
    x,y,z=pos;s=chunks.get((x>>4,z>>4),{}).get(y>>4,{})
    i=(x&15)+16*(z&15)+256*(y&15)
    def nibble(key):
        data=s.get(key)
        return None if data is None else (data.value[i>>1]>>(4*(i&1)))&15
    return {'sky':nibble('SkyLight'),'block':nibble('BlockLight')}
rows=[]
for r in manifest['exhibits']:
    if r['kind']!='architecture':continue
    pos=r['root'];state=r['placedState'];pose=int(state.get('variant',0))+16*int(state.get('art_bank',0))
    row={'id':r['id'],'registry':r['registry'],'origin':r['origin'],'root':pos,'state':state,'savedRootLight':light(pos),
        'neighborLight':{name:light([pos[i]+delta[i] for i in range(3)]) for name,delta in [('up',(0,1,0)),('north',(0,0,-1)),('south',(0,0,1)),('west',(-1,0,0)),('east',(1,0,0))]}}
    raw=by_pose.get((r['registry'],pose,state.get('open')=='true'))
    row['sourceOpticsStates']=None if raw is None else raw['sources']
    rows.append(row)
result={'status':'PASS_READ_ONLY_LIGHTING_EVIDENCE','world':str(WORLD),'countsByOriginAndRootSky':{str(k):v for k,v in collections.Counter((r['origin'],r['savedRootLight']['sky']) for r in rows).items()},'rows':rows,'note':'Absent light arrays mean implicit data; native client probe still required. No world or production file modified.'}
(ROOT/'build/v127-black-blocks-saved-light.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps({k:v for k,v in result.items() if k!='rows'},ensure_ascii=False))
print(json.dumps([r for r in rows if r['id'] in {'90002','90049','90083','90143','90247','93153','93116','93232','93233'}],ensure_ascii=False,indent=2))
