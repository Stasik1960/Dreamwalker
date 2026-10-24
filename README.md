# Dreamwalker — полная карта и проверенная пользовательская сборка

Полная карта записана и освещена. После исходных финальных11+1 и осмотра12 PNG выполнено отдельное сравнение пользовательской сборки в центре, выбран корректный вариант A и завершён его полный маршрут. Контрольное повторное открытие отражается в итоговом отчёте; окончательная приёмка комплекта — [DELIVERY-ACCEPTANCE.json](DELIVERY-ACCEPTANCE.json).

- Мир: `research/city/bloodborne-native-grid-city`.
- Выбрана пользовательская сборка A: Fabric Indigo, **без Sodium/Indium**. B с этими двумя модами механически завершился, но не рисовал проверенные statue/tree; его FPS не является выигрышем при эквивалентном изображении.
- Полный маршрут A: PASS,1948.812s,92 фазы, реально доставлены3712/3712 целевых колонок;7865 уникальных доставленных с соседями. Это покрытие известного содержимого/сохранённых конструкций, не ручной осмотр каждого полигона.
- Статичные окна полного маршрута:59.82295FPS,1%low39.1917. При движении/streaming были226 кадров >250ms, один >1s, ни одного >3s; максимум2043.4025ms. Не обещается постоянные60FPS при любой загрузке.

[Установка](INSTALL-RU.txt), [передача и доказательства](HANDOFF-RU.md), [исходное задание](TASK/START-RU.txt), [приоритеты](TASK/USER-PRIORITIES-CURRENT-RU.txt), [краткий отчёт проверки](USER-MODPACK-FINAL-REPORT-RU.txt). Отчёт также сохранён в восстановленном комплекте: `research/delivery/USER-MODPACK-FINAL-REPORT-RU.txt`.

## Мод, исходники и восстановление

Minecraft1.20.1 Fabric и точные зависимости проверенной сборки A. Production JAR:
`research/runtime-history/production-lazy-frapi-9b2edaa041331ff510df87ad1abd51b1de9a535f5768396bc04b6b5cf24d7d33.jar`.
SHA256 `9b2edaa041331ff510df87ad1abd51b1de9a535f5768396bc04b6b5cf24d7d33`.

Текущие исходники: `research/delivery/full-current-sources.delivery.zip`, SHA256 `6bda26166864d142aef6fe770e664ba91c796f94e48e965ac7d7b17cf3eac063`. Исторические source ZIP не заменяют этот архив.

Ветка `codex/native-grid-city-full-20261024`, [релиз](https://github.com/Stasik1960/Dreamwalker/releases/tag/native-grid-city-full-20261024), [manifest](https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-full-20261024/bundle-manifest.json). **Manifest SHA256: `290a6891c9e83c70e63bba37e5da555da09a01bccb4b7383dcb74cc88ebea2d2`.** Опубликованные фактические refs: [FINAL-SNAPSHOT.json](https://github.com/Stasik1960/Dreamwalker/blob/codex/native-grid-city-full-20261024/FINAL-SNAPSHOT.json).

Скачать manifest как `final-bundle-manifest.json` и существующий `github_delta_restore.py`. Восстановить все тома по URL/SHA manifest, включая переиспользуемую FULL-LIGHT базу, не только новые ZIP:

```powershell
python github_delta_restore.py --manifest final-bundle-manifest.json --manifest-sha256 290a6891c9e83c70e63bba37e5da555da09a01bccb4b7383dcb74cc88ebea2d2 --assets downloaded-volumes --download --target C:/Users/vakir --receipt restore-final-receipt.json
```

Полный авторитетный проект — восстановленный `DW-native-grid-city-20261022`; Git code mirror не заменяет generated assets, кэши и план. Предпочтителен исходный user root `C:/Users/vakir`: защищённые абсолютные bindings не переписываются автоматически. До restore отключить auto-worktree-deletion. Manifest не содержит `.git`; для проектных Guard-запусков восстановить настоящий independent Git по HANDOFF, не fake HEAD и не поверх неизвестного репозитория.

## Сохранность и границы результата

Исходные v12, данные, двери и согласованные самостоятельные цельные объекты сохранены. Два бюста [223,71,-925] и [-62,113,-733] оставлены original-format с необходимыми частями/данными. Удалены только согласованные10 visual quads /4 fragment records. Пять vanilla bakes — сухой свет1/2/4/5 и сухая нижняя andesite slab: вид/коллизия/излучение сохраняются, изменение уровня света, слияние полуплиты и заливка водой могут быть потеряны. Исходные наложения коллизий допустимы; ручное восстановление после break может не пройти.

Проверки выполнены на одном Ryzen7 5800H/RTX3070 Laptop/16GiB RAM с одновременными client4G/server4G, Java17,1280×720, fancy+AO, view6/simulation4, cap60. При проходе свободная RAM снижалась до349274112 bytes; observed system CPU достигал100%. Эти наблюдения не доказывают причину каждого длинного кадра. Heap snapshots и process working set — разные величины; общие страницы и несовпадающие пики нельзя просто складывать.

Старый before-map baseline не завершился. Новый пользовательский A/B — отдельное центральное сравнение, не доказательство процента ускорения исходной миграции и не гарантия удалённого хостинга. Доставка3712 колонок и92 изображения не означают ручную проверку всех полигонов/копий; часть камер показывает подложку или перекрытия.

На новом ПК/в новом чате читать HANDOFF, START, USER-PRIORITIES и актуальные `research/RESUME-CURRENT-RU.txt`/`current-operation.json`. Принятые clone/apply/light/coverage, исходные11+1, A/B и полный маршрут не повторять. LocalC; старый перенос на E: остаётся приостановленным.
