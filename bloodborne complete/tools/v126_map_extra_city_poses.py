"""Propose exact missing conditional poses for the expanded source city only.

No production edits. Existing canonical poses are resolved first by the export;
remaining source-supported one-voxel unions are proposed for append or explicit
root handling. Multi-cell merged construction/door rules are supplied later.
"""
from __future__ import annotations
import argparse, collections, copy, gzip, json
from pathlib import Path
import numpy as np
from v125_map_index import ROOT, MAP, PACK, dump, sha
from v125_map_resolve import Resolver, source_uuid
from v125_map_extra_city_poses import normalized, parts, canonical_wall_target, sig
from v125_map_door_projections import SourceVisuals
from v124_visual_audit import Visuals

DEST=ROOT/'docs/v12.6-city-review/map/citycandidate'
RES=ROOT/'src/architecture/resources'


def main():
    p=argparse.ArgumentParser();p.add_argument('--candidate',type=int,default=1);args=p.parse_args()
    file=DEST/f'city-source-data-{args.candidate:02d}.json.gz'
    city=json.loads(gzip.decompress(file.read_bytes()));resolver=Resolver()
    source=SourceVisuals();native=Visuals(vanilla_jar=Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'))
    carriers={}
    for path,spec in resolver.baseline['descriptors'].items():
        carrier=spec.get('source',{}).get('carrier')
        if carrier and not spec['source'].get('profileModel'):
            old='bloodborne_dw:'+path;mapping=resolver.maps.get((old,0))
            carriers[carrier]=mapping['newRegistryId'] if mapping else old
    groups={};walls=[];exceptions=[];special=[]
    for row in city['unresolvedSourceCells']:
        carrier=row['sourceCarrier'];props=row['sourceProperties'];apps=row['sourceApplications'];xyz=row['sourcePosition']
        if carrier=='minecraft:polished_deepslate_wall':
            materials={int(a['model'].rsplit('_',1)[1]) for a in apps if '_side_tall_' in a['model']}
            material=next(iter(materials),6)
            result={**row,'proposal':'PROTECTED_V12_EXISTING_STATE_ONLY','mainId':'90002',
                'registryId':'bloodborne_dw:prototype_wall','properties':{**props,'material':str(material),'rotation':'0','profile':'base','connections':'manual'},
                'mountY':0.,'sourceShift':[0.,0.,0.],
                'instanceUuid':source_uuid('minecraft:overworld',xyz,'native-wall'),'sourceTallMaterial':material}
            if len(materials)>1:
                result['status']='UNRESOLVED_MULTIPLE_TALL_MATERIALS_ONE_NATIVE_STATE'
            elif material==1:
                result['status']='EXPLICIT_PROTECTED_V12_MATERIAL1_SOURCE_GEOMETRY_UNREPRESENTABLE'
                result['reason']='PROTECTED_V12_MATERIAL1_FORCES_DIAGONAL_POST_REMOVING_CARDINAL_ARMS'
                result['nativeConversion']='minecraft:air'
            else:
                targetparts=[]
                if props.get('up')=='true':targetparts.append({'model':'bloodborne_dw:block/wall/base/post','yaw':0})
                for direction,yaw in [('north',0),('east',90),('south',180),('west',270)]:
                    shape=props.get(direction,'none')
                    if shape!='none':targetparts.append({'model':'bloodborne_dw:block/wall/base/'+('low' if shape=='low' else 'tall_'+str(material)),'yaw':yaw})
                src=[q for a in apps for q in source.quads({'model':a['model'],'yaw':a.get('y',0),'pitch':a.get('x',0)})]
                dst=[q for part in targetparts for q in native.quads(part)]
                equal=sig(src)==sig(dst);result['status']='PASS_EXISTING_V12_NATIVE_PRIMITIVE_GEOMETRY' if equal else 'FAIL_EXISTING_V12_PRIMITIVE_GEOMETRY'
                result['sourceQuads']=len(src);result['nativeQuads']=len(dst);result['selectedExistingPrimitiveModels']=targetparts
                result['nativeBakeUvProof']='NOT_RUN_BY_OFFLINE_GEOMETRY_PROOF'
            result=canonical_wall_target(result);walls.append(result)
            if result['status'].startswith('EXPLICIT'):exceptions.append(copy.deepcopy(result))
            elif not result['status'].startswith('PASS'):special.append(copy.deepcopy(result))
            continue
        if carrier=='minecraft:jungle_trapdoor':
            exceptions.append({**row,'status':'EXPLICIT_INTENTIONALLY_DELETED_SOURCE_OBJECT_NOT_RESTORED','nativeConversion':'minecraft:air'});continue
        if carrier=='minecraft:potted_spruce_sapling':
            exceptions.append({**row,'status':'EXPLICIT_MISSING_AUTHORED_SOURCE_MODEL_NOT_GUESSED','nativeConversion':'minecraft:air'});continue
        reg=carriers.get(carrier)
        if not reg or not reg.startswith('bloodborne_dw:source_'):
            special.append({**row,'status':'NO_EXISTING_NEW_CANONICAL_MULTIPART_TYPE','candidateCanonicalRegistry':reg});continue
        key,yaw,canonical=normalized(apps);specfile=RES/'bloodborne_dw/composite'/f'{reg.split(":",1)[1]}.json'
        spec=json.loads(specfile.read_text());group=groups.setdefault(reg,{'primaryRegistryId':reg,'primaryId':resolver.ids[reg],
            'existingPoseCount':len(spec['variants']),'existingDescriptorSha256':sha(specfile),'sourceCarriers':set(),'poses':{},
            'policy':'Source-supported one-voxel conditional union only. Existing source geometry/UV unchanged; no multi-voxel construction merge performed here.'})
        group['sourceCarriers'].add(carrier)
        if key not in group['poses']:
            quads=[q for a in canonical for q in source.quads({'model':a['model'],'yaw':a.get('y',0),'pitch':a.get('x',0)})]
            if not quads:
                special.append({**row,'status':'SOURCE_MODEL_HAS_NO_FACES_REQUIRES_EXPLICIT_SERVICE_DISPOSITION'});continue
            vertices=np.asarray([q[0] for q in quads]).reshape(-1,3);lo=vertices.min(0);hi=vertices.max(0)
            offset=[0.,round(-float(lo[1]),8),0.];targetparts,missing=parts(canonical,offset)
            group['poses'][key]={'canonicalSourceApplications':canonical,'floorNormalizationOffsetUnits':offset,
                'sourceShiftCompensationBlocks':[0.,round(float(lo[1])/16,8),0.],
                'sourceRawVisibleBoundsUnits':[float(v) for v in np.concatenate((lo,hi))],
                'proposedParts':targetparts,'existingResourceDependenciesMissing':missing,
                'sourceOccurrences':[],'sourcePropertyStates':set(),'sourceCoords':[],
                'confidence':'EXACT_SELECTED_SOURCE_CONDITIONAL_UNION_ONE_BLOCK','nativeVisualCheck':'NOT_RUN'}
        pose=group['poses'][key];pose['sourceCoords'].append(xyz);pose['sourcePropertyStates'].add(row['sourceState'])
        pose['sourceOccurrences'].append({'sourcePosition':xyz,'relativePosition':row['relativePosition'],
            'sourceState':row['sourceState'],'sourceProperties':props,'sourceApplications':apps,'canonicalRotation':yaw})
    resultgroups=[]
    for reg,group in sorted(groups.items()):
        poses=[]
        for n,(_,pose) in enumerate(sorted(group['poses'].items())):
            index=group['existingPoseCount']+n;pose['proposedLogicalPoseIndex']=index
            pose['proposedState']={'variant':str(index%16),'art_bank':str(index//16),'profile':'base','open':'false'}
            pose['sourcePropertyStates']=sorted(pose['sourcePropertyStates']);pose['sourceOccurrenceCount']=len(pose['sourceOccurrences']);poses.append(pose)
        group['poses']=poses;group['sourceCarriers']=sorted(group['sourceCarriers']);group['newPoseCount']=len(poses)
        group['proposedTotalPoseCount']=group['existingPoseCount']+len(poses);group['within128PoseCap']=group['proposedTotalPoseCount']<=128
        group['requiresArtworkBankProperty']=group['proposedTotalPoseCount']>16;resultgroups.append(group)
    report={'schemaVersion':1,'status':'APPEND_ONLY_SOURCE_POSE_PROPOSAL_NO_PRODUCTION_WRITE',
        'mapSha256':sha(MAP),'packSha256':sha(PACK),'cityDataSha256':sha(file),'sourceBoundingBox':city['rawBlocks']['boundsInclusive'],
        'groups':resultgroups,'newOneBlockPoseCount':sum(g['newPoseCount'] for g in resultgroups),
        'representedSourceCellCount':sum(p['sourceOccurrenceCount'] for g in resultgroups for p in g['poses']),
        'capExceededGroups':[g['primaryRegistryId'] for g in resultgroups if not g['within128PoseCap']],
        'missingResourcePaths':sorted({f for g in resultgroups for p in g['poses'] for f in p['existingResourceDependenciesMissing']}),
        'protectedV12NativeWalls':walls,'protectedV12WallMaterialCounts':dict(collections.Counter(w['sourceTallMaterial'] for w in walls)),
        'protectedV12WallExactGeometryCount':sum(w['status'].startswith('PASS') for w in walls),
        'protectedV12WallExplicitOmissionCount':sum(w['status'].startswith('EXPLICIT') for w in walls),
        'explicitSourceExceptions':exceptions,'specialUnresolvedSourceRows':special,
        'passThroughCarrierCounts':dict(collections.Counter(r['registryId'] for r in city['passThroughBlocks'])),
        'sourceBlockEntities':city['rawBlocks']['blockEntities'],'nativeValidation':'NOT_RUN'}
    dump(DEST/f'extra-canonical-city-poses-{args.candidate:02d}.json',report)
    print(json.dumps({k:report[k] for k in ['newOneBlockPoseCount','representedSourceCellCount','capExceededGroups','missingResourcePaths','protectedV12WallMaterialCounts','protectedV12WallExactGeometryCount','protectedV12WallExplicitOmissionCount','passThroughCarrierCounts']}))
    print(json.dumps({'specialUnresolvedRows':len(special),'specialCarriers':dict(collections.Counter(r['sourceCarrier'] for r in special))}))

if __name__=='__main__':main()
