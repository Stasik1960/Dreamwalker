# Проверки и воспроизводимость

Проверки выполнены локально на Windows с Microsoft OpenJDK 17.0.15. Все игровые процессы остановлены; исходные ZIP/JAR не изменены. Gradle/Fabric build не запускался в этой задаче: игровой код проекта не менялся.

Публикационный пакет дополнительно проверен: [publication-validation.json](publication-validation.json). Повторены 10 resource audits с теми же hashes/counts, сверены SHA-256 всех 16 исходников, проверены 100-entry/4096-cell palette fixture и отказ для invalid index/неполного map audit. Пять негативных smoke-сценариев отвергнуты до Popen: выход пути, существующая fresh-папка, лишний JAR, смена режима и подменённый JAR. Новых игровых процессов эта проверка не запускала. Переносимый harness — `scripts/validate_tools.py` (--audit-root, --inputs, --work-root внутри audit-root); он требует уже записанные `portable-validation/resources/resource-analysis.json`.

## Фактически выполненные проверки

| Проверка | Результат | Данные |
| --- | --- | --- |
| SHA-256 всех 16 файлов | Сохранены для идентификации входов | [input-manifest](../input-manifest.json) |
| Python ZIP CRC32, duplicate/unsafe paths | PASS для всех ZIP/JAR | [resource-analysis](../resource-analysis.json), [map-analysis](../map-analysis.json) |
| Настоящий Minecraft 1.18.2 BlockModel parser | 734 / 1101 / 1112 / 1112 model JSON; 0 failures | [model probe](../minecraft-model-probe.json) |
| Java ZipFile v13 resource | FAIL стандартного открытия: invalid CEN header; модели прочитаны с явным IBM437 fallback | [model probe](../minecraft-model-probe.json) |
| Все terrain/entity/POI записи шести миров | 165372 terrain chunks, 166062 всех container records; 0 NBT/palette/structural errors | [map-analysis](../map-analysis.json) |
| CFR 0.152 | 357 class entries → 319 Java-файлов, включая объединённые nested classes | [jar-analysis](../jar-analysis.json); восстановленный код остаётся локально |
| Bloodborne без GeckoLib | FAIL загрузки: отсутствует GeckoLib | [result](no-gecko/result.json), [lifecycle](no-gecko/result-lifecycle.log) |
| Bloodborne + GeckoLib, копия v16 | FAIL до готовности: отсутствует client PoseStack на dedicated server | [result](gecko-v16/result.json), [lifecycle](gecko-v16/result-lifecycle.log) |
| v14, Forge 1.18.2 без Bloodborne | Done, save-all flush, stop; повторная загрузка PASS | [result](control-v14/result.json), [restart](control-v14/restart-result.json), [lifecycle](control-v14/result-lifecycle.log), [restart lifecycle](control-v14/restart-result-lifecycle.log) |
| VladraCastle, vanilla 1.20.1 | Done, save-all flush, stop; повторная загрузка PASS | [result](control-vladra120/result.json), [restart](control-vladra120/restart-result.json), [lifecycle](control-vladra120/result-lifecycle.log), [restart lifecycle](control-vladra120/restart-result-lifecycle.log) |

Готовность определялась по `Done`, сохранение — по фактическому выводу команды/остановки. Exit code 0 у ошибочного mod bootstrap не засчитывался как успех. В result JSON приведён исходный command с заменой абсолютных путей на placeholders. Lifecycle log — отобранные строки реального лога, не полный stdout.

Серверные запуски: loopback 127.0.0.1:25587, online-mode=true, 2 GiB heap, view/simulation distance 2, RCON/query/command blocks выключены, timeout 110 секунд с последующей остановкой/kill при необходимости. Smoke проверяет загружаемую область мира и save/restart, не проходимость всех 165372 чанков.

Установщик Forge 40.2.21 проверен по официальному SHA-1 `5507eebfe3ca62545419e7aa8ee7b5dc559f0e26`. Vanilla 1.20.1 server — по Mojang SHA-1 `84194a2f286ef7c14ed7ce0090dba59902951553`. Использовались официальные источники: [Forge](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.18.2.html), Mojang launcher metadata. Никакие клиентские shader/renderer-моды в dedicated tests не добавлялись.

## Переносимые скрипты

В `scripts/` включён собственный код выполненных проверок. Пути параметризованы для повтора; smoke дополнительно запрещает выход test-name за work-root и перезапись существующей fresh test-папки. Для --reuse проверяются сохранённый Minecraft/mod/Gecko scenario, точный набор JAR и их SHA-256; flags или JAR сменить незаметно нельзя. Он копирует мир, а не открывает исходник из Downloads. Бинарные Minecraft/Forge/моды, Java и декомпилированный код не распространяются здесь.

`audit_resources.py`: Python standard library, четыре packs и остальные ZIP/JAR с именами из manifest; дополнительно сравнивает наш reference-inputs/source-resource-pack.zip.

`audit_maps.py`: Python + NumPy, tracked `Bloodborne-Blocks/tools/world_io.py`; декодирует реальные palette indices, а не просто перечисляет palette entries. `valid` вычисляется по CRC/ошибкам/полноте всех шести архивов. Semantic delta — число координат terrain chunks с изменённым расположением nonair states; это не число изменённых клеток. Missing sections считаются implicit air и не входят в explicit cell volume.

`MinecraftModelProbe.java`: Java source mode и фактические Minecraft 1.18.2 SRG client classes + соответствующий Forge library classpath. Метод `BlockModel.m_111461_` выбран из проверенного runtime; нельзя заменить эти классы Fabric/Yarn 1.20.1 и считать тест тем же самым. Probe сначала проверяет default Java ZipFile, отдельно регистрирует ошибку и использует IBM437 только для чтения содержимого. Его успешный parse не является подтверждением полной texture bake или загрузки исходного v13 ZIP игрой.

`build_inventory.py`: читает локальное дерево CFR и результаты аудитов; извлекает ID/классы/строки, пути assets и animation names. Проверяет уникальность всех 98 entities, 121 items, 51 sounds и наличие всех saved bloodborne:* IDs в registry. Нужен приватный audit-root со своими subdirs resources/, maps-fast/, mod/src/.

Примеры повтора в PowerShell из корня Bloodborne-Blocks; пути заменить своими. Output/work-root должны быть отдельными scratch-папками. Эти команды — переносимые эквиваленты выполненных локальных invocations.

```powershell
$bbmcInputs = 'D:\BBMC-inputs'
$bbmcOutput = 'D:\BBMC-audit'
$bbmcScripts = '.\docs\external-bbmc-review-20261003\verification\scripts'
python -X utf8 "$bbmcScripts\audit_resources.py" --inputs "$bbmcInputs" --output "$bbmcOutput\resources" --project .
python -X utf8 "$bbmcScripts\audit_maps.py" --inputs "$bbmcInputs" --output "$bbmcOutput\maps-fast" --project .
python -X utf8 "$bbmcScripts\verify_bundle.py"
```

Model parser использовался через `java @model-probe.args`. Эквивалент source-mode запуска из того же корня проекта:

```powershell
& $bbmcJava --class-path '<MC_1182_CLIENT_SRG_JAR>;<FORGE_1182_LIBRARY_CLASSPATH>' "$bbmcScripts\MinecraftModelProbe.java" "$bbmcInputs\bbmc_v1_resource (2).zip" "$bbmcInputs\bbmc_v13_resource (1).zip" "$bbmcInputs\bbmc_v14_resource (1).zip" "$bbmcInputs\bbmc_v15_resource (1).zip"
```

Сначала задать `$bbmcJava` (Java 17, пример ниже) и заменить placeholders реальным version-matched classpath. Standalone Gson/строгий JsonParser этим тестом не является. Shader GLSL не компилировался. Аудиты выполнены Python 3.12.8, NumPy 2.5.3; их установка не входит в эти команды.

Примеры smoke после установки официального Forge server в отдельную scratch-папку и получения vanilla 1.20.1 server:

```powershell
$bbmcJava = 'D:\Java17\bin\java.exe'
$bbmcRuntime = 'D:\BBMC-smoke'
$bbmcForge = 'D:\Forge-1.18.2-40.2.21'
$bbmcVanilla = 'D:\vanilla-1.20.1-server.jar'
python "$bbmcScripts\smoke.py" --work-root "$bbmcRuntime" --java "$bbmcJava" --inputs "$bbmcInputs" --forge-install "$bbmcForge" no-gecko --no-gecko
python "$bbmcScripts\smoke.py" --work-root "$bbmcRuntime" --java "$bbmcJava" --inputs "$bbmcInputs" --forge-install "$bbmcForge" gecko-v16 --map "$bbmcInputs\bbmc_v16_map (1).zip"
python "$bbmcScripts\smoke.py" --work-root "$bbmcRuntime" --java "$bbmcJava" --inputs "$bbmcInputs" --forge-install "$bbmcForge" control-v14 --no-mod --map "$bbmcInputs\bbmc_v14_map.zip"
python "$bbmcScripts\smoke.py" --work-root "$bbmcRuntime" --java "$bbmcJava" --inputs "$bbmcInputs" --forge-install "$bbmcForge" control-v14 --no-mod --reuse
python "$bbmcScripts\smoke.py" --work-root "$bbmcRuntime" --java "$bbmcJava" --inputs "$bbmcInputs" --vanilla-server "$bbmcVanilla" control-vladra120 --vanilla-120 --map "$bbmcInputs\VladraCastle (1).zip"
python "$bbmcScripts\smoke.py" --work-root "$bbmcRuntime" --java "$bbmcJava" --inputs "$bbmcInputs" --vanilla-server "$bbmcVanilla" control-vladra120 --vanilla-120 --reuse
```

Скрипт записывает eula=true для своей локальной test-копии: использовать его только при принятии Minecraft EULA. Не запускать на production volume. Copy libraries использует hardlink при возможности и copy fallback между томами. Windows использовалась фактически; POSIX-ветка в переносимом скрипте отдельно не выполнялась.

Декомпиляция выполнена командой CFR вида `java -jar cfr-0.152.jar <Bloodborne-JAR> --outputdir <private-audit>/mod/src --silent true`. Это восстановленный bytecode text; исходные комментарии/build project отсутствуют, повторная компиляция чужого мода не проверена.

## Что остаётся для графической проверки

1. Отдельный локальный Forge 1.18.2 / 40.2.21 client-профиль с Bloodborne и GeckoLib, сначала без Oculus/Rubidium/Kappa; чистая копия v16, pack v15.
2. Подтвердить меню, открытие мира, resource reload и отсутствие missing-texture/model ошибок у реально используемых состояний. Проверить 15 unresolved target types по inventory.
3. Обойти галерею двери/ворота/люк/лестница/лифт/дерево/карета/люстра, проверить collision, opening/sync и save/reload.
4. Запись двух игроков/двух измерений, смерть/respawn/reconnect, неизвестная lamp area, forbidden target и частые пакеты. Dedicated test пока блокируется startup; ранее это не было выполнено.
5. Только после baseline подключить optional renderer/shader profile и сравнить FPS/внешний вид. Условия лицензий и аппаратный профиль записать отдельно.

Это будущий тест-план, а не список уже пройденных проверок. В нашей Fabric 1.20.1 сборке иностранные Forge JAR не устанавливались.
