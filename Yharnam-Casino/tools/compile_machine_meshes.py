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

from PIL import Image, ImageDraw, ImageFont


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


def cyrillic_font(size):
    candidates = (
        Path("C:/Windows/Fonts/arialbd.ttf"),
        Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"),
    )
    for candidate in candidates:
        if candidate.is_file():
            return ImageFont.truetype(candidate, size)
    raise FileNotFoundError("A Cyrillic bold font is required to render the broken-machine sign")


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


def compile_part(project, elements, group, texture_width, texture_height, local_to_pivot):
    pivot = tuple(float(value) / 16.0 for value in group["origin"])
    vertices = []
    for element_id in descendants(group):
        element = elements[element_id]
        element_origin = tuple(float(value) for value in element["origin"])
        positions = {}
        for key, local in element["vertices"].items():
            world = tuple((float(local[axis]) + element_origin[axis]) / 16.0 for axis in range(3))
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


def sign_mesh(texture_width, texture_height):
    # A shallow plaque across the reel window. +Z is the machine front.
    x0, x1 = -0.37, 0.37
    y0, y1 = 1.14, 1.42
    z0, z1 = 0.445, 0.475
    sx0, sy0, sx1, sy1 = 520 / texture_width, 8 / texture_height, 760 / texture_width, 168 / texture_height
    dark = (524 / texture_width, 172 / texture_height)
    result = []

    def face(points, uvs):
        for indices in ((0, 1, 2), (0, 2, 3)):
            tri = [points[index] for index in indices]
            face_normal = normal(*tri)
            for index, point in zip(indices, tri):
                uv = uvs[index]
                result.append((*point, *uv, *face_normal))

    face(((x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1)),
         ((sx0,sy1),(sx1,sy1),(sx1,sy0),(sx0,sy0)))
    face(((x1,y0,z0),(x0,y0,z0),(x0,y1,z0),(x1,y1,z0)), (dark,dark,dark,dark))
    face(((x0,y0,z0),(x0,y0,z1),(x0,y1,z1),(x0,y1,z0)), (dark,dark,dark,dark))
    face(((x1,y0,z1),(x1,y0,z0),(x1,y1,z0),(x1,y1,z1)), (dark,dark,dark,dark))
    face(((x0,y1,z1),(x1,y1,z1),(x1,y1,z0),(x0,y1,z0)), (dark,dark,dark,dark))
    face(((x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1)), (dark,dark,dark,dark))
    return result


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


def broken_atlas(style, source_atlas):
    with Image.open(source_atlas) as source:
        source = source.convert("RGBA")
        if source.width != 512:
            raise ValueError(f"{style}: expected a 512px-wide atlas, found {source.width}")
        atlas = Image.new("RGBA", (768, source.height))
        atlas.alpha_composite(source)
    draw = ImageDraw.Draw(atlas)
    draw.rounded_rectangle((520,8,760,168), radius=12, fill="#161820", outline="#B8C7D0", width=6)
    draw.rounded_rectangle((530,18,750,158), radius=7, outline="#6D1D28", width=5)
    font = cyrillic_font(43)
    for line, y in (("НЕ", 24), ("РАБОТАЕТ", 82)):
        box = draw.textbbox((0,0), line, font=font, stroke_width=1)
        width = box[2] - box[0]
        draw.text((640 - width / 2, y), line, font=font, fill="#E7DFD2",
                  stroke_width=2, stroke_fill="#5A111B")
    draw.line((538,146,742,28), fill="#697984", width=3)
    path = DESTINATION / "textures" / "machine" / f"{style}_broken.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(path, optimize=True)
    return atlas.width, atlas.height


def compile_item_icon(style, broken=False):
    texture_directory = DESTINATION / "textures" / "item"
    model_directory = DESTINATION / "models" / "item"
    texture_directory.mkdir(parents=True, exist_ok=True)
    model_directory.mkdir(parents=True, exist_ok=True)
    name = f"{style}_broken" if broken else style

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
    if broken:
        draw = ImageDraw.Draw(icon)
        draw.rounded_rectangle((35,96,221,151), radius=6, fill="#171820DD", outline="#C0CED4", width=3)
        font = cyrillic_font(24)
        text = "НЕ РАБОТАЕТ"
        box = draw.textbbox((0,0), text, font=font)
        draw.text(((256-(box[2]-box[0]))/2,108), text, font=font, fill="#F0E6D8",
                  stroke_width=1, stroke_fill="#651621")
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


def compile_style(style):
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
    dynamic = []
    for name in DYNAMIC_PARTS:
        pivot, vertices = compile_part(
            project, elements, groups[name], source_width, source_height, True)
        dynamic.append((name, pivot, vertices))
    write_mesh(DESTINATION / "models" / "machine" / f"{style}.mesh", dynamic)

    _, cabinet = compile_part(
        project, elements, groups["cabinet"], source_width, source_height, False)
    static_halves = split_halves(cabinet)
    write_mesh(DESTINATION / "models" / "machine" / f"{style}_static.mesh", [
        (half, (0.0, 0.0, 0.0), static_halves[half]) for half in ("lower", "upper")
    ])

    broken_width, broken_height = broken_atlas(style, atlas_path)
    broken_vertices = []
    for name in ALL_PARTS:
        _, vertices = compile_part(
            project, elements, groups[name], broken_width, broken_height, False)
        broken_vertices.extend(vertices)
    broken_vertices.extend(sign_mesh(broken_width, broken_height))
    broken_halves = split_halves(broken_vertices)
    write_mesh(DESTINATION / "models" / "machine" / f"{style}_broken.mesh", [
        (half, (0.0, 0.0, 0.0), broken_halves[half]) for half in ("lower", "upper")
    ])

    texture_directory = DESTINATION / "textures" / "machine"
    texture_directory.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(atlas_path, texture_directory / f"{style}.png")
    compile_item_icon(style)
    compile_item_icon(style, broken=True)
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
    for style in STYLES:
        static_triangles, broken_triangles = compile_style(style)
        print(f"{style}: {static_triangles} static triangles; {broken_triangles} broken triangles")


if __name__ == "__main__":
    main()
