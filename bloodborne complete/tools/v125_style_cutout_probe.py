"""Compare authored cutout UV silhouettes with vanilla models, including atlas regions."""
import json,traceback
import numpy as np
from v125_style_inventory import ROOT,OUT,VANILLA,read
from v124_visual_audit import Visuals

MODELS=['chain','grass','tall_grass_bottom','tall_grass_top','fern','large_fern_bottom','large_fern_top','dead_bush','vine','potted_fern','potted_dead_bush','potted_bamboo','potted_azalea_bush','potted_flowering_azalea_bush','potted_mangrove_propagule']
IDS={'90084','90086','90088','90144','90145','90146','90149','90150','90151','90158','90519','90673','90755','90786','93098','93141'}
def sample(image,uv):
    h,w=image.shape[:2];grid=[]
    for a in (np.arange(32)+.5)/32:
        for b in (np.arange(32)+.5)/32:
            p=(1-a)*(1-b)*uv[0]+(1-a)*b*uv[1]+a*b*uv[2]+a*(1-b)*uv[3]
            grid.append(image[int(p[1]*h/16)%h,int(p[0]*w/16)%w]/255)
    return np.array(grid).reshape(32,32,4)
def main():
    visual=Visuals(vanilla_jar=VANILLA);references=[]
    for name in MODELS:
        try:faces=visual.quads({'model':'minecraft:block/'+name,'offset':[0,0,0]})
        except Exception:continue
        for face in faces:
            v,u,t,tint=face;s=sample(visual.image(t),u)
            for rotation in range(4):
                for mirror in (False,True):
                    transformed=np.rot90(s,rotation);transformed=np.flip(transformed,0) if mirror else transformed
                    references.append((name,transformed,v.max(axis=0)-v.min(axis=0),rotation*90,mirror))
    output=[]
    for row in read(OUT/'style-inventory.json')['rows']:
        if row['id'] not in IDS:continue
        spec=read(ROOT/f"src/architecture/resources/bloodborne_dw/composite/{row['registryId'].split(':')[1]}.json")
        observations=[]
        for part in spec['variants'][0]['poses']['closed']['parts']:
            for vertices,uv,texture,tint in visual.quads(part):
                actual=sample(visual.image(texture),uv);mask=actual[:,:,3]>.5
                if mask.all() or not mask.any():continue
                scores=[]
                for model,reference,extent,rotation,mirror in references:
                    other=reference[:,:,3]>.5;union=mask|other
                    disagreement=np.count_nonzero(mask^other)/max(1,np.count_nonzero(union))
                    scores.append((disagreement,model,rotation,mirror,extent.tolist()))
                score,model,rotation,mirror,extent=min(scores)
                observations.append({'texture':texture,'vanillaModel':model,'alphaSilhouetteDifference':round(score,5),
                  'rotation':rotation,'mirrored':mirror,'sourcePlaneExtents':(vertices.max(axis=0)-vertices.min(axis=0)).round(5).tolist(),
                  'vanillaPlaneExtents':extent,'sourceCoveredFraction':round(float(mask.mean()),5)})
        output.append({'id':row['id'],'name':row['name'],'planes':observations})
    (OUT/'style-cutout-silhouettes.json').write_text(json.dumps({'status':'UV_ALPHA_DIAGNOSTIC_NATIVE_STYLE_REQUIRED','vanillaModelsChecked':MODELS,'rows':output},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
if __name__=='__main__':
    try:main()
    except Exception:
        (OUT/'style-cutout-silhouettes-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
