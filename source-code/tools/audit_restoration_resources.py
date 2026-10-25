import hashlib,json,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'restoration-evidence';before=None
for f in Path('C:/Users/vakir/Desktop/client_mods').glob('*.jar'):
 with zipfile.ZipFile(f) as z:
  if 'fabric.mod.json' in z.namelist():
   info=json.loads(z.read('fabric.mod.json'))
   if info.get('id')=='bloodborne_dw':before=f;break
assert before
after=P/'build-native-visual/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.6.jar'
def sha(z,name):
 h=hashlib.sha256()
 with z.open(name) as f:
  for b in iter(lambda:f.read(1<<20),b''):h.update(b)
 return h.hexdigest()
with zipfile.ZipFile(before) as a,zipfile.ZipFile(after) as b:
 selected=[name for name in a.namelist() if name.startswith(('bloodborne_dw/composite/','assets/bloodborne_rp/','assets/bloodborne_dw/native_grid/')) and not name.endswith('/')]
 selected += [name for name in a.namelist() if name.startswith(('bloodborne_dw/imported-collision-','bloodborne_dw/imported-full-map-','bloodborne_dw/static-architecture-')) and name.endswith('.json')]
 rows=[{'path':name,'before':sha(a,name),'after':sha(b,name)} for name in selected]
 old=json.loads(a.read('bloodborne_dw/remaining-catalogue.json'));new=json.loads(b.read('bloodborne_dw/remaining-catalogue.json'));new['objects']=[r for r in new['objects'] if r['path']!='source_street_lamp_post']
 result={'status':'PASS' if all(r['before']==r['after'] for r in rows) and old==new else 'FAIL','baseline':before.name,'checkedResources':len(rows),'remainingCatalogueUnchangedExceptNewPost':old==new,'differences':[r for r in rows if r['before']!=r['after']]}
(D/'frozen-resources-audit.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
