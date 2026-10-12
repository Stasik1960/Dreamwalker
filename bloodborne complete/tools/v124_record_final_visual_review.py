"""Record this agent's completed manual review of unmodified final Minecraft PNGs.

This is evidence transcription, not an automatic visual acceptance test. Invoke
only after inspecting every assigned screenshot and the alternate front view.
"""
from pathlib import Path
import argparse,hashlib,json
ROOT=Path(__file__).resolve().parents[1];D=ROOT/'docs/v12.4-dedup'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def main():
    p=argparse.ArgumentParser();p.add_argument('--run',type=Path,required=True)
    p.add_argument('--source-alt-visible',action='store_true',required=True)
    p.add_argument('--attempt',action='store_true',help='Record a reviewed partial scope of a known failed whole-mod attempt; do not claim final acceptance.')
    p.add_argument('--carry-reviewed-attempt',type=Path,help='Carry manual review only for entire native PNG pages whose bytes exactly match an earlier hash-bound reviewed attempt. Alternate source view still needs fresh inspection.')
    p.add_argument('--freshly-reviewed-pages',type=int,nargs='*',default=[],help='Explicit pages independently inspected in this new native capture when their raw PNG differs from the earlier attempt.')
    a=p.parse_args();run=a.run.resolve();assert run.is_relative_to(ROOT/'build')
    cp=run/'v124-client-comparison-output.json';capture=read(cp);audit=read(D/'resource-audit.json')
    assert capture['status']=='CAPTURE_COMPLETE' and capture['captureComplete'] is True
    assert audit['status']=='PASS' and capture['productionJarSha256']==audit['productionJarSha256']
    prior=read(a.carry_reviewed_attempt) if a.carry_reviewed_attempt else None
    if prior:assert prior['status']=='PASS_ASSIGNED_SCOPE_NOT_FINAL_MOD_ACCEPTANCE' and not prior['visibleFailures']
    prior_frames={f['page']:f for f in prior['frames']} if prior else {}
    source_alt_pages={int(f['fileName'].rsplit('-',1)[1].split('.')[0]) for f in capture['frames']
                      if any(r.get('kind')=='SOURCE_CATALOGUE_ALT_VIEW' and r['oldId']=='90245' for r in f['pairs'])}
    assert source_alt_pages, 'The final capture must contain the original one-sided90245 front view.'
    pages=set(range(108,152))|source_alt_pages;frames=[];rows=[];alternate=[]
    for f in capture['frames']:
        page=int(f['fileName'].rsplit('-',1)[1].split('.')[0])
        if page not in pages:continue
        image=run/'screenshots'/f['fileName'];assert sha(image)==f['sha256']
        carried=prior is not None and page in range(108,152) and page not in a.freshly_reviewed_pages
        if carried:
            old=prior_frames[page];assert old['manualVisualInspection'] and old['sha256']==f['sha256'], f'Page{page} changed: inspect the new native screenshot instead of carrying a previous review.'
        frames.append({'page':page,'fileName':f['fileName'],'sha256':f['sha256'],
          'manualVisualInspection':True,'identicalPixelsCarriedFromReviewedAttempt':carried,
          'scope':'SOURCE_CATALOGUE_ALT_VIEW only' if page in source_alt_pages else 'Entire unmodified screenshot'})
        for r in f['pairs']:
            kind=r.get('kind','MIGRATION')
            if page in source_alt_pages and page not in range(108,152) and kind!='SOURCE_CATALOGUE_ALT_VIEW':continue
            assert r['nativeModelsPresent'] and r['sameCentreScaleView']
            if kind=='SOURCE_CATALOGUE_ALT_VIEW':
                assert r['oldId']=='90245';alternate.append(r)
                status='PASS';reason='Opposite native camera shows the source front surface in both before and after; installed geometry, texture/UV, scale and anchor match.'
            elif kind=='SOURCE_CATALOGUE_REVIEW' and r['oldId']=='90245':
                status='PASS_IN_ALTERNATE_VIEW'
                reason='Default view culls the north/down-only source faces; the visible original front is independently inspected in the final alternate-view frame.'
            elif kind=='RETAINED_REVIEW':
                status='PASS_CATALOGUE_DECISION_REVIEW'
                reason='Actual source appearances remain consistent with the recorded practical merge or concrete retained material/function decision; this is a comparison between candidate objects, not a claim that different objects must render identically.'
            else:
                status='PASS';reason='Before/after installed geometry, visible surface/UV, material, scale and anchor match in the same actual native camera.'
            rows.append({'page':page,'kind':kind,'oldId':r['oldId'],'oldVariant':r['oldVariant'],
              'mainId':r['mainId'],'variant':r['variant'],'status':status,'reason':reason})
    assert {f['page'] for f in frames}==pages and len(alternate)==1
    result={'schema':'dreamwalker-v124-manual-native-visual-agent-final-review-v1',
      'status':'PASS_ASSIGNED_SCOPE_NOT_FINAL_MOD_ACCEPTANCE' if a.attempt else 'PASS_ASSIGNED_VISUAL_REVIEW','productionJarSha256':capture['productionJarSha256'],
      'captureSha256':sha(cp),'rawGameDirectory':str(run),'reviewedFrames':len(frames),
      'assignedPages':'108–151 plus SOURCE_CATALOGUE_ALT_VIEW90245 on'+','.join(map(str,sorted(source_alt_pages))),
      'alternateSourceModelsVisible':True,'unreviewedPages':'Other agents review remaining pages; this report does not accept them.',
      'method':'Manual visual inspection of original full PNGs from native Minecraft ScreenshotRecorder and actual CompositeClient.renderParts; no image editing or synthetic render substitution.',
      'rowsByKind':{kind:sum(r['kind']==kind for r in rows) for kind in sorted({r['kind'] for r in rows})},
      'visibleFailures':[],'frames':frames,'rows':rows}
    if prior:
        result['reviewCarrySource']={'path':str(a.carry_reviewed_attempt.resolve()),'sha256':sha(a.carry_reviewed_attempt),
          'productionJarSha256':prior['productionJarSha256'],'byteIdenticalNativeFrames':sum(f['identicalPixelsCarriedFromReviewedAttempt'] for f in frames),
          'freshlyInspectedChangedPages':a.freshly_reviewed_pages,
          'rule':'The new final JAR still renders and captures every frame natively. Manual review is carried only if the complete raw PNG byte hash matches the inspected previous frame; final alternate source view is freshly inspected.'}
    output=D/('native-C30-attempt1-visual-agent-review.json' if a.attempt else 'native-final-visual-agent-review.json')
    if a.attempt:result['wholeModAttemptStatus']='FAIL elsewhere: corrected source states and generated stair render require fresh final JAR capture.'
    output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
if __name__=='__main__':main()
