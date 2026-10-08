# Аудит исходного ресурспака

Статус: полный файловый инвентарь и ресурсные связи; семантический каталог ещё не закреплён. Номера объектов не назначены.

Исходный архив: `C:\Users\vakir\Downloads\bbmc_v15_resource (1).zip`
SHA-256: `29b31e3744af5794ae95ebe4c5474e3b70dfb08c143355bce815c08ceef09a36`

Blockstates исходного пакета: **246**; модели блока: **1108**; предметные модели: **4**; отдельные CIT-модели: **9**; PNG assets: **400**.
Дополнительные vanilla carriers, чьи blockstate-файлы отсутствуют в пакете, но выбирают переопределённые модели/текстуры или наследуют источник: **320**. Их references учтены отдельно с origin=dependency. Невалидные JSON и нестандартные комментарии сохранены в файловом инвентаре и issues.
Правила variants: **2817**; multipart: **384**; правила со случайным выбором: **155**.

Пять JSON-отчётов сохраняют исходные selectors/AND/OR, порядок и веса моделей, x/y/uvlock, всю геометрию и UV, наследование, display, texture variables, PNG alpha, .mcmeta, tint/cullface, CIT и шейдерные параметры.

Ресурсные ссылки на модели без файла в переданных архивах: **10**. Без vanilla JAR это непроверенные внешние зависимости, а не установленный дефект. Отсутствующие текстурные ссылки: **5**.
Группы побайтно одинаковых JSON: **14**; группы одинаковой разрешённой render structure: **16**. Это доказательства для анализа; поведение и установка должны быть сравнены до объединения объектов.

## Источник специфического рендера

CTM-пути в архиве: **0**. Эмиссивные PNG `_e`: **3**. Сохраняются точные .properties, включая `layer.translucent=flower_pot, warped_button`, `layer.solid=acacia_trapdoor,gold_block` и `suffix.emissive=_e`.
CIT связан с предметами и именами NBT; его не следует объявлять архитектурой. Core shader `rendertype_text.vsh` меняет отображение текста/маркеров. Прямой перенос этих настроек в обычный Fabric не доказан.

## Границы доказательства

Геометрические bounds описывают видимый объём, но не сплошную коллизию и не маску установки. Парные грани нулевой толщины ещё требуют проверки отсечения с обеих сторон в игре. Модели без blockstate-ссылок остаются кандидатами: законченные объекты, части, шаблоны и черновики разделяются по назначению и связи с картой.

Порядок случайных моделей и веса сохранены. Фактический выбранный вариант определяется из исходной позиции и renderer Minecraft 1.18.2 до сдвига pivot/группировки; использование алгоритма 1.20.1 требует подтверждения эквивалентности. Tint требует регистрации подходящих color providers, а прозрачность — слоя Fabric и игровой проверки. Геометрия, UV, display и анимации остаются ресурсами. Точный список решений записан в migration_requirements.

## Воспроизведение

```powershell
& 'C:/Users/vakir/miniconda3/python.exe' 'bloodborne complete/tools/analyze_resources.py' --pack 'C:/Users/vakir/Downloads/bbmc_v15_resource (1).zip' --dependency 'bloodborne complete/inputs/extracted/vanilla-1.18.2/client.jar' --target-vanilla 'C:/Users/vakir/Limacina/project/dw/1.20.1.jar' --world-states 'bloodborne complete/reports/WORLD_AUDIT.json' --out 'bloodborne complete/reports'
```

Источник карты — Minecraft 1.18.2. `--dependency` задаёт исходный vanilla client и прочие ресурсы в порядке приоритета после пакета. Целевой 1.20.1 проверяется отдельно через `--target-vanilla`; различия не подменяют исходник. `--alpha-cache` может использовать предыдущий RESOURCE_AUDIT_TEXTURES.json: сохраняются только результаты PNG alpha с совпадающим точным SHA файла. Формат статистики: массив `states` с name/properties/count/samples либо словарь canonical-state → count/record.
