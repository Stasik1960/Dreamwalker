"""Build and export three standalone low-poly gaming-floor props with Blender 4.5."""
from math import cos, sin, pi
from pathlib import Path

import bpy
from mathutils import Vector

PROJECT = Path(__file__).resolve().parents[1]
TEXTURES = PROJECT / "build/gaming-floor-textures"
OUT = PROJECT / "art/gaming-floor"
OUT.mkdir(parents=True, exist_ok=True)

def color(hex_color):
    values = [int(hex_color[i:i+2],16)/255 for i in (0,2,4)]
    return [c/12.92 if c<=.04045 else ((c+.055)/1.055)**2.4 for c in values]+[1]

def material(name, hex_color, rough=.52, metal=0, texture=None):
    m=bpy.data.materials.new(name)
    m.diffuse_color=color(hex_color)
    m.use_nodes=True
    bs=m.node_tree.nodes.get("Principled BSDF")
    bs.inputs["Base Color"].default_value=m.diffuse_color
    bs.inputs["Roughness"].default_value=rough
    bs.inputs["Metallic"].default_value=metal*.30
    bs.inputs["Specular IOR Level"].default_value=.17
    if texture:
        tex=m.node_tree.nodes.new("ShaderNodeTexImage")
        tex.image=bpy.data.images.load(str(TEXTURES/(texture+".png")), check_existing=True)
        tex.interpolation="Closest"
        tex.image.pack()
        m.node_tree.links.new(tex.outputs["Color"],bs.inputs["Base Color"])
    return m

def materials():
    return dict(wood=material("Polished walnut","51372D",.4,texture="walnut"),
                ebony=material("Ebonised carved oak","2B2427",.4), gold=material("Rubbed antique brass","B99360",.32,.66),
                aged=material("Oxidised brass","796247",.48,.52), wine=material("Oxblood leather","59333B",.46),
                ink=material("Midnight leather","263438",.49), green=material("Sage enamel","435F57",.36),
                bone=material("Ivory inlay","E1CDA9",.38), red=material("Oxblood enamel","883F46",.36),
                floor=material("Studio stone","34343A",.75), felt=material("Sunk cup lining","202529",.66))

def mesh(name,vertices,faces,mats,indices=None):
    data=bpy.data.meshes.new(name)
    data.from_pydata(vertices,[],faces)
    data.update()
    obj=bpy.data.objects.new(name,data)
    bpy.context.collection.objects.link(obj)
    for m in mats: data.materials.append(m)
    uv=data.uv_layers.new(name="UVMap")
    for poly in data.polygons:
        if indices: poly.material_index=indices[poly.index]
        axis=max(range(3),key=lambda i: abs(poly.normal[i]))
        for loop in poly.loop_indices:
            x,y,z=vertices[data.loops[loop].vertex_index]
            uv.data[loop].uv = (y*2,z*2) if axis==0 else ((x*2,z*2) if axis==1 else (x*2,y*2))
    return obj

def band(name,path,levels,mat,top=None,uv_map=None,close=True):
    """Extrude varying concentric sections; optional image on the top cap."""
    verts=[(x*sx,y*sy,z) for sx,sy,z in levels for x,y in path]
    count=len(path)
    faces=[]
    for row in range(len(levels)-1):
        for i in range(count):
            j=(i+1)%count
            faces.append((row*count+i,row*count+j,(row+1)*count+j,(row+1)*count+i))
    if close:
        faces.append(tuple(reversed(range(count))))
        faces.append(tuple(range(len(verts)-count,len(verts))))
    else:
        last=len(verts)-count
        for i in range(count):
            j=(i+1)%count
            faces.append((last+i,last+j,j,i))
    obj=mesh(name,verts,faces,[mat,top] if top else [mat])
    if top:
        face=obj.data.polygons[-1]
        face.material_index=1
        uv=obj.data.uv_layers.active
        for loop in face.loop_indices:
            x,y,_=verts[obj.data.loops[loop].vertex_index]
            uv.data[loop].uv=uv_map(x,y)
    return obj

def circle(n): return [(cos(2*pi*i/n),sin(2*pi*i/n)) for i in range(n)]

def lathe(name,levels,mat,n=24):
    return band(name,circle(n),[(r,r,z) for z,r in levels],mat)

def ring(name,path,outer,inner,z,mat,height=.004):
    return band(name,path,[(outer,outer,z-height),(outer,outer,z),(inner,inner,z),(inner,inner,z-height)],mat,close=False)

def cube(name,loc,size,mat):
    bpy.ops.mesh.primitive_cube_add(size=1,location=loc)
    ob=bpy.context.object
    ob.name=name
    ob.dimensions=size
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    ob.data.materials.append(mat)
    return ob

def beam(name,a,b,width,depth,mat):
    a,b=Vector(a),Vector(b)
    ob=cube(name,(a+b)/2,(width,depth,(b-a).length),mat)
    ob.rotation_euler=(b-a).to_track_quat("Z","Y").to_euler()
    return ob

def outline(half_widths):
    """Trace stepped rectangles on a 1/16-block grid, front edge first."""
    rows=len(half_widths)
    left=[]
    right=[]
    for index,half in enumerate(half_widths):
        y=(index-rows/2)/8
        if index and half==half_widths[index-1]:
            continue
        if index:
            left.append((-half_widths[index-1]/16,y))
            right.append((half_widths[index-1]/16,y))
        left.append((-half/16,y))
        right.append((half/16,y))
    left.append((-half_widths[-1]/16,rows/16))
    right.append((half_widths[-1]/16,rows/16))
    return right+left[::-1]

def table_base(m):
    for x in (-.75,.75):
        cube("Trestle foot",(x,0,.09375),(.3125,.875,.1875),m["wood"])
        for y in (-.3125,.3125):
            cube("Foot shoe",(x,y,.046875),(.34375,.1875,.09375),m["ebony"])
        cube("Square column",(x,0,.40625),(.25,.3125,.4375),m["wood"])
        cube("Column collar",(x,0,.25),(.28125,.34375,.0625),m["aged"])
        cube("Corbel",(x,0,.59375),(.3125,.50,.125),m["ebony"])
        cube("Capital",(x,0,.6875),(.375,.6875,.125),m["wood"])
    cube("Stretcher",(0,0,.31),(1.5,.125,.125),m["wood"])

def chip_stack(m,x,y,z,height,shade):
    count=max(2,round(height/.012))
    for i in range(count):
        ob=lathe("Stack layer",[(0,.035),(.012,.035)],m["bone" if i==count//2 else shade],8)
        ob.location=(x,y,z+.012*i)

def card(m,name,x,y,z,ang):
    corners=[(-.05,-.075),(.05,-.075),(.053,-.072),(.053,.072),(.05,.075),(-.05,.075),(-.053,.072),(-.053,-.072)]
    face=material("Printed "+name,"EDDEB7",.72,texture=name)
    obj=band("Playing card "+name,corners,[(1,1,0),(1,1,.0016)],m["bone"],face,lambda x,y:(x/.106+.5,y/.150+.5))
    obj.location=(x,y,z)
    obj.rotation_euler.z=ang

def reset_button(m,x,y,z,vertical=False):
    # A brass collar, oxblood cap and inlaid return arrow shared by the tables.
    base=lathe("Reset | brass collar",[(0,.078),(.014,.078),(.020,.068)],m["aged"],12)
    base.location=(x,y,z)
    if vertical: base.rotation_euler.x=pi/2
    cap=lathe("Reset | enamel cap",[(.019,.065),(.028,.061)],m["red"],12)
    cap.parent=base
    path=[(.025,.014),(.014,.027),(-.007,.030),(-.025,.017),(-.030,-.003),(-.022,-.019),(-.001,-.027),(.016,-.023)]
    for (ax,ay),(bx,by) in zip(path,path[1:]):
        stripe=beam("Reset | arrow",(ax,ay,.030),(bx,by,.030),.007,.004,m["bone"])
        stripe.parent=base
    tip=band("Reset | arrow head",[(.009,-.031),(.031,-.014),(.008,-.013)],[(1,1,.0285),(1,1,.033)],m["bone"])
    tip.parent=base

def table(m,blackjack):
    path=outline([8,12,16,18,20,21,21,21,21,21,21] if blackjack else [13,17,20,21,22,22,22,22,21,20,17,13])
    if blackjack:
        path=[(x,y-.11) for x,y in path]
    table_base(m)
    band("Timber apron",path,[(.93,.93,.6875),(.93,.93,.80)],m["wood"])
    band("Square outer rail",path,[(1,1,.78125),(1,1,.84375)],m["ebony"])
    felt=material("Printed baize","294E42",.94,texture="jack-felt" if blackjack else "poker-felt")
    band("Cloth playing surface",path,[(.94,.94,.84375),(.94,.94,.8516)],m["felt"],felt,
         lambda x,y:(.5+x/2.96,.5+y/1.80))
    band("Armrest",path,[(1,1,.84375),(1,1,.90625),(.91,.88,.90625),(.91,.88,.84375)],
         m["ink" if blackjack else "wine"],close=False)
    ring("Brass inset",path,.922,.914,.907,m["aged"],.002)
    reset_button(m,-.51,.43,.852)
    if blackjack:
        cube("Dealer tray",(.30,.355,.875),(.46,.1875,.046875),m["wood"])
        for i,shade in enumerate(("bone","red","green","ink","red","bone")):
            chip=lathe("Tray roll",[(0,.034),(.125,.034)],m[shade],8)
            chip.rotation_euler.x=pi/2
            chip.location=(.30+(i-2.5)*.067,.422,.922)
            seam=lathe("Chip edge",[(0,.03475),(.012,.03475)],m["aged"],8)
            seam.rotation_euler.x=pi/2
            seam.location=(chip.location.x,.37,.922)
        cube("Dealer shoe",(.77,.29,.883),(.22,.22,.0625),m["wood"])
        for side in (-1,1):
            cube("Shoe cheek",(.77+side*.10,.29,.93),(.025,.22,.0625),m["ebony"])
        card(m,"king",.77,.235,.915,0)
    else:
        for x,y in ((-.75,-.699),(.75,-.699),(-1.34,0),(1.34,0),(-.75,.699),(.75,.699)):
            cube("Brass rail stud",(x,y,.909),(.039,.039,.008),m["aged"])
        for i,n in enumerate(("ace","king","nine")): card(m,n,(i-2)*.155,-.010,.852,0)
        for x,y,h,shade in ((.72,.16,.047,"red"),(.79,.18,.033,"ink"),(.75,.23,.020,"bone"),
                            (-.83,-.14,.038,"green"),(-.78,-.20,.020,"red")):
            chip_stack(m,x,y,.852,h,shade)

def wheel(m):
    centre=1.43
    for x in (-.4375,.4375):
        cube("Sled foot",(x,.125,.0625),(.25,.75,.125),m["wood"])
        for y in (-.1875,.4375):
            cube("Foot cap",(x,y,.0664),(.265625,.1328,.1328),m["ebony"])
    cube("Plinth",(0,.16,.1875),(1.125,.50,.125),m["wood"])
    cube("Plinth cap",(0,.16,.265625),(1.1875,.50,.03125),m["aged"])
    reset_button(m,.38,-.091,.1875,True)
    for sign in (-1,1):
        cube("Lower post",(sign*.34375,.20,.50),(.1875,.1875,.4375),m["wood"])
        beam("Fork",(sign*.34375,.20,.68),(sign*.09375,.20,1.45),.15625,.1875,m["wood"])
    cube("Fixed upright",(0,.20,1.29),(.15625,.1875,2.13),m["ebony"])
    cube("Axle housing",(0,.14,centre),(.3125,.25,.25),m["wood"])
    rotor=bpy.data.objects.new("Wheel • axle pivot",None)
    bpy.context.collection.objects.link(rotor)
    rotor.location=(0,.06,centre)
    rotor.rotation_euler.x=pi/2
    rotor.empty_display_size=.20
    def rotating(ob,x=0,y=0,z=0):
        ob.parent=rotor
        ob.location=(x,y,z)
        return ob
    wheel_path=outline([4,7,9,11,12,13,13,14,14,14,14,14,14,13,13,12,11,9,7,4])
    wheel_path=[(x*.98,y*.69) for x,y in wheel_path]
    rotating(band("Stepped wood drum",wheel_path,[(1,1,-.078),(1,1,.035)],m["wood"]))
    face=material("Printed numbered sectors","6F4643",.72,texture="wheel-face")
    rotating(band("Printed wheel",wheel_path,[(.953,.953,.035),(.953,.953,.052)],
                  m["ebony"],face,lambda x,y:(.5+x/1.67,.5+y/1.67)))
    rotating(ring("Metal-bound rim",wheel_path,1,.973,.05,m["aged"],.03))
    for i in range(16):
        a=(i+.5)*2*pi/16
        x,y=.757*sin(a),.757*cos(a)
        peg=cube("Square wheel peg",(0,0,0),(.035,.035,.078),m["gold"])
        rotating(peg,x,y,.09)
    # The hub cap and pointer are mounted to the stand and remain stationary.
    def fixed_axle(name,rad,depth,y,mat):
        ob=lathe(name,[(0,rad),(.008,rad*1.04),(depth,rad*.88)],mat,8)
        ob.location=(0,y,centre)
        ob.rotation_euler.x=pi/2
    fixed_axle("Axle",.048,.33,.23,m["aged"])
    fixed_axle("Rosette screw",.052,.021,-.071,m["gold"])
    cube("Pointer bracket",(0,.032,2.354),(.125,.375,.0625),m["wood"])
    cube("Flapper mount",(0,-.14,2.33),(.09375,.0625,.09375),m["aged"])
    for i,width in enumerate((.125,.09375,.0625,.03125)):
        cube("Enamel pointer",(0,-.171875,2.29-i*.03125),(width,.03125,.03125),m["red"])

def aim(ob,at):
    ob.rotation_euler=(Vector(at)-ob.location).to_track_quat("-Z","Y").to_euler()

def studio(scene,m,model):
    studio=bpy.data.collections.new("Studio • excluded from export")
    scene.collection.children.link(studio)
    def move(ob):
        for col in list(ob.users_collection): col.objects.unlink(ob)
        studio.objects.link(ob)
    floor=cube("Studio ground",(0,0,-.042),(200,200,.08),m["floor"])
    move(floor)
    for name,pos,power,size,shade in (("Window",(-3,-4,6),640,4.3,(1,.88,.73)),
            ("Cool fill",(4,-1,4),480,3.2,(.78,.87,1)),("Rim",(-1,3,5),790,3.2,(1,.86,.71))):
        l=bpy.data.lights.new(name,"AREA")
        l.energy,l.shape,l.size,l.color=power,"DISK",size,shade
        ob=bpy.data.objects.new(name,l)
        studio.objects.link(ob)
        ob.location=pos
        aim(ob,(0,0,.7))
    camera=bpy.data.cameras.new("Product view")
    ob=bpy.data.objects.new("Product view",camera)
    studio.objects.link(ob)
    upright=model=="wheel-of-fortune"
    ob.location=(2.25,-6.7,3.22) if upright else (2.80,-5.8,4.4)
    target=(0,0,1.17 if upright else .56)
    aim(ob,target)
    camera.type="ORTHO"
    camera.ortho_scale=3.68 if upright else 3.52
    scene.camera=ob
    scene.world=bpy.data.worlds.new("Softbox ambient")
    scene.world.use_nodes=True
    scene.world.node_tree.nodes.get("Background").inputs["Color"].default_value=(.34,.34,.36,1)
    scene.world.node_tree.nodes.get("Background").inputs["Strength"].default_value=.46
    for screen in bpy.data.screens:
        for area in screen.areas:
            if area.type=="VIEW_3D":
                area.spaces.active.region_3d.view_perspective="CAMERA"
                area.spaces.active.overlay.show_overlays=False
                area.spaces.active.shading.type="MATERIAL"

def build(key,title):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.context.preferences.filepaths.save_version=0
    scene=bpy.context.scene
    scene.name=title
    m=materials()
    if key=="wheel-of-fortune": wheel(m)
    else: table(m,key=="black-jack")
    props=list(scene.objects)
    bpy.context.view_layer.update()
    vertices=[ob.matrix_world @ Vector(v) for ob in props if ob.type=="MESH" for v in ob.bound_box]
    bounds=[round(max(v[i] for v in vertices)-min(v[i] for v in vertices),3) for i in range(3)]
    print("PROP",key,"size",bounds,"mesh objects",sum(ob.type=="MESH" for ob in props),flush=True)
    bpy.ops.object.select_all(action="DESELECT")
    for ob in props: ob.select_set(True)
    bpy.ops.export_scene.gltf(filepath=str(OUT/(key+".glb")),export_format="GLB",use_selection=True,export_apply=True)
    bpy.ops.object.select_all(action="DESELECT")
    studio(scene,m,key)
    scene.render.engine="BLENDER_EEVEE_NEXT"
    scene.eevee.taa_render_samples=48
    scene.render.resolution_x,scene.render.resolution_y=1100,800
    scene.render.resolution_percentage=100
    scene.render.image_settings.file_format="PNG"
    scene.render.filepath="//"+key+".png"
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/(key+".blend")),compress=True)
    bpy.ops.render.render(write_still=True)

if __name__=="__main__":
    for model,title in (("poker","Покер"),("black-jack","Чёрный Валет"),("wheel-of-fortune","Колесо фортуны")):
        build(model,title)
