# Catalog B — первая партия: 12 source assemblies

Откройте `index.html` в обычном браузере или `batch-01-contact.png`.
Исходник — проверенный source-world.zip, не converted map. Manifest партии:
`batch-01-manifest.json`; основной authoritative файл: `docs/manual-source-assemblies.json`.

Номер 1,2,3… обозначает **source block cell**, не F-family и не отдельный полигон.
Одна cell может включать несколько model applications. Нумерация видна на exploded
preview и в таблице source ID + properties + relative XYZ. Показаны ракурсы сверху,
сбоку и наклонный; отдельный CONTEXT не включён в assembled geometry.

Отвечайте по одной строке (ID/номера ниже условные):

```text
C001 OBJECT: 1+2+3
C002 OBJECT: 1+2; 3=CONTEXT
C003 SPLIT: 1+2 / 3+4
C004 CONNECTED
C005 NOT_OBJECT
C006 NEEDS_REVIEW: чего не хватает или что лишнее
```

Если компонента нет в предложении, опишите его через source ID/координату из
CONTEXT; не нужно подтверждать неполный объект как правильный. OBJECT подтверждает
только явно названные номера; остальные необходимо явно отнести к CONTEXT или
отдельному объекту. Ответы пока переносит разработчик с явной проверкой; автоматический
apply/parser Catalog B намеренно не реализован в этом ограниченном checkpoint.

Все границы предварительные. Здесь могут быть группа соседних самостоятельных
вещей или часть большего prefab. Требуется ручная курация, а не автоматическое OK.
Повторы считаются только в одном явно указанном region, не во всём мире. Выбор
случайного visual model приближённый и помечен в карточке. Mirrors и точный RNG
не поддерживаются; runtime/collision не менялись.
