"""Package the actually tested local artifacts, evidence and current sources."""
import argparse,hashlib,json,shutil,subprocess,zipfile
from pathlib import Path

P=Path(__file__).absolute().parents[2];REPO=P.parent;O=P/'build/streaming-validation';D=P/'build/dh-city-pilot'
GIT=shutil.which('git') or r'C:\Users\vakir\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\git\cmd\git.exe'
BASE='c36bc8b8708d347a5193e3d4a12cf721e0a27b75'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def git(*args):return subprocess.check_output([GIT,*args],cwd=REPO)
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--case',default='after4');ap.add_argument('--out',type=Path,default=REPO/'deliverables/streaming-dh-20261018');a=ap.parse_args()
 comparison=read(O/('comparison-'+a.case+'.json'));test=read(O/'streaming-native-decode/receipt.json');dh=read(D/'native-launch.json');review=read(D/'pixel-review.json')
 assert comparison['status']=='PASS_MATCHED_NATIVE_ROUTE' and test['status']=='PASS' and len(test['tests'])==12
 assert dh['status']=='PASS_NATIVE_DH_RENDER_REQUIRES_PIXEL_REVIEW' and review['status']=='API_RENDER_PASS_VISUAL_REJECTED'
 version=dict(line.split('=',1) for line in (P/'gradle.properties').read_text().splitlines() if '=' in line)['mod_version']
 audit=read(O/('unchanged-assets-'+version+'.json'));assert audit['status']=='PASS'
 mainjar=P/'build/libs'/('dreamwalker-bb-fabric-1.20.1-'+version+'.jar');assert sha(mainjar)==comparison['runs'][a.case]['jarSha256']==audit['candidateSha256']
 pilotjar=P/'build/libs'/('dreamwalker-dh-city-pilot-'+version+'.jar');scene=D/'tower-scene-portable';scene_meta=read(scene/'scene.json');prepared=read(D/'native-prepared.json');run=Path(prepared['run_directory']);cap=read(run/'client-capture-output.json')
 assert sha(pilotjar)==next(row['sha256'] for row in prepared['mods'] if row['id']=='dreamwalker_dh_city_pilot')
 commit=git('rev-parse','HEAD').decode().strip();out=a.out.absolute();out.mkdir(parents=True,exist_ok=False)
 def copy(source,target):
  target=out/target;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target);return target
 copy(mainjar,'game/dreamwalker-bb-streaming.4.jar');copy(pilotjar,'optional-dh/dreamwalker-dh-city-pilot-streaming.4.jar')
 for name in ['scene.json','city-boxes.bin.gz','colors.json','report.json']:
  if (scene/name).exists():copy(scene/name,'optional-dh/scene/'+name)
 copy(P/'tools/city-streaming-20261018/bind_dh_scene.py','optional-dh/bind_dh_scene.py')
 copy(D/'dependency-receipt.json','optional-dh/dependency-receipt.json')
 (out/'optional-dh/README-RU.txt').write_text('Это диагностический пилот одного района, а не готовый LOD города.\nAPI и отрисовка без дальнего FULL чанка работают, но визуальная проверка НЕ ПРОЙДЕНА: selection boxes искажают силуэт и создают висящие детали.\nДля обычной игры используйте только основной JAR из game. Этот адаптер оставлен для воспроизведения исследования на отдельной копии карты.\nПодробности и дальнейшее решение — в REPORT-RU.txt.\n',encoding='utf8')
 (out/'optional-dh/DEPENDENCY.txt').write_text('Distant Horizons is a separate, unmodified third-party mod, LGPL-3.\nTested exact release: 3.3.3, Minecraft 1.20.1, version 6UnEfsRQ.\nOfficial download: https://modrinth.com/mod/distanthorizons/version/6UnEfsRQ\nSHA256: a537a71171d935540b56b7598d0de2c106f6f5026336c713d89a1d9ea0ea14e3\nSource: https://gitlab.com/distant-horizons-team/distant-horizons/-/tree/a54dd3eb1df0078e63a71266d042c10d9cda4493\nCore API source: https://gitlab.com/jeseibel/distant-horizons-core/-/tree/b02c66d778beea11a931291815d652cd8abaa7ad\nThe third-party binary is not redistributed in this package.\n',encoding='utf8')
 sources={
  O/('comparison-'+a.case+'.json'):'native-comparison.json',O/'streaming-native-decode/receipt.json':'production-validation.json',O/'streaming-native-decode/gametest.xml':'gametest.xml',
  O/('unchanged-assets-'+version+'.json'):'unchanged-assets.json',O/'scene-binding-check.json':'scene-binding-check.json',
  D/'native-launch.json':'dh-native-launch.json',D/'pixel-review.json':'dh-pixel-review.json',D/'build-pilot-ready-receipt.json':'dh-build.json',
  run/'client-capture-output.json':'dh-capture.json',run/'dreamwalker-dh-pilot-settings-result.json':'dh-qa-settings.json',run/'saves/prototype-fixture/dreamwalker-dh-pilot/runtime-result.json':'dh-runtime.json',
 }
 for source,name in sources.items():copy(source,'evidence/'+name)
 for frame in cap['frames']:copy(run/'screenshots'/frame['fileName'],'evidence/'+frame['fileName'])
 archive=O/(a.case+'-evidence')
 for name in [a.case+'-facade.png',a.case+'-tower.png',a.case+'-brick.png']:copy(archive/name,'evidence/'+name)
 sourcezip=out/'sources/dreamwalker-bb-streaming.4-source.zip';sourcezip.parent.mkdir()
 prefix=P.relative_to(REPO).as_posix()+'/'
 names=[name.decode('utf8') for name in git('ls-files','-z','--',P.name).split(b'\0') if name]
 accepted=[]
 for name in names:
  relative=name[len(prefix):]
  if relative.startswith(('src/','tools/','gradle/')) or relative in ['build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat'] or relative.upper().startswith(('README','LICENSE')):accepted.append(name)
 with zipfile.ZipFile(sourcezip,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
  for name in sorted(accepted):
   path=REPO/name;assert path.is_file() and not path.is_symlink() and not path.stat().st_file_attributes&1024
   archive.write(path,name)
 (out/'sources/changes.patch').write_bytes(git('diff','--binary',BASE,'HEAD','--',P.name))
 before=comparison['runs']['before1'];after=comparison['runs'][a.case];b=before['travelServerWorldTicks'];n=after['travelServerWorldTicks'];bt=before['towerPresentation'];nt=after['towerPresentation'];bm=before['peakMemoryTravel'];nm=after['peakMemoryTravel'];gib=lambda value:value/2**30
 phases=after['phases'];dhframes={f['key']:f for f in cap['frames']}
 report=f'''Dreamwalker BB: подгрузка города и пилот Distant Horizons
Дата: 18.10.2026. Версия: {version}
Исходники: ветка codex/city-streaming-dh-pilot, commit {commit}.

Готовый обычный мод: game/dreamwalker-bb-streaming.4.jar
SHA256: {sha(mainjar)}
Заменить прежний Dreamwalker JAR этим файлом, оставив один экземпляр мода.
Используется прежняя уже конвертированная карта. Повторная конвертация не нужна.
Пользовательская карта и профиль Limacina в этой работе не изменялись; запуски
проведены в отдельно подготовленных копиях. Исправление освещения отложено.

Причины и внесённые изменения

При загрузке повторно проверялись одни и те же владельцы составной геометрии:
каждая здоровая ячейка запускала дорогую подготовку полной транзакции. Это
создавало каскад сериализации NBT, временных объектов и копирования снимков.
Теперь загрузка обходит сохранённые ячейки один раз, проверяет настоящие
реестр/UUID/актуальную геометрию корня и запускает прежнюю атомарную транзакцию
только при доказанной устаревшей записи. Отсутствующий корневой чанк не грузится
принудительно: ячейка ждёт его обычного прибытия. Выгрузка отменяет зависимую
работу. Фоновый обход ограничен мягкими 2 мс и 256 шагами за вызов; отдельная
атомарная миграция может превысить 2 мс. Действия игрока остаются синхронными.

Публикации неизменяемых снимков объединены на границах загрузки серверного
чанка и клиентского пакета; одинаковые присоединения не копируют карты.
Декодирование одного чанка/пакета временно разделяет равные неизменяемые NBT
значения; пул полностью освобождается после пакета. Изменяемый payload каждого
блока остаётся независимым. Форматы сохранений и сетевых пакетов не менялись.

Остаточный профиль также выявил пересчёт лестниц без лимита и полный обход
индекса миграций при каждой выгрузке. Лестницы получили очередь по чанкам,
мягкий лимит 2 мс / 512 шагов и повторную проверку границы после прибытия соседа.
Проверки используют фактический завершённый FULL чанк без ожидания его future.
Завершённые миграции сразу удаляются через обратный индекс владельца.

Нативная проверка обычной версии, без DH

Один старый и один окончательный прогон, Java 17, Xmx10G, 1280x720,
Fabric Loader 0.19.5, Fabric API 0.92.9+1.20.1, GeckoLib 4.4.9,
обычная дальность 12 / симуляция 12, AO выключено, gamma 0.5, без шейдеров,
Sodium и ресурс-пака. Все {comparison['matchedWorldFiles']} входных файлов мира
и параметры совпадают. Маршрут: фасад 272/63/-903, далее обычное движение
спектатора по 1 блоку за шаг к 650/110/-150 и 980/100/95 с подтверждением сервера.
Запуск JVM, загрузка ресурсов и финальное сохранение исключены из таймингов.
Две фазы движения и ожидания: {before['travelSeconds']:.2f} -> {after['travelSeconds']:.2f} с
({(1-after['travelSeconds']/before['travelSeconds'])*100:.1f}% быстрее).

                           До              После
Серверный тик p95:          {b['p95Ms']:.2f} мс       {n['p95Ms']:.2f} мс
Серверный тик p99:          {b['p99Ms']:.2f} мс      {n['p99Ms']:.2f} мс
Серверный тик максимум:     {b['maxMs']:.2f} мс     {n['maxMs']:.2f} мс
Серверные тики >1 с:        {b['over1000ms']}              {n['over1000ms']}
Клиент, башня, максимум:    {bt['maxMs']:.2f} мс      {nt['maxMs']:.2f} мс
Клиент, башня, p99:         {bt['p99Ms']:.2f} мс         {nt['p99Ms']:.2f} мс
Клиент, башня, кадры >1 с:  {bt['over1000ms']}              {nt['over1000ms']}
Первичная загрузка фасада, максимальный кадр: {before['phases'][0]['presentation']['maxMs']:.2f} -> {phases[0]['presentation']['maxMs']:.2f} мс.

Длинные остановки существенно сокращены, однако плавная подгрузка ещё не
достигнута: клиентский p99 стал хуже, и остаются секундные кадры. Быстрее
поступающие чанки требуют реальной сборки мешей и обработки пакетов; остаточная
работа и GC требуют отдельной дальнейшей оптимизации. Нельзя объявлять общий
FPS лучше по среднему: старый клиент рисовал много быстрых кадров во время
ожидания сервера. К моменту кадров tower/brick старый клиент получил только
{before['frames'][1]['loadedChunks']}/{before['frames'][2]['loadedChunks']} чанков, новый —
{after['frames'][1]['loadedChunks']}/{after['frames'][2]['loadedChunks']} при одинаковой настройке 12.
Поэтому картинки не представляют одинаковый объём уже доставленной геометрии;
сравнение p99 кадров не доказывает ускорение чистого рендеринга одной сцены.
В старой фазе brick достигнут лимит хранения 131072 кадров,
поэтому процентили кадров всего старого маршрута неполны. Выше приведена только
полная фаза tower. Серверные выборки полны и измеряют ServerWorld.tick вместе
с END_WORLD_TICK обслуживанием, а не весь цикл интегрированного сервера.
Кеш файлов ОС не сбрасывался; это одиночные прогоны, не статистическая серия.

Память: пиковые значения процесса во время двух фаз движения
Рабочий набор Windows: {gib(bm['workingSetBytes']):.2f} -> {gib(nm['workingSetBytes']):.2f} ГиБ
Private commit:        {gib(bm['privateCommitBytes']):.2f} -> {gib(nm['privateCommitBytes']):.2f} ГиБ
Dedicated GPU учёт:    {gib(bm['dedicatedGpuBytes']):.2f} -> {gib(nm['dedicatedGpuBytes']):.2f} ГиБ
Рабочий набор вырос на {(nm['workingSetBytes']-bm['workingSetBytes'])/2**20:.0f} МиБ.
Это не уменьшение памяти. Private commit не равен физической RAM; GPU счётчик
Windows не означает столько же эксклюзивно занятой VRAM. Xmx10G — лимит Java
heap, а не лимит всего процесса. В новом прогоне действительно доставлено больше
клиентских чанков (609 вместо 332/113 на конечных кадрах), поэтому сравнены
разные объёмы клиентской геометрии. Это согласуется с прибавкой RAM/GPU, но
один прогон не разделяет все причины роста памяти. Пул декодирования не
сохраняет историю посещённых чанков. Низкую загрузку памяти пока не заявляю.

Пилот Distant Horizons

Результаты нативного запуска и просмотра: evidence/dh-pixel-review.json.
ВИЗУАЛЬНАЯ ПРОВЕРКА НЕ ПРОЙДЕНА. Технический API-пилот работает, но силуэт
шпиля и опор заметно искажён, видны отдельные висящие детали. Для обычной игры
его устанавливать пока не рекомендую. Основной JAR не включает этот адаптер.
Проверен точный DH 3.3.3 для Minecraft 1.20.1. Адаптер находится отдельно в
optional-dh, обычный мод не имеет зависимости DH. Пилот — замороженный район
X=560..688, Z=-176..-48, 128x128 блоков: {scene_meta['totalBoxes']} цветных
параллелепипедов, {len(scene_meta['tiles'])} тайлов, { (scene/'city-boxes.bin.gz').stat().st_size} байт сжатых данных.
Геометрия экспортируется вне Minecraft из Anvil и сохранённых owner-shard NBT,
без создания FULL чанков. Используются selection boxes на сетке 0.5 блока;
сохраняются пустоты между занятыми ячейками, объём под крышей не заливается.
Цвета взяты из исходных текстур с учётом UV/площади граней и округлены с шагом 8.
Экспорт проверяет точное совпадение цветных voxel до/после объединения боксов.
Отсутствующих моделей/текстур в окончательном экспорте нет.

Три одинаковые камеры 760/140/-112, yaw=90, pitch=10:
reference12 — обычная дальность 12, proxy выключен;
native6 — обычная дальность 6, proxy выключен;
dh6 — дальность 6, proxy включён. В обоих кадрах с дальностью 6 реальный
клиентский чанк 37/-7 подтверждён отсутствующим. Проверены настоящие вызовы
DH renderer и активные дальние тайлы. Сам адаптер не запрашивает чанки Minecraft.
Фактически в reference12 получено {dhframes['reference12']['loadedChunks']} клиентских чанков,
в native6/dh6 — {dhframes['native6']['loadedChunks']}/{dhframes['dh6']['loadedChunks']}.
Камера преимущественно смотрит на небо; записанные в сырых данных
{dhframes['reference12']['frameTimes']['fpsAverage']:.0f}/{dhframes['dh6']['frameTimes']['fpsAverage']:.0f} FPS
не характеризуют производительность в застроенной части города и не являются
подтверждением её ускорения. Это только техническая проверка появления proxy.
Для этого изолированного QA запуска генерация DH отключена API override,
сохранённая настройка профиля не переписана. К пользовательскому профилю это
не применялось. Это сравнение неподвижной сцены; маршрут с DH не измерялся,
поэтому дополнительное ускорение подгрузки всего города не доказано.

Ограничения: это не полноценный текстурированный LOD города. Selection boxes
отличаются от видимой модели; тонкие детали, стекло, крыши и цвет/освещение
упрощаются. Один район не закрывает весь горизонт. Изменения города требуют
повторного экспорта. Поддержан только singleplayer Overworld; пользовательский
мультиплеер, другие измерения и пересечения сложных границ LOD не проверены.
Уменьшать обычную дальность для всего города только на основании этого пилота
рано. Этот прототип годится только для проверки API. Следующий необходимый
шаг для пригодного LOD — экспорт видимой геометрии моделей с правильной позой,
UV и прозрачностью вместо selection/collision boxes, затем проверка силуэтов
и переходов на полностью занятой городом камере. Масштабировать нынешние
искажения на весь город нельзя. Это отдельная дальнейшая работа.

Воспроизведение диагностического пилота на отдельной копии прежней карты:
1. Обычный исправленный мод из game, Fabric API и GeckoLib как прежде.
2. Отдельно установить точный DH из optional-dh/DEPENDENCY.txt и адаптер JAR.
3. Закрыть эту карту. В optional-dh выполнить Windows Python 3.10+:
   python bind_dh_scene.py --scene scene --world "ПОЛНЫЙ_ПУТЬ_К_КОПИИ_КАРТЫ" --main-jar "ПОЛНЫЙ_ПУТЬ_К_game/dreamwalker-bb-streaming.4.jar" --check
   Проверить путь, затем повторить без --check. Скрипт добавляет только папку
   dreamwalker-dh-pilot, проверяет hash данных/каталога, отказывается от открытой
   карты, существующей привязки и reparse points. Блоки/region/player не меняет.
4. Проверить район с камеры выше. Для полного удаления пилота закрыть мир,
   убрать отдельный адаптер и его dreamwalker-dh-pilot из выбранной копии.
Не устанавливать QA gallery JAR и не копировать dreamwalker-dh-pilot-qa.json.

Проверки сохранности

12/12 реальных GameTests, проверки транзакций/rollback и декодирования прошли.
Проверены здоровая многоклеточная геометрия, чужой carrier, замена UUID,
изменение footprint при том же UUID, отсутствующие корни, отмена на выгрузке,
вложенные пулы NBT с независимыми payload и лестницы на границе чанков.
{audit['assetAndCatalogFiles']} файлов ресурсов/каталогов, {audit['pngFiles']} PNG,
{audit['modelJsonFiles']} model JSON байтово совпадают с прежним JAR.
Классы нативной коррекции света и CompositeOwnerNbt также совпадают.
Чёрные фасады и исходный контраст текстур в этой версии не исправлялись.

Источники/воспроизведение: sources/*, tools/city-streaming-20261018/README.txt
в архиве исходников. Полные исходные замеры остаются в build/streaming-validation
рабочего проекта; выбранные результаты и скриншоты находятся в evidence.
'''
 (out/'REPORT-RU.txt').write_text(report,encoding='utf8')
 manifest={'status':'PACKAGED_CORE_FIX_DH_VISUAL_REJECTED','version':version,'commit':commit,'base':BASE,'lightingDeferred':True,'sameConvertedWorld':True,'remainingClientStallsReported':True,'dhPilotProductionReady':False,'mainJarSha256':sha(mainjar),'pilotJarSha256':sha(pilotjar),'files':[]}
 for path in sorted(out.rglob('*')):
  if path.is_file():manifest['files'].append({'path':path.relative_to(out).as_posix(),'bytes':path.stat().st_size,'sha256':sha(path)})
 (out/'MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf8')
 print(json.dumps({'status':manifest['status'],'out':str(out),'files':len(manifest['files'])}))
if __name__=='__main__':main()
