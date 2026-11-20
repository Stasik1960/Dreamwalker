"""Faceted wind-up racers. One manifold-island mesh per lane, in local track space."""
from math import cos, sin, pi
import bpy
from mathutils import Vector
from build_gaming_floor_blender import mesh, lathe, material

def add_rat(lane,pos,m,enamel):
    parts=[]
    def keep(obj):
        parts.append(obj)
        return obj
    bronze=m["aged"]
    black=m["ebony"]
    bright=m["gold"]
    warm=m["bone"]
    ear_mat=bpy.data.materials.get("Runner | ear copper") or material("Runner | ear copper","AD705B",.55,.18)

    def shell(name,sections,mats,pick=None,n=8):
        # Elliptical YZ sections follow the arched back all the way to the tapered snout.
        pts=[(x,cy+w*cos((j+.5)*2*pi/n),cz+h*sin((j+.5)*2*pi/n))
             for x,cy,cz,w,h in sections for j in range(n)]
        faces=[tuple(reversed(range(n)))];indices=[0]
        for i in range(len(sections)-1):
            for j in range(n):
                a=i*n+j;b=i*n+(j+1)%n
                faces.append((a,b,b+n,a+n))
                indices.append(pick(i,j) if pick else 0)
        faces.append(tuple(range(len(pts)-n,len(pts))));indices.append(0)
        return keep(mesh(name,pts,faces,mats,indices))

    sections=[(-.197,0,.143,.051,.055),(-.159,0,.157,.094,.084),(-.099,0,.165,.112,.089),
              (-.030,0,.156,.107,.090),(.031,0,.148,.092,.078),(.064,0,.151,.080,.067),
              (.114,0,.159,.081,.077),(.163,0,.142,.075,.064),(.211,0,.125,.049,.043),
              (.267,0,.108,.029,.025),(.293,0,.107,.017,.015)]
    def hull_colour(i,j):
        if j in (4,5,6) and i<6:return 1
        if i>=8:return 2
        return 0
    shell("Runner | enamel hull",sections,[enamel,bronze,bright],hull_colour)
    # A brass band follows the existing cross section rather than raising an extra dome.
    shell("Runner | spring housing band",[(-.044,0,.158,.111,.094),(-.030,0,.157,.111,.094)],[bright])

    def cap(name,at,dims,mat):
        x,y,z=at;dx,dy,dz=dims
        return shell(name,[(x-dx,y,z,dy*.46,dz*.50),(x-dx*.30,y,z,dy,dz),
                           (x+dx*.5,y,z,dy*.84,dz*.81),(x+dx,y,z,dy*.30,dz*.4)],[mat])
    cap("Runner | black nose",(.298,0,.107),(.019,.023,.018),black)

    def disc(name,at,r,depth,mat,n=10):
        obj=keep(lathe(name,[(-depth/2,r),(depth/2,r)],mat,n))
        obj.rotation_euler.x=pi/2
        obj.location=at
        return obj
    for side in (-1,1):
        ear=disc("Runner | ear rim",(.074,side*.068,.224),.043,.014,bright,10)
        ear.rotation_euler.z=-side*.22
        inner=disc("Runner | copper ear",(.075,side*.076,.225),.031,.006,ear_mat,10)
        inner.rotation_euler.z=ear.rotation_euler.z
        cap("Runner | onyx eye",(.157,side*.061,.174),(.017,.013,.017),black)
        cap("Runner | eye glint",(.163,side*.072,.181),(.005,.003,.004),warm)

    def tube(name,points,radii,mat,sides=6):
        points=[Vector(p) for p in points];verts=[];faces=[]
        for i,p in enumerate(points):
            tangent=(points[min(len(points)-1,i+1)]-points[max(0,i-1)]).normalized()
            up=Vector((0,0,1)) if abs(tangent.z)<.9 else Vector((0,1,0))
            u=tangent.cross(up).normalized();v=tangent.cross(u).normalized()
            for j in range(sides):
                ang=2*pi*j/sides
                verts.append(tuple(p+radii[i]*(u*cos(ang)+v*sin(ang))))
        faces.append(tuple(reversed(range(sides))))
        for i in range(len(points)-1):
            for j in range(sides):
                a=i*sides+j;b=i*sides+(j+1)%sides
                faces.append((a,b,b+sides,a+sides))
        faces.append(tuple(range(len(verts)-sides,len(verts))))
        return keep(mesh(name,verts,faces,[mat]))
    bend=(1,-1,1,-1)[lane-1]
    tube("Runner | tapering articulated tail",
         [(-.166,0,.151),(-.21,-.014*bend,.130),(-.271,-.049*bend,.089),
          (-.333,-.057*bend,.067),(-.399,-.033*bend,.077),(-.456,.004*bend,.105)],
         [.023,.021,.018,.013,.009,.004],bright)

    for side in (-1,1):
        # Narrow black drive wheels touch the rail underneath the articulated feet.
        for x in (-.12,.096):
            disc("Runner | guide wheel",(x,side*.046,.033),.033,.024,black,8)
        y=side*.088
        tube("Runner | hind leg",[(-.139,y,.137),(-.093,y,.087),(-.154,y,.038)],
             [.028,.021,.012],bronze,5)
        tube("Runner | foreleg",[(.082,y,.124),(.056,y,.073),(.110,y,.036)],
             [.022,.016,.010],bright,5)
        cap("Runner | hind paw",(-.133,y,.035),(.042,.019,.012),bronze)
        cap("Runner | front paw",(.127,y,.034),(.031,.017,.010),bronze)
        disc("Runner | haunch pivot",(-.134,side*.106,.145),.021,.010,bright,8)
    key_x=-.098
    tube("Runner | key stem",[(key_x,0,.243),(key_x,0,.278),(key_x,0,.293)],
         [.012,.012,.017],bright,8)
    # Winding-key bows have real open holes. The two flat brass loops meet the central stem.
    for direction in (-1,1):
        cx=key_x+direction*.030;cz=.296
        pts=[];faces=[];n=8
        for y,scale in ((-.008,1),(.008,1),(.008,.52),(-.008,.52)):
            for j in range(n):
                a=2*pi*j/n
                pts.append((cx+.035*scale*cos(a),y,cz+.026*scale*sin(a)))
        for ring in range(4):
            for j in range(n):
                a=ring*n+j;b=ring*n+(j+1)%n;c=((ring+1)%4)*n+(j+1)%n;d=((ring+1)%4)*n+j
                faces.append((a,d,c,b))
        keep(mesh("Runner | winding key bow",pts,faces,[bright]))
    bpy.ops.object.select_all(action="DESELECT")
    for p in parts:p.select_set(True)
    bpy.context.view_layer.objects.active=parts[0]
    bpy.ops.object.join()
    root=bpy.context.object
    root.name=f"Rat_{lane:02d}"
    root.data.name=root.name+" | wind-up runner"
    root.location=pos
    return root
