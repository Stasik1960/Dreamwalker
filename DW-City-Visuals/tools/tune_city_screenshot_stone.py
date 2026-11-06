"""Tune the column corner using the adjacent stone in each profile."""
import json
from math import ceil, floor
from zipfile import ZipFile

from repair_facade_assets import transfer
from tune_city_followup_materials import BLOCK, NAMESPACE, PROFILES, ROOT, image, texture_path


def pixels(uv, width, height):
    u0, v0, u1, v1 = uv
    return (floor(min(u0, u1) * width / 16), floor(min(v0, v1) * height / 16),
            ceil(max(u0, u1) * width / 16), ceil(max(v0, v1) * height / 16))


def scoped_sprites(base, profile, model_name, stem, reference, cap_reference):
    path = f"{NAMESPACE}/models/{BLOCK}{model_name}.json"
    overlay = PROFILES / profile / path
    model = json.loads(overlay.read_bytes() if overlay.is_file() else base.read(path))
    zones = {}
    for element in model["elements"]:
        for face in element["faces"].values():
            zones.setdefault(face["texture"].removeprefix("#"), []).append(face["uv"])
    for key, rects in zones.items():
        original = image(base, profile, texture_path(model["textures"][key]))
        changed = transfer(original, reference, [pixels(uv, *original.size) for uv in rects])
        if key == "0":
            cap = model["elements"][3]["faces"]["up"]["uv"]
            changed = transfer(changed, cap_reference, [pixels(cap, *original.size)])
        assert changed.size == original.size
        assert changed.getchannel("A").tobytes() == original.getchannel("A").tobytes()
        output = PROFILES / profile / f"{NAMESPACE}/textures/block/city_followup/{stem}_{key}.png"
        output.parent.mkdir(parents=True, exist_ok=True)
        changed.save(output)


def main():
    with ZipFile(ROOT / "baseline/mod.jar") as base:
        for profile in ("original", "alt"):
            adjacent = image(base, profile, texture_path("bloodborne_dw:block/" + BLOCK + "addon/spirelamp_0028"))
            cap = adjacent.crop(pixels((10, 10, 12, 12.625), *adjacent.size))

            granite = image(base, profile, texture_path("bloodborne_dw:block/" + BLOCK + "granite"))
            granite.putdata([(*(round(value * 0.92) for value in pixel[:3]), pixel[3])
                             for pixel in granite.getdata()])
            scoped_sprites(base, profile, "_wall_dec_out", "column_trim", granite, cap)


if __name__ == "__main__":
    main()
