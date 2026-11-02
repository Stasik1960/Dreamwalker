#!/usr/bin/env python3
"""Regress native visual/physics separation using real cells and compiled calls.

No Minecraft process is started. Source fixtures come from the pinned native
catalog and visual_physics mapping; bytecode confirms the material path reads
the actual chunk visual before the generic shared-physics cell interface.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import struct
import zipfile

ROOT=Path(__file__).resolve().parents[1]
OWNER='dev/dreamwalker/bloodbornedw/composite/'
TARGET='dev/dreamwalker/bloodbornedw/visual/ShutterSubstrate.class'


def class_methods(data):
    if data[:4]!=b'\xca\xfe\xba\xbe':
        raise ValueError('Invalid Java class')
    position=8
    def take(n):
        nonlocal position
        value=data[position:position+n];position+=n
        if len(value)!=n:raise ValueError('Truncated Java class')
        return value
    def u2():return struct.unpack('>H',take(2))[0]
    def u4():return struct.unpack('>I',take(4))[0]
    pool=[None]*u2();index=1
    while index<len(pool):
        tag=take(1)[0]
        if tag==1:pool[index]=(tag,take(u2()).decode('utf-8',errors='replace'))
        elif tag in (7,8,16,19,20):pool[index]=(tag,u2())
        elif tag in (9,10,11,12,17,18):pool[index]=(tag,u2(),u2())
        elif tag in (3,4):take(4)
        elif tag in (5,6):take(8);index+=1
        elif tag==15:take(3)
        else:raise ValueError('Unsupported constant-pool tag '+str(tag))
        index+=1
    def attributes():
        result={}
        for _ in range(u2()):
            name=pool[u2()][1]
            result[name]=take(u4())
        return result
    take(6)
    take(2*u2())
    for _ in range(u2()):take(6);attributes()
    methods={}
    for _ in range(u2()):
        take(2);name=pool[u2()][1];descriptor=pool[u2()][1];attrs=attributes()
        if 'Code' in attrs:
            raw=attrs['Code'];length=struct.unpack_from('>I',raw,4)[0]
            methods[(name,descriptor)]=raw[8:8+length]
    def reference(index):
        row=pool[index]
        if row is None or row[0] not in (10,11):raise ValueError('Expected method reference')
        klass=pool[pool[row[1]][1]][1]
        names=pool[row[2]]
        return klass,pool[names[1]][1],pool[names[2]][1]
    return methods,reference


def calls(code, reference):
    result=[];offset=0
    while offset<len(code):
        op=code[offset]
        if op in (182,183,184,185):
            result.append((offset,*reference(struct.unpack_from('>H',code,offset+1)[0])))
        if op in (16,18,21,22,23,24,25,54,55,56,57,58,169,188):size=2
        elif op in (17,19,20,132,*range(153,169),*range(178,185),187,189,192,193,198,199):size=3
        elif op==197:size=4
        elif op in (185,186,200,201):size=5
        elif op==196:size=6 if code[offset+1]==132 else 4
        elif op==170:
            start=(offset+4)&~3;low,high=struct.unpack_from('>ii',code,start+4)
            size=start+12+4*(high-low+1)-offset
        elif op==171:
            start=(offset+4)&~3;pairs=struct.unpack_from('>i',code,start+4)[0]
            size=start+8+8*pairs-offset
        else:size=1
        offset+=size
    if offset!=len(code):raise ValueError('Invalid bytecode instruction length')
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',type=Path)
    parser.add_argument('--report',type=Path,default=ROOT/'validation/shutter-native-v7.json')
    args=parser.parse_args()
    checks=[]
    def require(name,ok,detail):
        checks.append({'name':name,'status':'PASS' if ok else 'FAIL','detail':detail})
        if not ok:raise AssertionError(name+': '+str(detail))
    with zipfile.ZipFile(ROOT/'baseline/mod.jar') as base:
        catalog=json.loads(base.read('assets/bloodborne_dw/native_grid/catalog.json'))
        layout=json.loads(base.read('assets/bloodborne_dw/native_grid/visual_physics.json'))
        metadata=(ROOT/'src/main/resources/assets/bloodborne_dw/visuals/native-substrates.bin').read_bytes()
        require('mesh-table-native-index-domain',metadata[:4]==b'NSS1' and struct.unpack('>I',metadata[4:8])[0]==len(catalog['meshes'])==len(metadata)-8,'One indexed byte per actual native mesh')
        types=metadata[8:]
        groups={}
        for visual,physics in enumerate(layout['cellToPhysics']):
            if physics<0:continue
            kind=types[catalog['cells'][visual]['mesh']]
            if kind in (1,2):groups.setdefault(physics,{}).setdefault(kind,visual)
        pairs=[{'physics':physics,'stoneVisual':members[1],'brickVisual':members[2],
                'representative':layout['representatives'][physics],
                'representativeKind':types[catalog['cells'][layout['representatives'][physics]]['mesh']]}
               for physics,members in groups.items() if 1 in members and 2 in members]
        require('actual-visuals-sharing-one-physics-differ-in-wall-material',bool(pairs),{'sharedPhysicsGroups':len(pairs),'examples':pairs[:4]})
        require('physics-representative-cannot-classify-both-materials',all(p['stoneVisual']!=p['brickVisual'] and types[catalog['cells'][p['stoneVisual']]['mesh']]==1 and types[catalog['cells'][p['brickVisual']]['mesh']]==2 for p in pairs),'A shared state has both stone and brick visuals; using one representative necessarily misclassifies one')
        methods,reference=class_methods(base.read(OWNER+'NativePhysicsBlock.class'))
        method=next(code for (name,_),code in methods.items() if name=='cell')
        baseline_calls=calls(method,reference)
        require('baseline-cell-is-documented-physical-representative',any(owner==OWNER+'NativeVisualLayout' and name=='representative' for _,owner,name,_ in baseline_calls),'Pinned NativePhysicsBlock.cell bytecode calls NativeVisualLayout.representative')
    if args.jar:
        with zipfile.ZipFile(args.jar) as production:
            klass=production.read(TARGET)
            require('production-table-is-reviewed-table',production.read('assets/bloodborne_dw/visuals/native-substrates.bin')==metadata,'Combined mod contains the reviewed finite metadata bytes')
    else:
        klass=(ROOT/'build/classes/java/client'/TARGET).read_bytes()
    methods,reference=class_methods(klass)
    method=next(code for (name,_),code in methods.items() if name=='material')
    production_calls=calls(method,reference)
    visual_positions=[offset for offset,owner,name,_ in production_calls if owner==OWNER+'NativeVisuals' and name=='forView']
    physical_positions=[offset for offset,owner,name,_ in production_calls if owner==OWNER+'NativeCellBlock' and name=='cell']
    require('compiled-material-uses-actual-visual-before-cell-interface',len(visual_positions)==1 and len(physical_positions)==1 and visual_positions[0]<physical_positions[0],production_calls)
    require('compiled-bridge-retains-view-and-position-lookup',any(owner==OWNER+'NativeGridBridge' and name=='base' for _,owner,name,_ in production_calls),'Bridge background keeps the established coordinate-aware lookup')
    report={'status':'PASS','jar':str(args.jar) if args.jar else 'compiled-client-classes','checks':checks,
            'limitation':'Read-only native metadata and compiled callflow checks; no game or world is launched.'}
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'status':'PASS','checks':len(checks),'report':str(args.report)}))


if __name__=='__main__':main()
