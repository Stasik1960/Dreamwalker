"""Read-only visible source-face alpha audit; source blank sprites are helpers."""
import hashlib,io,json,traceback,zipfile
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'docs/remaining-transfer'
PACK=ROOT.parent/'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16/bbmc_v15_resource.zip'
VANILLA=Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar')
def main():
    candidate=json.loads((OUT/'source-candidates.json').read_text(encoding='utf-8'))
    inventory=json.loads((OUT/'source-inventory.json').read_text(encoding='utf-8'))
    models={row['model']:row for row in inventory['models']};images={};texture_evidence={};visibility={};blank=[];unknown=[]
    with zipfile.ZipFile(PACK) as pack,zipfile.ZipFile(VANILLA) as vanilla:
        names=set(pack.namelist());prefix=next(name[:-len('pack.mcmeta')] for name in names if name.endswith('pack.mcmeta'))
        def png(texture):
            if texture in images:return images[texture]
            namespace,path=texture.split(':',1) if ':' in texture else ('minecraft',texture)
            name=f'assets/{namespace}/textures/{path}.png'
            from_pack=prefix+name in names
            raw=pack.read(prefix+name) if from_pack else vanilla.read(name)
            image=Image.open(io.BytesIO(raw)).convert('RGBA')
            texture_evidence[texture]={'texture':texture,'origin':'SOURCE_PACK' if from_pack else 'VANILLA_DEPENDENCY','entry':prefix+name if from_pack else name,'sha256':hashlib.sha256(raw).hexdigest(),'size':[image.width,image.height],'wholeImageAlphaExtrema':list(image.getchannel('A').getextrema())}
            images[texture]=image;return image
        for key,row in models.items():
            visible=False;reasons=[];faces=0;face_evidence=[]
            for element in row['resolved'].get('elements',[]):
                for face in element.get('faces',{}).values():
                    faces+=1;texture=face['texture']
                    if texture.startswith('#'):texture=row['resolved']['textures'].get(texture[1:],texture)
                    if texture.startswith('#'):
                        reasons.append('Undefined visible texture '+texture);continue
                    try:
                        image=png(texture)
                    except Exception as error:
                        reasons.append(str(error));continue
                    uv=face.get('uv')
                    if uv:
                        left,right=sorted([uv[0]*image.width/16,uv[2]*image.width/16]);top,bottom=sorted([uv[1]*image.height/16,uv[3]*image.height/16])
                        if right==left or bottom==top:
                            # Vanilla may clamp or stretch degenerate UV to one texel.
                            left=max(0,min(image.width-1,int(left)));right=left+1;top=max(0,min(image.height-1,int(top)));bottom=top+1
                        box=(int(max(0,left)),int(max(0,top)),int(min(image.width,right+1)),int(min(image.height,bottom+1)))
                        if box[2]>box[0] and box[3]>box[1]:image=image.crop(box)
                    extrema=list(image.getchannel('A').getextrema())
                    visible|=extrema[1]>0
                    face_evidence.append({'texture':texture,'uv':uv,'sampledAlphaExtrema':extrema})
            visibility[key]=visible
            if not visible and faces and not reasons:blank.append({'model':key,'faces':faces,'faceSamples':face_evidence,'sourceStates':row['used_by']})
            if reasons:unknown.append({'model':key,'issues':sorted(set(reasons))})
        empty=[]
        for obj in candidate['objects']:
            for variant in obj['variants']:
                if not any(visibility.get(app['model'],False) for app in variant['closed']):
                    empty.append({'path':obj['path'],'variant':variant['variant'],'name':obj['displayName'],'sourceModels':variant['sourceModels']})
        report={'status':'PASS_READ_ONLY','modelsWithOnlyTransparentSampledFaces':blank,'offeredStatesWithOnlyTransparentSampledFaces':empty,'unavailableSourceFaceTextures':unknown,'imagesDecoded':len(images),'decodedTextureEvidence':list(texture_evidence.values())}
        (OUT/'source-alpha-audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
if __name__=='__main__':
    try:main()
    except Exception:
        (OUT/'source-alpha-audit.json').write_text(json.dumps({'status':'FAIL','traceback':traceback.format_exc()},indent=2),encoding='utf-8');raise
