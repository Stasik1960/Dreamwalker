"""Package only the final tested collision-streaming build and its evidence."""
import argparse
import hashlib
import json
import shutil
import subprocess
import zipfile
from pathlib import Path

PROJECT=Path(__file__).absolute().parents[2]
REPO=PROJECT.parent
OUT=REPO/'deliverables/collision-streaming-20261018'
EVIDENCE=OUT/'evidence'
BASE='28855213258c23c683cbe46e158e594dbb9caf3f'

def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1024*1024),b''):
            h.update(block)
    return h.hexdigest()

def git(*args):
    return subprocess.check_output(['git',*args],cwd=REPO)

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--validation-case',default='shape-reuse-final')
    a=ap.parse_args()
    comparison=read(EVIDENCE/'route-comparison.json')
    tests=read(EVIDENCE/a.validation_case/'receipt.json')
    assets=read(EVIDENCE/'unchanged-assets.json')
    barriers=read(EVIDENCE/'barriers/source-barrier-verification.json')
    backup=read(EVIDENCE/'map-backup.json')
    reopen=read(EVIDENCE/'shapes-reopen-driver.json')
    profiles=read(EVIDENCE/'jfr/comparison-summary.json')
    repair=read(EVIDENCE/'reopen-camera-repair/repair-receipt.json')
    assert comparison['status']=='PASS_ONE_MATCHED_NATIVE_ROUTE'
    assert tests['status']==assets['status']=='PASS'
    assert reopen['status']=='NATIVE_SAVED'
    assert barriers['status']=='PASS_SOURCE_BARRIERS_ALREADY_ABSENT'
    assert backup['status']=='PASS_INDEPENDENT_BACKUP_AND_WORK_COPY'
    assert profiles['status']=='PASS_ONE_MATCHED_ROUTE_JFR_TARGET_COMPARISON'
    assert repair['status']=='PASS_QA_CAMERA_EXPECTATION_ONLY'
    version=dict(line.split('=',1) for line in (PROJECT/'gradle.properties').read_text().splitlines() if '=' in line)['mod_version']
    jar=PROJECT/'build/libs'/('dreamwalker-bb-fabric-1.20.1-'+version+'.jar')
    assert sha(jar)==assets['candidateSha256']==comparison['runs']['shapes-after']['jarSha256']
    (OUT/'game').mkdir(exist_ok=True)
    final_jar=OUT/'game/dreamwalker-bb-streaming.5.jar'
    shutil.copyfile(jar,final_jar)
    native=EVIDENCE/'native'
    native.mkdir(exist_ok=True)
    recordings=[]
    for case in ['shapes-before','shapes-after','shapes-reopen']:
        prep=read(EVIDENCE/(case+'-prepared.json'))
        runtime=Path(prep['run_directory'])
        for filename in ['client-capture-input.json','client-capture-output.json','launch-console.log']:
            shutil.copyfile(runtime/filename,native/(case+'-'+filename))
        capture=read(runtime/'client-capture-output.json')
        for frame in capture['frames']:
            shutil.copyfile(runtime/'screenshots'/frame['fileName'],native/frame['fileName'])
        if case!='shapes-reopen':
            recording=EVIDENCE/'jfr'/case/'streaming.jfr'
            shutil.copyfile(runtime/'streaming.jfr',recording)
            profile_receipt=read(recording.parent/'receipt.json')
            assert sha(recording)==profile_receipt['jfrSha256']
            recordings.append({'path':str(recording.relative_to(OUT)),'sha256':sha(recording),'bytes':recording.stat().st_size})
    (OUT/'sources').mkdir(exist_ok=True)
    tracked=[n.decode('utf8') for n in git('ls-files','-z','--',PROJECT.name).split(b'\0') if n]
    prefix=PROJECT.name+'/'
    selected=[]
    for name in tracked:
        relative=name[len(prefix):]
        if relative.startswith(('src/','tools/','gradle/')) or relative in ['build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','.gitignore'] or relative.upper().startswith(('README','LICENSE')):
            selected.append(name)
    source_zip=OUT/'sources/dreamwalker-bb-streaming.5-source.zip'
    with zipfile.ZipFile(source_zip,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
        for name in sorted(selected):
            path=REPO/name
            assert path.is_file() and not path.is_symlink() and not path.stat().st_file_attributes&1024
            archive.write(path,name)
    patch=OUT/'sources/changes-from-streaming.4.patch'
    patch.write_bytes(git('diff','--binary',BASE,'HEAD','--',PROJECT.name))
    commit=git('rev-parse','HEAD').decode().strip()
    result={'status':'PASS_PACKAGED_TESTED_RESULT','version':version,'baseCommit':BASE,'commit':commit,
            'branch':git('branch','--show-current').decode().strip(),
            'jar':{'path':str(final_jar.relative_to(OUT)),'sha256':sha(final_jar),'bytes':final_jar.stat().st_size},
            'source':{'path':str(source_zip.relative_to(OUT)),'sha256':sha(source_zip),'files':len(selected)},
            'patch':{'path':str(patch.relative_to(OUT)),'sha256':sha(patch)},
            'map':{'path':'map/world','files':backup['files'],'bytes':backup['bytes'],'removedThisRun':barriers['removedThisRun'],
                   'sourceOriginalBarrierCount':barriers['sourceBarrierCount'],'priorRemoved':barriers['previousCleanupRemoved']},
            'independentBackup':'backup/world','comparison':'evidence/route-comparison.json','flightRecordings':recordings,
            'validation':'evidence/'+a.validation_case+'/receipt.json','reopen':'evidence/shapes-reopen-launch.json'}
    result['reopenCameraRepair']={'path':'evidence/reopen-camera-repair/repair-receipt.json','sha256':sha(EVIDENCE/'reopen-camera-repair/repair-receipt.json')}
    (OUT/'MANIFEST.json').write_text(json.dumps(result,indent=2)+'\n')
    before=comparison['runs']['shapes-before'];after=comparison['runs']['shapes-after']
    bp=before['travelPresentation'];np=after['travelPresentation']
    bm=before['peakMemoryTravel'];nm=after['peakMemoryTravel']
    bgc=profiles['runs']['shapes-before']['gcPauses'];ngc=profiles['runs']['shapes-after']['gcPauses']
    read_change=profiles['targetChanges']['CompositeOwnerNbt.read']
    seconds=before['travelAndEqualDeliveryWaitSeconds'];new_seconds=after['travelAndEqualDeliveryWaitSeconds']
    gib=lambda n:n/2**30
    report=f'''Dreamwalker BB streaming.5: коллизии и подгрузка, 18.10.2026
Версия: {version}
База: {BASE}
Исходники: {result['branch']}, commit {commit}
JAR: game/dreamwalker-bb-streaming.5.jar
SHA256: {sha(final_jar)}

Равные неизменяемые боксы, вычисленные формы и списки фрагментов конечного
каталога теперь удерживаются общими ссылками в самих рецептах. Временные
словари инициализации освобождаются. Для 32768 рецептов достаточно 16433
различных форм/списков боксов; физические 4667 входят в этот набор. Полного сброса
после 8192 больше нет. Динамический lookup имеет слабые ключи. Для одного
владельца берётся его сохранённый неизменяемый footprint без промежуточного
списка. В существующем временном пуле чанка одинаковые OwnerDataV1 проверяются
до повторного NBT-разбора; целые неизменяемые списки переиспользуются. Хеш
BlockData вычисляется один раз. Изменяемый payload каждого BE остаётся своим.
Соседние боксы не объединялись: пропускаются лишь точные дубли при вычислении
union. ID, рецепты, порядок фрагментов, владельцы, смещения и форматы сохранены.

Один сопоставимый проход до/после: фасад 272/63/-903, обычное движение
спектатора по одному блоку за шаг к башне 650/110/-150 и району 980/100/95.
Одинаковые {comparison['matchedActiveFiles']} активных файлов исходного мира,
Java 17, Xmx10G, 1280x720, дальность/симуляция 12, AO off, gamma 0.5,
без ресурс-пака/шейдеров/DH. Один и тот же отдельный QA JAR и JFR profile.
На каждом конце требуется 289 конкретных FULL-чанков, готовая сцена,
минимум 80 ticks и 100 ticks стабильного числа резидентных чанков.
Запуск, загрузка ресурсов и финальное сохранение исключены из времени маршрута.

                                      До             После
Движение + равные ожидания, с:       {seconds:.3f}         {new_seconds:.3f}
Максимальный интервал кадра, мс:     {bp['maxMs']:.2f}         {np['maxMs']:.2f}
p99 интервала кадра, мс:             {bp['p99Ms']:.2f}         {np['p99Ms']:.2f}
Кадры длиннее 1 с:                   {bp['over1000ms']}               {np['over1000ms']}
Кадры длиннее 100 мс:                {bp['over100ms']}               {np['over100ms']}
Сумма интервалов >100 мс, с:         {bp['durationOver100msSeconds']:.3f}         {np['durationOver100msSeconds']:.3f}
Фактические доставки чанков:         {before['deliveredChunks']['loadCallbacks']}             {after['deliveredChunks']['loadCallbacks']}
Пик рабочего набора процесса, ГиБ:   {gib(bm['workingSetBytes']):.3f}          {gib(nm['workingSetBytes']):.3f}
Пик private commit процесса, ГиБ:    {gib(bm['privateCommitBytes']):.3f}          {gib(nm['privateCommitBytes']):.3f}
Сумма пауз GC, с:                   {bgc['seconds']:.3f}         {ngc['seconds']:.3f}
Максимум паузы GC, с:                {bgc['maximumSeconds']:.3f}          {ngc['maximumSeconds']:.3f}

Изменение длительности движения и ожиданий: {(new_seconds/seconds-1)*100:+.2f}%.
Первые телепорт/ожидание фасада отдельно: {before['initialTeleportAndSettleSeconds']:.3f} -> {after['initialTeleportAndSettleSeconds']:.3f} с.
Загружено на кадрах facade/tower/brick: {[f['loadedChunks'] for f in before['frames']]} -> {[f['loadedChunks'] for f in after['frames']]}.
Это одно парное наблюдение; кеш ОС не сбрасывался, исходный прогон первый.
Разное число фактических доставок указано отдельно; один прогон не доказывает
универсального ускорения. Дедупликация каталога при запуске не выдается за
устранение подгрузки. Private commit не равен физической памяти; Xmx ограничивает
heap, не весь процесс. Подробные фазы, доставка, память и профили: evidence/.
Профиль именно движения/доставки поддерживает сокращение повторного NBT-разбора:
оценённые JFR выделения внутри CompositeOwnerNbt.read на 1000 доставок
{read_change['beforeInclusiveWeightedGiBPer1000Delivered']:.3f} -> {read_change['afterInclusiveWeightedGiBPer1000Delivered']:.3f} ГиБ.
Это выборочная оценка накопленных временных выделений, не живая RAM; категории
профиля перекрываются. Основная оставшаяся CPU-работа — нативная сборка мешей.

Все 6 точечных нативных проверок прошли: точный XOR занятого объёма всех
16433 различных форм, независимый typed NBT, неправильные packed-конверты,
освобождение временных пулов/слабых ключей, выборочное разрушение общей ячейки
и настоящий ChunkSerializer с записью/чтением дискового чанка. Сохранённый
после маршрута город открыт отдельным процессом, проверен один brick-вид
с 289 FULL-чанками и повторным штатным сохранением. Маршрут не повторялся.
В этой однокадровой QA-проверке исправлено ожидание центра блока после целого
tp: 980/95 -> 980.5/95.5. JAR, мир и сравнительный маршрут не менялись;
дальнейшие команды стенда используют десятичные координаты.
8253 ресурса (включая 562 PNG), освещение, рендер и остальные классы механик
побайтно совпали с исходным JAR за исключением перечисленных классов оптимизации.

Барьеры: оригинальный ZIP SHA256 {barriers['sourceOriginalZipSha256']}.
Все 14855 исходных minecraft:barrier уже удалены прежней операцией. Координаты,
восстановленные из оригинала, побайтно совпали с подтверждённым списком.
Остаток 0; дополнительно удалено 0; записей карты 0. Сохранены 8590 helper-ячеек
с 9240 вкладами соседей; barrier-resident/guest/root в этих местах отсутствует.
Стекло, лестницы, самостоятельные модовые барьеры и свет не удалялись.

Готовая карта: {OUT/'map/world'}
Независимый резерв: {OUT/'backup/world'}
Исходная готовая карта: {backup['sourceWorld']}
По {backup['files']} файлов / {backup['bytes']} байт; полные копии независимо
проверены хешами. Доказательства: evidence/map-backup.json и evidence/barriers/.
Исходники: sources/dreamwalker-bb-streaming.5-source.zip и changes-from-streaming.4.patch.
Дальнейшие ограничения: сложные несколько владельцев могут заново вычислять
динамический union после GC; все причины остановок этим не устранены.
Освещение вне задачи; повторной конвертации, миграции города, DH/LOD и
переписывания рендера не выполнялось.
'''
    (OUT/'REPORT-RU.txt').write_text(report,encoding='utf8')
    result['report']={'path':'REPORT-RU.txt','sha256':sha(OUT/'REPORT-RU.txt')}
    (OUT/'MANIFEST.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result))

if __name__=='__main__':
    main()
