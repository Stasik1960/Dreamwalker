"""Compile editable Blockbench machines into dynamic and chunk-baked mesh streams.

The preferred editable source is ``art/bloodborne_slots_3d`` inside this project.
For the existing workspace layout the sibling ``bloodborne_slots_3d`` remains a
fallback. Source models and atlases are never modified.
"""

from __future__ import annotations

import json
import math
import shutil
import struct
from pathlib import Path

from PIL import Image

from out_of_order_sign import add_to_atlas, add_to_icon, plaque_art, sign_faces


PROJECT = Path(__file__).resolve().parents[1]
SOURCE_CANDIDATES = (
    PROJECT / "art" / "bloodborne_slots_3d",
    PROJECT / "art",
    PROJECT.parent / "bloodborne_slots_3d",
)
SOURCE = next(
    (path for path in SOURCE_CANDIDATES if (path / "models").is_dir()),
    SOURCE_CANDIDATES[-1],
)
DESTINATION = PROJECT / "src" / "main" / "resources" / "assets" / "yharnam_casino"
DATA = PROJECT / "src" / "main" / "resources" / "data" / "yharnam_casino"
STYLES = ("hunters_wager", "astral_choir", "crimson_oath")
DYNAMIC_PARTS = ("lever", "reel_01", "reel_02", "reel_03")
ALL_PARTS = ("cabinet", *DYNAMIC_PARTS)
BASE_TRIM = {"hunters_wager": "Iron sill", "astral_choir": "Rough hewn step", "crimson_oath": "Reliquary base"}
PLINTH_HEIGHT = .25
REEL_LIFT = .50


def vector_subtract(a, b):
    return a[0] - b[0], a[1] - b[1], a[2] - b[2]


def normal(a, b, c):
    ab = vector_subtract(b, a)
    ac = vector_subtract(c, a)
    cross = (
        ab[1] * ac[2] - ab[2] * ac[1],
        ab[2] * ac[0] - ab[0] * ac[2],
        ab[0] * ac[1] - ab[1] * ac[0],
    )
    length = math.sqrt(sum(component * component for component in cross))
    if length < 1.0e-7:
        return None
    return tuple(component / length for component in cross)


def group_map(project):
    groups = {}

    def visit(node):
        if isinstance(node, str):
            return
        groups[node["name"]] = node
        for child in node.get("children", []):
            visit(child)

    for root in project["outliner"]:
        visit(root)
    return groups


def descendants(group):
    result = []
    for child in group.get("children", []):
        if isinstance(child, str):
            result.append(child)
        else:
            result.extend(descendants(child))
    return result


def fit_point(point, base_half_width, moving=False):
    x, y, z = point
    # Leave room for the side handle, even between cabinets of different styles.
    side = abs(x)
    upper_x = min(side, .375) * 1.14 + max(0, side - .375) * .66
    base_x = min(.5, side * .5 / base_half_width)
    blend = 1 if moving else min(1, max(0, (y - .35) / .40))
    x = math.copysign(base_x * (1 - blend) + upper_x * blend, x)
    # Move the rotating mechanisms rigidly in the Y/Z plane. Only the lower
    # cabinet and its decorative crown change proportions around the window.
    if moving:
        return x, y + REEL_LIFT, z
    lift = PLINTH_HEIGHT + (REEL_LIFT - PLINTH_HEIGHT) * min(1, y / .75)
    # Take height out of the gap above the reels and the roof, keeping the
    # printed marquees at their original height across all three cabinets.
    trim = .60 * min(.11, max(0, y - 1.28)) + .46 * max(0, y - 1.63)
    return x, y + lift - trim, z


def compile_part(elements, group, texture_width, texture_height, local_to_pivot, transform):
    pivot = transform(tuple(float(value) / 16.0 for value in group["origin"]))
    vertices = []
    for element_id in descendants(group):
        element = elements[element_id]
        element_origin = tuple(float(value) for value in element["origin"])
        positions = {}
        for key, local in element["vertices"].items():
            world = transform(tuple((float(local[axis]) + element_origin[axis]) / 16.0 for axis in range(3)))
            positions[key] = tuple(world[axis] - pivot[axis] for axis in range(3)) if local_to_pivot else world
        for face in element["faces"].values():
            keys = face["vertices"]
            if len(keys) != 3:
                raise ValueError(f"expected triangles, found {len(keys)} vertices")
            points = [positions[key] for key in keys]
            face_normal = normal(*points)
            if face_normal is None:
                continue
            for key, point in zip(keys, points):
                uv = face["uv"][key]
                vertices.append((
                    *point,
                    float(uv[0]) / texture_width,
                    float(uv[1]) / texture_height,
                    *face_normal,
                ))
    return pivot, vertices


def split_halves(vertices):
    halves = {"lower": [], "upper": []}
    for offset in range(0, len(vertices), 3):
        triangle = vertices[offset:offset + 3]
        half = "upper" if sum(vertex[1] for vertex in triangle) / 3.0 >= 1.0 else "lower"
        y_offset = 1.0 if half == "upper" else 0.0
        halves[half].extend(
            (vertex[0], vertex[1] - y_offset, *vertex[2:]) for vertex in triangle
        )
    return halves


def sign_mesh(panel_front, texture_width, texture_height, transform):
    for points, uvs in sign_faces(panel_front):
        points = [transform(point) for point in points]
        for middle in range(1, len(points)-1):
            indices = (0, middle, middle+1)
            tri = [points[index] for index in indices]
            face_normal = normal(*tri)
            if face_normal is None:
                continue
            for index, point in zip(indices, tri):
                u, v = uvs[index]
                yield (*point, u/texture_width, v/texture_height, *face_normal)


def write_mesh(path, parts):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as output:
        output.write(b"YCSM")
        output.write(struct.pack(">ii", 1, len(parts)))
        for name, pivot, vertices in parts:
            encoded_name = name.encode("utf8")
            output.write(struct.pack(">H", len(encoded_name)))
            output.write(encoded_name)
            output.write(struct.pack(">3fi", *pivot, len(vertices)))
            for vertex in vertices:
                output.write(struct.pack(">8f", *vertex))


def broken_atlas(style, source_atlas, plaque):
    with Image.open(source_atlas) as source:
        source = source.convert("RGBA")
        if source.width != 512:
            raise ValueError(f"{style}: expected a 512px-wide atlas, found {source.width}")
        atlas = Image.new("RGBA", (768, source.height))
        atlas.alpha_composite(source)
    add_to_atlas(atlas, plaque)
    path = DESTINATION / "textures" / "machine" / f"{style}_broken.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(path, optimize=True)
    return atlas.width, atlas.height


def compile_item_icon(style, plaque=None):
    texture_directory = DESTINATION / "textures" / "item"
    model_directory = DESTINATION / "models" / "item"
    texture_directory.mkdir(parents=True, exist_ok=True)
    model_directory.mkdir(parents=True, exist_ok=True)
    name = f"{style}_broken" if plaque is not None else style

    with Image.open(SOURCE / "previews" / f"{style}.png") as render:
        render = render.convert("RGBA")
        bounds = render.getchannel("A").getbbox()
        if not bounds:
            raise ValueError(f"{style}: preview has no visible pixels")
        portrait = render.crop(bounds)
    scale = min(226 / portrait.width, 240 / portrait.height)
    portrait = portrait.resize(
        (round(portrait.width * scale), round(portrait.height * scale)),
        Image.Resampling.LANCZOS,
    )
    icon = Image.new("RGBA", (256, 256))
    icon.alpha_composite(portrait, ((256 - portrait.width) // 2, 250 - portrait.height))
    if plaque is not None:
        add_to_icon(icon, style, plaque)
    icon.save(texture_directory / f"{name}.png", optimize=True)
    (model_directory / f"{name}.json").write_text(json.dumps({
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"yharnam_casino:item/{name}"},
    }, indent=2) + "\n", encoding="utf8")


def write_support_resources(style):
    def variants(name):
        values = {}
        for half in ("lower", "upper"):
            for facing in ("north", "east", "south", "west"):
                values[f"facing={facing},half={half}"] = {
                    "model": f"yharnam_casino:machine/{name}_{half}_{facing}"
                }
        return {"variants": values}

    blockstates = DESTINATION / "blockstates"
    blockstates.mkdir(parents=True, exist_ok=True)
    (blockstates / f"{style}.json").write_text(
        json.dumps(variants(style), indent=2) + "\n", encoding="utf8")
    broken = f"{style}_broken"
    (blockstates / f"{broken}.json").write_text(
        json.dumps(variants(broken), indent=2) + "\n", encoding="utf8")
    block_models = DESTINATION / "models" / "block"
    block_models.mkdir(parents=True, exist_ok=True)
    (block_models / f"{broken}.json").write_text(json.dumps({
        "textures": {"particle": f"yharnam_casino:machine/{style}_broken"}
    }, indent=2) + "\n", encoding="utf8")
    loot = DATA / "loot_tables" / "blocks"
    loot.mkdir(parents=True, exist_ok=True)
    (loot / f"{broken}.json").write_text(json.dumps({
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": f"yharnam_casino:{broken}"}],
            "conditions": [
                {"condition": "minecraft:survives_explosion"},
                {"condition": "minecraft:block_state_property", "block": f"yharnam_casino:{broken}",
                 "properties": {"half": "lower"}},
            ],
        }],
    }, indent=2) + "\n", encoding="utf8")


def compile_style(style, plaque):
    model_path = SOURCE / "models" / f"{style}.bbmodel"
    atlas_path = SOURCE / "textures" / f"{style}_atlas.png"
    project = json.loads(model_path.read_text(encoding="utf8"))
    elements = {element["uuid"]: element for element in project["elements"]}
    groups = group_map(project)
    assigned = set()
    for part_name in ALL_PARTS:
        assigned.update(descendants(groups[part_name]))
    if assigned != set(elements):
        missing = set(elements) - assigned
        raise ValueError(f"{style}: {len(missing)} mesh elements are outside runtime groups")

    source_width = project["resolution"]["width"]
    source_height = project["resolution"]["height"]
    # Carry each style's original beveled base and its material down to the floor.
    trim = next(element for element in elements.values() if element["name"] == BASE_TRIM[style])
    trim_points = [tuple((o + v) / 16 for o, v in zip(trim["origin"], vertex))
                   for vertex in trim["vertices"].values()]
    base_half_width = max(abs(x) for x, y, z in trim_points)
    bottom = min(y for x, y, z in trim_points)
    top = max(y for x, y, z in trim_points)

    def fit_body(point):
        return fit_point(point, base_half_width)

    def fit_moving(point):
        return fit_point(point, base_half_width, moving=True)

    def plinth(width, height):
        def fit_foot(point):
            x, y, z = point
            return x * .5 / base_half_width, (y - bottom) / (top - bottom) * PLINTH_HEIGHT, z
        group = {"origin": (0, 0, 0), "children": [trim["uuid"]]}
        return compile_part(elements, group, width, height, False, fit_foot)[1]

    dynamic = []
    for name in DYNAMIC_PARTS:
        pivot, vertices = compile_part(
            elements, groups[name], source_width, source_height, True, fit_moving)
        dynamic.append((name, pivot, vertices))
    write_mesh(DESTINATION / "models" / "machine" / f"{style}.mesh", dynamic)

    _, cabinet = compile_part(
        elements, groups["cabinet"], source_width, source_height, False, fit_body)
    cabinet.extend(plinth(source_width, source_height))
    static_halves = split_halves(cabinet)
    write_mesh(DESTINATION / "models" / "machine" / f"{style}_static.mesh", [
        (half, (0.0, 0.0, 0.0), static_halves[half]) for half in ("lower", "upper")
    ])

    broken_width, broken_height = broken_atlas(style, atlas_path, plaque)
    broken_vertices = plinth(broken_width, broken_height)
    for name in ALL_PARTS:
        _, vertices = compile_part(
            elements, groups[name], broken_width, broken_height, False, fit_body if name == "cabinet" else fit_moving)
        broken_vertices.extend(vertices)
    rail = next(element for element in elements.values() if element["name"].split(".")[0] == "Upper window blind")
    panel_front = (rail["origin"][2] + max(vertex[2] for vertex in rail["vertices"].values())) / 16
    broken_vertices.extend(sign_mesh(panel_front, broken_width, broken_height, fit_body))
    broken_halves = split_halves(broken_vertices)
    write_mesh(DESTINATION / "models" / "machine" / f"{style}_broken.mesh", [
        (half, (0.0, 0.0, 0.0), broken_halves[half]) for half in ("lower", "upper")
    ])

    texture_directory = DESTINATION / "textures" / "machine"
    texture_directory.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(atlas_path, texture_directory / f"{style}.png")
    compile_item_icon(style)
    compile_item_icon(style, plaque)
    write_support_resources(style)
    return len(cabinet) // 3, len(broken_vertices) // 3


def main():
    if not (SOURCE / "models").is_dir():
        raise FileNotFoundError(f"No editable machine source found; checked: {SOURCE_CANDIDATES}")
    atlas_config = PROJECT / "src" / "main" / "resources" / "assets" / "minecraft" / "atlases" / "blocks.json"
    atlas_config.parent.mkdir(parents=True, exist_ok=True)
    atlas_config.write_text(json.dumps({
        "sources": [{"type": "directory", "source": "machine", "prefix": "machine/"}]
    }, indent=2) + "\n", encoding="utf8")
    print(f"source: {SOURCE}")
    plaque = plaque_art(SOURCE / "textures" / "surfaces" / "hunters_wager_slat.png")
    for style in STYLES:
        static_triangles, broken_triangles = compile_style(style, plaque)
        print(f"{style}: {static_triangles} static triangles; {broken_triangles} broken triangles")


if __name__ == "__main__":
    main()
