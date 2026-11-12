"""Generate and extract modular Bloodborne skateboard parts as NSM1 meshes.

Run headlessly from the mod root:
  blender --background --factory-startup --python tools/generate_modular_parts.py

Coordinates in generated assets are already runtime coordinates: +X width,
+Y up, +Z length. Extracted authored boards are remapped and normalized here.
"""
from __future__ import annotations

import math
import json
import struct
import sys
from pathlib import Path

import bpy
import bmesh
from mathutils import Matrix, Vector

ROOT = Path(__file__).resolve().parents[1]
ARTWORK = ROOT / "artwork"
OUT = ROOT / "src/main/resources/assets/night_shift_skate/meshes/parts"
BUILD = ROOT / "build/art-redesign"
CATALOG = BUILD / "night_shift_gothic_parts.blend"

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gothic_parts import Craft, DECK_DESIGNS, TRUCK_DESIGNS
from gothic_wheels import WHEEL_DESIGNS

DESIGNS = {"deck": DECK_DESIGNS, "trucks": TRUCK_DESIGNS, "wheels": WHEEL_DESIGNS}
BUDGETS = {"deck": 6000, "trucks": 3000, "wheels": 3000}

def linear_to_srgb(value):
    value=max(0.0,min(1.0,float(value)))
    if value<=.0031308: value*=12.92
    else: value=1.055*value**(1/2.4)-.055
    return round(value*255)


def color(material):
    c=material.diffuse_color if material else (.5,.5,.5,1)
    return tuple(linear_to_srgb(v) for v in c[:3])+(round(max(0,min(1,float(c[3])))*255),)


def evaluated_triangles(objects, transform=None):
    deps=bpy.context.evaluated_depsgraph_get(); rows=[]
    for src in objects:
        if src.type not in {"MESH","CURVE","SURFACE","FONT"}: continue
        ev=src.evaluated_get(deps); mesh=ev.to_mesh(preserve_all_data_layers=True,depsgraph=deps)
        if not mesh or not mesh.polygons: continue
        mesh.calc_loop_triangles(); mats=[color(s.material) for s in ev.material_slots] or [(128,128,128,255)]
        normal_m=ev.matrix_world.to_3x3().inverted_safe().transposed()
        for tri in mesh.loop_triangles:
            poly=mesh.polygons[tri.polygon_index]; rgba=mats[min(poly.material_index,len(mats)-1)]; corners=[]
            for li in tri.loops:
                loop=mesh.loops[li]; p=ev.matrix_world@mesh.vertices[loop.vertex_index].co
                n=(normal_m@mesh.corner_normals[li].vector).normalized()
                if transform: p,n=transform(p,n)
                corners.append((p,n,rgba))
            rows.append(corners)
        ev.to_mesh_clear()
    return rows


def decimated_triangles(objects, budget, raw_count):
    if raw_count <= budget: return evaluated_triangles(objects)
    ratio=min(1.0,budget/raw_count*.88)
    for _ in range(4):
        temporary=[]
        for source in objects:
            if source.type != "MESH": continue
            mod=source.modifiers.new("NS runtime closed-shell budget","DECIMATE")
            mod.decimate_type="COLLAPSE"; mod.ratio=max(.015,ratio); mod.use_collapse_triangulate=True
            temporary.append((source,mod))
        bpy.context.view_layer.update(); result=evaluated_triangles(objects)
        for source,mod in temporary: source.modifiers.remove(mod)
        if len(result)<=budget:
            print(f"DECIMATED closed shells raw={raw_count} result={len(result)} budget={budget}")
            return result
        ratio*=budget/len(result)*.90
    raise RuntimeError(f"closed-shell decimation remained above budget: {len(result)} > {budget}")


def write_nsmesh(path, triangles, budget):
    if not triangles: raise RuntimeError(f"empty asset {path}")
    if len(triangles)>budget: raise RuntimeError(f"{path.name}: {len(triangles)} triangles exceeds {budget}")
    verts=[]; inds=[]; unique={}
    for tri in triangles:
        for p,n,rgba in tri:
            key=tuple(round(float(v),6) for v in (*p,*n))+rgba
            idx=unique.get(key)
            if idx is None: idx=len(verts); unique[key]=idx; verts.append((*p,*n,*rgba))
            inds.append(idx)
    mins=[min(v[a] for v in verts) for a in range(3)]; maxs=[max(v[a] for v in verts) for a in range(3)]
    path.parent.mkdir(parents=True,exist_ok=True)
    with path.open("wb") as f:
        f.write(struct.pack(">4sIII6f",b"NSM1",1,len(verts),len(inds),*mins,*maxs))
        for v in verts: f.write(struct.pack(">6f4B",*v))
        for i in inds: f.write(struct.pack(">I",i))
    print(f"ASSET {path.relative_to(ROOT)} tri={len(triangles)} verts={len(verts)} bounds={mins}..{maxs}")


def export_generated():
    for category, designs in DESIGNS.items():
        for ident, sculpt in designs.items():
            craft = Craft(category, ident)
            sculpt(craft)
            bpy.context.view_layer.update()
            objects = craft.collection.all_objects
            triangles = evaluated_triangles(objects)
            budget = BUDGETS[category]
            if len(triangles) > budget:
                triangles = decimated_triangles(objects, budget, len(triangles))
            write_nsmesh(OUT / category / f"{ident}.nsmesh", triangles, budget)


def authored_transform(kind, rows):
    # Original boards: Blender X=length, Y=width, Z=up -> runtime X=Y,Y=Z,Z=X.
    points=[Vector((p.y,p.z,p.x)) for tri in rows for p,_,_ in tri]
    lo=Vector((min(p.x for p in points),min(p.y for p in points),min(p.z for p in points)))
    hi=Vector((max(p.x for p in points),max(p.y for p in points),max(p.z for p in points)))
    center=(lo+hi)/2
    if kind=="deck": sx=.26/(hi.x-lo.x); sz=1.02/(hi.z-lo.z); sy=sz; base=lo.y
    elif kind=="trucks": sx=.30/(hi.x-lo.x); sz=.13/(hi.z-lo.z); sy=.075/(hi.y-lo.y); base=hi.y
    else: sx=.054/(hi.x-lo.x); sy=sz=.078/max(hi.y-lo.y,hi.z-lo.z); base=center.y
    def tf(p,n):
        q=Vector((p.y,p.z,p.x)); nn=Vector((n.y,n.z,n.x))
        q=Vector(((q.x-center.x)*sx,(q.y-base)*sy,(q.z-center.z)*sz))
        nn=Vector((nn.x/sx,nn.y/sy,nn.z/sz)).normalized()
        return q,nn
    return tf


def extract_authored(source, ident, deck_collections, truck_collection, wheel_collection, axle_name):
    bpy.ops.wm.open_mainfile(filepath=str(source))
    groups={"deck":[],"trucks":[],"wheels":[]}
    for o in bpy.context.scene.objects:
        cols={c.name for c in o.users_collection}
        if cols & set(deck_collections): groups["deck"].append(o)
        if truck_collection in cols: groups["trucks"].append(o)
        if wheel_collection in cols: groups["wheels"].append(o)
    for kind,budget in (("deck",6000),("trucks",3000),("wheels",3000)):
        raw=evaluated_triangles(groups[kind]); tf=authored_transform(kind,raw)
        runtime=decimated_triangles(groups[kind],budget,len(raw))
        transformed=[[(tf(p,n)[0],tf(p,n)[1],rgba) for p,n,rgba in tri] for tri in runtime]
        if kind == "trucks":
            axle=bpy.data.objects[axle_name]; ev=axle.evaluated_get(bpy.context.evaluated_depsgraph_get())
            axle_mesh=ev.to_mesh(depsgraph=bpy.context.evaluated_depsgraph_get())
            axle_y=sum((ev.matrix_world@v.co).z for v in axle_mesh.vertices)/len(axle_mesh.vertices); ev.to_mesh_clear()
            source_y=[p.z for tri in raw for p,_,_ in tri]; hi=max(source_y); lo=min(source_y)
            normalized=(axle_y-hi)*.075/(hi-lo); correction=.065/abs(normalized)
            transformed=[[(Vector((p.x,p.y*correction,p.z)),Vector((n.x,n.y/correction,n.z)).normalized(),rgba)
                          for p,n,rgba in tri] for tri in transformed]
            print(f"AXLE {ident} source_normalized={normalized:.6f} corrected=-0.065000")
        if kind == "deck":
            selected=[p for tri in transformed for p,_,_ in tri]
            lo=Vector((min(p.x for p in selected),min(p.y for p in selected),min(p.z for p in selected)))
            hi=Vector((max(p.x for p in selected),max(p.y for p in selected),max(p.z for p in selected)))
            sx=.26/(hi.x-lo.x); sz=1.02/(hi.z-lo.z); cx=(lo.x+hi.x)/2; cz=(lo.z+hi.z)/2
            fixed=[]
            for tri in transformed:
                row=[]
                for p,n,rgba in tri:
                    q=Vector(((p.x-cx)*sx,p.y-lo.y,(p.z-cz)*sz))
                    nn=Vector((n.x/sx,n.y,n.z/sz)).normalized(); row.append((q,nn,rgba))
                fixed.append(row)
            transformed=fixed
        write_nsmesh(OUT/kind/f"{ident}.nsmesh",transformed,budget)


def verify_outputs():
    expected = {category: ("iron_vespers", "grave_procession", *designs) for category, designs in DESIGNS.items()}
    report={"files":{},"procedural_manifold":{}}
    for category,names in expected.items():
        for ident in names:
            path=OUT/category/f"{ident}.nsmesh"; data=path.read_bytes()
            magic,version,vc,ic,*bounds=struct.unpack_from(">4sIII6f",data,0)
            assert magic==b"NSM1" and version==1 and ic%3==0
            assert len(data)==40+vc*28+ic*4
            tri=ic//3; limit=BUDGETS[category]
            assert tri<=limit
            lo=bounds[:3]; hi=bounds[3:]
            if category=="deck":
                assert abs(lo[0])<=.131 and abs(hi[0])<=.131 and abs(lo[2])<=.511 and abs(hi[2])<=.511 and -.012<=lo[1]<=.001, (ident, bounds)
            elif category=="trucks":
                assert hi[1]<=.001 and abs(lo[0])<=.151 and abs(hi[0])<=.151, (ident, bounds)
            else:
                assert max(abs(lo[1]),abs(hi[1]),abs(lo[2]),abs(hi[2]))<=.042 and lo[0]>=-.0281 and hi[0]<=.0301, (ident, bounds)
            report["files"][f"{category}/{ident}"]={"triangles":tri,"vertices":vc,"bounds_min":lo,"bounds_max":hi,"bytes":len(data)}
    deps=bpy.context.evaluated_depsgraph_get()
    for category, designs in DESIGNS.items():
        for ident in designs:
            boundary=nonmanifold=triangles=0
            for obj in bpy.data.collections[f"{category}/{ident}"].all_objects:
                ev=obj.evaluated_get(deps); mesh=ev.to_mesh(depsgraph=deps); mesh.calc_loop_triangles(); triangles+=len(mesh.loop_triangles)
                bm=bmesh.new(); bm.from_mesh(mesh)
                boundary+=sum(1 for e in bm.edges if e.is_boundary)
                nonmanifold+=sum(1 for e in bm.edges if not e.is_manifold and not e.is_boundary)
                bm.free(); ev.to_mesh_clear()
            report["procedural_manifold"][f"{category}/{ident}"]={"triangles":triangles,"boundary_edges":boundary,"nonmanifold_edges":nonmanifold}
            assert boundary==0 and nonmanifold==0, (category, ident, boundary, nonmanifold)
    path=BUILD/"verification.json"; path.write_text(json.dumps(report,indent=2),encoding="utf-8")
    print(f"VERIFIED files={len(report['files'])} procedural_assets={len(report['procedural_manifold'])} report={path}")


def arrange_editor_catalog():
    to_blender = Matrix.Rotation(math.pi / 2, 4, "X")
    for row, (category, designs) in enumerate(DESIGNS.items()):
        for column, ident in enumerate(designs):
            collection = bpy.data.collections[f"{category}/{ident}"]
            scale = {"deck": 1, "trucks": 2.4, "wheels": 5.5}[category]
            transform = (Matrix.Translation(((column - 2) * .78, (1 - row) * 1.18, 0))
                         @ Matrix.Scale(scale, 4) @ to_blender)
            if category == "wheels":
                transform = transform @ Matrix.Rotation(math.radians(-55), 4, "Z")
            for obj in collection.objects:
                obj.matrix_world = transform @ obj.matrix_world
    for screen in bpy.data.screens:
        for area in screen.areas:
            if area.type == "VIEW_3D":
                view = area.spaces.active
                view.shading.type = "MATERIAL"
                view.region_3d.view_location = (0, 0, 0)
                view.region_3d.view_distance = 5.5
                view.region_3d.view_rotation = Vector((.22, -.65, 1.6)).to_track_quat("Z", "Y")


def main():
    BUILD.mkdir(parents=True, exist_ok=True)
    if "--extract-originals" in sys.argv:
        extract_authored(ARTWORK / "Iron Vespers.blend", "iron_vespers",
                         ["01 | Shaped walnut and gilt laminations", "02 | Rose window and hand-beaten tracery", "03 | Leather grip and deck fittings"],
                         "Nose assembly", "Right wheel", "Nose truck | axle")
        extract_authored(ARTWORK / "Grave Procession.blend", "grave_procession",
                         ["01 | Sculpted coffin lid", "02 | Underside fittings and jewelry"],
                         "Foot rolling gear", "Right reliquary wheel", "Antique steel axle")
    bpy.ops.wm.read_factory_settings(use_empty=True)
    export_generated()
    verify_outputs()
    arrange_editor_catalog()
    bpy.ops.wm.save_as_mainfile(filepath=str(CATALOG))
    print(f"CATALOG {CATALOG}")


if __name__ == "__main__":
    main()
