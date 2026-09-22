# Ручная курация logical families

**Catalog B/Cxxx теперь имеет отдельный v2 pipeline:**
[batch-02](manual-review/source-assemblies/batch-02/index.html),
[правила signatures/coverage](CATALOG-B-V2.md). Ниже описан Catalog A/Fxxx;
его `decide` нельзя применять к C-карточкам. Catalog B не применяет решения к моду.

Этот этап не меняет Java, runtime resources, logical contracts POC или мир.
Все 256 существующих families — кандидаты, не подтверждённые семантические объекты.
Пять принятых POC имеют `status: APPROVED`, `architecture_status: POC_ACCEPTED`.

## Где смотреть

- Каталог первой партии: `build/manual-review/index.html`.
- PNG contact sheet: `build/manual-review/batch-01-contact.png`.
- 25 карточек по приоритету: 9 деревьев/растительности, 12 контейнеров/книг,
  4 мемориала/статуи/свечи. Остальные POC показаны отдельно как эталоны.
- `docs/manual-families.json` — versioned источник стабильных review ID и решений.
- `build/manual-review/occurrences.json` — воспроизводимая статистика только
  правильного исходного мира с hash/config provenance; это не ledger конвертации.

У карточек три ракурса **текущего полного logical mesh** и отдельные нумерованные
source-model applications. Компоненты независимо масштабированы для читаемости;
таблица под изображением содержит model ID и XYZ offset. Номер обозначает application,
а не AABB/полигон/каждую Minecraft-ячейку. Если внутри одной модели несколько объектов,
нужно `NEEDS_REVIEW` с описанием, а не выдуманные номера частей.

Контекст ±2 клетки показан ортогональными срезами и таблицей relative XYZ → source
block/state. Окружение не включается в family. `candidate_count` — совпадения
известного шаблона, а не доказательство семантической границы. `not_mapped`/null
значит отсутствие надёжной привязки к исходнику, **не ноль объектов в мире**.
Inherited category отмечена PROVISIONAL; числовой confidence не выдумывается.

## Как отвечать

```text
F001 OK
F003 MERGE: F004+F005
F002 SPLIT: 1+2 / 3
F006 CONNECTED
F007 STATE_VARIANTS: F008
F009 NOT_OBJECT
F010 DELETE
F011 NEEDS_REVIEW: описание сомнения
```

ID в примерах условные. Пять POC повторно подтверждать не нужно.
MERGE/STATE_VARIANTS с несколькими ID записывает явное решение для всех названных
families. SPLIT требует полного разбиения показанных номеров без пропусков/повторов.
Решения на HTML-странице — черновик для отправки в чат, не запись на диск.

После явного подтверждения пользователя ответ можно сохранить в текстовый файл и
записать командой `decide`. Это только очередь решений, НЕ запуск миграции/генерации.
Каждая последующая реализация ограничена подтверждённой партией; UNREVIEWED не
расширяются автоматически. DELETE также сначала решение, а не удаление файлов.

Повторная генерация сохраняет ID, component numbers, пользовательские поля и историю.
Новые ID только добавляются, удалённые из исходников не переиспользуются.
Если upstream manifest изменился, выставляется `source_drift`, старое решение не
отменяется; каталог с таким drift блокируется до сверки. Для явной замены ранее принятого решения нужен `--replace`; старое
решение сохраняется в `decision_history`. Односторонняя замена участника MERGE или
STATE_VARIANTS отклоняется: пересматривать нужно целую группу. Ошибка в любой
строке отменяет весь ввод. Статистика проверяется по SHA исходного мира, pack,
vanilla JAR, версии scanner и составу партии; устаревший отчёт не отображается.
Отчёт и разреженный индекс исходного мира проверяются также по checksum тела.
Это защита от случайного повреждения/редактирования кэша, не криптографическая
подпись. При ошибке индекса удалить только `build/manual-review/source-candidates.json.gz`
и повторить чтение исходного архива. Известные многоблочные source patterns никогда
не заменяются одиночными carrier cells по сходству модели; reverse visual fallback
используется только при отсутствии скомпилированного source pattern.

## Команды без JAR/Gradle

Python 3 + numpy + Pillow. При необходимости `BLOODBORNE_VANILLA_JAR` указывает на
локальный Minecraft client JAR 1.20.1 для vanilla parents/textures.

```powershell
python -B -X utf8 tools/manual_review.py init
python -B -X utf8 tools/manual_review_world.py --batch 1
python -B -X utf8 tools/manual_review.py catalog --batch 1
python -B -X utf8 tools/test_manual_review.py
python -B -X utf8 tools/test_manual_review_render.py
python -B -X utf8 tools/test_manual_review_world.py
# Только после ответа пользователя:
python -B -X utf8 tools/manual_review.py decide --decisions build/manual-review/user-decisions.txt
```

JAR не собирается при разметке/перегенерации каталога. Сборка накопительная после
20–30 подтверждённых и реализованных families; узкие проверки выполняются отдельно.
Automatic spatial discovery, массовое преобразование оставшихся 251 families,
полная конвертация мира и каталог UNASSIGNED отложены. UNASSIGNED создаётся только
после ручного просмотра всех существующих кандидатов.
