"""Build the roulette table and clockwork rat racetrack with Blender 4.5.
Run tools/make_gaming_floor_prints.py and tools/make_attraction_prints.py with Pillow first.
Pass roulette or rat-derby after -- to render only a single asset.
"""
import importlib
import sys
from pathlib import Path

import bpy
from mathutils import Vector
sys.path.insert(0,str(Path(__file__).resolve().parent))
import build_gaming_floor_blender as floor

MODELS = {
    "roulette": ("Европейская рулетка","model_roulette"),
    "rat-derby": ("Механические крысиные бега","model_rat_derby"),
}

def organize(scene,objects):
    body=bpy.data.collections.new("Корпус и отделка")
    motion=bpy.data.collections.new("Механизмы")
    scene.collection.children.link(body)
    scene.collection.children.link(motion)
    for obj in objects:
        group=motion if obj.parent or obj.type=="EMPTY" or obj.name.startswith(("Rat_","Lever_","Roulette_")) else body
        for old in list(obj.users_collection): old.objects.unlink(obj)
        group.objects.link(obj)

def build(key):
    title,module=MODELS[key]
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.context.preferences.filepaths.save_version=0
    scene=bpy.context.scene
    scene.name=title
    mats=floor.materials()
    importlib.import_module(module).build(mats)
    objects=list(scene.objects)
    organize(scene,objects)
    bpy.context.view_layer.update()
    meshes=[obj for obj in objects if obj.type=="MESH"]
    points=[obj.matrix_world @ vert.co for obj in meshes for vert in obj.data.vertices]
    bounds=[(min(p[i] for p in points),max(p[i] for p in points)) for i in range(3)]
    print("PROP_BOUNDS",key,bounds,flush=True)
    for ob in objects:ob.select_set(True)
    bpy.context.view_layer.objects.active=meshes[0]
    target=floor.OUT/key
    bpy.ops.export_scene.gltf(filepath=str(target.with_suffix(".glb")),export_format="GLB",use_selection=True,
                              export_apply=True,export_extras=True,export_cameras=False,export_lights=False)
    bpy.ops.object.select_all(action="DESELECT")
    floor.studio(scene,mats,key)
    camera=scene.camera
    camera.location=(2.75,-7.8,6.6) if key=="roulette" else (2.6,-7.6,5.55)
    look=Vector((0,0,.565 if key=="roulette" else .67))
    floor.aim(camera,look)
    width=bounds[0][1]-bounds[0][0]
    camera.data.ortho_scale=width*1.18
    scene.render.engine="BLENDER_EEVEE_NEXT"
    scene.eevee.taa_render_samples=64
    scene.render.resolution_x,scene.render.resolution_y=1300,860
    scene.render.resolution_percentage=100
    scene.render.image_settings.file_format="PNG"
    scene.render.filepath="//"+key+".png"
    for screen in bpy.data.screens:
        for area in screen.areas:
            if area.type=="VIEW_3D":
                view=area.spaces.active.region_3d
                view.view_rotation=camera.rotation_euler.to_quaternion()
                view.view_distance=width*1.4
                view.view_location=look
    bpy.ops.wm.save_as_mainfile(filepath=str(target.with_suffix(".blend")),compress=True)
    bpy.ops.render.render(write_still=True)

if __name__=="__main__":
    for asset in sys.argv[sys.argv.index("--")+1:] if "--" in sys.argv else MODELS:
        build(asset)
