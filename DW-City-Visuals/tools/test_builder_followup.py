"""Check the follow-up's generated visuals against the previous release without a new JAR."""
import argparse
import json
from pathlib import Path
from zipfile import ZipFile

from PIL import ImageChops, ImageEnhance

from repair_builder_followup import (COMPOSITES, DARKEN, REGISTRIES, RETIRED_SHUTTERS, RETIRED_SURROUND,
                                     SHUTTER_CROWN, SHUTTER_FRAME_BOUNDS, SHUTTER_PAD,
                                     Source, resolve_texture, uv_rect)


def opaque(source, model, element, directions, profile):
    for direction in directions:
        face = element["faces"][direction]
        texture = source.image(resolve_texture(model, face["texture"]), profile)
        assert face["uv"] == [0, 0, 16, 16]
        assert texture.getchannel("A").getextrema() == (255, 255), direction


def geometry(model):
    return [
        {**element, "faces": {face: {k: v for k, v in description.items() if k != "texture"}
                             for face, description in element["faces"].items()}}
        for element in model["elements"]
    ]


def shutter_pixels(source, model, face, profile, size):
    atlas = source.image(resolve_texture(model, face["texture"]), profile)
    width, height = size
    padded = (SHUTTER_PAD, SHUTTER_PAD, SHUTTER_PAD + width, SHUTTER_PAD + height)
    assert atlas.getchannel("A").getbbox() is not None
    assert uv_rect(face, atlas.size) == padded
    visible = atlas.crop(padded)
    assert atlas.getchannel("A").histogram()[0] == (
        atlas.width * atlas.height - width * height + visible.getchannel("A").histogram()[0])
    return visible


def check_shutter(after, before, old_part, old, new, profile):
    if old_part["model"].endswith("/shutter_glass"):
        (old_glass,), (new_glass,) = old["elements"], new["elements"]
        assert old_glass["from"] == new_glass["from"] and old_glass["to"] == new_glass["to"]
        old_face = old_glass["faces"]["north"]
        image = before.image(resolve_texture(old, old_face["texture"]), profile)
        old_pane = image.crop(uv_rect(old_face, image.size))
        pane = shutter_pixels(after, new, new_glass["faces"]["north"], profile, old_pane.size)
        for y in range(pane.height):
            for x in range(pane.width):
                atlas_x = SHUTTER_FRAME_BOUNDS[0] + 1 + x
                retain = y + 2 >= len(SHUTTER_CROWN) or SHUTTER_CROWN[y + 2][0] <= atlas_x <= SHUTTER_CROWN[y + 2][1]
                assert pane.getpixel((x, y)) == (old_pane.getpixel((x, y)) if retain else (0, 0, 0, 0))
        return

    assert len(new["elements"]) > len(old["elements"])
    if old_part["model"].endswith("/shutter_0"):
        for old_leaf, new_leaf in zip(old["elements"][:2], new["elements"][:2], strict=True):
            for direction in ("north", "south"):
                assert old_leaf["faces"][direction] == new_leaf["faces"][direction]
            assert new_leaf["faces"]["up"]["uv"][::2] == new_leaf["faces"]["north"]["uv"][::2][::-1]
        assert old["elements"][2] == new["elements"][2]
    else:
        assert old["elements"][:3] == new["elements"][:3]
    src = before.image(resolve_texture(old, "#0"), profile).crop(SHUTTER_FRAME_BOUNDS)
    face = new["elements"][3]["faces"]["north"]
    frame = shutter_pixels(after, new, face, profile, src.size)
    x0 = SHUTTER_FRAME_BOUNDS[0]
    prior_face = old["elements"][3]["faces"]["north"]
    prior = before.image(resolve_texture(old, prior_face["texture"]), profile).crop(SHUTTER_FRAME_BOUNDS)
    filled_alt = 0
    for y in range(src.height):
        for x in range(src.width):
            retain = y >= len(SHUTTER_CROWN) or SHUTTER_CROWN[y][0] <= x0 + x <= SHUTTER_CROWN[y][1]
            pixel = (x, y)
            base = src.getpixel(pixel)
            actual = frame.getpixel(pixel)
            assert actual[3] == (base[3] if retain else 0)
            if actual[3] and profile == "alt":
                old_tint = prior.getpixel(pixel)
                if old_tint != base:
                    assert actual == old_tint
                elif actual != base:
                    filled_alt += 1
    if profile == "alt":
        assert filled_alt > 0


def check(jar, resources):
    before = Source(jar, ())
    after = Source(jar, (resources,))
    changed = 0
    for number, registry in REGISTRIES.items():
        path = COMPOSITES + registry + ".json"
        original = json.loads(before.read(path))
        updated = json.loads(after.read(path))
        expected_variants = original["variants"][:1] if number == "93241" else original["variants"]
        for old_variant, new_variant in zip(expected_variants, updated["variants"], strict=True):
            for key, old_pose in old_variant["poses"].items():
                new_pose = new_variant["poses"][key]
                ignored = {"parts", "collision", "selection"} if number == "90986" else {"parts"}
                assert {k: v for k, v in old_pose.items() if k not in ignored} == {
                    k: v for k, v in new_pose.items() if k not in ignored}, (number, key)
                if number == "90986":
                    for field in ("collision", "selection"):
                        old_boxes, fitted = old_pose[field], new_pose[field]
                        assert [box["to"][1] for box in fitted] == [48, 48, 48, 48.34823587, 48.34823587, 34]
                        assert fitted[2]["from"][1] == 34
                        assert [box[corner][::2] for box in fitted for corner in ("from", "to")] == [
                            box[corner][::2] for box in old_boxes for corner in ("from", "to")]
                old_parts = old_pose["parts"]
                new_parts = new_pose["parts"]
                if number == "93241":
                    old_parts = [part for part in old_parts if not part["model"].endswith("/shutter_backdrop")]
                if number == "90001" and key == "closed":
                    assert old_parts == new_parts
                for old_part, new_part in zip(old_parts, new_parts, strict=True):
                    assert {k: v for k, v in old_part.items() if k not in ("model", "altModel")} == {
                        k: v for k, v in new_part.items() if k not in ("model", "altModel")}
                    if old_part == new_part:
                        assert number == "90001"
                        continue
                    changed += 1
                    for profile in ("original", "alt"):
                        role = "altModel" if profile == "alt" and "altModel" in new_part else "model"
                        old = before.model(old_part[role], profile)
                        new = after.model(new_part[role], profile)
                        if number in ("90001", "90986"):
                            directions = ("east", "west", "up", "down") if number == "90001" else ("east", "west")
                            count = 2 if new_part["model"].endswith("/door_90986_frame") else 1
                            for index in range(count):
                                assert old["elements"][index]["faces"]["north"] == new["elements"][index]["faces"]["north"]
                                opaque(after, new, new["elements"][index], directions, profile)
                        elif number == "93066":
                            kept = [element for index, element in enumerate(old["elements"])
                                    if index != 5 and index < 7]
                            assert new["elements"] == kept
                        elif number == "93241":
                            check_shutter(after, before, old_part, old, new, profile)
                        else:
                            assert geometry(old) == geometry(new)
                            for texture_key in old["textures"]:
                                source = before.image(resolve_texture(old, "#" + texture_key), profile)
                                target = after.image(resolve_texture(new, "#" + texture_key), profile)
                                assert source.getchannel("A").tobytes() == target.getchannel("A").tobytes()
                                expected = ImageEnhance.Brightness(source.convert("RGB")).enhance(DARKEN[number])
                                assert ImageChops.difference(expected, target.convert("RGB")).getbbox() is None
    window = json.loads(after.read(COMPOSITES + REGISTRIES["93241"] + ".json"))
    assert window["layer"] == "translucent"
    assert window["openable"]
    variant, = window["variants"]
    assert set(variant["poses"]) == {"closed", "open"}
    for position, pose in variant["poses"].items():
        frame, glass = pose["parts"]
        assert frame["model"].endswith("/shutter_0" if position == "closed" else "/shutter_1")
        for profile in ("original", "alt"):
            model = after.model(glass["model"], profile)
            (pane,) = model["elements"]
            for direction in ("north", "south"):
                visible = shutter_pixels(after, model, pane["faces"][direction], profile, (16, 30))
                assert visible.getchannel("A").getextrema() == (0, 80)
    wall = json.loads(after.read(COMPOSITES + RETIRED_SURROUND + ".json"))
    assert all(variant["poses"] == {"closed": {"parts": [], "collision": [], "selection": []}}
               for variant in wall["variants"])
    catalogue = json.loads(after.read("bloodborne_dw/remaining-catalogue.json"))
    assert next(row for row in catalogue["objects"] if row["path"] == RETIRED_SURROUND)["conversionOnly"]
    assert not after.model("bloodborne_dw:item/" + RETIRED_SURROUND, "original")["elements"]
    assert all(not (resources / f"{COMPOSITES}{name}.json").exists() for name in RETIRED_SHUTTERS)
    print(f"PASS: {len(REGISTRIES) + 1} recipes, {changed} model references and both 93241 poses in Original and ALT")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release", type=Path, required=True)
    parser.add_argument("--resources", type=Path, required=True)
    args = parser.parse_args()
    with ZipFile(args.release) as jar:
        check(jar, args.resources)
