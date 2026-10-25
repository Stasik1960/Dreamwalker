"""Append only missing baked base surfaces; never renumber a cell or edit a map."""
import base64,hashlib,json,struct,sys
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'fix-evidence/export';A=P/'src/architecture/resources/assets/bloodborne_dw/native_grid'
canonical=lambda v:json.dumps(v,sort_keys=True,separators=(',',':'))
sys.path.insert(0,str(P/'tools/native-grid'))
from export_cells import TextureOpacity
opacity=TextureOpacity(P/'src/architecture/resources')
cat=json.loads((A/'catalog.json').read_text());layout=json.loads((P/'src/nativevisual/resources/assets/bloodborne_dw/native_grid/visual_physics.json').read_text());proof=json.loads((D/'candidate-cells.json').read_text());assert hashlib.sha256((A/'catalog.json').read_bytes()).hexdigest()==proof['catalogSha256']==layout['catalogSha256']
assert json.loads((D/'baked/export-result.json').read_text())=={'status':'PASS','worldOpened':False}
native={canonical(r['state']):r for r in map(json.loads,(D/'baked/native-geometry.jsonl').read_text().splitlines())};materials={canonical(m):i for i,m in enumerate(cat['materials'])}
def signature(material,face,vertices):return (material['sprite'],face,tuple(sorted(tuple(round(v[a],6) for a in range(3)) for v in vertices)))
def signed(n):return ((int(n)+(1<<31))%(1<<32))-(1<<31)
def encode(q):
 assert all(-1e-6<=v[a]<=1.000001 for v in q['vertices'] for a in range(3))
 flags=int(q.get('shade',True))|int(q.get('ao',True))*2|int(q.get('emissive',False))*4
 material=q['material']
 if canonical(material) not in materials and material['layer'] in ('cutout','cutout_mipped') and opacity.opaque_quad(q):
  material={**material,'layer':'solid'}
 out=struct.pack('>ibbiB',materials[canonical(material)],int(q.get('cull',-1)),q['face'],q.get('tint',-1),flags)
 return out+b''.join(struct.pack('>fffffiii',*map(float,v[:5]),*map(signed,v[5:])) for v in q['vertices'])
needed={cat['cells'][i]['mesh'] for i in proof['cells']};raws={}
with (A/'meshes.bin').open('rb') as f:
 magic,count=struct.unpack('>II',f.read(8));assert magic==0x4e475131 and count==len(cat['meshes'])
 for mesh in range(count):
  n=struct.unpack('>I',f.read(4))[0];assert n==cat['meshes'][mesh]['quadCount']
  if mesh in needed:raws[mesh]=f.read(n*139)
  else:f.seek(n*139,1)
 assert not f.read(1)
groups=[];keys={};visuals={};rows=[]
for visual in proof['cells']:
 c=cat['cells'][visual]
 if layout['cellToPhysics'][visual]<0:continue
 source=native[canonical(c['baseState'])];assert source.get('nativeVisualComplete',True) and source.get('renderType')=='MODEL' and len(source['quads'])==6
 raw=raws[c['mesh']];present=set()
 for offset in range(0,len(raw),139):
  mat,cull,face,tint,flags=struct.unpack_from('>ibbiB',raw,offset);vertices=[struct.unpack_from('>fffffiii',raw,offset+11+v*32) for v in range(4)];present.add(signature(cat['materials'][mat],face,vertices))
 missing=[q for q in source['quads'] if signature(q['material'],q['face'],q['vertices']) not in present]
 if not missing:continue
 extra=b''.join(map(encode,missing));key=(c['mesh'],hashlib.sha256(extra).hexdigest())
 if key not in keys:keys[key]=len(groups);groups.append({'mesh':c['mesh'],'quads':base64.b64encode(extra).decode('ascii')})
 visuals[str(visual)]=keys[key];rows.append({'visual':visual,'mesh':c['mesh'],'addedQuads':len(missing),'base':c['baseState']})
out={'schemaVersion':1,'catalogSha256':proof['catalogSha256'],'groups':groups,'visuals':visuals};(A/'visual-repairs.json').write_text(canonical(out)+'\n',encoding='utf-8')
assert all(str(i) in visuals for i in (40837,23514))
# A complete expected base must be present after the supplement for every repaired cell.
for r in rows:
 c=cat['cells'][r['visual']];assert r['addedQuads']>0 and layout['cellToPhysics'][r['visual']]>=0
receipt={'status':'PASS','candidateCells':len(proof['cells']),'repairedVisuals':len(rows),'sharedSupplementGroups':len(groups),'addedQuads':sum(r['addedQuads'] for r in rows),'catalogAndPhysicsUnchanged':True,'mapRewritten':False,'resourceSha256':hashlib.sha256((A/'visual-repairs.json').read_bytes()).hexdigest(),'rows':rows};(P/'fix-evidence/visual-repair-receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
print('Render supplements:',len(rows),'visuals,',len(groups),'shared groups')
