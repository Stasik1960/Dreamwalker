"""Read-only used-UV alpha evidence for the 90631 lamp layer discrepancy."""
from pathlib import Path
import json, traceback
import numpy as np
from v124_visual_audit import Visuals, pose_data, OUT

def main():
    baseline=json.loads((OUT/'baseline-content.json').read_text(encoding='utf-8'))
    rows={str(r['temporaryId']):r for r in baseline['debugCatalogue']['entries']}
    visuals=Visuals(vanilla_jar=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar')
    evidence=[]
    groups=json.loads((OUT/'general-functional-groups.json').read_text(encoding='utf-8'))['groups']
    discrepancies=json.loads((OUT/'group-metadata-audit.json').read_text(encoding='utf-8'))['metadataDifferences']
    selected={r['mainId'] for r in discrepancies if 'layer' in r['fields']}
    ids=sorted({m['id'] for g in groups if g['mainId'] in selected for m in g['members']})
    for i in ids:
        registry=rows[i]['registryId'];spec=baseline['descriptors'][registry.split(':')[1]]
        quads=[q for variant in spec['variants'] for q in pose_data(visuals,spec,variant)]
        by_texture={}
        for _,uv,texture,_ in quads:
            image=visuals.image(texture);height,width=image.shape[:2]
            low=uv.min(axis=0);high=uv.max(axis=0)
            us=np.arange(int(np.floor(low[0]*width/16)),int(np.ceil(high[0]*width/16))+1)%width
            vs=np.arange(int(np.floor(low[1]*height/16)),int(np.ceil(high[1]*height/16))+1)%height
            alpha=image[vs[:,None],us[None,:],3].reshape(-1)
            stats=by_texture.setdefault(texture,{'referencedFaceCount':0,'sampledAlphaValues':set(),'partialAlphaSamples':0})
            stats['referencedFaceCount']+=1;stats['sampledAlphaValues'].update(int(v) for v in np.unique(alpha))
            stats['partialAlphaSamples']+=int(((alpha>0)&(alpha<255)).sum())
        for data in by_texture.values():data['sampledAlphaValues']=sorted(data['sampledAlphaValues'])
        evidence.append({'id':i,'registryId':registry,'baselineLayer':spec.get('layer'),'textures':by_texture})
    output={'status':'USED_UV_ALPHA_ANALYSIS_COMPLETE_NATIVE_LAYER_REVIEW_PENDING',
      'method':'Inspect all referenced source-quad UV rectangles at texture pixel resolution; include boundary pixels conservatively. No model or texture changes.',
      'ids':evidence,'partialAlphaSamples':sum(t['partialAlphaSamples'] for e in evidence for t in e['textures'].values())}
    (OUT/'merged-render-layer-alpha.json').write_text(json.dumps(output,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
if __name__=='__main__':
    try:main()
    except Exception:
        (OUT/'lamp-render-layer-alpha-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
