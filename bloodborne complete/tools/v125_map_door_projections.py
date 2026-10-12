"""Source-ZIP textured nomination sheets; diagnostic projections, never game QA."""
from __future__ import annotations
import functools,io,json,math,zipfile
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw,ImageFont
from v124_visual_audit import Visuals,render
from v125_map_index import ROOT,PACK,MAP,BASELINE,dump,sha

DEST=ROOT/'docs/v12.5-city-review/doors'
class SourceVisuals(Visuals):
    def __init__(self):
        super().__init__(vanilla_jar=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar')
        self.pack=zipfile.ZipFile(PACK);self.prefix=next(n[:-len('pack.mcmeta')] for n in self.pack.namelist() if n.endswith('pack.mcmeta'))
        self.models={r['model']:r['resolved'] for r in json.loads((ROOT/'docs/remaining-transfer/source-inventory.json').read_text(encoding='utf-8-sig'))['models']}
    @functools.lru_cache(maxsize=None)
    def document(self,id):
        id=id if ':' in id else 'minecraft:'+id
        if id in self.models:return self.models[id]
        ns,path=id.split(':',1);file='assets/'+ns+'/models/'+path+'.json'
        return json.loads(self.pack.read(self.prefix+file) if self.prefix+file in self.pack.namelist() else self.vanilla.read(file))
    @functools.lru_cache(maxsize=None)
    def image(self,id):
        ns,path=id.split(':',1) if ':' in id else ('minecraft',id);file='assets/'+ns+'/textures/'+path+'.png'
        raw=self.pack.read(self.prefix+file) if self.prefix+file in self.pack.namelist() else self.vanilla.read(file)
        return np.asarray(Image.open(io.BytesIO(raw)).convert('RGBA'))

def main():
    doc=json.loads((DEST/'all-door-candidates.json').read_text());visuals=SourceVisuals();font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',12);labels=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',14);results=[];sheets=[]
    for start in range(0,len(doc['candidates']),30):
        subset=doc['candidates'][start:start+30];canvas=Image.new('RGBA',(1800,1050),'#25302f');draw=ImageDraw.Draw(canvas)
        for j,row in enumerate(subset):
            x=(j%6)*300;y=(j//6)*210;number=start+j+1
            try:
                quads=visuals.quads({'model':row['sourceModel']})
                for view,yaw in enumerate([0,180]):canvas.alpha_composite(Image.fromarray(render(visuals,quads,yaw,size=130)),(x+view*145,y+20))
                status='SOURCE_TEXTURED_PROJECTION_RENDERED'
            except Exception as error:
                status='SOURCE_DEPENDENCY_RENDER_FAIL';draw.text((x+5,y+25),'SOURCE ERROR '+str(error)[:30],font=font,fill='#ff9999')
            ids=sorted({r.get('mainId','') for r in row['canonicalTargets'] if r.get('mainId')});caption=f'{number:03d} ID '+','.join(ids)[:35]+'; map '+str(row['actualMapApplicationCount'])
            draw.text((x+5,y),caption,font=labels,fill='white');short=row['sourceModel'].removeprefix('minecraft:block/')
            draw.text((x+5,y+153),short[:38],font=font,fill='white');draw.text((x+5,y+169),short[38:76],font=font,fill='white');draw.text((x+5,y+186),row['classification'][:40],font=font,fill='#bad7cb')
            results.append({'number':number,'sourceModel':row['sourceModel'],'mainIds':ids,'sourceOccurrences':row['actualMapApplicationCount'],'status':status,'sheet':f'door-source-sheet-{start//30+1:02d}.png','tile':[x,y,300,210]})
        file=DEST/f'door-source-sheet-{start//30+1:02d}.png';canvas.convert('RGB').save(file);sheets.append({'fileName':file.name,'sha256':sha(file),'numbers':[start+1,start+len(subset)],'status':'DIAGNOSTIC_SOURCE_PROJECTION_NOT_NATIVE'})
    dump(DEST/'source-projection-evidence.json',{'schemaVersion':1,'baselineCommit':BASELINE,'packSha256':sha(PACK),'mapSha256':sha(MAP),'policy':'Source geometry/textures sampled from original ZIP, normalized fit two views. These are manual candidate-nomination sheets, not nativeMinecraft screenshots or functional/openability tests.','sheets':sheets,'models':results})

if __name__=='__main__':main()
