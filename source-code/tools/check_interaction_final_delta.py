import hashlib,json,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'interaction-evidence';name='dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.7.jar';old=P/'interaction-profiles/server-1/mods'/name;new=P/'build-native-visual/libs'/name
with zipfile.ZipFile(old) as a,zipfile.ZipFile(new) as b:
 aa={p:a.read(p) for p in a.namelist() if not p.endswith('/')};bb={p:b.read(p) for p in b.namelist() if not p.endswith('/')};changed=[p for p in sorted(set(aa)|set(bb)) if aa.get(p)!=bb.get(p)]
classes=[p for p in changed if p.endswith('.class')]
allowed={'dev/dreamwalker/bloodbornedw/composite/FabricCompositeWorld.class','dev/dreamwalker/bloodbornedw/compat/worldedit/WorldEditObjectTransactions.class'}
assert set(classes)==allowed,classes
resourceChanges=json.loads((D/'resource-only-delta.json').read_text())['changedEntries'];assert set(changed)==allowed|set(resourceChanges)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
(D/'final-binary-delta.json').write_text(json.dumps({'status':'PASS','productionSha256':sha(new),'serverChecksSha256':sha(old),'changedEntries':changed,'unaffectedInteractionClassesAndContractsIdentical':True,'adapterValidation':'preview-2/map-apply.json'},indent=2),encoding='utf-8')
