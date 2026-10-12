"""Review current newly reserved types after the frozen544-type visual pass."""
import json
import numpy as np
from PIL import Image,ImageDraw
from v127_visual_export import Art,ROOT,OUT,VANILLA,inventory,spec_of,active_indices,quads_for,render,composite,BG,write_json

def main():
    art=Art(vanilla_jar=VANILLA);records=[]
    for row in inventory():
        if int(row['id'])<93228:continue
        spec=spec_of(row);indices=active_indices(spec);side=160
        sheet=Image.new('RGB',(side*4,180*len(indices)),tuple(BG));draw=ImageDraw.Draw(sheet)
        for n,index in enumerate(indices):
            quads=quads_for(art,spec,index)
            for yaw in range(4):sheet.paste(Image.fromarray(composite(render(art,quads,yaw*90,side))),(yaw*side,n*180+20))
            draw.text((5,n*180),f"{row['id']} pose {index} | {spec['displayName']}",fill=(238,242,245))
        path=OUT/'projections'/f"final-extra-{row['id']}.png";sheet.save(path)
        records.append({'id':row['id'],'registryId':row['registry'],'name':spec['displayName'],'poses':indices,'image':str(path.relative_to(OUT)),'reviewStatus':'PENDING_VIEW'})
    write_json(OUT/'final-visual-supplement.json',{'method':'Current native source RGBA/UV transformed geometry; four yaw views of every active pose of newly reserved composite types. Software projection, native client QA tracked separately.','objects':records})
    print(json.dumps({'objects':len(records),'poses':sum(len(r['poses']) for r in records)}))

if __name__=='__main__':main()
