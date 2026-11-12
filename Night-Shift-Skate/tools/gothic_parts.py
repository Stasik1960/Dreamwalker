"""Sculpt the workshop's five new decks, trucks and wheels in runtime coordinates.

X is across the deck, Y is up, and Z follows the deck. All decoration is
geometry so the same details show in inventory, in hand and on the ground.
"""
from __future__ import annotations

import math

import bmesh
import bpy
from mathutils import Vector


SWATCHES = {
    "ebony": "24232A", "walnut": "402526", "wood_edge": "62403A", "velvet": "461828",
    "leather": "33212A", "indigo": "242C43", "slate": "343842", "stone": "51545C",
    "steel_dark": "343641", "steel": "616675", "silver": "A7B2BA", "silver_light": "D2D8D4",
    "lead": "3E3B45", "pewter": "787984", "bronze": "6C492C", "brass": "AB8050",
    "gold": "C7A16A", "ivory": "C8B799", "bone": "9E8C71", "red": "821F35",
    "garnet": "53152B", "ruby": "B43747", "jade": "205F5E", "mint": "5B9994",
    "blue": "2E4878", "lapis": "3B6394", "violet": "59436F", "plum": "37263E",
    "rubber": "27282F", "tread": "3B3942", "shadow": "1D1922",
}
METALS = {"steel_dark", "steel", "silver", "silver_light", "lead", "pewter", "bronze", "brass", "gold"}


def material(tone):
    name = "Workshop | " + tone
    found = bpy.data.materials.get(name)
    if found is not None:
        return found
    hex_color = SWATCHES[tone]
    srgb = [int(hex_color[index:index + 2], 16) / 255 for index in (0, 2, 4)]
    rgb = tuple(v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in srgb)
    mat = bpy.data.materials.new(name)
    mat.diffuse_color = (*rgb, 1)
    mat.use_nodes = True
    shader = mat.node_tree.nodes.get("Principled BSDF")
    shader.inputs["Base Color"].default_value = mat.diffuse_color
    shader.inputs["Metallic"].default_value = .65 if tone in METALS else .05
    shader.inputs["Roughness"].default_value = .34 if tone in METALS else .58
    return mat


def polar(radius, angle):
    return radius * math.cos(angle), radius * math.sin(angle)


def oval(width, length, count=28, center=(0, 0)):
    return [(center[0] + width * math.cos(a), center[1] + length * math.sin(a))
            for a in (math.tau * i / count for i in range(count))]


def bezier(a, b, c, d, steps=10):
    points = tuple(Vector(p) for p in (a, b, c, d))
    result = []
    for i in range(steps + 1):
        t = i / steps
        s = 1 - t
        result.append(tuple(points[0] * s**3 + points[1] * 3*s*s*t
                            + points[2] * 3*s*t*t + points[3] * t**3))
    return result


def lancet(cx, bottom, top, width, shoulder=.5):
    mid = bottom + (top - bottom) * shoulder
    return (
        [(cx - width, bottom), (cx + width, bottom), (cx + width, mid)]
        + bezier((cx + width, mid), (cx + width, mid + (top - mid) * .52),
                 (cx + width * .30, top - (top - mid) * .22), (cx, top), 7)[1:]
        + bezier((cx, top), (cx - width * .30, top - (top - mid) * .22),
                 (cx - width, mid + (top - mid) * .52), (cx - width, mid), 7)[1:]
    )


def rotate2(point, angle):
    u, v = point
    c, s = math.cos(angle), math.sin(angle)
    return u*c - v*s, u*s + v*c


def axis_point(axis, depth, u, v):
    if axis == "X":
        return depth, u, v
    if axis == "Y":
        return u, depth, v
    return u, v, depth


class Craft:
    def __init__(self, category, ident):
        self.collection = bpy.data.collections.new(f"{category}/{ident}")
        bpy.context.scene.collection.children.link(self.collection)

    def mesh(self, name, vertices, faces, tones, smooth=False):
        mesh = bpy.data.meshes.new(name)
        mesh.from_pydata(vertices, [], faces)
        if isinstance(tones, str):
            tones = [tones] * len(faces)
        slots = {}
        for polygon, tone in zip(mesh.polygons, tones, strict=True):
            if tone not in slots:
                slots[tone] = len(slots)
                mesh.materials.append(material(tone))
            polygon.material_index = slots[tone]
            polygon.use_smooth = smooth
        bm = bmesh.new()
        bm.from_mesh(mesh)
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
        bm.to_mesh(mesh)
        bm.free()
        mesh.update()
        obj = bpy.data.objects.new(name, mesh)
        self.collection.objects.link(obj)
        return obj

    def loft(self, name, rings, tones, caps=True, closed=False, smooth=False, cap_tones=None):
        count = len(rings[0])
        faces, colors = [], []
        for i in range(len(rings) if closed else len(rings) - 1):
            j = (i + 1) % len(rings)
            for k in range(count):
                q = (k + 1) % count
                faces.append((i*count+k, i*count+q, j*count+q, j*count+k))
                if isinstance(tones, str):
                    colors.append(tones)
                else:
                    tone = tones[min(i, len(tones) - 1)]
                    colors.append(tone[k % len(tone)] if isinstance(tone, tuple) else tone)
        if caps:
            faces.extend([tuple(range(count - 1, -1, -1)), tuple(range((len(rings)-1)*count, len(rings)*count))])
            colors.extend(cap_tones or [colors[0], colors[-1]])
        return self.mesh(name, [p for ring in rings for p in ring], faces, colors, smooth)

    def plate(self, name, outline, low, high, tone, *, axis="Y", bevel=.002, rim=None):
        cx = sum(p[0] for p in outline) / len(outline)
        cy = sum(p[1] for p in outline) / len(outline)
        radius = max(math.hypot(x - cx, y - cy) for x, y in outline)
        depth = min(bevel, (high - low) * .30)
        inset = 1 - min(.20, depth / radius)
        rings = []
        for h, factor in ((low, inset), (low+depth, 1), (high-depth, 1), (high, inset)):
            rings.append([axis_point(axis, h, cx+(u-cx)*factor, cy+(v-cy)*factor) for u,v in outline])
        return self.loft(name, rings, [rim or tone, tone, rim or tone], cap_tones=(tone, tone))

    def sweep(self, name, points, radii, tone, *, sides=6, closed=False, ref=(0, 1, 0)):
        points = [Vector(p) for p in points]
        if isinstance(radii, (int, float, tuple)):
            radii = [radii] * len(points)
        reference = Vector(ref)
        rings = []
        for i, (center, radius) in enumerate(zip(points, radii, strict=True)):
            prev = points[(i-1) % len(points)] if closed else points[max(i-1, 0)]
            next_ = points[(i+1) % len(points)] if closed else points[min(i+1, len(points)-1)]
            tangent = (next_ - prev).normalized()
            forward = reference - tangent * reference.dot(tangent)
            if forward.length_squared < .0001:
                forward = tangent.orthogonal()
            forward.normalize()
            side = tangent.cross(forward)
            thickness, width = radius if isinstance(radius, tuple) else (radius, radius)
            rings.append([tuple(center + forward * (thickness * math.cos(a)) + side * (width * math.sin(a)))
                          for a in (math.tau * j / sides for j in range(sides))])
        return self.loft(name, rings, tone, caps=not closed, closed=closed)

    def rim(self, name, outline, depth, thickness, tone, *, axis="Y", sides=5):
        return self.sweep(name, [axis_point(axis, depth, u, v) for u,v in outline], thickness, tone,
                          sides=sides, closed=True, ref=axis_point(axis, 1, 0, 0))

    def turn(self, name, axis, profile, tone, *, count=24, center=(0, 0), closed=False, flutes=0):
        rings = []
        for depth, radius in profile:
            ring = []
            for i in range(count):
                angle = i * math.tau / count
                r = radius * (1 + .045 * math.cos(flutes * angle)) if flutes else radius
                u, v = polar(r, angle)
                ring.append(axis_point(axis, depth, u + center[0], v + center[1]))
            rings.append(ring)
        return self.loft(name, rings, tone, caps=not closed, closed=closed, smooth=True)

    def gem(self, name, outline, low, high, colors, axis="Y"):
        cx = sum(u for u,v in outline) / len(outline)
        cy = sum(v for u,v in outline) / len(outline)
        rings = [[axis_point(axis, h, cx+(u-cx)*s, cy+(v-cy)*s) for u,v in outline]
                 for h,s in ((low,.88), (low+(high-low)*.34,1), (high,.45))]
        return self.loft(name, rings, [colors[0], tuple(colors[1:])], cap_tones=(colors[0], colors[-1]))


def body_height(sections, z):
    for first, second in zip(sections, sections[1:]):
        if z <= second[0]:
            t = max(0, (z-first[0]) / (second[0]-first[0]))
            return tuple(first[i] + t * (second[i]-first[i]) for i in (1, 2, 3))
    return sections[-1][1:]


def deck(craft, sections, shell, grip, line, inner="bronze"):
    rings = []
    for z, center, w, h in sections:
        rings.append([(center + w * x, h + y, z) for x, y in (
            (-.90,0),(-.99,.003),(-1,.008),(-1,.012),(-1,.017),(-.97,.024),
            (-.91,.030),(.91,.030),(.97,.024),(1,.017),(1,.012),(1,.008),(.99,.003),(.90,0))])
    colors = (shell, inner, line, shell, line, shell, grip, shell, line, shell, line, inner, shell, shell)
    body = craft.loft("Layered deck with shaped kicks", rings, [colors] * (len(rings)-1))
    for end in (-.285, .285):
        center, _, h = body_height(sections, end)
        for x in (center-.051, center+.051):
            for z in (end-.022, end+.022):
                top = body_height(sections, z)[2] + .030
                craft.turn("Recessed mount screw", "Y", [(top-.001,.0052),(top+.001,.0052),
                           (top+.003,.0039)], [inner, line], count=8, center=(x,z))
    return body


def edging(craft, sections, tone, width=.002, offset=.81):
    left = [(center-half*offset, h+.0305,max(-.508,min(.508,z))) for z,center,half,h in sections]
    right = [(center+half*offset, h+.0305,max(-.508,min(.508,z))) for z,center,half,h in reversed(sections)]
    craft.sweep("Inlaid perimeter fillet", left+right, (.0018,width), tone, closed=True)


def decorative_lines(craft, name, points, depth, tone, radius=.0025, axis="Y"):
    craft.sweep(name, [axis_point(axis, depth, x, z) for x,z in points], radius, tone,
                ref=axis_point(axis, 1, 0, 0), sides=5)


def fleur(craft, x, z, width, length, depth, tone, axis="Y"):
    profile = [(0,-1),(.22,-.24),(.57,-.07),(1,.18),(.70,.45),(.32,.25),(.29,.62),
               (0,1),(-.29,.62),(-.32,.25),(-.70,.45),(-1,.18),(-.57,-.07),(-.22,-.24)]
    outline = [(x + u*width, z + v*length) for u,v in profile]
    craft.plate("Cast fleur-de-lis", outline, depth-.001, depth+.0035, tone, axis=axis, bevel=.0015)


def faceted_stud(craft, name, u, v, size, depth, tones, axis="Y", count=8):
    craft.gem(name, oval(size,size,count,(u,v)), depth-.002, depth+.004, tones, axis)


def leaf(craft, name, center, width, length, angle, depth, tone, axis="Y"):
    polygon = [(0,-length),(.76*width,-.55*length),(width,.07*length),(.61*width,.59*length),
               (0,length),(-.61*width,.59*length),(-width,.07*length),(-.76*width,-.55*length)]
    points = [(center[0]+u, center[1]+v) for u,v in (rotate2(p, angle) for p in polygon)]
    craft.plate(name, points, depth-.0015, depth+.0025, tone, axis=axis, bevel=.001)
    return points


def tendril(craft, x, z, direction, span, depth, tone, axis="Y"):
    curve = bezier((x,z), (x+direction*.72*span,z-.34*span),
                   (x+direction*.84*span,z+.83*span), (x+direction*.18*span,z+.57*span), 12)
    craft.sweep("Engraved foliage scroll", [axis_point(axis, depth, u,v) for u,v in curve],
                [max(.0009, .0032 * (1-i/15)) for i in range(len(curve))], tone,
                ref=axis_point(axis, 1, 0, 0), sides=5)


def window(craft, axis, depth, radius, petals, relief, backing=True, reverse=False):
    direction = -1 if reverse else 1
    def level(offset):
        return depth + offset * relief * direction
    outer = [(level(-.35),radius*.80),(level(-.30),radius*.97),(level(.20),radius),
             (level(.87),radius*.97),(level(1),radius*.89),(level(.64),radius*.82)]
    craft.turn("Cast rose-window surround",axis,outer,["lead","bronze","brass","gold","brass","bronze"],
               count=petals*4,closed=True)
    if backing:
        low, high = sorted((level(-.85),level(.15)))
        craft.plate("Smoky glass backing",oval(radius*.86,radius*.86,petals*4),low,high,"shadow",
                    axis=axis,bevel=relief*.16)
    palette = [("garnet","red","ruby"),("blue","lapis","blue"),("plum","violet","violet"),
               ("jade","mint","jade"),("red","garnet","red"),("blue","lapis","violet")]
    breadth = radius * min(.235, .94/petals*2)
    for i in range(petals):
        angle = math.tau * i / petals
        outline = [(0,radius*.19),(breadth*.53,radius*.37),(breadth,radius*.65),
                   (breadth*.67,radius*.76),(0,radius*.827),(-breadth*.67,radius*.76),
                   (-breadth,radius*.65),(-breadth*.53,radius*.37)]
        outline = [rotate2(p,angle) for p in outline]
        low, high = sorted((level(-.12),level(.51)))
        craft.gem("Faceted stained-glass petal",outline,low,high,palette[i % len(palette)],axis)
        craft.rim("Lead window tracery",outline,level(.44),radius*.021,"lead",axis=axis,sides=5)
    center = [(level(-.31),radius*.15),(level(.33),radius*.23),(level(.71),radius*.22),
              (level(.91),radius*.15)]
    craft.turn("Gilt rose center",axis,center,["bronze","brass","gold"],count=petals*2)
    low, high = sorted((level(.69),level(1.14)))
    craft.gem("Central garnet",oval(radius*.14,radius*.14,8),low,high,("garnet","red","ruby"),axis)


def cathedral(craft):
    sections = [
        (-.51,0,.029,.044),(-.47,0,.058,.029),(-.414,0,.076,.014),(-.35,0,.080,.004),
        (-.285,0,.093,0),(-.19,0,.113,0),(-.10,0,.118,0),(.07,0,.119,0),(.20,0,.111,0),
        (.285,0,.096,0),(.35,0,.083,.008),(.42,0,.056,.027),(.475,0,.028,.051),(.51,0,.006,.065),
    ]
    deck(craft, sections, "steel_dark", "ebony", "brass", "bronze")
    edging(craft, sections, "brass")
    outer = lancet(0,-.221,.248,.077,.36)
    craft.plate("Recessed nave", outer,.029,.033,"slate",bevel=.0015)
    craft.rim("Nave framing",outer,.035,(.0025,.004),"bronze")
    for sign in (-1, 1):
        outer = lancet(sign*.032,-.182,.163,.027,.45)
        craft.plate("Deep garnet lancet", outer,.032,.037,"garnet",bevel=.0018)
        craft.rim("Gilt lancet moulding",outer,.038,(.0027,.0038),"gold")
        inner = lancet(sign*.032,-.155,.121,.016,.45)
        craft.plate("Red enamel recess",inner,.037,.040,"red",bevel=.0014)
        decorative_lines(craft,"Lancet mullion",[(sign*.032,-.162),(sign*.032,.124)],.041,"brass",.002)
        for z in (-.113,-.052,.009):
            decorative_lines(craft,"Lancet crossbar",[(sign*.032-.017,z),(sign*.032+.017,z)],.040,"bronze",.002)
        for z in (-.18,-.095,-.01,.075,.155):
            tendril(craft,sign*.056,z,sign,.041,.034,"brass")
            faceted_stud(craft,"Nave rivet",sign*.091,z+.008,.004,.032,("bronze","gold"))
    decorative_lines(craft,"Central stone rib",[(0,-.205),(0,.19),(0,.233)],.044,"silver",.0032)
    for z in (-.39,.378,.433):
        h = body_height(sections,z)[2]
        fleur(craft,0,z,.023,.027,h+.034,"gold")
    for z in (-.344,.322):
        crown = oval(.060,.014,16,(0,z))
        y = body_height(sections,z)[2]+.032
        craft.plate("Chased end ferrule",crown,y-.004,y+.004,"bronze",rim="brass")
    shadow = lancet(0,-.216,.251,.066,.30)
    craft.plate("Underside chapel relief",shadow,-.006,.001,"bronze",rim="steel_dark")
    craft.rim("Underside brass fillet",shadow,-.005,.0025,"brass")
    for z in (-.147,-.049,.049,.145):
        fleur(craft,0,z,.019,.029,-.005,"gold")


def crescent(width, length, offset=.45, count=18):
    # The intersection of two circles supplies the two clean, joined horns.
    radius = 1.0
    inner = .89
    ix = (radius**2 - inner**2 + offset**2) / (2*offset)
    iz = math.sqrt(1 - ix*ix)
    start = math.atan2(iz, ix)
    outer = [polar(radius,start+(math.tau-2*start)*i/count) for i in range(count+1)]
    cut = math.atan2(iz, ix-offset)
    reverse = [(offset+u,v) for u,v in (polar(inner,-cut-(math.tau-2*cut)*i/count)
                                         for i in range(1,count))]
    return [(x*width,z*length) for x,z in outer+reverse]


def moon(craft):
    sections = [
        (-.51,-.050,.017,.061),(-.48,-.048,.041,.040),(-.435,-.043,.067,.018),(-.38,-.037,.080,.008),
        (-.285,-.027,.094,0),(-.18,-.006,.113,0),(-.05,.004,.120,0),(.09,.012,.117,0),
        (.20,.020,.108,0),(.285,.024,.099,0),(.35,.035,.082,.009),(.416,.052,.062,.023),
        (.47,.063,.037,.049),(.51,.069,.008,.067),
    ]
    deck(craft, sections,"indigo","leather","silver","steel_dark")
    edging(craft,sections,"silver",.0021)
    for sign in (-1,1):
        curve = []
        for z,x,w,h in sections[1:-1]:
            curve.append((x+sign*w*.60,h+.032,z))
        craft.sweep("Swept silver rib",curve,[(.0017,.0027)]*len(curve),"pewter",sides=5)
    disk = oval(.087,.103,34)
    craft.plate("Eclipse medallion",disk,.028,.038,"shadow",rim="lead")
    craft.rim("Eclipse engraved rim",disk,.037,.0026,"pewter")
    silhouette = crescent(.073,.089)
    craft.plate("Forged crescent",silhouette,.036,.048,"silver",rim="pewter")
    for i, z in enumerate((-.173,-.122,.127,.185,.231)):
        x = (.043,-.041,.043,-.026,.033)[i]
        faceted_stud(craft,"Star setting",x,z,.0068,.033,("steel","silver_light","blue"),count=6)
        for a in range(4):
            u,v=polar(.012, a*math.pi*.5)
            decorative_lines(craft,"Star ray",[(x+u*.45,z+v*.45),(x+u,z+v)],.033,"silver",.0015)
    for z in (-.39,.385):
        center, w, h=body_height(sections,z)
        for sign in (-1,1):
            leaf(craft,"Hooked silver end plate",(center+sign*w*.44,z),w*.30,.034,-sign*.62,h+.031,"pewter")
            faceted_stud(craft,"End plate pin",center+sign*w*.38,z-.01,.0048,h+.034,("steel","silver"))
    craft.plate("Eclipse seal beneath the deck",oval(.073,.091,28),-.006,.001,"steel_dark",rim="pewter")
    craft.plate("Reverse crescent",crescent(.055,.072),-.009,-.004,"silver",rim="lead")
    for x,z in ((.046,-.144),(-.050,.131),(.052,.174)):
        faceted_stud(craft,"Reverse seal star",x,z,.004,-.005,("steel","pewter"))


def reliquary(craft):
    sections=[
        (-.51,0,.042,.055),(-.472,0,.068,.040),(-.38,0,.072,.013),(-.285,0,.099,0),
        (-.22,0,.126,0),(-.161,0,.129,0),(.19,0,.127,0),(.267,0,.108,0),
        (.33,0,.092,.007),(.417,0,.074,.023),(.48,0,.057,.047),(.51,0,.041,.059),
    ]
    deck(craft,sections,"wood_edge","walnut","brass","bronze")
    edging(craft,sections,"gold",.0022)
    panel=[(-.082,-.202),(-.093,-.140),(-.084,.141),(-.061,.215),(0,.252),
           (.061,.215),(.084,.141),(.093,-.140),(.082,-.202),(0,-.228)]
    craft.plate("Velvet lined reliquary bed",panel,.029,.038,"velvet",rim="bronze")
    craft.rim("Reliquary brass frame",panel,.039,(.0035,.0052),"brass")
    cage=[(-.041,-.133),(-.055,-.086),(-.052,.105),(-.036,.166),(0,.19),
          (.036,.166),(.052,.105),(.055,-.086),(.041,-.133),(0,-.148)]
    craft.plate("Raised bronze crystal socket",cage,.036,.046,"bronze",rim="brass")
    crystal=[(x*.83,z*.89) for x,z in cage]
    craft.gem("Long faceted carnelian crystal",crystal,.044,.062,("garnet","red","garnet","ruby","garnet","red"))
    for sign in (-1,1):
        for z in (-.14,-.076,.076,.14):
            curve=bezier((sign*.082,z-.018),(sign*.085,z+.018),(sign*.054,z+.038),(sign*.036,z+.021),8)
            points=[(x,.039+(.011*math.sin(math.pi*i/8)),v) for i,(x,v) in enumerate(curve)]
            craft.sweep("Crystal cage prong",points,[.005-.002*i/8 for i in range(9)],"gold")
            faceted_stud(craft,"Cage rivet",sign*.081,z-.019,.006,.039,("bronze","gold"))
        for z in (-.345,.323):
            h=body_height(sections,z)[2]
            plate=oval(.027,.023,8,(sign*.043,z))
            craft.plate("Case hinge housing",plate,h+.027,h+.037,"bronze",rim="brass")
            for dz in (-.01,.01):
                faceted_stud(craft,"Hinge pin",sign*.048,z+dz,.004,h+.037,("brass","gold"))
    for z in (-.32,.30):
        h=body_height(sections,z)[2]
        fleur(craft,0,z,.018,.026,h+.037,"brass")
    reverse=[(x*.86,z*.82) for x,z in panel]
    craft.plate("Bottom case embossing",reverse,-.006,.001,"leather",rim="bronze")
    craft.rim("Bottom case border",reverse,-.005,.0024,"brass")
    for sign in (-1,1):
        for z in (-.13,-.033,.067,.15):
            tendril(craft,sign*.015,z,sign,.054,-.005,"brass")
    fleur(craft,0,0,.017,.055,-.007,"gold")


def rose(craft):
    sections=[
        (-.51,0,.049,.047),(-.464,0,.078,.027),(-.413,0,.094,.011),(-.352,0,.092,.003),
        (-.285,0,.090,0),(-.226,0,.094,0),(-.16,0,.111,0),(-.08,0,.125,0),(0,0,.130,0),
        (.08,0,.125,0),(.16,0,.111,0),(.226,0,.094,0),(.285,0,.090,0),(.352,0,.092,.003),
        (.413,0,.094,.011),(.464,0,.078,.027),(.51,0,.049,.047),
    ]
    body=deck(craft,sections,"bronze","plum","brass","lead")
    # The glass is a separate inset rather than a medallion pasted onto a solid deck.
    cut=craft.turn("Temporary rosette aperture","Y",[(-.03,.083),(.08,.083)],"shadow",count=40)
    mod=body.modifiers.new("Actual circular rose-window aperture","BOOLEAN")
    mod.operation="DIFFERENCE"
    mod.solver="EXACT"
    mod.object=cut
    bpy.context.view_layer.objects.active=body
    bpy.ops.object.modifier_apply(modifier=mod.name)
    bpy.data.objects.remove(cut,do_unlink=True)
    edging(craft,sections,"brass")
    window(craft, "Y", .026, .093, 8, .010, backing=False)
    window(craft, "Y", .003, .083, 8, .004, backing=False, reverse=True)
    for sign in (-1,1):
        for z in (-.259,-.194,.194,.259):
            outline=oval(.018,.025,10,(sign*.052,z))
            craft.plate("Champlevé border tile",outline,.029,.034,"velvet",rim="bronze")
            craft.rim("Champlevé brass lip",outline,.034,.0018,"brass")
            tendril(craft,sign*.034,z-.021,sign,.043,.035,"gold")
    for z in (-.388,.388):
        h=body_height(sections,z)[2]
        fleur(craft,0,z,.034,.044,h+.033,"brass")


def gargoyle(craft):
    sections=[
        (-.51,0,.054,.055),(-.476,0,.070,.040),(-.44,0,.075,.022),(-.397,0,.115,.013),
        (-.359,0,.129,.008),(-.323,0,.101,.003),(-.285,0,.092,0),(-.218,0,.127,0),
        (-.178,0,.129,0),(-.119,0,.108,0),(-.07,0,.118,0),(0,0,.124,0),(.115,0,.126,0),
        (.19,0,.111,0),(.285,0,.097,0),(.352,0,.124,.010),(.39,0,.126,.017),
        (.429,0,.091,.026),(.47,0,.055,.049),(.51,0,.025,.070),
    ]
    deck(craft,sections,"steel_dark","slate","pewter","lead")
    edging(craft,sections,"pewter",.0023)
    for sign in (-1,1):
        wing=[(sign*x,z) for x,z in ((.014,-.174),(.112,-.207),(.099,-.122),(.115,-.018),
               (.081,.012),(.110,.102),(.082,.182),(.045,.158),(.015,.099))]
        craft.plate("Carved batwing webbing",wing,.029,.038,"lead",rim="steel")
        craft.rim("Raised wing edge",wing,.038,(.0023,.0027),"pewter")
        for tipx, tipz in ((.108,-.193),(.114,-.021),(.105,.098),(.078,.168)):
            start=(sign*.020,.044,-.063)
            middle1=(sign*.054,.052,tipz*.35)
            middle2=(sign*(tipx-.011),.046,tipz-.024)
            end=(sign*tipx,.038,tipz)
            rib=bezier(start,middle1,middle2,end,10)
            craft.sweep("Sculpted wing rib",rib,[.0063-.0048*i/10 for i in range(11)],"stone")
        for z in (-.357,.316):
            h=body_height(sections,z)[2]
            leaf(craft,"Carved stone talon",(sign*.051,z),.016,.043,-sign*.45,h+.033,"stone")
    face=[(-.039,.052),(-.039,-.025),(-.023,-.078),(0,-.091),(.023,-.078),(.039,-.025),(.039,.052),
          (.025,.083),(0,.071),(-.025,.083)]
    craft.gem("Gargoyle brow and muzzle",face,.032,.059,("lead","stone","slate","pewter"))
    for sign in (-1,1):
        horn=bezier((sign*.023,.053,.042),(sign*.039,.053,.083),(sign*.043,.044,.106),
                    (sign*.052,.036,.125),10)
        craft.sweep("Recurved gargoyle horn",horn,[.010-.009*i/10 for i in range(11)],"pewter")
        outline=[(sign*.009,.014),(sign*.027,.027),(sign*.032,.017),(sign*.017,.006)]
        craft.gem("Garnet eye",outline,.050,.058,("garnet","red","ruby"))
        fang=[(sign*.011,-.055),(sign*.019,-.053),(sign*.015,-.075)]
        craft.plate("Chipped stone fang",fang,.045,.053,"ivory",bevel=.0012)
    reverse=[(-.082,-.118),(0,-.194),(.082,-.118),(.069,.099),(0,.190),(-.069,.099)]
    craft.plate("Sculpted reverse escutcheon",reverse,-.006,.001,"lead",rim="steel")
    craft.rim("Reverse relief rim",reverse,-.005,.0028,"pewter")
    for sign in (-1,1):
        for z in (-.13,-.063,.006,.075):
            tendril(craft,sign*.011,z,sign,.055,-.005,"stone")


DECK_DESIGNS = {
    "cathedral_spire": cathedral, "moon_sickle": moon, "hunter_reliquary": reliquary,
    "rose_window": rose, "gargoyle_wing": gargoyle,
}


def truck_mount(craft, tone, edge, width=.067, length=.049):
    shape=[(-width,-length*.73),(-width*.72,-length),(width*.72,-length),(width,-length*.73),
           (width,length*.73),(width*.72,length),(-width*.72,length),(-width,length*.73)]
    craft.plate("Bevelled deck saddle",shape,-.014,0,tone,rim=edge,bevel=.004)
    for x in (-.050,.050):
        for z in (-.027,.027):
            craft.turn("Saddle screw head","Y",[(-.019,.0039),(-.017,.0054),(-.012,.0052)],
                       [edge,"silver"],count=8,center=(x,z))
    craft.turn("Tempered axle","X",[(-.146,.0059),(-.142,.0087),(.142,.0087),(.146,.0059)],
               ["steel","silver","steel"],count=16,center=(-.065,0))
    for side in (-1,1):
        profile=sorted((side*x,r) for x,r in ((.104,.010),(.107,.0135),(.117,.0135),(.122,.0091)))
        craft.turn("Carved axle collar","X",profile,edge,count=12,center=(-.065,0))
    craft.turn("Kingpin sleeve","Y",[(-.078,.0075),(-.075,.012),(-.058,.012),(-.024,.013),(-.015,.011)],
               [edge,tone,"garnet",edge],count=12)


def bracket(craft, name, points, radii, tone, sides=8):
    craft.sweep(name, points, radii, tone, sides=sides, ref=(0,0,1))


def buttress(craft):
    truck_mount(craft,"steel_dark","brass",.062,.044)
    for face in (-1,1):
        for side in (-1,1):
            rib=bezier((side*.008,-.016,face*.028),(side*.041,-.017,face*.034),
                       (side*.080,-.065,face*.036),(side*.115,-.065,face*.010),12)
            bracket(craft,"Forged flying arch",rib,[(.010-.004*i/12,.010-.004*i/12) for i in range(13)],"steel_dark")
            brass=[(x,y+.003,z+face*.007) for x,y,z in rib]
            bracket(craft,"Ribbed brass arch facing",brass,[.0038-.0014*i/12 for i in range(13)],"brass",5)
            for z in (-.018,.018):
                if math.copysign(1,z) == face:
                    faceted_stud(craft,"Arch foot fastener",side*.101,-.060,.0062,z,
                                  ("bronze","brass","gold"),axis="Z")
        arch=lancet(0,-.069,-.019,.034,.45)
        craft.rim("Open lancet in buttress",arch,face*.031,(.004,.0048),"steel",axis="Z")
        for side in (-1,1):
            decorative_lines(craft,"Delicate side mullion",[(side*.016,-.063),(side*.016,-.039)],
                             face*.034,"brass",.002,axis="Z")
        faceted_stud(craft,"Garnet keystone",0,-.030,.007,face*.033,("garnet","red","ruby"),axis="Z")


def chalice(craft):
    truck_mount(craft,"bronze","gold",.069,.047)
    for side in (-1,1):
        arm=bezier((side*.018,-.046,.021),(side*.046,-.075,.030),(side*.086,-.080,.016),
                   (side*.113,-.064,0),11)
        bracket(craft,"Cast chalice yoke",arm,[(.012-.003*i/11,.013-.005*i/11) for i in range(12)],"bronze")
        trim=[(x,y-.004,z+.010) for x,y,z in arm]
        bracket(craft,"Gilt edge on chalice yoke",trim,.0027,"gold",5)
        tendril(craft,side*.040,-.057,side,.037,.027,"brass",axis="Z")
    cup=[(-.070,.014),(-.066,.024),(-.061,.026),(-.052,.019),(-.043,.020),(-.036,.030),
         (-.026,.039),(-.019,.047),(-.014,.047),(-.011,.041),(-.017,.034),(-.027,.030)]
    craft.turn("Fluted carved chalice","Y",cup,
               ["bronze","gold","bronze","garnet","bronze","brass","bronze","gold","brass","garnet","bronze","lead"],
               count=32,closed=True,flutes=8)
    for y,r in ((-.055,.021),(-.028,.038),(-.015,.046)):
        craft.turn("Chalice rolled lip","Y",[(y-.002,r-.001),(y-.001,r+.001),(y+.001,r+.001),(y+.002,r-.001)],
                   "gold",count=24,closed=True)
    for face in (-1,1):
        shape=lancet(0,-.051,-.021,.013,.45)
        craft.plate("Set enamel window",shape,face*.030-.003,face*.030+.003,"garnet",axis="Z",rim="bronze")
        craft.rim("Enamel bezel",shape,face*.034,.002,"brass",axis="Z")


def ribcage(craft):
    truck_mount(craft,"lead","pewter",.065,.042)
    for side in (-1,1):
        arm=bezier((side*.013,-.029,0),(side*.039,-.063,0),(side*.076,-.081,0),(side*.112,-.064,0),12)
        bracket(craft,"Forged ribcage axle arm",arm,[.013-.005*i/12 for i in range(13)],"steel_dark")
        for i, z in enumerate((-.033,-.013,.013,.033)):
            reach=(.068,.092,.103,.080)[i]
            rib=bezier((side*.009,-.019,z),(side*reach,-.012,z+.009),
                       (side*reach,-.073,z-.006),(side*.034,-.073,z-.014),12)
            bracket(craft,"Curved bone rib",rib,[.0061-.002*i/12 for i in range(13)],"bone" if i%2 else "ivory",6)
    spine=[(-.018,-.012),(0,-.023),(.018,-.012),(.023,.018),(0,.044),(-.023,.018)]
    craft.plate("Carved sternum shield",spine,-.043,-.026,"bone",rim="lead",bevel=.003)
    for z in (-.039,-.020,0,.020,.039):
        craft.turn("Sectioned sternum","Z",[(z-.005,.007),(z-.002,.011),(z+.003,.010),(z+.005,.006)],
                   ["bone","ivory","bone"],count=10,center=(0,-.047))
    faceted_stud(craft,"Sternum garnet",0,-.053,.009,.048,("garnet","red","ruby"),axis="Z")


def gate(craft):
    truck_mount(craft,"steel_dark","bronze",.070,.043)
    for face in (-1,1):
        z=face*.024
        for side in (-1,1):
            frame=bezier((0,-.015,z),(side*.057,-.021,z),(side*.079,-.050,z),(side*.113,-.060,z),12)
            bracket(craft,"Forged pointed grille coping",frame,.006,"lead")
            bracket(craft,"Grille coping brass edge",[(x,y,z+face*.005) for x,y,z in frame],.0021,"brass",5)
        for i, x in enumerate((-.082,-.055,-.027,0,.027,.055,.082)):
            top=-.021-.031*(abs(x)/.082)
            decorative_lines(craft,"Tapered grille bar",[(x,-.075),(x,top)],z,"steel",.0035,axis="Z")
            finial=[(x-.008,top-.004),(x,top+.008),(x+.008,top-.004),(x,top-.010)]
            craft.plate("Cast grille spear finial",finial,z-.002,z+.002,"brass",axis="Z",bevel=.0009)
            if i%2==1:
                outline=oval(.009,.011,10,(x,-.057))
                craft.rim("Open grille ring",outline,z+face*.005,.0023,"bronze",axis="Z")
        decorative_lines(craft,"Cast grille bottom bar",[(-.103,-.071),(-.064,-.077),(.064,-.077),(.103,-.071)],
                         z,"steel_dark",.0047,axis="Z")
        fleur(craft,0,-.047,.010,.020,z+face*.008,"gold",axis="Z")


def claw(craft):
    truck_mount(craft,"steel_dark","pewter",.064,.047)
    for side in (-1,1):
        limb=bezier((side*.013,-.020,-.018),(side*.049,-.031,-.025),
                    (side*.066,-.067,-.012),(side*.113,-.064,0),13)
        bracket(craft,"Cast gargoyle forelimb",limb,[(.017-.008*i/13,.017-.006*i/13) for i in range(14)],"steel_dark")
        ridge=[(x,y+.007,z-.011) for x,y,z in limb]
        bracket(craft,"Pewter ridge along talon",ridge,[.005-.002*i/13 for i in range(14)],"pewter",6)
        for i in range(3):
            z=-.023+.022*i
            hooked=bezier((side*.071,-.046,z),(side*.114,-.036,z+.006),
                          (side*.134,-.063,z+.012),(side*.117,-.082,z+.016),10)
            bracket(craft,"Recurved claw around axle",hooked,[.0084-.0065*j/10 for j in range(11)],
                    "stone" if i%2 else "pewter",6)
        for i in range(3):
            faceted_stud(craft,"Bronze claw knuckle",side*(.045+.019*i),-.047-.006*i,.006,-.033,
                          ("bronze","brass","gold"),axis="Z")
        faceted_stud(craft,"Blood-red rivet setting",side*.064,-.045,.010,-.035,
                      ("garnet","red","ruby"),axis="Z")
    shield=lancet(0,-.069,-.017,.025,.49)
    craft.plate("Cast gargoyle escutcheon",shield,-.046,-.030,"slate",axis="Z",rim="pewter")
    craft.rim("Escutcheon carved edge",shield,-.045,.0026,"lead",axis="Z")


TRUCK_DESIGNS = {
    "flying_buttress": buttress, "chalice_yoke": chalice, "ribcage": ribcage,
    "iron_gate": gate, "gargoyle_claw": claw,
}
