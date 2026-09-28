# Bloodborne Architecture

Архитектурные блоки, декор и инструменты подготовки карты Bloodborne для
**Minecraft 1.20.1 / Fabric / Java 17**. Рабочий checkpoint: **2.1.0-rc.3**.

[Кандидат: JAR и карта](../releases/Bloodborne-Blocks/README.md) · [Статус проверок](docs/RELEASE-STATUS.md) · [Все проекты](../README.md)

**Новое задание для исполнителя (29.09.2026):** [выбор лучшей исторической основы, объединение цельных моделей и комплект Blockbench](docs/whole-models-handoff/TASK.md). Ниже описан ранее опубликованный checkpoint rc.3.

## Текущее состояние

rc.3 продолжает опубликованный rc.2 и сохраняет принятые repair-исправления,
каталог/GUI и физику 2 914 compatibility-блоков. В новой полной копии города
применены 989 атомарных групп (4 771 изменённый root); нецелевые клетки/NBT
и все 170 файлов вне terrain сохранены. Второй проход побайтно идентичен.

Независимый census охватывает 36 795 кандидатов: 29 194 уже корректны,
3 148 восстановлены, 4 453 остаются известными нерешёнными случаями.
Это **checkpoint для проверки на отдельной копии**, без `RELEASE_READY`:
полнота применения и настоящая клиентская приёмка ещё не подтверждены.
[Таблица, реальные координаты и ограничения](docs/complete-accepted-repair/REPORT.md).

## Установка для теста

1. Подготовьте отдельную копию сборки: Minecraft 1.20.1, Java 17, Fabric Loader 0.16.10+ и Fabric API 0.92.9+1.20.1 или совместимую более новую версию для 1.20.1.
2. Получите **rc.3 JAR** и соответствующую **полную карту rc.3 checkpoint** через [каталог](../releases/Bloodborne-Blocks/README.md). Сначала сделайте backup старого мира.
3. Установите одинаковый JAR на клиент и сервер, заменив прежний Bloodborne JAR.
4. Отключите исходный Bloodborne resource pack и старый `bloodborne_transparency_fix`: ресурсы уже входят в мод.
5. Распакуйте мир так, чтобы `level.dat` находился непосредственно в папке сохранения. Для сервера укажите эту папку через `level-name`.

Замена одного JAR не является миграцией старой карты. Старые миры и архивные галереи
используйте со своими сборками. [Миграция и rollback rc.3](docs/complete-accepted-repair/MIGRATION.md).

## Разработка и документация

JDK 17, Gradle wrapper 8.8, Python 3.11+ и зависимости из `tools/requirements-ci.txt`.
Из этой папки в PowerShell:

```powershell
python -m pip install -r tools/requirements-ci.txt
.\gradlew.bat check build logicalGameTest checkReleaseVersion --max-workers=1
```

Linux/macOS: `bash ./gradlew` с теми же задачами. Результат — `build/libs/`.

| Документ | Назначение |
| --- | --- |
| [RELEASE-STATUS](docs/RELEASE-STATUS.md) | Актуальные gates и ограничения |
| [HANDOFF](HANDOFF.md) | Контекст для продолжения разработки |
| [Воспроизведение проверок](docs/complete-accepted-repair/REPRODUCE.md) | Команды, версии инструментов, границы QA |
| [Известные проблемы](docs/known-issues/README.md) | Зарегистрированные ограничения |
| [История](docs/history/README.md) | Предыдущие этапы и доказательства |

Исходные материалы, generated resources и авторство сохраняются; техническая конвертация не меняет права на исходные модели и текстуры.
