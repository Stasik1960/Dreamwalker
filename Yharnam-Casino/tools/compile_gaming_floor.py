"""Bake the Blender GLB props into per-block chunk meshes and a separate wheel rotor."""
import io, json, math, struct
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/"src/main/resources/assets/yharnam_casino"
DATA=ROOT/"src/main/resources/data/yharnam_casino"
ART=ROOT/"art/gaming-floor"
MODELS={"poker_table":"poker","black_jack_table":"black-jack","fortune_wheel":"wheel-of-fortune"}

def save_json(path,obj):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(obj,indent=2,ensure_ascii=False)+"\n",encoding="utf8")

def clip(poly,axis,level,direction):
    out=[]
    for a,b in zip(poly,poly[1:]+poly[:1]):
        da,db=(a[axis]-level)*direction,(b[axis]-level)*direction
        if da>=0: out.append(a)
        if (da<0<db) or (da>0>db): out.append(a+(b-a)*(da/(da-db)))
    return out

def write_mesh(name,parts):
    dest=ASSETS/"models/machine"/(name+".mesh")
    with dest.open("wb") as f:
        f.write(b"YCSM"+struct.pack(">ii",1,len(parts)))
        for part,verts in parts.items():
            label=str(part).encode()
            f.write(struct.pack(">H",len(label))+label+struct.pack(">3fi",0,0,0,len(verts)))
            f.write(np.asarray(verts,dtype=">f4").tobytes())

def compile_model(key,file):
    raw=(ART/(file+".glb")).read_bytes()
    length=struct.unpack_from("<I",raw,12)[0]
    doc=json.loads(raw[20:20+length]);binary=raw[28+length:]
    wheel=key=="fortune_wheel"
    parts={i:[] for i in range(9 if wheel else 6)}
    rotor=[]
    atlas=Image.new("RGB",(512,512),"#423431")
    def view(index):
        v=doc["bufferViews"][index];off=v.get("byteOffset",0)
        return binary[off:off+v["byteLength"]]
    def read(index):
        a=doc["accessors"][index]
        dtype={5123:"<u2",5125:"<u4",5126:"<f4"}[a["componentType"]]
        n={"SCALAR":1,"VEC2":2,"VEC3":3}[a["type"]]
        return np.frombuffer(view(a["bufferView"]),dtype,offset=a.get("byteOffset",0),count=a["count"]*n).reshape(-1,n)
    palettes=[]
    for i,mat in enumerate(doc["materials"]):
        pbr=mat["pbrMetallicRoughness"]
        if mat["name"] in ("Polished walnut","Printed baize","Printed numbered sectors"):
            index=doc["textures"][pbr["baseColorTexture"]["index"]]["source"]
            tex=Image.open(io.BytesIO(view(doc["images"][index]["bufferView"]))).convert("RGB")
            tiled=mat["name"]=="Polished walnut"
            x,y=(8,248) if tiled else (8,8)
            for ny in range(12 if tiled else 1):
                for nx in range(12 if tiled else 1): atlas.paste(tex,(x+nx*tex.width,y+ny*tex.height))
            palettes.append((x,y,tex.width,tex.height,tiled))
        else:
            col=[round(255*(12.92*c if c<=.0031308 else 1.055*c**(1/2.4)-.055)) for c in pbr.get("baseColorFactor",[1,1,1])[:3]]
            x=418+(i%4)*18;y=24+(i//4)*18
            ImageDraw.Draw(atlas).rectangle((x-6,y-6,x+6,y+6),fill=tuple(col))
            palettes.append((x,y,0,0,False))
    def visit(index,parent,is_rotor=False):
        node=doc["nodes"][index];name=node["name"]
        if name.startswith(("Stack layer","Playing card","Tray roll","Chip edge")):return
        x,y,z,w=node.get("rotation",(0,0,0,1))
        rot=np.array([[1-2*(y*y+z*z),2*(x*y-w*z),2*(x*z+w*y)],
                      [2*(x*y+w*z),1-2*(x*x+z*z),2*(y*z-w*x)],
                      [2*(x*z-w*y),2*(y*z+w*x),1-2*(x*x+y*y)]])
        m=np.eye(4);m[:3,:3]=rot@np.diag(node.get("scale",[1,1,1]));m[:3,3]=node.get("translation",[0,0,0])
        if "matrix" in node:m=np.asarray(node["matrix"]).reshape(4,4).T
        m=parent@m
        is_rotor=is_rotor or "axle pivot" in name
        for child in node.get("children",[]):visit(child,m,is_rotor)
        if "mesh" not in node:return
        for prim in doc["meshes"][node["mesh"]]["primitives"]:
            attrs=prim["attributes"]
            points=read(attrs["POSITION"])@m[:3,:3].T+m[:3,3]
            normals=read(attrs["NORMAL"])@np.linalg.inv(m[:3,:3])
            normals/=np.linalg.norm(normals,axis=1)[:,None]
            uv=read(attrs["TEXCOORD_0"])
            x,y,w,h,tiled=palettes[prim["material"]]
            for ix in read(prim["indices"]).reshape(-1,3):
                coords=points[ix].copy()
                if is_rotor:coords-=np.array([0,1.43,-.06])
                elif not wheel:coords[:,2]+=.5
                uvs=uv[ix].copy()
                if tiled:uvs-=np.floor(uvs.min(axis=0))
                mapped=(uvs*np.array([w,h])+[x,y])/512
                tri=list(np.column_stack((coords,mapped,normals[ix])))
                if is_rotor:rotor.extend(tri);continue
                for part,out in parts.items():
                    col=part%3-1;row=part//3
                    bounds=[(0,col-.5,col+.5),(1,row,row+1)] if wheel else [(0,col-.5,col+.5),(2,row-.5,row+.5)]
                    poly=tri
                    for axis,lo,hi in bounds:
                        if not poly:break
                        if min(p[axis] for p in poly)==max(p[axis] for p in poly)==hi:poly=[];break
                        poly=clip(clip(poly,axis,lo,1),axis,hi,-1)
                    for k in range(1,len(poly)-1):
                        a,b,c=poly[0],poly[k],poly[k+1]
                        if np.linalg.norm(np.cross(b[:3]-a[:3],c[:3]-a[:3]))<1e-10:continue
                        for v in (a,b,c):
                            v=v.copy();v[0]-=col;v[1 if wheel else 2]-=row;out.append(v)
    for n in doc["scenes"][doc.get("scene",0)]["nodes"]:visit(n,np.eye(4))
    if rotor:write_mesh("fortune_rotor",{"rotor":rotor})
    write_mesh(key,parts)
    atlas.save(ASSETS/"textures/machine"/(key+".png"))
    save_json(ASSETS/"blockstates"/(key+".json"),{"variants":{"":{"model":"yharnam_casino:block/"+key}}})
    save_json(ASSETS/"models/block"/(key+".json"),{"textures":{"particle":"yharnam_casino:machine/"+key}})
    save_json(DATA/"loot_tables/blocks"/(key+".json"),{"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"yharnam_casino:"+key}],"conditions":[{"condition":"minecraft:survives_explosion"},{"condition":"minecraft:block_state_property","block":"yharnam_casino:"+key,"properties":{"part":"1"}}]}]})
    print(key,sum(map(len,parts.values()))//3,len(rotor)//3)

def tower_texture():
    from math import cos,sin,pi
    image=Image.new("RGBA",(64,128))
    d=ImageDraw.Draw(image)
    for y in range(0,128,8):
        d.rectangle((0,y,31,y+7),fill="#dbd5cb")
        d.rectangle((2,y+1,10,y+5),fill="#fff8dd")
        d.rectangle((19,y+1,28,y+5),fill="#706458")
        d.line((0,y+6,31,y+6),fill="#9f9184")
        d.line((0,y+7,31,y+7),fill="#61574e")
    points=[(47.5+16*cos((i+.5)*pi/4),15.5+16*sin((i+.5)*pi/4)) for i in range(8)]
    d.polygon(points,fill="#e1d9c8",outline="#756a5f")
    d.ellipse((38,6,57,25),fill="#fff4d6",outline="#9b8971",width=2)
    d.polygon([(48,10),(52,16),(48,21),(44,16)],fill="#a29276")
    image.save(ASSETS/"textures/entity/cards/towers.png")

def icons():
    tower_texture()
    for key in [*MODELS,"card_deck"]:
        icon=Image.new("RGBA",(32,32));d=ImageDraw.Draw(icon)
        if key=="card_deck":
            d.rounded_rectangle((5,4,23,29),radius=2,fill="#6d4d40",outline="#302224")
            d.rounded_rectangle((8,2,26,26),radius=2,fill="#eee1ba",outline="#382f32")
            d.rectangle((10,4,24,24),fill="#663e4a",outline="#ad8959")
            d.polygon([(17,7),(22,14),(17,21),(12,14)],outline="#efcc8e")
        elif key=="fortune_wheel":
            d.rectangle((10,19,13,29),fill="#614235");d.rectangle((19,19,22,29),fill="#614235");d.rectangle((6,28,25,30),fill="#735340")
            for i in range(16):d.pieslice((3,1,28,25),i*22.5,(i+1)*22.5,fill=("#864752","#435360","#687d65","#513f4b")[i%4])
            d.ellipse((3,1,28,25),outline="#c19962",width=2);d.rectangle((14,11,17,14),fill="#e4c58a")
            d.polygon([(13,1),(19,1),(16,6)],fill="#e6c697")
        else:
            for x in (7,22):d.rectangle((x,17,x+3,28),fill="#715239")
            d.polygon([(1,12),(7,7),(25,7),(31,11),(31,16),(26,20),(6,20),(1,17)],fill="#523935")
            d.polygon([(1,11),(7,6),(25,6),(31,10),(31,14),(25,18),(6,18),(1,15)],fill="#61414d" if key=="poker_table" else "#454b48")
            d.polygon([(4,11),(8,8),(24,8),(28,10),(28,13),(24,15),(7,15),(4,14)],fill="#487452" if key=="poker_table" else "#397476")
            for x in (11,15,19):d.rectangle((x,10,x+2,12),fill="#e4d1a5")
        icon.save(ASSETS/"textures/item"/(key+".png"))
        save_json(ASSETS/"models/item"/(key+".json"),{"parent":"minecraft:item/generated","textures":{"layer0":"yharnam_casino:item/"+key}})
    for key,pattern,symbol,ingredient in (("card_deck",["PPP","PDP"],"D","red_dye"),
        ("poker_table",["WWW","PIP","S S"],"W","green_wool"),("black_jack_table",["WWW","PIP","S S"],"W","cyan_wool"),
        ("fortune_wheel",["WSW","PIP","S S"],"W","red_wool")):
        refs={"P":"paper" if key=="card_deck" else "dark_oak_planks","S":"stick","I":"iron_ingot",symbol:ingredient}
        save_json(DATA/"recipes"/(key+".json"),{"type":"minecraft:crafting_shaped","pattern":pattern,"key":{k:{"item":"minecraft:"+v} for k,v in refs.items() if k in "".join(pattern)},"result":{"item":"yharnam_casino:"+key}})
    folder=ASSETS/"textures/entity/cards";folder.mkdir(parents=True,exist_ok=True)
    atlas=Image.new("RGBA",(512,256))
    font=ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf",14) if Path("C:/Windows/Fonts/arialbd.ttf").is_file() else ImageFont.truetype("DejaVuSans-Bold.ttf",14)
    for suit,symbol in enumerate(("♣","♦","♥","♠")):
        for rank,label in enumerate(["2","3","4","5","6","7","8","9","10","J","Q","K","A"]):
            x=32*rank;y=48*suit
            d=ImageDraw.Draw(atlas);color="#963946" if suit in (1,2) else "#202b2a"
            d.rounded_rectangle((x,y,x+31,y+47),radius=3,fill="#eadab9",outline="#917b5e")
            d.text((x+3,y+1),label,font=font,fill=color)
            d.text((x+16,y+28),symbol,anchor="mm",font=font,fill=color)
    atlas.save(folder/"faces.png")
    back=Image.new("RGBA",(32,48));d=ImageDraw.Draw(back)
    d.rounded_rectangle((0,0,31,47),radius=3,fill="#d5bb8a",outline="#77664c")
    d.rectangle((2,2,29,45),fill="#65414b",outline="#9d7a58")
    for r in (5,10):d.polygon([(16,24-r*1.65),(16+r,24),(16,24+r*1.65),(16-r,24)],outline="#cdad74")
    back.save(folder/"classic.png")
    counter=Image.new("RGBA",(16,16));d=ImageDraw.Draw(counter)
    d.ellipse((0,0,15,15),fill="#5c5750",outline="#49453d")
    d.ellipse((0,0,15,13),fill="#ffffff",outline="#b8ad93")
    d.ellipse((4,3,11,10),outline="#8f806a")
    for x,y in ((7,0),(1,4),(12,3),(4,11),(11,10)):d.rectangle((x,y,x+1,y+1),fill="#8e8169")
    counter.save(folder/"counter.png")

if __name__=="__main__":
    for k,v in MODELS.items():compile_model(k,v)
    icons()
