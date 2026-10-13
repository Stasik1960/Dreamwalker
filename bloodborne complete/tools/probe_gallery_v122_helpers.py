"""Read-only preflight of new wrapper syntax and all preserved RP model bounds."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import runpy
import zipfile
from verify_gallery_v122 import model_rp_bounds
from world_io import RegionFile,Tag,compound
from verify_flat_gallery import entity_uuid,plain

ROOT=Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser();p.add_argument('--report',type=Path,required=True);a=p.parse_args()
    syntax=[]
    for name in ['build_gallery_v122_addon.py','prepare_gallery_v122_capture.py','collect_gallery_v122_client.py',
                 'prepare_gallery_v122_input.py','validate_gallery_v122_plan.py','validate_gallery_v122_labels.py','verify_gallery_v122.py','finalize_gallery_v122_world.py']:
        path=Path(__file__).with_name(name);runpy.run_path(str(path),run_name='gallery_v122_preflight');syntax.append(name)
    old_manifest=json.loads((ROOT/'build/flat-gallery/gallery-manifest.json').read_text(encoding='utf8'))
    before=json.loads((ROOT/'build/flat-gallery/disk-before.json').read_text(encoding='utf8'));world=Path(before['world'])
    assert world.is_relative_to((ROOT/'build').resolve())
    entities={}
    for path in sorted((world/'entities').glob('r.*.*.mca')):
        if path.stat().st_size:
            for chunk in RegionFile.open(path).chunks():
                for tag in compound(chunk.nbt().root).get('Entities',Tag(9,[],10)).value:entities[entity_uuid(tag)]=plain(tag)
    catalogue=json.loads((ROOT/'docs/gallery-v12.1/catalogue.json').read_text(encoding='utf8'));items=[]
    with zipfile.ZipFile(catalogue['productionJar'])as archive:
        for row in old_manifest['exhibits']:
            if row['kind']!='rp_object':continue
            bounds=model_rp_bounds(row,entities[row['uuid']],archive);previous=row['actualBounds']
            assert all(bounds[i]>=previous[i]-1e-5 and bounds[i+3]<=previous[i+3]+1e-5 for i in range(3)),row['key']
            assert bounds[3]>bounds[0] and bounds[4]>bounds[1] and bounds[5]>bounds[2]
            items.append({'key':row['key'],'sourceRenderedBounds':bounds,'oldPlatformY':64,
                          'oldRenderedBottomAboveFloor':bounds[1]-64,'tightBoundsWithinPriorConservativeVisualPhysicsBounds':True})
    assert len(items)==69
    result={'status':'PASS','newHelperSyntax':syntax,'preservedRpModelsRead':len(items),
            'scope':'Syntax + independent RP cube/bone/yaw/scale validation on previous saved gallery only, no game launch or modification',
            'sourceWorld':str(world),'items':items}
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

if __name__=='__main__':main()
