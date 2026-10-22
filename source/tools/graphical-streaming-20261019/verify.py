import copy,json,shutil,subprocess,sys,traceback
from common import *
sys.path.insert(0,str(PROJECT/'tools/city-streaming-20261018'))
from world_io import *

def main():
 wait_foreign();saveFirst='--standalone-save' in sys.argv;reopenNormal='--reopen-normal-save' in sys.argv
 out=OUT/('normal-save' if saveFirst else 'verification');out.mkdir(exist_ok=False)
 measured=json.loads((OUT/'after/prepared.json').read_text())
 if saveFirst or reopenNormal:
  after=measured;assert after['jarSha256']==json.loads((OUT/'candidate.json').read_text())['sha256']
  if reopenNormal:
   assert json.loads((OUT/'normal-save/receipt.json').read_text())['status']=='PASS'
   for row in json.loads((OUT/'normal-save/saved-outputs.json').read_text())['regions']:assert sha(Path(row['path']))==row['sha256'],'Working map reset between save and reopen'
  props=(SERVER/'server.properties').read_text();props='\n'.join('max-tick-time=60000' if line.startswith('max-tick-time=') else line for line in props.splitlines())+'\n';(SERVER/'server.properties').write_text(props)
 else:
  after=measured if measured['status']=='PASS_NATIVE_GRAPHICAL_DEDICATED_ROUTE' else json.loads((OUT/'validation-tail/prepared.json').read_text())
  assert after['status']=='PASS_NATIVE_GRAPHICAL_DEDICATED_ROUTE' and after['jarSha256']==measured['jarSha256']
 probes=[];inputs=[]
 for x,y,z in [(253,66,-915),(603,70,-100),(940,28,129),(284,118,-649),(290,137,-656)]:
  region=WORLD/'region'/f'r.{x>>9}.{z>>9}.mca';inputs.append({'path':str(region),'sha256':sha(region)})
  chunk=RegionFile.open(region).get_chunk((x>>4)&31,(z>>4)&31).nbt().root.value
  section=next(s for s in chunk['sections'].value if s.value['Y'].value==y>>4)
  palette,indices=section_blocks(section);state=palette[indices[((y&15)*16+(z&15))*16+(x&15)]]
  entity=next((b for b in chunk.get('block_entities',Tag(TAG_LIST,[],TAG_COMPOUND)).value if [b.value[k].value for k in ['x','y','z']]==[x,y,z]),None)
  probes.append(Tag(TAG_COMPOUND,{'pos':Tag(TAG_INT_ARRAY,[x,y,z]),'state':copy.deepcopy(state),'hasEntity':Tag(TAG_BYTE,entity is not None),'entity':copy.deepcopy(entity) if entity else Tag(TAG_COMPOUND,{})}))
 expected=out/'expected.nbt';write_nbt(expected,NbtFile('',Tag(TAG_COMPOUND,{'probes':Tag(TAG_LIST,probes,TAG_COMPOUND)})))
 scope='STANDALONE_DEDICATED_NORMAL_SAVE' if saveFirst else 'FRESH_REOPEN_OF_NORMAL_SERVER_SAVE' if reopenNormal else 'NORMAL_FINAL_ROUTE_SAVE_READ_FROM_DISK_WITHOUT_RESET'
 write(out/'saved-inputs.json',{'status':scope,'workingWorld':str(WORLD),'regions':inputs,'expectedSha256':sha(expected)})
 nativeQa=SERVER/'mods/dreamwalker-native-route-qa.jar'
 if nativeQa.exists():ordinary(nativeQa).unlink()
 version=next(line.split('=',1)[1].strip() for line in (PROJECT/'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
 addon=PROJECT/'build/libs'/('dreamwalker-streaming7-checks-'+version+'.jar');assert addon.is_file()
 shutil.copyfile(addon,SERVER/'mods'/addon.name)
 args=[s for s in after['serverArgs'] if not s.startswith('-Ddw.route.output=')];args.insert(0,'-Ddw.verify.output='+str(out));args.insert(0,'-Ddw.verify.reopenOnly=true');argsfile(SERVER/'verify.args',args)
 child=None
 try:
  with (out/'console.log').open('w') as log:
   child=subprocess.Popen([str(JAVA),'@'+str(SERVER/'verify.args')],cwd=SERVER,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
   write(out/'receipt.json',{'status':'RUNNING','pid':child.pid,'productionJarSha256':after['jarSha256'],'qaSha256':sha(addon)})
   child.wait(timeout=420)
  report=json.loads((out/'checks.json').read_text());assert child.returncode==0 and report['status']=='PASS_FRESH_REOPEN',report
  write(out/'receipt.json',{'status':'PASS','exitCode':child.returncode,'productionJarSha256':after['jarSha256'],'qaSha256':sha(addon),'checks':len(report['checks']),'scope':scope})
  write(out/'saved-outputs.json',{'regions':[{'path':path,'sha256':sha(Path(path))} for path in sorted({row['path'] for row in inputs})]})
 except BaseException as failure:
  write(out/'receipt.json',{'status':'FAIL','error':str(failure),'pid':child.pid if child else None,'exitCode':child.poll() if child else None,'productionJarSha256':after['jarSha256'],'qaSha256':sha(addon),'scope':scope})
  raise
 finally:
  if child and child.poll() is None:
   try:child.stdin.write(b'stop\n');child.stdin.flush();child.wait(timeout=60)
   except Exception:child.kill();child.wait(timeout=15)

if __name__=='__main__':
 try:main()
 except BaseException:(OUT/'verification-error.txt').write_text(traceback.format_exc());raise
