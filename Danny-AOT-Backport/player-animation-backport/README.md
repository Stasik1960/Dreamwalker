# Player Animation Library — Fabric 1.20.1 backport

Исходники ZigyTheBird из ветки 1.21.1, commit
`10e019f89fa25d0cd6f50fb8768586a969106911`, адаптированы к Fabric 1.20.1 / Java 17.
Авторство и MIT-лицензия сохранены в `LICENSE`.

Сборка из этой папки: `gradlew.bat build` (Windows) или `./gradlew build`
(Linux/macOS), с JAVA_HOME на JDK 21. Выходной JAR:
`build/libs/PlayerAnimationLib-1.1.6-backport.1+mc.1.20.1.jar`.
Затем соберите родительский проект AoT. Основной JAR включает эту библиотеку.

## Описание оригинального проекта

A library that allows mods to animate the player, in a way that doesn't conflict with other mods. (and much more!)  
This mod is a library for mod developers, and does not do anything on it's own.

Here are SOME of the features:
* You can load animations from Blender and Blockbench JSON files. (GeckoLib and Bedrock format)
* Pretty good molang support!
* Effect keyframes support like particle keyframes.
* Ability to add custom pivot points (for example a hip bone that rotates everything except the legs around the hip)
* Ability to add custom bones the location of which you can get in order to add custom particles.

# Important links
**Documentation:** https://docs.zigythebird.com/  
**Modrinth:** https://modrinth.com/mod/player-animation-library  
**CurseForge:** https://www.curseforge.com/minecraft/mc-mods/player-animation-library  
**Maven Repository:** https://repo.redlance.org/#/public/com/zigythebird/playeranim
