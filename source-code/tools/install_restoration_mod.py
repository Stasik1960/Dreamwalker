import hashlib,json,shutil,stat,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'restoration-evidence';V='0.1.0-v12.7.4-visual-physics.6';J=P/f'build-native-visual/libs/dreamwalker-bb-fabric-1.20.1-{V}.jar'
assert json.loads((D/'ready-to-release.json').read_text())['status']=='READY'
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1<<20),b''):h.update(b)
 return h.hexdigest()
H=sha(J);assert H==json.loads((D/'ready-to-release.json').read_text())['productionSha256'];installs=[]
for role,mods in [('client',Path('C:/Users/vakir/Desktop/client_mods')),('server',Path('C:/Users/vakir/Desktop/server_mods')),('limacina',Path('C:/Users/vakir/AppData/Local/Limacina/project/DreamWalker/mods'))]:
 backup=D/'installed-backups'/role;backup.mkdir(parents=True,exist_ok=True);removed=[]
 for f in mods.glob('*.jar'):
  with zipfile.ZipFile(f) as z:info=json.loads(z.read('fabric.mod.json')) if 'fabric.mod.json' in z.namelist() else {}
  if info.get('id')!='bloodborne_dw':continue
  assert f.resolve().parent==mods.resolve() and not f.stat().st_file_attributes&stat.FILE_ATTRIBUTE_REPARSE_POINT
  if f.name==J.name and sha(f)==H:continue
  target=backup/f.name
  if target.exists():assert sha(target)==sha(f)
  else:shutil.copy2(f,target)
  assert sha(f)==sha(target);f.unlink();removed.append(f.name)
 shutil.copy2(J,mods/J.name);assert sha(mods/J.name)==H
 lock=mods/'DREAMWALKER-MODPACK-LOCK.json'
 if lock.exists():
  data=json.loads(lock.read_text(encoding='utf-8'));old=backup/lock.name
  if not old.exists():shutil.copy2(lock,old)
  data['dreamwalkerSha256']=H;data['files']={name:r for name,r in data['files'].items() if r.get('id')!='bloodborne_dw'};data['files'][J.name]={'id':'bloodborne_dw','version':V,'sha256':H,'bytes':J.stat().st_size};lock.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
 installs.append({'role':role,'path':str(mods/J.name),'sha256':H,'replaced':removed})
(D/'installed.json').write_text(json.dumps(installs,indent=2),encoding='utf-8')
