# Bloodborne Architecture — сборки

[Проект](../../Bloodborne-Blocks/README.md) · [Статус проверки](../../Bloodborne-Blocks/docs/RELEASE-STATUS.md) · [Общий каталог](../README.md)

## Текущий кандидат: 2.1.0-rc.1

**Для проверки на отдельной копии мира. Production-приёмка не завершена.**

| Файл / документ | Где получить |
| --- | --- |
| JAR rc.1 | [Проверенный CI artifact](https://github.com/Stasik1960/Dreamwalker/actions/runs/36336948341/artifacts/10937273847) — внутри обычный JAR и sources; для игры нужен обычный |
| Карта города rc.1 | [Bloodborne-City-2.1.0-rc.1.zip](../../Bloodborne-Blocks/releases/Bloodborne-Blocks/2.1.0-rc.1/Bloodborne-City-2.1.0-rc.1.zip) |
| Хэши JAR и результаты | [RELEASE-STATUS](../../Bloodborne-Blocks/docs/RELEASE-STATUS.md) |
| Хэш карты и протокол конвертации | [retired-composites-world.json](../../Bloodborne-Blocks/docs/release/evidence/rc1/retired-composites-world.json) |

Карта физически хранится во вложенной папке `Bloodborne-Blocks/releases/`;
ссылка выше ведёт именно к отслеживаемому файлу. Перемещения архивов в этой уборке не выполнялись.
CI artifact может требовать входа в GitHub и имеет срок хранения. Если он недоступен,
используйте [сборку из исходников](../../Bloodborne-Blocks/README.md#разработка-и-документация),
а не JAR старой beta под новую карту.

Копия MODDED-карты переведена в текущую сетку. По одобренной политике удалены
33 клетки с 23 отсутствующими composite ID. В итоговом census не осталось старых
`m_*` и неизвестных ID. Это офлайн-результат; реальный запуск/перезапуск сервера
и визуальная клиентская приёмка ещё нужны. Не заменяйте этими файлами единственную копию рабочего мира.

## Архив и отдельная repair-линия

- [Исторические описания и ссылки](HISTORY.md) — alpha, «Агония», галереи и прежние этапы.
- [beta.1](2.1.0-beta.1/README.md), [beta.2](2.1.0-beta.2/README.md), [beta.2 recovery](2.1.0-beta.2-city-recovery/README.md), [beta.3](2.1.0-beta.3-grid-physics/README.md) — исторические пакеты, не актуальная рекомендация.
- [Архив документации](../../Bloodborne-Blocks/docs/history/README.md) — исходные отчёты без переоценки их статуса.
- [repair/composite-preserving-grid](https://github.com/Stasik1960/Dreamwalker/tree/repair/composite-preserving-grid) — отдельные REPAIR TEST-сборки; не включены в `main` и не являются обновлением rc.1.

Мир, галерея и JAR должны относиться к одному проверяемому комплекту.
