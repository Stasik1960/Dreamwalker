"""Compile editable Blockbench meshes into compact, render-ready triangle streams.

The editable source models and atlases live in art/;
the generated .mesh files and copied atlases are runtime resources.
"""

from __future__ import annotations

import json
import math
import shutil
import struct
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "art"
DESTINATION = ROOT / "src" / "main" / "resources" / "assets" / "yharnam_casino"
STYLES = ("hunters_wager", "astral_choir", "crimson_oath")
PARTS = ("cabinet", "lever", "reel_01", "reel_02", "reel_03")


def vector_subtract(a: tuple[float, float, float], b: tuple[float, float, float]):
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


def compile_style(style):
    project = json.loads((SOURCE / "models" / f"{style}.bbmodel").read_text(encoding="utf8"))
    elements = {element["uuid"]: element for element in project["elements"]}
    groups = group_map(project)
    assigned = set()
    compiled = []

    for part_name in PARTS:
        group = groups[part_name]
        pivot = tuple(float(value) / 16.0 for value in group["origin"])
        vertices = []
        element_ids = descendants(group)
        assigned.update(element_ids)

        for element_id in element_ids:
            element = elements[element_id]
            element_origin = tuple(float(value) for value in element["origin"])
            positions = {
                key: tuple((float(local[axis]) + element_origin[axis]) / 16.0 - pivot[axis]
                           for axis in range(3))
                for key, local in element["vertices"].items()
            }
            for face in element["faces"].values():
                keys = face["vertices"]
                if len(keys) != 3:
                    raise ValueError(f"{style}: expected triangles, found {len(keys)} vertices")
                points = [positions[key] for key in keys]
                face_normal = normal(*points)
                if face_normal is None:
                    continue
                for key, point in zip(keys, points):
                    uv = face["uv"][key]
                    vertices.append((
                        *point,
                        float(uv[0]) / project["resolution"]["width"],
                        float(uv[1]) / project["resolution"]["height"],
                        *face_normal,
                    ))

        compiled.append((part_name, pivot, vertices))

    if assigned != set(elements):
        missing = set(elements) - assigned
        raise ValueError(f"{style}: {len(missing)} mesh elements are outside runtime groups")

    model_directory = DESTINATION / "models" / "machine"
    texture_directory = DESTINATION / "textures" / "machine"
    model_directory.mkdir(parents=True, exist_ok=True)
    texture_directory.mkdir(parents=True, exist_ok=True)

    with (model_directory / f"{style}.mesh").open("wb") as output:
        output.write(b"YCSM")
        output.write(struct.pack(">ii", 1, len(compiled)))
        for name, pivot, vertices in compiled:
            encoded_name = name.encode("utf8")
            output.write(struct.pack(">H", len(encoded_name)))
            output.write(encoded_name)
            output.write(struct.pack(">3fi", *pivot, len(vertices)))
            for vertex in vertices:
                output.write(struct.pack(">8f", *vertex))

    shutil.copyfile(
        SOURCE / "textures" / f"{style}_atlas.png",
        texture_directory / f"{style}.png",
    )
    compile_item_icon(style)
    return sum(len(vertices) for _, _, vertices in compiled)


def compile_item_icon(style):
    texture_directory = DESTINATION / "textures" / "item"
    model_directory = DESTINATION / "models" / "item"
    texture_directory.mkdir(parents=True, exist_ok=True)
    model_directory.mkdir(parents=True, exist_ok=True)

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
    icon.save(texture_directory / f"{style}.png", optimize=True)

    item_model = {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"yharnam_casino:item/{style}"},
    }
    (model_directory / f"{style}.json").write_text(
        json.dumps(item_model, indent=2) + "\n",
        encoding="utf8",
    )


def main():
    for style in STYLES:
        vertex_count = compile_style(style)
        print(f"{style}: {vertex_count // 3} triangles")


if __name__ == "__main__":
    main()
