# Оптимизированная карта Bloodborne v22.1

`migrate_restored_bushes.py` заменяет отдельные восстановленные кусты и три проверенных вида декора существующими статическими блоками. Статические блоки используют общие рецепты модели, коллизии и выделения. Их можно ломать как статическую архитектуру. Чтобы изменить высоту или другие настройки отдельного объекта через редактор, поставьте на его место редактируемый исходный блок.

| Исходный блок | Вариант | Объектов | Удалено BlockEntity |
| --- | ---: | ---: | ---: |
| `source_chiseled_nether_bricks` | 4 | 2 912 | 2 912 |
| `source_brick_wall_side_tall` | 2 | 1 747 | 1 747 |
| `source_polished_blackstone` | 0 | 365 | 365 |
| `source_addon_grass_0` | 0 | 10 | 20 |
| `source_addon_grass_1` | 0 | 30 | 204 |
| `source_addon_grass_2` | 0 | 11 | 88 |
| `source_addon_grass_5` | 0 | 7 | 56 |
| `source_addon_grass_6` | 0 | 28 | 56 |
| `source_addon_grass_7` | 0 | 8 | 48 |
| **Итого** | | **5 118** | **5 496** |

В основном измерении заменено 5 073 объекта и удалено 5 296 BlockEntity. В `bloodborne_old` заменено 45 кустов и удалено 200 BlockEntity. Скрипт сверяет каждую замену с рецептом, формой, записями владельца и сохранёнными ссылками. Для трёх видов декора он дополнительно сверяет состояние, UUID и данные корня с картой от 27 октября. Затронуты 6 регионов и 110 файлов владельцев. При повторном чтении скрипт проверяет изменённые блоки, остальные записи в тех же чанках и побайтовое совпадение остальных файлов мира.

## Повторная сборка

Нужен Python 3.12 или новее и собранный `DreamWalker-v22.1-City-Performance.1.jar`.

- `Bloodborne-v22-Bush-Restoration-world.zip` с SHA-256 `1a8696ed5875305ba0c4d4c52aaf5ec601dd06fd7f7c539e96ee43af21e64cdb` и `Bloodborne-v22-Bush-Restoration-report.json` с SHA-256 `d740deeb11e4f2acdbcfd5d23e3c75ca090f36110615305ef5a84f175003c1b2`. [Релиз `v22-bloodborne-bush-restoration.1`](https://github.com/Stasik1960/Dreamwalker/releases/tag/v22-bloodborne-bush-restoration.1)
- `bloodborne-with-bloodborne_old-20261027.zip` с SHA-256 `43a9e865e2cd8f419e57d41c3224a9086ac718e1b1294d47f3834c851a98e3e1`. [Релиз `dreamwalker-visual4-rc3-map-audit-20261027`](https://github.com/Stasik1960/Dreamwalker/releases/tag/dreamwalker-visual4-rc3-map-audit-20261027)

Из каталога `Bloodborne-Startup/city-performance` запустите команду. В примере исходные файлы лежат в подкаталоге `inputs`.

```powershell
python .\migrate_restored_bushes.py `
  --world-zip .\inputs\Bloodborne-v22-Bush-Restoration-world.zip `
  --bush-report .\inputs\Bloodborne-v22-Bush-Restoration-report.json `
  --historical-world-zip .\inputs\bloodborne-with-bloodborne_old-20261027.zip `
  --jar .\inputs\DreamWalker-v22.1-City-Performance.1.jar `
  --output-world .\world-city-performance-expanded `
  --output-zip .\Bloodborne-v22.1-City-Performance-world.zip `
  --output-report .\Bloodborne-v22.1-City-Performance-migration.json
```

Используйте свободные пути для трёх результатов. Скрипт распакует `bloodborne` в указанный выходной каталог и запишет архив и JSON с координатами, рецептами и контрольными суммами. Без `--historical-world-zip` скрипт обработает только кусты из отчёта восстановления.
