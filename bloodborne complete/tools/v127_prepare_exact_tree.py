"""New exact original tree, leaving the intentionally revised V12 tree intact."""
import copy,json,hashlib
import numpy as np
from pathlib import Path
from v125_map_index import ROOT,PACK,MAP,dump,sha
from v127_apply_full_map_poses import write,box,bounds_for_quads
from v125_map_door_projections import SourceVisuals
from v125_map_verify_extra_pose_uv import source_quads,signature
from v124_visual_audit import Visuals
from import_remaining_assets import Source

RES=ROOT/'src/architecture/resources';OUT=ROOT/'docs/v12.7/conversion';VANILLA=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'
ID='bloodborne_dw:source_original_tree';NUMBER='93232'
TREE=['white_wool','oak_log','spruce_log','birch_log','birch_log_1','jungle_log','acacia_log','dark_oak_log','orange_wool','magenta_wool','light_blue_wool','cyan_wool']
def main():
    exporter=Source(PACK,VANILLA);source=SourceVisuals();native=Visuals(vanilla_jar=VANILLA)
    apps=[{'model':'minecraft:block/white_wool'}]
    for y,center,left,right,back in [(48,'orange_wool','oak_log','jungle_log','oak_log'),(96,'magenta_wool','spruce_log','acacia_log','spruce_log'),(144,'light_blue_wool','birch_log_1','dark_oak_log','birch_log')]:
        apps.extend([{'model':'minecraft:block/'+center,'offset':[0,y,0]}, {'model':'minecraft:block/'+left,'y':270,'offset':[-48,y,0]}, {'model':'minecraft:block/'+right,'y':270,'offset':[48,y,0]}, {'model':'minecraft:block/'+right,'y':180,'offset':[0,y,-48]}, {'model':'minecraft:block/'+back,'y':180,'offset':[0,y,48]}])
    apps.append({'model':'minecraft:block/cyan_wool','offset':[0,192,0]})
    variants=[];proof=[]
    for index,selected in enumerate([apps]+[[{'model':'minecraft:block/'+name}] for name in TREE]):
        quads=source_quads(source,selected);lo,hi=bounds_for_quads(quads);offset=[0.,round(-float(lo[1]),8),0.];parts=[]
        for a in selected:
            off=[a.get('offset',[0,0,0])[i]+offset[i] for i in range(3)]
            parts.append({'model':exporter.export_application_model(a),'offset':off,'yaw':a.get('y',0),'pitch':a.get('x',0)})
        bounds=box(lo+offset,hi+offset)
        trunk=box([7,0,7],[9,48,9]) if index==0 or selected[0]['model'].endswith('white_wool') else None
        collision=[trunk] if trunk else [];aim=collision or [copy.deepcopy(bounds)]
        for b in aim:
            for axis in range(3):
                if b['to'][axis]-b['from'][axis]<.125:
                    center=(b['to'][axis]+b['from'][axis])/2;b['from'][axis]=center-.0625;b['to'][axis]=center+.0625
        closed={'parts':parts,'collision':collision,'selection':aim}
        variants.append({'poses':{'closed':closed,'open':copy.deepcopy(closed)},'visibleBounds':bounds,'sourcePlacementOffset':offset,'sourceClosedApplications':selected,'sourceOpenApplications':[], 'sourceVariant':'Цельное исходное дерево' if index==0 else 'Сохранённая исходная часть: '+TREE[index-1], 'v127SourceOnlyCompatibilityPart':index>0})
        actual=[(v-np.asarray(offset),uv,t,ti) for part in parts for v,uv,t,ti in native.quads(part)]
        a,b=signature(quads,source),signature(actual,native);assert a==b,('Original tree source mismatch',index)
        proof.append({'variant':index,'sourceApplications':selected,'sourceFaces':sum(a.values()),'status':'PASS_EXACT_SOURCE_VERTEX_UV_RGBA','sourceShift':[-v/16 for v in offset]})
    descriptor={'schemaVersion':1,'units':16,'id':ID,'displayName':'Сухое дерево','openable':False,'layer':'cutout','support':{'required':False},'essentialMask':[[0,0,0]],'variants':variants,'simplePhysicalCollision':True,'source':{'kind':'SOURCE_ORIGINAL_TREE_UNALTERED','authorTreeModels':TREE,'visibleDifferenceFromV12':'Original branches begin3blocks abovewhite_wool source root; protectedV12 proposedtree movesbranchcrown7blocks aboveitsroot anddoubleslowerstem. Originalsourcegeometrynotreverted; thisnewtypepreservesactualsourcefaces.'},'sourcePartialCompatibility':'Partialsourcepieces retainedonlyforincompleteauthoredtrees; handpick/placecanonicalwholevariant0, noseparateinstallationitems.'}
    write(RES/'bloodborne_dw/composite/source_original_tree.json',descriptor)
    write(RES/'assets/bloodborne_dw/blockstates/source_original_tree.json',{'variants':{'':{'model':'bloodborne_dw:block/source_original_tree_empty'}}})
    write(RES/'assets/bloodborne_dw/models/block/source_original_tree_empty.json',{'textures':{'particle':'bloodborne_dw:block/remaining/source/minecraft/block/addon/tree_1'},'elements':[]})
    write(RES/'assets/bloodborne_dw/models/item/source_original_tree.json',{'parent':'builtin/entity'})
    catalogue=RES/'bloodborne_dw/remaining-catalogue.json';cat=json.loads(catalogue.read_text(encoding='utf-8-sig'));assert not any(r['path']=='source_original_tree' for r in cat['objects'])
    cat['objects'].append({'path':'source_original_tree','displayName':'Сухое дерево','variants':[{'variant':i,'name':'Сухое дерево','sourceModels':[a['model'] for a in v['sourceClosedApplications']]} for i,v in enumerate(variants)]});write(catalogue,cat)
    debug=RES/'bloodborne_dw/debug_catalogue.json';d=json.loads(debug.read_text(encoding='utf-8-sig'));assert not any(r['temporaryId']==NUMBER or r['registryId']==ID for r in d['entries']);d['entries'].append({'temporaryId':NUMBER,'finalId':None,'kind':'architecture','name':'Сухое дерево','registryId':ID,'aliases':[ID]});write(debug,d)
    langfile=RES/'assets/bloodborne_dw/lang/ru_ru.json';lang=json.loads(langfile.read_text(encoding='utf-8-sig'));lang['block.bloodborne_dw.source_original_tree']='Сухое дерево';lang['item.bloodborne_dw.source_original_tree']='Сухое дерево';write(langfile,lang)
    dump(OUT/'original-tree-proof.json',{'status':'PASS_NEW_EXACT_SOURCE_TREE_NO_V12_CHANGE','registryId':ID,'mainId':NUMBER,'variantCount':len(variants),'physicalRule':'One2x48x2modelunittrunkcuboid; nobranchcollisions. Sourcepartials passableexceptstem.','sourceDifference':descriptor['source']['visibleDifferenceFromV12'],'sourceCoordinateEvidence':'source-tree-inspection.json','checks':proof,'sourceHashes':{'map':sha(MAP),'pack':sha(PACK)},'sourceMapConverted':False,'nativeValidation':'NOT_RUN_BY_OFFLINE_PROOF'})
    print(json.dumps({'status':'PASS','id':NUMBER,'variants':len(variants),'wholeSourceParts':len(apps)}))
if __name__=='__main__':main()
