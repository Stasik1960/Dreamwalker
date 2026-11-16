"""The hanging sign shared by the three out-of-order machines."""

from math import cos, hypot, pi, sin, sqrt
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps


FACE = (520, 8, 760, 100)
ROPE = (528, 120, 560, 152)
EDGE = (580, 120)
BRASS = (604, 120)
DARK_BRASS = (628, 120)
COLORS = {EDGE: "#482F27", BRASS: "#AB8255", DARK_BRASS: "#644935"}
EYELETS = (30, 210)
EYELET_Y = 15
ICON_PEG = {"hunters_wager": (111, 85), "astral_choir": (110, 92), "crimson_oath": (111, 86)}


def clipped_rectangle(width, height, margin, clip):
    left, top = margin, margin
    right, bottom = width - margin, height - margin
    return [(left + clip, top), (right - clip, top), (right, top + clip),
            (right, bottom - clip), (right - clip, bottom), (left + clip, bottom),
            (left, bottom - clip), (left, top + clip)]


def plaque_art(wood_path):
    width, height = FACE[2] - FACE[0], FACE[3] - FACE[1]
    with Image.open(wood_path) as wood:
        grain = wood.convert("L").rotate(90).resize((width, height), Image.Resampling.BICUBIC)
    shading = ImageOps.colorize(ImageOps.autocontrast(grain, cutoff=3), "#34231D", "#544035")
    image = shading.convert("RGBA")
    draw = ImageDraw.Draw(image)
    for margin, clip, color, size in ((3, 10, "#271915", 3), (6, 9, "#9C754C", 2),
                                      (9, 7, "#5C412E", 1)):
        points = clipped_rectangle(width - 1, height - 1, margin, clip)
        draw.line(points + points[:1], fill=color, width=size)
    for x in EYELETS:
        draw.ellipse((x-5, EYELET_Y-5, x+5, EYELET_Y+5), fill="#261A16", outline="#B78B57", width=2)

    label = "НЕ РАБОТАЕТ"
    font_path = next(path for path in (
        Path("C:/Windows/Fonts/georgiab.ttf"),
        Path("/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"),
    ) if path.is_file())
    for size in range(31, 15, -1):
        font = ImageFont.truetype(font_path, size)
        if draw.textlength(label, font=font) <= width - 31:
            break
    left, top, right, bottom = draw.textbbox((0, 0), label, font=font)
    text_x = (width - left - right) / 2
    text_y = 46 - (top + bottom) / 2
    draw.text((text_x, text_y + 2), label, font=font, fill="#211611")
    draw.text((text_x, text_y), label, font=font, fill="#F4DFC0")
    for x in (92, 148):
        draw.polygon(((x-3, 73), (x, 71), (x+3, 73), (x, 75)), fill="#BA8F60")
    draw.line((96, 73, 144, 73), fill="#906947", width=1)
    return image


def add_to_atlas(atlas, plaque):
    # Edge bleed keeps the freestanding plaque and rope opaque at mip levels.
    x, y, _, _ = FACE
    padded = ImageOps.expand(plaque, border=6, fill="#432E24")
    atlas.paste(padded, (x-6, y-6))
    x, y, right, bottom = ROPE
    rope = Image.new("RGB", (right-x+8, bottom-y+8), "#8B6342")
    draw = ImageDraw.Draw(rope)
    for offset in range(-44, 44, 7):
        draw.line((0, offset, rope.width-1, offset+26), fill="#5A3E2D", width=2)
        draw.line((0, offset+2, rope.width-1, offset+28), fill="#C5A071", width=2)
    atlas.paste(rope, (x-4,y-4))
    draw = ImageDraw.Draw(atlas)
    for (x, y), color in COLORS.items():
        draw.rectangle((x-6, y-6, x+6, y+6), fill=color)


def add_to_icon(icon, style, plaque):
    x, y = ICON_PEG[style]
    draw = ImageDraw.Draw(icon)
    points = ((x-23,y+26), (x,y), (x+22,y+30))
    draw.line(points, fill="#362620", width=4)
    draw.line(points, fill="#BE986A", width=2)
    draw.ellipse((x-3,y-3,x+3,y+3), fill="#B48B59", outline="#463023")
    sign = plaque.copy()
    alpha = Image.new("L", sign.size)
    ImageDraw.Draw(alpha).polygon(clipped_rectangle(sign.width-1, sign.height-1, 0, 16), fill=255)
    sign.putalpha(alpha)
    sign = sign.resize((65, 25), Image.Resampling.LANCZOS).rotate(-5, Image.Resampling.BICUBIC, expand=True)
    icon.alpha_composite(sign, (x-sign.width//2, y+21))


def sign_faces(panel_front):
    """Yield outward-facing polygons with UV coordinates in atlas pixels."""
    face_z = panel_front + .103
    center_y = 1.128
    width, height = .552, .204
    tilt = -2.2 * pi / 180
    sprite_width, sprite_height = FACE[2]-FACE[0], FACE[3]-FACE[1]

    def point(u, v, depth):
        x = (u / sprite_width - .5) * width
        y = (.5 - v / sprite_height) * height
        return (x*cos(tilt)-y*sin(tilt), center_y+x*sin(tilt)+y*cos(tilt), face_z+depth+.09*y)

    def ring(margin, depth):
        outline = clipped_rectangle(sprite_width, sprite_height, margin, 13)
        return [point(u,v,depth) for u,v in reversed(outline)], list(reversed(outline))

    front, outline = ring(0, 0)
    edge, _ = ring(-4, -.009)
    back, _ = ring(1, -.036)
    yield front, [(FACE[0]+u, FACE[1]+v) for u,v in outline]
    yield back[::-1], [EDGE] * len(back)
    for near, far in ((front,edge), (edge,back)):
        for index in range(len(near)):
            next_index = (index+1) % len(near)
            yield [near[index], far[index], far[next_index], near[next_index]], [EDGE] * 4

    peg_y = 1.370
    cord_z = panel_front + .058
    for start, end, radius, swatch in ((-.009,.019,.024,DARK_BRASS), (.016,.067,.011,DARK_BRASS),
                                        (.064,.078,.018,BRASS)):
        points = [(0,peg_y,panel_front+depth) for depth in (start,end)]
        yield from tube_faces(points, radius, 8, swatch)

    left, right = (point(x, EYELET_Y, .003) for x in EYELETS)
    yield from tube_faces([
        (left[0],left[1],left[2]-.006),
        (left[0]+.044,left[1]+.036,left[2]-.005),
        (-.014,peg_y+.012,cord_z),
        (0,peg_y+.017,cord_z),
        (.014,peg_y+.012,cord_z),
        (right[0]-.043,right[1]+.034,right[2]-.005),
        (right[0],right[1],right[2]-.006),
    ], .009, 6)
    for x,y,z in (left,right):
        yield from tube_faces([(x,y,z-.009),(x,y,z+.007)], .010, 6)


def tube_faces(points, radius, sides, swatch=None):
    def cross(a, b):
        return a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]

    rings = []
    for index, point in enumerate(points):
        before, after = points[max(index-1,0)], points[min(index+1,len(points)-1)]
        direction = [b-a for a,b in zip(before,after)]
        length = sqrt(sum(value*value for value in direction))
        direction = [value/length for value in direction]
        reference = (0,0,1) if abs(direction[2]) < .9 else (0,1,0)
        u = cross(reference, direction)
        length = hypot(*u)
        u = [value/length for value in u]
        v = cross(direction, u)
        angles = [side*2*pi/sides for side in range(sides)]
        rings.append([tuple(p+radius*(a*cos(angle)+b*sin(angle)) for p,a,b in zip(point,u,v))
                      for angle in angles])
    for start,end in zip(rings,rings[1:]):
        for side in range(sides):
            following = (side+1)%sides
            x0 = ROPE[0] + side*(ROPE[2]-ROPE[0])/sides
            x1 = ROPE[0] + (side+1)*(ROPE[2]-ROPE[0])/sides
            yield [start[side],start[following],end[following],end[side]], (
                [swatch]*4 if swatch else [(x0,ROPE[1]),(x1,ROPE[1]),(x1,ROPE[3]),(x0,ROPE[3])])
    end_uv = swatch or (ROPE[0]+2,ROPE[1]+2)
    yield rings[0][::-1], [end_uv]*sides
    yield rings[-1], [end_uv]*sides
