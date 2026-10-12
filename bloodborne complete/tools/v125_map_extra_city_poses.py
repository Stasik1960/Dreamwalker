"""Propose append-only exact one-source-block multipart poses; no production edit."""
from __future__ import annotations
import collections,copy,gzip,json,re
import numpy as np
from v125_map_index import ROOT,MAP,PACK,BASELINE,dump,sha,token,app_clean
from v125_map_resolve import Resolver,source_uuid
from v125_map_door_projections import SourceVisuals
from v124_visual_audit import Visuals,rot
from pathlib import Path
DEST=ROOT/'docs/v12.5-city-review/citycandidate'
RES=ROOT/'src/architecture/resources'

def sig(quads):return collections.Counter(tuple(sorted(tuple(round(float(x),6) for x in p) for p in q[0])) for q in quads)
def normalized(apps):
    # Rotating a UV-locked application outside its native bake would rotate its
    # locked UVs as well. Such rows retain source yaw until an exact UV proof.
    yaws=[0] if any(a.get('uvlock') for a in apps) else [0,2,4,6]
    options=[]
    for steps in yaws:
        values=[]
        for a in apps:
            c=app_clean(a);y=(c.get('y',0)-steps*45)%360
            if y:c['y']=y
            else:c.pop('y',None)
            values.append(c)
        values.sort(key=token);options.append((token(values),steps,values))
    return min(options,key=lambda x:x[0])
def parts(apps,offset):
    result=[];missing=[]
    for a in apps:
        ns,local=a['model'].split(':',1);base=f'remaining/source/{ns}/{local}'
        if a.get('uvlock') and (a.get('x',0) or a.get('y',0)):base=f'remaining/source_uvlock/{ns}/{local}_x{a.get("x",0)}_y{a.get("y",0)}'
        model='bloodborne_dw:'+base;file=RES/'assets/bloodborne_dw/models'/f'{base}.json'
        if not file.exists():missing.append(str(file.relative_to(RES)))
        result.append({'model':model,'offset':offset,'yaw':a.get('y',0),'pitch':a.get('x',0)})
    return result,missing

def main():
    city=json.loads(gzip.decompress((DEST/'city-source-data.json.gz').read_bytes()));resolver=Resolver();source=SourceVisuals();native=Visuals(vanilla_jar=Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'))
    carriers={}
    for path,spec in resolver.baseline['descriptors'].items():
        carrier=spec.get('source',{}).get('carrier')
        if carrier and not spec['source'].get('profileModel'):
            old='bloodborne_dw:'+path;m=resolver.maps.get((old,0));carriers[carrier]=m['newRegistryId'] if m else old
    groups={};walls=[];exceptions=[]
    for row in city['unresolvedSourceCells']:
        carrier=row['sourceCarrier'];props=row['sourceProperties'];apps=row['sourceApplications'];xyz=row['sourcePosition']
        if carrier=='minecraft:polished_deepslate_wall':
            materials={int(a['model'].rsplit('_',1)[1]) for a in apps if '_side_tall_' in a['model']};material=next(iter(materials),6)
            result={**row,'proposal':'PROTECTED_V12_EXISTING_STATE_ONLY','mainId':'90002','registryId':'bloodborne_dw:prototype_wall','properties':{**props,'material':str(material),'rotation':'0','profile':'base','connections':'manual'},'mountY':0.0,'sourceShift':[0.0,0.0,0.0],'instanceUuid':source_uuid('minecraft:overworld',xyz,'native-wall'),'sourceTallMaterial':material}
            if len(materials)>1:result['status']='UNRESOLVED_MULTIPLE_TALL_MATERIALS_ONE_NATIVE_STATE'
            elif material==1:result['status']='FAIL_EXACT_EQUIVALENCE_V12_MATERIAL1_CANONICAL_DIAGONAL_POST';result['reason']='PrototypeWallBlock.canonicalForm material1 forces diagonal rotation, up=true and removes all arms. Source voxel has cardinal tall1 arms; existing90012is the same forcedpost. V12 must not change.'
            else:
                targetparts=[]
                if props.get('up')=='true':targetparts.append({'model':'bloodborne_dw:block/wall/base/post','yaw':0})
                for direction,yaw in [('north',0),('east',90),('south',180),('west',270)]:
                    shape=props.get(direction,'none')
                    if shape!='none':targetparts.append({'model':'bloodborne_dw:block/wall/base/'+('low' if shape=='low' else 'tall_'+str(material)),'yaw':yaw})
                src=[q for a in apps for q in source.quads({'model':a['model'],'yaw':a.get('y',0),'pitch':a.get('x',0)})];dst=[q for p in targetparts for q in native.quads(p)]
                equal=sig(src)==sig(dst);result['status']='PASS_EXISTING_V12_NATIVE_PRIMITIVE_GEOMETRY' if equal else 'FAIL_EXISTING_V12_PRIMITIVE_GEOMETRY';result['sourceQuads']=len(src);result['nativeQuads']=len(dst);result['selectedExistingPrimitiveModels']=targetparts
                result['nativeBakeUvProof']='NOT_RUN_NATIVE; source/native wall LOWboth use UVlock=true; TALL/POSTfalse perPrototypeWallModels. Same frozen private source model/texture dependencies, runtime class/models not edited.'
                if material in {2,3,4,5,7}:result['intentionalRetirementNote']='Use an already supported material state of90002only; do not restore hidden/deletedskinregistry/item/cataloguenumber. Root must review source-state authoring against retirement policy.'
            walls.append(result);continue
        if carrier=='minecraft:jungle_trapdoor':exceptions.append({**row,'status':'EXPLICIT_INTENTIONALLY_DELETED_SOURCE_OBJECT_NOT_RESTORED','sourceException':'Registered damaged jungletrapdoor types were removed by user. Original source occurrence remains recorded; no fabricated visible replacement.'});continue
        if carrier=='minecraft:potted_spruce_sapling':exceptions.append({**row,'status':'EXPLICIT_MISSING_AUTHORED_SOURCE_MODEL_NOT_GUESSED'});continue
        reg=carriers.get(carrier)
        if not reg or not reg.startswith('bloodborne_dw:source_'):
            exceptions.append({**row,'status':'NO_EXISTING_NEW_CANONICAL_MULTIPART_TYPE'});continue
        key,yaw,canonical=normalized(apps)
        specfile=RES/'bloodborne_dw/composite'/f'{reg.split(":",1)[1]}.json';spec=json.loads(specfile.read_text());group=groups.setdefault(reg,{'primaryRegistryId':reg,'primaryId':resolver.ids[reg],'existingPoseCount':len(spec['variants']),'existingDescriptorSha256':sha(specfile),'sourceCarriers':set(),'poses':{},'policy':'Append source-supported one-voxel conditional unions only. Existing pose/model bytes retained; no multi-voxel object merge/installation item.'});group['sourceCarriers'].add(carrier)
        if key not in group['poses']:
            quads=[q for a in canonical for q in source.quads({'model':a['model'],'yaw':a.get('y',0),'pitch':a.get('x',0)})]
            vertices=np.asarray([q[0] for q in quads]).reshape(-1,3);lo=vertices.min(0);hi=vertices.max(0);offset=[0.0,round(-float(lo[1]),8),0.0];p,missing=parts(canonical,offset)
            group['poses'][key]={'canonicalSourceApplications':canonical,'floorNormalizationOffsetUnits':offset,'sourceShiftCompensationBlocks':[0.0,round(float(lo[1])/16,8),0.0],'sourceRawVisibleBoundsUnits':[float(v) for v in np.concatenate((lo,hi))],'proposedParts':p,'existingResourceDependenciesMissing':missing,'sourceOccurrences':[],'sourcePropertyStates':set(),'sourceCoords':[],'confidence':'EXACT_SELECTED_SOURCE_CONDITIONAL_UNION_ONE_BLOCK','nativeVisualCheck':'NOT_RUN'}
        pose=group['poses'][key];pose['sourceCoords'].append(xyz);pose['sourcePropertyStates'].add(row['sourceState']);pose['sourceOccurrences'].append({'sourcePosition':xyz,'relativePosition':row['relativePosition'],'sourceState':row['sourceState'],'sourceProperties':props,'sourceApplications':apps,'canonicalRotation':yaw})
    resultgroups=[];posedata=[]
    for reg,g in sorted(groups.items()):
        poses=[]
        for n,(signature,p) in enumerate(sorted(g['poses'].items())):
            index=g['existingPoseCount']+n;p['proposedLogicalPoseIndex']=index;p['proposedState']={'variant':str(index%16),'art_bank':str(index//16),'profile':'base','open':'false'};p['sourcePropertyStates']=sorted(p['sourcePropertyStates']);p['sourceOccurrenceCount']=len(p['sourceOccurrences']);poses.append(p)
        g['poses']=poses;g['sourceCarriers']=sorted(g['sourceCarriers']);g['newPoseCount']=len(poses);g['proposedTotalPoseCount']=g['existingPoseCount']+len(poses);g['within128PoseCap']=g['proposedTotalPoseCount']<=128;g['requiresArtworkBankProperty']=g['proposedTotalPoseCount']>16;resultgroups.append(g)
    proof={'schemaVersion':1,'status':'APPEND_ONLY_POSE_PROPOSAL_ROOT_REVIEW_REQUIRED','baselineCommit':BASELINE,'mapSha256':sha(MAP),'packSha256':sha(PACK),'cityDataSha256':sha(DEST/'city-source-data.json.gz'),'sourceBoundingBox':city['rawBlocks']['boundsInclusive'],'groups':resultgroups,'newOneBlockPoseCount':sum(g['newPoseCount'] for g in resultgroups),'representedSourceCellCount':sum(p['sourceOccurrenceCount'] for g in resultgroups for p in g['poses']),'capExceededGroups':[g['primaryRegistryId'] for g in resultgroups if not g['within128PoseCap']],'missingResourcePaths':sorted({f for g in resultgroups for p in g['poses'] for f in p['existingResourceDependenciesMissing']}),'protectedV12NativeWalls':walls,'protectedV12WallExactGeometryCount':sum(w['status'].startswith('PASS') for w in walls),'protectedV12WallUnresolvedCount':sum(not w['status'].startswith('PASS') for w in walls),'explicitSourceExceptions':exceptions,'noProductionModification':True,'noClaimOfNativeGameplay':'Proposed appended poses and existing native wall states require root review then actual source/client geometry+texture verification. Source code/models/IDs unchanged by this tool.'}
    dump(DEST/'extra-canonical-city-poses.json',proof)
    dump(DEST/'protected-v12-wall-equivalence.json',{'status':'PASS_GEOMETRY_WITH_EXPLICIT_UNRESOLVED' if all(w['status'].startswith('PASS') or 'MATERIAL1' in w['status'] for w in walls) else 'FAIL','rows':walls,'originalV12ModelsOrBehaviorChanged':False,'sourceWallCells':len(walls),'exactExistingGeometryCells':proof['protectedV12WallExactGeometryCount'],'unresolvedCells':proof['protectedV12WallUnresolvedCount']})

if __name__=='__main__':
    try:main()
    except BaseException:
        import traceback
        (DEST/'extra-poses-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
