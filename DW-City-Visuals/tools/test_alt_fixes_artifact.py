"""Audit the authored ALT assets and Whiteness in the combined release JAR."""
from argparse import ArgumentParser
from hashlib import sha256
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile
import json

from PIL import Image

ALT = "dw_city_visuals/alt/"
ASSETS = "assets/bloodborne_dw/"
EVIDENCE = Path(__file__).resolve().parents[1] / "validation/alt-fixes-white"


def digest(data):
    return sha256(data).hexdigest()


def pixels(data):
    with Image.open(BytesIO(data)) as texture:
        return texture.convert("RGBA")


def sprite_path(identifier):
    namespace, path = identifier.split(":", 1)
    return f"assets/{namespace}/textures/{path}.png"


def main():
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--previous", type=Path, required=True)
    args = parser.parse_args()

    sources = json.loads((EVIDENCE / "source-materials.json").read_text(encoding="utf-8"))
    native = json.loads((EVIDENCE / "native-materials.json").read_text(encoding="utf-8"))
    static = json.loads((EVIDENCE.parent / "alt-fixes-scenes/materials.json").read_text(encoding="utf-8"))
    with ZipFile(args.jar) as jar, ZipFile(args.previous) as previous:
        index = set(json.loads(jar.read(ASSETS + "visuals/resource-index.json"))["alt"])
        names = set(jar.namelist())
        assert jar.read("bloodborne_dw/rgb-phase1.json") == previous.read("bloodborne_dw/rgb-phase1.json")
        inspected = set()

        def inspect(base_path, base_hash, target_id, target_hash):
            path = sprite_path(target_id)
            base = previous.read(base_path)
            target = jar.read(ALT + path)
            assert path in index and path not in names, path
            assert digest(base) == base_hash and digest(target) == target_hash, path
            assert jar.read(base_path) == base, base_path
            before, after = pixels(base), pixels(target)
            assert before.size == after.size and before.tobytes() != after.tobytes(), path
            assert before.getchannel("A").tobytes() == after.getchannel("A").tobytes(), path
            inspected.add(path)

        for row in sources.values():
            inspect(row["source"], row["sourceSha256"], row["altSprite"], row["altSha256"])
        for scene in native.values():
            for row in scene["materials"]:
                inspect(row["baseAltPath"], row["baseSha256"], row["targetSprite"], row["targetSha256"])

        for row in static["cases"]:
            base_path = row["sourceFile"].replace("\\", "/").removeprefix("src/main/resources/")
            inspect(base_path, row["sourceSha256"], row["targetSprite"], row["targetSha256"])
            model = row["model"]
            assert jar.read(model) == previous.read(model)
            assert digest(jar.read(model)) == row["modelSha256"]

        assert inspected == {p for p in index if p.startswith(ASSETS + "textures/block/city_altfix/")}
        ornament = sources["15"]
        model = ASSETS + ornament["model"]
        original = jar.read(model)
        authored = jar.read(ALT + model)
        assert model in index and original == previous.read(model)
        assert digest(original) == ornament["originalModelSha256"]
        assert digest(authored) == ornament["altModelSha256"]
        expected = json.loads(original)
        expected["textures"]["cross"] = ornament["altSprite"]
        assert json.loads(authored) == expected

        white = "dw_whiteness"
        blockstate = json.loads(jar.read(ASSETS + f"blockstates/{white}.json"))
        model_id = f"bloodborne_dw:block/{white}"
        assert blockstate["variants"][""]["model"] == model_id
        block_model = json.loads(jar.read(ASSETS + f"models/block/{white}.json"))
        item_model = json.loads(jar.read(ASSETS + f"models/item/{white}.json"))
        assert item_model["parent"] == model_id and block_model["textures"]["all"] == model_id
        assert block_model["ambientocclusion"] is False
        assert pixels(jar.read(sprite_path(model_id))).getextrema() == ((255, 255),) * 4
        ru = json.loads(jar.read(ASSETS + "lang/ru_ru.json"))
        assert ru[f"block.bloodborne_dw.{white}"] == ru[f"item.bloodborne_dw.{white}"] == "Белизна"
        entrypoints = json.loads(jar.read("fabric.mod.json"))["entrypoints"]
        for side, entrypoint in (
            ("main", "dev.dreamwalker.bloodbornedw.architecture.WhitenessBlock"),
            ("client", "dev.dreamwalker.bloodbornedw.visual.WhitenessClient"),
        ):
            assert entrypoint in entrypoints[side]
            assert entrypoint.replace(".", "/") + ".class" in names

        for info in previous.infolist():
            if not info.is_dir() and info.filename.startswith(ALT) and info.filename != ALT + model:
                current = jar.getinfo(info.filename)
                assert (current.CRC, current.file_size) == (info.CRC, info.file_size), info.filename

        protected = {info.filename for info in previous.infolist() if not info.is_dir()
                     and info.filename.endswith((".png", ".json", ".mcmeta"))
                     and any(token in info.filename.lower() for token in ("water", "darkness"))}
        for name in protected:
            assert jar.read(name) == previous.read(name), name
        print(f"ALT material and Whiteness audit PASS: {len(inspected)} reviewed sprites, "
              f"{len(protected)} water and Darkness resources identical")


if __name__ == "__main__":
    main()
