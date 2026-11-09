"""Offline counterpart of the reviewed cardinal and diagonal Studio transforms."""
from collections import defaultdict
from itertools import product
from math import ceil, cos, floor, pi, sin

EPS = 1e-8
ZERO = (0, 0, 0)


def add(a, b):
    return tuple(x + y for x, y in zip(a, b, strict=True))


def subtract(a, b):
    return tuple(x - y for x, y in zip(a, b, strict=True))


def rotate(vector, yaw):
    x, y, z = vector
    angle = (yaw % 8) * pi / 4
    c, s = cos(angle), sin(angle)
    if yaw % 2 == 0:
        c, s = round(c), round(s)
    return x * c - z * s, y, x * s + z * c


def point(vector, yaw):
    pivot = (.5, .5, .5)
    return add(pivot, rotate(subtract(vector, pivot), yaw))


def pose_key(props):
    return int(props.get("variant", 0)) + 16 * int(props.get("art_bank", 0)) + 10 * (props.get("trim") == "true"), props.get("open") == "true"


def selected(model, props):
    if model is None:
        return None
    return model["poses"].get(pose_key(props))


def read_models(project):
    return {model["registryId"]: {"anchor": tuple(model.get("anchorCell", ZERO)), "poses": {
        (state["selector"].get("variant", 0), state["selector"].get("open", state["selector"].get("Open", False))): state
        for state in model["states"]}} for model in project["models"]}


def clip(box, out):
    minimum, maximum = box[:3], box[3:]
    for cell in product(*(range(floor(a + EPS), ceil(b - EPS)) for a, b in zip(minimum, maximum))):
        lo = [max(0., v - c) for v, c in zip(minimum, cell)]
        hi = [min(1., v - c) for v, c in zip(maximum, cell)]
        if all(b - a > EPS for a, b in zip(lo, hi)):
            out[cell].append(lo + hi)


def intersects(polygon, x0, z0, x1, z1):
    rectangle = ((x0, z0), (x1, z0), (x1, z1), (x0, z1))
    axes = [(1, 0), (0, 1)]
    for (x, z), (next_x, next_z) in zip(polygon, (*polygon[1:], polygon[0])):
        axes.append((z - next_z, next_x - x))
    for ax, az in axes:
        a = [x * ax + z * az for x, z in polygon]
        b = [x * ax + z * az for x, z in rectangle]
        if max(a) <= min(b) + 1e-9 or max(b) <= min(a) + 1e-9:
            return False
    return True


def boxes_for(collider, anchor, yaw, offset, grid=16):
    if any(collider["rotation"]):
        raise ValueError("This reviewed archive contains only axis-aligned authoring volumes")
    center = subtract(tuple(v / 16 for v in collider["center"]), anchor)
    half = tuple(v / 32 for v in collider["size"])
    lo, hi = subtract(center, half), add(center, half)
    vertices = [point((x, lo[1], z), yaw) for x, z in ((lo[0], lo[2]), (hi[0], lo[2]), (hi[0], hi[2]), (lo[0], hi[2]))]
    x0, x1 = min(v[0] for v in vertices), max(v[0] for v in vertices)
    z0, z1 = min(v[2] for v in vertices), max(v[2] for v in vertices)
    if yaw % 2 == 0:
        return [(*add((x0, lo[1], z0), offset), *add((x1, hi[1], z1), offset))]
    polygon = [(x, z) for x, _, z in vertices]
    strips = []
    for z in range(floor(z0 * grid), ceil(z1 * grid)):
        first = None
        end = ceil(x1 * grid)
        for x in range(floor(x0 * grid), end + 1):
            occupied = x < end and intersects(polygon, x / grid, z / grid, (x + 1) / grid, (z + 1) / grid)
            if occupied and first is None:
                first = x
            if not occupied and first is not None:
                strips.append((*add((first / grid, lo[1], z / grid), offset),
                               *add((x / grid, hi[1], (z + 1) / grid), offset)))
                first = None
    return strips


def layout_cells(pose, anchor, props, local_shift=ZERO, mount=0.):
    if props.get("mount", "vertical") != "vertical":
        raise ValueError(f"Unexpected horizontal mounted legacy pose {props}")
    yaw = int(props.get("rotation", 0))
    offset = add(rotate(local_shift, yaw), (0, mount, 0))
    modes = {name: defaultdict(list) for name in ("players", "blocks", "outline")}
    for box in pose["colliders"]:
        names = ("players", "blocks") if box["mode"] == "both" else (box["mode"],)
        for bounds in boxes_for(box, anchor, yaw, offset):
            for name in names:
                clip(bounds, modes[name])
    seats = {tuple(floor(v) for v in add(point(subtract(tuple(v / 16 for v in seat["surfaceCenter"]), anchor), yaw), offset))
             for seat in pose["seats"]}
    cells = set().union(seats, *modes.values(), {ZERO})
    return cells, modes
