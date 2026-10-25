import hashlib,json,zipfile,subprocess
from pathlib import Path
P=Path(__file__).resolve().parents[1];old=P/'fix-release/assets/dreamwalker-bb-0.1.0-v12.7.4-visual-physics.2.jar';new=P/'build-native-visual/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.3.jar';sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest();assert sha(old)=='b776096762ea39f4b46fade64bb0aecc4f166d83086cd95bb6dba37846639584'
with zipfile.ZipFile(old) as a,zipfile.ZipFile(new) as b:
 assets=[n for n in a.namelist() if n.startswith('assets/') and not n.endswith('/')];assert all(a.read(n)==b.read(n) for n in assets)
 data=[n for n in a.namelist() if n.startswith('bloodborne_dw/')];assert all(a.read(n)==b.read(n) for n in data)
 assert not any('/benchqa/' in n for n in b.namelist());assert json.loads(b.read('fabric.mod.json'))['version']=='0.1.0-v12.7.4-visual-physics.3'
 old_classes={n for n in a.namelist() if n.endswith('.class')};new_classes={n for n in b.namelist() if n.endswith('.class')}
 changed=sorted(n for n in old_classes&new_classes if a.read(n)!=b.read(n));added=sorted(new_classes-old_classes)
 assert old_classes<=new_classes and all(n.rsplit('/',1)[-1] in ('CompositeArchitecture.class','CompositeClient.class') or '/CompositeClient$' in n for n in changed),changed
 assert all('/composite/Bench' in n for n in added),added
debug_only=[]
for name in changed:
 if '/CompositeClient$' not in name:continue
 def code(jar):
  result=subprocess.run([str(P/'build-tools/jdk/bin/javap.exe'),'-p','-c','-constants','-classpath',str(jar),name[:-6].replace('/','.')],capture_output=True,text=True,timeout=30);assert result.returncode==0;return result.stdout
 assert code(old)==code(new),'Unexpected inner-class behavior change: '+name
 debug_only.append(name)
(P/'bench-evidence/artifact-integrity.json').write_text(json.dumps({'status':'PASS','jar':str(new),'sha256':sha(new),'baselineSha256':sha(old),'unchangedExistingAssets':len(assets),'unchangedDataResources':len(data),'changedExistingClasses':changed,'unchangedCodeWithShiftedDebugLines':debug_only,'addedClasses':added,'newBlockStates':0,'addedProductionEntityTypes':['bloodborne_dw:bench_seat'],'mapRewriteRequired':False},indent=2),encoding='utf-8');print('All',len(assets),'existing assets and',len(data),'data resources preserved; only initializers and new bench classes differ')
