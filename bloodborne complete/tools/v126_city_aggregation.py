"""Readonly source-owner aggregation mirroring approved V12.6 recipes.

Root UUID, source anchor, settings, mount and SourceShift remain stable. Every
consumed owner/source cell is recorded. Partial groups retain hidden frozen art.
No native world, source ZIP, production resource or prior report is modified.
"""
from __future__ import annotations
import collections, copy, hashlib, json, math
from pathlib import Path
import numpy as np
from v125_map_index import ROOT, MAP, PACK, dump, sha, token
from v125_map_resolve import rotation
from v125_map_door_projections import SourceVisuals
from v125_map_verify_extra_pose_uv import source_quads, signature
from v124_visual_audit import Visuals, rot

RES=ROOT/'src/architecture/resources'
SRC=ROOT/'docs/v12.6-city-review/map/citycandidate/city-final-source-data.json'
DEST=ROOT/'docs/v12.6/city'
RECIPES=RES/'bloodborne_dw/imported-assembly-v126.json'
VANILLA=Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar')


def pose(row):
    state=row['properties']
    if 'shape' in state and 'half' in state:
        index=['straight','inner_left','inner_right','outer_left','outer_right'].index(state['shape'])+(5 if state['half']=='top' else 0)
        if state.get('trim')=='true':index+=10
        return index
    return int(state.get('variant',0))+int(state.get('art_bank',0))*16


def shift(row):return row.get('payload',{}).get('SourceShift',row.get('sourceShift',[0.,0.,0.]))
def yaw(row):return int(row['properties'].get('rotation',0))
def vertical(row):return float(row.get('mountY',row.get('payload',{}).get('MountY',0)))+float(row.get('payload',{}).get('VerticalOffset',0))


def match(anchor,recipe,byposition,consumed):
    first=recipe['components'][0];anchorYaw=yaw(anchor);anchorShift=rotation(shift(anchor),anchorYaw)
    rows=[];ids=set()
    for component in recipe['components']:
        delta=int(component.get('componentYawDelta',0));relative=rotation(component['relativePosition'],anchorYaw)
        if any(abs(relative[i]-round(relative[i]))>1e-7 for i in [0,2]):return None
        xyz=tuple(anchor['sourcePosition'][i]+round(relative[i]) for i in range(3));row=byposition.get(xyz)
        if row is None or row['instanceUuid'] in consumed or row['instanceUuid'] in ids:return None
        if row['registryId']!=component['registryId'] or pose(row)!=component['pose'] or yaw(row)!=(anchorYaw+delta)%8:return None
        if any(row['properties'].get(p,'base' if p=='profile' else 'false')!=anchor['properties'].get(p,'base' if p=='profile' else 'false') for p in ['profile','open']):return None
        actual=rotation(shift(row),yaw(row));expectedlocal=rotation(component['sourceShift'],delta)
        expected=rotation([expectedlocal[i]-first['sourceShift'][i] for i in range(3)],anchorYaw)
        if any(abs(actual[i]-anchorShift[i]-expected[i])>1e-6 for i in [0,2]):return None
        if abs(actual[1]+vertical(row)-anchorShift[1]-vertical(anchor)-expected[1])>1e-6:return None
        rows.append(row);ids.add(row['instanceUuid'])
    return rows


def source_components(row):
    if row.get('sourceAssemblyProvenance'):
        return [part for old in row['sourceAssemblyProvenance'] for part in source_components(old)]
    return row.get('sourceComponentApplications',[row])


def descriptor(registry,cache):
    if registry not in cache:
        path=RES/'bloodborne_dw/composite'/(registry.split(':',1)[1]+'.json')
        cache[registry]=(json.loads(path.read_text(encoding='utf-8-sig')),path,sha(path))
    return cache[registry]


def target_geometry(row,native,cache,legacy):
    if row['registryId'].startswith('bloodborne_dw:prototype_wall'):
        # Exactly the same fourteen accepted V12.5 fixed-material0 rows; no
        # native model/state changes here. They are separately bound below.
        return None
    spec,path,digest=descriptor(row['registryId'],cache);index=pose(row)
    state=row['properties'];variant=spec['variants'][index]
    name='open' if state.get('open')=='true' and 'open' in variant['poses'] else 'closed'
    parts=variant['poses'][name]['parts'];selector=row.get('payload',{}).get('ImportedStairLegacyArt')
    if selector:
        art=legacy[selector]
        if art['registryId']==row['registryId'] and art['variant']==int(state.get('variant',0)):parts=art['parts']
    result=[];angle=-yaw(row)*45;worldshift=rotation(shift(row),yaw(row));worldshift[1]+=vertical(row)
    for part in parts:
        if part.get('emissive'):continue
        chosen={**part,'model':part.get('altModel',part['model'])} if state.get('profile')=='alt' else part
        for vertices,uv,texture,tint in native.quads(chosen):
            result.append((rot(vertices,1,angle,[8,8,8])+np.asarray(worldshift)*16,uv,texture,tint))
    return result


def verify_geometry(data,olddata,source,native,cache):
    legacy={r['key']:r for r in json.loads((RES/'bloodborne_dw/imported-stair-legacy-art.json').read_text())['states']}
    oldmap={tuple(r['sourcePosition']):r for r in olddata['owners']}
    checks=[];failures=[];signatures={};nativewalls=[];exceptions=[]
    for row in data['owners']:
        if row.get('sourceExplicitReplacement')=='EXPLICIT_90479_TO_90001':
            exceptions.append({'sourcePosition':row['sourcePosition'],'instanceUuid':row['instanceUuid'],
                'status':'USER_AUTHORIZED_DIFFERENT_FULL_DOOR_GEOMETRY','oldRegistryId':row['sourceAssemblyProvenance'][0]['registryId'],
                'targetRegistryId':row['registryId'],'sourceShiftAddition':[0,0,.375]});continue
        if row['registryId'].startswith('bloodborne_dw:prototype_wall'):
            old=oldmap[tuple(row['sourcePosition'])]
            assert row==old,'Protected V12.5 native-wall data changed'
            nativewalls.append({'sourcePosition':row['sourcePosition'],'instanceUuid':row['instanceUuid'],
                'registryId':row['registryId'],'properties':row['properties'],
                'status':'PASS_UNCHANGED_ACCEPTED_V125_SOURCE_NATIVE_WALL_ROW'});continue
        components=source_components(row)
        normalized=[{'delta':[part['sourcePosition'][i]-row['sourcePosition'][i] for i in range(3)],
                     'sourceApplications':part['sourceApplications']} for part in components]
        key=token({'registryId':row['registryId'],'properties':row['properties'],
            'sourceShift':shift(row),'vertical':vertical(row),'legacy':row.get('payload',{}).get('ImportedStairLegacyArt'),
            'source':normalized})
        if key not in signatures:
            original=[]
            for component,normalizedpart in zip(components,normalized):
                delta=np.asarray(normalizedpart['delta'])*16
                original.extend((v+delta,uv,t,ti) for v,uv,t,ti in source_quads(source,component['sourceApplications']))
            actual=target_geometry(row,native,cache,legacy)
            a,b=signature(original,source),signature(actual,native)
            signatures[key]={'status':'PASS_EXACT_SOURCE_WORLD_VERTICES_UV_RGBA_TINT' if a==b else 'FAIL_SOURCE_WORLD_VERTICES_UV_RGBA_TINT',
                'sourceQuads':sum(a.values()),'nativeQuads':sum(b.values()),
                'missingSurfaceSignatures':sum((a-b).values()),'extraSurfaceSignatures':sum((b-a).values())}
        result=signatures[key];check={'sourcePosition':row['sourcePosition'],'instanceUuid':row['instanceUuid'],
            'registryId':row['registryId'],'mainId':row['mainId'],'properties':row['properties'],
            'assemblyRecipe':row.get('sourceAssemblyRecipe'),**result};checks.append(check)
        if result['status'].startswith('FAIL'):failures.append(check)
    return {'status':'PASS_EXACT_SOURCE_CLOSED_GEOMETRY_UV_RGBA' if not failures else 'FAIL_EXACT_SOURCE_CLOSED_GEOMETRY_UV_RGBA',
        'ownersChecked':len(checks),'distinctTypedSourcePosesChecked':len(signatures),
        'sourceQuadSignaturesChecked':sum(v['sourceQuads'] for v in signatures.values()),
        'sourceOwnerChecks':checks,'failures':failures,'protectedNativeWallsUnchanged':nativewalls,
        'userAuthorizedGeometryReplacements':exceptions,
        'nativeValidation':'NOT_RUN_BY_OFFLINE_SOURCE_PROOF',
        'proofScope':'Each original source quad world vertex and UV association, complete texture RGBA and tintindex versus final closed native private models including SourceShift, legacy-art selectors, per-component yaw and mount. Fourteen preaccepted V12.5 native-wall rows preserved exactly; 90479 full-door replacement is explicit user-authorized geometry change.'}


def main():
    DEST.mkdir(parents=True,exist_ok=True)
    data=json.loads(SRC.read_text());olddata=copy.deepcopy(data);rules=json.loads(RECIPES.read_text())
    byposition={tuple(r['sourcePosition']):r for r in data['owners']};assert len(byposition)==len(data['owners'])
    consumed=set();whole=[];groups=[]
    for recipe in rules['recipes']:
        first=recipe['components'][0]
        for anchor in sorted(data['owners'],key=lambda r:tuple(r['sourcePosition'])):
            if anchor['instanceUuid'] in consumed or anchor['registryId']!=first['registryId'] or pose(anchor)!=first['pose']:continue
            parts=match(anchor,recipe,byposition,consumed)
            if parts is None:continue
            row=copy.deepcopy(anchor);row['registryId']=recipe['primaryRegistryId'];row['mainId']=recipe['primaryId']
            installation=recipe['installationPose'];row['properties']['variant']=str(installation%16)
            spec=json.loads((RES/'bloodborne_dw/composite'/(row['registryId'].split(':',1)[1]+'.json')).read_text())
            if len(spec['variants'])>16:row['properties']['art_bank']=str(installation//16)
            else:row['properties'].pop('art_bank',None)
            row['sourceLogicalPoseIndex']=installation;row['sourceAssemblyRecipe']=recipe['key']
            row['sourceAssemblyProvenance']=copy.deepcopy(parts)
            row['sourceComponentPositions']=[c['sourcePosition'] for p in parts for c in source_components(p)]
            row['sourceComponentApplications']=[copy.deepcopy(c) for p in parts for c in source_components(p)]
            row['consumedInstanceUuids']=[p['instanceUuid'] for p in parts]
            row['payload']['ImportedAssemblyV126']={'Version':1,'Recipe':recipe['key']}
            row['resolution']='EXACT_SOURCE_COMPLETE_APPROVED_V126_ASSEMBLY_WITH_ANCHOR_UUID_SETTINGS_MOUNT_AND_SHIFT_PRESERVED'
            consumed.update(row['consumedInstanceUuids']);whole.append(row)
            groups.append({'recipe':recipe['key'],'primaryId':row['mainId'],'registryId':row['registryId'],
                'anchorSourcePosition':anchor['sourcePosition'],'anchorInstanceUuid':anchor['instanceUuid'],
                'installationPose':installation,'sourceShift':shift(row),'mountY':row.get('mountY',0),
                'sourceComponentPositions':row['sourceComponentPositions'],'consumedInstanceUuids':row['consumedInstanceUuids'],
                'retainedProperties':{k:v for k,v in row['properties'].items() if k not in {'variant','art_bank'}},
                'componentYawStates':[yaw(p) for p in parts],
                'componentYawDeltas':[p.get('componentYawDelta',0) for p in recipe['components']]})
    remaining=[copy.deepcopy(r) for r in data['owners'] if r['instanceUuid'] not in consumed]
    replacement=rules['explicitDoorReplacement'];replaced=[]
    for row in remaining:
        if row['registryId']!=replacement['oldRegistryId']:continue
        old=copy.deepcopy(row);row['registryId']=replacement['targetRegistryId'];row['mainId']=replacement['targetId']
        row['properties']['variant']='0';row['properties'].pop('art_bank',None)
        addition=replacement['sourceShiftAddition'];newshift=[shift(old)[i]+addition[i] for i in range(3)]
        row['sourceShift']=newshift;row['payload']['SourceShift']=newshift
        row['payload']['ImportedAssemblyV126']={'Version':1,'Recipe':'EXPLICIT_90479_TO_90001'}
        row['sourceAssemblyProvenance']=[old];row['sourceExplicitReplacement']='EXPLICIT_90479_TO_90001'
        row['resolution']='USER_EXPLICIT_90479_FULL_DOOR90001_REPLACEMENT_WITH_SOURCE_SHIFT_Z_PLUS_0375'
        replaced.append({'sourcePosition':row['sourcePosition'],'instanceUuid':row['instanceUuid'],
            'oldRegistryId':old['registryId'],'newRegistryId':row['registryId'],'oldSourceShift':shift(old),'newSourceShift':newshift})
    owners=sorted(remaining+whole,key=lambda r:tuple(r['sourcePosition']))
    components={(c['registryId'],c['pose']) for r in rules['recipes'] for c in r['components']}
    unmatched=[{'sourcePosition':r['sourcePosition'],'relativePosition':r['relativePosition'],
        'instanceUuid':r['instanceUuid'],'mainId':r['mainId'],'registryId':r['registryId'],'pose':pose(r),
        'properties':r['properties'],'sourceShift':shift(r),
        'reason':'Exact approved whole group not present with required component pose/yaw/shift/mount, or cut by source crop. Frozen old art retained as hidden compatibility; no guessed replacement.'}
        for r in remaining if (r['registryId'],pose(r)) in components]
    # The short 90458 is explicitly retained as its own functional single door;
    # an unjoined occurrence is active, unlike a hidden old upper90923 fragment.
    standalone={("bloodborne_dw:source_crimson_top",0)}
    unmatched_active=[{**r,'reason':'User retains90458shortsingle-doorasindependentworkingtype; no exact approved tall assembly at same relative roots.'}
                      for r in unmatched if (r['registryId'],r['pose']) in standalone]
    unmatched=[r for r in unmatched if (r['registryId'],r['pose']) not in standalone]
    unmatched_evidence=[]
    for recipe in rules['recipes']:
        first=recipe['components'][0]
        for anchor in remaining:
            if anchor['registryId']!=first['registryId'] or pose(anchor)!=first['pose']:continue
            row={'recipe':recipe['key'],'anchorSourcePosition':anchor['sourcePosition'],
                 'anchorInstanceUuid':anchor['instanceUuid'],'anchorRotation':yaw(anchor),'components':[]}
            for component in recipe['components'][1:]:
                relative=rotation(component['relativePosition'],yaw(anchor))
                expectedroot=[anchor['sourcePosition'][i]+round(relative[i]) for i in range(3)]
                nearby=[]
                for candidate in remaining:
                    if candidate['registryId']!=component['registryId'] or pose(candidate)!=component['pose']:continue
                    delta=[candidate['sourcePosition'][i]-anchor['sourcePosition'][i] for i in range(3)]
                    if max(abs(delta[i]-relative[i]) for i in range(3))>2:continue
                    nearby.append({'sourcePosition':candidate['sourcePosition'],'instanceUuid':candidate['instanceUuid'],
                        'actualRelativePositionWorld':delta,'actualRelativePositionInAnchorAxes':rotation(delta,-yaw(anchor)),
                        'actualRotation':yaw(candidate),'expectedRotation':(yaw(anchor)+component.get('componentYawDelta',0))%8,
                        'sameExpectedRoot':candidate['sourcePosition']==expectedroot,'sourceShift':shift(candidate)})
                row['components'].append({'registryId':component['registryId'],'pose':component['pose'],
                    'expectedRelativePosition':component['relativePosition'],'expectedSourceRoot':expectedroot,
                    'nearbySameRegistryAndPose':nearby})
            unmatched_evidence.append(row)
    expected={tuple(r['sourcePosition']) for r in data['sourceApplications']};covered=set()
    for r in owners+data['sourceInvisibleServices']+data['sourceExceptions']:
        for xyz in r.get('sourceComponentPositions',[r['sourcePosition']]):
            assert tuple(xyz) not in covered,('Duplicate source coverage',xyz)
            covered.add(tuple(xyz))
    assert covered==expected
    prior=json.loads((ROOT/'docs/v12.5-city-review/citycandidate/city-final-source-data.json').read_text())
    sourcebypos={tuple(r['sourcePosition']):r for r in data['sourceApplications']}
    assert all(all(sourcebypos[tuple(r['sourcePosition'])][k]==r[k] for k in ['sourceState','sourceCarrier','sourceProperties','sourceApplications']) for r in prior['sourceApplications'])
    data['owners']=owners;data['status']='SOURCE_APPROVED_ASSEMBLIES_AGGREGATED_FINAL_JAR_AND_NATIVE_VALIDATION_PENDING'
    data['approvedAssemblyMigration']='APPLIED_EXACT_RECIPE_COMPONENT_POSE_YAW_SHIFT_MOUNT_MATCH'
    data['productionPoseApplication']='APPLIED_AND_SOURCE_POSE_PROOF_REQUIRED_AGAINST_FINAL_JAR'
    data['allSourceCellsClassified']=True;data['sourceCoverageCount']=len(covered)
    data['aggregatedAssemblies']=groups;data['explicitDoorReplacements']=replaced;data['unmatchedHiddenCompatibilityParts']=unmatched
    data['unmatchedActiveStandaloneParts']=unmatched_active
    data['unmatchedApprovedRecipeEvidence']=unmatched_evidence
    data['sourceAssemblyRecipeFile']=str(RECIPES.relative_to(ROOT));data['sourceAssemblyRecipeSha256']=sha(RECIPES)
    source=SourceVisuals();native=Visuals(vanilla_jar=VANILLA);descriptors={}
    proof=verify_geometry(data,olddata,source,native,descriptors)
    proof['assemblyRecipeSha256']=sha(RECIPES);proof['inputCitySha256']=sha(SRC)
    proof['descriptorBindings']={reg:{'file':str(path.relative_to(ROOT)),'sha256':digest} for reg,(_,path,digest) in sorted(descriptors.items())}
    dump(DEST/'source-current-descriptor-geometry-proof.json',proof)
    output=DEST/'city-final-aggregated.json';output.write_text(json.dumps(data,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8',newline='\n')
    summary={'schemaVersion':1,'status':data['status'],'sourceBoundsInclusive':data['rawBlocks']['boundsInclusive'],
        'sizeXYZ':data['rawBlocks']['dimensions'],'previousOwnerCount':len(olddata['owners']),'aggregatedOwnerCount':len(owners),
        'completeGroups':len(groups),'completeGroupCountsByRecipe':dict(collections.Counter(r['recipe'] for r in groups)),
        'consumedSourceOwners':len(consumed),'removedRedundantOwnerCount':len(olddata['owners'])-len(owners),
        'explicit90479To90001Replacements':len(replaced),'unmatchedHiddenCompatibilityParts':len(unmatched),
        'unmatchedActiveStandaloneObjects':len(unmatched_active),
        'unmatchedHiddenCountsById':dict(collections.Counter(r['mainId'] for r in unmatched)),
        'allSourceCellsClassified':True,'affectedSourceCoverageCells':len(covered),'previousApprovedSourceCellsUnchanged':True,
        'previousApprovedSourceCoverageCells':len(prior['sourceApplications']),'geometryProofStatus':proof['status'],
        'geometryProofFailureCount':len(proof['failures']),'dataFile':str(output),'dataSha256':sha(output),'dataBytes':output.stat().st_size,
        'mapSha256After':sha(MAP),'packSha256After':sha(PACK),'nativeValidation':'NOT_RUN'}
    dump(DEST/'city-final-aggregated-summary.json',summary)
    print(json.dumps({k:summary[k] for k in ['previousOwnerCount','aggregatedOwnerCount','completeGroups','completeGroupCountsByRecipe','explicit90479To90001Replacements','unmatchedHiddenCompatibilityParts','geometryProofStatus','geometryProofFailureCount','dataSha256']}))
    assert not proof['failures'],('Current closed geometry proof failed',len(proof['failures']))

if __name__=='__main__':main()
