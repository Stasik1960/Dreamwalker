"""Scoped Builder art edits, usable with the build inputs or the published JAR."""
import argparse
from copy import deepcopy
import hashlib
from io import BytesIO
import json
from math import floor
from pathlib import Path
from zipfile import ZipFile

from PIL import Image, ImageEnhance

from repair_facade_assets import transfer


NAMESPACE = "bloodborne_dw"
MODELS = f"assets/{NAMESPACE}/models/"
COMPOSITES = f"{NAMESPACE}/composite/"
INPUT_RECIPES = Path(__file__).with_name("builder-followup-inputs")
RETIRED_SHUTTERS = {"source_spruce_trapdoor_bottom", "source_spruce_trapdoor_open"}
RETIRED_SURROUND = "source_shutter_window_surround"
REGISTRIES = {
    "90001": "prototype_double_door",
    "93066": "source_stairs_waxed_oxidized_cut_copper_stairs_inner_left_top2",
    "93241": "source_shutter_window",
    "90986": "source_stairs_dark_prismarine_stairs_inner_right",
    "90545": "source_gold_block",
    "90024": "source__wall_dec_out",
    "93003": "source_stairs_exposed_cut_copper_stairs_bottom",
    "90249": "source_addon_wall_deco_5",
    "90152": "source_addon_gray_glazed_terracotta_0",
}
DARKEN = {"90545": .81, "90024": .84, "93003": .85, "90249": .88, "90152": .77}
SHUTTER_FRAME_BOUNDS = (95, 81, 114, 112)
# Inclusive outline of the actual arch, traced in the 128px source atlas.
SHUTTER_CROWN = ((103, 104), (102, 105), (100, 107), (98, 108), (97, 110), (96, 112))
SHUTTER_PAD = 2


def texture_file(identifier):
    namespace, path = identifier.split(":", 1)
    return f"assets/{namespace}/textures/{path}.png"


def profile_path(profile, path):
    return f"dw_city_visuals/{profile}/{path}"


def uv_rect(face, size):
    u0, v0, u1, v1 = face["uv"]
    w, h = size
    x = min(w - 1, max(0, floor(min(u0, u1) * w / 16)))
    y = min(h - 1, max(0, floor(min(v0, v1) * h / 16)))
    return (x, y, min(w, max(x + 1, round(max(u0, u1) * w / 16))),
            min(h, max(y + 1, round(max(v0, v1) * h / 16))))


class Source:
    def __init__(self, jar, roots):
        self.jar = jar
        self.roots = roots

    def read(self, path):
        for root in self.roots:
            candidate = root / path
            if candidate.is_file():
                return candidate.read_bytes()
        return self.jar.read(path)

    def profile(self, profile, path):
        try:
            return self.read(profile_path(profile, path))
        except KeyError:
            return self.read(path)

    def model(self, identifier, profile, chain=()):
        if identifier in chain:
            raise ValueError(f"Cycle resolving {identifier}")
        namespace, path = identifier.split(":", 1)
        model = json.loads(self.profile(profile, f"assets/{namespace}/models/{path}.json"))
        parent = model.pop("parent", None)
        if parent:
            base = self.model(parent, profile, chain + (identifier,))
            return {**base, **model, "textures": {**base.get("textures", {}), **model.get("textures", {})}}
        return model

    def image(self, identifier, profile):
        return Image.open(BytesIO(self.profile(profile, texture_file(identifier)))).convert("RGBA")


def resolve_texture(model, key):
    seen = set()
    while key.startswith("#"):
        if key in seen:
            raise ValueError(f"Cycle resolving {key}")
        seen.add(key)
        key = model["textures"][key[1:]]
    return key


def write_json(output, path, data):
    target = output / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n",
                      encoding="utf-8", newline="\n")


class Art:
    def __init__(self, source, output, name, profile, model):
        self.source = source
        self.output = output
        self.name = name
        self.profile = profile
        self.model = model

    def path(self, path):
        return profile_path("alt", path) if self.profile == "alt" else path

    def add_texture(self, key, image):
        identifier = f"{NAMESPACE}:block/{self.name}_{key}"
        path = self.output / self.path(texture_file(identifier))
        path.parent.mkdir(parents=True, exist_ok=True)
        image.save(path)
        self.model["textures"][key] = identifier
        return "#" + key

    def face_image(self, face):
        return self.source.image(resolve_texture(self.model, face["texture"]), self.profile)

    def save(self):
        write_json(self.output, self.path(MODELS + self.name + ".json"), self.model)


def edge_strip(front, direction):
    """Extend the opaque border of the source art across the thickness."""
    vertical = direction in ("west", "east")
    length = front.height if vertical else front.width
    depth = front.width if vertical else front.height
    offsets = range(depth) if direction in ("west", "up") else range(depth - 1, -1, -1)
    colours = {}
    for i in range(length):
        for j in offsets:
            pixel = front.getpixel((j, i) if vertical else (i, j))
            if pixel[3] == 255:
                colours[i] = pixel
                break
    if not colours:
        raise ValueError("Door face has no opaque source pixels")
    strip = Image.new("RGBA", (1, length) if vertical else (length, 1))
    strip.putdata([colours[i] if i in colours else colours[min(colours, key=lambda j: abs(i - j))]
                   for i in range(length)])
    power = max(16, 1 << (length - 1).bit_length())
    return strip.resize((16, power) if vertical else (power, 16), Image.Resampling.NEAREST)


def fix_door(art, number):
    model = art.model
    framed = art.name.endswith("/door_90986_frame")
    leaf = art.name.endswith("/door_90986_leaf")
    indices = (0, 1) if framed else (0,)
    if number == "90986" and not leaf:
        atlas = art.source.image(resolve_texture(model, "#0"), art.profile)
        # This opaque stone is already used on the frame's broad top face.
        stone = atlas.crop(uv_rect({"uv": [0, 6.875, 4.25, 9.125]}, atlas.size))
        stone = stone.resize((32, 16), Image.Resampling.NEAREST)
        texture = art.add_texture("arch_stone", stone)
        for index in indices:
            for direction in ("west", "east"):
                model["elements"][index]["faces"][direction] = {"texture": texture, "uv": [0, 0, 16, 16]}
        # The arch thickness is a sequence of coplanar strips. Use one
        # continuous projection so each strip samples the neighboring stone.
        for element in model["elements"]:
            faces = element["faces"]
            if len(faces) != 1 or element["to"][2] - element["from"][2] < 15:
                continue
            direction = next(iter(faces))
            if direction in ("east", "west"):
                uv = [0, (element["from"][1] + 16) / 3, 16, (element["to"][1] + 16) / 3]
            elif direction in ("up", "down"):
                uv = [(element["from"][0] + 16) / 3, 0, (element["to"][0] + 16) / 3, 16]
            else:
                continue
            faces[direction] = {"texture": texture, "uv": uv}
        return
    directions = ("west", "east", "up", "down") if number == "90001" else ("west", "east")
    for index in indices:
        element = model["elements"][index]
        front = element["faces"]["north"]
        atlas = art.face_image(front)
        crop = atlas.crop(uv_rect(front, atlas.size))
        for direction in directions:
            texture = art.add_texture(f"edge_{index}_{direction}", edge_strip(crop, direction))
            element["faces"][direction] = {"texture": texture, "uv": [0, 0, 16, 16]}


def strip_window_bricks(art):
    elements = art.model["elements"]
    upper = art.name.endswith("_right_top2")
    assert len(elements) == (16 if upper else 7)
    # Remove the old flat brick backing. Keep the depth faces of the arch,
    # and sample the same stone as the vertical side of the window frame.
    for element in elements[7:]:
        for face in element["faces"].values():
            face["texture"] = "#1"
            face["uv"] = [2, 6, 3.75, 10]
    art.model["elements"] = [element for index, element in enumerate(elements) if index != 5]


def add_hold_lantern(jar, output, profile_resources):
    source = Source(jar, (output, profile_resources) if profile_resources else ())
    registry = "source_hold_lanterns"
    path = COMPOSITES + registry + ".json"
    recipe = json.loads(jar.read(path))
    identifier = "bloodborne_dw:remaining/source/minecraft/block/hold/lanterns"
    alias = "builder_followup/90588/lit/lanterns"
    for profile in ("original", "alt"):
        art = Art(source, output, alias, profile, source.model(identifier, profile))
        image = art.source.image(resolve_texture(art.model, "#0"), profile)
        bounds = uv_rect({"uv": [8.375, 1.75, 8.875, 2.625]}, image.size)
        lit = image.copy()
        for y in range(bounds[1], bounds[3]):
            for x in range(bounds[0], bounds[2]):
                red, green, blue, alpha = image.getpixel((x, y))
                luminance = (red * 3 + green * 6 + blue) // 10
                lit.putpixel((x, y), (min(255, 70 + luminance * 3 // 4),
                                      min(255, 45 + luminance * 2 // 3),
                                      min(255, 6 + luminance // 4), alpha))
        texture = art.add_texture("glass_lit", lit)
        for index in (2, 6, 10, 14, 18):
            for face in art.model["elements"][index]["faces"].values():
                face["texture"] = texture
        art.save()
    for variant in recipe["variants"]:
        lit_pose = deepcopy(variant["poses"]["closed"])
        for part in lit_pose["parts"]:
            if part["model"] == identifier:
                part["model"] = f"{NAMESPACE}:{alias}"
        variant["poses"]["open"] = lit_pose
    write_json(output, path, recipe)


def shutter_texture(art, key, image, faces, atlas_size, bounds):
    # A transparent border isolates the new silhouette from neighboring atlas
    # sprites, even when the original UVs reach the edge of the old region.
    width, height = 32, 64
    atlas = Image.new("RGBA", (width, height))
    atlas.paste(image, (SHUTTER_PAD, SHUTTER_PAD))
    texture = art.add_texture(key, atlas)
    for face in faces:
        face["texture"] = texture
        face["uv"] = [
            (value * old_size / 16 - start + SHUTTER_PAD) * 16 / new_size
            for value, old_size, start, new_size in zip(
                face["uv"], atlas_size * 2, bounds[:2] * 2, (width, height) * 2, strict=True)
        ]


def shutter_leaf_edges(leaves, atlas_width):
    # The north face starts at the right edge of each leaf. Top and bottom
    # start at its left edge, so the old caps placed the peak on the outside.
    half_pixel = 8 / atlas_width
    inset = half_pixel / 2
    for leaf in leaves:
        faces = leaf["faces"]
        front = faces["north"]
        u0, v0, u1, v1 = front["uv"]
        step = half_pixel if u0 < u1 else -half_pixel
        for direction, u in (("east", u0 + step), ("west", u1 - step)):
            faces[direction] = {"texture": front["texture"], "uv": [u - inset, v0, u + inset, v1]}
        for direction, v in (("up", v0 + half_pixel), ("down", v1 - half_pixel)):
            faces[direction] = {"texture": front["texture"], "uv": [u1, v - inset, u0, v + inset]}


def shutter_frame(art):
    elements = art.model["elements"]
    assert len(elements) == 116
    source = art.source.image(resolve_texture(art.model, "#0"), art.profile)
    assert source.size == (128, 128)
    if art.name.endswith("/shutter_0"):
        shutter_leaf_edges(elements[:2], source.width)
    xmin, ymin, xmax, ymax = SHUTTER_FRAME_BOUNDS
    frame = source.crop(SHUTTER_FRAME_BOUNDS)
    removed = set()
    for y, (low, high) in enumerate(SHUTTER_CROWN, start=ymin):
        for x in range(xmin, xmax):
            pixel = (x - xmin, y - ymin)
            if not low <= x <= high and frame.getpixel(pixel)[3]:
                frame.putpixel(pixel, (0, 0, 0, 0))
                removed.add((x, y))
    if art.profile == "alt":
        sill = elements[2]["faces"]["north"]
        reference = art.face_image(sill)
        reference = reference.crop(uv_rect(sill, reference.size))
        previous = art.face_image(elements[3]["faces"]["north"]).crop(SHUTTER_FRAME_BOUNDS)
        recolored = transfer(frame, reference)
        for y in range(frame.height):
            for x in range(frame.width):
                pixel = (x, y)
                prior = previous.getpixel(pixel)
                current = frame.getpixel(pixel)
                if current[3] and prior != current:
                    recolored.putpixel(pixel, prior)
        frame = recolored

    # These thickness quads were generated from the old opaque silhouette.
    # Cap the new exposed crown edges using the retained texel on each side.
    for y in range(ymin, ymax):
        for x in range(xmin, xmax):
            if not frame.getpixel((x - xmin, y - ymin))[3]:
                continue
            right, top = 17 - (x - xmin), 24 - (y - ymin)
            uv = [(x + .1) / 8, (y + .1) / 8, (x + .9) / 8, (y + .9) / 8]
            for dx, dy, direction in ((-1, 0, "east"), (1, 0, "west"), (0, -1, "up"), (0, 1, "down")):
                if (x + dx, y + dy) not in removed:
                    continue
                lower, upper = [right - 1, top - 1, 4.75], [right, top, 7.25]
                axis = 0 if dx else 1
                boundary = (right if direction == "east" else right - 1) if dx else (
                    top if direction == "up" else top - 1)
                lower[axis] = upper[axis] = boundary
                elements.append({"from": lower, "to": upper, "faces": {direction: {"uv": uv}}})
    faces = (face for element in elements[3:] for face in element["faces"].values())
    shutter_texture(art, "frame", frame, faces, source.size, SHUTTER_FRAME_BOUNDS)


def shutter_glass(art):
    (glass,) = art.model["elements"]
    faces = glass["faces"]
    source = art.face_image(faces["north"])
    bounds = uv_rect(faces["north"], source.size)
    pane = source.crop(bounds)
    assert glass["to"] == [16, 22, 6] and pane.size == (16, 30)
    # The pane starts one pixel in from the right of the frame and two below
    # the crown. Keep it inside the same pixel silhouette on both faces.
    for y, (low, high) in enumerate(SHUTTER_CROWN[2:]):
        for x in range(pane.width):
            if not low <= SHUTTER_FRAME_BOUNDS[0] + 1 + x <= high:
                pane.putpixel((x, y), (0, 0, 0, 0))
    shutter_texture(art, "glass", pane, faces.values(), source.size, bounds)


def darken(art, number):
    textures = art.model["textures"]
    replacements = {}
    # Resolve aliases before changing any dictionary entry so all faces that
    # shared one sprite in this model keep sharing one local replacement.
    resolved = {key: resolve_texture(art.model, "#" + key) for key in textures}
    for key, sprite in resolved.items():
        if sprite not in replacements:
            image = art.source.image(sprite, art.profile)
            dark = ImageEnhance.Brightness(image.convert("RGB")).enhance(DARKEN[number])
            dark.putalpha(image.getchannel("A"))
            alias = art.add_texture("stone_" + str(len(replacements)), dark)
            replacements[sprite] = alias
        textures[key] = replacements[sprite]


def fit_arched_door(recipe):
    # The actual frame reaches Y=48 at the doorway and the moving leaf is
    # 34 units tall. The earlier envelope continued above the visible mesh.
    for pose in recipe["variants"][0]["poses"].values():
        for field in ("collision", "selection"):
            left, right, header, _, _, leaf = pose[field]
            for frame in (left, right, header):
                frame["to"][1] = 48
            header["from"][1] = 34
            leaf["to"][1] = 34


def retire_window_surround(jar, output):
    path = f"{COMPOSITES}{RETIRED_SURROUND}.json"
    recipe = json.loads(jar.read(path))
    for variant in recipe["variants"]:
        variant["poses"] = {"closed": {"parts": [], "collision": [], "selection": []}}
    write_json(output, path, recipe)

    path = f"{NAMESPACE}/remaining-catalogue.json"
    catalogue = json.loads(jar.read(path))
    row = next(row for row in catalogue["objects"] if row["path"] == RETIRED_SURROUND)
    row["conversionOnly"] = True
    write_json(output, path, catalogue)
    write_json(output, f"{MODELS}item/{RETIRED_SURROUND}.json", {"elements": []})


def retire_duplicate_shutters(jar, output, include_studio_map):
    path = f"{NAMESPACE}/debug_catalogue.json"
    catalogue = json.loads((output / path).read_bytes())
    old_ids = {f"{NAMESPACE}:{name}" for name in RETIRED_SHUTTERS}
    catalogue["entries"] = [row for row in catalogue["entries"] if row["registryId"] not in old_ids]
    write_json(output, path, catalogue)

    # The live map has already migrated every old window out of the static
    # banks. Keep the old indices and their removal edges valid with inert art.
    path = f"{NAMESPACE}/static-architecture-v1274.json"
    static = json.loads((output / path).read_bytes())
    retired_keys = set()
    for pose in static["poses"]:
        if pose["registryId"] in old_ids:
            pose["registryId"] = f"{NAMESPACE}:{RETIRED_SURROUND}"
            retired_keys.add(pose["key"])
    for recipe in static["recipes"]:
        if any(fragment["pose"] in retired_keys for fragment in recipe["fragments"]):
            recipe["physicalFromPoses"] = True
    write_json(output, path, static)
    append_path = Path(__file__).with_name("studio-static-append.json")
    if include_studio_map and append_path.is_file():
        supplement = json.loads(append_path.read_bytes())
        base = (output / path).read_bytes()
        if hashlib.sha256(base).hexdigest() != supplement["baseSha256"] or len(static["recipes"]) != supplement["baseRecipes"]:
            raise ValueError("Studio map requires the inventoried static catalogue")
        old_banks = (len(static["recipes"]) + 127) // 128
        static["poses"].extend(supplement["poses"])
        static["recipes"].extend(supplement["recipes"])
        banks = (len(static["recipes"]) + 127) // 128
        write_json(output, path, static)
        template = json.loads(jar.read(f"assets/{NAMESPACE}/blockstates/static_architecture_0.json"))
        for bank in range(old_banks, banks):
            write_json(output, f"assets/{NAMESPACE}/blockstates/static_architecture_{bank}.json", template)


def generate(jar, output, profile_resources=None, include_studio_map=True):
    # The first pass always rewrites the 90986 recipe before this pass runs.
    # Other recipes are loaded from independent inputs, so regeneration is idempotent.
    roots = (output, profile_resources) if profile_resources else ()
    source = Source(jar, roots)
    produced = {}

    def replace(part, number):
        for key in ("model", "altModel"):
            identifier = part.get(key)
            if identifier is None:
                continue
            cache_key = (number, key, identifier)
            if cache_key not in produced:
                stem = identifier.rsplit("/", 1)[-1]
                role = "base" if key == "model" else "alternate"
                alias = f"builder_followup/{number}/{role}/{stem}"
                for profile in ("original", "alt"):
                    art = Art(source, output, alias, profile, source.model(identifier, profile))
                    if number in DARKEN:
                        darken(art, number)
                    elif number in ("90001", "90986"):
                        fix_door(art, number)
                    elif number == "93066":
                        strip_window_bricks(art)
                    elif stem == "shutter_glass":
                        shutter_glass(art)
                    else:
                        shutter_frame(art)
                    art.save()
                produced[cache_key] = f"{NAMESPACE}:{alias}"
            part[key] = produced[cache_key]

    for number, registry in REGISTRIES.items():
        path = COMPOSITES + registry + ".json"
        original = INPUT_RECIPES / (registry + ".json")
        if profile_resources and original.is_file():
            recipe = json.loads(original.read_bytes())
        elif profile_resources and number == "90986":
            recipe = json.loads((output / path).read_bytes())
        else:
            recipe = json.loads(jar.read(path))
        if number == "93241":
            recipe["variants"] = recipe["variants"][:1]
        elif number == "90986":
            fit_arched_door(recipe)
        for variant in recipe["variants"]:
            for pose_key, pose in variant["poses"].items():
                if number == "93241":
                    pose["parts"] = [part for part in pose["parts"]
                                     if not part["model"].endswith("/shutter_backdrop")]
                for part in pose["parts"]:
                    if number == "90001" and (pose_key != "open" or not part["model"].endswith("/central_leaf")):
                        continue
                    replace(part, number)
        write_json(output, path, recipe)

    add_hold_lantern(jar, output, profile_resources)
    retire_window_surround(jar, output)
    if profile_resources:
        retire_duplicate_shutters(jar, output, include_studio_map)
    print(f"Builder follow-up: {len(REGISTRIES) + 1} recipes, {len(produced)} model aliases in Original and ALT")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release", type=Path, required=True)
    parser.add_argument("--resources", type=Path, required=True)
    args = parser.parse_args()
    with ZipFile(args.release) as jar:
        generate(jar, args.resources)
