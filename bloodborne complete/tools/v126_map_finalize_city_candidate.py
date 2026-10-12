"""Classify every expanded source cell without modifying production assets.

Proposed source poses are encoded in the derived export and marked pending
application. Root must apply/rebind final assembly migrations and then verify
against the actual final production JAR before native world authoring.
"""
from __future__ import annotations
import argparse, collections, copy, gzip, json
from v125_map_index import ROOT, MAP, PACK, dump, sha, token
from v125_map_resolve import source_uuid

DEST=ROOT/'docs/v12.6-city-review/map/citycandidate'


def main():
    p=argparse.ArgumentParser();p.add_argument('--candidate',type=int,default=4);args=p.parse_args()
    sourcefile=DEST/f'city-source-data-{args.candidate:02d}.json.gz'
    proposalfile=DEST/f'extra-canonical-city-poses-{args.candidate:02d}.json'
    data=json.loads(gzip.decompress(sourcefile.read_bytes()));proposal=json.loads(proposalfile.read_text())
    retired=json.loads((ROOT/'src/architecture/resources/bloodborne_dw/imported-style-retirements.json').read_text(encoding='utf-8-sig'))
    transparentfile=ROOT/'docs/remaining-transfer/source-transparent-exclusions.json'
    transparent=json.loads(transparentfile.read_text(encoding='utf-8-sig'))
    transparentstates={(r['registryId'],r['variant']):r for r in transparent['entries']}
    retiredregs={r['registryId'] for r in retired.get('retirements',retired.get('entries',[]))}
    if not retiredregs:
        # The confirmed audit is the authoritative style retirement list, with
        # the same production helper manifest verified by the root build audit.
        retiredregs={r['registryId'] for r in json.loads((ROOT/'docs/v12.5-city-review/style-retirement-confirmed.json').read_text(encoding='utf-8-sig'))['retirements']}
    owners=copy.deepcopy(data['owners']);extra=[];native=[];services=copy.deepcopy(data['sourceInvisibleServices'])
    exceptions=copy.deepcopy(proposal['explicitSourceExceptions']);special=[]
    for group in proposal['groups']:
        for pose in group['poses']:
            shift=pose['sourceShiftCompensationBlocks']
            for occurrence in pose['sourceOccurrences']:
                props={**pose['proposedState'],'rotation':str(occurrence['canonicalRotation'])}
                if group['proposedTotalPoseCount']<=16:props.pop('art_bank',None)
                payload={'ImportedTypeVersion':1}
                if any(shift):payload['SourceShift']=shift
                extra.append({**occurrence,'registryId':group['primaryRegistryId'],'mainId':group['primaryId'],
                    'properties':props,'mountY':0.,'sourceShift':shift,'payload':payload,
                    'sourceCarrier':occurrence['sourceState'].split('[',1)[0],
                    'instanceUuid':source_uuid('minecraft:overworld',occurrence['sourcePosition']),
                    'sourceLogicalPoseIndex':pose['proposedLogicalPoseIndex'],
                    'resolution':'EXACT_SOURCE_SINGLE_CELL_PROPOSED_CANONICAL_POSE_PENDING_APPLICATION',
                    'productionPoseApplication':'PENDING_ROOT_APPENDING_AND_FINAL_JAR_VERIFICATION'})
    for row in proposal['protectedV12NativeWalls']:
        if row['status'].startswith('PASS'):
            native.append({**row,'payload':{},'resolution':row.get('resolution','EXACT_PROTECTED_V12_EXISTING_NATIVE_WALL_STATE_ONLY')})
        elif not row['status'].startswith('EXPLICIT'):
            special.append(row)
    empty_evidence=json.loads((DEST/'empty-source-states.json').read_text())
    bycarrier={r['carrier']:r for r in empty_evidence['rows']}
    for row in proposal['specialUnresolvedSourceRows']:
        if not row['sourceApplications'] and row['sourceCarrier']=='minecraft:red_nether_brick_wall':
            evidence=bycarrier[row['sourceCarrier']]
            assert evidence['sourceJson'].get('multipart') and not evidence['selectedApplications']
            services.append({**row,'resolution':'SOURCE_MULTIPART_NO_ACTIVE_APPLICATIONS',
                'nativeConversion':'minecraft:air','status':'EXPLICIT_INVISIBLE_SERVICE_AIR',
                'sourceBlockstateJsonSha256':evidence['sourceJsonSha256'],
                'sourceServiceReason':'Original multipart has only tall side predicates; saved state has low sides and no post, so no source draw applications apply.'})
        elif not row['sourceApplications'] and row['sourceCarrier']=='minecraft:acacia_planks':
            evidence=bycarrier[row['sourceCarrier']]
            assert set(evidence['sourceJson']['variants'])=={'facing=east','facing=north','facing=south','facing=west'}
            exceptions.append({**row,'status':'EXPLICIT_SOURCE_BLOCKSTATE_INVALID_PROPERTY_VARIANTS_NOT_GUESSED',
                'nativeConversion':'minecraft:air','sourceBlockstateJsonSha256':evidence['sourceJsonSha256'],
                'sourceException':'Original acacia_planks has no facing state property; resource blockstate incorrectly provides only facing variants. No authored branch can be selected for this saved source state. Do not guess bell/lectern or insert visible vanilla fallback.'})
        else:special.append(row)
    assert not special, f'Expanded city has {len(special)} remaining unclassified source rows'
    style=[];active=[]
    transparentservices=[]
    for row in owners+extra+native:
        poseindex=int(row['properties'].get('variant',0))+16*int(row['properties'].get('art_bank',0))
        if (row['registryId'],poseindex) in transparentstates:
            evidence=transparentstates[row['registryId'],poseindex]
            assert set(a['model'] for a in row['sourceApplications'])<=set(evidence['sourceModels'])
            assert all(t['wholeImageAlphaExtrema']==[0,0] for t in evidence['textures'])
            service={**row,'status':'EXPLICIT_PREEXISTING_SOURCE_TRANSPARENT_HELPER_AIR',
                'nativeConversion':'minecraft:air','resolution':'SOURCE_TRANSPARENT_EXCLUSION_RELEASE_ALPHA_CONFIRMED',
                'sourceServiceReason':transparent['reason'],
                'sourceTransparentExclusionEvidence':str(transparentfile.relative_to(ROOT)),
                'sourceTransparentExclusionEvidenceSha256':sha(transparentfile),
                'excludedRegistryId':row['registryId'],'reservedNumber':evidence['reservedNumber'],
                'physicalPolicy':'No source carrier physics guessed or restored for excluded invisible source helper.'}
            transparentservices.append(service);services.append(service)
        elif row['registryId'] in retiredregs:
            style.append({**row,'status':'EXPLICIT_V125_STYLE_RETIREMENT_SOURCE_CELL_OMITTED_NOT_RESTORED','nativeConversion':'minecraft:air'})
        else:active.append(row)
    exceptions.extend(style)
    expected={tuple(r['sourcePosition']) for r in data['sourceApplications']};covered=set()
    for rows in [active,services,exceptions]:
        for row in rows:
            for xyz in row.get('sourceComponentPositions',[row['sourcePosition']]):
                assert tuple(xyz) not in covered, ('duplicate source coverage',xyz)
                covered.add(tuple(xyz))
    assert covered==expected, ('unclassified source coordinates',len(expected-covered),'extra',len(covered-expected))
    old=json.loads((ROOT/'docs/v12.5-city-review/citycandidate/city-final-source-data.json').read_text())
    originals={tuple(r['sourcePosition']):r for r in old['sourceApplications']}
    actual={tuple(r['sourcePosition']):r for r in data['sourceApplications']}
    assert all(xyz in actual and all(actual[xyz][k]==row[k] for k in ['sourceState','sourceCarrier','sourceProperties','sourceApplications']) for xyz,row in originals.items())
    result={**data,'status':'SOURCE_CLASSIFIED_EXACT_APPEND_POSES_AND_ASSEMBLY_MIGRATION_PENDING',
        'owners':active,'sourceInvisibleServices':services,'sourceExceptions':exceptions,'unresolvedSourceCells':[],
        'existingExactOwnerCount':len(owners),'appendedExactOwnerCount':len(extra),
        'protectedOriginalV12NativeWallOwnerCount':len(native),'styleRetiredOwnerCount':len(style),
        'preexistingTransparentHelperServiceCount':len(transparentservices),
        'allSourceCellsClassified':True,'sourceCoverageCount':len(covered),'nativeValidation':'NOT_RUN',
        'productionPoseApplication':'PENDING_ROOT_APPENDING_AND_FINAL_JAR_VERIFICATION',
        'approvedAssemblyMigration':'PENDING_FINAL_V126_RECIPES_AND_ID_ASSIGNMENT',
        'previousApprovedSourceCellsUnchanged':True,
        'sourceUuidPolicy':'Stable UUIDv5 of original dimension/XYZ/role, identical to V12.5 for retained existing source owners. No live entity/NPC data copied.'}
    output=DEST/'city-final-source-data.json'
    output.write_text(json.dumps(result,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8',newline='\n')
    summary={'schemaVersion':1,'status':result['status'],'sourceBoundsInclusive':data['rawBlocks']['boundsInclusive'],
        'sizeXYZ':data['rawBlocks']['dimensions'],'horizontalAreaRatioToV125':4.,'denseVolumeRatioToV125':4.,
        'owners':len(active),'exactExistingOwners':len(owners),'appendedSourcePoseOwners':len(extra),
        'existingNativeV12WallOwners':len(native),'invisibleServicesAir':len(services),
        'preexistingTransparentHelperServices':len(transparentservices),
        'transparentHelperCountsByReservedId':dict(collections.Counter(r['reservedNumber'] for r in transparentservices)),
        'explicitOriginalSourceExceptions':len(exceptions)-len(style),'styleRetirementOmissions':len(style),
        'sourceExceptions':len(exceptions),'passThroughBlocks':len(data['passThroughBlocks']),
        'rawDenseCells':len(data['rawBlocks']['denseIndices']),'affectedSourceCoverageCells':len(covered),
        'sourceBlockEntities':len(data['rawBlocks']['blockEntities']),'allSourceCellsClassified':True,
        'previousApprovedSourceCellsUnchanged':True,'previousApprovedSourceCoverageCells':len(originals),
        'dataFile':str(output),'dataSha256':sha(output),'dataBytes':output.stat().st_size,
        'proposalFile':str(proposalfile),'proposalSha256':sha(proposalfile),'proposedAdditionalPoses':proposal['newOneBlockPoseCount'],
        'bankLimitRequired':9,'capExceededGroups':proposal['capExceededGroups'],
        'mapSha256After':sha(MAP),'packSha256After':sha(PACK),
        'ownerCountsByRegistry':dict(collections.Counter(r['registryId'] for r in active)),
        'exceptionCountsByStatus':dict(collections.Counter(r['status'] for r in exceptions)),
        'nativeValidation':'NOT_RUN','productionPoseApplication':result['productionPoseApplication'],
        'approvedAssemblyMigration':result['approvedAssemblyMigration']}
    dump(DEST/'city-final-source-data-summary.json',summary)
    print(json.dumps({k:summary[k] for k in ['owners','invisibleServicesAir','sourceExceptions','styleRetirementOmissions','explicitOriginalSourceExceptions','passThroughBlocks','affectedSourceCoverageCells','proposedAdditionalPoses','dataSha256']}))

if __name__=='__main__':main()
