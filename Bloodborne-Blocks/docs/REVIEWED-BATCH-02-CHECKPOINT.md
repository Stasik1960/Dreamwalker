# Reviewed Catalog B batch-02 — build 2026-09-22, handoff 2026-09-23

Это реализация только пользовательских решений batch-02, а не новая discovery
и не массовая нормализация карты. Исходные мир и resource pack не изменены.
Тестовый JAR: `releases/Bloodborne-Blocks/bloodborne-blocks-2.1.0-alpha.1-batch-02-20260922-mc1.20.1.jar`
от корня репозитория. Удалите предыдущий JAR этого мода из тестовой сборки:
внутренняя версия остаётся `2.1.0-alpha.1`, одновременно устанавливать их нельзя.
Minecraft 1.20.1, Java 17, Fabric Loader 0.16.10+, Fabric API 0.92.9+1.20.1.
Проверять на копии/синтетическом мире, не production.

## Принятые объекты

| Review ID | Logical ID | Выбранные компоненты |
|---|---|---|
| C001 | `o_c001_a`, `o_c001_b` | `1+3+5` / `2+4+6` |
| C009 | `o_c009_a`, `o_c009_b` | `1+3+5` / `2+4+6` |
| C002 / C471 / C1680 / C1962 | `o_c002` / `o_c471` / `o_c1680` / `o_c1962` | `1+2` каждого |
| C003 / C008 | `o_c003` / `o_c008` | `1..6` каждого |
| C046 | `o_c046` | `1`; `2+3+4` CONTEXT |
| C474 | `o_c474` | `1..16` |
| C1979 | `o_c1979` | `2..10+12+13`; `1+11` CONTEXT |
| C028 / C282 / C561 / C618 / C654 / C1319 / C1491 | соответствующий `o_cxxx` | `1` каждого |

20 новых семейств после SPLIT, 0 rotationally symmetric, 20 с facing.
Всего Contract V2: 25 семейств, 228 состояний, 912 state/yaw checks.
Общий legacy+logical runtime больше: 276 logical definitions, 1516 mesh states.
Эти общие счётчики **не означают**, что все 276 уже подтверждены в Contract V2.

## Существенные решения и ограничения

- Один registry ID на family; yaw вращает mesh с UV, collision, selection и
  helpers вокруг общего canonical transform. Master не смещается от yaw.
  Wall placement использует clicked face; двери/ворота — направление игрока.
  Несимметричная POC-решётка получила facing + connection masks; старые north
  artwork/geometry сохранены. Остальные четыре POC сохранены по baseline hashes.
- C282: проблема найдена в исходном `dark_door_1`, south UV
  `[6.25,15.375,6.625,16]` берёт посторонний участок атласа. В derived mesh
  использован зеркальный UV корректной north-стороны. PNG и исходный ZIP
  не редактировались. C/D — ориентации того же artwork, не разные open/closed.
  `open`, `hinge`, `facing` относятся к одному объекту.
- C282 `placement_height=manual`: полный 3×3 panel, mesh начинается у основания.
  `source_height`: сохраняет исходную высоту artwork относительно старого carrier;
  collision/selection ограничены центральным 1×2 проходом, боковая стеклянная
  рама и пол не становятся owned cells. В открытом source-height режиме
  collision пустая, interaction root-only; выбор центрального прохода позволяет
  закрыть дверь. Это сознательная упрощённая physics для старой архитектуры.
  Pick→manual placement возвращает `manual`, а не устаревший carrier offset.
- C618: `lit=true/false`, свет 15/0, ПКМ переключает. Потухший artwork —
  `lantern_0`, сохранена также альтернативная lit-модель `lantern_1`.
- C654: один wall object; manual `surface` сдвигает plane на -0.1875 Z в
  canonical north до yaw, чтобы окно было у поверхности стены. Migration
  `source_depth` сохраняет fractional depth исходного artwork. Это явные
  model states одного transform contract, не скрытый post-render offset.
- C1491 остаётся целой single-model композицией. Нумерованный CONTEXT C046/C1979
  исключён из render, helper ownership, breaking и consumption. Обычный preview
  context иногда содержит другой независимо подтверждённый объект: тот может
  мигрировать своим rule, но не присоединяется к проверяемому объекту.
- Exact migration использует raw `minecraft:*` и фактические pack applications,
  а не предполагаемое значение carrier-facing. Weighted guards воспроизводят
  Minecraft 1.20.1 position seed, Java random и weighted model selection.
  Незнакомый weighted artwork не подменяется ближайшим известным.
- Сохранено 58 raw rules; 54 эффективны в `original-v2` после supersession
  четырёх POC cases fallbacks. 43 — reviewed rules. Reviewed сборка может
  вытеснить другой matcher только при **строгом включении source и touched sets**.
- 11 exact-pattern aliases, с доказательством одинаковых raw cells, world mesh
  и helper footprint: C009a→C001a, C009b→C001b, C561→C046; ещё два повтора C471
  внутри одного family. Registry IDs ручной палитры сохранены. При различии
  простых collision primitives alias сохраняет политику более раннего target;
  `identical_collision` записан в migration evidence, это не скрытое равенство.
- **Предел покрытия:** 1751 потенциальная weighted-art комбинация не имеет
  authored mesh в этом checkpoint (главным образом C003). Они fail-closed;
  это число комбинаций, НЕ число объектов/ошибок в городе. Для некоторых
  pack carriers нет artwork для всех четырёх source-yaw; такие matchers не
  выдумываются. Ручная установка всех 25 families работает в четырёх yaw.
  Все 36 сохранённых реальных review examples покрыты, но полнота города
  не проверялась и не заявляется.

## Проверка и воспроизведение

Из `Bloodborne-Blocks`, с JDK 17 и Python (numpy/Pillow как у исходных tools):

```powershell
.\gradlew.bat -I tools/verification-direct-resources.init.gradle -PbloodbornePython=python check build logicalGameTest
python -B -X utf8 -m unittest discover -s tools -p 'test_source_assembly*.py'
python -B -X utf8 -m unittest discover -s tools -p 'test_manual_review*.py'
python -B -X utf8 tools/verify_source_assembly_catalog.py
python -B -X utf8 tools/build_reviewed_gallery.py --output build/reviewed-batch-02-gallery-ready
python -B -X utf8 tools/verify_reviewed_checkpoint.py
```

Optional init-script убирает только копирование ~191k уже готовых resource files;
JAR и tests читают те же `src/main/resources`. Обычная wrapper-команда без `-I`
также поддерживается. Генератор галереи отказывается перезаписывать существующую
папку: для повтора укажите новое имя, не удаляйте мир, уже открытый пользователем.
Migration tests создают заново только свой disposable `build/reviewed-batch-02`.

`tools/build_reviewed_contracts.py` воспроизводит batch-компиляцию и вызывает
`compile_reviewed_migration.py` в конце; `source_review_decisions.py` защищает
authoritative историю. Не запускать compiler ради открытия галереи/каждой разметки.
Runtime ресурсов достаточно для сборки; тест реальных source examples требует
полные LFS-архивы из `reference-inputs`.

Доказательства: `reviewed-batch-02-authoring.json`, `reviewed-batch-02-evidence.json`,
`reviewed-batch-02-poc-baseline.json`, `reviewed-batch-02-migration-evidence.json`,
`reviewed-batch-02-source-examples.json`, `reviewed-batch-02-orientation.json`,
`reviewed-batch-02-gametest.xml`, `reviewed-batch-02-checkpoint-qa.json` в этой папке.
Отдельно проходят 44 Catalog A/B tests и portable verifier; совместимость
reviewed overlay не изменяет immutable snapshot/source signatures.
Серверные 11 GameTests включают
manual placement/pick/break, foreign cells, shared master, lamp, window,
door frame preservation, gate и four-direction railing. Отдельные Python checks
проверяют все 43 reviewed rules, идемпотентность и независимый ledger.

## Что визуально проверить в Minecraft

Скопируйте `build/reviewed-batch-02-gallery-ready` в `saves`. Это синтетическая creative
галерея из 100 образцов, не город. В `gallery-positions.json` есть координаты
и `/give` для каждого: X-группы — family, внутри группы четыре facing; следующий
ряд по Z. Можно использовать `/tp @s X Y Z` из списка.
Сравните с отдельным `build/reviewed-batch-02/migrated-copy` (raw→logical fixture).

1. Возьмите каждый объект через pick или `/give @s bloodborne_blocks:o_cxxx`;
   поставьте на свободной площадке во всех четырёх направлениях. Место master
   не должно «прыгать», artwork/outline должны поворачиваться согласованно.
2. C001/C009: две самостоятельные половины-дерева из каждого SPLIT; ветви
   не мешают ходьбе. C1491, наоборот, остаётся одной целой композицией.
3. C282: нет чужого участка атласа с обратной стороны; hinge/open корректны.
   Сравните полный manual panel и более узкую collision migration source_height;
   рамка не исчезает при открытии/разрушении. C618: ПКМ, artwork и свет 15/0.
4. C654 и настенный декор: поставьте на четыре грани стены; одинаковое положение
   относительно поверхности, без утопления или бокового сдвига. Галерея содержит
   свободные образцы, для свежей установки постройте собственную стену.
5. C474/C1979/статуи: простая collision у опор, без collision декоративных деталей.
   Pick любой owned части даёт один item; разрушение части убирает только объект,
   не CONTEXT и не соседнюю постройку. Проверьте решётку и ворота в разных yaw.

Клиентский визуальный осмотр в Minecraft агентом не выполнялся. Его результаты
нужны перед массовой конвертацией; batch-03 не применять без нового review.
