from common import *
import shutil,time

def archive(target,entries):
 pending=target.with_suffix(target.suffix+'.pending');target.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(pending,'w',zipfile.ZIP_DEFLATED,compresslevel=3) as z:
  for name,p in sorted(entries.items()):ordinary(p);z.write(p,name)
 with zipfile.ZipFile(pending) as z:assert z.testzip() is None
 pending.replace(target)

def main():
 log_to('package-final');start=time.time();prod=DELIVERY/'game/dreamwalker-bb-materials.1-streaming.7-blocks.1.jar';prodsha=sha(prod)
 mechanics=read(OUT/'native-ground-final/receipt.json');reopen=read(OUT/'saved-map/reopen/receipt.json');worldcheck=read(OUT/'final-world-check.json')
 for receipt in [mechanics,reopen]:assert receipt['status']=='PASS' and receipt['productionJarSha256']==prodsha
 assert reopen['sameSavedWorldReopenedWithoutReset'] and worldcheck['status']=='PASS'
 assert read(OUT/'final-mechanics-equivalence.json')['status']=='PASS'
 client=read(OUT/'native-assets-only/receipt.json');assets=read(OUT/'native-assets-only/asset-preservation.json');assert client['status']=='PASS'
 harness=OUT/'native-assets-only/asset-harness.jar';assert sha(harness)==client['qaSha256']==assets['harnessSha256']
 with zipfile.ZipFile(prod) as final,zipfile.ZipFile(harness) as tested:
  for n in assets['exactAssetEntries']:assert final.read(n)==tested.read(n),n
  assert not any('BlockRepairChecks' in n or '/blockassetsqa/' in n or '/blockrepairqa/' in n for n in final.namelist())
  assert json.loads(final.read('fabric.mod.json'))['version'].endswith('streaming.7-blocks.1')
 write(OUT/'final-assets-equivalence.json',{'status':'PASS','nativeClientTestInputSha256':client['jarSha256'],'nativeClientTestHarnessSha256':sha(harness),'finalJarSha256':prodsha,'identicalAssetEntries':len(assets['exactAssetEntries']),'scope':'Exact slab models, blockstates, material manifest, PNGs and animation metadata checked by Minecraft native model baking. Final production mechanics checked separately.'})
 world=DELIVERY/'map/bloodborne-blocks-fixed';world_entries={};world_manifest=[]
 for p in files(world):
  if p.name=='session.lock':continue
  rel=p.relative_to(world).as_posix();world_entries['bloodborne-blocks-fixed/'+rel]=p;world_manifest.append({'path':rel,'bytes':p.stat().st_size,'sha256':sha(p)})
 write(OUT/'fixed-world-manifest.json',world_manifest)
 mapzip=DELIVERY/'map/bloodborne-blocks-fixed.zip';archive(mapzip,world_entries)
 # A source overlay for the existing streaming.7 project, with the exact
 # modified classes/resources and the conversion/verification tools.
 preservation=read(OUT/'binary-preservation.json');entries={}
 for name in preservation['changedEntries']:
  if name.endswith('.class'):
   rel='src/architecture/java/'+name.split('$',1)[0].removesuffix('.class')+'.java'
   p=PROJECT/rel
  elif name in ['fabric.mod.json'] or name in preservation['deleted']:continue
  else:rel='src/architecture/resources/'+name;p=PROJECT/rel
  assert p.is_file(),str(p);entries[rel]=p
 for rel in ['gradle.properties','build.gradle','settings.gradle','tools/world_io.py']:
  if (PROJECT/rel).is_file():entries[rel]=PROJECT/rel
 for parent in [PROJECT/'tools/block-fixes-20261020',PROJECT/'src/blockqa']:
  for p in files(parent):
   if '__pycache__' in p.parts:continue
   entries[p.relative_to(PROJECT).as_posix()]=p
 sourcezip=DELIVERY/'source/block-repair-source-overlay.zip';archive(sourcezip,entries)
 evidence=DELIVERY/'checks';evidence.mkdir(exist_ok=True)
 evidence_names=['binary-preservation.json','conversion.json','final-world-check.json','final-mechanics-equivalence.json','final-assets-equivalence.json','native-slab-preflight.json','source-model-evidence.json','original-resource-pack-check.json','original-side-textures-restored.json','texture-references.json','world-copy.json','runtime-class-before.json']
 for name in evidence_names:shutil.copyfile(OUT/name,evidence/name)
 for src,name in [('native-ground-final/receipt.json','server-receipt.json'),('native-ground-final/checks.json','server-checks.json'),('native-assets-only/client-assets.json','client-model-baking.json'),('native-assets-only/receipt.json','client-receipt.json'),('saved-map/first/receipt.json','map-first-load.json'),('saved-map/reopen/receipt.json','map-reopen.json'),('saved-map/reopen/checks.json','map-reopen-checks.json')]:shutil.copyfile(OUT/src,evidence/name)
 report=f'''Dreamwalker BB materials.1-streaming.7-blocks.1 — исправление блоков

Установка
1. Закройте Minecraft/сервер. Замените предыдущий JAR dreamwalker-bb этим файлом:
   game/dreamwalker-bb-materials.1-streaming.7-blocks.1.jar
   На сервере и у клиентов должна стоять одна версия. Остальные зависимости прежние.
2. Распакуйте map/bloodborne-blocks-fixed.zip в saves. В архиве одна папка мира
   bloodborne-blocks-fixed; в игре — «Bloodborne — исправленные плиты».
   Для исправления существующих плит и удаления невидимых блоков нужна именно
   эта скорректированная карта. Одна замена JAR не выполняет конвертацию старого мира.
3. Исходная карта сохранена без изменений. Её резервная копия:
   backup/bloodborne-before-block-fixes.zip

Что исправлено
• Восстановлены настоящие плиты: 119 материалов и 38 служебных сочетаний плиты
  с соседним статическим объектом. У плит коллизия 16×8×16, текстуры шести граней,
  верхнее/нижнее положение, объединение двух половин и работа с ведром воды.
  Где оригинальный ресурспак содержал боковые грани, использованы их материалы;
  для отсутствующих граней взята текстура поверхности.
• На карте заменены 141 296 ячеек материалов и 5 219 резервных плит.
  178 наружных ячеек больших декоративных плит обрезаны по границе занятых
  соседних блоков. Корни плит и соседние постройки сохранены.
• Исправлена коллизия 17 вариантов деревянного пола, включая «Дощатая плитка
  [90566]»: по размеру модели, с обрезкой горизонтального захода меньше половины
  соседнего блока. Обновлены также существующие статические варианты пола.
• Удалены обе регистрации source_invisible_carrier/source_invisible_solid_carrier.
  С карты удалены все 315 796 таких блоков; итоговый полный проход не нашёл
  оставшихся ссылок на них в чанках, сущностях и остальных NBT-файлах мира.
• Встроенная вода у восстановленных плит убрана. Новые плиты сухие;
  воду можно добавить и забрать обычным ведром по правилам Minecraft.

Проверка
• Fabric Minecraft 1.20.1, итоговый production JAR: {mechanics['checks']} проверок PASS.
  Проверены все материалы, вода/слив, двойные плиты, установка деревянного пола
  на камень и опора персонажа на край настила, удаление соседнего общего объекта.
• Нативный клиент Minecraft загрузил {client['states']} состояния моделей,
  {client['faces']} граней без отсутствующих текстур. Проверенные модели и PNG
  побайтово совпадают с итоговым JAR. Проверка выполнена отдельным клиентским
  стендом загрузки моделей; полный визуальный обход города не проводился.
• Скорректированная карта открыта и сохранена сервером, затем повторно открыта
  тем же сохранением без сброса. Проверены плиты обоих положений, смешанная ячейка,
  деревянный пол и сохранность UUID/данных другого объекта.
  Серверный стенд использовал Fabric API, GeckoLib и Lithium; полный RP-набор
  с лимитом 3 ГБ памяти не завершил загрузку из-за нехватки памяти.
• Полностью проверены {worldcheck['chunks']} чанков. Хеши {worldcheck['sourceMapFilesHashVerified']}
  файлов исходной карты и исходного streaming.7 JAR остались прежними.

Дополнительно
source/block-repair-source-overlay.zip — исходники исправлений, модели и инструменты
конвертации для существующего проекта streaming.7. Служебные QA-моды в игровой
JAR не входят. Машиночитаемые результаты находятся в checks.

SHA-256 игрового JAR: {prodsha}
'''
 (DELIVERY/'README-RU.txt').write_text(report,encoding='utf8')
 hashes={p.relative_to(DELIVERY).as_posix():sha(p) for p in [prod,mapzip,sourcezip,DELIVERY/'backup/bloodborne-before-block-fixes.zip']}
 (DELIVERY/'SHA256SUMS.txt').write_text(''.join(value+'  '+name+'\n' for name,value in hashes.items()),encoding='ascii')
 write(DELIVERY/'release.json',{'status':'PASS','version':'materials.1-streaming.7-blocks.1','sha256':hashes,'serverChecks':mechanics['checks'],'clientModelStates':client['states'],'mapChunksVerified':worldcheck['chunks'],'sameMapSavedAndReopened':True,'originalMapUnchanged':True,'sourceOverlayFiles':len(entries),'mapFiles':len(world_entries),'seconds':time.time()-start})
 print('PASS',json.dumps(hashes),len(world_entries),'world files',len(entries),'source files')
if __name__=='__main__':main()
