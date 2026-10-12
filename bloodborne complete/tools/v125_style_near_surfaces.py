"""Find practically vanilla rendered surface patches, regardless of source filenames."""
from pathlib import Path
from collections import defaultdict
import io,json,traceback,zipfile
import numpy as np
from PIL import Image
from v125_style_inventory import ROOT,OUT,VANILLA,read
from v124_visual_audit import Visuals

def sample(image,uv):
    h,w=image.shape[:2];rows=[]
    for a in np.linspace(.01,.99,8):
        for b in np.linspace(.01,.99,8):
            p=(1-a)*(1-b)*uv[0]+(1-a)*b*uv[1]+a*b*uv[2]+a*(1-b)*uv[3]
            rows.append(image[int(p[1]*h/16)%h,int(p[0]*w/16)%w])
    return np.asarray(rows,dtype=np.float32).reshape(8,8,4)/255
def main():
    inventory=read(OUT/'style-inventory.json');visuals=Visuals(vanilla_jar=VANILLA)
    vanilla=zipfile.ZipFile(VANILLA);samples=[];names=[];uv=np.array([[0,0],[0,16],[16,16],[16,0]],dtype=float)
    for path in vanilla.namelist():
        if not path.startswith('assets/minecraft/textures/block/') or not path.endswith('.png'):continue
        image=np.asarray(Image.open(io.BytesIO(vanilla.read(path))).convert('RGBA'))
        # Animated textures are compared in their first displayed square frame.
        if image.shape[0]>image.shape[1]:image=image[:image.shape[1]]
        base=sample(image,uv)
        for quarter in range(4):
            for flip in (False,True):
                s=np.rot90(base,quarter);s=np.flip(s,0) if flip else s
                samples.append(s);names.append(('minecraft:'+path.split('/textures/',1)[1][:-4],quarter*90,flip))
    array=np.asarray(samples);cache={};output=[]
    def nearest(texture,coords):
        key=(texture,tuple(coords.reshape(-1)))
        if key not in cache:
            actual=sample(visuals.image(texture),coords)
            alpha=actual[:,:,3]
            score=np.mean(np.abs(array[:,:,:,:3]-actual[:,:,:3])*alpha[None,:,:,None],axis=(1,2,3))+np.mean(np.abs(array[:,:,:,3]-alpha),axis=(1,2))*.5
            best=int(score.argmin());rgb=np.mean(actual[:,:,:3],axis=2)
            # Contrast-centred luminance identifies a reused vanilla motif even
            # where a palette edit defeats byte-identical texture comparison.
            lum=np.mean(array[:,:,:,:3],axis=3);lum-=lum.mean(axis=(1,2),keepdims=True)
            rgb-=rgb.mean();pattern=np.mean(np.abs(lum-rgb[None,:,:])*alpha[None,:,:],axis=(1,2))+np.mean(np.abs(array[:,:,:,3]-alpha),axis=(1,2))*.5
            patterned=int(pattern.argmin())
            cache[key]={'pixelMae':round(float(score[best]),5),'closestVanillaTexture':names[best][0],
              'vanillaRotation':names[best][1],'vanillaMirrored':names[best][2],
              'patternMae':round(float(pattern[patterned]),5),'closestVanillaPattern':names[patterned][0]}
        return cache[key]
    for row in inventory['rows']:
        spec=read(ROOT/f"src/architecture/resources/bloodborne_dw/composite/{row['registryId'].split(':')[1]}.json")
        quads=[q for p in spec['variants'][0]['poses']['closed']['parts'] for q in visuals.quads(p)]
        groups=defaultdict(lambda:{'area':0.,'quadCount':0})
        for vertices,coords,texture,tint in quads:
            area=float(np.linalg.norm(np.cross(vertices[1]-vertices[0],vertices[2]-vertices[0]))+np.linalg.norm(np.cross(vertices[2]-vertices[0],vertices[3]-vertices[0])))/2
            alpha=float(sample(visuals.image(texture),coords)[:,:,3].mean());area*=alpha
            if area<=.01:continue
            key=texture,tuple(coords.reshape(-1));groups[key]['area']+=area;groups[key]['quadCount']+=1
        total=sum(g['area'] for g in groups.values());faces=[]
        for (texture,coords),group in groups.items():
            faces.append(dict(texture=texture,fraction=round(group['area']/total,5),quads=group['quadCount'],**nearest(texture,np.asarray(coords).reshape(4,2))))
        score=sum(r['fraction']*r['pixelMae'] for r in faces)
        output.append({'id':row['id'],'registryId':row['registryId'],'name':row['name'],'quadCount':len(quads),
          'weightedNearestVanillaPixelMae':round(score,5),'vanillaLikeAreaFraction':round(sum(r['fraction'] for r in faces if r['pixelMae']<=.08),5),
          'faces':sorted(faces,key=lambda r:-r['fraction'])})
    output.sort(key=lambda r:r['weightedNearestVanillaPixelMae'])
    result={'schema':'dreamwalker-v125-near-vanilla-surface-diagnostic-v1','status':'CANDIDATE_EVIDENCE_ONLY_NO_AUTOMATIC_RETIREMENT',
      'method':'Compare rendered alpha-covered UV patches against decoded vanilla block pixels and contrast-centred motifs, with all four rotations and mirrors. Full installed silhouette and native frames still determine decisions. A low score for common grey noise alone cannot justify deletion.',
      'workingTypesChecked':len(output),'rows':output}
    (OUT/'style-near-surfaces.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (OUT/'style-near-candidates.txt').write_text('\n'.join(r['id']+' '+r['name']+' q='+str(r['quadCount'])+' near='+str(r['vanillaLikeAreaFraction'])+' mae='+str(r['weightedNearestVanillaPixelMae'])+' '+', '.join(f['closestVanillaTexture']+':'+str(f['pixelMae']) for f in r['faces'][:2]) for r in output[:110])+'\n',encoding='utf-8')
if __name__=='__main__':
    try:main()
    except Exception:
        (OUT/'style-near-surfaces-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
