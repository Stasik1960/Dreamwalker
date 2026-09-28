# Воспроизведение checkpoint

Из `Bloodborne-Blocks`, Java 17, wrapper Gradle 8.8 и Python 3.11+ с
`pip install -r tools/requirements-ci.txt`. Для Git LFS нужны исходные ZIP,
resources и предыдущий опубликованный rc.2. Требуется история Git:
проверки сравнивают принятый `41c20ee8b730c2d1567b2ab5a3d579d51bdbea1a`
и physics baseline `90a4e051a71ecc7b3156802c54dbfd8219789469`.

Выходные папки должны быть новыми. Исходный rc.2 не открывается на запись.

```powershell
python tools/convert_logical_world.py `
  releases/Bloodborne-Blocks/2.1.0-rc.2/Bloodborne-City-2.1.0-rc.2.zip `
  build/complete-accepted-repair/reproduced-city `
  --complete-accepted-objects --repeat-check --progress `
  --report build/complete-accepted-repair/reproduced.json

python tools/verify_complete_accepted_repair.py `
  releases/Bloodborne-Blocks/2.1.0-rc.2/Bloodborne-City-2.1.0-rc.2.zip `
  build/complete-accepted-repair/reproduced-city `
  build/complete-accepted-repair/reproduced.json `
  --second build/complete-accepted-repair/reproduced-city-second-pass `
  --result build/complete-accepted-repair/reproduced-verification.json

.\gradlew.bat --no-daemon --continue --console=plain check build logicalGameTest checkReleaseVersion --max-workers=1
```

Ожидается `subsetResult=PASS`, `coverageCompleteness=FAIL`, `releaseReady=false`.
Независимый verifier проверяет все terrain/NBT и non-terrain файлы, реальные
root/helper связи, source authority и второй архив/каталог. PASS подмножества
не означает готовность всех исторических объектов.

На этой Windows-машине использован явный путь Python через
`-PbloodbornePython=.../build/rc1-verification/venv/Scripts/python.exe` и
`-I tools/verification-direct-resources.init.gradle`: последний читает те же
нефильтруемые resources напрямую и исключает только их лишнее копирование.
В Linux CI применяется обычный `bash ./gradlew` без этого init script.
Итоговые логи и записи завершённых команд приложены отдельно.

Серверный smoke запускается только на ещё одной одноразовой копии города.
Его сохранения не входят в публикуемый ZIP. Для окончательной игровой
приёмки требуется исходная полная сборка модов и настоящий графический
клиент; команды GameTest не заменяют ручные действия из `REQUEST.md`.
