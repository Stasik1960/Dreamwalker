# Переход к объединённому комплекту rc.2

Сначала сделайте backup старого мира, модов и player data. Используйте JAR и
полную копию города только из [единого каталога](../../../releases/Bloodborne-Blocks/README.md).
Версия: Minecraft 1.20.1, Java 17, Fabric Loader 0.16.10, Fabric API 0.92.9+1.20.1.

Карта rc.2 — адресно исправленная копия опубликованного rc.1. Замена одного JAR
не исправляет carrier-фрагменты старой карты. Новые пользовательские правки,
которых нет в предоставленном rc.1 ZIP, в этот комплект не входят.

23 неизвестных composite ID намеренно удалены в предшествующей карте; перед
обновлением обязателен backup старого мира. Возможны визуальные пустоты.
Текущий ремонт не восстанавливает неизвестные модели: все 33 координаты
сохранены в точном состоянии опубликованного rc.1, включая 20 уже занятых клеток.

Установите один и тот же rc.2 JAR на клиент и изолированный тестовый сервер.
Распакуйте архив города: `level.dat` должен находиться непосредственно в папке
мира. Старые Bloodborne JAR/resource pack отключите согласно основному README.
Сохраните исходные ZIP отдельно; rollback — возврат всей резервной копии вместе
с соответствующим старым JAR, без обратной конвертации новой карты.

Для воспроизведения адресного ремонта:

```powershell
python tools/convert_logical_world.py releases/Bloodborne-Blocks/2.1.0-rc.1/Bloodborne-City-2.1.0-rc.1.zip build/accepted-city-new --restore-accepted-objects --report build/accepted-first.json
python tools/verify_accepted_restore.py releases/Bloodborne-Blocks/2.1.0-rc.1/Bloodborne-City-2.1.0-rc.1.zip build/accepted-city-new build/accepted-first.json --result build/accepted-verify.json
```

Повторный запуск требует новый output-путь и `--restore-source-tree-sha256`
из `outputTreeSha256` первого отчёта. Иные ZIP, частично изменённые target-клетки,
чужие NBT/ticks и незамкнутое владение блокируют запись всей карты.
Не сочетать этот режим с `--recover-city`, aggressive или full-grid.
