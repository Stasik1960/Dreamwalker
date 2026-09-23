"""Pure, provenance-preserving NightmareRunning functional geometry recipes.

Inputs are canonical source polygons tagged with ``source_component`` and
``source_element`` (normally from ``nightmare_geometry_recipes.tagged_polys``).
The recipes intentionally author only meshes, anchors, pivot coordinates, and
simple gameplay boxes; state yaw, hinge/open behaviour, and migration remain
the caller's responsibility.
"""
from __future__ import annotations

from copy import deepcopy
from typing import Any, Iterable, Mapping, Sequence


Polygon = Mapping[str, Any]
Box = tuple[float, float, float, float, float, float]


def transform(polygons: Iterable[Polygon], translation: Sequence[float]) -> list[dict[str, Any]]:
    """Deep-copy and translate x/y/z only, preserving UV/artwork metadata."""
    if len(translation) != 3:
        raise ValueError("translation must contain x, y, z")
    dx, dy, dz = (float(value) for value in translation)
    result = deepcopy(list(polygons))
    for polygon in result:
        for vertex in polygon.get("vertices", []):
            if len(vertex) < 5:
                raise ValueError("vertex must contain x, y, z, u, v")
            vertex[0] += dx
            vertex[1] += dy
            vertex[2] += dz
    return result


def rotate_y(polygons: Iterable[Polygon], turns: int, pivot: tuple[float, float] = (0.5, 0.5)) -> list[dict[str, Any]]:
    """Rotate by exact quarter turns around a canonical X/Z pivot."""
    matrices = {0: ((1, 0), (0, 1)), 1: ((0, -1), (1, 0)), 2: ((-1, 0), (0, -1)), 3: ((0, 1), (-1, 0))}
    try:
        matrix = matrices[turns % 4]
    except KeyError as error:  # defensive; modulo makes this unreachable
        raise ValueError("turns must be integral quarter turns") from error
    result = deepcopy(list(polygons))
    for polygon in result:
        for vertex in polygon["vertices"]:
            x, z = vertex[0] - pivot[0], vertex[2] - pivot[1]
            vertex[0] = matrix[0][0] * x + matrix[0][1] * z + pivot[0]
            vertex[2] = matrix[1][0] * x + matrix[1][1] * z + pivot[1]
    return result


def _clip_axis(polygon: Polygon, axis: int, value: float, keep_above: bool) -> dict[str, Any] | None:
    vertices = polygon["vertices"]
    output = []
    for current, following in zip(vertices, vertices[1:] + vertices[:1]):
        current_side = (current[axis] - value) * (1 if keep_above else -1)
        next_side = (following[axis] - value) * (1 if keep_above else -1)
        if current_side >= -1e-9:
            output.append(list(current))
        if (current_side > 1e-9 and next_side < -1e-9) or (current_side < -1e-9 and next_side > 1e-9):
            ratio = current_side / (current_side - next_side)
            output.append([left + (right - left) * ratio for left, right in zip(current, following)])
    return {**polygon, "vertices": output} if len(output) >= 3 else None


def clip_x(polygons: Iterable[Polygon], low: float, high: float) -> list[dict[str, Any]]:
    """Clip artwork polygons to an explicit human-reviewed X boundary."""
    result = []
    for polygon in polygons:
        clipped = _clip_axis(polygon, 0, low, True)
        if clipped is not None:
            clipped = _clip_axis(clipped, 0, high, False)
        if clipped is not None:
            result.append(clipped)
    return result


def _bounds(polygons: Iterable[Polygon]) -> Box:
    vertices = [vertex for polygon in polygons for vertex in polygon["vertices"]]
    if not vertices:
        raise ValueError("recipe output has no polygons")
    return (
        min(vertex[0] for vertex in vertices), min(vertex[1] for vertex in vertices), min(vertex[2] for vertex in vertices),
        max(vertex[0] for vertex in vertices), max(vertex[1] for vertex in vertices), max(vertex[2] for vertex in vertices),
    )


def _require_source(polygons: list[Polygon], element_count: int) -> None:
    if not polygons:
        raise ValueError("recipe input has no polygons")
    for polygon in polygons:
        if polygon.get("source_component") != 1:
            raise ValueError("functional recipe expects exactly source component 1")
        element = polygon.get("source_element")
        if not isinstance(element, int) or element not in range(element_count):
            raise ValueError("functional recipe received an unknown source element")


def _output(name: str, polygons: list[Polygon], translation: tuple[float, float, float], collision_box: Box,
            selection_box: Box, provenance: Mapping[str, Any], source_translation: tuple[float, float, float] | None = None,
            **extra: Any) -> dict[str, Any]:
    transformed = transform(polygons, translation)
    return {
        "name": name,
        "polygons": transformed,
        "source_translation": source_translation or translation,
        "source_bounds": _bounds(polygons),
        "canonical_bounds": _bounds(transformed),
        "collision_box": collision_box,
        "selection_box": selection_box,
        "provenance": dict(provenance),
        **extra,
    }


def _correct_c282_uv(polygons: list[Polygon]) -> list[dict[str, Any]]:
    """The pre-existing approved repair for the only documented bad UV face."""
    result = deepcopy(polygons)
    bad_uv = [(6.25, 16.0), (6.625, 16.0), (6.625, 15.375), (6.25, 15.375)]
    for polygon in result:
        if [(vertex[3], vertex[4]) for vertex in polygon["vertices"]] == bad_uv:
            for vertex, replacement in zip(polygon["vertices"], ((6, 8), (0, 8), (0, 2), (6, 2))):
                vertex[3:] = replacement
    return result


def _c282(polygons: list[Polygon]) -> list[dict[str, Any]]:
    _require_source(polygons, 1)
    # Source element 0 is a single 3-wide carrier panel.  Image 8 explicitly
    # draws its semantic leaves at canonical X=.5 after the documented +90°
    # pack-to-north transform.  This is the sole geometry clip in this module.
    north = rotate_y(transform(_correct_c282_uv(polygons), (0, 1, 0)), 1)
    left = clip_x(north, -1.0, 0.5)
    right = clip_x(north, 0.5, 2.0)
    common = {"source_component": 1, "source_element": 0, "semantic_boundary": "image8 vertical seam x=0.5"}
    return [
        _output("o_c282_a", left, (1.0, 0.0, 0.0), (0.0, 0.0, 0.3125, 1.5, 3.0, 0.5625),
                (0.0, 0.0, 0.3125, 1.5, 3.0, 0.5625), {**common, "canonical_x_range": [-1.0, 0.5]},
                source_translation=(1.0, 1.0, 0.0), source_transform={"pre_yaw_translation": (0.0, 1.0, 0.0), "canonical_yaw": 90, "post_yaw_translation": (1.0, 0.0, 0.0)},
                door_pivots={"left": (0.0, 0.4375), "right": (1.5, 0.4375)}),
        _output("o_c282_b", right, (0.0, 0.0, 0.0), (0.5, 0.0, 0.3125, 2.0, 3.0, 0.5625),
                (0.5, 0.0, 0.3125, 2.0, 3.0, 0.5625), {**common, "canonical_x_range": [0.5, 2.0]},
                source_translation=(0.0, 1.0, 0.0), source_transform={"pre_yaw_translation": (0.0, 1.0, 0.0), "canonical_yaw": 90, "post_yaw_translation": (0.0, 0.0, 0.0)},
                door_pivots={"left": (0.5, 0.4375), "right": (2.0, 0.4375)}),
    ]


def _duplicate_opposite_side(polygon: Polygon, depth: float) -> dict[str, Any]:
    copied = deepcopy(dict(polygon))
    copied["vertices"] = [list(vertex) for vertex in reversed(copied["vertices"])]
    for vertex in copied["vertices"]:
        vertex[2] += depth
    return copied


def _c654(polygons: list[Polygon]) -> list[dict[str, Any]]:
    _require_source(polygons, 1)
    if len(polygons) != 2:
        raise ValueError("C654 must contain exactly its two authored source faces")
    # Face 0 and face 1 have distinct source UV art.  Bake each face to both
    # thin planes, reversing winding for the back plane, so A and B each show
    # their own design on both sides rather than two overlapping migration rows.
    north = transform(rotate_y(transform(polygons, (0, 1, 0)), 3), (0, 0, -0.1875))
    face_a, face_b = north
    a = [face_a, _duplicate_opposite_side(face_a, 0.0625)]
    b = [_duplicate_opposite_side(face_b, -0.0625), face_b]
    box = (0.0, 0.0, 0.90625, 1.0, 2.75, 0.96875)
    return [
        _output("o_c654_a", a, (0.0, 0.0, 0.0), box, box,
                {"source_component": 1, "source_element": 0, "source_face": "north", "both_sides": "source north UV"},
                source_translation=(0.0, 1.0, -0.1875), source_transform={"pre_yaw_translation": (0.0, 1.0, 0.0), "canonical_yaw": 270, "post_yaw_translation": (0.0, 0.0, -0.1875)}, canonical_yaw=270, authored_depth_correction=-0.1875),
        _output("o_c654_b", b, (0.0, 0.0, 0.0), box, box,
                {"source_component": 1, "source_element": 0, "source_face": "south", "both_sides": "source south UV"},
                source_translation=(0.0, 1.0, -0.1875), source_transform={"pre_yaw_translation": (0.0, 1.0, 0.0), "canonical_yaw": 270, "post_yaw_translation": (0.0, 0.0, -0.1875)}, canonical_yaw=270, authored_depth_correction=-0.1875),
    ]


def _vertical_recipe(name: str, polygons: list[Polygon], element_count: int, collision_box: Box | None = None) -> list[dict[str, Any]]:
    _require_source(polygons, element_count)
    source_bounds = _bounds(polygons)
    translation = (0.0, -source_bounds[1], 0.0)
    # Decoration keeps its existing no-collision policy; the lantern retains a
    # deliberately simple authored box rather than polygon-derived physics.
    box = collision_box or (0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    return [_output(name, polygons, translation, box, _bounds(transform(polygons, translation)),
                    {"vertical_anchor": "source minimum y raised to canonical floor"}, collision_enabled=collision_box is not None)]


def recipe(old_id: str, polygons: Iterable[Polygon]) -> list[dict[str, Any]]:
    """Return only reviewed C282/C654/C618/wall functional recipe outputs."""
    source = list(polygons)
    if old_id == "o_c282":
        return _c282(source)
    if old_id == "o_c654":
        return _c654(source)
    if old_id == "o_c618":
        return _vertical_recipe(old_id, source, 6, (0.25, 0.0, 0.25, 0.75, 1.9375, 0.75))
    if old_id == "o_wall_deco_1":
        return _vertical_recipe(old_id, source, 1)
    raise ValueError(f"no NightmareRunning functional recipe for {old_id}")
