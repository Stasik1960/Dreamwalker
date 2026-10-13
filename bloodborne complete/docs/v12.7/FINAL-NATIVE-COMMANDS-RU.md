# Финальные команды нативной проверки

Это план выполнения после окончательного build19/tests16 и следующего
замороженного checkpoint. Промежуточный JAR `34c2f13c…` и tests15 не принимаются
за финальные доказательства после правки WorldEdit. Команды здесь не запускались.
Все процессы Java запускает родительский исполнитель последовательно через
`Guard.ps1 -Mode Operation`; Python-обёртка журнала — `tools/run_v124_stage.py`.

Используется проверенный чистый шаблон
`build/runtime-server-v127-native-flat2/isolated-smoke-world`. Его 128 слоёв
начинаются с Y=-64 и заканчиваются Y=63: цели Y=64 свободны. Галерея не создаётся.
Во всех примерах финальный JAR имеет имя
`build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7-final.jar`, но SHA читается
заново после окончательной сборки. Папки `final19-*` обязаны отсутствовать.

После окончательной сборки отдельно собрать QA и подготовить чистую копию:

```text
python tools/build_v127_qa_addon.py --production-jar build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7-final.jar --output build/v127-final19-qa
python tools/prepare_v127_native_inputs.py --production-jar build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7-final.jar --fixture-source build/runtime-server-v127-native-flat2/isolated-smoke-world --output build/v127-final19-capture-input
python tools/run_v11_client.py --jar build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7-final.jar --fixture build/v127-final19-capture-input/world --qa-addon build/libs/dreamwalker-v127-native-functions-qa-1.2.7.jar --capture-input build/v127-final19-capture-input/client-capture-input.json --run-name v127-final19-capture --report build/v127-final19-client-launch.json --quick-play --launch --heap-gb 4
```

Собственный отчёт движка:
`build/runtime-client-v127-final19-capture/v127-native-client-qa.json` должен
иметь `PASS_ENGINE_CAPTURE`, текущий SHA и существующие PNG с правильными SHA.
Состояние `LAUNCHED_AWAITING_GAME_VERIFICATION` у launcher-отчёта не является PASS.
Пиксели отдельно осматриваются и фиксируются текущим SHA в pixel-review.

После завершения клиента подготовить и открыть сохранённый мир для проверки:

```text
python tools/prepare_v127_native_inputs.py --production-jar build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7-final.jar --fixture-source build/runtime-client-v127-final19-capture/saves/prototype-fixture --previous-evidence build/runtime-client-v127-final19-capture/v127-native-client-qa.json --output build/v127-final19-reopen-input
python tools/run_v11_client.py --jar build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7-final.jar --fixture build/v127-final19-reopen-input/world --qa-addon build/libs/dreamwalker-v127-native-functions-qa-1.2.7.jar --capture-input build/v127-final19-reopen-input/client-capture-input.json --run-name v127-final19-reopen --report build/v127-final19-client-reopen-launch.json --quick-play --launch --heap-gb 4
```

Требуется `PASS_ENGINE_REOPEN` в
`build/runtime-client-v127-final19-reopen/v127-native-client-qa.json`. Сохранённый
мир клиента находится в `saves/prototype-fixture`, а не `isolated-smoke-world`.

Чистый production-сервер повторно открывает эту копию без QA/converter:

```text
python tools/run_v11_server.py --jar build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7-final.jar --world-copy build/runtime-client-v127-final19-reopen/saves/prototype-fixture --accepted-eula-file build/runtime-server-v126-flatgallery-author-2/eula.txt --run-name v127-final19-production-reopen --report build/v127-final19-production-server.json --hold-seconds 5 --startup-timeout 600 --shutdown-timeout 120
```

Требуется настоящий `PASS`, `world_startup=PASS`, exit0 и `extraMods=[]`.
Серверная сохранённая копия —
`build/runtime-server-v127-final19-production-reopen/isolated-smoke-world`.

Конвертер исходной схемы 2975 запускается отдельной последовательностью
[NATIVE-CONVERTER-FIXTURE-RU.md](NATIVE-CONVERTER-FIXTURE-RU.md), с новым author,
`--fixture-only`, принятым EULA и явным `--conversion-launch` при production reopen.
Полная исходная карта по этим командам не открывается.

При final acceptance обязательно указывать
`--runtime-plan build/v127-runtime-conversion-final2` и
`--source-plan build/v127-full-conversion-first.sqlite`. То же явное значение
runtime-plan передавать упаковщику: его старый default `final` уже не актуален.
В release ZIP каталог нормализуется к имени `v127-runtime-conversion`.

GameTests JSON связывает точный финальный sourceCommit и XML; статические/dev
отчёты без поля JAR SHA связывает `PASS_FINAL_FROZEN_PROOF_BINDINGS` с точным SHA
каждого файла. Клиент/production-сервер/конвертер/resource/pixel отчёты содержат
собственный финальный JAR SHA. `worldedit-tests15-provisional.json` специально
не имеет финального PASS и не подходит для приёмки. Итоговый числовой файл —
`final-counts.json`; размещение всех предметов подтверждает финальный
`build/v127-manual-placement.json`, а не старый отчёт.
