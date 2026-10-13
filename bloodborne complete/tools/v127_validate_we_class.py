"""Read-only pinned optional WE class descriptor check; no Java execution."""
import zipfile,struct,json
from pathlib import Path
p=Path(__file__).resolve().parents[1]/'build/v127-dependencies/worldedit-mod-7.2.15.jar'
with zipfile.ZipFile(p) as z:b=z.read('com/sk89q/worldedit/world/block/BlockState.class')
at=8
def u2():
    global at
    n=int.from_bytes(b[at:at+2],'big');at+=2;return n
count=u2();cp={};i=1
while i<count:
    tag=b[at];at+=1
    if tag==1:n=u2();cp[i]=b[at:at+n].decode();at+=n
    elif tag in [3,4]:at+=4
    elif tag in [5,6]:at+=8;i+=1
    elif tag in [7,8,16,19,20]:at+=2
    elif tag in [9,10,11,12,17,18]:at+=4
    elif tag==15:at+=3
    else:raise ValueError(tag)
    i+=1
at+=6;interfaces=u2();at+=2*interfaces;fields=[]
for i in range(u2()):
    access,name,descriptor=u2(),u2(),u2();fields.append({'name':cp[name],'descriptor':cp[descriptor],'access':access})
    for j in range(u2()):at+=2;n=int.from_bytes(b[at:at+4],'big');at+=4+n
expected={'blockType':'Lcom/sk89q/worldedit/world/block/BlockType;','values':'Ljava/util/Map;','states':'Lcom/google/common/collect/Table;'}
assert all(any(f['name']==name and f['descriptor']==descriptor for f in fields) for name,descriptor in expected.items())
print(json.dumps({'status':'PASS_PINNED_WE_SHADOW_DESCRIPTORS','fields':fields}))
