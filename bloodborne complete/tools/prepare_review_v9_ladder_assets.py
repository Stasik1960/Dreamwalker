"""Reproduce V9 item types from immutable V8 art; no source model/UV edit."""
from pathlib import Path
import json, zipfile, hashlib
ROOT=Path(__file__).resolve().parents[1]
ARCHIVE=ROOT/'build/delivery/v8/dreamwalker-bb-fabric-1.20.1-review-v8-full-source.zip'
PREFIX='dreamwalker-bb-fabric-1.20.1-full-source-review-v8/'
REL='src/architecture/resources/'
with zipfile.ZipFile(ARCHIVE) as z:
    source=json.loads(z.read(PREFIX+REL+'assets/bloodborne_dw/blockstates/prototype_ladder.json'))
    item=z.read(PREFIX+REL+'assets/bloodborne_dw/models/item/prototype_ladder.json')
rows=[]
for art,path in [(-1,'prototype_ladder'),(1,'prototype_ladder_art_1'),(2,'prototype_ladder_art_2')]:
    variants={}
    for key,value in source['variants'].items():
        entry=dict(value)
        if art>=0:
            props=dict(pair.split('=') for pair in key.split(','))
            entry['model']=entry['model'].replace('wood_ladder_0'+str(int(props['variant'])+2),'wood_ladder_0'+str(art+2))
        for free in ('false','true'):variants[key+',freestanding='+free]=entry
    target=ROOT/REL/'assets/bloodborne_dw/blockstates'/f'{path}.json'
    target.write_text(json.dumps({'variants':variants},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    if art>=0:(ROOT/REL/'assets/bloodborne_dw/models/item'/f'{path}.json').write_bytes(item)
    rows.append({'registry':f'bloodborne_dw:{path}','states':len(variants),'sha256':hashlib.sha256(target.read_bytes()).hexdigest()})
(ROOT/REL/'data/minecraft/tags/blocks/climbable.json').write_text(json.dumps({'replace':False,'values':[r['registry'] for r in rows]},indent=2)+'\n',encoding='utf-8')
(ROOT/'reports/REVIEW_V9_LADDER_ASSETS.json').write_text(json.dumps({'schema':'v9-ladder-assets-1','sourceArchiveSha256':hashlib.sha256(ARCHIVE.read_bytes()).hexdigest(),'modelTextureBytesUnchanged':True,'stateBindings':rows,'runtime':'NOT_RUN'},indent=2)+'\n',encoding='utf-8')
print(json.dumps(rows))
