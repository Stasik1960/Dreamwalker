# Сборка и работа с исходниками

## Получить репозиторий

Для крупных файлов нужен Git LFS. Выполните из терминала:

```sh
git lfs install
git clone https://github.com/Stasik1960/Dreamwalker.git
cd Dreamwalker
git lfs pull
```

Если checkout уже существует, сначала сохраните свои изменения и проверьте ветку через `git status`.
Repair-ветки Bloodborne содержат отдельные эксперименты: их результаты не следует приписывать `main`.

## Инструменты

| Проекты | JDK для сборки | Gradle | Примечание |
| --- | --- | --- | --- |
| RP-Chat, RP-Chat-UI, MC-Pool, DW_Languages, DW_Magic_Connect | 17 | 8.10.2, установленный отдельно | Версия используется в CI; wrapper в этих проектах отсутствует |
| Model-Props | 17 | Wrapper 8.6 | Содержит собственный wrapper и собирается независимо |
| Bloodborne-Blocks | 17 | Wrapper 8.8 | Для проверок нужен Python 3.11+ и `tools/requirements-ci.txt` |
| Danny-AOT-Backport и вложенная Player Animation Library | 21 | Wrapper 8.12.1 | Выходные классы совместимы с Java 17 |

Общая игровая платформа — Minecraft 1.20.1 / Fabric, mappings — Yarn 1.20.1+build.10.
Точные зависимости закреплены в `build.gradle`, `gradle.properties` и `fabric.mod.json` каждого проекта.
Укажите нужный JDK в `JAVA_HOME`; не используйте пути к Java с чужого компьютера.

## Обычные моды

Из корня репозитория, с Gradle 8.10.2 в `PATH`:

```sh
gradle -p RP-Chat build
gradle -p RP-Chat-UI build
gradle -p MC-Pool build
gradle -p DW_Languages build
gradle -p DW_Magic_Connect build
```

DW Languages и DW Magic Connect подключают соседний RP-Chat через composite build.
Сохраняйте расположение этих папок. Результат каждого проекта — `build/libs/`;
для игры выбирайте обычный JAR, без `-sources` и `-dev`.

Model Props использует собственный wrapper. Из `Model-Props`, PowerShell:

```powershell
.\gradlew.bat clean check build --no-daemon
```

В Linux/macOS используйте `chmod +x gradlew && ./gradlew clean check build --no-daemon`.

## Bloodborne

Из `Bloodborne-Blocks`, PowerShell:

```powershell
python -m pip install -r tools/requirements-ci.txt
.\gradlew.bat check build logicalGameTest checkReleaseVersion --max-workers=1
```

В Linux/macOS используйте `bash ./gradlew` вместо `.\gradlew.bat`.
Полные команды и границы проверки: [REPRODUCE.md](Bloodborne-Blocks/docs/release/REPRODUCE.md).
Сборка JAR не конвертирует карту и не закрывает [игровые gates](Bloodborne-Blocks/docs/RELEASE-STATUS.md).

## Danny’s AoT

Из `Danny-AOT-Backport`, с JDK 21:

```powershell
.\player-animation-backport\gradlew.bat -p player-animation-backport build
.\gradlew.bat build
```

В Linux/macOS: `bash player-animation-backport/gradlew -p player-animation-backport build`, затем `bash gradlew build`.
[Зависимости, авторство и проверки](Danny-AOT-Backport/README.md).

## Проверка изменений

Используйте существующие задачи проекта; не обновляйте платформу или библиотеки ради правки документации.
Для изменений кода выполните подходящие `check`/`build`; для игрового поведения дополнительно проверьте клиент или сервер.
Успешная компиляция и офлайн-тесты не заменяют игровую проверку.
Правки README требуют проверки ссылок, версий и команд, но не пересборки модов.
