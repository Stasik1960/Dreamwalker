"""Apply the photographed city material fixes to Original and ALT assets."""

import argparse
import json
from pathlib import Path
from statistics import median
from zipfile import ZipFile

from PIL import Image, ImageEnhance

from repair_builder_followup import Art, Source, profile_path, resolve_texture, texture_file, uv_rect, write_json
from repair_facade_assets import lum, transfer


BLOCK = "bloodborne_dw:block/remaining/source/minecraft/block/"
MODEL = "bloodborne_dw:remaining/source/minecraft/block/"
MODELS = "assets/bloodborne_dw/models/"
PROFILES = ("original", "alt")
CORBEL_CAP = [12.875, 12.875, 16, 16]


def crop(image, uv):
    return image.crop(uv_rect({"uv": uv}, image.size))


def opaque_median(image):
    return round(median(lum(p) for p in image.getdata() if p[3] == 255))


def write_texture(root, profile, sprite, image):
    output = root / profile_path(profile, texture_file(sprite))
    output.parent.mkdir(parents=True, exist_ok=True)
    image.save(output)


def stone_materials(source, root, profile):
    """Correct the source texture islands also referenced by compiled native quads."""
    adjacent = source.image(BLOCK + "addon/spirelamp_0028", profile)
    lower = BLOCK + "addon/spirelamp_002"
    corner = BLOCK + "addon/spirelamp_0029"
    masks = [
        (lower, [([6, 0, 8, 1], [6, 0, 8, 1]), ([8, 0, 10, 1], [8, 0, 10, 1]),
                 ([6, 1.125, 8, 2.125], [6, 0, 8, 1]), ([6, 8, 8, 9.25], [6, 8, 8, 8.875])]),
        (corner, [(CORBEL_CAP, [10, 10, 12, 12.625]),
                  ([8.75, 1.625, 12.375, 2.625], [6, 8, 8, 8.875])]),
    ]
    result = {}
    for sprite, rectangles in masks:
        old = source.image(sprite, profile)
        fixed = old.copy()
        for uv, adjacent_uv in rectangles:
            rect = uv_rect({"uv": uv}, old.size)
            patch = old.crop(rect)
            reference = crop(adjacent, adjacent_uv)
            corrected = transfer(patch, reference)
            factor = min(1, opaque_median(reference) / max(1, opaque_median(corrected)))
            if factor < 1:
                adjusted = ImageEnhance.Brightness(corrected.convert("RGB")).enhance(factor)
                adjusted.putalpha(corrected.getchannel("A"))
                corrected = adjusted
            corrected.putdata([after if lum(after) < lum(before) else before
                               for before, after in zip(patch.getdata(), corrected.getdata(), strict=True)])
            fixed.paste(corrected, rect)
        if fixed.getchannel("A").tobytes() != old.getchannel("A").tobytes():
            raise ValueError(f"Changing masonry modified alpha: {sprite}")
        write_texture(root, profile, sprite, fixed)
        rect = rectangles[0][0]
        result[sprite.rsplit("/", 1)[-1]] = [opaque_median(crop(old, rect)), opaque_median(crop(fixed, rect))]

    finial = BLOCK + "addon/design_2"
    old = source.image(finial, profile)
    target = opaque_median(crop(adjacent, [8, 0, 10, 1]))
    factor = min(.88, target / opaque_median(old))
    fixed = ImageEnhance.Brightness(old.convert("RGB")).enhance(factor)
    fixed.putalpha(old.getchannel("A"))
    write_texture(root, profile, finial, fixed)
    result["design_2"] = [opaque_median(old), opaque_median(fixed)]
    return result


def darken_balustrade_pillar(source, root, profile):
    # The model owning the bright geometry at (-84, 29, -868) is birch_fence.
    # Clone its sprites so other pieces of the spirelamp atlas keep their palette.
    name = (MODEL + "birch_fence").split(":", 1)[1]
    model = source.model(MODEL + "birch_fence", profile)
    art = Art(source, root, name, profile, model)
    neighbor = source.model(MODEL + "spruce_fence", profile)
    face = neighbor["elements"][0]["faces"]["east"]
    target = opaque_median(crop(source.image(resolve_texture(neighbor, face["texture"]), profile), face["uv"]))
    for key, face in (("2", model["elements"][0]["faces"]["west"]),
                      ("1", model["elements"][1]["faces"]["west"])):
        image = art.face_image(face)
        factor = min(0.90, target / opaque_median(crop(image, face["uv"])))
        shaded = ImageEnhance.Brightness(image.convert("RGB")).enhance(factor)
        shaded.putalpha(image.getchannel("A"))
        art.add_texture(key, shaded)
    art.save()
    if profile == "original":
        write_json(root, profile_path(profile, MODELS + name + ".json"), model)


def opaque_tile(image, width, height):
    if image.getchannel("A").getextrema() != (255, 255):
        raise ValueError("Side material contains a transparent pixel")
    # Keep the source density, extending the sample with whole repeats as needed.
    result = Image.new("RGBA", (width, height))
    for y in range(0, height, image.height):
        for x in range(0, width, image.width):
            result.paste(image, (x, y))
    return result


def door_sides(source, root, profile):
    recipe = json.loads(source.read("bloodborne_dw/composite/source_stairs_dark_prismarine_stairs_inner_right.json"))
    models = {part[key] for variant in recipe["variants"] for pose in variant["poses"].values()
              for part in pose["parts"] for key in ("model", "altModel") if key in part}
    atlas = source.image(BLOCK + "addon/spirelamp_0092", profile)
    masonry = crop(atlas, [.125, 11.125, 3.875, 15.875])
    wood = crop(atlas, [9, 2.5, 15, 8])
    for identifier in sorted(models):
        name = identifier.split(":", 1)[1]
        model = source.model(identifier, profile)
        art = Art(source, root, name, profile, model)
        for index, element in enumerate(model["elements"]):
            faces = element["faces"]
            # The solid jamb pieces with front and back only need side caps.
            # Keep the cutout arch overlays and intentional single planes open.
            uncapped = (faces.keys() == {"north", "south"}
                        and all(a < b for a, b in zip(element["from"], element["to"]))
                        and all(crop(art.face_image(face), face["uv"]).getchannel("A").getextrema() == (255, 255)
                                for face in faces.values()))
            for direction in ("west", "east"):
                face = faces.get(direction)
                if face is None and not uncapped:
                    continue
                if face is not None and not face["texture"].startswith("#edge_"):
                    continue
                # Keep the original face geometry. Replace the stretched one-column
                # edge with a fully textured side at the original pixel scale.
                width = max(1, round(element["to"][2] - element["from"][2]))
                height = max(1, round(element["to"][1] - element["from"][1]))
                material = wood if name.endswith("_leaf") else masonry
                area = opaque_tile(material, width, height)
                size = (max(16, 1 << (width - 1).bit_length()), max(16, 1 << (height - 1).bit_length()))
                padded = opaque_tile(area, *size)
                key = face["texture"][1:] if face is not None else f"edge_{index}_{direction}"
                end_u, end_v = 16 * width / size[0], 16 * height / size[1]
                element["faces"][direction] = {
                    "texture": art.add_texture(key, padded),
                    "uv": [end_u, 0, 0, end_v] if direction == "east" else [0, 0, end_u, end_v],
                }
        art.save()


def stained_glass_caps(source, root, profile):
    donor = source.model(MODEL + "dark_oak_trapdoor_bottom_1", profile)
    donor_face = donor["elements"][0]["faces"]["up"]
    pattern = crop(source.image(resolve_texture(donor, donor_face["texture"]), profile), donor_face["uv"])
    name = (MODEL + "green_stained_glass").split(":", 1)[1]
    model = source.model(MODEL + "green_stained_glass", profile)
    art = Art(source, root, name, profile, model)
    (element,) = model["elements"]
    side = element["faces"]["north"]
    side_image = crop(art.face_image(side), side["uv"])
    # Sample the opaque stone, excluding the blue translucent panes.
    opaque = [p for p in side_image.getdata() if p[3] == 255]
    if not opaque:
        raise ValueError("Missing solid stone reference on 90569")
    stone = Image.new("RGBA", (len(opaque), 1))
    stone.putdata(opaque)
    tile = transfer(pattern, stone)
    area = opaque_tile(tile, 48, 48)
    cap = art.add_texture("city_masonry_cap", opaque_tile(area, 64, 64))
    for direction in ("up", "down"):
        element["faces"][direction] = {"texture": cap, "uv": [0, 0, 12, 12]}
    art.save()
    if profile == "original":
        write_json(root, profile_path(profile, MODELS + name + ".json"), model)


def generate(jar, root, profile_resources=None):
    roots = (root, profile_resources) if profile_resources is not None else (root,)
    source = Source(jar, roots)
    if profile_resources is None:
        # Standalone previews start from the older release. The game build
        # already has these six atlas overrides in its profile resource tree.
        unchanged = Source(jar, ())
        receipt = {profile: stone_materials(unchanged, root, profile) for profile in PROFILES}
        print("Photographed masonry median luminance before/after:", json.dumps(receipt, sort_keys=True))
    for profile in PROFILES:
        door_sides(source, root, profile)
        darken_balustrade_pillar(source, root, profile)
        stained_glass_caps(source, root, profile)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release", type=Path, required=True)
    parser.add_argument("--resources", type=Path, required=True)
    args = parser.parse_args()
    with ZipFile(args.release) as release:
        generate(release, args.resources)
