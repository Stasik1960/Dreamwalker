"""Read pinned native bytecode without running Java, proving opacity dispatch."""
import hashlib,json,struct,zipfile
from pathlib import Path
from v127_visual_export import ROOT,write_json

JAR=Path.home()/'.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2/minecraft-merged-1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2.jar'
def read_class(blob):
    at=8;pool=[None]
    def u2():
        nonlocal at
        n=int.from_bytes(blob[at:at+2],'big');at+=2;return n
    size=u2()
    while len(pool)<size:
        tag=blob[at];at+=1
        if tag==1:n=u2();pool.append(blob[at:at+n].decode());at+=n
        elif tag in (3,4):pool.append(blob[at:at+4]);at+=4
        elif tag in (5,6):pool.extend((blob[at:at+8],None));at+=8
        elif tag in (7,8,16,19,20):pool.append((tag,u2()))
        elif tag in (9,10,11,12,17,18):pool.append((tag,u2(),u2()))
        elif tag==15:pool.append((tag,blob[at],int.from_bytes(blob[at+1:at+3],'big')));at+=3
        else:raise ValueError(tag)
    def attr():
        nonlocal at
        name=pool[u2()];length=int.from_bytes(blob[at:at+4],'big');at+=4;value=blob[at:at+length];at+=length;return name,value
    def members():
        rows=[]
        for _ in range(u2()):
            access,name,desc=u2(),pool[u2()],pool[u2()];rows.append((access,name,desc,dict(attr() for _ in range(u2()))))
        return rows
    at+=6;interfaces=u2();at+=2*interfaces;members();methods=members()
    def reference(i):
        entry=pool[i]
        if isinstance(entry,tuple) and entry[0] in (9,10,11):
            owner=pool[pool[entry[1]][1]];member=pool[entry[2]];return owner+'.'+pool[member[1]]+pool[member[2]]
        if isinstance(entry,tuple) and entry[0]==7:return pool[entry[1]]
        return repr(entry)
    results=[]
    for access,name,desc,attrs in methods:
        if name not in ('getOpacity','initShapeCache','initCache'):continue
        raw=attrs['Code'];code=raw[8:8+int.from_bytes(raw[4:8],'big')];refs=[];i=0
        fixed={0x10:1,0x11:2,0x12:1,0x13:2,0x14:2,0x84:2,0xa9:1,0xbc:1,0xc5:3,0xc8:4,0xc9:4}
        for op in list(range(0x15,0x1a))+list(range(0x36,0x3b)):fixed[op]=1
        for op in range(0x99,0xa9):fixed[op]=2
        for op in range(0xb2,0xb9):fixed[op]=2
        for op in (0xb9,0xba):fixed[op]=4
        for op in (0xbb,0xbd,0xc0,0xc1,0xc6,0xc7):fixed[op]=2
        while i<len(code):
            start=i;op=code[i];i+=1
            if op in range(0xb2,0xba) or op in (0xbb,0xbd,0xc0,0xc1):refs.append({'offset':start,'opcode':hex(op),'reference':reference(int.from_bytes(code[i:i+2],'big'))})
            if op==0xaa:
                i+=(4-i%4)%4;low=int.from_bytes(code[i+4:i+8],'big',signed=True);high=int.from_bytes(code[i+8:i+12],'big',signed=True);i+=12+4*(high-low+1)
            elif op==0xab:
                i+=(4-i%4)%4;count=int.from_bytes(code[i+4:i+8],'big');i+=8+8*count
            elif op==0xc4:i+=5 if code[i]==0x84 else 3
            else:i+=fixed.get(op,0)
        assert i==len(code)
        results.append({'method':name,'descriptor':desc,'codeSha256':hashlib.sha256(code).hexdigest(),'references':refs})
    return results
def main():
    with zipfile.ZipFile(JAR) as z:blob=z.read('net/minecraft/block/AbstractBlock$AbstractBlockState.class')
    rows=read_class(blob);opacity=next(r for r in rows if r['method']=='getOpacity');init=next(r for r in rows if r['method'] in ('initShapeCache','initCache'))
    assert any('.shapeCache' in r['reference'] for r in opacity['references'])
    assert any('Block.getOpacity(' in r['reference'] for r in opacity['references'])
    assert any('.hasDynamicBounds()' in r['reference'] for r in init['references'])
    proof={'status':'PASS_PINNED_NATIVE_DYNAMIC_OPACITY_DISPATCH','jar':str(JAR),'classSha256':hashlib.sha256(blob).hexdigest(),'methods':rows,
        'meaning':'Native state reads ShapeCache.lightSubtracted when cached, otherwise delegates Block.getOpacity; native cache initializer checks block.hasDynamicBounds. NEW composite/compact Settings are dynamicBounds, so constructor guard can return old opacity until spec assignment without freezing that fallback into a static shape cache. No Java launched.'}
    write_json(ROOT/'docs/v12.7/conversion/native-opacity-cache-bytecode-proof.json',proof);print(json.dumps(proof))
if __name__=='__main__':main()
