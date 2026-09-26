"""Bounded building adapter for the TEST1 stone-brick wall, using existing meshes."""
import copy
import hashlib
import json
import zipfile
from pathlib import Path
from atomic_owner_groups import state

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
CITY=RES/'bloodborne_blocks/city'
ID='building_stone_brick_wall'
DIRS=('north','east','south','west')

def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(',',':'))+'\n',encoding='utf8')

def build():
    historical=ROOT.parent/'releases/Bloodborne-Blocks/bloodborne-blocks-2.0.1-mc1.20.1.jar'
    historical_sha=hashlib.file_digest(historical.open('rb'),'sha256').hexdigest()
    assert historical_sha=='e400442c1711b013dd73d2a7763fc7c34e778af05876d90b69c2bedced3c1212'
    with zipfile.ZipFile(historical) as jar:
        raw=jar.read('assets/bloodborne_blocks/blockstates/stone_brick_wall.json')
        multipart=json.loads(raw)['multipart']
        models={part['apply']['model'] for part in multipart}
        assert models=={'bloodborne_blocks:block/stone_brick_wall_'+part for part in ('post','side','side_tall')}
        for model in models:
            data=json.loads(jar.read('assets/bloodborne_blocks/models/'+model.split(':')[1]+'.json'))
            assert data['textures']=={'wall':'bloodborne_blocks:block/stone_bricks'}
        art_proof={'jarSha256':historical_sha,'multipartSha256':hashlib.sha256(raw).hexdigest(),
                   'models':sorted(models),'texture':'bloodborne_blocks:block/stone_bricks'}
    mappings=json.loads((CITY/'owner-runtime-mappings.json').read_bytes())['states']
    definitions=json.loads((CITY/'definitions.json').read_bytes())
    geometry=json.loads((CITY/'geometry.json').read_bytes())
    old={d['id']:d for d in definitions['blocks'] if d['id']!=ID}
    lookup={};aliases={}
    for source,mapping in sorted(mappings.items()):
        if not source.startswith('minecraft:stone_brick_wall['):continue
        props=dict(state(source)[1]);ident=mapping['id'].split(':')[1]
        aliases[ident]=source
        assert old[ident]['source']=='minecraft:stone_brick_wall' and old[ident]['whole_owner']
        for turn,facing in enumerate(DIRS):
            key=tuple(props[DIRS[(i-turn)%4]] for i in range(4))+(props['up'],)
            lookup.setdefault(key,(ident,facing))
    chosen={}
    for level in ('low','tall'):
        for mask in range(16):
            # The frozen low cross is authored without a central post. Do not invent it.
            up='false' if mask in (5,10) or (level=='low' and mask==15) else 'true'
            key=tuple(level if mask&(1<<i) else 'none' for i in range(4))+(up,)
            assert key in lookup,('unsupported',key)
            chosen[level+'_'+str(mask)]=lookup[key]
    definition=copy.deepcopy(old[chosen['low_0'][0]])
    definition.update(id=ID,creative=True,behavior='reviewed_wall',connection_family='reviewed_stone_brick_wall',
                      default={'facing':'north','connection':'low_0'},
                      properties={'facing':list(DIRS),'connection':list(chosen)},states={},models={})
    states={};variants={};proof={}
    for connection,(ident,base_facing) in chosen.items():
        proof[connection]={'owner':ident,'facing':base_facing,'source':aliases[ident]}
        for turn,facing in enumerate(DIRS):
            target_facing=DIRS[(DIRS.index(base_facing)+turn)%4]
            oldkey='facing='+target_facing
            key='connection='+connection+',facing='+facing
            profile=geometry['blocks'][ident]['states'][oldkey]
            assert set(profile['cells'])=={'0,0,0'},ident
            definition['states'][key]=old[ident]['states'][oldkey]
            definition['models'][key]=old[ident]['models'][oldkey]
            states[key]=copy.deepcopy(profile)
            variants[key]={'model':'bloodborne_blocks:block/city/'+ident}
    definitions['blocks']=[d for d in definitions['blocks'] if d['id']!=ID]+[definition]
    geometry['blocks'][ID]={'states':states}
    write(CITY/'definitions.json',definitions);write(CITY/'geometry.json',geometry)
    write(CITY/'reviewed-wall-family.json',{'id':ID,'aliases':aliases,'connections':proof,'artProof':art_proof,
          'sourceMappingsSha256':hashlib.sha256((CITY/'owner-runtime-mappings.json').read_bytes()).hexdigest(),
          'unsupported':['low four-way junction WITH central post; existing postless cross used'],
          'scope':'Only stone_brick_wall from REPAIR TEST1. Existing owner IDs/models remain unchanged.'})
    write(RES/f'assets/bloodborne_blocks/blockstates/{ID}.json',{'variants':variants})
    write(RES/f'assets/bloodborne_blocks/models/item/{ID}.json',{'parent':'bloodborne_blocks:block/city/'+chosen['low_0'][0]})
    write(RES/f'data/bloodborne_blocks/loot_tables/blocks/{ID}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'bloodborne_blocks:'+ID}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    for language,label in [('ru_ru','Кирпичная ограда — соединяемая'),('en_us','Connecting stone-brick wall')]:
        path=RES/f'assets/bloodborne_blocks/lang/{language}.json'
        data=json.loads(path.read_bytes()) if path.exists() else {}
        data['block.bloodborne_blocks.'+ID]=label;write(path,data)
    print('Existing source aliases:',len(aliases),'building states:',len(states),'new meshes: 0')

if __name__=='__main__':build()
