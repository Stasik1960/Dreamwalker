"""Read-only typed audit of fresh old-alias AUTHOR, disk REENTER, ordinary reopen.

The Java probe's expected stable NBT is decoded, then compared independently to
actual entity-region disk NBT in all three worlds. Compound order is irrelevant;
tag types, exact UUID/pose/names, array/list order and full opaque Original are not.
"""
from __future__ import annotations
import argparse, base64, hashlib, json, uuid
from pathlib import Path
from collections import Counter, defaultdict
from verify_client_scene_persistence import full_rp_entities, identity
from world_io import Tag, TAG_COMPOUND, TAG_LIST, TAG_DOUBLE, TAG_FLOAT, TAG_BYTE, TAG_INT, TAG_LONG_ARRAY, compound, read_nbt, decode_nbt, RegionFile

ROOT=Path(__file__).resolve().parents[1]
ALIASES={'furniture_8':'furniture_1','furniture_3':'furniture_10','furniture_4':'furniture_11','furniture_5':'furniture_12',
         'furniture_6':'furniture_13','furniture_7':'furniture_14','furniture_9':'furniture_2'}
FIELDS=('id','UUID','Pos','Rotation','Open','Locked','Scale','VerticalOffset','CustomName','Links','SourceLegacyPayload','OpaqueAliasProbe')
AUTHOR='PASS_ALIAS_AUTHOR_14_INSTANCES_REQUIRES_DISTINCT_DISK_REENTER'
REENTER='PASS_ALIAS_DISTINCT_DISK_REENTER_14_PRESERVED_AND_7_CANONICAL_OLD_ITEM_PROBES'


def require(condition,message):
    if not condition:raise ValueError(message)
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def read(path):return json.loads(Path(path).read_text(encoding='utf8'))
def resolve(path):
    path=Path(path);return (path if path.is_absolute() else ROOT/path).resolve()
def file_record(path):return {'path':str(path),'bytes':path.stat().st_size,'sha256':sha(path)}
def subset(tag):
    fields=compound(tag)
    return Tag(TAG_COMPOUND,{k:fields[k] for k in FIELDS if k in fields})
def state(world,name):
    path=world/'data'/name
    require(path.is_file(),'Missing saved data '+name)
    return compound(compound(read_nbt(path).root)['data'])


def checked_phase(path,expected_sha,mode):
    path=resolve(path);wrapper=read(path)
    require(wrapper.get('status')=='PASS' and wrapper.get('exit_code')==0 and wrapper.get('world_startup')=='PASS'
            and wrapper.get('artifact_sha256')==expected_sha and wrapper.get('original_world_loaded') is False
            and 'termination' not in wrapper,'Actual exact-artifact normal save/exit wrapper required: '+mode)
    console=resolve(wrapper['evidence']);text=console.read_text(encoding='utf8',errors='replace')
    require(sha(console)==wrapper['console_sha256'] and 'Saving worlds' in text,'Actual console/save bytes changed')
    world=resolve(wrapper['run_directory'])/'isolated-smoke-world'
    require(world.is_relative_to((ROOT/'build').resolve()) and (world/'level.dat').is_file(),'Only saved derived build worlds allowed')
    raw=None
    if mode!='PRODUCTION_REOPEN':
        result=wrapper['alias_review'];raw_path=resolve(result['path']);raw=read(raw_path)
        require(sha(raw_path)==result['sha256'] and raw==result['result'],'Actual alias primary output changed')
        require(raw['schema']=='dw-v10-alias-disk-output-v1' and raw['mode']==mode
                and raw['status']==(AUTHOR if mode=='AUTHOR' else REENTER) and raw['productionJarSha256']==expected_sha
                and raw.get('actualSave') is True and raw.get('actualFinalSave') is True and raw.get('forcedFlagsRestored') is True
                and raw.get('persistedOldInstanceCount')==14 and raw.get('persistedOldAliasTypeCount')==7
                and raw.get('savedOldInventoryStackCount')==7
                and wrapper.get('alias_output_ready_before_stop') is True,'Missing actual separate alias save/restore proof')
        marker=wrapper['alias_input'];require(marker['mode']==mode and sha(resolve(marker['source']))==marker['sha256']==raw['markerSha256'],'Copied guard marker bytes changed')
        require(any(row.get('id')=='bloodborne_dw_review' for row in wrapper['modset']),'Explicit QA add-on missing')
    else:
        require(wrapper.get('alias_input') is None and not any(row.get('id')=='bloodborne_dw_review' for row in wrapper['modset']), 'Production-only reopen unexpectedly has QA')
        require(not (resolve(wrapper['run_directory'])/'review-v10-alias-input.json').exists(),'Production reopen contains author marker')
    forced_path=world/'data/chunks.dat'
    if forced_path.is_file():
        fields=state(world,'chunks.dat')
        require('Forced' not in fields or (fields['Forced'].type==TAG_LONG_ARRAY and not fields['Forced'].value),'Temporary chunk forcing leaked into saved world')
    require('dw_review_v10_alias_fixture.dat' in {p.name for p in (world/'data').glob('*.dat')},'Permanent fixture journal not actually on disk')
    saved=state(world,'dw_review_v10_alias_fixture.dat')
    require(saved['Schema'].type==TAG_INT and saved['Schema'].value==2 and saved['Artifact'].value==expected_sha
            and len(saved['Rows'].value)==14 and len(saved['Items'].value)==7,'Saved exact artifact/14instance/7inventory journal differs')
    return {'wrapper':wrapper,'raw':raw,'world':world,'saved':saved,'reportRecord':file_record(path),'consoleRecord':file_record(console)}


def check_copy(phase,source):
    record=phase['wrapper']['derived_world_copy']
    require(resolve(record['source'])==source and record.get('source_unchanged_after_run') is True and record.get('copy_byte_verification')=='PASS','Distinct phase did not copy immutable previous actual saved world')
    require(record['omitted_files']==['session.lock'],'Copy omitted more than lock')
    for row in record['files']:
        path=source/row['path'];require(path.resolve().is_relative_to(source) and path.stat().st_size==row['bytes'] and sha(path)==row['sha256'],'Copy source bytes no longer exact: '+row['path'])


def actual_item_drops(world):
    found=[]
    for path in sorted((world/'entities').glob('*.mca')):
        if not path.stat().st_size:continue
        for chunk in RegionFile(path.read_bytes()).chunks():
            for tag in compound(chunk.nbt().root)['Entities'].value:
                fields=compound(tag)
                if fields['id'].value!='minecraft:item':continue
                pos=[x.value for x in fields['Pos'].value]
                if -52<=pos[0]<16 and 55<=pos[1]<90 and 36<=pos[2]<72:found.append({'position':pos,'uuid':identity(fields['UUID'])})
    return found


def verify(author,reenter,production,artifact_sha):
    phases=[author,reenter,production];require(len({p['world'] for p in phases})==3,'Disk proof requires three distinct process/world directories')
    check_copy(reenter,author['world']);check_copy(production,reenter['world'])
    require(author['saved']==reenter['saved']==production['saved'],'Full typed14fixture journal changed across actual disk loads')
    raw=author['raw'];expected={row['uuid']:decode_nbt(base64.b64decode(row['expectedTypedNbtBase64'],validate=True)).root for row in raw['fixtures']}
    require(len(raw['fixtures'])==len(expected)==14,'Duplicate raw fixture UUID or missing member')
    journal={identity(compound(row)['UUID']):compound(row) for row in author['saved']['Rows'].value}
    require(set(journal)==set(expected),'Saved fixture journal differs from primary14UUID evidence')
    inventories=[full_rp_entities(p['world']) for p in phases]
    counts=Counter();grouped=defaultdict(list);rows=[]
    raw_reenter={r['uuid']:r for r in reenter['raw']['fixtures']};require(set(raw_reenter)==set(expected),'REENTER selection/pick/debug rows lost an instance')
    for uid,want in expected.items():
        fields=compound(want);alias=fields['id'].value.removeprefix('bloodborne_rp:')
        require(alias in ALIASES and journal[uid]['Alias'].value==alias and journal[uid]['Stable']==want,'Wrong raw alias journal/member')
        require(identity(fields['UUID'])==uid,'Stable UUID differs from saved identity')
        require(set(FIELDS)==set(fields),'Missing requested typed stable field')
        require(fields['Pos'].type==TAG_LIST and fields['Pos'].list_type==TAG_DOUBLE and fields['Rotation'].list_type==TAG_FLOAT
                and fields['Locked'].type==TAG_BYTE and fields['Open'].type==TAG_BYTE and fields['Scale'].type==TAG_FLOAT
                and fields['VerticalOffset'].type==TAG_DOUBLE,'Pose/settings tag types changed')
        require(len(fields['Links'].value)==1,'Raw historical link UUID list not retained')
        envelope=compound(fields['SourceLegacyPayload']);original=compound(envelope['Original'])
        require(envelope['Schema'].type==TAG_INT and envelope['Schema'].value==1 and 'SourceLegacyPayload' not in original
                and original['OpaqueAliasProbe']==fields['OpaqueAliasProbe'] and identity(original['UUID'])==uid
                and original['id'].value=='bloodborne_rp:'+alias,'Original opaque provenance lost, nested, or reassigned')
        source_row=next(row for row in raw['fixtures'] if row['uuid']==uid)
        require(hashlib.sha256(base64.b64decode(source_row['expectedTypedNbtBase64'])).hexdigest()==source_row['stableTypedSha256'],'Primary typed snapshot hash differs')
        for phase,rp in zip(phases,inventories):
            require(uid in rp and subset(rp[uid])==want,'Actual saved typed alias fields changed in '+str(phase['world'])+' UUID='+uid)
        observed=raw_reenter[uid]
        require(observed['expectedTypedNbtBase64']==source_row['expectedTypedNbtBase64'] and observed['currentAndExpectedTypedEqual'] is True
                and observed['actualDebugCommand']=='bb debug' and observed['actualDebugResult']>0 and observed['exactCommandEntityRay'] is True
                and observed['canonical']==ALIASES[alias] and observed['canonicalModelSelected']==source_row['canonicalModelSelected']
                and observed['temporaryId']==source_row['temporaryId'] and observed['policyLookupKey']=='RP|minecraft:overworld|'+uid,
                'Actual canonical debug/model/pick/UUID lookup evidence differs')
        counts[alias]+=1;grouped[alias].append(fields)
        rows.append({'uuid':uid,'oldRegistry':fields['id'].value,'canonicalAsset':ALIASES[alias],
                     'typedPose':[x.value for x in fields['Pos'].value],'scale':fields['Scale'].value,
                     'verticalOffset':fields['VerticalOffset'].value,'oldRegistryAndFullRequestedTypedFieldsEqualInAllThreeSavedWorlds':True,
                     'opaqueOriginalAndRawLinksExact':True,'actualCommandResult':observed['actualDebugResult'],
                     'canonicalSelectedModel':observed['canonicalModelSelected'],'temporaryId':observed['temporaryId']})
    require(counts==Counter({alias:2 for alias in ALIASES}),'Two instances of each alias did not survive')
    for alias,pair in grouped.items():
        for field in ('UUID','Pos','Rotation','CustomName','Scale','VerticalOffset','Locked','Open','Links'):
            require(pair[0][field]!=pair[1][field],'Pair was deduplicated/shared setting '+alias+'/'+field)
    inventory_rows=[]
    saved_items={compound(row)['Alias'].value:compound(row)['Stack'] for row in author['saved']['Items'].value}
    require(set(saved_items)==set(ALIASES),'Seven old inventory items were not saved independently')
    observed_inventory={row['aliasItem']:row for row in reenter['raw']['savedOldInventoryStacks']}
    author_inventory={row['aliasItem']:row for row in author['raw']['savedOldInventoryStacks']}
    require(set(observed_inventory)==set(author_inventory)=={'bloodborne_rp:'+a+'_placer' for a in ALIASES},'Actual saved inventory parsed rows differ')
    for alias,stack in saved_items.items():
        fields=compound(stack);item_id='bloodborne_rp:'+alias+'_placer';observed=observed_inventory[item_id]
        payload=base64.b64decode(observed['savedTypedStackNbtBase64'],validate=True)
        require(fields['id'].value==item_id and fields['Count'].type==TAG_BYTE and fields['Count'].value==1
                and decode_nbt(payload).root==stack and hashlib.sha256(payload).hexdigest()==observed['savedTypedStackSha256']
                and observed['parsedAndSerializedTypedEqual'] is True and observed==author_inventory[item_id], 'Saved old ItemStack id/count/full typed roundtrip differs')
        item_tag=compound(fields['tag']);supported=compound(item_tag['bloodborne_rp_object'])
        require(set(supported)=={'Open','Locked','Scale'} and supported['Open'].type==supported['Locked'].type==TAG_BYTE
                and supported['Scale'].type==TAG_FLOAT and identity(item_tag['UUID']) in expected and len(item_tag['Links'].value)==1
                and 'AliasInventoryOpaque' in item_tag and 'Name' in compound(item_tag['display']), 'Old item supported/opaque/old identity settings missing')
        inventory_rows.append({'originalSavedItemId':item_id,'count':1,'oldOwnerUuid':identity(item_tag['UUID']),
                               'fullTypedSavedStackEqualAcrossAllThreeWorlds':True,'fromNbtThenWriteNbtTypedEqual':True,
                               'customName':compound(item_tag['display'])['Name'].value,'scale':supported['Scale'].value})
    for rp in inventories:require(set(rp)==set(expected),'Actual disk contains lost permanent or leaked temporary/canonical probe RP entities')
    probes=reenter['raw']['probes'];require(len(probes)==7 and {r['aliasItem'] for r in probes}=={'bloodborne_rp:'+a+'_placer' for a in ALIASES},'Seven actual old-item constructors not individually tested')
    temporary=set()
    for probe in probes:
        alias=probe['aliasItem'].removeprefix('bloodborne_rp:').removesuffix('_placer')
        require(probe['status']=='PASS_SERVER_OLD_ITEM_CANONICAL_PICK_FRESH_UUID_TYPED_SETTINGS_AND_RAY_VALIDATED_CREATIVE_ATTACK'
                and probe['newRegistry']=='bloodborne_rp:'+ALIASES[alias] and probe['pickCanonical'] is True and probe['heldTypedNbtPreserved'] is True
                and probe.get('oldInventoryDiskRoundtrip') is True and probe.get('oldInventoryIdentityNotCopied') is True
                and probe['newLinksEmpty'] is True and probe['oldAndCanonicalCreativeDrops']==0
                and probe['exactTwoTemporaryInstancesRemoved'] is True and probe['permanentFourteenUnchanged'] is True
                and probe['oldRegistryTemporaryProbeActualRayAndDamageGuard'] is True
                and probe['newCanonicalTemporaryProbeActualRayAndDamageGuard'] is True,'Ordinary item/pick/zeroDrop Creative guard proof incomplete')
        old,new=probe['oldRegistryTemporaryProbeUuid'],probe['newCanonicalTemporaryProbeUuid']
        require(new==probe['newUuid'] and old!=new and not ({old,new}&(temporary|set(expected))),'An old item copied UUID or a temporary attack targeted a permanent instance')
        uuid.UUID(old);uuid.UUID(new);temporary.update((old,new))
    require(reenter['raw']['permanentFourteenBeforeSha256']==reenter['raw']['permanentFourteenAfterSha256'],'Probe batch changed a permanent same-type instance')
    for phase in phases:require(not actual_item_drops(phase['world']),'Item drops leaked to actual saved temporary probe area')
    return {'schema':'dw-v10-alias-saved-independent-v1','status':'PASS_14_OLD_ALIAS_INSTANCES_SAVED_REENTERED_AND_PRODUCTION_REOPENED_TYPED_EXACT',
            'productionJarSha256':artifact_sha,'reports':[p['reportRecord'] for p in phases],'consoles':[p['consoleRecord'] for p in phases],
            'worlds':[str(p['world']) for p in phases],'oldTypes':7,'permanentInstances':14,'instancesPerAlias':2,
            'savedOldInventoryStacks':7,'savedOldInventoryRows':inventory_rows,'temporaryOldItemConstructors':7,'temporaryOldRegistryCreativeAttacks':7,'temporaryCanonicalCreativeAttacks':7,
            'savedTemporaryRpInstancesOrItemDrops':0,'fixtureRows':rows,'forcedChunksRestoredAndAbsentOnDisk':True,
            'copySourcesStillByteExact':True,'furniturePolicyAndHooks':'UNSUPPORTED_STATIC_TYPES; no artificial active links/hooks authored; raw Links and full opaque provenance preserved',
            'renderScope':'canonical model identifier selection validated on actual saved server objects; actual client render/quads NOT_RUN in this probe',
            'ordinaryApiScope':'actual ItemStack.useOnBlock and ray-validated Creative player DamageSource guards on loaded server; client packets NOT_RUN',
            'fullCityOrUserApproval':'NOT_RUN; fresh isolated compatibility fixture only'}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('author-report','reenter-report','production-report','report'):parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--artifact-sha',required=True);args=parser.parse_args()
    require(not args.report.exists(),'Refusing independent historical report overwrite')
    phases=[checked_phase(path,args.artifact_sha,mode) for path,mode in zip((args.author_report,args.reenter_report,args.production_report),('AUTHOR','REENTER','PRODUCTION_REOPEN'))]
    result=verify(*phases,args.artifact_sha);args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'status':result['status'],'report':str(args.report),'instances':14,'oldTypes':7}));return 0


if __name__=='__main__':raise SystemExit(main())
