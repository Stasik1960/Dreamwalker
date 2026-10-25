import hashlib,json,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];old=Path('C:/Users/vakir/AppData/Local/Limacina/project/DreamWalker/mods/dreamwalker-bb-0.1.0-v12.7.4-visual-physics.1.jar');new=P/'build-native-visual/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.2.jar';sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(old)=='337ad3113ea02fd9e59b0646167d1ed51c769f048277e3a0858b4c89d2cfceb5';changed=[];preserved=0;proof={}
with zipfile.ZipFile(old) as a,zipfile.ZipFile(new) as b:
 for name in a.namelist():
  if name.startswith('assets/') and not name.endswith('/'):
   if a.read(name)!=b.read(name):changed.append(name)
   else:preserved+=1
 for name in ('assets/bloodborne_dw/native_grid/catalog.json','assets/bloodborne_dw/native_grid/meshes.bin','assets/bloodborne_dw/native_grid/visual_physics.json','bloodborne_dw/static-architecture-v1274.json','bloodborne_dw/imported-assembly-v126.json','bloodborne_dw/source-fallback-carriers-v1271.json'):
  before=hashlib.sha256(a.read(name)).hexdigest();after=hashlib.sha256(b.read(name)).hexdigest();assert before==after;proof[name]=before
 repair=json.loads(b.read('assets/bloodborne_dw/native_grid/visual-repairs.json'));assert len(repair['visuals'])==38 and repair['catalogSha256']==proof['assets/bloodborne_dw/native_grid/catalog.json']
 assert '40837' in repair['visuals'] and '23514' in repair['visuals'];assert not changed
 nested=[n for n in b.namelist() if n.startswith('META-INF/jars/')]
out={'status':'PASS','jar':str(new),'sha256':sha(new),'baselineSha256':sha(old),'unchangedExistingAssetFiles':preserved,'changedExistingAssets':changed,'physicalAndWholeResources':proof,'renderOnlyRepairedVisuals':len(repair['visuals']),'nestedJars':nested,'mapRewriteRequired':False};(P/'fix-evidence/artifact-integrity.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8');print('Frozen assets preserved',preserved)
