"""Correct frozen city data against existing V12 migration; no production edit.

Do not rerun the append-pose proposal generator after its94poses were appended.
This changes only its protected wall dispositions and derives the finalcity file.
"""
import ast,copy,json,sys
from pathlib import Path
from v125_map_index import ROOT,MAP,PACK,dump,sha,token
from v125_map_door_projections import SourceVisuals
from v124_visual_audit import Visuals
from v125_map_extra_city_poses import canonical_wall_target,sig
from v125_map_final_city_data import main as finish_city

DEST=ROOT/'docs/v12.5-city-review/citycandidate'
WALL_DIR=ROOT/'src/architecture/java/dev/dreamwalker/bloodbornedw/architecture/wall'
REASON='DELETED_V12_WALL_VARIANT_NOT_RESTORED+CANNOT_PRESERVE_AFTER_REOPEN'

def dump(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')

def syntax_check():
    files=sorted((ROOT/'tools').glob('v125_map_*.py'))
    for path in files:ast.parse(path.read_text(encoding='utf-8-sig'),filename=str(path))
    dump(ROOT/'docs/v12.5-city-review/map/owned-script-syntax-final.json',{'status':'PASS_AST_PARSE','scripts':len(files),'paths':[str(p.relative_to(ROOT)) for p in files]})

def main():
    proposal_file=DEST/'extra-canonical-city-poses.json';city_file=DEST/'city-final-source-data.json'
    proposal=json.loads(proposal_file.read_text());old_city=json.loads(city_file.read_text())
    old_city_sha=sha(city_file);old_proposal_sha=sha(proposal_file);group_signature=token(proposal['groups'])
    previous_file=DEST/'city-native-wall-target-correction.json'
    previous=json.loads(previous_file.read_text()) if previous_file.exists() else {}
    source=SourceVisuals();native=Visuals(vanilla_jar=Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'))
    # These are source-code bindings, not an executed native BlockState test.
    code_paths=[WALL_DIR/'FixedMaterialWallBlock.java',WALL_DIR/'PrototypeWallBlock.java',WALL_DIR/'PrototypeWallArchitecture.java',WALL_DIR/'PrototypeWallModels.java',WALL_DIR.parent/'CatalogueMigration.java']
    code={p.name:p.read_text(encoding='utf-8-sig') for p in code_paths}
    assert 'new FixedMaterialWallBlock' in code['PrototypeWallArchitecture.java'] and 'prototype_wall_skin_' in code['PrototypeWallArchitecture.java']
    assert 'appendFixedProperties(builder)' in code['FixedMaterialWallBlock.java']
    assert '((FixedMaterialWallBlock)state.getBlock()).material()' in code['PrototypeWallBlock.java']
    assert 'if(material==0)return old' in code['CatalogueMigration.java']
    assert 'PrototypeWallBlock.material(state):0' in code['PrototypeWallModels.java']
    checks=[];walls=[];omissions=[]
    for old in proposal['protectedV12NativeWalls']:
        row=canonical_wall_target(old)
        if row['sourceTallMaterial']==0:
            assert row['registryId']=='bloodborne_dw:prototype_wall_skin_0' and 'material' not in row['properties']
            # Material0 is resolved identically for the old state and fixed block:
            # appearance art key0, same side/post/yaw/profile and same primitives.
            parts=row['selectedExistingPrimitiveModels']
            assert all(p['model'].split('/')[-1] in {'low','post','tall_0'} for p in parts)
            src=[q for a in row['sourceApplications'] for q in source.quads({'model':a['model'],'yaw':a.get('y',0),'pitch':a.get('x',0)})]
            dst=[q for p in parts for q in native.quads(p)]
            assert sig(src)==sig(dst),(row['sourcePosition'],'native_skin0geometry mismatch')
            row['fixedMaterialGeometryProof']='PASS_EXACT_SOURCE_VERTICES_AND_IDENTICAL_EXISTING_APPEARANCE_KEY_MATERIAL0'
            checks.append({'sourcePosition':row['sourcePosition'],'instanceUuid':row['instanceUuid'],'mainId':'90011','registryId':row['registryId'],'properties':row['properties'],'sourceQuadCount':len(src),'nativeQuadCount':len(dst),'selectedPrimitiveModels':parts,'status':row['fixedMaterialGeometryProof']})
        else:
            assert row['sourceTallMaterial'] in {3,4} and row['reason']==REASON
            omissions.append(copy.deepcopy(row))
        walls.append(row)
    assert len(checks)==14 and len(omissions)==2
    assert {tuple(r['sourcePosition']) for r in omissions}=={(160,72,-1086),(161,72,-1086)}
    proposal['protectedV12NativeWalls']=walls
    proposal['protectedV12WallExactGeometryCount']=14;proposal['protectedV12WallUnresolvedCount']=0;proposal['protectedV12WallExplicitOmissionCount']=2
    proposal['explicitSourceExceptions']=[r for r in proposal['explicitSourceExceptions'] if r.get('reason')!=REASON]+omissions
    assert len(proposal['explicitSourceExceptions'])==9 and token(proposal['groups'])==group_signature
    dump(proposal_file,proposal)
    dump(DEST/'protected-v12-wall-equivalence.json',{'schemaVersion':2,'status':'PASS_GEOMETRY_WITH_EXPLICIT_DELETED_VARIANT_OMISSIONS','rows':walls,'originalV12ModelsOrBehaviorChanged':False,'sourceWallCells':16,'exactExistingGeometryCells':14,'unresolvedCells':0,'explicitDeletedVariantOmissions':2,'checks':checks,'migrationSourceCodeBindings':{str(p.relative_to(ROOT)):sha(p) for p in code_paths},'testScope':'Original source quad vertices versus existing private primitives; fixed material0 produces the same appearance key and selected parts. CatalogueMigration material0 return path is verified from unchanged source code. Actual native client/server/save/reopen remains NOT_RUN here.'})
    finish_city();new_city=json.loads(city_file.read_text());old_by_uuid={r['instanceUuid']:r for r in old_city['owners']};new_by_uuid={r['instanceUuid']:r for r in new_city['owners']}
    removed=set(old_by_uuid)-set(new_by_uuid)
    assert len(removed) in {0,2} and not set(new_by_uuid)-set(old_by_uuid)
    assert len(new_city['owners'])==4575 and len(new_city['sourceExceptions'])==122 and new_city['sourceCoverageCount']==5146
    assert new_city['rawBlocks']==old_city['rawBlocks'] and new_city['passThroughBlocks']==old_city['passThroughBlocks']
    for uid,row in new_by_uuid.items():
        old=old_by_uuid[uid]
        if row['registryId']=='bloodborne_dw:prototype_wall_skin_0':
            assert old['registryId'] in {'bloodborne_dw:prototype_wall','bloodborne_dw:prototype_wall_skin_0'}
            assert row['sourcePosition']==old['sourcePosition'] and row['relativePosition']==old['relativePosition'] and row['sourceApplications']==old['sourceApplications']
        else:assert row==old,(uid,'unrelated owner changed')
    dump(DEST/'city-native-wall-target-correction.json',{'schemaVersion':1,'status':'PASS_DATA_GEOMETRY_AND_UUID_PRESERVATION','beforeCityDataSha256':previous.get('beforeCityDataSha256',old_city_sha),'afterCityDataSha256':sha(city_file),'beforeProposalSha256':previous.get('beforeProposalSha256',old_proposal_sha),'afterProposalSha256':sha(proposal_file),'sourceBoundsUnchanged':True,'rawDenseCellsUnchanged':True,'passThroughBlocksUnchanged':True,'allRetainedOwnerUuidsUnchanged':True,'unrelatedOwnersByteSemanticUnchanged':True,'fixedMaterial0Owners':14,'explicitDeletedWallOmissions':2,'ownerCount':4575,'sourceExceptions':122,'affectedSourceCoverageCells':5146,'appendedPoseGroupsUnchanged':True,'appendedPoseCount':94,'appendedSourceOccurrenceCount':413,'mapSha256After':sha(MAP),'packSha256After':sha(PACK),'originalV12ProductionChanges':False,'nativeValidation':'NOT_RUN_BY_THIS_TOOL'})
    print(json.dumps({'status':'PASS','citySha256':sha(city_file),'owners':4575,'exceptions':122,'fixedWalls':14,'omittedWalls':2}))

if __name__=='__main__':
    syntax_check()
    if '--syntax-only' not in sys.argv:main()
