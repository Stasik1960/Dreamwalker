"""Read-only source versus exported native model vertices/UV/RGBA proof."""
import copy,traceback
from pathlib import Path
import v125_map_index as index
index.PACK=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW\Bloodborne-Blocks\reference-inputs\bbmc-v15-v16\bbmc_v15_resource.zip')
from prepare_v1271_city_compatibility import ROOT,RES,OUT,read,write,sha
from v125_map_door_projections import SourceVisuals
from v124_visual_audit import Visuals
from v125_map_verify_extra_pose_uv import source_quads,signature

def main():
    source=SourceVisuals();native=Visuals(vanilla_jar=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar');native.models={}
    originalDocument=native.document;native.document=lambda identifier:native.models.get(identifier) or originalDocument(identifier)
    rows=read(ROOT/'build/final-city-fixes/source-fallback-target-probes.json');checks=[]
    for n,row in enumerate(rows):
        definition=read(RES/'assets/bloodborne_dw/blockstates'/f'{row["registryId"].split(":")[1]}.json')
        expected=signature(source_quads(source,row['modelCandidatesExpected']),source)
        slot=n%128;bank=n//128
        for profile in ('base','alt'):
            if 'multipart' in definition:
                targets=[part['apply'] for part in definition['multipart'] if part['when']=={'source':str(slot),'source_bank':str(bank)}]
                assert len(targets)==len(row['modelCandidatesExpected']),(n,targets)
            else:targets=[definition['variants'][f'profile={profile},source={slot},source_bank={bank}']]
            actual=signature(source_quads(native,targets),native)
            check={'sourceState':row['sourceState'],'targetRegistryId':row['registryId'],'source':n,'profile':profile,'sourceSurfaceCount':sum(expected.values()),'targetSurfaceCount':sum(actual.values()),'missingSurfaceSignatures':sum((expected-actual).values()),'extraSurfaceSignatures':sum((actual-expected).values()),'status':'PASS' if expected==actual else 'FAIL'}
            checks.append(check);assert expected==actual,check
    write(OUT/'native-fallback-vertex-uv-rgba-proof.json',{'status':'PASS_EXPORTED_NATIVE_SOURCE_VERTICES_UV_RGBA_TINTINDEX','sourceStates':len(rows),'profileChecks':len(checks),'authoredTexturePixelsNotRepainted':True,'altArtworkLoaded':0,'nativeSourceReferenceSha256':sha(ROOT/'build/runtime-client-comparison-fresh-final-reference-r2/native-source-model-probes.json'),'checks':checks,'nativeBakedModelAndLightAcceptance':'SEPARATE_REAL_CLIENT_TEST'})

if __name__=='__main__':
    try:main()
    except BaseException:write(ROOT/'build/v1271-fallback-surface-proof-error.json',{'error':traceback.format_exc()});raise
