"""Record the agent's manual inspection of original Minecraft PNGs; no image edits."""
from pathlib import Path
import hashlib,json
ROOT=Path(__file__).resolve().parents[1];D=ROOT/'docs/v12.4-dedup'
run=ROOT/'build/runtime-client-v124-comparison-preview-3'
capture=json.loads((run/'v124-client-comparison-output.json').read_text(encoding='utf-8'))
pages=set(range(82,113))|set(range(151,159))
frames=[];rows=[]
for f in capture['frames']:
    page=int(f['fileName'].rsplit('-',1)[1].split('.')[0])
    if page not in pages:continue
    path=run/'screenshots'/f['fileName'];assert hashlib.sha256(path.read_bytes()).hexdigest()==f['sha256']
    frames.append({'page':page,'fileName':f['fileName'],'sha256':f['sha256'],'manualVisualInspection':True})
    for r in f['pairs']:
        kind=r.get('kind','MIGRATION')
        if kind=='MIGRATION':continue
        if kind=='SOURCE_CATALOGUE_REVIEW':
            status='NOT_RUN_VIEW_CULLED' if r['oldId']=='90245' else 'PASS'
            reason='One-sided source north/down faces are culled from the default camera; opposite-side final fixture added.' if status!='PASS' else 'Actual source before/after has the same installed geometry, visible surface/UV, material, scale and anchor in the same native camera.'
        elif kind=='RETAINED_REVIEW':
            status='CATALOGUE_DECISION_RECORDED'
            reason='Appearance/function decision recorded in native-source-catalogue-decisions.json and reviewed-global-decisions.json; final normalized capture must use corrected relative yaw and current manifest.'
        else:
            status='PREVIEW_GENERATED_UV_DEFECT_KNOWN_FINAL_RECHECK_REQUIRED'
            reason='Preview build2 uses old generated stair render faces/UV; stair agent corrected the source before final build. This frame is not final PASS.'
        rows.append({'page':page,'kind':kind,'oldId':r['oldId'],'oldVariant':r['oldVariant'],'mainId':r['mainId'],'variant':r['variant'],'status':status,'reason':reason})
result={'schema':'dreamwalker-v124-manual-native-visual-preview-review-v1','status':'PARTIAL_PREVIEW_EVIDENCE_REQUIRES_FINAL_JAR_REVIEW',
    'productionJarSha256':capture['productionJarSha256'],'captureSha256':hashlib.sha256((run/'v124-client-comparison-output.json').read_bytes()).hexdigest(),
    'rawGameDirectory':str(run),'reviewedFrames':len(frames),'sourceCatalogueStatesReviewed':sum(r['kind']=='SOURCE_CATALOGUE_REVIEW' for r in rows),
    'sourceCatalogueVisiblePass':sum(r['kind']=='SOURCE_CATALOGUE_REVIEW' and r['status']=='PASS' for r in rows),
    'sourceCatalogueCulled':sum(r['status']=='NOT_RUN_VIEW_CULLED' for r in rows),'retainedCandidatePairsReviewed':sum(r['kind']=='RETAINED_REVIEW' for r in rows),
    'method':'Manual visual inspection of full original PNGs produced by native Minecraft ScreenshotRecorder and CompositeClient.renderParts, with source baked models; no image rewriting.',
    'frames':frames,'rows':rows}
(D/'native-preview-visual-agent-review.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
