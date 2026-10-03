"""Review cards rendered from shipped mesh polygons, explicitly not game screenshots."""
import json,sqlite3
from collections import defaultdict
from pathlib import Path
import numpy as np
from build_unified_registry import ROOT,read,SOURCE,AUDIT,MeshStore,stream_members,key,props
from render_modular_preview import draw_polys
from render_catalog import CAM
from PIL import Image,ImageDraw,ImageFont
OUT=ROOT/'docs/unified-models-gallery-2026-10-03/images'
FONT='C:/Windows/Fonts/arial.ttf'
def card(polys,labels,name):
    points=np.concatenate([np.asarray(p['vertices'])[:,:3]@CAM.T for group in polys for p in group]);frame=(points.min(0),points.max(0))
    im=Image.new('RGB',(420*len(polys),510),(27,30,36));draw=ImageDraw.Draw(im)
    for i,(group,label)in enumerate(zip(polys,labels)):
        im.paste(draw_polys(group,420,frame).convert('RGB'),(i*420,60));draw.text((i*420+10,12),label,font=ImageFont.truetype(FONT,18),fill='white')
    draw.text((10,484),'Real mesh software render / фактические модели, не скриншот игры',font=ImageFont.truetype(FONT,16),fill='white')
    path=OUT/name;im.save(path);return path.name
def main():
    OUT.mkdir(parents=True,exist_ok=True);stage=ROOT/'build/unified-generated';defs={d['id']:d for d in read(stage/'definitions.json')['blocks']};old={d['id']:d for d in read(SOURCE/'definitions.json')['blocks']};audit={r['key']:r for r in read(AUDIT)['entries']};store=MeshStore(SOURCE,ROOT/'build/unified-model-cache.sqlite')
    choices=sorted(read(stage/'merge-decisions.json'),key=lambda r:(r['privateStates'],len(r['sources'])),reverse=True)
    picks=[];families=set()
    for r in choices:
        if len(picks)>=8:break
        refs=[audit[s]for s in r['sources']];a=refs[0];b=refs[-1]
        ma=store.get(old[a['id']]['models'][a['state']])['polygons'];mb=store.get(old[b['id']]['models'][b['state']])['polygons']
        if len(ma)>200 or len(mb)>200:continue
        p=defs[r['id']]['default'];mid=defs[r['id']]['models'][key(p)];picks.append((r,a,b,ma,mb,mid));families.add(mid)
    new={i:m['polygons']for i,m in stream_members(stage/'owner-meshes.json.gz')if i in families};images=[]
    for n,(r,a,b,ma,mb,mid)in enumerate(picks):
        path=card([ma,mb,new[mid]],['До: исходный образец A','До: исходный образец B','После: общий ID, вариант 0'],f'merge-{n+1:02d}.png')
        images.append({'path':path,'id':r['id'],'reason':r['reason'],'privateStates':r['privateStates'],'sources':[{'id':x['id'],'state':x['state']}for x in(a,b)],'defaultSource':r['defaultSource'],'retainedPrivateVariants':True})
    logical=ROOT/'src/main/resources/bloodborne_blocks/logical';definitions={d['id']:d for d in read(logical/'definitions.json')['blocks']};d=definitions['o_c001'];bush=definitions['o_dry_bush'];t=key(d['default']);b=key(bush['default']);ids={d['models'][t],bush['models'][b]};meshes={i:m['polygons']for i,m in stream_members(logical/'meshes.json.gz')if i in ids}
    path=card([meshes[d['models'][t]],meshes[bush['models'][b]]],['Сухое дерево / o_c001','Сухой куст / o_dry_bush'],'tree-and-bush-two-ids.png');images.insert(0,{'path':path,'ids':['o_c001','o_dry_bush'],'states':[t,b],'reason':'two distinct constructions; each has its own player-facing rotation'})
    (OUT/'shortimageindex.json').write_text(json.dumps({'renderer':'software real-mesh render; not Minecraft screenshot','pathsRelativeTo':'index directory','images':images},ensure_ascii=False,indent=2)+'\n',encoding='utf8');print('Rendered',len(images),'review cards')
if __name__=='__main__':main()
