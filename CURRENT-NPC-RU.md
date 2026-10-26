# Завершённый этап NPC

Релиз и полные исходники: https://github.com/Stasik1960/Dreamwalker/releases/tag/native-grid-city-npc-20261026

Версия 0.1.0-v12.7.4-visual-physics.8. SHA256 JAR: `632c800f08ad2aab81586bcd16bd567eeb9b151ed0f9d8f671f56d24b2b3f040`. Установка проверена в трёх папках; receipts: installed.json. Запрос находится в TASK-RU.md, результат — REPORT-RU.md, решения — DECISIONS-RU.md, история проверок — TEST-NOTES-RU.md. Итоговый приёмочный receipt: acceptance.json; тестовый пак: tested-pack.json; визуальная оценка: visual-review.json.

Для нового ПК распаковать dreamwalker-npc-full-sources-20261026.zip и работать в DW. Это полный источник, включая ресурсы, Gradle wrapper, QA и инструменты. Git source-code — сопровождающий overlay; для восстановления брать полный архив. Нужен JDK 17. Сборка: `gradlew.bat remapJar -Pworldedit_api_jar=build-dependencies/worldedit-mod-7.2.15.jar`. Сеть потребуется для загрузки зависимостей Gradle. QA-инструменты содержат пути этого ПК и требуют адаптации на другом.

Главные файлы: RpMobEntity.java, NpcMobVisualBridge.java, CatalogMobRenderer.java, CorpseFloor.java, compat/customnpc/CustomNpcMobRendererMixin.java и optional mixin config; BbToolService.java/BbToolRotations.java. Важные детали: NoAI при сохранении берётся из реального флага, команды движения/оживления его снимают; pose sequence перезапускает повторную атаку; широкий моб доводится до центра клетки после найденного пути, максимум 200 завершающих тиков; типы/ID не менялись. CorpseFloor меняет только матрицу визуала, не кости или коллизии.

Серверное поведение и исходный мост модели приняты на AAE-сборке (server-5/client-2). Новая клиентская коррекция принята на финальном JAR; renderer-delta.json доказывает побайтную идентичность всех остальных записей. client-reviewed.json объясняет ошибку завершения старого QA, не скрывая оригинальный FAIL. Новые финальные прогоны завершаются штатно.

Карта не менялась. Использовать текущую пользовательскую bloodborne или карту https://github.com/Stasik1960/Dreamwalker/releases/tag/native-grid-city-interactions-20261026 . Блоки v12, цельные сборки, скамьи, фонари, окна, utility blocks и прочие исправления .7 сохранены. Не выполнять конвертацию заново. Первоначальная задача города находится также в Git/TASK и history/TASK полного архива.

На текущем ПК проект: C:/Users/vakir/CodexProjects/DW-visual-fixes-20261025. Рабочее хранилище C:, миграция E: приостановлена. Перед работой читать C:/Users/vakir/CodexControl/storage-mode.json и C-return-result.txt; применять Guard.ps1 Check/Operation. Старые E-копии не использовать. Последние материалы сохранять на GitHub: пользователь регулярно очищает локальный диск.
