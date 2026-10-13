"""Extract unmerged global candidates and review sheets; no production edits."""
import json
from pathlib import Path
from PIL import Image,ImageDraw
from v124_visual_audit import OUT,ROOT
from v124_consolidate_visual_groups import STair_IDS

audit=json.loads((OUT/'visual-candidates.json').read_text(encoding='utf-8'))
full=json.loads((OUT/'full-visual-coverage.json').read_text(encoding='utf-8'))
assert len(full['states'])==len(audit['inventory'])==1802
for state,row in zip(full['states'],audit['inventory']):
    for field in ['id','registryId','variant']:state[field]=row[field]
assert len({(s['registryId'],s['variant']) for s in full['states']})==1802
(OUT/'full-visual-coverage.json').write_text(json.dumps(full,ensure_ascii=False,indent=2),encoding='utf-8')
general=json.loads((OUT/'general-functional-groups.json').read_text(encoding='utf-8'))
parent={r['registryId']:r['registryId'] for r in audit['inventory']}
def find(r):
    while parent[r]!=r:parent[r]=parent[parent[r]];r=parent[r]
    return r
for g in general['groups']:
    for m in g['members']:parent[m['registryId']]=g['mainRegistryId']
owned=set(STair_IDS)|{'90980'}
candidates=[];seen=set()
for p in full['matches']:
    a,b=p['a'],p['b']
    if a['id'] in owned or b['id'] in owned:continue
    if find(a['registryId'])==find(b['registryId']):continue
    key=tuple(sorted([find(a['registryId']),find(b['registryId'])]))
    if key in seen:continue
    seen.add(key);candidates.append(p)
(OUT/'additional-global-candidates.json').write_text(json.dumps(candidates,ensure_ascii=False,indent=2),encoding='utf-8')
lines=[]
for index,p in enumerate(candidates):
    lines.append(f"{index:03} {p['a']['id']}:{p['a']['variant']} {p['a']['registryId'].split(':')[1]} / {p['b']['id']}:{p['b']['variant']} {p['b']['registryId'].split(':')[1]} diff={p['difference']:.5f} sil={p['silhouetteDifference']:.5f} px={p['pixelDifference']:.5f}")
(OUT/'additional-global-candidates.txt').write_text('\n'.join(lines),encoding='utf-8')
review=[p for p in candidates if p['difference']<.05 and p.get('diagnosticImage')]
for page,start in enumerate(range(0,len(review),18)):
    canvas=Image.new('RGB',(1024,18*110),(39,48,55));draw=ImageDraw.Draw(canvas)
    for row,p in enumerate(review[start:start+18]):
        canvas.paste(Image.open(ROOT/p['diagnosticImage']).convert('RGB'),(0,row*110))
        draw.text((350,row*110+82),f"candidate {candidates.index(p):03}",fill='yellow')
    canvas.save(OUT/f'full-review-sheet-{page:02}.png')
(OUT/'full-review-summary.txt').write_text(f"Unmerged candidate registry-pairs {len(candidates)}; strong <.05 diagnostic sheets {len(review)}. State identity repaired without rerendering; 1802 unique registry+variant rows.",encoding='utf-8')
