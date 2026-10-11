"""Record only raw native frames actually reviewed by root; preview is not final acceptance."""
from pathlib import Path
import hashlib,json
ROOT=Path(__file__).resolve().parents[1]
run=ROOT/'build/runtime-client-v124-comparison-preview-3'
capture=json.loads((run/'v124-client-comparison-output.json').read_text(encoding='utf8'))
pages=list(range(1,26))+list(range(56,83))+list(range(113,151))
rows=[]
for number in pages:
    frame=next(f for f in capture['frames'] if f['page']==number)
    png=run/'screenshots'/frame['fileName']
    digest=hashlib.sha256(png.read_bytes()).hexdigest();assert digest==frame['sha256']
    rows.append({'page':number,'file':png.name,'sha256':digest,'pairsReviewed':len(frame['pairs']),
                 'status':'REQUIRES_STAIR_UV_FIX_AND_FINAL_RECAPTURE' if 56<=number<=82 else 'PASS_OBSERVED_PREVIEW'})
result={'schemaVersion':1,'status':'PREVIEW_NOT_FINAL_ACCEPTANCE','productionJarSha256':capture['productionJarSha256'],
        'basis':'Raw original 1920x1055 Minecraft PNGs opened using view_image. LEFT before / RIGHT after. Visible native geometry, UV, alpha and source anchor compared; this does not prove unseen faces.',
        'pages':rows,'concreteFailures':[{'category':'LEGACY_STAIR_UV_ORIENTATION','pages':[57,58,59,60,66,67,68,70,71,73,74,75,76,78,79],
            'examples':['90276:2 and :4','90322:2 and :4','90409:2 and :4','90523:2,:4,:5,:7','90686:2,:4,:5,:7','93081:4,:5,:6,:7'],
            'playerVisibleDifference':'The old horizontal/vertical plank grain or brick tread pattern rotates despite the same stair geometry. Validated legacy art selector is required before final acceptance.'}],
        'unchangedObserved':'Nonstair mapped views on pages1-25 and56 plus upright90586/90587 match; unmapped catalogue states pages113-150 match. All inspected pages contain visible models; no blank panels in root assigned ranges.',
        'additionalGroupingRecommendations':[['93090','93091'],['93157','93158'],['93187','93192'],['93201','93204'],['93202','93215','93229'],['93180','93220']],
        'finalProductionRerun':'REQUIRED','physicalMouseKeyboard':'NOT_RUN'}
out=ROOT/'docs/v12.4-dedup/native-preview-root-review.json'
out.write_bytes((json.dumps(result,ensure_ascii=False,indent=2)+'\n').encode('utf8'))
print(json.dumps({'status':result['status'],'pagesReviewed':len(rows),'pairsReviewed':sum(r['pairsReviewed'] for r in rows)}))
