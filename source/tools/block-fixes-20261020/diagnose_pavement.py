from common import *

def main():
 log_to('diagnose-pavement')
 allrows=read(OUT/'materials.json')['slabs'];selected=read(OUT/'selected-materials.json')['slabs'];keys={(r['registryId'],r['variant']) for r in selected}
 static=read(RES/'bloodborne_dw/static-architecture-v1274.json');poses=static['poses'];result=[]
 for row in allrows:
  if (row['registryId'],row['variant']) in keys:continue
  d=read(RES/'bloodborne_dw/composite'/(row['registryId'].split(':')[1]+'.json'));v=d['variants'][row['variant']];parts=v['poses']['closed']['parts'];m=model(row['top']['model'])
  pp=[p for p in poses if p['registryId']==row['registryId'] and int(p['properties'].get('variant',0))+16*int(p['properties'].get('art_bank',0))==row['variant']]
  result.append({'source':row['registryId'],'variant':row['variant'],'name':row['name'],'bounds':row['bounds'],'kind':row['kind'],'model':row['top']['model'],'elements':len(m.get('elements',[])),'elementBounds':[{'from':e['from'],'to':e['to'],'faces':list(e.get('faces',{})),'rotation':e.get('rotation')} for e in m.get('elements',[])],'sourcePose':pp[:1],'partCount':len(parts)})
 write(OUT/'remaining-slab-candidates.json',result)
 for r in result:print(json.dumps({k:v for k,v in r.items() if k not in ['sourcePose','elementBounds']},ensure_ascii=False))
 print('EXCLUDED',len(result))
 more=[]
 for path in (RES/'bloodborne_dw/composite').glob('source_addon_slabs*.json'):
  d=read(path)
  for i,v in enumerate(d['variants']):
   if (d['id'],i) in keys:continue
   parts=v['poses']['closed']['parts'];b,t=bounds(parts);pp=[p for p in poses if p['registryId']==d['id'] and int(p['properties'].get('variant',0))+16*int(p['properties'].get('art_bank',0))==i]
   row={'source':d['id'],'variant':i,'name':d['displayName'],'bounds':b,'parts':parts,'poseRotations':sorted({int(p['properties'].get('rotation',0)) for p in pp}),'poseShifts':sorted({tuple(p['shift']) for p in pp}),'sourceRefs':v.get('sourceStateRefs',[]),'topTexture':t['texture'] if t else None};more.append(row)
 write(OUT/'pavement-family-remaining.json',more)
 for r in more:print('FAMILY',json.dumps(r,ensure_ascii=False))
if __name__=='__main__':main()
