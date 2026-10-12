"""Apply exact-UV moving parts with a few useful frame/leaf physics cuboids."""
import copy,json,hashlib,shutil
from pathlib import Path
import numpy as np
from gallery_platform_plan import ModelGeometry
from v125_collision_geometry import solid_parts,envelope,fit_large_boxes,box
from simplify_imported_collisions import exact_coalesce
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/architecture/resources';STAGE=ROOT/'build/v126-door-plan'
NAMES={'90031':'Двустворчатая дверь с каменной аркой','93160':'Узкая деревянная дверь','90266':'Большие кованые двустворчатые ворота','93152':'Двустворчатая дверь с застеклённой перемычкой','90458':'Деревянная дверь с каменным обрамлением','93153':'Двустворчатая дверь с деревянным обрамлением','90923':'Деревянная дверь с высокой каменной аркой'}
def read(p):return json.loads(p.read_text(encoding='utf8'))
def write(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
    manifest=read(STAGE/'door-manifest.json');assert manifest['status'].startswith('PASS_STAGED') and not manifest['original90001Modified']
    cat=read(RES/'bloodborne_dw/remaining-catalogue.json');byregistry={'bloodborne_dw:'+r['path']:r for r in cat['objects']}
    evidence=[]
    for door in manifest['doors']:
        for model in door['models']:
            source=Path(model['file']);assert hashlib.sha256(source.read_bytes()).hexdigest()==model['sha256']
            relative=source.relative_to(STAGE/'resources');target=RES/relative;target.parent.mkdir(parents=True,exist_ok=True)
            assert not target.exists() or target.read_bytes()==source.read_bytes();shutil.copyfile(source,target)
    models=ModelGeometry();preserved={}
    for door in manifest['doors']:
        file=RES/'bloodborne_dw/composite'/(door['registryId'].split(':')[1]+'.json');spec=read(file);variant=spec['variants'][door['variant']]
        assert variant['poses']['closed']['parts']==door['sourceClosedParts']
        preserved[door['registryId']]=copy.deepcopy(variant['poses']['closed']['parts'])
        roles={m['model']:m['role'] for m in door['models']};domain=[];perrole=[]
        for role in ['static','left','right','single']:
            parts=[p for p in door['partitionedClosedParts'] if roles[p['model']]==role]
            if not parts:continue
            bounds=[m['boundsUnits'] for m in door['models'] if m['role']==role]
            low=np.asarray([min(b[i] for b in bounds)-1 for i in range(3)]);high=np.asarray([max(b[i+3] for b in bounds)+1 for i in range(3)])
            solids=solid_parts({'parts':parts,'collision':[box(low,high)]},models,panel_tolerance=.5)
            assert solids,(door['mainId'],role)
            budget=4 if role=='static' else 3
            fitted,detail=fit_large_boxes(solids,budget,.82);assert fitted,(door['mainId'],role,detail)
            domain.extend(fitted);perrole.append({'role':role,'closedBoxes':fitted,'fit':detail})
        closed=exact_coalesce(domain);opened=[]
        for role in perrole:
            config=door['leafHinges'].get(role['role'])
            for value in role['closedBoxes']:
                if config:
                    from gallery_platform_plan import corners,rotate
                    points=np.asarray([rotate(p,1,-config['yaw'],config['pivot']) for p in corners(value)])
                    opened.append(box(points.min(0),points.max(0)))
                else:opened.append(copy.deepcopy(value))
        opened=exact_coalesce(opened)
        assert len(closed)<=12 and len(opened)<=12
        variant['poses']['closed']['collision']=closed;variant['poses']['closed']['selection']=copy.deepcopy(closed)
        variant['poses']['closed'].pop('simpleCollisionByRotation',None)
        variant['poses']['open']={'parts':door['openParts'],'collision':opened,'selection':copy.deepcopy(opened)}
        spec['openable']=True;spec['displayName']=NAMES[door['mainId']]
        spec['v126DoorPolicy']='RMB toggles both marked leaves simultaneously by90degrees; fixed frame/header/outerpanels preserve source geometry. CLOSED sourceparts/UV/textures unchanged. Thin panel support .5modelunits is purposeful physical behavior; few fitted ordinary cuboids, not author Collision Studio shapes.'
        spec.pop('v126DefaultPhysicsStatus',None)
        assert variant['poses']['closed']['parts']==preserved[door['registryId']]
        write(file,spec)
        row=byregistry[door['registryId']];row['displayName']=spec['displayName'];row['variants'][door['variant']]['name']=spec['displayName']
        evidence.append({'id':door['mainId'],'registryId':door['registryId'],'variant':door['variant'],'leafCount':door['leafCount'],'closedBoxes':closed,'openBoxes':opened,'perRole':perrole,'sourceClosedPartsPreserved':True,'nativeValidation':'NOT_RUN'})
    write(RES/'bloodborne_dw/remaining-catalogue.json',cat)
    write(ROOT/'docs/v12.6/doors/applied-doors.json',{'status':'PASS_SOURCE_STAGING_APPLIED_NATIVE_NOT_RUN','doors':evidence,'sourceOriginal90001Changed':False,'authorCollisionExport':'NOT_SUPPLIED_NOT_APPLIED'})
    print(json.dumps({'status':'PASS','doors':len(evidence),'leaves':sum(d['leafCount'] for d in evidence),'cardinalMaxBoxes':max(len(d['closedBoxes']) for d in evidence)}))
if __name__=='__main__':main()
