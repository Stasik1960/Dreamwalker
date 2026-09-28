# Bloodborne Architecture — rc.3 checkpoint

Один согласованный JAR и полная новая копия rc.2-города. Это разрешённый
checkpoint рабочей ветки `codex/bloodborne-complete-accepted-repair`;
`main` остаётся на rc.2, release tag не создаётся.

| Файл | Скачать |
|---|---|
| Игровой JAR | [bloodborne-blocks-2.1.0-rc.3.jar](https://media.githubusercontent.com/media/Stasik1960/Dreamwalker/refs/heads/codex/bloodborne-complete-accepted-repair/Bloodborne-Blocks/releases/Bloodborne-Blocks/2.1.0-rc.3/bloodborne-blocks-2.1.0-rc.3.jar) |
| Полная карта | [Bloodborne-City-2.1.0-rc.3-checkpoint.zip](https://media.githubusercontent.com/media/Stasik1960/Dreamwalker/refs/heads/codex/bloodborne-complete-accepted-repair/Bloodborne-Blocks/releases/Bloodborne-Blocks/2.1.0-rc.3/Bloodborne-City-2.1.0-rc.3-checkpoint.zip) |
| SHA-256 | [SHA256SUMS.txt](../../Bloodborne-Blocks/releases/Bloodborne-Blocks/2.1.0-rc.3/SHA256SUMS.txt) |
| Source commit и первичные доказательства | [delivery.json](../../Bloodborne-Blocks/docs/complete-accepted-repair/delivery.json) |

Независимо проверены 989 применённых целых групп, 7 207 изменённых клеток,
сохранность нецелевого NBT и 170 файлов вне terrain. Второй проход: ноль
изменений, все 186 файлов идентичны. Census: 29 194 уже корректных,
3 148 восстановленных и **4 453 известных нерешённых кандидата**.
Часть дополнительного source scope также ещё не доказана.

`SUBSET_PASS` не означает `COVERAGE_COMPLETENESS_PASS` или `RELEASE_READY`.
[Все gates и фактические проверки](../../Bloodborne-Blocks/docs/RELEASE-STATUS.md) ·
[Таблица семейств и координаты](../../Bloodborne-Blocks/docs/complete-accepted-repair/REPORT.md) ·
[Отдельные комментарии агента](../../Bloodborne-Blocks/docs/complete-accepted-repair/AGENT-COMMENTS.md).

Minecraft **1.20.1**, Java **17**, Fabric Loader **0.16.10**, Fabric API
**0.92.9+1.20.1**. Сначала сделайте backup. Устанавливайте одинаковый игровой
JAR на клиент/сервер и используйте соответствующую карту на отдельной копии.
Сохраните остальные моды исходной сборки: их данные в ZIP не удалены.
[Миграция и rollback](../../Bloodborne-Blocks/docs/complete-accepted-repair/MIGRATION.md).

Sources JAR с Yarn-именами предназначен для анализа, не для папки `mods`.
[Все три файла](../../Bloodborne-Blocks/releases/Bloodborne-Blocks/2.1.0-rc.3).
Прежние rc.2 и исходные архивы сохранены без перезаписи.
[История комплектов](HISTORY.md).
