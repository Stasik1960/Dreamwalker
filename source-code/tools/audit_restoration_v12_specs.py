import json
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'restoration-evidence';rows=[]
for f in sorted((P/'src/architecture/resources/bloodborne_dw/composite').glob('*.json')):
 s=json.loads(f.read_text(encoding='utf-8-sig'))
 if s['id'].split(':')[-1].startswith('source_'):continue
 for i,v in enumerate(s['variants']):
  for pose,p in v['poses'].items():
   b=p.get('collision',[]);visible=v.get('visibleBounds');outside=0.0
   if visible:
    outside=max([0.0,*[max(visible['from'][k]-x['from'][k],x['to'][k]-visible['to'][k])/16 for x in b for k in range(3)]])
   rows.append({'id':s['id'],'variant':i,'pose':pose,'boxes':len(b),'maxBoxSpan':max([0,*[(x['to'][k]-x['from'][k])/16 for x in b for k in range(3)]]),'hasAuthoredVisualEnvelope':visible is not None,'outsideVisual':outside,'openable':s.get('openable',False)})
(D/'v12-composite-bounds.json').write_text(json.dumps({'rows':rows,'outliers':[r for r in rows if r['outsideVisual']>0.125]},ensure_ascii=False,indent=2),encoding='utf-8')
