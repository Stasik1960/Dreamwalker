# Снимки выполненной оркестрации

Здесь сохранены точные helper-скрипты запуска и расчёта отчётов. Их исходное
расположение — `Bloodborne-Blocks/build/catalog-city-tools/`; вычисление ROOT
зависит от этого расположения. Для повторения сначала скопируйте нужные helpers
туда, затем запускайте из корня `Bloodborne-Blocks` с Python 3.

```powershell
python -B -X utf8 build/catalog-city-tools/run.py all --run-dir build/catalog-city-diagnostic-NEW
python -B -X utf8 build/catalog-city-tools/summarize.py build/catalog-city-diagnostic-NEW
```

Указывайте новый выходной каталог. Вход закреплён как подтверждённый
`reference-inputs/latest-modded-world.zip` с проверкой SHA-256. Helpers вызывают
существующие tools; не заменяют конвертер и не обходят его проверки. Код 1
protected checker сохраняется как диагностический FAIL, а не успешная приёмка.

`extend_storage_roots.py` — снимок staging-проверки расширения от ресурсов TEST3:
prepare перенаправляет RES/CITY, сохраняет production textures, проверяет каждый
путь и все прежние записи; promote повторяет проверку перед копированием.
Расширение уже применено: запускать его повторно для установки JAR не нужно.

`package_results.py` и `write_report.py` описывают упаковку именно текущего
проверенного запуска. Они используют фиксированные имена каталогов артефактов;
для другого запуска измените их явно, сохраняя предыдущие доказательства.
`final/*.command.json` фиксируют фактически выполненные команды и exit codes.

Мир остаётся диагностическим. Успех preservation/idempotence не отменяет
protected/composite FAIL и оставшиеся старые/неизвестные registry ID.
