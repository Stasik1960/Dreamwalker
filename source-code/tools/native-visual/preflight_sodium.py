"""Load the actual optional mod mixins in Knot, before expensive game startup."""
import hashlib,json,os,shutil,subprocess,sys,time,traceback,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024';A=T/'addons';B=A/'preflights'/time.strftime('%Y%m%dT%H%M%S')
JAVA=Path('C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin/java.exe')
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
 B.mkdir(parents=True,exist_ok=False);source=T/'runs/addon-iris-off-1'
 args=[json.loads(l) for l in (source/'client.args').read_text(encoding='utf-8-sig').splitlines() if l.strip()]
 cp=args[args.index('-cp')+1];loader=next(Path(p) for p in cp.split(os.pathsep) if 'fabric-loader-' in Path(p).name)
 classes=B/'classes';classes.mkdir(exist_ok=True)
 result=subprocess.run([str(JAVA.with_name('javac.exe')),'-J-Xmx128M','-encoding','UTF-8','-cp',str(loader),'-d',str(classes),str(Path(__file__).with_name('SodiumMixinPreflight.java'))],capture_output=True,text=True,creationflags=subprocess.CREATE_NO_WINDOW)
 (B/'compile.log').write_text(result.stdout+result.stderr,encoding='utf-8');assert result.returncode==0
 profile=B/'profile';mods=profile/'mods';mods.mkdir(parents=True,exist_ok=True)
 for jar in (T/'profiles/addon-iris-off-1/client/mods').glob('*.jar'):
  target=mods/jar.name
  if not target.exists():shutil.copy2(jar,target)
 production=T/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.1.jar'
 shutil.copy2(production,mods/'dreamwalker-production.jar')
 downloads=json.loads((A/'downloads.json').read_text(encoding='utf-8'))
 for kind in ('sodium','iris'):
  for jar in list(mods.glob(kind+'-*.jar')):
   assert jar.parent.resolve()==mods.resolve() and mods.resolve().is_relative_to(A.resolve());jar.unlink()
  jar=Path(downloads[kind]['path']);assert sha(jar)==downloads[kind]['sha256'];shutil.copy2(jar,mods/jar.name)
 with zipfile.ZipFile(mods/'mixin-preflight-qa.jar','w',zipfile.ZIP_DEFLATED) as z:
  z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'dw_sodium_preflight','version':'1.0','environment':'client','entrypoints':{'preLaunch':['dev.dreamwalker.preflight.SodiumMixinPreflight']},'depends':{'fabricloader':'>=0.16.10','bloodborne_dw':'*','sodium':'*','indium':'*','iris':'*'}}))
  for path in classes.rglob('*.class'):z.write(path,path.relative_to(classes).as_posix())
 index=args.index('--gameDir');args[index+1]=str(profile)
 args=[('-Xmx1G' if x.startswith('-Xmx') else '-Dfabric.modsFolder='+str(mods) if x.startswith('-Dfabric.modsFolder=') else x) for x in args if not x.startswith('-Ddw.route')]
 receipt=B/('result-'+sha(production)+'.txt');args.insert(0,'-Ddw.sodium.preflight.out='+str(receipt))
 argument=B/'client.args';argument.write_text('\n'.join(json.dumps(a) for a in args)+'\n',encoding='utf-8')
 started=time.monotonic()
 with (B/'console.log').open('w',encoding='utf-8') as log:
  run=subprocess.run([str(JAVA),'@'+str(argument)],cwd=profile,stdout=log,stderr=subprocess.STDOUT,timeout=150,creationflags=subprocess.CREATE_NO_WINDOW)
 status=receipt.read_text(encoding='utf-8') if receipt.exists() else 'NO_RESULT'
 output={'status':status.splitlines()[0],'exitCode':run.returncode,'seconds':time.monotonic()-started,'productionSha256':sha(production),'receipt':str(receipt),'scope':'Class transformations only; not a graphical/functional test'}
 (B/('receipt-'+sha(production)+'.json')).write_text(json.dumps(output,indent=2)+'\n',encoding='utf-8')
 assert run.returncode==0 and status.startswith('PASS_MIXIN_CLASS_TRANSFORMS_ONLY'),status[:3000]
if __name__=='__main__':
 try:main()
 except BaseException:(B/'error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
