"""Export the approved carriage into the runtime mesh and item icon.

Run from the mod directory with Blender's bpy module and Pillow:
    python scripts/export_carriage.py ../victorian-carriage/night-watch-carriage.blend
"""

import gzip
import math
import struct
import sys
from pathlib import Path

import bpy
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/nightwatchcarriage"
MODEL = ASSETS / "meshes/carriage.nwc"
MAGIC = 0x4E574332


def to_game(v):
    return v.x, v.z, -v.y


def srgb(linear):
    value = linear * 12.92 if linear <= .0031308 else 1.055 * linear ** (1 / 2.4) - .055
    return max(0, min(255, round(255 * value)))


def pack_color(material):
    r, g, b = (srgb(v) for v in material.diffuse_color[:3])
    return r << 16 | g << 8 | b


def parent_name(obj):
    return obj.parent.name if obj.parent else ""


def static_group(obj):
    parent = parent_name(obj)
    if parent == "Wheel_L_Pivot":
        return 1
    if parent == "Wheel_R_Pivot":
        return 2
    if any(m and m.name == "Honey glass" for m in obj.data.materials):
        return 3
    return 0


def quad_corners(mesh):
    """Pack pairs of Blender triangles without changing their diagonal or winding."""
    triangles = [[] for _ in mesh.polygons]
    for tri in mesh.loop_triangles:
        triangles[tri.polygon_index].append(tuple(tri.loops))
    for face, parts in zip(mesh.polygons, triangles):
        if face.loop_total == 4 and len(parts) == 2:
            diagonal = set(parts[0]).intersection(parts[1])
            loops = tuple(face.loop_indices)
            start = 0 if loops[0] in diagonal else 1
            for loop in loops[start:] + loops[:start]:
                yield loop, mesh.loops[loop].vertex_index, face.material_index
        else:
            for a, b, c in parts:
                for loop in (a, b, c, c):
                    yield loop, mesh.loops[loop].vertex_index, face.material_index


def snapshot(obj, selection=None):
    depsgraph = bpy.context.evaluated_depsgraph_get()
    evaluated = obj.evaluated_get(depsgraph)
    mesh = evaluated.to_mesh()
    mesh.calc_loop_triangles()
    if selection is None:
        selection = list(quad_corners(mesh))
    world = evaluated.matrix_world
    normal_matrix = world.to_3x3().inverted().transposed()
    positions = [to_game(world @ v.co) for v in mesh.vertices]
    normals = [to_game((normal_matrix @ n.vector).normalized()) for n in mesh.corner_normals]
    palette = [pack_color(m) if m else 0x402C2A for m in mesh.materials]
    vertices = [(*positions[v], *normals[loop], palette[mat] if palette else 0x402C2A)
                for loop, v, mat in selection]
    evaluated.to_mesh_clear()
    return vertices, selection


def crossbar():
    """Forged tube across the draw shafts and two rolled grips."""
    def tube(profile, color):
        count = 12
        rings = []
        for x, radius in profile:
            rings.append([(x, 1.055 + radius * math.cos(i * math.tau / count),
                           2.55 + radius * math.sin(i * math.tau / count),
                           0., math.cos(i * math.tau / count), math.sin(i * math.tau / count), color)
                          for i in range(count)])
        faces = []
        for left, right in zip(rings, rings[1:]):
            for i in range(count):
                j = (i + 1) % count
                faces.extend((left[i], left[j], right[j], right[i]))
        for ring, sign in ((rings[0], -1), (rings[-1], 1)):
            cap = [(x, y, z, float(sign), 0., 0., color) for x, y, z, *_ in ring]
            for i in range(1, count - 1):
                faces.extend((cap[0], cap[i+1], cap[i], cap[i]) if sign < 0
                             else (cap[0], cap[i], cap[i+1], cap[i+1]))
        return faces

    bar = tube([(-.518,.018),(-.501,.036),(-.450,.039),(-.42,.026),(.42,.026),
                (.45,.039),(.501,.036),(.518,.018)],0x42434B)
    for center in [-.322,.322]:
        bar += tube([(center+x,r) for x,r in [(-.077,.028),(-.069,.036),(-.059,.034),
                    (.059,.034),(.069,.036),(.077,.028)]],0x775843)
    return bar


def export(source):
    bpy.ops.wm.open_mainfile(filepath=str(source))
    scene = bpy.context.scene
    scene.frame_set(1)
    objects = [obj for obj in bpy.data.collections["01  Carriage"].all_objects if obj.type == 'MESH']
    static, animated = [[], [], [], []], []
    for obj in objects:
        keys = obj.data.shape_keys
        if keys:
            keys.animation_data_clear()
            animated.append(obj)
            continue
        # Lower the subdivision of smooth upholstery and densely sampled metal profiles.
        if obj.name.startswith("Button-tufted back "):
            modifier = obj.modifiers.new("Runtime upholstery simplification", 'DECIMATE')
            modifier.ratio = .13
            bpy.context.view_layer.update()
        elif obj.name.startswith(("Shrunk iron tire ", "Bent oak felly ", "Rim bead ", "Rolled iron mudguard ",
                                  "Mudguard copper bead ", "Cast hub boss ", "Raised lancet moulding ")):
            modifier = obj.modifiers.new("Runtime metal simplification", 'DECIMATE')
            modifier.ratio = .48
            bpy.context.view_layer.update()
        vertices, _ = snapshot(obj)
        static[static_group(obj)].extend(vertices)
    static[0].extend(crossbar())
    samples = []
    count = len(animated[0].data.shape_keys.key_blocks)
    assert count == 13 and all(len(obj.data.shape_keys.key_blocks) == count for obj in animated)
    triangles = {}
    for index in range(count):
        for obj in animated:
            keys = obj.data.shape_keys
            keys.eval_time = keys.key_blocks[index].frame
        bpy.context.view_layer.update()
        vertices = []
        for obj in animated:
            current, topology = snapshot(obj, triangles.get(obj.name))
            triangles[obj.name] = topology
            vertices.extend(current)
        assert not samples or len(vertices) == len(samples[0])
        samples.append(vertices)
    MODEL.parent.mkdir(parents=True, exist_ok=True)
    vertex = struct.Struct(">6fI")
    with gzip.GzipFile(filename=str(MODEL), mode="wb", mtime=0) as stream:
        stream.write(struct.pack(">II", MAGIC, count))
        for batch in [*static, *samples]:
            assert batch and len(batch) % 4 == 0
            stream.write(struct.pack(">I", len(batch)))
            stream.write(b"".join(vertex.pack(*item) for item in batch))
    print(f"Exported {sum(map(len, static)) + len(samples[0]):,} vertices per carriage"
          f" and {count} canopy states to {MODEL}")


def textures():
    entity = ASSETS / "textures/entity"
    item = ASSETS / "textures/item"
    entity.mkdir(parents=True, exist_ok=True)
    item.mkdir(parents=True, exist_ok=True)
    Image.new("RGBA", (1,1), "white").save(entity/"white.png")
    icon = Image.new("RGBA", (32,32))
    draw = ImageDraw.Draw(icon)
    draw.polygon([(5,17),(7,11),(20,11),(20,16),(17,22),(5,22)],
                 fill="#493135",outline="#201820")
    draw.polygon([(4,12),(5,8),(8,5),(15,4),(19,6),(22,10),(21,12),(13,11),(8,13)],
                 fill="#2d2834",outline="#14121a")
    draw.line([(6,8),(10,6),(15,5),(19,7),(21,10)],fill="#65596a",width=1)
    draw.polygon([(12,12),(19,12),(18,15),(13,17),(10,15)],fill="#7b3545")
    draw.polygon([(4,21),(16,22),(22,19),(30,17),(31,18),(24,21),(20,24),(13,25),(4,23)],
                 fill="#553634",outline="#241820")
    draw.line([(19,22),(25,20),(30,19)],fill="#8c6149",width=1)
    draw.ellipse((2,15,16,29),fill="#222029",outline="#151219",width=2)
    draw.ellipse((4,17,14,27),outline="#b1815b",width=1)
    for end in [(5,19),(9,17),(13,20),(14,23),(11,26),(6,26),(4,22)]:
        draw.line([(9,22),end],fill="#765243")
    draw.ellipse((8,21,10,23),fill="#e1ab68")
    draw.rectangle((19,13,21,17),fill="#e2a45a",outline="#794e3c")
    draw.point((20,14),fill="#ffd99a")
    draw.point((30,18),fill="#cd9e6b")
    icon.save(item/"carriage.png")


export(Path(sys.argv[1]).resolve())
textures()
