# Dreamwalker — полная карта и свет: CHECKPOINT

**Это сохранение незавершённой работы, не готовая поставка. READY не заявлен.** Полная карта записана, освещена, сохранена; конечный игровой маршрут и итоговая приёмка ещё не выполнены. После сохранения работа продолжается при достаточной памяти, без повторения принятых этапов.

Ветка: `codex/native-grid-city-full-20261024`. Tag этого checkpoint: `native-grid-city-full-light-20261024`. [Релиз checkpoint](https://github.com/Stasik1960/Dreamwalker/releases/tag/native-grid-city-full-light-20261024), [bundle-manifest.json](https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-full-light-20261024/bundle-manifest.json). **Manifest SHA256: `75e1ddf017d7ec04733e047c42ed55239be641755d518040074e4db99491565a`.** Итоговый tag `native-grid-city-full-20261024` ещё не создан.

## Восстановить на новом ПК

Скачать текущий manifest как `bundle-manifest.json` и существующий `github_delta_restore.py` из репозитория. Нужны ВСЕ тома по URL/SHA из этого manifest, включая переиспользуемую базу PRE-MAP; одного Git checkout или только новых ZIP недостаточно.

```powershell
python github_delta_restore.py --manifest bundle-manifest.json --manifest-sha256 75e1ddf017d7ec04733e047c42ed55239be641755d518040074e4db99491565a --assets downloaded-volumes --download --target C:/Users/vakir --receipt restore-full-light-receipt.json
```

Restore проверяет точные байты/времена файлов и не перезаписывает отличающиеся существующие файлы. Предпочтительный user root — `C:/Users/vakir`, поскольку защищённые absolute bindings автоматически не переписываются. Авторитетный полный проект — восстановленный `DW-native-grid-city-20261022`; Git code mirror не заменяет его generated assets, кэши и SQLite-план.

Прочитать [HANDOFF-RU](HANDOFF-RU.md), [START](TASK/START-RU.txt), [приоритеты](TASK/USER-PRIORITIES-CURRENT-RU.txt), затем восстановленные `research/RESUME-CURRENT-RU.txt` и `research/current-operation.json`.

## Уже завершено

FULL apply: 2153693 placements. Native light: 4370/4370; четыре исходные сборки объединены и сохранены; accept-relight прошёл. Финальная preserved coverage с retention members: 28 entries,76 poses,3801 objects — 277 compiled whole,3520 local original и4 merged runtime groups;4 actual groups/8 source owners.

Карта: `research/city/bloodborne-native-grid-city`. Мод Minecraft1.20.1 Fabric: `research/runtime-history/production-full-ac56c8643cf46c245793191c0e5c099d95f755160b0a7109b2d6d6f5091ed0aa.jar`, SHA256 `ac56c8643cf46c245793191c0e5c099d95f755160b0a7109b2d6d6f5091ed0aa`; зависимости — из сохранённого рабочего профиля.

Актуальные исходники: `research/delivery/full-current-sources.final.zip`, SHA256 `f49a7c4947414159d3507f78b65bcbd730bea03abfdaaa96badd6987bf122d97`. Архив принят: 170277 файлов,238066951 bytes.

## Продолжить только оставшееся

После достаточной RAM выполнить готовый final route11, дождаться нормального save/exit клиента и отдельного сервера, затем запустить отдельный reopen сохранённого мира. Root должен посмотреть все12 actual PNG, оценить движение, оформить visual/delivery review и final aggregate, затем опубликовать финальный delta. Сейчас route/reopen/PNG/aggregate отсутствуют. Clone, apply, light, coverage, принятый пилот и незатронутые QA заново не запускать.

Default reserve перед парой — 9GiB свободной физической RAM. Допустим отдельный functional admission: минимум7.5GiB physical available И9GiB Windows available commit при прежних server4G/client3G и frozen recorder/profile. Текущие6.2GiB не удовлетворяют этому допуску. Heap budget и heap snapshots не равны process RSS. Полный before/after baseline не завершён: FPS и процент ускорения не доказаны.

Сохранены исходные v12, данные, двери и согласованные цельные объекты; два бюста оставлены original-format. По точным разрешениям удалены10 decorative visual quads /4 fragment records, запечены5 vanilla states: сухой свет1/2/4/5 и сухая нижняя andesite slab. Вид/коллизии/излучение сохраняются, перечисленные взаимодействия могут быть потеряны. Первоначальные наложения коллизий допустимы; восстановить их вручную после break может быть невозможно. Подробности и authority — в HANDOFF/USER-PRIORITIES.
