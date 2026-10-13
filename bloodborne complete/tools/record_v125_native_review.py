"""Bind already inspected native pixels and live checks to immutable production."""
import hashlib,json,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];DOC=ROOT/'docs/v12.5-city-review/native'
SHA='76690ecea281ecd5ac59d9b00493546af1978b0819bb2923da977b176f16b845'

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def read(p):return json.loads(p.read_text(encoding='utf8'))
def write(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n')

def main():
    runs=['v125-flatgallery-native-1','v125-flatgallery-native-front-2','v125-flatgallery-native-opposite-3'];frames=[];captures=[]
    for run in runs:
        folder=ROOT/'build'/('runtime-client-'+run);path=folder/'client-capture-output.json';capture=read(path)
        assert capture['status']=='CAPTURE_COMPLETE' and capture['captureComplete'] and capture['productionJarSha256']==SHA
        assert capture['capturedFrames']==capture['expectedFrames']==len(capture['frames'])
        log=(folder/'logs/latest.log').read_text(encoding='utf8',errors='replace')
        assert 'Saving worlds' in log and 'All dimensions are saved' in log
        errors=[line for line in log.splitlines() if '/ERROR]' in line and 'Failed to verify authentication' not in line]
        assert not errors,errors
        target=DOC/'captures'/run;target.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,target/'client-capture-output.json');shutil.copyfile(folder/'logs/latest.log',target/'latest.log')
        for row in capture['frames']:
            assert row['status']=='PASS' and row['renderedCameraVerified']
            assert all(r['status']=='PASS' for r in row.get('actualRootChecks',[])+row.get('actualEntityChecks',[]))
            source=folder/'screenshots'/row['fileName'];assert sha(source)==row['sha256'];out=target/row['fileName'];shutil.copyfile(source,out);assert sha(out)==row['sha256']
            note='Raw native pixels inspected; visible source model/texture and gallery seating accepted.'
            status='PASS_OBSERVED_PIXELS'
            if row['fileName'].startswith('v125-006-'):
                status='HISTORICAL_BACKFACE_VIEW_RESOLVED_BY_WEST_SUPPLEMENT';note='Authored single WEST face is correctly culled from EAST; object accepted using both actual WEST supplement PNGs, no model repair.'
            if run.endswith('front-2') and row['key'] in ('2672:2673:0,1,0','929:1092:0,2,0','DOOR-929:1092:0,2,0'):
                status='HISTORICAL_BACKFACE_VIEW_RESOLVED_BY_OPPOSITE_SUPPLEMENT';note='Native opposite-side run3 confirms authored visible facade; this back-face view alone is not visual acceptance.'
            if row['section']=='city':note='Representative city image accepted, including original crop seams, omitted source exceptions and separated source finial; no per-owner visual claim for all4575.'
            frames.append({'run':run,'key':row['key'],'section':row['section'],'fileName':row['fileName'],'sha256':row['sha256'],'status':status,'observation':note,'path':str(out.relative_to(DOC))})
        captures.append({'run':run,'frames':len(capture['frames']),'captureSha256':sha(path),'liveRootChecks':sum(len(r.get('actualRootChecks',[])) for r in capture['frames']),'saveAndStop':'PASS','unexpectedErrors':[]})
    assert len(frames)==102 and [c['frames'] for c in captures]==[82,17,3]
    manifest=read(ROOT/'build/v125-gallery-manifest.json');reviewkeys={r['key'] for r in manifest['reviewPlatforms']}
    assert len(reviewkeys)==49 and reviewkeys<={f['key'] for f in frames}
    assert read(DOC/'main-supplement-review.json')['status']=='PASS_SOURCE_FACE_CONFIRMED'
    write(DOC/'visual-acceptance.json',{'schema':'dreamwalker-v125-native-pixel-review','status':'PASS','productionJarSha256':SHA,'reviewedFrames':102,'effectiveMainObjectViews':26,'allConstructionPlatformsObserved':31,'allDoorReviewPlatformsObserved':18,'cityRepresentativeFrames':7,'captures':captures,'frames':frames,'method':'Actual raw native ScreenshotRecorder PNGs viewed using view_image, by root and independent reviewer. Source geometry/static proofs are separate.','physicalMouseKeyboard':'NOT_RUN','everyCityOwnerVisuallyInspected':'NOT_RUN','doorSemanticApproval':'USER_REVIEW_PENDING_PROPERTIES_UNCHANGED','assemblyMergeApproval':'USER_REVIEW_PENDING_REGISTRATIONS_NOT_MERGED'})
    print(json.dumps({'status':'PASS','nativeFrames':102,'reviewPlatforms':49}))
if __name__=='__main__':main()
