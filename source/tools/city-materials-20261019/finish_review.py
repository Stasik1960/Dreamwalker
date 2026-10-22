"""Record the final review only after both visible-light cases pass on saved reopen."""
import json
import re
from pathlib import Path

OUT=Path(__file__).absolute().parents[3]/'research/city-materials-20261019'
def read(name):return json.loads((OUT/name).read_text(encoding='utf-8-sig'))

def main():
    visual=read('visual-verification.json');trim=read('trim-verification.json')
    negative=read('negative-visual-controls.json');comparison=read('comparison.json')
    built=read('built-jar.json');artifact=read('artifact-verification.json')
    reopen=read('reopen-final2/launch.json')
    assert visual['status'].startswith('PASS_') and trim['status'].startswith('PASS_REVIEWED_')
    assert negative['status']=='PASS_NO_NEW_BLACK_NEGATIVE_CONTROLS'
    assert reopen['exitCode']==0 and reopen['normalSaveMessages'] and reopen['captureComplete']
    assert reopen['artifact_sha256']==built['sha256']==artifact['candidateSha256']==trim['productionJarSha256']
    assert not comparison['newWarningOrErrorFamilies']
    assert read('build-receipt.json')['exitCode']==0
    checks=int(re.search(r'PASS SourceFlatLightChecks: (\d+)',(OUT/'build.log').read_text()).group(1))
    before=comparison['runs']['streaming.5'];after=comparison['runs']['materials.1']
    assert after['jarSha256']==built['sha256']
    repeat=read('repeat-performance.json')
    assert repeat['jarSha256']==built['sha256']
    gib=lambda x:x/2**30
    frames=[]
    for a,b in zip(before['frames'],after['frames']):
        frames.append(f"{a['key']:8} FPS {a['frames']['fpsAverage']:.2f} -> {b['frames']['fpsAverage']:.2f}; p95 кадра {a['frames']['p95Ms']:.3f} -> {b['frames']['p95Ms']:.3f} мс; RSS окна {gib(a['windowRssMedianBytes']):.2f} -> {gib(b['windowRssMedianBytes']):.2f} ГиБ")
    phases=[]
    for a,b in zip(before['phases'],after['phases']):
        phases.append(f"{a['key']:8} переход/ожидание/окно FPS {a['navigationSettleAnd20sWindowMs']/1000:.2f} -> {b['navigationSettleAnd20sWindowMs']/1000:.2f} с; p95 тика сервера {a['serverTicks']['p95Ms']:.2f} -> {b['serverTicks']['p95Ms']:.2f} мс; максимум {a['serverTicks']['maxMs']:.2f} -> {b['serverTicks']['maxMs']:.2f} мс")
    pixels=[]
    for row in visual['cases']+trim['cases']:
        colors=row['nativeScreenRGB']
        pixels.append(f"{row['root']}: экран RGB {colors}; материал/UV/tint/shade/геометрия сохранены, свет — в JSON.")
    regressions=[c['key'] for c in comparison['changes'] if c['fpsChangePercent']<0]
    steady=('В этой паре ухудшения стабильного FPS не наблюдалось.' if not regressions else
        'В этой паре стабильный FPS снизился в ракурсах: '+', '.join(regressions)+'. Данные приведены без скрытия регрессии.')
    review={'status':'PASS_VISUAL_AND_NATIVE_REOPEN_REVIEW','dateLocal':'2026-10-19',
        'productionJarSha256':built['sha256'],'coordinateAssertions':checks,
        'visuals':'Native contact sheets inspected; original and residual basalt triangles, pixels, saved reopen and negative controls passed.',
        'steadyPerformance':steady+' One sequential pair cannot attribute FPS changes to the correction.',
        'memoryAssessment':'Compare full-process peaks and phase RSS in comparison.json; phase RSS and heap snapshots depend on residency/GC timing.',
        'confirmedRemainingDefects':['Existing chunk-load stalls remain. Per-phase p95/max server ticks and durations are reported in comparison.json; no claim that all tail spikes are non-regressing.'],
        'performanceObservations':{'settledFpsDecreasesInThisPair':regressions,
            'firstSceneObservedSeconds':[before['firstSceneObservedAfterMemorySamplerStartSeconds'],after['firstSceneObservedAfterMemorySamplerStartSeconds']],
            'sameJarFacadeRepeatFps':repeat['repeatFps']},
        'performanceNonRegressionProven':False,
        'unconfirmedSuspicions':['13 selected models have out-of-sprite UVs already present in v15, without a confirmed import deviation. Unchanged.',
            'Some distant overview rays miss the bounded 128-block search. Their model identities are not claimed; native images and negative masks were reviewed.']}
    (OUT/'review.json').write_text(json.dumps(review,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    report=f'''Dreamwalker BB materials.1 — финальный результат, 19.10.2026
Версия: {built['version']}
База: streaming.5, ece36054605f3fbd00b1ee90449b6749e0a977d7.
JAR SHA256: {built['sha256']}
Финальный commit и хеши исходников/патча: MANIFEST.json.

Установка
Достаточно заменить JAR мода на клиенте. Использовать прежнюю готовую карту
collision-streaming-20261018/map/world. Подготовка карты, пересчёт света,
конвертация и миграция не нужны. Исправление относится к клиентской отрисовке.
Исходный streaming.5 JAR и независимый backup/world сохранены. Все проверки
выполнялись в одной рабочей копии, без дополнительных копий мира.

Исправленные типы и причина
1. source_bricks_bricks_r_0 variant=1, модель bricks/bricks_r_1,
   корень 253/66/-915, sprite addon/spirelamp_0030, texel 85/89/92/255.
2. source_soul_soil, корень 254/75/-916, addon/spirelamp_008,
   texel 73/73/73/255; прозрачные области и cutout сохранены.
В обоих случаях диагональная грань общего списка читала свет 0 внутри
непрозрачного корня. Её реальная внешняя плоскость находится в ячейке со
светом 15. Исправлена координата выборки существующего света.
3. source_basalt, сохранённый как static_architecture_97 part=79,
   корни 258/71/-919, 258/64/-918, 260/64/-920; модель basalt,
   addon/spirelamp_0014, исходный texel 73/73/73/255.
Центр диагонального пояса находится внутри opacity-15 корня со светом 0,
но все четыре края этой же видимой грани находятся снаружи и имеют sky=15.
Для такого случая берётся среднее двух lightmap-каналов четырёх внешних точек.
Точки смещены на 1% к центру грани и на 0.001 вдоль геометрической нормали.
Если хотя бы одна остаётся в корне или находится в другой непрозрачной ячейке,
усреднение не применяется. Нулевые значения участвуют в среднем без замены.

Это общее исправление доказанного класса граней, без списков координат/ID.
Оно применяется только к плоскому свету диагональных GENERAL-граней source_*
с непрозрачным нативным носителем, source-origin в собственном opacity-15 корне.
Направленные/cullface-грани, кардинальные плоские грани, модели со смещённым
source-origin, модели без доказанной непрозрачности и ветка AO сохраняют прежнюю логику.
Нет минимума яркости, выбора самого яркого соседа, свечения или замены AO.
Блок-коллизия под лучом не считался доказательством фактически видимой модели.

{chr(10).join(pixels)}

Сохранение исходного вида
Один общий аудит выполнен до правки. Все 476 импортированных PNG (242 уникальные
исходные текстуры) побайтно совпали с указанным v15 ZIP. У 1214 импортированных
моделей сохранены UV/tint/shade, отличий ambientocclusion от v15 нет.
У 330 моделей прозрачность PNG находится вне используемых UV и не является
поводом менять слой. У 1528 фактически выбранных моделей missingTextureFaces=0;
абстрактные родительские #texture не объявлялись пропавшими текстурами.
В итоговом JAR побайтно сохранены 8258 ресурсов: все 562 PNG, 4394 JSON моделей,
ID/рецепты/каталоги/форматы сохранения. Геометрия, коллизии, разрушение, двери
и лестницы сохранены. Алгоритмы оптимизаций streaming.5 сохранены; изменения
ограничены клиентскими классами выборки света и её метаданных. Все остальные
классы основного мода побайтно совпадают с базой. Проверка используемых UV/mip
и прежняя маршрутизация SOLID/cutout сохранены. Новых доказанных случаев
прозрачных texel в SOLID не найдено. Тёмные и чёрные texel v15 не осветлялись.

Проверки и скриншоты
Сборка и {checks} узких проверок координат, общего/направленного списка граней,
перезагрузки метаданных и среднего света прошли. Среднее нулевых значений
остаётся нулём. Штатный запуск, сохранение и открытие в новом процессе прошли.
Повторное открытие не использовало reset карты. Оригинальные две поверхности,
три пояса basalt и тёмный контроль проверены по фактическим треугольникам.
Все поля вершин, кроме lightmap, совпали. Свет контроля остался 13.
На трёх ракурсах нет новых чёрных пикселей по негативной маске (база RGB>25,
новая версия maxRGB<12). Ранний широкий черновик давал 18985/2440/41 таких
пикселей и отклонён. Узкий промежуточный a991… оставлял пояса basalt чёрными
и также не выдаётся. Его данные сохранены отдельно, финальный SHA указан выше.
Сравнения при одинаковых настройках: screenshots/*-before-after.png.
Доказательства: evidence/visual-verification.json, trim-verification.json,
negative-visual-controls.json; исходные нативные кадры лежат в screenshots/.

Отдельный контроль AO на промежуточном a991… подтвердил работу нативной AO-ветки
и позволил найти оставшийся basalt. Последняя правка меняет только ветку flat;
ветка AO после контроля не менялась. Этот контроль не является FPS-сравнением
финального JAR. Один дополнительный диагностический запуск был остановлен
до открытия мира из-за моих 10 проб при лимите стенда 8. Его FAIL и причина
сохранены; повтор с 8 пробами прошёл. Ошибка ввода QA не засчитана как тест.

Сравнение со streaming.5 по дополнительному запросу пользователя
База compare-base, финальная сборка compare-final2. Записанная база не запускалась
повторно после изменения только кандидата. Перед каждой измеряемой версией
восстановлены те же 25215 активных файлов В ТОЙ ЖЕ рабочей копии, хеш входа
{before['worldInputManifest']}.
Java17, Xmx10G, 1280x720, дальности 12/12, AO=false, gamma=0.5, день 6000,
ясная погода, без шейдеров/Sodium/DH. Нативный клиент со встроенным сервером;
это не замер отдельного dedicated-сервера. Один и тот же замороженный QA JAR.
В каждом ракурсе доставлены 289 конкретных FULL-чанков, завершена сцена,
после чего записано 20 секунд интервалов flipFrame. Пробы идут после окна.

{chr(10).join(frames)}

Пик RAM/RSS всего процесса: {gib(before['peakRssFullProcessBytes']):.3f} -> {gib(after['peakRssFullProcessBytes']):.3f} ГиБ.
Пик private commit: {gib(before['peakCommitFullProcessBytes']):.3f} -> {gib(after['peakCommitFullProcessBytes']):.3f} ГиБ.
{steady}
Одна последовательная пара не позволяет приписать изменение FPS этой правке.
Из-за просадки фасада выполнен один адресный повтор, совмещённый с обязательным
повторным открытием сохранённой карты. Тот же финальный JAR и настройки дали
{repeat['repeatFps']:.2f} FPS, p95 кадра {repeat['repeatP95FrameMs']:.3f} мс; в этом окне RSS
{gib(repeat['repeatWindowRssMedianBytes']):.2f} ГиБ. Наблюдавшиеся 35 FPS не воспроизвелись.
Это проверка аномалии после сохранения, а не замена исходной пары: все три её
пониженных результата оставлены выше. Пробы усиленного QA идут после окна.
За оба окна на фасаде счётчики новых сборок owned/fallback-мешей и GPU uploads
не менялись; новая выборка света заново не выполнялась на каждом кадре.
На хосте 16 ГиБ RAM. При проверке основного запуска свободно оставалось около
0.4–0.5 ГиБ; одиночный снимок во время повтора также зафиксировал чтение страниц
с диска. Это совместимо с влиянием среды, но не доказывает причину первого
замедления. Отдельные факты сохранены в repeat-performance.json и
reopen-final2/host-state-at-fps.json. Строгая нерегрессия FPS всех ракурсов
не подтверждена; воспроизводимая причинная регрессия самой правки не установлена.
Время от начала измерителя RAM до первой отмеченной навигации в сцену:
{before['firstSceneObservedAfterMemorySamplerStartSeconds']:.2f} -> {after['firstSceneObservedAfterMemorySamplerStartSeconds']:.2f} с.
В финальном запуске запекание моделей перед открытием мира задержалось сильнее.
Один снимок Thread.print до начала кадрового окна показал работу нативного
ModelLoader/BakedModelCacheKey.hashCode. Код новой выборки света на этой стадии
не выполняется; причинная связь задержки с правкой не доказана. Это наблюдение
сохранено отдельно от стабильного FPS и фаз подгрузки, startup-thread.txt.
В повторном открытии первая сцена отмечена через {repeat['firstSceneObservedAfterMemorySamplerStartSeconds']:.2f} с.
OS-кэши не сбрасывались; статистической оценки разброса нет. RAM/commit снимались
примерно каждые 2 секунды. Разный RSS фаз совместим с влиянием резидентности
страниц и не равен разнице созданных Java-объектов. Heap — снимок без forced GC,
а не объём удерживаемых живых объектов. Новые кэши содержат только метаданные
ресурсов, без координат мира, и очищаются при reload. По одной паре нельзя
доказать отсутствие редких выбросов FPS/памяти/задержек.

{chr(10).join(phases)}

Оставшиеся подтверждённые ограничения и подозрения
Подгрузки по-прежнему могут фризить в обеих версиях; их оптимизация отложена.
Максимумы/перцентили и время каждой фазы приведены выше без скрытия ухудшений.
Новых семейств WARN/ERROR и исключений в финальной паре не найдено. Старые
Can't keep up, deferred assembly migration, mip limit и offline-auth 401
сохранены в журналах. Ресурсы, серверная логика и форматы при этом не менялись.
У 13 выбранных моделей UV выходят за спрайт уже в v15; без доказанного отклонения
они оставлены. Дальние пиксели обзорных кадров не все попали в 128-блочный поиск
треугольников: модели для промахов не выдумывались. Новых подтверждённых дефектов
почернения в проверенных сценах не осталось. Это не индивидуальная проверка
каждого блока города. DH/LOD, очистка барьеров и обработка всего города не шли.
evidence/QA-ONLY-*.jar — диагностические дополнения для воспроизведения,
в обычную игру устанавливается только JAR из game/.
'''
    (OUT/'FINAL-REPORT-RU.txt').write_text(report,encoding='utf-8')

if __name__=='__main__':main()
