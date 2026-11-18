# DreamWalker NPC Bestiary

Аддон для Fabric 1.20.1 с двумя постановочными NPC.

| Актёр | ID | Модель и движения |
| --- | --- | --- |
| Топяной ткач | `dw_npc_bestiary:mire_weaver` | Шестиногий панцирный зверь со светлым споровым мешком. Щёлкает жвалами и бьёт передней лапой. |
| Сумеречный смотритель | `dw_npc_bestiary:vesper_warden` | Страж в маске с крыльями ночной бабочки и фонарём. Размахивает фонарём и делает выпад рукой. |

## Установка

1. Скачайте [JAR аддона](releases/dw-npc-bestiary-1.0.0.jar).
2. Положите его в `mods` клиента и сервера рядом с [DreamWalker-v22 NPC Patrols](https://github.com/Stasik1960/Dreamwalker/releases/tag/v22-bloodborne-npc-patrols.1), Fabric API 0.92.9+1.20.1 или новее и GeckoLib 4.x для 1.20.1. Нужны Java 17+ и Fabric Loader 0.16.10+.
3. Выберите яйцо на вкладке яиц призыва в Creative или вызовите актёра командой:

   ```mcfunction
   /summon dw_npc_bestiary:mire_weaver
   /summon dw_npc_bestiary:vesper_warden
   /give @s bloodborne_dw:bb_npc_tool
   ```

Актёры пассивны и появляются через яйца и команды. ПКМ переключает режимы «Жезла постановщика». В режиме «Особое действие NPC» кликните по актёру, затем по земле для движения или снова по актёру для постановочной атаки. Для маршрутов есть режим «Патруль». Звуки покоя по умолчанию выключены. Их можно включить жезлом в режиме «Включить idle-звуки».

## Интеграция и ассеты

В DreamWalker тип `RpMobEntity` реализует навигацию, анимации, позу после смерти и оживление. Жезл работает с этим типом, поэтому аддон регистрирует обоих актёров как `RpMobEntity`. Их GeckoLib-ресурсы и события звука находятся в пространстве имён `dw_npc_bestiary`. Миксины подают данные новых актёров в каталог и подключают две постановочные атаки каждого актёра.

Модели, текстуры, анимации и звуки созданы для аддона. Scape and Run: Parasites работает на Forge 1.12.2, а Grimoire of Gaia 4 для Minecraft 1.20.1 выпускается под Forge. Scape and Run: Parasites помечен как All Rights Reserved. У Grimoire of Gaia лицензия CC0 для кода в публичном репозитории исключает модели и текстуры. [Scape and Run: Parasites](https://www.curseforge.com/minecraft/mc-mods/scape-and-run-parasites) · [Файлы Grimoire of Gaia](https://www.curseforge.com/minecraft/mc-mods/grimoire-of-gaia/files/all?version=1.20.1) · [Лицензия Grimoire of Gaia](https://github.com/Silentine/GrimoireOfGaia/blob/master/README.md#license)

## Сборка

Скачайте `DreamWalker-v22-NPC-Patrols.jar` из релиза DreamWalker и передайте абсолютный путь к нему как свойство Gradle. SHA-256 проверенного JAR DreamWalker — `0fc440ce7981d712c50ef42c1920447c7f2a3b2c4bae9d8af9faa30bde6dc5d6`.

```powershell
.\gradlew.bat check build "-PdreamWalkerJar=C:\path\to\DreamWalker-v22-NPC-Patrols.jar"
```

Сборка на JDK 17 создаёт `build/libs/dw-npc-bestiary-1.0.0.jar`. На Linux или macOS используйте `./gradlew`. Для изменения ассетов используйте генераторы в `tools/`. Модели генерируются через Pillow, звуки — через FFmpeg с кодеком Vorbis. Готовые ассеты уже включены в исходники.
