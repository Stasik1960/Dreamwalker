"""Compile the original GLB tables into chunk cells plus pivot-relative moving parts."""
import io
import json
import struct
import numpy as np
from PIL import Image, ImageDraw
from compile_gaming_floor import ART, ASSETS, DATA, clip, save_json, write_mesh


def transform(node):
    if "matrix" in node:
        return np.array(node["matrix"]).reshape(4, 4).T
    x, y, z, w = node.get("rotation", (0, 0, 0, 1))
    rot = [[1-2*(y*y+z*z), 2*(x*y-z*w), 2*(x*z+y*w)],
           [2*(x*y+z*w), 1-2*(x*x+z*z), 2*(y*z-x*w)],
           [2*(x*z-y*w), 2*(y*z+x*w), 1-2*(x*x+y*y)]]
    m = np.eye(4)
    m[:3,:3] = rot @ np.diag(node.get("scale", (1, 1, 1)))
    m[:3,3] = node.get("translation", (0, 0, 0))
    return m


def compile_table(key, filename):
    raw = (ART / (filename + ".glb")).read_bytes()
    end = 20 + struct.unpack_from("<I", raw, 12)[0]
    doc, binary = json.loads(raw[20:end]), raw[end+8:]
    def view(i):
        v = doc["bufferViews"][i]
        off = v.get("byteOffset", 0)
        return binary[off:off+v["byteLength"]]
    def data(i):
        a = doc["accessors"][i]
        dtype = {5121:"u1", 5123:"<u2", 5125:"<u4", 5126:"<f4"}[a["componentType"]]
        width = {"SCALAR":1, "VEC2":2, "VEC3":3}[a["type"]]
        return np.frombuffer(view(a["bufferView"]), dtype, count=a["count"]*width,
                             offset=a.get("byteOffset",0)).reshape(-1,width)
    atlas = Image.new("RGBA", (1024,1024), "#49392e")
    regions = []
    print_y = 8
    for i, mat in enumerate(doc["materials"]):
        pbr = mat["pbrMetallicRoughness"]
        tiled = mat["name"] == "Polished walnut"
        if "baseColorTexture" in pbr:
            img_id = doc["textures"][pbr["baseColorTexture"]["index"]]["source"]
            image = Image.open(io.BytesIO(view(doc["images"][img_id]["bufferView"]))).convert("RGBA")
            w,h = image.size
            x,y = (544,8) if tiled else (8,print_y)
            if tiled:
                for dy in range(0,448,h):
                    for dx in range(0,448,w):
                        atlas.paste(image,(x+dx,y+dy))
            else:
                atlas.paste(image,(x,y))
                print_y += h+16
            regions.append((x,y,w,h,tiled))
        else:
            rgba = pbr.get("baseColorFactor",(1,1,1,1))
            col = [round(255*(12.92*c if c<=.0031308 else 1.055*c**(1/2.4)-.055)) for c in rgba[:3]]
            a = 20 if mat.get("alphaMode") == "BLEND" else 255
            x,y = 560+(i%6)*22,560+(i//6)*22
            ImageDraw.Draw(atlas).rectangle((x-8,y-8,x+8,y+8),fill=tuple(col+[a]))
            regions.append((x,y,0,0,False))
    static = {i:[] for i in range(16)}
    moving = {}
    pivots = {"Roulette_Rotor":"rotor","Roulette_Ball":"ball","Lever_Start":"lever",
              **{f"Rat_0{i+1}":f"rat{i}" for i in range(4)}}
    def visit(i, parent, group=None, origin=None):
        node = doc["nodes"][i]
        name = node.get("name","")
        if name.startswith("Felt | sample wager"):
            return
        m = parent @ transform(node)
        if name in pivots:
            group,origin = pivots[name],m[:3,3]
        for child in node.get("children",[]):
            visit(child,m,group,origin)
        if "mesh" not in node:
            return
        for prim in doc["meshes"][node["mesh"]]["primitives"]:
            attrs = prim["attributes"]
            points = data(attrs["POSITION"]) @ m[:3,:3].T + m[:3,3]
            normals = data(attrs["NORMAL"]) @ np.linalg.inv(m[:3,:3])
            normals /= np.linalg.norm(normals,axis=1)[:,None]
            uv = data(attrs["TEXCOORD_0"]) if "TEXCOORD_0" in attrs else np.zeros((len(points),2))
            material = prim["material"]
            x,y,w,h,tiled = regions[material]
            dynamic = "glass" if doc["materials"][material].get("alphaMode")=="BLEND" else group
            for ix in data(prim["indices"]).reshape(-1,3):
                u = uv[ix].copy()
                if tiled:
                    u -= np.floor(u.min(axis=0))
                    assert max((u*[w,h]).max(axis=0)) < 448
                coords = points[ix] - (origin if origin is not None else 0)
                if not dynamic:
                    coords += [.5,0,.5]
                tri = list(np.column_stack((coords,(u*[w,h]+[x,y])/1024,normals[ix])))
                if dynamic:
                    moving.setdefault(dynamic,[]).extend(tri)
                    continue
                lo,hi = coords.min(axis=0),coords.max(axis=0)
                for part,out in static.items():
                    col, row, level = part%4-1, (part//4)%2, part//8
                    cell = np.array([col,level,row])
                    lower,upper = cell+[-.5,0,-.5], cell+[.5,1,.5]
                    if np.any(hi<lower) or np.any(lo>=upper):
                        continue
                    poly = tri
                    for axis in range(3):
                        poly=clip(clip(poly,axis,lower[axis],1),axis,upper[axis],-1)
                    for k in range(1,len(poly)-1):
                        a,b,c = poly[0],poly[k],poly[k+1]
                        if np.linalg.norm(np.cross(b[:3]-a[:3],c[:3]-a[:3]))<1e-10:
                            continue
                        for v in (a,b,c):
                            v=v.copy()
                            v[:3]-=cell
                            out.append(v)
    for i in doc["scenes"][doc.get("scene",0)]["nodes"]:
        visit(i,np.eye(4))
    if key == "rat_derby":
        # A push button next to the original winding lever uses the same enamel and brass.
        # Both meshes are local to the axis of the button, with Y up.
        def button_part(name, outer, height, material_name):
            import math
            mat = next(i for i,m in enumerate(doc["materials"]) if m["name"]==material_name)
            u,v = np.array(regions[mat][:2])/1024
            vertices = []
            for segment in range(12):
                a,b = [i*math.tau/12 for i in (segment,segment+1)]
                ax,az,bx,bz = outer*math.cos(a),outer*math.sin(a),outer*math.cos(b),outer*math.sin(b)
                nx,nz = math.cos((a+b)/2),math.sin((a+b)/2)
                for x,y,z in ((ax,0,az),(ax,height,az),(bx,height,bz),(ax,0,az),(bx,height,bz),(bx,0,bz)):
                    vertices.append((x,y,z,u,v,nx,0,nz))
                for x,z in ((0,0),(bx,bz),(ax,az)):
                    vertices.append((x,height,z,u,v,0,1,0))
            moving[name] = vertices
        button_part("button_base",.080,.022,"Rubbed antique brass")
        button_part("button_cap",.060,.030,"Oxblood enamel")
    write_mesh(key,static)
    write_mesh(key+"_moving",moving)
    atlas.save(ASSETS/"textures/machine"/(key+".png"))
    ident = "yharnam_casino:"+key
    save_json(ASSETS/"blockstates"/(key+".json"),{"variants":{"":{"model":"yharnam_casino:block/"+key}}})
    save_json(ASSETS/"models/block"/(key+".json"),{"textures":{"particle":"yharnam_casino:machine/"+key}})
    save_json(DATA/"loot_tables/blocks"/(key+".json"),{"type":"minecraft:block","pools":[{"rolls":1,
        "entries":[{"type":"minecraft:item","name":ident}],"conditions":[{"condition":"minecraft:survives_explosion"},
        {"condition":"minecraft:block_state_property","block":ident,"properties":{"part":"1"}}]}]})
    ingredient = "green_carpet" if key=="roulette_table" else "glass"
    save_json(DATA/"recipes"/(key+".json"),{"type":"minecraft:crafting_shaped","pattern":["ABA","PCP","S S"],
        "key":{k:{"item":"minecraft:"+v} for k,v in
               dict(A=ingredient,B="gold_ingot" if key=="roulette_table" else "redstone",C="iron_ingot",P="dark_oak_planks",S="stick").items()},
        "result":{"item":ident}})
    # Downsample the matching transparent Blender render for the inventory.
    icon=Image.open(ART/(filename+".png")).convert("RGBA")
    icon=icon.crop(icon.getbbox())
    icon.thumbnail((60,60),Image.Resampling.LANCZOS)
    canvas=Image.new("RGBA",(64,64))
    canvas.paste(icon,((64-icon.width)//2,(64-icon.height)//2))
    canvas.save(ASSETS/"textures/item"/(key+".png"))
    save_json(ASSETS/"models/item"/(key+".json"),{"parent":"minecraft:item/generated","textures":{"layer0":"yharnam_casino:item/"+key}})
    print(key,{k:len(v)//3 for k,v in moving.items()},flush=True)


if __name__=="__main__":
    compile_table("roulette_table","roulette")
    compile_table("rat_derby","rat-derby")
