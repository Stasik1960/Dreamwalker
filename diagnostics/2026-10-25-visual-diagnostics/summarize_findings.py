import hashlib,json,zipfile,collections
from pathlib import Path
O=Path(__file__).resolve().parent;j=Path('C:/Users/vakir/AppData/Local/Limacina/project/DreamWalker/mods/dreamwalker-bb-0.1.0-v12.7.4-visual-physics.1.jar');d=json.loads((O/'target-geometry.json').read_text(encoding='utf-8'))
with zipfile.ZipFile(j) as z:
 cat=json.loads(z.read('assets/bloodborne_dw/native_grid/catalog.json'));counts=[m['quadCount'] for m in cat['meshes']];empty=[i for i,c in enumerate(cat['cells']) if counts[c['mesh']]==0];out={'jarSha256':hashlib.sha256(j.read_bytes()).hexdigest(),'cellCount':len(cat['cells']),'emptyMeshCells':len(empty),'emptyMeshWithCollision':sum(bool(cat['shapes'][cat['cells'][i]['collision']]) for i in empty),'emptyMeshWithOutline':sum(bool(cat['shapes'][cat['cells'][i]['outline']]) for i in empty),'emptyMeshExamples':empty[:20]}
 layout=json.loads(z.read('assets/bloodborne_dw/native_grid/visual_physics.json'));out['emptyMeshPhysicsBank0']=sum(0<=layout['cellToPhysics'][i]<128 for i in empty);out['emptyMeshOpaque']=sum(cat['optics'][cat['cells'][i]['optics']]['opacity']==15 for i in empty)
 out['emptyMeshNativePhysics']=sum(layout['cellToPhysics'][i]>=0 for i in empty);out['emptyBank0Examples']=[{'visual':i,'physics':layout['cellToPhysics'][i],'cell':cat['cells'][i]} for i in empty if 0<=layout['cellToPhysics'][i]<128]
 for r in d['records'][:2]:
  out[str(r['pos'])]={'baseState':r['cell']['baseState'],'mesh':r['cell']['mesh'],'quadCount':r['geometry']['quadCount'],'materials':sorted({q['material']['sprite'] for q in r['geometry']['quads']}),'allCullNone':all(q['cull']==-1 for q in r['geometry']['quads']),'allWhiteVertices':all(set(q['colors'])=={'0xffffffff'} for q in r['geometry']['quads'])}
 # Preserve just the two baseline model definitions needed to reproduce the missing-surface proof.
 for n in ['assets/bloodborne_dw/models/remaining/source/minecraft/block/cobblestone.json','assets/bloodborne_dw/models/block/restored_slabs/restored_slab_9c3f5c1f30a2b9bb_bottom.json']:
  p=O/'reference-models'/Path(n).name;p.parent.mkdir(exist_ok=True);p.write_bytes(z.read(n))
(O/'findings.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(out,ensure_ascii=False,indent=2))
configs={}
for label,p in [('activeSodium',Path('C:/Users/vakir/AppData/Local/Limacina/project/DreamWalker/config/sodium-options.json')),('deliveredSodium',Path('C:/Users/vakir/Desktop/Dreamwalker-client-settings/config/sodium-options.json'))]:
 if p.exists():configs[label]={'path':str(p),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'settings':json.loads(p.read_text(encoding='utf-8-sig'))}
(O/'configuration-evidence.json').write_text(json.dumps(configs,ensure_ascii=False,indent=2),encoding='utf-8')
