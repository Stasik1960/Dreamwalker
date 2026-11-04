#!/usr/bin/env python3
"""Create screenshot-scoped ALT texture copies from the pinned model UVs.

Only pixels sampled by the listed model/material pairs are tuned. Every other
pixel, including alpha and unrelated atlas islands, is copied unchanged.
"""
from __future__ import annotations

import hashlib
import json
import math
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / "baseline" / "mod.jar"
ALT = ROOT / "src/main/resources/dw_city_visuals/alt/assets/bloodborne_dw"
OUT = ALT / "textures/block/city_altfix"
REPORT = ROOT / "validation/alt-fixes-scenes/materials.json"
REFERENCE = (118, 111, 97)  # measured neighbouring ALT stone from screenshot 09 audit
TARGETS = [
    {"screenshot": "05", "position": [206, 61, -961], "block": "bloodborne_dw:static_architecture_15", "part": 67,
     "model": "bloodborne_dw:remaining/source/minecraft/block/_wall_dec_out"},
    {"screenshot": "06", "position": [190, 60, -965], "block": "bloodborne_dw:static_architecture_0", "part": 55,
     "model": "bloodborne_dw:remaining/source/minecraft/block/_wall_dec_out"},
    {"screenshot": "08", "position": [88, 21, -856], "block": "bloodborne_dw:static_architecture_1", "part": 49,
     "model": "bloodborne_dw:remaining/source/minecraft/block/_wall_dec_top_1"},
    {"screenshot": "10", "position": [12, -5, -1032], "block": "bloodborne_dw:static_architecture_43", "part": 50,
     "model": "bloodborne_dw:remaining/source/minecraft/block/oak_stairs_out"},
    {"screenshot": "11", "position": [14, -6, -1032], "block": "bloodborne_dw:static_architecture_38", "part": 89,
     "model": "bloodborne_dw:remaining/source/minecraft/block/_wall_dec_bottom"},
]

CASES = [
    # The two distinct stone sprites used by the same corbel/frieze model.
    ("05-06", "_wall_dec_out", "#0", "spirelamp_001", "scene05_06_stone_a"),
    ("05-06", "_wall_dec_out", "#3", "spirelamp_009", "scene05_06_stone_b"),
    ("08", "_wall_dec_top_1", "#0", "spirelamp_0028", "scene08_triangle"),
    ("10", "oak_stairs_out", "#0", "spirelamp_0018", "scene10_masonry"),
    ("11", "_wall_dec_bottom", "#0", "spirelamp_002", "scene11_hanging"),
]


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def median(rgb: list[tuple[int, int, int, int]]) -> list[int]:
    if not rgb:
        return [0, 0, 0]
    return [sorted(px[c] for px in rgb)[len(rgb) // 2] for c in range(3)]


def model_path(name: str) -> str:
    return f"assets/bloodborne_dw/models/remaining/source/minecraft/block/{name}.json"


def main() -> None:
    report: dict[str, object] = {
        "schema": 1,
        "sourceJar": str(JAR.relative_to(ROOT)),
        "referenceRgb": list(REFERENCE),
        "uvConvention": "Minecraft model UV 0..16 scaled by texture width/16 and height/16",
        "targetCells": TARGETS,
        "referenceNote": "Target RGB is the audited nearby ALT stone reference from screenshot 09. Only model-used source UV regions are scaled; this is an authored ALT tone, not a render measurement.",
        "cases": [],
    }
    OUT.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(JAR) as jar:
        for screenshot, model_name, texture_ref, sprite, output_name in CASES:
            model_raw = jar.read(model_path(model_name))
            model = json.loads(model_raw)
            texture_id = model["textures"][texture_ref.removeprefix("#")]
            namespace, asset = texture_id.split(":", 1)
            assert namespace == "bloodborne_dw", texture_id
            source_rel = "textures/" + asset + ".png"
            source_path = ALT / source_rel
            source_bytes = source_path.read_bytes()
            original = Image.open(source_path).convert("RGBA")
            w, h = original.size
            mask = Image.new("1", (w, h), 0)
            draw = Image.new("1", (w, h), 0)
            from PIL import ImageDraw
            painter = ImageDraw.Draw(draw)
            used_uvs = []
            for element_index, element in enumerate(model.get("elements", [])):
                for face_name, face in element.get("faces", {}).items():
                    if face.get("texture") != texture_ref or "uv" not in face:
                        continue
                    u1, v1, u2, v2 = face["uv"]
                    x0 = max(0, min(w - 1, math.floor(min(u1, u2) * w / 16)))
                    x1 = max(0, min(w - 1, math.ceil(max(u1, u2) * w / 16) - 1))
                    y0 = max(0, min(h - 1, math.floor(min(v1, v2) * h / 16)))
                    y1 = max(0, min(h - 1, math.ceil(max(v1, v2) * h / 16) - 1))
                    if x1 >= x0 and y1 >= y0:
                        painter.rectangle((x0, y0, x1, y1), fill=1)
                        used_uvs.append({"element": element_index, "face": face_name,
                                         "uv": [u1, v1, u2, v2], "pixels": [x0, y0, x1 + 1, y1 + 1]})
            mask = draw
            src = original.load()
            mask_px = mask.load()
            eligible = [src[x, y] for y in range(h) for x in range(w)
                        if mask_px[x, y] and src[x, y][3] == 255]
            src_median = median(eligible)
            if any(v == 0 for v in src_median):
                raise ValueError(f"Unexpected zero median in {sprite}: {src_median}")
            scale = [REFERENCE[i] / src_median[i] for i in range(3)]
            changed = original.copy()
            dst = changed.load()
            clipped = 0
            for y in range(h):
                for x in range(w):
                    if not mask_px[x, y]:
                        continue
                    r, g, b, a = src[x, y]
                    if a == 0:
                        continue
                    raw = [r * scale[0], g * scale[1], b * scale[2]]
                    if any(c > 255 for c in raw):
                        clipped += 1
                    dst[x, y] = (min(255, round(raw[0])), min(255, round(raw[1])),
                                 min(255, round(raw[2])), a)
            target_rel = f"textures/block/city_altfix/{output_name}.png"
            target_path = ALT / target_rel
            target_path.parent.mkdir(parents=True, exist_ok=True)
            changed.save(target_path, format="PNG", optimize=False)
            output_bytes = target_path.read_bytes()
            changed_opaque = [changed.getpixel((x, y)) for y in range(h) for x in range(w)
                              if mask_px[x, y] and changed.getpixel((x, y))[3] == 255]
            alpha_equal = original.getchannel("A").tobytes() == changed.getchannel("A").tobytes()
            outside_equal = all(src[x, y] == dst[x, y] for y in range(h) for x in range(w)
                                if not mask_px[x, y])
            report["cases"].append({
                "screenshots": screenshot,
                "model": model_path(model_name),
                "modelSha256": sha(model_raw),
                "textureKey": texture_ref,
                "keptModelTextures": {key: value for key, value in model.get("textures", {}).items()
                                      if key != texture_ref.removeprefix("#")},
                "sourceSprite": texture_id,
                "sourceFile": str(source_path.relative_to(ROOT)),
                "sourceSha256": sha(source_bytes),
                "targetSprite": "bloodborne_dw:block/city_altfix/" + output_name,
                "targetFile": str(target_path.relative_to(ROOT)),
                "targetSha256": sha(output_bytes),
                "dimensions": [w, h],
                "sourceMedianOnModelUv": src_median,
                "targetMedianOnModelUv": median(changed_opaque),
                "targetRgb": list(REFERENCE),
                "channelScale": scale,
                "usedPixelCount": sum(1 for y in range(h) for x in range(w) if mask_px[x, y]),
                "opaqueUsedPixelCount": len(eligible),
                "clippedUsedPixelCount": clipped,
                "alphaEqual": alpha_equal,
                "outsideUvPixelsEqual": outside_equal,
                "uvs": used_uvs,
            })
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
