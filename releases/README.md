# Готовые моды

Актуальная сводка исходников и совместимости: [основной README](../README.md#готовые-сборки). Ниже сохранены описания прежних сборок; они не означают, что старые версии рекомендуются для текущего набора.

## Bloodborne Architecture 1.2.1

[Скачать JAR](Bloodborne-Blocks/bloodborne-blocks-1.2.1-mc1.20.1.jar) · [SHA-256](Bloodborne-Blocks/bloodborne-blocks-1.2.1-mc1.20.1.jar.sha256) · [Обновление карты](../Bloodborne-Blocks/README.md).

Упрощены столкновения и выделение, кусты пропускают игрока, удалён каменный мусор, добавлены повороты, подъём по декоративным лестницам и сидение на лавке. Все старые ID сохранены; `/bloodborne update 32 preview|apply` заменяет 16 подтверждённых дубликатов с сохранением положения и восстанавливает части. Fabric 1.20.1 / Java 17, обновить сервер и клиенты. Автономные проверки и сборка выполнены без запуска Minecraft.

## Bloodborne Architecture 1.1.0

[Скачать JAR](Bloodborne-Blocks/bloodborne-blocks-1.1.0-mc1.20.1.jar) · [SHA-256](Bloodborne-Blocks/bloodborne-blocks-1.1.0-mc1.20.1.jar.sha256) · [Установка и миграция](../Bloodborne-Blocks/README.md).

503 блока, коллизии по моделям, связанные крупные объекты, открывающиеся двери и исправленные размеры предметов. Minecraft 1.20.1 / Fabric / Java 17; установка на сервер и клиенты. Сборка и автономные проверки прошли; игра не запускалась.

## MC-Pool 0.1.13 и DW Languages 0.1.0

Готовые JAR предоставлены пользователем и опубликованы без пересборки. Проверены метаданные, целостность ZIP и совпадение SHA-256 с исходными файлами; Minecraft не запускался.

| Мод | JAR | Контрольная сумма |
| --- | --- | --- |
| MC-Pool 0.1.13 | [pool-billiards-0.1.13.jar](MC-Pool/pool-billiards-0.1.13.jar) | [SHA-256](MC-Pool/pool-billiards-0.1.13.jar.sha256) |
| DW Languages 0.1.0 | [dw-languages-0.1.0.jar](DW_Languages/dw-languages-0.1.0.jar) | [SHA-256](DW_Languages/dw-languages-0.1.0.jar.sha256) |

MC-Pool устанавливается на сервер и клиенты. DW Languages нужен серверу и требует RP Chat 0.1.16+; для совместной работы с рациями используйте DW Magic Connect 0.4.3+. Обоим модам нужны Minecraft 1.20.1, Java 17 и Fabric API. [Бильярд](../MC-Pool/README.md) · [Языки](../DW_Languages/README.md).

## RP Chat 0.1.16 и DW Magic Connect 0.4.3

Готовые JAR предоставлены пользователем и опубликованы без пересборки. Проверены версии в `fabric.mod.json`, целостность архивов и совпадение SHA-256 с предоставленными файлами. Игра не запускалась.

| Мод | JAR | Контрольная сумма |
| --- | --- | --- |
| RP Chat 0.1.16 | [rp-chat-0.1.16.jar](RP-Chat/rp-chat-0.1.16.jar) | [SHA-256](RP-Chat/rp-chat-0.1.16.jar.sha256) |
| DW Magic Connect 0.4.3 | [dw-magic-connect-0.4.3.jar](DW_Magic_Connect/dw-magic-connect-0.4.3.jar) | [SHA-256](DW_Magic_Connect/dw-magic-connect-0.4.3.jar.sha256) |

Minecraft 1.20.1, Java 17, Fabric Loader 0.16.10+ и Fabric API. Установите на сервер и клиенты, заменив прежние JAR. Рации требуют RP Chat 0.1.16+; этот комплект совместим с [DW Languages 0.1.0](DW_Languages/dw-languages-0.1.0.jar).

## Архив предыдущих сборок

DW Magic Connect 0.2.0, RP Chat 0.1.14 и RP Chat UI 0.3.7 собраны из исходников; более ранние JAR предоставлены пользователем. Для обновления раций замените 0.1.0 на 0.2.0 на сервере и клиентах.

| Мод | Файл | Версия | SHA-256 |
| --- | --- | --- | --- |
| DW Magic Connect | [dw-magic-connect-0.2.0.jar](DW_Magic_Connect/dw-magic-connect-0.2.0.jar) | 0.2.0 | `809FCA4A1207AA9160F6F179AA9A79952CBEAB5D8A4C18F8EE2DAB8CFF4737BA` |
| DW Magic Connect | [dw-magic-connect-0.1.0.jar](DW_Magic_Connect/dw-magic-connect-0.1.0.jar) | 0.1.0 | `8717540259DF348EBDF2ED1E7D47C275D488FCA3A1882D9FF5C000B0E7299990` |
| RP Chat | [rp-chat-0.1.13.jar](RP-Chat/rp-chat-0.1.13.jar) | 0.1.13 | `CF403871111B520A68BBA55D56C40A98241CD105FC968C543431AF06FCD8B605` |

Все JAR соответствуют Minecraft 1.20.1 и Java 17; DW Magic Connect требует RP Chat `>=0.1.13`.

Обновление чата: [описание и установка](CHAT-UPDATE.md).

| Новый мод | Файл | SHA-256 |
| --- | --- | --- |
| RP Chat 0.1.14 | [rp-chat-0.1.14.jar](RP-Chat/rp-chat-0.1.14.jar) | 8A23A261AC03301A3E0D356558019828FF224AD749A31FCB8307E1F13150FC0E |
| RP Chat UI 0.3.7 | [rp-chat-ui-0.3.7.jar](RP-Chat-UI/rp-chat-ui-0.3.7.jar) | 6F0A22474D5C8C7622F632C7DB81FA3FBC709761E37290D30A3C657AE1BC701B |

## DW Magic Connect 0.4.0

[dw-magic-connect-0.4.0.jar](DW_Magic_Connect/dw-magic-connect-0.4.0.jar) — SHA-256: BABE857E6F482563DCA310BB9646163886CCA6201DF934E64B47E996658A2E00

Один канал, автосохранение, управление кристаллом и боковыми клавишами, громкая связь в инвентаре и рамках. Радиус 1–10 блоков. Требует RP Chat 0.1.15+; замените рацию на сервере и клиентах. [Описание](../DW_Magic_Connect/README.md).

## DW Magic Connect 0.4.1

[dw-magic-connect-0.4.1.jar](DW_Magic_Connect/dw-magic-connect-0.4.1.jar) — SHA-256: ED145C120741D75DB64FCEF2F558E80FE9251AF1ED075ED1EB3F932232E4BEAE

Золотые оправы боковых кнопок, руна громкой связи, анимированное свечение и дымка при включении. Без подписей статуса/радиуса. Максимальный радиус 18 блоков; по умолчанию 10. Обновите мод на сервере и клиентах.

## DW Magic Connect 0.4.2

[dw-magic-connect-0.4.2.jar](DW_Magic_Connect/dw-magic-connect-0.4.2.jar) — SHA-256: 34609227334898A75FB34AED3B0FF38019CD52D8BE49211048E069D1083C1826

Новый прозрачный атлас золотых стрелок и оправ по референсу. Постоянная яркость UI, отдельные аддитивные FX поверх интерфейса. Minecraft не запускался; визуальная проверка в игре требуется.
