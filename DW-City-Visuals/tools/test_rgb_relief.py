"""Check the unclipped RGB relief template and the packaged renderer's scope."""
from collections import Counter
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile
import argparse
import json

from PIL import Image

from generate_rgb_phase1 import neutral


def verify_curve():
    # Put 128 visible luminance levels on either side of the old median.
    # The transparent white must have no effect on exposure.
    source = Image.new("RGBA", (129, 1))
    source.putdata([(v, v, v, 255) for v in range(17, 145)] + [(255, 255, 255, 0)])
    result = neutral(source, {(x, 0) for x in range(source.width)})
    relief = list(result.getchannel("R").getdata())[:-1]
    assert relief[-1] == 255 and all(a < b for a, b in zip(relief, relief[1:]))
    assert result.getchannel("A").tobytes() == source.getchannel("A").tobytes()
    # The renderer multiplies each channel independently, covering the
    # entire 24-bit palette without enumerating every RGB triple.
    for channel in range(256):
        rendered = [value * channel // 255 for value in relief]
        assert rendered[-1] == channel
        assert all(a <= b for a, b in zip(rendered, rendered[1:]))


def sprites(archive):
    profiles = json.loads(archive.read("bloodborne_dw/rgb-phase1.json"))["profiles"]
    scope = {name: (row["block"], row["variants"], row["models"],
                    row.get("nativeCells", []), row["nativeMeshes"], set(row["paints"]))
             for name, row in profiles.items()}
    textures = {}
    for row in profiles.values():
        for paint in row["paints"].values():
            namespace, identifier = paint["neutral"].split(":", 1)
            key = f"dw_city_visuals/alt/assets/{namespace}/textures/{identifier}.png"
            textures[key] = (tuple(paint["size"]), paint["mipLevels"])
    return scope, textures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("candidate", type=Path)
    args = parser.parse_args()
    verify_curve()
    with ZipFile(args.baseline) as baseline, ZipFile(args.candidate) as candidate:
        before, before_textures = sprites(baseline)
        after, after_textures = sprites(candidate)
        assert before == after, "RGB eligibility changed"
        protected = [p for p in baseline.namelist()
                     if not p.endswith("/") and (p.startswith("dev/") or
                     p.startswith("assets/bloodborne_dw/native_grid/") or
                     p.startswith("dw_city_visuals/original/"))]
        assert all(baseline.read(p) == candidate.read(p) for p in protected)
        for path, (size, _) in after_textures.items():
            with Image.open(BytesIO(candidate.read(path))) as png:
                image = png.convert("RGBA")
            assert image.size == size
            assert image.getchannel("R").tobytes() == image.getchannel("G").tobytes()
            assert image.getchannel("R").tobytes() == image.getchannel("B").tobytes()
        # Compare decoded texture storage, rather than PNG file size.
        formats = lambda textures: Counter(textures.values())
        assert formats(before_textures) == formats(after_textures), "RGB atlas footprint changed"
    print(f"PASS: {len(after)} profiles, {len(after_textures)} sprites and "
          f"{len(protected)} unchanged runtime/original/native files")


if __name__ == "__main__":
    main()
