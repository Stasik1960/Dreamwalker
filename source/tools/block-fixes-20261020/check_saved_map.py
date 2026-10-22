from common import *
import copy,shutil,subprocess,time,gc
sys.path.insert(0,str(PROJECT/'tools'))
from world_io import *
WORLD=DELIVERY/'map/bloodborne-blocks-fixed'
def cell(world,p):
 x,y,z=p;region=RegionFile.open(world/'region'/f'r.{x>>9}.{z>>9}.mca');chunk=region.get_chunk((x>>4)&31,(z>>4)&31);root=compound(chunk.nbt().root);sec=next(s for s in root['sections'].value if compound(s)['Y'].value==y>>4);pal,indices=section_blocks(sec);state=copy.deepcopy(pal[indices[((y&15)<<8)|((z&15)<<4)|(x&15)]])
 entity=next((copy.deepcopy(b) for b in root.get('block_entities',Tag(9,[],10)).value if [compound(b)[k].value for k in ['x','y','z']]==list(p)),None)
 return state,entity,root
def main():
 log_to('saved-map-wrapper');assert read(OUT/'conversion.json')['status']=='PASS'
 out=OUT/'saved-map';out.mkdir(exist_ok=True);server=out/'server';server.mkdir(exist_ok=True);world=server/'bloodborne';second='--reopen' in sys.argv
 if not second:
  assert not world.exists(),'Do not reset a saved verification world'
  world.mkdir()
  for p in files(WORLD):
   if p.name=='session.lock':continue
   dest=world/p.relative_to(WORLD);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,dest)
  level=read_nbt(world/'level.dat');d=compound(compound(level.root)['Data']);d['SpawnX']=Tag(3,300000);d['SpawnY']=Tag(3,100);d['SpawnZ']=Tag(3,300000)
  rules=compound(d['GameRules'])
  for k,v in {'doMobSpawning':'false','doDaylightCycle':'false','doWeatherCycle':'false','randomTickSpeed':'0','doTraderSpawning':'false','doPatrolSpawning':'false'}.items():rules[k]=Tag(8,v)
  settings=Tag(10,{'layers':Tag(9,[Tag(10,{'height':Tag(3,1),'block':Tag(8,'minecraft:bedrock')})],10),'biome':Tag(8,'minecraft:plains'),'lakes':Tag(1,0),'features':Tag(1,0),'structure_overrides':Tag(9,[],8)})
  compound(compound(compound(d['WorldGenSettings'])['dimensions'])['minecraft:overworld'])['generator']=Tag(10,{'type':Tag(8,'minecraft:flat'),'settings':settings})
  write_nbt(world/'level.dat',level)
  installed=read(OUT/'installed-slab-states.json');meta={m['id']:m for m in read(RES/'bloodborne_dw/restored-material-slabs.json')['materials']};probes=[]
  for typ in ['bottom','top']:
   row=next(r for r in installed if r['position'][0]=='.' and 80<=r['position'][2]<=120 and r['type']==typ and r['id'] in meta and 'base' not in meta[r['id']] and meta[r['id']]['source']=='bloodborne_dw:source_addon_slabs_0' and meta[r['id']]['cell']==[0,0]);probes.append(row['position'][1:])
  row=next(r for r in installed if r['position'][0]=='.' and r['id'].startswith('bloodborne_dw:restored_slab_overlay_'));probes.append(row['position'][1:]);probes.append([-212,65,-99])
  # Preserve a real non-floor owner/typed payload from an existing city chunk.
  _,_,r=cell(world,probes[0]);entity=next((b for b in r.get('block_entities',Tag(9,[],10)).value if 'resident' in compound(b) and 'slab' not in compound(compound(b)['resident'])['id'].value),None)
  if entity is None:
   for p in [[253,66,-915],[603,70,-100],[940,28,129],[284,118,-649],[290,137,-656]]:
    _,_,r=cell(world,p);entity=next((b for b in r.get('block_entities',Tag(9,[],10)).value if 'resident' in compound(b) and 'slab' not in compound(compound(b)['resident'])['id'].value),None)
    if entity is not None:break
  assert entity is not None;probes.append([compound(entity)[k].value for k in ['x','y','z']]);write(out/'probe-positions.json',probes)
  del installed,meta,r;gc.collect()
 else:assert read(out/'first/receipt.json')['status']=='PASS'
 case=out/('reopen' if second else 'first');case.mkdir(exist_ok=True);probes=read(out/'probe-positions.json');tags=[];inputs=[]
 for p in probes:
  state,entity,_=cell(world,p);tags.append(Tag(10,{'pos':Tag(11,p),'state':state,'hasEntity':Tag(1,entity is not None),'entity':entity if entity else Tag(10,{})}));region=world/'region'/f'r.{p[0]>>9}.{p[2]>>9}.mca';inputs.append({'path':str(region.relative_to(world)),'sha256':sha(region)})
 write_nbt(case/'expected.nbt',NbtFile('',Tag(10,{'probes':Tag(9,tags,10)})));write(case/'inputs.json',inputs)
 mods=server/'mods';mods.mkdir(exist_ok=True)
 for p in (PROJECT/'build/runtime-server-rp-streaming7/mods').glob('*.jar'):
  if not any(s in p.name.lower() for s in ['fabric-api','geckolib','mclib','lithium']):continue
  shutil.copyfile(p,mods/p.name)
 production=DELIVERY/'game/dreamwalker-bb-materials.1-streaming.7-blocks.1.jar';shutil.copyfile(production,mods/'dreamwalker-bb-blocks.jar')
 oldqa=next((PROJECT/'build/runtime-server-rp-streaming7/mods').glob('dreamwalker-streaming7-checks-*.jar'));shutil.copyfile(oldqa,mods/'city-checks.jar')
 (server/'eula.txt').write_text('eula=true\n');(server/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\nview-distance=2\nsimulation-distance=2\nmax-tick-time=180000\nspawn-protection=0\n',encoding='utf8')
 prev=read(ROOT/'research/graphical-streaming-20261019/after/prepared.json')['serverArgs'];cp=prev[prev.index('-cp')+1]
 args=['-Xms128M','-Xmx3G','-XX:ActiveProcessorCount=2','-Dfabric.modsFolder='+str(mods),'-Ddw.verify.output='+str(case),'-Ddw.verify.reopenOnly=true','-cp',cp,'net.fabricmc.loader.impl.launch.knot.KnotServer','--universe',str(server),'--world','bloodborne','nogui']
 af=case/'launch.args';af.write_text('\n'.join('"'+s.replace('\\','/').replace('"','\\"')+'"' for s in args)+'\n',encoding='utf8');child=None;start=time.time();del tags;gc.collect()
 try:
  with (case/'console.log').open('w',encoding='utf8') as log:
   child=subprocess.Popen([str(JAVA),'@'+str(af)],cwd=server,stdout=log,stderr=subprocess.STDOUT,stdin=subprocess.PIPE,creationflags=subprocess.CREATE_NO_WINDOW);child.wait(timeout=600)
  report=read(case/'checks.json');assert report['status']=='PASS_FRESH_REOPEN',report.get('error')
  outputs=[{'path':r['path'],'sha256':sha(world/r['path'])} for r in inputs];write(case/'outputs.json',outputs)
  write(case/'receipt.json',{'status':'PASS','exitCode':child.returncode,'seconds':time.time()-start,'checks':len(report['checks']),'productionJarSha256':sha(mods/'dreamwalker-bb-blocks.jar'),'testWorld':str(world),'sourceDeliveryMapUntouched':True,'sameSavedWorldReopenedWithoutReset':second});print('PASS',case,len(report['checks']))
 except BaseException as failure:write(case/'receipt.json',{'status':'FAIL','error':str(failure),'seconds':time.time()-start});raise
 finally:
  if child and child.poll() is None:
   try:child.stdin.write(b'stop\n');child.stdin.flush();child.wait(timeout=30)
   except Exception:child.kill();child.wait(timeout=10)
if __name__=='__main__':main()
