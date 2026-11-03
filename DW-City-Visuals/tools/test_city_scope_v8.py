#!/usr/bin/env python3
"""Independent regressions for the v8 city/pavement scope repair.

Golden v6 global textures, the eleven pre-existing accent regions, reviewed
house bounds and original half-arch faces are independent of the v8 generator.
Compiled bytecode must prove that neighbour content cannot select red masonry.
This test never builds or launches Minecraft, and does not certify frame timing
or a completed in-game visual pass.
"""
from __future__ import annotations
import argparse
import copy
import hashlib
from io import BytesIO
import json
from pathlib import Path
import re
import struct
import sys
import traceback
import zipfile
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
JAVA=ROOT/'src/client/java/dev/dreamwalker/bloodbornedw/visual'
GLOBAL='assets/bloodborne_dw/textures/block/remaining/source/minecraft/block/'
ACCENT='assets/bloodborne_dw/textures/block/autumn_c3/accent/'
CONFIG='assets/bloodborne_dw/visuals/facades.json'
ARCH='assets/bloodborne_dw/models/remaining/source/minecraft/block/addon/gray_glazed_terracotta_0.json'
GOLDEN_GLOBAL={
    'bricks':'93404c16d46e1e98585eedf9dfdbb9fbb2fc686bc6fdb99a20ca0dd0550f906e',
    'bricks_0':'45d5d62c273b81c40abd7b30a861d8c2c433e99153784f2d22ea117d7448d4ef',
    'bricks_1':'55bd7eb1b7939d382447be6f4d9317ed735b41b2f55887d73838d80ac72f223b',
    'bricks_2':'0077cab116634d0a663044adfbe7b36a003dcbcad37d3943b0de73fd58f20e91',
}
GOLDEN_ROSE={
    'bricks':'ae7e27bb0f7ae30f1224d74317464667999abcf6c4c2edbb016b9b8e7b5925a3',
    'bricks_0':'2dbb4771913cfb704bf136d10ce074770bcf0db77c1ed06e8a00889f1f0a6ec4',
    'bricks_1':'0caf38013622afd0c85d447b404f2421c659aabc29fb0072b115130fa8588a48',
    'bricks_2':'82e592bcebf1dabb3b36ed3306626b2df369bb2f3306a5bdb683a3f009009ead',
}
OLD_REGIONS=[
    ((166,88,-816),(184,100,-795),'rose'),
    ((112,58,-1104),(124,70,-1088),'rose'),
    ((15,127,-725),(27,139,-709),'rose'),
    ((-3,43,-977),(10,55,-965),'rose'),
    ((347,70,-710),(359,82,-694),'rose'),
    ((73,90,-791),(85,102,-777),'rose'),
    ((193,75,-775),(203,90,-767),'sage'),
    ((193,94,-776),(203,103,-766),'sage'),
    ((193,103,-759),(209,115,-747),'sand'),
    ((327,70,-682),(351,85,-664),'sage'),
    ((160,66,-1125),(187,76,-1110),'sand'),
]
HOUSES=[((183,58,-953),(193,70,-941),'red_house'),
        ((194,58,-952),(199,70,-944),'red_house')]
NEGATIVES=[(161,88,-1022),(161,57,-1022),(161,56,-1022),
           (184,57,-943),(188,56,-948),(195,57,-948),
           (272,50,-917),(215,41,-914),(129,62,-860),
           (250,46,-836),(282,46,-847),(294,62,-983),(0,64,0)]


def class_methods(data):
    """Read method bytecode without loading game classes or executing a JVM."""
    if data[:4]!=b'\xca\xfe\xba\xbe':raise ValueError('Invalid class')
    offset=8
    def take(n):
        nonlocal offset
        value=data[offset:offset+n];offset+=n
        if len(value)!=n:raise ValueError('Truncated class')
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
            name=pool[u2()][1];result[name]=take(u4())
        return result
    take(6);take(2*u2())
    for _ in range(u2()):take(6);attributes()
    methods={}
    for _ in range(u2()):
        take(2);name=pool[u2()][1];descriptor=pool[u2()][1];attrs=attributes()
        if 'Code' in attrs:
            value=attrs['Code'];size=struct.unpack_from('>I',value,4)[0]
            methods[(name,descriptor)]=value[8:8+size]
    def method_reference(index):
        row=pool[index]
        if not row or row[0] not in (9,10,11):raise ValueError('Expected field or method reference')
        owner=pool[pool[row[1]][1]][1];names=pool[row[2]]
        return owner,pool[names[1]][1],pool[names[2]][1]
    return methods,method_reference


def compiled_region_predicate(data):
    """Execute only the tiny, pure integer Region.contains bytecode.

    This is deliberately not a JVM or a game launch: unsupported instructions
    fail closed. It checks the actual shipped inclusive bounds implementation,
    rather than assuming the Python fixture and Java implementation agree.
    """
    methods,reference=class_methods(data)
    candidates=[code for (name,_),code in methods.items() if name=='contains']
    if len(candidates)!=1:raise ValueError('Expected one compiled region predicate')
    code=candidates[0]
    getters={'getX':0,'getY':1,'getZ':2,'method_10263':0,'method_10264':1,'method_10260':2}
    def predicate(region,pos):
        low,high,_=region
        fields=dict(zip(('minX','minY','minZ','maxX','maxY','maxZ'),(*low,*high)))
        locals_=[fields,pos];stack=[];pc=0;steps=0
        while steps<256:
            steps+=1;op=code[pc]
            if op in (42,43):stack.append(locals_[op-42]);pc+=1
            elif op in (3,4):stack.append(op-3);pc+=1
            elif op==180:
                owner,name,descriptor=reference(struct.unpack_from('>H',code,pc+1)[0])
                if not owner.endswith('/FacadeConfig$Region') or descriptor!='I':raise ValueError('Unexpected predicate field')
                stack.append(stack.pop()[name]);pc+=3
            elif op==182:
                owner,name,descriptor=reference(struct.unpack_from('>H',code,pc+1)[0])
                if name not in getters or descriptor!='()I':raise ValueError('Unexpected predicate invocation '+str((owner,name,descriptor)))
                stack.append(stack.pop()[getters[name]]);pc+=3
            elif 159<=op<=164:
                right,left=stack.pop(),stack.pop()
                branch={159:left==right,160:left!=right,161:left<right,162:left>=right,163:left>right,164:left<=right}[op]
                pc+=struct.unpack_from('>h',code,pc+1)[0] if branch else 3
            elif op==167:pc+=struct.unpack_from('>h',code,pc+1)[0]
            elif op==172:return bool(stack.pop())
            else:raise ValueError('Unsupported pure predicate opcode '+str(op))
        raise ValueError('Predicate exceeds finite instruction budget')
    return predicate


def method_body(text,name,parameters=None):
    matches=re.finditer(r'\b'+re.escape(name)+r'\s*\(([^)]*)\)\s*\{',text)
    match=next((m for m in matches if parameters is None or parameters in m.group(1)),None)
    if not match:raise ValueError('Missing method '+name)
    start=match.end();depth=1;index=start
    while depth and index<len(text):
        depth+=(text[index]=='{')-(text[index]=='}');index+=1
    return text[start:index-1]


def strip_comments(text):
    return re.sub(r'/\*.*?\*/|//[^\n]*','',text,flags=re.S)


def region_tuple(row):return tuple(row['min']),tuple(row['max']),row['role']
def contains(region,pos):return all(region[0][i]<=pos[i]<=region[1][i] for i in range(3))
def sha(data):return hashlib.sha256(data).hexdigest()
def pixels(data):return Image.open(BytesIO(data)).convert('RGBA')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',type=Path,help='Combined delivered mod, not the intermediate overlay')
    parser.add_argument('--report',type=Path,default=ROOT/'validation/city-scope-v8.json')
    parser.add_argument('--source-only',action='store_true',help='Partial pre-build check; never reports full PASS')
    args=parser.parse_args()
    checks=[]
    def require(name,condition,detail):
        checks.append({'name':name,'status':'PASS' if condition else 'FAIL','detail':detail})
        if not condition:raise AssertionError(name+': '+str(detail))
    production=zipfile.ZipFile(args.jar) if args.jar else None
    def resource(path):return (RES/path).read_bytes()
    def actual(path):return production.read(path) if production else resource(path)
    config=json.loads(resource(CONFIG));regions=[region_tuple(r) for r in config['regions']]
    require('old-eleven-accent-regions-preserved',[r for r in regions if r[2]!='red_house']==OLD_REGIONS,'All v6 rose/sage/sand bounds and priorities remain intact')
    require('only-two-reviewed-house-volumes',[r for r in regions if r[2]=='red_house']==HOUSES and len(regions)==13,'Red cannot expand to the city, street level or a nearest-material neighbourhood')
    require('old-accent-multipliers-preserved',config['roleMultipliers']=={'sage':[.842,.97,1.0],'sand':[1.0,.98,.92]},'The original sage/sand tone transforms are unchanged')
    if production:require('production-region-table-exact',production.read(CONFIG)==resource(CONFIG),'Combined JAR uses the reviewed bounded configuration')
    with zipfile.ZipFile(ROOT/'tools/facade-inputs.zip') as frozen:
        for name,expected_sha in GOLDEN_GLOBAL.items():
            path=GLOBAL+name+'.png';prior=frozen.read('alt/'+path)
            require('trusted-v6-input-'+name,sha(prior)==expected_sha,'Frozen input matches the independently pinned v6 hash')
            require('global-source-png-exact-v6-'+name,resource('dw_city_visuals/alt/'+path)==prior,'Every global pixel and PNG byte is restored, including pavements and unrelated buildings')
            if production:require('global-production-png-exact-v6-'+name,production.read('dw_city_visuals/alt/'+path)==prior,'Delivered global resource is byte-identical to v6')
            sprite='bloodborne_dw:block/remaining/source/minecraft/block/'+name
            alias='bloodborne_dw:block/autumn_c3/accent/red_'+name
            require('red-is-separate-alias-'+name,config['brickWindowSprites'].get(sprite)==alias,'Local facade mapping points to a separate resource')
            red=actual('dw_city_visuals/alt/'+ACCENT+'red_'+name+'.png')
            a,b=pixels(prior),pixels(red)
            require('local-red-alias-protects-silhouette-'+name,a.size==b.size and a.getchannel('A').tobytes()==b.getchannel('A').tobytes() and a.tobytes()!=b.tobytes(),'The house colour is distinct without replacing the global sprite or changing transparency')
    for name,expected_sha in GOLDEN_ROSE.items():
        require('old-rose-palette-exact-'+name,sha(actual('dw_city_visuals/alt/'+ACCENT+name+'.png'))==expected_sha,'Pre-existing v6 rose brick pixels and PNG bytes are unchanged')

    def fixture_role(pos,alt=True,dimension='minecraft:overworld'):
        if not alt or dimension!=config['dimension']:return 0
        matches=[r[2] for r in regions if contains(r,pos)]
        if 'rose' in matches:return 1
        for name in ('sage','sand','red_house'):
            if name in matches:return {'sage':2,'sand':3,'red_house':4}[name]
        return 0
    boundary_negatives=set(NEGATIVES)
    positive=[]
    for low,high,_ in HOUSES:
        positive.extend([low,high,tuple((a+b)//2 for a,b in zip(low,high))])
        # Exercise every surface of the expanded box, excluding points that
        # legitimately belong to the adjoining annex's independently reviewed box.
        for x in range(low[0]-1,high[0]+2):
            for y in range(low[1]-1,high[1]+2):
                for z in range(low[2]-1,high[2]+2):
                    pos=(x,y,z)
                    if not any(contains(r,pos) for r in HOUSES):boundary_negatives.add(pos)
        for x in range(low[0],high[0]+1):
            for z in range(low[2],high[2]+1):boundary_negatives.add((x,57,z));boundary_negatives.add((x,56,z))
    require('curated-house-wall-points-red',all(fixture_role(p)==4 for p in positive),positive)
    failures=[p for p in sorted(boundary_negatives) if fixture_role(p)==4]
    require('all-house-boundaries-foundations-pavements-nonred',not failures,{'negativePositions':len(boundary_negatives),'reportedPanorama':[161,88,-1022],'failures':failures[:8]})
    require('original-and-other-dimensions-nonred',all(fixture_role(p,False)==0 and fixture_role(p,True,'minecraft:the_nether')==0 for p in positive),'ALT and dimension gating remain necessary for any local facade role')
    for low,high,kind in OLD_REGIONS:
        centre=tuple((a+b)//2 for a,b in zip(low,high))
        require('legacy-accent-fixture-'+str(centre),fixture_role(centre)=={'rose':1,'sage':2,'sand':3}[kind],kind)

    substrate=strip_comments((JAVA/'ShutterSubstrate.java').read_text(encoding='utf-8'))
    require('source-neighbour-independence',re.sub(r'\s+','',method_body(substrate,'role'))=='returnFacadeMaterials.role(pos);','The world/neighbour argument is ignored; generic/native/bridge bricks cannot imply a red house')
    materials=strip_comments((JAVA/'FacadeMaterials.java').read_text(encoding='utf-8'))
    role_body=method_body(materials,'role')
    require('source-alt-dimension-bounded-lookup','!CityVisualClient.isAlt()' in role_body and 'c.role(pos)' in role_body and '.equals(c.dimension)' in role_body,'All material routes ultimately use the sparse inclusive region lookup')
    quad=method_body(materials,'quad','boolean manual');transform=method_body(materials,'transform','boolean manual')
    require('automatic-quad-default-keeps-pavement-guard','return quad(source,role,false);' in method_body(materials,'quad'),'Existing automatic callers select the protected path')
    require('automatic-fabric-default-keeps-pavement-guard','return transform(quad,role,false);' in method_body(materials,'transform'),'Existing automatic callers select the protected path')
    require('vanilla-pavement-guard-precedes-recolour','if(role==0)return source;' in quad and 'if(!manual&&role==4&&(source.getFace()==Direction.UP||source.getFace()==Direction.DOWN))return source;' in quad and quad.index('source.getFace()==Direction.UP')<quad.index('int[] data='),'Automatic horizontal house floors retain their resource; explicit brush colors may cover eligible masonry')
    require('fabric-pavement-guard-precedes-recolour','if(role==0)return true;' in transform and 'if(!manual&&role==4&&' in transform and 'horizontal(quad)' in transform and transform.index('horizontal(quad)')<transform.index('Identifier targetId='),'Automatic Fabric flat faces remain protected before substitution')
    client=strip_comments((JAVA/'CityFacadesClient.java').read_text(encoding='utf-8'))
    for method,position in [('staticModel','root'),('visibleModel','root'),('terrainModel','pos')]:
        body=method_body(client,method)
        require('bounded-render-route-'+method,f'ShutterSubstrate.role(world,{position})' in body and f'FacadeMaterials.role({position})' in body and 'role==0?model:' in body,'Source/static/native/bridge path returns the original model outside any explicit accent region')
    model=strip_comments((JAVA/'FacadeModel.java').read_text(encoding='utf-8'))
    require('direct-fabric-source-route-bounded','ShutterSubstrate.role(world,pos)' in method_body(model,'emitBlockQuads') and 'if(role==0)' in method_body(model,'emitBlockQuads'),'Direct source model emission also delegates unchanged outside reviewed regions')

    with zipfile.ZipFile(ROOT/'baseline/mod.jar') as base:
        original=json.loads(base.read(ARCH));expected=copy.deepcopy(original)
        for face in ('north','south','up'):del expected['elements'][0]['faces'][face]
        for mode in ('original','alt'):
            path='dw_city_visuals/'+mode+'/'+ARCH;current=json.loads(actual(path))
            require('arch-'+mode+'-only-three-obstructing-faces-removed',current==expected,'Only element0 north/south/up are absent; all element geometry, other faces and half-arch UVs equal the original model')
            require('arch-'+mode+'-half-end-uv-exact',all(current['elements'][0]['faces'][side]==original['elements'][0]['faces'][side] for side in ('east','west','down')),'v7 full-arch UV replacement is reverted exactly, without changing the underside')
        if production:
            for path in ('assets/bloodborne_dw/native_grid/catalog.json','assets/bloodborne_dw/native_grid/meshes.bin','assets/bloodborne_dw/native_grid/visual_physics.json'):
                def digest(archive):
                    h=hashlib.sha256()
                    with archive.open(path) as stream:
                        for chunk in iter(lambda:stream.read(1<<20),b''):h.update(chunk)
                    return h.digest()
                require('native-city-content-preserved-'+Path(path).name,digest(base)==digest(production),'The material-scope repair does not modify world cell identities, geometry or physics mapping')

    if not args.source_only:
        target='dev/dreamwalker/bloodbornedw/visual/ShutterSubstrate.class'
        data=production.read(target) if production else (ROOT/'build/classes/java/client'/target).read_bytes()
        methods,reference=class_methods(data)
        codes=[code for (name,_),code in methods.items() if name=='role']
        code=codes[0] if len(codes)==1 else b''
        projection=len(code)==5 and code[0]==0x2b and code[1]==0xb8 and code[4]==0xac
        called=reference(struct.unpack('>H',code[2:4])[0]) if projection else None
        require('compiled-role-is-only-position-projection',projection and called[:2]==('dev/dreamwalker/bloodbornedw/visual/FacadeMaterials','role'),{'compiledBytes':list(code),'calledMethod':called,'neighbourTypesCovered':['source_bricks','static_architecture','native_grid','native_physics','NativeGridBridge']})
        target='dev/dreamwalker/bloodbornedw/visual/FacadeConfig$Region.class'
        data=production.read(target) if production else (ROOT/'build/classes/java/client'/target).read_bytes()
        predicate=compiled_region_predicate(data)
        positions=sorted(boundary_negatives|set(positive))
        mismatch=[(region,pos) for region in regions for pos in positions if predicate(region,pos)!=contains(region,pos)]
        require('compiled-region-boundaries-match-independent-fixtures',not mismatch,{'regions':len(regions),'positionsPerRegion':len(positions),'evaluations':len(regions)*len(positions),'mismatches':mismatch[:4]})
    result={'status':'PARTIAL' if args.source_only else 'PASS','jar':str(args.jar) if args.jar else None,'checks':checks,
            'limitations':['Read-only static resources, region fixtures and compiled scope contract; no game was launched and no timing or screenshot correctness is implied.']}
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    if production:production.close()
    print(json.dumps({'status':result['status'],'checks':len(checks),'report':str(args.report)}))


if __name__=='__main__':
    try:main()
    except Exception as error:
        path=Path(sys.argv[sys.argv.index('--report')+1]) if '--report' in sys.argv else ROOT/'validation/city-scope-v8.json'
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps({'status':'FAIL','error':str(error),'traceback':traceback.format_exc()},indent=2)+'\n',encoding='utf-8')
        raise
