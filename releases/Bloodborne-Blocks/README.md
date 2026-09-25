# Latest beta

[2.1.0-beta.1 — JAR, gallery, checksums and verification](2.1.0-beta.1/README.md). Graphical acceptance pending; old modular city requires separate conversion review.

# Bloodborne Architecture — cumulative Агония, 2026-09-25

- [Текущий JAR](bloodborne-blocks-2.1.0-alpha.1-agony-20260925-mc1.20.1.jar)
- [Новая галерея](production-agony-gallery-ready-20260924.zip)
- [Изменения и ограничения](../../Bloodborne-Blocks/docs/agony-patch/RESULTS.md)
- [Проверки и SHA-256](../../Bloodborne-Blocks/docs/agony-patch/release-proof.json)

49 production предметов, 73 образца; 37 unrelated families сохранены без
изменения geometry/gameplay data. Fabric **1.20.1, Java 17**. Замените прошлый
JAR, не устанавливайте обе версии одновременно. Из ZIP извлеките папку
`production-agony-gallery-ready-20260924` в `saves`, с `level.dat` прямо внутри.
Суффикс галереи — дата начала этого прохода; это новый cumulative QA save.

Проверяйте in-wall окна (проём должен быть подготовлен), фонари в руках статуй,
8 направлений/3 места скамьи и пять секций лестницы с отдельной площадкой.
Переход на площадку — короткий прыжок, не бесшовная дорожка. Настроенные
NBT/именованные фонари не принимаются в крепление и не расходуются.
Исходный мир не изменён. Старые QA saves — только со своими историческими JAR.
Итоговая графическая приёмка человеком остаётся необходимой.

## История: functional restoration, 2026-09-24

- [Текущий JAR](bloodborne-blocks-2.1.0-alpha.1-functional-restoration-20260924-mc1.20.1.jar)
- [Новая галерея](production-functional-gallery-20260924.zip)
- [Отчёт](../../Bloodborne-Blocks/docs/PRODUCTION-RESTORATION.md),
  [coverage / проверки / SHA-256](../../Bloodborne-Blocks/docs/production-restoration-checks/README.md)

56 production объектов; 64 образца: canonical + open/closed + BASE/ALT proof.
Восстановлены все 21 обязательных functional families. C003 заменён четырьмя
семантическими families: независимые бочки, книги, мешки и багаж.

Fabric **1.20.1 / Java 17**. Замените предыдущий JAR, не устанавливайте обе версии.
Извлеките папку `production-functional-gallery-20260924` из ZIP в `saves`;
`level.dat` лежит непосредственно в этой папке. Это QA-галерея, не город.
Оригинальный source world не изменён; старые QA worlds остаются одноразовыми.
Графическая приёмка новой палитры человеком ещё необходима.

## История: production palette cleanup (до восстановления)

- [Новый JAR](bloodborne-blocks-2.1.0-alpha.1-production-palette-20260924-mc1.20.1.jar)
  — 850 307 байт; [SHA-256](bloodborne-blocks-2.1.0-alpha.1-production-palette-20260924-mc1.20.1.jar.sha256).
- [Новая production-галерея](production-palette-gallery-ready-20260924.zip)
  — 31 861 байт, 33 объекта; [SHA-256](production-palette-gallery-ready-20260924.zip.sha256).
- [Аудит и ограничения](../../Bloodborne-Blocks/docs/PRODUCTION-PALETTE.md),
  [manifest](../../Bloodborne-Blocks/docs/production-logical-palette.json),
  [проверки](../../Bloodborne-Blocks/docs/production-checks/README.md).

JAR SHA-256: `7c2750b84d7d0230eb88418f32c114708b051513f01aa762b487a3c06a4aac45`.
Галерея SHA-256: `c81a4d95084a25875b3a88233f33ef123dd5115cc94d89498fd8d2ae8008fa1c`.

Fabric 1.20.1 / Java 17. Замените предыдущий JAR, не ставьте оба одновременно.
Извлеките папку `production-palette-gallery-ready-20260924` из ZIP в `saves`.
Это новая чистая QA-галерея, **не** конвертированный город. Штатный LevelStorage
распознал мир; графический клиент для визуальной приёмки не запускался.
`check build logicalGameTest`: BUILD SUCCESSFUL, 19 GameTests и 91 Python tests.
Предыдущие модифицированные миры/галереи несовместимы с удалёнными registry IDs;
используйте их только со своими историческими JAR. Оригинальная vanilla карта сохранена.

## История: batch-02 QA2 checkpoint

- [Новый тестовый JAR QA2](bloodborne-blocks-2.1.0-alpha.1-batch-02-qa2-20260923-mc1.20.1.jar).
- [Новая QA-галерея](reviewed-batch-02-gallery-qa2.zip): 104 образца на белом
  бетоне, включая шесть вариантов целого дерева × четыре ориентации.
- [Изменения, проверки и ограничения](../../Bloodborne-Blocks/docs/REVIEWED-BATCH-02-QA2.md).

JAR: 95 353 268 байт, SHA-256
`b7ef8d69d31efd10a9e857caecfc80d7abf6aab8f007659d6774e6d3abef7a13`.
`check build logicalGameTest qa2Checkpoint`: **BUILD SUCCESSFUL**;
17 GameTests, 1008 orientation checks и сверка 191 919 ресурсов — PASS.

Цельное дерево `o_c001`, collision только по стволу; команда
`/bloodborne debug target` (alias `/bloodborne debug`) с копируемой диагностикой.
Архив содержит папку мира с `level.dat` непосредственно внутри: извлеките её
в `saves`. Замените предыдущий JAR, не держите две версии одновременно.
Это промежуточный QA checkpoint, не конвертация города. Ждём отдельные баг-репорты
по объектам с выводом debug-команды. Старые файлы ниже сохранены.

## Исторический reviewed batch-02 checkpoint (до QA2)

[Тестовый JAR batch-02](bloodborne-blocks-2.1.0-alpha.1-batch-02-20260922-mc1.20.1.jar)
— 95 296 406 байт, SHA-256
`51041bb4c6148206d0fdb18146fb5f05e82caa76b899a7c4c1f54194a0d8a6f2`.
[Изменения, ограничения, команды и визуальный чек-лист](../../Bloodborne-Blocks/docs/REVIEWED-BATCH-02-CHECKPOINT.md).

20 новых подтверждённых logical families + 5 POC; orientation QA 912 PASS,
11 GameTests PASS, `check build logicalGameTest`: BUILD SUCCESSFUL.
Это тестовый checkpoint, не новая конвертация опубликованного города.
Внутренняя версия всё ещё `2.1.0-alpha.1`: замените предыдущий JAR, не держите
оба одновременно. Клиентская визуальная приёмка остаётся за владельцем.
Синтетические test worlds не публикуются; их генераторы находятся в `tools/`.

## Исторический technical snapshot 2026-09-21

[Техническая памятка для аудита](../../Bloodborne-Blocks/docs/TECHNICAL-SNAPSHOT-2026-09-21.md) ·
[Исходники](../../Bloodborne-Blocks/README.md).

Текущая незавершённая реализация: 256 логических семейств, 1324 состояния/меша,
1631 правило миграции. Это snapshot для анализа, а не заявление об исправлении
всех 23 пользовательских примеров. При публикации не менялся код и не запускалась
новая сборка; ниже приложен существующий проверенный JAR предыдущего рабочего прогона.

| Материал | Размер, байт | SHA-256 |
|---|---:|---|
| [JAR snapshot](bloodborne-blocks-2.1.0-alpha.1-snapshot-20260921-mc1.20.1.jar) | 94 781 029 | `a3dd108912214b6fcc20185fc1990a1be6a6a6b96bb7c1116f333066a5dd6026` |
| [Полная конвертированная карта](ether-current-20260921-converted.zip) | 34 393 818 | `3e750192baf44464b120c087571e5f0176af480e6e011e96dd59248d8b6ab202` |

Контрольные суммы отдельно: [JAR](bloodborne-blocks-2.1.0-alpha.1-snapshot-20260921-mc1.20.1.jar.sha256),
[карта](ether-current-20260921-converted.zip.sha256).

У JAR внутри остаётся версия `2.1.0-alpha.1`; датированный filename отличает его
от прежнего опубликованного alpha. Не устанавливайте оба JAR одновременно.
Нужны Minecraft 1.20.1, Java 17, Fabric Loader 0.16.10+ и Fabric API
0.92.9+1.20.1, а для карты — также остальные моды исходной сборки.

Карта опубликована **полностью, без обезличивания**, по отдельному прямому
указанию владельца: включены данные игроков, RP Chat, других модов и настройки
мира. Это отдельный архив конвертированной копии, не исходный мир. Повторной
конвертации при публикации не было. ZIP перепакован из сохранённых 646 файлов;
его SHA-256 отличается от прежнего Desktop ZIP из-за упаковки, не изменения мира.

Актуальный [исходный мир Ether ДО замены блоков модом](../../Bloodborne-Blocks/reference-inputs/README.md)
хранится отдельно в `Bloodborne-Blocks/reference-inputs/source-world.zip` через LFS.
Это исправленный владельцем исходник; опубликованная здесь конвертированная карта
и её ledger не пересоздавались из этого нового входного архива.

Полный ledger последней конвертации находится в
`Bloodborne-Blocks/build/logical-world/ether-current-20260921-report.json`
через Git LFS. Обычный ZIP исходников GitHub может содержать только указатель:
скачайте данные с `git lfs pull` по инструкции в технической памятке.

---

# Historical release: Bloodborne Architecture 2.1.0-alpha.1

[Скачать JAR](bloodborne-blocks-2.1.0-alpha.1-mc1.20.1.jar) · [SHA-256](bloodborne-blocks-2.1.0-alpha.1-mc1.20.1.jar.sha256) · [Исходники](../../Bloodborne-Blocks/README.md) · [Полная матрица готовности](../../Bloodborne-Blocks/docs/LOGICAL-OBJECTS-STATUS.md).

Экспериментальный этап структурной нормализации существующего мода для
**Minecraft 1.20.1 / Fabric / Java 17**. Требуются Fabric Loader 0.16.10+ и
Fabric API 0.92.9+1.20.1. JAR устанавливается на сервер и клиенты; прежний JAR
того же мода нельзя оставлять рядом. Исходный Bloodborne resource pack и старый
`bloodborne_transparency_fix` должны быть отключены.

## Что опубликовано

- 76 логических объектов, 388 состояний и 546 точных правил миграции.
- Цельные модели и управляемые служебные части, свойства ориентации, соединения
  и открывания для реализованных семейств; прежние 503 legacy и 46 236 v2 ID сохранены.
- Исходники, генератор, таблица соответствий, конвертер копии мира, автоматические
  проверки и отчёты без координат мира.

## Ограничения и использование

Все 23 пользовательских примера **ещё не завершены**. В частности, остаются
составные ограждения, бордюры, столбы, отдельные архитектурные сборки и крепление
фонаря к статуе. Игровая проверка взаимодействий, освещения, выпадения предметов
и производительности не выполнялась: Minecraft не запускался.

Использовать только для проверки на отдельной копии мира и с резервной копией.
Установка JAR сама по себе не объединяет фрагменты старой карты. Новый
`tools/convert_logical_world.py` создаёт отдельную выходную копию; её нужно
проверить `tools/check_logical_world.py`. Команды и условия приведены в
[статусе проверки](../../Bloodborne-Blocks/docs/LOGICAL-OBJECTS-STATUS.md#reproducible-checks).
Первоначальную модульную миграцию повторять не нужно.

Карта Ether и отчёты с приватными координатами остаются локальными и в Git не
включены. Alpha не заменяет прежний выпуск
[2.0.1](bloodborne-blocks-2.0.1-mc1.20.1.jar) для основной карты.

## Фактические проверки артефакта

- Gradle 8.8 / Java 17: `BUILD SUCCESSFUL`, четыре Java data/registry-проверки.
  Выполнено `check build -x processResources` после полной побайтовой сверки
  подготовленных ресурсов; обычная чистая сборка использует `check build`.
- Проверены 96 170 моделей и 46 816 blockstate-файлов; отсутствующих файлов
  моделей/текстур и неразрешённых активных текстурных ссылок не найдено.
- Проверены ZIP, mixin/refmap, производственные классы и 190 143 ресурса в JAR.
- На копии мира — 24 375 преобразований; независимая проверка 10 009 чанков
  пройдена. Повторная конвертация дала 0 преобразований и идентичные 186 файлов.

Размер JAR: **93 588 297 байт**. SHA-256:
`939a472f3cbfc3bd3052a60655d5392cfda91d278015cf4971d43fe404fb41c4`.
