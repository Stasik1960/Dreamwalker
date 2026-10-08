"""Local double-door proposal; partition source quads without repainting artwork.

Closed surface equals original aca_door_1 plus unchanged aca_door_2 header.
Only the two interior leaves move. Boundary/hinges/open pose are explicitly a
proposal for first-set acceptance, not an authored animation or city migration.
"""
from __future__ import annotations
import copy,hashlib,json,math,zipfile
from pathlib import Path
from record_inputs import sha256
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/architecture/resources'
PACK=Path('C:/Users/vakir/Downloads/bbmc_v15_resource (1).zip')
NS='bloodborne_dw'

def dump(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def clip_uv(original,face,x0,y0,x1,y1):
    """Vanilla NORTH U runs -X, SOUTH U runs +X; V runs -Y."""
    sx0,sy0,_=original['from'];sx1,sy1,_=original['to']
    u0,v0,u1,v1=original['faces'][face]['uv']
    a,b=(sx1-x1,sx1-x0) if face=='north' else (x0-sx0,x1-sx0)
    return [u0+(u1-u0)*a/(sx1-sx0),v0+(v1-v0)*(sy1-y1)/(sy1-sy0),
            u0+(u1-u0)*b/(sx1-sx0),v0+(v1-v0)*(sy1-y0)/(sy1-sy0)]

def transformed_box(element,offset,extra_yaw=0,extra_pivot=None):
    """Match runtime: authored extra Y(+angle), blockstate Y(-180), offset."""
    corners=[]
    for source_x in [element['from'][0],element['to'][0]]:
        for y in [element['from'][1],element['to'][1]]:
            for source_z in [element['from'][2],element['to'][2]]:
                x,z=source_x,source_z
                if extra_pivot:
                    px,py,pz=extra_pivot;angle=math.radians(extra_yaw);dx=x-px;dz=z-pz
                    x,z=px+dx*math.cos(angle)+dz*math.sin(angle),pz-dx*math.sin(angle)+dz*math.cos(angle)
                corners.append([16-x+offset[0],y+offset[1],16-z+offset[2]])
    return {'from':[min(p[i] for p in corners) for i in range(3)],'to':[max(p[i] for p in corners) for i in range(3)]}

def main():
    imports=[]
    with zipfile.ZipFile(PACK) as archive:
        def rebase_model(source,path):
            data=archive.read(source);model=json.loads(data)
            assert not model.get('parent'),'Door prototype closure unexpectedly inherited'
            new=copy.deepcopy(model)
            for key,value in model.get('textures',{}).items():
                if value.startswith('#'):continue
                namespace,texture=value.split(':',1) if ':' in value else ('minecraft',value)
                original=f'assets/{namespace}/textures/{texture}.png'
                target=f'assets/{NS}/textures/base/source/{namespace}/{texture}.png'
                for suffix in ['','.mcmeta']:
                    if original+suffix in archive.namelist():
                        content=archive.read(original+suffix);destination=RES/(target+suffix)
                        destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(content)
                        imports.append({'source':original+suffix,'target':target+suffix,'sha256':hashlib.sha256(content).hexdigest(),'byte_identical':True})
                new['textures'][key]=f'{NS}:base/source/{namespace}/{texture}'
            if 'particle' not in new.get('textures',{}):new['textures']['particle']='#'+next(iter(model['textures']))
            dump(RES/f'assets/{NS}/models/{path}.json',new)
            imports.append({'source':source,'target':f'assets/{NS}/models/{path}.json','source_sha256':hashlib.sha256(data).hexdigest(),
                'elements_uv_rotation_unchanged':new['elements']==model['elements'],'particle_alias_added':'particle' not in model.get('textures',{})})
            return model,new
        original,panel=rebase_model('assets/minecraft/models/block/aca_door_1.json','base/prototype_door/source_panel')
        _,header=rebase_model('assets/minecraft/models/block/aca_door_2.json','base/prototype_door/source_header')
    assert len(original['elements'])==1
    source=original['elements'][0]
    assert source['from']==[-16,-16,-3] and source['to']==[32,32,-1]
    assert set(source['faces'])=={'north','south'}
    # A narrow fixed boundary and central double leaves; exact partition of
    # the source rectangle. No guessed side faces or new pixels are added.
    # Human screenshot1 is authoritative: the middle 16x48 panel is ONE leaf.
    # The two full side panels and the separately authored lintel stay static.
    # There is no authored hinge/animation in this two-face model. The source
    # panel boundary supplies the axis; left hinge is an explicit local proposal.
    rectangles=[('left_panel',[-16,-16,0,32],None),('right_panel',[16,-16,32,32],None),
        ('central_leaf',[0,-16,16,32],[0,0,-1])]
    parts=[];checks=[]
    for name,(x0,y0,x1,y1),pivot in rectangles:
        element=copy.deepcopy(source);element['from']=[x0,y0,-3];element['to']=[x1,y1,-1]
        for face in ['north','south']:element['faces'][face]['uv']=clip_uv(source,face,x0,y0,x1,y1)
        path=f'base/prototype_door/{name}'
        dump(RES/f'assets/{NS}/models/{path}.json',{**panel,'elements':[element]})
        alt=f'alt/prototype_door/{name}'
        dump(RES/f'assets/{NS}/models/{alt}.json',{'parent':f'{NS}:{path}'})
        parts.append({'name':name,'model':f'{NS}:{path}','alt_model':f'{NS}:{alt}',
            'source_rectangle':[x0,y0,x1,y1],'translation_model_units':[0,16,-16],
            'pivot_source_units':pivot,'moving':pivot is not None,'open_additional_yaw':90 if pivot else 0,
            'source_element':element})
    parts.append({'name':'source_header','model':f'{NS}:base/prototype_door/source_header',
        'alt_model':f'{NS}:alt/prototype_door/source_header','moving':False,'translation_model_units':[0,48,-16],
        'open_additional_yaw':0,'source_element':header['elements'][0]})
    dump(RES/f'assets/{NS}/models/alt/prototype_door/source_header.json',{'parent':f'{NS}:base/prototype_door/source_header'})
    # Prove disjointness + exact area coverage, then sample both surface UV
    # maps continuously at corners, center and non-grid fractional points.
    total=sum((r[2]-r[0])*(r[3]-r[1]) for _,r,_ in rectangles)
    assert total==48*48
    for i,(_,a,_) in enumerate(rectangles):
        for _,b,_ in rectangles[i+1:]:assert max(0,min(a[2],b[2])-max(a[0],b[0]))*max(0,min(a[3],b[3])-max(a[1],b[1]))==0
    samples=0
    for part in parts[:-1]:
        x0,y0,x1,y1=part['source_rectangle']
        for face in ['north','south']:
            uv=part['source_element']['faces'][face]['uv']
            for fx,fy in [(0,0),(1,1),(.5,.5),(.173,.819)]:
                x=x0+(x1-x0)*fx;y=y0+(y1-y0)*fy
                expected=clip_uv(source,face,x,y,x,y)[:2]
                u=uv[2]-(uv[2]-uv[0])*fx if face=='north' else uv[0]+(uv[2]-uv[0])*fx
                v=uv[3]-(uv[3]-uv[1])*fy
                assert math.isclose(u,expected[0],abs_tol=1e-12) and math.isclose(v,expected[1],abs_tol=1e-12)
                samples+=1
    combined={'credit':'Original bbmc aca_door source; UV-preserving first-set functional proposal',
        'textures':panel['textures'],'elements':[p['source_element'] for p in parts[:-1]],
        'display':{'gui':{'rotation':[20,30,0],'translation':[0,0,0],'scale':[.35,.35,.35]}}}
    dump(RES/f'assets/{NS}/models/item/prototype_double_door.json',{'parent':'builtin/entity'})
    descriptor={'schemaVersion':1,'kind':'FIRST_SET_FUNCTIONAL_PROPOSAL','id':f'{NS}:prototype_double_door','numeric_id':None,
        'source_models':['minecraft:block/aca_door_1','minecraft:block/aca_door_2'],
        'source_samples':[{'lower':[-93,59,-212],'upper':[-93,61,-212],'facing':'north','source_yaw':180},
            {'lower':[-58,76,-113],'upper':[-58,78,-113],'facing':'east','source_yaw':270}],
        'source_to_new_local_translation_units':[0,16,-16],'parts':parts,
        'facing_source_yaw':{'south':0,'west':90,'north':180,'east':270},
        'closed_geometry':'Exact partition of original panel; original header translated from source +2Y',
        'proposal':'One central leaf x0..16,y-16..32, hinge at source x0,z-1, opens +90 degrees. Full left/right panels and source header stay static. Closed art and both-face UV exactly preserved. Source has no animation or asymmetric hinge mesh: hinge side and swing are explicitly a proposed functional interpretation at the source central-panel boundary, subject to human review. Central passage remains floor-level. Static side decoration remains selection-only apart from two thin outside jambs, preserving the previously measured source side passage under the approved cheap/partial-penetration policy.',
        'source_open_pose_exists':False,'user_acceptance':'PENDING','closed_mask':'Derive sparse rasterization from exact closed/open part bounds; never reserve filled AABB',
        'original_neighbors_consumed':False}
    dump(RES/'bloodborne_dw/prototype-double-door.json',descriptor)
    poses={}
    for pose in ['closed','open']:
        render_parts=[];collision=[];selection=[]
        for part in parts:
            offset=[0,48,-16] if part['name']=='source_header' else [0,16,-16]
            moving=part['moving'] and pose=='open'
            extra=part['open_additional_yaw'] if moving else 0
            pivot=part.get('pivot_source_units') if moving else None
            render_parts.append({'model':part['model'],'altModel':part['alt_model'],'offset':offset,'yaw':180,'pivot':[8,8,8],
                **({'extraYaw':extra,'extraPivot':pivot} if moving else {})})
            selection.append(transformed_box(part['source_element'],offset,extra,pivot))
            # Selection follows the full authored surface. Hard contact is a
            # separate proposal: no threshold across the original passage,
            # and only the visible outer strip of each jamb blocks players.
            physical=copy.deepcopy(part['source_element'])
            if part['name']=='left_panel':physical['to'][0]=-15
            elif part['name']=='right_panel':physical['from'][0]=31
            collision.append(transformed_box(physical,offset,extra,pivot))
        poses[pose]={'parts':render_parts,'collision':collision,'selection':selection}
    # The22-pixel leaf width must become depth after90°, while its2-pixel
    # thickness becomes width. Verify all8 corners, including both Y levels.
    for index in [2]:
        bounds=poses['open']['selection'][index]
        assert math.isclose(bounds['to'][0]-bounds['from'][0],2,abs_tol=1e-9)
        assert math.isclose(bounds['to'][2]-bounds['from'][2],16,abs_tol=1e-9)
        assert math.isclose(bounds['to'][1]-bounds['from'][1],48,abs_tol=1e-9)
        assert not(bounds['from'][0]<8<bounds['to'][0]),'Open leaf must not fill the central passage'
    runtime={'schemaVersion':1,'units':16,'id':f'{NS}:prototype_double_door','displayName':'Резная входная дверь · одна створка',
        'openable':True,'support':{'offset':[0,-1,0],'required':True},'variants':[{'poses':poses}],
        'essentialMask':[[0,0,0]],'proposal':descriptor['proposal'],'sourceClosedExact':True,'numericId':None,
        'physicalPlacementRules':'Collision is independent from root-only reservation. Adjacent contact/decorative overlap allowed; foreign states never cleared.'}
    dump(RES/'bloodborne_dw/composite/prototype_double_door.json',runtime)
    audit={'schema':'dreamwalker-first-door-assets-v1','status':'SOURCE_CLOSED_SURFACE_PRESERVED_FUNCTIONAL_PROPOSAL',
        'source_pack_sha256':sha256(PACK),'imports':imports,'checks':{'closed_surface_partition_area':'PASS','disjoint_interior_parts':'PASS',
            'continuous_source_uv_preservation_both_faces':'PASS','uv_samples':samples,'missing_faces_added':False,
            'source_header_geometry':'PASS','open_leaf_width_depth_and_aperture':'PASS','original_texture_bytes':'PASS','minecraft_rendering':'NOT_RUN','door_gameplay':'NOT_RUN'},
        'proposal':descriptor['proposal'],'final_ids_frozen':False,
        'runtime_closed_bounds':{'from':[-16,0,1],'to':[32,64,3]},
        'runtime_open_collision':poses['open']['collision'],
        'runtime_transform_order':'extra authored Y; source blockstate yaw=-180 around8; offset; global facing clockwise'}
    dump(ROOT/'reports/DOOR_PROTOTYPE_ASSETS.json',audit)
    print('Source door partition/UV PASS; independent selection and floor-level open-passage proposal.')
if __name__=='__main__':main()
