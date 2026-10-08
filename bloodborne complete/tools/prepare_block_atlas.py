"""Register imported own-namespace sprites without replacing any vanilla atlas sources."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/architecture/resources"
ATLAS = RES / "assets/minecraft/atlases/blocks.json"
REPORT = ROOT / "reports/BLOCK_ATLAS_ADDITIVE_IMPORT.json"
JDK = Path("C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma")
MC = Path("C:/Users/vakir/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2/minecraft-merged-1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2.jar")
SOURCE = Path("C:/Users/vakir/Downloads/bbmc_v15_resource (1).zip")


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Check existing metadata rather than regenerate it")
    args = parser.parse_args()
    textures = sorted((RES / "assets/bloodborne_dw/textures").rglob("*.png"))
    sprites = {"bloodborne_dw:"+p.relative_to(RES / "assets/bloodborne_dw/textures").with_suffix("").as_posix(): p for p in textures}
    assert sprites
    source_matches = {}
    with zipfile.ZipFile(SOURCE) as source:
        for sprite, path in sprites.items():
            local = sprite.split(":", 1)[1]
            if local.startswith("base/source/minecraft/"):
                original = "assets/minecraft/textures/" + local.removeprefix("base/source/minecraft/") + ".png"
            elif local.startswith("wall/source/minecraft/"):
                original = "assets/minecraft/textures/" + local.removeprefix("wall/source/minecraft/") + ".png"
            elif local.startswith("tree_source/"):
                original = "assets/minecraft/textures/" + local.removeprefix("tree_source/") + ".png"
            else:
                raise AssertionError("New sprite family needs explicit provenance mapping: " + local)
            assert path.read_bytes() == source.read(original), "Source PNG bytes changed: " + sprite
            source_matches[sprite] = original
    expected = {"sources": [{"type": "minecraft:single", "resource": sprite} for sprite in sprites]}
    if not args.check:
        ATLAS.parent.mkdir(parents=True, exist_ok=True)
        ATLAS.write_text(json.dumps(expected, indent=2)+"\n", encoding="utf-8")
    assert json.loads(ATLAS.read_text(encoding="utf-8")) == expected
    assert all(row["resource"].startswith("bloodborne_dw:") and "sprite" not in row for row in expected["sources"])
    minecraft_changes = sorted(p.relative_to(RES).as_posix() for p in (RES / "assets/minecraft").rglob("*") if p.is_file())
    assert minecraft_changes == ["assets/minecraft/atlases/blocks.json"], "Architectural vanilla models/textures must remain absent"
    references = {}
    for model in sorted((RES / "assets/bloodborne_dw/models").rglob("*.json")):
        body = json.loads(model.read_text(encoding="utf-8"))
        for key, value in body.get("textures", {}).items():
            if value.startswith("bloodborne_dw:"):
                assert value in sprites, "Referenced own sprite has no PNG/atlas entry: " + value
                references.setdefault(value, []).append({"model": model.relative_to(RES).as_posix(), "texture_key": key})
    bytecode = subprocess.run([str(JDK / "bin/javap.exe"), "-classpath", str(MC), "-c", "-p", "net.minecraft.client.texture.atlas.AtlasLoader"], check=True, capture_output=True, text=True).stdout
    assert "ResourceManager.getAllResources" in bytecode and "java/util/List.addAll" in bytecode
    evidence = ROOT / "reports/BLOCK_ATLAS_LOADER_1_20_1_BYTECODE.txt"
    evidence.write_text(bytecode, encoding="utf-8")
    with zipfile.ZipFile(MC) as archive:
        vanilla = archive.read("assets/minecraft/atlases/blocks.json")
    vanilla_sources = json.loads(vanilla)["sources"]
    assert any(row.get("type") == "directory" and row.get("source") == "block" for row in vanilla_sources)
    result = {"schema": "dreamwalker-additive-block-atlas-v1", "status": "PASS_OFFLINE_METADATA_CLIENT_CHECK_REQUIRED",
              "metadata": str(ATLAS.relative_to(ROOT)), "metadata_sha256": sha(ATLAS.read_bytes()),
              "atlas_target": "minecraft:blocks", "added_sprite_count": len(sprites), "only_added_namespace": "bloodborne_dw",
              "merge_proof": "Exact1.20.1 AtlasLoader.of iterates ResourceManager.getAllResources and List.addAll; this source list adds own single sprites to existing atlas sources",
              "vanilla_sources": vanilla_sources, "vanilla_metadata_sha256": sha(vanilla), "minecraft_model_or_texture_overrides": [],
              "bytecode_evidence": str(evidence.relative_to(ROOT)), "bytecode_evidence_sha256": sha(evidence.read_bytes()),
              "sprites": [{"id": sprite, "png": str(path.relative_to(ROOT)), "png_sha256": sha(path.read_bytes()), "original_resource_entry": source_matches[sprite], "original_png_bytes_equal": True, "references": references.get(sprite, [])} for sprite,path in sprites.items()],
              "generator_contract": "Run after architecture assets are generated; --check rejects missing PNGs, uncovered own texture references or any vanilla architectural model/texture override",
              "client_evidence": "NOT_RUN; require all authored baked models free of missingno and vanilla STONE sprite retained"}
    REPORT.write_text(json.dumps(result, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
    print(json.dumps({"status": result["status"], "sprites": len(sprites), "own_texture_references": sum(map(len,references.values()))}))


if __name__ == "__main__":
    main()
