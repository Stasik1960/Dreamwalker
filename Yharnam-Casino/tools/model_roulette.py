"""European roulette furniture, in the scale and materials of the gaming-floor tables."""
from math import cos, sin, pi
import bpy
from build_gaming_floor_blender import band, beam, circle, cube, lathe, material, mesh, outline, ring, table_base

CX, CY = -1.07, .024
SURFACE = .8516
COUNT = 37

def pivot(name, position):
    obj=bpy.data.objects.new(name,None)
    bpy.context.collection.objects.link(obj)
    obj.empty_display_type="PLAIN_AXES"
    obj.empty_display_size=.10
    obj.location=position
    return obj

def wheel(m):
    # The fixed wall rises outside the rotor, then slopes towards the numbered lip.
    bowl = band("Bowl | turned walnut race",circle(32),
                [(r,r,z) for r,z in [(.525,-.008),(.624,-.008),(.669,.035),(.682,.110),
                                      (.682,.154),(.650,.170),(.615,.170),(.600,.151),
                                      (.575,.127),(.545,.089),(.501,.059),(.497,.026),(.525,.002)]],
                m["wood"],close=False)
    bowl.location=(CX,CY,SURFACE)
    for name,outer,inner,z,mat in [
            ("Bowl | outside brass binding",.6827,.672,.126,"aged"),
            ("Bowl | polished rim",.653,.644,.171,"gold"),
            ("Bowl | ball track inner lip",.506,.497,.061,"aged")]:
        obj=ring(name,circle(32),outer,inner,z,m[mat],.005)
        obj.location=bowl.location
    # One object for all eight fixed lozenges on the sloping landing track.
    pieces=[]
    for i in range(8):
        a=2*pi*i/8+pi/8
        ob=cube("Track diamond",(CX+.552*cos(a),CY+.552*sin(a),SURFACE+.099),(.030,.030,.011),m["bone"])
        ob.rotation_euler=(0,-.50,a+pi/4)
        pieces.append(ob)
    merge(pieces,"Bowl | eight ivory deflectors")
    rotor=pivot("Roulette_Rotor",(CX,CY,SURFACE+.030))
    rotor["axis"]="Z"; rotor["pockets"]=COUNT; rotor["zero_direction"]="+Y"
    def attach(ob):
        ob.parent=rotor
        return ob
    # Centre the green pocket on +Y. All moving rings share identical sector boundaries.
    path=[(cos(pi/2+(i+.5)*2*pi/COUNT),sin(pi/2+(i+.5)*2*pi/COUNT)) for i in range(COUNT)]
    body=attach(band("Rotor | pocket bed",path,
                     [(r,r,z) for r,z in [(.493,0),(.493,.026),(.365,.027),(.352,.018),
                                           (.286,.018),(.269,.039),(.202,.066),(.136,.077)]],m["wood"]))
    bedmat=material("Pocket velvet","302b30",.9)
    body.data.materials.append(bedmat)
    for f in body.data.polygons:
        if 3*COUNT<=f.index<4*COUNT:
            f.material_index=1

    labels=material("Enamel | 37 wheel numbers","642f35",.48,texture="roulette-numbers")
    strip=attach(band("Rotor | single-zero number ring",path,
                      [(.491,.491,.023),(.491,.491,.029),(.359,.359,.030),(.359,.359,.024)],m["aged"],close=False))
    strip.data.materials.append(labels)
    uv=strip.data.uv_layers.active
    for f in strip.data.polygons[COUNT:2*COUNT]:
        f.material_index=1
        for loop in f.loop_indices:
            point=strip.data.vertices[strip.data.loops[loop].vertex_index].co
            uv.data[loop].uv=(.5+point.x/.982,.5+point.y/.982)

    # The pockets are recessed; all dividers are disconnected solid islands in one mesh.
    vertices,faces=[],[]
    for ux,uy in path:
        vx,vy=-uy,ux
        start=len(vertices)
        for z in (.017,.040):
            for r,t in ((.279,-.0045),(.362,-.005),(.362,.005),(.279,.0045)):
                vertices.append((r*ux+t*vx,r*uy+t*vy,z))
        faces.extend(tuple(start+i for i in face) for face in
                     [(3,2,1,0),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)])
    attach(mesh("Rotor | 37 brass pocket dividers",vertices,faces,[m["gold"]]))
    for name,rad,inner,z in [("Pocket inner rim",.282,.275,.043),("Number ring edging",.492,.487,.031),
                             ("Hub inlay",.192,.185,.070)]:
        attach(ring("Rotor | "+name,path,rad,inner,z,m["gold"],.004))
    attach(lathe("Rotor | spindle pedestal",[(.072,.14),(.086,.143),(.116,.089),(.125,.088),
                                               (.128,.054),(.183,.041)],m["gold"],12))
    attach(lathe("Rotor | dark spindle",[(.160,.033),(.240,.032),(.254,.043)],m["ebony"],8))
    arms=[]
    for angle in (pi/6,pi*2/3):
        obj=cube("Capstan crossbar",(0,0,.213),(.275,.021,.020),m["gold"])
        obj.rotation_euler.z=angle
        arms.append(obj)
        for sign in (-1,1):
            ob=lathe("Finial",[(0,.018),(.018,.021),(.028,.012)],m["bone"],8)
            ob.location=(sign*.132*cos(angle),sign*.132*sin(angle),.204)
            arms.append(ob)
    attach(merge(arms,"Rotor | brass capstan and ivory ends",origin=(0,0,0)))
    attach(lathe("Rotor | spindle finial",[(.237,.036),(.264,.038),(.291,.016)],m["gold"],8))
    ball_orbit=pivot("Roulette_Ball_Orbit",(CX,CY,SURFACE))
    bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=2,radius=.029)
    ball=bpy.context.object
    ball.name="Roulette_Ball"
    ball.parent=ball_orbit
    a=-pi*.66
    ball.location=(.591*cos(a),.591*sin(a),.165)
    ball.data.materials.append(material("Pale porcelain ball","F6EBCF",.30))
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.sphere_project()
    bpy.ops.object.mode_set(mode="OBJECT")

def merge(objects,name,origin=None):
    bpy.ops.object.select_all(action="DESELECT")
    for ob in objects: ob.select_set(True)
    bpy.context.view_layer.objects.active=objects[0]
    bpy.ops.object.join()
    result=bpy.context.object
    result.name=name
    if origin is not None:
        target=bpy.context.scene.cursor
        previous=target.location.copy()
        target.location=origin
        bpy.ops.object.origin_set(type="ORIGIN_CURSOR")
        target.location=previous
    return result

def betting_chips(m):
    # A printed chip stack is a single low-poly mesh, with one striped edge per layer.
    for idx,(x,y,n,ink) in enumerate([(.061,-.415,2,"red"),(1.033,.018,3,"ink"),(1.43,-.622,3,"green")]):
        sides=12; verts=[];faces=[];indices=[]
        for level in range(n):
            first=len(verts)
            for rad,z in [(.033,0),(.034,.002),(.034,.0105),(.0305,.012)]:
                for j in range(sides):
                    a=2*pi*j/sides+level*.14
                    verts.append((rad*cos(a),rad*sin(a),z+level*.0128))
            faces.append(tuple(first+j for j in reversed(range(sides))));indices.append(0)
            for k in range(3):
                for j in range(sides):
                    j2=(j+1)%sides;base=first+k*sides
                    faces.append((base+j,base+j2,base+sides+j2,base+sides+j));indices.append(1 if k==1 and j%3==0 else 0)
            faces.append(tuple(first+3*sides+j for j in range(sides)));indices.append(0)
        stack=mesh("Felt | sample wager %d"%(idx+1),verts,faces,[m[ink],m["bone"]],indices)
        stack.location=(x,y,SURFACE+.001)

def build(m):
    table_base(m)
    for obj in list(bpy.context.scene.objects):
        if obj.name=="Stretcher":
            for v in obj.data.vertices:v.co.x*=1.48
        elif obj.type=="MESH":
            obj.location.x*=1.48
    path=outline([24,27,29,30,30,30,30,30,30,30,30,29,27,24])
    band("Apron | walnut",path,[(.942,.915,.681),(.942,.915,.797)],m["wood"])
    band("Apron | ebonised lip",path,[(.978,.958,.770),(1,1,.812),(1,1,.846)],m["ebony"])
    face=material("Printed roulette baize","315649",.93,texture="roulette-layout")
    band("Felt | betting layout",path,[(.968,.937,.837),(.968,.937,SURFACE)],m["felt"],face,
         lambda x,y:(.5+x/4,.5+y/2))
    band("Rail | oxblood leather",path,[(1,1,.827),(1,1,.90625),(.957,.906,.90625),(.957,.906,.838)],
         m["wine"],close=False)
    ring("Rail | single line of brass",path,.970,.965,.907,m["aged"],.002)
    for x in (-1.25,1.25):
        for y in (-.832,.832):
            cube("Rail | brass stud",(x,y,.908),(.043,.040,.006),m["aged"])
    wheel(m)
    from build_gaming_floor_blender import reset_button
    reset_button(m,-.85,.70,SURFACE)
    betting_chips(m)
    # A croupier's rake rests on the far edge of the cloth.
    rake=[beam("Rake | shaft",(.08,.718,.867),(.92,.675,.867),.018,.018,m["wood"]),
          beam("Rake | brass collar",(.877,.677,.867),(.925,.675,.867),.023,.023,m["gold"]),
          beam("Rake | ivory head",(.917,.611,.866),(.924,.733,.866),.019,.021,m["bone"])]
    merge(rake,"Croupier rake")
