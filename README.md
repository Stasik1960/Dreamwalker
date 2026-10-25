# Dreamwalker — готовая карта visual/physics.1

Принятая локальная пара, исходники и проверенные настройки; выпуск: https://github.com/Stasik1960/Dreamwalker/releases/tag/native-grid-city-visual-physics-20261025.

Используйте **одну активную копию** `bloodborne-native-grid-city` и единственный matching Dreamwalker JAR на клиенте и сервере. Из архива modpacks перенесите client_mods/server_mods по ролям, а config/shaderpacks из Dreamwalker-*-settings — рядом с mods, не внутрь mods. Пользовательские аккаунты/options/клавиши архивами не заменяются. Шейдер по умолчанию выключен.

Все пять файлов Release обязательны для полного комплекта; `ASSETS.json` и `SHA256SUMS.txt` фиксируют байты. `python download_release.py --out downloads` скачивает/проверяет файлы без распаковки. Мир ZIP имеет ровно один корень `bloodborne-native-grid-city`; дополнительные старые миры в поставку не включены.

**Полный source ZIP авторитетен**: содержит effective assets, каталог/meshes, Java, QA и инструменты. `source-code/` здесь — небольшой обзор исходников, без больших ресурсов, не самостоятельная полная сборочная копия. Java17/Gradle и инструкции нового ПК: [HANDOFF-RU.txt](HANDOFF-RU.txt). Результаты/ограничения: [REPORT-RU.txt](REPORT-RU.txt).

v12, двери, данные и согласованные цельные объекты сохранены по приёмочным контрактам. Не повторять готовую конвертацию/свет и не открывать новую карту старым модом. Точный визуальный scope и нерешённые ограничения описаны в отчётах; FPS не гарантируется на ином железе.

История прежнего формата: https://github.com/Stasik1960/Dreamwalker/releases/tag/native-grid-city-full-20261024 . Это predecessor, не инструкция подменять текущую пару старой.
