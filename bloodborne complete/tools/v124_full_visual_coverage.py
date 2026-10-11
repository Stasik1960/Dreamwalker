"""Every old offered state receives diagnostic rendered-surface coverage.

The global pass compares render colours/shapes regardless of atlas/path identity.
It cannot certify gameplay or native appearance. Native screenshots are separate.
"""
from __future__ import annotations
import json, sys, traceback
from pathlib import Path
from collections import defaultdict
import numpy as np
from PIL import Image,ImageDraw
from v124_visual_audit import OUT,ROOT,Visuals,pose_data,rot,render,sha

BG=np.array([39,48,55],dtype=np.uint8)
def composite(a):
    alpha=a[...,3:4].astype(float)/255
    return (a[...,:3]*alpha+BG*(1-alpha)).astype(np.uint8)
def feature(a):
    reduced=np.asarray([np.asarray(Image.fromarray(i).resize((12,12),Image.Resampling.BOX)) for i in a])
    mask=np.any(reduced!=BG,axis=3)
    return np.concatenate([reduced.mean(axis=0).reshape(-1)/255,mask.mean(axis=0).reshape(-1)])
def main():
    audit=json.loads((OUT/'visual-candidates.json').read_text(encoding='utf-8'))
    near=json.loads((OUT/'near-visual-candidates.json').read_text(encoding='utf-8'))
    frozen=json.loads((OUT/'baseline-content.json').read_text(encoding='utf-8'))['descriptors']
    visuals=Visuals(vanilla_jar=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar')
    unique={r['appearanceSignature']:r for r in audit['inventory']};cache={}
    for p in near['pairs']:
        im=Image.open(ROOT/p['diagnosticImage']).convert('RGB')
        for index,key in enumerate(['a','b']):
            r=p[key];sig=r['appearanceSignature']
            if sig in cache:continue
            images=np.asarray([np.asarray(im.crop((yaw*128+index*64,15,yaw*128+index*64+64,79))) for yaw in range(8)])
            cache[sig]=np.roll(images,-int(r['canonicalYaw']/45),axis=0)
    records=[];features=[];dimensions=[];data=[]
    for index,(sig,row) in enumerate(unique.items()):
        descriptor=frozen[row['registryId'].split(':',1)[1]];quads=pose_data(visuals,descriptor,descriptor['variants'][row['variant']])
        points=np.asarray([q[0] for q in quads]).reshape(-1,3)
        sizes=[np.ptp(rot(points,1,yaw*45,[8,8,8]),axis=0) for yaw in range(8)]
        invariant=min(sizes,key=lambda s:s[0]*s[2]);invariant=np.array([min(invariant[0],invariant[2]),invariant[1],max(invariant[0],invariant[2])]);dimensions.append(invariant)
        if sig not in cache:cache[sig]=np.asarray([composite(render(visuals,quads,row['canonicalYaw']+yaw*45)) for yaw in range(8)])
        images=cache[sig];features.append(feature(images));data.append(images)
        records.append({'registryId':row['registryId'],'id':row['id'],'variant':row['variant'],'appearanceSignature':sig,
                        'canonicalViewSha256':[sha(frame.tobytes()) for frame in images],
                        'invariantDimensionsModelUnits':[round(float(v),6) for v in invariant]})
        if index%100==0:print('all-state render',index,'/',len(unique),flush=True)
    f=np.asarray(features);dims=np.asarray(dimensions);members=list(unique.values());matches=[];seen=set()
    for i,first in enumerate(members):
        ratio=np.max(np.abs(dims-dims[i])/np.maximum(np.maximum(dims,dims[i]),.01),axis=1)
        distance=np.mean(np.abs(f-f[i]),axis=1)
        candidate=[int(n) for n in np.argsort(distance) if n!=i and ratio[n]<=.15][:12]
        for j in candidate:
            key=tuple(sorted([i,j]));
            if key in seen:continue
            seen.add(key);second=members[j]
            if first['registryId']==second['registryId']:continue
            one,two=data[i],data[j];best=None
            for offset in range(8):
                other=np.roll(two,offset,axis=0)
                mask1=np.any(one!=BG,axis=3);mask2=np.any(other!=BG,axis=3);mask=mask1|mask2
                silhouette=1-int((mask1&mask2).sum())/max(1,int(mask.sum()))
                pixel=float(np.abs(one.astype(float)-other.astype(float))[mask].mean()/255) if mask.any() else 0
                metric=(silhouette+pixel*.6,silhouette,pixel,offset)
                if best is None or metric<best:best=metric
            if best[0]<.11 and best[2]<.1:
                matches.append({'a':first,'b':second,'difference':round(best[0],5),'silhouetteDifference':round(best[1],5),
                                'pixelDifference':round(best[2],5),'canonicalViewOffset':best[3]*45,
                                'crossTextureAtlasContent':first['textureContentHashes']!=second['textureContentHashes']})
    matches.sort(key=lambda p:p['difference'])
    projections=OUT/'full-coverage-projections';projections.mkdir(exist_ok=True)
    for i,p in enumerate(matches):
        if not p['crossTextureAtlasContent']:continue
        a=cache[p['a']['appearanceSignature']];b=cache[p['b']['appearanceSignature']];canvas=Image.new('RGB',(1024,100),tuple(BG))
        for yaw in range(8):canvas.paste(Image.fromarray(a[yaw]),(yaw*128,8));canvas.paste(Image.fromarray(b[yaw]),(yaw*128+64,8))
        ImageDraw.Draw(canvas).text((4,80),f"{p['a']['id']}:{p['a']['variant']} / {p['b']['id']}:{p['b']['variant']} diff {p['difference']:.4f} cross atlas content",fill='white')
        path=projections/f"{i:03d}-{p['a']['id']}-{p['b']['id']}.png";canvas.save(path);p['diagnosticImage']=str(path.relative_to(ROOT)).replace('\\','/')
    by_signature={r['appearanceSignature']:r for r in records}
    state_records=[{**by_signature[r['appearanceSignature']],'id':r['id'],'registryId':r['registryId'],'variant':r['variant']} for r in audit['inventory']]
    output={'schemaVersion':1,'status':'FULL_SOFTWARE_VISUAL_COVERAGE_PENDING_NATIVE_REVIEW',
            'summary':{'sourceStates':len(state_records),'uniqueNormalizedVisualStates':len(unique),'eightViewImages':len(unique)*8,
                       'allAtlasGlobalCandidatePairs':len(seen),'nearMatches':len(matches),'crossAtlasMatches':sum(p['crossTextureAtlasContent'] for p in matches)},
            'method':'All1802states rendered through preserved old descriptors, transformed source geometry/UV/actual RGBA. Translation and yaw normalized. Global visual features and invariant dimensions select nearest12same-size candidates independently of atlas hash; eight-view pixel/silhouette comparison evaluates candidates. Native Minecraft screenshots remain separate.',
            'states':state_records,'matches':matches}
    (OUT/'full-visual-coverage.json').write_text(json.dumps(output,ensure_ascii=False,indent=2),encoding='utf-8')
    print('COMPLETE',json.dumps(output['summary']),flush=True)
if __name__=='__main__':
    with (OUT/'full-visual-coverage-console.txt').open('w',encoding='utf-8') as stream:
        sys.stdout=sys.stderr=stream
        try:main()
        except BaseException:traceback.print_exc();raise
