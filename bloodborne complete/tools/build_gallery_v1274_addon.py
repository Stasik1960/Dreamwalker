"""Build the same reviewed generic author into fresh v12.7.4-only outputs."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "tools/build_gallery_v1271_addon.py").read_text(encoding="utf8")
source = source.replace("build/v1271-gallery-addon", "build/v1274-gallery-addon")
source = source.replace("dreamwalker-v1271-gallery-author", "dreamwalker-v1274-gallery-author")
source = source.replace("1.2.7.1", "1.2.7.4")
needle = "        java_source=output/'java/dev/dreamwalker/bloodbornedw/gallery/FlatGalleryV1271Author.java'"
assert source.count(needle) == 1
main_line = 'if(e.state.getBlock() instanceof CompositeRootBlock imported && imported.spec.simplePhysicalCollision)payload.putInt(ImportedTypeAliases.VERSION_KEY,1);'
current_type_line = 'if(e.state.getBlock() instanceof CompositeRootBlock imported && (imported.spec.simplePhysicalCollision || imported.spec.functionalDoorPhysics))payload.putInt(ImportedTypeAliases.VERSION_KEY,1);'
main_with_optics = current_type_line + '\n                if(source.has("manualDecorativeOptics")&&source.get("manualDecorativeOptics").getAsBoolean()&&e.state.getBlock() instanceof CompositeRootBlock&&!ImportedCompactBlock.compact(e.state)&&Registries.BLOCK.getId(e.state.getBlock()).getNamespace().equals("bloodborne_dw")&&Registries.BLOCK.getId(e.state.getBlock()).getPath().startsWith("source_"))payload.putInt("DecorativePlacementOptics",1);'
source = source.replace(needle, "        author=replace(author," + repr(main_line) + "," + repr(main_with_optics) + ")\n" + needle)
exec(compile(source, str(ROOT / "tools/build_gallery_v1271_addon.py"), "exec"),
     {"__file__": __file__, "__name__": "__main__"})
