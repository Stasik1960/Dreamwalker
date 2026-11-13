"""Build the connecting fence models from the matching DreamWalker release assets."""

from copy import deepcopy
import json


DATA = "bloodborne_dw/"
ASSETS = "assets/bloodborne_dw/"
MODEL_ROOT = "bloodborne_dw:block/"
SOURCE = ASSETS + "models/remaining/source/minecraft/block/"
DIRECTIONS = ("north", "east", "south", "west")
HORIZONTAL_FACES = ("north", "south", "west", "east")
PANE_SOURCE = "source_assembly_cyan_stained_glass_pane"


def read_json(archive, path):
    return json.loads(archive.read(path))


def face_element(lo, hi, texture, uv, sides=HORIZONTAL_FACES):
    return {"from": list(lo), "to": list(hi),
            "faces": {side: {"texture": texture, "uv": list(uv)} for side in sides}}


def move(element, axis, distance):
    copy = deepcopy(element)
    for field in ("from", "to"):
        copy[field][axis] += distance
    if "rotation" in copy:
        copy["rotation"]["origin"][axis] += distance
    return copy


def resize(element, *bounds):
    copy = deepcopy(element)
    for axis, low, high in bounds:
        copy["from"][axis], copy["to"][axis] = low, high
    return copy


def slice_panel(element, axis, low, high):
    """Crop an unrotated broad face, matching Minecraft's UV direction per face."""
    faces = ("east", "west") if axis == 2 else ("north", "south")
    reverse = "east" if axis == 2 else "north"
    source_low, source_high = element["from"][axis], element["to"][axis]
    start = (low - source_low) / (source_high - source_low)
    end = (high - source_low) / (source_high - source_low)
    result = resize(element, (axis, low, high))
    result["faces"] = {}
    for side in faces:
        face = deepcopy(element["faces"][side])
        u0, _, u1, _ = face["uv"]
        first, last = (1 - end, 1 - start) if side == reverse else (start, end)
        face["uv"][0] = round(u0 + (u1 - u0) * first, 6)
        face["uv"][2] = round(u0 + (u1 - u0) * last, 6)
        result["faces"][side] = face
    return result


def turn_panel_north(element):
    """Rotate an X-wide panel onto the Z axis and preserve both face textures."""
    lo, hi = element["from"], element["to"]
    return {"from": [lo[2], lo[1], 16 - hi[0]], "to": [hi[2], hi[1], 16 - lo[0]],
            "faces": {to: deepcopy(element["faces"][source])
                      for source, to in (("north", "west"), ("south", "east"))}}


def pane_set(models, color, stem_uv=None):
    base = f"{color}_stained_glass_pane_"
    north = models(base + "side")
    south = models(base + "side_alt")
    faces = {}
    for suffix in ("noside", "noside_alt"):
        for element in models(base + suffix):
            for side, face in element["faces"].items():
                if side in HORIZONTAL_FACES:
                    faces.setdefault(side, face)
    art = next(iter(faces.values()))
    if stem_uv is None:
        stem = [{"from": [7, 0, 7], "to": [9, north[0]["to"][1], 9],
                 "faces": {side: deepcopy(faces.get(side, art)) for side in HORIZONTAL_FACES}}]
    else:
        stem = [face_element((7, 0, 7), (9, north[0]["to"][1], 9), stem_uv[0], stem_uv[1])]
    # The pink source adds sculpted hardware beside its central spike to one arm.
    # Keep that hardware on the post, where it belongs in every connection state.
    if color == "pink":
        for element in models(base + "noside_alt")[1:]:
            stem.append(element)
        south = south[:1]
    return stem, north, south


def cap_bars(elements, tops):
    panel = elements[0]
    texture = panel["faces"]["east"]["texture"]
    # This patch of the source atlas is opaque on all four pixels.
    uv = (4.625, 11.875, 4.875, 12.125)
    return [*elements, *(face_element((panel["from"][0], height, start),
                                     (panel["to"][0], height, end), texture, uv, ("up",))
                         for height, start, end in tops)]


def straight_bar_set(models):
    _, north, south = pane_set(models, "light_gray")
    # The original map grille runs each horizontal rail across the center
    # strip. That strip's alpha leaves the middle open between the rails.
    center = models("light_gray_stained_glass_pane_noside")[0]
    source = center["faces"]
    source.pop("up")  # The middle has crossing rails but no vertical stem.
    center["faces"] = {
        **source,
        "east": deepcopy(source["south"]),
        "west": deepcopy(source["north"]),
    }
    north = cap_bars(north, ((29, 1, 2), (29, 5, 6)))
    south = cap_bars(south, ((29, 9, 10), (29, 13, 14)))
    return [center], north, south


def iron_post_set(models):
    _, north, south = pane_set(models, "pink")
    base = "pink_stained_glass_pane_"
    stem = models(base + "noside") + models(base + "noside_alt")
    # Keep the old model's fitted collar and darker solid spike on the
    # center of the post, including when adjacent posts grow their arms.
    stem[1] = resize(stem[1], (0, 7.5, 8.5))
    for side in ("east", "west"):
        stem[0]["faces"][side] = deepcopy(stem[0]["faces"]["south"])
        stem[2]["faces"].pop(side)
    north = cap_bars(north, ((29, 1, 2), (29, 5, 6)))
    south = cap_bars(south, ((32, 9, 10), (29, 13, 14)))
    spike_top = stem[1]["faces"]["up"]
    spike_top["uv"] = [4.625, 11.875, 4.875, 12.125]
    return [element for element in stem if element["faces"]], north, south


def iron_set(models):
    source_post = models("iron_bars_post")
    north = models("iron_bars_side")
    south = models("iron_bars_side_alt")
    stone = source_post[1]
    iron = north[0]["faces"]["east"]["texture"]
    stem = [stone, face_element((7, 5, 7), (9, 23, 9), iron, (4.25, 10.25, 4.375, 8))]
    return stem, [north[0], resize(north[1], (2, 0, 6))], [south[0], resize(south[1], (2, 10, 16))]


def blue_set(models):
    _, north, south = pane_set(models, "blue")
    trim = [resize(element, (2, 6, 10)) for element in north[:2]]
    stem = face_element((7, 4, 7), (9, 20, 9), north[2]["faces"]["east"]["texture"],
                        (11.25, 7.875, 11.375, 9.875))
    return [*trim, stem], [resize(e, (2, 0, 6)) for e in north[:2]] + north[2:], [
        resize(e, (2, 10, 16)) for e in south[:2]] + south[2:]


def forged_set(models):
    base_arm, plinth, grille, footing, _ = models("stairs/dark_prismarine_stairs_outer_right_bottom")
    center = [resize(footing, (0, 2, 14), (2, 2, 14)), resize(plinth, (2, 5, 11)),
              face_element((7.5, 4, 7.5), (8.5, 28, 8.5), grille["faces"]["east"]["texture"],
                           (8, 12.875, 8.125, 15.875))]
    north = [resize(base_arm, (2, 0, 5)), resize(footing, (2, 0, 2)),
             slice_panel(grille, 2, 0, 7.5)]
    south = [resize(base_arm, (2, 11, 16)), resize(footing, (2, 14, 16)),
             slice_panel(grille, 2, 8.5, 16)]
    return center, north, south


def finial_set(models):
    plinth, finial = models("slabs/roof_01")
    # This source is a standalone finial on a slab. Keep its scrollwork and add
    # narrow connectors from the original metal and the slab's matching stone.
    center_finial = resize(finial, (0, 2.5, 13.5))
    center = [resize(plinth, (0, 3, 13), (2, 3, 13)), center_finial, turn_panel_north(center_finial)]
    metal = finial["faces"]["north"]["texture"]
    north = [resize(plinth, (0, 5, 11), (2, 0, 3)),
             face_element((7.5, 13, 0), (8.5, 14.5, 8), metal, (11.875, 2, 12, 2.125),
                          ("east", "west", "up", "down")),
             face_element((7.5, 19, 0), (8.5, 20.5, 8), metal, (11.875, 2, 12, 2.125),
                          ("east", "west", "up", "down"))]
    south = [move(element, 2, 16 - element["from"][2] - element["to"][2]) for element in north]
    return center, north, south


def pedestal_set(models):
    panel, *base = [move(element, 1, 16) for element in models("stairs/cut_copper_stairs_inner_right")]
    panel = resize(panel, (1, 9, 32))
    center = [resize(element, (0, 3, 13), (2, 3, 13)) for element in base]
    middle = turn_panel_north(slice_panel(panel, 0, 7, 9))
    north = [resize(e, (0, 5, 11), (2, 0, 3)) for e in base]
    south = [move(element, 2, 13) for element in north]
    north.append(turn_panel_north(slice_panel(panel, 0, 9, 16)))
    south.append(turn_panel_north(slice_panel(panel, 0, 0, 7)))
    return [*center, middle], north, south


def column_set(models):
    lower = models("sandstone_wall_side_tall")
    upper = models("red_nether_brick_wall_side_tall")
    halves = {}
    for name, elements in (("lower", lower), ("upper", upper)):
        *center, rail = elements
        # Preserve the two adjoining halves of the original contiguous UV art.
        # Each arm only occupies space between the stone and the neighboring block.
        north = turn_panel_north(slice_panel(rail, 0, 10, 16))
        south = turn_panel_north(slice_panel(rail, 0, 0, 6))
        halves[name] = center, [north], [south]
    return halves


def flatten(archive):
    cache = {}

    def read_model(path):
        if path.startswith("bloodborne_dw:"):
            resource = ASSETS + "models/" + path.split(":", 1)[1] + ".json"
        else:
            resource = SOURCE + path + ".json"
        if resource in cache:
            return cache[resource]
        model = read_json(archive, resource)
        parent = model.get("parent")
        elements, materials = [], {}
        if parent and parent.startswith("bloodborne_dw:"):
            elements, materials = read_model(parent)
        merged = (model.get("elements", elements), materials | model.get("textures", {}))
        cache[resource] = merged
        return merged

    def resolve(name):
        elements, materials = read_model(name)
        result = deepcopy(elements)
        for element in result:
            # Zero-angle transforms only retain an irrelevant editing origin.
            if element.get("rotation", {}).get("angle") == 0:
                del element["rotation"]
            for face in element["faces"].values():
                texture = face["texture"]
                while texture.startswith("#"):
                    texture = materials[texture[1:]]
                face["texture"] = texture
                face.pop("cullface", None)
        return result

    return resolve


def model_json(elements, gui=False):
    textures = {}
    formatted = deepcopy(elements)
    for element in formatted:
        for face in element["faces"].values():
            texture = face["texture"]
            key = textures.setdefault(texture, f"material{len(textures)}")
            face["texture"] = "#" + key
    document = {"parent": "minecraft:block/block", "textures": {
        "particle": next(iter(textures)), **{name: texture for texture, name in textures.items()}},
        "elements": formatted}
    if gui:
        document["display"] = {"gui": {"rotation": [22, 320, 0], "translation": [0, -3, 0], "scale": [0.52] * 3}}
    return document


def put_models(output, key, models, half=None, isolated_grille=False):
    stem, north, south = models
    suffix = "_" + half if half else ""
    half_condition = {"half": half} if half else {}
    condition = half_condition | {"wall_axis": "none|same_id"}
    center_key = key + suffix
    output[ASSETS + f"models/block/{center_key}.json"] = model_json(stem)
    multipart = [{"apply": {"model": MODEL_ROOT + center_key}}]
    if isolated_grille:
        isolated = center_key + "_isolated"
        # Preserve the full grille pattern in the short panel by compressing
        # the geometry around the center and keeping its original UVs.
        elements = [resize(element, (2, 4 + element["from"][2] / 2, 4 + element["to"][2] / 2))
                    for element in (*stem, *north, *south)]
        output[ASSETS + f"models/block/{isolated}.json"] = model_json(elements)
        multipart[0]["when"] = {"OR": [condition | {direction: "true"} for direction in DIRECTIONS]}
        multipart.append({"when": condition | {direction: "false" for direction in DIRECTIONS},
                          "apply": {"model": MODEL_ROOT + isolated}})
    else:
        multipart[0]["when"] = condition
    for direction, rotation in zip(DIRECTIONS, (0, 90, 0, 90)):
        arm = "north" if direction in ("north", "east") else "south"
        model_key = f"{center_key}_{arm}"
        if rotation == 0:
            output[ASSETS + f"models/block/{model_key}.json"] = model_json(north if arm == "north" else south)
        transform = {"model": MODEL_ROOT + model_key}
        if rotation:
            transform["y"] = rotation
        multipart.append({"when": condition | {direction: "true"}, "apply": transform})
    straight = center_key + "_wall"
    output[ASSETS + f"models/block/{straight}.json"] = model_json([*stem, *north, *south])
    multipart.extend((
        {"when": half_condition | {"wall_axis": "z"}, "apply": {"model": MODEL_ROOT + straight}},
        {"when": half_condition | {"wall_axis": "x"}, "apply": {"model": MODEL_ROOT + straight, "y": 90}},
    ))
    return multipart


def fence_replacements(release):
    """Return JSON entry replacements for the connecting-fences feature."""
    models = flatten(release)
    catalogue = read_json(release, DATA + "debug_catalogue.json")
    sources = {int(row["temporaryId"]): row["registryId"].split(":", 1)[1]
               for row in catalogue["entries"] + catalogue.get("retiredEntries", [])}
    legacy = read_json(release, DATA + f"composite/{PANE_SOURCE}.json")
    poses = {color: [] for color in ("cyan", "light_gray", "pink", "yellow")}
    for index, variant in enumerate(legacy["variants"]):
        model = variant["poses"]["closed"]["parts"][0]["model"].rsplit("/", 1)[1]
        color = model.split("_stained_glass_pane_")[0]
        poses[color].append(index)

    art_039 = models("yellow_stained_glass_pane_side")[0]["faces"]["east"]["texture"]
    styles = [
        ("93195", (93195,), "Железная ограда на цоколе", "Iron Fence on Stone Base",
         iron_set(models), None),
        ("93185_plain", (93185, 94279), "Ограда с прямыми прутьями", "Straight-Bar Fence",
         straight_bar_set(models), "light_gray"),
        ("93185_ornate", (93185, 94272), "Резная ограда", "Ornate Fence",
         pane_set(models, "cyan"), "cyan"),
        ("93185_double", (93185, 94293), "Ограда с двойной вязью", "Double-Rail Fence",
         pane_set(models, "yellow", (art_039, (1.25, 11.875, 1.375, 15.875))), "yellow"),
        ("93185_post", (93185, 94286), "Стойка для ограды", "Fence Post",
         iron_post_set(models), "pink"),
        ("93164", (93164,), "Кованая ограда с ромбами", "Diamond Iron Fence",
         pane_set(models, "black", (models("black_stained_glass_pane_side")[0]["faces"]["east"]["texture"],
                                    (10, 0, 11, 16))), None),
        ("93181", (93181,), "Каменная балюстрада", "Stone Balustrade",
         pane_set(models, "brown"), None),
        ("93214", (93214, 93228), "Кованая ограда с крестом", "Crosswork Iron Fence",
         pane_set(models, "white"), None),
        ("90993", (90993,), "Ограда на низком цоколе", "Low-Plinth Fence",
         forged_set(models), None),
        ("90910", (90910,), "Ограда с кованым навершием", "Iron Finial Fence",
         finial_set(models), None),
        ("90969", (90969,), "Решётка на каменном цоколе", "Stone-Pedestal Grille",
         pedestal_set(models), None),
        ("93166", (93166,), "Ограда в каменной раме", "Stone-Framed Fence",
         blue_set(models), None),
        ("column", (90876, 90853), "Двухблочный столб ограды", "Two-Block Fence Column",
         column_set(models), None),
    ]
    replacements = {}
    fences = []
    lang = {code: read_json(release, ASSETS + f"lang/{code}.json") for code in ("ru_ru", "en_us")}
    for number, (key, legacy_numbers, russian, english, geometry, source_style) in enumerate(styles, 96001):
        registry = "connected_fence_" + key
        shown_numbers = ["93185"] if source_style else [str(value) for value in legacy_numbers]
        label = " (" + " / ".join(shown_numbers) + f") [{number}]"
        old_sources = [sources[n] for n in ((93185,) if source_style else legacy_numbers)]
        # The shared original ID opens the straight-bar replacement. Its
        # pose-specific numbers are routed using the explicit sourcePoses map.
        aliases = list(legacy_numbers if source_style in (None, "light_gray") else legacy_numbers[1:])
        fence = {"id": registry, "name": russian + label, "number": str(number),
                 "legacyNumbers": [str(alias) for alias in aliases],
                 "sources": old_sources, "post": key == "column", "pane": True}
        if source_style:
            fence["sourcePoses"] = {PANE_SOURCE: poses[source_style]}
        elif key == "90969":
            # Earlier variants in this family are independent bollards.
            fence["sourcePoses"] = {old_sources[0]: [2]}
        fences.append(fence)

        if fence["post"]:
            parts = [part for half, models_by_half in geometry.items()
                     for part in put_models(replacements, registry, models_by_half, half)]
            item_elements = [*geometry["lower"][0], *(move(e, 1, 16) for e in geometry["upper"][0])]
        else:
            parts = put_models(replacements, registry, geometry, isolated_grille=key != "93185_post")
            center, north, south = geometry
            item_elements = center if key == "93185_post" else [*center, *north, *south]
        replacements[ASSETS + f"blockstates/{registry}.json"] = {"multipart": parts}
        replacements[ASSETS + f"models/item/{registry}.json"] = model_json(item_elements, gui=True)
        for code, name in (("ru_ru", russian), ("en_us", english)):
            lang[code][f"block.bloodborne_dw.{registry}"] = name + label
            lang[code][f"item.bloodborne_dw.{registry}"] = name + label

    replacements[DATA + "unified-fences.json"] = {"fences": fences}
    replacements.update({ASSETS + f"lang/{code}.json": document for code, document in lang.items()})
    return replacements
