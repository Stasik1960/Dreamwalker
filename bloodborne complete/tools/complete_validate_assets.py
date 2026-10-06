"""Validate committed numeric resources against 1.20.1; retain declared source defects."""
import argparse, collections, hashlib, json, pathlib, zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
SOURCE_TEXTURE_DEFECTS={'assets/minecraft/textures/block/'+s+'.png' for s in ('model_false/polished_diorite','model_false/unlit_lantern','texture','light_pressure_plate','model_false/window_4')}
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def walk(v):
 if isinstance(v,dict):
  for k,x in v.items():yield k,x;yield from walk(x)
 elif isinstance(v,list):
  for x in v:yield from walk(x)
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--resources',type=pathlib.Path,default=ROOT/'src/main/resources');p.add_argument('--vanilla-client',type=pathlib.Path,required=True);p.add_argument('--report',type=pathlib.Path,default=ROOT/'docs/asset-reference-verification.json');a=p.parse_args()
 root=a.resources;ns=root/'assets/bloodborne_dw';cp=ns/'catalog.json';blocks=load(cp)['blocks'];ids={b['id'] for b in blocks};errors=[];defects=set()
 models={p.relative_to(ns/'models').as_posix()[:-5]:load(p) for p in (ns/'models').rglob('*.json')};states={p.stem:load(p) for p in (ns/'blockstates').glob('*.json')}
 assert len(ids)==len(blocks)==580 and ids=={f'{i:05d}' for i in range(1,581)}
 if blocks!=sorted(blocks,key=lambda b:(-b['frequency'],b['source'].split(':',1)[1])):errors.append('Frozen frequency/tie order differs')
 if set(states)!=ids:errors.append('Blockstate catalog differs')
 with zipfile.ZipFile(a.vanilla_client) as vanilla:
  native=set(vanilla.namelist())
  def ref(value,kind,owner):
   if not isinstance(value,str) or value.startswith('#'):return
   n,s=value.split(':',1) if ':' in value else ('minecraft',value)
   if kind=='models' and s.startswith('builtin/'):return
   target='assets/'+n+'/'+kind+'/'+s+('.png' if kind=='textures' else '.json')
   if (root/target).is_file() or (n=='minecraft' and target in native):return
   if target in SOURCE_TEXTURE_DEFECTS:defects.add((owner,target))
   else:errors.append(owner+' -> '+target)
  for name,model in models.items():
   ref(model.get('parent'),'models',name)
   for k,v in walk(model):
    if k=='model':ref(v,'models',name)
   for v in model.get('textures',{}).values():ref(v,'textures',name)
  for id,state in states.items():
   for k,v in walk(state):
    if k=='model':ref(v,'models','blockstate/'+id)
   if 'visual' not in json.dumps(state):errors.append('Missing visual property '+id)
  for b in blocks:
   if b['alt_model_paths']!=['alt/'+b['id']+'/'+m for m in b['base_models']]:errors.append('ALT leaf paths differ '+b['id'])
   for m in b['alt_model_paths']:
    if m not in models:errors.append('Missing ALT model '+m)
   if 'item/'+b['id'] not in models:errors.append('Missing item model '+b['id'])
  done=set()
  def parents(name,visiting):
   if name in done or name not in models:return
   if name in visiting:raise ValueError('Parent cycle '+name)
   parent=models[name].get('parent','')
   if parent.startswith('bloodborne_dw:'):parents(parent.split(':',1)[1],visiting|{name})
   done.add(name)
  for name in models:parents(name,set())
 loot=list((root/'data/bloodborne_dw/loot_tables/blocks').glob('*.json'));tags=list((root/'data/minecraft/tags').rglob('*.json'))
 if {p.stem for p in loot}!=ids:errors.append('Loot table catalog differs')
 for path in loot+tags:
  for k,v in walk(load(path)):
   for x in (v if isinstance(v,list) else [v]):
    if isinstance(x,str) and x.startswith('bloodborne_dw:') and x.split(':',1)[1] not in ids:errors.append('Unknown numeric data reference '+str(path)+' -> '+x)
 for lang in ('ru_ru','en_us'):
  translations=load(ns/'lang'/f'{lang}.json')
  for id in ids:
   if 'block.bloodborne_dw.'+id not in translations:errors.append('Missing '+lang+' label '+id)
 report={'catalog_records':len(blocks),'blockstates':len(states),'models':len(models),'item_models':sum('item/'+id in models for id in ids),'loot_tables':len(loot),'native_tag_files':len(tags),'render_layers':dict(collections.Counter(b['render_layer'] for b in blocks)),'catalog_sha256':hashlib.sha256(cp.read_bytes()).hexdigest(),'declared_original_texture_defects':[{'model':m,'reference':t} for m,t in sorted(defects)],'unexpected_errors':sorted(set(errors)),'valid_except_declared_source_defects':not errors}
 a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps(report));raise SystemExit(1 if errors else 0)
if __name__=='__main__':main()
