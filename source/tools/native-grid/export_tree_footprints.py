"""Exact finite source_original_tree carrier footprints from immutable V127 tables.

This is the numerical route in CompositeSourceShift -> ImportedFullMapCollisionV127
-> ImportedCollisionMath for the addressed protected partial-tree descriptors.
It never renders, reads worlds, changes a database, or creates catalogue types.
"""
import hashlib,itertools,json,math,zipfile
from pathlib import Path
from export_cells import canonical
from export_merge_physics import require,sha,read
ROOT=Path(__file__).resolve().parents[3];P=ROOT/'source';R=ROOT/'research'
BASE=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW-city-mesh-20261018\deliverables\block-fixes-2-20261020\game\dreamwalker-bb-materials.1-streaming.7-blocks.2.jar')
BASESHA='40cecfb7e5cf783a762e350fc1864518c39abff177f6f025cb94892a181fbf51'
def hashed(value):return hashlib.sha256(canonical(value).encode()).hexdigest()
def transformed(boxes,mount,shift,crop):
    # Tables already select the authored yaw: transform receives rotation=0.
    values=[]
    for box in boxes:
        lo=[float(v)/16 for v in box['from']];hi=[float(v)/16 for v in box['to']]
        require(all(lo[i]<hi[i] for i in range(3)),'Degenerate exact imported box')
        xx=[.5+1*(x-.5)-0*(z-.5) for x,z in itertools.product((lo[0],hi[0]),(lo[2],hi[2]))]
        zz=[.5+0*(x-.5)+1*(z-.5) for x,z in itertools.product((lo[0],hi[0]),(lo[2],hi[2]))]
        values.append([min(xx)+shift[0],lo[1]+mount+shift[1],min(zz)+shift[2],max(xx)+shift[0],hi[1]+mount+shift[1],max(zz)+shift[2]])
    if not values or not crop:return values
    low=[min(v[i] for v in values) for i in range(3)];high=[max(v[i+3] for v in values) for i in range(3)]
    for i in range(3):
        if -.5<low[i]<0:low[i]=0
        if 1<high[i]<1.5:high[i]=1
    result=[]
    for value in values:
        bounded=[max(low[i],value[i]) for i in range(3)]+[min(high[i],value[i+3]) for i in range(3)]
        if all(bounded[i]<bounded[i+3] for i in range(3)):result.append(bounded)
    return result
def clipped(values):
    result={}
    for value in values:
        for cell in itertools.product(*(range(math.floor(value[i]+1e-8),math.ceil(value[i+3]-1e-8)) for i in range(3))):
            box=[max(0,value[i]-cell[i]) for i in range(3)]+[min(1,value[i+3]-cell[i]) for i in range(3)]
            if all(box[i+3]>box[i] for i in range(3)):result.setdefault(cell,[]).append(box)
    return result
def main():
    request=R/'native-functional-bases/unknown-native-pose-descriptors.json';inputsha=sha(request);descriptors=read(request);require(len(descriptors)==17,'This proof is restricted to the exact17 descriptors')
    require(sha(BASE)==BASESHA,'Immutable original JAR changed')
    with zipfile.ZipFile(BASE) as jar:
        resource='bloodborne_dw/imported-full-map-v127.json';raw=jar.read(resource);full=json.loads(raw);require(full['schemaVersion']==1 and full['unitsPerBlock']==16,'Exact V127 metadata schema changed')
        treebytes=jar.read('bloodborne_dw/composite/source_original_tree.json');tree=json.loads(treebytes);require(tree['simplePhysicalCollision'] is True and not tree.get('functionalDoorPhysics',False),'Expected imported simple physical dispatch changed')
        woodraw=jar.read('bloodborne_dw/wood-floor-collision.json');woodkeys={(v['source'],v['variant']) for v in json.loads(woodraw)}
        opticsraw=jar.read('bloodborne_dw/imported-whole-source-optics-v127.json');opticsrules=json.loads(opticsraw)['objects']
        definitions={v['variant']:v for v in full['objects'] if v['registryId']=='bloodborne_dw:source_original_tree'}
        classes={name:hashlib.sha256(jar.read('dev/dreamwalker/bloodbornedw/composite/'+name+'.class')).hexdigest() for name in ('CompositeSourceShift','WoodFloorCollision','ImportedFullMapCollisionV127','ImportedCollisionMath','ImportedWholeSourceOpticsV127','CompositeSpec','SourceOriginalTreeBlock')}
    poses=[]
    for descriptor in descriptors:
        pose=descriptor['legacyPose'];props=pose['properties'];variant=int(props['variant']);yaw=int(props['rotation']);shift=pose['shift'];mount=float(pose['mountY'])
        require(pose['registryId']=='bloodborne_dw:source_original_tree' and 2<=variant<=11 and yaw in (0,4,6) and props['open']=='false' and props['profile']=='base' and shift==[0,-1,0] and mount==0,'Descriptor is outside the exact finite reviewed transform route')
        require((pose['registryId'],variant) not in woodkeys,'Earlier wooden-floor provider applies to this exact tree pose')
        require(not any(rule['registryId']==pose['registryId'] and rule['variant']==variant for rule in opticsrules),'A separate whole-source optics rule adds helper membership')
        definition=definitions[variant];require(len(definition['rotations'])==8,'Incomplete exact yaw table');rotation=definition['rotations'][yaw]
        # SourceShift x=z=0, so world rotation leaves this vertical shift exact.
        collision=clipped(transformed(rotation['collision'],mount,shift,True));selection=collision if collision else clipped(transformed(rotation['aim'],mount,shift,False))
        cells=sorted(set(collision)|set(selection)|{(0,0,0)})
        footprint=[{'cell':list(cell),'collision':collision.get(cell,[]),'selection':selection.get(cell,[])} for cell in cells]
        poses.append({'poseKey':pose['key'],'legacyPose':pose,'cells':[list(cell) for cell in cells],'footprint':footprint,'footprintSha256':hashed(footprint),'nativeProvider':'ImportedFullMapCollisionV127.footprint','sourceTableVariant':variant,'sourceTableYaw':yaw,'sourceDefinitionSha256':hashed(definition),'sourceRotationSha256':hashed(rotation),'extraWholeOpticsCarrierCells':0})
    require(sha(request)==inputsha,'Exact descriptor input changed during proof')
    out=R/'native-functional-bases/unknown-native-tree-footprints.json';result={'schemaVersion':1,'status':'PASS','mode':'EXACT_FINITE_V127_RUNTIME_FOOTPRINT_REFERENCE','descriptorInput':{'path':str(request),'sha256':inputsha,'poses':17},'sourceJar':str(BASE),'sourceJarSha256':BASESHA,'resource':{'path':resource,'sha256':hashlib.sha256(raw).hexdigest()},'treeSpecSha256':hashlib.sha256(treebytes).hexdigest(),'primaryClassSha256':classes,'dispatch':'CompositeSourceShift.footprint -> physicalFootprint -> ImportedFullMapCollisionV127 (priority before V126/V125); selection uses collision iff nonempty, otherwise exact aim; origin always retained; whole-source optics applies only variant0, outside these17','numericRules':{'unitsPerBlock':16,'tablesAlreadyRotated':True,'transformRotation':0,'clipEpsilon':1e-8,'physicalFaceCropping':'strict -.5<low<0 and 1<high<1.5 after source shift','noVisualBBoxApproximation':True},'poses':poses,'worldFilesRead':0,'worldWrites':0,'databaseReads':0,'databaseWrites':0,'gameProcessesStarted':0,'catalogueWrites':0}
    result['earlierWoodFloorDispatch']={'resourceSha256':hashlib.sha256(woodraw).hexdigest(),'all17ExactKeysAbsent':True}
    result['wholeOpticsDispatch']={'resourceSha256':hashlib.sha256(opticsraw).hexdigest(),'all17ExactVariantRulesAbsent':True}
    out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps({'status':'PASS','output':str(out),'sha256':sha(out),'poses':len(poses),'totalFootprintCells':sum(len(v['cells']) for v in poses)}))
if __name__=='__main__':main()
