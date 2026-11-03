"""Generate reviewed Editor2 palette sprites; never infer materials from names."""
from pathlib import Path
from zipfile import ZipFile
from PIL import Image
import hashlib, io, json

root=Path(__file__).resolve().parents[2]
review=root/'DW-City-Visuals/tools/editor2-palette-inputs'
output=root/'DW-City-Visuals/src/main/resources/dw_city_visuals/alt/assets'
policy=json.loads((review/'classification.json').read_text(encoding='utf8'))
inventory=json.loads((review/'inventory.json').read_text(encoding='utf8'))
assert policy['schema']==1
entries=policy['sprites']; rows={r['sprite']:r for r in inventory['rows']}
colors=((201,133,135),(135,148,126),(203,184,142),(164,71,66))
mapping={}; generated={}; total=0; excluded=[]
with ZipFile(review/'sources.zip') as jar:
    for sprite,classification in sorted(entries.items()):
        kind=classification['classification']
        if kind not in ('stone','mixed'):
            if kind=='unknown': excluded.append(sprite)
            continue
        row=rows[sprite]; source=row['altPath']; data=jar.read(source)
        image=Image.open(io.BytesIO(data)).convert('RGBA')
        if kind=='mixed':
            mask=Image.open(review/classification['mask']).convert('L')
            if mask.size!=image.size: raise ValueError('Mask dimensions differ: '+sprite)
        else: mask=Image.new('L',image.size,255)
        sidecar=source+'.mcmeta'; meta=jar.read(sidecar) if sidecar in jar.namelist() else b''
        normalpath=source[:-4]+'_n.png'; normal=jar.read(normalpath) if normalpath in jar.namelist() else b''
        ids=[]
        for color in colors:
            result=Image.new('RGBA',image.size); pixels=[]
            for rgba,m in zip(image.getdata(),mask.getdata()):
                r,g,b,a=rgba; luminance=(r*54+g*183+b*19)/256/144
                toned=tuple(min(255,round(c*luminance)) for c in color)
                pixels.append(tuple(round(old+(new-old)*m/255) for old,new in zip((r,g,b),toned))+(a,))
            result.putdata(pixels); buf=io.BytesIO();result.save(buf,format='PNG'); png=buf.getvalue()
            digest=hashlib.sha256(png+b'\0'+meta+b'\0'+normal).hexdigest()
            target='block/editor2_palette/'+digest
            if digest not in generated:
                dest=output/'bloodborne_dw/textures'/f'{target}.png';dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(png)
                if meta: Path(str(dest)+'.mcmeta').write_bytes(meta)
                if normal: dest.with_name(dest.stem+'_n.png').write_bytes(normal)
                generated[digest]=image.size;total+=image.width*image.height
            ids.append('bloodborne_dw:'+target)
        mapping[sprite]=ids
(output/'bloodborne_dw/visuals').mkdir(parents=True,exist_ok=True)
(output/'bloodborne_dw/visuals/editor2-palette.json').write_text(json.dumps({'schema':1,'sprites':mapping},indent=2),encoding='utf8')
print(json.dumps({'sources':len(mapping),'variants':len(generated),'pixels':total,'rgbaMipUpperBytes':(total*16+2)//3,'unknown':excluded},indent=2))
