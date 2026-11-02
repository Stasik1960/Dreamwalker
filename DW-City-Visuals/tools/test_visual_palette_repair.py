#!/usr/bin/env python3
"""Static asset-contract checks for the November city palette repair.

This intentionally does not launch Minecraft. The current source camera plan
is checked separately from runtime captures and their visual review evidence.
"""
from __future__ import annotations

import argparse
import json
import math
import re
import sys
from io import BytesIO
import zipfile
from pathlib import Path


WORK = Path(__file__).resolve().parents[1]
DEFAULT_JAR = WORK / "baseline/mod.jar"
CANONICAL_INPUTS = WORK / "tools" / "v12-palette-inputs.zip"
REPORT = WORK / "validation" / "palette-v7-regression.json"


class Audit:
    def __init__(self, jar: Path):
        self.jar_path = jar
        self.jar = zipfile.ZipFile(jar)
        self.checks: list[dict[str, object]] = []

    def check(self, name: str, condition: bool, detail: str) -> None:
        self.checks.append({"name": name, "status": "PASS" if condition else "FAIL", "detail": detail})

    def require(self, name: str, condition: bool, detail: str) -> None:
        self.check(name, condition, detail)
        if not condition:
            raise AssertionError(f"{name}: {detail}")

    def pending(self, name: str, detail: str) -> None:
        self.checks.append({"name": name, "status": "PENDING", "detail": detail})

    def jar_bytes(self, name: str) -> bytes:
        try:
            return self.jar.read(name)
        except KeyError as exc:
            raise AssertionError(f"Pinned base JAR lacks {name}") from exc

    def jar_json(self, name: str) -> dict:
        return json.loads(self.jar_bytes(name))

    def source_json(self, path: Path) -> dict:
        return json.loads(path.read_text(encoding="utf-8"))


def model_texture(model: dict) -> str | None:
    textures = model.get("textures", {})
    for key in ("0", "particle"):
        value = textures.get(key)
        if isinstance(value, str) and not value.startswith("#"):
            return value
    for element in model.get("elements", []):
        for face in element.get("faces", {}).values():
            value = face.get("texture")
            if isinstance(value, str) and not value.startswith("#"):
                return value
    return None


def sprite_png_path(sprite: str) -> str:
    namespace, path = sprite.split(":", 1)
    if ".." in Path(path).parts:
        raise ValueError(f"unsafe sprite id: {sprite}")
    return f"assets/{namespace}/textures/{path}.png"


def transformed_pixel(pixel: tuple[int, int, int, int], tone: dict | None, palettes: dict) -> tuple[int, int, int, int]:
    if tone is None or pixel[3] == 0:
        return pixel
    scale, offset = tone.get("scale"), tone.get("offset")
    if scale is None:
        palette = palettes[tone["palette"]]
        scale, offset = palette["scale"], palette["offset"]
        luminance = False
    else:
        luminance = tone.get("luminance", False)
    red, green, blue = pixel[:3]
    if luminance:
        luminance_value = (77 * red + 150 * green + 29 * blue + 128) >> 8
        red = green = blue = luminance_value
    channels = [red, green, blue]
    converted = [max(0, min(255, math.floor(channels[i] * scale[i] + offset[i] + 0.5))) for i in range(3)]
    return converted[0], converted[1], converted[2], pixel[3]


def verify_baked_png(source_bytes: bytes, output_path: Path, rule: dict, mode: str, palettes: dict, family: bool,
                     superseded_regions: tuple = ()) -> dict:
    from PIL import Image

    source = Image.open(BytesIO(source_bytes)).convert("RGBA")
    output = Image.open(output_path).convert("RGBA")
    if source.size != output.size:
        return {"ok": False, "reason": f"dimensions source={source.size} output={output.size}"}
    fallback = rule.get(mode) if family else ({"palette": rule["palette"]} if "palette" in rule else None)
    if family and fallback is None:
        fallback = None
    regions = []
    for region in rule.get("regions", []):
        tone = region.get(mode) if family else ({"palette": region["palette"]} if "palette" in region else None)
        regions.append((region["pixels"], tone))
    changed = 0
    alpha_changed = 0
    outside_changed = 0
    changed_in_region = 0
    superseded_pixels = 0
    for y in range(source.height):
        for x in range(source.width):
            before = source.getpixel((x, y))
            after = output.getpixel((x, y))
            selected = fallback
            in_region = False
            for box, tone in regions:
                if box[0] <= x < box[2] and box[1] <= y < box[3]:
                    selected = tone
                    in_region = True
                    break
            expected = transformed_pixel(before, selected, palettes)
            superseded = any(x0 <= x < x1 and y0 <= y < y1 for x0,y0,x1,y1 in superseded_regions)
            if after != expected and not superseded:
                return {"ok": False, "reason": f"pixel mismatch at ({x},{y}) expected={expected} actual={after}"}
            if superseded:
                # v7 facade audit owns colour comparison only inside this
                # explicit UV island; this audit still checks every alpha.
                superseded_pixels += 1
            if after != before:
                changed += 1
                if after[3] != before[3]:
                    alpha_changed += 1
                if fallback is None and not in_region and not superseded:
                    outside_changed += 1
                if in_region:
                    changed_in_region += 1
    return {"ok": alpha_changed == 0 and outside_changed == 0, "size": list(source.size), "changedPixels": changed,
            "alphaChanged": alpha_changed, "outsideMaskedRegionChanged": outside_changed,
            "maskedRegionChangedPixels": changed_in_region,
            "supersededUVPixelsVerifiedByFacadeAudit": superseded_pixels}


def inspect_materialized_profiles(a: "Audit", resources: Path, family: dict, baked_v12: dict,
                                  family_inputs_path: Path, v12_inputs_path: Path) -> None:
    """Validate indexed profile files and generated pixels; missing bake outputs stay PENDING."""
    index_path = resources / "assets/bloodborne_dw/visuals/resource-index.json"
    index = a.source_json(index_path)
    actual_sets: dict[str, set[str]] = {}
    for mode in ("original", "alt"):
        profile = resources / "dw_city_visuals" / mode
        actual = {path.relative_to(profile).as_posix() for path in profile.rglob("*")
                  if path.is_file() and path.relative_to(profile).as_posix().startswith("assets/")}
        indexed = set(index.get(mode, []))
        actual_sets[mode] = actual
        a.require(f"{mode}-resource-index-complete", actual == indexed,
                  f"active profile assets={len(actual)} indexed={len(indexed)} unindexed={sorted(actual-indexed)[:8]} stale={sorted(indexed-actual)[:8]}")

    expected_overrides = {
        "alt": [
            "assets/bloodborne_dw/models/interactions/shutter_wall_0.json",
            "assets/bloodborne_dw/models/interactions/shutter_wall_1.json",
        ],
        "original": [
        ],
    }
    for mode, paths in expected_overrides.items():
        for relative in paths:
            a.require(f"{mode}-indexed-override-{Path(relative).name}", relative in index.get(mode, []) and (resources / "dw_city_visuals" / mode / relative).is_file(),
                      f"required model override is physical and indexed: {relative}")

    missing: list[str] = []
    checked: list[dict] = []
    try:
        family_zip = zipfile.ZipFile(family_inputs_path)
        v12_zip = zipfile.ZipFile(v12_inputs_path)
    except FileNotFoundError as exc:
        a.pending("materialized-palette-pixels", f"canonical palette input archive is unavailable: {exc}")
        return
    try:
        # Family rules can target both profiles and use a separate immutable
        # source sprite. V12 rules are ALT-only and preserve all pixels outside masks.
        jobs = []
        for rule in family.get("sprites", []):
            # v7 deliberately supersedes these v6 intermediate bakes with the
            # same real masonry material used by all source shutter atlases.
            # test_facade_v7 verifies their UV coverage and protected pixels.
            if rule["sprite"] in {"bloodborne_dw:block/autumn_c3/window_surround", "bloodborne_dw:block/autumn_c3/accent/window_surround"}:
                continue
            for mode in ("original", "alt"):
                fallback = rule.get(mode)
                active = fallback is not None or any(region.get(mode) is not None for region in rule.get("regions", []))
                if active:
                    jobs.append(("family", mode, rule, rule["sourceSprite"], {}))
        for rule in baked_v12.get("sprites", []):
            jobs.append(("v12", "alt", rule, rule["sprite"], baked_v12.get("palettes", {})))
        for kind, mode, rule, source_sprite, palettes in jobs:
            target_sprite = rule["sprite"]
            source_path = sprite_png_path(source_sprite)
            target_path = resources / "dw_city_visuals" / mode / sprite_png_path(target_sprite)
            if not target_path.is_file():
                missing.append(f"{mode}/{target_path.relative_to(resources / 'dw_city_visuals' / mode).as_posix()}")
                continue
            if f"{mode}/{source_path}" in family_zip.namelist() if kind == "family" else source_path in v12_zip.namelist():
                source_bytes = family_zip.read(f"{mode}/{source_path}") if kind == "family" else v12_zip.read(source_path)
            else:
                source_bytes = a.jar_bytes(source_path)
            # The corner masonry supersedes .6's old grey tint in exactly the
            # model's shared brick island. Facade-v7 independently checks tone,
            # alpha and the entire protected outside for Original and ALT.
            superseded = ((96, 64, 126, 96),) if target_sprite == "bloodborne_dw:block/remaining/source/minecraft/block/addon/spirelamp_0018" else ()
            result = verify_baked_png(source_bytes, target_path, rule, mode, palettes, kind == "family", superseded)
            a.require(f"baked-pixels-{mode}-{target_sprite}", result.get("ok", False), str(result))
            checked.append({"profile": mode, "sprite": target_sprite, **result})
    finally:
        family_zip.close()
        v12_zip.close()
    if missing:
        a.pending("materialized-palette-pixels", f"offline bake not complete yet: {len(missing)} of {len(jobs)} active profile PNGs missing; sample={missing[:6]}")
    else:
        a.require("materialized-palette-pixels", len(checked) == len(jobs), f"validated {len(checked)} generated profile PNGs; explicit v7 UV supersessions are independently covered by facade-v7")


def run(jar: Path) -> dict:
    a = Audit(jar)
    resources = WORK / "src" / "main" / "resources"
    assets = resources / "assets"
    family = a.source_json(WORK / "tools" / "family-baked-palettes.json")
    baked_v12 = a.source_json(WORK / "tools" / "v12-baked-palettes.json")
    entity_v12 = a.source_json(assets / "bloodborne_dw" / "visuals" / "v12-palettes.json")
    facade = a.source_json(assets / "bloodborne_dw" / "visuals" / "facades.json")
    rules = {row["sprite"]: row for row in family.get("sprites", [])}
    palette_defs = baked_v12.get("palettes", {})
    v12_rules = {row["sprite"]: row for row in baked_v12.get("sprites", [])}
    entity_rules = {row["sprite"]: row for row in entity_v12.get("sprites", [])}
    base = "assets/bloodborne_dw/models/remaining/source/minecraft/block/addon/"

    # The problematic spire and the good cyan family must resolve to one
    # palette rule across all real compiled model variants, rather than a
    # handcrafted, hard-coded count that could miss an additional variant.
    oak_path = base + "oak_button_side_on.json"
    oak = a.jar_json(oak_path)
    oak_texture = model_texture(oak)
    blue_rule = rules.get(oak_texture or "", {}).get("alt", {})
    all_cyan = sorted(n for n in a.jar.namelist() if re.fullmatch(re.escape(base) + r"cyan_terracotta/cyan_terracotta_[1-8]\.json", n))
    cyan_textures = {model_texture(a.jar_json(n)) for n in all_cyan}
    blue_ok = bool(blue_rule.get("luminance")) and len(blue_rule.get("scale", [])) == 3 and blue_rule["scale"][2] > blue_rule["scale"][1] > blue_rule["scale"][0]
    a.require("spire-blue-family", oak_texture.endswith("spirelamp_0013") and blue_ok and len(all_cyan) == 8 and len(cyan_textures) == 1 and next(iter(cyan_textures)).endswith("spirelamp_0115"),
              f"bad spire material={oak_texture}; blue bias={blue_rule.get('scale')}; cyan reference variants={len(all_cyan)}, textures={sorted(str(x) for x in cyan_textures)}")

    # Prototype walls are a distinct model/material family. Require each of its
    # eight authored atlas sprites to resolve to the exact same ALT rule.
    wall_ids = [f"bloodborne_dw:wall/source/minecraft/block/polished_deepslate_wall_{i}" for i in range(8)]
    wall_rules = [rules.get(s) for s in wall_ids]
    wall_palettes = [row.get("alt") if row else None for row in wall_rules]
    a.require("prototype-wall-eight-materials", all(wall_rules) and all(value is not None for value in wall_palettes) and len({json.dumps(value, sort_keys=True) for value in wall_palettes}) == 1,
              f"eight source backing sprites map to one ALT material: {wall_palettes}")

    # Bright fixture textures are baked once from the same immutable sources
    # for both profiles; no sprite-color runtime mixin should remain.
    bright_rules = []
    for suffix in ("0010", "0028", "0045"):
        sprite = f"bloodborne_dw:block/remaining/source/minecraft/block/addon/spirelamp_{suffix}"
        rule = rules.get(sprite, {})
        alt = rule.get("alt", {})
        bright_rules.append(rule)
        a.require(f"bright-spire-{suffix}-single-decode-rule", bool(rule.get("original")) and bool(alt) and len(alt.get("scale", [])) == 3,
                  f"original and ALT modes each have exactly one sprite rule; ALT={alt}")
    mixin_config = a.source_json(resources / "dw-v12-palettes.mixins.json")
    sprite_mixin = WORK / "src/client/java/dev/dreamwalker/bloodbornedw/visual/mixin/V12SpritePaletteMixin.java"
    a.require("sprite-tone-runtime-hook-removed", not sprite_mixin.exists() and "V12SpritePaletteMixin" not in mixin_config.get("client", []),
              "color families are baked to profile PNGs; no per-sprite runtime color hook is registered")
    original_bright = [row.get("original") for row in bright_rules]
    a.require("original-bright-family-unified", all(value is not None for value in original_bright) and len({json.dumps(value, sort_keys=True) for value in original_bright}) == 1,
              f"0010/0028/0045 use one original-mode tone rule: {original_bright}")

    # The pink window backing is a same-sized atlas alias whose only pink area
    # is the horizontal wall UV. Top/bottom UVs are intentionally preserved.
    atlas = json.loads((resources / "dw_city_visuals/alt/assets/minecraft/atlases/blocks.json").read_text(encoding="utf-8"))
    singles = [row for row in atlas.get("sources", []) if row.get("type") == "minecraft:single"]
    aliases = {row.get("sprite"): row.get("resource") for row in singles if row.get("sprite")}
    pink_alias = "bloodborne_dw:block/autumn_c3/accent/window_wall_0010"
    source_0010 = "bloodborne_dw:block/remaining/source/minecraft/block/addon/spirelamp_0010"
    pink_rule = rules.get(pink_alias, {})
    pixel_regions = [row.get("pixels") for row in pink_rule.get("regions", [])]
    rose_cover = any(len(lo) == 3 and lo[0] <= 178 <= row["max"][0] and lo[1] <= 92 <= row["max"][1] and lo[2] <= -814 <= row["max"][2] for row in facade.get("regions", []) if (lo := row.get("min", [])))
    a.require("pink-window-alias-atlas", aliases.get(pink_alias) == pink_alias and rules.get(pink_alias, {}).get("sourceSprite") == source_0010,
              f"ALT atlas loads the baked alias PNG; family rule derives it from source {rules.get(pink_alias, {}).get('sourceSprite')}")
    a.require("pink-window-region-mask", pixel_regions == [[51, 48, 71, 64]] and rose_cover,
              f"side-only pixel mask={pixel_regions}; facade rose region covers (178,92,-814)={rose_cover}")
    for name in ("shutter_wall_0.json", "shutter_wall_1.json"):
        rel = Path("dw_city_visuals/alt/assets/bloodborne_dw/models/interactions") / name
        target = resources / rel
        source = a.jar_json("assets/bloodborne_dw/models/interactions/" + name)
        alt_model = a.source_json(target)
        source_texture = source.get("textures", {}).get("0")
        alt_texture = alt_model.get("textures", {}).get("0")
        geometry_unchanged = source.get("elements") == alt_model.get("elements")
        roof_shared = "roof_1" in str(source_texture) and alt_texture == "bloodborne_dw:block/autumn_c3/window_surround"
        a.require(f"shutter-wall-dedicated-backing-{name}", geometry_unchanged and roof_shared and alt_model.get("textures", {}).get("particle") == alt_texture,
                  f"geometry unchanged={geometry_unchanged}; shared roof1 source={source_texture}; ALT backing={alt_texture}")
    surround_aliases = {"bloodborne_dw:block/autumn_c3/window_surround": "bloodborne_dw:block/remaining/source/minecraft/block/addon/roof_1", "bloodborne_dw:block/autumn_c3/accent/window_surround": "bloodborne_dw:block/remaining/source/minecraft/block/addon/roof_1"}
    a.require("window-backing-aliases-indexed", all(aliases.get(alias) == alias and rules.get(alias, {}).get("sourceSprite") == source for alias, source in surround_aliases.items()),
              "dedicated backing aliases remain physical roof_1-derived PNGs; v7 also repairs the shared wall islands used by all other shutter representations")
    original_atlas = json.loads((resources / "dw_city_visuals/original/assets/minecraft/atlases/blocks.json").read_text(encoding="utf-8"))
    original_roof = [row for row in original_atlas.get("sources", []) if row.get("resource") == "bloodborne_dw:block/remaining/source/minecraft/block/addon/roof_1"]
    a.require("shared-roof-resource-not-repointed", len(original_roof) == 1 and "sprite" not in original_roof[0],
              "original atlas retains ordinary roof_1 without an alias override")
    from test_facade_v7 import run as verify_facades
    for row in verify_facades()["checks"]:
        a.require("v7-"+row["name"], row["status"]=="PASS", str(row["detail"]))

    # These scene gates are RP entities, not the native source blocks behind them.
    # Match their real atlas islands and preserve all alpha holes.
    rp_gates = {
        "small_gate": [[0, 0, 64, 96], [130, 0, 159, 86], [131, 96, 149, 109]],
        "main_gate": [[256, 256, 311, 379]],
    }
    for name, body_boxes in rp_gates.items():
        sprite = "bloodborne_rp:entities/decorations/" + name
        rule = rules.get(sprite, {})
        actual_boxes = [region["pixels"] for region in rule.get("regions", [])]
        a.require("actual-rp-gate-palette-" + name, rule.get("sourceSprite") == sprite and rule.get("alt", {}).get("luminance") is True and actual_boxes == body_boxes,
                  f"real entity texture; bronze leaf islands={actual_boxes}; warm frame base registered")
        geo = a.jar_json("assets/bloodborne_rp/geo/" + name + ".geo.json")
        bones = geo["minecraft:geometry"][0]["bones"]
        leaves = [bone for bone in bones if bone["name"] in (("left", "right") if name == "small_gate" else ("left2", "right2"))]
        faces = [cube.get("uv", {}) for bone in leaves for cube in bone.get("cubes", [])]
        a.require("rp-gate-leaves-two-sided-" + name, len(leaves) == 2 and all("north" in face and "south" in face for face in faces[:1]),
                  "actual entity leaf geometry retains front/back faces and original transforms")

    # Native retained fragments and editable wall primitives must use identical
    # baked image data, otherwise legacy bits form pale islands in the facade.
    for number in (1, 2, 3, 4, 5, 7):
        source = f"bloodborne_dw:wall/source/minecraft/block/polished_deepslate_wall_{number}"
        legacy = f"bloodborne_dw:block/remaining/source/minecraft/block/polished_deepslate_wall_{number}"
        rule = rules.get(legacy, {})
        matching = rule.get("sourceSprite") == source and rule.get("alt") == rules[source].get("alt")
        left = resources / "dw_city_visuals/alt" / sprite_png_path(source)
        right = resources / "dw_city_visuals/alt" / sprite_png_path(legacy)
        if left.exists() and right.exists():
            matching = matching and left.read_bytes() == right.read_bytes()
        else:
            a.pending("legacy-wall-bake-" + str(number), "corrected wall copy awaits materialization")
        a.require("native-editable-wall-material-parity-" + str(number), matching,
                  "native retained and editable primitive share the exact same authored material and tone")

    # Real main_gate leaf cubes lacked three perimeter faces per leaf, and
    # their old side UV included transparent padding. Check the full six-face
    # model and keep every other bone/transform/front-back UV byte-semantic equal.
    import copy
    from PIL import Image
    main_original = a.jar_json("assets/bloodborne_rp/geo/main_gate.geo.json")
    main_expected = copy.deepcopy(main_original)
    for bone in main_expected["minecraft:geometry"][0]["bones"]:
        if bone["name"] not in ("left2", "right2"):
            continue
        uv = bone["cubes"][0]["uv"]
        uv["east"] = {"uv": [309,256], "uv_size": [1,122]}
        uv["west"] = {"uv": [309,256], "uv_size": [1,122]}
        uv["up"] = {"uv": [309,256], "uv_size": [1,1]}
        uv["down"] = {"uv": [309,256], "uv_size": [1,1]}
    gate_image = Image.open(BytesIO(a.jar_bytes("assets/bloodborne_rp/textures/entities/decorations/main_gate.png"))).convert("RGBA")
    edge_opaque = all(gate_image.getpixel((309,y))[3] == 255 for y in range(256,378))
    for mode in ("original", "alt"):
        model = a.source_json(resources / "dw_city_visuals" / mode / "assets/bloodborne_rp/geo/main_gate.geo.json")
        a.require("main-gate-complete-opaque-edges-" + mode, model == main_expected and edge_opaque,
                  "six added perimeter quads; two side UVs exclude transparent padding; all geometry transforms/front-back faces preserved")

    backing_id = "bloodborne_dw:block/remaining/source/minecraft/block/addon/window_6"
    backing_rule = rules.get(backing_id, {})
    backing_model = a.jar_json("assets/bloodborne_dw/models/remaining/source/minecraft/block/stairs/waxed_oxidized_cut_copper_stairs_inner_left_top2.json")
    has_backing_face = any(face.get("uv") == [10,8,16,10] for element in backing_model.get("elements", []) for face in element.get("faces", {}).values())
    a.require("chestnut-window-backing-only", "alt" not in backing_rule and has_backing_face and [r["pixels"] for r in backing_rule.get("regions", [])] == [[80,64,128,80]] and "addon/window_6" in str(backing_model.get("textures", {})),
              "the remaining beige insert is the exact window_6 brick UV; glass, wood and stone frame outside it are protected")

    # The visible door and grille are entity assets. Preserve their wood and
    # glass islands rather than tinting unrelated F3-hit blocks behind them.
    door_entity = rules.get("bloodborne_rp:entities/decorations/door", {})
    window_entity = rules.get("bloodborne_rp:entities/decorations/spirelamp_0094", {})
    protected_panes = {(x,y) for x0,y0,x1,y1 in ([378,240,409,292],[409,240,440,291])
                       for y in range(y0,y1) for x in range(x0,x1)}
    window_masks = {(x,y) for row in window_entity.get("regions", []) for x0,y0,x1,y1 in [row["pixels"]]
                    for y in range(y0,y1) for x in range(x0,x1)}
    a.require("rp-door-frame-only", "alt" not in door_entity and len(door_entity.get("regions", [])) > 0,
              "static stone frame is masked; full-atlas default leaves moving wood and inset panes unchanged")
    a.require("rp-window-pane-protection", "alt" not in window_entity and len(window_masks) > 0 and not (window_masks & protected_panes),
              f"frame/ironwork masks={len(window_masks)} texels; protected pane overlap={len(window_masks & protected_panes)}")

    # Region-masked tree base must leave the rest of the billboard atlas alone.
    tree_id = "bloodborne_dw:block/remaining/source/minecraft/block/addon/tree_1"
    tree = v12_rules.get(tree_id, {})
    tree_regions = [row.get("pixels") for row in tree.get("regions", [])]
    tree_palette_names = [row.get("palette") for row in tree.get("regions", [])]
    base_tex = a.jar_bytes("assets/bloodborne_dw/textures/block/remaining/source/minecraft/block/addon/tree_1.png")
    dims = __import__("struct").unpack(">II", base_tex[16:24]) if base_tex[:8] == b"\x89PNG\r\n\x1a\n" else (0, 0)
    import math
    model = a.jar_json("assets/bloodborne_dw/models/remaining/source/minecraft/block/white_wool.json")
    expected_stone = set()
    for element in model["elements"][2:]:
        for face in element.get("faces", {}).values():
            u0, v0, u1, v1 = face["uv"]
            expected_stone.update((x, y) for y in range(math.floor(min(v0,v1)*16), math.ceil(max(v0,v1)*16))
                                  for x in range(math.floor(min(u0,u1)*16), math.ceil(max(u0,u1)*16)))
    mask = {(x,y) for x0,y0,x1,y1 in tree_regions for y in range(y0,y1) for x in range(x0,x1)}
    trunk_overlap = any(127 <= x < 175 and 208 <= y < 256 for x,y in mask)
    a.require("billboard-tree-base-only-mask", tree.get("palette", "preserve") == "preserve" and mask == expected_stone and not trunk_overlap and set(tree_palette_names) == {"alt_warm_stone"} and dims == (256,256),
              f"all stone UVs covered={mask == expected_stone}; masked texels={len(mask)}; trunk overlap={trunk_overlap}; atlas dimensions={dims}")

    # The user's editor project replaces ALT only; keep Original bound to the
    # pinned baseline rather than the former .5 rollback equality.
    originals = resources / "dw_city_visuals/original/assets/bloodborne_rp/geo"
    alts = resources / "dw_city_visuals/alt/assets/bloodborne_rp/geo"
    project = json.loads((WORK / "inputs/trees/DreamWalker-trees.dwtree.json").read_text(encoding="utf-8-sig"))
    tree_equal = all(json.loads((alts / Path(m["assetPath"]).name).read_text(encoding="utf-8")) == m["geometry"] for m in project["models"])
    with zipfile.ZipFile(WORK / "baseline/mod.jar") as pinned:
        original_equal = all((originals / f"tree_{i}.geo.json").read_bytes() == pinned.read(f"assets/bloodborne_rp/geo/tree_{i}.geo.json") for i in (1, 2, 3))
    a.require("user-3d-tree-project-imported-original-preserved", tree_equal and original_equal,
              "ALT tree_1/2/3 geometries equal the uploaded project; Original stays byte-identical to the pinned baseline")

    for sprite, palette in (("bloodborne_rp:textures/entities/decorations/statue.png", "alt_statue_stone"),):
        rule = entity_rules.get(sprite, {})
        entity_palettes = entity_v12.get("palettes", {})
        a.require("statue-entity-palette-contract", rule.get("palette") == palette and palette in entity_palettes,
                  f"ResourceTexture decode rule={rule.get('palette')}; expected registered {palette}")

    texture_hook = (WORK / "src/client/java/dev/dreamwalker/bloodbornedw/visual/mixin/V12ResourceTexturePaletteMixin.java").read_text(encoding="utf-8")
    palette_code = (WORK / "src/client/java/dev/dreamwalker/bloodbornedw/visual/V12Palettes.java").read_text(encoding="utf-8")
    texture_accessor = (WORK / "src/client/java/dev/dreamwalker/bloodbornedw/visual/mixin/V12TextureDataAccessor.java").read_text(encoding="utf-8")
    v12_mixins = a.source_json(resources / "dw-v12-palettes.mixins.json")
    a.require("statue-hook-is-texture-scoped-and-mode-aware", "textures/entities/decorations/statue.png" in texture_hook and "bloodborne_rp" in texture_hook and "V12TextureDataAccessor" in texture_hook and "dw$getImage()" in texture_hook and "V12Palettes.apply(location, image)" in texture_hook and "loadingProfileIsAlt()" in palette_code and "java.lang.reflect" not in texture_hook,
              "exact RP entity hook uses the remapped image accessor and decoder applies only for ALT")
    a.require("statue-image-accessor-registered", "net.minecraft.client.texture.ResourceTexture$TextureData" in texture_accessor and '@Accessor("image")' in texture_accessor and "V12TextureDataAccessor" in v12_mixins.get("client", []),
              "TextureData image accessor is remapped and registered in the client mixin config")

    inspect_materialized_profiles(a, resources, family, baked_v12, CANONICAL_INPUTS, CANONICAL_INPUTS)

    # The ignored .6 artifacts/qa-plan.json is historical and cannot be a
    # prerequisite for checking the published .7 sources. Inspect the current
    # finite source plan separately from real runtime captures and their logs.
    driver_path = WORK / "src/visualqa/java/dev/dreamwalker/bloodbornedw/visual/VisualRevisionQa.java"
    driver = driver_path.read_text(encoding="utf-8")
    views_source = driver.split("double[][] VIEWS =", 1)[1].split("};", 1)[0]
    views = [tuple(float(v) for v in row.split(",")) for row in re.findall(r"\{\s*([-+\d.,\s]+)\s*\}", views_source)]
    screenshots = [(262.312,50.18922,-910.191), (221.217,46.06987,-895.841),
                   (68.873,104.9155,-788.288), (125.434,60.07117,-858.094),
                   (120.85,47,-950.338), (177.089,66.99565,-933.721),
                   (278.429,44.87609,-846.249), (194.524,60.05132,-971.282),
                   (293.234,61.5177,-979.893), (249.608,46.24593,-839.298)]
    a.require("current-source-camera-plan-coverage", len(views) == 15 and all(len(v) == 5 and all(math.isfinite(c) for c in v) for v in views)
              and all(all(abs(views[i][c]-expected[c]) < .00001 for c in range(3)) for i,expected in enumerate(screenshots)),
              "Static plan has the ten supplied screenshot positions plus three 3D-tree positions. This does not certify actual capture, terrain readiness or visual correctness.")

    result = {
        "status": "FAIL" if any(row["status"] == "FAIL" for row in a.checks) else ("PENDING" if any(row["status"] == "PENDING" for row in a.checks) else "PASS"),
        "kind": "static-source-and-pinned-base-asset-contract",
        "baseJar": str(jar),
        "checks": a.checks,
        "limitations": [
            "This does not prove in-game colors, texture reload mode selection, or gate visibility at runtime.",
            "The separate runtime pass uses the current 15-camera driver; completion, terrain readiness and visual review require actual capture evidence."
        ]
    }
    a.jar.close()
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, default=DEFAULT_JAR, help="Pinned v22 source JAR to resolve original models and texture dimensions")
    parser.add_argument("--report", type=Path, default=REPORT)
    args = parser.parse_args()
    try:
        report = run(args.jar)
    except Exception as error:
        report = {"status": "FAIL", "kind": "static-source-and-pinned-base-asset-contract", "baseJar": str(args.jar), "error": str(error)}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    for row in report.get("checks", []):
        print(f"{row['status']}: {row['name']} | {row['detail']}")
    print(f"{report['status']}: report={args.report}")
    return 0 if report["status"] == "PASS" else (2 if report["status"] == "PENDING" else 1)


if __name__ == "__main__":
    sys.exit(main())
