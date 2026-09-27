# Bloodborne Architecture

Архитектурные блоки, декор и инструменты подготовки карты Bloodborne для
**Minecraft 1.20.1 / Fabric / Java 17**. Версия в `main`: **2.1.0-rc.1**.

[Кандидат: JAR и карта](../releases/Bloodborne-Blocks/README.md) · [Статус проверок](docs/RELEASE-STATUS.md) · [Все проекты](../README.md)

## Текущее состояние

rc.1 — кандидат для проверки, **не сертифицированный production-релиз**.
Карта города подготовлена из копии MODDED backup. Офлайн-конвертация и полный
census прошли; реальные загрузка/перезапуск выделенного сервера и визуальная
приёмка клиентом остаются неподтверждёнными.

По зафиксированной политике в копии удалены 33 клетки с 23 недоступными composite ID,
после чего выполнена конвертация. Исходный архив не менялся. Это не заявление
о восстановлении этих утраченных объектов: [отчёт](docs/release/evidence/rc1/retired-composites-world.json).

В текущей линии — 49 логических семейств и 2 914 совместимых блоков.
Repair TEST-сборки и whole-owner исправления находятся в отдельной repair-линии;
они не интегрированы в этот `main`. Исторические beta/repair результаты не заменяют проверок rc.1.

## Установка для теста

1. Подготовьте отдельную копию сборки: Minecraft 1.20.1, Java 17, Fabric Loader 0.16.10+ и Fabric API 0.92.9+1.20.1 или совместимую более новую версию для 1.20.1.
2. Получите **rc.1 JAR** и соответствующую **карту rc.1** через [каталог](../releases/Bloodborne-Blocks/README.md).
3. Установите одинаковый JAR на клиент и сервер, заменив прежний Bloodborne JAR.
4. Отключите исходный Bloodborne resource pack и старый `bloodborne_transparency_fix`: ресурсы уже входят в мод.
5. Распакуйте мир так, чтобы `level.dat` находился непосредственно в папке сохранения. Для сервера укажите эту папку через `level-name`.

Замена одного JAR не является миграцией старой карты. Старые миры и архивные галереи
используйте со своими сборками. [Границы миграции](docs/release/MIGRATION.md).

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
| [Воспроизведение проверок](docs/release/REPRODUCE.md) | Команды, версии инструментов, границы QA |
| [Известные проблемы](docs/known-issues/README.md) | Зарегистрированные ограничения |
| [История](docs/history/README.md) | Предыдущие этапы и доказательства |

Исходные материалы, generated resources и авторство сохраняются; техническая конвертация не меняет права на исходные модели и текстуры.
