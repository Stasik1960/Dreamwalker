import hashlib,json,zipfile,re,subprocess
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'interaction-evidence';name='dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.7.jar';old=P/'interaction-profiles/server-1/mods'/name;new=P/'build-native-visual/libs'/name
with zipfile.ZipFile(old) as a,zipfile.ZipFile(new) as b:
 aa={p:a.read(p) for p in a.namelist() if not p.endswith('/')};bb={p:b.read(p) for p in b.namelist() if not p.endswith('/')};changed=[p for p in sorted(set(aa)|set(bb)) if aa.get(p)!=bb.get(p)]
classes=[p for p in changed if p.endswith('.class')]
allowed={'dev/dreamwalker/bloodbornedw/composite/FabricCompositeWorld.class','dev/dreamwalker/bloodbornedw/compat/worldedit/WorldEditObjectTransactions.class'}
extra=set(classes)-allowed
(D/'final-binary-delta.json').write_text(json.dumps({'status':'REVIEW_REQUIRED','changedEntries':changed,'extraClasses':sorted(extra)},indent=2),encoding='utf-8')
metadata=[]
for name in sorted(extra):
 assert name=='dev/dreamwalker/bloodbornedw/compat/worldedit/WorldEditObjectTransactions$1.class',name
 outputs=[]
 for jar in (old,new):
  result=subprocess.run([str(P/'build-tools/jdk/bin/javap.exe'),'-p','-c','-s','-classpath',str(jar),name[:-6].replace('/','.')],capture_output=True,text=True,timeout=30);assert result.returncode==0;outputs.append(re.sub(r'#\d+','#',result.stdout))
 assert outputs[0]==outputs[1], 'Unexpected anonymous adapter bytecode change'
 metadata.append(name)
assert allowed<=set(classes)
resourceChanges=json.loads((D/'resource-only-delta.json').read_text())['changedEntries'];assert set(changed)==set(classes)|set(resourceChanges)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
(D/'final-binary-delta.json').write_text(json.dumps({'status':'PASS','productionSha256':sha(new),'serverChecksSha256':sha(old),'changedEntries':changed,'metadataOnlyClasses':metadata,'unaffectedInteractionClassesAndContractsIdentical':True,'adapterValidation':'preview-2/map-apply.json'},indent=2),encoding='utf-8')
