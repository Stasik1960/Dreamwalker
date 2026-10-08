"""Verify the actual AUTHOR -> REENTER -> production-only V10 world, then archive it.

Read-only world inspection. All world files except root session.lock are retained.
No original city, Minecraft process, direct API object mutation or prior archive edit.
"""
from __future__ import annotations
import argparse, hashlib, json, zipfile
from pathlib import Path
from archive_first_set_scene import inventory
from verify_client_scene_persistence import full_rp_entities
from verify_review_v9_scene import identity, owner, state_parts, typed_sha
from world_io import compound, read_nbt, decode_nbt
from package_review_v10 import ROOT, read, resolve, digest, require, exact_artifact, expected_types, gate_client

PREFIX='Review-v10-scene/'
COMMAND='scoreboard players add event_counter dw_v10_gui 1'

def raw_output(wrapper,key):
    row=wrapper[key];path=resolve(row['path'],ROOT)
    require(digest(path)==row['sha256'] and read(path)==row['result'],'Changed actual raw output: '+str(path))
    return row['result']

def data_file(world,name):
    path=world/'data'/name
    require(path.is_file(),'Missing actual saved state: '+name)
    return compound(compound(read_nbt(path).root)['data'])

def values(tag):return [entry.value for entry in tag.value]
def default(fields,key,value=False):return fields[key].value if key in fields else value
def ids(tag):return [identity(entry) for entry in tag.value]
def signed(value,bits):return value-(1<<bits) if value&(1<<(bits-1)) else value
def unpack_pos(value):return (signed((value>>38)&0x3ffffff,26),signed(value&0xfff,12),signed((value>>12)&0x3ffffff,26))
def ref(tag):
    fields=compound(tag)
    return {'kind':fields['Kind'].value,'dimension':fields['Dimension'].value,'uuid':identity(fields['Instance']),
            'root':list(unpack_pos(fields['Root'].value)),'registry':fields['Registry'].value}

def snapshot(world):
    rows={}
    for path in sorted(world.rglob('*')):
        if not path.is_file():continue
        require(path.resolve().is_relative_to(world),'World symlink leaves isolated world')
        relative=path.relative_to(world).as_posix()
        if relative=='session.lock':continue
        rows[relative]={'bytes':path.stat().st_size,'sha256':digest(path)}
    return rows

def fixture_map(author):
    rows={row['key']:row for row in author['fixtures']}
    require(len(rows)==len(author['fixtures']),'Duplicate scene fixture key')
    for key in ['lamp_A','lamp_B','lamp_C','lamp_D','lever_1','lever_2','lever_3','architecture_door','rp_door','wood_gate']:
        require(key in rows,'Missing explicit author fixture: '+key)
    return rows

def verify_creative(world,allow_no_joined_player=False):
    level=compound(compound(read_nbt(world/'level.dat').root)['Data']);require(level['GameType'].value==1 and level['allowCommands'].value==1,'Delivered world must already be Creative with cheats')
    if 'Player' in level:require(compound(level['Player'])['playerGameType'].value==1,'Embedded saved player is not Creative')
    players=[]
    for path in sorted((world/'playerdata').glob('*.dat')):
        fields=compound(read_nbt(path).root);require(fields['playerGameType'].value==1,'Actual saved player is not Creative');players.append({'file':path.name,'playerGameType':1})
    require(players or allow_no_joined_player,'Actual joined client player save missing')
    return {'levelGameType':1,'allowCommands':True,'savedPlayers':players,'neverJoinedPristineBaselineAllowed':allow_no_joined_player}

def baseline_controls(world,source,fixtures,rp):
    for key in ['lamp_A','lamp_B','lamp_C','lamp_D','lever_1','lever_2','lever_3']:require(fixtures[key]['uuid'] in rp,'Fresh usable stand lost '+key)
    names=['bloodborne_dw_mechanism_rules.dat','bloodborne_dw_object_policies.dat','bloodborne_rp_lamps.dat'];rows=[]
    for name in names:
        original,current=source/'data'/name,world/'data'/name
        if original.is_file():require(current.is_file() and read_nbt(current)==read_nbt(original),'Fresh baseline saved control state changed on reopen: '+name)
        if not current.is_file():rows.append({'file':name,'present':False});continue
        saved=data_file(world,name)
        if name=='bloodborne_dw_mechanism_rules.dat':require(not saved.get('Rules').value and not saved.get('Queue').value,'Fresh delivery must have no authored GUI rules/effects')
        if name=='bloodborne_dw_object_policies.dat':require(not saved.get('Entries').value,'Fresh delivery must have no configured manual policies/event commands')
        if name=='bloodborne_rp_lamps.dat':
            require(saved['Schema'].value==2 and not saved['Lines'].value,'Fresh delivery must not claim GUI-authored lines/routes')
            nodes=[compound(n) for n in saved['Nodes'].value]
            require(len(nodes)==1 and identity(nodes[0]['Lamp'])==fixtures['lamp_D']['uuid'] and not nodes[0]['Routes'].value,'Only the author-declared unlinked remote D node may be registered in pristine delivery')
        rows.append({'file':name,'present':True,'typedSavedStateSha256':typed_sha(read_nbt(current).root),'newEmptyPersistenceFile':not original.exists()})
    scores=data_file(world,'scoreboard.dat');counter=[compound(x)['Score'].value for x in scores['PlayerScores'].value if compound(x)['Name'].value=='event_counter' and compound(x)['Objective'].value=='dw_v10_gui'];require(counter==[0],'Pristine delivery counter must precede actual GUI transitions')
    return {'usableLocalLamps':3,'usableRemoteLamps':1,'usableLevers':3,'configuredGuiRules':0,'configuredGuiLines':0,'remoteDRegistration':'AUTHOR_ONLY_EXPLICIT_UNLINKED_NODE','harmlessCommandCounter':0,'savedControlFiles':rows,'status':'PASS_PRISTINE_FIXTURES_NO_GUI_GRAPH_IMPORTED'}

def verify_saved(world,source,author,client,fresh_baseline=False):
    fixtures=fixture_map(author);roots,bes,_=inventory(world);old_roots,old_bes,_=inventory(source)
    require(roots==old_roots and bes==old_bes,'Production-only reopen changed a native root or full typed block entity')
    require(read_nbt(world/'data/bloodborne_dw_composite_owners.dat')==read_nbt(source/'data/bloodborne_dw_composite_owners.dat'),'Actual owner ledger typed NBT changed on production reopen')
    rp,prior_rp=full_rp_entities(world),full_rp_entities(source)
    require(set(rp)==set(prior_rp),'Production reopen changed saved RP UUID set')
    stable=('id','UUID','Pos','Rotation','Open','Locked','Scale','VerticalOffset','Links','CustomName','DogsVisible','RpBehaviourVersion','LadderDeployTicks')
    for key,entity in rp.items():
        fields,prior=compound(entity),compound(prior_rp[key])
        require({k:fields[k] for k in stable if k in fields}=={k:prior[k] for k in stable if k in prior},'Saved RP identity/pose/control fields changed: '+key)
    removed=set() if fresh_baseline else {'lamp_C','lever_3'};rp_rows=[]
    for key,row in fixtures.items():
        if row['kind']!='RP':continue
        uid=row['uuid']
        if key in removed:
            require(uid not in rp,'Intentional actual GUI removal did not persist: '+key);continue
        require(uid in rp,'Saved fixture RP UUID missing: '+key);fields=compound(rp[uid])
        require(fields['id'].value==row['entityRegistryId'],'Saved registry changed for '+key)
        rp_rows.append({'key':key,'uuid':uid,'registry':fields['id'].value,'position':values(fields['Pos']),
                        'open':bool(default(fields,'Open')),'verticalOffset':default(fields,'VerticalOffset',0),
                        'stableReopenFieldsEqual':True,'typedNbtSha256':typed_sha(rp[uid])})
    expected={tuple(row['root']):row for row in fixtures.values() if row['kind']=='ARCHITECTURE'}
    showcase=author['sourceLadderShowcase'];expected[tuple(showcase['root'])]={'uuid':showcase['uuid'],'registryId':'bloodborne_dw:prototype_ladder','key':'source_template'}
    expected_registry={row['registryId'] for row in expected.values()}
    actual={pos:state for pos,state in roots.items() if state_parts(state)[0] in expected_registry}
    require(set(actual)==set(expected),'Saved architecture root set differs from declared stand+source template')
    owner_rows=[];owners={}
    samples={tuple(row['root']):row for row in author['architectureSamples']}
    require(len(samples)==18,'Exactly18 independent authored height samples required')
    for pos,row in expected.items():
        state,props=state_parts(actual[pos]);require(state==row['registryId'],'Wrong saved art registry at '+str(pos))
        require(pos in bes,'Architecture owner BE missing');fields=compound(bes[pos])
        if 'resident' in fields:o=owner(fields['resident'])
        else:
            require(pos==tuple(showcase['root']) and props.get('source_clone')=='true' and 'Owner' in fields and 'Original' in fields,'Only the unmoved recognized source-ladder root may use its existing private typed owner fields')
            o=(identity(fields['Owner']),state,unpack_pos(fields['Root'].value));require(unpack_pos(fields['FixedBacking'].value)==tuple(showcase['backing']),'Source fixed installation role moved')
        require(o==(row['uuid'],state,pos) and o[0] not in owners,'Saved root/UUID/type duplicated or changed')
        payload=compound(fields['payload']);offset=default(payload,'VerticalOffset',0)
        if pos in samples:require(offset==samples[pos]['expectedOffset'],'Saved authored height differs for '+str(pos))
        owners[o[0]]=(o,fields['payload']);owner_rows.append({'root':list(pos),'uuid':o[0],'registry':state,'state':actual[pos],
            'verticalOffset':offset,'fullTypedBeEqualOnReopen':True,'typedNbtSha256':typed_sha(bes[pos])})
    ledger=data_file(world,'bloodborne_dw_composite_owners.dat');counts={uid:0 for uid in owners};seen=set()
    for raw in ledger['cells'].value:
        cell=compound(raw);pos=tuple(cell['pos'].value);require(pos not in seen and cell['owners'].value,'Duplicate/empty saved owner ledger cell');seen.add(pos);local=set()
        for contribution in cell['owners'].value:
            entry=compound(contribution);o=owner(entry['owner']);require(o[0] in owners and o==owners[o[0]][0] and o[0] not in local,'Stale/foreign/duplicate ledger owner')
            local.add(o[0]);counts[o[0]]+=1;cached=compound(entry['rootData']);root_state,root_props=state_parts(actual[o[2]])
            require(cached['id'].value==root_state and {k:v.value for k,v in compound(cached['properties']).items()}==root_props,'Cached root state differs from actual saved root')
            require(decode_nbt(cached['payload'].value).root==owners[o[0]][1],'Cached typed payload differs from actual root')
        if pos in bes and compound(bes[pos])['id'].value=='bloodborne_dw:composite_cell':require(compound(bes[pos])['owners']==cell['owners'],'Actual helper carrier differs from ledger')
    for pos,sample in samples.items():require(counts[sample['uuid']]>0,'Shifted sample lost all helper/overlay owner contributions')
    for pos,tag in bes.items():
        fields=compound(tag)
        if fields['id'].value=='bloodborne_dw:composite_cell':require(pos in seen,'Saved orphan composite helper/root without ledger')
    if fresh_baseline:
        return {'rootCount':len(expected),'architectureSampleCount':18,'architectureOwners':owner_rows,'ledgerCells':len(seen),'ownerContributionCounts':counts,
            'rpFixtures':rp_rows,'totalRpEntities':len(rp),'intentionalRemovedFixtures':[],'creativeLevelAndAllSavedPlayers':verify_creative(world,True),
            'allRootAndForeignBlockEntitiesTypedEqual':True,'freshStand':baseline_controls(world,source,fixtures,rp),
            'guiPersistenceEvidence':'Separate actual client AUTHOR/REENTER worlds and their persisted snapshots; NOT the delivered pristine world'}
    rule_state=data_file(world,'bloodborne_dw_mechanism_rules.dat');rules=[compound(x) for x in rule_state['Rules'].value]
    qa_rules=[row for row in rules if row['Name'].value=='QA All'];require(len(qa_rules)==1,'Exact saved QA All rule missing/duplicated');rule=qa_rules[0]
    require(rule['Condition'].value=='ALL' and rule['Incomplete'].value==1 and rule['Satisfied'].value==0,'Saved ALL incomplete condition/state wrong')
    sources=[ref(x) for x in rule['Sources'].value];targets=[ref(x) for x in rule['Targets'].value]
    require({x['uuid'] for x in sources}=={fixtures[k]['uuid'] for k in ['lever_1','lever_2']},'Saved mandatory sources do not match intentional source removal')
    require({x['uuid'] for x in targets}=={fixtures[k]['uuid'] for k in ['architecture_door','rp_door','wood_gate']},'Saved exact target identities changed')
    flags={identity(compound(x)['Id']):bool(compound(x)['Active'].value) for x in rule_state['Flags'].value}
    require(all(not flags.get(x['uuid'],True) for x in sources) and fixtures['lever_3']['uuid'] not in flags and not rule_state['Queue'].value,'Saved reset flags or removed-source/pending effects were not persisted')
    policy_state=data_file(world,'bloodborne_dw_object_policies.dat');policy_rows=[compound(x) for x in policy_state['Entries'].value]
    door_uuid=fixtures['architecture_door']['uuid'];door=[x for x in policy_rows if ref(x['Ref'])['uuid']==door_uuid]
    require(len(door)==1 and door[0]['LeversOnly'].value==1 and values(door[0]['Open'])==[COMMAND] and values(door[0]['Close'])==[COMMAND],'Saved levers-only door and ordered phase command lists differ')
    require(state_parts(actual[tuple(fixtures['architecture_door']['root'])])[1]['open']=='false' and not compound(rp[fixtures['rp_door']['uuid']])['Open'].value,'Balanced actual rule scenario must save both doors closed')
    lamps=data_file(world,'bloodborne_rp_lamps.dat');nodes=[compound(x) for x in lamps['Nodes'].value];by_entity={identity(x['Lamp']):x for x in nodes}
    require(fixtures['lamp_C']['uuid'] not in by_entity,'Destroyed lamp still exists in saved network')
    required_names={'lamp_A':'QA A renamed','lamp_B':'QA B','lamp_D':'QA D'}
    for key,name in required_names.items():require(fixtures[key]['uuid'] in by_entity and by_entity[fixtures[key]['uuid']]['Name'].value==name,'Saved explicit lamp name/UUID differs: '+key)
    node_ids={key:identity(by_entity[fixtures[key]['uuid']]['Id']) for key in required_names};lines=[compound(x) for x in lamps['Lines'].value];main=[x for x in lines if x['Name'].value=='QA Main'];require(len(main)==1,'Saved named explicit line missing')
    directed=set();connections=[]
    for raw in main[0]['Connections'].value:
        x=compound(raw);a,b=identity(x['A']),identity(x['B']);forward,reverse=bool(x['AtoB'].value),bool(x['BtoA'].value)
        if forward:directed.add((a,b))
        if reverse:directed.add((b,a))
        connections.append({'id':identity(x['Id']),'a':a,'b':b,'aToB':forward,'bToA':reverse})
    require(directed=={(node_ids['lamp_A'],node_ids['lamp_B']),(node_ids['lamp_A'],node_ids['lamp_D']),(node_ids['lamp_D'],node_ids['lamp_A'])},'Saved actual directed/bidirectional lamp links differ')
    for name in ['bloodborne_dw_mechanism_rules.dat','bloodborne_dw_object_policies.dat','bloodborne_rp_lamps.dat']:
        require(read_nbt(world/'data'/name)==read_nbt(source/'data'/name),'Saved rules/policy/lamps full typed data changed on production-only reopen: '+name)
    players=verify_creative(world)
    menu=client['menusActual'];require(menu.get('ordinaryClientReentryActual')=='PASS_DISTINCT_SAVED_WORLD_GUI' and menu.get('savedRemoteDestinationReloadActual')=='PASS_ORDINARY_GUI_SAVED_UUID_LOAD_ARRIVAL_AND_LEASE_RELEASE','Actual distinct saved-world GUI and remote arrival required')
    observed=menu['persistedStateAfter']['state']
    expected_rule={'uuid':identity(rule['Id']),'name':rule['Name'].value,'order':rule['Order'].value,'condition':rule['Condition'].value,'effect':rule['Effect'].value,'incomplete':bool(rule['Incomplete'].value),'satisfied':bool(rule['Satisfied'].value),
                   'sources':[dict(x,savedActive=flags.get(x['uuid'],False)) for x in sources],'targets':targets}
    require(observed['rule']==expected_rule,'Offline typed saved rule differs from actual REENTER semantic snapshot')
    expected_flags={key:{'uuid':fixtures[key]['uuid'],'ruleMember':fixtures[key]['uuid'] in {x['uuid'] for x in sources},'savedActive':flags.get(fixtures[key]['uuid'],False)} for key in ['lever_1','lever_2','lever_3']}
    require(observed['sourceFlags']==expected_flags,'Offline saved source flags differ from actual snapshot')
    saved_graph={'schema':lamps['Schema'].value,'nodes':[],'lines':[]}
    for n in nodes:saved_graph['nodes'].append({'nodeUuid':identity(n['Id']),'entityUuid':identity(n['Lamp']),'name':n['Name'].value,'dimension':n['Dimension'].value,'root':list(unpack_pos(n['Pos'].value)),'position':[n[k].value for k in ['X','Y','Z']],'routes':ids(n['Routes'])})
    for line in lines:saved_graph['lines'].append({'lineUuid':identity(line['Id']),'name':line['Name'].value,'connections':[{'connectionUuid':identity(compound(x)['Id']),'a':identity(compound(x)['A']),'b':identity(compound(x)['B']),'aToB':bool(compound(x)['AtoB'].value),'bToA':bool(compound(x)['BtoA'].value)} for x in line['Connections'].value]})
    require(observed['lampGraph']==saved_graph,'Offline saved schema2 lamp nodes/names/coordinates/routes/ordered lines differ from actual snapshot')
    policies={ref(row['Ref'])['uuid']:row for row in policy_rows};target_refs={row['uuid']:row for row in targets}
    for key in ['architecture_door','rp_door','wood_gate']:
        uid=fixtures[key]['uuid'];actual=observed['targets'][key];policy=policies.get(uid)
        require(actual['identity']==target_refs[uid] and actual['leversOnly']==bool(default(policy or {},'LeversOnly')) and actual['afterOpen']==(values(policy['Open']) if policy else []) and actual['afterClose']==(values(policy['Close']) if policy else []) and actual['pendingEffect'] is False,'Offline saved target identity/manual/ordered-command snapshot differs: '+key)
        if key!='architecture_door':
            fields=compound(rp[uid]);require(actual['open']==bool(default(fields,'Open')) and actual['locked']==bool(default(fields,'Locked')) and actual['verticalOffset']==default(fields,'VerticalOffset',0),'Offline saved RP target state differs from actual snapshot')
    scoreboard=data_file(world,'scoreboard.dat');score_rows=[compound(x) for x in scoreboard['PlayerScores'].value]
    counter=[x['Score'].value for x in score_rows if x['Name'].value=='event_counter' and x['Objective'].value=='dw_v10_gui']
    require(counter==[2] and observed['harmlessCommandCounter']==2,'Offline saved harmless event counter differs from observed two actual transitions')
    return {'rootCount':len(expected),'architectureSampleCount':18,'architectureOwners':owner_rows,'ledgerCells':len(seen),'ownerContributionCounts':counts,
        'rpFixtures':rp_rows,'totalRpEntities':len(rp),'intentionalRemovedFixtures':sorted(removed),'rule':{'uuid':identity(rule['Id']),'order':rule['Order'].value,'condition':'ALL','effect':rule['Effect'].value,'incomplete':True,'sources':sources,'targets':targets,'flags':flags,'pendingEffects':0},
        'doorPolicy':{'uuid':door_uuid,'leversOnly':True,'afterOpen':[COMMAND],'afterClose':[COMMAND],'savedBothDoorsOpen':False},
        'lamps':{'nodes':{k:{'id':node_ids[k],'entityUuid':fixtures[k]['uuid'],'name':required_names[k]} for k in node_ids},'lineUuid':identity(main[0]['Id']),'line':'QA Main','connections':connections},
        'creativeLevelAndAllSavedPlayers':players,'allRootAndForeignBlockEntitiesTypedEqual':True,'savedRulePolicyLampTypedEqualOnReopen':True,
        'actualSnapshotComparedToOfflineTypedRuleFlagsPoliciesLampGraphCounter':'PASS','reenterSemanticSnapshotSha256':menu['persistedStateAfter']['sha256']}

def run(a):
    sha=digest(a.jar);types=expected_types(a.jar)
    _,author_client=gate_client(a.author_client_report,sha,types,'AUTHOR');_,client=gate_client(a.client_reenter_report,sha,types,'REENTER')
    author_wrapper=read(a.author_report);exact_artifact(author_wrapper,sha,'fresh author');require(author_wrapper['status']=='PASS' and author_wrapper['exit_code']==0,'Actual author failed normal exit')
    author=raw_output(author_wrapper,'review_scene_output');exact_artifact(author,sha,'actual author output');require(author['status']=='PASS_SCENE_AUTHORED_REQUIRES_PRODUCTION_REOPEN','Actual author incomplete')
    reopen=read(a.server_report);exact_artifact(reopen,sha,'production-only reopen');require(reopen['status']=='PASS' and reopen['exit_code']==0 and not reopen.get('termination'),'Production-only reopen/save/normal exit required')
    require(not any(row.get('extra_mod') for row in reopen['modset']),'Production reopen must exclude QA author/client addon')
    copied=reopen['derived_world_copy'];source=resolve(copied['source'],ROOT);world=a.world
    require(copied['copy_byte_verification']=='PASS' and copied['source_unchanged_after_run'] is True,'Production-only source copy verification failed')
    if a.fresh_baseline:
        require(source==resolve(author_wrapper['run_directory'],ROOT)/'isolated-smoke-world','Fresh production-only reopen must copy the actual successful author baseline')
        copy=read(a.author_client_report)['derived_world_copy'];require(resolve(copy['source'],ROOT)==source and copy['copy_byte_verification']=='PASS' and copy['source_unchanged_after_run'] is True,'Actual first-entry Creative client did not use this unchanged pristine baseline')
        for row in copy['files']:
            path=source/row['path'];require(path.stat().st_size==row['bytes'] and digest(path)==row['sha256'],'Pristine actual first-entry source bytes changed: '+row['path'])
    else:require(source==resolve(client['actualWorldDirectory'],ROOT),'Production-only reopen must copy the actual REENTER saved world')
    reenter_wrapper=read(a.client_reenter_report);require(resolve(reenter_wrapper['derived_world_copy']['source'],ROOT)==resolve(author_client['actualWorldDirectory'],ROOT),'REENTER did not load the actual ordinary AUTHOR saved world')
    require(world==resolve(reopen['run_directory'],ROOT)/'isolated-smoke-world','Archive is not the actual production-reopened world')
    require(world.is_relative_to((ROOT/'build').resolve()) and source.is_relative_to((ROOT/'build').resolve()) and (world/'level.dat').is_file(),'Only isolated build worlds allowed')
    for row in copied['files']:
        path=source/row['path'];require(path.stat().st_size==row['bytes'] and digest(path)==row['sha256'],'Copied final client world changed: '+row['path'])
    actual=verify_saved(world,source,author,client,a.fresh_baseline);before=snapshot(world)
    require(not a.output.is_relative_to(world) and not a.output.exists(),'Archive output cannot overwrite/add to saved world')
    a.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        folder=zipfile.ZipInfo(PREFIX,(2026,10,8,0,0,0));folder.create_system=3;folder.external_attr=0o40755<<16;z.writestr(folder,b'')
        for relative,row in before.items():
            content=(world/relative).read_bytes();require(len(content)==row['bytes'] and hashlib.sha256(content).hexdigest()==row['sha256'],'World file changed during archive read')
            info=zipfile.ZipInfo(PREFIX+relative,(2026,10,8,0,0,0));info.create_system=3;info.external_attr=0o100644<<16;info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,content)
    with zipfile.ZipFile(a.output) as z:
        require(z.testzip() is None and set(z.namelist())=={PREFIX}|{PREFIX+p for p in before},'World ZIP CRC/entry set mismatch')
        for relative,row in before.items():
            content=z.read(PREFIX+relative);require(len(content)==row['bytes'] and hashlib.sha256(content).hexdigest()==row['sha256'],'Archived file not byte-exact')
    require(snapshot(world)==before,'Source world changed during archive write')
    evidence={key:{'path':str(getattr(a,key)),'sha256':digest(getattr(a,key))} for key in ['author_report','author_client_report','client_reenter_report','server_report']}
    return {'schema':'dreamwalker-review-v10-scene-archive-v1','status':'PASS_VERIFIED_PRODUCTION_ONLY_SAVED_V10_WORLD_ARCHIVE','production_jar_sha256':sha,'productionOnlyWorld':True,
        'world':str(world),'source':str(source),'archive':str(a.output),'archive_sha256':digest(a.output),'prefix':PREFIX,
        'file_count':len(before),'uncompressed_bytes':sum(row['bytes'] for row in before.values()),'file_manifest':[dict(path=p,**row) for p,row in before.items()],
        'omitted_files':['session.lock'],'zipCrcAndEveryEntryByteVerification':'PASS','sourceWorldUnchangedDuringArchive':'PASS','evidence':evidence,'savedState':actual,
        'deliveryMode':'FRESH_PRISTINE_AUTHOR_BASELINE' if a.fresh_baseline else 'ACTUAL_GUI_AUTHOR_REENTER_SAVED_WORLD',
        'manualVisualAcceptance':'PENDING_USER_REVIEW','fullTask':'NOT_READY_FULL_CATALOGUE_NUMERIC_IDS_CITY_CONVERSION','originalCityIncluded':False}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['jar','author-report','author-client-report','client-reenter-report','server-report','world','output','report']:parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--fresh-baseline',action='store_true',help='Deliver the pristine successful author baseline; actual GUI deletion/persistence worlds remain separate evidence')
    a=parser.parse_args()
    for name,value in vars(a).items():
        if isinstance(value,Path):setattr(a,name,resolve(value,ROOT))
    require(a.output.is_relative_to((ROOT/'build').resolve()) and not a.report.exists(),'Fresh output under build and fresh report required')
    try:result=run(a)
    except Exception as failure:result={'schema':'dreamwalker-review-v10-scene-archive-v1','status':'FAIL','error':str(failure),'manualVisualAcceptance':'PENDING_USER_REVIEW'}
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8');print(json.dumps({k:result[k] for k in ['status','archive_sha256','file_count','error'] if k in result}))
    if result['status']=='FAIL':raise SystemExit(1)

if __name__=='__main__':main()
