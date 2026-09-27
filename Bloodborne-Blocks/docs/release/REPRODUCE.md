# Воспроизведение проверок и границы QA

Команды ниже выполняются из `Bloodborne-Blocks`. Нужны Java 17, wrapper Gradle
8.8, Python 3.11+ и зависимости из `tools/requirements-ci.txt` (NumPy и Pillow).
Retirement suites используют NumPy и реальный Anvil/NBT reader. Зависимости Minecraft/Fabric/Yarn/Loom
не изменены. Git LFS-входы должны быть гидратированы, а не оставаться pointer-файлами.

```powershell
java -version
.\gradlew.bat --version
python -m pip install -r tools/requirements-ci.txt
python -c "import sys,numpy,PIL; print(sys.version); print(numpy.__version__); print(PIL.__version__)"
.\gradlew.bat check build logicalGameTest checkReleaseVersion --max-workers=1
python -B -X utf8 -m unittest discover -s tools -p 'test_release*.py'
python -B -X utf8 -m unittest discover -s tools -p 'test_composite_retirement*.py'
python -B -X utf8 tools/release_gates.py docs/release/status.json --evidence-root ..
python -B -X utf8 tools/release_gates.py docs/release/status.json --evidence-root .. --require-ready
```

Последняя команда **должна завершиться кодом 1** для текущего BLOCKED/FAIL отчёта.
Корректная JSON-схема не является разрешением выпуска. Положительные unit fixtures
синтетические: они проверяют валидатор, не являются доказательствами игровых запусков.

Исторические beta.3 команды с абсолютными Java/Python путями,
`--offline --no-daemon`, `verification-direct-resources.init.gradle`, exit code
и длительностью сохранены в:

- [полный check/build/GameTest/package](evidence/main-checks/check-build-gametest.json);
- [финальные release suites/package](evidence/main-checks/release-checks.json).

Исторический rc.1 запуск до intentional retirement, с NumPy 1.26.4 / Pillow 10.4.0 (локально Python 3.12.14):
[команда и exit code](evidence/rc1/check-build-gametest.json),
[лог](evidence/rc1/check-build-gametest.log), [package](evidence/rc1/package.json).
Он прошёл 202 Python tests и 37/37 GameTests. CI отдельно использует Python 3.11.

Новый локальный запуск после controlled retirement: 219 Python tests / 38 запусков,
37/37 GameTests и check/build/version/package PASS —
[команда](evidence/retirement-rc1/verification/check-build-gametest.json),
[лог](evidence/retirement-rc1/verification/check-build-gametest.log).
Полная конвертация отдельно получила FAIL, см. [диагностику](FULL-CITY-DIAGNOSTIC.md).

Init script — существующая оптимизация копирования ресурсов. Полное сравнение
13 969 source resources с remapped JAR подтверждено package check. 25 production
Java sources проверены по inventory в sources JAR; Java sources ремаппятся Loom,
поэтому их текст не сравнивается с Yarn source побайтно.

Для воспроизведения только исторического beta.3 read-only census доступны:

```powershell
python -B -X utf8 tools/collect_release_baseline.py --repair-checkout '<path-to-repair-checkout>' --junit build/release-audit/TEST-logical-gametest.xml
```

Collector содержит beta.3 имена JAR: его нельзя запускать как сборщик текущего
rc.1 evidence. Он читает source/artifacts и записывает evidence; world conversion не
выполняет. `--junit` должен указывать на XML нужного свежего запуска, а не на
исторический tracked XML. После нового запуска/изменения исходников необходимо
пересобрать связанные gate reports/status с новыми хэшами; прежний `PASS` не переносится.
Параметр `--repair-checkout` нужен для сравнения с отдельной repair-линией:
подставьте путь к её локальному checkout. Source/resource fingerprints этого
снимка фиксируют байты проверенной Windows-копии; изменение окончаний строк при
checkout также меняет эти хэши и требует нового снимка для новой сборки.

Linux CI использует `bash ./gradlew` (executable bit у wrapper отсутствует),
Java 17, Python 3.11, NumPy 1.26.4 и Pillow 10.4.0. Workflow
`.github/workflows/bloodborne-blocks.yml` скачивает только относящиеся к Bloodborne
LFS-входы и полную Git-историю для исторической базы grid-check,
выполняет check/build/GameTests/package/version и пишет SHA двух
проверенных JAR. Логи выгружаются и при ошибке; бинарники — только после успеха.
Сохранённое evidence относится к локальным запускам до публикации audit-коммита.
Результаты последующих GitHub Actions runs проверяются отдельно по SHA коммита.

## Следующий обязательный порядок

Согласованное удаление 23 неизвестных ID уже выполнено только в новой копии.
Команды, точные 33 клетки, immutable input hash и independent verifier — в
[RETIREMENT.md](RETIREMENT.md). Старый `--recover-city` не применяется.
Свежие проверки и оставшиеся ограничения — в [status.json](status.json);
старый GitHub Actions run не проверяет новые retirement tools.

1. Проверить совместимость выбранного rc.1 runtime baseline `b086e8892`.
   Локальные repair-изменения не сливаются автоматически. Восстановить/доказать
   старые registry IDs, chunk NBT и ItemStack; повторить старые compatibility tests.
2. На read-only копии MODDED-входа получить полный owner graph, физические и
   interaction footprints, foreign/unknown/helper census и fail-closed whole-owner
   transaction plan. Main `--full-grid` этого условия не выполняет.
3. Пользователь разрешил конвертацию тестовой копии после read-only audit и
   выполнения compatibility/whole-owner условий. Проверить результат независимым verifier; второй проход
   должен давать ноль изменений и одинаковые file manifests. Player data и данные
   других модов должны оставаться побайтно неизменными. Исходный архив не заменяется;
   rollback — возврат из неизменного исходника в другую тестовую копию.
4. Только после этих gates подготовить изолированные dedicated/client instances
   для выбранного JAR и проверенной копии города. Пары fresh/save/stop/restart
   должны иметь разные run IDs/логи и сравнение persistent state после restart.
   Существующие GameTests не заменяют этот запуск. Runtime harness/утверждённой
   конфигурации production-modpack сейчас нет, поэтому завершённой runtime-команды
   или лога для этого сценария нет.
5. В реальном клиенте проверить fresh start, reload, restart, placement/break/use
   дверей, окон, лестниц, сидений и attachments; пересечь chunk boundaries и
   проверить старый мир. Зафиксировать точные координаты, вход/выход, run IDs,
   логи и PNG screenshots. Обязательны RAM/startup/reload/FPS/TPS на одной
   конфигурации до/после. PNG evidence проверяется структурно, визуальная оценка
   требует человека/интерактивной сессии и не выводится из наличия файла.
6. Проверить весь evidence и только затем `--require-ready`. Пользователь разрешил
   release/merge только при всех обязательных PASS; этот workflow их не делает.

Это план непроведённых проверок. Пустые `plannedCommands` у runtime gates в JSON
означают отсутствие готовой совместимой среды/конвертера, а не успешный пропуск.
