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
SHUTTER_ROOF = "bloodborne_dw:block/" + BLOCK + "addon/roof_1"
SHUTTER_HEADER = "bloodborne_dw:block/city_followup/shutter_upper_frame"


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
    target = (image(base, profile, texture_path("bloodborne_dw:block/" + BLOCK + reference))
              if isinstance(reference, str) else reference)
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


def shutter_header(base):
    profile = "alt"
    source = image(base, profile, texture_path(SHUTTER_ROOF))
    assert source.size == (128, 128)
    # Cover the masonry and the light stepped rim of the arch. The left
    # and right edges of the atlas island have slightly different widths.
    widths = ((19, 19), (19, 19), (7, 8), (5, 6), (3, 4), (2, 4),
              (1, 3), (1, 3), (1, 3), (1, 3), (1, 3))
    stone = []
    for y, (left, right) in enumerate(widths, start=81):
        for x in range(95, 114):
            if (x < 95 + left or x >= 114 - right) and source.getpixel((x, y))[0] >= 100:
                stone.append((x, y, x + 1, y + 1))
    reference = image(base, profile, texture_path("bloodborne_dw:block/" + BLOCK + "stone_bricks"))
    reference.putdata([(*(round(value * 0.94) for value in pixel[:3]), pixel[3])
                       for pixel in reference.getdata()])
    result = transfer(source, reference, stone)
    assert result.getchannel("A").tobytes() == source.getchannel("A").tobytes()
    result.save(PROFILES / profile / texture_path(SHUTTER_HEADER))

    for name in ("shutter_0", "shutter_1"):
        path = PROFILES / profile / f"{NAMESPACE}/models/interactions/{name}.json"
        model = json.loads(path.read_bytes())
        model["textures"]["city_header"] = SHUTTER_HEADER
        changed = 0
        for element in model["elements"][3:]:
            for face in element["faces"].values():
                if face["texture"] != "#0":
                    continue
                u0, v0, u1, v1 = face["uv"]
                left, right = sorted((u0 * 8, u1 * 8))
                top, bottom = sorted((v0 * 8, v1 * 8))
                if any(left < x1 and x0 < right and top < y1 and y0 < bottom for x0, y0, x1, y1 in stone):
                    face["texture"] = "#city_header"
                    changed += 1
        assert changed > 0, name
        path.write_text(json.dumps(model, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")


def main():
    with ZipFile(ROOT / "baseline/mod.jar") as base:
        for model in ("shutter_0", "shutter_1"):
            change_model(base, "alt", f"interactions/{model}",
                         lambda e: (e["from"], e["to"]) == SILL, "stone_bricks")
        shutter_header(base)
        for model in ("addon/wall_deco_5", "addon/wall_deco_6", "crimson_trapdoor_top"):
            change_model(base, "alt", BLOCK + model, lambda e: True, "stone_bricks")
        for profile in ("original", "alt"):
            adjacent = image(base, profile, texture_path("bloodborne_dw:block/" + BLOCK + "addon/spirelamp_0028"))
            assert adjacent.size == (128, 128)
            front = adjacent.crop((64, 0, 80, 8))
            # Both corbel models use shade:false. Match their lit neighboring rail.
            front.putdata([(*(round(value * 0.76) for value in pixel[:3]), pixel[3])
                           for pixel in front.getdata()])
            for model in ("sandstone_stairs_bottom", "sandstone_stairs_outer"):
                change_model(base, profile, BLOCK + "stairs/" + model, lambda e: True, front)


if __name__ == "__main__":
    main()
