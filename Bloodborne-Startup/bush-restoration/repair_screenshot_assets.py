"""Repair the specific source assets and fitted shapes shown in the building review."""

from copy import deepcopy
from math import cos, pi, sin

from generate_form_assets import ASSETS, DATA, read


COLLISION_125 = DATA + "imported-collision-v125.json"
COLLISION_127 = DATA + "imported-full-map-v127.json"
COMPOSITES = DATA + "composite/"
MODELS = ASSETS + "models/"
SOURCE_MODELS = MODELS + "remaining/source/minecraft/block/"
GLASS = "source_light_blue_stained_glass"
BALUSTRADE = "source_warped_trapdoor_open"
PILASTER = "source_dark_oak_trapdoor_open"
CONSOLE = "source_quartz_stairs_outer_1"
WALL = "source_compatibility_fragment"
WALL_POSE = 33
PILASTER_DROP = 4


def box(start, end):
    return {"from": list(start), "to": list(end)}


def rotated_box(start, end, yaw):
    angle = yaw * pi / 4
    c, s = cos(angle), sin(angle)
    vertices = [(8 + c * (x - 8) - s * (z - 8), 8 + s * (x - 8) + c * (z - 8))
                for x in (start[0], end[0]) for z in (start[2], end[2])]
    xs, zs = zip(*vertices)
    return box((round(min(xs), 8), start[1], round(min(zs), 8)),
               (round(max(xs), 8), end[1], round(max(zs), 8)))


def railing_shape(start, end, yaw):
    # Keep the narrow diagonal barrier. One rotated bounding box for the
    # whole rail would block the empty corners of its cell.
    if yaw % 2 == 0:
        return [rotated_box(start, end, yaw)]
    return [rotated_box((x, start[1], start[2]), (min(x + 4, end[0]), end[1], end[2]), yaw)
            for x in range(start[0], end[0], 4)]


def lower_boxes(boxes):
    for shape in boxes:
        shape["from"][1] = max(0, shape["from"][1] - PILASTER_DROP)
        shape["to"][1] -= PILASTER_DROP


def fitted_shapes(release):
    doc = read(release, COLLISION_125)

    def replace(source, edit):
        # Shape indices are interned across unrelated objects. Append private
        # rows so each correction applies only to its own source family.
        mapped = {}
        for form in doc["objects"]["bloodborne_dw:" + source]:
            for key, index in form.items():
                if index not in mapped:
                    rows = deepcopy(doc["shapes"][index])
                    for yaw, row in enumerate(rows):
                        edit(yaw, row)
                    mapped[index] = len(doc["shapes"])
                    doc["shapes"].append(rows)
                form[key] = mapped[index]

    def glass(_yaw, row):
        # The rendered cube is already 16 units high. V125 retained the old
        # two-block collision and allocated a second invisible carrier.
        for shape in row["collision"] + row["aim"]:
            if shape["to"][1] != 32:
                raise ValueError("Unexpected fitted height for patterned blue/pink glass")
            shape["to"][1] = 16

    def balustrade(yaw, row):
        # The base stays fitted. The visible balusters and the top rail must
        # stop a player even where the imported render uses zero-thick planes.
        row["collision"] = [shape for shape in row["collision"] if shape["to"][1] <= 13]
        row["collision"] += railing_shape((0, 12, 7), (16, 29, 9), yaw)
        row["collision"] += railing_shape((0, 29, 3.5), (16, 32, 12.5), yaw)
        if len(row["collision"]) > doc["maxBoxesPerOrientation"]:
            raise ValueError("Balustrade collision exceeds the fitted box budget")

    def pilaster(_yaw, row):
        lower_boxes(row["collision"])
        lower_boxes(row["aim"])

    def console(_yaw, row):
        # The block reservation is one grid cell even at a diagonal yaw.
        row["collision"] = [box((0, 0, 0), (16, 16, 16))]
        row["aim"] = deepcopy(row["collision"])

    replace(GLASS, glass)
    replace(BALUSTRADE, balustrade)
    replace(PILASTER, pilaster)
    replace(CONSOLE, console)
    return doc


def compatibility_wall(release):
    doc = read(release, COLLISION_127)
    row, = [row for row in doc["objects"]
            if row["registryId"] == "bloodborne_dw:" + WALL and row["variant"] == WALL_POSE]
    for yaw, shapes in enumerate(row["rotations"]):
        if shapes["collision"]:
            raise ValueError("Unexpected collision for hidden source wall 34")
        shapes["collision"] = railing_shape((0, 0, 5), (16, 16, 11), (yaw + 2) % 8)
    return doc


def source_geometry(release):
    result = {}
    for source in (BALUSTRADE, PILASTER, WALL):
        path = COMPOSITES + source + ".json"
        doc = result[path] = read(release, path)
        variant = doc["variants"][WALL_POSE if source == WALL else 0]
        for pose in variant["poses"].values():
            if source == BALUSTRADE:
                pose["collision"] = [
                    box((0, 0, 2.5), (16, 13, 13.5)),
                    box((0, 12, 7), (16, 29, 9)),
                    box((0, 29, 3.5), (16, 32, 12.5)),
                ]
                pose["simpleCollisionByRotation"] = [
                    [rotated_box(shape["from"], shape["to"], yaw) for shape in pose["collision"]]
                    for yaw in range(8)
                ]
            elif source == PILASTER:
                # This one source item contains the entire pilaster. The
                # lowered crest still starts from the same support surface.
                for key in ("collision", "selection"):
                    lower_boxes(pose[key])
                for key in ("simpleCollisionByRotation", "simpleSelectionByRotation"):
                    for boxes in pose.get(key, ()):
                        lower_boxes(boxes)
            else:
                pose["collision"] = deepcopy(pose["selection"])
        if source == PILASTER:
            variant["visibleBounds"]["to"][1] -= PILASTER_DROP
    return result


def door_pane(element, side, y_offset):
    original = element["faces"][side]
    u0, v0, u1, v1 = original["uv"]
    x0, y0, z = element["from"]
    x1, y1, _ = element["to"]

    def u(x):
        fraction = (x - x0) / (x1 - x0)
        return u0 + (u1 - u0) * (1 - fraction if side == "north" else fraction)

    def v(y):
        return v0 + (v1 - v0) * ((y1 - y) / (y1 - y0))

    result = []
    # spirelamp_0018 has two holes in the 48x23 frame region. These
    # rectangles are the holes transformed from texture pixels into model
    # coordinates. The two sides share their boundaries and the mullion.
    for left, right in ((-5, 6), (10, 21)):
        bottom, top = -12 + y_offset, y_offset
        pane = deepcopy(element)
        plane_z = z - 1 / 16 if side == "south" else z
        pane["from"] = [left, bottom, plane_z]
        pane["to"] = [right, top, plane_z]
        face = deepcopy(original)
        u_left, u_right = u(left), u(right)
        face["uv"] = [u_right if side == "north" else u_left, v(top),
                      u_left if side == "north" else u_right, v(bottom)]
        pane["faces"] = {side: face}
        result.append(pane)
    return result


def door_glass(release):
    # The stacked birch-grille doorway supplies the translucent pane seen
    # above the dark door. Cover the same two apertures in the unsplit
    # model and the generated open-door model.
    result = {}
    for path, y_offset in ((SOURCE_MODELS + "bir_door_2.json", 0),
                           (MODELS + "v126/doors/93152/static_0_1_0.json", 16)):
        doc = read(release, path)
        panes, keep = [], []
        for element in doc["elements"]:
            texture_keys = {face["texture"].removeprefix("#") for face in element["faces"].values()}
            glass = any(doc["textures"].get(key, "").endswith("/window_3") for key in texture_keys)
            if glass:
                for side in ("north", "south"):
                    if side in element["faces"]:
                        panes.extend(door_pane(element, side, y_offset))
                continue
            rear = element["faces"].get("south")
            if rear and rear.get("uv") == [1, 1.125, 7, 4]:
                # Match the front frame's symmetric hole mask. Previously
                # the rear sampled that mask a pixel to the right.
                rear["uv"] = [0.875, 1.125, 6.875, 4]
            keep.append(element)
        if len(panes) != 4:
            raise ValueError(f"Expected paired door apertures in {path}")
        doc["elements"] = keep + panes
        result[path] = doc
    return result


def pilaster_model(release):
    path = SOURCE_MODELS + "dark_oak_trapdoor_open.json"
    doc = read(release, path)
    # Compress the base course against the floor and lower the body. Move
    # the rotation pivot with each element, keeping the diagonal crest.
    for element in doc["elements"]:
        for point in (element["from"], element["to"]):
            point[1] = max(-16, point[1] - PILASTER_DROP)
        if "rotation" in element:
            element["rotation"]["origin"][1] -= PILASTER_DROP
    return {path: doc}


def door_frame(release):
    # These one-cuboid models are shared by both door poses. ALT inherits
    # the same mesh through its corresponding model parents.
    frame = {
        "left_panel": ("east", "#0", [1.875, 10, 2, 16]),
        "right_panel": ("west", "#0", [4, 10, 4.125, 16]),
        "source_header": ("down", "#1", [0, 3.5, 12, 3.75]),
    }
    result = {}
    for model, (side, texture, uv) in frame.items():
        path = MODELS + f"base/prototype_door/{model}.json"
        doc = read(release, path)
        element, = doc["elements"]
        if set(element["faces"]) != {"north", "south"}:
            raise ValueError(f"Unexpected sculpted door frame faces in {path}")
        # Reuse the opaque inner edge pixels from each source texture.
        element["faces"][side] = {"texture": texture, "uv": uv}
        result[path] = doc
    return result


def console_model(release):
    path = SOURCE_MODELS + "quartz_stairs_outer_1.json"
    doc = read(release, path)
    lower = [element for element in doc["elements"] if element["to"][1] <= 0]
    if [(element["from"], element["to"]) for element in lower] != [([0, -5, 6], [10, 0, 16])]:
        raise ValueError(f"Unexpected lower console piece in {path}")
    doc["elements"].remove(lower[0])

    spec_path = COMPOSITES + CONSOLE + ".json"
    spec = read(release, spec_path)
    variant, = spec["variants"]
    pose = variant["poses"]["closed"]
    pose["collision"] = [box((0, 0, 0), (16, 16, 16))]
    pose["simpleCollisionByRotation"] = [deepcopy(pose["collision"]) for _ in range(8)]
    pose["selection"] = [shape for shape in pose["selection"] if shape["to"][1] > 5]
    variant["visibleBounds"]["from"][1] = min(shape["from"][1] for shape in pose["selection"])
    return {path: doc, spec_path: spec}


def screenshot_replacements(release):
    """Return JSON replacements without touching the native city catalog or saved map."""
    return {
        COLLISION_125: fitted_shapes(release),
        COLLISION_127: compatibility_wall(release),
        **source_geometry(release),
        **pilaster_model(release),
        **door_glass(release),
        **door_frame(release),
        **console_model(release),
    }
