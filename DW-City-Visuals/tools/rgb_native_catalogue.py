"""Audit native cells against reviewed source variants and face materials.

The result is a finite cell-index allowlist. A base-state name or a shared
mesh ID alone never authorises a cell. Every quad must also fit a reviewed
PAINT or KEEP UV rectangle and agree with the renderer's actual paint zones.
The output is evidence for subsequent game checks, not a gameplay PASS.
"""
from collections import Counter, defaultdict
from hashlib import sha256
from math import isfinite
import json
import struct


CATALOGUE = "assets/bloodborne_dw/native_grid/catalog.json"
MESHES = "assets/bloodborne_dw/native_grid/meshes.bin"
REPAIRS = "assets/bloodborne_dw/native_grid/visual-repairs.json"
MAGIC = 1313296689
QUAD_BYTES = 139
VERTEX_BYTES = 32
EPSILON = 0.0002


def _rectangle(value):
    if not isinstance(value, (list, tuple)) or len(value) != 4:
        raise ValueError("Invalid reviewed UV rectangle")
    u0, v0, u1, v1 = (float(number) for number in value)
    if not all(isfinite(number) and 0 <= number <= 16 for number in (u0, v0, u1, v1)):
        raise ValueError("Reviewed UV rectangle outside 0..16")
    return min(u0, u1), min(v0, v1), max(u0, u1), max(v0, v1)


def _covered(bounds, rectangles):
    u0, v0, u1, v1 = bounds
    return any(u0 >= left - EPSILON and v0 >= top - EPSILON
               and u1 <= right + EPSILON and v1 <= bottom + EPSILON
               for left, top, right, bottom in rectangles)


def _policy(profile, review):
    result = {}
    paints = profile.get("paints", {})
    for sprite, faces in review.items():
        paint = tuple(_rectangle(zone) for zone in faces.get("paint", ()))
        keep = tuple(_rectangle(zone) for zone in faces.get("keep", ()))
        current = paints.get(sprite)
        if current is None:
            runtime = None
        elif "zones" not in current:
            runtime = ()
        else:
            if not current["zones"]:
                raise ValueError("Empty runtime RGB zone list for " + sprite)
            runtime = tuple(_rectangle(zone) for zone in current["zones"])
        result[sprite] = paint, keep, runtime
    return result


def _state_key(cell):
    state = cell.get("baseState")
    if not isinstance(state, dict) or not isinstance(state.get("properties"), dict):
        return None
    properties = state["properties"]
    raw_variant = properties.get("variant")
    if not isinstance(raw_variant, str) or not raw_variant.isdecimal():
        return None
    variant = int(raw_variant)
    if "art_bank" in properties:
        # ImportedArtworkBlock.variant uses 16 variants in an art bank.
        bank = properties["art_bank"]
        if not isinstance(bank, str) or not bank.isdecimal():
            return None
        variant += 16 * int(bank)
    if "trim" in properties:
        # ImportedStairShapes.poseVariant uses a ten-pose trim bank.
        if variant >= 10:
            variant = 0
        if properties["trim"] == "true":
            variant += 10
    return state.get("registryId"), variant


def _quad_records(raw, start, count, materials):
    for index in range(count):
        offset = start + index * QUAD_BYTES
        material_index = struct.unpack_from(">I", raw, offset)[0]
        if material_index >= len(materials):
            raise ValueError("Native quad references a missing material")
        material = materials[material_index]
        if set(material) != {"layer", "sprite"} or material["layer"] not in ("solid", "cutout", "translucent"):
            raise ValueError("Unexpected native material descriptor")
        tint_index = struct.unpack_from(">i", raw, offset + 6)[0]
        points, texcoords, colors = [], [], []
        for vertex in range(4):
            x, y, z, u, v, color = struct.unpack_from(">5fI", raw, offset + 11 + vertex * VERTEX_BYTES)
            points.append((x, y, z))
            texcoords.append((u, v))
            colors.append(color)
        yield material, tint_index, points, texcoords, colors


def _review_mesh(raw, start, count, materials, policy):
    if count == 0:
        return False, "empty_mesh", {}
    summary = Counter()
    for material, tint_index, points, texcoords, colors in _quad_records(raw, start, count, materials):
        if not all(isfinite(value) for point in points for value in point):
            return False, "nonfinite_geometry", {}
        if not all(isfinite(value) for point in texcoords for value in point):
            return False, "nonfinite_uv", {}
        if len(set(points)) != 4:
            return False, "degenerate_quad", {}
        if any(value < -EPSILON or value > 1 + EPSILON for point in points for value in point):
            return False, "geometry_outside_cell", {}
        sprite = material["sprite"]
        if sprite not in policy:
            return False, "sprite_not_reviewed", {"sprite": sprite}
        bounds = (min(p[0] for p in texcoords), min(p[1] for p in texcoords),
                  max(p[0] for p in texcoords), max(p[1] for p in texcoords))
        if bounds[0] < -EPSILON or bounds[1] < -EPSILON or bounds[2] > 16 + EPSILON or bounds[3] > 16 + EPSILON:
            return False, "uv_outside_sprite", {"sprite": sprite, "uv": bounds}
        paint, keep, runtime = policy[sprite]
        reviewed_paint = _covered(bounds, paint)
        reviewed_keep = _covered(bounds, keep)
        runtime_paint = runtime is not None and (not runtime or _covered(bounds, runtime)) and tint_index < 0
        if reviewed_paint and reviewed_keep:
            return False, "paint_keep_overlap", {"sprite": sprite, "uv": bounds}
        if runtime_paint:
            if not reviewed_paint:
                return False, "runtime_paints_unreviewed_face", {"sprite": sprite, "uv": bounds}
            if material["layer"] == "translucent":
                return False, "translucent_paint_layer", {"sprite": sprite, "layer": material["layer"]}
            if tint_index != -1 or any(color != 0xffffffff for color in colors):
                return False, "paint_vertex_not_neutral", {"sprite": sprite}
            summary["paint"] += 1
        else:
            if not reviewed_keep or reviewed_paint:
                return False, "runtime_does_not_match_reviewed_face", {"sprite": sprite, "uv": bounds}
            summary["keep"] += 1
    if not summary["paint"]:
        return False, "no_paint_face", {}
    return True, "eligible_for_game_check", dict(summary)


def _strict_cube(raw, start, count, materials, profile):
    """Recheck the four original audited cube meshes if no face table exists."""
    paints = profile.get("paints", {})
    if count != 6 or len(paints) != 1:
        return False
    sprite = next(iter(paints))
    if "zones" in paints[sprite]:
        return False
    planes = set()
    for material, tint_index, points, texcoords, colors in _quad_records(raw, start, count, materials):
        if material != {"layer": "solid", "sprite": sprite} or tint_index != -1:
            return False
        if any(color != 0xffffffff for color in colors):
            return False
        if not all(isfinite(k) and abs(k - round(k)) < 1e-5 and round(k) in (0, 1)
                   for point in points for k in point):
            return False
        corners = {tuple(round(k) for k in point) for point in points}
        if len(corners) != 4 or not all(isfinite(u) and isfinite(v) and 0 < u < 16 and 0 < v < 16
                                        for u, v in texcoords):
            return False
        fixed = [axis for axis in range(3) if len({point[axis] for point in corners}) == 1]
        if len(fixed) != 1:
            return False
        planes.add((fixed[0], next(iter(corners))[fixed[0]]))
    return len(planes) == 6


def extend_native(baseline, profiles, reviewed_faces):
    """Add sorted ``nativeCells`` [cell index, effective mesh] to profiles.

    ``baseline`` is an open ZipFile. ``reviewed_faces`` maps profile key to
    sprite to {"paint": rects, "keep": rects}, with UVs in model units 0..16.
    Source variant conflicts, changed visual groups and unproved native quads
    are rejected. Alternative cells must independently pass the same profile.
    """
    catalogue_bytes = baseline.read(CATALOGUE)
    mesh_bytes = baseline.read(MESHES)
    repair_bytes = baseline.read(REPAIRS)
    catalogue = json.loads(catalogue_bytes)
    repairs = json.loads(repair_bytes)
    catalogue_sha = sha256(catalogue_bytes).hexdigest()
    if repairs.get("catalogSha256") != catalogue_sha:
        raise ValueError("Visual repairs do not match the native catalogue")
    if catalogue.get("schemaVersion") != 1 or repairs.get("schemaVersion") != 1:
        raise ValueError("Unsupported native catalogue schema")
    cells = catalogue["cells"]
    materials = catalogue["materials"]
    meshes = catalogue["meshes"]
    repaired = {int(index) for index in repairs["visuals"]}
    whole = {member["cell"] for item in catalogue["wholeObjects"] for member in item["cells"]}

    sources = {}
    ambiguous = set()
    for key in sorted(profiles):
        profile = profiles[key]
        profile["nativeCells"] = []
        for variant in profile["variants"]:
            state = profile["block"], int(variant)
            if state in sources and sources[state] != key:
                ambiguous.add(state)
            else:
                sources[state] = key

    rows = defaultdict(list)
    candidates = Counter()
    excluded = defaultdict(Counter)
    examples = defaultdict(dict)

    def reject(key, index, reason, details=None):
        excluded[key][reason] += 1
        samples = examples[key].setdefault(reason, [])
        if len(samples) < 4:
            sample = {"cell": index}
            if details:
                sample.update(details)
            samples.append(sample)

    for index, cell in enumerate(cells):
        state = _state_key(cell)
        key = sources.get(state)
        if key is None:
            continue
        candidates[key] += 1
        if state in ambiguous:
            reject(key, index, "ambiguous_source_variant")
        elif index in whole:
            reject(key, index, "whole_object_member")
        elif cell.get("localFunction") is not None:
            reject(key, index, "local_function")
        elif cell.get("fluid") is not None:
            reject(key, index, "fluid")
        elif index in repaired:
            reject(key, index, "unreviewed_visual_repair")
        else:
            rows[(key, cell["mesh"])].append(index)

    magic, count = struct.unpack_from(">II", mesh_bytes, 0)
    if magic != MAGIC or count != len(meshes):
        raise ValueError("Native mesh archive does not match the catalogue")
    wanted = defaultdict(list)
    for key, mesh in rows:
        if not isinstance(mesh, int) or not 0 <= mesh < len(meshes):
            raise ValueError("Native cell references a missing mesh")
        wanted[mesh].append(key)
    policies = {key: _policy(profiles[key], review) for key, review in reviewed_faces.items() if key in profiles}
    accepted = {}
    mesh_summaries = defaultdict(dict)
    offset = 8
    for mesh, descriptor in enumerate(meshes):
        if offset + 4 > len(mesh_bytes):
            raise ValueError("Truncated native mesh header")
        quad_count = struct.unpack_from(">I", mesh_bytes, offset)[0]
        offset += 4
        if quad_count != descriptor["quadCount"] or offset + quad_count * QUAD_BYTES > len(mesh_bytes):
            raise ValueError("Native mesh archive length mismatch")
        for key in wanted.get(mesh, ()):
            profile = profiles[key]
            if key in policies:
                ok, reason, details = _review_mesh(mesh_bytes, offset, quad_count, materials, policies[key])
            elif mesh in profile.get("nativeMeshes", ()) and _strict_cube(mesh_bytes, offset, quad_count, materials, profile):
                ok, reason, details = True, "original_audited_cube", {"paint": 6}
            else:
                ok, reason, details = False, "reviewed_faces_missing", {}
            if ok:
                mesh_summaries[key][str(mesh)] = details
                for index in rows[(key, mesh)]:
                    accepted[index] = key, mesh
            else:
                for index in rows[(key, mesh)]:
                    reject(key, index, reason, details)
        offset += quad_count * QUAD_BYTES
    if offset != len(mesh_bytes):
        raise ValueError("Trailing native mesh archive bytes")

    # Alternates must survive the same source, material and repair audit. Repeat
    # until stable so a chain cannot retain an indirectly rejected alternate.
    changed = True
    while changed:
        changed = False
        for index, (key, mesh) in list(accepted.items()):
            alternate = cells[index].get("alt", -1)
            if alternate is None:
                alternate = -1
            if not isinstance(alternate, int):
                raise ValueError("Invalid native alternate index")
            if alternate >= 0 and alternate != index and (alternate not in accepted or accepted[alternate][0] != key):
                del accepted[index]
                reject(key, index, "alternate_not_equivalent", {"alternate": alternate})
                changed = True

    for index, (key, mesh) in sorted(accepted.items()):
        profiles[key]["nativeCells"].append([index, mesh])
    counts = Counter(key for key, _mesh in accepted.values())
    evidence = {
        "schema": 1,
        "catalogueSha256": catalogue_sha,
        "meshesSha256": sha256(mesh_bytes).hexdigest(),
        "repairsSha256": sha256(repair_bytes).hexdigest(),
        "sourceVariantConflicts": len(ambiguous),
        "candidateCells": sum(candidates.values()),
        "acceptedCells": len(accepted),
        "acceptedMeshes": len({mesh for _key, mesh in accepted.values()}),
        "profiles": {},
    }
    for key in sorted(candidates):
        evidence["profiles"][key] = {
            "candidateCells": candidates[key],
            "acceptedCells": counts[key],
            "excluded": dict(sorted(excluded[key].items())),
            "examples": examples[key],
            "meshes": mesh_summaries[key],
        }
    return evidence
