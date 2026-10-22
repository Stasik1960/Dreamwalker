import json,shutil,zipfile
from common import *

def read(path):return json.loads(path.read_text())
def optional(path):return read(path) if path.exists() else {'status':'NOT_RUN','checks':0}
def peaks(case):
 result={side:{'privateBytes':0,'workingSet':0} for side in ['client','server']}
 for line in (OUT/case/'memory.jsonl').read_text().splitlines():
  for row in json.loads(line)['processes']:
   p=result[row['label']];p['privateBytes']=max(p['privateBytes'],row['privateBytes']);p['workingSet']=max(p['workingSet'],row['processLifetimePeakWorkingSet'])
 return result
def main():
 release=ROOT/'deliverables/streaming-7-20261020';evidence=release/'evidence';evidence.mkdir(exist_ok=True);settings=release/'server';settings.mkdir(exist_ok=True)
 candidate=read(OUT/'candidate.json');comparison=read(OUT/'comparison.json');b=comparison['before'];a=comparison['after']
 beforeConditions=read(OUT/'baseline/prepared.json');afterConditions=read(OUT/'after/prepared.json')
 for key in ['java','qaSha256','graphics','optionsSha256','serverPropertiesSha256']:
  assert beforeConditions[key]==afterConditions[key], 'Noncomparable conditions: '+key
 for side in ['client','server']:
  assert [arg for arg in beforeConditions[side+'Args'] if arg.startswith(('-Xms','-Xmx'))]==[arg for arg in afterConditions[side+'Args'] if arg.startswith(('-Xms','-Xmx'))]
 normalizeMods=lambda rows:sorted((r['side'],r['name'],r['sha256']) for r in rows if not r['name'].startswith('dreamwalker-bb-'))
 assert normalizeMods(beforeConditions['mods'])==normalizeMods(afterConditions['mods']), 'Mod composition changed'
 assert read(OUT/'baseline/world-input.json')==read(OUT/'after/world-input.json'), 'Different city map input'
 for case,s in [('baseline',b),('after',a)]:
  phases=read(OUT/case/'client/client-route.json')['phases']
  assert s['phaseEvidence']==[p for p in phases if p['phase'] in comparison['comparablePhases']], 'Stale comparison data'
 verify=optional(OUT/'verification/receipt.json');normal=optional(OUT/'normal-save/receipt.json');preflight=read(OUT/'preflight/receipt.json');reuse=read(OUT/'membership-fix/reused-checks.json')
 assert preflight['status']=='PASS' and read(OUT/'build-membership.json')['status']=='PASS'
 assert 'All 4 required tests passed' in (OUT/'build-membership.log').read_text()
 assert sha(Path(candidate['jar']))==candidate['sha256']==reuse['productionJarSha256']
 for validation in [normal,verify]:
  if validation['status']=='PASS':assert validation['productionJarSha256']==candidate['sha256']
 assert preflight['productionJarSha256']==reuse['previousJarSha256']
 build=read(OUT/'build-production.json');assert build['status']=='PASS' and build['sourceHashes']=={name:sha(PROJECT/name) for name in PRODUCTION_INPUTS}
 with zipfile.ZipFile(release/'sources/changed-sources.zip') as archived:
  for name in PRODUCTION_INPUTS:assert archived.read('bloodborne complete/'+name)==(PROJECT/name).read_bytes(), 'Outdated source archive: '+name
 probe=read(OUT/'membership-fix/nbt-probe.json');probeInput=read(OUT/'membership-fix/nbt-input.json')
 assert probe['status']=='PASS_EXACT_RETAINED_FIELDS' and probeInput['readerSourceSha256']==sha(PROJECT/'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/MembershipNbtReader.java')
 measured=read(OUT/'after/prepared.json');tail=read(OUT/'validation-tail/prepared.json')
 validationCase='after' if measured['status']=='PASS_NATIVE_GRAPHICAL_DEDICATED_ROUTE' else 'validation-tail' if tail['status']=='PASS_NATIVE_GRAPHICAL_DEDICATED_ROUTE' else 'standalone-save'
 headless=validationCase=='standalone-save';unloaded=None
 saveVerified=normal['status']==verify['status']=='PASS' and verify['scope']=='FRESH_REOPEN_OF_NORMAL_SERVER_SAVE'
 if headless:
  assert measured['status']==tail['status']=='FAIL'
 else:
  validated=read(OUT/validationCase/'prepared.json');assert validated['status']=='PASS_NATIVE_GRAPHICAL_DEDICATED_ROUTE' and validated['jarSha256']==candidate['sha256']
  client=read(OUT/validationCase/'client/client-route.json');server=read(OUT/validationCase/'server/server-route.json');unloaded=server['afterUnloadGc']
  assert client['status']=='PASS_NATIVE_GRAPHICAL_ROUTE' and server['status']=='PASS_SAVED'
 assert manifest(SOURCE)==read(OUT/'source-map.json')
 for side in ['client','server']:
  for row in read(OUT/(side+'-mod-input.json')):assert sha(Path(row['source']))==row['sha256']
 for case,s in [('baseline',b),('after',a)]:
  metadata=read(OUT/case/'frame-profile.json')['metadata']
  for p in s['phaseEvidence']:
   for side in ['client','server']:
    if metadata[side].get('status')=='RECORDING_UNAVAILABLE_AFTER_WATCHDOG':continue
    assert metadata[side]['firstEventEpochNs']<=p['startEpochNs'] and metadata[side]['lastEventEpochNs']>=p['endEpochNs']
 causes={}
 for case in ['baseline','after']:
  profile=read(OUT/case/'frame-profile.json');causes[case]={}
  for thread in ['client|tower|ALL','client|tower|MAIN','client|tower|Netty-*']:
   row=profile['phaseThreads'][thread]
   causes[case][thread]={'weightedAllocationBytes':row['weightedAllocationBytes'],'executionSamples':row['executionSamples'],'targets':{key:row['targets'][key] for key in ['CompositeClient.*','DecodePool.contribution','VanillaChunkBuilder.*','VanillaNbtIo.*','LithiumHashPalette.*']},'topSelfMethods':row['topSelfMethods'][:10]}
 write(evidence/'cause-profile.json',causes)
 (evidence/'README-RU.txt').write_text('Численная пара: только baseline/ и after/, общий завершённый этап tower (фасад → башня).\nvalidation-tail — отдельная незавершённая функциональная проверка с другими heap/watchdog; не включена в сравнение.\nnormal-save и verification — только проверка дискового сохранения, не измерение графической скорости.\nШтатный серверный JFR не сохранился при аварии. Серверные GC и выделения неизвестны, а не равны нулю; ориентироваться на null в comparison/summary и metadata server в frame-profile. Нулевые промежуточные счётчики отсутствующих событий не доказывают отсутствие работы.\nСерверные тики и память взяты из CSV/системных счётчиков. Последняя оборванная строка ticks при аварии исключена анализатором, исходный CSV сохранён.\nJFR allocation weights оценивают оборот временных выделений; категории стеков пересекаются и не складываются.\n',encoding='utf-8')
 for name in ['comparison.json','source-map.json','client-mod-input.json','server-mod-input.json','build-production.json','build-production.log','build-membership.json','build-membership.log','frozen-measurement-sources.zip']:
  shutil.copyfile(OUT/name,evidence/name)
 for name in ['reused-checks.json','nbt-probe.json','nbt-input.json','nbt-fields.csv','nbt-probe.log']:
  shutil.copyfile(OUT/'membership-fix'/name,evidence/('membership-'+name))
 for case in ['baseline','after']+(['validation-tail'] if validationCase!='after' else []):
  dest=evidence/case;dest.mkdir(exist_ok=True);(dest/'screenshots').mkdir(exist_ok=True)
  for name in ['summary.json','frame-profile.json','world-input.json','memory.jsonl','validation-only-gc.json','server-console.log','client-console.log']:
   if (OUT/case/name).exists():shutil.copyfile(OUT/case/name,dest/name)
  for side in ['client','server']:
   for name in [side+'-route.json',side+'-frames.csv' if side=='client' else 'server-ticks.csv']:
    if (OUT/case/side/name).exists():shutil.copyfile(OUT/case/side/name,dest/name)
  for p in (OUT/case/'screenshots').glob('*.png'):shutil.copyfile(p,dest/'screenshots'/p.name)
  prepared=read(OUT/case/'prepared.json');conditions={key:prepared[key] for key in ['status','java','jarSha256','qaSha256','graphics','optionsSha256','serverPropertiesSha256','mods']}
  conditions['heaps']={side:[arg for arg in prepared[side+'Args'] if arg.startswith(('-Xms','-Xmx'))] for side in ['client','server']};write(dest/'conditions.json',conditions)
 for case in ['preflight','normal-save-burst-oom','normal-save-spawn-wait-fixture','normal-save','verification']:
  for name in ['receipt.json','checks.json','progress.json','combined-checks.json','saved-inputs.json','saved-outputs.json','dense-palette.nbt','console.log','heap-diagnostic.txt']:
   if (OUT/case/name).exists():shutil.copyfile(OUT/case/name,evidence/(case+'-'+name))
 with zipfile.ZipFile(release/'sources/qa-tools-and-sources.zip','w',zipfile.ZIP_DEFLATED) as archive:
  for folder in [PROJECT/'src/routeqa',PROJECT/'src/verifyqa',TOOLS]:
   for p in files(folder):
    if p.suffix in ['.java','.json','.py','.gradle']:archive.write(p,'bloodborne complete/'+p.relative_to(PROJECT).as_posix())
  for name in ['CompositeMembershipReadGameTests','CompositeMembershipScanGameTests','CompositeShapeReuseGameTests','CompositeStreamingGameTests','CompositeNbtSnapshotGameTests']:
   p=PROJECT/('src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/'+name+'.java');archive.write(p,'bloodborne complete/'+p.relative_to(PROJECT).as_posix())
  for p in [PROJECT/'tools/collision-streaming-20261018/JfrShapeSummary.java',PROJECT/'tools/city-streaming-20261018/world_io.py']:archive.write(p,'bloodborne complete/'+p.relative_to(PROJECT).as_posix())
 (release/'sources/README-RU.txt').write_text('changes-from-streaming.6.patch — накопительный патч к принятой streaming.6, пути от корня DW-city-mesh-20261018. В текущей рабочей папке изменения уже применены.\nchanged-sources.zip — полные версии десяти изменённых production-файлов. Предшествовавшие изменения streaming.6 сохранены.\nqa-tools-and-sources.zip — инструменты сборки, упаковки, анализа и отдельные проверочные моды; проверочные классы не входят в игровой JAR.\nТочные исходники измерительной обвязки 9c2549e5... находятся в evidence/frozen-measurement-sources.zip; функциональная проверка сохранения использует более позднюю отдельную обвязку.\nСборка Java 17: gradlew.bat --offline --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1G remapJar (с локальным WorldEdit API, см. evidence/build-production.json).\nФинальная упаковка описана в tools/graphical-streaming-20261019/candidate.py: из сырой сборки взяты только перечисленные изменённые классы/метаданные, 8948 остальных ZIP-элементов скопированы из исходной streaming.6. Скрипт создаёт новый выходной файл и отказывается его перезаписывать.\nУстанавливать одну и ту же streaming.7 на клиент и сервер.\n',encoding='utf-8')
 (settings/'jvm-args.txt').write_text('-Xms128M\n-Xmx9G\n',encoding='utf-8')
 (settings/'server.properties.fragment').write_text('view-distance=8\nsimulation-distance=6\n',encoding='utf-8')
 (settings/'capture-profile.sh').write_text((PROJECT/'tools/streaming-6-20261019/capture-profile.sh').read_text(encoding='utf-8'),encoding='utf-8',newline='\n')
 (settings/'README-RU.txt').write_text('Для сервера с общим лимитом 12 ГБ: -Xmx9G оставляет запас вне кучи.\nПрименить jvm-args.txt к существующему запуску Fabric и заменить две строки из server.properties.fragment.\nФайлы start.sh и server.properties на рабочем столе не изменялись.\nВ численном сравнении сервер 9G / клиент 5G, дальности 8/6 и 120 FPS в обеих версиях.\nДля Linux-хостинга: из каталога запущенного сервера выполнить bash capture-profile.sh JAVA_PID (номер Java-процесса).\n90 секунд двигаться тем же маршрутом через плотный район, затем покинуть его и дождаться 30 секунд выгрузки.\nНужны полученный серверный JFR, журнал, сведения о Java, модах, памяти и фактически доставленных чанках.\n',encoding='utf-8')
 g=lambda n:'н/д' if n is None else f'{n/2**30:.2f}'
 seconds=lambda n:'н/д' if n is None else f'{n/1000:.2f}'
 percent=lambda x,y:f'{(y/x-1)*100:+.1f}%'
 def activity(s):
  result={'roots':0,'updates':0,'ownerBytes':0,'distance':0}
  for p in s['phaseEvidence']:
   start=p['countersAtStart'];end=p['counters'];result['roots']+=end['terrain']['sectionBuiltOwnedRoots']-start['terrain']['sectionBuiltOwnedRoots'];result['updates']+=end['terrainUpdates']['overhangRebuilds']-start['terrainUpdates']['overhangRebuilds'];result['ownerBytes']+=end['ownerPayloadBytes']-start['ownerPayloadBytes'];result['distance']+=p['nativeDistance']
  return result
 x=activity(b);y=activity(a);verifiedChecks=verify['checks'] if verify['status']=='PASS' else 0;normalChecks=normal['checks'] if normal['status']=='PASS' else 0;checks=preflight['checks']+4+verifiedChecks+normalChecks
 c0=b['lifetimePeaks']['client'];c1=a['lifetimePeaks']['client'];s0=b['lifetimePeaks']['server'];s1=a['lifetimePeaks']['server']
 oldUnload=b.get('serverAfterUnloadGc')
 unloadText='Сопоставимого замера памяти после выгрузки района нет: полные игровые прогоны не завершились.' if unloaded is None else f'После выгрузки и GC сервера: {g(oldUnload["heapUsed"])} → {g(unloaded["heapUsed"])} ГиБ кучи; ожидающие shape-публикации {oldUnload["snapshots"]["shapeSnapshotPendingCells"]} → {unloaded["snapshots"]["shapeSnapshotPendingCells"]}.' if oldUnload else f'После выгрузки и GC нового сервера: {g(unloaded["heapUsed"])} ГиБ кучи, ожидающие shape-публикации {unloaded["snapshots"]["shapeSnapshotPendingCells"]}; сопоставимого старого замера нет.'
 validationText='Новый полный маршрут завершён: фасад, башня, кирпичный район; штатные выгрузка и сохранение.' if validationCase=='after' else 'Фасад и башня завершены в измеренной паре. Полный графический переход в кирпичный район не завершён.' if headless else 'Фасад и башня завершены в измеренном проходе; переход башня → кирпичный район завершён отдельной проверкой с тем же игровым JAR.'
 if saveVerified:validationText+=' Отдельный сервер 9G проверил пять городских точек по очереди, выгрузил вновь загруженные чанки и штатно сохранил карту. Область спавна оставалась загруженной. Следующий выделенный JVM открыл эти же файлы без сброса и проверил те же точки.'
 elif headless:validationText+=' Полное городское сохранение и повторное открытие не подтверждены; состояния проверок: '+normal['status']+' / '+verify['status']+'. Плотная палитра прошла отдельную native ChunkSerializer save/reopen проверку.'
 fullPair=set(comparison['comparablePhases'])=={'tower','brick'}
 segment='фасад → башня → кирпичный район' if fullPair else 'фасад → башня'
 tailLimit='Оба полных маршрута завершены.' if fullPair else 'Численно сравниваются только общие завершённые участки; незавершённый хвост исключён.'
 if validationCase!='after':tailLimit+=' Новый измеренный хвост также исключён. Дополнительная функциональная проверка не смешивалась с численной парой.'
 extra=''
 if validationCase!='after':
  caseForPeaks='validation-tail' if headless else validationCase
  vp=peaks(caseForPeaks);write(evidence/caseForPeaks/'peaks.json',vp)
  extra=f'Отдельная {"незавершённая " if headless else ""}проверка хвоста: Private Bytes клиент / сервер {g(vp["client"]["privateBytes"])} / {g(vp["server"]["privateBytes"])}; Peak Working Set {g(vp["client"]["workingSet"])} / {g(vp["server"]["workingSet"])} ГиБ.\n'
  extra+='Функциональная проверка хвоста использовала сервер 9G / клиент 4G, тайм-аут watchdog 180 с и обвязку с записью заполненных порций; численная пара осталась 9G / 5G, watchdog 60 с и прежняя обвязка. Эти результаты не смешивались.\n'
  if (OUT/validationCase/'validation-only-gc.json').exists():extra+='Только отдельная проверка хвоста использовала GC сервера перед входом клиента; численная пара такого GC не использовала.\n'
 report=f'''Dreamwalker BB materials.1-streaming.7 — 20.10.2026

{'Результат частичный: длинные зависания и остановки сервера в кирпичном районе не устранены.' if headless else ''}

Настоящее окно Minecraft 1280×720 и выделенный сервер localhost,
очищенная карта, отобранные моды Dreamwalker-RP-Test-20261019.
Одинаковы: Java 17.0.15, Fabric Loader 0.19.5, обвязка, графика (120 FPS), исходная карта,
-Xmx9G сервер / -Xmx5G клиент, view-distance=8, simulation-distance=6.

Сопоставимый завершённый маршрут: {segment}.
Показатель                              streaming.6 → streaming.7
Кадры >100 мс                           {b['framesOver100Ms']} → {a['framesOver100Ms']} ({percent(b['framesOver100Ms'],a['framesOver100Ms'])})
Кадры >1 с                              {b['framesOver1000Ms']} → {a['framesOver1000Ms']}
Максимальный интервал кадра              {b['maxFrameMs']/1000:.3f} → {a['maxFrameMs']/1000:.3f} с
Сумма интервалов кадров >100 мс           {b['sumFrameIntervalsOver100Ms']/1000:.2f} → {a['sumFrameIntervalsOver100Ms']/1000:.2f} с
Длительность участка                    {b['routeSeconds']:.2f} → {a['routeSeconds']:.2f} с
Работа тика: P99 / максимум              {b['serverTickWorkP99Ms']:.1f} / {b['serverTickWorkMaxMs']:.1f} → {a['serverTickWorkP99Ms']:.1f} / {a['serverTickWorkMaxMs']:.1f} мс
Максимальный интервал начала тиков       {b['serverStartIntervalMaxMs']/1000:.3f} → {a['serverStartIntervalMaxMs']/1000:.3f} с
Фактически принятые чанки                {b['acceptedChunkDeliveries']} → {a['acceptedChunkDeliveries']}
Отдельные custom payload владельцев      {x['ownerBytes']/2**20:.2f} → {y['ownerBytes']/2**20:.2f} МиБ
Временные выделения JFR/1000 чанков:
  клиент / сервер                       {g(b['weightedAllocationBytesPer1000ChunkDeliveries']['client'])} / {g(b['weightedAllocationBytesPer1000ChunkDeliveries']['server'])} → {g(a['weightedAllocationBytesPer1000ChunkDeliveries']['client'])} / {g(a['weightedAllocationBytesPer1000ChunkDeliveries']['server'])} ГиБ
Совпадение долгих кадров с GC клиента     {b['stallGcOverlapMs']['client']/1000:.2f} → {a['stallGcOverlapMs']['client']/1000:.2f} с
                           GC сервера    {seconds(b['stallGcOverlapMs']['server'])} → {seconds(a['stallGcOverlapMs']['server'])} с

Пики памяти за измеренные запуски, клиент / сервер:
Private Bytes: {g(c0['privateBytesSampledPeak'])} / {g(s0['privateBytesSampledPeak'])} → {g(c1['privateBytesSampledPeak'])} / {g(s1['privateBytesSampledPeak'])} ГиБ.
Peak Working Set: {g(c0['workingSet'])} / {g(s0['workingSet'])} → {g(c1['workingSet'])} / {g(s1['workingSet'])} ГиБ.
Private Bytes на самом маршруте: {g(b['peaks']['client']['privateBytes'])} / {g(b['peaks']['server']['privateBytes'])} → {g(a['peaks']['client']['privateBytes'])} / {g(a['peaks']['server']['privateBytes'])} ГиБ.
Private Bytes — выделенная память (выборка 0,5 с), Working Set — резидентные страницы Windows.
Лимиты кучи прежние. Пик Private Bytes клиента изменился на {percent(c0['privateBytesSampledPeak'],c1['privateBytesSampledPeak'])}; строгого равенства памяти одна пара не доказывает.
{unloadText}
{extra}
Изменено: пропуск одинакового owner/payload, лишних render-уведомлений
и доказанно дублирующей начальной передачи поверх native-пакета чанка.
Холодный запрос читает маски только нужных владельцев и их формы,
пропуская чужие NBT-деревья. Для Lithium 0.11.4 ограничена начальная ёмкость
временной локальной палитры; ID, штатный рост и сериализация сохранены.
Лимиты, постоянные кэши и пулы streaming.5 сохранены; новых очередей нет.
В исходном проходе owner-сообщения в Netty дали оценочно 6,16 ГБ временных выделений; это крупнейшая подтверждённая устранимая избыточность в этой ветке. Общая сборка мешей затратнее и остаётся ограничением.

{validationText}
{checks} целевых проверок: 44 по неизменившемуся байткоду повторно не запускались;
четыре проверки нового чтения масок, {verifiedChecks} успешных проверок открытия в новом JVM, {normalChecks} перед отдельным штатным сохранением.
Дверь, лестница, разрушение, точные коллизии, payload и плотная палитра проверены.
Текстуры, материалы, свет, геометрия, ID и формат карты сохранены.
Исходная карта, выбранные моды и независимый резерв не изменялись.

Ограничения: {tailLimit}
{'' if fullPair else 'Ускорение всего маршрута по неполному старому прогону не заявляется.'}
Серверные коррекции меняли фактический путь ({x['distance']:.1f} → {y['distance']:.1f} блока)
и повторные доставки. Одна измеренная пара на общем ПК с 16 ГБ и подкачкой.
GC-совпадение — временная корреляция; выделения JFR — оценка оборота памяти.
{'Серверный JFR не сохранился при аварийном выходе; недоступные GC и выделения отмечены н/д. Тики и память сохранены.' if b['jfr']['server'] is None or a['jfr']['server'] is None else ''}
Прежние сорванные замеры с безлимитным FPS исключены из этих чисел.

Кандидат для тестовой установки: заменить streaming.6 этим JAR на клиенте и сервере. Полная приёмка по всему маршруту не пройдена.
JAR: game/dreamwalker-bb-materials.1-streaming.7.jar
Исходники и патч: sources/. Настройки 9G/8/6: server/.
Подробности: DETAILS-RU.txt, evidence/, binary-preservation.json.
SHA-256: {candidate['sha256']}
'''
 (release/'REPORT-RU.txt').write_text(report,encoding='utf-8')
 preserved=read(release/'binary-preservation.json')['unchangedEntries']
 details=report+f'''
Подробности исправления и проверки

Начальная передача исключается только для точного native-пакета этого чанка,
с полным совпадением owner/root/typed payload и известной фабрикой BE.
Чужие носители, подклассы и повторно использованный пакет сохраняют полную отправку.
Живые изменения остаются полными; необязательный сетевой OwnerShardInitial
отличает их от первого снимка. Сборщик ограничен 4096 записями и очищается в finally.
Новых данных между отправками не хранит. Боксы не укрупнялись.

Штатное полное NBT-чтение в readMemberships строило cells, payload и маски
всех владельцев. Новый stateless reader делает до трёх проходов через один
открытый файл: идентификаторы/бюджеты, нужные маски, их dictionary shapes.
Ненужные ветви пропускаются штатным NBT scanner. Дополнительная распаковка
синхронна, формат файла прежний. Файловый канал закрывается при успехе и ошибке.
На реальном неизменённом owners-33.-7.nbt, один запрошенный владелец:
точные выделения потока чтения {probe['fullReadBytes']} → {probe['filteredReadBytes']} байт
({percent(probe['fullReadBytes'],probe['filteredReadBytes'])}); {probe['fullReadNs']/1e6:.2f} → {probe['filteredReadNs']/1e6:.2f} мс.
Это отдельная проверка NBT после прогрева обоих путей, не игровой бенчмарк.
Все сохранённые выбранные поля/маски/формы совпали с полным чтением.
Проверены словарный и старый формат, сохранённые последующие изменения,
повреждённая ссылка, восстановление после ошибки и бюджет незапрошенного владельца.
Холодные запросы не загружают и не удерживают корневой чанк и не пишут в карту.

Палитра Lithium: начальная ёмкость до 4096 вместо ёмкости глобального реестра;
индексные биты прежние, проверены 5000 состояний и штатный рост.
Формы, списки и временные пулы streaming.5 повторно не внедрялись.

Пересобранные terrain-корни: {x['roots']} → {y['roots']}; уведомления соседних
секций: {x['updates']} → {y['updates']}. Их суммы тоже зависят от реального пути.
По JFR после исправления остаются штатная сборка мешей чанков и native NBT.
Категории стека пересекаются: их выделения нельзя складывать.
Кешированные native-пакеты и непокрытые носители сохраняют полный owner-sync.
На общем ПК подкачка существенно влияет на остановки; это не замер удалённого хостинга.

{preserved} прочих ZIP-элементов побайтно взяты из принятой streaming.6.
В evidence/membership-reused-checks.json перечислены единственные изменения
после предыдущих 44 успешных проверок: чтение shard и новый stateless helper.
Проверки остальных, побайтно одинаковых механизмов переиспользованы.
{'Сохранение открыто новым выделенным JVM без сброса рабочей карты.' if saveVerified else 'Полное городское сохранение с повторным открытием не подтверждено. См. неуспешные receipts/logs.'}
PNG, реальные доставки FULL-чанков, серверные ACK и CSV кадров/тиков в evidence.
Полные исходные JFR остаются локально в research/graphical-streaming-20261019.
Custom payload измерен после сетевой распаковки, до decodeOwners; это не
количество сжатых TCP-байтов. В обоих финальных прогонах maxFps=120.
Промежуточные варианты без достаточного эффекта и неудачные запуски сохранены там же;
с финальной численной парой они не смешивались. Рабочая копия города одна.
'''
 (release/'DETAILS-RU.txt').write_text(details,encoding='utf-8')
 short=f'''Dreamwalker BB materials.1-streaming.7 — частично проверенный кандидат

Игра с настоящим графическим окном + выделенный сервер. Одинаковые Java 17.0.15,
моды, исходная очищенная карта, 1280×720/120 FPS, клиент 5G / сервер 9G,
view-distance=8, simulation-distance=6. Общий завершённый участок: фасад → башня.

                                        streaming.6 → streaming.7
Кадры >100 мс                           {b['framesOver100Ms']} → {a['framesOver100Ms']} ({percent(b['framesOver100Ms'],a['framesOver100Ms'])})
Кадры >1 с                              {b['framesOver1000Ms']} → {a['framesOver1000Ms']}
Максимальный интервал кадра              {b['maxFrameMs']/1000:.3f} → {a['maxFrameMs']/1000:.3f} с
Сумма интервалов кадров >100 мс           {b['sumFrameIntervalsOver100Ms']/1000:.2f} → {a['sumFrameIntervalsOver100Ms']/1000:.2f} с
Работа тика: P99 / максимум              {b['serverTickWorkP99Ms']:.1f} / {b['serverTickWorkMaxMs']:.1f} → {a['serverTickWorkP99Ms']:.1f} / {a['serverTickWorkMaxMs']:.1f} мс
Длительность участка                    {b['routeSeconds']:.2f} → {a['routeSeconds']:.2f} с
Фактически доставленные чанки            {b['acceptedChunkDeliveries']} → {a['acceptedChunkDeliveries']}
Owner custom payload до decode           {x['ownerBytes']/2**20:.1f} → {y['ownerBytes']/2**20:.1f} МиБ
JFR временные выделения/1000 чанков       {g(b['weightedAllocationBytesPer1000ChunkDeliveries']['client'])} → {g(a['weightedAllocationBytesPer1000ChunkDeliveries']['client'])} ГиБ клиента; сервер н/д
Перекрытие долгих кадров с GC клиента     {b['stallGcOverlapMs']['client']/1000:.2f} → {a['stallGcOverlapMs']['client']/1000:.2f} с

Пики Private Bytes, клиент / сервер: {g(c0['privateBytesSampledPeak'])} / {g(s0['privateBytesSampledPeak'])} → {g(c1['privateBytesSampledPeak'])} / {g(s1['privateBytesSampledPeak'])} ГиБ.
Peak Working Set, клиент / сервер: {g(c0['workingSet'])} / {g(s0['workingSet'])} → {g(c1['workingSet'])} / {g(s1['workingSet'])} ГиБ.
Лимиты и постоянные кэши не увеличены. Private Bytes клиента: {percent(c0['privateBytesSampledPeak'],c1['privateBytesSampledPeak'])}; строгого равенства памяти одна пара не доказывает.
{unloadText}

Патч убирает повторное применение равных owner/payload и доказанное дублирование
начальной передачи поверх native chunk packet. Холодное чтение выбирает только
нужные NBT-маски/формы; на реальном shard точные выделения −87,4%.
Начальная локальная палитра Lithium ограничена 4096 с сохранением штатного роста.
Новых очередей нет; временные ссылки освобождаются. Пулы streaming.5 сохранены.

{validationText}
{checks} успешных целевых проверок (44 переиспользованы по одинаковому байткоду).
Проверены дверь, лестница, разрушение, коллизии, payload и native save/reopen плотной палитры.
Материалы, свет, геометрия, ID, формат карты, исходный комплект и резерв сохранены.

Ограничения: кирпичный район по-прежнему вызывает длинные остановки сервера.
Полная приёмка маршрута не пройдена; ускорение всего города не заявляется.
Одна пара на общем ПК с 16 ГБ и подкачкой; фактический путь {x['distance']:.1f} → {y['distance']:.1f} блока.
Серверный JFR не сохранился при аварии: GC и выделения сервера неизвестны.
Безлимитные FPS и дополнительные функциональные запуски исключены из сравнения.

JAR: game/dreamwalker-bb-materials.1-streaming.7.jar (клиент + сервер, тестовая установка).
Патч и исходники: sources/. Настройки 9G/8/6 и команда профиля хостинга: server/.
Подробности и доказательства: DETAILS-RU.txt, evidence/, binary-preservation.json.
SHA-256: {candidate['sha256']}
'''
 (release/'REPORT-RU.txt').write_text(short,encoding='utf-8')
 write(release/'integrity.json',{'status':('PARTIAL_PERFORMANCE_IMPROVEMENT_SAVE_REOPEN_VERIFIED' if saveVerified else 'PARTIAL_PERFORMANCE_IMPROVEMENT_FULL_CITY_VALIDATION_FAILED') if headless else 'VERIFIED_SOURCE_MAP_AND_ORIGINAL_KIT_UNCHANGED','jarSha256':candidate['sha256'],'sourceMapFiles':len(read(OUT/'source-map.json')),'verificationChecks':checks,'reusedChecks':preflight['checks'],'newColdReaderChecks':4,'verificationMode':validationCase,'saveReopenVerified':saveVerified,'completeNativeRoute':not headless,'normalSaveStatus':normal['status'],'freshReopenStatus':verify['status'],'independentBackup':'Never modified; no test path uses backup/world'})

if __name__=='__main__':main()
