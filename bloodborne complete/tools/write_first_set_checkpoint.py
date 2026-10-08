"""Write the review checkpoint only from matching successful artifact evidence."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def read(path):return json.loads(Path(path).read_text(encoding='utf8'))

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--revision',required=True)
    p.add_argument('--attempt',type=int,required=True)
    p.add_argument('--client-report',type=Path,required=True)
    p.add_argument('--client-reopen-report',type=Path,required=True)
    p.add_argument('--full-client-report',type=Path,required=True)
    p.add_argument('--alt-report',type=Path,required=True)
    p.add_argument('--shader-report',type=Path)
    p.add_argument('--jar',type=Path,default=ROOT/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-prototype.2.jar')
    a=p.parse_args();assert re.fullmatch('v[1-9][0-9]*',a.revision)
    suffix=a.revision.upper();artifact=sha(a.jar);report_dir=ROOT/'reports'
    xml=report_dir/f'FIRST_SET_GAMETEST_ATTEMPT_{a.attempt}.xml'
    log=report_dir/f'FIRST_SET_GAMETEST_ATTEMPT_{a.attempt}.log'
    cases=list(ET.parse(xml).getroot().iter('testcase'))
    assert len(cases)==61 and all(c.find('failure') is None and c.find('error') is None for c in cases)
    raw=log.read_text(encoding='utf8',errors='replace')
    assert 'PASS 19 transaction core checks' in raw and 'BUILD SUCCESSFUL' in raw
    evidence=[]
    def gate(path,status='PASS',client=False):
        d=read(path);assert d['status']==status,(path,d['status']);assert d['artifact_sha256']==artifact,path
        assert d['exit_code']==0,path
        if client:assert d['integrated_save_messages_present'] and d['derived_world_copy']['source_unchanged_after_run']
        evidence.append({'path':str(path),'sha256':sha(path),'status':d['status'],'artifact_sha256':artifact});return d
    minimal=gate(a.client_report,'PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT',True)
    gate(a.client_reopen_report,'PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT',True)
    full=gate(a.full_client_report,'PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT',True)
    alt=gate(a.alt_report,'PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT',True)
    shader=gate(a.shader_report,'PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT',True) if a.shader_report else None
    alt_runtime=read(report_dir/f'ALT_RUNTIME_{suffix}.json')
    assert alt_runtime['status']=='PASS_ACTUAL_ALT_PACK_TEXTURE_AND_MODEL_REPLACEMENT' and alt_runtime['artifact_sha256']==artifact
    client_persistence=read(report_dir/f'CLIENT_SCENE_PERSISTENCE_{suffix}.json')
    assert client_persistence['status']=='PASS_BOUNDED_CLIENT_SCENE_PERSISTENCE' and client_persistence['artifact_sha256']==artifact
    if shader:
        live=shader['client_review_output']['result']['irisRuntime']
        assert live['status']=='PASS_ACTIVE_IRIS_RENDERING_PIPELINE' and live['frameCounterAdvanced']
        assert len(live['effectiveOptions'])==42 and all(row['matches'] for row in live['effectiveOptions'])
    for group in ('REVIEW_SCENE','SOURCE_REVIEW'):
        for phase in ('AUTHOR','REOPEN'):
            gate(report_dir/f'SERVER_{group}_{phase}_{suffix}.json')
    gate(report_dir/f'SERVER_SOURCE_REVIEW_REPLAY_{suffix}.json')
    gate(report_dir/f'SERVER_FIRST_SET_FULL_{suffix}.json')
    scene=read(report_dir/f'FIRST_SET_REVIEW_SCENE_{suffix}.json')
    source=read(report_dir/f'FIRST_SET_SOURCE_REVIEW_SCENE_{suffix}.json')
    # Archive tools have their own exact world-byte and artifact gates.
    assert artifact in json.dumps(scene) and artifact in json.dumps(source)
    post=read(report_dir/f'SOURCE_REVIEW_POSTSAVE_INDEPENDENT_{suffix}.json')
    assert post['status'].startswith('PASS_')
    result={'schema':'dreamwalker-first-set-checkpoint-v1','status':'NOT_READY_FULL_TASK',
        'first_set':'PACKAGED_PENDING_USER_REVIEW','user_visual_game_acceptance':'PENDING',
        'artifact_sha256':artifact,'revision':a.revision,'native_tests':len(cases),'transaction_checks':19,
        'native_xml_sha256':sha(xml),'build_log_sha256':sha(log),'evidence':evidence,
        'manual_graphics_gameplay':'NOT_RUN','city_conversion':'NOT_STARTED','production_gallery':'NOT_GENERATED'}
    (report_dir/'FIRST_SET_CHECKPOINT.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    inputs=read(ROOT/'INPUTS.json')
    inputs['target']['build_status']='PROTOTYPE_2_REMAPPED_FIRST_SET_CHECKPOINT_VERIFIED_FULL_TASK_NOT_READY'
    inputs['target']['prototype_2_artifact_sha256']=artifact
    inputs['target']['first_set_evidence_revision']=a.revision
    inputs['target']['user_visual_game_acceptance']='PENDING'
    (ROOT/'INPUTS.json').write_text(json.dumps(inputs,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    shader_text='Обычный клиент с Iris/Kappa 5.2 загрузил мир с actual IrisRenderingPipeline без fallback, advancing frame counter и42 live options, проверил модели и штатно сохранился/вышел; художественная/игровая шейдерная приёмка не выполнена.' if shader else 'Фактический запуск шейдерного клиента не подтверждён.'
    progress=f'''# Dreamwalker BB — состояние работы 2026-10-07

Ветка `codex/dreamwalker-bb-fabric-1.20.1`. Полное задание **NOT_READY**. Первый набор **PACKAGED_PENDING_USER_REVIEW**; визуальная/игровая приёмка пользователя **PENDING**. Производственные каталог/город/галерея ещё не готовы.

## Действующие требования и границы

TASK.md сохраняет исходный new_prompt_revised.txt побайтным префиксом и содержит последующие уточнения. Действующее имя мода dreamwalker-bb-fabric-1.20.1; ID bloodborne_dw; встроенный RP namespace bloodborne_rp. Восемь оригинальных файлов, установленная сборка dw, другие проекты, лаунчер/авторизация не изменены. Публикация/merge/deployment не выполнялись.

Основа изучена: локальная 6bca311b87856ee251cc1d88f94ba6071ae231cf, remote main 12e35e0d282837510d79f3b8b33c3ebc088cf380, bloodborne ca01e3d94630691acf43d8132606bc5f55c628e0. Reuse review: docs/REUSE_REVIEW.md. Исторические палитры/PASS не используются доказательством новой карты.

## Приоритетное уточнение пользователя: коллизии, установка, диагонали

Все пять пунктов уточнения внесены в TASK.md и применены поверх противоречащих прежних формулировок:

- Физическая коллизия, выделение и ограничения установки разделены. Твёрдая форма не запрещает соседнюю установку автоматически.
- Касания/выступы/частичные декоративные пересечения разрешены по умолчанию. Резервируются необходимые основные клетки; общий AABB не заполняется. Подходящие неполные опоры проверяются по поверхности.
- Исходные углы, включая 45°, применяются один раз. Обычный предмет/инструмент дают восемь ориентаций при одном ID. Тонкие диагонали имеют сегментированную коллизию с пустыми углами.
- Фактическая исходная физика с её модами измерена отдельно; доступные проходы сохраняются. Существенные изменения двери/окна/дерева записаны как предложения для приёмки.
- Декоративный куст по умолчанию проходим. Ствол/ветви/крона оцениваются отдельно. Проходимая крона остаётся доступной для выделения/копирования/поворота/удаления.

Контракты и ограничения: docs/PHYSICS_POLICY.md, COMPOSITE_RUNTIME.md и объектные журналы.

## Анализ исходников

Проверены 47 regions, 35 890 terrain chunks, 3 852 занятых полных состояния. Исходник Minecraft1.18.2/DataVersion2975; level объявляет три vanilla dimensions, terrain сохранён в Overworld. Entities/players/block entities/data inventoried; 12 пустых region artifacts и обрезанный временный level сохранены, основной level.dat исправен.

Проанализированы 1 121 моделей и 400 PNG, включая source dependencies. Кандидаты файлов не равны объектам: 923 complete-model candidates, 141 multipart parts, 13 templates, 18 duplicate nonrepresentatives, 15 missing и 11 ambiguous. Выявлены 61 source/target model-chain различие и 18 текстур; импорт замораживает зависимости Mojang1.18.2. Занятые клетки Y=-64..319, geometry достигает Y=321. Галерея требует отдельной настройки высоты только её копии.

## Первый набор и данные

Реализованы семь временных семейств: двойная дверь, деревянное/тонкое окна, лестница, стенка, 18-part дерево, крыша. Крыша и деревянное окно — независимые ID при одном исходном jungle_stairs. Основные/служебные части, ledger UUID, native overlays, атомарное размещение/откат, pick/drop, восемь поворотов и BASE/ALT проверены. Чужие стены/земля/сундук и их NBT сохраняются при декоративном пересечении.

Дверь сохраняет закрытые UV; внешние петли, неподвижные 1-pixel стойки и проходимый нижний рисунок дают проход после открытия. Дерево собрано по двум реальным 18-cell примерам: узкий нижний ствол твёрдый, ветви/крона проходимы и выделяются. У окна центр остаётся твёрдым, боковые панели имеют предложенную открытую позу. Эти изменения исходной логики требуют визуальной/игровой приёмки.

Обычная лестница: три source variants, 8 ориентаций, BASE/ALT, waterlogging, 96 states; технический source_clone даёт 192 зарегистрированных states того же ID. Все 34 source honey1 установки — пары с adjacent vanilla climbing cell. Фиксированная legacy опора сохраняет исходную полную коллизию на прежнем месте, лазание — измеренную полосу3/16. Все 1 360 authored corners совпали с source после проверенной art/pivot компенсации. Honey0 caps не включены. Удаление пары/права/save-unload-reload проверены.

Полный RP встроен в один JAR; все 707 original resource files byte-exact, 28/30 Java файлов неизменны. Два узких serialization hooks сохраняют typed unknown legacy payload, не заменяя рабочие роли/AI/health. Standalone RP вместе с combined JAR отвергается до RP инициализации. Технический source light в двух доказанных клетках — luminosity9, isAir=false, replaceable, empty collision/outline/fluid, no item/BE; внутреннее имя bloodborne_dw:source_hunter_lamp_light.

## Актуальные технические результаты

Production SHA256 `{artifact}`, prototype.2. Attempt{a.attempt}: **61/61** actual Fabric GameTests, **19** transaction checks, check/build/remap PASS. Все предыдущие XML/log/ошибки сохранены как история. Java17, Loom1.6.12, Gradle8.8, Yarn1.20.1+build.10, Fabric API0.92.9, Gecko4.4.9; обычные проверки Loader0.19.5.

Ревизия {a.revision}: отдельная сцена 68 объектов и source subset 41 объекта/110 members авторизованы только в derived copies, сохранены и повторно открыты production-only сервером; повторный source вызов — persistent no-op. Независимые проверки typed native/foreign BE данных, source lights, 15 RP UUID/typed Original и ownership выполнены. Дополнительные generated chunks, metadata/light/ticks/runtime entity поля учитываются отдельно, побайтная тождественность всего живого мира не заявляется. Архивы проверены побайтно по всем включённым файлам; session.lock исключён.

Обычный минимальный клиент4GB проверил actual models/sprites/vanilla STONE/инструменты/состояния/rerender, открыл мир и штатно сохранился/вышел. Новая копия его сохранения повторно открыта и сохранена. Независимо сверены68 states,12 UUID/root NBT,448 root/helper BEs, ledger и сундук7diamonds в minimal/reopen/full сохранениях; дополнительные BalmData поля двух RP entities записаны отдельно. Выбранные 83 серверных и 109 клиентских мода также прошли запуск/save/exit. Их сторонние warnings/errors перечислены в первичных отчётах; PASS относится к этим действиям, а не всей игре. ALT pack загружен в правильном приоритете:32ALT texture replacements и32ALT model replacements доказаны actual baked vertex hashes относительно BASE, оригинальный архитектурный pack новой сцене не нужен. {shader_text}

Ранний OOM клиента был вызван 592 wall multipart selectors ×32 768 states. Resolver сохраняет state appearance при 160 общих native-baked primitives и ограниченных wrappers; equivalence audit содержит0 mismatches. 16 собственных sprites добавлены additive atlas metadata с сохранением vanilla sources; PNG и vanilla models/textures не менялись. Тесты сохраняют native collision/outline отдельно от selection overlays, включая свет под проходимой кроной и worldless Create queries.

Исходный actual Forge1.18.2/Bloodborne6.0/Gecko3.0.57 клиент:165 shape observations/7 swept FakePlayer movements, весь путь загружен, normal exit0. Старое окно с unloaded neighbor retained as partial history, не эталон.

## Не выполнено и следующий этап

Ручное сравнение graphics, Creative UI/лазание/игра, два клиента, RP оружие/формы/рычаги/два фонаря и city performance NOT_RUN. Windows Graphics Capture прежде дважды завершился timeout; новая попытка Computer Use остановлена пользователем клавишей Escape, последующие UI-действия не выполнялись. Автоматический клиентский PASS не объявляется визуальной приёмкой.

63 использованных состояния без выбранной модели затрагивают10 795 клеток, ещё3 с отсутствующей видимой поверхностью —227. Реконструкция/исключение не согласованы. Полный RP dry run NOT_APPLIED:264entities/6players/56 inventoryBEs, восемь legacy utility stacks не сопоставлены. Полный семантический каталог/частоты/пятизначные ID не закреплены; allocator5tests PASS не заменяет реальные частоты.

Далее: пользовательская приёмка конкретного review kit, затем пакетное расширение объектов/каталога/миграции/галереи. TASK §8: «Не начинай массовую генерацию и преобразование всего города, пока этот набор не прошёл технические проверки и не получена моя визуальная/игровая приёмка его спорных случаев.» Этот gate открыт; независимый анализ допустим, городской конверсии без ответа нет.
'''
    (ROOT/'PROGRESS.md').write_text(progress,encoding='utf8')
    matrix=f'''# Матрица первого набора — {a.revision}

Production SHA256 `{artifact}`. Полное задание NOT_READY; приёмка пользователя PENDING.

| Проверка | Результат | Доказательство/граница |
|---|---|---|
| Размещение/45°/pick/remove/пересечения/опоры/NBT/rollback | PASS61/61 | FIRST_SET_GAMETEST_ATTEMPT_{a.attempt}.xml; настоящее движение/лучи/права/save-unload-reload |
| Транзакционное ядро | PASS19 | Gradle check/build log; Java17 |
| Исходная физика с Bloodborne6.0 | PASS измерений165/7 | SOURCE_PHYSICS_CLIENT_SWEPT_ACTUAL.json; actual original integrated client, не ручная игра |
| 68 новых объектов author/save/production reopen | PASS | SERVER_REVIEW_SCENE_AUTHOR/REOPEN_{suffix}; FIRST_SET_REVIEW_SCENE_{suffix} |
| 41 исходных объектов/110members/15RP/2lights | PASS ограниченного scope | SERVER_SOURCE_REVIEW_*_{suffix}; независимые SOURCE_REVIEW_*_{suffix}; metadata/runtime differences отдельно |
| Повторный source вызов после сохранения | PASS no-op | Persisted UUID/signature/bindings, source/native/foreignBE сохранность |
| Минимальный ordinary client4GB, ресурсы/save/exit | PASS | {a.client_report.name}; actual baked models/sprites, vanilla atlas retention |
| Повторное открытие клиентского сохранения | PASS | {a.client_reopen_report.name}; свежая derived copy |
| 83server/109client selected mods | PASS startup/save/exit | SERVER_FIRST_SET_FULL_{suffix}; {a.full_client_report.name}; не вся gameplay совместимость |
| ALT-only pack actual load/resources | PASS | {a.alt_report.name}; внешняя BASE архитектура не требуется |
| Shader client | {'PASS startup/resources/save/exit' if shader else 'NOT_RUN'} | {a.shader_report.name if shader else 'Нет actual shader runtime proof'}; визуальная/игровая приёмка NOT_RUN |
| RP ресурсы/registry namespace и standalone rejection | PASS | RP_PACKAGE_VERIFICATION/REVIEW_PACKAGE_VERIFICATION/SERVER_NEGATIVE_STANDALONE_RP_FIRST_SET_FINAL_{suffix} |
| Ручное изображение/Creative UI/проходы/лазание | NOT_RUN | Нужна пользовательская игра в комплекте |
| Два клиента/RP forms/levers/two-lamp travel | NOT_RUN | Требуется отдельный gameplay прогон |
| Полный каталог/город/пятизначные IDs/галерея | NOT_READY | TASK§8 gate; source defects/8legacy utility stacks остаются |

Спорные предложения: внешний hinge/open pose двери; твёрдый центр/боковое открывание окна; узкий ствол и проходимая крона/ветви дерева; собственная low-wall высота. См. docs/PROTOTYPE_REVIEW.md и объектные журналы. Исторические diagnostic JAR/сцены не устанавливать.
'''
    (ROOT/'CHECK_MATRIX.md').write_text(matrix,encoding='utf8')
    (ROOT/'TEST_MATRIX.md').write_text(matrix,encoding='utf8')
    readme=f'''DREAMWALKER BB — ПЕРВЫЙ НАБОР ДЛЯ ПРИЁМКИ ({a.revision})

Это рабочий prototype.2 первого набора, а не завершённый городской мод.
Production SHA256: {artifact}
Полное задание NOT_READY; пользовательская визуальная/игровая приёмка PENDING.

1. Сделайте отдельную тестовую установку Minecraft1.20.1/Fabric/Java17.
   Проверены Loader0.19.5, Fabric API0.92.9+1.20.1 и GeckoLib4.4.9.
   Положите один dreamwalker-bb-fabric-1.20.1-0.1.0-prototype.2.jar в mods.
   Он уже содержит RP. Уберите из этой тестовой установки standalone
   bloodborne-rp и старый Complete; не изменяйте рабочий игровой мир.
   Optional QA JAR нужен только автоматическим инструментам, для игры не нужен.

2. Распакуйте папки миров из First-set-new-placement-scene.zip и
   First-set-migrated-source-fixture.zip в saves тестовой установки.
   Новая площадка использует модели мода и не требует original resource pack.
   У исходного фрагмента остаются невыбранные vanilla-carriers; для их
   исходного оформления подключите предоставленный bbmc_v15_resource (1).zip.
   Source-coordinate-fixture.zip — неизменённый 1.18.2 original subset;
   его эталон требует отдельного Forge/Bloodborne6.0/Gecko3.0.57 профиля.

3. Новая сцена: spawn0,64,0; двери/окнаZ8, стенкиZ22, крышиZ26,
   два пересекающихся 18-part дерева24/26,64,32, лестницыZ48.
   68 объектов, обычный сундук с7diamonds, RP дерево и фонарь.
   Состав/координаты исходной сцены находятся в mapping/source-review-input.json.
   В ней41объект/110member cells, включая34пары лестниц; остальной город не преобразован.

4. В Creative откройте Dreamwalker: прототипы / Bloodborne: цельные прототипы.
   Обычный предмет ставит объект при восьми направлениях игрока.
   Инструмент строителя: ПКМ поворачивает45°, Shift+ПКМ переключает BASE/ALT.
   У стенки отдельный инструмент с теми же действиями.
   При перекрытии положите инструмент в offhand и оставьте main hand пустой:
   ПКМ циклически выбирает владельца, затем инструментом измените его.
   Средняя кнопка копирует полезный художественный предмет. Удаление убирает
   цельный объект, сохраняя чужие стены/землю/сундук и соседний объект.

5. Проверьте с обеих сторон дверь, окна и тонкие45° части, проходы у пола,
   диагональные пустые углы, лестничный стык/лазание, опоры из неполных блоков,
   повторную установку и save/reopen. Дерево: ствол твёрдый, ветви/крона
   проходимы и выделяются. Исходный лестничный backing сохраняет прежнюю
   полную коллизию на фиксированном месте и не переносится при повороте.
   BASE остаётся эталоном; ALT-example.zip подключается отдельно как pack.

6. Нужен ваш ответ о спорных случаях: дверь/open pose/петли; неподвижный центр
   и боковые панели деревянного окна; узкий ствол/проходимая крона дерева;
   высота и соседство стенки; диагонали и реальные проходы.
   Укажите принятие или правки с координатой/семейством; автоматические PASS
   не заменяют эту игровую приёмку. TASK§8 требует её до массовой генерации.

Документы: PROGRESS.md, TASK.md, CHECK_MATRIX.md, docs/PROTOTYPE_REVIEW.md,
docs/PHYSICS_POLICY.md, docs/SOURCE_REVIEW_MIGRATION.md. Сверяйте artifact_sha256
в текущих {suffix} отчётах; старые попытки сохранены как история.

Сборка полного source ZIP: Java17; gradlew.bat check build remapReviewJar.
Игровые проверки: gradlew.bat firstSetGameTest -Pgametest_run_name=имя_новой_попытки.
Запуски инструментов выполняются из корня извлечённого исходного проекта;
оригинальные внешние входные пути указаны в INPUTS.json и CLI параметрах.

Ещё не завершены: full catalog/numeric IDs, source defects,8legacy utility stacks,
полный город/галерея, ручное graphics/gameplay, два клиента/RP формы/рычаги/
two-lamp travel и city performance. Оригиналы и рабочая установка не изменены.
'''
    (ROOT/'README.txt').write_text(readme,encoding='utf8')
    print(json.dumps({'status':result['status'],'first_set':result['first_set'],'artifact_sha256':artifact,'files':['PROGRESS.md','README.txt','CHECK_MATRIX.md','reports/FIRST_SET_CHECKPOINT.json']}))

if __name__=='__main__':main()
