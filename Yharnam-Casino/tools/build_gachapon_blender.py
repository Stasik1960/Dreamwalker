"""Build the casino capsule dispenser, its reusable prize capsule and their product views in Blender."""
from math import cos, sin, pi, hypot, radians, sqrt
from pathlib import Path
from random import Random
import sys

import bpy
from mathutils import Vector
sys.path.insert(0,str(Path(__file__).resolve().parent))
import build_gaming_floor_blender as f
import model_gachapon_capsule as ball

OUT = f.OUT / "gachapon"
OUT.mkdir(exist_ok=True)
PROFILE = [(1.054,.267),(1.116,.338),(1.248,.413),(1.337,.438),
           (1.568,.438),(1.695,.393),(1.782,.311)]
PANES = 12
# A broad facet faces the player.
CIRCLE = [(cos(2*pi*(i+.5)/PANES),sin(2*pi*(i+.5)/PANES)) for i in range(PANES)]
HOPPER_Y = .018


def fresh(title):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.context.preferences.filepaths.save_version=0
    sc=bpy.context.scene
    sc.name=title
    sc.unit_settings.system="METRIC"
    return sc


def palette():
    m=f.materials()
    for key,name,tone,rough,metal in [
        ("wine","Aubergine fired enamel","562B38",.30,.27),
        ("red","Garnet lacquer","AD4354",.28,.24),
        ("green","Verdigris lacquer","427D74",.30,.20),
        ("blue","Prussian blue lacquer","486F92",.30,.20),
        ("honey","Amber lacquer","CA8545",.31,.24),
        ("bone","Warm ivory porcelain","EBDCC1",.38,.08),
        ("gold","Brushed champagne brass","C19961",.32,.75),
        ("trim","Pale brass edge","D9BB80",.35,.58),
        ("ink","Blued iron","293331",.38,.38),
        ("inside","Chute interior","201D22",.67,.03),
        ("enamel","Petrol enamel","244842",.38,.16)]:
        m[key]=f.material(name,tone,rough,metal)
    glass=f.material("Faceted clear bottle glass","A7CDC6",.15,.05)
    bs=glass.node_tree.nodes.get("Principled BSDF")
    bs.inputs["Alpha"].default_value=.115
    bs.inputs["Specular IOR Level"].default_value=.48
    glass.diffuse_color=(*glass.diffuse_color[:3],.115)
    glass.surface_render_method="BLENDED"
    glass.use_backface_culling=True
    m["glass"]=glass
    hues={
        "blue":("398BD7","165CEB","4FAEFE","C0F3FF"),
        "purple":("9850D9","6922B1","AD62FC","EFD0FF"),
        "orange":("EF902F","C0550A","FF9D23","FFF1A1"),
        "red":("D9495C","A71D38","FC4551","FFC8B0")
    }
    for key,(shell,deep,light,spark) in hues.items():
        mat=f.material("Capsule glass "+key,shell,.16,.04)
        bs=mat.node_tree.nodes.get("Principled BSDF")
        bs.inputs["Alpha"].default_value=.32
        bs.inputs["Specular IOR Level"].default_value=.50
        mat.diffuse_color=(*mat.diffuse_color[:3],.32)
        mat.surface_render_method="BLENDED"
        mat.use_backface_culling=True
        m["glass_"+key]=mat
        for band,tone,power in (("deep",deep,.8),("light",light,1.7),("spark",spark,5.0)):
            mat=f.material("Lumen "+key+" "+band,tone,.32,.05)
            bs=mat.node_tree.nodes.get("Principled BSDF")
            bs.inputs["Emission Color"].default_value=f.color(tone)
            bs.inputs["Emission Strength"].default_value=power
            m[band+"_"+key]=mat
    return m


def parent(obj,root):
    obj.parent=root
    return obj


def pivot(name,location=(0,0,0)):
    ob=bpy.data.objects.new(name,None)
    bpy.context.collection.objects.link(ob)
    ob.empty_display_type="PLAIN_AXES"
    ob.empty_display_size=.065
    ob.location=location
    return ob


def octagon(w,d,cut):
    return [(-w+cut,-d),(w-cut,-d),(w,-d+cut),(w,d-cut),
            (w-cut,d),(-w+cut,d),(-w,d-cut),(-w,-d+cut)]


def case_band(name,levels,material):
    path=octagon(.43,.363,.065)
    return f.band(name,path,[(scale,scale,z) for z,scale in levels],material)


def annular_drum(name,profile,material):
    ob=f.band(name,CIRCLE,[(r,r,z) for z,r in profile],material,close=False)
    ob.location.y=HOPPER_Y
    return ob


def drum(name,profile,material,parent_obj=None):
    ob=f.band(name,CIRCLE,[(r,r,z) for z,r in profile],material)
    if parent_obj:
        parent(ob,parent_obj)
    else:
        ob.location.y=HOPPER_Y
    return ob


def xz_plate(name,poly,front,back,mats,face_mat=0):
    """A solid plaque; its flat face points towards the crank side (-Y)."""
    n=len(poly)
    verts=[(x,y,z) for y in (front,back) for x,z in poly]
    faces=[tuple(range(n)),tuple(reversed(range(n,2*n)))]
    faces.extend(((i,i+n,(i+1)%n+n,(i+1)%n) for i in range(n)))
    return f.mesh(name,verts,faces,mats,[face_mat,0]+[0]*n)


def rect(x,z,w,h,cut):
    return [(x+dx,z+dz) for dx,dz in octagon(w/2,h/2,cut)]


def rail_xz(name,outer,inner,front,back,material):
    """Open central aperture with a solid rim, matching loops traced counterclockwise."""
    n=len(outer)
    v=[(x,y,z) for path,y in ((outer,front),(inner,front),(outer,back),(inner,back)) for x,z in path]
    faces=[]
    for i in range(n):
        j=(i+1)%n
        faces.extend([(i,j,n+j,n+i),(2*n+i,3*n+i,3*n+j,2*n+j),
                      (i,2*n+i,2*n+j,j),(n+i,n+j,3*n+j,3*n+i)])
    return f.mesh(name,v,faces,[material])


def face_disc(name,x,y,z,radius,depth,mat,segments=12):
    ob=f.lathe(name,[(-depth/2,radius),(depth/2,radius)],mat,segments)
    ob.rotation_euler.x=pi/2
    ob.location=(x,y,z)
    return ob


def bin_and_case(m):
    outer=[(-.342,.153),(.342,.153),(.377,.39),(.377,.936),(.327,.98),
           (0,.98),(-.327,.98),(-.377,.936),(-.377,.39)]
    hole=[(-.205,.212),(.205,.212),(.205,.404),(.174,.478),(.095,.535),
          (0,.548),(-.095,.535),(-.174,.478),(-.205,.404)]
    n=len(hole)
    inner=[(x*.91,.22+(z-.212)*.90) for x,z in hole]
    verts=[(x,y,z) for loop,y in ((outer,-.344),(hole,-.344),(outer,.333),(inner,-.056)) for x,z in loop]
    faces=[]; inds=[]
    for i in range(n):
        j=(i+1)%n
        faces.extend([(i,j,n+j,n+i),(i,2*n+i,2*n+j,j),(n+i,n+j,3*n+j,3*n+i)])
        inds.extend((0,1,2))
    faces.extend((tuple(reversed(range(2*n,3*n))),tuple(range(3*n,4*n))))
    inds.extend((1,2))
    f.mesh("Cabinet | inset delivery vault",verts,faces,[m["wine"],m["wood"],m["inside"]],inds)
    rim=[(x*1.105,.367+(z-.367)*1.095) for x,z in hole]
    rail_xz("Delivery | cast arch surround",rim,hole,-.357,-.342,m["aged"])
    # A recessed exit at the back of the dark vault.
    xz_plate("Delivery | gravity chute",rect(0,.38,.275,.19,.023),-.060,-.056,[m["felt"]])
    # Shallow shelf and two cast cheeks, open at the front for retrieving the ball.
    tray=f.band("Delivery | projecting catch tray",octagon(.225,.107,.038),
                [(1,1,.209),(1,1,.233),(.945,.94,.244)],m["gold"],m["ink"],lambda x,y:(x,y))
    tray.location.y=-.339
    for side in (-1,1):
        xz_plate("Delivery | tray cheek",[(side*.191,.237),(side*.213,.237),(side*.213,.269),(side*.191,.269)]
                       if side==1 else [(-.213,.237),(-.191,.237),(-.191,.269),(-.213,.269)],-.404,-.266,[m["aged"]])
    f.cube("Delivery | low tray lip",(0,-.437,.250),(.35,.014,.025),m["aged"])
    case_band("Foot | ebonised plinth",[(.012,.94),(.035,1.0),(.086,1.0),(.108,.962)],m["ebony"])
    case_band("Foot | brass piping",[(.075,1.003),(.087,1.003)],m["aged"])
    case_band("Foot | walnut shoe",[(.104,.954),(.139,.967),(.171,.902)],m["wood"])
    case_band("Foot | lower reveal",[(.151,.920),(.17,.92)],m["aged"])
    for side in (-1,1):
        # Chamfered inset service panel and latch on the cabinet flank.
        panel=xz_plate("Side | panel frame",rect(0,.583,.456,.565,.065),-.013,.001,[m["aged"]])
        panel.rotation_euler.z=side*pi/2
        panel.location.x=side*.377
        inset=xz_plate("Side | lacquer panel",rect(0,.583,.432,.543,.061),-.015,-.012,[m["ink"]])
        inset.rotation_euler.z=side*pi/2
        inset.location.x=side*.377
        core=xz_plate("Side | walnut field",rect(0,.580,.395,.493,.058),-.016,-.014,[m["wood"]])
        core.rotation_euler.z=side*pi/2
        core.location.x=side*.377
        for j in (-1,0,1):
            y=j*.036
            height=.142 if j==0 else .084
            f.cube("Side | pressed flute",(side*.394,y,.555),(.005,.009,height),m["aged"])
    xz_plate("Service | rear hatch",rect(0,.590,.54,.58,.048),.335,.348,[m["ebony"]])
    f.cube("Service | rear pull",(0,.365,.73),(.12,.030,.024),m["aged"])
    case_band("Mantel | basal moulding",[(.959,.889),(.98,.965),(1.002,1.0),(1.075,1.0),(1.09,.918)],m["ebony"])
    case_band("Mantel | brass fillet",[(1.063,1.002),(1.073,1.002)],m["gold"])


def controls(m):
    # Front escutcheons and a true dark throat through the token bezel.
    x,z=-.190,.793
    panel=rect(x,z,.247,.278,.038)
    xz_plate("Payment | instrument backplate",panel,-.351,-.342,[m["aged"]])
    xz_plate("Payment | green enamel",rect(x,z,.228,.260,.032),-.355,-.351,[m["enamel"]])
    outer=rect(x,z-.02,.192,.057,.012); inner=rect(x,z-.02,.151,.021,.005)
    rail_xz("Payment | brass chip slot",outer,inner,-.376,-.355,m["gold"])
    xz_plate("Payment | slot shadow",inner,-.356,-.352,[m["inside"]])
    face_disc("Payment | token relief",x,-.359,z+.061,.038,.007,m["gold"])
    face_disc("Payment | token centre",x,-.365,z+.061,.026,.006,m["enamel"])
    glyph=[(x-.014,z+.061),(x,z+.080),(x+.014,z+.061),(x,z+.042)]
    xz_plate("Payment | lozenge stamp",list(reversed(glyph)),-.370,-.365,[m["bone"]])
    # One moving unit, with its origin at the horizontal shaft.
    x,z=.139,.781
    face_disc("Winder | cast circular plate",x,-.357,z,.166,.026,m["aged"],16)
    face_disc("Winder | brass face",x,-.374,z,.153,.016,m["gold"],16)
    face_disc("Winder | enamel dial",x,-.384,z,.130,.010,m["enamel"],16)
    for i in range(5):
        angle=radians(42+20*i)
        xx,zz=x+.103*cos(angle),z+.103*sin(angle)
        face_disc("Winder | raised dial mark",xx,-.391,zz,.0065,.005,m["trim"],6)
    xx,zz=x+.075,z+.076
    xz_plate("Winder | turn arrow",[(xx-.013,zz+.004),(xx+.009,zz-.015),(xx+.010,zz+.010)],-.395,-.39,[m["trim"]])
    handle=pivot("Crank_Pivot",(x,-.397,z))
    handle["rotation_axis_blender"]="Y"
    handle["rotation_axis_gltf"]="Z"
    hub=face_disc("Crank | spindle",0,-.012,0,.052,.046,m["ebony"])
    parent(hub,handle)
    # A solid brass butterfly key with two thumb pads.
    shape=[(-.134,-.034),(-.117,-.049),(-.068,-.044),(-.034,-.025),(.034,-.025),
           (.068,-.044),(.117,-.049),(.134,-.034),(.134,.036),(.118,.049),(.068,.042),
           (.032,.024),(-.032,.024),(-.068,.042),(-.118,.049),(-.134,.036)]
    bar=xz_plate("Crank | butterfly grip",shape,-.059,-.023,[m["gold"]])
    parent(bar,handle)
    for side in (-1,1):
        pad=xz_plate("Crank | oxblood thumb pad",rect(side*.096,0,.052,.051,.009),-.063,-.059,[m["wine"]])
        parent(pad,handle)
    parent(face_disc("Crank | central pin",0,-.062,0,.020,.009,m["trim"],8),handle)
    handle.rotation_euler.y=radians(-16)


def hopper(m):
    annular_drum("Reservoir | foot",[(1.065,.248),(1.077,.296),(1.101,.296),(1.115,.275),
                  (1.115,.238),(1.065,.215)],m["aged"])
    annular_drum("Reservoir | feed bowl",[(1.078,.125),(1.088,.24),(1.105,.305),(1.125,.322),
                  (1.125,.293),(1.112,.153),(1.089,.113),(1.072,.113)],m["ink"])
    annular_drum("Reservoir | lower brass bead",[(1.107,.329),(1.125,.347),(1.134,.348),
                  (1.134,.338),(1.108,.318)],m["gold"])
    # A thin-walled, twelve-panel glass shell, open through its mouth and base.
    shell=[(r,r,z) for z,r in PROFILE]+[(r-.005,r-.005,z) for z,r in reversed(PROFILE)]
    jar=f.band("Hopper_Glass",CIRCLE,shell,m["glass"],close=False)
    jar.location.y=HOPPER_Y
    for angle in (-135,-15,105):
        a=radians(angle)
        for (z0,r0),(z1,r1) in zip(PROFILE,PROFILE[1:]):
            r0+=.003; r1+=.003
            f.beam("Reservoir | brass retaining rib",
                   (r0*cos(a),HOPPER_Y+r0*sin(a),z0),(r1*cos(a),HOPPER_Y+r1*sin(a),z1),
                   .010,.012,m["aged"])
    annular_drum("Reservoir | open refill neck",[(1.757,.334),(1.777,.332),(1.787,.329),
                  (1.787,.291),(1.778,.291),(1.757,.311)],m["aged"])
    hinge=pivot("Refill_Lid_Hinge",(0,HOPPER_Y+.275,1.787))
    hinge["rotation_axis_blender"]="X"
    hinge["rotation_axis_gltf"]="X"
    def cap(name,profile,mat):
        ob=drum(name,profile,mat)
        ob.location-=hinge.location
        return parent(ob,hinge)
    cap("Lid | brass overhang",[(1.774,.335),(1.782,.350),(1.803,.350),(1.811,.331)],m["gold"])
    cap("Lid | garnet crown",[(1.805,.329),(1.819,.326),(1.847,.248),(1.852,.245),
                             (1.853,.150),(1.884,.096),(1.887,.075)],m["wine"])
    cap("Lid | finial collar",[(1.883,.071),(1.893,.074),(1.902,.055)],m["aged"])
    cap("Lid | faceted thumbpiece",[(1.900,.038),(1.918,.054),(1.944,.038),(1.948,.027)],m["gold"])
    for x in (-.067,.067):
        pin=face_disc("Refill | hinge knuckle",x,HOPPER_Y+.280,1.790,.026,.065,m["aged"],8)
        pin.rotation_euler=(0,pi/2,0)
    # Front hasp bridges the lid seam; its lower end unlatches before the lid opens.
    catch=xz_plate("Lid | front hasp",rect(0,1.775,.050,.093,.009),-.313,-.299,[m["gold"]])
    catch.location-=hinge.location
    parent(catch,hinge)


def settled_stock(count):
    # Position-based contact solver for touching capsules in the actual faceted vessel.
    rng=Random(1847)
    radius=.100
    pts=[]
    for i in range(count):
        ang=i*2.39996
        dist=.155*(.65+.35*rng.random())
        pts.append(Vector((dist*cos(ang),dist*sin(ang),1.27+.093*i)))
    prev=[p.copy() for p in pts]
    planes=[]
    apothem=cos(pi/PANES)
    for (za,ra),(zb,rb) in zip(PROFILE,PROFILE[1:]):
        slope=(rb-ra)/(zb-za)*apothem
        if slope<0: continue
        for j in range(PANES):
            a=(j+1)*2*pi/PANES
            norm=Vector((cos(a),sin(a),-slope))
            offset=(ra-.005)*apothem-za*slope-radius*norm.length
            planes.append((norm,offset,norm/norm.length_squared))
    for step in range(330):
        for i,p in enumerate(pts):
            old=p.copy()
            p+=(p-prev[i])*.88
            p.z-=.00155
            prev[i]=old
        for _ in range(5):
            for i,p in enumerate(pts):
                for j in range(i):
                    d=p-pts[j]
                    length=d.length
                    if 0<length<2*radius:
                        push=d*((2*radius-length)/length*.5)
                        p+=push
                        pts[j]-=push
            for p in pts:
                p.z=max(p.z,1.125+radius)
                for n,k,stepper in planes:
                    diff=p.dot(n)-k
                    if diff>0: p-=stepper*diff
    return sorted(pts,key=lambda p:p.z)


def join_objects(name,objects):
    bpy.ops.object.select_all(action="DESELECT")
    for obj in objects: obj.select_set(True)
    bpy.context.view_layer.objects.active=objects[0]
    bpy.ops.object.join()
    ob=objects[0]
    ob.name=name
    return ob


def stocks(m):
    rng=Random(3218)
    keys=("blue","purple","orange","red")
    shared={}
    for key in keys:
        ob=join_objects("Capsule "+key,[ball.build_display(m,key),ball.core(m,key)])
        shared[key]=ob.data
        bpy.data.objects.remove(ob,do_unlink=True)
    positions=settled_stock(19)
    palette_order=["blue","orange","purple","red"]*5
    rng.shuffle(palette_order)
    groups=[[] for _ in range(3)]
    for i,loc in enumerate(positions):
        key=palette_order[i]
        ob=bpy.data.objects.new("Stock "+key,shared[key])
        bpy.context.collection.objects.link(ob)
        ob.location=(loc.x,loc.y+HOPPER_Y,loc.z)
        ob.rotation_euler=(rng.uniform(-2.70,.35),rng.uniform(-1.15,1.15),rng.uniform(0,2*pi))
        groups[min(2,i//7)].append(ob)
    for i,group in enumerate(groups):
        # Joining instances must never modify the shared meshes used in the next band or tray.
        for ob in group: ob.data=ob.data.copy()
        out=join_objects("Stock_Layer_%02d"%(i+1),group)
        out["capsule_count"]=len(group)
    tray=bpy.data.objects.new("Outlet_Capsule",shared["red"])
    bpy.context.collection.objects.link(tray)
    tray.location=(.026,-.296,.344)
    tray.rotation_euler=(radians(-125),radians(-18),radians(8))
    tray.location.z=.244-min((tray.rotation_euler.to_matrix() @ v.co).z for v in tray.data.vertices)
    print("STOCK",len(positions),[(round(p.x,3),round(p.y,3),round(p.z,3)) for p in positions],flush=True)
    return positions


def arrange(scene,objects):
    groups={}
    for ob in objects:
        if ob.name.startswith("Stock_Layer"): key="03 • Запас капсул"
        elif ob.name.startswith("Outlet_"): key="04 • Выданная капсула"
        elif ob.parent or ob.type=="EMPTY": key="02 • Ручка и крышка"
        else: key="01 • Корпус и стекло"
        if key not in groups:
            groups[key]=bpy.data.collections.new(key)
            scene.collection.children.link(groups[key])
        for old in list(ob.users_collection): old.objects.unlink(ob)
        groups[key].objects.link(ob)


def export(name,objects):
    bpy.ops.object.select_all(action="DESELECT")
    for ob in objects: ob.select_set(True)
    bpy.context.view_layer.objects.active=next(ob for ob in objects if ob.type=="MESH")
    bpy.ops.export_scene.gltf(filepath=str(OUT/(name+".glb")),export_format="GLB",use_selection=True,
                             export_apply=True,export_extras=True,export_cameras=False,export_lights=False)
    bpy.ops.object.select_all(action="DESELECT")


def view(sc,m,target,eye,scale,size):
    f.studio(sc,m,"wheel-of-fortune")
    cam=sc.camera
    cam.location=eye
    f.aim(cam,target)
    cam.data.ortho_scale=scale
    cam.data.clip_start=.01
    sc.render.engine="BLENDER_EEVEE_NEXT"
    sc.eevee.taa_render_samples=48
    sc.render.resolution_x,sc.render.resolution_y=size
    sc.render.resolution_percentage=100
    sc.render.image_settings.file_format="PNG"
    sc.view_settings.view_transform="AgX"
    sc.use_nodes=True
    nodes=sc.node_tree.nodes
    glare=nodes.new("CompositorNodeGlare")
    glare.glare_type="FOG_GLOW"
    glare.quality="HIGH"
    sc.node_tree.links.new(nodes.get("Render Layers").outputs["Image"],glare.inputs["Image"])
    sc.node_tree.links.new(glare.outputs["Image"],nodes.get("Composite").inputs["Image"])
    for screen in bpy.data.screens:
        for a in screen.areas:
            if a.type=="VIEW_3D":
                s=a.spaces.active
                s.region_3d.view_distance=scale*1.35
                s.region_3d.view_rotation=cam.rotation_euler.to_quaternion()
                s.region_3d.view_location=Vector(target)
                s.region_3d.view_perspective="CAMERA"
                s.clip_start=.005


def cabinet():
    sc=fresh("Капсульный автомат")
    m=palette()
    bin_and_case(m)
    controls(m)
    hopper(m)
    positions=stocks(m)
    sc["stock_shown"]=len(positions)
    sc["model_front"]="-Y"
    objects=list(sc.objects)
    arrange(sc,objects)
    bpy.context.view_layer.update()
    export("gachapon",objects)
    view(sc,m,(0,-.015,.943),(2.75,-6.8,2.78),2.20,(900,1040))
    sc.render.filepath="//gachapon.png"
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/"gachapon.blend"),compress=True)
    bpy.ops.render.render(write_still=True)


def capsules():
    sc=fresh("Призовые капсулы")
    m=palette()
    roots=[]
    for i,key in enumerate(("blue","purple","orange","red")):
        root=pivot("Prize_Capsule_"+key)
        lo,hi=ball.build_halves(m,key)
        orb=ball.core(m,key)
        for ob in (lo,hi,orb): parent(ob,root)
        socket=parent(pivot("Loot_Socket_"+key),root)
        socket.empty_display_size=.018
        root["radius_blocks"]=.100
        root["opens_along"]="local Z"
        export("capsule" if key=="blue" else "capsule_"+key,[root,lo,hi,orb,socket])
        roots.append(root)
    for i,root in enumerate(roots):
        root.location=((i-1.5)*.223,(-.013,.022,.018,-.012)[i],0)
        root.rotation_euler=(radians((-140,-128,-147,-130)[i]),radians((-15,15,-9,18)[i]),radians((-12,15,-10,7)[i]))
    view(sc,m,(0,0,-.007),(.055,-1.9,.68),.999,(1250,430))
    ground=next(o for o in sc.objects if o.name.startswith("Studio ground"))
    ground.location.z-=.098
    sc.render.filepath="//capsules.png"
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/"capsule.blend"),compress=True)
    bpy.ops.render.render(write_still=True)


if __name__=="__main__":
    which=sys.argv[sys.argv.index("--")+1:] if "--" in sys.argv else ["machine","capsules"]
    if "machine" in which: cabinet()
    if "capsules" in which: capsules()
