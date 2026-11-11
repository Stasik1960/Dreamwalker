"""Build the v22 model corrections from the pinned Minecraft asset checkpoint."""
import argparse
from copy import deepcopy
from io import BytesIO
import json
from pathlib import Path
from zipfile import ZipFile

from PIL import Image


MODELS = "assets/bloodborne_dw/models/"
TEXTURES = "assets/bloodborne_dw/textures/"
COMPOSITES = "bloodborne_dw/composite/"
IMPORTED = "remaining/source/minecraft/block/"
ALT = "dw_city_visuals/alt/"
FACE_NAMES = ("north", "south", "east", "west", "up", "down")
GLASS_CUBES = {"source_magenta_stained_glass", "source_light_blue_stained_glass"}
PLAIN_DOOR = "door_90960_plain"
WINDOW_ALT_MODELS = ("alt/prototype_windows/thin/window_01", "alt/prototype_windows/thin/window_02",
                     "alt/prototype_windows/thin/window_03", "alt/v10_windows/window_03")
WINDOW_PLACED_MODELS = ("base/source/minecraft/block/hold/window_01", "base/source/minecraft/block/hold/window_02",
                        "base/source/minecraft/block/hold/window_03", "base/v10_windows/window_02",
                        "base/v10_windows/window_03")


def write_json(resources, path, data):
    target = resources / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf8")


def read_json(base, path):
    return json.loads(base.read(path))


def read_texture(base, identifier):
    namespace, name = identifier.split(":", 1)
    return Image.open(BytesIO(base.read(f"assets/{namespace}/textures/{name}.png"))).convert("RGBA")


def save_texture(resources, path, image):
    target = resources / (TEXTURES + path + ".png")
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target)


def tint(image, transform):
    result = image.copy()
    pixels = result.load()
    for y in range(image.height):
        for x in range(image.width):
            red, green, blue, alpha = pixels[x, y]
            if alpha:
                pixels[x, y] = (*transform(red, green, blue), alpha)
    return result


def repaint_alts(base, resources):
    windows = read_texture(base, "bloodborne_dw:base/source/minecraft/block/addon/window_5")

    def window_colour(r, g, b):
        if max(r, g, b) - min(r, g, b) <= 26:
            return (int(r * .87 + 14), int(g * .78 + 11), int(b * .69 + 7))
        return (int(r * .72 + 18), int(g * .73 + 19), int(b * .77 + 20))

    texture = "block/city_followup/narrow_rose_windows"
    save_texture(resources, texture, tint(windows, window_colour))
    for pack, models in (("", WINDOW_ALT_MODELS), (ALT, WINDOW_PLACED_MODELS)):
        for model in models:
            path = MODELS + model + ".json"
            data = read_json(base, path)
            data["textures"] = dict.fromkeys(("0", "particle"), "bloodborne_dw:" + texture)
            write_json(resources, pack + path, data)

    # The source atlas is shared with other masonry. Route this corbel alone
    # to the warm city stone in the ALT resource profile.
    path = MODELS + IMPORTED + "stairs/polished_diorite_stairs_top.json"
    model = read_json(base, path)
    source = read_texture(base, model["textures"]["0"])
    texture = "block/city_followup/carved_corbel_stone"
    recoloured = tint(source, lambda r, g, b: (int(r * .82 + 23), int(g * .74 + 19), int(b * .65 + 16)))
    save_texture(resources / ALT, texture, recoloured)
    model["textures"].update(dict.fromkeys(("0", "particle"), "bloodborne_dw:" + texture))
    write_json(resources, ALT + path, model)


def repair_glass(base, resources):
    for name in ("blue_stained_glass", "brown_stained_glass", "green_stained_glass",
                 "light_blue_stained_glass", "pink_stained_glass", "magenta_stained_glass"):
        path = MODELS + IMPORTED + name + ".json"
        model = read_json(base, path)
        element, = model["elements"]
        faces = element["faces"]
        cap = deepcopy(faces["north"])
        if name in {"light_blue_stained_glass", "pink_stained_glass", "magenta_stained_glass"}:
            # Keep the entire stained design visible on the four sides of
            # the shorter cube. Caps use a square crop from that same atlas.
            assert element["to"][1] == 32
            element["to"][1] = 16
            u1, v1, u2, _ = cap["uv"]
            cap["uv"] = [u1, v1, u2, v1 + (u2 - u1)]
        for face in ("up", "down"):
            faces[face] = deepcopy(cap)
        write_json(resources, path, model)

    path = MODELS + IMPORTED + "dark_oak_trapdoor_bottom_1.json"
    model = read_json(base, path)
    frame, pane, ornament = model["elements"]
    # The large frame and inner glass are separate shells. Use a solid stone
    # atlas swatch on the frame edges and glass along the inner pane's edges.
    for face in ("east", "west", "up", "down"):
        frame["faces"][face] = {"uv": [0, 0, 2, 2], "texture": "#0"}
        pane["faces"][face] = {"uv": [6, 12, 7, 13], "texture": "#0"}
    add_back_face(ornament)
    write_json(resources, path, model)

    path = MODELS + IMPORTED + "stairs/deep_tile_stairs_top.json"
    model = read_json(base, path)
    for pane in model["elements"][2:]:
        add_back_face(pane)
    write_json(resources, path, model)

    for name in sorted(GLASS_CUBES):
        path = COMPOSITES + name + ".json"
        definition = read_json(base, path)
        for variant in definition["variants"]:
            variant["visibleBounds"]["to"][1] = 16
            for pose in variant["poses"].values():
                for key in ("collision", "selection"):
                    for box in pose[key]:
                        assert box["from"][1] == 0 and box["to"][1] == 32
                        box["to"][1] = 16
                for boxes in pose["simpleCollisionByRotation"]:
                    for box in boxes:
                        assert box["from"][1] == 0 and box["to"][1] == 32
                        box["to"][1] = 16
        write_json(resources, path, definition)


def add_back_face(element):
    assert element["from"][2] == element["to"][2] and "south" not in element["faces"]
    # Give the opposite-facing quad a small depth separation. The front
    # retains its original position and UVs.
    element["to"][2] += 1 / 16
    back = deepcopy(element["faces"]["north"])
    u1, v1, u2, v2 = back["uv"]
    back["uv"] = [u2, v1, u1, v2]
    element["faces"]["south"] = back


def repair_static_cubes(base, resources):
    path = "bloodborne_dw/static-architecture-v1274.json"
    data = read_json(base, path)
    keys = set()
    for pose in data["poses"]:
        if pose["registryId"].split(":")[1] in GLASS_CUBES:
            keys.add(pose["key"])
            pose["reviewedVisualBounds"][1][1] -= 1

    recipes = data["recipes"]

    def surviving_recipe(index):
        while index >= 0:
            recipe = recipes[index]
            removed = next((part for part, fragment in enumerate(recipe["fragments"])
                            if fragment["pose"] in keys and fragment["origin"][1] == -1), None)
            if removed is None:
                return recipe
            index = recipe["remove"][removed]
        return None

    # Preserve every stored recipe index and removal edge. Use the existing
    # removal closure to recover just the remaining component shapes.
    for index, recipe in enumerate(recipes):
        remaining = surviving_recipe(index)
        if remaining is not recipe:
            for key in ("collision", "selection"):
                recipe[key] = [] if remaining is None else remaining[key]
    target = resources / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf8")


def repair_arched_door(base, resources):
    path = MODELS + "remaining/final_followups/door_90986_frame.json"
    frame = read_json(base, path)
    left, right = frame["elements"][:2]
    left["faces"]["east"] = deepcopy(left["faces"]["north"])
    right["faces"]["west"] = deepcopy(right["faces"]["south"])
    write_json(resources, path, frame)

    path = COMPOSITES + "source_stairs_dark_prismarine_stairs_inner_right.json"
    definition = read_json(base, path)
    pose = definition["variants"][0]["poses"]["open"]
    part = next(part for part in pose["parts"] if part["model"].endswith("door_90986_leaf"))
    # Jamb depth is z=2..14. Center the hinge at z=8 and clear the
    # inside jamb face by 1/32 model units to separate the depth planes.
    offset = (2 + 1 / 32, 0, 6)
    part["offset"] = [value + step for value, step in zip(part["offset"], offset)]
    for key in ("collision", "selection"):
        leaf = pose[key][-1]
        for corner in ("from", "to"):
            leaf[corner] = [value + step for value, step in zip(leaf[corner], offset)]
    write_json(resources, path, definition)


def vanilla_door(base, resources):
    leaf = read_json(base, MODELS + "remaining/final_followups/door_90960_leaf.json")
    atlas = read_texture(base, leaf["textures"]["0"])
    u1, v1, u2, v2 = leaf["elements"][0]["faces"]["north"]["uv"]
    pixels = [round(value * axis / 16) for value, axis in zip((u1, v1, u2, v2), atlas.size * 2)]
    artwork = atlas.crop(pixels).resize((32, 64), Image.Resampling.NEAREST)
    texture = "block/" + PLAIN_DOOR
    for suffix, bounds in (("top", (0, 0, 32, 32)), ("bottom", (0, 32, 32, 64))):
        save_texture(resources, f"{texture}_{suffix}", artwork.crop(bounds))
    save_texture(resources, "item/" + PLAIN_DOOR, artwork)

    for template in ("bottom", "top"):
        for hinge in ("left", "right"):
            for opened in (False, True):
                suffix = f"{template}_{hinge}" + ("_open" if opened else "")
                data = {
                    "parent": "minecraft:block/door_" + suffix,
                    "textures": {face: f"bloodborne_dw:{texture}_{face}" for face in ("top", "bottom")},
                }
                write_json(resources, f"{MODELS}block/{PLAIN_DOOR}_{suffix}.json", data)

    variants = {}
    for facing, angle in (("east", 0), ("south", 90), ("west", 180), ("north", 270)):
        for half, template in (("lower", "bottom"), ("upper", "top")):
            for hinge in ("left", "right"):
                for opened in (False, True):
                    suffix = f"{template}_{hinge}" + ("_open" if opened else "")
                    key = f"facing={facing},half={half},hinge={hinge},open={str(opened).lower()}"
                    rotation = (angle + ({"left": 90, "right": -90}[hinge] if opened else 0)) % 360
                    variants[key] = {"model": f"bloodborne_dw:block/{PLAIN_DOOR}_{suffix}", "y": rotation}
    write_json(resources, f"assets/bloodborne_dw/blockstates/{PLAIN_DOOR}.json", {"variants": variants})
    write_json(resources, f"{MODELS}item/{PLAIN_DOOR}.json", {
        "parent": "minecraft:item/generated", "textures": {"layer0": f"bloodborne_dw:item/{PLAIN_DOOR}"}})
    write_json(resources, f"data/bloodborne_dw/loot_tables/blocks/{PLAIN_DOOR}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "bloodborne_dw:" + PLAIN_DOOR}],
                   "conditions": [{"condition": "minecraft:block_state_property", "block": "bloodborne_dw:" + PLAIN_DOOR,
                                   "properties": {"half": "lower"}},
                                  {"condition": "minecraft:survives_explosion"}]}]})
    for tag in ("blocks/wooden_doors", "items/wooden_doors", "blocks/mineable/axe"):
        path = "data/minecraft/tags/" + tag + ".json"
        data = read_json(base, path) if path in base.namelist() else {"replace": False, "values": []}
        data["values"].append("bloodborne_dw:" + PLAIN_DOOR)
        write_json(resources, path, data)


def update_catalogue(base, resources):
    path = "bloodborne_dw/debug_catalogue.json"
    data = read_json(base, path)
    for number, path_name, label in (
        ("93241", "source_shutter_window", "Узкое окно со ставнями"),
        ("93242", PLAIN_DOOR, "Деревянная дверь без кладки"),
    ):
        registry = "bloodborne_dw:" + path_name
        assert all(e["temporaryId"] != number and registry not in e["aliases"] for e in data["entries"])
        data["entries"].append({"temporaryId": number, "finalId": None, "kind": "architecture",
                                "name": label, "registryId": registry, "aliases": [registry]})
    write_json(resources, path, data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--resources", type=Path, required=True)
    parser.add_argument("--profile-resources", type=Path)
    parser.add_argument("--omit-studio-map", action="store_true",
                        help="Generate the exact pre-migration static catalogue for the offline map converter")
    args = parser.parse_args()
    with ZipFile(args.baseline) as base:
        repaint_alts(base, args.resources)
        repair_glass(base, args.resources)
        repair_static_cubes(base, args.resources)
        repair_arched_door(base, args.resources)
        vanilla_door(base, args.resources)
        update_catalogue(base, args.resources)
        if args.profile_resources:
            from repair_builder_followup import generate
            from repair_city_screenshot_assets import generate as repair_screenshots
            from generate_wall_straights import generate as generate_wall_straights
            from generate_wall_tall import generate as generate_wall_tall

            generate(base, args.resources, args.profile_resources, not args.omit_studio_map)
            repair_screenshots(base, args.resources, args.profile_resources)
            generate_wall_straights(base, args.resources, args.profile_resources)
            generate_wall_tall(base, args.resources, args.profile_resources)


if __name__ == "__main__":
    main()
