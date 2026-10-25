import hashlib,json,urllib.request,zipfile
from pathlib import Path
O=Path(__file__).resolve().parent;target=O/'published-source.zip';expected='8b166f107abf4f754104be4b35b53c7c3aca61c09c2de8f9e40c74b179099c51'
if not target.exists():
 with urllib.request.urlopen('https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-visual-physics-20261025/dreamwalker-visual-physics-sources.zip',timeout=120) as response,target.open('xb') as out:
  for data in iter(lambda:response.read(1<<20),b''):out.write(data)
assert hashlib.sha256(target.read_bytes()).hexdigest()==expected
with zipfile.ZipFile(target) as z:
 for name in z.namelist():
  if name.startswith('source/') and (name.endswith('.java') or name.endswith('/world_io.py') or name.endswith('/FORMAT-RU.md') or '/tools/native-visual/' in name and name.endswith('.py')):
   path=O/'recovered'/name;assert path.resolve().is_relative_to((O/'recovered').resolve());path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(z.read(name))
(O/'source-recovery.json').write_text(json.dumps({'status':'PUBLISHED_SOURCE_SHA256_VERIFIED_READONLY_DIAGNOSTIC_COPY','sha256':expected,'originalWorkingSourcesUnavailable':True},indent=2))
