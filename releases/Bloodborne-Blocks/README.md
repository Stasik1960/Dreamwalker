# Bloodborne Architecture — единый комплект 2.1.0-rc.2

Принятые TEST3 и локальное продолжение repair объединены. Используйте **эти JAR
и полную карту вместе**, на отдельной копии. Графический клиент ещё не принят;
комплект не объявлен production RELEASE_READY или исправлением всех исторических объектов.

| Файл | Скачать |
|---|---|
| Игровой JAR rc.2 | [bloodborne-blocks-2.1.0-rc.2.jar](https://media.githubusercontent.com/media/Stasik1960/Dreamwalker/main/Bloodborne-Blocks/releases/Bloodborne-Blocks/2.1.0-rc.2/bloodborne-blocks-2.1.0-rc.2.jar) |
| Полный город rc.2 | [Bloodborne-City-2.1.0-rc.2.zip](https://media.githubusercontent.com/media/Stasik1960/Dreamwalker/main/Bloodborne-Blocks/releases/Bloodborne-Blocks/2.1.0-rc.2/Bloodborne-City-2.1.0-rc.2.zip) |
| SHA-256 | [SHA256SUMS.txt](../../Bloodborne-Blocks/releases/Bloodborne-Blocks/2.1.0-rc.2/SHA256SUMS.txt) |
| Source commit и первичные доказательства | [delivery.json](../../Bloodborne-Blocks/docs/accepted-restore/delivery.json) |

74 подтверждённых дерева, 34 стопки книг и 411 окон восстановлены вместе с
остальными принятыми семействами и строительными адаптерами. Всего 6 507 групп
восстановлены, 20 474 уже корректны. [Полная таблица, координаты и ограничения](../../Bloodborne-Blocks/docs/accepted-restore/REPORT.md).
Всё вне доказанных транзакций сохранено; повторный ремонт даёт побайтно ту же карту.
[Linux CI — PASS](https://github.com/Stasik1960/Dreamwalker/actions/runs/36415497358):
сборка, 285 Python тестов, 57/57 GameTests и повторная проверка поставляемого города.

Minecraft **1.20.1**, Java **17**, Fabric Loader **0.16.10**, Fabric API
**0.92.9+1.20.1**. Перед обновлением обязателен backup.
Остальные моды исходной сборки нужно сохранить: архив содержит их исходные
данные, а серверный smoke-test охватывал только Bloodborne/Fabric API.
[Миграция/rollback](../../Bloodborne-Blocks/docs/accepted-restore/MIGRATION.md) ·
[Все gates](../../Bloodborne-Blocks/docs/RELEASE-STATUS.md) ·
[Комментарии агента](../../Bloodborne-Blocks/docs/accepted-restore/AGENT-COMMENTS.md).

Для разработчиков отдельно сохранён sources JAR с исходными Yarn-именами;
устанавливать его в `mods` не нужно. Файлы хранятся в
[`Bloodborne-Blocks/releases/Bloodborne-Blocks/2.1.0-rc.2`](../../Bloodborne-Blocks/releases/Bloodborne-Blocks/2.1.0-rc.2).

## Исторические комплекты

**rc.1 не содержит принятые repair-исправления.** Его JAR и карта оставлены как
историческая база, не как текущая рекомендация. Старые beta, REPAIR TEST и
`repair-catalog.1` diagnostic world также не заменяют комплект выше.
[История](HISTORY.md). Никогда не смешивайте их JAR и карты с rc.2.
