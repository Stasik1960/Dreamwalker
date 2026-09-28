import json,re,shutil,subprocess,xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
DOC=ROOT/'docs/catalog-city-continuation'
RUN=ROOT/'build/catalog-city-diagnostic-20260927-storage-fixed'
OUT=ROOT/'releases/repair-catalog-city-1'
summary=json.loads((RUN/'summary.json').read_bytes())
baseline=json.loads((DOC/'baseline/summary.json').read_bytes())
coverage=json.loads((DOC/'final/creative-catalog-coverage.json').read_bytes())
artifacts=json.loads((OUT/'artifacts.json').read_bytes())
build=json.loads((ROOT/'build/test3-verification/catalog-city-final-check-build.json').read_bytes())
log=(ROOT/'build/test3-verification/catalog-city-final-check-build.log').read_text(encoding='utf8')
counts=[int(count) for count in re.findall(r'Ran (\d+) tests? in',log)]
xml=ET.parse(DOC/'final/TEST-logical-gametest.xml').getroot()
cases=list(xml.iter('testcase')); failures=list(xml.iter('failure'))+list(xml.iter('error'))
assert not failures and cases and build['exitCode']==0
old=summary['remainingIds']; atomic=summary['atomicGroups']; protected=summary['protectedComposite']
reasons='\n'.join('| `'+reason+'` | '+str(count)+' |' for reason,count in atomic['failedGroupsByReason'].items())
links='\n'.join('- ['+name+']('+name+') — '+str(row['bytes'])+' байт, SHA-256 `'+row['sha256']+'`.' for name,row in artifacts['artifacts'].items())
text=f'''# Bloodborne Blocks: каталог, GUI и диагностика города

Статус: **каталог/GUI проверены; полная конвертация города FAIL**. Полностью готового мира пока нет. ZIP города является диагностической копией: его нельзя подменять рабочим сохранением или загружать/сохранять новым JAR, пока не устранены старые/неизвестные ID и composite failures.

Работа продолжена от TEST3, ветка `repair/composite-preserving-grid`, HEAD `{artifacts['verification']['head']}`. Commit, push и перенос в main не выполнялись. Принятые logical/window ресурсы TEST3 не изменены; staging-проверка подтвердила сохранность всех ранее существовавших city definitions, geometry, meshes, mappings, reviewed wall и художественных ресурсов.

## Каталог и GUI

Дедупликация сравнивает установленную геометрию, разрешённые материалы, emissive/animation, физику, placement и все служебные состояния. Разные пути с одинаковым содержимым могут совпадать. Функционально разные и недоказанные пары остаются раздельными. BASE/ALT сохранены в registry и ресурсах; при будущем изменении встроенного art нужно повторно сгенерировать creative-equivalence proof. Внешний ресурс-пак сам по себе не пересчитывает статический каталог.

| Счётчик | Итог |
|---|---:|
| Registry BlockItem IDs | {coverage['registeredBlockItems']} |
| Кандидаты художественных вариантов | {coverage['artCandidates']} |
| Видимые записи | {coverage['visibleEntries']} |
| Доказанно скрытые эквиваленты | {coverage['hiddenEquivalentEntries']} |
| Основная вкладка: записи | {coverage['mainVisibleEntries']} |
| Техническая вкладка: записи | {coverage['technicalVisibleEntries']} |
| Зарегистрированные block states | {coverage['registeredStates']} |
| Исключённые placement states | {coverage['placementExcludedStates']} |
| Служебные состояния, свёрнутые в art-кандидатов | {coverage['serviceStatesCollapsedIntoArtCandidates']} |

Служебный `architecture_part` не имеет BlockItem и исключён явно. Это отдельная registry-запись сверх указанного счётчика BlockItem IDs. Каждый кандидат покрыт собственной записью либо прямым доказанным redirect; циклы и цепочки запрещены. Runtime IDs и художественные ресурсы не удалялись. Четыре новых owner ID добавлены для доказанных исторических объектов с пустой storage-root, а не для оформления каталога.

GUI использует bounds конкретной выбранной baked-модели, центрирование и пропорциональное уменьшение до 84% слота. Кэш различает объекты моделей и обновляется при model reload. Мировые transforms, collision/outline и семь остальных режимов предмета сохранены. Математические проверки и выбор art PASS; графический клиент **NOT_RUN**.

## Свежая диагностика

Вход: `reference-inputs/latest-modded-world.zip`, SHA-256 `{summary['source']['sha256']}`. Это подтверждённый MODDED backup; TEST3/beta.3 и vanilla-карта входом не служили. Результаты первой диагностики сохранены отдельно в `baseline/`; после конкретного исправления storage-root сделан новый проход с оригинального ZIP в отдельный каталог. Прерванные попытки в build не учитываются в итоговых числах.

`--atomic-owner-groups` теперь действительно включает проверенный shared-ownership путь. Preflight, root/root, foreign-block, membership, owner-limit и целостность транзакций сохранены. Independent fragment fallback и city-compat palette не включались.

| Единица измерения | До storage-root исправления | Финальный свежий проход |
|---|---:|---:|
| Ledger-транзакции | {baseline['ledgerEntries']} | {summary['ledgerEntries']} |
| Восстановленные root-объекты | {baseline['restoredRootObjects']} | {summary['restoredRootObjects']} |
| Восстановленные atomic groups | {baseline['atomicGroups']['converted']} / {baseline['atomicGroups']['total']} | {atomic['converted']} / {atomic['total']} |
| Отклонённые atomic groups | {baseline['atomicGroups']['notConverted']} | {atomic['notConverted']} |
| Уникальные целевые registry-семейства | {baseline['uniqueTargetRegistryFamilies']['count']} | {summary['uniqueTargetRegistryFamilies']['count']} |

Root-объект — уникальная пара `(dimension, targetRoot)` итогового ledger. Семейство здесь — точный target registry ID; семантическая уникальность не заявляется. Группы, memberships, объекты и транзакции не складываются.

Причины оставшихся отказов групп:

| Причина | Групп |
|---|---:|
{reasons}

Protected/composite gate: **{protected['result']}**, неполных protected-объектов **{protected['fragmentedProtectedObjects']}**, семейств с отказами **{protected['familiesWithFailures']}**, недоказанных memberships **{protected['unprovenMemberships']}**, foreign-conflict occurrences **{protected['foreignConflictOccurrences']}**. Это самостоятельные показатели проверки, не дополнительные группы. Отказы не означают разрешение перезаписывать чужие клетки; preservation ниже подтверждает отсутствие изменений вне ledger.

Helpers: **{summary['helpers']['checked']} проверено, {summary['helpers']['orphanHelpers']} orphan**, PASS. Из отсутствующих в текущем registry исторических ID: **{old['knownOldDistinctIds']} ID / {old['knownOldBlocks']} блоков**. Неизвестные даже frozen legacy+v2 архиву: **{old['unknownDistinctIds']} ID / {old['unknownBlocks']} блока**. Ещё {len(old['registeredButReportedUnmatched'])} уже зарегистрированных ID остаются в converter unmatched ({sum(old['registeredButReportedUnmatched'].values())} блоков); наличие ID не считается доказательством корректного восстановления объекта. Полные списки приведены в `final/summary.json`.

Preservation: **{summary['preservation']['result']}**; {summary['preservation']['preserved_nonterrain_files']} нетеррейновых файлов сохранено; изменено {summary['preservation']['changed_cells']} клеток в {summary['preservation']['changed_chunks']} чанках, все изменения объяснены ledger. Независимый checker сравнил все 10 009 чанков / 1 003 216 896 блоковых ячеек с исходником и проверил resource fingerprints.

Второй проход: **{summary['secondPass']['counts']['converted']} конвертаций**, preservation {summary['secondPass']['preservation']['result']}, {summary['secondPass']['firstFiles']} файлов побайтно совпадают, изменённых путей {len(summary['secondPass']['changedPaths'])}. Idempotence PASS не заменяет провал composite/registry gates.

## Проверки и оставшиеся ограничения

`check build logicalGameTest --max-workers=1`: PASS, {len(cases)} GameTest / 0 failures. В логах unittest: {sum(counts)} тестов в {len(counts)} запусках; script-based invariant checks проходят дополнительно. Новый server GameTest покрывает все четыре пустых root, физику соседних клеток, ownership, rebuild и cleanup без orphan. Проверки выполнялись на Java 17 / Gradle 8.8, Minecraft 1.20.1, Yarn 1.20.1+build.10, Loader 0.16.10, Fabric API 0.92.9+1.20.1, Loom 1.6.12; версии зависимостей не обновлялись.

Точная Gradle-команда сохранена в `final/check-build.json`; все команды конвертации и сравнения — `final/*.command.json`, выходы — соседние `.log`. После синхронизации версии в fabric.mod.json выполнен дополнительный `assemble logicalGameTest --max-workers=1` — PASS, тот же набор из {len(cases)} тестов (не суммируется с первым запуском); команда и лог — `final/metadata-assemble.*`. Использованы `--offline --no-daemon`, существующий `tools/verification-direct-resources.init.gradle`, закреплённый JDK и bundled Python. Полные ledger/gate/residual proofs сохранены с gzip.

Очистка legacy IDs/ресурсов **не начата**, поскольку условие успешной полной конвертации не выполнено. Полный город не загружался в новый runtime: unresolved registry IDs сделали бы такую загрузку потенциально убыточной. Следующий шаг — конкретные оставшиеся группы из `final/residuals.json.gz`: доказать целые owner closures для membership/foreign/root конфликтов, отдельно доказать точную допустимую физику оставшихся неподдержанных owners. Без этих доказательств guards сохраняются. 23 неизвестных ID перечислены с количеством в summary; для их идентификации нужен соответствующий исходный mapping/art, существующий frozen архив их не содержит.

## Артефакты

{links}

Экспорт art содержит все текущие JSON-модели и PNG/mcmeta, распакованные meshes, reference geometry/contracts/mappings, а также существующие ALT artist kit и template. Geometry/reference остаются данными для выравнивания, не местом изменения gameplay. Проверены каждый payload SHA-256, оба вложенных ZIP и побайтовое соответствие текущим assets. JAR ресурсы ({artifacts['verification']['jarRuntimeResourceFiles']} файлов) проверены против исходников; diagnostic world ZIP соответствует сохранённой проверенной копии.
'''
(OUT/'REPORT.md').write_text(text+'\nМашинные доказательства: [итоговые показатели](../../docs/catalog-city-continuation/final/summary.json), [проверки сборки](../../docs/catalog-city-continuation/final/check-build.json), [каталог](../../docs/catalog-city-continuation/final/creative-catalog-coverage.json). Пути `baseline/` и `final/` выше относятся к `docs/catalog-city-continuation/`.\n',encoding='utf8')
doc_text=text
for name in artifacts['artifacts']:
    doc_text=doc_text.replace(']('+name+')','](../../releases/repair-catalog-city-1/'+name+')')
(DOC/'REPORT.md').write_text(doc_text,encoding='utf8')
print(json.dumps({'gameTests':len(cases),'unittestCases':sum(counts),'unittestRuns':len(counts),'report':str(OUT/'REPORT.md')},ensure_ascii=False))
