"""Cheap offline checks; no JVM, world authoring or source preparation scan."""
import ast, json, traceback
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'build/v1271-gallery-tooling-check.json'
FILES=['prepare_gallery_v1271_input.py','build_gallery_v1271_addon.py','verify_gallery_v1271.py','gallery_v1271_alias_contract.py','finalize_gallery_v1271_world.py','prepare_gallery_v1271_delivery.py','package_gallery_v1271.py','verify_v1271_final_readiness.py','audit_v1271_prepared_decision_usage.py','package_v1271_preparation.py','plan_gallery_v1271_workflow.py','run_gallery_v1271_server_offline.py','run_gallery_v1271_workflow.py']
try:
    for name in FILES:ast.parse((ROOT/'tools'/name).read_text(encoding='utf8'),filename=name)
    from prepare_gallery_v1271_input import planned_flower_offset,refresh_known_source_owner
    proof=json.loads((ROOT/'docs/v12.7/native-evidence/final31/captured-build/gametest-tests28/world/v127-source-model-offset-native-proof.json').read_text(encoding='utf-8-sig'))
    assert proof['status']=='PASS_NATIVE_EXACT_ORIGINAL_SOURCE_MODEL_OFFSETS'
    mismatches=[{'position':row['position'],'expected':row['nativeSourceModelOffset'],'actual':planned_flower_offset(row['position'])} for row in proof['rows'] if max(abs(a-b) for a,b in zip(planned_flower_offset(row['position']),row['nativeSourceModelOffset']))>=1e-12]
    assert not mismatches,mismatches[:5]
    # These append-only source deltas must update art, never move root/UUID or
    # reinterpret unrelated frozen whole-construction rows.
    import copy
    optical={'registryId':'bloodborne_dw:source_snow_height12','properties':{'variant':'3','art_bank':'0','rotation':'2'},'relativePosition':[4,7,-3],'instanceUuid':'frozen-source-uuid','payload':{'Original':{'opaque':[1,2,3]},'SourceShift':[.2,0,-.1]}}
    decision={'action':'COMPACT','registryId':optical['registryId'],'properties':{'variant':'11','art_bank':'1','rotation':'2','compact':'true','profile':'base'},'payload':{'SourceShift':[.2,0,-.1]},'sourceState':'minecraft:quartz_stairs[facing=north]','reason':'EXACT_SOURCE_NATIVE_OPTICAL_CARRIER_POSE'}
    numbers={optical['registryId']:{'temporaryId':'90935'},'bloodborne_dw:source_compatibility_fragment':{'temporaryId':'93234'}}
    assert refresh_known_source_owner(optical,decision,numbers)==decision['reason']
    assert optical['properties']['variant']=='11' and optical['properties']['art_bank']=='1' and 'compact' not in optical['properties']
    assert optical['relativePosition']==[4,7,-3] and optical['instanceUuid']=='frozen-source-uuid' and optical['payload']['Original']=={'opaque':[1,2,3]}
    protected={'registryId':'bloodborne_dw:prototype_ladder','properties':{},'relativePosition':[1,2,3],'instanceUuid':'unchanged','payload':{'SourceShift':[999,999,999],'OriginalTypedNbt':'opaque'}}
    decision={'action':'OWNER','registryId':'bloodborne_dw:source_compatibility_fragment','properties':{'variant':'2','art_bank':'7','rotation':'0','profile':'base'},'payload':{'ImportedTypeVersion':1},'sourceShift':[0,-.25,0],'sourceState':'minecraft:beehive[facing=east,honey_level=0]','reason':'EXACT_HIDDEN_SINGLE_BEEHIVE_SOURCE_LADDER'}
    refresh_known_source_owner(protected,decision,numbers)
    assert protected['sourceShift']==[0,-.25,0] and protected['properties']['art_bank']=='7' and protected['payload']['OriginalTypedNbt']=='opaque' and 'SourceShift' not in protected['payload']
    assert protected['relativePosition']==[1,2,3] and protected['instanceUuid']=='unchanged'
    untouched=copy.deepcopy(protected);assert refresh_known_source_owner(protected,{'action':'CONSUMED','reason':'EXACT_COMPLETE_SOURCE_CONSTRUCTION'},numbers) is None and protected==untouched
    # Guard the exact native union path: five fixed applications all contribute
    # to bounds. An apply array would be weighted and silently lose four parts.
    import io,zipfile
    from prepare_gallery_v1271_input import passthrough_bounds
    class ProbeModels:
        def quads(self,part):
            n=int(part['model'].rsplit('_',1)[1]);return [([[n*16,0,0],[n*16+16,0,0],[n*16+16,16,16],[n*16,16,16]],None,None,None)]
    buffer=io.BytesIO()
    with zipfile.ZipFile(buffer,'w') as fake:
        fake.writestr('assets/bloodborne_dw/blockstates/source_fallback_multipart_carrier.json',json.dumps({'multipart':[{'when':{'source':'65','source_bank':'0'},'apply':{'model':f'probe:model_{n}'}} for n in range(5)]}))
        targets=['unused']*66;targets[65]='bloodborne_dw:source_fallback_multipart_carrier';applications=[[]]*66;applications[65]=[{'model':f'probe:model_{n}'} for n in range(5)]
        fake.writestr('bloodborne_dw/source-fallback-carriers-v1271.json',json.dumps({'targetRegistryIds':targets,'sourceApplicationSets':applications}))
    with zipfile.ZipFile(buffer) as fake:
        native={'registryId':'bloodborne_dw:source_fallback_multipart_carrier','properties':{'source':'65','profile':'base'},'relativePosition':[2,3,4]}
        assert passthrough_bounds(native,fake,ProbeModels())==[2.,3.,4.,7.,4.,5.]
        assert native['properties']['source_bank']=='0'
    banked=io.BytesIO()
    with zipfile.ZipFile(banked,'w') as fake:
        fake.writestr('assets/bloodborne_dw/blockstates/source_fallback_solid_multipart_carrier.json',json.dumps({'multipart':[{'when':{'source':'0','source_bank':'1'},'apply':{'model':f'probe:model_{n}'}} for n in range(5,10)]+[{'when':{'source':'0','source_bank':'0'},'apply':{'model':'probe:model_1000'}}]}))
        targets=['unused']*129;targets[128]='bloodborne_dw:source_fallback_solid_multipart_carrier';applications=[[]]*129;applications[128]=[{'model':f'probe:model_{n}'} for n in range(5,10)]
        fake.writestr('bloodborne_dw/source-fallback-carriers-v1271.json',json.dumps({'targetRegistryIds':targets,'sourceApplicationSets':applications}))
    with zipfile.ZipFile(banked) as fake:
        native={'registryId':'bloodborne_dw:source_fallback_solid_multipart_carrier','properties':{'source':'0','source_bank':'1','profile':'base'},'relativePosition':[2,3,4]}
        assert passthrough_bounds(native,fake,ProbeModels())==[7.,3.,4.,12.,4.,5.]
    from gallery_v1271_alias_contract import AliasContract
    old,mid,new=['bloodborne_dw:source_'+v for v in ('old','mid','new')]
    aliases=io.BytesIO()
    with zipfile.ZipFile(aliases,'w') as fake:
        fake.writestr('bloodborne_dw/imported-type-aliases.json',json.dumps({'schemaVersion':1,'groups':[{'primaryRegistryId':mid,'members':[old,mid]}],'mappings':[{'oldRegistryId':old,'oldVariant':2,'newRegistryId':mid,'newVariant':4,'newId':'90101','rotationDelta':2,'sourceShift':[.5,0,-.25],'properties':{}}]}))
        fake.writestr('bloodborne_dw/imported-type-aliases-v127.json',json.dumps({'schemaVersion':1,'groups':[{'primaryRegistryId':new,'members':[mid,new]}],'mappings':[{'oldRegistryId':mid,'oldVariant':4,'newRegistryId':new,'newVariant':7,'newId':'90102','rotationDelta':0,'sourceShift':[0,0,0],'properties':{}}]}))
        fake.writestr('bloodborne_dw/imported-stair-types.json',json.dumps({'stairTypes':[],'ladderTypes':[]}))
        fake.writestr('bloodborne_dw/composite/source_new.json',json.dumps({'variants':[{}]*8}))
    with zipfile.ZipFile(aliases) as fake:contract=AliasContract(fake)
    row={'key':'city/frozen-uuid','registry':old,'root':[10,64,20],'properties':{'variant':'2','rotation':'6','art_bank':'0','profile':'base','open':'false'},'placedState':{'variant':'7','rotation':'0','art_bank':'0','profile':'base','open':'false'},'sourceShift':[1,0,0],'payload':{'OriginalSourceState':'minecraft:stone'}}
    actual={'nbt':{'payload':{'ImportedTypeVersion':1,'SourceShift':[.5,0,-1.25],'OriginalSourceState':'minecraft:stone'}}};unchanged=copy.deepcopy(row)
    assert contract.verify(row,actual,[100,64,200])==new and row==unchanged and contract.report()['normalizedOwners']==1
    for wrong in ('art_bank','variant'):
        malformed=copy.deepcopy(row);malformed['placedState'][wrong]='1'
        try:contract.verify(malformed,actual,[100,64,200]);raise RuntimeError('Bad alias selector accepted')
        except AssertionError:pass
    malformed=copy.deepcopy(actual);malformed['nbt']['payload']['SourceShift']=[0,0,0]
    try:contract.verify(row,malformed,[100,64,200]);raise RuntimeError('Bad alias shift accepted')
    except AssertionError:pass
    # Inspect workflow argument names through AST only. No main/import side
    # effects and no JVM are needed to catch a misspelled delivery CLI option.
    workflow=ROOT/'build/v1271-gallery/workflow.json'
    if workflow.is_file():
        for step in json.loads(workflow.read_text(encoding='utf8'))['steps']:
            script=Path(step['command'][1]);tree=ast.parse(script.read_text(encoding='utf8'))
            if script.name in {'verify_gallery_v1271.py','finalize_gallery_v1271_world.py'}:continue # Delegates to reviewed V127 parser.
            allowed={arg.value for node in ast.walk(tree) if isinstance(node,ast.Call) and isinstance(node.func,ast.Attribute) and node.func.attr=='add_argument' for arg in node.args if isinstance(arg,ast.Constant) and isinstance(arg.value,str) and arg.value.startswith('--')}
            if script.name=='run_gallery_v1271_server_offline.py':
                delegated=ast.parse((ROOT/'tools/run_v11_server.py').read_text(encoding='utf8'))
                allowed.update(arg.value for node in ast.walk(delegated) if isinstance(node,ast.Call) and isinstance(node.func,ast.Attribute) and node.func.attr=='add_argument' for arg in node.args if isinstance(arg,ast.Constant) and isinstance(arg.value,str) and arg.value.startswith('--'))
            supplied={v for v in step['command'][2:] if v.startswith('--')}
            assert supplied<=allowed,(script.name,sorted(supplied-allowed))
    init=ROOT/'build/v1271-gallery-addon-final/gallery.init.gradle'
    if init.is_file():
        generated=init.read_text(encoding='utf8');folder=init.parent
        assert f"java.srcDirs=['{(folder/'java').as_posix()}'];resources.srcDirs=['{(folder/'resources').as_posix()}']" in generated
        assert '__QA_JAVA_ROOT__' not in generated and '__QA_RESOURCE_ROOT__' not in generated and 'REC:/' not in generated
    result={'status':'PASS_STATIC_TOOLING_AND_64_REAL_NATIVE_OFFSET_REFERENCES','pythonFilesParsed':FILES,'actualRecordedNativeOffsetPositionsCompared':len(proof['rows']),'sourceDeltaRegressionChecks':['opticalPoseAppendPreservesUuidCoordinatesOpaquePayload','protectedLadderHiddenPoseReplacesStaleShift','unrelatedWholeSourceConstructionUntouched','allFiveNativeMultipartApplicationsReserveBounds','nativeSourceBankOneSelectsOnlyOwnApplicationsAndPrefixDefaultsBankZero','exactAliasCompositionSelectorsShiftAndArchivedSourceRejectWrongStateWithoutChangingInput','workflowCliOptionsMatchParsers','addonJavaAndResourceDirectoriesDistinctAndExact'],'nativeAuthorAndReopen':'NOT_EXECUTED_BY_THIS_STATIC_CHECK','jvmStarted':False}
except BaseException:
    result={'status':'FAIL','error':traceback.format_exc()}
finally:
    OUT.parent.mkdir(parents=True,exist_ok=True);OUT.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    if result['status']=='FAIL':raise RuntimeError(result['error'])
