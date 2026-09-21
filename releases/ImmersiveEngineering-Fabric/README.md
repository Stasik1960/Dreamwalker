# Immersive Engineering на Fabric 1.20.1

Неофициальный протестированный слой совместимости для запуска официальной Forge-сборки **Immersive Engineering 10.2.0-183** через Kilt на Fabric 1.20.1.

Это не нативный порт всего Immersive Engineering на Fabric API. Адаптер сохраняет официальный мод неизменным и исправляет подтверждённую несовместимость: через Fabric Loot API возвращает 10% шанс выпадения семян технической конопли из обычной и высокой травы. При использовании ножниц семена не добавляются, как в оригинале.

## Файлы

- `ie-fabric-compat-0.2.0.jar` — готовый адаптер;
- `Install-IEFabric.ps1` — установщик точных проверенных зависимостей;
- `ie-fabric-compat-0.2.0-sources.jar` и `ie-fabric-compat-0.2.0-source.zip` — исходники адаптера;
- `TEST_REPORT.md` — выполненные проверки и известные ограничения;
- `CHECKSUMS-SHA256.txt` — контрольные суммы всех файлов.

## Проверенная конфигурация

| Компонент | Версия |
| --- | --- |
| Minecraft | 1.20.1 |
| Java | 17 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.92.12+1.20.1 |
| Kilt | 20.1.21 |
| Fabric Language Kotlin | 1.14.1+kotlin.2.4.20 |
| Forge Config API Port | 8.0.3 |
| Immersive Engineering | 1.20.1-10.2.0-183 |
| IE Fabric Compatibility | 0.2.0 |

Версии зафиксированы: не обновляйте Kilt или IE отдельно без нового теста.

## Установка

1. Сделайте резервную копию мира.
2. Установите Fabric Loader 0.19.5 для Minecraft 1.20.1.
3. Положите `ie-fabric-compat-0.2.0.jar` рядом с `Install-IEFabric.ps1`.
4. Запустите в PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\Install-IEFabric.ps1
```

Для отдельной папки игры добавьте `-GameDirectory "D:\Minecraft\IE-Fabric"`; для сервера также добавьте `-Server`.

Установщик скачивает точные версии зависимостей с официальных CDN Fabric и Modrinth, проверяет SHA-512 и не удаляет другие моды.

## Проверка и ограничения

Пройдены запуск выделенного сервера, загрузка реестров, построение Arc Recycling и тесты лута обычной/высокой травы. Полный клиентский проход всех машин, рендеров, шейдеров, миров и интеграций ещё не выполнен. Перед публичным сервером протестируйте копию мира и нужный набор модов.

## Лицензии

Адаптер распространяется по MIT. Официальный JAR, исходники и ресурсы Immersive Engineering в эту папку не включены: они принадлежат BluSunrize и остаются под **Blu's License of Common Sense**. Установщик получает официальный JAR напрямую с Modrinth.

- https://github.com/BluSunrize/ImmersiveEngineering
- https://modrinth.com/mod/immersiveengineering
- https://modrinth.com/mod/kilt
