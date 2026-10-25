import hashlib,json,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'interaction-evidence';name='dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.7.jar'
old=P/'interaction-profiles/server-1/mods'/name;new=P/'build-native-visual/libs'/name
with zipfile.ZipFile(old) as a,zipfile.ZipFile(new) as b:
 aa={p:a.read(p) for p in a.namelist() if not p.endswith('/')};bb={p:b.read(p) for p in b.namelist() if not p.endswith('/')}
 changed=[p for p in sorted(set(aa)|set(bb)) if aa.get(p)!=bb.get(p)]
allowed={'assets/bloodborne_dw/blockstates/static_architecture_256.json','assets/bloodborne_dw/blockstates/static_architecture_257.json','assets/bloodborne_dw/models/interactions/shutter_glass.json','assets/bloodborne_dw/textures/interactions/shutter_glass.png','assets/bloodborne_dw/textures/block/interactions/shutter_glass.png'}
assert set(changed)<=allowed and changed,changed
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
report={'status':'PASS','previousProductionSha256':sha(old),'productionSha256':sha(new),'changedEntries':changed,'allClassesIdentical':True,'allServerPhysicsAndInteractionResourcesIdentical':True,'acceptedServerChecks':'server-1/server-checks.json','acceptedUnchangedClientChecks':'client-1/client-checks.json','requiresNewWindowVisualReview':True}
(D/'resource-only-delta.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
