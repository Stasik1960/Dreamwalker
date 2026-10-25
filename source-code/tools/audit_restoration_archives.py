import json,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'restoration-evidence';O=D/'final-artifacts';rows=[]
for p in sorted(O.glob('*.zip')):
 with zipfile.ZipFile(p) as z:
  bad=z.testzip();assert bad is None,(p.name,bad)
  names=z.namelist()
  if 'full-sources' in p.name:
   for required in ('DW/build.gradle','DW/gradle/wrapper/gradle-wrapper.jar','DW/build-dependencies/worldedit-mod-7.2.15.jar','DW/history/TASK/START-RU.txt','DW/history/TASK/preserved-assemblies.json','DW/restoration-evidence/HANDOFF-RU.md'):assert required in names,required
   assert any(name.endswith('/native_grid/meshes.bin') for name in names),'Frozen meshes missing from full sources'
   for relative in json.loads((D/'source-files.json').read_text()):assert z.read('DW/'+relative)==(P/relative).read_bytes(),relative
  rows.append({'name':p.name,'entries':len(names),'crc':'PASS'})
(D/'archive-integrity.json').write_text(json.dumps({'status':'PASS','archives':rows},indent=2),encoding='utf-8')
