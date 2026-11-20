"""Bake the dispenser into two chunk cells and export the crank and hand-held capsule layers."""
import io
import json
import struct
import numpy as np
from PIL import Image, ImageDraw
from compile_gaming_floor import ASSETS, ART, clip, write_mesh
from compile_attractions import transform

ART_DIR = ART / "gachapon"
COLORS = ("blue", "purple", "orange", "red")
LAYERS = ("shell", "glass", "core")


class Atlas:
    def __init__(self):
        self.image = Image.new("RGBA",(512,512),"#433529")
        self.palette = {}

    def region(self, mat, doc, view):
        name = mat["name"]
        if name in self.palette:
            return self.palette[name]
        pbr = mat["pbrMetallicRoughness"]
        if "baseColorTexture" in pbr:
            source = doc["textures"][pbr["baseColorTexture"]["index"]]["source"]
            tile = Image.open(io.BytesIO(view(doc["images"][source]["bufferView"]))).convert("RGBA")
            if name=="Stamped ivory cup":
                self.image.paste(tile,(320,32))
                region=(320,32,tile.width,tile.height)
            else:
                assert name=="Polished walnut", name
                for y in range(0,256,tile.height):
                    for x in range(0,256,tile.width):
                        self.image.paste(tile,(x,y))
                region = (4,4,tile.width,tile.height)
        else:
            red,green,blue,alpha = pbr.get("baseColorFactor",(1,1,1,1))
            # Keep the brightest facets of an emitting core distinct even without postprocessing.
            rgb = tuple(round(255*(c*12.92 if c<=.0031308 else 1.055*c**(1/2.4)-.055)) for c in (red,green,blue))
            x,y = 10+(len(self.palette)%22)*22,278+(len(self.palette)//22)*22
            ImageDraw.Draw(self.image).rectangle((x-8,y-8,x+8,y+8),fill=(*rgb,round(255*alpha)))
            region=(x+.5,y+.5,0,0)
        self.palette[name]=region
        return region


def triangles(path,atlas):
    raw=path.read_bytes()
    length=struct.unpack_from("<I",raw,12)[0]
    doc=json.loads(raw[20:20+length])
    binary=raw[28+length:]
    def view(index):
        v=doc["bufferViews"][index]
        start=v.get("byteOffset",0)
        return binary[start:start+v["byteLength"]]
    def data(index):
        a=doc["accessors"][index]
        dt={5121:"u1",5123:"<u2",5125:"<u4",5126:"<f4"}[a["componentType"]]
        n={"VEC2":2,"VEC3":3,"SCALAR":1}[a["type"]]
        return np.frombuffer(view(a["bufferView"]),dt,count=a["count"]*n,offset=a.get("byteOffset",0)).reshape(-1,n)
    def walk(index,parent,crank=None):
        node=doc["nodes"][index]
        if node["name"].startswith("Outlet_Capsule"): return
        matrix=parent@transform(node)
        if node["name"]=="Crank_Pivot": crank=matrix[:3,3]
        for child in node.get("children",[]):
            yield from walk(child,matrix,crank)
        if "mesh" not in node: return
        for prim in doc["meshes"][node["mesh"]]["primitives"]:
            mat=doc["materials"][prim["material"]]
            layer="core" if mat["name"].startswith("Lumen ") else "glass" if mat.get("alphaMode")=="BLEND" else "shell"
            a=prim["attributes"]
            points=data(a["POSITION"])@matrix[:3,:3].T+matrix[:3,3]
            if crank is not None: points-=crank
            normals=data(a["NORMAL"])@np.linalg.inv(matrix[:3,:3])
            normals/=np.linalg.norm(normals,axis=1)[:,None]
            uv=data(a["TEXCOORD_0"]) if "TEXCOORD_0" in a else np.zeros((len(points),2))
            x,y,w,h=atlas.region(mat,doc,view)
            for ix in data(prim["indices"]).reshape(-1,3):
                u=uv[ix].copy()
                if w: u-=np.floor(u.min(axis=0))
                mapped=(u*[w,h]+[x,y])/512
                yield layer,crank is not None,list(np.column_stack((points[ix],mapped,normals[ix])))
    for i in doc["scenes"][doc.get("scene",0)]["nodes"]:
        yield from walk(i,np.eye(4))


def main():
    atlas=Atlas()
    static={half+suffix:[] for half in ("lower","upper") for suffix in ("","_glass","_core")}
    moving={"crank":[]}
    for layer,crank,tri in triangles(ART_DIR/"gachapon.glb",atlas):
        if crank:
            moving["crank"].extend(tri)
            continue
        for level,half in enumerate(("lower","upper")):
            if min(v[1] for v in tri)>=level+1: continue
            poly=clip(clip(tri,1,level,1),1,level+1,-1)
            dst=static[half+("" if layer=="shell" else "_"+layer)]
            for j in range(1,len(poly)-1):
                a,b,c=poly[0],poly[j],poly[j+1]
                if np.linalg.norm(np.cross(b[:3]-a[:3],c[:3]-a[:3]))<1e-12: continue
                for v in (a,b,c):
                    v=v.copy()
                    v[1]-=level
                    dst.append(v)
    for color in COLORS:
        for layer in LAYERS: moving[color+"_"+layer]=[]
        file="capsule.glb" if color=="blue" else "capsule_"+color+".glb"
        for layer,_,tri in triangles(ART_DIR/file,atlas):
            # Closed in-game items cannot expose the inner walls of either half.
            if layer!="core" and np.dot(tri[0][:3],tri[0][5:])<-.001: continue
            moving[color+"_"+layer].extend(tri)
    assert all(len(v)>0 for v in moving.values()) and static["upper_core"] and static["upper_glass"]
    write_mesh("gachapon",static)
    write_mesh("gachapon_moving",moving)
    atlas.image.save(ASSETS/"textures/machine/gachapon.png")
    print("static", {k:len(v)//3 for k,v in static.items()}, "moving",{k:len(v)//3 for k,v in moving.items()})


if __name__=="__main__":
    main()
