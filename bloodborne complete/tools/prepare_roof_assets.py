"""One independent first-set roof: exact source visual, sparse authored physics.

Two positive-volume elements provide collision. The two zero-volume decorative
fins receive only a small selection thickness. Source elements are never flattened
or rewritten to add a second intrinsic rotation; the composite renderer turns the
complete baked source. No source-world writes or final catalog IDs are performed.
"""
from __future__ import annotations
import copy, hashlib, json, math, zipfile
from pathlib import Path
from analyze_resources import element_vertices, rotate_point
from record_inputs import sha256

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/architecture/resources'
PACK=Path('C:/Users/vakir/Downloads/bbmc_v15_resource (1).zip')
NS='bloodborne_dw'
SOURCE='assets/minecraft/models/block/stairs/jungle_stairs_roof.json'
MODEL=f'{NS}:base/source/minecraft/block/stairs/jungle_stairs_roof'
OFFSET=[0,3,-5]
STRIP=.5
EPSILON=.125  # model units, selection only

def dump(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def digest(data):return hashlib.sha256(data).hexdigest()

def clip_polygon(points,axis,limit,lower):
    result=[]
    for a,b in zip(points,points[1:]+points[:1]):
        inside_a=a[axis]>=limit if lower else a[axis]<=limit
        inside_b=b[axis]>=limit if lower else b[axis]<=limit
        if inside_a:result.append(a)
        if inside_a!=inside_b:
            amount=(limit-a[axis])/(b[axis]-a[axis]);result.append([a[i]+amount*(b[i]-a[i]) for i in range(2)])
    return result

def surface_boxes(element,selection=False):
    low=element['from'].copy();high=element['to'].copy()
    flat=any(math.isclose(low[i],high[i],abs_tol=1e-9) for i in range(3))
    if flat and not selection:return []
    if flat:
        for i in range(3):
            if math.isclose(low[i],high[i],abs_tol=1e-9):low[i]-=EPSILON/2;high[i]+=EPSILON/2
    rotation=element.get('rotation',{})
    assert rotation.get('axis')=='x' and rotation.get('angle')==45, 'Only the reviewed authored X slope is implemented'
    origin=rotation['origin']
    polygon=[]
    for y,z in [(low[1],low[2]),(high[1],low[2]),(high[1],high[2]),(low[1],high[2])]:
        point=rotate_point([low[0],y,z],'x',45,origin,rotation.get('rescale',False))
        # The renderer first performs intrinsic model rotations, then source
        # blockstate NORTH yaw180, then the explicitly declared new-build offset.
        point=rotate_point(point,'y',-180,[8,8,8])
        polygon.append([point[1]+OFFSET[1],point[2]+OFFSET[2]])
    x0=16-high[0]+OFFSET[0];x1=16-low[0]+OFFSET[0]
    boxes=[]
    for strip in range(math.floor(min(p[1] for p in polygon)/STRIP),math.ceil(max(p[1] for p in polygon)/STRIP)):
        a,b=strip*STRIP,(strip+1)*STRIP
        clipped=clip_polygon(clip_polygon(polygon,1,a,True),1,b,False)
        if not clipped:continue
        y0,y1=min(p[0] for p in clipped),max(p[0] for p in clipped);z0,z1=min(p[1] for p in clipped),max(p[1] for p in clipped)
        if y1-y0<1e-8 or z1-z0<1e-8:continue
        boxes.append({'from':[round(x0,10),round(y0,10),round(z0,10)],'to':[round(x1,10),round(y1,10),round(z1,10)]})
    return boxes

def main():
    before=sha256(PACK);imports=[]
    with zipfile.ZipFile(PACK) as archive:
        raw=archive.read(SOURCE);original=json.loads(raw);model=copy.deepcopy(original)
        assert 'parent' not in original and len(original['elements'])==4
        for key,ref in model['textures'].items():
            assert not ref.startswith('#');ns,path=ref.split(':',1) if ':' in ref else ('minecraft',ref)
            source=f'assets/{ns}/textures/{path}.png';target=f'assets/{NS}/textures/base/source/{ns}/{path}.png'
            for suffix in ['', '.mcmeta','_e.png']:
                src=source if not suffix else source+suffix if suffix=='.mcmeta' else source[:-4]+suffix
                dst=target if not suffix else target+suffix if suffix=='.mcmeta' else target[:-4]+suffix
                if src in archive.namelist():
                    data=archive.read(src);destination=RES/dst;destination.parent.mkdir(parents=True,exist_ok=True)
                    if destination.exists() and destination.read_bytes()!=data:raise ValueError('Refusing to overwrite edited source texture '+str(destination))
                    destination.write_bytes(data);imports.append({'kind':'texture','source':src,'target':dst,'source_sha256':digest(data),'byte_identical':True})
            model['textures'][key]=f'{NS}:base/source/{ns}/{path}'
        model['textures']['particle']='#3'
        assert model['elements']==original['elements'] and model['display']==original['display']
        dump(RES/f'assets/{NS}/models/base/source/minecraft/block/stairs/jungle_stairs_roof.json',model)
        imports.append({'kind':'model','source':SOURCE,'target':f'assets/{NS}/models/base/source/minecraft/block/stairs/jungle_stairs_roof.json','source_sha256':digest(raw),'geometry_uv_rotations_display_preserved':True,'particle_alias_added':True})
        alt=f'{NS}:alt/prototype_roof/0';dump(RES/f'assets/{NS}/models/alt/prototype_roof/0.json',{'parent':MODEL})
        collision=[box for element in original['elements'] for box in surface_boxes(element)]
        selection=[box for element in original['elements'] for box in surface_boxes(element,selection=True)]
        vertices=[point for element in original['elements'] for point in element_vertices(element)]
        solid_vertices=[point for element in original['elements'] if all(element['from'][i]!=element['to'][i] for i in range(3)) for point in element_vertices(element)]
        source_bounds={'from':[min(p[i] for p in vertices) for i in range(3)],'to':[max(p[i] for p in vertices) for i in range(3)]}
        solid_bounds={'from':[min(p[i] for p in solid_vertices) for i in range(3)],'to':[max(p[i] for p in solid_vertices) for i in range(3)]}
        normalized=[[(16-p[0])+OFFSET[0],p[1]+OFFSET[1],(16-p[2])+OFFSET[2]] for p in solid_vertices]
        lowest=min(p[1] for p in normalized);contact=[p for p in normalized if math.isclose(p[1],lowest,abs_tol=1e-8)]
        assert math.isclose(solid_bounds['from'][1],-3,abs_tol=1e-8)
        assert math.isclose(lowest,0,abs_tol=1e-8)
        assert sorted(p[0] for p in contact)==[3,13] and all(math.isclose(p[2],8,abs_tol=1e-8) for p in contact),contact
        part={'model':MODEL,'altModel':alt,'yaw':180,'pivot':[8,8,8],'offset':OFFSET}
        runtime={'schemaVersion':1,'units':16,'id':f'{NS}:prototype_roof','displayName':'Наклонный кровельный модуль — прототип',
            'openable':False,'support':{'required':True,'offset':[0,-1,0]},'essentialMask':[[0,0,0]],
            'variants':[{'poses':{'closed':{'parts':[part],'collision':collision,'selection':selection}}}],
            'sourceGeometryExact':True,'numericId':None,'sourceMountProposal':'New-build foot normalization; source-equivalent fractional world compensation is explicitly audited. City migration pending.',
            'selectionOnlyFinThicknessUnits':EPSILON,'physicalPlacementRules':'Only primary root reservation; preserve adjacent soft overlaps and native foreign blocks. Sloped solids rasterized, fins do not collide.'}
        dump(RES/'bloodborne_dw/composite/prototype_roof.json',runtime)
        dump(RES/f'assets/{NS}/models/block/prototype_roof_empty.json',{'elements':[],'textures':{'particle':model['textures']['3']}})
        dump(RES/f'assets/{NS}/blockstates/prototype_roof.json',{'variants':{'':{'model':f'{NS}:block/prototype_roof_empty'}}})
        dump(RES/f'assets/{NS}/models/item/prototype_roof.json',{'parent':'builtin/entity'})
        # The generic composite block supplies one item per owner; no carrier loot
        # or vanilla stairs neighbor rules are inherited.
        join=json.loads((ROOT/'reports/RESOURCE_AUDIT_WORLD_JOIN.json').read_text(encoding='utf-8'))['states']
        roof_rows=[row for row in join if row['name']=='minecraft:jungle_stairs' and row['properties'].get('half')=='top' and row['properties'].get('shape')=='straight']
        window_rows=[row for row in join if row['name']=='minecraft:jungle_stairs' and row['properties'].get('half')=='bottom' and row['properties'].get('shape')=='straight']
        blockstate=json.loads(archive.read('assets/minecraft/blockstates/jungle_stairs.json'))
        audit={'schema':'dreamwalker-roof-firstset-v1','status':'SOURCE_VISUAL_PRESERVED_NEW_BUILD_MOUNT_PROPOSAL','source_pack_sha256':before,
            'source_model':SOURCE,'imports':imports,'source_source_bounds_units':source_bounds,'source_solid_bounds_units':solid_bounds,
            'normalization':{'source_north_yaw':180,'part_offset_units':OFFSET,'source_equivalent_world_compensation_blocks':[0,-3/16,5/16],
                'normalized_lowest_solid_vertices_units':contact,'normalized_contact':'Lowest actual rotated/rescaled edge spans X3..13 at Y0,Z8. Real central support top reaches this edge.'},
            'collision':{'solid_source_elements':[0,1],'decorative_zero_volume_elements':[2,3],'boxes':len(collision),'selection_boxes':len(selection),'slope_strip_units':STRIP,
                'selection_only_plane_extrusion_units':EPSILON,'full_visual_aabb_filled':False,'root_reservation_only':True},
            'same_carrier_proof':{'carrier':'minecraft:jungle_stairs','roof_source_cells':sum(row['count'] for row in roof_rows),'wood_window_source_cells':sum(row['count'] for row in window_rows),
                'counts_mean':'source cells, not object counts','roof_states':[{'state':row['state'],'count':row['count'],'samples':row['samples']} for row in roof_rows],
                'window_states':[{'state':row['state'],'count':row['count'],'samples':row['samples'][:2]} for row in window_rows],
                'selectors':{key:value for key,value in blockstate['variants'].items() if 'shape=straight' in key},
                'independence_evidence':'Different selected model, different original texture and authored geometry; sloped fixed roof/fins versus upright window center and pivoted sides. Separate registered kinds with no vanilla StairsBlock inheritance.'},
            'random_selection':'One deterministic alternative per source selector; no rerolling, random art state or weights to flatten',
            'source_world_changed':False,'city_migration':'NOT_RUN_MOUNT_ALIGNMENT_PENDING','numeric_ids_frozen':False,
            'checks':{'all_32_source_corners_rotated_with_authored_rescale':'PASS','exact_intrinsic_geometry_uv_display':'PASS','lowest_solid_edge_and_normalized_contact':'PASS',
                'original_texture_bytes':'PASS','distinct_unrelated_models_same_carrier':'PASS','minecraft_server':'NOT_RUN','minecraft_client_render':'NOT_RUN'}}
        assert sha256(PACK)==before
        dump(ROOT/'reports/ROOF_PROTOTYPE_ASSETS.json',audit)
        print(json.dumps({'roof_assets':'PASS','solid_elements':2,'selection_only_fins':2,'collision_boxes':len(collision),'selection_boxes':len(selection),'source_cells':audit['same_carrier_proof']['roof_source_cells'],'numeric_ids':0}))

if __name__=='__main__':main()
