"""Fetch the pinned optional compile API without game profiles or user files."""
import hashlib,json,urllib.request,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'build-tools';D.mkdir(exist_ok=True)
url='https://api.modrinth.com/v2/project/worldedit/version?game_versions=%5B%221.20.1%22%5D&loaders=%5B%22fabric%22%5D'
with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Dreamwalker-build'}),timeout=60) as response:versions=json.load(response)
v=next(v for v in versions if v['version_number']=='7.2.15');entry=next(f for f in v['files'] if f.get('primary'));jar=D/entry['filename'];assert jar.name=='worldedit-mod-7.2.15.jar'
if not jar.exists():
 with urllib.request.urlopen(entry['url'],timeout=120) as source,jar.open('xb') as target:
  for block in iter(lambda:source.read(1<<20),b''):target.write(block)
assert hashlib.sha512(jar.read_bytes()).hexdigest()==entry['hashes']['sha512']
with zipfile.ZipFile(jar) as archive:assert json.loads(archive.read('fabric.mod.json'))['id']=='worldedit'
print('Verified compile-only API:',jar)
print('gradlew.bat --no-daemon -Pworldedit_api_jar=build-tools/worldedit-mod-7.2.15.jar remapJar')
