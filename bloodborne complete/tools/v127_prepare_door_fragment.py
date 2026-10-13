"""User-approved NEW door fragment family; protected90001/retired90479 untouched."""
import copy,json,math
import numpy as np
from pathlib import Path
from v125_map_index import ROOT,MAP,PACK,sha,token
from v127_apply_full_map_poses import write,box,bounds_for_quads
from v125_map_door_projections import SourceVisuals
from v125_map_verify_extra_pose_uv import source_quads,signature
from v124_visual_audit import Visuals
from import_remaining_assets import Source
R=ROOT/'src/architecture/resources';O=ROOT/'docs/v12.7/conversion';ID='bloodborne_dw:source_door_fragment';NUMBER='93233'
def main():
    debugpath=R/'bloodborne_dw/debug_catalogue.json';debug=json.loads(debugpath.read_text(encoding='utf-8-sig'));assert not any(e['temporaryId']==NUMBER or e['registryId']==ID for e in debug['entries'])
    source=SourceVisuals();native=Visuals(vanilla_jar=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar');export=Source(PACK,Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar');variants=[];physics=[];proof=[]
    for index,(model,name) in enumerate([('aca_door_2','Вершина портала'),('aca_door_1','Низ портала')]):
        apps=[{'model':'minecraft:block/'+model}];quads=source_quads(source,apps);lo,hi=bounds_for_quads(quads);offset=np.array([0.,-lo[1],-lo[2]]);part={'model':export.export_application_model(apps[0]),'offset':offset.tolist(),'yaw':0,'pitch':0};visible=box(lo+offset,hi+offset);center=(lo+hi)/2;size=hi-lo;rotations=[]
        for yaw in range(8):
            a=math.pi*yaw/4;c,s=math.cos(a),math.sin(a);normalized=center+offset;x,z=normalized[0]-8,normalized[2]-8;middle=np.array([x*c-z*s+8,normalized[1],x*s+z*c+8])
            extent=np.array([min(size[0],size[2])/math.sqrt(2),size[1],min(size[0],size[2])/math.sqrt(2)]) if yaw%2 else np.array([size[2] if yaw%4 else size[0],size[1],size[0] if yaw%4 else size[2]])
            physical=box(middle-extent/2,middle+extent/2);rotations.append({'collision':[physical],'aim':[copy.deepcopy(physical)]})
        pose={'parts':[part],'collision':rotations[0]['collision'],'selection':rotations[0]['aim']};variants.append({'poses':{'closed':pose,'open':copy.deepcopy(pose)},'visibleBounds':visible,'sourcePlacementOffset':offset.tolist(),'sourceClosedApplications':apps,'sourceOpenApplications':[],'sourceVariant':name})
        actual=[(v-offset,uv,t,ti) for v,uv,t,ti in native.quads(part)];assert signature(quads,source)==signature(actual,native)
        physics.append({'registryId':ID,'variant':index,'rotations':rotations,'sourceVariant':name});proof.append({'variant':index,'name':name,'sourceApplications':apps,'sourceShift':(-offset/16).tolist(),'status':'PASS_EXACT_SOURCE_VERTEX_UV_RGBA_TINTINDEX','physicalRule':'One ordinary rectangle/cuboid; inscribed minimum square at45degrees; exact rootface<0.5crop applied at runtime.'})
    d={'schemaVersion':1,'units':16,'id':ID,'displayName':'Часть портала','openable':False,'layer':'cutout','support':{'required':False},'essentialMask':[[0,0,0]],'variants':variants,'simplePhysicalCollision':True,'source':{'kind':'USER_APPROVED_SEPARATE_DOOR_FRAGMENTS','protectedWholeId':'90001','retiredNumberNeverReused':'90479'},'sourcePolicy':'One installing item, two meaningful source part states. This is a fixed building part, no opening mechanism. Whole source pairs still map to unchanged90001.'}
    particle=native.quads(variants[0]['poses']['closed']['parts'][0])[0][2]
    write(R/'bloodborne_dw/composite/source_door_fragment.json',d);write(R/'assets/bloodborne_dw/blockstates/source_door_fragment.json',{'variants':{'':{'model':'bloodborne_dw:block/source_door_fragment_empty'}}});write(R/'assets/bloodborne_dw/models/block/source_door_fragment_empty.json',{'textures':{'particle':particle},'elements':[]});write(R/'assets/bloodborne_dw/models/item/source_door_fragment.json',{'parent':'builtin/entity'})
    debug['entries'].append({'temporaryId':NUMBER,'finalId':None,'kind':'architecture','name':'Часть портала','registryId':ID,'aliases':[ID]});write(debugpath,debug)
    p=R/'bloodborne_dw/remaining-catalogue.json';cat=json.loads(p.read_text(encoding='utf-8-sig'));cat['objects'].append({'path':'source_door_fragment','displayName':'Часть портала','variants':[{'variant':i,'name':v['sourceVariant'],'sourceModels':[a['model'] for a in v['sourceClosedApplications']]} for i,v in enumerate(variants)]});write(p,cat)
    p=R/'bloodborne_dw/imported-full-map-v127.json';f=json.loads(p.read_text(encoding='utf-8-sig'));f.setdefault('additionalRegistryIds',[]).append(ID);f['objects'].extend(physics);write(p,f)
    p=R/'assets/bloodborne_dw/lang/ru_ru.json';lang=json.loads(p.read_text(encoding='utf-8-sig'));lang['block.bloodborne_dw.source_door_fragment']='Часть портала';lang['item.bloodborne_dw.source_door_fragment']='Часть портала';write(p,lang)
    write(O/'door-fragment-proof.json',{'status':'PASS_USER_APPROVED_EXACT_NEW_FRAGMENT_FAMILY','registryId':ID,'mainId':NUMBER,'variants':proof,'sourceCellCount':393,'headerCells':381,'lowerCells':12,'whole90001Changed':False,'old90479Restored':False,'sourceWorldConverted':False,'nativeValidation':'NOT_RUN_BY_OFFLINE_PROOF'})
    print(token({'newId':NUMBER,'variants':2,'sourceCells':393}))
if __name__=='__main__':main()
