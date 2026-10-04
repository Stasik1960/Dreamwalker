import argparse,hashlib,json,os,shutil,subprocess,time,zipfile
from pathlib import Path

root=Path(__file__).resolve().parent.parent
game=Path('C:/Users/vakir/AppData/Roaming/eh/updates/eh_s2')
pack=game/'mods.zip'
if not pack.exists():pack=root/'build/supplied-modpack-recovered.zip'
jdk=Path('C:/Users/vakir/Documents/ChatGPT/DW/Bloodborne-Blocks/build/toolchain/jdk-17.0.20.1')
ap=argparse.ArgumentParser();ap.add_argument('--client',action='store_true');ap.add_argument('--jar',type=Path,required=True);ap.add_argument('--world',type=Path,required=True);ap.add_argument('--heap');ap.add_argument('--with-dh',action='store_true');ap.add_argument('--suffix',default='');ap.add_argument('--cameras',type=Path);ap.add_argument('--extra-mods',type=Path);ap.add_argument('--disable-probejs',action='store_true');args=ap.parse_args()
heap=args.heap or ('4G' if args.client else '3G')
if args.suffix and not all(c.isalnum() or c=='-' for c in args.suffix):raise ValueError('invalid runtime suffix')
run=root/'build'/(('full-pack-compact-client'if args.client else'full-pack-compact-server')+args.suffix)
if run.exists():raise ValueError('QA runtime already exists; choose a fresh --suffix')
run.mkdir();mods=run/'mods';mods.mkdir()
with zipfile.ZipFile(pack)as z:
 for item in z.infolist():
  if item.filename.endswith('.jar')and'bloodborne'not in Path(item.filename).name.lower()and(args.with_dh or 'distanthorizons'not in Path(item.filename).name.lower()):(mods/Path(item.filename).name).write_bytes(z.read(item))
release=args.jar.resolve();shutil.copy2(release,mods/release.name)
extras={}
if args.extra_mods:
 for extra in sorted(args.extra_mods.glob('*.jar')):
  if (mods/extra.name).exists():raise ValueError('additional mod conflicts with supplied filename '+extra.name)
  shutil.copy2(extra,mods/extra.name);extras[extra.name]=hashlib.sha256(extra.read_bytes()).hexdigest()
if args.cameras:shutil.copy2(args.cameras,run/'qa-cameras.json')
overrides={}
if args.disable_probejs:
 config_rel='kubejs/config/probejs.json';config_source=root/'docs/compact-gallery/client-config'/config_rel
 target=run/config_rel;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(config_source,target)
 overrides[config_rel]=hashlib.sha256(config_source.read_bytes()).hexdigest()
libraries=[p for p in(game/'libraries').rglob('*.jar')if'authlib'not in str(p)and'log4j-slf4j18-impl'not in str(p)]
spec=json.loads((root/'build/unified-server-launch.json').read_text());original=spec['command'][spec['command'].index('-cp')+1].split(';')
libraries += [Path(p)for p in original if'/authlib/'in p.replace('\\','/')]
classpath=';'.join(p.as_posix()for p in[game/'minecraft.jar',*libraries])
qa=root/'build/compat-smoke';classes=qa/'classes';classes.mkdir(parents=True,exist_ok=True)
compile_cp=classpath+';'+str(mods/'worldedit-mod-7.2.15.jar')
javac_args=qa/'compile.args';source=root/'tools/compat-smoke/java/dev/dreamwalker/bloodborneblocks/qa/WorldEditSmokeInitializer.java'
javac_args.write_text('--release\n17\n-encoding\nUTF-8\n-cp\n"'+compile_cp.replace('\\','/')+'"\n-d\n"'+classes.as_posix()+'"\n"'+source.as_posix()+'"\n')
subprocess.run([str(jdk/'bin/javac.exe'),'@'+str(javac_args)],check=True,creationflags=subprocess.CREATE_NO_WINDOW)
with zipfile.ZipFile(mods/'bloodborne-runtime-qa.jar','w',zipfile.ZIP_DEFLATED)as z:
 for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'bloodborne_runtime_qa','version':'1','environment':'*','entrypoints':{'main':['dev.dreamwalker.bloodborneblocks.qa.WorldEditSmokeInitializer']}}))
ids=['architecture_part']
for scope in('logical','city'):ids.extend(d['id']for d in json.loads((root/f'src/main/resources/bloodborne_blocks/{scope}/definitions.json').read_bytes())['blocks'])
(run/'qa-block-ids.txt').write_text('\n'.join(ids))
world=run/('saves/CompactCity'if args.client else'world')
shutil.copytree(args.world.resolve(),world)
if args.client:
 (run/'options.txt').write_text('version:3465\nrenderDistance:3\nsimulationDistance:3\nmaxFps:30\nguiScale:2\nfullscreen:false\nsoundCategory_master:0.0\n')
 (run/'config').mkdir(exist_ok=True)
 if(game/'config/DistantHorizons.toml').exists():shutil.copy2(game/'config/DistantHorizons.toml',run/'config/DistantHorizons.toml')
 (run/'config/iris.properties').write_text('enableShaders=false\n')
 tail=['net.fabricmc.loader.impl.launch.knot.KnotClient','--version','1.20.1','--gameDir',run.as_posix(),'--assetsDir','C:/Users/vakir/.gradle/caches/fabric-loom/assets','--assetIndex','1.20.1-5','--username','LocalMapQA','--accessToken','0','--quickPlaySingleplayer','CompactCity','--width','1280','--height','720']
else:
 (run/'eula.txt').write_text('eula=true\n')
 (run/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nlevel-name=world\nonline-mode=true\nview-distance=3\nsimulation-distance=3\nmax-tick-time=180000\n')
 tail=['net.fabricmc.loader.impl.launch.knot.KnotServer','nogui']
argfile=run/'launch.args'
native_dir=(game/'natives/mustdie/x86-64').as_posix()
argfile.write_text('\n'.join(['-Xms512M','-Xmx'+heap,'-Dbloodborne.qa.visibleTicks='+('1400'if args.cameras else'600'),'-Dfile.encoding=UTF-8','"-Djava.library.path='+native_dir+'"','"-Dorg.lwjgl.librarypath='+native_dir+'"','-Xlog:gc:file=gc.log:time,uptime,level,tags','-cp','"'+classpath+'"',*['"'+t+'"'for t in tail]])+'\n')
start=time.monotonic();log=run/'console.log'
with log.open('w',encoding='utf-8')as out:
 proc=subprocess.Popen([str(jdk/'bin/java.exe'),'@'+str(argfile)],cwd=run,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True,creationflags=subprocess.CREATE_NO_WINDOW)
 print('QA_PID',proc.pid,'CLIENT',args.client,flush=True);ready=False;listed=False;dumps=set()
 while proc.poll()is None and time.monotonic()-start<420:
  content=log.read_text('utf-8',errors='replace');elapsed=time.monotonic()-start
  if not args.client and'WORLD_EDIT_RUNTIME_QA_PASSED'in content and not listed:proc.stdin.write('list\n');proc.stdin.flush();listed=True
  if not args.client and'There are 0 of a max of'in content:
   ready=True;proc.stdin.write('save-all flush\nstop\n');proc.stdin.flush();print('QA_READY',round(elapsed,1),flush=True);break
  if args.client and'FULL_PACK_CLIENT_WORLD_VISIBLE'in content:ready=True
  if int(elapsed)//90 not in dumps and elapsed>=90:
   period=int(elapsed)//90;dumps.add(period)
   with(run/f'threads-{period}.txt').open('w',encoding='utf-8')as target:
    try:subprocess.run([str(jdk/'bin/jcmd.exe'),str(proc.pid),'Thread.print'],stdout=target,stderr=subprocess.STDOUT,timeout=10,creationflags=subprocess.CREATE_NO_WINDOW)
    except subprocess.TimeoutExpired:pass
   print('QA_PROGRESS',round(elapsed),flush=True)
  time.sleep(1)
 if proc.poll()is None and not ready:
  if not args.client:proc.stdin.write('stop\n');proc.stdin.flush()
  else:proc.terminate()
 try:code=proc.wait(timeout=60)
 except subprocess.TimeoutExpired:proc.terminate();code=proc.wait(timeout=20)
proof={'passed':ready and code==0,'client':args.client,'exitCode':code,'seconds':round(time.monotonic()-start,1),'jarSha256':hashlib.sha256(release.read_bytes()).hexdigest(),'packSha256':hashlib.sha256(pack.read_bytes()).hexdigest(),'heap':heap,'distantHorizonsIncluded':args.with_dh,'additionalMods':extras,'configurationOverrides':overrides}
if args.disable_probejs:
 proof['probejsAutomaticDumpMessages']=sum(log.read_text('utf8',errors='replace').count(v)for v in ('automatic dump will be triggered','Started generating type files','Snippets generated.'))
 if proof['probejsAutomaticDumpMessages']:proof['passed']=False
proof['bakedModelModifierFailures']=log.read_text('utf8',errors='replace').count('Failed to modify baked model after bake')
if proof['bakedModelModifierFailures']:proof['passed']=False
if args.client:
 proof['clientModelProof']=json.loads((run/'client-model-proof.json').read_bytes())if(run/'client-model-proof.json').exists()else None
 if proof['clientModelProof'] is None:proof['passed']=False
 elif set(proof['clientModelProof'])!=set(ids)-{'architecture_part'}or any(v<=0 for v in proof['clientModelProof'].values()):proof['passed']=False
gc_text=(run/'gc.log').read_text(errors='replace') if(run/'gc.log').exists() else ''
import re
proof['gcHeapAfterGcMiB']=[int(x) for x in re.findall(r'->([0-9]+)M',gc_text)]
proof['worldeditProof']=json.loads((run/'worldedit-runtime-proof.json').read_text())if(run/'worldedit-runtime-proof.json').exists()else None
(run/'launch-proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps({k:v for k,v in proof.items()if k not in ('gcHeapAfterGcMiB','clientModelProof')},ensure_ascii=False),flush=True)
raise SystemExit(0 if proof['passed'] else 1)
