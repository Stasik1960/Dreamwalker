"""Keep accepted gallery navigation; describe only this release's requested repairs."""
from pathlib import Path
import sys,json
ROOT=Path(__file__).resolve().parents[1]
exec(compile((ROOT/'tools/finalize_gallery_v1271_world.py').read_text(encoding='utf8'),str(ROOT/'tools/finalize_gallery_v1271_world.py'),'exec'),{'__file__':str(ROOT/'tools/finalize_gallery_v1271_world.py'),'__name__':'__main__'})
world=Path(sys.argv[sys.argv.index('--world')+1]);readme=world/'README-RU.txt'
text=readme.read_text(encoding='utf-8-sig').replace('v12.7.1','v12.7.3').replace('539 новых архитектурных типов','537 новых архитектурных типов')
text+='\n\nФинальные проверки v12.7.3: две повторяющиеся позиции90025 и90599 скрыты из каталога, их сохранённые ID и модели города остаются доступными. Дверь90960 стала одностворчатой рабочей дверью; дополнительный образец показывает открытый вид. Проверенные плоские накладки при ручной установке получают небольшой зазор над исходной поверхностью. Исходные пиксели текстур не перекрашены.\n\n/bb no-collide разрешает следующее успешное размещение обычного предмета мода в пересечении с коллизиями. Для DW-барьера93230 и DW-света93231 команда отключена: их правило действует постоянно. Существующие самостоятельные корни и чужие данные сохраняются.\n\nsource_invisible_carrier — скрытый технический носитель исходной физики, света и прочих свойств ванильной карты, когда внешний вид уже находится в соседней составной модели. Это не строительный предмет. Не удаляйте такие ячейки как отсутствующие декоративные блоки.\n'
readme.write_text(text,encoding='utf8')
