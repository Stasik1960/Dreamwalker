"""Author the one affected door-bridge continuation; no world/DB/JVM access.

The prior real AIR motion remains inherited evidence. The ordinary item's
installed state/payload, never a source-conversion privilege, defines setup.
Run through Guard.ps1 -Mode Operation. --proof-only needs no assembly manifest.
"""
from __future__ import annotations
import argparse, base64, copy, hashlib, json, math, struct, zipfile
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
ROOT = PROJECT.parent
HERE = ROOT / 'research' / 'function-qa'
RESOURCES = PROJECT / 'src' / 'architecture' / 'resources'
ID = 'bloodborne_dw:source_dark_arch_door'
KEY = 'source-parts:' + ID + ':0'
PRODUCTION = '3477fa1f016126eb58ea294a37a0ba06ad711f7e137ae72b103919df45e35bd3'
PRIOR_PLAN = '87075dccc42d30ef6429e2bc9b9664b126bf6539a7a78b16d6e0a2119ba75354'
PRIOR_REPORT = 'cbe2eaa4b26bf163b728ebc2e955afcc154a44c539f99a996df5c2a1af0876e7'
AIR_NAMES = {'actual selected door part handles right-click',
             'door right click changes one whole door open state',
             'door open collision moves with the leaf',
             'door second right click restores closed-state exact collision'}

def require(condition, message):
    if not condition: raise RuntimeError(message)
def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def read(path): return json.loads(Path(path).read_text(encoding='utf-8-sig'))
def binding(path): return {'path': str(Path(path).absolute()), 'sha256': sha(path)}
def write_new(path, value):
    require(not path.exists(), 'Refuse to replace evidence: ' + str(path))
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
def air_check(row): return row.get('name') in AIR_NAMES

def payload_ints(encoded):
    """This observed payload has exactly two INT tags; reject every other form."""
    data = base64.b64decode(encoded, validate=True)
    require(data[:3] == b'\x0a\x00\x00', 'Expected the actual unnamed NBT compound')
    result = {}; at = 3
    while data[at] != 0:
        require(data[at] == 3, 'Unexpected installed payload tag; do not infer absent transforms')
        length = struct.unpack_from('>H', data, at + 1)[0]; at += 3
        name = data[at:at+length].decode('utf-8'); at += length
        require(name not in result, 'Repeated payload field')
        result[name] = struct.unpack_from('>i', data, at)[0]; at += 4
    require(at + 1 == len(data), 'Trailing payload data')
    require(result == {'DecorativePlacementOptics': 1, 'ImportedTypeVersion': 1}, 'Actual manual payload changed')
    return result

def transformed(rows):
    # ImportedCollisionV126 has already oriented these boxes for yaw2. Its
    # transform is rotation0, MountY0, SourceShift0, with physical outer crop.
    boxes = [[v / 16 for v in row['from'] + row['to']] for row in rows]
    if not boxes: return []
    low = [min(b[a] for b in boxes) for a in range(3)]
    high = [max(b[a+3] for b in boxes) for a in range(3)]
    for a in range(3):
        if -.5 < low[a] < 0: low[a] = 0
        if 1 < high[a] < 1.5: high[a] = 1
    result = []
    for b in boxes:
        clipped = [max(low[a], b[a]) for a in range(3)] + [min(high[a], b[a+3]) for a in range(3)]
        if all(clipped[a] < clipped[a+3] for a in range(3)): result.append(clipped)
    return result

def cells_of(boxes):
    cells = {(0, 0, 0)}
    for b in boxes:
        for x in range(math.floor(b[0]+1e-8), math.ceil(b[3]-1e-8)):
            for y in range(math.floor(b[1]+1e-8), math.ceil(b[4]-1e-8)):
                for z in range(math.floor(b[2]+1e-8), math.ceil(b[5]-1e-8)):
                    # Match the provider's final cell-local nonempty check.
                    if all(min(1, b[a+3]-(x,y,z)[a]) > max(0, b[a]-(x,y,z)[a]) for a in range(3)):
                        cells.add((x,y,z))
    return cells

def proof(plan, report, plan_path, report_path):
    jar = Path(plan['productionJar']); require(sha(jar) == PRODUCTION, 'Production freeze changed')
    records = [r for r in report['installedWholeObjects'] if r['kind'] == 'door']
    require(len(records) == 1, 'Need precisely the observed ordinary door placement')
    installed = records[0]; state = installed['state']
    require(state == {'registryId': ID, 'properties': {'open':'false','profile':'base','rotation':'2','variant':'0'}}, 'Actual manual state changed')
    ints = payload_ints(installed['payloadNbt'])
    witness_path = HERE / 'witness-selection.json'; source = read(witness_path)['selected']['door']
    require(source['registry_id'] == ID and source['source'] == [990,-58,139] and source['poseData']['shift'] == [0,-1,0] and source['poseData']['mountY'] == 0, 'Bound source transform differs')
    require(source['poseData']['properties']['variant'] == '2' and source['poseData']['properties']['rotation'] == '2', 'Source art/yaw changed')
    blobs = {}; documents = {}; proofs = []
    names = ['composite/source_dark_arch_door.json', 'imported-source-parts-v127.json',
             'wood-floor-collision.json', 'imported-full-map-v127.json',
             'imported-collision-v126.json', 'imported-whole-source-optics-v127.json']
    with zipfile.ZipFile(jar) as archive:
        for name in names:
            resource = 'bloodborne_dw/' + name; data = archive.read(resource)
            require(data == (RESOURCES / resource).read_bytes(), 'Current source resource differs from frozen loaded resource: ' + resource)
            blobs[name] = data; documents[name] = json.loads(data)
            proofs.append({'resource': resource, 'sha256': hashlib.sha256(data).hexdigest()})
        classes = [{'class': name, 'sha256': hashlib.sha256(archive.read('dev/dreamwalker/bloodbornedw/composite/'+name+'.class')).hexdigest()}
                   for name in ('CompositeSourceShift','ImportedCollisionV126','ImportedCollisionMath','ImportedSourcePartsV127','WoodFloorCollision','ImportedWholeSourceOpticsV127')]
    spec = documents[names[0]]
    require(spec['simplePhysicalCollision'] and not spec.get('functionalDoorPhysics', False), 'Exact provider dispatch changed')
    require(spec['source']['kind'] == 'SOURCE_MAP_MATCHED_HALF_PAIR', 'Explicit whole-pair decision differs')
    parts = [r for r in documents[names[1]]['parts'] if r['registryId'] == ID]
    require(any(r['variant']==2 and r['defaultInstallationPose']==0 for r in parts), 'Existing item normalizer differs')
    require(not any(r['source']==ID and r['variant']==0 for r in documents[names[2]]), 'Earlier wood-floor provider now handles this pose')
    require(not any(r['registryId']==ID and r['variant']==0 for r in documents[names[3]]['objects']), 'Earlier full-map provider now handles this pose')
    require(not any(r['registryId']==ID for r in documents[names[5]]['objects']), 'A whole-source optical carrier now adds door cells')
    collision = documents[names[4]]; variant = collision['objects'][ID][0]
    stand_root = plan['stand']['root']; offset = (0,0,1); point = [stand_root[i]+offset[i] for i in range(3)]
    poses = []
    for name, field in [('closed','closedBase'), ('open','openBase')]:
        table = variant[field]; row = collision['shapes'][table][2]; boxes = transformed(row['collision'])
        require(boxes, 'This finite proof requires the actual nonempty physical route')
        cells = cells_of(boxes)  # Selection=physical when physical is nonempty.
        require(offset in cells, 'The mapped fresh helper is absent from '+name)
        poses.append({'name':name,'table':table,'yaw':2,'cells':[list(p) for p in sorted(cells)],'collision':boxes,'selectionEqualsCollision':True,'helperIncluded':True})
    union = {tuple(p) for pose in poses for p in pose['cells']}
    actual_union = {tuple(p[i]-stand_root[i] for i in range(3)) for p in installed['closedOpenCells']}
    require(union == actual_union, 'Exact two-pose provider union differs from the real installed report')
    require(point == [1024,225,112], 'Bound helper point changed')
    return {'schemaVersion':1,'status':'PASS','mode':'OFFLINE_SOURCE_BOUND_PROVIDER_PROOF_NOT_GAMEPLAY',
            'mapReads':0,'mapWrites':0,'jvmStarts':0,'productionSha256':PRODUCTION,
            'priorPlan':binding(plan_path),'priorReport':binding(report_path),'sourceWitness':binding(witness_path),
            'sourcePose':source['poseData'],'manualState':state,'manualPayloadNbt':installed['payloadNbt'],
            'manualPayloadFields':ints,'helperPoint':point,'sourceHelperOffset':[0,-1,1],'manualHelperOffset':list(offset),
            'helperInBothStates':True,'actualReportedUnionMatches':True,'poses':poses,'resources':proofs,'loadedProviderClasses':classes,
            'provider':'CompositeSourceShift.physicalFootprint -> ImportedCollisionV126.footprint; preoriented table/yaw -> transform(rotation0,mount0,shift0,crop=true) -> clip1e-8; selection=physical',
            'downstreamAssertions':{'tree':'SourceOriginalTreeBlock.art normalizes variant6 to0; actual installed closed/open footprint already authoritative; no source-variant equality remains',
                                    'statue':'NativeGrid fixed template pick/placement has no source-privilege transform; exact compiled member equality/crosschunk/wholebreak assertions retained'}}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--assemblies',type=Path);parser.add_argument('--proof-only',action='store_true');parser.add_argument('--runtime-patch',type=Path);parser.add_argument('--stand-observation',type=Path);args=parser.parse_args()
    if args.runtime_patch:return terminal_continuation(args)
    plan_path=HERE/'candidate-actual-footprint-plan.json';report_path=HERE/'candidate-actual-footprint/functions.json'
    require(sha(plan_path)==PRIOR_PLAN and sha(report_path)==PRIOR_REPORT,'Prior real FAIL evidence changed')
    plan=read(plan_path);report=read(report_path)
    require(report['status']=='FAIL' and report['standRestored'] and report['normalServerThreadFinished'] and report['productionSha256']==PRODUCTION and report['planSha256']==PRIOR_PLAN,'Prior run is not the exact restored normal-stop FAIL')
    require('Manual door must retain the bound source art and yaw' in report['error'],'Different failure requires a new addressed diagnosis')
    result=proof(plan,report,plan_path,report_path);proof_path=HERE/'manual-door-helper-two-pose-proof.json'
    if proof_path.exists(): require(read(proof_path)==result,'Existing source proof differs; preserve history before replacing')
    else: write_new(proof_path,result)
    if args.proof_only: print('SOURCE_BOUND_TWO_POSE_PROOF_PASS '+sha(proof_path));return
    require(args.assemblies is not None,'Final confirmed preserved-assemblies manifest is required before authoring this continuation')
    manifest=read(args.assemblies)
    require(manifest.get('schemaVersion')==1,'Unknown preserved-assembly manifest schema')
    rows=manifest.get('entries',[]);matches=[r for r in rows if r.get('key')==KEY]
    require(len(matches)==1,'Exact confirmed representative assembly key is missing/ambiguous')
    assembly=matches[0];spec_sha=next(r['sha256'] for r in result['resources'] if r['resource']=='bloodborne_dw/composite/source_dark_arch_door.json')
    # Entry schema is bound by the manifest owner, never guessed by family name.
    require(assembly.get('registryId')==ID and assembly.get('sourceSpec',{}).get('sha256')==spec_sha,'Representative assembly registry/spec source differs')
    require(assembly.get('exactVariant')==0 and assembly.get('installationPose')==0 and assembly.get('currentMainItem',{}).get('registryId')==ID and assembly.get('currentMainItem',{}).get('installationPose')==0,'Representative must be the confirmed whole installation pose/main item')
    inherited=[r for r in report['checks'] if air_check(r)]
    require(len(inherited)==5 and all(r['pass'] for r in inherited),'Both real AIR right clicks plus exact collision checks must already PASS')
    prior_continuation=copy.deepcopy(plan['continuation'])
    failures=[]
    for name, plan_name in [('candidate','candidate-plan.json'),('candidate-continuation','candidate-continuation-plan.json'),('candidate-door-loaded','candidate-door-loaded-plan.json'),('candidate-actual-footprint','candidate-actual-footprint-plan.json')]:
        old_plan=HERE/plan_name;old_report=HERE/name/'functions.json';old=read(old_report)
        require(old['status']=='FAIL' and old['standRestored'] and old['productionSha256']==PRODUCTION and old['planSha256']==sha(old_plan),'A prior FAIL/history binding differs')
        failures.append({'plan':binding(old_plan),'report':binding(old_report),'status':'FAIL','error':old.get('error')})
    plan['output']=str(HERE/'candidate-manual-policy')
    plan['continuation']['previousDiagnosticAttemptPlan']=binding(plan_path)
    plan['continuation']['diagnosticPreviousAttempt']=binding(report_path)
    plan['continuation']['priorContinuationBinding']=prior_continuation
    plan['continuation']['priorFailedAttempts']=failures
    plan['continuation']['addressedCorrection']='Existing source-parts defaultInstallationPose2->0 is legitimate; preserve registry/yaw, actual shift/mount and both-pose helper guards. Inherit prior real AIR motion; only bridge motion/removal and unrun tree/statue execute.'
    plan['continuation']['inheritedAirDoor']={'plan':binding(plan_path),'report':binding(report_path),'checks':inherited,
        'manualState':result['manualState'],'manualPayloadNbt':result['manualPayloadNbt'],'helperProof':binding(proof_path),
        'representativeAssembly':{'manifest':binding(args.assemblies),'entryKey':KEY,'sourceSpecSha256':spec_sha}}
    require(plan['authoringReferenceOnly'] is False and 'referenceScope' not in plan,'Candidate must remain the full target profile')
    output=HERE/'candidate-manual-policy-plan.json';write_new(output,plan)
    write_new(HERE/'candidate-manual-policy-binding.json',{'status':'INPUT_CONTINUATION_NOT_QA_READY','plan':binding(output),
        'productionSha256':PRODUCTION,'inheritedOriginalSuccessfulChecks':83,'inheritedAirMotionChecks':5,
        'inheritedAirDoor':plan['continuation']['inheritedAirDoor'],'allPriorFailureBindings':plan['continuation'],
        'newChecksRequired':['ordinary door setup','exact background bridge open/close and wholebreak','independent background break','representative whole-door main-item pick and complete destruction from each actually selectable part; fresh ordinary setup for each part in the same JVM','tree wholepick/place/break','statue template pick/place/crosschunk wholebreak','read-only originals invariance','entire stand restoration'],
        'authoringReferenceOnly':False,'worldFilesRead':0,'mapWrites':0,'jvmStarts':0})
    print('CONTINUATION_INPUT_BOUND_NOT_QA_READY '+str(output)+' '+sha(output))

def terminal_continuation(args):
    # Root owns this receipt's strict all-entry comparison and the runner. Reuse
    # the same verifier; never rewrite any old report's production fingerprint.
    import runtime_patch_receipt
    patch=read(args.runtime_patch);runtime_patch_receipt.validate(args.runtime_patch,Path(patch['after']['path']))
    require(patch['before']['sha256']==PRODUCTION,'The compatibility bridge must start at the actual old production 3477')
    prior_path=HERE/'candidate-manual-policy-plan.json';failed_path=HERE/'candidate-manual-policy/functions.json'
    require(sha(prior_path)=='0b206f7f0b013c1f4176241896297f4fe7b7f2872f1f28cd2fc1196de7272695','Prior manual-policy plan changed')
    require(sha(failed_path)=='573dbc1f451f5b7313e2f4406d6fae74dd1b40cfe65053d817348e4c44d5f419','Actual terminal-detach FAIL changed')
    plan=read(prior_path);failed=read(failed_path)
    require(failed['status']=='FAIL' and failed['standRestored'] is False and failed['normalServerThreadFinished'] and failed['productionSha256']==PRODUCTION and failed['planSha256']==sha(prior_path),'Wrong unrestored terminal-bridge failure')
    checks=failed['checks'];require(len(checks)==20 and all(r['pass'] for r in checks[:17]) and checks[17]['pass'] is False and checks[17]['name'].startswith('whole door break clears every authored/helper cell'),'Expected the exact seventeen successful bridge setup/motion checks followed by failed final detach')
    installed=failed['installedWholeObjects'];require(len(installed)==1 and installed[0]['kind']=='door','Recovery is limited to the one failed door fixture')
    installed=installed[0];require(installed['ownerUuid']=='b9eb3995-1f6a-47c6-915b-bf455cd1e30b','Fresh failed fixture owner differs')
    expected=[[1024,y,z] for y in range(225,229) for z in range(110,113)]
    require(installed['closedOpenCells']==expected,'Recovery must not extend outside the exact known twelve-cell fixture')
    require(installed['state']==plan['continuation']['inheritedAirDoor']['manualState'] and installed['payloadNbt']==plan['continuation']['inheritedAirDoor']['manualPayloadNbt'],'Recovery ordinary state/private payload changed')
    require(args.stand_observation is not None,'The root-executed exact read-only stand observation is required')
    observation=read(args.stand_observation)
    require(observation['status']=='OBSERVED_EXACT_QA_STAND' and observation['issues']==[] and observation['ownerUuid']==installed['ownerUuid'] and observation['report']==binding(failed_path) and Path(observation['world']).resolve()==Path(plan['targetWorldPath']).resolve(),'Read-only stand observation does not match this precise failed fixture')
    require(len(observation['cells'])==12 and {tuple(r['position']) for r in observation['cells']}=={tuple(p) for p in expected} and observation['expectedBase']=={'position':[1024,225,112],'index':1713},'Read-only observation covers another footprint/base')
    plan['productionJar']=patch['after']['path'];plan['productionSha256']=patch['after']['sha256'];plan['runtimePatch']=binding(args.runtime_patch)
    plan['output']=str(HERE/'candidate-terminal-bridge')
    cont=plan['continuation'];cont['priorFailedAttempts'].append({'plan':binding(prior_path),'report':binding(failed_path),'status':'FAIL','standRestored':False,'error':failed['error']})
    cont['inheritedBridgeDoor']={'plan':binding(prior_path),'report':binding(failed_path),'checks':checks[:17],
        'scope':'same dry persistent helper; real bridge opening/closing, exact collision/outline unions and same owner/base data; terminal detach was not successful'}
    cont['fixtureRecovery']={'report':binding(failed_path),'observation':binding(args.stand_observation),'ownerUuid':installed['ownerUuid'],'root':plan['stand']['root'],'allowedCells':expected,
        'background':[1024,225,112],'cell':1713,'policy':'before every mutation verify all twelve exact fresh typed cells/one-owner contributions/background plus AIR everywhere else in the entire finite stand; normal remove via fixed adapter then independent background break; abort on any mismatch'}
    cont['addressedCorrection']='Adapter-only ephemeral readback for final NativeGridBase detach; recovery only the exact known test-owned UUID. Inherit real prior AIR and bridge motion. Now exercise rollback/exact restoration, real terminal whole break/native background preservation, per-part whole assembly pick/break, tree/statue and final invariants. tryBreakBlock false after a handled BEFORE callback is diagnostic, not failure.'
    require(plan['authoringReferenceOnly'] is False and 'referenceScope' not in plan,'Candidate must keep the full target profile')
    out=HERE/'candidate-terminal-bridge-plan.json';write_new(out,plan)
    write_new(HERE/'candidate-terminal-bridge-binding.json',{'status':'INPUT_PATCHED_CONTINUATION_NOT_QA_READY','plan':binding(out),'runtimePatch':plan['runtimePatch'],
        'inheritedEvidenceProductionSha256':PRODUCTION,'actualProductionSha256':patch['after']['sha256'],'inheritedSuccessfulChecks':83,'inheritedAirMotionChecks':5,'inheritedBridgeSetupMotionChecks':17,
        'fixtureRecovery':cont['fixtureRecovery'],'priorFailedAttempts':cont['priorFailedAttempts'],'authoringReferenceOnly':False,'worldFilesRead':0,'mapWrites':0,'jvmStarts':0,
        'actualChecksRequired':['strict own-UUID recovery','injected terminal-write rollback and exact twelve-cell restoration','normal selected final detach leaves exact plain background','cold/released adapter remains FOREIGN for plain base','independent background break','representative whole assembly all accessible parts one-item/wholebreak','tree whole behavior','native statue exact roles/crosschunk wholebreak','originals invariance and complete stand restoration']})
    print('PATCHED_CONTINUATION_INPUT_BOUND_NOT_QA_READY '+str(out)+' '+sha(out))

if __name__=='__main__':main()
