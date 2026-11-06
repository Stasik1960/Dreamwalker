"""Regenerate the city follow-up's model-scoped material overrides."""
from io import BytesIO
import json
from math import ceil, floor
from pathlib import Path
from zipfile import ZipFile

from PIL import Image

from repair_facade_assets import transfer


ROOT = Path(__file__).resolve().parents[1]
PROFILES = ROOT / "src/main/resources/dw_city_visuals"
NAMESPACE = "assets/bloodborne_dw"
BLOCK = "remaining/source/minecraft/block/"
SILL = ([-5, -14, 0], [21, -8, 8])


def texture_path(sprite):
    return f"{NAMESPACE}/textures/{sprite.split(':', 1)[1]}.png"


def image(base, profile, path):
    file = PROFILES / profile / path
    return Image.open(file if file.exists() else BytesIO(base.read(path))).convert("RGBA")


def change_model(base, profile, model_id, selected_faces, reference):
    path = f"{NAMESPACE}/models/{model_id}.json"
    model = json.loads(base.read(path))
    zones = {}
    for element in model["elements"]:
        if not selected_faces(element):
            continue
        for face in element["faces"].values():
            key = face["texture"].removeprefix("#")
            zones.setdefault(key, []).append(face["uv"])
            face["texture"] = "#city_" + key
    assert zones, model_id
    reference_path = texture_path("bloodborne_dw:block/" + BLOCK + reference)
    target = image(base, profile, reference_path)
    for key, rects in zones.items():
        sprite = model["textures"][key]
        source = image(base, profile, texture_path(sprite))
        width, height = source.size
        bounds = []
        for u0, v0, u1, v1 in rects:
            bounds.append((floor(min(u0, u1)*width/16), floor(min(v0, v1)*height/16),
                           ceil(max(u0, u1)*width/16), ceil(max(v0, v1)*height/16)))
        result = transfer(source, target, bounds)
        assert result.getchannel("A").tobytes() == source.getchannel("A").tobytes()
        identifier = f"bloodborne_dw:block/city_followup/{model_id.rsplit('/', 1)[-1]}_{key}"
        output = PROFILES / profile / texture_path(identifier)
        output.parent.mkdir(parents=True, exist_ok=True)
        result.save(output)
        model["textures"]["city_" + key] = identifier
    output = PROFILES / profile / path
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(model, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")


def main():
    with ZipFile(ROOT / "baseline/mod.jar") as base:
        for model in ("shutter_0", "shutter_1"):
            change_model(base, "alt", f"interactions/{model}",
                         lambda e: (e["from"], e["to"]) == SILL, "stone_bricks")
        for model in ("addon/wall_deco_5", "addon/wall_deco_6", "crimson_trapdoor_top"):
            change_model(base, "alt", BLOCK + model, lambda e: True, "stone_bricks")
        for profile in ("original", "alt"):
            for model in ("sandstone_stairs_bottom", "sandstone_stairs_outer"):
                change_model(base, profile, BLOCK + "stairs/" + model, lambda e: True, "stone_bricks")


if __name__ == "__main__":
    main()
