# Catalog B: source patterns и provisional visual families

Этот слой создаёт только документацию для ручного review. Никакого применения
решений, построения Contract V2, миграции мира или генерации JAR он не выполняет.

## Полный scope и carrier universe

`source_assembly_index.py` читает все 33 `ether/dimensions/eh_s2/yharnam/region/*.mca`
правильного ZIP (SHA-256 в manifest). Удалённые области того же dimension также
включены. Это 21 503 chunks, не только исходный r.-1.-1.

Carrier IDs — union исходных mappings и vanilla blockstates, затронутых исходным
pack: blockstate, model, parent, texture/animation dependencies с vanilla fallback.
Для каждого обнаруженного ID сохраняются реально встреченные полные properties,
число ячеек, примеры координат и dependency evidence. На уровне ID индекс намеренно
консервативный: учитывает все встреченные состояния carrier, даже если конкретная
ветка его blockstate использует обычную vanilla model. Названия/seed categories
не ограничивают этот universe. Существующие semantic hints используются только
для порядка/названий review; границы Fxxx и runtime mesh не импортируются.

SQLite под `build/source-assembly-v2` содержит координаты всех carrier cells.
Кэш привязан к SHA мира, pack, vanilla JAR, байтам mapping-файлов, dimension,
формату и контрольным суммам metadata/DB. Он не входит в Git.

## Две сигнатуры

- `exact_source_signature`: SHA от полных исходных ID/properties и relative XYZ.
  Anchor — минимальный source cell по Y, затем Z, затем X. Нормализуется только
  абсолютная позиция: никакой догадки, что vanilla axis/facing соответствует yaw
  переопределённой geometry. Каждая ориентация/layout остаётся отдельным pattern.
- `canonical_visual_family_signature`: textured polygons исходных model elements,
  model transforms и UV; нормализация translation и yaw 0/90/180/270. Учитываются
  все независимые blockstate alternatives. Точное geometry identity дополняется
  узким review-only сравнением положения одинаковых компонентных artworks.

Fuzzy rule: одинаковый multiset component artwork; один общий yaw с совпадением
ориентации всех alternatives; после удаления общего сдвига максимальное смещение
центра компонента <= min(0.51 блока, 8% меньшей диагонали двух layouts), разница
component-centre spans <=1.01 блока. Это **не вероятностный semantic classifier**.
Повторяющиеся artworks сопоставляются minimum-bottleneck perfect matching,
не направленным greedy nearest-neighbour; допуск симметричен для обеих моделей.
Mirrors не объединяются, если не эквивалентны разрешённому yaw. Разные partition
одной polygon union также могут остаться отдельными — алгоритм консервативный.

Группировка deterministic, относительно сохранённого representative, не transitive
closure: A≈B и B≈C не даёт автоматического A≈C. Старые C-ID имеют приоритет.
C005 сохранён как `VARIANT_OF C001`, вместе с исходными cells и history; C001
хранит A/B/C patterns с собственными exact signatures, anchor и match evidence.
Это alias **карточки review**, не удаление registry ID и не правило migration.
Ручное решение блокирует новый автоматический merge независимых ID. Уже
установленные decisionless aliases сохраняются при последующем review canonical ID.

## Граница кандидата и счётчики

Каждый наблюдаемый state сохраняется как single-cell template, включая строительные
материалы, пустую geometry и unresolved resources. Это НЕ число изолированных
одиночных предметов города. Число occurrences этого template равно числу ячеек.

Предложения multi-cell строятся по 26-соседству геометрически не-фоновых carriers;
tree artwork допускает vertical gap <=6 при horizontal radius <=1. Фоновые full
cubes и пустые models не склеиваются с каждым соседом. Избыточные clusters
(>24 cells или extents >9×18×9) отклоняются как конечные группы, но не из inventory.
Ранее опубликованные C-boundaries дополнительно ищутся точным state/layout matching
по всему dimension. Source connectivity — только предложение, а не доказательство
одной вещи. Поэтому соседние независимые props могут требовать SPLIT/CONTEXT.

Context извлекается из полного source index отдельно от components, внутри bounds
плюс один слой. Соседний carrier вне выбранной группы или отсутствующий доступный
контекст даёт `POSSIBLY_INCOMPLETE`. Он **никогда автоматически не добавляется** в
assembly. Контекст подробно собирается для текущей партии; вне неё границы ещё
не сертифицированы. Наличие всех carrier IDs не гарантирует, что cluster — объект.

`similar_count` canonical-карточки суммирует counts её точных patterns, а не число
доказанных непересекающихся logical objects. Single-cell inventory и multi-cell
proposals могут описывать те же физические ячейки. Нельзя суммировать их как число
объектов карты. Подробные rejected reasons и числа находятся в coverage JSON.

7 source patterns имеют `UNRESOLVED_VISUAL`: отсутствующий template_wall_tall,
невалидный original iron_trapdoor JSON и unresolved texture aliases dandelion /
potted spruce sapling. Их original assets не правились; записи с причинами явно
присутствуют в основном manifest и sidecar. Они не попадают в render batch.

## Review package

Batch-02 содержит 18 canonical cards с ограничением category diversity. Weighted
positional RNG Minecraft не эмулируется: отдельно показана **каждая независимая
alternative каждого source pattern**, с weight и model data. Cartesian product
multipart combinations не разворачивается. Primary assembly/мини-preview — лишь
схематическая комбинация первых apps, не точная реконструкция экземпляра мира.

Это offline renderer исходного pack + vanilla fallback; не игровой screenshot.
Alpha учитывается растеризатором, но geometry signatures не пытаются сравнивать
отрендеренную картинку с occlusion, animation timing или tint. Полигональная
эквивалентность не доказывает collision, placement или функциональное поведение.

`docs/manual-source-assemblies.json` — полный registry review. Gzip sidecars —
carrier index, exact patterns и отдельные single-cell IDs/signatures. Portable
batch manifest хранит только текущие карточки и их aliases, без копии всего
registry. Исторические batch-01 и Catalog A/Fxxx неизменны.
