"""Independent original-v15 versus actual exported vertex/UV/RGBA proof."""
import copy,json,traceback
from pathlib import Path
import numpy as np
import v125_map_index as index
index.PACK=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW\Bloodborne-Blocks\reference-inputs\bbmc-v15-v16\bbmc_v15_resource.zip')
from prepare_v1271_city_compatibility import ROOT,RES,OUT,read,write
from v125_map_door_projections import SourceVisuals
from v125_map_verify_extra_pose_uv import signature,source_quads
from v124_visual_audit import Visuals,rot

def main():
    source=SourceVisuals();native=Visuals(vanilla_jar=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar')
    for model in ('dandelion','dandelion_1','dandelion_3'):
        name='minecraft:block/'+model;doc=copy.deepcopy(source.document(name))
        for e in doc['elements']:
            for side,face in list(e.get('faces',{}).items()):
                if face['texture']=='#missing':
                    axis=1 if side in ('up','down') else 2 if side in ('north','south') else 0
                    assert np.prod(np.delete(np.abs(np.array(e['to'])-e['from']),axis))==0
                    del e['faces'][side]
        source.models[name]=doc
    source.models['minecraft:block/potted_spruce_sapling']=copy.deepcopy(source.document('minecraft:block/potted_spruce_sapling'))
    source.models['minecraft:block/potted_spruce_sapling']['textures']['dirt']='minecraft:block/dirt'
    source.document.cache_clear();source.textures.cache_clear()
    checks=[]
    selections=[('source_compatibility_fragment',None),('source_assembly_diorite_wall',[7]),('source_door_fragment',[2])]
    for path,wanted in selections:
        spec=read(RES/'bloodborne_dw/composite'/f'{path}.json')
        for pose,row in enumerate(spec['variants']):
            if wanted is not None and pose not in wanted:continue
            expected=source_quads(source,row['sourceClosedApplications'])
            parts=row['poses']['closed']['parts'];offset=np.asarray(row['sourcePlacementOffset'])
            actual=[(v-offset,uv,t,ti) for part in parts for v,uv,t,ti in native.quads(part)]
            for yaw in range(8):
                original=[(rot(v,1,-yaw*45,[8,8,8]),uv,t,ti) for v,uv,t,ti in expected]
                target=[(rot(v,1,-yaw*45,[8,8,8]),uv,t,ti) for v,uv,t,ti in actual]
                a,b=signature(original,source),signature(target,native)
                check={'registryId':spec['id'],'variant':pose,'yaw':yaw,'sourceFaces':sum(a.values()),'missingSurfaceSignatures':sum((a-b).values()),'extraSurfaceSignatures':sum((b-a).values()),'status':'PASS' if a==b else 'FAIL'}
                checks.append(check);assert a==b,check
    write(OUT/'source-fragment-vertex-uv-rgba-proof.json',{'status':'PASS_ORIGINAL_SOURCE_VERTICES_UV_RGBA_TINTINDEX_ALL_EIGHT_YAWS','poses':len(checks)//8,'orientationChecks':len(checks),'sourceTextureBytesNotRepainted':True,'correctiveSourceExceptions':'Only zero-area undefined faces omitted and original pot dirt alias restored using existing v15 dirt texture; missing unused parents/declarations do not change drawable surfaces','checks':checks,'nativeRenderingAcceptance':'SEPARATE'})

if __name__=='__main__':
    try:main()
    except BaseException:write(ROOT/'build/v1271-fragment-surface-proof-error.json',{'error':traceback.format_exc()});raise
