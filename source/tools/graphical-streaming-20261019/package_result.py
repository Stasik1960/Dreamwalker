import json,shutil,zipfile
from common import *

def main():
 release=ROOT/'deliverables/streaming-7-20261020';evidence=release/'evidence';evidence.mkdir(exist_ok=True);settings=release/'server';settings.mkdir(exist_ok=True)
 comparison=json.loads((OUT/'comparison.json').read_text());b=comparison['before'];a=comparison['after']
 validation=json.loads((OUT/'validation-tail/prepared.json').read_text());assert validation['status']=='PASS_NATIVE_GRAPHICAL_DEDICATED_ROUTE'
 validationClient=json.loads((OUT/'validation-tail/client/client-route.json').read_text());validationServer=json.loads((OUT/'validation-tail/server/server-route.json').read_text());unloaded=validationServer['afterUnloadGc']
 verify=json.loads((OUT/'verification/receipt.json').read_text());assert verify['status']=='PASS'
 preflight=json.loads((OUT/'preflight/receipt.json').read_text());assert preflight['status']=='PASS'
 checkCount=verify['checks']+preflight['checks']
 candidate=json.loads((OUT/'candidate.json').read_text());assert sha(Path(candidate['jar']))==candidate['sha256']==verify['productionJarSha256']==preflight['productionJarSha256']==validation['jarSha256']
 assert {name:sha(PROJECT/name) for name in PRODUCTION_INPUTS}==json.loads((OUT/'build-production.json').read_text())['sourceHashes']
 preserved=json.loads((release/'binary-preservation.json').read_text())['unchangedEntries']
 for case,s in [('baseline',b),('after',a)]:
  metadata=json.loads((OUT/case/'frame-profile.json').read_text())['metadata'];phase=s['phaseEvidence'][0]
  for side in ['client','server']:assert metadata[side]['firstEventEpochNs']<=phase['startEpochNs'] and metadata[side]['lastEventEpochNs']>=phase['endEpochNs']
 assert manifest(SOURCE)==json.loads((OUT/'source-map.json').read_text())
 for side in ['client','server']:
  for row in json.loads((OUT/(side+'-mod-input.json')).read_text()):assert sha(Path(row['source']))==row['sha256']
 selected=['comparison.json','source-map.json','client-mod-input.json','server-mod-input.json','build-production.json','build-production.log','build-verify.json','build-validation.json','build-validation.log','validation-input.json','validation-ready.json','palette-link.json','palette-link.log','frozen-measurement-sources.zip']
 for name in selected:shutil.copyfile(OUT/name,evidence/name)
 for case in ['baseline','after','validation-tail']:
  dest=evidence/case;dest.mkdir(exist_ok=True)
  for name in ['summary.json','frame-profile.json','world-input.json','memory.jsonl','validation-only-gc.json','validation-only-gc.log']:
   if (OUT/case/name).exists():shutil.copyfile(OUT/case/name,dest/name)
  for side in ['client','server']:
   for name in [side+'-route.json',side+'-frames.csv' if side=='client' else 'server-ticks.csv','watchdog.txt']:
    if (OUT/case/side/name).exists():shutil.copyfile(OUT/case/side/name,dest/name)
  (dest/'screenshots').mkdir(exist_ok=True)
  for p in (OUT/case/'screenshots').glob('*.png'):shutil.copyfile(p,dest/'screenshots'/p.name)
  prepared=json.loads((OUT/case/'prepared.json').read_text())
  write(dest/'conditions.json',{k:prepared[k] for k in ['status','java','jarSha256','qaSha256','graphics','optionsSha256','serverPropertiesSha256','mods']})
 for name in ['receipt.json','checks.json','saved-inputs.json','dense-palette.nbt']:
  if (OUT/'verification'/name).exists():shutil.copyfile(OUT/'verification'/name,evidence/('verification-'+name))
 for name in ['receipt.json','checks.json','combined-checks.json','dense-palette.nbt']:
  if (OUT/'preflight'/name).exists():shutil.copyfile(OUT/'preflight'/name,evidence/('preflight-'+name))
 (settings/'jvm-args.txt').write_text('-Xms128M\n-Xmx9G\n',encoding='utf-8')
 (settings/'server.properties.fragment').write_text('view-distance=8\nsimulation-distance=6\n',encoding='utf-8')
 (settings/'README-RU.txt').write_text('Для отдельного сервера с общим лимитом 12 ГБ: -Xmx9G оставляет около 3 ГБ вне кучи.\nПрименить jvm-args.txt к существующему запуску Fabric и заменить две строки из server.properties.fragment.\nСтартовый скрипт и настройки на рабочем столе не изменялись.\nВ локальном игровом сравнении: сервер 9G, клиент 5G, дальности 8/6; лимиты одинаковы до/после.\nТестовая обвязка использует localhost и сетевой тайм-аут 300 секунд; она не входит в игровой JAR.\n',encoding='utf-8')
 with zipfile.ZipFile(release/'sources/qa-tools-and-sources.zip','w',zipfile.ZIP_DEFLATED) as archive:
  for folder in [PROJECT/'src/routeqa',PROJECT/'src/verifyqa',TOOLS]:
   for p in files(folder):
    if p.suffix in ['.java','.json','.py','.gradle']:archive.write(p,'bloodborne complete/'+p.relative_to(PROJECT).as_posix())
  for p in [PROJECT/'tools/collision-streaming-20261018/JfrShapeSummary.java',PROJECT/'tools/city-streaming-20261018/world_io.py']:archive.write(p,'bloodborne complete/'+p.relative_to(PROJECT).as_posix())
 g=lambda n:f'{n/2**30:.2f}'
 pair=lambda x,y:f'{x} → {y}'
 pct=lambda x,y:f'{(y/x-1)*100:+.1f}%'
 cli0=b['lifetimePeaks']['client'];cli1=a['lifetimePeaks']['client'];srv0=b['lifetimePeaks']['server'];srv1=a['lifetimePeaks']['server']
 validationPeaks={side:{'workingSet':0,'privateBytes':0} for side in ['client','server']}
 for line in (OUT/'validation-tail/memory.jsonl').read_text().splitlines():
  for row in json.loads(line)['processes']:
   peak=validationPeaks[row['label']];peak['workingSet']=max(peak['workingSet'],row['processLifetimePeakWorkingSet']);peak['privateBytes']=max(peak['privateBytes'],row['privateBytes'])
 write(evidence/'validation-tail/peaks.json',validationPeaks)
 def activity(s):
  p=s['phaseEvidence'][0];start=p['countersAtStart'];end=p['counters'];return {'roots':end['terrain']['sectionBuiltOwnedRoots']-start['terrain']['sectionBuiltOwnedRoots'],'updates':end['terrainUpdates']['overhangRebuilds']-start['terrainUpdates']['overhangRebuilds'],'ownerBytes':end['ownerPayloadBytes']-start['ownerPayloadBytes'],'distance':p['nativeDistance']}
 x=activity(b);y=activity(a)
 text=f'''Dreamwalker BB materials.1 — streaming.7, 20.10.2026

Сопоставимый участок: фасад → башня, настоящее окно Minecraft 1280×720,
выделенный сервер localhost. Java 17.0.15, Fabric Loader 0.19.5,
отобранные JAR из Dreamwalker-RP-Test-20261019; точные SHA в evidence.
Одинаковы: -Xmx9G сервер, -Xmx5G клиент, view-distance=8,
simulation-distance=6, графика, маршрут с шагом до 0,75 блока/клиентский тик,
измерительный мод и исходные файлы карты. Уменьшение дальности не включено в эффект кода.

ДО → ПОСЛЕ (движение от фасада до башни)
Кадры >100 мс: {pair(b['framesOver100Ms'],a['framesOver100Ms'])} ({pct(b['framesOver100Ms'],a['framesOver100Ms'])}).
Кадры >1 с: {pair(b['framesOver1000Ms'],a['framesOver1000Ms'])}.
Максимальный интервал кадра: {b['maxFrameMs']/1000:.3f} → {a['maxFrameMs']/1000:.3f} с.
Сумма интервалов кадров >100 мс: {b['sumFrameIntervalsOver100Ms']/1000:.2f} → {a['sumFrameIntervalsOver100Ms']/1000:.2f} с.
Длительность участка: {b['routeSeconds']:.2f} → {a['routeSeconds']:.2f} с.
Фактически принятые клиентом чанки, включая повторные: {pair(b['acceptedChunkDeliveries'],a['acceptedChunkDeliveries'])}.
Отдельные данные владельцев: {x['ownerBytes']/2**20:.2f} → {y['ownerBytes']/2**20:.2f} МиБ ({pct(x['ownerBytes'],y['ownerBytes'])}).
Фактическое движение: {x['distance']:.2f} → {y['distance']:.2f} блока.
Максимальная работа серверного тика: {b['serverTickWorkMaxMs']/1000:.3f} → {a['serverTickWorkMaxMs']/1000:.3f} с.
Максимальный интервал начала серверных тиков: {b['serverStartIntervalMaxMs']/1000:.3f} → {a['serverStartIntervalMaxMs']/1000:.3f} с.
Совпадение длинных кадров с GC клиента: {b['stallGcOverlapMs']['client']/1000:.2f} → {a['stallGcOverlapMs']['client']/1000:.2f} с;
с GC сервера: {b['stallGcOverlapMs']['server']/1000:.2f} → {a['stallGcOverlapMs']['server']/1000:.2f} с. Это временная корреляция.
Оценка временных выделений JFR на 1000 доставленных чанков:
  клиент {g(b['weightedAllocationBytesPer1000ChunkDeliveries']['client'])} → {g(a['weightedAllocationBytesPer1000ChunkDeliveries']['client'])} ГиБ;
  сервер {g(b['weightedAllocationBytesPer1000ChunkDeliveries']['server'])} → {g(a['weightedAllocationBytesPer1000ChunkDeliveries']['server'])} ГиБ.
Пересобранные корни terrain: {pair(x['roots'],y['roots'])}; флаги соседних секций: {pair(x['updates'],y['updates'])}.

ПАМЯТЬ — пики за весь игровой запуск, ГиБ
Private Bytes (выделенная память процесса, выборка 0,5 с):
  клиент {g(cli0['privateBytesSampledPeak'])} → {g(cli1['privateBytesSampledPeak'])};
  сервер {g(srv0['privateBytesSampledPeak'])} → {g(srv1['privateBytesSampledPeak'])}.
Peak Working Set (пиковые резидентные страницы Windows):
  клиент {g(cli0['workingSet'])} → {g(cli1['workingSet'])};
  сервер {g(srv0['workingSet'])} → {g(srv1['workingSet'])}.
RSS зависит также от подкачки и резидентности. Числа по самому участку тоже сохранены в comparison.json.
Private Bytes на сопоставимом участке, клиент / сервер:
  {g(b['peaks']['client']['privateBytes'])} / {g(b['peaks']['server']['privateBytes'])} → {g(a['peaks']['client']['privateBytes'])} / {g(a['peaks']['server']['privateBytes'])} ГиБ.
Отдельная проверка хвоста: пики Private Bytes клиент / сервер
  {g(validationPeaks['client']['privateBytes'])} / {g(validationPeaks['server']['privateBytes'])} ГиБ;
Peak Working Set {g(validationPeaks['client']['workingSet'])} / {g(validationPeaks['server']['workingSet'])} ГиБ.
После отдельной проверки хвоста, выгрузки и GC нового сервера: {g(unloaded['heapUsed'])} ГиБ кучи;
ожидающие shape-публикации: {unloaded['snapshots']['shapeSnapshotPendingCells']}.
В исходной измеренной паре до/после сопоставимого замера после выгрузки нет.

ИЗМЕНЕНИЯ
1. Клиент отличает одинаковую копию данных владельцев от изменения.
   Сохраняет канонические графы списков, пропускает повторное dirty/копирование
   payload и лишние render-уведомления. Изменённые владельцы, UUID, typed payload
   и данные чужих носителей проходят прежнее обновление. Новых очередей нет.
2. Начальные владельцы не передаются повторно, только если ограниченный
   синхронный сборщик подтвердил полное совпадение с точным native-пакетом
   этого же чанка. Чужие/неполные носители, подклассы и кешированный другой
   пакет сохраняют полную отправку. Один ряд сохраняет прежний флаг выгрузки.
   Живые изменения всегда полные; необязательный сетевой OwnerShardInitial
   отличает их от первого снимка. Сборщик ограничен 4096 записями и очищается
   в finally; данных между отправками не хранит.
3. Для Lithium 0.11.4 начальная ёмкость временной локальной палитры ограничена
   4096 ячейками секции. Сам индекс/ID, штатный рост, содержимое и сериализация
   сохранены. До правки профиль фиксировал около 4,7 ГиБ временных выделений
   этого конструктора за запуск сервера; проверены 5000 записей и штатный рост.
4. Формы, списки и временные пулы streaming.5 сохранены. Размеры постоянных
   кэшей и лимиты памяти не увеличены. Боксы не укрупнялись.

ПРОВЕРКИ
Фасад и башня завершены в измеренном проходе; незавершённый переход
башня → кирпичный район проверен отдельно с тем же игровым JAR:
{validationClient['status']}. Везде реальные FULL-чанки, серверные ACK
положения и завершённый terrain; исходные PNG в evidence/after и validation-tail.
После нормального сохранения карта открыта новым выделенным JVM-процессом.
{checkCount} проверок: пять сохранённых городских точек, owner/payload,
повторная синхронизация и настоящие изменения, канонические ссылки,
дверь открыть/закрыть/разрушить, лестница поставить/повернуть/разрушить,
коллизии и selection, 513 состояний в 4096 ячейках через native NBT save/reopen.
Ранее успешный общий набор проверок повторно не запускался.
Патч исходников проверен обратным git apply --check; собранный JAR проверен в игре.

ОГРАНИЧЕНИЯ
Измеренные маршруты старой и новой версий через третью, кирпичную камеру
не завершились: сработали предел 131072 кадровых выборок и watchdog.
Этот хвост исключён из численного сравнения. Успешная отдельная проверка
хвоста использует ту же ёмкость буфера с записью заполненных порций в CSV.
Её результаты не смешивались с измеренной парой. Ускорение всего маршрута
по неполным прогонам не заявляется. Полные исходные JFR сохранены локально
в research/graphical-streaming-20261019; в evidence включены агрегаты.
Только перед отдельной проверкой хвоста выполнен GC простаивающего сервера
до входа клиента, чтобы снизить подкачку на общем ПК. В обоих численных
прогонах такого подготовительного GC не было; их исходные данные сохранены.
Оставшиеся затраты: штатная сборка мешей чанков, native NBT и полные owner-
пакеты для непокрытых носителей или повторно использованного vanilla-пакета.
JFR после исправления оценивает оборот VanillaChunkBuilder примерно в
{g(a['jfr']['client']['targets']['VanillaChunkBuilder.*']['weightedAllocationBytes'])} ГиБ на выбранном участке;
категории профиля пересекаются, их нельзя складывать. Новых кешей для этого
не добавлено; сильная подкачка на общем ПК остаётся ограничением.
Серверные коррекции движения при задержках изменили фактическую длину пути
и повторные доставки; поэтому приведены их количества и выделения на 1000 чанков.
Один игровой проход каждой версии на общем ПК с 16 ГБ RAM, подкачка влияла
на результаты. Это не статистическая оценка и не измерение удалённого хостинга.
Настройки 9G/8/6 одинаковы в сравнении. Подготовительные неудачные запуски с
другими бюджетами памяти и ошибки обвязки не смешивались с этими числами.
Промежуточный вариант только с пропуском повторного применения owner/payload
не уменьшил самые длинные остановки. Его профиль выявил оставшуюся распаковку
дублей на Netty; итоговый патч дополнительно сокращает саму повторную передачу.

СОХРАННОСТЬ
{preserved} остальных ZIP-элемента JAR побайтно взяты из принятой streaming.6.
Текстуры, материалы, свет, геометрические данные, ID и формат карты сохранены.
Исходные выбранные моды и очищенная карта проверены по SHA; независимый
backup/world и комплект на рабочем столе не изменялись. Рабочий WORLD один.
Готовый JAR: game/dreamwalker-bb-materials.1-streaming.7.jar
Установка: заменить JAR streaming.6 этим JAR и на клиенте, и на сервере.
SHA-256: {candidate['sha256']}
Исходники: sources/changed-sources.zip и changes-from-streaming.6.patch.
Измерительный код: sources/qa-tools-and-sources.zip; он не включён в игровой JAR.
'''
 (release/'DETAILS-RU.txt').write_text(text,encoding='utf-8')
 short=f'''Dreamwalker BB materials.1-streaming.7 — 20.10.2026

Настоящие графический клиент и выделенный сервер, исходная очищенная карта,
отобранная сборка Dreamwalker-RP-Test-20261019. Одинаковые Java 17.0.15,
Fabric Loader 0.19.5, -Xmx9G сервер / -Xmx5G клиент, дальности 8/6, графика и обвязка.

Завершённый сопоставимый участок: фасад → башня.
Показатель                         streaming.6 → streaming.7
Кадры >100 мс                      {b['framesOver100Ms']} → {a['framesOver100Ms']} ({pct(b['framesOver100Ms'],a['framesOver100Ms'])})
Кадры >1 с                         {b['framesOver1000Ms']} → {a['framesOver1000Ms']}
Максимальный интервал кадра         {b['maxFrameMs']/1000:.3f} → {a['maxFrameMs']/1000:.3f} с
Сумма интервалов кадров >100 мс      {b['sumFrameIntervalsOver100Ms']/1000:.2f} → {a['sumFrameIntervalsOver100Ms']/1000:.2f} с
Максимальная работа тика            {b['serverTickWorkMaxMs']/1000:.3f} → {a['serverTickWorkMaxMs']/1000:.3f} с
Фактически доставленные чанки       {b['acceptedChunkDeliveries']} → {a['acceptedChunkDeliveries']}
Отдельные данные владельцев         {x['ownerBytes']/2**20:.2f} → {y['ownerBytes']/2**20:.2f} МиБ
Выделения JFR/1000 чанков, клиент    {g(b['weightedAllocationBytesPer1000ChunkDeliveries']['client'])} → {g(a['weightedAllocationBytesPer1000ChunkDeliveries']['client'])} ГиБ
Выделения JFR/1000 чанков, сервер    {g(b['weightedAllocationBytesPer1000ChunkDeliveries']['server'])} → {g(a['weightedAllocationBytesPer1000ChunkDeliveries']['server'])} ГиБ

Пики памяти за весь запуск, клиент / сервер:
Private Bytes: {g(cli0['privateBytesSampledPeak'])} / {g(srv0['privateBytesSampledPeak'])} → {g(cli1['privateBytesSampledPeak'])} / {g(srv1['privateBytesSampledPeak'])} ГиБ.
Peak Working Set: {g(cli0['workingSet'])} / {g(srv0['workingSet'])} → {g(cli1['workingSet'])} / {g(srv1['workingSet'])} ГиБ.
Private Bytes на сравниваемом участке: {g(b['peaks']['client']['privateBytes'])} / {g(b['peaks']['server']['privateBytes'])} → {g(a['peaks']['client']['privateBytes'])} / {g(a['peaks']['server']['privateBytes'])} ГиБ.
Отдельная проверка хвоста: Private Bytes {g(validationPeaks['client']['privateBytes'])} / {g(validationPeaks['server']['privateBytes'])}, Peak Working Set {g(validationPeaks['client']['workingSet'])} / {g(validationPeaks['server']['workingSet'])} ГиБ.
После отдельной проверки хвоста, выгрузки и GC нового сервера: {g(unloaded['heapUsed'])} ГиБ кучи.
Лимиты и постоянные кэши не увеличены; новых очередей/кэшей чанков нет.

Патч устраняет повторную начальную передачу владельцев при доказанном полном
совпадении с native-пакетом, повторное применение одинаковых owner/payload
и лишние render-уведомления. Живые изменения передаются полностью.
В Lithium 0.11.4 временная локальная палитра начинает с
4096 элементов вместо ёмкости всего реестра, сохраняя ID и штатный рост.
Пулы/формы streaming.5, свет, материалы, текстуры и формат карты сохранены.

Проверены фасад и башня; переход в кирпичный район проверен отдельно после
исправления предела выборок обвязки. Также {checkCount} целевых проверок, реальные операции
с дверью и лестницей, разрушение, коллизии, 513 состояний/4096 ячеек через
native NBT save/reopen и пять городских точек в свежем выделенном JVM-процессе.
Исходный комплект, очищенная карта и независимый резерв не изменены.

Ограничения: третий участок обоих измеренных прогонов оборван лимитом выборок
обвязки и watchdog; он исключён из сравнения. Отдельная проверка хвоста после
исправления обвязки не смешивалась с замером. Не заявлено ускорение всего
маршрута по неполным прогонам. Коррекции сервера меняли фактический путь
({x['distance']:.1f} → {y['distance']:.1f} блока) и повторные доставки. Один прогон каждой версии,
общий ПК с 16 ГБ и подкачкой; RSS зависит от резидентности страниц Windows,
а JFR-выделения являются оценкой оборота, не размером живой памяти.
Только отдельная проверка хвоста использовала GC сервера перед входом клиента.

JAR: game/dreamwalker-bb-materials.1-streaming.7.jar
Заменить им streaming.6 на клиенте и сервере; не оставлять оба JAR в mods.
Исходники/патч: sources/. Настройки 9G/8/6: server/.
Подробности и доказательства: DETAILS-RU.txt, evidence/, binary-preservation.json.
SHA-256: {candidate['sha256']}
'''
 (release/'REPORT-RU.txt').write_text(short,encoding='utf-8')
 write(release/'integrity.json',{'status':'VERIFIED_SOURCE_MAP_AND_ORIGINAL_KIT_UNCHANGED','jarSha256':candidate['sha256'],'sourceMapFiles':len(json.loads((OUT/'source-map.json').read_text())),'independentBackup':'Never modified; no test path references backup/world','verificationChecks':checkCount})

if __name__=='__main__':main()
