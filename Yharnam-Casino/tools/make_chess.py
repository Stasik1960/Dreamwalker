"""Build the chess furniture, textured chessmen and an intermediate scene for Blender."""
import json, math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from compile_gaming_floor import ASSETS, DATA, ROOT, clip, save_json, write_mesh

BUILD=ROOT/"build/chess-art"
ART=ROOT/"art/gaming-floor/chess"
CELL=.19
SURFACE=.75
PALETTE={"walnut":(72,46,37),"oak":(104,70,47),"edge":(43,32,31),"bronze":(151,108,57),
         "gold":(198,159,95),"felt":(48,66,61),"ivory":(225,207,164),"lighttrim":(177,145,99),
         "ebony":(54,59,66),"darktrim":(112,102,88),"red":(145,61,59),"green":(65,104,91)}
TILES={name:(352+48*(i%3),8+48*(i//3)) for i,name in enumerate(PALETTE)}
def atlas():
    im=Image.new("RGB",(512,512));pix=im.load()
    for n,(sx,sy) in TILES.items():
        for y in range(32):
            for x in range(32):
                a=(math.sin(x*.28+math.sin(y*.15))*.62+math.sin(x*1.2+y*.11)*.23)*(.075 if n in ("walnut","oak","edge") else .025)
                pix[sx+x,sy+y]=tuple(int(v*(1+a)) for v in PALETTE[n])
    d=ImageDraw.Draw(im);d.rectangle((0,0,335,335),fill="#392e2a")
    d.rectangle((2,2,333,333),outline="#ab8658",width=2)
    for r in range(8):
        for f in range(8):
            x0,y0=16+f*38,16+r*38
            col=(204,178,135) if (f+r)%2==0 else (65,82,75)
            for j in range(38):
                for i in range(38):
                    tint=math.sin(i*.18+2.8*f+.45*math.sin(j*.10+r))*1.6+math.sin(i*.88+j*.05)*.9
                    pix[x0+i,y0+j]=tuple(int(k+tint) for k in col)
    font=ImageFont.truetype("C:/Windows/Fonts/consolab.ttf" if Path("C:/Windows/Fonts/consolab.ttf").exists() else "DejaVuSansMono.ttf",12)
    for i in range(8):
        n=16+(i+.5)*38
        for y in (8,328):d.text((n,y),chr(65+i),font=font,anchor="mm",fill="#eed8ab")
        for x in (8,328):d.text((x,n),str(8-i),font=font,anchor="mm",fill="#eed8ab")
    # Physical icons on the two control buttons.
    for sx,bg in [(356,"#97433e"),(406,"#456b61")]:
        d.rectangle((sx,235,sx+37,272),fill=bg,outline="#be9e68",width=2)
    d.arc((363,242,384,264),-100,200,fill="#ffe4ad",width=3)
    d.polygon([(361,248),(370,249),(366,255)],fill="#ffe4ad")
    d.text((425,252),"½",font=ImageFont.truetype(font.path,25),anchor="mm",fill="#ffe4ad")
    (ASSETS/"textures/machine").mkdir(parents=True,exist_ok=True);im.save(ASSETS/"textures/machine/chess_table.png")
    return im

def uv_tile(mat,n):
    x,y=TILES[mat]
    if n==4:uv=[(.02,.02),(.02,.98),(.98,.98),(.98,.02)]
    else:uv=[(.5+.46*math.cos(2*math.pi*i/n),.5+.46*math.sin(2*math.pi*i/n)) for i in range(n)]
    return [((x+1+u*29)/512,(y+1+v*29)/512) for u,v in uv]
def face(out,points,mat,uv=None):
    uv=uv or uv_tile(mat,len(points))
    for i in range(1,len(points)-1):
        inds=(0,i,i+1);p=[points[j] for j in inds];n=np.cross(np.subtract(p[1],p[0]),np.subtract(p[2],p[0]))
        if np.linalg.norm(n)<1e-10:continue
        n=(n/np.linalg.norm(n)).tolist()
        out.append([list(p[k])+list(uv[j])+n for k,j in enumerate(inds)])
def loft(out,rings,mat):
    face(out,rings[0],mat)
    for a,b in zip(rings,rings[1:]):
        for i in range(len(a)):
            j=(i+1)%len(a);face(out,[a[i],b[i],b[j],a[j]],mat)
    face(out,rings[-1][::-1],mat)
def rect(out,x,z,w,d,b,t,mat,cut=.008):
    c=min(cut,w/3,d/3)
    r=[(-w/2+c,-d/2),(w/2-c,-d/2),(w/2,-d/2+c),(w/2,d/2-c),(w/2-c,d/2),(-w/2+c,d/2),(-w/2,d/2-c),(-w/2,-d/2+c)]
    loft(out,[[(x+dx,h,z+dz) for dx,dz in r] for h in (b,t)],mat)
def turn(out,x,z,profile,mat,sides=9):
    loft(out,[[(x+r*math.cos((i+.5)*2*math.pi/sides),h,z+r*math.sin((i+.5)*2*math.pi/sides)) for i in range(sides)] for h,r in profile],mat)
def stamp(out,x,z,w,d,y,region):
    a,b,c,e=region
    face(out,[(x-w/2,y,z-d/2),(x-w/2,y,z+d/2),(x+w/2,y,z+d/2),(x+w/2,y,z-d/2)],"oak",[(a/512,b/512),(a/512,e/512),(c/512,e/512),(c/512,b/512)])
def table():
    out=[]
    for x in (-1.12,1.12):
        for z in (-.68,.68):
            for a,b,w,dep,mat in [(0,.07,.235,.21,"edge"),(.07,.13,.19,.17,"oak"),(.13,.49,.143,.14,"walnut"),(.48,.505,.154,.15,"bronze"),(.505,.57,.18,.17,"oak")]:
                rect(out,x,z,w,dep,a,b,mat,.025)
        rect(out,x,0,.118,1.4,.19,.26,"walnut")
    rect(out,0,0,2.25,.105,.20,.245,"edge")
    for w,d,b,t,mat in [(2.69,1.76,.50,.614,"walnut"),(2.76,1.83,.594,.62,"bronze"),(2.84,1.91,.62,.69,"edge"),(2.88,1.94,.682,.706,"oak"),(2.84,1.90,.706,.722,"walnut")]:
        rect(out,0,0,w,d,b,t,mat,.075)
    rect(out,0,0,1.70,1.70,.72,.748,"bronze",.022)
    stamp(out,0,0,1.68,1.68,SURFACE,(.5,.5,335.5,335.5))
    for sign in (-1,1):
        x=1.14*sign
        rect(out,x,0,.46,1.54,.722,.732,"bronze",.033)
        rect(out,x,0,.432,1.515,.733,.74,"felt",.025)
        for z in (-.749,.749):rect(out,x,z,.435,.019,.74,.753,"oak")
        for dx in (-.215,0,.215):rect(out,x+dx,0,.015,1.49,.74,.754,"oak")
        for end in (-1,1):
            z=end*.838
            reset=sign==end
            rect(out,x,z,.216,.148,.722,.742,"bronze",.026)
            rect(out,x,z,.183,.119,.742,.76,"red" if reset else "green",.018)
            sx=356 if reset else 406
            uv=(sx+.5,235.5,sx+37.5,272.5)
            stamp(out,x,z,.115,.10,.7605,uv if end==1 else (uv[2],uv[3],uv[0],uv[1]))
    return out
def piece(kind,white):
    out=[];main="ivory" if white else "ebony";trim="lighttrim" if white else "darktrim"
    size=.91 if kind=="p" else 1
    turn(out,0,0,[(0,.068*size),(.012,.075*size),(.022,.071*size),(.030,.060*size)],main,10)
    turn(out,0,0,[(.024,.067*size),(.032,.065*size)],trim,10)
    h={"p":.079,"r":.087,"n":.067,"b":.108,"q":.116,"k":.122}[kind]
    turn(out,0,0,[(.031,.059*size),(.044,.048*size),(h-.012,.028*size),(h,.044*size)],main)
    turn(out,0,0,[(h-.004,.044*size),(h+.005,.044*size)],trim)
    if kind=="p":
        turn(out,0,0,[(h+.003,.020),(.093,.037),(.112,.040),(.133,.024),(.139,.009)],main)
    elif kind=="r":
        turn(out,0,0,[(h,.040),(.105,.058),(.130,.058)],main,8)
        for j in range(4):
            x=.039*math.cos(j*math.pi/2);z=.039*math.sin(j*math.pi/2)
            rect(out,x,z,.032,.032,.128,.158,main,.003)
    elif kind=="n":
        # A stepped mane, swept ears and projecting muzzle distinguish the horse even in silhouette.
        shape=[(-.043,.065),(.041,.065),(.040,.102),(.028,.135),(.017,.154),(.021,.186),(.003,.176),(-.008,.190),(-.018,.167),(-.048,.153),(-.065,.123),(-.057,.114),(-.026,.119),(-.008,.128),(-.013,.104)]
        # Profile in Z/Y, extruded across X. Triangulate the concave face as a simple polygon.
        ring=[(z if white else -z,y) for z,y in shape]
        triangles=[(14, 0, 1), (14, 1, 2), (14, 2, 3), (14, 3, 4), (14, 4, 5), (14, 5, 6), (6, 7, 8), (6, 8, 9), (6, 9, 10), (6, 10, 11), (6, 11, 12), (6, 12, 13), (6, 13, 14)]
        for side in [-1,1]:
            for triangle in triangles:
                pts=[(side*.022,ring[i][1],ring[i][0]) for i in triangle]
                if np.cross(np.subtract(pts[1],pts[0]),np.subtract(pts[2],pts[0]))[0]*side<0:pts.reverse()
                face(out,pts,main)
        for i,(z,y) in enumerate(ring):
            nz,ny=ring[(i+1)%len(ring)];pts=[(-.022,y,z),(.022,y,z),(.022,ny,nz),(-.022,ny,nz)]
            if not white:pts.reverse()
            face(out,pts,main)
        for sx in [-1,1]:rect(out,sx*.022,-.026 if white else .026,.004,.010,.146,.154,trim,.001)
    elif kind=="b":
        turn(out,0,0,[(h,.023),(.126,.044),(.147,.041),(.170,.026),(.186,.008)],main)
        # A mitre notch on each face.
        for z in (-.038,.038):
            face(out,[(-.020,.143,z),(-.013,.141,z),(.009,.166,z*.77),(.003,.17,z*.75)],trim)
    elif kind=="q":
        turn(out,0,0,[(h,.027),(.129,.032),(.153,.052),(.160,.052)],main,10)
        for j in range(5):
            a=j*2*math.pi/5;turn(out,math.cos(a)*.042,math.sin(a)*.042,[(.156,.013),(.177,.010),(.184,.004)],trim,5)
        turn(out,0,0,[(.154,.015),(.179,.010),(.189,.003)],main,8)
    else:
        turn(out,0,0,[(h,.033),(.138,.046),(.154,.036),(.163,.020)],main)
        rect(out,0,0,.019,.020,.159,.217,main,.002)
        rect(out,0,0,.062,.021,.188,.201,main,.002)
    return out

def lod_piece(kind, white):
    """A recognisable five-sided silhouette for tables farther than nine blocks."""
    out=[];main="ivory" if white else "ebony";trim="lighttrim" if white else "darktrim"
    if kind=="n":
        # Low-poly base plus an asymmetric horse profile. The opposite facing
        # for black keeps both armies looking across the board.
        turn(out,0,0,[(0,.061),(.022,.066),(.038,.045)],main,5)
        profile=[(-.041,.036),(.037,.036),(.032,.087),(.015,.151),
                 (-.004,.181),(-.044,.149),(-.059,.112),(-.027,.119)]
        ring=[(z if white else -z,y) for z,y in profile]
        triangles=[(7,0,1),(7,1,2),(7,2,3),(7,3,4),(4,5,6),(4,6,7)]
        for side in (-1,1):
            for triangle in triangles:
                points=[(side*.021,ring[i][1],ring[i][0]) for i in triangle]
                if np.cross(np.subtract(points[1],points[0]),
                            np.subtract(points[2],points[0]))[0]*side<0:
                    points.reverse()
                face(out,points,main)
        for i,(z,y) in enumerate(ring):
            nz,ny=ring[(i+1)%len(ring)]
            points=[(-.021,y,z),(.021,y,z),(.021,ny,nz),(-.021,ny,nz)]
            if white:points.reverse()
            face(out,points,main)
        return out
    profiles={
        "p":[(0,.061),(.025,.066),(.083,.030),(.139,.005)],
        "r":[(0,.069),(.040,.052),(.112,.052),(.158,.046)],
        "b":[(0,.069),(.051,.048),(.134,.026),(.186,.007)],
        "q":[(0,.070),(.049,.045),(.135,.051),(.189,.013)],
        "k":[(0,.070),(.054,.045),(.150,.031),(.217,.010)],
    }
    turn(out,0,0,profiles[kind],main,5)
    # The trim band keeps side and type readable without adding another ring.
    if kind in "qk":
        turn(out,0,0,[(profiles[kind][2][0],.033),
                      (profiles[kind][2][0]+.006,.034)],trim,5)
    return out

def hint_meshes():
    """Two-layer teal and brass square used as a low-cost legal-move mark."""
    out=[]
    face(out,[(-.015,0,-.015),(-.015,0,.015),(.015,0,.015),(.015,0,-.015)],"bronze")
    face(out,[(-.009,.001,-.009),(-.009,.001,.009),(.009,.001,.009),(.009,.001,-.009)],"green")
    return out

def main():
    BUILD.mkdir(parents=True,exist_ok=True);ART.mkdir(parents=True,exist_ok=True)
    atlas();top=table(); pieces={('w' if w else 'b')+p:piece(p,w) for w in (True,False) for p in "pnbrqk"}
    lod={('w' if w else 'b')+p:lod_piece(p,w) for w in (True,False) for p in "pnbrqk"}
    data={"table":top,**pieces}
    (BUILD/"geometry.json").write_text(json.dumps(data,separators=(',',':')),encoding="utf-8")
    parts={i:[] for i in range(6)}
    for tri in top:
        for i,dest in parts.items():
            col=i%3-1;row=i//3;poly=[np.array(v) for v in tri]
            for axis,lo,hi in ((0,col-.5,col+.5),(2,row-1,row)):
                if not poly:break
                if all(abs(v[axis]-hi)<1e-9 for v in poly):poly=[];break
                poly=clip(clip(poly,axis,lo,1),axis,hi,-1)
            for k in range(1,len(poly)-1):
                a,b,c=poly[0],poly[k],poly[k+1]
                if np.linalg.norm(np.cross(b[:3]-a[:3],c[:3]-a[:3]))<1.e-10:continue
                for vv in (a,b,c):
                    v=vv.copy();v[0]-=col;v[2]+=.5-row;dest.append(v)
    write_mesh("chess_table",parts)
    write_mesh("chessmen",{k:[v for tri in tris for v in tri] for k,tris in pieces.items()})
    write_mesh("chessmen_lod",{k:[v for tri in tris for v in tri] for k,tris in lod.items()})
    write_mesh("chess_hint",{"hint":[v for tri in hint_meshes() for v in tri]})
    counts={k:len(v) for k,v in lod.items()}
    if max(counts.values())>60:
        raise ValueError(f"Chess LOD exceeds 60 triangles: {counts}")
    save_json(ASSETS/"blockstates/chess_table.json",{"variants":{"":{"model":"yharnam_casino:block/chess_table"}}})
    save_json(ASSETS/"models/block/chess_table.json",{"textures":{"particle":"yharnam_casino:machine/chess_table"}})
    for k in ("chess_table","chess_set"):save_json(ASSETS/f"models/item/{k}.json",{"parent":"minecraft:item/generated","textures":{"layer0":f"yharnam_casino:item/{k}"}})
    save_json(DATA/"loot_tables/blocks/chess_table.json",{"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"yharnam_casino:chess_table"}],"conditions":[{"condition":"minecraft:survives_explosion"},{"condition":"minecraft:block_state_property","block":"yharnam_casino:chess_table","properties":{"part":"1"}}]}]})
    for name,pattern,keys in [("chess_table",["WBW","PPP","F F"],{"W":"birch_planks","B":"polished_deepslate","P":"dark_oak_planks","F":"dark_oak_fence"}),("chess_set",["IBI","PPP"],{"I":"bone","B":"blackstone","P":"dark_oak_planks"})]:
        save_json(DATA/f"recipes/{name}.json",{"type":"minecraft:crafting_shaped","pattern":pattern,"key":{k:{"item":"minecraft:"+v} for k,v in keys.items()},"result":{"item":"yharnam_casino:"+name}})
    print("Baked six table cells and twelve chessmen",len(top),{k:len(v) for k,v in pieces.items()})
    print("Chess LOD triangles",counts)
if __name__=="__main__":main()
