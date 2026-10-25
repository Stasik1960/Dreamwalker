import hashlib,json,subprocess,zipfile
from pathlib import Path
from PIL import Image
P=Path(__file__).resolve().parents[1];D=P/'interaction-evidence';R=P/'src/architecture/resources';read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));checks={}
old=read(P/'restoration-evidence/world-after.json');now=read(D/'world-before.json');checks['baselineMatchesPublishedV6Map']={r['path']:(r['sha256'],r['bytes']) for r in old['entries']}=={r['path']:(r['sha256'],r['bytes']) for r in now['files']}
resources=read(D/'window-resources.json');catalog=read(R/'bloodborne_dw/static-architecture-v1274.json');poses={p['key']:p for p in catalog['poses']}
for row in catalog['recipes']:
 for i,nextid in enumerate(row['remove']):
  rest=row['fragments'][:i]+row['fragments'][i+1:]
  assert (not rest and nextid==-1) or (nextid>=0 and sorted(json.dumps(f,sort_keys=True) for f in rest)==sorted(json.dumps(f,sort_keys=True) for f in catalog['recipes'][nextid]['fragments']))
checks['completeStaticRemovalClosures']=True;checks['extraStaticRecipes']=len(catalog['recipes'])-resources['originalRecipeCount'];checks['extraStaticPoses']=len(catalog['poses'])-resources['originalPoseCount']
im=Image.open(R/'assets/bloodborne_dw/textures/interactions/shutter_glass.png').convert('RGBA');checks['glassPaneAlpha']=sorted({p[3] for p in im.crop((107,41,123,71)).getdata()});assert checks['glassPaneAlpha']==[80]
for name in ('source_shutter_window','source_shutter_window_surround'):
 doc=read(R/f'bloodborne_dw/composite/{name}.json');checks[name+'Variants']=len(doc['variants']);checks[name+'Openable']=doc['openable']
 for v in doc['variants']:
  for pose in v['poses'].values():
   for part in pose['parts']:assert (R/'assets/bloodborne_dw/models'/(part['model'].split(':')[1]+'.json')).is_file()
checks['twoSidedGlass']=set(read(R/'assets/bloodborne_dw/models/interactions/shutter_glass.json')['elements'][0]['faces'])=={'north','south'}
with zipfile.ZipFile(P/'restoration-evidence/final-artifacts/dreamwalker-restoration-full-sources-20261025.zip') as z:
 n=next(n for n in z.namelist() if n.endswith('src/architecture/resources/bloodborne_dw/static-architecture-v1274.json'));baseline=json.loads(z.read(n));checks['allOriginalStaticRowsUnmodified']=catalog['recipes'][:len(baseline['recipes'])]==baseline['recipes'] and catalog['poses'][:len(baseline['poses'])]==baseline['poses']
 assert checks['allOriginalStaticRowsUnmodified']
checks['status']='PASS';(D/'resource-audit.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
G=Path('C:/Users/vakir/AppData/Local/Limacina/project/DreamWalker');jar=next((G/'mods').glob('*Jade*.jar'),None) or next((G/'mods').glob('*jade*.jar'),None)
if jar:
 with zipfile.ZipFile(jar) as z:classes=[n[:-6].replace('/','.') for n in z.namelist() if n.endswith('.class') and any(s in n for s in ('RayTracing','RayTrace','Raycast','RayCast'))]
 result={'jar':jar.name,'classes':classes}
 for cls in classes[:6]:
  p=subprocess.run([str(P/'build-tools/jdk/bin/javap.exe'),'-p','-c','-classpath',str(jar),cls],capture_output=True,text=True,timeout=30);(D/(cls.rsplit('.',1)[-1]+'.txt')).write_text(p.stdout,encoding='utf-8')
 (D/'jade-ray-inspection.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
