import hashlib,json,shutil,stat,subprocess
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'interaction-evidence';V='0.1.0-v12.7.4-visual-physics.7';J=P/f'build-native-visual/libs/dreamwalker-bb-fabric-1.20.1-{V}.jar'
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for chunk in iter(lambda:f.read(1<<20),b''):h.update(chunk)
 return h.hexdigest()
ready=json.loads((D/'acceptance.json').read_text());assert ready['status']=='PASS';H=sha(J);assert H==ready['productionSha256']
import zipfile
running=subprocess.run(['powershell.exe','-NoProfile','-Command','Get-Process java,javaw -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id'],capture_output=True,text=True,timeout=15)
assert not running.stdout.strip(), 'Close Minecraft and wait for the QA JVMs to stop before installing'
receipts=[]
for role,mods in [('client',Path('C:/Users/vakir/Desktop/client_mods')),('server',Path('C:/Users/vakir/Desktop/server_mods')),('limacina',Path('C:/Users/vakir/AppData/Local/Limacina/project/DreamWalker/mods'))]:
 assert mods.is_dir() and not mods.stat().st_file_attributes&stat.FILE_ATTRIBUTE_REPARSE_POINT
 backup=D/'installed-backups'/role;backup.mkdir(parents=True,exist_ok=True)
 staged=mods/(J.name+'.tmp');shutil.copy2(J,staged);assert sha(staged)==H
 removed=[]
 for f in mods.glob('*.jar'):
  with zipfile.ZipFile(f) as z:info=json.loads(z.read('fabric.mod.json')) if 'fabric.mod.json' in z.namelist() else {}
  if info.get('id')!='bloodborne_dw' or (f.name==J.name and sha(f)==H):continue
  assert f.resolve().parent==mods.resolve() and not f.stat().st_file_attributes&stat.FILE_ATTRIBUTE_REPARSE_POINT
  target=backup/f.name
  if target.exists():assert sha(target)==sha(f)
  else:shutil.copy2(f,target)
  assert sha(target)==sha(f);f.unlink();removed.append(f.name)
 staged.replace(mods/J.name);assert sha(mods/J.name)==H
 lock=mods/'DREAMWALKER-MODPACK-LOCK.json'
 if lock.exists():
  data=json.loads(lock.read_text(encoding='utf-8'));old=backup/lock.name
  if not old.exists():shutil.copy2(lock,old)
  data['dreamwalkerSha256']=H;data['files']={name:row for name,row in data['files'].items() if row.get('id')!='bloodborne_dw'};data['files'][J.name]={'id':'bloodborne_dw','version':V,'sha256':H,'bytes':J.stat().st_size};lock.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
 receipts.append({'role':role,'path':str(mods/J.name),'sha256':H,'replaced':removed})
(D/'installed.json').write_text(json.dumps(receipts,indent=2),encoding='utf-8')
