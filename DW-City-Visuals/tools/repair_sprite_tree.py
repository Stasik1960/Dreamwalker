#!/usr/bin/env python3
"""Match the second billboard's old dark bark to the approved first billboard.

Leaves get a small brightness lift. Alpha, geometry and the architectural
islands in the shared atlas remain intact. Frozen .6 inputs make this idempotent.
"""
from __future__ import annotations
import argparse
import hashlib
import os
import tempfile
from io import BytesIO
import json
import math
from pathlib import Path
import zipfile
from PIL import Image

WORK = Path(__file__).resolve().parents[1]
SPRITE_PATH = 'assets/bloodborne_dw/textures/block/remaining/source/minecraft/block/addon/'
ALT = WORK / 'src/main/resources/dw_city_visuals/alt'
OLD_BARK = [(57, 54, 51), (73, 68, 61), (84, 78, 69), (90, 84, 76), (100, 94, 85), (109, 103, 95)]
MATCHED_BARK = [(89, 79, 69), (106, 94, 80), (117, 104, 88), (123, 110, 95), (132, 120, 105), (140, 129, 114)]
BARK = dict(zip(OLD_BARK, MATCHED_BARK))


def leaf_pixel(name: str, x: int, y: int, pixel: tuple) -> bool:
    r, g, b, a = pixel
    in_sprite = (x >= 52 and y >= 60) if name == 'tree_1.png' else (x < 80 and y < 50)
    # Bark and stone are neutral. The authored ochre leaves have distinct chroma.
    return in_sprite and a > 0 and r > g * 1.15 and g > b * 1.35


def repair(name: str, source: Image.Image) -> tuple[Image.Image, dict]:
    output = source.copy()
    counts = {'leafTexels': 0, 'barkTexels': 0, 'twigContourTexels': 0}
    for y in range(source.height):
        for x in range(source.width):
            px = source.getpixel((x, y))
            if leaf_pixel(name, x, y, px):
                output.putpixel((x, y), tuple(min(255, math.floor(v * 1.10 + 4.5)) for v in px[:3]) + (px[3],))
                counts['leafTexels'] += 1
            elif name == 'tree_2.png' and px[3] > 0 and px[:3] in BARK and (x < 145 and y >= 63 or x < 140 and y < 50):
                output.putpixel((x, y), BARK[px[:3]] + (px[3],))
                counts['barkTexels'] += 1
    if name == 'tree_2.png':
        # The screenshot's small twig is a separate 48x48 UV island used by
        # birch_log_1 (source_original_tree variant 5), not the large tree below.
        # It includes donor contour colours outside the six old bark swatches.
        # Lift only neutral dark contour pixels to the main bark's shadow level;
        # keep saturated ochre leaf pigments and the exact alpha silhouette.
        for y in range(48):
            for x in range(48):
                px = output.getpixel((x, y))
                luminance = (77*px[0] + 150*px[1] + 29*px[2] + 128) >> 8
                if px[3] and luminance < 80 and max(px[:3])-min(px[:3]) < 48:
                    delta = 80-luminance
                    output.putpixel((x,y), tuple(v+delta for v in px[:3]) + (px[3],))
                    counts['twigContourTexels'] += 1
    counts['alphaUnchanged'] = source.getchannel('A').tobytes() == output.getchannel('A').tobytes()
    if not counts['alphaUnchanged'] or counts['leafTexels'] == 0:
        raise AssertionError('Billboard repair violated alpha/foliage contract')
    return output, counts


def png(image: Image.Image) -> bytes:
    target = BytesIO()
    image.save(target, format='PNG', optimize=False)
    return target.getvalue()


def stone_palette(image: Image.Image) -> Image.Image:
    """Preview the unchanged existing base mask; Gradle re-bakes it independently."""
    data = json.loads((WORK / 'tools/v12-baked-palettes.json').read_text(encoding='utf-8'))
    rule = next(row for row in data['sprites'] if row['sprite'] == 'bloodborne_dw:block/remaining/source/minecraft/block/addon/tree_1')
    output = image.copy()
    for row in rule['regions']:
        palette = data['palettes'][row['palette']]
        x0, y0, x1, y1 = row['pixels']
        for y in range(y0, y1):
            for x in range(x0, x1):
                px = image.getpixel((x, y))
                if px[3]:
                    rgb = tuple(max(0, min(255, math.floor(px[c] * palette['scale'][c] + palette['offset'][c] + .5))) for c in range(3))
                    output.putpixel((x, y), rgb + (px[3],))
    return output


def update_palette_input(tree: Image.Image) -> None:
    inputs = WORK / 'tools/v12-palette-inputs.zip'
    with zipfile.ZipFile(inputs) as archive:
        entries = [(entry, archive.read(entry)) for entry in archive.infolist()]
    tree_path = SPRITE_PATH + 'tree_1.png'
    if sum(entry.filename == tree_path for entry, _ in entries) != 1:
        raise AssertionError('Canonical palette input tree missing/duplicated')
    tree_png = png(tree)
    if next(raw for entry, raw in entries if entry.filename == tree_path) == tree_png:
        return
    result = BytesIO()
    with zipfile.ZipFile(result, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for entry, raw in entries:
            archive.writestr(entry, tree_png if entry.filename == tree_path else raw)
    # Publish the complete archive only after it has been written successfully.
    temporary_path = None
    try:
        with tempfile.NamedTemporaryFile(dir=inputs.parent, prefix='v12-palette-', suffix='.tmp', delete=False) as temporary:
            temporary_path = Path(temporary.name)
            temporary.write(result.getvalue())
        os.replace(temporary_path, inputs)
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive-only', action='store_true')
    args = parser.parse_args()
    report = {'status': 'PASS', 'sprites': {}}
    with zipfile.ZipFile(WORK / 'tools/tree-sprite-inputs.zip') as frozen:
        images = {}
        names = ('tree_1.png',) if args.archive_only else ('tree_1.png', 'tree_2.png')
        for name in names:
            raw = frozen.read(name)
            source = Image.open(BytesIO(raw)).convert('RGBA')
            images[name], receipt = repair(name, source)
            receipt['sourceSha256'] = hashlib.sha256(raw).hexdigest()
            receipt['sourceDimensions'] = list(source.size)
            report['sprites'][name] = receipt
    update_palette_input(images['tree_1.png'])
    if args.archive_only:
        return
    for name, image in images.items():
        target = ALT / (SPRITE_PATH + name)
        rendered = stone_palette(image) if name == 'tree_1.png' else image
        target.write_bytes(png(rendered))
        report['sprites'][name]['outputSha256'] = hashlib.sha256(target.read_bytes()).hexdigest()
    path = WORK / 'validation/sprite-tree-v7.json'
    path.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
