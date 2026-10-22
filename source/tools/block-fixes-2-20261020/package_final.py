from common import *
import shutil,time

def archive(target,entries):
 target.parent.mkdir(parents=True,exist_ok=True);pending=target.with_suffix(target.suffix+'.pending')
 with zipfile.ZipFile(pending,'w',zipfile.ZIP_DEFLATED,compresslevel=3) as z:
  for name,p in sorted(entries.items()):ordinary(p);z.write(p,name)
 with zipfile.ZipFile(pending) as z:assert z.testzip() is None
 pending.replace(target)

def main():
 log_to('package-final');start=time.time();prod=DELIVERY/'game'/GAME;prodsha=sha(prod);native=read(OUT/'native/receipt.json');client=read(OUT/'native-assets-only/receipt.json');first=read(OUT/'saved-map/first/receipt.json');reopen=read(OUT/'saved-map/reopen/receipt.json');verification=read(OUT/'world-verification.json');assets=read(OUT/'asset-validation.json')
 for r in [native,first,reopen]:assert r['status']=='PASS' and r['productionJarSha256']==prodsha
 assert client['status']=='PASS' and client['jarSha256']==prodsha and reopen['sameSavedWorldReopenedWithoutReset'];assert verification['status']=='PASS' and assets['status']=='PASS'
 proof=read(OUT/'native-assets-only/asset-preservation.json');harness=OUT/'native-assets-only/asset-harness.jar';assert sha(harness)==client['qaSha256']==proof['harnessSha256']
 with zipfile.ZipFile(prod) as z,zipfile.ZipFile(harness) as tested:
  for n in proof['exactAssetEntries']:assert z.read(n)==tested.read(n),n
  assert json.loads(z.read('fabric.mod.json'))['version']==VERSION
 write(OUT/'final-assets-equivalence.json',{'status':'PASS','productionJarSha256':prodsha,'clientHarnessSha256':sha(harness),'exactTestedAssetEntries':len(proof['exactAssetEntries'])})
 world_entries={};world_manifest=[]
 for p in files(WORLD):
  if p.name=='session.lock':continue
  rel=p.relative_to(WORLD).as_posix();world_entries['bloodborne-blocks-fixed-2/'+rel]=p;world_manifest.append({'path':rel,'bytes':p.stat().st_size,'sha256':sha(p)})
 write(OUT/'fixed-world-manifest.json',world_manifest);mapzip=DELIVERY/'map/bloodborne-blocks-fixed-2.zip';archive(mapzip,world_entries)
 entries={};old_source=PREVIOUS/'source/block-repair-source-overlay.zip'
 with zipfile.ZipFile(old_source) as z:
  for n in z.namelist():
   p=PROJECT/n
   if p.is_file():entries[n]=p
 for dirname in ['tools/block-fixes-20261020','tools/block-fixes-2-20261020','src/blockqa']:
  for p in files(PROJECT/dirname):
   if '__pycache__' not in p.parts:entries[p.relative_to(PROJECT).as_posix()]=p
 for rel in ['gradle.properties','src/architecture/resources/fabric.mod.json','src/architecture/java/dev/dreamwalker/bloodbornedw/composite/MaterialSlabs.java','src/architecture/java/dev/dreamwalker/bloodbornedw/composite/MaterialSlabClient.java']:entries[rel]=PROJECT/rel
 for n in read(OUT/'binary-preservation.json')['changedEntries']:
  if n.endswith('.class') or n=='fabric.mod.json':continue
  rel='src/architecture/resources/'+n;entries[rel]=PROJECT/rel
 sourcezip=DELIVERY/'source/block-repair-source-overlay.zip';archive(sourcezip,entries)
 checks=DELIVERY/'checks';checks.mkdir(exist_ok=True)
 for name in ['conversion.json','native-pavement-preflight.json','world-verification.json','asset-validation.json','binary-preservation.json','final-assets-equivalence.json','empty-shards.json','world-copy.json']:shutil.copyfile(OUT/name,checks/name)
 for src,name in [('native/receipt.json','server-receipt.json'),('native/checks.json','server-checks.json'),('native-assets-only/receipt.json','client-receipt.json'),('native-assets-only/client-assets.json','client-model-baking.json'),('saved-map/first/receipt.json','map-first-load.json'),('saved-map/first/checks.json','map-first-checks.json'),('saved-map/reopen/receipt.json','map-reopen.json'),('saved-map/reopen/checks.json','map-reopen-checks.json')]:shutil.copyfile(OUT/src,checks/name)
 report=f'''Dreamwalker BB materials.1-streaming.7-blocks.2 — основание под камнями мостовой

Причина
На участке около X=143, Y=74, Z=-985 остались декоративные варианты «Камни на полу»,
«Пёстрая каменная плитка» и «Куча обломков». В blocks.1 они не попали в преобразование
плит: их коллизия отсутствовала или занимала небольшой фрагмент модели. Среди
обычных новых плит они оставляли провалы с отдельными видимыми камнями.

Исправление
Добавлены 32 варианта настоящих SlabBlock со сплошным каменным основанием и
исходным каменным декором. У всех есть коллизия обычной плиты, верхнее/нижнее
положение, объединение половин и заполнение/слив воды ведром. Декоративные PNG
и UV не изменялись. Положение исходных декоративных вершин на карте сохранено;
добавлен только непрозрачный объём основания. Декор использует слой cutout.
На карте исправлено ещё 1 151 ячеек: 772 нижние и 379 верхних плит.
Удалены 7 011 старых привязок этих объектов и 4 703 опустевших block entities.
Посторонние объекты и 17 ячеек с их общими данными сохранены.
Исправления blocks.1, включая деревянный пол и удаление невидимых блоков, включены.

Установка
1. При закрытой игре/сервере замените JAR blocks.1 на:
   game/{GAME}
   На сервере и у клиентов нужна эта же версия.
2. Распакуйте map/bloodborne-blocks-fixed-2.zip в saves. В архиве одна папка
   bloodborne-blocks-fixed-2; имя в игре — «Bloodborne — исправленная мостовая 2».
   Для исправления уже расставленной мостовой нужны оба файла: JAR и новая карта.
   Одна замена JAR не конвертирует старые расставленные блоки.
3. Предыдущие JAR и карта blocks.1 не изменены. Полная копия карты blocks.1 перед
   этой правкой находится отдельно в backup/bloodborne-before-block-fixes.zip.
   Личные изменения установленного игрового мира в эту выдаваемую копию не входят.

Проверки
• Итоговый production JAR: {native['checks']} серверных проверок PASS.
• На фактической карте проверены блоки (143,74,-985), (143,74,-984),
  (144,74,-979), (144,74,-987) и соседняя исходная плита (141,74,-989).
  Сущность стоит на мостовой Y=74.5, не падает через прежние отверстия и проходит
  последовательность плит. Луч выделяет каждую проверяемую плиту.
  Сохранены UUID и typed payload постороннего объекта (-1,91,-788).
• {first['checks']} проверок карты прошли при первом открытии и {reopen['checks']}
  после сохранения/повторного открытия того же QA-мира без сброса.
• Minecraft загрузил {client['states']} состояния моделей и {client['faces']} граней
  без отсутствующих текстур. Все {assets['completeSlabBaseFacesVerifiedOpaque']} грани
  оснований новых моделей проверены на полную непрозрачность. Декор и UV сверены
  с исходными моделями. Полный визуальный обход города не проводился.
• Проверены все {verification['chunks']} чанков. Не осталось старых блоков из
  исправляемых семейств и ссылок на удалённые невидимые блоки. Пустых файлов
  owner_shards, возвращённой истории, логов, дампов и кешей в карте нет.
• Хеши {verification['previousMapFilesHashVerifiedUnchanged']} файлов предыдущей
  карты и JAR blocks.1 совпали с исходными. Все остальные классы мода побайтно
  взяты из blocks.1; изменены только MaterialSlabs, добавление слоя cutout и ресурсы.

Подробные результаты — checks/. Исходники исправлений и инструменты текущего
проекта — source/block-repair-source-overlay.zip. QA-классов в игровом JAR нет.
SHA-256 JAR: {prodsha}
'''
 (DELIVERY/'README-RU.txt').write_text(report,encoding='utf8')
 hashes={p.relative_to(DELIVERY).as_posix():sha(p) for p in [prod,mapzip,sourcezip,DELIVERY/'backup/bloodborne-before-block-fixes.zip']}
 (DELIVERY/'SHA256SUMS.txt').write_text(''.join(v+'  '+k+'\n' for k,v in hashes.items()),encoding='ascii')
 write(DELIVERY/'release.json',{'status':'PASS','version':'materials.1-streaming.7-blocks.2','sha256':hashes,'newPavementCells':1151,'newMaterials':32,'serverChecks':native['checks'],'cityChecksPerOpen':first['checks'],'nativeModelStates':client['states'],'allMapChunksVerified':verification['chunks'],'previousMapAndJarUnchanged':True,'previousCleanupPreserved':True,'worldFiles':len(world_entries),'sourceFiles':len(entries),'seconds':time.time()-start});print('PASS',json.dumps(hashes),len(world_entries),'map files')
if __name__=='__main__':main()
