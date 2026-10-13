"""Read-only native screenshot index; no image transformation or PASS by drawing."""
from pathlib import Path
from collections import Counter
import argparse, hashlib, json

ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'docs/v12.4-dedup'
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--game-dir',type=Path,required=True)
    parser.add_argument('--out',type=Path,default=OUT/'native-preview-review-index.json')
    args=parser.parse_args();capture=args.game_dir/'v124-client-comparison-output.json'
    data=json.loads(capture.read_text(encoding='utf-8'));inventory=json.loads((OUT/'visual-candidates.json').read_text(encoding='utf-8'))['inventory']
    expected={(r['registryId'],int(r['variant'])) for r in inventory}
    seen=set();counts=Counter();rows=[];failures=[]
    for frame in data.get('frames',[]):
        image=args.game_dir/'screenshots'/frame['fileName']
        valid=image.is_file() and digest(image)==frame.get('sha256')
        if not valid:failures.append({'page':frame['page'],'reason':'Captured screenshot absent or byte hash differs'})
        for pair in frame['pairs']:
            kind=pair.get('kind','MIGRATION');counts[kind]+=1
            if kind in ('MIGRATION','SOURCE_CATALOGUE_REVIEW'):seen.add((pair['oldRegistryId'],int(pair['oldVariant'])))
            rows.append({**pair,'page':frame['page'],'image':str(image.resolve()),'imageSha256':frame.get('sha256'),
                'captureBytesVerified':valid,'visualReview':'NOT_REVIEWED'})
    report={'schema':'dreamwalker-v124-native-capture-review-index-v1',
      'status':'CAPTURE_INDEXED_REQUIRES_VISUAL_REVIEW','captureStatus':data.get('status'),
      'productionJarSha256':data.get('productionJarSha256'),'captureJsonSha256':digest(capture),
      'expectedSourceStates':len(expected),'capturedUniqueSourceStates':len(seen),'kinds':dict(counts),
      'missingSourceStates':[{'registryId':r,'variant':v} for r,v in sorted(expected-seen)],
      'unexpectedSourceStates':[{'registryId':r,'variant':v} for r,v in sorted(seen-expected)],
      'nativePixelFlaggedPairs':sum(r.get('nativePixelComparison')=='REVIEW_REQUIRED' for r in rows),
      'failures':failures,'rows':rows}
    args.out.parent.mkdir(parents=True,exist_ok=True);args.out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    if failures:raise SystemExit(1)
if __name__=='__main__':main()
