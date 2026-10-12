"""Changed NEW poses only; retain exact V12.5 tables for old fragment states."""
import copy,json,hashlib
from pathlib import Path
import numpy as np
from gallery_platform_plan import ModelGeometry
from simplify_imported_collisions import rotate_clip_boxes,exact_coalesce
from v125_collision_geometry import box,box_solids,oriented,solid_parts,envelope,axis_aligned,fit_large_boxes,geometry_key,validate_inside_boxes
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/architecture/resources/bloodborne_dw'
def read(p):return json.loads(p.read_text(encoding='utf8'))
def write(p,d,compact=False):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=None if compact else 2,separators=(',',':') if compact else None)+'\n',encoding='utf8',newline='\n')
def main():
    old=read(RES/'imported-collision-v125.json');applied=read(ROOT/'docs/v12.6/applied-content.json')
    changed={r['registryId']:r for r in applied['groups']}
    changed['bloodborne_dw:source_hold_ladder_02']={'kind':'functional-ladder','before':0}
    doorfile=ROOT/'build/v126-door-plan/door-manifest.json'
    doorrows={}
    if doorfile.exists():
        doors=read(doorfile)
        for row in doors['doors']:
            registry=row['registryId'];doorrows[registry]=row
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
                        elif solids:
                            source=oriented(solids,yaw);low,high=envelope(source)
                            if not purposeful and axis_aligned(source) and max(high-low)<=16.000001:
                                physical=[box(low,high)];policy='ONE_MODEL_BOUNDED_CUBOID'
                            else:
                                budget=9 if ladder else min(12,max(3,len(sourcebox)*3)) if purposeful else 9
                                key=(geometry_key(source),budget)
                                if key not in cache:cache[key]=fit_large_boxes(source,budget,.86)[0]
                                physical=validate_inside_boxes(cache[key],source);policy='FEW_INSCRIBED_ORDINARY_CUBOIDS'
                        else:physical=rotate_clip_boxes(sourcebox,yaw,crop=False);policy='PRESERVED_FINITE_PRIOR'
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
