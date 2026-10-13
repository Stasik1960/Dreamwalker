"""Lay out existing source diagnostics for retained-pair review only."""
import json
from pathlib import Path
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'docs/v12.4-dedup'
rows=json.loads((OUT/'retained-near-pairs-to-document.json').read_text(encoding='utf-8'))
unique={}
for row in rows:
    key=tuple(sorted([row['a']['id'],row['b']['id']]))
    if key not in unique:unique[key]=row
pairs=list(unique.values())
for page,start in enumerate(range(0,len(pairs),16)):
    canvas=Image.new('RGB',(1024,16*126),(40,49,58));draw=ImageDraw.Draw(canvas)
    for index,row in enumerate(pairs[start:start+16]):
        path=ROOT/row['diagnosticImage'];im=Image.open(path).convert('RGB')
        canvas.paste(im,(0,index*126))
        draw.text((4,index*126+110),str(start+index)+' '+row['a']['id']+'/'+row['b']['id'],fill='yellow')
    canvas.save(OUT/f'retained-near-review-{page:02}.png')
(OUT/'retained-near-unique.json').write_text(json.dumps(pairs,ensure_ascii=False,indent=2),encoding='utf-8')
