import json,zipfile,subprocess
from pathlib import Path
import world_io as n
P=Path(__file__).resolve().parents[1];D=P/'interaction-evidence';rows=[]
for role in ('client-1','client-2','client-3'):
 path=P/'interaction-profiles'/role/'saves/interactions/level.dat';data=n.read_nbt(path).root.value['Data'].value;rows.append({'role':role,'allowCommands':data.get('allowCommands',n.Tag(1,0)).value})
hits=[]
for path in Path('C:/Users/vakir/AppData/Local/Limacina/project/DreamWalker/mods').glob('*.jar'):
 with zipfile.ZipFile(path) as archive:
  for name in archive.namelist():
   if name.endswith('.class') and b'\x11\x01\x29' in archive.read(name) and any(k in name.lower() for k in ('key','client','binding','input')):hits.append({'jar':path.name,'class':name})
(D/'key-environment.json').write_text(json.dumps({'worldPermissions':rows,'f8ConstantCandidates':hits},indent=2),encoding='utf-8')
model=next(Path('C:/Users/vakir/AppData/Local/Limacina/project/DreamWalker/mods').glob('modelprops-*.jar'))
result=subprocess.run([str(P/'build-tools/jdk/bin/javap.exe'),'-p','-c','-classpath',str(model),'ru.modelprops.client.ModelPropsClient'],capture_output=True,text=True,timeout=30);(D/'modelprops-key-bytecode.txt').write_text(result.stdout,encoding='utf-8')
