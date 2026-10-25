import json,subprocess,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'npc-evidence';D.mkdir(exist_ok=True);G=Path('C:/Users/vakir/AppData/Local/Limacina/project/DreamWalker');jar=next((G/'mods').glob('CustomNPCs-*.jar'))
with zipfile.ZipFile(jar) as z:
 classes=[n[:-6].replace('/','.') for n in z.namelist() if n.endswith('.class') and n.startswith('noppes/npcs/') and any(v in n for v in ('RenderCustomNpc','RenderNpc','ModelData','DataDisplay','EntityNPCInterface','EntityCustomNpc'))]
(D/'custom-npc-classes.json').write_text(json.dumps({'jar':jar.name,'classes':classes},indent=2),encoding='utf-8')
for cls in classes:
 if '$' in cls:continue
 result=subprocess.run([str(P/'build-tools/jdk/bin/javap.exe'),'-p','-c','-s','-classpath',str(jar),cls],capture_output=True,text=True,timeout=40);(D/(cls.rsplit('.',1)[-1]+'.txt')).write_text(result.stdout,encoding='utf-8')
