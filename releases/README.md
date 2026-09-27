# Сборки Dreamwalker

[Главная](../README.md) · [Сборка из исходников](../CONTRIBUTING.md) · [Архив описаний](HISTORY.md)

Здесь перечислены опубликованные файлы, а не обещание совместимости с любой сборкой.
Все игровые проекты рассчитаны на Minecraft 1.20.1. Обычным Fabric-модам нужны
Java 17 и Fabric API; дополнительные требования указаны в их README.

## Основные моды

| Проект | JAR | Инструкция |
| --- | --- | --- |
| RP Chat 0.1.16 | [Скачать](RP-Chat/rp-chat-0.1.16.jar) · [SHA-256](RP-Chat/rp-chat-0.1.16.jar.sha256) | [Ролевой чат](../RP-Chat/README.md) |
| RP Chat UI 0.3.7 | [Скачать](RP-Chat-UI/rp-chat-ui-0.3.7.jar) | [Интерфейс](../RP-Chat-UI/README.md) |
| DW Magic Connect 0.4.3 | [Скачать](DW_Magic_Connect/dw-magic-connect-0.4.3.jar) · [SHA-256](DW_Magic_Connect/dw-magic-connect-0.4.3.jar.sha256) | [Рации](../DW_Magic_Connect/README.md) |
| DW Languages 0.1.0 | [Скачать](DW_Languages/dw-languages-0.1.0.jar) · [SHA-256](DW_Languages/dw-languages-0.1.0.jar.sha256) | [Языки](../DW_Languages/README.md) |
| MC-Pool 0.1.14 | [Скачать](MC-Pool/pool-billiards-0.1.14.jar) · [SHA-256](MC-Pool/pool-billiards-0.1.14.jar.sha256) | [Бильярд](../MC-Pool/README.md) |

Для раций и языков используйте RP Chat 0.1.16+. JAR RP Chat, DW Magic Connect и
DW Languages предоставлены пользователем и опубликованы без пересборки.
Наличие файла в каталоге не означает нового тестирования в рамках правок документации.

## Проверочные сборки и адаптеры

| Проект | Сборки и статус |
| --- | --- |
| Bloodborne Architecture 2.1.0-rc.1 | [JAR, карта и ограничения](Bloodborne-Blocks/README.md). Офлайн-проверки прошли; серверный перезапуск и клиентская приёмка ещё не подтверждены |
| Danny’s AoT 2.4.3-backport.2 | [Единый JAR и установка](Danny-AOT/README.md). Неофициальный тестовый порт |
| Immersive Engineering через Kilt | [Описание адаптера 0.2.0](ImmersiveEngineering-Fabric/README.md). Только документация: JAR и установщика в `main` нет |

## Скачивание и архив

Откройте ссылку на файл и используйте кнопку скачивания GitHub.
При клонировании репозитория получите крупные файлы через `git lfs pull`;
маленький текстовый LFS pointer не является JAR или картой.

Старые файлы оставлены для воспроизводимости: [история общего каталога](HISTORY.md),
[история Bloodborne](Bloodborne-Blocks/HISTORY.md).
Старые JAR не нужно устанавливать вместе с новыми версиями того же мода.
