"""Match actual v16 states to original pack selectors; quantify source defects."""
import argparse,json,pathlib,collections
from complete_material_pipeline import Importer,refs
def condition(d,p):
 if 'OR' in d:return any(condition(x,p) for x in d['OR'])
 if 'AND' in d:return all(condition(x,p) for x in d['AND'])
 return all(p.get(k) in (str(v).lower() if isinstance(v,bool) else v).split('|') for k,v in d.items())
def main():
 p=argparse.ArgumentParser();p.add_argument('--vanilla-client',type=pathlib.Path,required=True);p.add_argument('--pack',type=pathlib.Path,required=True);p.add_argument('--analysis',type=pathlib.Path,required=True);p.add_argument('--catalog',type=pathlib.Path,required=True);p.add_argument('--output',type=pathlib.Path,required=True);a=p.parse_args()
 im=Importer(a.vanilla_client,a.pack);audit=next(x for x in json.loads(a.analysis.read_text(encoding='utf-8'))['archives'] if x['file']=='bbmc_v16_map (1).zip');cat=json.loads(a.catalog.read_text(encoding='utf-8'));families={b['source']:b['id'] for b in cat['blocks']};undefined=[];missing=[]
 for s,count in audit['block_state_counts'].items():
  source=s.split('[',1)[0]
  if source not in families:continue
  props=dict(kv.split('=',1) for kv in s.split('[',1)[1].rstrip(']').split(',')) if '[' in s else {}
  d=im.states[source.split(':')[1]];chosen=[]
  if 'variants' in d:
   for k,v in d['variants'].items():
    rules=dict(kv.split('=',1) for kv in k.split(',')) if k else {}
    if all(props.get(key)==value for key,value in rules.items()):chosen.extend(refs(v))
  else:
   for part in d['multipart']:
    if 'when' not in part or condition(part['when'],props):chosen.extend(refs(part['apply']))
  if not chosen:undefined.append({'id':families[source],'state':s,'cells':count})
  _,holes=im.closure(chosen)
  if holes:missing.append({'id':families[source],'state':s,'cells':count,'missing_models':sorted(holes)})
 r={'undefined_actual_states':undefined,'missing_dependencies_in_actual_states':missing,'undefined_cells':sum(x['cells'] for x in undefined),'missing_dependency_cells':sum(x['cells'] for x in missing),'scope':'exact selectors versus every actually occupied v16 state; family-wide dependencies are not treated as selected models'}
 a.output.write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps({'undefined_states':len(undefined),'undefined_cells':r['undefined_cells'],'missing_states':len(missing),'missing_cells':r['missing_dependency_cells']}))
if __name__=='__main__':main()
