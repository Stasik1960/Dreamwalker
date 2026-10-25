import hashlib,json,urllib.request,zipfile
from pathlib import Path
R=Path(__file__).resolve().parents[1];T=R/'build-tools';T.mkdir(exist_ok=True)
request=urllib.request.Request('https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&heap_size=normal&image_type=jdk&jvm_impl=hotspot&os=windows&vendor=eclipse',headers={'User-Agent':'Dreamwalker-build'})
with urllib.request.urlopen(request,timeout=60) as f:asset=json.load(f)[0]
package=asset['binary']['package'];p=T/package['name']
if not p.exists():
 with urllib.request.urlopen(package['link'],timeout=120) as f,p.open('xb') as out:
  for data in iter(lambda:f.read(1<<20),b''):out.write(data)
assert hashlib.sha256(p.read_bytes()).hexdigest()==package['checksum']
jdk=T/'jdk'
if not jdk.exists():
 with zipfile.ZipFile(p) as z:
  for name in z.namelist():
   if name.endswith('/'):continue
   parts=name.split('/',1)
   if len(parts)<2:continue
   dst=jdk/parts[1];assert dst.resolve().is_relative_to(jdk.resolve());dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(z.read(name))
(T/'jdk-receipt.json').write_text(json.dumps({'version':asset['version'],'package':package,'home':str(jdk)},indent=2),encoding='utf-8')
print('Verified portable JDK17',jdk)
