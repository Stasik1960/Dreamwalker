"""Compile the finite RGB material table and verify the reviewed geometry."""
from collections import Counter, deque
from hashlib import sha256
from io import BytesIO
from math import ceil, floor, isfinite
from pathlib import Path
from statistics import median
from zipfile import ZipFile
import argparse
import json
import struct

from PIL import Image
from rgb_catalogue import Inspector, candidates
from rgb_native_catalogue import extend_native

MODULE = Path(__file__).resolve().parents[1]
RESOURCES = MODULE / "src/main/resources"
ALT = RESOURCES / "dw_city_visuals/alt"
CUBE = "bloodborne_dw:remaining/source/minecraft/block/cube_all"
BRICKS = "bloodborne_dw:block/remaining/source/minecraft/block/bricks"
STONE5 = "bloodborne_dw:block/remaining/source/minecraft/block/addon/stone_bricks_5"
OBSIDIAN = "bloodborne_dw:block/remaining/source/minecraft/block/obsidian"
SURROUND = "bloodborne_dw:block/autumn_c3/window_surround"
GOTHIC_BOTTOM = "bloodborne_dw:block/remaining/source/minecraft/block/addon/spirelamp_0010"
GOTHIC_TOP = "bloodborne_dw:block/remaining/source/minecraft/block/addon/spirelamp_0017"
GOTHIC_GLASS = "bloodborne_dw:block/remaining/source/minecraft/block/addon/window_4"
WALL_MODELS = ("interactions/shutter_wall_0", "interactions/shutter_wall_1")
GOTHIC_MODELS = ("remaining/source/minecraft/block/window_bottom_right",
                 "remaining/source/minecraft/block/window_top_right")
PROFILES = {
    "bricks": ("source_bricks", {0: ("remaining/source/minecraft/block/bricks",)}, (BRICKS,), (2399, 25786)),
    "stone_5": ("source_bricks_stone_2", {8: ("remaining/source/minecraft/block/bricks/stonebrick_5",)}, (STONE5,), (1470,)),
    "obsidian": ("source_obsidian", {0: ("remaining/source/minecraft/block/obsidian",)}, (OBSIDIAN,), (82205,)),
    "shutter_stone": ("source_shutter_window_surround", {0: WALL_MODELS[:1], 1: WALL_MODELS[1:]}, (SURROUND,), ()),
    "gothic_right": ("source_gothic_window_right", {0: GOTHIC_MODELS}, (GOTHIC_BOTTOM, GOTHIC_TOP), ()),
}
NAMES = {BRICKS: "source_bricks", STONE5: "stone_bricks_5", OBSIDIAN: "obsidian",
         SURROUND: "window_surround", GOTHIC_BOTTOM: "gothic_bottom", GOTHIC_TOP: "gothic_top"}

# The names identify the actual textured models inspected for this release.
# All rows below are separately pinned to variant 0 and a six-face cube.
CUBE_ROOT = "remaining/source/minecraft/block/"
SIMPLE_STONE = (
    ("source_bricks_m_bricks_0", "bricks/m_bricks_0", "addon/mossy_stone_bricks_0"),
    ("source_bricks_purpur_block_0", "bricks/purpur_block_0", "addon/purpur_block_0"),
    ("source_bricks_stonebrick_6", "bricks/stonebrick_6", "addon/stone_bricks_6"),
    ("source_calcite", "calcite", "calcite"),
    ("source_cobblestone", "cobblestone", "cobblestone"),
    ("source_cracked_deepslate_tiles", "cracked_deepslate_tiles", "cracked_deepslate_tiles"),
    ("source_dripstone_block", "dripstone_block", "dripstone_block"),
    ("source_granite", "granite", "granite"),
    ("source_mossy_cobblestone", "mossy_cobblestone", "mossy_cobblestone"),
    ("source_polished_andesite", "polished_andesite", "polished_andesite"),
    ("source_smooth_basalt", "smooth_basalt", "smooth_basalt"),
    ("source_tuff", "tuff", "tuff"),
)
for block, model, sprite in SIMPLE_STONE:
    material = "bloodborne_dw:block/" + CUBE_ROOT + sprite
    PROFILES[block] = (block, {0: (CUBE_ROOT + model,)}, (material,), ())
    NAMES[material] = block

def asset(identifier, directory, ext):
    namespace, path = identifier.split(":", 1)
    return f"assets/{namespace}/{directory}/{path}{ext}"


def asset_id(path):
    _, namespace, name = path.split("/", 2)
    return f"{namespace}:{name}"


def luminance(pixel):
    return (54 * pixel[0] + 183 * pixel[1] + 19 * pixel[2] + 128) // 256


def tone(image, zone, factor=(1, 1, 1)):
    colors = [image.getpixel(point) for point in sorted(zone) if image.getpixel(point)[3]]
    assert colors, "Empty reviewed texture zone"
    ref = [round(median(color[i] for color in colors) * factor[i]) for i in range(3)]
    return (ref[0] << 16) | (ref[1] << 8) | ref[2]


def uv_texels(uv, width, height):
    assert len(uv) == 4 and all(isfinite(v) and 0 <= v <= 16 for v in uv), uv
    x0, x1 = sorted((uv[0] * width / 16, uv[2] * width / 16))
    y0, y1 = sorted((uv[1] * height / 16, uv[3] * height / 16))
    left, top = min(width-1, floor(x0)), min(height-1, floor(y0))
    return {(x, y) for y in range(top, max(top+1, ceil(y1)))
                   for x in range(left, max(left+1, ceil(x1)))}


def neutral(image, zone):
    pixels = image.load()
    # Extend the verified stone relief around the UV islands before Minecraft
    # makes its mip chain. Protected details continue using the original sprite.
    ref = round(median(luminance(pixels[p]) for p in zone if pixels[p][3]))
    assert ref > 0
    result = Image.new("RGBA", image.size)
    out = result.load()
    seen = set(zone)
    queue = deque(sorted(zone))
    for x, y in queue:
        shade = min(255, round(luminance(pixels[x, y]) * 255 / ref))
        out[x, y] = (shade, shade, shade, pixels[x, y][3])
    while queue:
        x, y = queue.popleft()
        shade = out[x, y][0]
        for nx, ny in ((x-1, y), (x+1, y), (x, y-1), (x, y+1)):
            if 0 <= nx < image.width and 0 <= ny < image.height and (nx, ny) not in seen:
                seen.add((nx, ny))
                queue.append((nx, ny))
                out[nx, ny] = (shade, shade, shade, pixels[nx, ny][3])
    return result



def uv_bounds(uv):
    return min(uv[0], uv[2]), min(uv[1], uv[3]), max(uv[0], uv[2]), max(uv[1], uv[3])


def contains(outer, inner):
    return (outer[0] <= inner[0] and outer[1] <= inner[1]
            and inner[2] <= outer[2] and inner[3] <= outer[3])


def catalogue_variants(inspector, inherited):
    """Yield exact source variants, keeping independently separable faces only."""
    for row in candidates():
        audit = inspector.inspect(row, scan_all=True)
        for variant in audit["variants"]:
            models, model_assets, by_sprite = {}, set(), {}
            for parts in variant["poses"].values():
                for part in parts:
                    if "error" in part or part["model"] in models:
                        continue
                    counts = Counter(face["zone"] for face in part["faces"])
                    models[part["model"]] = dict(counts)
                    models[part["sourceModel"]] = dict(counts)
                    model_assets.update(part["parents"])
                    model_assets.add(asset(part["sourceModel"], "models", ".json"))
                    for face in part["faces"]:
                        by_sprite.setdefault(face["sprite"], []).append((part["model"], face))
            zones, texels, examples = {}, {}, []
            for sprite, faces in by_sprite.items():
                paint = [f for _, f in faces if f["zone"] == "PAINT"]
                keep = [uv_bounds(f["uv"]) for _, f in faces if f["zone"] != "PAINT"
                        and f["reason"] != "no visible texels in sampled UV"]
                # A cropped render quad must never turn an intersecting KEEP
                # island into a painted face. Classify actual texels offline.
                safe = [f for f in paint if not any(contains(uv_bounds(f["uv"]), guard) for guard in keep)]
                if safe:
                    zones[sprite] = sorted({uv_bounds(f["uv"]) for f in safe})
                    texels[sprite] = {p for f in safe
                                     for p in uv_texels(f["uv"], *inspector.image(sprite)[0].size)}
                for model, face in faces:
                    if face["zone"] == "KEEP" and len(examples) < 16:
                        examples.append({"model": model, **face})
                if paint and not safe:
                    examples.append({"sprite": sprite, "reason": "protected UV footprint lies inside every paint zone",
                                     "paint": [f["uv"] for f in paint], "keep": keep})
            block, number = row["id"], variant["variant"]
            recorded = "variant" not in row or row["variant"] == number
            status = "UNRESOLVED" if variant["review"] == "UNRESOLVED" else (
                "READY_FOR_GAME_CHECK" if zones else "EXCLUDED_WITH_EVIDENCE")
            geometry = {"variant": number, "recorded": recorded, "status": status,
                        "models": models, "poseParts": {
                            pose: [{k: v for k, v in part.items() if k not in ("faces", "parents")}
                                   for part in parts]
                            for pose, parts in variant["poses"].items()},
                        "faceCounts": variant["faceCounts"], "directional": variant["directional"]}
            if examples and (status != "READY_FOR_GAME_CHECK" or recorded):
                geometry["keepEvidence"] = examples
            if (block, number) in inherited:
                geometry.update({"status": "READY_FOR_GAME_CHECK", "profile": inherited[(block, number)]})
                zones = {}
            yield row, audit, geometry, models, model_assets, by_sprite, zones, texels


def extend_catalogue(baseline, profiles, manual, facades, evidence_path):
    inspector = Inspector(baseline)
    inherited = {(p["block"], number): key for key, p in profiles.items() for number in p["variants"]}
    inspected = list(catalogue_variants(inspector, inherited))
    source_texels = {}
    for _, _, _, _, _, _, zones, texels in inspected:
        for sprite in zones:
            source_texels.setdefault(sprite, set()).update(texels[sprite])

    # Neutral atlas entries belong to materials, not the chosen RGB or pose.
    # Reuse previously shipped sprites with the same encoded image.
    known_images = {}
    for p in profiles.values():
        for info in p["paints"].values():
            identifier = info["neutral"]
            known_images[sha256(inspector.read(asset(identifier, "textures", ".png"))).hexdigest()] = identifier
    outputs = {}
    for sprite, pixels in sorted(source_texels.items()):
        source = asset(sprite, "textures", ".png")
        try:
            inspector.read(source + ".mcmeta")
        except FileNotFoundError:
            pass
        else:
            raise ValueError("Animated RGB source needs a per-frame review: " + sprite)
        image = inspector.image(sprite)[0]
        gray = neutral(image, pixels)
        assert gray.getchannel("A").tobytes() == image.getchannel("A").tobytes()
        for mip in range(min(image.size).bit_length()):
            size = tuple(max(1, extent >> mip) for extent in image.size)
            mip_pixels = gray.resize(size, Image.Resampling.BOX)
            assert mip_pixels.getchannel("R").tobytes() == mip_pixels.getchannel("G").tobytes()
            assert mip_pixels.getchannel("G").tobytes() == mip_pixels.getchannel("B").tobytes()
            assert mip_pixels.getchannel("A").tobytes() == image.resize(size, Image.Resampling.BOX).getchannel("A").tobytes()
        encoded = BytesIO()
        gray.save(encoded, format="PNG", optimize=True)
        data = encoded.getvalue()
        digest = sha256(data).hexdigest()
        target = known_images.get(digest)
        if target is None:
            target = "bloodborne_dw:block/editor2_rgb/catalogue/" + digest[:24]
            out = ALT / asset(target, "textures", ".png")
            out.parent.mkdir(parents=True, exist_ok=True)
            if not out.is_file() or out.read_bytes() != data:
                out.write_bytes(data)
            known_images[digest] = target
        outputs[sprite] = (target, digest)

    generated = {asset(identifier, "textures", ".png") for identifier, _ in outputs.values()}
    output_dir = ALT / "assets/bloodborne_dw/textures/block/editor2_rgb/catalogue"
    for prior in output_dir.glob("*.png"):
        # Only remove orphan files owned by this compiler's content-addressed output.
        if (prior.relative_to(ALT).as_posix() not in generated
                and sha256(prior.read_bytes()).hexdigest()[:24] == prior.stem):
            prior.unlink()

    expected_hashes = {}
    def fingerprint(path, manifest):
        if path not in expected_hashes:
            try:
                expected_hashes[path] = sha256(inspector.read(path)).hexdigest()
            except FileNotFoundError:
                if not path.endswith(".mcmeta"):
                    raise
                expected_hashes[path] = ""
        manifest[asset_id(path)] = expected_hashes[path]

    grouped, evidence, reviewed = {}, {}, {}
    for row, audit, geometry, models, model_assets, by_sprite, zones, texels in inspected:
        block = row["id"]
        if block not in evidence:
            evidence[block] = {"id": block, "catalogueKey": row["catalogueKey"], "displayName": row["displayName"],
                               "sourceVariantCount": audit["sourceVariantCount"],
                               "specSha256": sha256(inspector.read(audit["specPath"])).hexdigest(), "variants": []}
        evidence[block]["variants"].append(geometry)
        inherited_key = geometry.get("profile")
        if inherited_key:
            if len(profiles[inherited_key]["variants"]) == 1:
                reviewed[inherited_key] = reviewed_rectangles(by_sprite, profiles[inherited_key]["paints"])
        if not zones or geometry["status"] != "READY_FOR_GAME_CHECK":
            continue
        expected, paints = {}, {}
        model_assets.add(asset(block, "blockstates", ".json"))
        for path in model_assets:
            fingerprint(path, expected)
        for source in by_sprite:
            path = asset(source, "textures", ".png")
            fingerprint(path, expected)
            fingerprint(path + ".mcmeta", expected)
        for source, bounds in zones.items():
            target, target_hash = outputs[source]
            target_path = asset(target, "textures", ".png")
            expected[asset_id(target_path)] = target_hash
            fingerprint(target_path + ".mcmeta", expected)
            image = inspector.image(source)[0]
            pixels = texels[source]
            base = tone(image, pixels)
            stored_tones, regional_tones = [base], [base]
            for role in range(1, 5):
                targets = manual.get(source)
                legacy = targets[role-1] if targets else source
                regional, multiplier = regional_sprite(facades, source, role)
                for used in (legacy, regional):
                    used_path = asset(used, "textures", ".png")
                    fingerprint(used_path, expected)
                    fingerprint(used_path + ".mcmeta", expected)
                    assert inspector.image(used)[0].size == image.size, (source, used)
                stored_tones.append(tone(inspector.image(legacy)[0], pixels))
                regional_tones.append(tone(inspector.image(regional)[0], pixels, multiplier))
            paint = {"neutral": target, "manual": stored_tones, "regional": regional_tones,
                     "size": list(image.size), "texels": len(pixels),
                     "mipLevels": min(image.size).bit_length()-1}
            # Runtime checks all four UVs for split surfaces. Whole entries
            # are used only when every visible face of this sprite was safe.
            if any(f["zone"] != "PAINT" for _, f in by_sprite[source]) or (
                    set(bounds) != {uv_bounds(f["uv"]) for _, f in by_sprite[source]}):
                paint["zones"] = bounds
            paints[source] = paint
        unambiguous = len(paints) == 1 and all(f["zone"] == "PAINT" or f["reason"] == "no visible texels in sampled UV"
                for faces in by_sprite.values() for _, f in faces)
        definition = {"block": block, "assets": expected, "models": models, "paints": paints,
                      "nativeMeshes": [], "sampleUnambiguous": unambiguous}
        if not unambiguous:
            directions = {side: source for side, source in geometry["directional"].items() if source in paints}
            if directions:
                definition["sampleFaces"] = directions
        signature = json.dumps(definition, sort_keys=True, separators=(",", ":"))
        key = grouped.get(signature)
        number = geometry["variant"]
        if key is None:
            key = block.split(":", 1)[1] + "_v" + str(number)
            grouped[signature] = key
            profiles[key] = {**definition, "variants": []}
            reviewed[key] = reviewed_rectangles(by_sprite, paints)
        profiles[key]["variants"].append(number)
        geometry["profile"] = key
    if evidence_path:
        evidence_path.parent.mkdir(parents=True, exist_ok=True)
        evidence_path.write_text(json.dumps({"schema": 1, "items": list(evidence.values())},
                                            ensure_ascii=False, separators=(",", ":")) + "\n",
                                 encoding="utf-8", newline="\n")
    summary = Counter(v["status"] for item in evidence.values() for v in item["variants"] if v["recorded"])
    print(f"Recorded catalogue geometry: {dict(summary)}; {len(outputs)} shared new material mappings")
    return reviewed


def reviewed_rectangles(by_sprite, paints):
    result = {}
    for sprite, faces in by_sprite.items():
        allowed = paints.get(sprite)
        paint = []
        keep = []
        for _, face in faces:
            bounds = uv_bounds(face["uv"])
            if allowed and face["zone"] == "PAINT" and (
                    "zones" not in allowed or any(contains(z, bounds) for z in allowed["zones"])):
                paint.append(bounds)
            else:
                keep.append(bounds)
        result[sprite] = {"paint": sorted(set(paint)), "keep": sorted(set(keep))}
    return result

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, default=MODULE / "baseline/mod.jar")
    parser.add_argument("--catalogue-evidence", type=Path)
    args = parser.parse_args()
    with ZipFile(args.baseline) as baseline:
        names = set(baseline.namelist())

        def read(path):
            for tree in (ALT, RESOURCES):
                file = tree / path
                if file.is_file():
                    return file.read_bytes()
            if path in names:
                return baseline.read(path)
            raise FileNotFoundError(path)

        def image_for(sprite):
            return Image.open(BytesIO(read(asset(sprite, "textures", ".png")))).convert("RGBA")

        def fingerprint(path, expected):
            expected[asset_id(path)] = sha256(read(path)).hexdigest()
            if path.endswith(".png"):
                try:
                    expected[asset_id(path + ".mcmeta")] = sha256(read(path + ".mcmeta")).hexdigest()
                except FileNotFoundError:
                    expected[asset_id(path + ".mcmeta")] = ""

        manual = json.loads(read("assets/bloodborne_dw/visuals/editor2-palette.json"))["sprites"]
        facades = json.loads(read("assets/bloodborne_dw/visuals/facades.json"))
        policies = json.loads((MODULE / "tools/editor2-palette-inputs/classification.json").read_text(encoding="utf-8"))["sprites"]
        meshes = json.loads(read("assets/bloodborne_dw/native_grid/catalog.json"))
        native = read("assets/bloodborne_dw/native_grid/meshes.bin")
        mesh_profiles = {mesh: (registry, next(iter(variants)), sprites[0])
                         for registry, variants, sprites, indices in PROFILES.values() for mesh in indices}
        native_evidence = audit_native(native, meshes, mesh_profiles)
        profiles = {}
        for key, (registry, variants, sprites, indices) in PROFILES.items():
            expected = {}
            fingerprint(f"assets/bloodborne_dw/blockstates/{registry}.json", expected)
            spec = json.loads(read(f"bloodborne_dw/composite/{registry}.json"))
            model_zones = {sprite: set() for sprite in sprites}
            originals = {sprite: image_for(sprite) for sprite in sprites}
            faces = {}
            for variant, paths in variants.items():
                for pose in spec["variants"][variant]["poses"].values():
                    assert [p["model"] for p in pose["parts"]] == ["bloodborne_dw:" + path for path in paths]
                for path in paths:
                    full_id = "bloodborne_dw:" + path
                    model_path = asset(full_id, "models", ".json")
                    fingerprint(model_path, expected)
                    model = json.loads(read(model_path))
                    counts = Counter()
                    if "elements" not in model:
                        assert model["parent"] == CUBE and set(model["textures"]) == {"all"}
                        fingerprint(asset(CUBE, "models", ".json"), expected)
                        all_model = json.loads(read(asset(CUBE, "models", ".json")))
                        cube_path = asset(all_model["parent"], "models", ".json")
                        fingerprint(cube_path, expected)
                        cube = json.loads(read(cube_path))
                        fingerprint(asset(cube["parent"], "models", ".json"), expected)
                        assert len(cube["elements"]) == 1
                        used = cube["elements"][0]["faces"]
                        assert len(used) == 6 and all(all_model["textures"][f["texture"][1:]] == "#all" for f in used.values())
                        sprite = model["textures"]["all"]
                        assert sprite in sprites
                        im = originals[sprite]
                        model_zones[sprite].update(uv_texels([0, 0, 16, 16], *im.size))
                        counts["PAINT"] = 6
                    else:
                        for element in model["elements"]:
                            for face in element["faces"].values():
                                sprite = model["textures"][face["texture"][1:]]
                                assert sprite in sprites or sprite == GOTHIC_GLASS, (path, sprite)
                                if sprite not in sprites:
                                    counts["KEEP"] += 1
                                    assert all(regional_sprite(facades,sprite,r)[0] == sprite for r in range(1,5))
                                    fingerprint(asset(sprite, "textures", ".png"), expected)
                                    continue
                                assert face.get("tintindex", -1) == -1
                                counts["PAINT"] += 1
                                im = originals[sprite]
                                model_zones[sprite].update(uv_texels(face["uv"], *im.size))
                    faces[full_id] = dict(counts)
            assert key != "shutter_stone" or [f["PAINT"] for f in faces.values()] == [38, 20]
            assert key != "gothic_right" or [f["KEEP"] for f in faces.values()] == [4, 4]
            paints = {}
            for sprite, zone in model_zones.items():
                im = originals[sprite]
                src_file = asset(sprite, "textures", ".png")
                fingerprint(src_file, expected)
                policy = policies[sprite]
                if policy["classification"] == "mixed":
                    mask = Image.open(MODULE / "tools/editor2-palette-inputs" / policy["mask"]).convert("L")
                    assert mask.size == im.size and all(mask.getpixel(p) == 255 for p in zone), sprite
                else:
                    assert policy["classification"] == "stone", sprite
                output = "bloodborne_dw:block/editor2_rgb/" + NAMES[sprite]
                out_path = asset(output, "textures", ".png")
                output_file = ALT / out_path
                output_file.parent.mkdir(parents=True, exist_ok=True)
                gray = neutral(im, zone)
                assert gray.getchannel("A").tobytes() == im.getchannel("A").tobytes()
                for level in range(min(im.size).bit_length()):
                    size = tuple(max(1, n >> level) for n in im.size)
                    down = gray.resize(size, Image.Resampling.BOX)
                    assert down.getchannel("R").tobytes() == down.getchannel("G").tobytes() == down.getchannel("B").tobytes()
                    assert down.getchannel("A").tobytes() == im.resize(size, Image.Resampling.BOX).getchannel("A").tobytes()
                gray.save(output_file, optimize=True)
                fingerprint(out_path, expected)
                base = tone(im, zone)
                stored_tones, regional_tones = [base], [base]
                for role in range(1, 5):
                    targets = manual.get(sprite)
                    legacy = targets[role-1] if targets else sprite
                    regional, multiplier = regional_sprite(facades, sprite, role)
                    for used in (legacy, regional):
                        fingerprint(asset(used, "textures", ".png"), expected)
                        assert image_for(used).size == im.size
                    stored_tones.append(tone(image_for(legacy), zone))
                    regional_tones.append(tone(image_for(regional), zone, multiplier))
                paints[sprite] = {"neutral": output, "manual": stored_tones, "regional": regional_tones,
                                  "size": list(im.size), "texels": len(zone),
                                  "mipLevels": min(im.size).bit_length()-1}
            profiles[key] = {"block": "bloodborne_dw:" + registry, "variants": list(variants),
                             "assets": expected, "models": faces, "paints": paints,
                             "nativeMeshes": list(indices)}
        reviewed = extend_catalogue(baseline, profiles, manual, facades, args.catalogue_evidence)
        native_catalogue = extend_native(baseline, profiles, reviewed)
        if args.catalogue_evidence:
            path = args.catalogue_evidence.with_name("native-geometry.json")
            path.write_text(json.dumps(native_catalogue, separators=(",", ":"), sort_keys=True) + "\n",
                            encoding="utf-8", newline="\n")
        for profile in profiles.values():
            if not profile.get("nativeCells"):
                profile.pop("nativeCells", None)
        assets = {}
        for profile in profiles.values():
            required = profile.pop("assets")
            for path, digest in required.items():
                if assets.setdefault(path, digest) != digest:
                    raise ValueError("Conflicting expected RGB asset hashes: " + path)
            profile["assets"] = sorted(required)
        output_file = RESOURCES / "bloodborne_dw/rgb-phase1.json"
        policy = {"schema": 2, "assets": assets, "profiles": profiles, "nativeAudit": native_evidence}
        catalogue_dir = ALT / "assets/bloodborne_dw/textures/block/editor2_rgb/catalogue"
        wanted = {asset(paint["neutral"], "textures", ".png")
                  for profile in profiles.values() for paint in profile["paints"].values()}
        if catalogue_dir.is_dir():
            for path in catalogue_dir.glob("*.png"):
                if path.relative_to(ALT).as_posix() in wanted:
                    continue
                if len(path.stem) != 24 or any(c not in "0123456789abcdef" for c in path.stem):
                    raise ValueError(f"Unexpected file in generated RGB directory: {path}")
                path.unlink()
        output_file.write_text(json.dumps(policy, separators=(",", ":"), sort_keys=True) + "\n",
                               encoding="utf-8", newline="\n")
        unique_sprites = {paint["neutral"] for profile in profiles.values() for paint in profile["paints"].values()}
        print(f"RGB: {len(profiles)} profiles, {len(unique_sprites)} neutral sprites, "
              f"{native_catalogue['acceptedCells']} exact native cells / {native_catalogue['acceptedMeshes']} meshes")


def regional_sprite(facades, sprite, role):
    identity = (1, 1, 1)
    if role == 1:
        return facades["roseSprites"].get(sprite, sprite), identity
    if role == 4:
        return facades["brickWindowSprites"].get(sprite, sprite), identity
    if sprite not in facades["brickSprites"]:
        return sprite, identity
    return (facades["accentBaseSprites"].get(sprite, sprite),
            facades["roleMultipliers"]["sage" if role == 2 else "sand"])


def audit_native(raw, catalogue, required):
    magic, count = struct.unpack_from(">II", raw)
    assert magic == 1313296689 and count == len(catalogue["meshes"])
    evidence = {}
    offset = 8
    for mesh, entry in enumerate(catalogue["meshes"]):
        n, = struct.unpack_from(">I", raw, offset)
        offset += 4
        assert n == entry["quadCount"]
        if mesh in required:
            block, variant, sprite = required[mesh]
            assert n == 6
            geometry = set()
            for q in range(n):
                start = offset + q*139
                material, = struct.unpack_from(">I", raw, start)
                assert catalogue["materials"][material] == {"layer": "solid", "sprite": sprite}
                corners = set()
                for vertex in range(4):
                    x, y, z, u, v, color = struct.unpack_from(">5fI", raw, start+11+vertex*32)
                    assert all(abs(k-round(k)) < 1e-5 and round(k) in (0,1) for k in (x,y,z))
                    assert 0 < u < 16 and 0 < v < 16 and color == 0xffffffff
                    corners.add((round(x),round(y),round(z)))
                planes = [axis for axis in range(3) if len({p[axis] for p in corners}) == 1]
                assert len(corners) == 4 and len(planes) == 1
                axis = planes[0]
                geometry.add((axis, next(iter(corners))[axis]))
            assert len(geometry) == 6
            cells = [i for i, cell in enumerate(catalogue["cells"]) if cell["mesh"] == mesh]
            assert cells
            member_cells = {c["cell"] for obj in catalogue["wholeObjects"] for c in obj["cells"]}
            for i in cells:
                cell = catalogue["cells"][i]
                state = cell["baseState"]
                assert (state["registryId"], state["properties"]["variant"]) == ("bloodborne_dw:" + block, str(variant))
                assert cell["localFunction"] is None and cell["fluid"] is None and i not in member_cells
            evidence[str(mesh)] = {"cells": cells, "sprite": sprite, "faces": 6}
        offset += n*139
    assert offset == len(raw) and len(evidence) == len(required)
    return evidence


if __name__ == "__main__":
    main()
