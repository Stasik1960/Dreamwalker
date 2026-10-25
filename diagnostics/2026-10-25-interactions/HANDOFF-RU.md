Публикация подтверждена: https://github.com/Stasik1960/Dreamwalker/releases/tag/native-grid-city-interactions-20261026. completion.json — COMPLETE; все3 установки проверены.

# Восстановление Dreamwalker visual-physics.7

Все изменения кода и карты проверены и приняты. Production SHA256 `be12bdb812d405899389570d48264e193564aae4c7c36ab97d4b7882db5797ac`. Основная карта: `C:/Users/vakir/Desktop/bloodborne`. Проверки — acceptance.json; публикацию подтверждают final-release.json со статусом PUBLISHED и completion.json в GitHub diagnostics/2026-10-25-interactions. Этот архив исходников создан перед загрузкой релиза, поэтому сам receipt публикации смотрите в репозитории.

Итоговый релиз: https://github.com/Stasik1960/Dreamwalker/releases/tag/native-grid-city-interactions-20261026. Скачайте JAR .7, bloodborne-interactions-20261026.zip и dreamwalker-interactions-full-sources-20261026.zip; сверяйте SHA256SUMS.txt. Source ZIP содержит полный рабочий проект DW: исходники, все native meshes, ресурсы, gradle, инструменты, QA, доказательства и исходную задачу в history/TASK. Неполный Git-каталог source-code не заменяет полный архив. Ветка источников: codex/interaction-fixes-20261025 репозитория Stasik1960/Dreamwalker.

На новом ПК: распаковать source ZIP, установить JDK17, запустить gradlew.bat -Pworldedit_api_jar=build-dependencies/worldedit-mod-7.2.15.jar restorationBuild. Первый запуск требует обычной загрузки зависимостей Gradle/Fabric; в текущем окружении используются caches. Пользователю ставится только production JAR, не QA. Все203 окна уже заменены: мигратор повторно не запускать; повторной конвертации города и фонарей нет.

Рабочий исходный каталог этого ПК C:/Users/vakir/CodexProjects/DW-visual-fixes-20261025; доказательства interaction-evidence, тестовые копии interaction-profiles. Исходный пользовательский архив bloodborne-before-interactions.zip SHA256 9df2e6b5053dd86eb2bac093a372daf82afef2d6028d840ec89e8f5c75466087; его1888 файлов совпадают с публичным bloodborne-restored-20261025.zip предыдущего v6. Поэтому старый стабильный релиз также сохраняет полный вход этой задачи.

Прочесть TASK-RU.md, REPORT-RU.md, DECISIONS-RU.md. Принятые проверки: server1, client1 и final-binary-delta; preview2/map-audit, global-data; client2 с полной Limacina-сборкой и визуальный осмотр; apply1 и оба итоговых аудита. Старые незатронутые проверки v6 повторять не требуется. Мигратор и постобработка глобальных tombstones находятся только в QA/инструментах и в production автоматически не запускаются. Все существующие функциональные данные проверялись перед записью и после сохранения.

Активный диск C:, старый перенос E: PAUSED. На этом ПК читать CodexControl/storage-mode.json, C-return-result.txt и использовать Guard.ps1 Check/Operation. Не возобновлять E: и не удалять проекты при очистке. Не следовать reparse points. Пользователь разрешил самостоятельные разумные компромиссы; сохранение v12, ручных правок и согласованных цельных конструкций обязательно.

Клавиша no-collide назначается пользователем в управлении; по умолчанию свободна. Новый Special не переключает ALT; для поворота есть шестой режим. Окна переключаются обычным ПКМ. Известных незавершённых изменений кода/карты по текущей задаче после acceptance нет; техническое завершение установки/публикации фиксируется в completion.json.
