"""Preserve only C30 original PNG pages actually opened by the stair reviewer."""
import hashlib
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];DOC=ROOT/'docs/v12.4-dedup';RUN=ROOT/'build/runtime-client-v124-comparison-final-1'
def read(path):return json.loads(path.read_text(encoding='utf8'))
def write(path,value):path.write_bytes((json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode('utf8'))
def main():
    capture=read(RUN/'v124-client-comparison-output.json');assert capture['captureComplete']
    pages=set(range(81,108))|set(range(152,176));frames=[];pairs=[]
    for frame in capture['frames']:
        if frame['page'] not in pages:continue
        image=RUN/'screenshots'/frame['fileName'];assert hashlib.sha256(image.read_bytes()).hexdigest()==frame['sha256']
        frames.append({'page':frame['page'],'fileName':frame['fileName'],'sha256':frame['sha256'],'pairsActuallyReviewed':len(frame['pairs'])});pairs.extend(frame['pairs'])
    functional=[r for r in pairs if r.get('kind')=='FUNCTIONAL_NEW_STATE'];assert len(functional)==200
    native_ids={r['oldRegistryId'] for r in read(DOC/'stair-review/functional-type-fragment.json')['mappings']}
    native=[r for r in pairs if not r.get('kind') and r['oldRegistryId'] in native_ids];assert len(native)==315
    result={'schemaVersion':1,'status':'PRE_FINAL_C30_FAIL_REQUIRES_GENERATED_ALPHA_FIX_AND_RECAPTURE','productionJarSha256':capture['productionJarSha256'],
        'reviewer':'v124_stair_types','reviewMethod':'Actual original native PNGs opened with view_image; common centre/scale/view; pixels never modified','rawImagesModified':False,
        'frames':frames,'nativeMappingsReviewed':315,'nativeMappingsAppearance':'PASS_OBSERVED_313_STAIRS_AND_TWO_LADDERS',
        'functionalFormsReviewed':200,'functionalFormsObservedPass':196,'retainedPairsReviewed':sum(r.get('kind')=='RETAINED_REVIEW' for r in pairs),
        'retainedStairMaterials':'KEEP19_NOTICABLY_DIFFERENT_AUTHORED_SURFACES','materialRationale':'stair-review/material-differences.md',
        'failures':[{'category':'GENERATED_SOURCE_ALPHA_PATCH_EMPTY','mainId':'93028','visibleFailStates':[{'variant':7,'trim':False},{'variant':9,'trim':False},{'variant':7,'trim':True},{'variant':9,'trim':True}],
            'pages':[172,173],'playerVisibleDifference':'New top/right corners show missing east exterior walls/open C-shaped planes. Source east atlas patch is100percent transparent despite a present JSON face.',
            'correction':'Only generated93028 models reuse100percent opaque authored opposite west UV with identical physical extent. Exact313 legacy art and source JSON/PNG unchanged.'}],
        'sourceVisibilityExceptions':[{'oldRegistryId':'bloodborne_dw:source_addon_floor_panels_empty','oldVariant':0,'mainId':'90133','variant':8,'status':'SOURCE_FULLY_TRANSPARENT_STATE_PRESERVED','visualVisibility':'NOT_APPLICABLE_FULLY_TRANSPARENT_SOURCE','evidence':'one-sided-source-alpha-audit.json: source UP face has0 nonzero-alpha pixels; no authored appearance guessed'}],
        'pendingOneSidedViews':[{'oldId':'93223','oldVariants':[3,4,5],'currentStatus':'Some35/215degree views culled/blank; independently added125/305 views, no visiblePASS until native capture'}],
        'sourceCorrectionProofs':['stair-render-c30-correction-proof.json','stair-c30-legacy-art-proof.json','stair-face-alpha-corrected.json'],
        'newFinalNativeCapture':'NOT_RUN','physicalMouseKeyboard':'NOT_RUN'}
    write(DOC/'native-C30-stair-review-preserved.json',result)
    for source,name in [('v124-stair-render-c30-correction-proof.json','stair-render-c30-correction-proof.json'),('v124-stair-c30-legacy-art-proof.json','stair-c30-legacy-art-proof.json'),('v124-stair-face-alpha.json','stair-face-alpha-corrected.json')]:
        value=read(ROOT/'build'/source);assert value['status']=='PASS';write(DOC/name,value)
    print(json.dumps({'status':result['status'],'rawFramesActuallyReviewed':len(frames),'nativeMappings':315,'functionalForms':200}))
if __name__=='__main__':main()
