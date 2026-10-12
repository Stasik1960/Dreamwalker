"""Changed NEW poses only; retain exact V12.5 tables for old fragment states."""
import copy,json,hashlib
from pathlib import Path
import numpy as np
from gallery_platform_plan import ModelGeometry,corners
from simplify_imported_collisions import rotate_clip_boxes,exact_coalesce
from v125_collision_geometry import box,box_solids,oriented,solid_parts,envelope,axis_aligned,fit_large_boxes,geometry_key,validate_inside_boxes,inside,largest_inscribed_single
from v126_collision_independent_audit import crop_runtime,sweep_intersections,transform_point
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/architecture/resources/bloodborne_dw'
def read(p):return json.loads(p.read_text(encoding='utf8'))
def write(p,d,compact=False):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=None if compact else 2,separators=(',',':') if compact else None)+'\n',encoding='utf8',newline='\n')
def strict_inside(values,solids):
    """A cuboid contained in one convex source primitive is guaranteed inside.

    Exact unions of these rectangles remain inside the source union; unlike a
    sparse voxel sample this cannot miss a small wedge between primitives.
    """
    result=[];primitives=None
    for value in values:
        points=np.asarray(list(corners(value)))
        if any(np.all(inside(points,[solid])) for solid in solids):result.append(value);continue
        if primitives is None:primitives=[p for s in solids for p in largest_inscribed_single([s])]
        candidates=[]
        for p in primitives:
            low=np.maximum(value['from'],p['from']);high=np.minimum(value['to'],p['to'])
            if np.all(high-low>1e-7):candidates.append(box(low,high))
        if candidates:result.append(max(candidates,key=lambda b:np.prod(np.asarray(b['to'])-b['from'])))
    return result
def door_path(door,yaw):
    number=door['mainId'];x,z=(4.5,8) if number=='93160' else (8.04,8) if number=='90266' else (8,6) if number=='93152' else (8,7) if number=='93153' else (8,8) if number=='90031' else (8,0)
    centre=transform_point([x,14.416,z],yaw);normal=np.asarray([1,0,0] if number=='93160' else [0,0,1]);direction=transform_point(np.asarray([x,14.416,z])+normal,yaw)-centre
    return centre-direction*48,centre+direction*48,x
def door_joint_seeds(door,models,yaw,target_x):
    roles={m['model']:m['role'] for m in door['models']};seeds=[]
    for role in ['left','right']:
        parts=[p for p in door['partitionedClosedParts'] if roles[p['model']]==role]
        panels=solid_parts({'parts':parts,'collision':[box([-1000]*3,[1000]*3)]},models,panel_tolerance=.5)
        candidates=[]
        for panel in panels:
            low,high=envelope([panel]);width=high-low
            if width[0]<.2 or width[1]<16 or width[2]>1:continue
            x1=min(high[0]-1e-6,max(low[0]+.5,target_x+.25));x0=max(low[0]+1e-6,x1-.5)
            y0=max(low[1]+1e-6,.1);y1=min(high[1]-1e-6,y0+32)
            if x1-x0<1e-5 or y1-y0<16:continue
            seed=box([x0,y0,low[2]+1e-6],[x1,y1,high[2]-1e-6])
            distance=abs((x0+x1)/2-target_x)
            candidates.append((distance,seed))
        if candidates:
            seed=min(candidates,key=lambda a:a[0])[1]
            seeds.extend(largest_inscribed_single(oriented(box_solids([seed]),yaw)))
    return seeds
def main():
    old=read(RES/'imported-collision-v125.json');applied=read(ROOT/'docs/v12.6/applied-content.json')
    changed={r['registryId']:r for r in applied['groups']}
    changed['bloodborne_dw:source_hold_ladder_02']={'kind':'functional-ladder','before':0}
    doorfile=ROOT/'build/v126-door-plan/door-manifest.json'
    doorrows={};doorposes={}
    for file in [doorfile,ROOT/'build/v126-city-mounteddoor/door-manifest.json']:
        if not file.exists():continue
        doors=read(file)
        for row in doors['doors']:
            registry=row['registryId'];entry=doorrows.setdefault(registry,{'variants':[]});entry['variants']+=row.get('variants',[row['variant']])
            for v in row.get('variants',[row['variant']]):doorposes[registry,v]=row
            changed.setdefault(registry,{'kind':'functional-door','before':0})
    models=ModelGeometry();tables=[];indices={};objects={};cache={};proof=[]
    def table(rows):
        key=json.dumps(rows,sort_keys=True,separators=(',',':'))
        if key not in indices:indices[key]=len(tables);tables.append(copy.deepcopy(rows))
        return indices[key]
    for registry,reason in changed.items():
        spec=read(RES/'composite'/(registry.split(':')[1]+'.json'));values=[]
        for v,variant in enumerate(spec['variants']):
            isdoor=registry in doorrows and v in doorrows[registry].get('variants',[doorrows[registry].get('variant',0)])
            if v<reason['before'] and not isdoor:
                values.append({k:table(old['shapes'][s]) for k,s in old['objects'][registry][v].items()});continue
            selectors={}
            for posename in ['closed','open']:
                pose=variant['poses'].get(posename,variant['poses']['closed'])
                for profile in ['base','alt']:
                    ladder=reason['kind']=='functional-ladder'
                    purposeful=reason['kind']=='approved-complete-assembly' or isdoor or ladder
                    sourcebox=pose['collision']
                    if ladder:sourcebox=[box([0,0,15],[16,16 if v==0 else 32,16])]
                    solids=box_solids(sourcebox) if purposeful else solid_parts(pose,models,profile,panel_tolerance=1 if sourcebox else 0)
                    rows=[]
                    for yaw in range(8):
                        if not sourcebox:physical=[];policy='PRESERVED_PASSABLE'
                        elif purposeful and yaw%2==0:
                            physical=rotate_clip_boxes(sourcebox,yaw,crop=False);policy='PURPOSEFUL_FEW_CARDINAL_CUBOIDS'
                        elif ladder:
                            # A single tiny inscribed column lets Minecraft's
                            # axis-separated movement slide past a45-degree
                            # ladder. Three tall structural supports hold the
                            # player's normal approach without following rungs.
                            # Every support stays strictly inside the thin
                            # authored functional plane; no outward AABB growth.
                            # Keep the original central support's first-contact
                            # extent. Additional narrow side supports prevent
                            # sliding away during a sustained upward climb.
                            physical=largest_inscribed_single(oriented(solids,yaw))
                            for centre_x in (5,11):
                                support=box([centre_x-.5,0,15],[centre_x+.5,16 if v==0 else 32,16])
                                physical.extend(largest_inscribed_single(oriented(box_solids([support]),yaw)))
                            policy='THREE_INSCRIBED_TALL_FUNCTIONAL_LADDER_SUPPORTS'
                        elif solids:
                            source=oriented(solids,yaw);low,high=envelope(source)
                            if not purposeful and axis_aligned(source) and max(high-low)<=16.000001:
                                physical=[box(low,high)];policy='ONE_MODEL_BOUNDED_CUBOID'
                            else:
                                budget=9 if ladder else min(12,max(3,len(sourcebox)*3)) if purposeful else 9
                                key=(geometry_key(source),budget)
                                if key not in cache:cache[key]=fit_large_boxes(source,budget,.86)[0]
                                physical=strict_inside(cache[key],source);policy='FEW_INSCRIBED_ORDINARY_CUBOIDS'
                        else:physical=rotate_clip_boxes(sourcebox,yaw,crop=False);policy='PRESERVED_FINITE_PRIOR'
                        if isdoor and yaw%2:
                            door=doorposes[registry,v];start,finish,target_x=door_path(door,yaw)
                            if posename=='closed' and door['leafCount']==2 and not sweep_intersections(crop_runtime(physical),start,finish):
                                seeds=door_joint_seeds(door,models,yaw,target_x)
                                assert seeds,(registry,v,yaw)
                                physical=sorted(physical,key=lambda b:np.prod(np.asarray(b['to'])-b['from']),reverse=True)[:12-len(seeds)]+seeds
                                policy='FUNCTIONAL_CLOSED_JOINT_SEEDS_INSIDE_SOURCE_PLANES'
                            elif posename=='open' and door['leafCount']==1:
                                hit=sweep_intersections(crop_runtime(physical),start,finish)
                                # Tiny hinge/frame supports may be omitted to
                                # preserve a 0.6-wide walking passage at45°.
                                drop={h['boxIndex'] for h in hit if np.prod(np.asarray(h['box']['to'])-h['box']['from'])<=64}
                                physical=[b for i,b in enumerate(physical) if i not in drop]
                                if drop:policy='FUNCTIONAL_OPEN_HINGE_UNDERCOVERAGE_FOR_PLAYER_PASSAGE'
                        physical=exact_coalesce(physical) if physical else []
                        assert len(physical)<=12,(registry,v,yaw,len(physical))
                        aim=[]
                        selection=pose.get('selection',[])
                        if selection:
                            bounds=box([min(b['from'][i] for b in selection) for i in range(3)],[max(b['to'][i] for b in selection) for i in range(3)])
                            aim=rotate_clip_boxes([bounds],yaw,crop=False)
                        rows.append({'collision':physical,'aim':aim})
                        proof.append({'registryId':registry,'variant':v,'pose':posename,'profile':profile,'yaw':yaw,'boxes':len(physical),'policy':policy})
                    selectors[posename+profile.title()]=table(rows)
            values.append(selectors)
        objects[registry]=values
        print(json.dumps({'registry':registry,'variants':len(values),'tables':len(tables)}),flush=True)
    doc={'schemaVersion':1,'unitsPerBlock':16,'policy':'changed-new-functional-defaults-v126','baselineCommit':'c52a06c9fa20007a1c06c8fc654b3c1b86f72791','registeredNewTypes':1045,'maxBoxesPerOrientation':12,'authorCollisionExport':'NOT_SUPPLIED_NOT_APPLIED','objects':objects,'shapes':tables}
    target=RES/'imported-collision-v126.json';write(target,doc,True)
    java=ROOT/'src/architecture/java/dev/dreamwalker/bloodbornedw/composite'
    s=(java/'ImportedCollisionV125.java').read_text(encoding='utf8').replace('ImportedCollisionV125','ImportedCollisionV126').replace('imported-collision-v125.json','imported-collision-v126.json')
    s=s.replace('variants.size()>128','variants.size()>ImportedArtworkBlock.variantBudget(entry.getKey().substring("bloodborne_dw:".length()))')
    s=s.replace('!objects.keySet().equals(registered)','objects.isEmpty() || !registered.containsAll(objects.keySet())')
    s=s.replace('if (registered.size()!=1045 || objects.isEmpty() || !registered.containsAll(objects.keySet()))','if (registered.size()!=1045 || objects.isEmpty() || !registered.containsAll(objects.keySet()))')
    s=s.replace('Immutable, exact-cohort NEW default physics.','Immutable defaults for changed NEW V12.6 types and poses.')
    (java/'ImportedCollisionV126.java').write_text(s,encoding='utf8',newline='\n')
    for filename in ['CompositeSpec.java','CompositeSourceShift.java']:
        p=java/filename;s=p.read_text(encoding='utf8')
        if 'ImportedCollisionV126.supports' not in s:
            lines=s.splitlines();out=[]
            for line in lines:
                if 'if(ImportedCollisionV125.supports' in line or 'if (ImportedCollisionV125.supports' in line:out.append(line.replace('ImportedCollisionV125','ImportedCollisionV126'))
                out.append(line)
            p.write_text('\n'.join(out)+'\n',encoding='utf8',newline='\n')
    write(ROOT/'docs/v12.6/collisions/generation.json',{'status':'PASS_OFFLINE_GENERATION_NATIVE_NOT_RUN','resourceSha256':hashlib.sha256(target.read_bytes()).hexdigest(),'types':len(objects),'shapes':len(tables),'maximumBoxes':max(p['boxes'] for p in proof),'authorExport':'NOT_SUPPLIED_NOT_APPLIED','originalV12Changes':0,'priorV125ResourceUnchanged':True,'cases':proof})
if __name__=='__main__':main()
