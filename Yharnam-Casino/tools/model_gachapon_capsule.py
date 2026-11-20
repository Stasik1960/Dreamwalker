"""Faceted two-piece prize capsule. The seam is at local Z = 0 and one unit is one block."""
from math import cos, pi, sin
import bpy

SIDES = 12
# Pole to mouth for the exterior of one half. The narrow last interval is the dark locking band.
OUTER = [(0,.100),(.060,.080),(.091,.041),(.0995,.005),(.100,.0004)]
INNER = [(.086,.0004),(.086,.005),(.079,.035),(.050,.066),(0,.083)]


def surface(name, profile, materials, band_materials, reverse=False):
    verts, rings, faces, indices = [], [], [], []
    for radius,z in profile:
        ring=[]
        for j in range(SIDES if radius else 1):
            angle=2*pi*j/SIDES
            ring.append(len(verts))
            verts.append((radius*cos(angle),radius*sin(angle),z))
        rings.append(ring)
    for k,(a,b) in enumerate(zip(rings,rings[1:])):
        for j in range(SIDES):
            nxt=(j+1)%SIDES
            if len(a)==1:
                face=(a[0],b[nxt],b[j])
            elif len(b)==1:
                face=(a[j],a[nxt],b[0])
            else:
                face=(a[j],a[nxt],b[nxt],b[j])
            faces.append(face[::-1] if reverse else face)
            mat=band_materials[k]
            # Two small brass indexing marks share the existing surface.
            indices.append(3 if mat==2 and j in (2,8) else mat)
    data=bpy.data.meshes.new(name)
    data.from_pydata(verts,[],faces)
    data.update()
    obj=bpy.data.objects.new(name,data)
    bpy.context.collection.objects.link(obj)
    for mat in materials:
        data.materials.append(mat)
    for face,index in zip(data.polygons,indices):
        face.material_index=index
    return obj


def materials(palette,key):
    return [palette["glass_"+key],palette["bone"],palette["ink"],palette["gold"]]


def build_halves(palette,color_key="red"):
    mats=materials(palette,color_key)
    halves=[]
    for upper in (False,True):
        sign=1 if upper else -1
        profile=[(r,z*sign) for r,z in OUTER+INNER]
        color=int(upper)
        bands=[color,color,color,2,2,color,color,color,color]
        halves.append(surface("Capsule_Lid" if upper else "Capsule_Cup",
                              profile,mats,bands,reverse=upper))
    return tuple(halves)


def build_display(palette,color_key="red"):
    # A single continuous exterior; the hidden inside walls are only in the editable item.
    profile=[(r,-z) for r,z in OUTER]+list(reversed(OUTER))
    return surface("Capsule_Display_"+color_key,profile,materials(palette,color_key),
                   [0,0,0,2,2,2,1,1,1])

def core(palette,key):
    """A small luminous orb on a spindle inside the glass cup, with a brighter upper facet."""
    profile=[(0,-.049),(.024,-.043),(.042,-.025),(.048,0),(.041,.025),(.022,.043),(0,.048)]
    orb=surface("Lumen_"+key,profile,[palette["deep_"+key],palette["light_"+key],palette["spark_"+key],palette["spark_"+key]],
                [2,1,0,0,1,2])
    for v in orb.data.vertices:
        v.co.z-=.029
    return orb
