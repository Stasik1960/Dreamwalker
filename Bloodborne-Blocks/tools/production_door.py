"""Pure compiler for the final, collective C282 double-door proposal.

It deliberately returns dictionaries only.  Integration into the reviewed
contract generator, resource writer, registry and migration policy is owned by
the caller after the proposal is reviewed.
"""
from __future__ import annotations

from copy import deepcopy
from hashlib import sha256
from math import ceil, floor
from typing import Any, Iterable, Mapping

EPSILON = 1.0e-9
FACINGS = ("north", "east", "south", "west")
SEAM_X = 0.5
LEFT_RANGE = (-1.0, SEAM_X)
RIGHT_RANGE = (SEAM_X, 2.0)
LEFT_PIVOT = (-1.0, 0.4375)
RIGHT_PIVOT = (2.0, 0.4375)


def _rotate(polygons: Iterable[Mapping[str, Any]], turns: int, pivot: tuple[float, float] = (0.5, 0.5)) -> list[dict[str, Any]]:
    matrices = (((1, 0), (0, 1)), ((0, -1), (1, 0)), ((-1, 0), (0, -1)), ((0, 1), (-1, 0)))
    matrix = matrices[turns % 4]
    result = deepcopy(list(polygons))
    for polygon in result:
        for vertex in polygon["vertices"]:
            x, z = vertex[0] - pivot[0], vertex[2] - pivot[1]
            vertex[0] = matrix[0][0] * x + matrix[0][1] * z + pivot[0]
            vertex[2] = matrix[1][0] * x + matrix[1][1] * z + pivot[1]
    return result


def _translate(polygons: Iterable[Mapping[str, Any]], dx: float, dy: float, dz: float) -> list[dict[str, Any]]:
    result = deepcopy(list(polygons))
    for polygon in result:
        for vertex in polygon["vertices"]:
            vertex[0] += dx
            vertex[1] += dy
            vertex[2] += dz
    return result


def _clip_plane(polygon: Mapping[str, Any], value: float, keep_greater: bool) -> dict[str, Any] | None:
    vertices = polygon["vertices"]
    output: list[list[float]] = []
    for current, following in zip(vertices, vertices[1:] + vertices[:1]):
        current_value = current[0] - value
        following_value = following[0] - value
        current_inside = current_value >= -EPSILON if keep_greater else current_value <= EPSILON
        following_inside = following_value >= -EPSILON if keep_greater else following_value <= EPSILON
        if current_inside:
            output.append(list(current))
        if current_inside != following_inside:
            ratio = current_value / (current_value - following_value)
            output.append([left + (right - left) * ratio for left, right in zip(current, following)])
    return {**polygon, "vertices": output} if len(output) >= 3 else None


def _clip_x(polygons: Iterable[Mapping[str, Any]], low: float, high: float) -> list[dict[str, Any]]:
    result = []
    for polygon in polygons:
        clipped = _clip_plane(polygon, low, True)
        if clipped is not None:
            clipped = _clip_plane(clipped, high, False)
        if clipped is not None:
            result.append(clipped)
    return result


def _uv_fix(polygons: Iterable[Mapping[str, Any]]) -> list[dict[str, Any]]:
    """Keep the approved C282 south-face repair, without modifying source input."""
    result = deepcopy(list(polygons))
    bad = [(6.25, 16.0), (6.625, 16.0), (6.625, 15.375), (6.25, 15.375)]
    for polygon in result:
        if [(vertex[3], vertex[4]) for vertex in polygon["vertices"]] == bad:
            for vertex, replacement in zip(polygon["vertices"], ((6, 8), (0, 8), (0, 2), (6, 2))):
                vertex[3:] = replacement
    return result


def _seam_cap(source: list[dict[str, Any]], edge: float, use_maximum: bool) -> dict[str, Any] | None:
    """Reuse an authored outer side face as a UV-preserving exposed cut edge."""
    candidates = [polygon for polygon in source if all(abs(vertex[0] - (max(v[0] for p in source for v in p["vertices"]) if use_maximum else min(v[0] for p in source for v in p["vertices"]))) < EPSILON for vertex in polygon["vertices"])]
    if not candidates:
        return None
    cap = deepcopy(candidates[0]); original = cap["vertices"][0][0]
    for vertex in cap["vertices"]:
        vertex[0] += edge - original
    cap["production_door_cap"] = True
    return cap


def _bounds(polygons: Iterable[Mapping[str, Any]]) -> list[float]:
    vertices = [vertex for polygon in polygons for vertex in polygon["vertices"]]
    if not vertices:
        raise ValueError("door leaf has no polygons")
    return [min(vertex[index] for vertex in vertices) for index in range(3)] + [max(vertex[index] for vertex in vertices) for index in range(3)]


def _union(boxes: Iterable[list[float]]) -> list[float]:
    boxes = list(boxes)
    return [min(box[index] for box in boxes) for index in range(3)] + [max(box[index + 3] for box in boxes) for index in range(3)]


def _cells(boxes: Iterable[list[float]]) -> list[list[int]]:
    result = {(0, 0, 0)}
    for box in boxes:
        for x in range(floor(box[0]), ceil(box[3])):
            for y in range(max(0, floor(box[1])), ceil(box[4])):
                for z in range(floor(box[2]), ceil(box[5])):
                    result.add((x, y, z))
    return [list(cell) for cell in sorted(result)]


def _key(values: Mapping[str, str]) -> str:
    return ",".join(f"{name}={values[name]}" for name in sorted(values))


def _source_patterns(contract: Mapping[str, Any], facing: str) -> list[dict[str, Any]]:
    """Retain only exact raw source identities on closed source-height states."""
    for key, state in contract["states"].items():
        values = dict(part.split("=", 1) for part in key.split(","))
        if values.get("facing") == facing and values.get("open") == "false" and values.get("placement_height") == "source_height":
            return deepcopy(state.get("migration_source_pattern", []))
    return []


def compile_c282(definition: Mapping[str, Any], contract: Mapping[str, Any], source_polygons: Iterable[Mapping[str, Any]]) -> dict[str, Any]:
    """Compile one C282 double-door definition, Contract V2 family and mesh map.

    ``source_polygons`` must be the current tagged C282 source application
    (including its pack application yaw).  The existing +90-degree conversion
    establishes canonical north before the human-reviewed x=.5 split.
    """
    if definition.get("id") != "o_c282" or contract.get("id") != "o_c282":
        raise ValueError("compiler accepts only o_c282")
    source = list(source_polygons)
    if not source or any(len(vertex) < 5 for polygon in source for vertex in polygon.get("vertices", [])):
        raise ValueError("C282 requires textured source polygons")
    canonical = _rotate(_translate(_uv_fix(source), 0, 1, 0), 1)
    left = _clip_x(canonical, *LEFT_RANGE)
    right = _clip_x(canonical, *RIGHT_RANGE)
    # Left cut faces +X; right cut faces -X. Preserve authored outward winding.
    left_cap, right_cap = _seam_cap(canonical, SEAM_X, True), _seam_cap(canonical, SEAM_X, False)
    if left_cap is not None:
        left.append(left_cap)
    if right_cap is not None:
        right.append(right_cap)
    if not left or not right:
        raise ValueError("C282 seam does not produce two leaves")
    properties = {"facing": list(FACINGS), "open": ["false", "true"]}
    output_definition = {key: deepcopy(value) for key, value in definition.items() if key not in {"models", "states", "properties", "default", "placement_properties", "visual_models"}}
    output_definition.update({"id": "o_c282", "creative": True, "behavior": "door", "properties": properties,
                              "default": {"facing": "north", "open": "false"},
                              "placement_properties": {}, "states": {}, "models": {}})
    output_contract = {key: deepcopy(value) for key, value in contract.items() if key not in {"states", "migration_disabled", "support_plane"}}
    output_contract.update({"id": "o_c282", "placement_policy": "FLOOR", "collision_policy": "DOOR", "states": {}})
    meshes: dict[str, dict[str, Any]] = {}
    for facing_index, facing in enumerate(FACINGS):
        for opened in (False, True):
            values = {"facing": facing, "open": str(opened).lower()}
            leaf_meshes = (left, right) if not opened else (_rotate(left, 1, LEFT_PIVOT), _rotate(right, 3, RIGHT_PIVOT))
            leaf_meshes = tuple(_rotate(mesh, facing_index) for mesh in leaf_meshes)
            polygons = leaf_meshes[0] + leaf_meshes[1]
            boxes = [_bounds(mesh) for mesh in leaf_meshes]
            key = _key(values); mesh_id = "production_c282_" + sha256(key.encode("utf-8")).hexdigest()[:12]
            meshes[mesh_id] = {"polygons": polygons, "leaf_pivots": {"left": LEFT_PIVOT, "right": RIGHT_PIVOT}, "collective": True}
            selection = _union(boxes)
            output_definition["states"][key] = [0, 0, 0]
            output_definition["models"][key] = mesh_id
            output_contract["states"][key] = {"rotation": facing_index * 90,
                "render_mesh": {"id": mesh_id, "bounds": _bounds(polygons), "offset": [0.0,0.0,0.0]},
                "selection_footprint": {"boxes": [selection]}, "collision_footprint": {"boxes": boxes},
                "interaction_footprint": {"cells": _cells(boxes)},
                "migration_source_pattern": _source_patterns(contract, facing) if not opened else []}
    evidence = {"review_id": "C282", "semantic": "one collective double-door item/root; leaves are not standalone items",
                "source_model": "minecraft:block/dark_door_1", "source_component_count": 1,
                "screenshot_interpretation": "image8 red outlines label leaf 1 and leaf 2 at the x=0.5 seam",
                "seam_x": SEAM_X, "outer_leaf_pivots": {"left": list(LEFT_PIVOT), "right": list(RIGHT_PIVOT)},
                "frame_policy": "glass-pane source context remains foreign; compiler creates no frame", "source_patterns": deepcopy(contract.get("review_source_patterns", []))}
    return {"definition": output_definition, "contract": output_contract, "meshes": meshes, "evidence": evidence}
