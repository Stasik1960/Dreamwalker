"""Install the accepted hotfix into the two authorized Desktop mod folders."""
import hashlib,json,shutil,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'bench-evidence';B=P/'bench-release';desktop=Path('C:/Users/vakir/Desktop');delivery=desktop/'Dreamwalker-visual-physics.3-20261025';delivery.mkdir(exist_ok=True)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
accepted=json.loads((D/'acceptance.json').read_text());assert accepted['status']=='PASS';jar=B/'assets/dreamwalker-bb-0.1.0-v12.7.4-visual-physics.3.jar';expected=accepted['jarSha256'];assert sha(jar)==expected
rows=[]
for role in ('client','server'):
 folder=desktop/(role+'_mods');assert folder.is_dir() and not folder.is_symlink() and str(folder.resolve()).casefold()==str(folder.absolute()).casefold() and str(delivery.resolve()).casefold()==str(delivery.absolute()).casefold();old=[]
 for candidate in folder.glob('*.jar'):
  with zipfile.ZipFile(candidate) as z:
   if 'fabric.mod.json' in z.namelist() and json.loads(z.read('fabric.mod.json')).get('id')=='bloodborne_dw':old.append(candidate)
 assert len(old)==1,'Expected exactly one Dreamwalker jar in '+str(folder)
 previous=old[0];previousSha=sha(previous);assert previousSha in ('b776096762ea39f4b46fade64bb0aecc4f166d83086cd95bb6dba37846639584',expected),'Unexpected local Dreamwalker version'
 backup=delivery/'previous-mods'/role;backup.mkdir(parents=True,exist_ok=True);lock=folder/'DREAMWALKER-MODPACK-LOCK.json'
 if previousSha!=expected:
  assert previous.resolve().parent==folder.resolve() and not previous.is_symlink()
  target=backup/previous.name;assert target.resolve().is_relative_to(delivery.resolve())
  if target.exists():assert sha(target)==previousSha;previous.unlink()
  else:shutil.move(str(previous),str(target))
  if lock.exists():shutil.copy2(lock,backup/lock.name)
 shutil.copy2(jar,folder/jar.name);assert sha(folder/jar.name)==expected
 if lock.exists():
  data=json.loads(lock.read_text(encoding='utf-8-sig'));files=data['files']
  for name,entry in list(files.items()):
   if entry.get('id')=='bloodborne_dw':del files[name]
  files[jar.name]={'id':'bloodborne_dw','version':accepted['version'],'sha256':expected,'bytes':jar.stat().st_size};data['dreamwalkerSha256']=expected
  temp=lock.with_suffix('.json.new');temp.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');temp.replace(lock)
 rows.append({'role':role,'folder':str(folder),'jar':jar.name,'sha256':expected,'previousSha256':previousSha,'backup':str(backup)})
for asset in (B/'assets').iterdir():
 if asset.is_file():shutil.copy2(asset,delivery/asset.name);assert sha(delivery/asset.name)==sha(asset)
shutil.copy2(B/'ASSETS.json',delivery/'ASSETS.json')
(D/'desktop-install.json').write_text(json.dumps({'status':'PASS','delivery':str(delivery),'jarSha256':expected,'roles':rows},ensure_ascii=False,indent=2),encoding='utf-8');print('Installed accepted jar',expected)
