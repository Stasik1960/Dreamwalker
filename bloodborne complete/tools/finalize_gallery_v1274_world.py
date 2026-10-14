"""Keep accepted navigation and describe only the scoped v12.7.4 repairs."""
from pathlib import Path
import json
import sys
from world_io import read_nbt, write_nbt, compound, Tag

ROOT = Path(__file__).resolve().parents[1]
exec(compile((ROOT / "tools/finalize_gallery_v1273_world.py").read_text(encoding="utf8"),
             str(ROOT / "tools/finalize_gallery_v1273_world.py"), "exec"),
     {"__file__": __file__, "__name__": "__main__"})

world = Path(sys.argv[sys.argv.index("--world") + 1])
manifest_path = Path(sys.argv[sys.argv.index("--manifest") + 1])
readme = world / "README-RU.txt"
text = readme.read_text(encoding="utf-8-sig").replace("v12.7.3", "v12.7.4")
text += "\n\nДверь90986: открывается только одна центральная створка; боковые панели, каменная кладка и верхняя арка неподвижны. Закрытый вид сохраняет исходную модель и монтаж. В секции doors добавлен отдельный открытый образец. Галерея создана заново с актуальными сохранёнными коллизиями, на прежней проверенной подготовке города без повторного сканирования исходного мира.\n"
readme.write_text(text, encoding="utf8")
level = read_nbt(world / "level.dat")
compound(compound(level.root)["Data"])["LevelName"] = Tag(8, "Dreamwalker BB v12.7.4 — галерея и город")
write_nbt(world / "level.dat", level)
manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
manifest["worldName"] = "Dreamwalker-BB-v12.7.4-Gallery"
manifest["displayRevision"] = "V12.7.4"
for path in (manifest_path, world / "gallery-manifest.json"):
    path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
pack = world / "datapacks/dw_gallery_entry/pack.mcmeta"
document = json.loads(pack.read_text(encoding="utf8"))
document["pack"]["description"] = "Dreamwalker BB v12.7.4: галерея, рабочие двери, фрагмент города"
pack.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
