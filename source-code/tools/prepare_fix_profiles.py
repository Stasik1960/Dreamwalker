import hashlib,json,shutil,urllib.request,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];T=P/'build-tools';D=P/'fix-evidence';D.mkdir(exist_ok=True)
def identity(path):
 with zipfile.ZipFile(path) as z:
  try:return json.loads(z.read('fabric.mod.json'))['id']
  except (KeyError,json.JSONDecodeError):return None
url='https://api.modrinth.com/v2/project/worldedit/version?game_versions=%5B%221.20.1%22%5D&loaders=%5B%22fabric%22%5D'
with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Dreamwalker-fix-qa'}),timeout=60) as f:versions=json.load(f)
version=next(v for v in versions if v['version_number']=='7.2.15');f=next(f for f in version['files'] if f.get('primary'));jar=T/f['filename']
if not jar.exists():
 with urllib.request.urlopen(f['url'],timeout=120) as src,jar.open('xb') as dst:
  for data in iter(lambda:src.read(1<<20),b''):dst.write(data)
assert hashlib.sha512(jar.read_bytes()).hexdigest()==f['hashes']['sha512'];assert identity(jar)=='worldedit'
(D/'worldedit-download.json').write_text(json.dumps({'version':version['version_number'],'file':f,'qaOnly':True},indent=2),encoding='utf-8')
reach_url='https://maven.jamieswhiteshirt.com/libs-release/com/jamieswhiteshirt/reach-entity-attributes/2.4.0/reach-entity-attributes-2.4.0.jar';reach=T/'reach-entity-attributes-2.4.0.jar'
if not reach.exists():
 with urllib.request.urlopen(reach_url,timeout=120) as src,reach.open('xb') as dst:shutil.copyfileobj(src,dst)
with urllib.request.urlopen(reach_url+'.sha1',timeout=60) as src:reach_sha=src.read().decode().strip().split()[0]
assert hashlib.sha1(reach.read_bytes()).hexdigest()==reach_sha and identity(reach)=='reach-entity-attributes'
(D/'reach-download.json').write_text(json.dumps({'version':'2.4.0','url':reach_url,'sha1':reach_sha,'sha256':hashlib.sha256(reach.read_bytes()).hexdigest(),'qaOnly':True},indent=2),encoding='utf-8')
production=P/'build-native-visual/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.2.jar';qa=P/'build-native-visual/libs/dreamwalker-fix-qa-0.1.0-v12.7.4-visual-physics.2.jar';assert production.is_file() and qa.is_file()
receipt={}
for role in ('server','client'):
 profile=P/'fix-profiles'/role;(profile/'mods').mkdir(parents=True,exist_ok=True);selected={}
 for source in Path('C:/Users/vakir/Desktop').joinpath(role+'_mods').glob('*.jar'):
  mid=identity(source)
  if mid in ('bloodborne_dw','worldedit'):continue
  shutil.copy2(source,profile/'mods'/source.name);selected[mid or source.name]={'file':source.name,'sha256':hashlib.sha256(source.read_bytes()).hexdigest()}
 for source in (production,qa,jar):shutil.copy2(source,profile/'mods'/source.name)
 if 'reach-entity-attributes' not in selected:shutil.copy2(reach,profile/'mods'/reach.name)
 if role=='client':
  source=Path('C:/Users/vakir/Desktop/Dreamwalker-client-settings/config')
  if source.exists():shutil.copytree(source,profile/'config',dirs_exist_ok=True)
  (profile/'config').mkdir(exist_ok=True);(profile/'config/iris.properties').write_text('enableShaders=false\nshaderPack=\n',encoding='utf-8')
  (profile/'options.txt').write_text('lang:en_us\nrenderDistance:3\nsimulationDistance:5\nmaxFps:40\nenableVsync:false\nfullscreen:false\ngamma:0.5\npauseOnLostFocus:false\nao:true\n',encoding='utf-8')
  assert {'sodium','indium','farmersdelight'}<=selected.keys(),str(selected.keys())
  (profile/'saves/dw-fix-city').mkdir(parents=True,exist_ok=True)
  archive=Path('C:/Users/vakir/.codex/worktrees/3f07/DW/audits/Dreamwalker-BB/2026-10-25-visual-diagnostics/published-map.zip');assert hashlib.sha256(archive.read_bytes()).hexdigest()=='da6d02e3e86e1780576359033f908a0a5cb22a255348939eae1637a44423c636'
  with zipfile.ZipFile(archive) as z:
   for name in z.namelist():
    if not name.startswith('bloodborne-native-grid-city/') or name.endswith('/'):continue
    dst=profile/'saves/dw-fix-city'/name.split('/',1)[1];assert dst.resolve().is_relative_to((profile/'saves/dw-fix-city').resolve());dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(z.read(name))
 else:
  (profile/'eula.txt').write_text('eula=true\n');(profile/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=25589\nlevel-name=fix-regression\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:air","height":1}],"biome":"minecraft:plains","lakes":false,"features":false}\nview-distance=2\nsimulation-distance=3\nspawn-protection=0\nmax-tick-time=180000\nenable-command-block=false\n',encoding='utf-8')
 receipt[role]={'source':'Desktop/'+role+'_mods','profile':str(profile),'mods':selected,'productionSha256':hashlib.sha256(production.read_bytes()).hexdigest()}
(D/'profiles.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
