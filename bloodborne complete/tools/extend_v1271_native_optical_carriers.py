"""Preserve source glass optical flags through finite native compatibility.

Append14 fixed preselected source applications after the accepted63 source
prefix. Five multipart pane unions use a separate native multipart block;
single-model branches keep their original standard variant bake semantics.
"""
import collections,copy,gzip,json,re,sqlite3,traceback,zlib
from pathlib import Path
import numpy as np
from prepare_v1271_city_compatibility import ROOT,RES,OUT,read,write,sha,token

DEST=ROOT/'build/v1271-city-preparation';RESULT=ROOT/'build/v1271-optical-extension-result.json'

def main():
    plan=DEST/'source-plan.sqlite';runtime=DEST/'runtime';con=sqlite3.connect(plan)
    decisions={i:json.loads(raw) for i,raw in con.execute('SELECT id,data FROM decisions')};frequency=read(DEST/'decision-frequency-proof.json')
    config=read(RES/'bloodborne_dw/source-fallback-carriers-v1271.json');assert len(config['sourceStates'])==63
    inputs=[{'source':'minecraft:tinted_glass','count':18175,'decisions':[{'decisionId':363,'sourceApplications':[{'model':'minecraft:block/tinted_glass'}]}]}]+read(ROOT/'build/final-geometry/glass-optical-fallback-candidates.json')['cases']
    assert len(inputs)==14 and sum(r['count'] for r in inputs)==40135
    import import_remaining_assets as assets
    exporter=assets.Source(Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW\Bloodborne-Blocks\reference-inputs\bbmc-v15-v16\bbmc_v15_resource.zip'),assets.VANILLA)
    variants=read(RES/'assets/bloodborne_dw/blockstates/source_fallback_carrier.json')['variants'];multipart=[];changes={};checks=[]
    config['sourceApplicationSets']=[[app] for app in config['sourceModelApplications']]
    config['targetRegistryIds']=['bloodborne_dw:source_fallback_translucent_carrier' if s in config['translucentSourceStates'] else 'bloodborne_dw:source_fallback_carrier' for s in config['sourceStates']]
    probes=read(ROOT/'build/final-city-fixes/source-fallback-target-probes.json')
    for row in inputs:
        assert len(row['decisions'])==1,row;case=row['decisions'][0];i=case['decisionId'];old=decisions[i];state=row['source'];apps=case['sourceApplications']
        assert old['sourceState']==state and old['action']=='COMPACT' and old['sourceApplications']==apps,(i,old,case)
        assert not any(old.get('sourceShift',[0,0,0])) and not old.get('mountY',0),i
        n=len(config['sourceStates']);assert state not in config['sourceStates'];registry='bloodborne_dw:source_fallback_multipart_carrier' if len(apps)>1 else 'bloodborne_dw:source_fallback_translucent_carrier'
        exported=[]
        for app in apps:
            node=copy.deepcopy(app);node['model']=exporter.export_model(app['model']);exported.append(node)
        if len(apps)>1:
            assert len(apps)==5,(state,apps)
            # Native multipart properties come from the first source post model
            # exactly as on the original source pane. All five pane post models
            # share the same AO declaration; no weighted application is used.
            flags={exporter.resolved(a['model']).get('ambientocclusion',True) for a in apps}
            checks.append({'sourceState':state,'postAmbientOcclusion':exporter.resolved(apps[0]['model']).get('ambientocclusion',True),'allPartAmbientOcclusionValues':sorted(flags),'applications':5})
            for app in exported:multipart.append({'when':{'source':str(n)},'apply':app})
            for profile in ('base','alt'):variants[f'profile={profile},source={n}']={'model':'bloodborne_dw:block/source_invisible_carrier_empty'}
        else:
            for profile in ('base','alt'):variants[f'profile={profile},source={n}']=exported[0]
        config['sourceStates'].append(state);config['translucentSourceStates'].append(state);config['sourceApplicationSets'].append(apps);config['sourceModelApplications'].append(apps[0] if len(apps)==1 else apps);config['targetRegistryIds'].append(registry)
        changes[i]={**copy.deepcopy(old),'action':'VANILLA','registryId':registry,'properties':{'source':str(n),'profile':'base'},'payload':{},'reason':'EXACT_NATIVE_GLASS_OPTICAL_COMPATIBILITY','previousPreparationReason':old.get('reason',old.get('resolution',''))}
        probes.append({'sourceState':state,'decisionId':i,'count':row['count'],'registryId':registry,'properties':changes[i]['properties'],'modelCandidatesExpected':apps,'sourceOpticalCompatibilityAppend':True,'sourceOpacity':15 if state=='minecraft:tinted_glass' else 0,'sourceOpaque':False})
    assert len(config['sourceStates'])==77 and len(multipart)==25 and len(changes)==14
    assert len({c['postAmbientOcclusion'] for c in checks})==1,checks
    config['opticalSourceCompatibilityAppendCells']=40135;config['multipartPolicy']='Five exact fixed native applications per all-connected pane; separate translucent multipart carrier prevents changing original single-model baked flags.'
    write(RES/'bloodborne_dw/source-fallback-carriers-v1271.json',config)
    for path in ('source_fallback_carrier','source_fallback_translucent_carrier'):write(RES/'assets/bloodborne_dw/blockstates'/f'{path}.json',{'variants':variants})
    write(RES/'assets/bloodborne_dw/blockstates/source_fallback_multipart_carrier.json',{'multipart':multipart})
    debug=read(RES/'bloodborne_dw/debug_catalogue.json');assert not any(e['temporaryId']=='93239' for e in debug['entries'])
    debug['entries'].append({'temporaryId':'93239','finalId':None,'kind':'compatibility','name':'Исходный составной резервный носитель','registryId':'bloodborne_dw:source_fallback_multipart_carrier','aliases':['bloodborne_dw:source_fallback_multipart_carrier']});write(RES/'bloodborne_dw/debug_catalogue.json',debug)
    write(ROOT/'build/final-city-fixes/source-fallback-target-probes.json',probes)
    previous={i:copy.deepcopy(decisions[i]) for i in changes}
    for i,d in changes.items():con.execute('UPDATE decisions SET data=? WHERE id=?',(token(d),i))
    con.commit();decisions.update(changes)
    # Check only sections containing one of the original native compact palette
    # entries; source/decision arrays and all owner streams remain untouched.
    names=tuple({d['registryId'] for d in previous.values()});pattern=re.compile(r'^\{"chunk":\[(-?\d+),(-?\d+)\],"dimension":"([^"]+)"');counts=collections.Counter();changed=0
    target=runtime/'sections.optical-native.jsonl.gz'
    with gzip.open(runtime/'sections.jsonl.gz','rt',encoding='utf8') as src,gzip.open(target,'wt',encoding='utf8') as out:
        for line in src:
            if not any(name in line for name in names):out.write(line);continue
            m=pattern.match(line);assert m;sy=int(line.rsplit('"sectionY":',1)[1].split('}',1)[0]);key=(m[3],int(m[1]),int(m[2]),sy)
            raw=con.execute('SELECT decision_indices FROM sections WHERE dimension=? AND cx=? AND cz=? AND sy=?',key).fetchone()[0];index=np.frombuffer(zlib.decompress(raw),dtype='<u2');hits=[int(i) for i in np.unique(index) if int(i) in changes]
            if not hits:out.write(line);continue
            row=json.loads(line);ids={token(state):n for n,state in enumerate(row['palette'])}
            for i in hits:
                d=decisions[i];old={'registryId':previous[i]['registryId'],'properties':previous[i]['properties']};at=np.flatnonzero(index==i);assert all(row['palette'][row['indices'][int(v)]]==old for v in at),(key,i)
                state={'registryId':d['registryId'],'properties':d['properties']};text=token(state)
                if text not in ids:ids[text]=len(row['palette']);row['palette'].append(state)
                for v in at:row['indices'][int(v)]=ids[text]
                counts[i]+=len(at)
            changed+=1;out.write(token(row)+'\n')
    assert sum(counts.values())==40135,counts
    for row in inputs:assert counts[row['decisions'][0]['decisionId']]==row['count'],row
    target.replace(runtime/'sections.jsonl.gz')
    manifest=read(runtime/'manifest.json');summary=json.loads(con.execute("SELECT data FROM metadata WHERE key='summary'").fetchone()[0]);summary['counts']['COMPACT']-=40135;summary['counts']['VANILLA']+=40135
    reasons=summary['reasonCounts']
    for i,n in counts.items():
        reason=previous[i].get('reason',previous[i].get('resolution',''));assert reasons[reason]>=n;(reasons.__setitem__(reason,reasons[reason]-n))
    reasons['EXACT_NATIVE_GLASS_OPTICAL_COMPATIBILITY']=40135
    summary.update(nativeSourceFallbackStates=77,nativeSourceSingleModelFallbackStates=72,nativeSourceMultipartFallbackStates=5,nativeGlassOpticalCompatibilityCells=40135,nativeTintedGlassCompatibilityCells=18175,nativeInvisibleSourceCells=315796,nativeOpticalSourceCompatibilityNote='Original63 branch fallback states retained; append14 source glass states to preserve native AO/full-cube/transparency/opacity. Fixed multipart unions stay in a separate native carrier; no BE.',nativeTargetModelProbeFile='source-fallback-target-probes.json')
    con.execute('UPDATE metadata SET data=? WHERE key=?',(token(summary),'summary'));con.commit();con.close();manifest.update(summary);manifest['sourcePlanSha256']=sha(plan);manifest['runtimeFiles']={p.name:sha(p) for p in runtime.glob('*.jsonl.gz')};write(runtime/'manifest.json',manifest)
    report=read(OUT/'final-prepared-bundle-verification.json');report.update(sourcePlanSha256=sha(plan),runtimeManifestSha256=sha(runtime/'manifest.json'),runtimeFiles=manifest['runtimeFiles'],counts=summary['counts'],nativeFallbackSourceStates=77,nativeGlassOpticalCompatibilityCells=40135,nativeTintedGlassCompatibilityCells=18175,nativeGlassOpticalSectionRowsChanged=changed,additionalNativeBlockEntities=0,allIdFrequencyProof=str(DEST/'decision-frequency-proof.json'));write(OUT/'final-prepared-bundle-verification.json',report)
    write(OUT/'native-glass-optical-compatibility.json',{'status':'PASS_FIXED_NATIVE_SOURCE_APPLICATION_OPTICS_DELTA','appendStates':14,'finalSourceStates':77,'cells':40135,'tintedGlassCells':18175,'multipartPaneStates':5,'multipartApplications':25,'newOwnerRecords':0,'newBlockEntities':0,'original63SourcePrefixPreserved':True,'weightedSelectionsRecomputed':False,'sourceWorldRescans':0,'rows':inputs,'multipartAmbientOcclusionChecks':checks,'sourcePlanSha256':sha(plan),'runtimeManifestSha256':sha(runtime/'manifest.json')})
    write(RESULT,{'status':'PASS','sourcePlanSha256':sha(plan),'runtimeManifestSha256':sha(runtime/'manifest.json')})

if __name__=='__main__':
    try:main()
    except BaseException:write(RESULT,{'status':'FAIL','error':traceback.format_exc()});raise
