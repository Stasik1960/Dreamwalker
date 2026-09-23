"""Fail-closed, source-provenance geometry recipes for NightmareRunning QA.

The input is the canonical north mesh, with every polygon annotated by the
source assembly compiler::

    {"source_component": 1, "source_element": 0,
     "texture": "minecraft:block/...", "vertices": [[x, y, z, u, v], ...]}

Coordinates are already block-local.  Recipes only partition source elements
or discard the specifically reviewed source elements; they never infer a
semantic object by clipping a cell or by a coordinate-only heuristic.
"""
from __future__ import annotations

from copy import deepcopy
from math import floor
from typing import Any, Iterable, Mapping, Sequence


Polygon = Mapping[str, Any]
Box = tuple[float, float, float, float, float, float]

# C1491 is one carrier model.  These groups are the three complete graves in
# minecraft:block/addon/tombstone_gathered_1, in source-model element order.
_C1491_GROUPS = ((0, 1, 2, 3, 4, 5, 6), tuple(range(7, 20)), (20, 21))

# The circled side fragments are distinct elements in the original source
# models, not parts produced by cell clipping.  Retaining only these element
# ranges leaves the intended suitcase bodies.
_CASE_RETAINED = {
    "o_c028": {1: frozenset(range(12, 25))},
    "o_c1680": {1: frozenset(range(12, 25)), 2: frozenset(range(8, 16))},
}
_CASE_ALLOWED = {
    "o_c028": {1: frozenset(range(25))},
    "o_c1680": {1: frozenset(range(25)), 2: frozenset(range(16))},
}
_COMPONENT_ELEMENT_COUNTS = {"o_c1962": 4, "o_c471": 2}

# Deliberately simple authoring proposals.  They are not derived from mesh
# polygons and therefore cannot accidentally become expensive/fragile complex
# collision.  Each tuple is min-x, min-y, min-z, max-x, max-y, max-z.
_BOXES: dict[str, tuple[Box, Box]] = {
    "o_c1491_a": ((0.22, 0.0, 0.22, 0.78, 1.80, 0.78), (0.05, 0.0, 0.05, 0.95, 3.00, 0.95)),
    "o_c1491_b": ((0.22, 0.0, 0.22, 0.78, 1.80, 0.78), (0.05, 0.0, 0.05, 0.95, 3.00, 0.95)),
    "o_c1491_c": ((0.22, 0.0, 0.22, 0.78, 1.80, 0.78), (0.05, 0.0, 0.05, 0.95, 3.00, 0.95)),
    "o_c1962_a": ((0.20, 0.0, 0.20, 0.80, 0.88, 0.80), (0.14, 0.0, 0.14, 0.86, 0.90, 0.86)),
    "o_c1962_b": ((0.20, 0.0, 0.20, 0.80, 0.88, 0.80), (0.14, 0.0, 0.14, 0.86, 0.90, 0.86)),
    "o_c471_a": ((0.28, 0.0, 0.28, 0.72, 3.00, 0.72), (0.10, 0.0, 0.10, 0.90, 3.00, 0.90)),
    "o_c471_b": ((0.28, 0.0, 0.28, 0.72, 3.00, 0.72), (0.10, 0.0, 0.10, 0.90, 3.00, 0.90)),
    "o_c028": ((0.10, 0.0, 0.10, 0.90, 1.30, 0.90), (0.02, 0.0, 0.02, 0.98, 1.40, 0.98)),
    "o_c1680": ((0.10, 0.0, 0.10, 0.90, 1.30, 0.90), (0.02, 0.0, 0.02, 0.98, 1.40, 0.98)),
}


def transform(polygons: Iterable[Polygon], translation: Sequence[float]) -> list[dict[str, Any]]:
    """Return a deep-copied mesh translated in x/y/z without altering UVs."""
    if len(translation) != 3:
        raise ValueError("translation must contain exactly x, y, z")
    dx, dy, dz = (float(value) for value in translation)
    result = deepcopy(list(polygons))
    for polygon in result:
        if "vertices" not in polygon:
            raise ValueError("polygon has no vertices")
        for vertex in polygon["vertices"]:
            if len(vertex) < 5:
                raise ValueError("vertex must contain x, y, z, u, v")
            vertex[0] += dx
            vertex[1] += dy
            vertex[2] += dz
    return result


def tagged_polys(apps: Sequence[Mapping[str, Any]]) -> list[dict[str, Any]]:
    """Build canonical source polygons while retaining app/element provenance.

    This intentionally follows :func:`source_assembly_visuals.source_polys`
    face-for-face.  The extra tags let a later recipe prove that it split a
    source model element, rather than guessing from a flattened mesh.
    """
    # Keep these heavy, source-pack-only imports out of the pure recipe path.
    import itertools

    import numpy as np

    from source_assembly_visuals import source_model, source_texture
    from catalog_geometry import rotation
    from modular_mesh import FACE_INDEX, face_uv, texname, tile_uv

    polygons: list[dict[str, Any]] = []
    for component, supplied_app in enumerate(apps, 1):
        app = dict(supplied_app)
        model = source_model(app["model"])
        for element_number, element in enumerate(model.get("elements", [])):
            points = np.array(list(itertools.product(*zip(element["from"], element["to"]))), float)
            if "rotation" in element:
                item = element["rotation"]
                axis = "xyz".index(item["axis"])
                matrix = rotation(axis, item["angle"])
                if item.get("rescale"):
                    scale = np.ones(3)
                    scale[[index for index in range(3) if index != axis]] = 1 / np.cos(np.radians(item["angle"]))
                    matrix = matrix @ np.diag(scale)
                origin = np.asarray(item["origin"], float)
                points = (points - origin) @ matrix.T + origin
            matrix = rotation(1, -app.get("y", 0)) @ rotation(0, -app.get("x", 0))
            points = ((points - 8) @ matrix.T + 8) / 16 + np.asarray(app.get("offset", [0, 0, 0]))
            for side, face in element.get("faces", {}).items():
                vertices = np.column_stack((points[FACE_INDEX[side]], face_uv(face, side, element, app)))
                if np.linalg.norm(np.cross(vertices[1, :3] - vertices[0, :3], vertices[2, :3] - vertices[0, :3])) < 1e-10:
                    continue
                texture = texname(model, face["texture"])
                source_texture(texture)  # Preserve source_polys' missing-artwork failure.
                for polygon in tile_uv({"texture": texture, "vertices": vertices.tolist()}):
                    polygon["source_component"] = component
                    polygon["source_element"] = element_number
                    polygon["source_app"] = deepcopy(app)
                    polygons.append(polygon)
    return polygons


def _provenance(polygon: Polygon) -> tuple[int, int]:
    try:
        component = polygon["source_component"]
        element = polygon["source_element"]
    except KeyError as error:
        raise ValueError("recipe input requires source_component and source_element") from error
    if not isinstance(component, int) or not isinstance(element, int):
        raise ValueError("source_component and source_element must be integers")
    return component, element


def _bounds(polygons: Iterable[Polygon]) -> Box:
    vertices = [vertex for polygon in polygons for vertex in polygon["vertices"]]
    if not vertices:
        raise ValueError("semantic output has no polygons")
    return (
        min(vertex[0] for vertex in vertices), min(vertex[1] for vertex in vertices), min(vertex[2] for vertex in vertices),
        max(vertex[0] for vertex in vertices), max(vertex[1] for vertex in vertices), max(vertex[2] for vertex in vertices),
    )


def _floor_with_integer_root(polygons: Iterable[Polygon]) -> tuple[list[dict[str, Any]], tuple[float, float, float], Box, tuple[int, int, int]]:
    source = list(polygons)
    source_bounds = _bounds(source)
    center_x = (source_bounds[0] + source_bounds[3]) / 2.0
    center_z = (source_bounds[2] + source_bounds[5]) / 2.0
    # Do not recenter to an arbitrary fractional X/Z value: migration can put
    # the logical root at this integer source cell and preserve the source
    # world's visual layout.  Y alone is corrected to the floor.
    root = (floor(center_x), 0, floor(center_z))
    translation = (-float(root[0]), -source_bounds[1], -float(root[2]))
    return transform(source, translation), translation, source_bounds, root


def _output(name: str, polygons: Iterable[Polygon], provenance: Mapping[str, Any]) -> dict[str, Any]:
    translated, translation, source_bounds, root = _floor_with_integer_root(polygons)
    collision_box, selection_box = _BOXES[name]
    return {
        "name": name,
        "polygons": translated,
        "source_translation": translation,
        "recommended_source_root_offset": root,
        "source_bounds": source_bounds,
        "canonical_bounds": _bounds(translated),
        "collision_box": collision_box,
        "selection_box": selection_box,
        "provenance": dict(provenance),
    }


def _split_components(old_id: str, polygons: list[Polygon], names: Sequence[str]) -> list[dict[str, Any]]:
    groups: dict[int, list[Polygon]] = {number: [] for number in range(1, len(names) + 1)}
    for polygon in polygons:
        component, element = _provenance(polygon)
        if component not in groups:
            raise ValueError(f"{old_id} has unknown source component {component}")
        if element not in range(_COMPONENT_ELEMENT_COUNTS[old_id]):
            raise ValueError(f"{old_id} has unknown source element {element}")
        groups[component].append(polygon)
    return [_output(name, groups[number], {"source_components": [number]}) for number, name in enumerate(names, 1)]


def recipe(old_id: str, polygons: Iterable[Polygon]) -> list[dict[str, Any]]:
    """Split or clean one reviewed canonical mesh; reject all other inputs.

    `source_translation` documents the exact affine translation from the
    supplied source mesh to the new canonical, floor-aligned mesh. X/Z are
    always integer-cell translations; `recommended_source_root_offset` is the
    corresponding source-world root used by migration. Textures,
    UVs, weighted choice provenance, and polygon ordering are otherwise
    preserved verbatim.
    """
    source = list(polygons)
    if not source:
        raise ValueError("recipe input has no polygons")
    if old_id == "o_c1491":
        groups = [[] for _ in _C1491_GROUPS]
        element_to_group = {element: index for index, elements in enumerate(_C1491_GROUPS) for element in elements}
        for polygon in source:
            component, element = _provenance(polygon)
            if component != 1 or element not in element_to_group:
                raise ValueError(f"o_c1491 has unknown source provenance ({component}, {element})")
            groups[element_to_group[element]].append(polygon)
        return [_output(f"o_c1491_{suffix}", group, {"source_component": 1, "source_elements": list(elements)})
                for suffix, group, elements in zip("abc", groups, _C1491_GROUPS)]
    if old_id == "o_c1962":
        return _split_components(old_id, source, ("o_c1962_a", "o_c1962_b"))
    if old_id == "o_c471":
        return _split_components(old_id, source, ("o_c471_a", "o_c471_b"))
    if old_id in _CASE_RETAINED:
        retained = _CASE_RETAINED[old_id]
        allowed = _CASE_ALLOWED[old_id]
        kept: list[Polygon] = []
        removed: set[tuple[int, int]] = set()
        for polygon in source:
            component, element = _provenance(polygon)
            if component not in retained:
                raise ValueError(f"{old_id} has unknown source component {component}")
            if element not in allowed[component]:
                raise ValueError(f"{old_id} has unknown source element {element}")
            if element in retained[component]:
                kept.append(polygon)
            else:
                removed.add((component, element))
        return [_output(old_id, kept, {
            "retained_source_elements": {component: sorted(elements) for component, elements in retained.items()},
            "removed_source_elements": sorted(removed),
        })]
    raise ValueError(f"no NightmareRunning geometry recipe for {old_id}")
