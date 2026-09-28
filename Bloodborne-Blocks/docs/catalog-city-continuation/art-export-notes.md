# Current art reference export

Создайте новый ZIP после стабилизации runtime ресурсов:

```powershell
python tools/export_current_art_bundle.py build/current-art-reference.zip
```

Существующий файл не перезаписывается. Только если требуется сознательная замена,
добавьте `--force`. Экспортёр не запускает Gradle, конвертеры и не меняет игровые
ресурсы.

В ZIP входят все `assets/*/models/**/*.json`, `textures/**/*.png` и `.png.mcmeta`,
все retained logical/city/whole-owner definitions, распакованные logical/city/owner
meshes, и reference JSON для contracts, geometry, physical footprints, transforms,
owner mappings и reviewed wall. Он также вкладывает текущие 49-production
`ALT-Art-Kit.zip` и `ALT-ResourcePack-Template.zip`, созданные существующими
`export_alt_artist_kit.py` и `export_alt_visual_starter.py`.

`reference/**` — read-only ориентация художника. Geometry, footprints, contracts,
transforms и mappings не являются точкой изменения gameplay. ALT выбирается через
`BlockStateTag.visual=alt`; совпадающий с BASE ALT допустим. После изменения
встроенного art повторно создайте creative-equivalence proof перед выпуском.

`manifest.json` и `SHA256SUMS.csv` содержат полный список payload-файлов с SHA-256.
Экспорт сохраняет технические assets даже если сейчас они не встречаются в мире.