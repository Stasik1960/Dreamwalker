import json,shutil,subprocess,traceback
from run import prepare
from common import *
def main():
 wait_foreign();case=OUT/'preflight';candidate=json.loads((OUT/'candidate.json').read_text());p=prepare(case,Path(candidate['jar']))
 ordinary(SERVER/'mods/dreamwalker-native-route-qa.jar').unlink()
 addon=next(x for x in (PROJECT/'build/libs').glob('dreamwalker-streaming7-checks-*.jar') if not x.name.endswith('-dev.jar'));installed=SERVER/'mods'/addon.name;shutil.copyfile(addon,installed)
 args=[s for s in p['serverArgs'] if not s.startswith('-Ddw.route.output=')];args[:0]=['-Ddw.verify.output='+str(case),'-Ddw.verify.preflight=true'];prior=OUT/'preflight-core-passed-dense-fixture-error';priorChecks=[]
 if prior.exists():
  old=json.loads((prior/'checks.json').read_text());assert old['error']=='java.lang.IllegalStateException: dense palette has 513 distinct architectural states';assert json.loads((prior/'receipt.json').read_text())['productionJarSha256']==candidate['sha256'];priorChecks=old['checks'];args.insert(0,'-Ddw.verify.denseOnly=true')
 argsfile(SERVER/'preflight.args',args);child=None
 try:
  with (case/'console.log').open('w') as log:
   child=subprocess.Popen([str(JAVA),'@'+str(SERVER/'preflight.args')],cwd=SERVER,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
   write(case/'receipt.json',{'status':'RUNNING','pid':child.pid,'productionJarSha256':candidate['sha256']});child.wait(timeout=420)
  report=json.loads((case/'checks.json').read_text());assert child.returncode==0 and report['status']=='PASS_TARGETED_PREFLIGHT',report
  write(case/'receipt.json',{'status':'PASS','productionJarSha256':candidate['sha256'],'qaSha256':sha(addon),'checks':len(report['checks'])+len(priorChecks),'priorChecks':len(priorChecks),'currentChecks':len(report['checks'])})
  if priorChecks:write(case/'combined-checks.json',{'status':'PASS_TARGETED_PREFLIGHT','sameProductionSha256':candidate['sha256'],'checks':priorChecks+report['checks'],'fixtureRepair':'Dense state selection only; successful core checks were not repeated.'})
 finally:
  if child and child.poll() is None:
   try:child.stdin.write(b'stop\n');child.stdin.flush();child.wait(timeout=60)
   except Exception:child.kill();child.wait(timeout=15)
  if installed.exists():ordinary(installed).unlink()
if __name__=='__main__':
 try:main()
 except BaseException:(OUT/'preflight-error.txt').write_text(traceback.format_exc());raise
