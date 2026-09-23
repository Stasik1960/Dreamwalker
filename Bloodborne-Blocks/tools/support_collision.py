"""Offline, conservative collision proposals for static decorative meshes.

The input is render mesh data (polygons with five-component vertices), but the
output intentionally is not a rendering-derived box for every polygon.  A box
is proposed only when six axis-aligned rectangular faces prove a closed volume.
"""
from __future__ import annotations

from math import isfinite

AMBIGUOUS_COLLISION = "AMBIGUOUS_COLLISION"
_FUNCTIONAL = {"door", "gate", "fence", "wall"}
_EPSILON = 1.0e-6
_MAX_POLYGONS = 512
_MAX_RECTANGLES = 256
_MAX_FACE_PAIRS = 4096


def _kind(value):
    return "" if value is None else str(value).lower().replace("-", "_")


def _vertices(polygon):
    values = polygon.get("vertices", polygon) if isinstance(polygon, dict) else polygon
    if isinstance(values, (str, bytes, dict)) or not hasattr(values, "__iter__"):
        return None
    try:
        vertices = []
        for vertex in values:
            if isinstance(vertex, (str, bytes, dict)) or not hasattr(vertex, "__len__") or len(vertex) < 3:
                return None
            vertices.append(tuple(float(vertex[axis]) for axis in range(3)))
        return vertices if all(isfinite(value) for vertex in vertices for value in vertex) else None
    except (TypeError, ValueError, IndexError, OverflowError):
        return None


def _rectangles(polygons):
    """Return correctly wound, full axis-aligned quad faces only."""
    out = []
    for polygon in polygons:
        vertices = _vertices(polygon)
        if vertices is None:
            return None
        if len(vertices) != 4 or len(set(vertices)) != 4:
            continue
        low = tuple(min(v[axis] for v in vertices) for axis in range(3))
        high = tuple(max(v[axis] for v in vertices) for axis in range(3))
        flat = [axis for axis in range(3) if high[axis] - low[axis] <= _EPSILON]
        if len(flat) != 1 or any(
            any(not (abs(value - low[axis]) <= _EPSILON or abs(value - high[axis]) <= _EPSILON)
                for axis, value in enumerate(vertex))
            for vertex in vertices
        ):
            continue
        varying = [axis for axis in range(3) if axis != flat[0]]
        if high[varying[0]] - low[varying[0]] <= _EPSILON or high[varying[1]] - low[varying[1]] <= _EPSILON:
            continue
        expected = set()
        for first in (low[varying[0]], high[varying[0]]):
            for second in (low[varying[1]], high[varying[1]]):
                corner = list(low)
                corner[varying[0]], corner[varying[1]] = first, second
                expected.add(tuple(corner))
        # The comprehension above enumerates the two varying coordinates; a
        # face must contain all four corners, in perimeter order, not a triangle
        # whose AABB merely resembles a rectangle.
        if set(vertices) != expected:
            continue
        edges = [tuple(vertices[(index + 1) % 4][axis] - vertices[index][axis] for axis in range(3))
                 for index in range(4)]
        crosses = [_cross(edges[index], edges[(index + 1) % 4]) for index in range(4)]
        if any(_dot(crosses[0], cross) <= _EPSILON for cross in crosses[1:]):
            continue
        out.append((flat[0], (low[flat[0]] + high[flat[0]]) / 2, low, high))
        if len(out) > _MAX_RECTANGLES:
            return None
    return out


def _cross(first, second):
    return (first[1] * second[2] - first[2] * second[1],
            first[2] * second[0] - first[0] * second[2],
            first[0] * second[1] - first[1] * second[0])


def _dot(first, second):
    return sum(left * right for left, right in zip(first, second))


def _covers(rectangles, axis, coordinate, low, high):
    """A single face must cover the candidate face; stitched faces are ambiguous."""
    for normal, plane, rect_low, rect_high in rectangles:
        if normal != axis or abs(plane - coordinate) > _EPSILON:
            continue
        other = [value for value in range(3) if value != axis]
        if all(rect_low[value] <= low[value] + _EPSILON and rect_high[value] >= high[value] - _EPSILON
               for value in other):
            return True
    return False


def _closed_boxes(rectangles):
    boxes = []
    # Seed candidates from opposite faces.  This remains quadratic in the
    # number of render faces, unlike the unsafe Cartesian product of every
    # coordinate in a detailed mesh.
    for axis in range(3):
        faces = [(plane, low, high) for normal, plane, low, high in rectangles if normal == axis]
        if len(faces) * (len(faces) - 1) // 2 > _MAX_FACE_PAIRS:
            return None
        for index, (first_plane, first_low, first_high) in enumerate(faces):
            for second_plane, second_low, second_high in faces[index + 1:]:
                if abs(first_plane - second_plane) <= _EPSILON:
                    continue
                low = [0.0, 0.0, 0.0]
                high = [0.0, 0.0, 0.0]
                low[axis], high[axis] = sorted((first_plane, second_plane))
                for other in range(3):
                    if other != axis:
                        low[other] = max(first_low[other], second_low[other])
                        high[other] = min(first_high[other], second_high[other])
                if any(high[value] - low[value] <= _EPSILON for value in range(3)):
                    continue
                candidate = (*low, *high)
                if all(_covers(rectangles, normal, candidate[normal], low, high) and
                       _covers(rectangles, normal, candidate[normal + 3], low, high) for normal in range(3)):
                    boxes.append(candidate)
    # A larger proven solid supersedes its six faces' smaller duplicate boxes.
    boxes.sort(key=_volume, reverse=True)
    selected = []
    for box in boxes:
        if not any(_contains(existing, box) for existing in selected):
            selected.append(box)
    return selected


def _volume(box):
    return (box[3] - box[0]) * (box[4] - box[1]) * (box[5] - box[2])


def _contains(outer, inner):
    return all(outer[axis] <= inner[axis] + _EPSILON and outer[axis + 3] >= inner[axis + 3] - _EPSILON
               for axis in range(3))


def _gap(first, second):
    return max(0.0, max(max(first[axis] - second[axis + 3], second[axis] - first[axis + 3])
                        for axis in range(3)))


def _occupancy_ratio(boxes):
    """Estimated solid primitive volume / combined-AABB volume."""
    low = [min(box[axis] for box in boxes) for axis in range(3)]
    high = [max(box[axis + 3] for box in boxes) for axis in range(3)]
    bounds_volume = (high[0] - low[0]) * (high[1] - low[1]) * (high[2] - low[2])
    # Chosen masses are disjoint by construction.  This is a solid-volume
    # estimate of the proposed primitives, never a misleading count of mesh
    # surface samples (which would rate a hollow shell as densely occupied).
    return sum(_volume(box) for box in boxes) / bounds_volume


def _bounds(boxes):
    return tuple(min(box[axis] for box in boxes) for axis in range(3)) + tuple(max(box[axis + 3] for box in boxes) for axis in range(3))


def _union_volume(boxes):
    """Exact union volume for a few AABBs; used only before a merge proposal."""
    coordinates = [sorted({value for box in boxes for value in (box[axis], box[axis + 3])}) for axis in range(3)]
    volume = 0.0
    for x0, x1 in zip(coordinates[0], coordinates[0][1:]):
        for y0, y1 in zip(coordinates[1], coordinates[1][1:]):
            for z0, z1 in zip(coordinates[2], coordinates[2][1:]):
                if any(box[0] <= x0 + _EPSILON and box[3] >= x1 - _EPSILON and
                       box[1] <= y0 + _EPSILON and box[4] >= y1 - _EPSILON and
                       box[2] <= z0 + _EPSILON and box[5] >= z1 - _EPSILON for box in boxes):
                    volume += (x1 - x0) * (y1 - y0) * (z1 - z0)
    return volume


def _thin(box):
    extents = [box[axis + 3] - box[axis] for axis in range(3)]
    return min(extents) < .0625 or min(extents) / max(extents) < .05


def _window_box(rectangles):
    if not rectangles:
        return None
    normal, plane, low, high = max(rectangles, key=lambda face: (face[3][(face[0] + 1) % 3] - face[2][(face[0] + 1) % 3]) * (face[3][(face[0] + 2) % 3] - face[2][(face[0] + 2) % 3]))
    low, high = list(low), list(high)
    low[normal], high[normal] = plane - .03125, plane + .03125
    return tuple(low + high)


def _proposal(policy, reason, boxes, polygon_count, vertex_count, discarded, gaps=()):
    ratio = _occupancy_ratio(boxes)
    return {
        "status": "PROPOSED",
        "policy": policy,
        "reason": reason,
        "boxes": [[round(value, 6) for value in box] for box in boxes],
        "evidence": {
            "source_polygon_count": polygon_count,
            "source_vertex_count": vertex_count,
            "source_render_element_count": None,
            "closed_volume_count": len(boxes) + discarded,
            "discarded_thin_or_distant_volume_count": discarded,
            "selected_occupancy_ratio": round(ratio, 6),
            "occupancy_ratio_definition": "estimated solid volume of selected disjoint primitives divided by their combined AABB volume; mesh surface samples are not counted",
            "separation_gaps": [round(value, 6) for value in gaps],
        },
    }


def _ambiguous(reason, polygon_count=0, vertex_count=0):
    return {"status": AMBIGUOUS_COLLISION, "reason": reason, "boxes": None,
            "evidence": {"source_polygon_count": polygon_count, "source_vertex_count": vertex_count}}


def simplify(polygons, semantic_kind=None):
    """Propose at most three offline collision boxes, or return AMBIGUOUS_COLLISION.

    This deliberately has no runtime dependency.  It refuses functional kinds,
    open/sparse artwork, and meshes without a substantial proven closed mass.
    """
    kind = _kind(semantic_kind)
    polygon_list = []
    for polygon in polygons or ():
        if len(polygon_list) >= _MAX_POLYGONS:
            return _ambiguous("input_polygon_budget_exceeded", len(polygon_list))
        polygon_list.append(polygon)
    parsed = [_vertices(polygon) for polygon in polygon_list]
    if any(vertices is None for vertices in parsed):
        return _ambiguous("malformed_vertex_input", len(polygon_list))
    vertex_count = sum(len(vertices) for vertices in parsed)
    if any(token in kind for token in _FUNCTIONAL):
        return _ambiguous("functional_semantic_kind_excluded", len(polygon_list), vertex_count)
    rectangles = _rectangles(polygon_list)
    if rectangles is None:
        return _ambiguous("input_face_budget_exceeded", len(polygon_list), vertex_count)
    boxes = _closed_boxes(rectangles)
    if boxes is None:
        return _ambiguous("input_candidate_budget_exceeded", len(polygon_list), vertex_count)
    if not boxes:
        if "window" in kind:
            plane = _window_box(rectangles)
            if plane is not None:
                return _proposal("SIMPLE_BOX", "explicit_window_plane", [plane], len(polygon_list), vertex_count, 0)
        return _ambiguous("no_proven_closed_volume", len(polygon_list), vertex_count)
    boxes.sort(key=_volume, reverse=True)
    dominant = boxes[0]
    dominant_volume = _volume(dominant)
    if dominant_volume < 1.0e-4:
        return _ambiguous("no_substantial_closed_volume", len(polygon_list), vertex_count)

    vertical_kind = "tree" in kind or any(token in kind for token in ("post", "column", "chimney"))
    if vertical_kind:
        overall_center = [(min(box[axis] for box in boxes) + max(box[axis + 3] for box in boxes)) / 2 for axis in (0, 2)]
        vertical = [box for box in boxes if box[4] - box[1] >= 2 * max(box[3] - box[0], box[5] - box[2])
                    and abs((box[0] + box[3]) / 2 - overall_center[0]) <= max(box[3] - box[0], .125)
                    and abs((box[2] + box[5]) / 2 - overall_center[1]) <= max(box[5] - box[2], .125)]
        if not vertical:
            return _ambiguous("no_central_substantial_vertical_mass", len(polygon_list), vertex_count)
        trunk = max(vertical, key=_volume)
        return _proposal("TRUNK" if "tree" in kind else "POST", "explicit_semantic_vertical_mass", [trunk], len(polygon_list), vertex_count, len(boxes) - 1)

    # Small hardware and isolated ornaments never earn their own collider.
    substantial = [box for box in boxes if _volume(box) >= dominant_volume * .20]
    if any(_thin(box) for box in substantial):
        if "window" not in kind:
            return _ambiguous("thin_or_noncompact_mass_requires_explicit_window", len(polygon_list), vertex_count)
    if len(substantial) > 3:
        return _ambiguous("more_than_three_substantial_masses", len(polygon_list), vertex_count)
    chosen = substantial[:3]
    discarded = len(boxes) - len(chosen)
    if len(chosen) == 1:
        return _proposal("SIMPLE_BOX", "proven_dominant_compact_closed_volume", chosen, len(polygon_list), vertex_count, discarded)
    gaps = [_gap(first, second) for index, first in enumerate(chosen) for second in chosen[index + 1:]]
    if not all(gap > _EPSILON for gap in gaps):
        merged = _bounds(chosen)
        if _union_volume(chosen) >= _volume(merged) - _EPSILON:
            return _proposal("SIMPLE_BOX", "touching_volumes_fill_compact_bounds", [merged], len(polygon_list), vertex_count, discarded)
        return _ambiguous("touching_substantial_masses_leave_empty_volume", len(polygon_list), vertex_count)
    policy = "TWO_BOX" if len(chosen) == 2 else "THREE_BOX"
    return _proposal(policy, "separated_substantial_masses_leave_empty_volume", chosen, len(polygon_list), vertex_count, discarded, gaps)
