from pathlib import Path
import json, zipfile, io, struct, hashlib, re
root=Path(__file__).resolve().parents[1]
version=re.search(r"(?m)^version\s*=\s*'([^']+)'", (root/'build.gradle').read_text(encoding='utf-8'))[1]
jar=root/f'build/libs/dannys-aot-1.20.1-{version}.jar'
mods=[]; errors=[]; classes=0; max_major=0; mixin_configs=[]
def visit(data,label):
 global classes,max_major
 with zipfile.ZipFile(io.BytesIO(data)) as z:
  names=set(z.namelist())
  m=json.loads(z.read('fabric.mod.json'))
  if m['id']=='dannys-aot': assert m['version']==version,(m['version'],version)
  mods.append({'jar':label,'id':m['id'],'version':m['version'],'depends':m.get('depends',{})})
  if 'LICENSE_dannys-aot' in names:
   assert z.read('LICENSE_dannys-aot')==(root/'src/main/resources/LICENSE_dannys-aot').read_bytes()
   assert not any(n.startswith('daot/verification/') for n in names)
  for name in names:
   if name.endswith('.class') and not name.startswith('META-INF/versions/'):
    major=struct.unpack('>H',z.read(name)[6:8])[0];classes+=1;max_major=max(max_major,major)
    if major>61: errors.append(f'{label}!{name}: Java class {major}')
  for entry in m.get('mixins',[]):
   cfg=entry if isinstance(entry,str) else entry['config']
   d=json.loads(z.read(cfg));ref=d.get('refmap')
   if ref and ref not in names: errors.append(f'Missing refmap {label}!{ref}')
   mixin_configs.append({'jar':label,'config':cfg,'refmap':ref,'compatibility':d.get('compatibilityLevel')})
   for section in ('mixins','client','server'):
    for mixin in d.get(section,[]):
     cls=(d.get('package','')+'.'+mixin).replace('.','/')+'.class'
     if cls not in names: errors.append(f'Missing mixin class {label}!{cls}')
  for entry in m.get('jars',[]): visit(z.read(entry['file']),label+'!'+entry['file'])
visit(jar.read_bytes(),jar.name)
ids=[m['id'] for m in mods]
assert len(ids)==len(set(ids)),ids
assert {'dannys-aot','aaa_particles','geckolib','player_animation_library'}.issubset(ids)
report={'artifact':jar.name,'bytes':jar.stat().st_size,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'classes':classes,'maximumClassMajor':max_major,'mods':mods,'mixins':mixin_configs,'errors':errors}
(root/'reports/bundle-verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in report.items() if k not in ('mods','mixins')},indent=2))
print('Bundled mod ids:',', '.join(ids))
print('Mixin configs:',json.dumps(mixin_configs,indent=2))
assert not errors,errors
