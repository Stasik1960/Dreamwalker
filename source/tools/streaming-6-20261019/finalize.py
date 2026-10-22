import difflib,hashlib,json,shutil,sys,traceback,xml.etree.ElementTree as ET,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
from dedicated import PROJECT,ROOT,OUT,SOURCE,manifest,sha,write
DELIVERY=ROOT/'deliverables/streaming-6-20261019';TOOLS=Path(__file__).parent

def main():
    comparison=json.loads((OUT/'comparison.json').read_text());b=comparison['before'];a=comparison['after']
    assert json.loads((OUT/'reopen/reopen.json').read_text())['status']=='PASS_NEW_PROCESS_SAVED_MAP_REOPEN'
    tests=ET.parse(PROJECT/'build/test-results/gametest/TEST-streaming6-membership.xml').getroot()
    assert len(tests.findall('.//testcase'))==14 and not tests.findall('.//failure') and not tests.findall('.//error')
    assert manifest(SOURCE)==json.loads((OUT/'source-map.json').read_text()),'Delivered source map changed'
    prep=json.loads((OUT/'baseline/prepared.json').read_text())
    assert sha(Path(r'C:\Users\vakir\Desktop\start.sh'))==prep['desktopStartSha256']
    assert sha(Path(r'C:\Users\vakir\Desktop\server.properties'))==prep['desktopPropertiesSha256']
    preservation={'status':'PASS_SOURCE_MAP_AND_DESKTOP_FILES_UNCHANGED','sourceMapFiles':len(json.loads((OUT/'source-map.json').read_text())),
       'sourceMapManifestSha256':sha(OUT/'source-map.json'),'workingMap':prep['workingWorld'],
       'independentBackup':'Never used as a working copy; no task writes or copies target backup/world',
       'serverStartSha256':prep['desktopStartSha256'],'serverPropertiesSha256':prep['desktopPropertiesSha256']}
    write(DELIVERY/'evidence/preservation.json',preservation)
    # A zero-context patch includes only the changed Java line, never launcher tokens.
    start=Path(r'C:\Users\vakir\Desktop\start.sh').read_text();next_start=start.replace('-Xmx12000M','-Xmx9G',1)
    patch=''.join(difflib.unified_diff(start.splitlines(keepends=True),next_start.splitlines(keepends=True),fromfile='a/start.sh',tofile='b/start.sh',n=0))
    assert 'SERVERWRAPPER' not in patch and '-Xmx9G' in patch
    (DELIVERY/'server/start.sh.memory.patch').write_text(patch,encoding='utf-8',newline='\n')
    readme=DELIVERY/'server/README-RU.txt'
    text=readme.read_text(encoding='utf-8').replace('Запас около 3 ГиБ предназначен для native memory, метаданных, потоков, сжатия, ОС/служб контейнера; Xmx не ограничивает весь процесс.',
        'Запас вне Java-кучи составляет примерно 2,3–3 ГБ в зависимости от единиц реального лимита (GB/GiB). Он нужен native memory, метаданным, потокам, сжатию и службам контейнера; Xmx не ограничивает весь процесс.')
    readme.write_text(text,encoding='utf-8')
    for case in ['baseline','after']:
        dest=DELIVERY/'evidence'/case;dest.mkdir(exist_ok=True)
        for name in ['prepared.json','reset.json','route.json','ticks.csv','memory.jsonl','server.jfr','server-console.log','target-analysis.json','analysis-receipt.json','intervals.tsv','phase-provenance.json']:
            shutil.copyfile(OUT/case/name,dest/name)
    dest=DELIVERY/'evidence/reopen';dest.mkdir(exist_ok=True)
    for name in ['reopen.json','receipt.json','saved-inputs.json','expected.nbt','console.log']:shutil.copyfile(OUT/'reopen'/name,dest/name)
    shutil.copyfile(OUT/'comparison.json',DELIVERY/'evidence/comparison.json')
    shutil.copyfile(PROJECT/'build/test-results/gametest/TEST-streaming6-membership.xml',DELIVERY/'evidence/gametest.xml')
    shutil.copyfile(TOOLS/'build-tests-python.log',DELIVERY/'evidence/gametest.log')
    shutil.copyfile(TOOLS/'build-production-python.log',DELIVERY/'evidence/build-check.log')
    ba=json.loads((OUT/'baseline/target-analysis.json').read_text())['phaseThreads']['TRAVEL_TOTAL|Server thread']
    aa=json.loads((OUT/'after/target-analysis.json').read_text())['phaseThreads']['TRAVEL_TOTAL|Server thread']
    def target(s):return s['targets']['ImportedCollisionMigration.*']['weightedAllocationBytes']/2**30
    percent=(a['routeSeconds']/b['routeSeconds']-1)*100
    rss=(a['peakRouteWorkingSet']/b['peakRouteWorkingSet']-1)*100
    def g(n):return n/2**30
    report=f'''Dreamwalker BB materials.1 → streaming.6. 19.10.2026

JAR: game/dreamwalker-bb-materials.1-streaming.6.jar
SHA256: {sha(DELIVERY/'game/dreamwalker-bb-materials.1-streaming.6.jar')}
База: e1b8335850d4c9cb3db83cfd9bca5ebd8569fe0d / готовая materials.1.

Условия: один локальный выделенный сервер Windows, Java 21.0.10+10-LTS,
Fabric Loader 0.19.5, Fabric API 0.92.9, GeckoLib 4.4.9, одинаковая QA-надстройка.
Обе версии: -Xms128M -Xmx9G, view-distance=8, simulation-distance=6.
Для общего лимита 12 ГБ оставлен запас вне heap. Реальные байты лимита хостинга
ещё не известны. Исходные start.sh и server.properties на рабочем столе не менялись.
Исходные 12/10 и Xmx12000M в сравнении не использовались; эффект дальности не
выдаётся за ускорение кода. Полной удалённой сборки/профиля хостинга нет.

Одна существующая рабочая копия карты: build/runtime-client-materials/saves/
prototype-fixture. Перед каждым из двух проходов в ней восстановлены одинаковые
активные файлы очищенной map/bloodborne; SHA256-манифесты совпали. Независимый
backup/world не использовался и не изменялся. C: / Guard, переноса на E: не было.

Маршрут: фасад 272.5/100/-902.5, обычные сетевые пакеты движения по 1,5 блока
за шаг к башне 650.5/110/-149.5 и району 980.5/100/95.5. На каждом конце нужны
169 точно адресованных FULL-чанков и 100 ticks ожидания. Считались реально
полученные TCP-пакеты ChunkData, а не только отправки или размер обзора.
Старт JVM, первый телепорт, финальное сохранение исключены из времени маршрута.

Подтверждённая лишняя работа: compatibility-проверка владельцев читала целый
холодный root-shard ради его saved membership. В базовом Server thread:
404 из 4340 execution samples проходили через этот повторный полный read из
savedMemberships; все ShardedCompositeLedger.read дали 500 samples и оценку
13,60 ГБ временных выделений. Категории вложены, их нельзя суммировать.
Импортированные формы почти не представлены; укрупнять боксы оснований нет.

Исправление: холодный запрос теперь декодирует только запрошенные полные маски
и нужные shapeRef. Он не восстанавливает посторонние клетки и typed payload.
Распаковка и структурный разбор самого NBT остаются синхронными. Полная проверка
данных остаётся при фактической загрузке root-shard. Неизменившийся pending-mask
повторно не заменяет before и не пересоздаёт обратные индексы. Все таблицы нового
чтения локальны одному вызову; постоянных очередей/кэшей нет. Пулы и общие формы
streaming.5 сохранены. Боксы, занятый объём, schema=1 и writer не менялись.

Одно сопоставимое наблюдение                 До              После
Маршрут, с                               {b['routeSeconds']:8.3f}         {a['routeSeconds']:8.3f}
Ticks в окне                             {b['tickCount']:8d}         {a['tickCount']:8d}
p95 / p99 работы tick, мс                 {b['tickWorkMs']['p95']:.2f}/{b['tickWorkMs']['p99']:.2f}     {a['tickWorkMs']['p95']:.2f}/{a['tickWorkMs']['p99']:.2f}
Максимум работы tick, мс                  {b['tickWorkMs']['max']:8.2f}         {a['tickWorkMs']['max']:8.2f}
Ticks >100 мс / >1 с                     {b['tickWorkMs']['over100']}/{b['tickWorkMs']['over1000']}               {a['tickWorkMs']['over100']}/{a['tickWorkMs']['over1000']}
Макс. интервал между ticks, с            {b['tickStartIntervalMs']['max']/1000:8.3f}         {a['tickStartIntervalMs']['max']/1000:8.3f}
Число интервалов >100 мс                 {b['tickStartIntervalMs']['over100']:8d}         {a['tickStartIntervalMs']['over100']:8d}
Сумма этих интервалов, с                 {b['tickStartIntervalMs']['sumIntervalsOver100Ms']/1000:8.3f}         {a['tickStartIntervalMs']['sumIntervalsOver100Ms']/1000:8.3f}
Фактические доставки чанков              {b['deliveredChunks']:8d}         {a['deliveredChunks']:8d}
GC: сумма / максимум пауз, с             {b['gcPauses']['seconds']:.3f}/{b['gcPauses']['maximumSeconds']:.3f}      {a['gcPauses']['seconds']:.3f}/{a['gcPauses']['maximumSeconds']:.3f}
Все временные выделения JFR, ГиБ         {g(b['weightedTemporaryAllocationBytes']):8.3f}         {g(a['weightedTemporaryAllocationBytes']):8.3f}
То же на 1000 доставок, ГиБ              {b['weightedTemporaryGiBPer1000Deliveries']:8.3f}         {a['weightedTemporaryGiBPer1000Deliveries']:8.3f}
Server thread: временные байты, ГиБ      {g(ba['weightedAllocationBytes']):8.3f}         {g(aa['weightedAllocationBytes']):8.3f}
Collision compatibility: байты, ГиБ      {target(ba):8.3f}         {target(aa):8.3f}
Пик working set процесса, ГиБ            {g(b['peakRouteWorkingSet']):8.3f}         {g(a['peakRouteWorkingSet']):8.3f}
Пик private commit процесса, ГиБ         {g(b['peakRoutePrivateBytes']):8.3f}         {g(a['peakRoutePrivateBytes']):8.3f}
Heap после выгрузки + равного GC, МиБ    {b['heapAfterUnloadGc']/2**20:8.2f}         {a['heapAfterUnloadGc']/2**20:8.2f}
Working set после выгрузки + GC, ГиБ     {g(b['processAfterUnloadGc']['workingSet']):8.3f}         {g(a['processAfterUnloadGc']['workingSet']):8.3f}

Маршрут сократился на {-percent:.2f}%, оценка временных выделений на доставку —
на {(1-a['weightedTemporaryGiBPer1000Deliveries']/b['weightedTemporaryGiBPer1000Deliveries'])*100:.2f}%. Живой heap после выгрузки практически одинаков: изменения <1 МиБ.
Однако физический пик working set вырос на {rss:.2f}%. Строгую нерегрессию пика RAM
этот единственный прогон НЕ подтверждает; уменьшение оборота объектов не равно
уменьшению RSS. Private commit немного снизился. Никаких новых постоянных
графов/кэшей не добавлено. Повторных одинаковых замеров ради лучшего результата нет.

После отключения игрока и 440 ticks ожидания обе версии сохранили одинаковые
492 owner-shard / 262019 ячеек в spawn, 309530 вкладов и 24985 rootData.
Задач владельцев, миграций, chunk batches/cells и pending snapshot: 0.
Старые deferred-cell индексы: 133 → 134, 9 → 10 root-chunk keys; это не ноль
всех служебных индексов. Изменяемых shard-записей за проход: 0 в обоих случаях.
Точечные unload-тесты подтвердили удаление зависимостей выгруженного source.

Проверки: сборка/check, 21 transaction + 8 rollback проверок, 14 native GameTests
(точный dictionary/legacy saved-mask; изменение файла между cold-read; повреждённая
shapeRef; typed NBT; UUID/footprint; отмена очередей; дверь с реальным движением
и кликами с двух сторон; лестница на границе чанка; разрушение общей статической
ячейки и native disk reload). Все прошли. GameTests — Java17, вне парного замера.
После нового прохода карта нормально сохранена и открыта в НОВОЙ dedicated JVM
без reset/второго маршрута. Пять адресных проб в трёх районах, дверь и лестница
точно сохранили state, owner graph, resident и typed payload. См. evidence/reopen.

Ограничения: это Windows/localhost и минимальные одинаковые моды, а не Linux
хостинг с полной пользовательской сборкой. Нет renderer-клиента, поэтому
клиентские frame freezes и стоимость соседних секций здесь НЕ измерены.
Исправленные клиентский свет, текстуры/UV, ID, рецепты и весь остальной JAR
побайтно прежние; менялись только три серверных Java-класса (включая анонимные
вложенные .class) и version. QA-надстройки в production JAR не входят.

У тестового TCP-приёмника обнаружено несовпадение remapped-имени unload-пакета:
его unload/resident счётчики исключены из выводов. ChunkData deliveries корректны,
маршрут прямой без возвращений, а FULL-гейты дополнительно проверяют сервер.
Память/выгрузка оценены по серверным shard/snapshot/queue данным и GC, не по
ошибочному client-resident счётчику. JFR sample weights — оценка оборота, а не
живой RAM; execution samples — не доля wall time. Кэш ОС не сбрасывался,
базовый прогон первый. Секундные остановки остаются, полного устранения нет.
Остаток: native chunk NBT/BE сериализация, обычная распаковка и owner-пакеты.

Для удалённого сервера: server/README-RU.txt, server.properties (8/6), jvm-args.txt
(9G), zero-context start.sh.memory.patch. Лаунчер и авторизация сохраняются.
Одна команда из каталога Linux-сервера: bash capture-profile.sh <PID_Java>
Нужен подходящий JDK jcmd. Не хватает его JFR/лога, точного состава модов,
реального cgroup лимита, количества игроков и профиля настоящего клиента.

Поставка: game/, sources/changes-from-materials.1.patch, исходники и QA tools
в sources/changed-sources-and-qa-tools.zip, server/, evidence/. Заменить старый
Dreamwalker JAR новым; карта остаётся прежняя очищенная bloodborne.
'''
    (DELIVERY/'REPORT-RU.txt').write_text(report,encoding='utf-8')
    # Refresh source package once with the completed reopen/packaging helpers.
    names=['build.py','qa.init.gradle','tests.init.gradle','dedicated.py','summarize.py','analyze.ps1','capture-profile.sh','package.py','reopen.py','reopen.init.gradle','finalize.py']
    files=[PROJECT/'gradle.properties',*[PROJECT/'src/architecture/java/dev/dreamwalker/bloodbornedw/composite'/n for n in ['ShardedCompositeLedger.java','ImportedTypeMigration.java','ImportedCollisionMigration.java']],PROJECT/'src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/CompositeMembershipReadGameTests.java']+[TOOLS/n for n in names]
    for folder in ['streamingqa','reopenqa']:files+=list((PROJECT/'src'/folder).rglob('*.java'))+[PROJECT/'src'/folder/'resources/fabric.mod.json']
    with zipfile.ZipFile(DELIVERY/'sources/changed-sources-and-qa-tools.zip','w',zipfile.ZIP_DEFLATED) as archive:
        for p in files:archive.write(p,p.relative_to(ROOT).as_posix())
    write(DELIVERY/'sources/source-files.json',[{'path':p.relative_to(ROOT).as_posix(),'sha256':sha(p)} for p in files])
    write(DELIVERY/'evidence/final-receipt.json',{'status':'PASS_PACKAGED_WITH_EXPLICIT_PEAK_RSS_LIMITATION','jarSha256':sha(DELIVERY/'game/dreamwalker-bb-materials.1-streaming.6.jar'),'reportSha256':sha(DELIVERY/'REPORT-RU.txt'),'nativeTests':14,'newProcessReopen':'PASS','performancePairs':1,'originalMapAndDesktopFiles':'UNCHANGED'})

if __name__=='__main__':
    try:main()
    except BaseException:
        (OUT/'finalize-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
