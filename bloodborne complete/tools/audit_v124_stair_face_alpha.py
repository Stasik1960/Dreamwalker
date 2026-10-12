"""Read-only source atlas alpha evidence for generated native stair faces."""
import json
import math
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/architecture/resources'
def main():
    rows=[]
    for path in sorted((RES/'assets/bloodborne_dw/models/block/imported_stairs/93028').glob('*.json')):
        model=json.loads(path.read_text(encoding='utf8'))
        for index,element in enumerate(model['elements']):
            for side,face in element['faces'].items():
                texture=face['texture'];seen=set()
                while texture.startswith('#'):
                    if texture in seen:raise ValueError('Cyclic face slot')
                    seen.add(texture);texture=model['textures'][texture[1:]]
                namespace,name=texture.split(':',1)
                image=Image.open(RES/f'assets/{namespace}/textures/{name}.png').convert('RGBA');uv=face['uv']
                x0,x1=sorted((uv[0]*image.width/16,uv[2]*image.width/16));y0,y1=sorted((uv[1]*image.height/16,uv[3]*image.height/16))
                pixels=[image.getpixel((x,y))[3] for y in range(max(0,math.floor(y0)),min(image.height,math.ceil(y1))) for x in range(max(0,math.floor(x0)),min(image.width,math.ceil(x1)))]
                rows.append({'model':path.name,'element':index,'side':side,'uv':uv,'opaqueFraction':sum(alpha>0 for alpha in pixels)/max(1,len(pixels)),'allTransparent':bool(pixels) and not any(pixels)})
    generated=[row for row in rows if row['model']!='original_trim.json']
    output={'schemaVersion':1,'status':'PASS' if not any(row['allTransparent'] for row in generated) else 'FAIL_TRANSPARENT_FUNCTIONAL_FACE','generatedFaceCount':len(generated),'allGeneratedFacesOpaque':all(row['opaqueFraction']==1 for row in generated),'faces':rows}
    path=ROOT/'build/v124-stair-face-alpha.json';path.write_bytes((json.dumps(output,ensure_ascii=False,indent=2)+'\n').encode('utf8'))
    print(json.dumps({'status':output['status'],'generatedFaces':len(generated),'allGeneratedFacesOpaque':output['allGeneratedFacesOpaque'],'allTransparent':[row for row in generated if row['allTransparent']]},ensure_ascii=False))
    if output['status']!='PASS':raise SystemExit(1)
if __name__=='__main__':main()
