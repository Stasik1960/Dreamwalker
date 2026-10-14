"""Preserve source slab opaque settings without reclassifying source cells."""
import gzip,json,sqlite3,traceback
from pathlib import Path
from prepare_v1271_city_compatibility import ROOT,RES,OUT,read,write,token,sha

def main():
    data=read(RES/'bloodborne_dw/invisible-source-carriers-v1271.json');states=data['sourceStates']
    solid=[s for s in states if s.startswith('minecraft:polished_diorite_slab[')];assert len(solid)==3
    data['solidSourceStates']=solid;write(RES/'bloodborne_dw/invisible-source-carriers-v1271.json',data)
    write(RES/'assets/bloodborne_dw/blockstates/source_invisible_solid_carrier.json',{'variants':{'':{'model':'bloodborne_dw:block/source_invisible_carrier_empty'}}})
    debug=read(RES/'bloodborne_dw/debug_catalogue.json')
    if not any(e['registryId']=='bloodborne_dw:source_invisible_solid_carrier' for e in debug['entries']):
        assert not any(e['temporaryId']=='93236' for e in debug['entries'])
        debug['entries'].append({'temporaryId':'93236','finalId':None,'kind':'compatibility','name':'Невидимый непрозрачный исходный носитель','registryId':'bloodborne_dw:source_invisible_solid_carrier','aliases':['bloodborne_dw:source_invisible_solid_carrier']});write(RES/'bloodborne_dw/debug_catalogue.json',debug)
    plan=ROOT/'build/v1271-city-preparation/source-plan.sqlite';con=sqlite3.connect(plan); replacements={}
    for i,raw in con.execute('SELECT id,data FROM decisions'):
        d=json.loads(raw)
        if d.get('reason')=='PRESERVE_NATIVE_INVISIBLE_SOURCE_CARRIER_PHYSICS' and d['sourceState'] in solid:
            old=token({'registryId':'bloodborne_dw:source_invisible_carrier','properties':d['properties']});d['registryId']='bloodborne_dw:source_invisible_solid_carrier'
            replacements[old]=token({'registryId':d['registryId'],'properties':d['properties']});con.execute('UPDATE decisions SET data=? WHERE id=?',(token(d),i))
    assert len(replacements)==3
    summary=json.loads(con.execute("SELECT data FROM metadata WHERE key='summary'").fetchone()[0]);summary['hiddenInvisibleNonOpaqueSourceStates']=33;summary['hiddenInvisibleOpaqueSourceStates']=3
    con.execute('UPDATE metadata SET data=? WHERE key=?',(token(summary),'summary'));con.commit();con.close()
    runtime=plan.parent/'runtime';source=runtime/'sections.jsonl.gz';target=runtime/'sections.solid.jsonl.gz';changed=0
    with gzip.open(source,'rt',encoding='utf8') as src,gzip.open(target,'wt',encoding='utf8') as out:
        for line in src:
            previous=line
            for old,new in replacements.items():line=line.replace(old,new)
            changed+=line!=previous;out.write(line)
    target.replace(source);manifest=read(runtime/'manifest.json');manifest.update(summary);manifest['sourcePlanSha256']=sha(plan);manifest['runtimeFiles']={p.name:sha(p) for p in runtime.glob('*.jsonl.gz')};write(runtime/'manifest.json',manifest)
    write(OUT/'invisible-carrier-opaque-settings.json',{'status':'PASS_STATIC_NATIVE_SOURCE_SETTING_SPLIT','nonOpaqueSourceStates':33,'opaqueSourceStates':solid,'runtimeSectionRowsChanged':changed,'additionalBlockEntities':0,'textureArtworkAdded':False,'sourcePlanSha256':sha(plan),'runtimeManifestSha256':sha(runtime/'manifest.json'),'nativeSourceShapeOpacityAoCullValidation':'PENDING'})
    # Keep the original immutable-input verification and mark its successor.
    proof=read(OUT/'extended-prepared-bundle-verification.json');proof['finalSourcePlanSha256']=sha(plan);proof['finalRuntimeManifestSha256']=sha(runtime/'manifest.json');proof['invisibleOpaqueSettingSplit']='invisible-carrier-opaque-settings.json';write(OUT/'extended-prepared-bundle-verification.json',proof)

if __name__=='__main__':
    result=ROOT/'build/v1271-invisible-finalize-result.json'
    try:main();write(result,{'status':'PASS','exitCode':0})
    except BaseException:write(result,{'status':'FAIL','exitCode':1,'error':traceback.format_exc()});raise
