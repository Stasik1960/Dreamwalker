"""Exact author-textured vanilla-parent mirrored poses omitted by the finite index."""
import copy,json
import numpy as np
from pathlib import Path
from v125_map_index import ROOT,PACK,token
from v127_apply_full_map_poses import write,box,bounds_for_quads,simple_boxes
from v125_map_door_projections import SourceVisuals
from v125_map_verify_extra_pose_uv import source_quads,signature
from v124_visual_audit import Visuals
from import_remaining_assets import Source
R=ROOT/'src/architecture/resources';O=ROOT/'docs/v12.7/conversion'
def main():
    source=SourceVisuals();native=Visuals(vanilla_jar=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar');export=Source(PACK,Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar');p=R/'bloodborne_dw/composite/source_deepslate.json';d=json.loads(p.read_text(encoding='utf-8-sig'));fpath=R/'bloodborne_dw/imported-full-map-v127.json';f=json.loads(fpath.read_text(encoding='utf-8-sig'));cpath=R/'bloodborne_dw/remaining-catalogue.json';cat=json.loads(cpath.read_text(encoding='utf-8-sig'));obj=next(r for r in cat['objects'] if r['path']=='source_deepslate');rows=[]
    for x in [0,90]:
        app={'model':'minecraft:block/deepslate_mirrored'}
        if x:app['x']=x
        apps=[app];assert not any(v.get('sourceClosedApplications')==apps for v in d['variants']);q=source_quads(source,apps);lo,hi=bounds_for_quads(q);offset=[0.,-float(lo[1]),0.];part={'model':export.export_application_model(app),'offset':offset,'yaw':0,'pitch':x};index=len(d['variants']);simple=simple_boxes(q,offset,True);visible=box(lo+offset,hi+offset);pose={'parts':[part],'collision':simple[0],'selection':simple[0]};v={'poses':{'closed':pose,'open':copy.deepcopy(pose)},'visibleBounds':visible,'sourcePlacementOffset':offset,'sourceClosedApplications':apps,'sourceOpenApplications':[],'sourceVariant':'Зеркальная текстура' if not x else 'Зеркальная текстура на боку','v127FullSourceSingleCell':True};d['variants'].append(v)
        actual=[(a-np.asarray(offset),uv,t,ti) for a,uv,t,ti in native.quads(part)];assert signature(q,source)==signature(actual,native)
        f['objects'].append({'registryId':d['id'],'variant':index,'rotations':[{'collision':simple[yaw],'aim':simple[yaw]} for yaw in range(8)],'sourceVariant':v['sourceVariant']});obj['variants'].append({'variant':index,'name':d['displayName'],'sourceModels':[app['model']]});rows.append({'registryId':d['id'],'variant':index,'sourceApplications':apps,'status':'PASS_EXACT_SOURCE_VERTICES_UV_RGBA_TINTINDEX','dependencyEvidence':'vanilla1.20.1deepslate_mirrored exists; bothPNGtextures are suppliedbyv15, so old SOURCE_DEFECT label is stale.'})
    write(p,d);write(fpath,f);write(cpath,cat);write(O/'nonfinite-source-fallback-proof.json',{'status':'PASS_EXACT_AUTHOR_TEXTURED_VANILLA_PARENT_SOURCE_POSES','rows':rows,'sourceCellCount':417,'separateVanillaFallbackRemoval':{'sourceModel':'minecraft:block/acacia_button','count':1,'reason':'Vanilla1.20.1buttonmodel/parent/acacia_planksPNGallunchanged byv15; removeperuserstylepolicy.'},'sourceWorldConverted':False})
    print(token({'poses':2,'sourceCells':417}))
if __name__=='__main__':main()
