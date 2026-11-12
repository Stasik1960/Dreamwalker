"""Bake the two Bloodborne Blender assemblies into compact runtime meshes.

Run with Blender 4.5:
  blender --background --factory-startup --python tools/export_bloodborne_boards.py

The export evaluates modifiers and curves, excludes studio objects, maps Blender
metres to Minecraft coordinates, and writes indexed position/normal/colour data.
"""

from __future__ import annotations

import math
import struct
import sys
import zlib
from pathlib import Path

import bpy


MOD_ROOT = Path(__file__).resolve().parents[1]
ARTWORK_ROOT = MOD_ROOT / "artwork"
ASSET_ROOT = MOD_ROOT / "src/main/resources/assets/night_shift_skate"
MESH_ROOT = ASSET_ROOT / "meshes/board"
TEXTURE_ROOT = ASSET_ROOT / "textures/entity"
SCALE = 1.20

BOARDS = (
    (ARTWORK_ROOT / "Iron Vespers.blend", "iron_vespers.nsmesh"),
    (ARTWORK_ROOT / "Grave Procession.blend", "grave_procession.nsmesh"),
)


def linear_to_srgb(value: float) -> int:
    value = max(0.0, min(1.0, value))
    if value <= 0.0031308:
        value *= 12.92
    else:
        value = 1.055 * value ** (1.0 / 2.4) - 0.055
    return round(value * 255.0)


def representative_color(material: bpy.types.Material | None) -> tuple[int, int, int, int]:
    if material is None:
        return 128, 128, 128, 255
    color = material.diffuse_color
    if material.use_nodes:
        principled = next((node for node in material.node_tree.nodes
                           if node.type == "BSDF_PRINCIPLED"), None)
        if principled is not None:
            color = principled.inputs["Base Color"].default_value
    return tuple(linear_to_srgb(float(channel)) for channel in color[:3]) + (255,)


def is_studio_object(obj: bpy.types.Object) -> bool:
    names = {collection.name.lower() for collection in obj.users_collection}
    return any("studio" in name or "camera" in name and "light" in name for name in names)


def mapped(vector) -> tuple[float, float, float]:
    return float(vector.y) * SCALE, float(vector.z) * SCALE, float(vector.x) * SCALE


def quantized(values: tuple[float, ...], precision: int = 6) -> tuple[float, ...]:
    return tuple(round(value, precision) for value in values)


def export_current_scene(destination: Path) -> None:
    depsgraph = bpy.context.evaluated_depsgraph_get()
    vertices: list[tuple[float, float, float, float, float, float, int, int, int, int]] = []
    indices: list[int] = []
    unique: dict[tuple, int] = {}
    source_triangles = 0
    exported_objects = 0
    object_triangles: list[tuple[int, str]] = []

    for source in sorted(bpy.context.scene.objects, key=lambda item: item.name):
        if source.type not in {"MESH", "CURVE", "SURFACE", "FONT"}:
            continue
        if source.hide_render or is_studio_object(source):
            continue
        evaluated = source.evaluated_get(depsgraph)
        mesh = evaluated.to_mesh(preserve_all_data_layers=True, depsgraph=depsgraph)
        if mesh is None or not mesh.polygons:
            evaluated.to_mesh_clear()
            continue
        mesh.calc_loop_triangles()
        runtime_decimate = None
        original_triangle_count = len(mesh.loop_triangles)
        is_iron_vespers = destination.name == "iron_vespers.nsmesh"
        decimate_threshold = 600 if is_iron_vespers else 1800
        if source.type == "MESH" and original_triangle_count > decimate_threshold:
            # Collapse only dense sculpt/ornament meshes after their authored modifier stack.
            # The square-root budget keeps more samples as visual complexity rises while
            # preventing a few embossed pieces from dominating the entire client model.
            target_factor = 12.0 if is_iron_vespers else 16.0
            target_floor = 320 if is_iron_vespers else 760
            target = max(target_floor, round(math.sqrt(original_triangle_count) * target_factor))
            evaluated.to_mesh_clear()
            runtime_decimate = source.modifiers.new("Runtime mesh budget", "DECIMATE")
            runtime_decimate.decimate_type = "COLLAPSE"
            runtime_decimate.ratio = min(1.0, target / original_triangle_count)
            runtime_decimate.use_collapse_triangulate = True
            bpy.context.view_layer.update()
            evaluated = source.evaluated_get(depsgraph)
            mesh = evaluated.to_mesh(preserve_all_data_layers=True, depsgraph=depsgraph)
            mesh.calc_loop_triangles()
        normal_matrix = evaluated.matrix_world.to_3x3().inverted_safe().transposed()
        materials = [representative_color(slot.material) for slot in evaluated.material_slots]
        if not materials:
            materials = [(128, 128, 128, 255)]

        object_triangles.append((len(mesh.loop_triangles), source.name))
        for triangle in mesh.loop_triangles:
            polygon = mesh.polygons[triangle.polygon_index]
            color = materials[min(polygon.material_index, len(materials) - 1)]
            source_triangles += 1
            for loop_index in triangle.loops:
                loop = mesh.loops[loop_index]
                position = evaluated.matrix_world @ mesh.vertices[loop.vertex_index].co
                normal = (normal_matrix @ mesh.corner_normals[loop_index].vector).normalized()
                px, py, pz = mapped(position)
                nx, ny, nz = mapped(normal)
                # Mapping normals must not inherit the metre-to-block scale.
                normal_length = math.sqrt(nx * nx + ny * ny + nz * nz)
                nx, ny, nz = nx / normal_length, ny / normal_length, nz / normal_length
                key = quantized((px, py, pz, nx, ny, nz)) + color
                index = unique.get(key)
                if index is None:
                    index = len(vertices)
                    unique[key] = index
                    vertices.append((px, py, pz, nx, ny, nz, *color))
                indices.append(index)
        exported_objects += 1
        evaluated.to_mesh_clear()
        if runtime_decimate is not None:
            source.modifiers.remove(runtime_decimate)

    if not indices:
        raise RuntimeError(f"No renderable geometry in {bpy.data.filepath}")
    bounds_min = [min(vertex[axis] for vertex in vertices) for axis in range(3)]
    bounds_max = [max(vertex[axis] for vertex in vertices) for axis in range(3)]
    destination.parent.mkdir(parents=True, exist_ok=True)
    with destination.open("wb") as stream:
        stream.write(struct.pack(">4sIII6f", b"NSM1", 1, len(vertices), len(indices),
                                 *bounds_min, *bounds_max))
        for vertex in vertices:
            stream.write(struct.pack(">6f4B", *vertex))
        for index in indices:
            stream.write(struct.pack(">I", index))
    print(f"EXPORTED {destination.name}: objects={exported_objects} "
          f"source_triangles={source_triangles} vertices={len(vertices)} "
          f"bytes={destination.stat().st_size} bounds={bounds_min}..{bounds_max}")
    print("HEAVIEST " + ", ".join(f"{name}={count}" for count, name in
                                  sorted(object_triangles, reverse=True)[:12]))


def png_chunk(kind: bytes, payload: bytes) -> bytes:
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload))


def write_white_texture() -> None:
    # A tiny opaque texture lets the renderer use material colours as vertex tint.
    raw = b"\x00\xff\xff\xff\xff"
    png = b"\x89PNG\r\n\x1a\n"
    png += png_chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0))
    png += png_chunk(b"IDAT", zlib.compress(raw, 9))
    png += png_chunk(b"IEND", b"")
    TEXTURE_ROOT.mkdir(parents=True, exist_ok=True)
    (TEXTURE_ROOT / "board_white.png").write_bytes(png)


def point_at(obj: bpy.types.Object, target: tuple[float, float, float]) -> None:
    from mathutils import Vector
    obj.rotation_euler = (Vector(target) - obj.location).to_track_quat("-Z", "Y").to_euler()


def preview_runtime_mesh(source: Path, destination: Path) -> None:
    """Render the shipped binary itself for export-time silhouette QA."""
    data = source.read_bytes()
    magic, version, vertex_count, index_count, *_ = struct.unpack_from(">4sIII6f", data, 0)
    if magic != b"NSM1" or version != 1:
        raise RuntimeError(f"Bad preview input {source}")
    offset = 40
    runtime_vertices = []
    colors = []
    for _ in range(vertex_count):
        row = struct.unpack_from(">6f4B", data, offset)
        offset += 28
        # Minecraft X/Y/Z back to Blender X/Y/Z solely for the QA camera.
        runtime_vertices.append((row[0], row[2], row[1]))
        colors.append(row[6:10])
    runtime_indices = struct.unpack_from(f">{index_count}I", data, offset)
    faces = [runtime_indices[i:i + 3] for i in range(0, index_count, 3)]

    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    mesh = bpy.data.meshes.new(source.stem + " runtime QA mesh")
    mesh.from_pydata(runtime_vertices, [], faces)
    obj = bpy.data.objects.new(source.stem, mesh)
    scene.collection.objects.link(obj)
    palette: dict[tuple[int, int, int, int], int] = {}
    for face, polygon in zip(faces, mesh.polygons):
        color = colors[face[0]]
        slot = palette.get(color)
        if slot is None:
            slot = len(palette)
            palette[color] = slot
            mat = bpy.data.materials.new("runtime rgba " + "-".join(map(str, color)))
            mat.diffuse_color = tuple(channel / 255.0 for channel in color)
            mat.metallic = .18
            mat.roughness = .48
            obj.data.materials.append(mat)
        polygon.material_index = slot

    camera_data = bpy.data.cameras.new("Runtime mesh QA camera")
    camera_data.type = "ORTHO"
    camera_data.ortho_scale = 1.34
    camera = bpy.data.objects.new(camera_data.name, camera_data)
    scene.collection.objects.link(camera)
    camera.location = (.78, -.96, .64)
    point_at(camera, (0, 0, .09))
    scene.camera = camera
    for name, location, energy, size, color in (
            ("Soft key", (-.65, -.55, .95), 180, .8, (1.0, .78, .60)),
            ("Cool rim", (.65, .45, .55), 90, .7, (.50, .65, 1.0))):
        light_data = bpy.data.lights.new(name, "AREA")
        light_data.energy = energy
        light_data.shape = "DISK"
        light_data.size = size
        light_data.color = color
        light = bpy.data.objects.new(name, light_data)
        scene.collection.objects.link(light)
        light.location = location
        point_at(light, (0, 0, .08))
    scene.render.engine = "BLENDER_EEVEE_NEXT"
    scene.render.resolution_x = 900
    scene.render.resolution_y = 520
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.film_transparent = True
    scene.view_settings.view_transform = "AgX"
    scene.view_settings.look = "AgX - Medium High Contrast"
    destination.parent.mkdir(parents=True, exist_ok=True)
    scene.render.filepath = str(destination)
    bpy.ops.render.render(write_still=True)
    print(f"PREVIEW {destination}")


def main() -> None:
    write_white_texture()
    for blend, output_name in BOARDS:
        if not blend.is_file():
            raise FileNotFoundError(blend)
        bpy.ops.wm.open_mainfile(filepath=str(blend))
        export_current_scene(MESH_ROOT / output_name)
    if "--qa-preview" in sys.argv:
        preview_root = MOD_ROOT / "build/visual-qa"
        preview_runtime_mesh(MESH_ROOT / "iron_vespers.nsmesh", preview_root / "iron_vespers_runtime.png")
        preview_runtime_mesh(MESH_ROOT / "grave_procession.nsmesh", preview_root / "grave_procession_runtime.png")


if __name__ == "__main__":
    main()
