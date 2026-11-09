"""Check v22 model corrections in the generated resource tree or a packaged JAR."""
import argparse
from io import BytesIO
import json
from pathlib import Path
from zipfile import ZipFile

from PIL import Image

from repair_requested_models import ALT, COMPOSITES, FACE_NAMES, GLASS_CUBES, IMPORTED, MODELS, PLAIN_DOOR, TEXTURES
from repair_requested_models import WINDOW_ALT_MODELS, WINDOW_PLACED_MODELS


def verify(base, generated, minecraft):
    def read(path):
        return json.loads(generated(path))

    def alpha(path):
        return Image.open(BytesIO(generated(path))).convert("RGBA").getchannel("A")

    original_sprite = "assets/bloodborne_dw/textures/base/source/minecraft/block/addon/window_5.png"
    alternate_sprite = TEXTURES + "block/city_followup/narrow_rose_windows.png"
    original = base.read(original_sprite)
    assert original != generated(alternate_sprite)
    assert Image.open(BytesIO(original)).convert("RGBA").getchannel("A").tobytes() == alpha(alternate_sprite).tobytes()
    for pack, models in (("", WINDOW_ALT_MODELS), (ALT, WINDOW_PLACED_MODELS)):
        for model in models:
            path = MODELS + model + ".json"
            altered = read(pack + path)
            assert altered["textures"]["0"].endswith("/narrow_rose_windows")
            original = json.loads(base.read(path))
            assert altered.get("elements") == original.get("elements")
    corbel = read(ALT + MODELS + IMPORTED + "stairs/polished_diorite_stairs_top.json")
    assert corbel["textures"]["0"].endswith("/carved_corbel_stone")
    assert alpha(ALT + TEXTURES + "block/city_followup/carved_corbel_stone.png").getextrema()[1] == 255

    for name in ("blue_stained_glass", "brown_stained_glass", "green_stained_glass",
                 "light_blue_stained_glass", "pink_stained_glass", "magenta_stained_glass"):
        element, = read(MODELS + IMPORTED + name + ".json")["elements"]
        assert set(element["faces"]) == set(FACE_NAMES)
        if name in {"light_blue_stained_glass", "pink_stained_glass", "magenta_stained_glass"}:
            assert element["from"] == [0, 0, 0] and element["to"] == [16, 16, 16]
        for direction in ("up", "down"):
            face = element["faces"][direction]
            textures = read(MODELS + IMPORTED + name + ".json")["textures"]
            image_name = TEXTURES + textures[face["texture"][1:]].split(":")[1] + ".png"
            image = Image.open(BytesIO(base.read(image_name))).convert("RGBA")
            crop = [round(value * axis / 16) for value, axis in zip(face["uv"], image.size * 2)]
            assert image.crop(crop).getchannel("A").getextrema()[1] > 0

    for name in GLASS_CUBES:
        for variant in read(COMPOSITES + name + ".json")["variants"]:
            assert variant["visibleBounds"]["to"][1] == 16
            for pose in variant["poses"].values():
                boxes = pose["collision"] + pose["selection"]
                boxes += [box for rotation in pose["simpleCollisionByRotation"] for box in rotation]
                assert boxes and all(box["to"][1] == 16 for box in boxes)

    static_path = "bloodborne_dw/static-architecture-v1274.json"
    before = json.loads(base.read(static_path))
    after = read(static_path)
    count = len(before["recipes"])
    if len(after["recipes"]) > count:
        append = json.loads(Path(__file__).with_name("studio-static-append.json").read_bytes())
        assert append["baseRecipes"] == count
        assert after["recipes"][count:] == append["recipes"]
        assert after["poses"][len(before["poses"]):] == append["poses"]
    else:
        assert len(after["recipes"]) == count
    shortened_poses = {pose["key"] for pose in before["poses"]
                       if pose["registryId"].split(":")[1] in GLASS_CUBES}
    from repair_builder_followup import RETIRED_SHUTTERS, RETIRED_SURROUND
    retired_poses = {pose["key"] for pose in before["poses"]
                     if pose["registryId"].split(":")[1] in RETIRED_SHUTTERS}
    for pose in after["poses"]:
        if pose["key"] in retired_poses:
            assert pose["registryId"] == f"bloodborne_dw:{RETIRED_SURROUND}"
    shortened_cells = 0
    for old, new in zip(before["recipes"], after["recipes"]):
        expected = old
        if any(fragment["pose"] in retired_poses for fragment in old["fragments"]):
            expected = {**old, "physicalFromPoses": True}
        assert {k: v for k, v in expected.items() if k not in {"collision", "selection"}} == {
            k: v for k, v in new.items() if k not in {"collision", "selection"}}
        removed_part = any(f["pose"] in shortened_poses and f["origin"][1] == -1 for f in old["fragments"])
        if not removed_part:
            assert expected == new
        else:
            assert new["collision"] != old["collision"] or new["selection"] != old["selection"]
            shortened_cells += 1
    assert shortened_cells

    rose = read(MODELS + IMPORTED + "dark_oak_trapdoor_bottom_1.json")["elements"]
    assert all(set(e["faces"]) == set(FACE_NAMES) for e in rose[:2])
    panes = read(MODELS + IMPORTED + "stairs/deep_tile_stairs_top.json")["elements"][2:]
    for pane in [rose[2], *panes]:
        assert pane["from"][2] < pane["to"][2]
        assert pane["faces"]["north"]["texture"] == pane["faces"]["south"]["texture"]

    path = COMPOSITES + "source_stairs_dark_prismarine_stairs_inner_right.json"
    old_door = json.loads(base.read(path))["variants"][0]["poses"]
    new_door = read(path)["variants"][0]["poses"]
    assert old_door["closed"].keys() == new_door["closed"].keys()
    assert new_door["closed"]["collision"] == new_door["closed"]["selection"]
    old_open, new_open = old_door["open"], new_door["open"]
    leaf = next(part for part in new_open["parts"] if part["model"].endswith("door_90986_leaf"))
    assert leaf["pivot"][2] + leaf["offset"][2] == 8
    assert new_open["collision"][-1]["from"][0] > old_open["collision"][0]["to"][0]
    assert new_open["collision"] == new_open["selection"]
    frame = read(MODELS + "remaining/final_followups/door_90986_frame.json")["elements"]
    assert frame[0]["faces"]["east"] == frame[0]["faces"]["north"]
    assert frame[1]["faces"]["west"] == frame[1]["faces"]["south"]

    states = read(f"assets/bloodborne_dw/blockstates/{PLAIN_DOOR}.json")["variants"]
    vanilla_states = json.loads(minecraft.read("assets/minecraft/blockstates/oak_door.json"))["variants"]
    assert states.keys() == vanilla_states.keys()
    for key, state in states.items():
        vanilla = vanilla_states[key]
        assert state["model"] == vanilla["model"].replace("minecraft:block/oak_door", f"bloodborne_dw:block/{PLAIN_DOOR}")
        assert state.get("y", 0) == vanilla.get("y", 0)
        model = read(MODELS + state["model"].split(":")[1] + ".json")
        assert model["parent"] == vanilla["model"].replace("oak_door", "door")
    for part in ("top", "bottom"):
        texture = alpha(f"{TEXTURES}block/{PLAIN_DOOR}_{part}.png")
        assert texture.size == (32, 32) and texture.getextrema()[1] == 255

    path = "bloodborne_dw/debug_catalogue.json"
    before = json.loads(base.read(path))["entries"]
    after = read(path)["entries"]
    assert [row for row in before if row["registryId"].split(":")[1] not in RETIRED_SHUTTERS] == after[:-2]
    assert [(e["temporaryId"], e["registryId"]) for e in after[-2:]] == [
        ("93241", "bloodborne_dw:source_shutter_window"), ("93242", f"bloodborne_dw:{PLAIN_DOOR}")]
    return {"model_contract": "PASS", "static_glass_cells_corrected": shortened_cells, "plain_door_states": len(states)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--minecraft", type=Path, required=True)
    inputs = parser.add_mutually_exclusive_group(required=True)
    inputs.add_argument("--resources", type=Path)
    inputs.add_argument("--jar", type=Path)
    args = parser.parse_args()
    with ZipFile(args.baseline) as base, ZipFile(args.minecraft) as minecraft:
        if args.jar:
            with ZipFile(args.jar) as jar:
                result = verify(base, jar.read, minecraft)
        else:
            result = verify(base, lambda path: (args.resources / path).read_bytes(), minecraft)
    print(json.dumps(result))


if __name__ == "__main__":
    main()
