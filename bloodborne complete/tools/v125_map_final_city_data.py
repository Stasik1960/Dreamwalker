"""Finish exact frozen-source crop placements using approved appended poses."""
import collections,copy,gzip,json
from v125_map_index import ROOT,MAP,PACK,BASELINE,dump,sha,token
from v125_map_resolve import source_uuid
DEST=ROOT/'docs/v12.5-city-review/citycandidate'
RES=ROOT/'src/architecture/resources'

def dump(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')

def main():
    data=json.loads(gzip.decompress((DEST/'city-source-data.json.gz').read_bytes()));proposal=json.loads((DEST/'extra-canonical-city-poses.json').read_text());applied=json.loads((DEST/'applied-city-poses.json').read_text());assert applied['appendedPoses']==proposal['newOneBlockPoseCount']==94
    retired=json.loads((ROOT/'docs/v12.5-city-review/style-retirement-confirmed.json').read_text(encoding='utf-8-sig'));retiredregs={r['registryId'] for r in retired['retirements']};owners=copy.deepcopy(data['owners']);extra=[];walls=[];omit=[]
    for group in proposal['groups']:
        reg=group['primaryRegistryId'];spec=json.loads((RES/'bloodborne_dw/composite'/f'{reg.split(":",1)[1]}.json').read_text());assert len(spec['variants'])==group['proposedTotalPoseCount']
        banked=len(spec['variants'])>16
        for pose in group['poses']:
            index=pose['proposedLogicalPoseIndex'];assert spec['variants'][index]['sourceClosedApplications']==pose['canonicalSourceApplications']
            for old in pose['sourceOccurrences']:
                props={**pose['proposedState'],'rotation':str(old['canonicalRotation'])}
                if not banked:props.pop('art_bank',None)
                shift=pose['sourceShiftCompensationBlocks'];payload={'ImportedTypeVersion':1}
                if any(v for v in shift):payload['SourceShift']=shift
                extra.append({**old,'registryId':reg,'mainId':group['primaryId'],'properties':props,'mountY':0.0,'sourceShift':shift,'payload':payload,'sourceCarrier':old['sourceState'].split('[',1)[0],'instanceUuid':source_uuid('minecraft:overworld',old['sourcePosition']),'sourceLogicalPoseIndex':index,'resolution':'EXACT_SOURCE_SINGLE_CELL_APPENDED_CANONICAL_POSE'})
    for wall in proposal['protectedV12NativeWalls']:
        if wall['status']=='EXPLICIT_DELETED_V12_WALL_VARIANT_NOT_RESTORED':
            assert any(r['sourcePosition']==wall['sourcePosition'] and r['reason']==wall['reason'] for r in proposal['explicitSourceExceptions'])
            continue
        assert wall['status'].startswith('PASS')
        if wall['sourceTallMaterial']==0:
            assert wall['registryId']=='bloodborne_dw:prototype_wall_skin_0' and wall['mainId']=='90011' and 'material' not in wall['properties']
        walls.append({**wall,'payload':{},'resolution':wall.get('resolution','EXACT_PROTECTED_V12_EXISTING_NATIVE_WALL_STATE_ONLY')})
    for row in owners+extra+walls:
        if row['registryId'] in retiredregs:omit.append({**row,'status':'EXPLICIT_V125_STYLE_RETIREMENT_SOURCE_CELL_OMITTED_NOT_RESTORED'})
    owners=[r for r in owners+extra+walls if r['registryId'] not in retiredregs]
    expected=set(tuple(r['sourcePosition']) for r in data['sourceApplications']);covered=set()
    for row in owners:
        for pos in row.get('sourceComponentPositions',[row['sourcePosition']]):assert tuple(pos) not in covered;covered.add(tuple(pos))
    services=[{**r,'nativeConversion':'minecraft:air','reason':'IndependentlyprovenallsourcefacePNGalpha0orzerorendergeometry;notstandardvisiblevanillaglass','status':'EXPLICIT_INVISIBLE_SERVICE_AIR'} for r in data['sourceInvisibleServices']]
    exclusions=copy.deepcopy(proposal['explicitSourceExceptions'])+omit
    for row in services+exclusions:
        for pos in row.get('sourceComponentPositions',[row['sourcePosition']]):assert tuple(pos) not in covered;covered.add(tuple(pos))
    assert covered==expected, 'Every affected source cell must have exactowner/explicitAIR/explicitexception'
    result={**data,'status':'READY_EXACT_SOURCE_CITY_WITH_EXPLICIT_OMISSIONS_NATIVE_VALIDATION_PENDING','owners':owners,'sourceInvisibleServices':services,'sourceExceptions':exclusions,'unresolvedSourceCells':[],'existingExactOwnerCount':len(data['owners']),'appendedExactOwnerCount':len(extra),'protectedOriginalV12NativeWallOwnerCount':len(walls),'styleRetiredOwnerCount':len(omit),'allSourceCellsClassified':True,'sourceCoverageCount':len(covered),'nativeValidation':'NOT_RUN','sourceUuidPolicy':'UUIDv5namespace86783c69-77d2-50cd-b6da-93882d539d99+dimension/originalXYZ/role; root positions stable, noliveentity/NPCcopy.'}
    out=DEST/'city-final-source-data.json';out.write_text(json.dumps(result,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8',newline='\n')
    summary={'schemaVersion':1,'status':result['status'],'dataFile':str(out),'dataSha256':sha(out),'dataBytes':out.stat().st_size,'sourceBoundsInclusive':data['rawBlocks']['boundsInclusive'],'sizeXYZ':data['rawBlocks']['dimensions'],'owners':len(owners),'exactExistingOwners':len(data['owners']),'appendedSourcePoseOwners':len(extra),'existingNativeV12WallOwners':len(walls),'invisibleServicesAir':len(services),'explicitOriginalSourceExceptions':len(proposal['explicitSourceExceptions']),'styleRetirementOmissions':len(omit),'passThroughBlocks':len(data['passThroughBlocks']),'rawDenseCells':len(data['rawBlocks']['denseIndices']),'affectedSourceCoverageCells':len(covered),'allSourceCellsClassified':True,'mapSha256After':sha(MAP),'packSha256After':sha(PACK),'ownerCountsByRegistry':dict(collections.Counter(r['registryId'] for r in owners))}
    dump(DEST/'city-final-source-data-summary.json',summary)

if __name__=='__main__':main()
