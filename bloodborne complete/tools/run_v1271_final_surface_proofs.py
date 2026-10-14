"""Controlled read-only pair of exact exported-surface proofs."""
import traceback
from prepare_v1271_city_compatibility import ROOT,RES,write,read

if __name__=='__main__':
    result=ROOT/'build/v1271-final-surface-proofs-result.json'
    try:
        import verify_v1271_source_fragment_surfaces as fragments
        fragments.main()
        import verify_v1271_native_fallback_surfaces as fallback
        fallback.main()
        count=len(read(RES/'bloodborne_dw/source-fallback-carriers-v1271.json')['sourceStates'])
        write(result,{'status':'PASS','hiddenPoses':117,'hiddenOrientationChecks':936,'fallbackSourceStates':count,'fallbackProfileChecks':count*2})
    except BaseException:write(result,{'status':'FAIL','error':traceback.format_exc()});raise
