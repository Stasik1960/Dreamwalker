"""Fail-closed source-component recipes for C008, C1979, and C002."""
from __future__ import annotations

from copy import deepcopy
from typing import Any, Iterable, Mapping, Sequence


Polygon = Mapping[str, Any]
Box = tuple[float, float, float, float, float, float]

_C008 = {
    "o_c008_1": (6,),
    "o_c008_2": (5,),
    "o_c008_3": (3,),  # Component 1 is an equal rotated placement instance.
    "o_c008_4": (2,),
    "o_c008_5": (4,),
}
_C008_INSTANCES = {"o_c008_1": (6,), "o_c008_2": (5,), "o_c008_3": (3, 1), "o_c008_4": (2,), "o_c008_5": (4,)}
_C1979 = {
    "o_c1979_1": (2, 12), "o_c1979_2": (6, 13), "o_c1979_3": (3, 4, 5),
    "o_c1979_4": (7, 9), "o_c1979_5": (8, 10),
}


def tagged_polys_for_components(components: Sequence[Mapping[str, Any]], component_numbers: Iterable[int]) -> list[dict[str, Any]]:
    """Generate only selected source components while preserving original IDs.

    Selection happens before model expansion.  This prevents C1979's known
    context components 1/11 from being flattened into a later semantic split.
    """
    wanted = set(component_numbers)
    available = {component["number"] for component in components}
    if not wanted or not wanted <= available:
        raise ValueError("requested source component is absent")
    from nightmare_geometry_recipes import tagged_polys

    result: list[dict[str, Any]] = []
    for component in components:
        if component["number"] not in wanted:
            continue
        for polygon in tagged_polys(component["apps"]):
            polygon["source_component"] = component["number"]
            result.append(polygon)
    return result


def _bounds(polygons: Iterable[Polygon]) -> Box:
    vertices = [vertex for polygon in polygons for vertex in polygon["vertices"]]
    if not vertices:
        raise ValueError("recipe output has no polygons")
    return (
        min(vertex[0] for vertex in vertices), min(vertex[1] for vertex in vertices), min(vertex[2] for vertex in vertices),
        max(vertex[0] for vertex in vertices), max(vertex[1] for vertex in vertices), max(vertex[2] for vertex in vertices),
    )


def transform(polygons: Iterable[Polygon], translation: Sequence[float]) -> list[dict[str, Any]]:
    if len(translation) != 3:
        raise ValueError("translation must contain x, y, z")
    result = deepcopy(list(polygons))
    for polygon in result:
        for vertex in polygon["vertices"]:
            vertex[0] += translation[0]
            vertex[1] += translation[1]
            vertex[2] += translation[2]
    return result


def _by_component(polygons: Iterable[Polygon], allowed: set[int]) -> dict[int, list[Polygon]]:
    groups = {component: [] for component in allowed}
    for polygon in polygons:
        component = polygon.get("source_component")
        if component not in groups:
            raise ValueError(f"unknown or context source component {component}")
        if not isinstance(polygon.get("source_element"), int):
            raise ValueError("recipe input requires source_element provenance")
        groups[component].append(polygon)
    if any(not group for group in groups.values()):
        raise ValueError("required source component has no geometry")
    return groups


def _output(name: str, polygons: list[Polygon], translation: tuple[float, float, float], provenance: Mapping[str, Any],
            collision_box: Box, selection_box: Box) -> dict[str, Any]:
    return {"name": name, "polygons": transform(polygons, translation), "source_translation": translation,
            "source_bounds": _bounds(polygons), "canonical_bounds": _bounds(transform(polygons, translation)),
            "collision_box": collision_box, "selection_box": selection_box, "provenance": dict(provenance)}


def _c008(polygons: list[Polygon]) -> list[dict[str, Any]]:
    allowed = set(range(1, 7))
    groups = _by_component(polygons, allowed)
    # Components 3/1 are the same statue_4/y=90 app.  One family deliberately
    # has two source-world placement instances; #2/#4 remain separate because
    # the reviewer requested five semantic families despite equal geometry.
    output = []
    relative = {1: (0, 0, 0), 2: (-1, 0, 1), 3: (-1, 0, 2), 4: (0, 0, 2), 5: (-1, 0, 3), 6: (-2, 1, 4)}
    def normalized(component):
        return sorted((p['texture'],tuple(tuple(round(v[i]-relative[component][i],6) if i<3 else v[i]
                      for i in range(5)) for v in p['vertices'])) for p in groups[component])
    if normalized(1)!=normalized(3):
        raise ValueError('C008 #3/#6 no longer share normalized textured geometry')
    for name, components in _C008.items():
        component = components[0]
        source = groups[component]
        min_y = _bounds(source)[1]
        # High #1's carrier +1 already cancels its model -1, while every low
        # statue is lifted only enough to expose its actual source base.
        dy = 0.0 if component == 6 else -min_y
        root = relative[component]
        translation = (-float(root[0]), dy, -float(root[2]))
        output.append(_output(name, source, translation, {
            "source_components": list(_C008_INSTANCES[name]), "review_number": int(name[-1]),
            "equal_geometry_note": "#2/#4 use statue_3 at yaws differing by 90 degrees; retained as requested semantic families"
            if name in {"o_c008_2", "o_c008_4"} else None,
            "placement_instances": [{"source_component": item, "source_relative": relative[item]} for item in _C008_INSTANCES[name]],
        }, (0.25, 0.0, 0.25, 0.75, 2.0 if component == 6 else 1.5, 0.75),
           (0.0, 0.0, 0.0, 1.0, 2.5 if component == 6 else 2.0, 1.0)))
    return output


def _c1979(polygons: list[Polygon]) -> list[dict[str, Any]]:
    allowed = set(range(2, 11)) | {12, 13}
    groups = _by_component(polygons, allowed)
    return [_output(name, [polygon for component in components for polygon in groups[component]], (-1.0, 0.0, 0.0),
                    {"source_components": list(components), "excluded_context_components": [1, 11],
                     "canonical_old_anchor": [1, 0, 0]},
                    (0.0, 0.0, 0.0, 1.0, 1.0, 1.0), (0.0, 0.0, 0.0, 4.0, 2.0, 3.0))
            for name, components in _C1979.items()]


def _c002(polygons: list[Polygon]) -> list[dict[str, Any]]:
    groups = _by_component(polygons, {1, 2})
    source = groups[1] + groups[2]
    source_bounds = _bounds(source)
    # Both fully evidenced carrier models remain one composition.  Their model
    # elements include the small graves/stones; no unproven source cells added.
    return [_output("o_c002", source, (0.0, -source_bounds[1], 0.0),
                    {"source_components": [1, 2], "required_source_element_counts": {1: 34, 2: 32},
                     "composition": "all source elements retained, including buried small details"},
                    (0.0, 0.0, 0.0, 1.0, 1.0, 1.0), (0.0, 0.0, 0.0, 2.0, 3.0, 2.0))]


def recipe(old_id: str, polygons: Iterable[Polygon]) -> list[dict[str, Any]]:
    """Return one of the explicitly reviewed composition recipes only."""
    source = list(polygons)
    if old_id == "o_c008":
        return _c008(source)
    if old_id == "o_c1979":
        return _c1979(source)
    if old_id == "o_c002":
        return _c002(source)
    raise ValueError(f"no NightmareRunning composition recipe for {old_id}")
