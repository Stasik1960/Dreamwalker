"""Prepare finite native source art/physics contexts before native audit.

This stage changes resources/probes only. Prepared city SQLite/runtime streams
remain untouched until the planned targets pass the complete native flag audit.
"""
import copy,json,sqlite3,traceback
from pathlib import Path
from prepare_v1271_city_compatibility import ROOT,RES,OUT,read,write,sha,token

RESULT=ROOT/'build/v1271-banked-contexts-prepare-result.json'

def main():
    candidate=ROOT/'docs/final-city-repair/geometry/remaining-compact-optical-contexts.json';data=read(candidate)
    assert data['finiteContexts']==79 and data['occurrences']==348528 and data['preparedDecisionRecords']==80
    cfg=read(RES/'bloodborne_dw/source-fallback-carriers-v1271.json');assert len(cfg['sourceStates'])==77
    cfg['physicalOverrides']=[None]*77;cfg['sourceNativeStateContexts']=[[s] for s in cfg['sourceStates']]
    probes=read(ROOT/'build/final-city-fixes/source-fallback-target-probes.json');assert len(probes)==77
    import import_remaining_assets as assets
    exporter=assets.Source(Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW\Bloodborne-Blocks\reference-inputs\bbmc-v15-v16\bbmc_v15_resource.zip'),assets.VANILLA)
    applied={};single={};transparentMulti=[];solidMulti=[]
    for n,apps in enumerate(cfg['sourceApplicationSets']):
        exported=[]
        for app in apps:node=copy.deepcopy(app);node['model']=exporter.export_model(app['model']);exported.append(node)
        if len(apps)==1:single[n]=exported[0]
        else:
            assert cfg['targetRegistryIds'][n]=='bloodborne_dw:source_fallback_multipart_carrier'
            for node in exported:transparentMulti.append({'when':{'source':str(n),'source_bank':'0'},'apply':node})
        probes[n]['properties']['source_bank']='0'
    con=sqlite3.connect(f'file:{ROOT}/build/v1271-city-preparation/source-plan.sqlite?mode=ro',uri=True);decisions={i:json.loads(raw) for i,raw in con.execute('SELECT id,data FROM decisions')};con.close()
    headers=set()
    for context in data['contexts']:
        n=len(cfg['sourceStates']);assert n<256 and context['sourceOpaque'] and not any(context['sourceShift']) and context['mountY']==0
        apps=context['sourceApplications'];isMultipart=len(apps)>1;registry='bloodborne_dw:source_fallback_solid_multipart_carrier' if isMultipart else 'bloodborne_dw:source_fallback_carrier';props={'source':str(n%128),'source_bank':str(n//128),'profile':'base'}
        exported=[]
        for app in apps:node=copy.deepcopy(app);node['model']=exporter.export_model(app['model']);exported.append(node)
        if isMultipart:
            headers.add(token(context['nativeMultipartFirstModelHeader']))
            for node in exported:solidMulti.append({'when':{'source':str(n%128),'source_bank':str(n//128)},'apply':node})
        else:single[n]=exported[0]
        cfg['sourceStates'].append(context['sourceState']);cfg['sourceNativeStateContexts'].append(context['nativeSourceStates']);cfg['sourceApplicationSets'].append(apps);cfg['sourceModelApplications'].append(apps[0] if len(apps)==1 else apps);cfg['targetRegistryIds'].append(registry);cfg['physicalOverrides'].append({'collision':context['collision'],'outline':context['outline'],'units':16,'policy':'Exact preserved previous native COMPACT physical and selection boxes; source optics/culling still delegates native vanilla source'})
        for decisionId in context['decisionIds']:
            old=decisions[decisionId];assert old['action']=='COMPACT' and old['sourceApplications']==apps and old['sourceState'] in context['nativeSourceStates']
            applied[str(decisionId)]={'registryId':registry,'properties':props,'reason':'EXACT_FIXED_SOURCE_NATIVE_OPTICAL_CONTEXT','sourceContextIndex':n,'sourceState':old['sourceState'],'sourceApplications':apps,'previousRegistryId':old['registryId'],'previousProperties':old['properties'],'caseIds':context['caseIds']}
        sample=context['originalSamples'][0]['position']
        probes.append({'sourceState':context['sourceState'],'sourceNativeStateContexts':context['nativeSourceStates'],'decisionIds':context['decisionIds'],'decisionId':context['decisionIds'][0],'count':context['occurrences'],'registryId':registry,'properties':props,'modelCandidatesExpected':apps,'sourceOpaque':True,'sourceRenderingSeedPosition':sample,'sourceRenderingSeedDimension':context['originalSamples'][0]['dimension'],'sourcePhysicalOverrides':cfg['physicalOverrides'][-1],'sourceFrozenOpticalContextAppend':True,'modelHeaders':context['modelHeaders']})
    assert len(cfg['sourceStates'])==156 and len(applied)==80 and len(headers)==1,headers
    empty={'model':'bloodborne_dw:block/source_invisible_carrier_empty'}
    variants={}
    for bank in range(2):
        for slot in range(128):
            n=bank*128+slot
            for profile in ('base','alt'):variants[f'profile={profile},source={slot},source_bank={bank}']=single.get(n,empty)
    # Separate multipart models retain original false/true AO headers; unused
    # native state combinations have an explicit empty-model definition.
    def multipartDefinition(parts,registry):
        inactive={}
        for bank in range(2):
            for slot in range(128):
                n=bank*128+slot
                if n<len(cfg['targetRegistryIds']) and cfg['targetRegistryIds'][n]==registry:continue
                for profile in ('base','alt'):inactive[f'profile={profile},source={slot},source_bank={bank}']=empty
        return {'multipart':parts,'variants':inactive}
    for path in ('source_fallback_carrier','source_fallback_translucent_carrier'):write(RES/'assets/bloodborne_dw/blockstates'/f'{path}.json',{'variants':variants})
    write(RES/'assets/bloodborne_dw/blockstates/source_fallback_multipart_carrier.json',multipartDefinition(transparentMulti,'bloodborne_dw:source_fallback_multipart_carrier'))
    write(RES/'assets/bloodborne_dw/blockstates/source_fallback_solid_multipart_carrier.json',multipartDefinition(solidMulti,'bloodborne_dw:source_fallback_solid_multipart_carrier'))
    cfg.update(sourceBankSize=128,sourceBankCount=2,sourceContextPrefixPreserved=77,frozenOpticalContextAppendCells=348528,frozenOpticalContextAppendStates=79,finiteContextBudget=256,unusedStatePolicy='Unassigned source/bank indices use safe source0 physics and an explicit empty model; never missing-model geometry',preparedDictionaryExecutionStatus='PLANNED_NATIVE_AUDIT_BEFORE_CITY_DELTA')
    write(RES/'bloodborne_dw/source-fallback-carriers-v1271.json',cfg);write(ROOT/'build/final-city-fixes/source-fallback-target-probes.json',probes)
    debug=read(RES/'bloodborne_dw/debug_catalogue.json');assert not any(e['temporaryId']=='93240' for e in debug['entries']);debug['entries'].append({'temporaryId':'93240','finalId':None,'kind':'compatibility','name':'Исходный непрозрачный составной резервный носитель','registryId':'bloodborne_dw:source_fallback_solid_multipart_carrier','aliases':['bloodborne_dw:source_fallback_solid_multipart_carrier']});write(RES/'bloodborne_dw/debug_catalogue.json',debug)
    output=ROOT/'build/final-city-fixes/planned-native-optical-context-mapping.json';write(output,{'schemaVersion':1,'status':'PLANNED_RESOURCE_TARGETS_AWAIT_NATIVE_AUDIT','sourceContextCount':156,'sourceContextPrefix':77,'nativeContextAppendCount':79,'decisionRecords':80,'sourceCells':348528,'sourceBankSize':128,'sourceBankCount':2,'sourceCandidateSha256':sha(candidate),'sourceConfigurationSha256':sha(RES/'bloodborne_dw/source-fallback-carriers-v1271.json'),'preparedSourcePlanSha256':sha(ROOT/'build/v1271-city-preparation/source-plan.sqlite'),'preparedDictionaryWritten':False,'mappings':applied})
    write(RESULT,{'status':'PASS_RESOURCES_PREPARED_DICTIONARY_UNCHANGED','contexts':156,'decisionMappingFile':str(output),'newOpaqueMultipartModelBranches':len(solidMulti)})

if __name__=='__main__':
    try:main()
    except BaseException:write(RESULT,{'status':'FAIL','error':traceback.format_exc()});raise
