"""Offline logical mesh contact sheets; not a substitute for Minecraft QA."""
from __future__ import annotations

import argparse
import gzip
import json
from pathlib import Path

from render_modular_preview import draw_polys, Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "build/logical-preview")
    args = parser.parse_args()
    resources = ROOT / "src/main/resources/bloodborne_blocks/logical"
    definitions = json.loads((resources / "definitions.json").read_text(encoding="utf-8"))["blocks"]
    with gzip.open(resources / "meshes.json.gz", "rt", encoding="utf-8") as stream:
        meshes = json.load(stream)
    args.output.mkdir(parents=True, exist_ok=True)
    font = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 13)
    samples = []
    for definition in definitions:
        states = definition["models"]
        key = ",".join(f"{k}={v}" for k, v in sorted(definition["default"].items()))
        samples.append((definition["id"], key, states[key]))
        for face in definition["properties"].get("face", []):
            if face == definition["default"].get("face"):
                continue
            mounted = {**definition["default"], "face": face}
            key = ",".join(f"{k}={v}" for k, v in sorted(mounted.items()))
            samples.append((definition["id"], key, states[key]))
        if "open" in definition["properties"]:
            opened = {**definition["default"], "open": "true"}
            key = ",".join(f"{k}={v}" for k, v in sorted(opened.items()))
            samples.append((definition["id"], key, states[key]))
        if definition.get("behavior") == "connected":
            joined = {**definition["default"], "north": "true", "east": "true"}
            key = ",".join(f"{k}={v}" for k, v in sorted(joined.items()))
            samples.append((definition["id"], key, states[key]))
    for start in range(0, len(samples), 20):
        batch = samples[start:start + 20]
        sheet = Image.new("RGB", (1200, ((len(batch) + 4) // 5) * 276), (27, 30, 36))
        draw = ImageDraw.Draw(sheet)
        for index, (ident, key, mesh) in enumerate(batch):
            x, y = index % 5 * 240, index // 5 * 276
            sheet.paste(draw_polys(meshes[mesh]["polygons"], size=236), (x, y))
            draw.text((x + 3, y + 237), ident[:33], font=font, fill="white")
            draw.text((x + 3, y + 254), key[:34], font=font, fill="#aab4c0")
        path = args.output / f"objects-{start // 20 + 1:02}.png"
        sheet.save(path)
        print(path)


if __name__ == "__main__":
    main()
