"""Repair exposed omitted cuboid faces and invalid neighbor-culling hints.

Retains zero-thickness artwork and faces covered by another element. Existing UVs
are never changed; newly exposed sides reuse the opposite authored face texture.
"""
import copy, json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/bloodborne_blocks/models/block'
SIDES={'west':(0,0,'east'),'east':(0,1,'west'),'down':(1,0,'up'),'up':(1,1,'down'),'north':(2,0,'south'),'south':(2,1,'north')}
added=culled=0
for path in ROOT.rglob('*.json'):
    d=json.loads(path.read_text(encoding='utf-8-sig')); elements=d.get('elements',[]); changed=False
    for e in elements:
        faces=e.get('faces',{})
        for side,f in faces.items():
            if 'cullface' in f:
                a,high,_=SIDES[f['cullface']]
                boundary=e['to' if high else 'from'][a]
                if e.get('rotation',{}).get('angle',0)!=0 or abs(boundary-(16 if high else 0))>1e-6:
                    del f['cullface']; culled+=1; changed=True
        if not faces or any(e['to'][a]-e['from'][a]<1e-5 for a in range(3)):continue
        for side,(a,high,opposite) in SIDES.items():
            if side in faces:continue
            plane=e['to' if high else 'from'][a]
            covered=False
            for other in elements:
                if other is e or other.get('rotation')!=e.get('rotation'):continue
                extends=(other['from'][a]<=plane and other['to'][a]>plane) if high else (other['from'][a]<plane and other['to'][a]>=plane)
                if extends and all(other['from'][b]<=e['from'][b] and other['to'][b]>=e['to'][b] for b in range(3) if b!=a):covered=True;break
            if not covered:
                source=faces.get(opposite,next(iter(faces.values())))
                face=copy.deepcopy(source);face.pop('cullface',None)
                faces[side]=face;added+=1;changed=True
    if changed:path.write_text(json.dumps(d,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8')
print(f'Restored {added} exposed faces; removed {culled} invalid cullface hints.')
