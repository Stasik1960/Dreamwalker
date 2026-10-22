from common import *
import copy,shutil,subprocess,time,gc
sys.path.insert(0,str(PROJECT/'tools'))
from world_io import *

def cell(world,p):
 x,y,z=p;region=RegionFile.open(world/'region'/f'r.{x>>9}.{z>>9}.mca');stored=region.get_chunk((x>>4)&31,(z>>4)&31);root=compound(stored.nbt().root);sec=next(t for t in root['sections'].value if compound(t)['Y'].value==y>>4);pal,ii=section_blocks(sec);state=copy.deepcopy(pal[ii[((y&15)<<8)|((z&15)<<4)|(x&15)]])
 be=next((copy.deepcopy(b) for b in root.get('block_entities',Tag(9,[],10)).value if [compound(b)[k].value for k in ['x','y','z']]==list(p)),None);return state,be

def main():
 log_to('city-check-wrapper');assert read(OUT/'conversion.json')['status']=='PASS';out=OUT/'saved-map';out.mkdir(exist_ok=True);server=out/'server';server.mkdir(exist_ok=True);world=server/'bloodborne';second='--reopen' in sys.argv
 if not second:
  assert not world.exists(),'Never reset an existing QA save';world.mkdir()
  for p in files(WORLD):
   if p.name=='session.lock':continue
   dest=world/p.relative_to(WORLD);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,dest)
  nbt=read_nbt(world/'level.dat');d=compound(compound(nbt.root)['Data']);d['SpawnX']=Tag(3,300000);d['SpawnY']=Tag(3,100);d['SpawnZ']=Tag(3,300000);rules=compound(d['GameRules'])
  for k,v in {'doMobSpawning':'false','doDaylightCycle':'false','doWeatherCycle':'false','randomTickSpeed':'0','doTraderSpawning':'false','doPatrolSpawning':'false'}.items():rules[k]=Tag(8,v)
  settings=Tag(10,{'layers':Tag(9,[Tag(10,{'height':Tag(3,1),'block':Tag(8,'minecraft:bedrock')})],10),'biome':Tag(8,'minecraft:plains'),'lakes':Tag(1,0),'features':Tag(1,0),'structure_overrides':Tag(9,[],8)})
  compound(compound(compound(d['WorldGenSettings'])['dimensions'])['minecraft:overworld'])['generator']=Tag(10,{'type':Tag(8,'minecraft:flat'),'settings':settings});write_nbt(world/'level.dat',nbt)
 else:assert read(out/'first/receipt.json')['status']=='PASS'
 case=out/('reopen' if second else 'first');case.mkdir(exist_ok=True);probes=[]
 for pos in [[143,74,-985],[143,74,-984],[144,74,-979],[144,74,-987],[141,74,-989],[-1,91,-788]]:
  state,be=cell(world,pos);probes.append(Tag(10,{'pos':Tag(11,pos),'state':state,'entity':be if be is not None else Tag(10,{}),'pavement':Tag(1,pos[0]>0)}))
 write_nbt(case/'expected.nbt',NbtFile('',Tag(10,{'probes':Tag(9,probes,10)})));del probes;gc.collect()
 mods=server/'mods';mods.mkdir(exist_ok=True)
 for p in (PROJECT/'build/runtime-server-rp-streaming7/mods').glob('*.jar'):
  if any(s in p.name.lower() for s in ['fabric-api','geckolib','mclib','lithium']):shutil.copyfile(p,mods/p.name)
 prod=DELIVERY/'game'/GAME;shutil.copyfile(prod,mods/'dreamwalker-bb-blocks.jar');qa=PROJECT/'build/libs'/('dreamwalker-block-repair-checks-'+VERSION+'.jar');addon=mods/'pavement-city-checks.jar'
 with zipfile.ZipFile(qa) as old,zipfile.ZipFile(addon,'w',zipfile.ZIP_DEFLATED) as z:
  name='dev/dreamwalker/bloodbornedw/composite/PavementMapChecks.class';z.writestr(name,old.read(name));z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'dw_pavement_city_checks','version':'1.0','environment':'server','entrypoints':{'main':['dev.dreamwalker.bloodbornedw.composite.PavementMapChecks']},'depends':{'bloodborne_dw':'*'}}))
 (server/'eula.txt').write_text('eula=true\n');(server/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\nview-distance=2\nsimulation-distance=2\nmax-tick-time=180000\nspawn-protection=0\n',encoding='utf8')
 prev=read(ROOT/'research/graphical-streaming-20261019/after/prepared.json')['serverArgs'];cp=prev[prev.index('-cp')+1]
 args=['-Xms128M','-Xmx3G','-XX:ActiveProcessorCount=2','-Dfabric.modsFolder='+str(mods),'-Ddw.pavement.output='+str(case),'-cp',cp,'net.fabricmc.loader.impl.launch.knot.KnotServer','--universe',str(server),'--world','bloodborne','nogui'];af=case/'launch.args';af.write_text('\n'.join('"'+s.replace('\\','/').replace('"','\\"')+'"' for s in args)+'\n',encoding='utf8');start=time.time();child=None
 try:
  with (case/'console.log').open('w',encoding='utf8') as log:child=subprocess.Popen([str(JAVA),'@'+str(af)],cwd=server,stdout=log,stderr=subprocess.STDOUT,stdin=subprocess.PIPE,creationflags=subprocess.CREATE_NO_WINDOW);child.wait(timeout=480)
  report=read(case/'checks.json');assert report['status']=='PASS',report.get('error')
  write(case/'receipt.json',{'status':'PASS','exitCode':child.returncode,'seconds':time.time()-start,'checks':len(report['checks']),'productionJarSha256':sha(mods/'dreamwalker-bb-blocks.jar'),'sameSavedWorldReopenedWithoutReset':second,'testCopyOnly':True,'reportedCoordinateRegion':[143,74,-985]});print('PASS',len(report['checks']))
 except BaseException as failure:write(case/'receipt.json',{'status':'FAIL','seconds':time.time()-start,'error':str(failure)});raise
 finally:
  if child and child.poll() is None:
   try:child.stdin.write(b'stop\n');child.stdin.flush();child.wait(timeout=30)
   except Exception:child.kill();child.wait(timeout=10)
if __name__=='__main__':main()
