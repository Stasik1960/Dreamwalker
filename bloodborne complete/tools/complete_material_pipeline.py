"""Fresh deterministic v15 import: closed models, native tags, alpha and ALT wrappers."""
import argparse,collections,copy,hashlib,io,json,pathlib,zipfile
from PIL import Image
ROOT=pathlib.Path(__file__).resolve().parents[1]
PACK_SHA='29b31e3744af5794ae95ebe4c5474e3b70dfb08c143355bce815c08ceef09a36'
MAP_SHA='39e83f2941b768fd2ae9f84d8f57dc76aec78bd926dc5e38eab3059251d2bb08'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def save(p,v):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def key(ref):
 if ':' in ref:
  ns,ref=ref.split(':',1)
  if ns!='minecraft':raise ValueError('Unexpected namespace '+ns)
 return ref
def refs(d):
 if isinstance(d,dict):
  if 'model' in d:yield key(d['model'])
  for v in d.values():yield from refs(v)
 elif isinstance(d,list):
  for v in d:yield from refs(v)
class Importer:
 def __init__(self,vanilla,pack):
  self.raw={};self.pack={};self.repairs=[];self.cache={};self.profiles={}
  for path,dest in ((vanilla,self.raw),(pack,self.pack)):
   with zipfile.ZipFile(path) as z:
    if z.testzip():raise ValueError('ZIP CRC failure')
    for n in z.namelist():
     if n.startswith(('assets/minecraft/','data/minecraft/')) and not n.endswith('/'):dest[n]=z.read(n)
  self.raw.update(self.pack)
  self.models={p[len('assets/minecraft/models/'):-5]:self.parse(p,b) for p,b in self.raw.items() if p.startswith('assets/minecraft/models/') and p.endswith('.json')}
  self.states={p[len('assets/minecraft/blockstates/'):-5]:self.parse(p,b) for p,b in self.raw.items() if p.startswith('assets/minecraft/blockstates/') and p.endswith('.json')}
 def parse(self,path,raw):
  text=raw.decode('utf-8-sig').lstrip();v,end=json.JSONDecoder().raw_decode(text);tail=text[end:].strip()
  if tail:
   if not tail.startswith(('/*','//')):raise ValueError('Unexpected trailing JSON '+path)
   self.repairs.append({'path':path,'action':'ignored obsolete trailing commented model'})
  return v
 def closure(self,roots):
  result=set();pending=list(roots);missing=set()
  while pending:
   n=pending.pop()
   if n in result or n.startswith('builtin/'):continue
   result.add(n);d=self.models.get(n)
   if d is None:missing.add(n);continue
   if 'parent' in d:pending.append(key(d['parent']))
   pending.extend(refs(d.get('overrides',[])))
  return result,missing
 def resolved(self,n,seen=()):
  if n in self.cache:return self.cache[n]
  if n in seen:raise ValueError('Parent cycle '+n)
  d=self.models.get(n,{});parent=key(d.get('parent','builtin/generated'))
  base=self.resolved(parent,seen+(n,)) if parent in self.models else {}
  v=dict(base);v.update(d);v['textures']=dict(base.get('textures',{}));v['textures'].update(d.get('textures',{}));self.cache[n]=v;return v
 def textures(self,n):
  d=self.resolved(n);vars=d.get('textures',{});used=[f['texture'] for e in d.get('elements',[]) for f in e.get('faces',{}).values() if 'texture' in f]
  if not used:used=list(vars.values())
  result=set()
  for ref in used:
   seen=set()
   while ref.startswith('#'):
    if ref in seen:raise ValueError('Texture cycle '+n)
    seen.add(ref);ref=vars.get(ref[1:],'minecraft:missingno')
   result.add(key(ref))
  return result
 def profile(self,t):
  if t not in self.profiles:
   b=self.raw.get('assets/minecraft/textures/'+t+'.png')
   if b is None:self.profiles[t]={'missing':True,'alpha':False,'partial':False}
   else:
    hist=Image.open(io.BytesIO(b)).convert('RGBA').getchannel('A').histogram();self.profiles[t]={'alpha':bool(sum(hist[:255])),'partial':bool(sum(hist[1:255]))}
  return self.profiles[t]
 def rewrite(self,d,copied):
  d=copy.deepcopy(d)
  if 'parent' in d and key(d['parent']) in copied:d['parent']='bloodborne_dw:'+key(d['parent'])
  for var,ref in d.get('textures',{}).items():
   if not ref.startswith('#') and 'assets/minecraft/textures/'+key(ref)+'.png' in self.pack:d['textures'][var]='bloodborne_dw:'+key(ref)
  for o in d.get('overrides',[]):
   if key(o['model']) in copied:o['model']='bloodborne_dw:'+key(o['model'])
  return d
 def state(self,source,id):
  def apply(d,v):
   if isinstance(d,list):return [apply(x,v) for x in d]
   d=copy.deepcopy(d);d['model']='bloodborne_dw:'+('alt/'+id+'/' if v=='alt' else '')+key(d['model']);return d
  if 'variants' in source:return {'variants':{(k+',' if k else '')+'visual='+v:apply(d,v) for k,d in source['variants'].items() for v in ('base','alt')}}
  out=[]
  for part in source['multipart']:
   for v in ('base','alt'):out.append({'when':{'AND':[copy.deepcopy(part['when']),{'visual':v}]} if 'when' in part else {'visual':v},'apply':apply(part['apply'],v)})
  return {'multipart':out}
def main():
 p=argparse.ArgumentParser(description=__doc__)
 for name in ('map-analysis','map','resource-pack','vanilla-client','output'):p.add_argument('--'+name,type=pathlib.Path,required=True)
 p.add_argument('--closure-report',type=pathlib.Path,default=ROOT/'docs/input-model-closure.json');p.add_argument('--accept-source-missing',action='store_true');p.add_argument('--frozen-catalog',type=pathlib.Path,default=ROOT/'src/main/resources/assets/bloodborne_dw/catalog.json');a=p.parse_args()
 if a.output.exists():raise ValueError('Refusing stale output '+str(a.output))
 if sha(a.map)!=MAP_SHA or sha(a.resource_pack)!=PACK_SHA:raise ValueError('Wrong input SHA/version')
 analysis=json.loads(a.map_analysis.read_text(encoding='utf-8'));counts=next(x['block_counts'] for x in analysis['archives'] if x['sha256']==MAP_SHA)
 closure=json.loads(a.closure_report.read_text(encoding='utf-8'));sources=sorted(closure['affected_sources'],key=lambda s:(-counts.get('minecraft:'+s,0),s));ids={s:f'{i:05d}' for i,s in enumerate(sources,1)}
 frozen={b['source']:b for b in json.loads(a.frozen_catalog.read_text(encoding='utf-8'))['blocks']} if a.frozen_catalog.exists() else {}
 if frozen and (set(frozen)!={'minecraft:'+s for s in sources} or any(frozen['minecraft:'+s]['id']!=ids[s] for s in sources)):raise ValueError('Frozen catalog differs: explicit migration required, do not renumber IDs')
 im=Importer(a.vanilla_client,a.resource_pack);roots={n for n in im.models if 'assets/minecraft/models/'+n+'.json' in im.pack}
 for s in sources:
  roots.update(refs(im.states[s]))
  if 'item/'+s in im.models:roots.add('item/'+s)
 copied,missing=im.closure(roots)
 known=set(closure['missing_model_dependencies'])|{'block/pressure_plate_inventory'}
 if missing-known:raise ValueError('New missing dependencies '+str(missing-known))
 if missing and not a.accept_source_missing:raise ValueError('Supplied pack has missing models: '+str(sorted(missing)))
 ns=a.output/'assets/bloodborne_dw';rows=[];languages={'en_us':{},'ru_ru':{}}
 for n in sorted(copied):
  d=im.rewrite(im.models[n],copied) if n in im.models else {'parent':'minecraft:builtin/missing'}
  save(ns/'models'/f'{n}.json',d);save(ns/'models/alt'/f'{n}.json',{'parent':'bloodborne_dw:'+n})
 for path,b in im.pack.items():
  if path.startswith('assets/minecraft/textures/'):
   dest=ns/path.removeprefix('assets/minecraft/');dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(b)
 for s in sources:
  id=ids[s];state=im.states[s];models=list(refs(state));textures=set().union(*(im.textures(m) for m in models));profiles=[im.profile(t) for t in textures]
  alpha=any(x['alpha'] for x in profiles);partial=any(x['partial'] for x in profiles);layer='translucent' if partial else 'cutout' if alpha else 'solid'
  save(ns/'blockstates'/f'{id}.json',im.state(state,id));item=im.models.get('item/'+s)
  for m in set(models):save(ns/'models/alt'/id/f'{m}.json',{'parent':'bloodborne_dw:'+m})
  save(ns/'models/item'/f'{id}.json',im.rewrite(item,copied) if item else {'parent':'bloodborne_dw:'+models[0]})
  design=next((m.rsplit('/',1)[-1] for m in models if 'addon/' in m),s);name=design.replace('_',' ').title()+' ('+id+')'
  ru='Деталь: '+design.replace('_',' ')+' ('+id+')'
  if 'minecraft:'+s in frozen:name=frozen['minecraft:'+s]['name_en'];ru=frozen['minecraft:'+s]['name_ru']
  rows.append({'id':id,'source':'minecraft:'+s,'frequency':counts.get('minecraft:'+s,0),'name_en':name,'name_ru':ru,'has_alpha':alpha,'render_layer':layer,'base_models':sorted(set(models)),'textures':sorted(textures),'alt_model_paths':['alt/'+id+'/'+m for m in sorted(set(models))]})
  languages['en_us']['block.bloodborne_dw.'+id]=name;languages['ru_ru']['block.bloodborne_dw.'+id]=ru
 tags={}
 for kind in ('blocks','items'):
  prefix='data/minecraft/tags/'+kind+'/';cache={}
  def resolve(name,seen=()):
   if name in cache:return cache[name]
   if name in seen:raise ValueError('Tag cycle '+name)
   vals=json.loads(im.raw.get(prefix+name+'.json',b'{"values":[]}'))['values'];result=set()
   for v in vals:
    v=v['id'] if isinstance(v,dict) else v
    if v.startswith('#minecraft:'):result.update(resolve(v.split(':',1)[1],seen+(name,)))
    elif not v.startswith('#'):result.add(v)
   cache[name]=result;return result
  for path in sorted(im.raw):
   if path.startswith(prefix) and path.endswith('.json'):
    values=['bloodborne_dw:'+ids[v.split(':',1)[1]] for v in sorted(resolve(path[len(prefix):-5])) if v.startswith('minecraft:') and v.split(':',1)[1] in ids]
    if values:save(a.output/path,{'replace':False,'values':values});tags[path]=len(values)
 def loot(d):
  if isinstance(d,list):return [loot(v) for v in d]
  if not isinstance(d,dict):return d
  d={k:loot(v) for k,v in d.items()}
  if d.get('type')=='minecraft:item' and d.get('name','').startswith('minecraft:') and d['name'].split(':',1)[1] in ids:d['name']='bloodborne_dw:'+ids[d['name'].split(':',1)[1]]
  if d.get('condition')=='minecraft:block_state_property' and d.get('block','').startswith('minecraft:') and d['block'].split(':',1)[1] in ids:d['block']='bloodborne_dw:'+ids[d['block'].split(':',1)[1]]
  return d
 for s,id in ids.items():
  path='data/minecraft/loot_tables/blocks/'+s+'.json';save(a.output/'data/bloodborne_dw/loot_tables/blocks'/f'{id}.json',loot(json.loads(im.raw[path])) if path in im.raw else {'type':'minecraft:block','pools':[]})
 for lang,d in languages.items():d['itemGroup.bloodborne_dw.complete']='Bloodborne DW';save(ns/'lang'/f'{lang}.json',d)
 catalog={'schema_version':1,'inputs':{'map_v16':MAP_SHA,'resource_pack_v15':PACK_SHA},'blocks':rows};save(ns/'catalog.json',catalog)
 save(a.output/'import-report.json',{'catalog_records':len(rows),'copied_models':len(copied),'source_missing_models':sorted(missing),'unused_original_models':closure['unused_block_models'],'parser_repairs':im.repairs,'tags':tags,'textures':im.profiles,'alpha_layers':dict(collections.Counter(b['render_layer'] for b in rows))})
 print(json.dumps({'output':str(a.output),'blocks':len(rows),'models':len(copied),'missing_source':sorted(missing),'layers':dict(collections.Counter(b['render_layer'] for b in rows))}))
if __name__=='__main__':main()
