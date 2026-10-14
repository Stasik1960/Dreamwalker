"""Persist the real read-only staging gate result under the storage guard."""
import contextlib,io,json,sys,traceback
from prepare_v1271_city_compatibility import ROOT,write,read

if __name__=='__main__':
    result=ROOT/'build/v1271-city-readonly-validation.json'
    try:
        import stage_v1271_city_conversion as checked
        output=io.StringIO();sys.argv=['stage_v1271_city_conversion.py']
        with contextlib.redirect_stdout(output):checked.main()
        row=json.loads(output.getvalue());assert row['status']=='PASS_READONLY_READY_TO_STAGE',row
        manifest=read(ROOT/'build/v1271-city-preparation/runtime/manifest.json')
        assert manifest['nativeSourceFallbackStates']==156 and manifest['nativeFrozenOpticalContextCells']==348528 and manifest['nativeFrozenOpticalContextAuditStatus']=='PASS'
        assert manifest['nativeCompactFlagAuditTargets']==1814 and manifest['nativeCompactFlagAuditCriticalDifferences']==0 and manifest['counts']['REMOVE']==0
        row['finalNativeContextGate']='PASS';row['nativeSourceContexts']=156;row['nativeAuditedTargetCases']=1814;row['nativeCriticalDifferences']=0
        write(result,row)
    except BaseException:write(result,{'status':'FAIL','error':traceback.format_exc()});raise
