"""Read-only binding of the authored/saved city to its retained source IR."""
import collections
import hashlib
import json
from pathlib import Path
from v125_map_index import MAP, PACK

ROOT=Path(__file__).resolve().parents[1]
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def without_null_object_members(value):
    if isinstance(value,dict):return {k:without_null_object_members(v) for k,v in value.items() if v is not None}
    if isinstance(value,list):return [without_null_object_members(v) for v in value]
    return value

def main():
    planfile=ROOT/'docs/v12.6/city/city-final-aggregated.json'
    manifestfile=ROOT/'build/runtime-server-v126-flatgallery-author-2/catalogue-gallery-output.json'
    savedfile=ROOT/'docs/v12.6/validation/savedServerReopen.json'
    beforefile=ROOT/'docs/v12.6/validation/savedInitial.json'
    registrationfile=ROOT/'docs/v12.6/city/exact-jar-city-registration-audit.json'
    initialmissingfile=ROOT/'docs/v12.6/city/exact-jar-city-registration-initial-missing.json'
    plan,manifest,saved,before,registration=map(read,[planfile,manifestfile,savedfile,beforefile,registrationfile])
    city=manifest['city'];assert manifest['status']=='PASS_AUTHORED_REQUIRES_PRODUCTION_REOPEN'
    assert saved['status']==before['status']=='PASS' and saved['productionReopenCompared'] and saved['inputFilesUnchanged']
    assert len(city['placedOwners'])==len(city['owners'])==city['plannedOwnerCount']==len(plan['owners'])==8071
    byuuid={r['uuid']:r for r in city['placedOwners']};assert len(byuuid)==8071
    sourcebyuuid={r['instanceUuid']:r for r in city['owners']};assert len(sourcebyuuid)==8071
    for owner in plan['owners']:
        actual=sourcebyuuid[owner['instanceUuid']]
        assert all(actual[k]==v for k,v in owner.items()),('Source IR field changed',owner['instanceUuid'])
        placed=byuuid[owner['instanceUuid']]
        assert placed['registry']==placed['registryId']==owner['registryId']
        assert all(placed['placedState'][k]==v for k,v in owner['properties'].items()),owner['instanceUuid']
        assert placed['root']==[owner['relativePosition'][i]+city['translation'][i] for i in range(3)]
        assert placed['instanceUuid']==owner['instanceUuid']
    for key in ['sourceApplications','sourceInvisibleServices','sourceExceptions','passThroughBlocks']:
        # Gson's manifest writer omits null object members; absence means the
        # same unspecified value. Array positions and all non-null data remain.
        assert without_null_object_members(city[key])==without_null_object_members(plan[key]),key+' changed in authored manifest'
    assert city['rawBlocks']==plan['rawBlocks']
    assert city['rawBlocks']['dimensions']==[64,49,64]
    bounds=city['rawBlocks']['boundsInclusive']
    canonical=[bounds['min'],bounds['max']] if isinstance(bounds,dict) else [bounds[:3],bounds[3:]] if len(bounds)==6 else bounds
    assert canonical==[[136,64,-1086],[199,112,-1023]],bounds
    oldcrop=[[144,64,-1086],[175,112,-1055]]
    assert all(canonical[0][i]<=oldcrop[0][i]<=oldcrop[1][i]<=canonical[1][i] for i in range(3))
    priorfile=ROOT/'docs/v12.5-city-review/citycandidate/city-final-source-data.json'
    prior=read(priorfile);actualapps={tuple(r['sourcePosition']):r for r in city['sourceApplications']}
    assert len(prior['sourceApplications'])==5146
    for previous in prior['sourceApplications']:
        current=actualapps[tuple(previous['sourcePosition'])]
        assert all(current[k]==previous[k] for k in ['sourceState','sourceCarrier','sourceProperties','sourceApplications'])
    assert city['denseVolumeRatioToV125']==city['horizontalAreaRatioToV125']==4
    assert city['allSourceCellsClassified'] and city['sourceCoverageCount']==9122 and city['previousApprovedSourceCellsUnchanged']
    assert len(city['sourceInvisibleServices'])==878 and len(city['sourceExceptions'])==160 and len(city['passThroughBlocks'])==52
    services=[r for r in city['sourceInvisibleServices'] if r.get('status')=='EXPLICIT_PREEXISTING_SOURCE_TRANSPARENT_HELPER_AIR']
    assert len(services)==355 and collections.Counter(r['reservedNumber'] for r in services)=={'90535':271,'93191':84}
    assert all(r['nativeConversion']=='minecraft:air' and r['sourceTransparentExclusionEvidence']=='docs\\remaining-transfer\\source-transparent-exclusions.json' for r in services)
    missing=read(initialmissingfile)
    assert missing['productionJarSha256']==manifest['productionJarSha256']
    assert all(proof['alphaExtrema']==[0,0] for r in missing['missing'] for proof in r['actualReleasePngProofs'])
    overlap=city['initialOverlapRuntimeCheck'];assert overlap['status']=='PASS_BREAK_THEN_NORMAL_REPLACEMENT_REJECTED_RESTORED_INITIAL_UUID'
    assert overlap['scopeIsEmptyOutsideInitialPlacement'] and city['oneTimePrivilegeSerialized'] is False
    assert 'outcome=COMMITTED' not in overlap['normalRejection'] and 'collision_with_owned_object' in overlap['normalRejection']
    assert overlap['testedUuid'] in byuuid
    assert registration['status']=='PASS_ALL_CITY_OWNERS_RUNTIME_REGISTERED' and registration['missingOwnerCount']==0
    prodsha=manifest['productionJarSha256'];assert saved['productionJarSha256']==before['productionJarSha256']==registration['productionJarSha256']==prodsha
    assert registration['citySha256']==sha(planfile)
    assert sha(MAP)==plan['mapSha256'] and sha(PACK)==plan['packSha256']
    out={'schema':'dreamwalker-v126-actual-city-independent-validation','status':'PASS_ACTUAL_AUTHORED_SAVED_REOPENED_SOURCE_CITY',
         'productionJarSha256':prodsha,'cityPlanSha256':sha(planfile),'authorManifestSha256':sha(manifestfile),
         'savedInitialProofSha256':sha(beforefile),'savedServerReopenProofSha256':sha(savedfile),
         'previousApprovedSourcePlanSha256':sha(priorfile),
         'actualOwnerCount':8071,'actualRegistryTypes':len({r['registry'] for r in city['placedOwners']}),
         'rawSizeXYZ':[64,49,64],'rawDenseCells':64*49*64,'previousSizeXYZ':[32,49,32],
         'volumeAndHorizontalAreaRatio':4,'originalApprovedCropContained':True,'previousSourceApplicationsUnchanged':5146,
         'sourceCoverageCells':9122,'invisibleServicesAir':878,'alreadyExcludedTransparentHelpers':355,
         'transparentHelperCounts':{'90535':271,'93191':84},'explicitExceptions':160,'lightPassThrough':52,
         'approvedCompleteMountedDoorGroups':12,'retainedUnmatchedHiddenParts':5,
         'sourceIRFieldsUuidStateRegistryRootMountPayloadAndProvenance':'PASS_ALL_8071',
         'manifestNullPolicy':'Gson omitted null object members only; all non-null fields and array positions compared exactly',
         'savedUuidStateNbtOwnedCellsAndGeometry':'PASS_INDEPENDENT_DISK_PROOF_AFTER_PRODUCTION_REOPEN',
         'totalSavedGalleryObjectsVerified':saved['verifiedExhibits'],
         'initialOverlapRuntimeCheck':overlap,'privilegeSerialized':False,
         'ordinaryConflictingReinstallRejected':True,'originalMapSha256':sha(MAP),'originalPackSha256':sha(PACK),
         'originalMapAndPackUnchanged':True,'worldModifiedByThisValidation':False,'nativePixelReview':'SEPARATE_PENDING_REVIEW'}
    target=ROOT/'docs/v12.6/validation/cityActual.json';target.write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n')
    print(json.dumps(out,ensure_ascii=False))

if __name__=='__main__':main()
