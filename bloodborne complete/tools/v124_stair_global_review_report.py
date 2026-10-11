"""Atlas-independent candidate evidence between retained native stair materials."""
import json
from PIL import Image,ImageDraw
from v124_visual_audit import OUT,ROOT

fragment=json.loads((OUT/'stair-review/functional-type-fragment.json').read_text(encoding='utf-8'))
full=json.loads((OUT/'full-visual-coverage.json').read_text(encoding='utf-8'))
lookup={m:g['primaryId'] for g in fragment['groups'] for m in g['members']}
nearest={}
for p in full['matches']:
    a,b=lookup.get(p['a']['registryId']),lookup.get(p['b']['registryId'])
    if not a or not b or a==b:continue
    key=tuple(sorted([a,b]))
    if key not in nearest or p['difference']<nearest[key]['difference']:nearest[key]=p
lines=[]
pairs=sorted(nearest.items(),key=lambda item:item[1]['difference'])
for (a,b),p in pairs:lines.append(f"canonical {a}/{b}; actual {p['a']['id']}:{p['a']['variant']}/{p['b']['id']}:{p['b']['variant']}; score {p['difference']:.5f} RGB {p['pixelDifference']:.5f}; {p.get('diagnosticImage','same atlas, initial projections')} ")
(OUT/'stair-review/global-material-candidates.txt').write_text('\n'.join(lines),encoding='utf-8')
for page,start in enumerate(range(0,len(pairs),18)):
    canvas=Image.new('RGB',(1024,18*110),(39,48,55));draw=ImageDraw.Draw(canvas)
    for row,((a,b),p) in enumerate(pairs[start:start+18]):
        if p.get('diagnosticImage'):canvas.paste(Image.open(ROOT/p['diagnosticImage']).convert('RGB'),(0,row*110))
        draw.text((400,row*110+82),f"canonical {a}/{b}",fill='yellow')
    canvas.save(OUT/f'stair-review/global-material-review-{page:02}.png')
