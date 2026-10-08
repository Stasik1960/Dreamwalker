"""Import the two reviewed window families, preserving source geometry and UV.

Only source identifiers are rebased. Wood-window movement is a clearly marked
proposal: the original three cuboids remain the closed/source pose; only the
two authored side pivots receive an additional rotation in the second pose.
This does not create a traversable opening through the opaque central panel.
No final numeric IDs, source-map writes, or mass generation are performed.
"""
from __future__ import annotations
import argparse
import copy
from collections import Counter
import hashlib
import json
import math
import struct
from pathlib import Path
import zipfile
import zlib
from analyze_resources import paeth, png_alpha, rotate_point

ROOT = Path(__file__).resolve().parents[1]
NS = "bloodborne_dw"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def alpha_roi_rgba8(data, bounds):
    """Read source alpha samples without changing or resaving the PNG."""
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    chunks = {}
    at = 8
    while at + 12 <= len(data):
        size = int.from_bytes(data[at:at + 4], "big")
        chunks.setdefault(data[at + 4:at + 8], []).append(data[at + 8:at + 8 + size])
        at += 12 + size
    width, height, depth, color, compression, filtering, interlace = struct.unpack(">IIBBBBB", chunks[b"IHDR"][0])
    assert (depth, color, compression, filtering, interlace) == (8, 6, 0, 0, 0)
    raw = zlib.decompress(b"".join(chunks[b"IDAT"]))
    stride = width * 4
    assert len(raw) == (stride + 1) * height
    left, top, right, bottom = bounds
    previous = bytearray(stride)
    histogram = Counter()
    for y in range(height):
        offset = y * (stride + 1)
        mode, row = raw[offset], bytearray(raw[offset + 1:offset + 1 + stride])
        for x in range(stride):
            a = row[x - 4] if x >= 4 else 0
            b = previous[x]
            c = previous[x - 4] if x >= 4 else 0
            adjustment = 0 if mode == 0 else a if mode == 1 else b if mode == 2 else (a + b) // 2 if mode == 3 else paeth(a, b, c)
            assert 0 <= mode <= 4
            row[x] = (row[x] + adjustment) & 255
        if top <= y < bottom:
            histogram.update(row[x * 4 + 3] for x in range(left, right))
        previous = row
    return {str(key): value for key, value in sorted(histogram.items())}


def dump(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf8")


def clip_polygon(points, axis, limit, lower):
    out = []
    for start, end in zip(points, points[1:] + points[:1]):
        a = start[axis] >= limit if lower else start[axis] <= limit
        b = end[axis] >= limit if lower else end[axis] <= limit
        if a:
            out.append(start)
        if a != b:
            amount = (limit - start[axis]) / (end[axis] - start[axis])
            out.append([start[i] + amount * (end[i] - start[i]) for i in range(2)])
    return out


def collision_boxes(element, extra_yaw=0):
    """Conservative one-model-unit strips of the rotated cuboid, not its AABB.

    Strip approximation can expand the rotated boundary by at most one model
    unit along X. Zero-yaw panels retain their exact thin rectangular prism.
    All returned coordinates already include source north blockstate yaw=180.
    The generic adapter clips these small boxes into sparse owning cells.
    """
    low, high = element["from"], element["to"]
    polygon = [[low[0], low[2]], [high[0], low[2]], [high[0], high[2]], [low[0], high[2]]]
    rotation = element.get("rotation", {})
    assert rotation.get("axis", "y") == "y"
    yaw = rotation.get("angle", 0) + extra_yaw
    pivot = rotation.get("origin", [8, 8, 8])
    def transform(point):
        authored = rotate_point([point[0], 0, point[1]], "y", yaw, pivot)
        source_north = rotate_point(authored, "y", -180, [8, 8, 8])
        return [source_north[0], source_north[2]]
    polygon = [transform(point) for point in polygon]
    y_low, y_high = low[1], high[1]
    if abs(math.sin(math.radians(yaw))) < 1e-8:
        return [{"from": [min(p[0] for p in polygon), y_low, min(p[1] for p in polygon)],
                 "to": [max(p[0] for p in polygon), y_high, max(p[1] for p in polygon)]}]
    out = []
    for x in range(math.floor(min(p[0] for p in polygon)), math.ceil(max(p[0] for p in polygon))):
        clipped = clip_polygon(clip_polygon(polygon, 0, x, True), 0, x + 1, False)
        if not clipped:
            continue
        x0, x1 = min(p[0] for p in clipped), max(p[0] for p in clipped)
        z0, z1 = min(p[1] for p in clipped), max(p[1] for p in clipped)
        if x1 - x0 < 1e-8 or z1 - z0 < 1e-8:
            continue
        out.append({"from": [round(x0, 10), y_low, round(z0, 10)],
                    "to": [round(x1, 10), y_high, round(z1, 10)]})
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-pack", type=Path, default=Path("C:/Users/vakir/Downloads/bbmc_v15_resource (1).zip"))
    parser.add_argument("--resources", type=Path, default=ROOT / "src/architecture/resources")
    args = parser.parse_args()
    imports = []
    models = {}
    textures = {}
    with zipfile.ZipFile(args.source_pack) as archive:
        names = set(archive.namelist())
        def import_model(path):
            entry = "assets/minecraft/models/" + path + ".json"
            raw = archive.read(entry)
            original = json.loads(raw)
            imported = copy.deepcopy(original)
            for key, ref in imported.get("textures", {}).items():
                if ref.startswith("#"):
                    continue
                ns, local = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
                source = f"assets/{ns}/textures/{local}.png"
                if source not in names:
                    raise ValueError("Missing reviewed window texture: " + source)
                target = f"assets/{NS}/textures/base/source/{ns}/{local}.png"
                for suffix in ("", ".mcmeta", "_e.png"):
                    src = source if not suffix else (source + suffix if suffix == ".mcmeta" else source[:-4] + suffix)
                    dst = target if not suffix else (target + suffix if suffix == ".mcmeta" else target[:-4] + suffix)
                    if src in names and src not in textures:
                        data = archive.read(src)
                        destination = args.resources / dst
                        destination.parent.mkdir(parents=True, exist_ok=True)
                        if destination.exists() and destination.read_bytes() != data:
                            raise ValueError("Refusing to replace an edited/shared source texture: " + str(destination))
                        destination.write_bytes(data)
                        textures[src] = {"source": src, "target": dst, "sha256": digest(data), "byte_identical": True}
                        if src.endswith(".png"):
                            textures[src]["png"] = png_alpha(data)
                imported["textures"][key] = f"{NS}:base/source/{ns}/{local}"
            target = f"assets/{NS}/models/base/source/minecraft/{path}.json"
            dump(args.resources / target, imported)
            assert imported["elements"] == original["elements"], "Source geometry/UV changed"
            models[path] = (original, imported, f"{NS}:base/source/minecraft/{path}")
            imports.append({"source": entry, "target": target, "source_sha256": digest(raw),
                            "element_geometry_uv_faces_rotation_equal": True})
        import_model("block/wood_window")
        import_model("block/hold/window_01")
        import_model("block/hold/window_02")
        import_model("block/hold/window_03")
        source_selectors = []
        for carrier in ("jungle_stairs", "red_sandstone_stairs", "blackstone_stairs"):
            blockstate = json.loads(archive.read(f"assets/minecraft/blockstates/{carrier}.json"))
            for state, choices in blockstate.get("variants", {}).items():
                for applied in choices if isinstance(choices, list) else [choices]:
                    if applied.get("model") in {"minecraft:" + key for key in models}:
                        source_selectors.append({"carrier": "minecraft:" + carrier, "state": state, "applied": applied})
        center_alpha = alpha_roi_rgba8(archive.read("assets/minecraft/textures/block/addon/spirelamp_0022.png"), [0, 41, 32, 57])
        assert center_alpha == {"255": 512}, "Opaque-center source evidence changed; review semantics instead of inventing an aperture"

    wood, rebased_wood, wood_model = models["block/wood_window"]
    assert len(wood["elements"]) == 3
    assert [element["rotation"]["angle"] for element in wood["elements"]] == [0, -22.5, 22.5]
    assert [element["rotation"]["origin"] for element in wood["elements"]][1:] == [[-8, 0, 7], [24, 0, 7]]
    assert wood["elements"][0]["faces"]["north"]["uv"] == [0, 5.125, 4, 7.125]
    source_parts = []
    for index, name in enumerate(("frame", "left", "right")):
        part = copy.deepcopy(rebased_wood)
        part["elements"] = [part["elements"][index]]
        model = f"{NS}:base/prototype_windows/wood/{name}"
        alt = f"{NS}:alt/prototype_windows/wood/{name}"
        dump(args.resources / f"assets/{NS}/models/base/prototype_windows/wood/{name}.json", part)
        dump(args.resources / f"assets/{NS}/models/alt/prototype_windows/wood/{name}.json", {"parent": model})
        assert part["elements"][0] == wood["elements"][index]
        source_parts.append({"model": model, "altModel": alt, "offset": [0, 0, 0], "yaw": 180,
                             "pivot": [8, 8, 8], "extraPivot": wood["elements"][index]["rotation"]["origin"], "extraYaw": 0})

    poses = {}
    for opened, name in ((False, "closed"), (True, "open")):
        parts = copy.deepcopy(source_parts)
        additional = [0, -45 if opened else 0, 45 if opened else 0]
        for index, extra in enumerate(additional):
            parts[index]["extraYaw"] = extra
        boxes = [box for index, element in enumerate(wood["elements"]) for box in collision_boxes(element, additional[index])]
        poses[name] = {"parts": parts, "collision": boxes, "selection": copy.deepcopy(boxes)}
    wood_descriptor = {"schemaVersion": 1, "units": 16, "id": f"{NS}:prototype_wood_window",
                       "openable": True,
                       "displayName": "Деревянное окно · боковые панели · прототип", "support": {"offset": [0, -1, 0], "required": True},
                       "variants": [{"poses": poses}], "proposal": {"status": "PROPOSED_NOT_ACCEPTED", "sourcePose": "closed",
                       "authoredSideYaw": [-22.5, 22.5], "proposedOpenSideYaw": [-67.5, 67.5],
                       "centralPanelMoves": False, "centralPanelTraversable": False,
                       "note": "Original center UV contains 512/512 opaque texels. Side-only movement does not open a central passage."},
                       "source": {"model": "minecraft:block/wood_window", "carrier": "minecraft:jungle_stairs",
                       "selector": "half=bottom,shape=straight", "sample": [-32, 37, -1120],
                       "yawByFacing": {"north": 180, "east": 270, "south": 0, "west": 90}, "numericIdsFrozen": False},
                       "essentialMask": [[0, 0, 0]], "placementPolicy": "SOFT_OVERLAP",
                       "globalOrientations": 8, "orientationStepDegrees": 45,
                       "collisionPolicy": "Opaque center exact thin prism; side panels conservative 1-model-unit strips. Physical collision and selection do not reserve placement volume."}
    thin_variants = []
    for number in range(1, 4):
        key = f"block/hold/window_{number:02}"
        thin, rebased_thin, thin_model = models[key]
        assert len(thin["elements"]) == 1 and set(thin["elements"][0]["faces"]) == {"north", "south"}
        assert all("cullface" not in face for face in thin["elements"][0]["faces"].values())
        thin_alt = f"{NS}:alt/prototype_windows/thin/window_{number:02}"
        dump(args.resources / f"assets/{NS}/models/alt/prototype_windows/thin/window_{number:02}.json", {"parent": thin_model})
        thin_boxes = collision_boxes(thin["elements"][0])
        thin_variants.append({"sourceModel": "minecraft:" + key, "sourceIntrinsicYaw": thin["elements"][0]["rotation"]["angle"],
            "poses": {"closed": {"parts": [{"model": thin_model, "altModel": thin_alt,
            "offset": [0, 0, 0], "yaw": 180, "pivot": [8, 8, 8], "extraYaw": 0}],
            "collision": thin_boxes, "selection": copy.deepcopy(thin_boxes)}}})
    thin_descriptor = {"schemaVersion": 1, "units": 16, "id": f"{NS}:prototype_thin_window",
                       "openable": False,
                       "displayName": "Тонкое остекление · прототип", "support": {"offset": [0, -1, 0], "required": False},
                       "variants": thin_variants, "essentialMask": [[0, 0, 0]], "placementPolicy": "SOFT_OVERLAP",
                       "globalOrientations": 8, "orientationStepDegrees": 45,
                       "source": {"model": "minecraft:block/hold/window_01", "carrier": "minecraft:red_sandstone_stairs",
                       "selector": "half=bottom,shape=straight", "sample": [-121, 53, -241],
                       "yawByFacing": {"north": 180, "east": 270, "south": 0, "west": 90}, "numericIdsFrozen": False},
                       "proposal": {"status": "PROPOSED_NOT_ACCEPTED", "motion": "FIXED_GLAZING",
                       "mountingPolicy": "PENDING_REVIEW", "note": "Source has no below support and intersects adjacent brick wall; foreign cells are never removed."},
                       "renderPolicy": "CUTOUT, exact PNG bytes and authored north+south faces without cullface; no translucency dependency added.",
                       "collisionPolicy": "Thin fixed glazing polygons, not a rotated full AABB. Alpha rendering does not imply a traversable pane. Collision and selection do not reserve neighbor cells for placement.",
                       "variantMeaning": "01 two identical artworks; 02 different north artwork; 03 preserves source 02 artwork with Z-4 and intrinsic -45 pose. All share one registry ID. Global rotation applies exactly once beyond authored model pose."}
    for key, descriptor in (("prototype_wood_window", wood_descriptor), ("prototype_thin_window", thin_descriptor)):
        dump(args.resources / f"bloodborne_dw/composite/{key}.json", descriptor)
        dump(args.resources / f"assets/{NS}/models/item/{key}.json", {"parent": "minecraft:builtin/entity"})
    report = {"schema": "dreamwalker-window-proposal-assets-v1", "status": "PROPOSED_NOT_ACCEPTED",
              "source_pack": str(args.source_pack), "source_pack_sha256": digest(args.source_pack.read_bytes()),
              "numeric_ids_frozen": False, "models": imports, "textures": list(textures.values()),
              "source_elements_preserved": "PASS", "closed_split_recombination": "PASS",
              "thin_opposite_faces_preserved": "PASS", "source_facing_mapping_preserved": "PASS",
              "center_alpha_evidence": {"source": "assets/minecraft/textures/block/addon/spirelamp_0022.png",
                  "roi_pixels": [0, 41, 32, 57], "alpha_histogram": center_alpha, "interpretation": "Opaque panel; no central aperture supplied."},
              "collision_approximation": "Source center/glazing exact. Rotated side footprints conservative X strips of width <=1 model unit.",
              "source_selectors": source_selectors, "global_orientations": 8, "orientation_step_degrees": 45,
              "placement_policy": "Only root essential; collision and selection kept independently from placement. Neighbor visual overlap does not authorize neighbor deletion.",
              "thin_variant_comparison": {"01_vs_02_geometry_equal": models["block/hold/window_01"][0]["elements"][0]["from"] == models["block/hold/window_02"][0]["elements"][0]["from"] and models["block/hold/window_01"][0]["elements"][0]["to"] == models["block/hold/window_02"][0]["elements"][0]["to"],
                  "01_vs_02_faces_uv_equal": models["block/hold/window_01"][0]["elements"][0]["faces"] == models["block/hold/window_02"][0]["elements"][0]["faces"],
                  "02_vs_03_faces_uv_equal": models["block/hold/window_02"][0]["elements"][0]["faces"] == models["block/hold/window_03"][0]["elements"][0]["faces"],
                  "03_intrinsic_yaw": -45, "03_additional_baked_yaw": 0, "registry_family_count": 1},
              "runtime": "NOT_RUN", "client_visuals": "NOT_RUN", "mass_conversion": "NOT_APPLIED"}
    dump(ROOT / "reports/WINDOW_ASSET_PROPOSAL.json", report)
    print("Imported exact source window geometry/UV and PNGs; emitted two reviewed proposal descriptors; no numeric IDs assigned.")


if __name__ == "__main__":
    main()
