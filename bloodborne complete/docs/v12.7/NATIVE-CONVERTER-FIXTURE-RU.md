# Малый тест конвертера на исходной схеме 2975

Это последовательность команд, а не готовый игровой PASS. Оригинальный город
не открывается и не записывается. Родительский исполнитель запускает Java
последовательно, через Guard. Все команды выполняются в `bloodborne complete`.
После любого изменения final JAR тест готовится в новой отсутствующей папке.

В текущем устройстве принятый EULA подтверждён файлом
`build/runtime-server-v126-flatgallery-author-2/eula.txt`. В нём `eula=true`;
достаточно копировать именно этот файл, старый мир не требуется. На другом
устройстве использовать собственный уже принятый EULA и реальные зависимости.

```powershell
$taskJar = 'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7-final.jar'
$taskAuthor = 'build/libs/dreamwalker-v127-full-conversion-author-1.2.7.jar'
$taskApi = 'C:/Users/vakir/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/0.92.9+1.20.1/89c79de1c8e17f3d34fded3a2fe401e7e60a707d/fabric-api-0.92.9+1.20.1.jar'
$taskGecko = 'C:/Users/vakir/.gradle/caches/modules-2/files-2.1/software.bernie.geckolib/geckolib-fabric-1.20.1/4.4.9/8a9b951935224f32c1ae51ca349aa3dcecd8e8da/geckolib-fabric-1.20.1-4.4.9.jar'
$taskEula = 'build/runtime-server-v126-flatgallery-author-2/eula.txt'
```

Указанные API/GeckoLib версии и текущие SHA соответственно
`4056979ccaa1012354dfff14f39fdb085aefb3f1e0224ff45c3af41e4ffed5fd` и
`36d567615f7a81c265f96edd30e9534ed679d8da2a8774866f8ed09d9347e218`.
`JAVA_HOME17` должен указывать на Java 17; резерв — `JAVA_HOME`.

1. Python-подготовка официального capsule без запуска Java:

```text
python tools/prepare_v127_server_bootstrap.py --output build/v127-server-bootstrap
python tools/prepare_v127_conversion_fixture.py --original-level-template --output build/v127-original-level-fixture-final --production-jar build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7-final.jar
```

2. После финального combined JAR отдельно собрать author. Это уже Java/Gradle;
не выполнять одновременно с другими нативными проверками:

```text
python tools/build_v127_conversion_addon.py --production-jar build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7-final.jar --output build/v127-conversion-author-final
```

3. Нативный author в малом мире. В PowerShell ранее определены `$task*`:

```powershell
python tools/run_v127_full_conversion.py --derived-world build/v127-original-level-fixture-final/world --runtime-plan build/v127-original-level-fixture-final/runtime-plan --runtime-directory build/v127-original-level-author-final --production-jar $taskJar --author-jar $taskAuthor --fabric-api $taskApi --geckolib $taskGecko --server-bootstrap build/v127-server-bootstrap --server-launcher build/v127-server-bootstrap/fabric-server-launch.jar --accepted-eula-file $taskEula --fixture-only --heap-gb 4 --timeout-hours 1
```

Требуемый результат `launch-report.json` —
`PASS_RUNTIME_CONVERTED_REOPEN_REQUIRED`. Такой статус ещё не заменяет
чистого повторного открытия и проверки сохранённого содержания.

4. Копия этого маленького мира открывается только production JAR; author,
QA и исходный ресурспак отсутствуют. Исходная тестовая папка не меняется:

```powershell
python tools/run_v127_conversion_reopen.py --world-copy build/v127-original-level-fixture-final/world --conversion-launch build/v127-original-level-author-final/launch-report.json --runtime-directory build/v127-original-level-reopen-final --production-jar $taskJar --fabric-api $taskApi --geckolib $taskGecko --server-bootstrap build/v127-server-bootstrap --server-launcher build/v127-server-bootstrap/fabric-server-launch.jar --accepted-eula-file $taskEula --report build/v127-original-level-reopen-final.json --heap-gb 4 --startup-timeout 900 --shutdown-timeout 120 --hold-seconds 5
```

`--prepare-only` у этой команды проверяет/копирует входы без Java, но всё равно
требует уже законченный настоящий author. Подготовка не выдаётся за native PASS.

5. Проверка настоящих сохранений и повторного открытия без запуска Java:

```text
python tools/validate_v127_conversion_fixture.py --fixture-input build/v127-original-level-fixture-final/fixture-input.json --conversion-launch build/v127-original-level-author-final/launch-report.json --reopen-launch build/v127-original-level-reopen-final.json --production-jar build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7-final.jar --output build/v127-original-level-fixture-final-validation.json
```

Итоговый PASS требует обновление исходной схемы 2975 до 3465 или выше,
точные UUID/состояния, RP payload, физическое наслаивание владельцев между
собой и с компактной палитрой, сохранение shard и восстановление правил игры.
Проверка полного города, физической мыши/клавиатуры и GUI Blockbench остаётся
отдельной; этим маленьким тестом их PASS не создаётся.
