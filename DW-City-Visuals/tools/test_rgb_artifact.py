"""Verify the combined RGB JAR offline against its pinned City Editor baseline."""
from hashlib import sha256
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile
import argparse
import json

from PIL import Image

ALT = "dw_city_visuals/alt/"
ORIGINAL = "dw_city_visuals/original/"
POLICY = "bloodborne_dw/rgb-phase1.json"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--previous", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    with ZipFile(args.jar) as jar, ZipFile(args.previous) as previous:
        assert jar.testzip() is None, "Invalid ZIP CRC"
        names = set(jar.namelist())
        old_names = set(previous.namelist())
        mod = json.loads(jar.read("fabric.mod.json"))
        assert mod["id"] == "bloodborne_dw" and mod["version"].endswith("-rgb.1")
        assert not any("dw-editorqa" in name or "/editorqa/" in name for name in names)
        item_model = json.loads(jar.read("assets/bloodborne_dw/models/item/nrp_pickaxe.json"))
        assert item_model["parent"] == "minecraft:item/handheld"
        namespace, sprite = item_model["textures"]["layer0"].split(":", 1)
        pixels = Image.open(BytesIO(jar.read(f"assets/{namespace}/textures/{sprite}.png"))).convert("RGBA")
        assert pixels.size == (16,16) and pixels.getchannel("A").getextrema() == (0,255)
        assert any(r > g+25 and b > g+10 and a == 255 for r, g, b, a in pixels.getdata())
        ru = json.loads(jar.read("assets/bloodborne_dw/lang/ru_ru.json"))
        assert ru["item.bloodborne_dw.nrp_pickaxe"] == "НРП-Кирка"
        registered = set(mod["entrypoints"]["main"])
        assert "dev.dreamwalker.bloodbornedw.item.NrpPickaxe" in registered
        configs = {m if isinstance(m,str) else m["config"] for m in mod["mixins"]}
        for config_name in ("dw-nrp-mining.mixins.json","dw-nrp-combat.mixins.json"):
            assert config_name in configs
            config = json.loads(jar.read(config_name))
            assert config["required"]
            for class_name in config.get("mixins",[]) + config.get("client",[]):
                path = config["package"].replace(".","/") + "/" + class_name + ".class"
                assert path in names, path
        protected = {name for name in old_names if name.startswith(ORIGINAL) and not name.endswith("/")}
        protected.update(name for name in old_names if name.startswith("assets/bloodborne_dw/native_grid/")
                         and not name.endswith("/"))
        for name in protected:
            assert jar.read(name) == previous.read(name), name
        policy = json.loads(jar.read(POLICY))
        assert policy["schema"] == 1
        materials = set()
        rgba_mips = 0
        for profile in policy["profiles"].values():
            for identifier, expected in profile["assets"].items():
                ns, resource = identifier.split(":", 1)
                path = f"assets/{ns}/{resource}"
                actual = next((name for name in (ALT + path, path) if name in names), None)
                if not expected:
                    assert actual is None, path
                else:
                    assert actual and sha256(jar.read(actual)).hexdigest() == expected, path
            for row in profile["paints"].values():
                neutral = row["neutral"]
                assert neutral not in materials
                materials.add(neutral)
                ns, texture = neutral.split(":", 1)
                assert f"{ALT}assets/{ns}/textures/{texture}.png" in names
                width, height = row["size"]
                rgba_mips += sum(max(1,width >> level) * max(1,height >> level) * 4
                                 for level in range(row["mipLevels"]+1))
        resource_names = {n for n in names if n.startswith(ALT) and "/textures/block/editor2_rgb/" in n and n.endswith(".png")}
        assert len(materials) == len(resource_names)
        result = {"status": "PASS", "profiles": len(policy["profiles"]), "neutralSprites": len(materials),
                  "nativeMeshes": len(policy["nativeAudit"]), "protectedFilesIdentical": len(protected),
                  "fullMipRgbaBytesEstimate": rgba_mips, "version": mod["version"],
                  "nrpPickaxeAssets": "PASS", "gameplay": "not run", "fpsAndStalls": "not measured"}
    args.report.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result))


if __name__ == "__main__":
    main()
