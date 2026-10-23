from pathlib import Path
import hashlib,json,subprocess,sys,time,zipfile
P=Path(__file__).resolve().parents[2];O=P.parent.parent/'DW-native-grid-github-transfer-20261022';T=O/('restore-fixture-'+time.strftime('%H%M%S'));T.mkdir()
assets=T/'assets';assets.mkdir();data=b'exact portable bytes\x00\xff';key=hashlib.sha256(data).hexdigest();volume=assets/'resume-data-001.zip'
with zipfile.ZipFile(volume,'w') as z:z.writestr('blobs/'+key,data)
manifest={'volumes':[{'name':volume.name,'bytes':volume.stat().st_size,'sha256':hashlib.sha256(volume.read_bytes()).hexdigest()}],'chunks':{key:{'bytes':len(data),'volume':volume.name}},'files':[{'path':'nested/a.bin','bytes':len(data),'sha256':key,'chunks':[key],'mtimeNs':1720000000000000000},{'path':'empty','bytes':0,'sha256':hashlib.sha256(b'').hexdigest(),'chunks':[],'mtimeNs':1720000000000000000},{'path':'copy.bin','bytes':len(data),'sha256':key,'chunks':[key],'mtimeNs':1720000000000000000}],'logicalBytes':len(data)*2}
def save():(assets/'bundle-manifest.json').write_text(json.dumps(manifest),encoding='utf-8')
def run(*args):return subprocess.run([sys.executable,str(O/'restore_bundle.py'),'--assets',str(assets),*args],capture_output=True,text=True,creationflags=subprocess.CREATE_NO_WINDOW)
save();assert run('--verify-only').returncode==0
target=T/'restored';assert run('--target',str(target)).returncode==0
assert (target/'nested/a.bin').read_bytes()==data and (target/'empty').read_bytes()==b'' and (target/'copy.bin').stat().st_mtime_ns==1720000000000000000
import os
os.utime(target/'copy.bin',ns=(1730000000000000000,1730000000000000000));assert run('--target',str(target)).returncode==0 and (target/'copy.bin').stat().st_mtime_ns==1720000000000000000
(target/'nested/a.bin').write_bytes(b'different');assert run('--target',str(target)).returncode!=0 and (target/'nested/a.bin').read_bytes()==b'different'
manifest['files'][0]['path']='../escape';save();assert run('--target',str(T/'unsafe')).returncode!=0 and not (T/'escape').exists()
manifest['files'][0]['path']='nested/a.bin';save();volume.write_bytes(b'corrupt');assert run('--verify-only').returncode!=0
receipt={'status':'PASS','checks':['exact bytes/dedup/empty file','mtime preservation including exact-byte retry','refuse differing existing file','reject traversal','reject corrupted volume'],'fixture':str(T)}
(O/'restore-fixture-receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8');print(json.dumps(receipt))
