"""Run make_chess.py first, then open this file with Blender in background mode."""
from pathlib import Path
import json, math
import bpy
from mathutils import Vector

ROOT=Path(__file__).resolve().parents[1]
ART=ROOT/"art/gaming-floor/chess"
RES=ROOT/"src/main/resources/assets/yharnam_casino"
bpy.ops.wm.read_factory_settings(use_empty=True)
scene=bpy.context.scene
atlas=bpy.data.images.load(str(RES/"textures/machine/chess_table.png"));atlas.pack()
mat=bpy.data.materials.new("Walnut, brass, ivory and ebony");mat.use_nodes=True
tex=mat.node_tree.nodes.new("ShaderNodeTexImage");tex.image=atlas;tex.interpolation="Closest"
bs=mat.node_tree.nodes.get("Principled BSDF");mat.node_tree.links.new(tex.outputs["Color"],bs.inputs["Base Color"]);bs.inputs["Roughness"].default_value=.68
geometry=json.loads((ROOT/"build/chess-art/geometry.json").read_text())
def mesh(key,triangles):
    vs=[];idx={};faces=[]
    for tri in triangles:
        f=[]
        for x,y,z,*_ in tri:
            pt=(round(x,7),round(-z,7),round(y,7))
            if pt not in idx:idx[pt]=len(vs);vs.append(pt)
            f.append(idx[pt])
        faces.append(f)
    m=bpy.data.meshes.new(key);m.from_pydata(vs,[],faces);m.materials.append(mat);m.update();uv=m.uv_layers.new(name="Atlas")
    for p,tri in zip(m.polygons,triangles):
        for i,v in zip(p.loop_indices,tri):uv.data[i].uv=(v[3],1-v[4])
    assert not m.validate() and all(p.area>1.e-10 for p in m.polygons)
    return m
meshes={k:mesh(k,tris) for k,tris in geometry.items()}
def obj(name,key,x=0,y=0,z=0):
    o=bpy.data.objects.new(name,meshes[key]);scene.collection.objects.link(o);o.location=(x,y,z);return o
table=obj("Inlaid playing table","table")
pieces=[]
for side,back,pawnrow in [('w',0,1),('b',7,6)]:
    for rank,arr in [(back,"rnbqkbnr"),(pawnrow,"p"*8)]:
        for file,kind in enumerate(arr):
            square=chr(97+file)+str(rank+1)
            pieces.append(obj(side+"_"+kind+"_"+square,side+kind,(file-3.5)*.19,(rank-3.5)*.19,.75))
camera_data=bpy.data.cameras.new("Cabinet view");cam=bpy.data.objects.new("Preview camera",camera_data);scene.collection.objects.link(cam);scene.camera=cam;cam.data.type="ORTHO"
def aim(pos,target,scale):
    cam.location=pos;cam.rotation_euler=(Vector(target)-cam.location).to_track_quat("-Z","Y").to_euler();cam.data.ortho_scale=scale
def lamp(pos,power,size,col):
    l=bpy.data.lights.new("Softbox","AREA");l.energy=power;l.shape="DISK";l.size=size;l.color=col;o=bpy.data.objects.new("Softbox",l);scene.collection.objects.link(o);o.location=pos;o.rotation_euler=(Vector((0,0,.4))-o.location).to_track_quat("-Z","Y").to_euler()
lamp((-2,-3,4.5),480,4,(1,.88,.73));lamp((3,1,3),300,3,(.78,.87,1));lamp((0,3,3),220,2,(1,.85,.66))
scene.world=bpy.data.worlds.new("Studio");scene.world.color=(.35,.35,.35)
scene.render.engine='CYCLES';scene.cycles.use_denoising=False;scene.cycles.samples=64;scene.render.film_transparent=True;scene.view_settings.view_transform='AgX'
scene.render.image_settings.color_mode="RGBA";scene.render.resolution_percentage=100
def render(path,w,h):scene.render.resolution_x=w;scene.render.resolution_y=h;scene.render.filepath=str(path);bpy.ops.render.render(write_still=True)
aim((2.2,-3.8,3.7),(0,0,.41),3.58)
scene.render.resolution_x=960;scene.render.resolution_y=700
bpy.context.preferences.filepaths.save_version=0
bpy.ops.wm.save_as_mainfile(filepath=str(ART/"chess-table.blend"))
render(ART/"chess-table.png",960,700)
for o in pieces:o.hide_render=True
aim((2.2,-3.6,3.0),(0,0,.40),3.69);render(RES/"textures/item/chess_table.png",192,192)
table.hide_render=True
# Show three representative pieces on the item icon, sized for the hotbar.
for n,(k,x,y) in enumerate([("bk",-.070,.040),("wq",.055,.015),("wn",-.013,-.077)]):obj("Set icon "+str(n),k,x,y,0)
aim((.23,-.62,.41),(0,0,.089),.310);render(RES/"textures/item/chess_set.png",192,192)
print("CHESS_ART_OK",len(pieces),"men, 12 shared geometries",list(table.dimensions))

# A separate editable set of the twelve unique chessmen in two display rows.
for o in list(scene.objects):
    if o.type=="MESH":bpy.data.objects.remove(o,do_unlink=True)
for row,side in enumerate(("w","b")):
    for file,kind in enumerate("prnbqk"):
        obj(side+"_"+kind,side+kind,(file-2.5)*.175,(row-.5)*.25,0)
aim((.46,-1.55,.91),(0,0,.065),1.18)
scene.render.resolution_x=840;scene.render.resolution_y=410
bpy.ops.wm.save_as_mainfile(filepath=str(ART/"chess-set.blend"))
render(ART/"chess-set.png",840,410)
