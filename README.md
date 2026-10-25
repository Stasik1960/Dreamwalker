NPC и Custom NPC, visual-physics.8: [текущее состояние и восстановление](CURRENT-NPC-RU.md).

Последние интерактивные исправления .7: [текущее состояние и восстановление](CURRENT-INTERACTIONS-RU.md).

# Dreamwalker — visual-physics.6

Текущая карта пользователя bloodborne, исправленный мод, полные исходники и отчёт: [релиз restoration-20261025](https://github.com/Stasik1960/Dreamwalker/releases/tag/native-grid-city-restoration-20261025). Инструкция установки и продолжения: [HANDOFF-RU.txt](HANDOFF-RU.txt). Доказательства: [city-restoration](diagnostics/2026-10-25-city-restoration).

<!-- CURRENT_RESTORATION_END -->

# Dreamwalker — готовая карта visual/physics.1

Принятая локальная пара, исходники и проверенные настройки; выпуск: https://github.com/Stasik1960/Dreamwalker/releases/tag/native-grid-city-visual-physics-20261025.

Используйте **одну активную копию** `bloodborne-native-grid-city` и единственный matching Dreamwalker JAR на клиенте и сервере. Из архива modpacks перенесите client_mods/server_mods по ролям, а config/shaderpacks из Dreamwalker-*-settings — рядом с mods, не внутрь mods. Пользовательские аккаунты/options/клавиши архивами не заменяются. Шейдер по умолчанию выключен.

Все пять файлов Release обязательны для полного комплекта; `ASSETS.json` и `SHA256SUMS.txt` фиксируют байты. `python download_release.py --out downloads` скачивает/проверяет файлы без распаковки. Мир ZIP имеет ровно один корень `bloodborne-native-grid-city`; дополнительные старые миры в поставку не включены.

**Полный source ZIP авторитетен**: содержит effective assets, каталог/meshes, Java, QA и инструменты. `source-code/` здесь — небольшой обзор исходников, без больших ресурсов, не самостоятельная полная сборочная копия. Java17/Gradle и инструкции нового ПК: [HANDOFF-RU.txt](HANDOFF-RU.txt). Результаты/ограничения: [REPORT-RU.txt](REPORT-RU.txt).

v12, двери, данные и согласованные цельные объекты сохранены по приёмочным контрактам. Не повторять готовую конвертацию/свет и не открывать новую карту старым модом. Точный визуальный scope и нерешённые ограничения описаны в отчётах; FPS не гарантируется на ином железе.

История прежнего формата: https://github.com/Stasik1960/Dreamwalker/releases/tag/native-grid-city-full-20261024 . Это predecessor, не инструкция подменять текущую пару старой.
