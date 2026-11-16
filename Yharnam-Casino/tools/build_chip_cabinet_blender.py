"""Run with Blender 4.5 after tools/make_chip_cabinet.py."""

from math import cos, pi, sin
from pathlib import Path
import sys

import bpy
from mathutils import Vector

sys.path.insert(0,str(Path(__file__).resolve().parent))
from chip_cabinet_layout import BOXES, CHIP_RADIUS, CHIP_THICKNESS, DENOMINATIONS, LANES, lane_center

ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT/"src/main/resources/assets/yharnam_casino/textures"
OUT = ROOT/"art/chip-cabinet"
OUT.mkdir(parents=True,exist_ok=True)


def texture_material(name, filename, roughness):
    material = bpy.data.materials.new(name)
    material.use_nodes = True
    nodes = material.node_tree.nodes
    image = nodes.new("ShaderNodeTexImage")
    image.image = bpy.data.images.load(str(filename))
    image.image.pack()
    shader = nodes.get("Principled BSDF")
    shader.inputs["Roughness"].default_value = roughness
    material.node_tree.links.new(image.outputs["Color"],shader.inputs["Base Color"])
    return material


def to_blender(point):
    x,y,z = point
    return (x-8)/16,(8-z)/16,y/16


def furniture_piece(name, lower, upper, material):
    center = to_blender([(a+b)/2 for a,b in zip(lower,upper)])
    x,y,z = [(b-a)/32 for a,b in zip(lower,upper)]
    bpy.ops.mesh.primitive_cube_add(location=center)
    obj = bpy.context.object
    obj.name = name
    obj.scale = x,z,y
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    for polygon in obj.data.polygons:
        for loop, uv in zip(polygon.loop_indices,[(0,0),(1,0),(1,1),(0,1)]):
            obj.data.uv_layers.active.data[loop].uv = uv
    obj.data.materials.append(material)
    bevel = obj.modifiers.new("Worn corners","BEVEL")
    bevel.width = min(.008, min(x,y,z)*.45)
    bevel.segments = 1


def chips(slot, lane, count, material):
    x,y,z = lane_center(slot,lane)
    angles = [2*pi*index/8+pi/8 for index in range(8)]
    vertices = [to_blender((x+CHIP_RADIUS*cos(a),y+CHIP_RADIUS*sin(a),depth))
                for depth in (z-count*CHIP_THICKNESS,z) for a in angles]
    faces = [(i,(i+1)%8,(i+1)%8+8,i+8) for i in range(8)]
    faces += [tuple(reversed(range(8))),tuple(range(8,16))]
    mesh = bpy.data.meshes.new(f"{DENOMINATIONS[slot]} chip layers")
    mesh.from_pydata(vertices,[],faces)
    mesh.update()
    obj = bpy.data.objects.new(f"Chips {DENOMINATIONS[slot]} • tray lane {lane+1} • layers {count}",mesh)
    bpy.context.collection.objects.link(obj)
    obj["denomination"],obj["visible_layers"] = DENOMINATIONS[slot],count
    obj.data.materials.append(material)
    uv = mesh.uv_layers.new()
    u0,u1 = (slot*16+.5)/128,(slot*16+15.5)/128
    vt,vb = 1-(272-4*count+.1)/512,1-271.9/512
    for polygon in mesh.polygons[:8]:
        for index, coords in zip(polygon.loop_indices,[(u0,vb),(u1,vb),(u1,vt),(u0,vt)]):
            uv.data[index].uv = coords
    for polygon in mesh.polygons[8:]:
        for index in polygon.loop_indices:
            a = angles[mesh.loops[index].vertex_index%8]
            uv.data[index].uv = (u0+(u1-u0)*(.5+.5*cos(a)),1-(8+7.25*sin(a))/512)


def aimed(obj, loc, target):
    obj.location = loc
    obj.rotation_euler = (Vector(target)-obj.location).to_track_quat("-Z","Y").to_euler()


bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.context.preferences.filepaths.save_version = 0
scene = bpy.context.scene
scene.name = "Yharnam Casino — Chip Cabinet"
surfaces = {name:texture_material(name.title(),TEXTURES/"block"/f"chip_cabinet_{name}.png",.74)
            for name in ("oak","edge","back","trim","felt","rail")}
for name,lower,upper,surface in BOXES:
    furniture_piece(name,lower,upper,surfaces[surface])
stock = texture_material("Chip denominations",TEXTURES/"entity/chip_cabinet_stock.png",.48)
for position,total in enumerate((344,192,246,365,252,318,147)):
    for lane in range(LANES):
        count = min(64,max(0,total-lane*64))
        if count:
            chips(position,lane,count,stock)

for name,power,color,size,position in (
    ("Warm counter light",145,(1,.83,.68),3.0,(-2,-3.8,4.2)),
    ("Front fill",86,(.83,.89,1),2.1,(2,-3.0,1.9)),
    ("Shoulder light",92,(.76,.82,1),2.4,(1,1.2,3.8)),
):
    light = bpy.data.lights.new(name,"AREA")
    light.energy,light.color,light.size = power,color,size
    obj = bpy.data.objects.new(name,light)
    scene.collection.objects.link(obj)
    aimed(obj,position,(0,0,.9))

camera = bpy.data.cameras.new("Cabinet camera")
cam = bpy.data.objects.new(camera.name,camera)
scene.collection.objects.link(cam)
aimed(cam,(2.3,-6,3.05),(0,0,.955))
camera.type,camera.ortho_scale = "ORTHO",2.21
scene.camera = cam
scene.world = bpy.data.worlds.new("Neutral studio")
scene.world.use_nodes = True
scene.world.node_tree.nodes.get("Background").inputs[0].default_value = (.10,.12,.15,1)
scene.render.engine = "BLENDER_EEVEE_NEXT"
scene.eevee.taa_render_samples = 32
scene.render.film_transparent = True
scene.render.image_settings.color_mode = "RGBA"
scene.render.resolution_x,scene.render.resolution_y = 760,900
scene.render.filepath = "//chip_cabinet.png"
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type == "VIEW_3D":
            area.spaces.active.region_3d.view_perspective = "CAMERA"
            area.spaces.active.overlay.show_overlays = False
            area.spaces.active.shading.type = "MATERIAL"
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/"chip_cabinet.blend"))
bpy.ops.render.render(write_still=True)
