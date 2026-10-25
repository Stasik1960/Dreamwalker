"""Derive physics states from the accepted catalogue; never re-export geometry."""
import collections, hashlib, json, math, struct
from pathlib import Path

P=Path(__file__).resolve().parents[2]
T=P.parent.parent/'DW-visual-physics-20261024'
OLD=P/'src/architecture/resources/assets/bloodborne_dw'
OUT=P/'src/nativevisual/resources/assets/bloodborne_dw'
def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8')
raw=(OLD/'native_grid/catalog.json').read_bytes()
catalog_sha=hashlib.sha256(raw).hexdigest()
assert catalog_sha=='c2f9e74e54c0298c68b0c8d765a1c595baed949b3d99e1a42d686535c55c07ff'
doc=json.loads(raw);del raw
cells=doc['cells'];roles={v['cell'] for o in doc['wholeObjects'] for v in o['cells']}
layers=[];ambients=[]
with (OLD/'native_grid/meshes.bin').open('rb') as f:
    assert f.read(4)==b'NGQ1' and struct.unpack('>i',f.read(4))[0]==len(doc['meshes'])
    for row in doc['meshes']:
        n=struct.unpack('>i',f.read(4))[0];assert n==row['quadCount']
        layer='solid';ambient=True
        for _ in range(n):
            material=struct.unpack('>i',f.read(4))[0];f.seek(6,1);flags=f.read(1)[0];f.seek(128,1)
            if _==0:ambient=bool(flags&2)
            other=doc['materials'][material]['layer']
            layer=max((layer,other),key={'solid':0,'cutout_mipped':1,'cutout':2,'translucent':3}.get)
        layers.append(layer);ambients.append(ambient)
    assert not f.read(1)
fields=('collision','outline','culling','climbing','optics','fluid','localFunction','alt')
mapping=[];representatives=[];signatures={};physics_layers=[];physics_ambient=[]
for i,row in enumerate(cells):
    if i in roles:mapping.append(-1);continue
    signature=json.dumps([*[row.get(k) for k in fields],layers[row['mesh']],ambients[row['mesh']]],sort_keys=True,separators=(',',':'))
    if signature not in signatures:
        signatures[signature]=len(representatives);representatives.append(i);physics_layers.append(layers[row['mesh']]);physics_ambient.append(ambients[row['mesh']])
    mapping.append(signatures[signature])
retained=collections.defaultdict(list)
for i in sorted(roles):retained[i//128].append(i%128)
layout={'schemaVersion':1,'catalogSha256':catalog_sha,'cellToPhysics':mapping,
        'representatives':representatives,'physicsLayers':physics_layers,'physicsAmbient':physics_ambient,
        'wholeBanks':{str(k):v for k,v in sorted(retained.items())}}
write(OUT/'native_grid/visual_physics.json',layout)
texture_map={'v'+str(i):m['sprite'] for i,m in enumerate(doc['materials'])}
write(OUT/'models/block/native_physics_marker.json',{'textures':texture_map})
atlas_path=P/'src/architecture/resources/assets/minecraft/atlases/blocks.json'
atlas=json.loads(atlas_path.read_text(encoding='utf-8'))
existing={source.get('resource') for source in atlas['sources'] if source.get('type')=='minecraft:single'}
for sprite in sorted(set(texture_map.values())-existing):atlas['sources'].append({'type':'minecraft:single','resource':sprite})
write(atlas_path,atlas)
for bank in range(math.ceil(len(representatives)/128)):
    write(OUT/f'blockstates/native_physics_{bank}.json',{'variants':{'part='+str(p):{'model':'bloodborne_dw:block/native_physics_marker'} for p in range(128)}})
    write(OUT/f'models/item/native_physics_{bank}.json',{'parent':'bloodborne_dw:block/native_physics_marker'})
whole_states=0
for bank,parts in sorted(retained.items()):
    lo,hi=min(parts),max(parts)
    if lo==hi:
        if hi<127:hi+=1
        else:lo-=1
    whole_states+=hi-lo+1
    variants={}
    for p in range(lo,hi+1):
        actual=bank*128+p if bank*128+p in roles else bank*128+parts[0]
        sprite='minecraft:block/stone';mesh=cells[actual]['mesh']
        # Shared marker includes all material dependencies for exact atlas coverage.
        name=f'native_grid_{bank}_{p}'
        write(OUT/f'models/block/{name}.json',{'parent':'bloodborne_dw:block/native_physics_marker'})
        variants['part='+str(p)]={'model':'bloodborne_dw:block/'+name}
    write(OUT/f'blockstates/native_grid_{bank}.json',{'variants':variants})
    write(OUT/f'models/item/native_grid_{bank}.json',{'parent':f'bloodborne_dw:block/native_grid_{bank}_{parts[0]}'})
report={'status':'COMPILED_LAYOUT_NOT_GAMEPLAY','catalogSha256':catalog_sha,'ordinaryVisualTypes':len(cells)-len(roles),
        'physicalTypesIncludingRenderLayer':len(representatives),'physicalBlockStates':math.ceil(len(representatives)/128)*128,
        'retainedWholeRoles':len(roles),'retainedWholeBanks':len(retained),'retainedWholeBlockStates':whole_states,
        'totalNewRegisteredStates':math.ceil(len(representatives)/128)*128+whole_states,'oldStates':math.ceil(len(cells)/128)*128,
        'layoutSha256':hashlib.sha256((OUT/'native_grid/visual_physics.json').read_bytes()).hexdigest(),
        'worldWrites':0,'geometryChanged':False,'opticalLayerInSignature':True}
write(T/'layout-report.json',report)
print(json.dumps(report))
