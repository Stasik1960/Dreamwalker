"""Stage V12.6 source-derived moving door leaves; never mutate production.

Native-rescaled rendered quads are partitioned into axis-aligned rectangles
with the exact original UV mapping, texture and tint. Closed coverage is proved
independently; opening is a ninety-degree rotation about each leaf hinge.
"""
from __future__ import annotations
import argparse, collections, copy, hashlib, json, math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from v124_visual_audit import Visuals, rot, render
from v125_map_index import ROOT, dump, sha

DEST=ROOT/'build/v126-door-plan'
DOCS=ROOT/'docs/v12.6/doors'
RES=ROOT/'src/architecture/resources'
ASSEMBLY_RES=ROOT/'build/v126-assembly-plan/resources'
VANILLA=Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar')
IDS=['90031','93160','90266','93152','90458','93153','90923']
FACES={'north':lambda a,b:np.array([[b[0],b[1],a[2]],[b[0],a[1],a[2]],[a[0],a[1],a[2]],[a[0],b[1],a[2]]]),
       'south':lambda a,b:np.array([[a[0],b[1],b[2]],[a[0],a[1],b[2]],[b[0],a[1],b[2]],[b[0],b[1],b[2]]]),
       'west':lambda a,b:np.array([[a[0],b[1],a[2]],[a[0],a[1],a[2]],[a[0],a[1],b[2]],[a[0],b[1],b[2]]]),
       'east':lambda a,b:np.array([[b[0],b[1],b[2]],[b[0],a[1],b[2]],[b[0],a[1],a[2]],[b[0],b[1],a[2]]]),
       'up':lambda a,b:np.array([[a[0],b[1],a[2]],[a[0],b[1],b[2]],[b[0],b[1],b[2]],[b[0],b[1],a[2]]]),
       'down':lambda a,b:np.array([[a[0],a[1],b[2]],[a[0],a[1],a[2]],[b[0],a[1],a[2]],[b[0],a[1],b[2]]])}
SIDE_BY_NORMAL={(0,-1):'west',(0,1):'east',(1,-1):'down',(1,1):'up',(2,-1):'north',(2,1):'south'}


def load_sources():
    cat=json.loads((RES/'bloodborne_dw/debug_catalogue.json').read_text(encoding='utf-8-sig'))
    regs={r['temporaryId']:r['registryId'] for r in cat['entries']};sources={}
    for number in IDS:
        reg=regs[number];relative=Path('bloodborne_dw/composite')/(reg.split(':',1)[1]+'.json')
        file=ASSEMBLY_RES/relative if number in {'90031','90923'} else RES/relative
        spec=json.loads(file.read_text(encoding='utf-8-sig'))
        vi=1 if number in {'90031','90923'} else 0
        sources[number]={'mainId':number,'registryId':reg,'sourceDescriptorFile':str(file),
            'sourceDescriptorSha256':sha(file),'sourceVariant':vi,'spec':spec,
            'parts':spec['variants'][vi]['poses']['closed']['parts']}
    return sources


def inspect(sources,visuals):
    result=[]
    for number,source in sources.items():
        quads=[];parts=[]
        for part in source['parts']:
            q=visuals.quads(part);quads.extend(q)
            points=np.asarray([a[0] for a in q]).reshape(-1,3)
            parts.append({'part':part,'boundsUnits':np.r_[points.min(0),points.max(0)].tolist(),
                'quadCount':len(q),'textures':sorted({a[2] for a in q}),
                'elements':visuals.elements(part['model'])})
        points=np.asarray([q[0] for q in quads]).reshape(-1,3)
        row={k:v for k,v in source.items() if k not in {'spec','parts'}}
        row.update({'boundsUnits':np.r_[points.min(0),points.max(0)].tolist(),'parts':parts,'quadCount':len(quads)})
        result.append(row)
        canvas=Image.new('RGBA',(1200,900),'#dbe8ec');draw=ImageDraw.Draw(canvas)
        for i,angle in enumerate([0,180]):
            canvas.alpha_composite(Image.fromarray(render(visuals,quads,angle,570)),(i*600+15,50))
        font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',18)
        draw.text((20,20),f'{number} {source["registryId"]} · closed original native-rescaled geometry',font=font,fill='#111111')
        draw.multiline_text((20,640),json.dumps({'bounds':row['boundsUnits'],'parts':[p['part'] for p in parts]},ensure_ascii=False,indent=2)[:2000],font=font,fill='#111111')
        file=DOCS/'source-inspection'/f'{number}-source-closed.png';file.parent.mkdir(parents=True,exist_ok=True);canvas.convert('RGB').save(file)
    dump(DOCS/'source-inspection'/'source-door-geometry.json',{'status':'READONLY_SOURCE_INSPECTION_NATIVE_NOT_RUN','doors':result})
    return result


def records(source,visuals):
    result=[]
    for pi,part in enumerate(source['parts']):
        values=iter(visuals.quads(part))
        for ei,e in enumerate(visuals.elements(part['model'])):
            for original_side in e.get('faces',{}):
                q=next(values);v=np.asarray(q[0]);spans=np.ptp(v,axis=0);normalaxis=int(np.argmin(spans))
                assert spans[normalaxis]<1e-7, ('Door quad not axis-aligned',source['mainId'],pi,ei,original_side)
                cross=np.cross(v[1]-v[0],v[2]-v[0]);sign=1 if cross[normalaxis]>0 else -1
                axes=[i for i in range(3) if i!=normalaxis]
                matrix=np.column_stack((v[:,axes],np.ones(4)));uvmatrix=np.linalg.lstsq(matrix,np.asarray(q[1]),rcond=None)[0]
                assert np.max(np.abs(matrix@uvmatrix-q[1]))<1e-7
                result.append({'quadId':len(result),'partIndex':pi,'elementIndex':ei,'sourceSide':original_side,
                    'side':SIDE_BY_NORMAL[(normalaxis,sign)],'normalAxis':normalaxis,'axes':axes,
                    'lo':v.min(0),'hi':v.max(0),'uvMatrix':uvmatrix,'q':q,'shade':e.get('shade',True)})
    return result


def uvs(record,points):
    return np.column_stack((np.asarray(points)[:,record['axes']],np.ones(len(points))))@record['uvMatrix']


def map_uv_to_axis(record,axis,uv_axis,value):
    column=record['axes'].index(axis);coefficient=record['uvMatrix'][column,uv_axis]
    other=record['axes'][1-column]
    if abs(coefficient)<1e-10:return None
    return float((value-record['uvMatrix'][1-column,uv_axis]*record['lo'][other]-record['uvMatrix'][2,uv_axis])/coefficient)


def semantic_configuration(sources,visuals):
    semantics=json.loads((DOCS/'semantics/source-geometry.json').read_text())
    woodrows={r['v']:r['woodUHalfOpen'] for r in semantics['roof_5']['woodRows']}
    gatefile=DOCS/'semantics/allium-moving-mask.json'
    assert gatefile.exists(), 'Awaiting separately reviewed allium arch semantic mask'
    gate=json.loads(gatefile.read_text())
    gate_rows={int(r['v']):float(r['movingUStart']) for r in gate['rows']}
    return woodrows,gate_rows,gate


def partition_quad(record,number,woodrows,gate_rows):
    """Return disjoint exact rectangles tagged static/left/right/single."""
    pi,ei=record['partIndex'],record['elementIndex'];lo,hi=record['lo'],record['hi']
    axes=record['axes'];cuts={axis:[float(lo[axis]),float(hi[axis])] for axis in axes}
    def add(axis,values):
        if axis in axes:cuts[axis].extend(float(v) for v in values if lo[axis]+1e-8<v<hi[axis]-1e-8)
    mode='static'
    if number=='93160':mode='single'
    elif number in {'90458','90923'} and pi==0 and ei==0:
        mode='archedsingle';add(0,range(0,17));add(1,range(0,44))
    elif number in {'90458','90923'} and pi==0 and ei==1:
        mode='apertureframe';add(0,range(-6,22));add(1,range(0,49))
    elif number=='90031':
        if pi==0 and ei==0:mode='woodpair';add(0,[8])
        elif pi==1 and ei==0 and record['normalAxis']==2:
            mode='woodcap';add(0,[8])
            for v,row in woodrows.items():
                for value in [v/8,(v+1)/8]:
                    axisvalue=map_uv_to_axis(record,1,1,value)
                    if axisvalue is not None:add(1,[axisvalue])
                if row:
                    for value in [row[0]/8,row[1]/8]:
                        axisvalue=map_uv_to_axis(record,0,0,value)
                        if axisvalue is not None:add(0,[axisvalue])
        elif pi==1 and ei==0 and record['side']=='down':
            # This is the underside where the upper wood cap meets the lower
            # wood leaves, not a fixed lintel stretched through the doorway.
            mode='woodpair';add(0,[8])
    elif number=='93152' and pi==0 and ei==0:
        mode='boundedpair';add(0,[-9,8,25])
    elif number=='93153' and pi==0:
        mode='boundedpair';add(0,[-8,8,24])
    elif number=='90266' and ei in {0,1,4,5}:
        mode='gate';add(0,[8.04])
        # Original source front/back UVs encode the entire lower or middle
        # panel including side railings. Other faces use the same world cuts.
        front=record['frontRecord']
        if record['normalAxis']==2:
            for uv in [20/8,55/8]+[start/8 for start in gate_rows.values()]:
                value=map_uv_to_axis(record,0,0,uv)
                if value is not None:add(0,[value])
            if ei in {0,1}:
                for uv in [v/8 for v in range(26,75)]:
                    value=map_uv_to_axis(record,1,1,uv)
                    if value is not None:add(1,[value])
        else:
            # Evaluate side/top strips through the exact corresponding front
            # affine UV map; independent rescaled source origins differ a few
            # hundredths of a model unit and must never be approximated.
            for uv in [20/8,55/8]+[start/8 for start in gate_rows.values()]:
                value=map_uv_to_axis(front,0,0,uv)
                if value is not None:add(0,[value])
            if ei in {0,1}:
                for uv in [v/8 for v in range(26,75)]:
                    value=map_uv_to_axis(front,1,1,uv)
                    if value is not None:add(1,[value])
    def group(point):
        x,y=point[0],point[1]
        if mode=='static':return 'static'
        if mode=='single':return 'single'
        if mode=='archedsingle':
            frame=record['apertureRecord'];image=record['apertureImage']
            point=point.copy();point[0]=min(16-1e-7,max(1e-7,point[0]));point[1]=min(43-1e-7,max(1e-7,point[1]))
            u,v=uvs(frame,[point])[0];h,w=image.shape[:2]
            alpha=int(image[int(math.floor(v*h/16))%h,int(math.floor(u*w/16))%w,3])
            return 'single' if alpha==0 else 'closed_only_behind_frame'
        if mode=='apertureframe':
            frame=record['apertureRecord'];image=record['apertureImage'];point=point.copy()
            for axis in [0,1]:point[axis]=min(frame['hi'][axis]-1e-7,max(frame['lo'][axis]+1e-7,point[axis]))
            u,v=uvs(frame,[point])[0];h,w=image.shape[:2]
            alpha=int(image[int(math.floor(v*h/16))%h,int(math.floor(u*w/16))%w,3])
            # The author used a texture alpha arch on a complete backing box.
            # Its differently UV-mapped side/back surfaces span the opening
            # but were hidden by the closed leaf. Retain them in CLOSED only.
            return 'static' if alpha else 'closed_only_behind_frame'
        if mode=='woodpair':return 'left' if x<8 else 'right'
        if mode=='woodcap':
            u,v=uvs(record,[point])[0]*8;row=woodrows.get(int(math.floor(v)))
            return ('left' if x<8 else 'right') if row and row[0]<=u<row[1] else 'static'
        if mode=='boundedpair':
            left,right=(-9,25) if number=='93152' else (-8,24)
            return ('left' if x<8 else 'right') if left-1e-7<=x<=right+1e-7 else 'static'
        if mode=='gate':
            if record['normalAxis']==2:
                u,v=uvs(record,[point])[0]*8
                start=20 if ei in {4,5} else gate_rows.get(int(math.floor(v)),55)
                moving=start<55 and start<=u<=55+1e-7
            else:
                # Evaluate the corresponding original front-face UVs rather
                # than unrelated narrow side-face atlas strips.
                u,v=uvs(record['frontRecord'],[point])[0]*8
                if ei in {4,5}:start=20
                else:start=gate_rows.get(int(math.floor(v)),55)
                moving=start<55 and start<=u<=55+1e-7
            return ('left' if ei in {1,4} else 'right') if moving else 'static'
        raise AssertionError(mode)
    for axis in cuts:cuts[axis]=sorted(set(round(v,12) for v in cuts[axis]))
    pieces=[];active={}
    a,b=axes
    for b0,b1 in zip(cuts[b],cuts[b][1:]):
        runs=[]
        for a0,a1 in zip(cuts[a],cuts[a][1:]):
            low=lo.copy();high=hi.copy();low[a]=a0;high[a]=a1;low[b]=b0;high[b]=b1
            label=group((low+high)/2)
            if runs and runs[-1][0]==label:runs[-1][2]=a1
            else:runs.append([label,a0,a1])
        continuing={}
        for label,a0,a1 in runs:
            key=(label,a0,a1)
            if key in active:
                piece=active[key];piece['hi'][b]=b1
            else:
                low=lo.copy();high=hi.copy();low[a]=a0;high[a]=a1;low[b]=b0;high[b]=b1
                piece={'lo':low,'hi':high,'group':label,'record':record}
            continuing[key]=piece
        pieces.extend(piece for key,piece in active.items() if key not in continuing)
        active=continuing
    pieces.extend(active.values())
    return pieces


def element_for(piece,texturekey):
    record=piece['record'];lo,hi=piece['lo'],piece['hi'];side=record['side']
    vertices=FACES[side](lo,hi);desired=uvs(record,vertices);found=None
    for r in range(4):
        values=np.roll(desired,r,axis=0)
        if max(abs(values[0,0]-values[1,0]),abs(values[1,1]-values[2,1]),abs(values[2,0]-values[3,0]),abs(values[3,1]-values[0,1]))<1e-7:
            found=(r,values);break
    assert found is not None, ('Generated UV is not representable as vanilla rect',record['quadId'])
    r,values=found;face={'texture':'#'+texturekey,'uv':[round(float(x),10) for x in [values[0,0],values[0,1],values[2,0],values[2,1]]]}
    if r:face['rotation']=r*90
    if record['q'][3]>=0:face['tintindex']=record['q'][3]
    result={'from':[round(float(x),10) for x in lo],'to':[round(float(x),10) for x in hi],'faces':{side:face}}
    if not record['shade']:result['shade']=False
    return result


def bounds_of(pieces):
    return np.r_[np.asarray([p['lo'] for p in pieces]).min(0),np.asarray([p['hi'] for p in pieces]).max(0)].tolist()


def legal_model_tiles(pieces):
    """Keep JSON element coordinates in vanilla's [-16,32] source range."""
    result=[]
    for piece in pieces:
        axes=piece['record']['axes'];cuts={axis:[float(piece['lo'][axis]),float(piece['hi'][axis])] for axis in axes}
        for axis in axes:
            for n in range(math.floor((piece['lo'][axis]+16)/48),math.ceil((piece['hi'][axis]+16)/48)+1):
                boundary=n*48-16
                if piece['lo'][axis]+1e-8<boundary<piece['hi'][axis]-1e-8:cuts[axis].append(float(boundary))
            cuts[axis]=sorted(set(cuts[axis]))
        a,b=axes
        for a0,a1 in zip(cuts[a],cuts[a][1:]):
            for b0,b1 in zip(cuts[b],cuts[b][1:]):
                low=piece['lo'].copy();high=piece['hi'].copy();low[a]=a0;high[a]=a1;low[b]=b0;high[b]=b1
                offset=np.floor(((low+high)/2+16)/48)*48
                result.append({**piece,'lo':low,'hi':high,'modelOffset':offset})
    return result


def open_configuration(number,bounds):
    if number=='93160':return {'single':{'pivot':[8,0,0],'yaw':90,'hingeAxis':'Y at the upper/main leaf north edge; complete source stepped-depth lower and upper panels rotate together and clear the original passage'}}
    if number in {'90458','90923'}:return {'single':{'pivot':[0,0,0],'yaw':90,'hingeAxis':'Y at left wood leaf edge'}}
    if number=='90031':left,right,z=-9,25,8
    elif number=='93152':left,right,z=-9,25,6
    elif number=='93153':left,right,z=-8,24,7
    else:
        left=bounds['left'][0];right=bounds['right'][3];z=8
    return {'left':{'pivot':[left,0,z],'yaw':90,'hingeAxis':'Y at outer left central-leaf edge'},
            'right':{'pivot':[right,0,z],'yaw':-90,'hingeAxis':'Y at outer right central-leaf edge'}}


def generate(source,visuals,woodrows,gate_rows):
    number=source['mainId'];rec=records(source,visuals)
    fronts={(r['partIndex'],r['elementIndex']):r for r in rec if r['side']=='north'}
    for record in rec:record['frontRecord']=fronts[(record['partIndex'],record['elementIndex'])]
    apertureproof=None
    if number in {'90458','90923'}:
        frame=fronts[(0,1)];image=visuals.image(frame['q'][2]);aperturerows=[]
        for y in range(43):
            xs=[]
            for x in range(16):
                u,v=uvs(frame,[[x+.5,y+.5,frame['lo'][2]]])[0];h,w=image.shape[:2]
                if image[int(math.floor(v*h/16))%h,int(math.floor(u*w/16))%w,3]==0:xs.append(x)
            aperturerows.append({'y':[y,y+1],'movingXHalfOpen':[min(xs),max(xs)+1] if xs else None})
        apertureproof={'sourceFrameTexture':frame['q'][2],
            'sourceFrameTextureRgbaSha256':hashlib.sha256(image.tobytes()).hexdigest(),
            'sourceFrameModel':source['parts'][0]['model'],'sourceFrameElement':1,
            'derivation':'Moving wood follows transparent source stone-frame aperture. Closed originals retained. Existing wood surfaces hidden behind the opaque frame corners are included only in closed coverage and excluded from OPEN.',
            'rows':aperturerows}
        for record in rec:record['apertureRecord']=frame;record['apertureImage']=image
    pieces=legal_model_tiles([p for r in rec for p in partition_quad(r,number,woodrows,gate_rows)])
    grouped=collections.defaultdict(list)
    for piece in pieces:grouped[piece['group']].append(piece)
    bounds={group:bounds_of(values) for group,values in grouped.items()};opening=open_configuration(number,bounds)
    textures=sorted({r['q'][2] for r in rec});keys={texture:f't{i}' for i,texture in enumerate(textures)}
    tiled=collections.defaultdict(list)
    for piece in pieces:tiled[(piece['group'],tuple(piece['modelOffset']))].append(piece)
    generated={};closedparts=[];openparts=[];modelproof=[];modelvalues=[]
    for (group,offset),values in sorted(tiled.items()):
        suffix='_'.join(('m'+str(-int(v//48))) if v<0 else str(int(v//48)) for v in offset)
        name=group+'_'+suffix;model='bloodborne_dw:v126/doors/'+number+'/'+name
        elements=[]
        for piece in values:
            element=element_for(piece,keys[piece['record']['q'][2]])
            element['from']=[round(v-o,10) for v,o in zip(element['from'],offset)]
            element['to']=[round(v-o,10) for v,o in zip(element['to'],offset)]
            assert all(-16.000000001<=v<=32.000000001 for v in element['from']+element['to'])
            elements.append(element)
        doc={'textures':{keys[t]:t for t in textures},'elements':elements}
        path=DEST/'resources/assets/bloodborne_dw/models/v126/doors'/number/(name+'.json')
        dump(path,doc);generated[model]=doc;part={'model':model}
        if any(offset):part['offset']=list(offset)
        closedparts.append(part);modelvalues.append((part,values))
        if group=='static':openparts.append(copy.deepcopy(part))
        elif group in opening:openparts.append({**part,'pivot':[h-o for h,o in zip(opening[group]['pivot'],offset)],'yaw':opening[group]['yaw']})
        modelproof.append({'role':group,'model':model,'file':str(path),'sha256':sha(path),
            'faceRectangles':len(values),'boundsUnits':bounds[group],
            'partOffset':list(offset),'sourceTextureDependencies':textures,'openTransform':opening.get(group),
            'includeInOpen':group=='static' or group in opening,
            'elementCoordinateRange':'PASS_ALL_FROM_TO_WITHIN_NEGATIVE16_TO_POSITIVE32'})
    # Independently parse emitted model JSON through the native-rescale Visuals
    # reader and compare each generated UV corner with its source affine map.
    verifier=Visuals(resource_roots=[DEST/'resources',RES],vanilla_jar=VANILLA)
    failures=[];maxuv=0.;maxvertex=0.;proofs=[]
    for part,values in modelvalues:
        group=values[0]['group'];quads=verifier.quads(part)
        assert len(quads)==len(values)
        for piece,q in zip(values,quads):
            desired=uvs(piece['record'],q[0]);uvdelta=float(np.max(np.abs(desired-q[1])));maxuv=max(maxuv,uvdelta)
            lo,hi=np.asarray(q[0]).min(0),np.asarray(q[0]).max(0)
            vertexdelta=max(float(np.max(abs(lo-piece['lo']))),float(np.max(abs(hi-piece['hi']))));maxvertex=max(maxvertex,vertexdelta)
            if uvdelta>1e-7 or vertexdelta>1e-7 or q[2:]!=piece['record']['q'][2:]:failures.append({'quadId':piece['record']['quadId'],'role':group,'uvError':uvdelta,'vertexError':vertexdelta})
    for r in rec:
        subset=[p for p in pieces if p['record']['quadId']==r['quadId']];a,b=r['axes']
        originalarea=(r['hi'][a]-r['lo'][a])*(r['hi'][b]-r['lo'][b])
        generatedarea=sum((p['hi'][a]-p['lo'][a])*(p['hi'][b]-p['lo'][b]) for p in subset)
        assert abs(originalarea-generatedarea)<1e-6, ('Face coverage gap',number,r['quadId'])
        # Rectangles derive from a complete Cartesian partition of one source
        # face. Areas equal and interiors are disjoint, so every source pixel
        # and surface position is preserved exactly without added inner faces.
        proofs.append({'sourceQuadId':r['quadId'],'sourcePart':r['partIndex'],'sourceElement':r['elementIndex'],
            'sourceSide':r['sourceSide'],'sourceArea':float(originalarea),'partitionArea':float(generatedarea),
            'rectangles':len(subset),'roles':dict(collections.Counter(p['group'] for p in subset)),
            'status':'PASS_COMPLETE_DISJOINT_UV_AFFINE_COVERAGE'})
    assert not failures, ('Door closed partition UV mismatch',number,failures[:5])
    # Diagnostic projections: these are source/pixel-derived planning renders,
    # explicitly separate from root's actual native client captures.
    sourceq=[q for part in source['parts'] for q in visuals.quads(part)]
    closedq=[q for part in closedparts for q in verifier.quads(part)]
    openq=[q for part in openparts for q in verifier.quads(part)]
    open_by_model={p['model']:p for p in openparts};rigidchecks=[];rolebounds={}
    for group in grouped:
        closedrole=[];openedrole=[]
        for model in modelproof:
            if model['role']!=group:continue
            closedpart=next(p for p in closedparts if p['model']==model['model']);closedvalues=verifier.quads(closedpart)
            closedrole.extend(closedvalues)
            if not model['includeInOpen']:continue
            openpart=open_by_model[model['model']];openvalues=verifier.quads(openpart);openedrole.extend(openvalues)
            if group=='static':assert openpart==closedpart
            else:
                hinge=opening[group]['pivot'];yaw=opening[group]['yaw']
                maximum=max(float(np.max(np.abs(rot(c[0],1,-yaw,hinge)-o[0]))) for c,o in zip(closedvalues,openvalues))
                assert maximum<1e-7,(number,group,'non-rigid open transform',maximum)
                rigidchecks.append({'model':model['model'],'role':group,'globalHinge':hinge,'rotationDegrees':-yaw,
                    'partOffset':model['partOffset'],'partLocalPivot':openpart['pivot'],
                    'maxRigidTransformError':maximum,'status':'PASS_ONE_RIGID_NINETY_DEGREE_LEAF_TRANSFORM'})
        cp=np.asarray([q[0] for q in closedrole]).reshape(-1,3)
        rolebounds[group]={'closedBoundsUnits':np.r_[cp.min(0),cp.max(0)].tolist(),'includeInOpen':group=='static' or group in opening}
        if openedrole:
            op=np.asarray([q[0] for q in openedrole]).reshape(-1,3)
            rolebounds[group]['openBoundsUnits']=np.r_[op.min(0),op.max(0)].tolist()
    canvas=Image.new('RGBA',(1800,750),'#dbe8ec');draw=ImageDraw.Draw(canvas);font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',20)
    for i,(title,quads) in enumerate([('Original CLOSED',sourceq),('Partitioned CLOSED',closedq),('OPEN ninety degrees',openq)]):
        canvas.alpha_composite(Image.fromarray(render(verifier,quads,0,580)),(i*600+10,70));draw.text((i*600+20,20),number+' '+title,font=font,fill='#111111')
    preview=DOCS/'generated-previews'/f'{number}-closed-open.png';preview.parent.mkdir(parents=True,exist_ok=True);canvas.convert('RGB').save(preview)
    sourceimage=render(visuals,sourceq,0,512);closedimage=render(verifier,closedq,0,512)
    pixelmismatch=int(np.count_nonzero(np.any(sourceimage!=closedimage,axis=2)))
    return {'mainId':number,'registryId':source['registryId'],'sourceVariant':source['sourceVariant'],
        'variant':source['sourceVariant'],'variants':[source['sourceVariant']],
        'sourceDescriptorFile':source['sourceDescriptorFile'],'sourceDescriptorSha256':source['sourceDescriptorSha256'],
        'leafCount':len(opening),'sourceClosedParts':source['parts'],'partitionedClosedParts':closedparts,'openParts':openparts,
        'models':modelproof,'leafHinges':opening,'closedVisualBoundsUnits':bounds_of(pieces),
        'roleBounds':rolebounds,'leafRigidTransformProof':rigidchecks,
        'closedSourceQuads':len(rec),'partitionedClosedQuads':len(pieces),'closedCoverageProof':proofs,
        'maxVertexRoundingError':maxvertex,'maxUvRoundingError':maxuv,'closedProofFailures':failures,
        'closedDiagnosticPixelMismatchCount':pixelmismatch,
        'allProfiles':['base','alt'],'alternateModels':'NONE_IN_SOURCE_PARTS_SAME_GEOMETRY_AND_TEXTURE_IN_BOTH_PROFILES',
        'sourceAlphaApertureProof':apertureproof,
        'closedOnlyBehindFramePolicy':'Existing wood behind opaque stone-frame corners is present in original CLOSED parts and in the closed coverage proof, excluded from OPEN so the visible leaf retains the authored arched aperture. No new painted content.',
        'closedPreservationPolicy':'Keep original CLOSED parts byte-semantic unchanged; generated static/leaf partitions are used for OPEN. Their union covers every original surface with the identical UV/texture/tint map. No source texture/model mutation.',
        'nativeValidation':'NOT_RUN','authorCollisionExport':'NOT_PROVIDED_NOT_APPLIED',
        'collisionSuggestions':'Root builds simple physical cuboids separately; leaf moving-model bounds and precise hinge transforms are provided here.'}


def main():
    p=argparse.ArgumentParser();p.add_argument('--inspect-only',action='store_true');args=p.parse_args()
    DOCS.mkdir(parents=True,exist_ok=True);DEST.mkdir(parents=True,exist_ok=True)
    sources=load_sources();visuals=Visuals(vanilla_jar=VANILLA)
    assert all(not p.get('altModel') or p['altModel']==p['model'] for source in sources.values() for p in source['parts']), 'Distinct alternate source models require separate partition proof'
    inspect(sources,visuals)
    if args.inspect_only:return
    woodrows,gate_rows,gate=semantic_configuration(sources,visuals)
    doors=[generate(source,visuals,woodrows,gate_rows) for source in sources.values()]
    manifest={'schemaVersion':1,'status':'PASS_STAGED_CLOSED_SURFACE_UV_EQUIVALENCE_NATIVE_NOT_RUN','scope':'NEW_POST_ORIGINAL_V12_ONLY',
        'original90001Modified':False,'doors':doors,'sourceSemantics':gate,
        'modelResources':sum(len(d['models']) for d in doors),'leafCount':sum(d['leafCount'] for d in doors),
        'closedSourceQuads':sum(d['closedSourceQuads'] for d in doors),'partitionedClosedQuads':sum(d['partitionedClosedQuads'] for d in doors),
        'productionMutation':False,'nativeValidation':'NOT_RUN','authorCollisionExport':'NOT_PROVIDED_NOT_APPLIED'}
    dump(DEST/'door-model-manifest.json',manifest);dump(DEST/'door-manifest.json',manifest);dump(DOCS/'door-model-staging-proof.json',manifest)
    print(json.dumps({k:manifest[k] for k in ['status','modelResources','leafCount','closedSourceQuads','partitionedClosedQuads']}))

if __name__=='__main__':main()
