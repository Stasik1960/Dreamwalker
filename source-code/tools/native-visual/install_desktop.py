"""Stage/hash all replacements, then rename only the explicitly named Desktop folders.
Original folders are retained in a complete sibling backup; no recursive deletion.
"""
import datetime,hashlib,json,os,shutil,stat,traceback,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024';D=Path('C:/Users/vakir/Desktop')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def write(p,v):p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1<<20),b''):h.update(b)
 return h.hexdigest()
def plain(p):
 for parent in (p,*p.parents):
  try:info=parent.lstat()
  except FileNotFoundError:continue
  assert not info.st_file_attributes&0x400,'Reparse point: '+str(parent)
def contained(p):
 plain(p);resolved=p.resolve();assert resolved!=D.resolve() and resolved.is_relative_to(D.resolve());return resolved
def tree(p):
 plain(p);result={}
 for root,dirs,files in os.walk(p,followlinks=False):
  for name in [*dirs,*files]:plain(Path(root)/name)
  for name in files:
   path=Path(root)/name;result[path.relative_to(p).as_posix()]=sha(path)
 return result
def copy_tree(source,target):
 before=tree(source);assert not target.exists();shutil.copytree(source,target,symlinks=False);assert tree(target)==before and tree(source)==before;return before
def mod(path):
 with zipfile.ZipFile(path) as z:return json.loads(z.read('fabric.mod.json'))
def main():
 ready=read(T/'final-ready.json');assert ready['status']=='FINAL_PAIR_ACCEPTED_LOCAL_DELIVERY_STAGED'
 final=T/'delivery/final';jar=Path(ready['jar']['path']);assert sha(jar)==ready['jar']['sha256']
 stamp=datetime.datetime.now().strftime('%Y%m%d-%H%M%S');stage=D/('.dreamwalker-stage-'+stamp);backup=D/('Dreamwalker-backup-before-visual-physics-'+stamp)
 for path in (stage,backup):contained(path);assert not path.exists();path.mkdir()
 names=('bloodborne-native-grid-city','client_mods','server_mods','Dreamwalker-client-settings','Dreamwalker-server-settings')
 originals={}
 for name in names:
  path=D/name;contained(path)
  if path.exists():originals[name]=tree(path)
 copy_tree(Path(ready['world']),stage/names[0]);copy_tree(final/'client-settings',stage/names[3])
 copy_tree(final/'server-settings',stage/names[4])
 expected={};third=read(final/'THIRDPARTY-DOWNLOADS.json')
 for role in ('client','server'):
  name=role+'_mods';assert name in originals;copy_tree(D/name,stage/name);folder=stage/name
  old={p.name:mod(p) for p in folder.glob('*.jar')}
  own=[n for n,m in old.items() if m['id']=='bloodborne_dw'];assert len(own)==1
  for filename in own:
   target=folder/filename;assert target.parent.resolve()==folder.resolve() and folder.resolve().is_relative_to(stage.resolve());target.unlink()
  shutil.copy2(jar,folder/jar.name)
  if role=='client':
   for kind in ('sodium','indium','iris'):
    assert not any(m['id']==kind for m in old.values()),'Existing user dependency needs explicit reconciliation: '+kind
    entry=third['mods'][kind];source=Path(entry['path']);assert sha(source)==entry['sha256'] and mod(source)['id']==kind;shutil.copy2(source,folder/source.name)
  current={p.name:{'id':mod(p)['id'],'version':mod(p)['version'],'sha256':sha(p),'bytes':p.stat().st_size} for p in folder.glob('*.jar')}
  for filename,info in old.items():
   if info['id']!='bloodborne_dw':assert current[filename]['sha256']==originals[name][filename]
  assert sum(m['id']=='bloodborne_dw' for m in current.values())==1
  assert not any('route-qa' in n or 'function-qa' in n or 'preflight-qa' in n for n in current)
  lock={'schemaVersion':2,'minecraft':'1.20.1','role':role,'dreamwalkerSha256':ready['jar']['sha256'],'selectedClientShader':ready['selectedAddon'] if role=='client' else None,'defaultShadersEnabled':ready['selection']['defaultShadersEnabled'] if role=='client' else None,'files':current,'settingsFolder':'../Dreamwalker-'+role+'-settings','note':'Inventory/hashes only. Minecraft ignores this JSON; it is not a launcher lock.'}
  write(folder/'DREAMWALKER-MODPACK-LOCK.json',lock)
  (folder/'DREAMWALKER-INSTALL-RU.txt').write_text('Используйте обновлённую карту bloodborne-native-grid-city и единственный Dreamwalker visual-physics.1 JAR вместе.\nОстальные исходные моды сохранены. Клиентские Iris/Sodium/Indium не нужны на сервере.\nКлиент: папки config и shaderpacks из соседней Dreamwalker-client-settings перенесите в корень игрового профиля, рядом с mods. Не вкладывайте их внутрь mods. Личные options.txt и клавиши не заменяются.\nСервер: config из Dreamwalker-server-settings перенести в корень серверного профиля рядом с mods. Порты/IP/ops/whitelist не заменяются.\nDREAMWALKER-MODPACK-LOCK.json содержит только перечень/хеши.\n',encoding='utf-8')
  expected[name]=tree(folder)
 expected[names[0]]=tree(stage/names[0]);assert expected[names[0]]==ready['worldFiles'];expected[names[3]]=tree(stage/names[3]);expected[names[4]]=tree(stage/names[4])
 assert expected[names[3]]==ready['clientSettingsFiles'] and expected[names[4]]==ready['serverSettingsFiles']
 # Check for edits made while staging, before any original is renamed.
 for name,before in originals.items():assert tree(D/name)==before,'Desktop changed during staging: '+name
 moved=[];installed=[]
 try:
  for name in names:
   destination=D/name;old=backup/name;new=stage/name
   for path in (destination,old,new):contained(path)
   if destination.exists():os.replace(destination,old);moved.append(name)
   os.replace(new,destination);installed.append(name)
  for name,manifest in expected.items():assert tree(D/name)==manifest
 except BaseException:
  for name in reversed(installed):
   source=D/name;target=stage/(name+'.rollback');contained(source);contained(target);os.replace(source,target)
  for name in reversed(moved):
   source=backup/name;target=D/name;contained(source);contained(target);os.replace(source,target)
  raise
 receipt={'status':'DESKTOP_PAIR_AND_MODPACKS_INSTALLED_ALL_FILES_VERIFIED','backup':str(backup),'folders':{name:str(D/name) for name in names},'worldFileCount':len(expected[names[0]]),'jarSha256':ready['jar']['sha256'],'selectedShader':ready['selectedAddon'],'originalFoldersPreserved':True,'installedManifests':expected,'originalManifests':originals}
 write(T/'desktop-install.json',receipt)
if __name__=='__main__':
 try:main()
 except BaseException:(T/'desktop-install-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
