"""Read-only evidence gates and a fresh V10 small review kit/full-source ZIP.

No Minecraft/build/checkpoint execution. Refuses historical artifact evidence,
missing local guide links, changed frozen main inputs and existing outputs.
"""
from __future__ import annotations
import argparse, hashlib, json, posixpath, re, shutil, xml.etree.ElementTree as ET, zipfile
from pathlib import Path
from package_first_set import ROOT, EXCLUDED, digest, require, resolve, read, json_bytes, source_files, write_zip, verified_archive
from package_review_v9 import MARKDOWN_LINK, MARKDOWN_REFERENCE, local_markdown_target, validate_kit_guide_links

BASE='dreamwalker-bb-fabric-1.20.1-review-v10'
SOURCE_PREFIX=BASE+'-full-source/'

def files_for_source():
    files=set(source_files(ROOT))
    # The two editable hand-drawn pixel grids are part of this source revision.
    for file in (ROOT/'artwork').rglob('*'):
        if file.is_file() and not any(p in EXCLUDED for p in file.relative_to(ROOT).parts):
            require(file.resolve().is_relative_to(ROOT),'Artwork symlink leaves project');files.add(file)
    return sorted(files,key=lambda p:p.relative_to(ROOT).as_posix())

def exact_artifact(value,sha,label):
    found=[value[k] for k in ('artifact_sha256','production_jar_sha256','productionJarSha256') if k in value]
    require(found and all(v==sha for v in found),'Exact current artifact binding missing/different: '+label)

def inner_client(value):
    row=value.get('client_review_output',{})
    return row.get('result',{})

def verified_raw_output(value,key):
    row=value.get(key,{})
    require(isinstance(row.get('result'),dict) and row.get('path') and row.get('sha256'),'Actual raw output record missing: '+key)
    path=resolve(row['path'],ROOT)
    require(path.is_file() and digest(path)==row['sha256'] and read(path)==row['result'],'Actual raw output bytes/content changed: '+str(path))
    return row['result'],path

def persisted_snapshot(menus,field):
    envelope=menus.get(field,{})
    require(envelope.get('schema')=='dw-v10-menus-persisted-state-v1' and isinstance(envelope.get('state'),dict) and isinstance(envelope.get('canonicalJson'),str),'Structured actual saved identity/policy snapshot missing: '+field)
    text=envelope['canonicalJson'];require(hashlib.sha256(text.encode('utf8')).hexdigest()==envelope.get('sha256') and json.loads(text)==envelope['state'],'Snapshot digest/content mismatch: '+field)
    state=envelope['state'];rule=state.get('rule',{});targets=state.get('targets',{});graph=state.get('lampGraph',{})
    require(rule.get('uuid') and rule.get('condition')=='ALL' and rule.get('incomplete') is True and rule.get('satisfied') is False and len(rule.get('sources',[]))==2 and len(rule.get('targets',[]))==3,'Incomplete saved ALL rule identity/source/target state differs')
    require(set(targets)=={'architecture_door','rp_door','wood_gate'} and all(t.get('identity',{}).get('uuid') and t.get('pendingEffect') is False for t in targets.values()),'Saved functional target UUID/queued-effect identities missing')
    door=targets['architecture_door'];command='scoreboard players add event_counter dw_v10_gui 1'
    require(door.get('leversOnly') is True and door.get('open') is False and door.get('afterOpen')==[command] and door.get('afterClose')==[command] and targets['rp_door'].get('open') is False and targets['wood_gate'].get('actualPulseIdle') is True,'Saved closed target policy/ordered after-state commands or gate idle differs')
    require(graph.get('schema')==2 and graph.get('nodes') and graph.get('lines') and state.get('removedFixtures',{}).get('lamp_C_still_registered') is False and state.get('removedFixtures',{}).get('lever_3_still_rule_member') is False and state.get('harmlessCommandCounter')==2,'Saved lamp graph/removal/actual event counter state missing')
    return envelope

def passed_steps(value,names,label):
    rows=value.get('steps',[])
    require(isinstance(rows,list) and rows and all(row.get('status')=='PASS' for row in rows),label+' contains incomplete/failed actual steps')
    observed=[row.get('name') for row in rows]
    require(all(name in observed for name in names),label+' is missing an actual required input/readback step')

def expected_types(jar):
    with zipfile.ZipFile(jar) as source:
        catalogue=json.loads(source.read('bloodborne_dw/debug_catalogue.json'))['entries']
        assets=json.loads(source.read('assets/bloodborne_rp/catalog.json'))
    architecture={row['registryId']:row['temporaryId'] for row in catalogue if row['kind']=='architecture'}
    rp={row['registryId'].split(':',1)[1] for row in catalogue if row['kind']=='rp_object' and not row.get('retiredNumberReserved')}
    aliases={row['registryId'].split(':',1)[1]:row['canonicalRegistryId'].split(':',1)[1] for row in catalogue if row['kind']=='rp_object' and row.get('retiredNumberReserved')}
    require(len(architecture)==18 and len(rp)==69,'Current offered canonical 18architecture/69RP set changed; review gate deliberately needs re-audit')
    require(len(aliases)==7 and set(aliases.values())<=rp,'Seven reserved old registry aliases must retain canonical offered targets')
    models={asset:assets[asset]['model'] for asset in rp}
    return architecture,rp,models,aliases

def typed_compound(value,label):
    require(isinstance(value,dict) and value.get('type')==10 and isinstance(value.get('value'),dict),'Actual typed compound missing: '+label)
    return value['value']

def picked_identity_present(value):
    if not isinstance(value,dict):return False
    children=value.get('value')
    if value.get('type')==10 and isinstance(children,dict):
        if set(children)&{'UUID','Links','VerticalOffset','SourceLegacyPayload','SourceShift','MountY'}:return True
        return any(picked_identity_present(child) for child in children.values())
    return value.get('type')==9 and isinstance(children,list) and any(picked_identity_present(child) for child in children)

def gate_middle_pick(pick,uuid,source,canonical,model):
    require(pick.get('status')=='PASS_ACTUAL_MIDDLE_KEY_CANONICAL_ITEM_SETTINGS_SOURCE_UNCHANGED','Actual completed middle-button input proof required')
    require(isinstance(uuid,str) and re.fullmatch(r'[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}',uuid),'Actual middle source UUID format differs')
    require(pick.get('sourceUuid')==uuid and pick.get('sourceRegistry')=='bloodborne_rp:'+source and pick.get('canonicalAsset')==canonical and pick.get('canonicalClientModel')==model and pick.get('pickedItem')=='bloodborne_rp:'+canonical+'_placer','Actual source UUID/raw registry and selected canonical item/client model differ')
    hit=pick.get('actualSourceHit',{})
    require(hit.get('type')=='ENTITY' and hit.get('entityUuid')==uuid and hit.get('entityRegistry')=='bloodborne_rp:'+source and pick.get('authoritativePrePickItem')=='minecraft:stick' and pick.get('clientPrePickItem')=='minecraft:stick','Middle input must begin on the exact old/new source ray with an observed non-placer sentinel')
    require(pick.get('inputPath')=='actual pickItemKey queued once; MinecraftClient native doItemPick and Creative inventory packet','Actual middle-button key/native inventory-packet input path required')
    before,after=pick.get('sourceStableTypedBefore'),pick.get('sourceStableTypedAfter')
    require(isinstance(before,dict) and before and before==after and before.get('actualUuid')==uuid and before.get('actualRegistry')=='bloodborne_rp:'+source and all(pick.get(k) is True for k in ['serverClientSettingsMatch','noInstanceIdentityCopied','sourceUnchanged']),'Actual middle input must preserve complete stable source identity/settings/pose/links')
    tag=typed_compound(pick.get('pickedTypedNbt'),'middle-picked item NBT');state=typed_compound(tag.get('bloodborne_rp_object'),'middle-picked settings')
    for key,kind in [('Open',1),('Locked',1),('Scale',5)]:
        require(isinstance(state.get(key),dict) and state[key].get('type')==kind and state[key].get('value')==before.get(key),'Picked setting/type differs from source: '+key)
    if 'DogsVisible' in before:require(state.get('DogsVisible')=={'type':1,'value':before['DogsVisible']},'Picked dog visibility differs from source')
    display=typed_compound(tag['display'],'picked display') if 'display' in tag else {}
    if 'CustomName' in before:require(display.get('Name')=={'type':8,'value':before['CustomName']},'Actual middle-picked name differs from source')
    else:require('Name' not in display,'Middle pick added an unobserved custom name')
    require(not picked_identity_present(pick['pickedTypedNbt']),'Middle-picked stack contains instance UUID/links/height/provenance')
    return state

def gate_inventory_snapshots(restored,reported_slot=False):
    original=restored.get('originalInventorySnapshot',{})
    server=restored.get('serverInventorySnapshot',{})
    client=restored.get('clientInventorySnapshot',{})
    require(isinstance(original,dict) and isinstance(original.get('size'),int) and 0<original['size']<=512 and isinstance(original.get('selectedSlot'),int) and 0<=original['selectedSlot']<9 and isinstance(original.get('slots'),list) and len(original['slots'])==original['size'],'Actual original complete PlayerInventory typed slots/selected slot snapshot required')
    for index,slot in enumerate(original['slots']):
        require(isinstance(slot,dict) and slot.get('slot')==index and isinstance(slot.get('item'),str) and ':' in slot['item'] and isinstance(slot.get('count'),int) and slot['count']>=0,'Actual original inventory ordered slot/item/count differs')
        typed_compound(slot.get('typedStack'),'original inventory typed ItemStack')
    require(server==original and client==original,'Actual server/client complete typed inventory snapshots must exactly equal original; true flags alone cannot prove restoration')
    for name in ['serverInventoryDifferences','clientInventoryDifferences']:
        difference=restored.get(name,{})
        require(isinstance(difference,dict) and difference.get('expectedSelectedSlot')==original['selectedSlot'] and difference.get('actualSelectedSlot')==original['selectedSlot'] and difference.get('selectedSlotMatches') is True and difference.get('changedSlots')==[],'Actual inventory differences/selected slot must be explicitly empty and equal on both sides')
    if reported_slot:require(restored.get('selectedSlot')==original['selectedSlot'],'Final reported selected slot differs from original complete inventory snapshot')


def gate_actual_middle_picks(actual,canonical_cases,models,aliases,mode):
    require(actual.get('actualCanonicalMiddlePickCount')==69,'All69 actual canonical middle-button inputs must complete')
    for row in canonical_cases:gate_middle_pick(row.get('actualMiddlePick',{}),row['uuid'],row['asset'],row['asset'],models[row['asset']])
    old=actual.get('ordinaryAliasPickCases',[]);expected=7 if mode=='AUTHOR' else 0
    require(isinstance(old,list) and len(old)==expected and actual.get('actualOldAliasMiddlePickCount')==expected,'Actual old-registry middle-pick count differs for client mode')
    if mode=='AUTHOR':
        require({row.get('alias') for row in old}==set(aliases),'All seven distinct old registry aliases must be actually picked')
        for row in old:
            source=row['alias'];canonical=aliases[source];uuid=row.get('uuid')
            require(row.get('canonical')==canonical and row.get('status')=='PASS_ACTUAL_OLD_REGISTRY_CLIENT_MIDDLE_KEY_CANONICAL_SETTINGS_AND_CREATIVE_CLEANUP','Old alias client pick/creative cleanup incomplete')
            gate_middle_pick(row.get('actualMiddlePick',{}),uuid,source,canonical,models[canonical])
            setup,cleanup=row.get('technicalSetupServerThread',{}),row.get('actualCleanupServerThread',{})
            require(setup.get('readThread')=='ACTUAL_SERVER_THREAD' and setup.get('uuid')==uuid and setup.get('registry')=='bloodborne_rp:'+source and setup.get('stable')==row['actualMiddlePick']['sourceStableTypedBefore'],'Exact old registry fixture identity/setup differs; setup is not user-input proof')
            require(cleanup.get('readThread')=='ACTUAL_SERVER_THREAD' and cleanup.get('present') is False and cleanup.get('creativeDropCount')==0 and isinstance(setup.get('nativeBefore'),dict) and setup['nativeBefore'] and cleanup.get('nativeAfter')==setup['nativeBefore'],'Actual old alias attack must preserve native pad and produce zero drops')
        placed=actual.get('actualPickedItemReinstallation',{})
        require(placed.get('status')=='PASS_ACTUAL_MIDDLE_PICK_CANONICAL_STACK_ORDINARY_REPLACE_NEW_UUID_SETTINGS_AND_CLEANUP','Actual middle-picked stack ordinary re-placement/cleanup required')
        original=next((row for row in old if row.get('uuid')==placed.get('sourceOldUuid')),None)
        require(original is not None and placed.get('canonicalAsset')==original['canonical'] and placed.get('actualPickedItem')==original['actualMiddlePick']['pickedItem'] and placed.get('actualPickedTypedNbt')==original['actualMiddlePick']['pickedTypedNbt'],'Re-placed stack must be the actually middle-picked old-alias stack')
        state=typed_compound(typed_compound(placed['actualPickedTypedNbt'],'re-placed picked NBT')['bloodborne_rp_object'],'re-placed picked settings')
        server=placed.get('actualServerThreadPlacement',{});cleanup=placed.get('actualCleanupServerThread',{})
        # This row is copied from the nested "added" list in serverRead's actual
        # server-thread observation. Its source schema has no nested readThread.
        require(isinstance(placed.get('newUuid'),str) and re.fullmatch(r'[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}',placed['newUuid']) and server.get('uuid')==placed['newUuid'] and server['uuid'] not in {row['uuid'] for row in old} and server.get('registry')=='bloodborne_rp:'+original['canonical'] and server.get('linksEmpty') is True and server.get('offset')==0,'Picked ordinary replacement must have a fresh canonical UUID without old links/height')
        require(server.get('open')==(state['Open']['value']=='1b') and server.get('locked')==(state['Locked']['value']=='1b') and float(str(state['Scale']['value']).rstrip('fF'))==server.get('scale') and server.get('name')==original['actualMiddlePick']['sourceStableTypedBefore'].get('CustomName',''),'Picked ordinary replacement settings/name differ')
        require(placed.get('inputPath')=='actual picked client stack copied only for actor inventory setup; ordinary use-key ItemUsageContext and attack-key packets' and isinstance(placed.get('nativeBefore'),dict) and placed['nativeBefore'] and cleanup.get('readThread')=='ACTUAL_SERVER_THREAD' and cleanup.get('present') is False and cleanup.get('creativeDropCount')==0 and cleanup.get('nativeAfter')==placed['nativeBefore'],'Picked ordinary replacement cleanup/native preservation/drop proof incomplete')
    restored=actual.get('actualOriginalInventoryRestorationAtExit',{})
    require(restored.get('readThread')=='ACTUAL_SERVER_THREAD' and restored.get('serverInventoryRestored') is True and restored.get('clientInventoryRestored') is True and isinstance(restored.get('selectedSlot'),int) and 0<=restored['selectedSlot']<9,'Actual middle-pick inventory/selected slot must restore on both sides before final save')
    gate_inventory_snapshots(restored,reported_slot=True)

def gate_rp_overlap(actual):
    row=actual.get('ordinaryRpOverlap',{})
    require(row.get('schema')=='dw-actual-rp-on-rp-placement-v1' and row.get('status')=='PASS_ACTUAL_ORDINARY_RP_ON_RP_PLACE_PRESERVE_AND_CREATIVE_CLEANUP','Actual completed ordinary RP-on-RP placement/preservation/cleanup proof required')
    original,new=row.get('originalUuid'),row.get('newUuid')
    require(row.get('originalAsset')=='npc_window' and row.get('secondAsset')=='chair' and isinstance(original,str) and re.fullmatch(r'[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}',original) and isinstance(new,str) and re.fullmatch(r'[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}',new) and original!=new,'Independent original and newly placed ordinary RP UUID/type required')
    before,after=row.get('originalStableBefore'),row.get('originalStableAfter')
    require(isinstance(before,dict) and before and before==after and before.get('actualUuid')==original and before.get('actualRegistry')=='bloodborne_rp:npc_window','Second ordinary RP placement changed original identity/pose/links/source data')
    selected,support=row.get('selectedOriginalRpHit',{}),row.get('blockOnlyNativeSupportHit',{})
    require(selected.get('type')=='ENTITY' and selected.get('entityUuid')==original and selected.get('entityRegistry')=='bloodborne_rp:npc_window' and support.get('type')=='BLOCK' and support.get('side')=='up' and isinstance(support.get('blockPos'),list) and len(support['blockPos'])==3 and row.get('actualMainHand')=='bloodborne_rp:chair_placer','Actual original RP ray/ordinary second item/native support ray required')
    first,last=row.get('initialServerObservation',{}),row.get('latestActualServerThreadObservation',{})
    created=last.get('created',[])
    require(first.get('readThread')=='ACTUAL_SERVER_THREAD' and last.get('readThread')=='ACTUAL_SERVER_THREAD' and first.get('originalStable')==before and last.get('originalStable')==after and last.get('originalPresent') is True and first.get('nativeSupportState') and first['nativeSupportState']==last.get('nativeSupportState'),'Actual server-thread original/support observations differ')
    require(row.get('observedSecondServerCount')==1 and row.get('observedSecondClientCount')==1 and last.get('newServerCount')==1 and len(created)==1 and created[0].get('uuid')==new and created[0].get('asset')=='chair' and created[0].get('registry')=='bloodborne_rp:chair' and row.get('ordinaryTwoUuidCleanup') is True,'One physical use must create one new UUID on both sides and ordinary attacks must clean both temporary actors')
    require(row.get('inputPath')=='ordinary use-key at real original RP EntityHitResult; no direct Item.useOnBlock/entity mutation','Ordinary RP-on-RP proof input path differs')
    passed_steps(actual,['second ordinary RP use-key creates new overlapping UUID','ordinary Creative removes only duplicate RP','ordinary Creative removes original temporary RP'],'Ordinary RP-on-RP placement/cleanup')

def gate_client(path,sha,types,mode,full=False):
    value=read(path);exact_artifact(value,sha,str(path));require(value.get('status')=='PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT' and value.get('exit_code')==0 and value.get('integrated_save_messages_present') is True and not value.get('termination'),'Actual client normal-save-exit failed: '+str(path))
    actual,_=verified_raw_output(value,'client_review_output');require(actual.get('status')=='PASS_ACTUAL_CLIENT_V10_MENUS_ORDINARY_RP_PARTS_HEIGHT_NATIVE_OVERLAP_AND_SAVE','Actual completed V10 client result missing')
    require(actual.get('mode')==mode and actual.get('firstEntryCreative') is True and actual.get('firstEntryGameMode')=='creative' and actual.get('firstEntryOperator4') is True and actual.get('actualIntegratedSave') is True and actual.get('directObjectMutationUsedAsClientProof') is False,'Actual joined Creative/OP4, ordinary input and integrated save must be explicit')
    exact_artifact(actual,sha,'actually loaded V10 client')
    gate_rp_overlap(actual)
    architecture=actual.get('architectureV10',actual.get('architectureV10Review',{}))
    require(architecture.get('status')=='PASS_ACTUAL_LOADED_CLIENT_BAKES_UV_AND_REQUESTED_RENDERER_HEIGHT_SAMPLES' and architecture.get('sampleCount')==18 and len(architecture.get('frontBackUv',[]))==4 and len(architecture.get('builderIcons',[]))==2,'Actual all18 owner/render/UV/icon client proof required')
    require({(row.get('type'),row.get('face')) for row in architecture['frontBackUv']}=={('90010','north'),('90010','south'),('90020','north'),('90020','south')} and all(row.get('frontBackAtlasUvEqualAtSameLocalXy') is True for row in architecture['frontBackUv']),'Actually baked front/back UV orientation must match at physical corners')
    # The QA result must itself bind the actually loaded production origin;
    # wrapper command-line hash alone does not prove the loaded class/resources.
    origins=actual.get('productionArtifactOrigins',[])
    require(any(row.get('sha256')==sha for row in origins),'Actual loaded JAR SHA absent from client probe')
    architecture_map,rp_types,models,aliases=types;architecture_types=set(architecture_map)
    shifted=architecture.get('shiftedInstances',[])
    require(len(shifted)==18 and {row.get('registry') for row in shifted}==architecture_types and all(row.get('type')==architecture_map.get(row.get('registry')) and row.get('uuid') and row.get('actualVertices',0)>0 and row.get('shiftedOwnerCells',0)>0 and row.get('clientPick')==row.get('type') for row in shifted),'Exact current artistic types and synchronized UUID/translated renderer/cell/pick samples required')
    architecture_cases=actual.get('ordinaryArchitectureCases',[])
    require(len(architecture_cases)==18 and {row.get('registry') for row in architecture_cases}==architecture_types and all(row.get('status')=='PASS_ACTUAL_ORDINARY_ARCHITECTURE_ITEM_AND_CREATIVE_ROOT_OR_HELPER_ATTACK' and row.get('uuid') for row in architecture_cases),'Every current architecture item must actually place its own type and be removed through ordinary client attack')
    rp_cases=actual.get('ordinaryRpCases',[])
    canonical_cases=[row for row in rp_cases if row.get('part')=='authored-first-part']
    require(actual.get('expectedCanonicalRpCases')==69 and len(canonical_cases)==69 and {row.get('asset') for row in canonical_cases}==rp_types and all(row.get('status')=='PASS_ACTUAL_ORDINARY_PLACE_AND_CREATIVE_PART_ATTACK' and row.get('uuid') and row.get('serverRegistry')=='bloodborne_rp:'+row.get('asset','') for row in canonical_cases),'Every canonical RP item must actually place its exact type and be removed through ordinary client entity attack')
    native_flags=['nativeBlockItemPlaced','nativePlacementSameServerClientUuid','nativeBlockSurvivesRpAttack','nativeBlockOrdinaryCleanup']
    require(all(all(row.get(flag) is True for flag in native_flags) and row.get('nativeBlockRegistry')=='minecraft:stone' and isinstance(row.get('nativeBlockCell'),list) and len(row['nativeBlockCell'])==3 and row.get('nativeBlockRelation') in {'INTERSECTS_SOURCE_SELECTION','ADJACENT_ROOT'} for row in canonical_cases),'Each of69canonical cases must actually place stone through ordinary input, preserve the same RP UUID, retain the stone after RP attack and ordinarily clean it up; source-overlap relation must be explicit')
    require(all(isinstance(row.get('nativeStableRpBefore'),dict) and row['nativeStableRpBefore'] and row['nativeStableRpBefore']==row.get('nativeStableRpAfter') for row in canonical_cases),'Each canonical native-placement case must retain the actual typed RP registry/UUID/name/pose/links/source payload snapshot')
    gate_actual_middle_picks(actual,canonical_cases,models,aliases,mode)
    for row in canonical_cases:
        camera=row.get('actualSourcePartCameraCandidate',{})
        require(camera.get('sourceHitUuid')==row['uuid'] and camera.get('nativeActorCollisionFree') is True and camera.get('sourcePhysicalActorCollisionFree') is True and camera.get('requestedDistance') in {2,3,4} and isinstance(camera.get('ordinaryReach'),(int,float)) and camera['requestedDistance']<=camera['ordinaryReach']+1e-6 and all(isinstance(camera.get(key),list) and len(camera[key])==count and all(isinstance(v,(int,float)) and -1e30<v<1e30 for v in camera[key]) for key,count in [('eye',3),('feet',3),('standingActorBox',6)]),'Each canonical ordinary attack must record the actual UUID ray and a collision-free standing-player camera within native reach')
    required_parts={('door_1','opened-moving-part'),('ladder','collapsed-moving-lower'),('ladder','upper-platform'),('ladder','remote-working-lower')}
    require(required_parts <= {(row.get('asset'),row.get('part')) for row in rp_cases if row.get('status')=='PASS_ACTUAL_ORDINARY_PLACE_AND_CREATIVE_PART_ATTACK'},'Actual independent moving/source-part attack cases are missing')
    platform=[row.get('actualPlatformWalk',{}) for row in rp_cases if (row.get('asset'),row.get('part'))==('ladder','upper-platform')]
    require(len(platform)==1 and platform[0].get('status')=='PASS_ACTUAL_SURVIVAL_PLATFORM_WALK' and platform[0].get('serverMovedZ',0)>.8 and abs(platform[0].get('serverFeetY',-1000)-platform[0].get('platformTopY',1000))<.06 and abs(platform[0].get('clientFeetY',-1000)-platform[0].get('platformTopY',1000))<.06,'Actual noncentral RP upper-platform ordinary Survival walk/gravity support required')
    require(actual.get('actualHeightWidgetAndHeldClickOneOperation') is True and actual.get('actualHeightInstance') and actual.get('ordinaryVanillaBlockPlacedInsideRp') is True and actual.get('ordinaryVanillaBlockSameRpUuid')==actual.get('actualHeightInstance'),'Actual GUI single height operation and ordinary native-inside-same-RP placement/identity required')
    passed_steps(actual,['no tool hold-repeat and UUID retained','reset height actual button','offhand tool real LKM guard','ordinary BlockItem native floor ray bypass keeps RP','ordinary RP removal leaves placed foreign native block','ordinary Creative native block cleanup'],'Ordinary client scenario')
    menus=actual.get('menusActual',{})
    require(menus.get('status')=='PASS_ACTUAL_CLIENT_MENUS_PACKETS_RULES_COMMANDS' and menus.get('mode')==mode and menus.get('directMutationEndpointsUsedAsGuiProof') is False and menus.get('completedSteps')==len(menus.get('steps',[])),'Actual menu input scenario not completed')
    entry=actual.get('firstEntrySnapshot',{})
    require(isinstance(entry,dict) and entry and entry.get('serverGameMode')=='creative' and entry.get('clientGameMode')=='creative' and entry.get('serverCreativeAbility') is True and entry.get('clientCreativeAbility') is True and entry.get('serverOperator4') is True and entry.get('scope')=='Actual first-entry readback before ANY QA prepare, inventory or mode setup','Actual unmodified first joined Creative/OP4 snapshot required')
    require(menus.get('firstEntrySnapshot')==entry and menus.get('firstEntryProofSource')=='bootstrap actual entry before ANY QA prepare','Menus must use the original joined snapshot rather than a later QA setup')
    if mode=='AUTHOR':
        before=actual.get('preConstructionFixtureState',{});after=actual.get('postConstructionFixtureState',{})
        require(isinstance(before,dict) and before and before==after and before.get('readThread')=='ACTUAL_SERVER_THREAD' and isinstance(before.get('fixtures'),dict) and len(before['fixtures'])==32 and isinstance(before.get('fixtureGraphs'),dict) and set(before['fixtureGraphs'])=={'bloodborne_rp_lamps','bloodborne_dw_mechanism_links','bloodborne_dw_mechanism_rules','bloodborne_dw_object_policies'} and actual.get('temporaryConstructionFixtureImmutable') is True,'Temporary ordinary construction tests must preserve all32 known fixture identities/typed data and relevant saved graph rows before actual menus')
        actor=actual.get('originalActorRestorationBeforeMenus',{})
        flags=['serverPositionRestored','serverYawPitchRestored','serverFlyingRestored','serverOriginalHandsRestored','clientYawPitchRestored','clientFlyingRestored','clientOriginalHandsRestored']
        require(isinstance(actor,dict) and actor.get('readThread')=='ACTUAL_SERVER_THREAD' and all(actor.get(flag) is True for flag in flags) and actual.get('originalActorInventoryCameraRestoredBeforeMenus') is True,'Original server/client actor camera, flying state and exact hands must be restored before menus')
        require(actor.get('serverInventoryRestored') is True and actor.get('clientInventoryRestored') is True,'Actual original PlayerInventory and selected slot must restore before menus after middle picks')
        gate_inventory_snapshots(actor)
        passed_steps(menus,['ordinary manual door packet is refused by levers-only policy','ALL3 starts0/3 and configuration does not open','ALL3 actual opens stable targets and pulses mixed gate','reset shows0/3 without ANY event or duplicate close command','removed ALL source marks rule incomplete','actual integrated save after GUI edits'],'Author menus/rules scenario')
        persisted_snapshot(menus,'persistedStateAfter')
    else:
        require(menus.get('ordinaryClientReentryActual')=='PASS_DISTINCT_SAVED_WORLD_GUI' and menus.get('savedRemoteDestinationReloadActual')=='PASS_ORDINARY_GUI_SAVED_UUID_LOAD_ARRIVAL_AND_LEASE_RELEASE' and menus.get('remoteDestinationBeforePacketChunkLoaded') is False and menus.get('remoteDestinationBeforePacketEntityLoaded') is False and menus.get('remoteDestinationExpectedUuid'),'Actual unloaded saved remote destination travel/release evidence required')
        passed_steps(menus,['saved source destruction remains incomplete','ordered harmless after-open commands survived save','persisted renamed source retains B and remote D','persisted directed route has no B→A','actual saved D loaded under same UUID and tickets released','actual saved bidirectional D→A widget returns'],'Reenter menus/rules scenario')
        before=persisted_snapshot(menus,'persistedStateBefore');after=persisted_snapshot(menus,'persistedStateAfter')
        require(menus.get('savedSemanticStateBalancedAfterRemoteTravel') is True and before['sha256']==after['sha256'],'Real balanced saved remote travel altered semantic instance/rule/policy/lamp state')
    if full:
        shader=actual.get('irisRuntime',{});final=shader.get('final',shader.get('after',{}))
        require(final.get('status','').startswith('PASS') and final.get('frameCounterAdvanced') is True and len(final.get('effectiveOptions',[]))==42 and all(o.get('matches') is True for o in final['effectiveOptions']),'Current live Iris pipeline/advancingcounter/all42 actual getters required')
    return value,actual

def gate_diagnostics(path,sha,jar,client_report,client_reenter_report):
    proof=read(path)
    require(proof.get('schema')=='dreamwalker-review-v10-diagnostics-proof-v1' and proof.get('status')=='PASS_CURRENT_V10_ACTUAL_DIAGNOSTICS_OFF_ON_REENTER_LINKED_EXPORTS_AND_PERSISTED_GUI_SECTIONS','Actual completed independent V10 diagnostics proof required')
    exact_artifact(proof,sha,'independent diagnostics')
    inputs=proof.get('inputs',{})
    required={'jar','server_off_report','server_on_report','server_reenter_report','client_report','client_reenter_report'}
    require(set(inputs)==required,'Diagnostics proof input coverage differs')
    fixed={'jar':jar,'client_report':client_report,'client_reenter_report':client_reenter_report}
    for name,row in inputs.items():
        source=resolve(row.get('path',''),ROOT)
        require(source.is_file() and source.stat().st_size==row.get('bytes') and digest(source)==row.get('sha256'),'Diagnostics proof input changed: '+name)
        if name in fixed:require(source==fixed[name],'Diagnostics proof uses another current artifact/client run: '+name)
    phases=proof.get('dedicated_server',{})
    require(set(phases)=={'off','on','reenter'},'All three actual dedicated diagnostics phases required')
    for key,name,mode in [('off','server_off_report','DISABLED'),('on','server_on_report','ENABLED'),('reenter','server_reenter_report','REENTER')]:
        source=resolve(inputs[name]['path'],ROOT);phase=phases[key]
        require(resolve(phase.get('report',''),ROOT)==source,'Independent dedicated phase uses another report: '+mode)
        wrapper=read(source);exact_artifact(wrapper,sha,'dedicated diagnostics '+mode)
        require(wrapper.get('status')=='PASS' and wrapper.get('exit_code')==0 and not wrapper.get('termination'),'Dedicated diagnostics normal exit missing: '+mode)
        actual,_=verified_raw_output(wrapper,'diagnostics_review')
        require(actual==phase.get('raw') and actual.get('schema')=='dreamwalker-server-diagnostics-review-v1' and actual.get('mode')==mode and actual.get('status')=='PASS_SERVER_DIAGNOSTICS_'+mode and actual.get('defaultSeconds')==60 and actual.get('actualSave') is True,'Exact dedicated default60/save/mode proof differs: '+mode)
    require(proof.get('default60_automatic_expiry')=='PASS_ACTUAL_DEDICATED_DEFAULT60' and proof.get('client_timing_retention_required') is True and proof.get('client_timing_retention',{}).get('status')=='PASS_CURRENT_WIRE_AND_LOCAL_RETENTION_BOTH_ACTUAL_V10_CLIENT_RUNS','Independent default60 and both actual client timing/export retention proof missing')
    for key,source in [('client',client_report),('client_reenter',client_reenter_report)]:
        require(resolve(proof.get(key,{}).get('report',''),ROOT)==source,'Independent diagnostics client identity differs: '+key)
    rows=proof.get('primary',[])
    require(isinstance(rows,list) and rows and len(rows)<=128,'Bounded diagnostics primary evidence missing')
    entries={}
    for row in rows:
        source=resolve(row.get('path',''),ROOT);name=row.get('name','')
        require(isinstance(name,str) and name and '/' not in name and '\\' not in name and name not in {'.','..'} and source.is_file() and digest(source)==row.get('sha256'),'Declared diagnostics primary artifact changed/unsafe')
        key='evidence/diagnostics/'+name
        require(key not in entries,'Duplicate diagnostics primary name');entries[key]=source
    return entries

def gate_alias_disk(path,sha):
    """Recheck the actual three saved worlds, not just a PASS label/count.

    This is the server ItemStack/ray/DamageSource compatibility proof. It does
    not claim a client packet or client-render test for the old alias items.
    """
    import verify_review_v10_alias_saved as verifier
    proof=read(path);exact_artifact(proof,sha,'independent old-alias disk compatibility')
    require(proof.get('schema')=='dw-v10-alias-saved-independent-v1' and proof.get('status')=='PASS_14_OLD_ALIAS_INSTANCES_SAVED_REENTERED_AND_PRODUCTION_REOPENED_TYPED_EXACT','Actual completed old-alias saved-world compatibility proof required')
    counts={'oldTypes':7,'permanentInstances':14,'instancesPerAlias':2,'savedOldInventoryStacks':7,'temporaryOldItemConstructors':7,'temporaryOldRegistryCreativeAttacks':7,'temporaryCanonicalCreativeAttacks':7,'savedTemporaryRpInstancesOrItemDrops':0}
    require(all(type(proof.get(key)) is int and proof[key]==value for key,value in counts.items()) and proof.get('forcedChunksRestoredAndAbsentOnDisk') is True and proof.get('copySourcesStillByteExact') is True,'Exact fourteen distinct old instances/seven saved old items/zero drops and restored chunk forcing required')
    reports=proof.get('reports',[])
    require(isinstance(reports,list) and len(reports)==3 and all(isinstance(row,dict) and row.get('path') for row in reports),'Three actual alias AUTHOR/REENTER/production-only report records required')
    paths=[resolve(row['path'],ROOT) for row in reports]
    require(len(set(paths))==3,'Alias disk compatibility requires three distinct actual processes/worlds')
    for row,source in zip(reports,paths):
        require(source.is_file() and source.stat().st_size==row.get('bytes') and digest(source)==row.get('sha256'),'Actual alias wrapper bytes changed: '+str(source))
    phases=[verifier.checked_phase(source,sha,mode) for source,mode in zip(paths,('AUTHOR','REENTER','PRODUCTION_REOPEN'))]
    actual=verifier.verify(*phases,sha)
    require(all(proof.get(key)==value for key,value in actual.items()),'Independent typed alias proof differs from actual three saved worlds/items/UUIDs/pick/debug/drop observations')
    entries={}
    for source,phase in zip(paths,phases):
        console=resolve(phase['consoleRecord']['path'],ROOT)
        entries['evidence/runtime/'+source.stem+'/'+console.name]=console
        if phase['raw'] is not None:
            _,raw=verified_raw_output(phase['wrapper'],'alias_review')
            entries['evidence/runtime/'+source.stem+'/'+raw.name]=raw
    return {'reports':paths,'entries':entries,'proof':proof}

def gates(a):
    sha=digest(a.jar);freeze=read(a.freeze_report)
    native_expected=freeze.get('nativeTests')
    require(freeze.get('schema')=='dreamwalker-v10-production-freeze-v1' and freeze.get('status')=='BUILD_NATIVE_PASS_REQUIRES_EXACT_ARTIFACT_ORDINARY_RUNTIME' and freeze.get('sha256')==sha and freeze.get('version')=='0.1.0-prototype.5' and type(native_expected) is int and native_expected>=129 and freeze.get('nativeFailures')==0 and freeze.get('coreChecks')==20,'Exact approved V10 frozen native/core20 build required')
    require(digest(resolve(freeze['nativeXml'],ROOT))==freeze['nativeXmlSha256'] and digest(resolve(freeze['buildLog'],ROOT))==freeze['buildLogSha256'],'Frozen native/build raw evidence changed')
    for row in freeze['mainSourceInputs']:
        path=resolve(row['path'],ROOT);require(path.stat().st_size==row['bytes'] and digest(path)==row['sha256'],'Frozen production input changed: '+row['path'])
    with zipfile.ZipFile(a.jar) as jar:
        meta=json.loads(jar.read('fabric.mod.json'));require(meta['id']=='bloodborne_dw' and meta['version']=='0.1.0-prototype.5','Wrong production metadata');require(jar.testzip() is None,'Production ZIP CRC failure');require(not any('/review/' in n or '/gametest/' in n or 'bloodborne-review-v10' in n for n in jar.namelist()),'QA classes/marker leaked into production')
    suite=ET.parse(a.native_xml).getroot();cases=suite.findall('.//testcase');require(len(cases)==native_expected and not suite.findall('.//failure') and not suite.findall('.//error'),'Exact frozen current native test population must all pass');require(digest(a.native_xml)==freeze['nativeXmlSha256'],'Native XML is not frozen candidate proof')
    author=read(a.scene_author_report);exact_artifact(author,sha,'scene author');require(author.get('status')=='PASS' and author.get('exit_code')==0 and not author.get('termination'),'Scene ordinary author/save/exit required')
    raw_author,author_output_path=verified_raw_output(author,'review_scene_output');exact_artifact(raw_author,sha,'actual scene author');require(raw_author.get('status')=='PASS_SCENE_AUTHORED_REQUIRES_PRODUCTION_REOPEN' and raw_author.get('architectureSampleCount')==18,'Completed actual18-sample author output required')
    clients=[];types=expected_types(a.jar)
    for path,mode,full in ((a.client_report,'AUTHOR',False),(a.client_reenter_report,'REENTER',False),(a.full_client_report,'AUTHOR',True)):
        wrapper,actual=gate_client(path,sha,types,mode,full);clients.append((path,wrapper,actual))
    require(clients[0][2]['menusActual']['persistedStateAfter']['sha256']==clients[1][2]['menusActual']['persistedStateBefore']['sha256'],'Actual AUTHOR-after and distinct REENTER-before instance/rules/policies/lamps changed')
    diagnostics_entries=gate_diagnostics(a.diagnostics_report,sha,a.jar,a.client_report,a.client_reenter_report)
    aliases=gate_alias_disk(a.alias_disk_report,sha)
    scene=read(a.scene_archive_report);exact_artifact(scene,sha,'scene archive');require(scene.get('status')=='PASS_VERIFIED_PRODUCTION_ONLY_SAVED_V10_WORLD_ARCHIVE' and scene.get('productionOnlyWorld') is True and scene.get('zipCrcAndEveryEntryByteVerification')=='PASS' and scene.get('sourceWorldUnchangedDuringArchive')=='PASS','Current production-only saved-world archive proof required')
    for key,path in [('author_report',a.scene_author_report),('author_client_report',a.client_report),('client_reenter_report',a.client_reenter_report)]:
        row=scene.get('evidence',{}).get(key,{});require(row.get('sha256')==digest(path) and resolve(row.get('path',''),ROOT)==path,'Saved-world verification used different actual proof: '+key)
    saved=scene.get('savedState',{})
    if scene.get('deliveryMode')=='FRESH_PRISTINE_AUTHOR_BASELINE':
        stand=saved.get('freshStand',{});require(saved.get('architectureSampleCount')==18 and saved.get('allRootAndForeignBlockEntitiesTypedEqual') is True and stand.get('status')=='PASS_PRISTINE_FIXTURES_NO_GUI_GRAPH_IMPORTED' and stand.get('usableLocalLamps')==3 and stand.get('usableRemoteLamps')==1 and stand.get('usableLevers')==3 and stand.get('configuredGuiRules')==0 and stand.get('configuredGuiLines')==0,'Fresh delivery stand must retain3local+1remote lamps/3levers/all18 samples without pretending it contains the separate GUI test graph')
    else:
        require(scene.get('deliveryMode')=='ACTUAL_GUI_AUTHOR_REENTER_SAVED_WORLD' and saved.get('reenterSemanticSnapshotSha256')==clients[1][2]['menusActual']['persistedStateAfter']['sha256'] and saved.get('actualSnapshotComparedToOfflineTypedRuleFlagsPoliciesLampGraphCounter')=='PASS','Delivered world is not matched to actual REENTER saved identities/control state')
    archive=resolve(scene.get('archive',scene.get('output','')),ROOT);require(archive.is_file() and digest(archive)==scene.get('archive_sha256',scene.get('sha256')),'Saved ordinary world ZIP changed')
    manifest=scene.get('file_manifest',scene.get('files'));require(isinstance(manifest,list) and manifest,'World manifest missing');prefix=scene.get('prefix','Review-v10-scene/');verified_archive(archive,prefix,manifest)
    with zipfile.ZipFile(archive) as z:require(z.testzip() is None and not any('/mods/' in n or n.endswith('.jar') or n.endswith('bloodborne-review-v10-scene-input.json') for n in z.namelist()),'QA/profiles must not enter saved ordinary world')
    text=a.request_text.read_text(encoding='utf-8-sig');require(text and text in (ROOT/'TASK.md').read_text(encoding='utf8') and text in (ROOT/'PROGRESS.md').read_text(encoding='utf8'),'Full user review text not preserved in TASK/PROGRESS')
    if a.request_sha256:require(digest(a.request_text)==a.request_sha256,'Exact user request text SHA changed')
    evidence=[a.freeze_report,a.native_xml,a.scene_author_report,a.client_report,a.client_reenter_report,a.full_client_report,a.scene_archive_report,a.diagnostics_report,a.alias_disk_report,*aliases['reports'],a.request_text,*a.extra_evidence]
    for path in evidence:require(path.is_file(),'Evidence path missing: '+str(path))
    runtime_entries={'evidence/runtime/'+a.scene_author_report.stem+'/'+author_output_path.name:author_output_path}
    for path,wrapper,_ in clients:
        _,raw=verified_raw_output(wrapper,'client_review_output');runtime_entries['evidence/runtime/'+path.stem+'/'+raw.name]=raw
    runtime_entries.update(diagnostics_entries)
    runtime_entries.update(aliases['entries'])
    return {'artifact_sha256':sha,'version':meta['version'],'native_tests':len(cases),'core_tests':20,'scene_archive':archive,'scene':scene,'evidence':evidence,'runtime_entries':runtime_entries,'clients':clients,'alias_compatibility':aliases['proof'],'full_task':'NOT_READY_FULL_CATALOGUE_NUMERIC_IDS_CITY_CONVERSION','manual_acceptance':'PENDING_USER_REVIEW'}

def transform_guides(entries,files):
    mapped={source.resolve():name for name,source in entries.items() if isinstance(source,Path)};snapshot={p.resolve() for p in files};names=[];rows=[]
    for name,source in list(entries.items()):
        if not isinstance(source,Path) or source.suffix.lower()!='.md' or not (name=='REVIEW-INSTRUCTIONS.md' or name.startswith('guides/')):continue
        names.append(name);counts={'mapped':0,'sourceOnly':0,'unavailableHistoricalBuild':0}
        def convert(match):
            target=local_markdown_target(match['target']);
            if target is None:return match.group(0)
            path,fragment=target;destination=(source.parent/path).resolve()
            if destination in mapped:
                counts['mapped']+=1;new=posixpath.relpath(mapped[destination],posixpath.dirname(name) or '.')+fragment
                return match['prefix']+'<'+new+'>'+ (match['title'] or '') + (')' if match.re is MARKDOWN_LINK else '')
            if destination.is_relative_to(ROOT):
                relative=destination.relative_to(ROOT).as_posix()
                if 'build' in destination.relative_to(ROOT).parts:notice='исторический build-архив/файл в комплект не включён: '+relative;counts['unavailableHistoricalBuild']+=1
                elif destination in snapshot:notice='в полном исходнике: '+relative;counts['sourceOnly']+=1
                else:notice='внешний/не включённый файл: '+relative;counts['sourceOnly']+=1
            else:notice='внешний файл в комплект не включён';counts['sourceOnly']+=1
            # A plain label is truthful; never leave an unavailable clickable
            # historical path disguised as an ordinary delivered guide link.
            if match.re is MARKDOWN_LINK:
                label=match['prefix'];label=label[2:-2] if label.startswith('![') else label[1:-2];return label+' ('+notice+')'
            return '<!-- '+notice+' -->'
        text=source.read_text(encoding='utf8');text=MARKDOWN_LINK.sub(convert,text);text=MARKDOWN_REFERENCE.sub(convert,text);entries[name]=text.encode('utf8');rows.append({'guide':name,**counts})
    validate_kit_guide_links(entries,names);return {'status':'PASS_SELF_CONTAINED_LOCAL_GUIDE_LINKS','guides':rows}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('jar','freeze-report','native-xml','scene-author-report','client-report','client-reenter-report','full-client-report','scene-archive-report','diagnostics-report','alias-disk-report','request-text','review-readme','output'):p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--request-sha256');p.add_argument('--guide',type=Path,action='append',default=[]);p.add_argument('--extra-evidence',type=Path,action='append',default=[]);p.add_argument('--dependency-jar',type=Path,action='append',default=[]);p.add_argument('--qa-jar',type=Path);p.add_argument('--check-only',action='store_true');a=p.parse_args()
    for key,value in vars(a).items():
        if isinstance(value,Path):setattr(a,key,resolve(value,ROOT))
        elif isinstance(value,list) and value and isinstance(value[0],Path):setattr(a,key,[resolve(x,ROOT) for x in value])
    require(a.output.is_relative_to((ROOT/'build/delivery').resolve()),'Output must stay within project build/delivery');proof=gates(a);files=files_for_source()
    require(a.review_readme.is_file() and all(g.is_file() for g in a.guide),'Current review guides missing')
    if a.check_only:print(json.dumps({'status':'PASS_V10_READ_ONLY_PACKAGE_GATES','artifact_sha256':proof['artifact_sha256'],'native_tests':proof['native_tests'],'core_tests':proof['core_tests'],'source_files':len(files),'manual_acceptance':'PENDING_USER_REVIEW','full_task':proof['full_task']}));return
    require(not a.output.exists(),'Never overwrite a delivery revision');a.output.mkdir(parents=True)
    source=a.output/(BASE+'-full-source.zip');source_records=write_zip(source,{SOURCE_PREFIX+p.relative_to(ROOT).as_posix():p for p in files});source_manifest=a.output/(BASE+'-source-files.json');source_manifest.write_bytes(json_bytes({'schema':'v10-full-source-byte-manifest-v1','artifact_sha256':proof['artifact_sha256'],'archive':source.name,'sha256':digest(source),'bytes':source.stat().st_size,'file_count':len(source_records),'files':source_records,'excluded':sorted(EXCLUDED),'external_original_inputs':'not copied; INPUTS.json retains exact paths/SHA','self_inclusion':False,'manual_acceptance':'PENDING_USER_REVIEW'}))
    entries={'mods/'+a.jar.name:a.jar,'REVIEW-INSTRUCTIONS.md':a.review_readme,'source/'+source.name:source,'source/'+source_manifest.name:source_manifest,'worlds/Review-v10-scene.zip':proof['scene_archive'],'mapping/INPUTS.json':ROOT/'INPUTS.json','mapping/user-review-v9-request.txt':a.request_text,'mapping/v10-builder-tools.pixel-grid.json':ROOT/'artwork/v10-builder-tools.pixel-grid.json'}
    for path in a.guide:require('guides/'+path.name not in entries,'Duplicate guide name');entries['guides/'+path.name]=path
    for path in proof['evidence']:key='evidence/'+path.name;require(key not in entries or entries[key]==path,'Evidence basename collision');entries[key]=path
    entries.update(proof['runtime_entries'])
    for dep in a.dependency_jar:require(dep.is_file() and dep.suffix=='.jar','Dependency JAR missing');key='mods/'+dep.name;require(key not in entries,'Duplicate dependency');entries[key]=dep
    if a.qa_jar:entries['TEST-ONLY-OPTIONAL/'+a.qa_jar.name]=a.qa_jar;entries['TEST-ONLY-OPTIONAL/README.txt']='QA addon не нужен для обычной игры. Не копируйте его в обычный mods.\n'.encode('utf8')
    native_scope=[]
    for path,_,actual in proof['clients']:
        rows=[row for row in actual['ordinaryRpCases'] if row['part']=='authored-first-part'];relations={key:sum(row['nativeBlockRelation']==key for row in rows) for key in ['INTERSECTS_SOURCE_SELECTION','ADJACENT_ROOT']};native_scope.append({'clientReport':path.name,'canonicalCases':len(rows),'actualNativePlacementRelations':relations,'scope':'Adjacent-root observations do not prove source mesh intersection; selection volume is distinct from physical geometry'})
    alias_scope={key:proof['alias_compatibility'][key] for key in ('status','oldTypes','permanentInstances','instancesPerAlias','savedOldInventoryStacks','temporaryOldItemConstructors','savedTemporaryRpInstancesOrItemDrops','ordinaryApiScope','renderScope','furniturePolicyAndHooks')}
    nav=transform_guides(entries,files);matrix={'schema':'v10-review-kit-check-matrix-v1','artifact_sha256':proof['artifact_sha256'],'version':proof['version'],'native_tests':proof['native_tests'],'core_tests':proof['core_tests'],'production_only_world':True,'deliveryWorldMode':proof['scene']['deliveryMode'],'actualGuiPersistence':'Separate actual AUTHOR/REENTER snapshots; delivered pristine baseline has no QA All/QA Main graph' if proof['scene']['deliveryMode']=='FRESH_PRISTINE_AUTHOR_BASELINE' else 'Delivered actual GUI-tested saved world','current_clients':'minimal+reenter+full live Iris42 actual PASS','canonicalRpNativePlacement':native_scope,'oldAliasActualDiskCompatibility':alias_scope,'guide_navigation':nav,'manual_acceptance':'PENDING_USER_REVIEW','full_task':proof['full_task']};entries['CHECK-MATRIX.json']=json_bytes(matrix)
    entries['README.txt']=(f'Dreamwalker BB review V10 · {proof["version"]}\nProduction SHA256: {proof["artifact_sha256"]}\nMinecraft1.20.1 / Java17 / Fabric. Удалите прежний combined Dreamwalker и отдельный bloodborne_rp. Обычный production JAR в mods; зависимости Fabric API и GeckoLib нужны отдельно, если не включены.\nРаспакуйте worlds/Review-v10-scene.zip в saves. Управление и координаты: REVIEW-INSTRUCTIONS.md. Полный исходник: source/.\nТехнические проверки не означают ручное принятие графики/удобства. Полный каталог, окончательные числовые ID и преобразование города НЕ готовы. QA addon, если вложен, находится только в TEST-ONLY-OPTIONAL.\n').encode('utf8')
    kit=a.output/(BASE+'-kit.zip');kit_records=write_zip(kit,entries);require(digest(a.jar)==proof['artifact_sha256'] and files_for_source()==files,'Production/source topology changed during packaging')
    for row in source_records:require(digest(ROOT/row['path'][len(SOURCE_PREFIX):])==row['sha256'],'Full source changed after ZIP write: '+row['path'])
    with zipfile.ZipFile(kit) as z:require(z.testzip() is None,'Kit CRC failure')
    standalone=a.output/a.jar.name;shutil.copyfile(a.jar,standalone);require(digest(standalone)==proof['artifact_sha256'],'Standalone JAR copy differs')
    manifest={'schema':'v10-delivery-byte-manifest-v1','status':'PACKAGED_PENDING_USER_REVIEW','artifact_sha256':proof['artifact_sha256'],'production_jar':standalone.name,'kit':kit.name,'kit_sha256':digest(kit),'kit_bytes':kit.stat().st_size,'kit_files':kit_records,'source':source.name,'source_sha256':digest(source),'source_bytes':source.stat().st_size,'source_files':len(source_records),'manual_acceptance':'PENDING_USER_REVIEW','full_task':proof['full_task']};path=a.output/(BASE+'-delivery-manifest.json');path.write_bytes(json_bytes(manifest));print(json.dumps({'status':manifest['status'],'manifest':str(path),'kit_sha256':manifest['kit_sha256'],'source_sha256':manifest['source_sha256']}))
if __name__=='__main__':main()
