"""Blender 4.5+: blender -b --python tools/build_slots.py -- [--no-render]."""
from collections import Counter
from math import cos, pi, sin
from pathlib import Path
from uuid import NAMESPACE_URL, uuid5
import json
import struct
import sys

import bpy
from mathutils import Vector

sys.path.insert(0, str(Path(__file__).resolve().parent))
from styles import THEMES

OUT = Path(__file__).resolve().parents[1]
TEX = OUT / "textures"
MODELS = OUT / "models"
PREVIEWS = OUT / "previews"
BUILD = OUT / ".build"
FPS, END = 30, 116


def rgb(code):
    return tuple(int(code[i:i + 2], 16) / 255 for i in (0, 2, 4))


def linear(c):
    return c / 12.92 if c <= .04045 else ((c + .055) / 1.055) ** 2.4


def material(name, code, metallic=0, rough=.4, emission=0, path=None):
    color = tuple(map(linear, rgb(code)))
    mat = bpy.data.materials.new(name)
    mat.diffuse_color = (*color, 1)
    mat.use_nodes = True
    tree = mat.node_tree
    p = tree.nodes.get("Principled BSDF")
    p.inputs["Base Color"].default_value = (*color, 1)
    p.inputs["Metallic"].default_value = metallic
    p.inputs["Roughness"].default_value = rough
    if emission:
        p.inputs["Emission Color"].default_value = (*color, 1)
        p.inputs["Emission Strength"].default_value = emission
    if path:
        node = tree.nodes.new("ShaderNodeTexImage")
        node.image = bpy.data.images.load(str(path), check_existing=True)
        node.image.pack()
        tree.links.new(node.outputs["Color"], p.inputs["Base Color"])
        if emission:
            tree.links.new(node.outputs["Color"], p.inputs["Emission Color"])
    return mat


class Cabinet:
    def __init__(self, style):
        self.id = style
        self.title, self.colors, self.finishes = THEMES[style]
        self.collection = bpy.data.collections.new(self.title)
        bpy.context.scene.collection.children.link(self.collection)
        self.root = self.node(style)
        self.root["front"] = "-Y (Blender), +Z (glTF / Blockbench)"
        self.static = self.node("cabinet", parent=self.root)
        self.mats = {}
        for role, color in self.colors.items():
            finish = self.finishes.get(role)
            metallic = .57 if role in ("trim", "bright", "hardware") else .06
            roughness = .32 if finish == "glass" else .43 if metallic > .1 else .67
            emission = .94 if role == "light" else .12 if finish == "glass" else 0
            path = TEX / "surfaces" / f"{style}_{role}.png" if finish else None
            self.mats[role] = material(f"{self.title} / {role}", color, metallic, roughness, emission, path)
        self.mats["reel"] = material(self.title + " / reel strip", "FFFFFF", 0, .62, .51,
                                    TEX / (style + "_reel.png"))
        self.mats["sign"] = material(self.title + " / marquee", "FFFFFF", 0, .55, .46,
                                    TEX / (style + "_marquee.png"))
        self.mat_key = {m.name: k for k, m in self.mats.items()}
        self.reels = []

    def node(self, name, loc=(0, 0, 0), parent=None):
        o = bpy.data.objects.new(name, None)
        self.collection.objects.link(o)
        o.parent, o.location = parent, loc
        o.empty_display_type = "PLAIN_AXES"
        o.empty_display_size = .05
        return o

    def mesh(self, name, points, faces, color, parent=None, loc=(0, 0, 0),
             bevel=0, uvs=None, per_face=None, smooth_through=0):
        mesh = bpy.data.meshes.new(name)
        mesh.from_pydata(points, [], faces)
        if mesh.validate():
            raise ValueError("Invalid generated mesh: " + name)
        mesh.update()
        o = bpy.data.objects.new(name, mesh)
        self.collection.objects.link(o)
        o.parent, o.location = parent or self.static, loc
        used = list(dict.fromkeys(per_face or [color]))
        for key in used:
            mesh.materials.append(self.mats[key])
        textured = bool(set(used) & self.finishes.keys())
        layer = mesh.uv_layers.new(name="UVMap") if uvs or textured else None
        if textured:
            minimum = [min(v[j] for v in points) for j in range(3)]
            maximum = [max(v[j] for v in points) for j in range(3)]
            spans = [max(.01, high-low) for low, high in zip(minimum, maximum)]
        for i, polygon in enumerate(mesh.polygons):
            role = per_face[i] if per_face else color
            polygon.material_index = used.index(role)
            polygon.use_smooth = i < smooth_through
            if role in self.finishes:
                axis = max(range(3), key=lambda j: abs(polygon.normal[j]))
                u, v = ((1,2),(0,2),(0,1))[axis]
                for index in polygon.loop_indices:
                    point = points[mesh.loops[index].vertex_index]
                    layer.data[index].uv = (
                        .035 + .93*(point[u]-minimum[u])/spans[u],
                        .035 + .93*(point[v]-minimum[v])/spans[v],
                    )
            elif uvs:
                for j, loop in enumerate(polygon.loop_indices):
                    layer.data[loop].uv = uvs[i][j]
        if bevel:
            mod = o.modifiers.new("Edge bevel", "BEVEL")
            mod.width, mod.segments = bevel, 1
        return o

    def box(self, name, loc, size, color, bevel=.008, parent=None):
        x, y, z = (s / 2 for s in size)
        pts = [(-x,-y,-z),(x,-y,-z),(x,y,-z),(-x,y,-z),
               (-x,-y,z),(x,-y,z),(x,y,z),(-x,y,z)]
        return self.mesh(name, pts, [(3,2,1,0),(0,1,5,4),(1,2,6,5),
                         (2,3,7,6),(3,0,4,7),(4,5,6,7)], color, parent, loc, bevel)

    def profile(self, name, points, lo, hi, color, plane="XZ", bevel=.007):
        """Counterclockwise 2D profile. XZ spans front to back; YZ spans left to right."""
        if plane == "XZ":
            verts = [(a, y, b) for y in (hi, lo) for a, b in points]
        else:
            verts = [(x, a, b) for x in (lo, hi) for a, b in points]
        n = len(points)
        faces = [tuple(reversed(range(n))), tuple(range(n, 2*n))]
        faces.extend((i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n))
        return self.mesh(name, verts, faces, color, bevel=bevel)

    def rod(self, name, a, b, r, color, n=10, parent=None, tip=None):
        a, b = Vector(a), Vector(b)
        delta = b - a
        rotation = delta.to_track_quat("Z", "Y")
        rr = r if tip is None else tip
        pts = [tuple(rotation @ Vector((rad*cos(2*pi*i/n),rad*sin(2*pi*i/n),z)))
               for rad, z in ((r, 0), (rr, delta.length)) for i in range(n)]
        faces = [tuple(reversed(range(n))), tuple(range(n, 2*n))]
        faces.extend((i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n))
        return self.mesh(name, pts, faces, color, parent, a, min(r, rr, .021)*.24)

    def stick(self, name, a, b, thickness, color):
        direction = Vector(b) - Vector(a)
        o = self.box(name, (Vector(a)+Vector(b))/2, (thickness, thickness, direction.length),
                     color, thickness*.2)
        o.rotation_euler = direction.to_track_quat("Z","Y").to_euler()
        return o

    def artwork(self, center, size):
        x, y, z = center
        w, h = (s/2 for s in size)
        self.mesh("Printed marquee", [(x-w,y,z-h),(x+w,y,z-h),(x+w,y,z+h),(x-w,y,z+h)],
                  [(0,1,2,3)], "sign", uvs=[[(0,0),(1,0),(1,1),(0,1)]])

    def ring(self, name, center, outer, inner, depth, color, sides=24, horizontal=False):
        """Make a closed ring in XZ or on a side panel in YZ."""
        x, y, z = center
        vertices = []
        for level in (depth/2, -depth/2):
            for radius in (outer,inner):
                for index in range(sides):
                    angle = 2*pi*index/sides
                    a,b = radius*cos(angle),radius*sin(angle)
                    vertices.append((x-level,y+a,z+b) if horizontal else (x+a,y+level,z+b))
        n = sides
        faces = []
        for i in range(n):
            j = (i+1)%n
            faces.extend(((i,j,j+2*n,i+2*n), (i+n,i+3*n,j+3*n,j+n),
                          (i,i+n,j+n,j), (i+2*n,j+2*n,j+3*n,i+3*n)))
        return self.mesh(name,vertices,faces,color,bevel=.0018)

    def gem(self, name, center, width, height, depth, color, parent=None):
        """Double pyramidal cabochon with four light-catching facets per side."""
        w,h = width/2,height/2
        return self.mesh(name,[(0,0,-h),(w,0,0),(0,0,h),(-w,0,0),(0,-depth,0),(0,depth,0)],
                         [(i,(i+1)%4,4) for i in range(4)]+
                         [((i+1)%4,i,5) for i in range(4)],color,parent,center)

    def pointed_frame(self, name, x, y, base, shoulder, crown, half, color, width):
        right = [(x+half,y,base),(x+half,y,shoulder),
                 (x+half*.74,y,shoulder+(crown-shoulder)*.52),
                 (x+half*.37,y,shoulder+(crown-shoulder)*.81),(x,y,crown)]
        left = [(2*x-px,py,pz) for px,py,pz in reversed(right[:-1])]
        for a,b in zip(right+left,(right+left)[1:]):
            self.stick(name,a,b,width,color)

    def drum(self, i, center, radius=.24, width=.154):
        axle = self.node(f"reel_{i+1:02}", center, self.root)
        axle["role"] = "spin"
        axle["axis"] = "+X"
        axle["starting_symbol_index"] = i
        self.reels.append(axle)
        n = 48
        verts = []
        for x in (-width/2, width/2):
            for j in range(n):
                theta = (j/n - .0625 - i/8) * 2*pi
                verts.append((x, -radius*cos(theta), -radius*sin(theta)))
        faces, uv = [], []
        for j in range(n):
            jj = (j+1) % n
            faces.append((j,jj,n+jj,n+j))
            uv.append(((0,1-j/n),(0,1-(j+1)/n),(1,1-(j+1)/n),(1,1-j/n)))
        faces.extend((tuple(reversed(range(n))), tuple(range(n,2*n))))
        uv.extend([[(0,0)]*n]*2)
        self.mesh("Textured reel", verts, faces, "reel", axle, uvs=uv,
                  per_face=["reel"]*n+["hardware"]*2, smooth_through=n)

    def reels_and_controls(self, z, y, deck, lever, control):
        b = self.box
        # The recess is made of four opaque rails; no plane occludes the animated drums.
        b("Recess back", (0,y+.11,z), (.695,.024,.315), "dark")
        # Face panels mask the circumference outside the window, including at steep angles.
        b("Upper window blind", (0,y-.016,z+.202), (.641,.052,.157), "body", .009)
        b("Lower window blind", (0,y-.010,z-.186), (.640,.048,.112), "body", .009)
        for i, x in enumerate((-.177, 0, .177)):
            self.drum(i, (x,y+.205,z))
        for x in (-.284, -.0885, .0885, .284):
            b("Reel window mullion", (x,y-.038,z), (.020 if abs(x)<.1 else .068,.052,.244), "dark")
            if abs(x) > .1:
                b("Vertical frame inlay", (x,y-.066,z), (.014,.012,.247), "trim", .002)
        for sign in (-1,1):
            railz = z + sign*.129
            b("Window ledge", (0,y-.035,railz), (.676,.087,.064), "dark")
            b("Window trim", (0,y-.081,railz), (.640,.016,.017), "trim", .004)
            x = sign*.266
            pts = [(x+.012*sign,z+.014),(x-.014*sign,z),(x+.012*sign,z-.014)]
            self.profile("Payline pointer", pts if sign == 1 else pts[::-1],
                         y-.075,y-.063,"light" if self.id == "astral_choir" else "accent",bevel=0)
        dy, dz = deck
        b("Control plate", (0,dy,dz), (.638,.232,.071), "hardware", .016)
        b("Control deck lip", (0,dy-.111,dz-.004), (.633,.026,.037), "trim")
        for x, r, c in ((-.197,.026,"accent"),(-.115,.023,"bright"),(.113,.034,"light")):
            self.rod("Button seat", (x,dy-.020,dz+.024),(x,dy-.020,dz+.042),r+.010,"dark")
            if control == "stone":
                b("Carved action key",(x,dy-.020,dz+.045),(r*1.5,r*1.45,.026),c,.006)
            else:
                self.rod("Action button",(x,dy-.020,dz+.036),(x,dy-.020,dz+.056),r,c,12)
        b("Token plate", (.244,dy-.010,dz+.039), (.080,.055,.018), "trim", .006)
        b("Token slot", (.244,dy-.010,dz+.049), (.052,.009,.005), "dark", .002)
        ty, tz = dy+.035, .283
        b("Payout recess",(0,ty,tz),(.398,.072,.139),"dark",.018)
        b("Payout tray",(0,ty-.092,tz-.057), (.453,.208,.046),"trim",.015)
        b("Payout tray liner",(0,ty-.092,tz-.031),(.396,.151,.012),"dark",.004)
        b("Payout tray lip",(0,ty-.192,tz-.025),(.440,.024,.060),"hardware",.007)
        for x in (-.209, .209):
            b("Payout tray wall",(x,ty-.104,tz-.027),(.024,.163,.056),"hardware",.004)
        # Lever axis points through a fixed hub on the right side.
        lx, ly, lz = lever
        self.rod("Fixed lever bearing",(lx-.065,ly,lz),(lx+.008,ly,lz),.071,"hardware",14)
        self.rod("Lever bearing cap",(lx+.008,ly,lz),(lx+.022,ly,lz),.047,"trim",12)
        o = self.node("lever", (lx+.020,ly,lz), self.root)
        self.lever = o
        o["role"], o["axis"] = "pull", "+X"
        self.rod("Lever stem",(0,0,0),(0,.045,.344),.013,"bright",10,parent=o)
        self.rod("Grip collar",(0,.042,.315),(0,.048,.360),.022,"trim",10,parent=o)
        if self.id == "crimson_oath":
            self.gem("Garnet grip", (0,.048,.386), .092, .114, .042, "accent", o)
        else:
            knob = "bright" if self.id == "astral_choir" else "panel"
            self.rod("Grip base",(0,.048,.340),(0,.048,.365),.028,"trim",12,parent=o,tip=.045)
            self.rod("Grip equator",(0,.048,.365),(0,.048,.393),.045,knob,12,parent=o)
            self.rod("Grip top",(0,.048,.393),(0,.048,.414),.045,knob,12,parent=o,tip=.026)

    def hunters_wager(self):
        b = self.box
        for x in (-.327,.327):
            for y in (-.174,.223):
                self.rod("Cast foot",(x,y,.008),(x,y,.089),.073,"hardware",8,tip=.058)
                b("Sole",(x,y,.016),(.157,.162,.032),"dark",.007)
        b("Iron sill",(0,.035,.106),(.865,.600,.091),"hardware",.018)
        b("Oak foundation",(0,.039,.177),(.800,.543,.063),"body",.013)
        b("Rounded brass edge",(0,-.259,.149),(.758,.025,.023),"trim",.005)
        cabinet = [(-.363,.189),(.363,.189),(.363,1.436),(.308,1.588),
                   (.241,1.658),(-.241,1.658),(-.308,1.588),(-.363,1.436)]
        self.profile("Oiled oak chassis",cabinet,-.198,.281,"body",bevel=.017)
        hood = [(-.321,1.443),(.321,1.443),(.315,1.552),(.255,1.675),(.125,1.763),
                (-.125,1.763),(-.255,1.675),(-.315,1.552)]
        self.profile("Foundry hood",hood,-.265,.236,"hardware",bevel=.011)
        self.pointed_frame("Rim of hood",0,-.277,1.639,1.649,1.779,.246,"trim",.018)
        b("Brass title frame",(0,-.283,1.507),(.661,.049,.201),"trim",.014)
        self.artwork((0,-.309,1.507),(.623,.167))
        for side in (-1,1):
            x = side*.336
            b("Corner casting",(x,-.203,.810),(.074,.102,1.244),"hardware",.013)
            for z in (.241,.381,.572,.740,1.082,1.363,1.441):
                self.rod("Rivet",(x,-.250,z),(x,-.265,z),.012,"trim",8)
            self.rod("Copper intake pipe",(side*.363,.217,.333),(side*.363,.217,1.386),
                     .033,"trim",12)
            for z in (.408,1.204,1.337):
                self.rod("Pipe collar",(side*.363,.217,z-.020),(side*.363,.217,z+.020),
                         .045,"hardware",10)
            for j in range(4):
                y = -.126+.073*j
                b("Side oak plank",(side*.365,y,.637),(.026,.070,.803),
                  "slat" if j%2 else "panel",.003)
            for z in (.257,1.110):
                b("Side mounting strap",(side*.378,.033,z),(.021,.371,.033),"hardware",.004)
        for x in (-.108,-.035,.035,.108):
            b("Vent slot",(x,-.267,.680),(.036,.021,.069),"dark",.007)
        b("Cast service door",(0,-.215,.473),(.559,.074,.459),"hardware",.015)
        b("Service door inset",(0,-.256,.512),(.433,.020,.310),"panel",.010)
        for x in (-.18,.18):
            self.stick("Door reinforcing brace",(x,-.273,.368),(0,-.273,.660),.017,"trim")
        self.ring("Service pull",(0,-.294,.555),.038,.025,.020,"bright",16)
        x, y, z = .394,.103,.829
        self.ring("Exposed gear",(x,y,z),.128,.092,.030,"hardware",24,horizontal=True)
        for i in range(12):
            angle = 2*pi*i/12
            yy, zz = y+.126*cos(angle), z+.126*sin(angle)
            self.stick("Gear tooth",(x,yy,zz),(x,yy+.029*cos(angle),zz+.029*sin(angle)),.026,"trim")
            if i%3 == 0:
                self.stick("Gear spoke",(x,y,z),(x,y+.105*cos(angle),z+.105*sin(angle)),.019,"trim")
        self.rod("Gear axle",(x-.034,y,z),(x+.027,y,z),.048,"hardware",12)
        b("Lantern pedestal",(0,-.148,1.724),(.228,.232,.068),"hardware",.011)
        self.rod("Amber lantern core",(0,-.148,1.744),(0,-.148,1.893),.070,"light",8)
        for x in (-.077,.077):
            for y in (-.223,-.072):
                self.stick("Lantern cage",(x,y,1.754),(x*.69,-.148+(y+.148)*.69,1.889),.017,"trim")
        self.rod("Lantern cap",(0,-.148,1.875),(0,-.148,1.944),.108,"hardware",8,tip=.042)
        self.rod("Lantern finial",(0,-.148,1.939),(0,-.148,1.969),.026,"trim",8,tip=.012)
        self.reels_and_controls(1.118,-.236,(-.270,.779),(.408,-.050,.897),"round")

    def astral_choir(self):
        b = self.box
        b("Rough hewn step",(0,.039,.044),(.879,.640,.088),"body",.016)
        b("Foundation molding",(0,.033,.113),(.834,.592,.060),"panel",.014)
        b("Foot of chapel",(0,.037,.173),(.762,.539,.080),"body",.013)
        self.profile("Limestone chapel",[(-.349,.149),(.349,.149),(.349,1.529),
                     (.252,1.708),(0,1.970),(-.252,1.708),(-.349,1.529)],
                     -.202,.302,"body",bevel=.013)
        for side in (-1,1):
            x=side*.353
            points = [(-.228,.189),(.267,.189),(.267,1.207),(.183,1.528),
                      (.101,1.382),(-.050,.603),(-.188,.442)]
            self.profile("Shouldered buttress",points,*sorted((side*.322,side*.426)),
                         "panel","YZ",.012)
            for z, size in ((.255,.362),(.424,.271),(.610,.209),(1.134,.178)):
                b("Side stone course",(side*.414,.152,z),(.034,size,.021),"slat",.004)
            b("Pilaster",(x,-.209,.707),(.079,.092,1.068),"slat",.011)
            for z in (.208,.462,.713,.974,1.214):
                b("Pilaster course",(x,-.264,z),(.083,.023,.016),"panel",.006)
            self.rod("Corner spire",(x,-.113,1.340),(x,-.113,1.705),.068,"body",6,tip=.035)
            self.rod("Verdigris spire cap",(x,-.113,1.669),(x,-.113,1.844),.047,"trim",6,tip=.007)
            self.gem("Beacon on corner spire",(x,-.113,1.742),.039,.075,.021,"light")
        ogive = [(-.30,1.360),(.30,1.360),(.30,1.586),(.216,1.714),(.101,1.841),
                 (0,1.929),(-.101,1.841),(-.216,1.714),(-.30,1.586)]
        self.profile("Chapel pediment",ogive,-.261,-.186,"dark",bevel=.008)
        self.pointed_frame("Carved gable molding",0,-.274,1.363,1.585,1.940,.314,"panel",.026)
        self.pointed_frame("Inner copper gable line",0,-.284,1.413,1.579,1.904,.284,"trim",.010)
        self.rod("Rose window glass",(0,-.268,1.724),(0,-.281,1.724),.116,"extra",24)
        self.ring("Outer rose tracery",(0,-.294,1.724),.125,.105,.025,"bright",24)
        self.ring("Inner rose tracery",(0,-.307,1.724),.058,.049,.016,"trim",20)
        for index in range(8):
            angle = index*pi/4
            x, z = cos(angle),sin(angle)
            self.stick("Rose leadwork",(.056*x,-.309,1.724+.056*z),
                       (.110*x,-.303,1.724+.110*z),.009,"trim")
            self.gem("Opal in rose window",(.081*x,-.305,1.724+.081*z),.034,.044,.013,"accent")
        self.gem("Central opal",(0,-.320,1.724),.055,.078,.020,"light")
        b("Incised title tablet",(0,-.277,1.481),(.622,.047,.191),"slat",.012)
        self.artwork((0,-.302,1.481),(.591,.158))
        b("Altar front",(0,-.217,.527),(.570,.042,.560),"slat",.012)
        for x in (-.167,0,.167):
            base, shoulder, crown, half = .322,.560,.711,.072
            self.profile("Recessed lancet",[(x-half,base),(x+half,base),(x+half,shoulder),
                         (x+.054,.640),(x,crown),(x-.054,.640),(x-half,shoulder)],
                         -.250,-.232,"dark",bevel=.002)
            self.pointed_frame("Chapel lancet",x,-.256,base,shoulder,crown,half,"panel",.015)
            self.stick("Lancet mullion",(x,-.264,base+.009),(x,-.264,crown-.016),.009,"trim")
            self.gem("Lancet glass pane",(x,-.261,.530),.083,.198,.008,"accent")
        self.reels_and_controls(1.122,-.237,(-.280,.778),(.407,-.010,.883),"stone")

    def crimson_oath(self):
        b = self.box
        frame = [(-.264,.157),(.264,.157),(.340,.527),(.385,1.317),(.385,1.467),
                 (.292,1.663),(.196,1.823),(.097,1.904),(-.097,1.904),
                 (-.196,1.823),(-.292,1.663),(-.385,1.467),(-.385,1.317),(-.340,.527)]
        self.profile("Faceted reliquary",frame,-.212,.276,"body",bevel=.022)
        inset = [(x*.904,.98+(z-.98)*.962) for x,z in frame]
        self.profile("Oxblood lacquer lid",inset,-.247,-.207,"panel",bevel=.012)
        b("Reliquary base",(0,.030,.061),(.788,.620,.122),"body",.027)
        b("Silver band on plinth",(0,.033,.129),(.728,.568,.032),"trim",.008)
        b("Plinth onyx molding",(0,.034,.173),(.656,.526,.068),"hardware",.015)
        for side in (-1,1):
            for (ax,az),(bx,bz) in zip(frame[1:8],frame[2:9]):
                self.stick("Silver edge",(side*ax,-.246,az),(side*bx,-.246,bz),.024,"trim")
            x=side*.316
            side_profile=[(-.182,.435),(.241,.435),(.241,.960),(.194,1.238),
                          (.027,1.539),(-.148,1.284),(-.182,1.124)]
            self.profile("Side velvet field",side_profile,*sorted((x,x+side*.037)),
                         "extra","YZ",.009)
            for j in range(3):
                y=-.11+.067*j
                self.stick("Side silver ribs",(side*.361,y,.676),(side*.375,y+.025,1.236),.012,"trim")
            for z in (.803,1.100,1.336,1.450):
                self.rod("Silver rosette nail",(side*.365,-.254,z), (side*.365,-.274,z),
                         .015,"bright",8)
        lower = [(-.215,.267),(.215,.267),(.274,.618),(.234,.721),(-.234,.721),(-.274,.618)]
        self.profile("Silver frame of crimson velvet",lower,-.270,-.240,"trim",bevel=.010)
        self.profile("Crimson velvet door",[(x*.935,.494+(z-.494)*.939) for x,z in lower],
                     -.277,-.265,"extra",bevel=.008)
        for row in range(3):
            z = .403+row*.089
            for col in (-1,0,1):
                x = col*.145+(.040 if row%2 else 0)
                self.gem("Velvet tuft",(x,-.279,z),.123,.104,.013,
                         "accent" if (row+col)%2 else "extra")
                self.rod("Tuft stud",(x,-.291,z),(x,-.300,z),.007,"trim",8)
        title = [(-.321,1.421),(.321,1.421),(.346,1.510),(.319,1.609),(.263,1.632),
                 (-.263,1.632),(-.319,1.609),(-.346,1.510)]
        self.profile("Silver title escutcheon",title,-.284,-.244,"trim",bevel=.010)
        self.artwork((0,-.286,1.526),(.609,.175))
        for side in (-1,1):
            x=side*.200
            flourish=[(x,-.261,1.671),(side*.140,-.261,1.657),(side*.109,-.261,1.705),
                      (side*.150,-.261,1.782),(side*.094,-.261,1.795)]
            for a,bound in zip(flourish,flourish[1:]):
                self.stick("Silver scroll",a,bound,.016,"bright")
            self.gem("Crown thorn",(side*.097,-.268,1.864),.042,.118,.016,"trim")
        self.gem("Silver garnet mount",(0,-.266,1.750),.193,.234,.020,"trim")
        self.gem("Reliquary garnet",(0,-.287,1.750),.124,.167,.049,"accent")
        self.gem("Garnet crest finial",(0,-.173,1.903),.064,.150,.026,"bright")
        self.reels_and_controls(1.120,-.267,(-.302,.783),(.413,-.051,.874),"round")

    def animate(self):
        scene = bpy.context.scene
        scene.frame_start, scene.frame_end, scene.render.fps = 1, END, FPS
        for f, deg in ((1,0),(8,-4),(22,57),(27,58),(37,0),(42,-3),(49,0),(END,0)):
            self.lever.rotation_euler.x = deg*pi/180
            self.lever.keyframe_insert("rotation_euler", index=0,frame=f,group="Lever")
        for i, r in enumerate(self.reels):
            start, finish = 20+3*i, 71+13*i
            final = -2*pi*(2+i) + (i-5)*pi/4
            # Integrated speed ramp with zero velocity at both ends.
            def travel(t):
                if t < .17:
                    return t*t/(2*.17*.775)
                if t < .72:
                    return (t-.085)/.775
                return 1 - (1-t)**2/(2*.28*.775)
            for f in range(1,END+1):
                t = max(0, min(1, (f-start)/(finish-start)))
                r.rotation_euler.x = final * travel(t)
                r.keyframe_insert("rotation_euler",index=0,frame=f,group=f"Reel {i+1}")
        scene.frame_set(1)


def ident(key):
    return str(uuid5(NAMESPACE_URL, "bloodborne-slots/" + key))


def game_coords(v):
    """Sixteen Blockbench units equal one Minecraft block."""
    return [round(v[0]*16, 5), round(v[2]*16, 5), round(-v[1]*16, 5)]


def blender_report(cabinet):
    deps = bpy.context.evaluated_depsgraph_get()
    stats = {"mesh_objects": 0, "triangles": 0, "open_meshes": [], "pivot_positions_blender": {}}
    mins, maxs = [float("inf")]*3, [float("-inf")]*3
    for o in cabinet.collection.objects:
        if o.type != "MESH":
            if o in [cabinet.lever,*cabinet.reels]:
                stats["pivot_positions_blender"][o.name] = [round(x,3) for x in o.location]
            continue
        stats["mesh_objects"] += 1
        eo = o.evaluated_get(deps)
        mesh = eo.to_mesh()
        mesh.calc_loop_triangles()
        stats["triangles"] += len(mesh.loop_triangles)
        edges = Counter(edge for poly in mesh.polygons for edge in poly.edge_keys)
        if any(n != 2 for n in edges.values()):
            stats["open_meshes"].append(o.name)
        for v in mesh.vertices:
            p = eo.matrix_world @ v.co
            for i in range(3):
                mins[i], maxs[i] = min(p[i],mins[i]),max(p[i],maxs[i])
        eo.to_mesh_clear()
    stats["bounds_m"] = [[round(n,4) for n in bounds] for bounds in (mins,maxs)]
    assert len(stats["open_meshes"]) == 1 and stats["open_meshes"][0].startswith("Printed marquee"), stats
    assert maxs[0]-mins[0] <= 1 and maxs[1]-mins[1] <= 1 and maxs[2]-mins[2] <= 2
    assert len(stats["pivot_positions_blender"]) == 4
    return stats


def export_game(cabinet):
    info = bpy.ops.export_scene.gltf.get_rna_type().properties
    target = MODELS / (cabinet.id+".glb")
    opts = dict(filepath=str(target), export_format="GLB",
                use_selection=True, export_animations=True, export_extras=True,
                export_animation_mode="SCENE", export_frame_range=True, export_force_sampling=True,
                export_anim_scene_split_object=False)
    if "export_bake_animation" in info:
        opts["export_bake_animation"] = True
    bpy.ops.object.select_all(action="DESELECT")
    for o in cabinet.collection.objects:
        o.select_set(True)
    bpy.context.view_layer.objects.active = cabinet.root
    bpy.context.scene.name = "pull_and_spin"
    if bpy.ops.export_scene.gltf(**opts) != {"FINISHED"}:
        raise RuntimeError("Failed GLB export: " + cabinet.id)
    data = target.read_bytes()
    magic, version, _ = struct.unpack_from("<4sII", data, 0)
    size, kind = struct.unpack_from("<I4s", data, 12)
    assert (magic, version, kind) == (b"glTF", 2, b"JSON")
    gltf = json.loads(data[20:20+size])
    moving = {"lever", "reel_01", "reel_02", "reel_03"}
    for node in gltf["nodes"]:
        short = node.get("name","").split(".")[0]
        if short in moving | {"cabinet"}:
            node["name"] = short
    assert len(gltf.get("animations",[])) == 1, cabinet.id
    for clip in gltf["animations"]:
        clip["name"] = "pull_and_spin"
        animated = {gltf["nodes"][c["target"]["node"]]["name"] for c in clip["channels"]
                    if c["target"]["path"] == "rotation"}
        assert moving <= animated, (cabinet.id, animated)
    payload = json.dumps(gltf, separators=(",",":")).encode()
    payload += b" "*(-len(payload)%4)
    remainder = data[20+size:]
    target.write_bytes(struct.pack("<4sIII4s", magic, version, 20+len(payload)+len(remainder),
                                   len(payload), kind) + payload + remainder)

    deps = bpy.context.evaluated_depsgraph_get()
    game_model = {"id":cabinet.id, "name":cabinet.title, "elements":[], "groups":{},
                  "colors":cabinet.colors, "surfaces":list(cabinet.finishes)}
    o_id = {o:ident(cabinet.id+"/"+o.name) for o in cabinet.collection.objects}
    for o in cabinet.collection.objects:
        if o.type == "EMPTY":
            game_model["groups"][o_id[o]] = {
                "name": o.name.removesuffix(".001").removesuffix(".002"),
                "origin": game_coords(o.matrix_world.translation),
                "children": [o_id[ch] for ch in o.children], "uuid": o_id[o],
            }
            continue
        eo = o.evaluated_get(deps)
        mesh = eo.to_mesh()
        mesh.calc_loop_triangles()
        # Bake model rotations, leaving the outliner group axes independent.
        origin = eo.matrix_world.translation
        verts = {f"v{i}": game_coords(eo.matrix_world @ v.co - origin) for i,v in enumerate(mesh.vertices)}
        obj = dict(name=o.name, type="mesh", uuid=o_id[o],
                   origin=game_coords(origin), vertices=verts, faces={})
        layer = mesh.uv_layers.active.data if mesh.uv_layers.active else None
        for i, tri in enumerate(mesh.loop_triangles):
            key = cabinet.mat_key[mesh.materials[tri.material_index].name]
            keys = [f"v{v}" for v in tri.vertices]
            uv = [[float(layer[l].uv[0]), float(layer[l].uv[1])] for l in tri.loops] if layer else None
            obj["faces"][f"f{i}"] = dict(vertices=keys, role=key, uv=uv)
        game_model["elements"].append(obj)
        eo.to_mesh_clear()
    game_model["root"] = o_id[cabinet.root]
    return game_model


def camera(scene, location, target, size):
    data = bpy.data.cameras.new("Studio camera")
    cam = bpy.data.objects.new("Studio camera", data)
    scene.collection.objects.link(cam)
    cam.location = location
    cam.rotation_euler = (Vector(target)-cam.location).to_track_quat("-Z","Y").to_euler()
    data.type, data.ortho_scale = "ORTHO", size
    scene.camera = cam
    return cam


def area(collection, name, location, target, power, color, size):
    d = bpy.data.lights.new(name,"AREA")
    d.energy, d.color, d.shape, d.size = power, color, "DISK", size
    o = bpy.data.objects.new(name,d)
    collection.objects.link(o)
    o.location = location
    o.rotation_euler = (Vector(target)-o.location).to_track_quat("-Z","Y").to_euler()


def studio(cabinets, render):
    scene = bpy.context.scene
    scene.name = "Yharnam • Slot Machines"
    engines = set(scene.render.bl_rna.properties["engine"].enum_items.keys())
    scene.render.engine = "BLENDER_EEVEE_NEXT" if "BLENDER_EEVEE_NEXT" in engines else "BLENDER_EEVEE"
    if hasattr(scene.eevee, "use_fast_gi"):
        scene.eevee.use_fast_gi = False
    scene.eevee.taa_render_samples = 24
    scene.world = bpy.data.worlds.new("Slate studio")
    scene.world.use_nodes = True
    bg = scene.world.node_tree.nodes.get("Background")
    bg.inputs[0].default_value = (.083,.101,.165,1)
    bg.inputs[1].default_value = .40
    scene.render.film_transparent = True
    scene.view_settings.view_transform = "AgX"
    scene.view_settings.look = "AgX - Medium High Contrast"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.image_settings.file_format = "PNG"
    scene.render.resolution_percentage = 100
    rig = bpy.data.collections.new("Studio lights")
    scene.collection.children.link(rig)
    area(rig,"Warm key",(-2.9,-3.7,4.3),(0,0,1),490,(1,.86,.70),3.4)
    area(rig,"Cold front fill",(1.3,-4,2),(0,0,1),300,(.72,.84,1),3.3)
    area(rig,"Moon rim",(2.2,1.5,4),(0,0,.8),525,(.60,.72,1),2.7)
    area(rig,"Bronze highlight",(-1,.3,5.5),(0,0,.9),205,(1,.84,.71),3.5)
    cam = camera(scene,(2.35,-6.6,2.45),(0,-.015,.977),2.22)
    scene.frame_set(1)
    if render:
        scene.render.resolution_x, scene.render.resolution_y = 620, 730
        for cabinet in cabinets:
            for other in cabinets:
                other.collection.hide_render = other != cabinet
            scene.render.filepath = str(PREVIEWS / (cabinet.id+".png"))
            bpy.ops.render.render(write_still=True)
        for cabinet in cabinets:
            cabinet.collection.hide_render = False
        # Proof that the actual cylinder textures move and all three reach another stop.
        for cabinet in cabinets:
            cabinet.collection.hide_render = cabinet.id != "astral_choir"
        cam.location = (.073,-5,1.25)
        cam.rotation_euler = (Vector((0,-.25,1.125))-cam.location).to_track_quat("-Z","Y").to_euler()
        cam.data.ortho_scale = .723
        scene.render.resolution_x, scene.render.resolution_y = 480, 210
        scene.eevee.taa_render_samples = 8
        for frame in sorted(set((1,109,*range(17,101,2)))):
            scene.frame_set(frame)
            scene.render.filepath = str(BUILD / f"_spin_{frame:03}.png")
            bpy.ops.render.render(write_still=True)
        scene.eevee.taa_render_samples = 32
    for i,cabinet in enumerate(cabinets):
        cabinet.collection.hide_render = False
        cabinet.root.location.x = ((i-1)*1.18)
    cam.location = (1.53,-7,2.78)
    cam.rotation_euler = (Vector((0,0,.97))-cam.location).to_track_quat("-Z","Y").to_euler()
    cam.data.ortho_scale = 4.02
    scene.render.resolution_x, scene.render.resolution_y = 1560, 900
    scene.render.filepath = "//previews/lineup.png"
    scene.frame_set(1)
    for marker in list(scene.timeline_markers):
        scene.timeline_markers.remove(marker)
    for frame, text in ((1,"Ready"),(22,"Pull"),(71,"Reel 1 stop"),(84,"Reel 2 stop"),(97,"Reel 3 stop")):
        scene.timeline_markers.new(text, frame=frame)
    bpy.ops.object.select_all(action="DESELECT")
    for screen in bpy.data.screens:
        for a in screen.areas:
            if a.type == "VIEW_3D":
                a.spaces.active.region_3d.view_perspective = "CAMERA"
                a.spaces.active.shading.type = "MATERIAL"
                a.spaces.active.overlay.show_overlays = False
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/"bloodborne_slots.blend"))


def main():
    for folder in (MODELS,PREVIEWS,BUILD):
        folder.mkdir(exist_ok=True)
    for style in THEMES:
        for name in ("reel","marquee"):
            assert (TEX/f"{style}_{name}.png").is_file(), f"Missing {style} {name} texture"
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.context.preferences.filepaths.save_version = 0
    cabinets = []
    models, verification = [], {}
    for style in THEMES:
        machine = Cabinet(style)
        getattr(machine,style)()
        machine.animate()
        bpy.context.view_layer.update()
        verification[style] = blender_report(machine)
        models.append(export_game(machine))
        cabinets.append(machine)
    (BUILD/"_bb_source.json").write_text(json.dumps(models,separators=(",",":")),encoding="utf8")
    (BUILD/"geometry.json").write_text(json.dumps(verification,indent=2)+"\n",encoding="utf8")
    studio(cabinets,"--no-render" not in sys.argv)


if __name__ == "__main__":
    main()
