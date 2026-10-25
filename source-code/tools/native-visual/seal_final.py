"""Seal only an accepted pair. No conversion or changes to canonical world data."""
import datetime,hashlib,json,os,re,shutil,traceback
from pathlib import Path
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def plain(p):
 for parent in (p,*p.parents):
  try:info=parent.lstat()
  except FileNotFoundError:continue
  assert not info.st_file_attributes&0x400,'Reparse point: '+str(parent)
def write(p,v):
 plain(p);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1<<20),b''):h.update(b)
 return h.hexdigest()
def copy_exact(source,target):
 plain(source);plain(target)
 target.parent.mkdir(parents=True,exist_ok=True);expected=sha(source)
 if not target.exists():shutil.copy2(source,target)
 assert sha(target)==expected;return expected
def main():
 selection=read(T/'addons/selection.json');addon=selection['selectedAddon'];assert addon in ('sildur','makeup','makeup32')
 proof=read(T/'addons/client-compat-delta-proof.json');jar=Path(proof['after']['path']);digest=proof['after']['sha256'];assert sha(jar)==digest
 full=read(T/'runs/full-2/receipt.json');sun=read(T/('runs/sun-'+addon+'-2/receipt.json'))
 assert full['status']==sun['status']=='PASS' and full['addon']==sun['addon']==addon
 assert full['productionSha256']==sun['productionSha256']==digest
 if not selection['defaultShadersEnabled']:
  default=read(T/'runs/full-3/receipt.json');assert default['status']=='PASS' and default['addon']=='iris-off' and default['productionSha256']==digest
  assert read(T/'addons/default-full-metrics.json')['coverage']['complete']
  assert read(T/'addons/default-full-visual-review.json')['status']=='ACCEPTED_WITH_RECORDED_VISUAL_SCOPE'
 assert read(T/'addons/full-metrics.json')['coverage']['complete']
 assert read(T/'addons/full-visual-review.json')['status']=='ACCEPTED_WITH_RECORDED_VISUAL_SCOPE'
 assert read(T/'addons/sun-review.json')['status']=='PASS_DYNAMIC_SUN_SHADOWS_AND_COLD_REOPEN'
 assert read(T/'main-ready.json')['status']=='MAIN_BASE_PAIR_READY_OPTIONAL_ADDONS_PENDING'
 assert read(T/'chunk-analysis/base/summary.json')['status']=='EXISTING_JOURNALS_AND_JFR_ANALYZED'
 stage=T/'delivery/final';plain(stage);assert not os.path.lexists(stage),'Final staging must be fresh'
 assert stage.resolve().is_relative_to((T/'delivery').resolve())
 world=stage/'bloodborne-native-grid-city';world.mkdir(parents=True,exist_ok=False)
 manifest={}
 for root,dirs,files in os.walk(T/'world',followlinks=False):
  for name in [*dirs,*files]:assert not (Path(root)/name).lstat().st_file_attributes&0x400
  for name in files:
   source=Path(root)/name;relative=source.relative_to(T/'world');manifest[relative.as_posix()]=copy_exact(source,world/relative)
 marker=read(T/'delivery/base/bloodborne-native-grid-city/NATIVE-VISUAL-READY.json')
 marker.update({'status':'READY_VISUAL_PHYSICS_V1','runtimeSha256':digest,'validatedOptionalBackend':'Sodium0.5.13/Indium1.0.36/Iris1.7.6','validatedShader':addon,'defaultShadersEnabled':selection['defaultShadersEnabled'],'shaderFullRouteAndSavedWorldColdReopenPassed':True})
 write(world/'NATIVE-VISUAL-READY.json',marker);manifest['NATIVE-VISUAL-READY.json']=sha(world/'NATIVE-VISUAL-READY.json')
 output=stage/'dreamwalker-bb-0.1.0-v12.7.4-visual-physics.1.jar';copy_exact(jar,output)
 config=stage/'client-settings';profile=T/'profiles/full-2/client'
 for name in ('iris.properties','sodium-options.json','modernfix-mixins.properties','bloodborne-rp.json'):
  copy_exact(profile/'config'/name,config/'config'/name)
 iris=config/'config/iris.properties';properties=iris.read_text(encoding='utf-8');properties,count=re.subn(r'(?m)^enableShaders=(?:true|false)$','enableShaders='+str(selection['defaultShadersEnabled']).lower(),properties);assert count==1;iris.write_text(properties,encoding='utf-8')
 (config/'INSTALL-RU.txt').write_text('Скопировать config и shaderpacks в корень клиентской игры рядом с mods.\nSodium0.5.13 + Indium1.0.36 + Iris1.7.6 установлены в client_mods.\nШейдер MakeUp NoEffects с тёплой палитрой Cocoa и солнечными тенями проверен на маршруте. По умолчанию выключен: его цена в FPS значительна. Включается в меню Iris при желании.\nПользовательские options.txt, клавиши, разрешение и ресурс-паки не перезаписываются. Профиль проверки:1280x720, дальность6 чанков, предел кадров60; серверная simulation-distance4. При16ГиБRAM обе JVM использовали heap4ГиБ каждая. Цифры отчёта относятся к этим условиям.\n',encoding='utf-8')
 downloads=read(T/'addons/downloads.json');key={'sildur':'sildurFast','makeup':'makeupLow','makeup32':'makeupNear'}[addon];shader=Path(downloads[key]['path'])
 assert sha(shader)==downloads[key]['sha256'];copy_exact(shader,config/'shaderpacks'/shader.name)
 copy_exact(profile/'shaderpacks'/(shader.name+'.txt'),config/'shaderpacks'/(shader.name+'.txt'))
 for name in ('MakeUp-LGPL-3.0.txt','GPL-3.0.txt','SOURCES.json'):
  copy_exact(T/'addons/licenses'/name,config/'licenses'/name)
 thirdparty={'shader':downloads[key],'mods':{kind:full['addonFiles'][kind] for kind in ('sodium','indium','iris')}}
 write(stage/'THIRDPARTY-DOWNLOADS.json',thirdparty)
 server_config=stage/'server-settings'
 for name in ('modernfix-mixins.properties','bloodborne-rp.json'):
  copy_exact(T/'profiles/full-2/server/config'/name,server_config/'config'/name)
 (server_config/'INSTALL-RU.txt').write_text('config перенести в корень серверного профиля рядом с mods. Это проверенные настройки ModernFix и DreamwalkerRP.\nIris/Sodium/Indium на сервер не устанавливать.\nПрофиль проверки: view-distance6, simulation-distance4, Java17, heap4ГиБ. Порты/IP/ops/whitelist не заменяются.\n',encoding='utf-8')
 for name in ('DECISIONS-RU.md','CHUNK-LOADING-ANALYSIS-RU.txt','NIGHT-TASK-RU.txt'):
  copy_exact(T/name,stage/name)
 result={'status':'FINAL_PAIR_ACCEPTED_LOCAL_DELIVERY_STAGED','utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'world':str(world),'jar':{'path':str(output),'sha256':digest},'worldFiles':manifest,'selectedAddon':addon,'selection':selection,'clientSettings':str(config),'clientSettingsFiles':{p.relative_to(config).as_posix():sha(p) for p in config.rglob('*') if p.is_file()},'serverSettings':str(server_config),'serverSettingsFiles':{p.relative_to(server_config).as_posix():sha(p) for p in server_config.rglob('*') if p.is_file()},'canonicalWorldUntouched':True,'sourcePackageAndDesktopInstallPending':True}
 write(T/'final-ready.json',result)
if __name__=='__main__':
 try:main()
 except BaseException:(T/'seal-final-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
