"""Bake repeatable 128-pixel surface samples for Blender, GLB and Blockbench."""

from math import exp, pi, sin
from pathlib import Path
from random import Random

from PIL import Image

from styles import THEMES

SIZE = 128
OUT = Path(__file__).resolve().parents[1] / "textures" / "surfaces"


def sample_surface(material, u, v, rng):
    angle = 2*pi
    cloud = (sin(angle*(u+2*v)) + sin(angle*(2*u-v+.2)) + sin(angle*(3*u+v+.7))) / 3
    speckle = rng.uniform(-1,1)
    if material == "oak":
        bend = .86*sin(angle*v+.6) + .42*sin(angle*(2*v+3*u))
        grain = sin(angle*(12*u)+bend) + .41*sin(angle*(27*u)+bend*1.4)
        groove = -7 if grain < -1.1 else 0
        return grain*9 + cloud*10 + groove + speckle*3
    if material == "limestone":
        vein = sin(angle*(2*u+v)+1.1*sin(angle*(u-2*v)))
        pit = -17 if rng.random() < .045 else 0
        return 19*cloud - 8*exp(-37*vein**2) + 7*speckle + pit
    if material in ("metal","iron"):
        pit = -14 if rng.random() < .065 else 0
        scratch = 9 if rng.random() < .035 and sin(angle*(11*v+2*u)) > .55 else 0
        grime = sin(angle*(4*u+3*v)+sin(angle*3*u))*7 + cloud*14
        return grime + pit + scratch + 5*speckle
    if material == "glass":
        filament = sin(angle*(3*u-2*v)+1.2*sin(angle*(u+v)))
        return cloud*24 + max(0,filament)**4 * 12 + speckle*4
    weave = sin(angle*37*u) * sin(angle*37*v)
    return cloud*10 + weave*5 + 3*speckle


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    for style, (_, colors, finishes) in THEMES.items():
        for role, material in finishes.items():
            base = [int(colors[role][i:i+2],16) for i in (0,2,4)]
            rng = Random(style+role)
            pixels = []
            for scanline in range(SIZE):
                for column in range(SIZE):
                    value = sample_surface(material,column/SIZE,scanline/SIZE,rng)
                    pixels.append(tuple(max(0,min(255,round(c+value))) for c in base))
            image = Image.new("RGB",(SIZE,SIZE))
            image.putdata(pixels)
            image.save(OUT/f"{style}_{role}.png",optimize=True)


if __name__ == "__main__":
    main()
