# Bloodborne Architecture — Fabric 1.20.1

503 independent decorative blocks and their assets. Java 17; Fabric Loader >=0.16.10; Fabric API >=0.92.9+1.20.1. Namespace: `bloodborne_blocks`. The original Bloodborne resource pack must be disabled when playing the migrated city. See the Russian installation and migration reports in the release documentation.

## Версия 1.0.1 и установка

В репозиторий перенесены готовые исходники и ресурсы проверенного выпуска. Исправлен выбор 1 874 вариантов моделей: учитываются нижележащие ванильные варианты, которые использует Minecraft при загрузке исходного ресурспака. Все восемь Java-классов сохранены из 1.0.0 побайтно, ID и свойства блоков не менялись.

1. Скачайте [bloodborne-blocks-1.0.1-mc1.20.1.jar](../releases/Bloodborne-Blocks/bloodborne-blocks-1.0.1-mc1.20.1.jar) в папку `mods` Minecraft 1.20.1 Fabric на Java 17. Замените прежний JAR `bloodborne_blocks`, если он установлен.
2. Нужны Fabric Loader **0.16.10 или новее** и Fabric API **0.92.9+1.20.1 или новее для Minecraft 1.20.1**. С Sodium 0.5.11 используйте Indium 1.0.34.
3. Используйте уже конвертированный мир с блоками `bloodborne_blocks:*`: сам мод не заменяет ванильные блоки при открытии карты. В мультиплеере он нужен серверу и клиентам. Сохраните моды, добавляющие размещённую мебель и другие сторонние блоки.
4. Отключите исходный Bloodborne и прежний `bloodborne_transparency_fix`. Шейдеры для прозрачности не нужны.

SHA-256 готового JAR: `8f06380f9dbf460b11983f09671b16e142e68be8cb34e1dc6601cb18f0a1bf44`.

Результаты проверок и известные ограничения: [docs/VALIDATION-RU.md](docs/VALIDATION-RU.md).

## Build the mod

Generated resources are included: rebuilding the JAR does not require re-analyzing the pack or converting the world.

```sh
./gradlew build
```

On Windows use `gradlew.bat build`. The project pins Loom 1.6.12, Gradle 8.8, Yarn `1.20.1+build.10`, Loader 0.16.10 and Fabric API 0.92.9+1.20.1. Dependency downloads require Internet access. Build outputs are under `build/libs`.

The production classes were compiled and remapped with Java 17 in the migration pipeline. Version 1.0.1 repackages those byte-identical classes with corrected resources and version metadata; it passed the real Fabric client checks described above. The JAR in `../releases/Bloodborne-Blocks/` is that exact tested artifact, preserved without recompilation during this repository import. The Gradle task itself was not executed in the migration environment; it is the conventional project build entry point and produces `build/libs/bloodborne-blocks-1.0.1.jar`.

## Implementation

- `BloodborneBlocks`: reads generated definitions and registers all states/items.
- `ArchitectureBlock`: geometry, preserved native state properties, decorative placement and interactions.
- `GeneratedShape`: exact union on a coordinate grid, avoiding expensive repeated union simplification.
- `BloodborneClient`: render layers, native tint delegation, FRAPI model wrapping.
- `EmissiveModel`: full-bright companion pass through Fabric Renderer API. With Sodium 0.5.x keep Indium.

There are no vanilla model overrides and no block-entity replacement classes. Architectural blocks intentionally do not inherit random ticking, natural copper oxidation, redstone machine logic or inventories. See the validation report for limitations of conservative collision geometry and missing-source-texture repairs, including the approximate replacements for absent `window_7` and `window_8`.

## Assets

See [ASSET-NOTICE.md](ASSET-NOTICE.md). The original architectural artwork and Minecraft fallback resources retain their original ownership. [docs/asset-provenance.json](docs/asset-provenance.json) records per-texture provenance and hashes extracted from the migration manifest. No recoloring was performed.
