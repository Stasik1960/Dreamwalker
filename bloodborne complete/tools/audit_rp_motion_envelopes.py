"""Read-only numeric reproduction of RP source selection envelopes.

No game is launched and no placement result is fabricated. The candidate sphere
propagation is a mathematical proposal, not production code or rendered art.
"""
from __future__ import annotations

import argparse
import hashlib
import itertools
import json
import math
import random
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ZERO = (0.0, 0.0, 0.0)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def vector(value: dict, name: str) -> tuple:
    answer = tuple(float(x) for x in value.get(name, ZERO))
    if len(answer) != 3 or not all(map(math.isfinite, answer)):
        raise ValueError("Non-finite/non-3D source vector")
    return answer


def add(a, b):
    return tuple(x + y for x, y in zip(a, b))


def mul(value, factor):
    return tuple(x * factor for x in value)


def mirror(value):
    return (-value[0], value[1], value[2])


def around(value, pivot, degrees):
    x, y, z = (value[i] - pivot[i] for i in range(3))
    angle = math.radians(-degrees[0]); c, s = math.cos(angle), math.sin(angle)
    y, z = y * c - z * s, y * s + z * c
    angle = math.radians(-degrees[1]); c, s = math.cos(angle), math.sin(angle)
    x, z = x * c + z * s, -x * s + z * c
    angle = math.radians(degrees[2]); c, s = math.cos(angle), math.sin(angle)
    x, y = x * c - y * s, x * s + y * c
    return add((x, y, z), pivot)


def corners(box):
    return list(itertools.product(*zip(box[:3], box[3:])))


def bounds(points):
    return tuple(min(p[i] for p in points) for i in range(3)) + tuple(max(p[i] for p in points) for i in range(3))


def union(boxes):
    return bounds([point for box in boxes for point in corners(box)])


def contains(box, point, epsilon=1e-8):
    return all(box[i] - epsilon <= point[i] <= box[i + 3] + epsilon for i in range(3))


def maximum_magnitude(value):
    if value is None:
        return 0.0
    if isinstance(value, list):
        return max(map(maximum_magnitude, value), default=0.0)
    if isinstance(value, dict):
        return max((maximum_magnitude(v) for k, v in value.items() if k != "lerp_mode"), default=0.0)
    if isinstance(value, (int, float)):
        return abs(float(value))
    expression = re.sub(r"\s+", "", value)
    try:
        return abs(float(expression))
    except ValueError:
        pass
    sine = re.search(r"(?i)math\.sin\(query\.anim_time\*[+-]?[0-9.]+(?:[+-][0-9.]+)?\)", expression)
    if not sine:
        raise ValueError("Unsupported source expression: " + expression)
    prefix, suffix = expression[:sine.start()], expression[sine.end():]
    constant, amplitude = 0.0, 1.0
    if prefix:
        if prefix[-1] in "+-":
            amplitude = 1.0 if prefix[-1] == "+" else -1.0
            constant = float(prefix[:-1]) if len(prefix) > 1 else 0.0
        elif prefix[-1] == "*":
            amplitude = float(prefix[:-1])
        else:
            raise ValueError("Unsupported source expression prefix")
    if suffix:
        if not suffix.startswith("*"):
            raise ValueError("Unsupported source expression suffix")
        amplitude *= float(suffix[1:])
    return abs(constant) + abs(amplitude)


def maximum_vector(value):
    if isinstance(value, list):
        if len(value) != 3:
            raise ValueError("Motion vector is not 3D")
        return tuple(map(maximum_magnitude, value))
    if isinstance(value, dict):
        candidates = [maximum_vector(v) for k, v in value.items() if k != "lerp_mode"]
        return tuple(max(v[i] for v in candidates) for i in range(3))
    return (maximum_magnitude(value),) * 3


def chain(bone, lookup):
    visited = set()
    while bone is not None:
        if bone["name"] in visited:
            raise ValueError("Cyclic source bone hierarchy")
        visited.add(bone["name"])
        yield bone
        bone = lookup.get(bone.get("parent"))


def static_cubes(document):
    bones = document["minecraft:geometry"][0]["bones"]
    lookup = {bone["name"]: bone for bone in bones}
    answer = []
    for bone in bones:
        for cube in bone.get("cubes", []):
            inflate = float(cube.get("inflate", 0))
            origin = tuple(x - inflate for x in vector(cube, "origin"))
            size = tuple(x + 2 * inflate for x in vector(cube, "size"))
            points = []
            for bits in itertools.product((0, 1), repeat=3):
                value = mirror(tuple(origin[i] + size[i] * bits[i] for i in range(3)))
                value = around(value, mirror(vector(cube, "pivot")), vector(cube, "rotation"))
                for parent in chain(bone, lookup):
                    value = around(value, mirror(vector(parent, "pivot")), vector(parent, "rotation"))
                points.append(mul(value, 1 / 16))
            answer.append((bone["name"], bounds(points)))
    if not answer:
        raise ValueError("Empty source model")
    return lookup, answer


def motions(spec, animation):
    answer = {}
    for declared in spec["clips"].values():
        for name, channels in animation.get("animations", {}).get(declared["name"], {}).get("bones", {}).items():
            rotation, translation, scale = answer.get(name, (False, ZERO, 1.0))
            rotation |= maximum_magnitude(channels.get("rotation")) > 1e-8
            if "position" in channels:
                upcoming = mul(maximum_vector(channels["position"]), 1 / 16)
                translation = tuple(max(translation[i], upcoming[i]) for i in range(3))
            if "scale" in channels:
                scale = max(scale, maximum_magnitude(channels["scale"]))
            if not all(map(math.isfinite, (*translation, scale))):
                raise ValueError("Non-finite source motion")
            answer[name] = rotation, translation, scale
    return answer


def pivot_world(bone, lookup):
    pivot = mirror(vector(bone, "pivot"))
    for parent in chain(lookup.get(bone.get("parent")), lookup):
        pivot = around(pivot, mirror(vector(parent, "pivot")), vector(parent, "rotation"))
    return mul(pivot, 1 / 16)


def translation_vectors(bone, translation, lookup):
    answer = []
    for signs in itertools.product((-1, 1), repeat=3):
        value = tuple(signs[i] * translation[i] for i in range(3))
        for parent in chain(lookup.get(bone.get("parent")), lookup):
            value = around(value, ZERO, vector(parent, "rotation"))
        answer.append(value)
    return answer


def cube_envelope(name, raw, lookup, motion, proposal):
    box, sphere, changed, steps = raw, None, False, []
    for bone in chain(lookup[name], lookup):
        present = motion.get(bone["name"])
        if present is None:
            continue
        rotation, translation, scale = present
        if not (rotation or sum(x * x for x in translation) > 1e-16 or scale > 1 + 1e-8):
            continue
        pivot = pivot_world(bone, lookup)
        translations = translation_vectors(bone, translation, lookup)
        if rotation or scale > 1 + 1e-8:
            # The proposed propagation preserves the ball, avoiding conversion of
            # its AABB corners into a larger sphere at every animated ancestor.
            radius = (math.dist(sphere[0], pivot) + sphere[1]) if proposal and sphere else max(math.dist(p, pivot) for p in corners(box))
            radius *= scale
            sphere = (pivot, radius) if proposal else None
            box = tuple(v - radius for v in pivot) + tuple(v + radius for v in pivot)
        if any(translation):
            if proposal and sphere:
                sphere = (sphere[0], sphere[1] + max(math.dist(ZERO, v) for v in translations))
                box = tuple(v - sphere[1] for v in sphere[0]) + tuple(v + sphere[1] for v in sphere[0])
            else:
                transformed = bounds(translations)
                extents = tuple(max(abs(transformed[i]), abs(transformed[i + 3])) for i in range(3))
                box = tuple(box[i] - extents[i] for i in range(3)) + tuple(box[i + 3] + extents[i] for i in range(3))
        changed = True
        steps.append((pivot, rotation, scale, translations))
    return box if changed else None, steps


def sampled_containment(raw, proposed, steps, rng):
    """Independent bounded-transform samples, not evaluation of source animation."""
    checked = 0
    for _ in range(3):
        for original in corners(raw):
            point = original
            for pivot, rotation, scale, translations in steps:
                if rotation:
                    point = around(point, pivot, tuple(rng.uniform(-180, 180) for _ in range(3)))
                if scale > 1:
                    point = add(pivot, mul(tuple(point[i] - pivot[i] for i in range(3)), rng.uniform(0, scale)))
                # Each corner includes already-applied static ancestor rotations;
                # their convex combinations cover all bounded translations.
                point = add(point, translations[rng.randrange(len(translations))])
            if not contains(proposed, point):
                raise AssertionError("Candidate envelope does not contain a bounded transform sample")
            checked += 1
    return checked


def audit_asset(asset, spec, resources, rng):
    model_path, animation_path = resources / spec["model"], resources / spec["animation"]
    lookup, cubes = static_cubes(json.loads(model_path.read_text(encoding="utf-8")))
    motion = motions(spec, json.loads(animation_path.read_text(encoding="utf-8")))
    current, proposed, worst, samples = [], [], [], 0
    for name, raw in cubes:
        old, _ = cube_envelope(name, raw, lookup, motion, False) if asset != "ladder" else (None, [])
        new, steps = cube_envelope(name, raw, lookup, motion, True) if asset != "ladder" else (None, [])
        if old is not None:
            current.append(old); proposed.append(new)
            if not all(contains(new, p) for p in corners(raw)):
                raise AssertionError("Candidate excludes static source cube")
            samples += sampled_containment(raw, new, steps, rng)
            worst.append({"bone": name, "current": old, "proposed": new})
    static = union([box for _, box in cubes])
    current_bounds, proposed_bounds = union([static] + current), union([static] + proposed)
    dimensions = lambda b: tuple(b[i + 3] - b[i] for i in range(3))
    return {"asset": asset, "model": spec["model"], "modelSha256": digest(model_path), "animation": spec["animation"], "animationSha256": digest(animation_path), "sourceDimensions": [spec["width"], spec["height"]], "sourceScale": spec["scale"], "cubeCount": len(cubes), "animatedEnvelopeCubeCount": len(current), "animatedBoneCount": len(motion), "staticLocal": static, "currentMotionUnionLocal": current_bounds, "proposedMotionUnionLocal": proposed_bounds, "currentSpan": dimensions(current_bounds), "proposedSpan": dimensions(proposed_bounds), "staticSourceContainment": True, "boundedTransformSampleCount": samples, "boundedTransformSamplesContained": True, "largestCurrentParts": sorted(worst, key=lambda row: max(dimensions(row["current"])), reverse=True)[:3]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact-sha", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{64}", args.artifact_sha):
        raise ValueError("Explicit artifact SHA256 is required")
    if args.output.exists():
        raise FileExistsError("Refuse to overwrite historical evidence")
    resources = ROOT / "src/rp/resources/assets/bloodborne_rp"
    catalogue_path = resources / "catalog.json"
    catalogue = json.loads(catalogue_path.read_text(encoding="utf-8"))
    registry_path = ROOT / "src/rp/java/dev/dreamwalker/bloodbornerp/object/ObjectRegistry.java"
    mobs_path = ROOT / "src/rp/java/dev/dreamwalker/bloodbornerp/mob/MobRegistry.java"
    aliases_path = ROOT / "src/rp/java/dev/dreamwalker/bloodbornerp/object/RpObjectCompatibility.java"
    registry = registry_path.read_text(encoding="utf-8")
    excluded = set(re.findall(r'"([a-z0-9_]+)"', re.search(r"NON_OBJECTS=Set.of\((.*?)\);", registry, re.S).group(1)))
    mobs = set(re.findall(r'"([a-z0-9_]+)"', re.search(r"IDS = List.of\((.*?)\);", mobs_path.read_text(encoding="utf-8"), re.S).group(1)))
    pairs = re.findall(r'"([a-z0-9_]+)"', re.search(r"ALIASES = Map.of\((.*?)\);", aliases_path.read_text(encoding="utf-8"), re.S).group(1))
    aliases = set(pairs[::2])
    assets = [asset for asset in catalogue if asset not in excluded | mobs | aliases]
    if len(assets) != 69:
        raise ValueError("Frozen canonical population differs from the69 requested ordinary objects")
    rng = random.Random(101306)
    rows = [audit_asset(asset, catalogue[asset], resources, rng) for asset in assets]
    cage = next(row for row in rows if row["asset"] == "cage_obj_1")
    origin, yaw = (-64, 100, 64), -180
    def world(box):
        angle = math.radians(yaw - 90); c, s = math.cos(angle), math.sin(angle)
        return bounds([add((p[0] * c + p[2] * s, p[1], -p[0] * s + p[2] * c), origin) for p in corners(box)])
    old, new = world(cage["currentMotionUnionLocal"]), world(cage["proposedMotionUnionLocal"])
    physical = (-65.4, 100, 62.6, -62.6, 106.5, 65.4)
    actor = (-63.8, 100, 60.2, -63.2, 101.8, 60.8)
    geometry_path = ROOT / "src/rp/java/dev/dreamwalker/bloodbornerp/object/RpObjectGeometry.java"
    entity_path = ROOT / "src/rp/java/dev/dreamwalker/bloodbornerp/object/RpObjectEntity.java"
    report = {"schema": "dw-rp-source-motion-envelope-audit-v1", "status": "PASS_MATHEMATICAL_REPRODUCTION_AND_CONSERVATIVE_PROPOSAL_RUNTIME_REFUSAL_PENDING", "productionJarSha256": args.artifact_sha, "canonicalOrdinaryAssetCount": len(rows), "catalogueSha256": digest(catalogue_path), "sourceFiles": {str(path.relative_to(ROOT)).replace("\\", "/"): digest(path) for path in (registry_path, geometry_path, entity_path, mobs_path, aliases_path)}, "allSourceStaticPartsRetained": True, "boundedTransformSamplesContained": sum(row["boundedTransformSampleCount"] for row in rows), "proof": {"initialSphere": "radius=max distance of current original/static-or-translated cube corners to transformed authored pivot", "laterSphere": "radius=maxScale*(distance(previous center,next pivot)+previous radius); center=next pivot", "translationAfterSphere": "add max Euclidean norm of bounded translation corners to radius", "conservativeness": "Triangle inequality; rotation preserves distance; component scales have operator norm<=maximum absolute source scale. Axis box is formed only for final reporting, never re-fed as sphere corners.", "staticBeforeSphere": "Keep current exact conservative translation-expanded axis box until first rotation/scale envelope.", "testScope": "3 seeded arbitrary bounded-transform samples per source cube corner; mathematical proposal only, no native game/Gecko animation or final render verdict"}, "failedClientProspectiveCage": {"source": "MIN8 actor/crosshair; ItemPlacementContext UP places root[-64,100,64]; ordinary yaw snapped to−180", "origin": origin, "placedYaw": yaw, "actorAssumedNormalStandingBox": actor, "legacyPhysicalBox": physical, "actorOverlapsLegacyPhysical": all(min(physical[i+3], actor[i+3])-max(physical[i],actor[i])>1e-6 for i in range(3)), "currentQueryBounds": union([old, physical]), "proposedQueryBounds": union([new, physical]), "staticVisualBounds": world(cage["staticLocal"]), "overworldBuildLimitsAssumed": [-64, 320], "firstExpectedGuardReason": "visual_bounds_outside_build_height", "currentQueryMinY": old[1], "currentRequiredChunkRectangle": [math.floor(old[0]) >> 4, math.floor(old[3]-1e-7) >> 4, math.floor(old[2]) >> 4, math.floor(old[5]-1e-7) >> 4], "currentRequiredChunkCount": ((math.floor(old[3]-1e-7)>>4)-(math.floor(old[0])>>4)+1)*((math.floor(old[5]-1e-7)>>4)-(math.floor(old[2])>>4)+1), "actualNativeRefusal": "NOT_YET_CAPTURED; pending ordinary QA13 diagnostics trace"}, "assets": rows, "limitations": ["Explicit production SHA supplied by parent; this source audit does not rehash or launch the production JAR.", "All declared clip bounds reproduce existing selection-envelope policy, including clips not currently played; policy narrowing is not proposed here.", "No physical collider, model bytes, UV, source placement, scale or animation keyframes changed.", "Sampled containment is supplementary to the analytical bound; no causality or native gameplay PASS is inferred."]}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": report["status"], "output": str(args.output.resolve()), "sha256": digest(args.output), "assets": len(rows), "samplePoints": report["boundedTransformSamplesContained"], "cageCurrentQuery": report["failedClientProspectiveCage"]["currentQueryBounds"], "cageProposedQuery": report["failedClientProspectiveCage"]["proposedQueryBounds"]}))


if __name__ == "__main__":
    main()
